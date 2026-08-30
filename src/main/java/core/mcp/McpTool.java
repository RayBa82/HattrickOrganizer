package core.mcp;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * One MCP tool: what {@code tools/list} advertises and what {@code tools/call} runs.
 *
 * @param name        the tool name the client calls
 * @param description what the tool does, read by the model to decide whether to call it
 * @param inputSchema JSON schema of the {@code arguments} object
 * @param handler     the implementation
 */
public record McpTool(String name, String description, JsonObject inputSchema, Handler handler) {

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
