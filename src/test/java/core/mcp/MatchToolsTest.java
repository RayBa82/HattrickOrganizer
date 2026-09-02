package core.mcp;

import com.google.gson.JsonObject;
import core.file.xml.XMLChallengesParser;
import core.net.ChppWriteService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * Like the other tool tests: the bodies need a loaded HOModel and a database, so what is asserted
 * here is the surface the client sees. The challenge tool bodies are exercised through a stubbed
 * {@link ChppWriteService}.
 */
class MatchToolsTest {

    private ChppWriteService original;

    @AfterEach
    void restore() {
        if (original != null) ChppWriteService.setInstanceForTest(original);
    }

    private void install(ChppWriteService stub) {
        original = ChppWriteService.instance();
        ChppWriteService.setInstanceForTest(stub);
    }

    private static McpTool tool(String name) {
        return MatchTools.tools().stream().filter(t -> t.name().equals(name)).findFirst().orElseThrow();
    }

    @Test
    void tools_areTheMatchTools() {
        assertThat(MatchTools.tools()).extracting(McpTool::name)
                .containsExactly("get_matches", "get_match", "predict_match", "simulate_lineup",
                        "list_challenges", "arrange_challenge", "respond_to_challenge");
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
        assertThat(properties.has("positions")).isFalse();
    }

    @Test
    void simulateLineup_addsAnOptionalLineupToPredictMatchsArguments() {
        final var simulateLineup = MatchTools.tools().get(3);

        assertThat(simulateLineup.name()).isEqualTo("simulate_lineup");
        // same opponent side as predict_match, and only that side is required
        assertThat(simulateLineup.inputSchema().getAsJsonArray("required"))
                .extracting(element -> element.getAsString())
                .containsExactlyInAnyOrder("opponentMidfield", "opponentDefenceLeft",
                        "opponentDefenceCentral", "opponentDefenceRight", "opponentAttackLeft",
                        "opponentAttackCentral", "opponentAttackRight");
        // plus an optional hypothetical lineup, the evaluate_lineup way
        final var properties = simulateLineup.inputSchema().getAsJsonObject("properties");
        assertThat(properties.has("positions")).isTrue();
        assertThat(properties.has("tactic")).isTrue();
        assertThat(properties.has("simulations")).isTrue();
    }

    @Test
    void arrangeChallenge_mapsLocationToMatchPlace() throws Exception {
        final int[] seenMatchPlace = {-1};
        install(new ChppWriteService() {
            @Override
            public void arrangeChallengeForAgent(int opp, int type, int place, Integer arena, boolean wk) {
                seenMatchPlace[0] = place;
            }
        });
        final var a = new JsonObject();
        a.addProperty("opponentTeamId", 999);
        a.addProperty("location", "away");
        tool("arrange_challenge").handler().call(a);
        assertThat(seenMatchPlace[0]).isEqualTo(1);
    }

    @Test
    void arrangeChallenge_neutralWithoutArenaIsRejected() {
        install(new ChppWriteService() {
            @Override
            public void arrangeChallengeForAgent(int opp, int type, int place, Integer arena, boolean wk) {
                throw new AssertionError("must not be called");
            }
        });
        final var a = new JsonObject();
        a.addProperty("opponentTeamId", 999);
        a.addProperty("location", "neutral");
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> tool("arrange_challenge").handler().call(a));
    }

    @Test
    void respondToChallenge_passesTheServiceErrorThrough() {
        install(new ChppWriteService() {
            @Override
            public void respondToChallengeForAgent(String verb, long id) throws ChppWriteException {
                throw new ChppWriteException(null, "Hattrick rejected the challenge accept: gone");
            }
        });
        final var a = new JsonObject();
        a.addProperty("trainingMatchId", 733551221L);
        a.addProperty("action", "accept");
        assertThatExceptionOfType(McpToolException.class)
                .isThrownBy(() -> tool("respond_to_challenge").handler().call(a))
                .withMessageContaining("gone");
    }

    @Test
    void listChallenges_shapesBothLists() throws Exception {
        install(new ChppWriteService() {
            @Override
            public XMLChallengesParser.ChallengeLists listChallengesForAgent(boolean weekend) {
                return new XMLChallengesParser.ChallengeLists(
                        List.of(new XMLChallengesParser.Challenge(
                                1L, 0L, "2026-09-06 15:00:00", 1, 10, "A", "AA", false)),
                        List.of());
            }
        });
        final var result = tool("list_challenges").handler().call(new JsonObject());
        assertThat(result.getAsJsonArray("outgoing")).hasSize(1);
        assertThat(result.getAsJsonArray("incoming")).isEmpty();
        assertThat(result.getAsJsonArray("outgoing").get(0).getAsJsonObject()
                .get("trainingMatchId").getAsLong()).isEqualTo(1L);
    }
}
