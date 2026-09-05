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
}
