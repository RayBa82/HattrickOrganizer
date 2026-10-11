package core.mcp;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.List;

/**
 * One MCP tool: what {@code tools/list} advertises and what {@code tools/call} runs.
 *
 * @param name             the tool name the client calls
 * @param description      what the tool does, read by the model to decide whether to call it
 * @param inputSchema      JSON schema of the {@code arguments} object
 * @param handler          the implementation
 * @param budgetHint       what an over-budget answer tells the model to do instead, naming this
 *                         tool's own arguments; null derives one from the schema
 * @param optionalSections what {@link McpBudget} may drop, in this order, before it gives up on an
 *                         over-budget answer
 */
public record McpTool(String name, String description, JsonObject inputSchema, Handler handler,
                      String budgetHint, List<OptionalSection> optionalSections) {

    public McpTool(String name, String description, JsonObject inputSchema, Handler handler) {
        this(name, description, inputSchema, handler, null, List.of());
    }

    /** This tool with its own over-budget hint and the sections it may shed, in drop order. */
    public McpTool withBudget(String hint, OptionalSection... sections) {
        return new McpTool(name, description, inputSchema, handler, hint, List.of(sections));
    }

    /**
     * The hint an over-budget answer carries. A tool without its own gets its argument names,
     * which at least points at the real ways to narrow the call.
     */
    public String hint() {
        if (budgetHint != null) {
            return budgetHint;
        }
        final var properties = inputSchema.getAsJsonObject("properties");
        if (properties == null || properties.isEmpty()) {
            return "This tool takes no arguments, so its answer cannot be narrowed; tell the user.";
        }
        return "Narrow the call with this tool's arguments: "
                + String.join(", ", properties.keySet()) + ".";
    }

    /**
     * A part of a tool's answer that can go when the whole is over budget: {@code keys} at the top
     * level, or in every element of the top-level array {@code array}.
     */
    public record OptionalSection(String name, String array, List<String> keys) {

        public static OptionalSection top(String key) {
            return new OptionalSection(key, null, List.of(key));
        }

        public static OptionalSection perItem(String array, String name, String... keys) {
            return new OptionalSection(array + "[]." + name, array, List.of(keys));
        }

        /** Removes the section; false when the answer did not carry it. */
        boolean removeFrom(JsonObject result) {
            var removed = false;
            if (array == null) {
                for (final var key : keys) {
                    removed |= result.remove(key) != null;
                }
                return removed;
            }
            if (!result.has(array) || !result.get(array).isJsonArray()) {
                return false;
            }
            for (final var element : result.getAsJsonArray(array)) {
                if (element.isJsonObject()) {
                    for (final var key : keys) {
                        removed |= element.getAsJsonObject().remove(key) != null;
                    }
                }
            }
            return removed;
        }
    }

    @FunctionalInterface
    public interface Handler {
        /**
         * @param arguments the client's {@code arguments} object, never null (empty if omitted)
         * @return the JSON that becomes the tool result
         * @throws McpToolException for an expected failure the model should read and act on
         */
        JsonObject call(JsonObject arguments) throws Exception;
    }

    /** An object schema over the given properties, with the named ones required. */
    public static JsonObject schema(JsonObject properties, String... required) {
        final var schema = new JsonObject();
        schema.addProperty("type", "object");
        schema.add("properties", properties);
        final var requiredNames = new JsonArray();
        for (final var name : required) {
            requiredNames.add(name);
        }
        schema.add("required", requiredNames);
        return schema;
    }

    /** One schema property. */
    public static JsonObject prop(String type, String description) {
        final var property = new JsonObject();
        property.addProperty("type", type);
        property.addProperty("description", description);
        return property;
    }

    /** A properties object built from alternating name / {@link #prop} pairs. */
    public static JsonObject properties(Object... nameAndProperty) {
        final var properties = new JsonObject();
        for (int i = 0; i + 1 < nameAndProperty.length; i += 2) {
            properties.add((String) nameAndProperty[i], (JsonObject) nameAndProperty[i + 1]);
        }
        return properties;
    }
}
