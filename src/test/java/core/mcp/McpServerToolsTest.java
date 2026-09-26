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

        assertThat(tools).hasSize(36);
        assertThat(tools).extracting(t -> t.getAsJsonObject().get("name").getAsString())
                .containsExactlyInAnyOrder("get_team", "get_squad", "get_player",
                        "get_opponent_squad", "scout_opponent",
                        "get_upcoming_matches", "search_transfers", "search_transfers_batch",
                        "get_team_transfers", "place_bid", "get_open_bids", "get_lineup",
                        "apply_lineup", "set_player_group", "send_match_order",
                        "get_training", "get_player_history", "get_training_events", "set_training",
                        "get_matches", "get_match",
                        "get_opponent_ratings", "get_rating_calibration",
                        "simulate_lineup", "list_challenges", "check_challengeable", "arrange_challenge",
                        "respond_to_challenge", "get_league_table", "get_finances",
                        "add_to_scout", "list_scout", "remove_from_scout",
                        "estimate_market_value", "backfill_character", "download_team");
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
