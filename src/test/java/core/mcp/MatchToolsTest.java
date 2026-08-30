package core.mcp;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Like the other tool tests: the bodies need a loaded HOModel and a database, so what is asserted
 * here is the surface the client sees.
 */
class MatchToolsTest {

    @Test
    void tools_areTheThreeMatchTools() {
        assertThat(MatchTools.tools()).extracting(McpTool::name)
                .containsExactly("get_matches", "get_match", "predict_match");
    }

    @Test
    void getMatches_takesNoRequiredArguments() {
        final var getMatches = MatchTools.tools().get(0);

        assertThat(getMatches.inputSchema().getAsJsonArray("required")).isEmpty();
        final var properties = getMatches.inputSchema().getAsJsonObject("properties");
        assertThat(properties.has("count")).isTrue();
        assertThat(properties.has("officialOnly")).isTrue();
    }

    @Test
    void getMatch_requiresOnlyTheMatchId_matchTypeIsOptional() {
        final var getMatch = MatchTools.tools().get(1);

        assertThat(getMatch.inputSchema().getAsJsonArray("required"))
                .extracting(element -> element.getAsString())
                .containsExactly("matchId");
        assertThat(getMatch.inputSchema().getAsJsonObject("properties").has("matchType")).isTrue();
    }

    @Test
    void predictMatch_requiresAllSevenOpponentSectors() {
        final var predictMatch = MatchTools.tools().get(2);

        assertThat(predictMatch.inputSchema().getAsJsonArray("required"))
                .extracting(element -> element.getAsString())
                .containsExactlyInAnyOrder("opponentMidfield", "opponentDefenceLeft",
                        "opponentDefenceCentral", "opponentDefenceRight", "opponentAttackLeft",
                        "opponentAttackCentral", "opponentAttackRight");
        // our own side comes from the current lineup, so nothing about it is an argument
        final var properties = predictMatch.inputSchema().getAsJsonObject("properties");
        assertThat(properties.has("location")).isTrue();
        assertThat(properties.has("simulations")).isTrue();
    }
}
