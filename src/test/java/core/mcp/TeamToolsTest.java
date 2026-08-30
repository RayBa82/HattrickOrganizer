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
    void tools_areTheFourReadTools() {
        assertThat(TeamTools.tools()).extracting(McpTool::name)
                .containsExactly("get_team", "get_squad", "get_player", "get_upcoming_matches");
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
