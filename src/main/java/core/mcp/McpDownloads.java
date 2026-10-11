package core.mcp;

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

    /** The file behind a request path, or null when it is unknown or gone. */
    static synchronized String get(String path) {
        return path != null && path.startsWith(PREFIX) ? FILES.get(path.substring(PREFIX.length())) : null;
    }
}
