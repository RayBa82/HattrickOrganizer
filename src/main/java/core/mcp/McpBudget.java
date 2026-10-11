package core.mcp;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import core.util.HOLogger;

/**
 * The per-call response budget from AGENTS.md: no tool answer leaves the server longer than
 * {@link #MAX_CHARS} characters of serialized JSON, and none is ever cut off partway.
 * <p>
 * Over budget, a tool first sheds its declared {@link McpTool.OptionalSection}s in order and says
 * which in {@code droppedForBudget}, so the core of an everyday call - the squad's availability,
 * the minutes already banked - still arrives. Only when the core alone is too big does the answer
 * become the bare envelope: {@code truncated}, the size it would have had, and the tool's hint.
 */
final class McpBudget {

    static final int MAX_CHARS = 10_000;
    /**
     * Set to true at the top of a result by a tool whose caller asked for bulk: the whole answer
     * goes out unbudgeted, for a script to read from the file the client saves it to.
     */
    static final String BULK = "bulk";

    private McpBudget() {
    }

    /** The text to send for this result: whole, with optional sections dropped, or the envelope. */
    static String fit(McpTool tool, JsonObject result) {
        var text = McpHttpServer.GSON.toJson(result);
        if (text.length() <= MAX_CHARS) {
            return text;
        }
        if (result.has(BULK) && result.get(BULK).isJsonPrimitive() && result.get(BULK).getAsBoolean()) {
            HOLogger.instance().info(McpBudget.class, tool.name() + ": bulk, " + text.length() + " chars");
            return text;
        }
        final var fullChars = text.length();
        final var dropped = new JsonArray();
        for (final var section : tool.optionalSections()) {
            if (!section.removeFrom(result)) {
                continue;
            }
            dropped.add(section.name());
            result.add("droppedForBudget", dropped);
            result.addProperty("budgetNote", "The full answer was " + fullChars
                    + " characters, over the " + MAX_CHARS + " limit, so droppedForBudget was "
                    + "left out. " + tool.hint());
            text = McpHttpServer.GSON.toJson(result);
            if (text.length() <= MAX_CHARS) {
                HOLogger.instance().info(McpBudget.class, tool.name() + ": " + fullChars
                        + " chars, dropped " + dropped);
                return text;
            }
        }
        HOLogger.instance().info(McpBudget.class, tool.name() + ": " + fullChars
                + " chars, over budget even without its optional sections");
        final var envelope = new JsonObject();
        envelope.addProperty("truncated", true);
        envelope.addProperty("wouldBeChars", fullChars);
        envelope.addProperty("maxChars", MAX_CHARS);
        envelope.addProperty("hint", tool.hint());
        return McpHttpServer.GSON.toJson(envelope);
    }
}
