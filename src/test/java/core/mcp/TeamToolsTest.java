package core.mcp;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The tool bodies need a loaded HOModel and a database, which the unit suite deliberately does not
 * have (there is no Swing or CHPP harness in this repo). What is testable here is the surface: the
 * names and schemas the client sees.
 */
class TeamToolsTest {

    @Test
    void tools_areTheSixReadTools() {
        assertThat(TeamTools.tools()).extracting(McpTool::name)
                .containsExactly("get_team", "get_squad", "get_player", "get_upcoming_matches",
                        "get_league_table", "get_finances");
    }

    @Test
    void theStandingsAndTheStatementTakeNoRequiredArguments() {
        final var byName = TeamTools.tools().stream()
                .filter(t -> t.name().equals("get_league_table") || t.name().equals("get_finances"))
                .toList();

        assertThat(byName).hasSize(2);
        assertThat(byName).allSatisfy(
                t -> assertThat(t.inputSchema().getAsJsonArray("required")).isEmpty());
        // the stadium block reads back a few played home matches, and how many is tunable
        assertThat(byName.get(1).inputSchema().getAsJsonObject("properties").has("homeMatches"))
                .isTrue();
    }

    @Test
    void getPlayer_requiresAPlayerId() {
        final var getPlayer = TeamTools.tools().stream()
                .filter(t -> t.name().equals("get_player")).findFirst().orElseThrow();

        assertThat(getPlayer.inputSchema().getAsJsonArray("required")).hasSize(1);
        assertThat(getPlayer.inputSchema().getAsJsonArray("required").get(0).getAsString())
                .isEqualTo("playerId");
        assertThat(getPlayer.inputSchema().getAsJsonObject("properties").has("playerId")).isTrue();
    }

    @Test
    void everyToolHasANonEmptyDescription() {
        assertThat(TeamTools.tools()).allSatisfy(t -> assertThat(t.description()).isNotBlank());
    }
}
