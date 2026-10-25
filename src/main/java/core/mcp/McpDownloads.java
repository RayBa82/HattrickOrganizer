package core.mcp;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Files a tool hands out instead of putting them in its answer: data too big for the response
 * budget that a script reads whole. The tool answer carries the path, the script fetches it
 * with a plain HTTP GET on the MCP endpoint's host and port ({@code GET /mcp/download/<id>}),
 * behind the same Origin and Host guards as the tool calls.
 * <p>
 * In memory, the newest {@link #KEPT} only: a restart or newer downloads clear the old ones.
 */
final class McpDownloads {

    static final String PREFIX = "/mcp/download/";
    static final int KEPT = 20;

    private static final Map<String, String> FILES = new LinkedHashMap<>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
            return size() > KEPT;
        }
    };

    private McpDownloads() {
    }

    /** Keeps the JSON text for download and returns the path to GET it from. */
    static synchronized String put(String json) {
        final var id = UUID.randomUUID().toString();
        FILES.put(id, json);
        return PREFIX + id;
    }

    /** Keeps {@code file} for download and names it in {@code answer}: download, downloadChars, downloadNote. */
    static void attach(JsonObject answer, JsonObject file) {
        final var text = McpHttpServer.GSON.toJson(file);
        answer.addProperty("download", put(text));
        answer.addProperty("downloadChars", text.length());
        answer.addProperty("downloadNote", "HTTP GET this path on the MCP endpoint's host and port "
                + "(e.g. curl http://localhost:8765/mcp/download/...); the newest " + KEPT
                + " downloads are kept in memory");
    }

    /** Rows of the list a {@link #bulk} answer still shows. */
    static final int PREVIEW = 3;

    /**
     * A tool's bulk answer: the whole answer goes into the download, and what comes back is the
     * same answer with {@code listKey} cut to its first {@link #PREVIEW} rows (as preview). The
     * header that makes the rows readable (skillOrder, count, note...) is in both; fileOnly names
     * parts too big for the answer, left out of the header and of each preview row alike.
     */
    static JsonObject bulk(JsonObject full, String listKey, String... fileOnly) {
        final var answer = full.deepCopy();
        for (final var key : fileOnly) {
            answer.remove(key);
        }
        cutToPreview(answer, listKey, fileOnly);
        attach(answer, full);
        return answer;
    }

    /**
     * {@link #bulk} for an answer whose lists sit in its sections: each {@code section.list} path
     * (e.g. ratings.matches) is cut to a preview inside its section, a bare name at the top level.
     * A section or list the answer lacks - not included by the caller - is skipped.
     */
    static JsonObject bulkSections(JsonObject full, String... listPaths) {
        final var answer = full.deepCopy();
        for (final var path : listPaths) {
            final var dot = path.indexOf('.');
            final var section = dot < 0 ? answer : answer.getAsJsonObject(path.substring(0, dot));
            final var list = path.substring(dot + 1);
            if (section != null && section.has(list)) {
                cutToPreview(section, list);
            }
        }
        attach(answer, full);
        return answer;
    }

    /** Replaces {@code owner}'s list under listKey with a preview of its first rows, fileOnly parts left out. */
    private static void cutToPreview(JsonObject owner, String listKey, String... fileOnly) {
        final var rows = owner.remove(listKey).getAsJsonArray();
        final var preview = new JsonArray();
        for (int i = 0; i < Math.min(PREVIEW, rows.size()); i++) {
            final var row = rows.get(i);
            if (row.isJsonObject()) {
                for (final var key : fileOnly) {
                    row.getAsJsonObject().remove(key);
                }
            }
            preview.add(row);
        }
        owner.add("preview", preview);
    }

    /** The file behind a request path, or null when it is unknown or gone. */
    static synchronized String get(String path) {
        return path != null && path.startsWith(PREFIX) ? FILES.get(path.substring(PREFIX.length())) : null;
    }
}
