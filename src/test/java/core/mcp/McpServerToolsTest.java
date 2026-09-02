package core.mcp;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class McpServerToolsTest {

    @Test
    void theServerAdvertisesAllNineteenTools() {
        final var server = new McpHttpServer();
        server.registerAllTools();

        final var tools = server.handle(JsonParser.parseString("""
                        {"jsonrpc":"2.0","id":1,"method":"tools/list"}""").getAsJsonObject())
                .getAsJsonObject("result").getAsJsonArray("tools");

        assertThat(tools).hasSize(19);
        assertThat(tools).extracting(t -> t.getAsJsonObject().get("name").getAsString())
                .containsExactlyInAnyOrder("get_team", "get_squad", "get_player",
                        "get_upcoming_matches", "search_transfers", "find_comparables",
                        "get_team_transfers", "get_lineup", "evaluate_lineup", "optimize_lineup",
                        "apply_lineup", "get_training", "get_matches", "get_match", "predict_match",
                        "simulate_lineup", "get_league_table", "get_finances", "set_player_group");
    }
}
