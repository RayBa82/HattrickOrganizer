package core.mcp;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
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
                        "get_rating_calibration", "simulate_lineup",
                        "list_challenges", "check_challengeable", "arrange_challenge",
                        "respond_to_challenge");
    }

    // ---- check_challengeable -------------------------------------------------------------------

    private static final java.time.LocalDate TODAY = java.time.LocalDate.of(2026, 9, 26);

    @Test
    void challengeableRow_activeHumanManagerIsOk() {
        final var row = MatchTools.challengeableRow(10,
                new XMLChallengesParser.Challengeable(10, "Active FC", 77, true),
                java.util.Map.of("LastLoginDate", "2026-09-25 21:10:00", "HasSupporter", "True",
                        "IsBot", "False", "LeagueLevelUnitName", "V.12"),
                TODAY);
        assertThat(row.get("challengeable").getAsBoolean()).isTrue();
        assertThat(row.get("daysSinceLogin").getAsInt()).isEqualTo(1);
        assertThat(row.get("managerIsSupporter").getAsBoolean()).isTrue();
        assertThat(row.get("isBot").getAsBoolean()).isFalse();
        assertThat(row.get("series").getAsString()).isEqualTo("V.12");
        assertThat(row.get("note").getAsString()).isEqualTo("ok");
    }

    @Test
    void challengeableRow_flagsDormantAndBotTeams() {
        final var dormant = MatchTools.challengeableRow(11,
                new XMLChallengesParser.Challengeable(11, "Sleepy FC", 78, true),
                java.util.Map.of("LastLoginDate", "2026-08-01 10:00:00", "IsBot", "False"), TODAY);
        assertThat(dormant.get("note").getAsString()).contains("inactive for 56 days");

        final var bot = MatchTools.challengeableRow(12,
                new XMLChallengesParser.Challengeable(12, "Bot FC", 0, true),
                java.util.Map.of("IsBot", "True"), TODAY);
        assertThat(bot.get("note").getAsString()).contains("bot");
    }

    /** Hattrick leaving a team out, or teamdetails failing, is "unknown", not a crash. */
    @Test
    void challengeableRow_missingAnswerAndDetailsAreNulls() {
        final var row = MatchTools.challengeableRow(13, null, java.util.Map.of(), TODAY);
        assertThat(row.get("challengeable").isJsonNull()).isTrue();
        assertThat(row.get("daysSinceLogin").isJsonNull()).isTrue();
        assertThat(row.get("managerIsSupporter").isJsonNull()).isTrue();
        assertThat(row.get("note").getAsString()).contains("did not answer");
    }

    @Test
    void checkChallengeable_putsChallengeableAndRecentlyActiveTeamsFirst() throws Exception {
        install(new ChppWriteService() {
            @Override
            public List<XMLChallengesParser.Challengeable> checkChallengeableForAgent(
                    List<Integer> ids, boolean weekend) {
                return List.of(new XMLChallengesParser.Challengeable(1, "No", 0, false),
                        new XMLChallengesParser.Challengeable(2, "Old", 0, true),
                        new XMLChallengesParser.Challengeable(3, "Fresh", 0, true));
            }

            @Override
            public java.util.Map<String, String> teamActivityForAgent(int teamId) {
                return java.util.Map.of("LastLoginDate",
                        teamId == 2 ? "2020-01-01 00:00:00" : "2099-01-01 00:00:00");
            }
        });
        final var args = JsonParser.parseString("{\"teamIds\":[1,2,3]}").getAsJsonObject();
        final var teams = tool("check_challengeable").handler().call(args).getAsJsonArray("teams");
        assertThat(teams).extracting(t -> t.getAsJsonObject().get("teamId").getAsInt())
                .containsExactly(3, 2, 1);
    }

    @Test
    void checkChallengeable_rejectsAnEmptyOrOversizedList() {
        final var none = JsonParser.parseString("{\"teamIds\":[]}").getAsJsonObject();
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> tool("check_challengeable").handler().call(none));

        final var many = new com.google.gson.JsonArray();
        for (int i = 1; i <= MatchTools.MAX_CHALLENGEABLE_TEAMS + 1; i++) many.add(i);
        final var tooMany = new JsonObject();
        tooMany.add("teamIds", many);
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> tool("check_challengeable").handler().call(tooMany));
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

    /**
     * simulate_lineup absorbed evaluate_lineup and predict_match, whose schemas were both strict
     * subsets of its own. Nothing is required any more: the lineup half and the opponent half are
     * each optional, which is what makes the three old calls one tool.
     */
    @Test
    void simulateLineup_requiresNothingAndCarriesBothHalves() {
        final var simulateLineup = tool("simulate_lineup");

        assertThat(simulateLineup.inputSchema().getAsJsonArray("required")).isEmpty();
        final var properties = simulateLineup.inputSchema().getAsJsonObject("properties");
        // the lineup half, as evaluate_lineup took it
        assertThat(properties.has("positions")).isTrue();
        assertThat(properties.has("tactic")).isTrue();
        assertThat(properties.has("allowUnavailable")).isTrue();
        // the opponent half, as predict_match took it
        assertThat(properties.keySet()).contains("opponentMidfield", "opponentDefenceLeft",
                "opponentDefenceCentral", "opponentDefenceRight", "opponentAttackLeft",
                "opponentAttackCentral", "opponentAttackRight");
        assertThat(properties.has("location")).isTrue();
        assertThat(properties.has("simulations")).isTrue();
    }

    /**
     * Six sectors out of seven is a caller who meant to simulate and dropped one. Answering that
     * with sector ratings would look like a working call, so it has to be refused by name.
     */
    @Test
    void simulateLineup_refusesAPartialOpponent() {
        final var args = JsonParser.parseString("""
                {"opponentMidfield":50,"opponentDefenceLeft":40,"opponentDefenceCentral":45,
                 "opponentDefenceRight":40,"opponentAttackLeft":30,"opponentAttackCentral":35}
                """).getAsJsonObject();

        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> tool("simulate_lineup").handler().call(args))
                .withMessageContaining("opponentAttackRight");
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

        // Raw match-report ints (12, 10, 8, ...), decoded to RatingPredictionModel's own scale
        // via (raw - 1) / 4.0 + 1 before averaging - see MatchTools.sectorRating.
        final var ratings = report.getAsJsonObject("opponentRatings");
        assertThat(ratings.get("opponentName").getAsString()).isEqualTo("Target FC");
        assertThat(ratings.get("opponentMidfield").getAsDouble()).isEqualTo(3.25);
        assertThat(ratings.get("opponentDefenceLeft").getAsDouble()).isEqualTo(2.75);
        assertThat(ratings.get("opponentDefenceCentral").getAsDouble()).isEqualTo(2.75);
        assertThat(ratings.get("opponentDefenceRight").getAsDouble()).isEqualTo(2.75);
        assertThat(ratings.get("opponentAttackLeft").getAsDouble()).isEqualTo(2.25);
        assertThat(ratings.get("opponentAttackCentral").getAsDouble()).isEqualTo(2.25);
        assertThat(ratings.get("opponentAttackRight").getAsDouble()).isEqualTo(2.25);
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
        // Decoded from the raw 12, see MatchTools.sectorRating.
        assertThat(newest.get("midfield").getAsDouble()).isEqualTo(3.75);

        final var older = matches.get(1).getAsJsonObject();
        assertThat(older.get("location").getAsString()).isEqualTo("away");
        assertThat(older.get("opponentTeamId").getAsInt()).isEqualTo(300);
        assertThat(older.get("opponentTeamName").getAsString()).isEqualTo("Someone Else");
        // Decoded from the raw 8, see MatchTools.sectorRating.
        assertThat(older.get("midfield").getAsDouble()).isEqualTo(2.75);
    }

    /**
     * The whole point of the tool: opponentRatings is copyable straight into simulate_lineup. Those
     * seven arguments are no longer in the schema's required array - the opponent half is optional
     * now - so assert against the properties, which is where the names actually live. A rename on
     * either side would silently turn a simulation into a ratings-only call.
     */
    @Test
    void opponentReport_fillsEverySectorSimulateLineupTakes() {
        final var ratings = MatchTools.opponentReport(TARGET, twoMatchesOfTargetTeam())
                .getAsJsonObject("opponentRatings");

        final var sectors = tool("simulate_lineup").inputSchema()
                .getAsJsonObject("properties").keySet().stream()
                .filter(name -> name.startsWith("opponent")
                        && !name.equals("opponentName")
                        && !name.startsWith("opponentTactic"))
                .toList();
        assertThat(sectors).hasSize(7);
        for (final var name : sectors) {
            assertThat(ratings.has(name)).as("opponentRatings is missing %s", name).isTrue();
        }
    }
}
