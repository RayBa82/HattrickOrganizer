package core.mcp;

import com.google.gson.JsonObject;
import core.file.xml.XMLChallengesParser;
import core.model.enums.MatchType;
import core.model.match.Matchdetails;
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
                .containsExactly("get_matches", "get_match", "get_opponent_ratings",
                        "predict_match", "simulate_lineup",
                        "list_challenges", "arrange_challenge", "respond_to_challenge");
    }

    @Test
    void getMatches_takesNoRequiredArguments() {
        final var getMatches = tool("get_matches");

        assertThat(getMatches.inputSchema().getAsJsonArray("required")).isEmpty();
        final var properties = getMatches.inputSchema().getAsJsonObject("properties");
        assertThat(properties.has("count")).isTrue();
        assertThat(properties.has("officialOnly")).isTrue();
    }

    @Test
    void getMatch_requiresOnlyTheMatchId_matchTypeIsOptional() {
        final var getMatch = tool("get_match");

        assertThat(getMatch.inputSchema().getAsJsonArray("required"))
                .extracting(element -> element.getAsString())
                .containsExactly("matchId");
        assertThat(getMatch.inputSchema().getAsJsonObject("properties").has("matchType")).isTrue();
    }

    @Test
    void predictMatch_requiresAllSevenOpponentSectors() {
        final var predictMatch = tool("predict_match");

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
        final var simulateLineup = tool("simulate_lineup");

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

    // ---- get_opponent_ratings ----------------------------------------------------------------
    //
    // The downloading is left to the handler; what is worth pinning down is the report over the
    // matches it fetched, since averaging the wrong end of a fixture would hand predict_match the
    // ratings of the scouted team's opponents.

    private static final int TARGET = 100;

    /** Newest first, the way {@link MatchTools#opponentReport} is given them. */
    private static List<Matchdetails> twoMatchesOfTargetTeam() {
        // Newest: the scouted team at home, midfield 12, on a tactic at level 7.
        final var atHome = new Matchdetails();
        atHome.setMatchID(1);
        atHome.setMatchType(MatchType.LEAGUE);
        atHome.setHeimId(TARGET);
        atHome.setHeimName("Target FC");
        atHome.setGastId(200);
        atHome.setGastName("Their Visitors");
        atHome.setHomeMidfield(12);
        atHome.setHomeLeftDef(10);
        atHome.setHomeMidDef(10);
        atHome.setHomeRightDef(10);
        atHome.setHomeLeftAtt(8);
        atHome.setHomeMidAtt(8);
        atHome.setHomeRightAtt(8);
        atHome.setHomeTacticType(3);
        atHome.setHomeTacticSkill(7);
        // The other side of that same match, which must not leak into the report.
        atHome.setGuestMidfield(2);
        atHome.setGuestLeftDef(2);
        atHome.setGuestMidDef(2);
        atHome.setGuestRightDef(2);
        atHome.setGuestLeftAtt(2);
        atHome.setGuestMidAtt(2);
        atHome.setGuestRightAtt(2);
        atHome.setGuestTacticType(9);
        atHome.setGuestTacticSkill(1);

        // Older: the same team away, midfield 8 - so the average midfield is 10.
        final var away = new Matchdetails();
        away.setMatchID(2);
        away.setMatchType(MatchType.CUP);
        away.setHeimId(300);
        away.setHeimName("Someone Else");
        away.setGastId(TARGET);
        away.setGastName("Target FC");
        away.setGuestMidfield(8);
        away.setGuestLeftDef(6);
        away.setGuestMidDef(6);
        away.setGuestRightDef(6);
        away.setGuestLeftAtt(4);
        away.setGuestMidAtt(4);
        away.setGuestRightAtt(4);
        away.setHomeMidfield(20);
        away.setHomeLeftDef(20);
        away.setHomeMidDef(20);
        away.setHomeRightDef(20);
        away.setHomeLeftAtt(20);
        away.setHomeMidAtt(20);
        away.setHomeRightAtt(20);

        return List.of(atHome, away);
    }

    @Test
    void opponentReport_averagesTheScoutedTeamsOwnSectorsWhicheverEndItPlayedAt() {
        final var report = MatchTools.opponentReport(TARGET, twoMatchesOfTargetTeam());

        assertThat(report.get("teamId").getAsInt()).isEqualTo(TARGET);
        assertThat(report.get("teamName").getAsString()).isEqualTo("Target FC");
        assertThat(report.get("matchesUsed").getAsInt()).isEqualTo(2);

        final var ratings = report.getAsJsonObject("opponentRatings");
        assertThat(ratings.get("opponentName").getAsString()).isEqualTo("Target FC");
        assertThat(ratings.get("opponentMidfield").getAsDouble()).isEqualTo(10.0);
        assertThat(ratings.get("opponentDefenceLeft").getAsDouble()).isEqualTo(8.0);
        assertThat(ratings.get("opponentDefenceCentral").getAsDouble()).isEqualTo(8.0);
        assertThat(ratings.get("opponentDefenceRight").getAsDouble()).isEqualTo(8.0);
        assertThat(ratings.get("opponentAttackLeft").getAsDouble()).isEqualTo(6.0);
        assertThat(ratings.get("opponentAttackCentral").getAsDouble()).isEqualTo(6.0);
        assertThat(ratings.get("opponentAttackRight").getAsDouble()).isEqualTo(6.0);
    }

    @Test
    void opponentReport_takesTheTacticFromTheMostRecentMatchNotAnAverage() {
        final var ratings = MatchTools.opponentReport(TARGET, twoMatchesOfTargetTeam())
                .getAsJsonObject("opponentRatings");

        assertThat(ratings.get("opponentTacticType").getAsInt()).isEqualTo(3);
        assertThat(ratings.get("opponentTacticSkill").getAsInt()).isEqualTo(7);
    }

    @Test
    void opponentReport_namesTheRealOpponentAndTheLocationOfEachMatch() {
        final var matches = MatchTools.opponentReport(TARGET, twoMatchesOfTargetTeam())
                .getAsJsonArray("matches");

        assertThat(matches).hasSize(2);
        final var newest = matches.get(0).getAsJsonObject();
        assertThat(newest.get("location").getAsString()).isEqualTo("home");
        assertThat(newest.get("opponentTeamId").getAsInt()).isEqualTo(200);
        assertThat(newest.get("matchType").getAsString()).isEqualTo("LEAGUE");
        assertThat(newest.get("midfield").getAsInt()).isEqualTo(12);

        final var older = matches.get(1).getAsJsonObject();
        assertThat(older.get("location").getAsString()).isEqualTo("away");
        assertThat(older.get("opponentTeamId").getAsInt()).isEqualTo(300);
        assertThat(older.get("opponentTeamName").getAsString()).isEqualTo("Someone Else");
        assertThat(older.get("midfield").getAsInt()).isEqualTo(8);
    }

    /**
     * The whole point of the tool: opponentRatings is copyable into predict_match. A rename on
     * either side would leave a required argument missing, so assert against the live schema.
     */
    @Test
    void opponentReport_fillsEveryArgumentPredictMatchRequires() {
        final var ratings = MatchTools.opponentReport(TARGET, twoMatchesOfTargetTeam())
                .getAsJsonObject("opponentRatings");

        final var required = tool("predict_match").inputSchema().getAsJsonArray("required");
        assertThat(required).isNotEmpty();
        for (final var name : required) {
            assertThat(ratings.has(name.getAsString()))
                    .as("opponentRatings is missing %s", name.getAsString()).isTrue();
        }
    }
}
