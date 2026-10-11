package core.mcp;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class McpServerToolsTest {

    @Test
    void theServerAdvertisesEveryTool() {
        final var server = new McpHttpServer();
        server.registerAllTools();

        final var tools = server.handle(JsonParser.parseString("""
                        {"jsonrpc":"2.0","id":1,"method":"tools/list"}""").getAsJsonObject())
                .getAsJsonObject("result").getAsJsonArray("tools");

        assertThat(tools).hasSize(29);
        assertThat(tools).extracting(t -> t.getAsJsonObject().get("name").getAsString())
                .containsExactlyInAnyOrder("get_team", "get_squad", "analyze_opponent", "get_series",
                        "search_transfers",
                        "get_team_transfers", "get_player_transfer_history", "place_bid", "get_open_bids", "get_lineup",
                        "apply_lineup", "set_player_group", "send_match_order", "get_match_order",
                        "get_training", "get_player_history", "set_training",
                        "get_matches", "get_match", "get_match_weather",
                        "simulate_lineup", "list_challenges", "manage_challenge",
                        "get_finances",
                        "manage_scout", "list_scout",
                        "estimate_market_value", "download_team", "database_backup");
    }

    /**
     * The tool descriptions are the one part of this surface that costs tokens in every session
     * before a single call is made, so they carry a budget: what it returns, when to call it
     * rather than its neighbour, and the one trap that changes the call. The long form lives in
     * the assistant repo's docs/mcp-tools.md, which is read on demand.
     */
    @Test
    void noDescriptionGrowsBackIntoADocumentationPage() {
        final var server = new McpHttpServer();
        server.registerAllTools();

        final var tools = server.handle(JsonParser.parseString("""
                        {"jsonrpc":"2.0","id":1,"method":"tools/list"}""").getAsJsonObject())
                .getAsJsonObject("result").getAsJsonArray("tools");

        assertThat(tools).allSatisfy(t -> assertThat(
                t.getAsJsonObject().get("description").getAsString().length())
                .as("description of %s", t.getAsJsonObject().get("name").getAsString())
                .isLessThanOrEqualTo(400));
    }

    /**
     * An over-budget answer is only as useful as its hint, and AGENTS.md wants the hint to name
     * the tool's own arguments - a generic "too large" leaves the model nothing to do next.
     */
    @Test
    void everyBudgetHintNamesAnArgumentOfItsOwnTool() {
        final var tools = new java.util.ArrayList<McpTool>();
        tools.addAll(TeamTools.tools());
        tools.addAll(TransferTools.tools());
        tools.addAll(LineupTools.tools());
        tools.addAll(TrainingTools.tools());
        tools.addAll(MatchTools.tools());
        tools.addAll(BackupTools.tools());

        assertThat(tools).allSatisfy(tool -> {
            final var arguments = tool.inputSchema().getAsJsonObject("properties").keySet();
            if (arguments.isEmpty()) {
                assertThat(tool.hint()).as(tool.name()).contains("no arguments");
            } else {
                assertThat(arguments).as("hint of %s: %s", tool.name(), tool.hint())
                        .anyMatch(tool.hint()::contains);
            }
        });
    }

    /**
     * A null field has to reach the client as an explicit null, not vanish. Gson drops nulls by
     * default, and an absent key is indistinguishable from a field the build does not have - which
     * has already been misread that way once.
     */
    @Test
    void nullFieldsAreSerializedRatherThanDropped() {
        final var json = new com.google.gson.JsonObject();
        json.addProperty("salePrice", (Number) null);
        json.add("details", com.google.gson.JsonNull.INSTANCE);
        json.addProperty("playerId", 1001);

        assertThat(McpHttpServer.GSON.toJson(json))
                .isEqualTo("{\"salePrice\":null,\"details\":null,\"playerId\":1001}");
    }
}
