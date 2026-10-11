package core.mcp;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class McpBudgetTest {

    private static McpTool tool(McpTool.OptionalSection... sections) {
        return new McpTool("t", "test", McpTool.schema(McpTool.properties(
                "playerIds", McpTool.prop("array", "ids"),
                "limit", McpTool.prop("integer", "page size"))), args -> new JsonObject())
                .withBudget("Ask for fewer playerIds.", sections);
    }

    /** n players, each with a small core and a bulky optional block. */
    private static JsonObject squad(int n, int bulk) {
        final var players = new JsonArray();
        for (int i = 0; i < n; i++) {
            final var p = new JsonObject();
            p.addProperty("playerId", i);
            p.addProperty("available", true);
            p.addProperty("positionRatings", "x".repeat(bulk));
            players.add(p);
        }
        final var json = new JsonObject();
        json.add("players", players);
        return json;
    }

    @Test
    void anAnswerInsideTheBudgetGoesOutUntouched() {
        final var result = squad(3, 10);
        final var expected = McpHttpServer.GSON.toJson(result);

        assertThat(McpBudget.fit(tool(), result)).isEqualTo(expected);
    }

    @Test
    void overBudgetTheOptionalSectionsGoFirstAndTheCoreStaysWhole() {
        final var text = McpBudget.fit(
                tool(McpTool.OptionalSection.perItem("players", "positionRatings", "positionRatings")),
                squad(30, 500));

        assertThat(text.length()).isLessThanOrEqualTo(McpBudget.MAX_CHARS);
        final var json = JsonParser.parseString(text).getAsJsonObject();
        assertThat(json.getAsJsonArray("players")).hasSize(30);
        assertThat(json.getAsJsonArray("players").get(0).getAsJsonObject().has("positionRatings")).isFalse();
        assertThat(json.getAsJsonArray("droppedForBudget").get(0).getAsString())
                .isEqualTo("players[].positionRatings");
        assertThat(json.get("budgetNote").getAsString()).contains("playerIds");
    }

    /** All or nothing for the core: no half list, only the envelope and a hint naming real arguments. */
    @Test
    void aCoreOverBudgetBecomesTheEnvelopeAndNothingPartialLeaks() {
        final var full = squad(400, 0);
        final var fullChars = McpHttpServer.GSON.toJson(full).length();

        final var json = JsonParser.parseString(McpBudget.fit(tool(), full)).getAsJsonObject();

        assertThat(json.keySet()).containsExactlyInAnyOrder("truncated", "wouldBeChars", "maxChars", "hint");
        assertThat(json.get("truncated").getAsBoolean()).isTrue();
        assertThat(json.get("wouldBeChars").getAsInt()).isEqualTo(fullChars);
        assertThat(json.get("hint").getAsString()).contains("playerIds");
    }

    /** A caller's explicit bulk request, marked by the tool, goes out whole and unbudgeted. */
    @Test
    void aBulkAnswerGoesOutWholePastTheBudget() {
        final var full = squad(400, 0);
        full.addProperty(McpBudget.BULK, true);
        final var expected = McpHttpServer.GSON.toJson(full);

        assertThat(McpBudget.fit(tool(), full)).isEqualTo(expected);
        assertThat(expected.length()).isGreaterThan(McpBudget.MAX_CHARS);
    }

    @Test
    void aToolWithoutItsOwnHintNamesItsArguments() {
        final var plain = new McpTool("t", "test", McpTool.schema(McpTool.properties(
                "limit", McpTool.prop("integer", "page size"))), args -> new JsonObject());

        assertThat(plain.hint()).contains("limit");
    }
}
