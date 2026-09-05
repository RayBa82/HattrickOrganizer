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

        assertThat(tools).hasSize(32);
        assertThat(tools).extracting(t -> t.getAsJsonObject().get("name").getAsString())
                .containsExactlyInAnyOrder("get_team", "get_squad", "get_player",
                        "get_opponent_squad",
                        "get_upcoming_matches", "search_transfers", "find_comparables",
                        "get_team_transfers", "place_bid", "get_open_bids", "get_lineup", "evaluate_lineup",
                        "optimize_lineup", "apply_lineup", "set_player_group", "send_match_order",
                        "get_training", "get_player_history", "set_training",
                        "get_matches", "get_match",
                        "get_opponent_ratings", "predict_match",
                        "simulate_lineup", "list_challenges", "arrange_challenge",
                        "respond_to_challenge", "get_league_table", "get_finances",
                        "add_to_scout", "list_scout", "estimate_market_value");
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
