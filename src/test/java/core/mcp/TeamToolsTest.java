package core.mcp;

import core.file.xml.SafeInsertMap;
import core.util.AmountOfMoney;
import module.opponentspy.OppPlayerSkillEstimator;
import module.teamanalyzer.vo.PlayerInfo;
import core.net.OnlineWorker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

/**
 * The tool bodies need a loaded HOModel and a database, which the unit suite deliberately does not
 * have (there is no Swing or CHPP harness in this repo). What is testable here is the surface: the
 * names and schemas the client sees, plus {@code get_opponent_squad}'s player mapping, which is
 * pure once the players file has been parsed.
 */
class TeamToolsTest {

    @Test
    void tools_areTheReadToolsPlusTheDownload() {
        assertThat(TeamTools.tools()).extracting(McpTool::name)
                .containsExactly("get_team", "get_squad", "analyze_opponent", "get_series",
                        "get_finances", "download_team");
    }

    @Test
    void includeParts_defaultsToAllAndRejectsAnUnknownPart() {
        final var all = java.util.List.of("matches", "table", "otherUpcoming");
        assertThat(TeamTools.includeParts(new com.google.gson.JsonObject(), all))
                .containsExactlyInAnyOrderElementsOf(all);
        final var some = com.google.gson.JsonParser.parseString("{\"include\":[\"table\"]}")
                .getAsJsonObject();
        assertThat(TeamTools.includeParts(some, all)).containsExactly("table");
        final var bad = com.google.gson.JsonParser.parseString("{\"include\":[\"fixtures\"]}")
                .getAsJsonObject();
        org.assertj.core.api.Assertions.assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> TeamTools.includeParts(bad, all))
                .withMessageContaining("otherUpcoming");
    }

    @Test
    void nextCupMatchId_pointsAtTheFirstCupRowInsteadOfCopyingIt() {
        final var rows = TeamTools.ownNonLeagueUpcoming(java.util.List.of(
                upcoming(2, core.model.enums.MatchType.CUP, "2026-10-08 18:00:00", 1, 600),
                upcoming(1, core.model.enums.MatchType.FRIENDLYNORMAL, "2026-10-01 18:00:00", 500, 1)), 1);

        assertThat(TeamTools.nextCupMatchId(rows).getAsInt()).isEqualTo(2);
        assertThat(TeamTools.nextCupMatchId(new com.google.gson.JsonArray()).isJsonNull()).isTrue();
    }

    @Test
    void isOwnUpcoming_keepsOnlyOurFixturesStillToBePlayed() {
        final var now = core.util.HODateTime.fromHT("2026-10-05 12:00:00");
        final var ours = new core.model.series.Paarung();
        ours.setHeimId(600);
        ours.setGastId(1);
        ours.setDatum(core.util.HODateTime.fromHT("2026-10-10 18:00:00"));
        final var played = new core.model.series.Paarung();
        played.setHeimId(1);
        played.setGastId(700);
        played.setDatum(core.util.HODateTime.fromHT("2026-10-03 18:00:00"));
        final var theirs = new core.model.series.Paarung();
        theirs.setHeimId(600);
        theirs.setGastId(700);
        theirs.setDatum(core.util.HODateTime.fromHT("2026-10-10 18:00:00"));

        assertThat(TeamTools.isOwnUpcoming(ours, 1, now)).isTrue();
        assertThat(TeamTools.isOwnUpcoming(played, 1, now)).isFalse();
        assertThat(TeamTools.isOwnUpcoming(theirs, 1, now)).isFalse();
    }

    @Test
    void fixtureRow_carriesTheStoredForecastOnlyForOurOwnUpcomingMatch() {
        final var pairing = new core.model.series.Paarung();
        pairing.setMatchId(77);
        pairing.setHeimId(1);
        pairing.setGastId(600);
        final var own = upcoming(77, core.model.enums.MatchType.LEAGUE, "2026-10-10 18:00:00", 1, 600);

        final var ours = TeamTools.fixtureRow(pairing, own);
        assertThat(ours.get("weather").getAsString()).isEqualTo(own.getWeather().name());
        assertThat(ours.get("weatherForecast").getAsString())
                .isEqualTo(own.getWeatherForecast().name());

        final var someoneElses = TeamTools.fixtureRow(pairing, null);
        assertThat(someoneElses.has("weather")).isFalse();
        assertThat(someoneElses.get("matchId").getAsInt()).isEqualTo(77);
    }

    private static core.model.match.MatchKurzInfo upcoming(int id, core.model.enums.MatchType type,
            String date, int home, int away) {
        final var m = new core.model.match.MatchKurzInfo();
        m.setMatchID(id);
        m.setMatchType(type);
        m.setMatchSchedule(core.util.HODateTime.fromHT(date));
        m.setHomeTeamID(home);
        m.setHomeTeamName("T" + home);
        m.setGuestTeamID(away);
        m.setGuestTeamName("T" + away);
        return m;
    }

    /** The DB hands them over newest first; the tool must put the soonest first. */
    @Test
    void ownNonLeagueUpcoming_soonestFirstWithOpponentAndSide() {
        final var rows = TeamTools.ownNonLeagueUpcoming(java.util.List.of(
                upcoming(2, core.model.enums.MatchType.FRIENDLYNORMAL, "2026-10-01 18:00:00", 500, 1),
                upcoming(1, core.model.enums.MatchType.CUP, "2026-09-30 18:00:00", 1, 600)), 1);

        assertThat(rows).extracting(r -> r.getAsJsonObject().get("matchId").getAsInt())
                .containsExactly(1, 2);
        final var cup = rows.get(0).getAsJsonObject();
        assertThat(cup.get("matchType").getAsString()).isEqualTo("CUP");
        assertThat(cup.get("location").getAsString()).isEqualTo("home");
        assertThat(cup.get("opponentTeamId").getAsInt()).isEqualTo(600);
        assertThat(cup.has("cupLevel")).isTrue();
        final var friendly = rows.get(1).getAsJsonObject();
        assertThat(friendly.get("location").getAsString()).isEqualTo("away");
        assertThat(friendly.get("opponentTeamName").getAsString()).isEqualTo("T500");
        assertThat(friendly.has("cupLevel")).isFalse();
    }

    @Test
    void downloadTeam_takesOnlyAnOptionalTaskId() {
        final var download = TeamTools.tools().stream()
                .filter(t -> t.name().equals("download_team"))
                .findFirst().orElseThrow();

        assertThat(download.inputSchema().getAsJsonArray("required")).isEmpty();
        // resolvePrices is gone: the download always resolves sale prices, the way HO's own
        // Download button always has. taskId only polls.
        assertThat(download.inputSchema().getAsJsonObject("properties").keySet())
                .containsExactly("taskId");
        // the lineup clobber is the one surprise this tool has, so it has to be in the description
        assertThat(download.description()).contains("lineupReplaced");
    }

    @Test
    void theStandingsAndTheStatementTakeNoRequiredArguments() {
        final var byName = TeamTools.tools().stream()
                .filter(t -> t.name().equals("get_series") || t.name().equals("get_finances"))
                .toList();

        assertThat(byName).hasSize(2);
        assertThat(byName).allSatisfy(
                t -> assertThat(t.inputSchema().getAsJsonArray("required")).isEmpty());
        assertThat(byName.get(0).inputSchema().getAsJsonObject("properties").has("upcomingOnly"))
                .isTrue();
        // the stadium block reads back a few played home matches, and how many is tunable
        assertThat(byName.get(1).inputSchema().getAsJsonObject("properties").has("homeMatches"))
                .isTrue();
    }

    @Test
    void getSquad_filtersAndAddsTransfersOnRequest_scoutOpponentStillNeedsATeamId() {
        final var squad = TeamTools.tools().stream()
                .filter(t -> t.name().equals("get_squad")).findFirst().orElseThrow();
        assertThat(squad.inputSchema().getAsJsonArray("required")).isEmpty();
        assertThat(squad.inputSchema().getAsJsonObject("properties").keySet())
                .containsExactly("playerIds", "include", "limit", "offset", "only");

        final var scout = TeamTools.tools().stream()
                .filter(t -> t.name().equals("analyze_opponent")).findFirst().orElseThrow();
        assertThat(scout.inputSchema().getAsJsonArray("required"))
                .extracting(element -> element.getAsString())
                .containsExactly("teamId");
        assertThat(scout.inputSchema().getAsJsonObject("properties").has("include")).isTrue();
    }

    @Test
    void scoutOpponent_rejectsAnUnknownIncludeBeforeAnyRequest() {
        final var scout = TeamTools.tools().stream()
                .filter(t -> t.name().equals("analyze_opponent")).findFirst().orElseThrow();
        final var args = com.google.gson.JsonParser.parseString(
                "{\"teamId\":1,\"include\":[\"lineup\"]}").getAsJsonObject();
        org.assertj.core.api.Assertions.assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> scout.handler().call(args));
    }

    @Test
    void everyToolHasANonEmptyDescription() {
        assertThat(TeamTools.tools()).allSatisfy(t -> assertThat(t.description()).isNotBlank());
    }

    /**
     * The arena sizer used to be its own tool, and shipped a byte-identical copy of get_finances'
     * stadium block to do it. It is now the opt-in half of get_finances, so the two knobs it needs
     * have to live on that schema - and stay optional, or every statement read would size an arena.
     */
    @Test
    void getFinances_carriesTheArenaSizerKnobsAndNoneOfThemIsRequired() {
        final var tool = TeamTools.tools().stream()
                .filter(t -> t.name().equals("get_finances")).findFirst().orElseThrow();

        assertThat(tool.inputSchema().getAsJsonArray("required")).isEmpty();
        assertThat(tool.inputSchema().getAsJsonObject("properties").keySet())
                .containsExactlyInAnyOrder("homeMatches", "sizeArena", "supporters", "capacity",
                        "forecastWeeks");
    }

    @Test
    void theStadiumIsReportedByExactlyOneTool() {
        assertThat(TeamTools.tools()).extracting(McpTool::name).doesNotContain("get_stadium");
    }

    /**
     * The sizer's own maths has its own tests; what is checked here is that the plan block reports
     * the arena it was handed - stands, the delta against the current one, and the three prices.
     */
    @Test
    void plan_reportsTheStandsToBuildAndWhatTheyCost() {
        final var current = new tool.arenasizer.Stadium();
        current.setTerraces(6_000);
        current.setBasicSeating(2_350);
        current.setUnderRoofSeating(1_400);
        current.setVipBox(250);   // a 10 000 arena, exactly Hattrick's distribution

        // 500 supporters at the normal 20 spectators each: a 10 000 arena again, nothing to build
        final var normal = tool.arenasizer.ArenaSizer.calcConstructionArenas(current, 500)[1];
        final var json = TeamTools.plan("normal", normal);

        assertThat(json.get("scenario").getAsString()).isEqualTo("normal");
        assertThat(json.get("capacity").getAsInt()).isEqualTo(10_000);
        assertThat(json.get("terraces").getAsInt()).isEqualTo(6_000);
        assertThat(json.get("vipBoxes").getAsInt()).isEqualTo(250);
        assertThat(json.get("terracesToBuild").getAsInt()).isZero();
        assertThat(json.get("vipBoxesToBuild").getAsInt()).isZero();
        // nothing to build means no rebuild, so not even the fixed 100 000 is charged
        assertThat(json.get("rebuildCost").getAsLong()).isZero();
        // 6000*5 + 2350*7 + 1400*10 + 250*25 = 66 700 krona, ten to the euro
        assertThat(json.get("weeklyMaintenance").getAsLong()).isEqualTo(6_670L);
        // 6000*70 + 2350*100 + 1400*190 + 250*350 = 1 008 500 krona
        assertThat(json.get("soldOutGateReceipts").getAsLong()).isEqualTo(100_850L);
    }

    @Test
    void plan_pricesAnExpansionIncludingTheFixedRebuildFee() {
        final var current = new tool.arenasizer.Stadium();
        current.setTerraces(6_000);
        current.setBasicSeating(2_350);
        current.setUnderRoofSeating(1_400);
        current.setVipBox(250);

        // the optimistic scenario is 25 spectators per supporter, so 12 500 - less the seat each
        // sector loses to truncation, which is why the sizer never quite hits a round number
        final var optimistic = tool.arenasizer.ArenaSizer.calcConstructionArenas(current, 500)[0];
        final var json = TeamTools.plan("optimistic", optimistic);

        assertThat(json.get("capacity").getAsInt()).isEqualTo(12_499);
        assertThat(json.get("terracesToBuild").getAsInt()).isEqualTo(1_500);
        assertThat(json.get("basicSeatingToBuild").getAsInt()).isEqualTo(587);
        assertThat(json.get("underRoofSeatingToBuild").getAsInt()).isEqualTo(350);
        assertThat(json.get("vipBoxesToBuild").getAsInt()).isEqualTo(62);
        // 100 000 fixed + 1500*450 + 587*750 + 350*900 + 62*3000 = 1 716 250 krona
        assertThat(json.get("rebuildCost").getAsLong()).isEqualTo(171_625L);
    }

    // ---- get_opponent_squad, per player ------------------------------------------------------

    /** A euro league, so the wage conversion is visible: ten krona to one of the user's units. */
    @BeforeEach
    void euroLeague() {
        AmountOfMoney.Companion.setExchangeRate(BigDecimal.TEN);
    }

    /**
     * The fields the players file carries for a team that is not ours - no skills among them. Only
     * the keys {@link PlayerInfo} needs; {@code lastMatch} is added by the callers that want one,
     * exactly as XMLPlayersParser leaves it out for a player who has never played.
     */
    private static SafeInsertMap foreignPlayer() {
        final var map = new SafeInsertMap();
        map.put("PlayerID", "1234567");
        map.put("TeamID", "999");
        map.put("FirstName", "Ola");
        map.put("LastName", "Nordmann");
        map.put("Age", "27");
        map.put("Experience", "8");
        map.put("PlayerForm", "6");
        map.put("Loyalty", "12");
        map.put("MotherClubBonus", "true");
        map.put("Salary", "239000");
        map.put("Specialty", "2");
        map.put("StaminaSkill", "8");
        map.put("MarketValue", "4300");
        map.put("Cards", "0");
        map.put("InjuryLevel", "-1");
        map.put("TransferListed", "false");
        return map;
    }

    private static SafeInsertMap withLastMatch(SafeInsertMap map, String roleId) {
        map.put("LastMatch_Date", "2026-08-30 20:00:00");
        map.put("LastMatch_Rating", "6.5");
        map.put("LastMatch_id", "777888");
        map.put("LastMatch_PositionCode", roleId);
        map.put("LastMatch_PlayedMinutes", "90");
        map.put("LastMatch_RatingEndOfGame", "5.5");
        return map;
    }

    private static com.google.gson.JsonObject map(SafeInsertMap raw) {
        return TeamTools.opponentPlayer(new PlayerInfo(raw), new OppPlayerSkillEstimator());
    }

    @Test
    void opponentPlayer_carriesTheVisibleAttributesAndTheWageInTheUsersCurrency() {
        final var json = map(withLastMatch(foreignPlayer(), "100"));

        assertThat(json.get("playerId").getAsInt()).isEqualTo(1234567);
        assertThat(json.get("name").getAsString()).isEqualTo("Ola Nordmann");
        assertThat(json.get("age").getAsInt()).isEqualTo(27);
        assertThat(json.get("tsi").getAsInt()).isEqualTo(4300);
        assertThat(json.get("wage").getAsLong()).isEqualTo(23_900L);
        assertThat(json.get("form").getAsInt()).isEqualTo(6);
        assertThat(json.get("stamina").getAsInt()).isEqualTo(8);
        assertThat(json.get("motherclub").getAsBoolean()).isTrue();
        assertThat(json.get("specialty").getAsInt()).isEqualTo(2);
        assertThat(json.get("specialtyName").getAsString()).isEqualTo("Quick");
        assertThat(json.get("bookingStatus").getAsString()).isEqualTo("none");
        assertThat(json.get("available").getAsBoolean()).isTrue();
    }

    @Test
    void opponentPlayer_reportsWhereAndHowLongHeLastPlayed() {
        final var json = map(withLastMatch(foreignPlayer(), "105")).getAsJsonObject("lastMatch");

        assertThat(json.get("matchId").getAsInt()).isEqualTo(777888);
        assertThat(json.get("roleId").getAsInt()).isEqualTo(105);
        assertThat(json.get("playedMinutes").getAsInt()).isEqualTo(90);
        assertThat(json.get("rating").getAsDouble()).isEqualTo(6.5);
        assertThat(json.get("ratingEndOfMatch").getAsDouble()).isEqualTo(5.5);
        assertThat(json.get("date").getAsString()).startsWith("2026-08-30");
    }

    /**
     * The estimate is only as good as the position it is made for, and the estimator's
     * fall-through branch is "goalkeeper" - so a player with no known last position must get no
     * estimate rather than a keeper's.
     */
    @Test
    void opponentPlayer_estimatesSkillsOnlyForAKnownLastPosition() {
        final var fielded = map(withLastMatch(foreignPlayer(), "112"));

        assertThat(fielded.get("estimatedForRoleId").getAsInt()).isEqualTo(112);
        assertThat(fielded.getAsJsonObject("estimatedSkills").keySet())
                .containsExactlyInAnyOrder("keeper", "defending", "playmaking", "winger",
                        "passing", "scoring", "setPieces");

        // roleId 0: never played, or came on as a substitute - MatchRoleID has no such slot
        final var benched = map(withLastMatch(foreignPlayer(), "0"));
        assertThat(benched.has("estimatedSkills")).isFalse();
        assertThat(benched.has("estimatedForRoleId")).isFalse();
    }

    /**
     * A player who has never played has no LastMatch block at all. That used to throw out of
     * {@link PlayerInfo}'s constructor and take the whole squad download with it.
     */
    @Test
    void opponentPlayer_survivesAPlayerWhoHasNeverPlayed() {
        assertThatNoException().isThrownBy(() -> map(foreignPlayer()));

        final var json = map(foreignPlayer());

        assertThat(json.get("playerId").getAsInt()).isEqualTo(1234567);
        assertThat(json.getAsJsonObject("lastMatch").get("matchId").getAsInt()).isZero();
        assertThat(json.getAsJsonObject("lastMatch").get("date").isJsonNull()).isTrue();
        assertThat(json.has("estimatedSkills")).isFalse();
    }

    @Test
    void opponentPlayer_flagsWhoCannotPlayTheNextMatch() {
        final var suspended = foreignPlayer();
        suspended.put("Cards", "3");
        final var injured = foreignPlayer();
        injured.put("InjuryLevel", "2");
        final var bruised = foreignPlayer();
        bruised.put("Cards", "1");

        assertThat(map(suspended).get("bookingStatus").getAsString()).isEqualTo("suspended");
        assertThat(map(suspended).get("available").getAsBoolean()).isFalse();
        assertThat(map(injured).get("injuryWeeks").getAsInt()).isEqualTo(2);
        assertThat(map(injured).get("available").getAsBoolean()).isFalse();
        // a single yellow is worth reporting but does not stop him playing
        assertThat(map(bruised).get("bookingStatus").getAsString()).isEqualTo("yellow");
        assertThat(map(bruised).get("available").getAsBoolean()).isTrue();
    }
    @Test
    void downloadAdvice_coversEveryFailureReasonAndSaysWhatIsWorthRetrying() {
        // exhaustive by construction - this loop fails the moment a reason maps to nothing
        for (final var reason : OnlineWorker.Failure.values()) {
            assertThat(TeamTools.advice(reason, "detail").message())
                    .as("advice for %s", reason).isNotBlank();
        }
        assertThat(TeamTools.advice(OnlineWorker.Failure.PLAYING_MATCH, null).retryable()).isTrue();
        assertThat(TeamTools.advice(OnlineWorker.Failure.NO_DATA, null).retryable()).isTrue();
        assertThat(TeamTools.advice(OnlineWorker.Failure.CHPP_ERROR, "boom").retryable()).isFalse();
        assertThat(TeamTools.advice(OnlineWorker.Failure.UNPARSEABLE, null).retryable()).isFalse();
        assertThat(TeamTools.advice(OnlineWorker.Failure.NO_TEAM, null).retryable()).isFalse();
    }

    @Test
    void downloadAdvice_passesHattricksOwnWordingThroughForAChppError() {
        // the underlying message verbatim - it is the only thing that says what actually broke
        assertThat(TeamTools.advice(OnlineWorker.Failure.CHPP_ERROR, "Corrupt/Missing Data").message())
                .contains("Corrupt/Missing Data");
    }

    /** The live bug: 3 ids asked, 3 plus every player after the last match came back. */
    @Test
    void onlyWanted_returnsExactlyTheRequestedPlayersAndNamesTheMissing() throws Exception {
        final var squad = java.util.List.of(10, 20, 30, 40, 50);

        assertThat(TeamTools.onlyWanted(squad, java.util.Set.of(20, 30), i -> i))
                .containsExactly(20, 30);
        assertThat(TeamTools.onlyWanted(squad, java.util.Set.of(), i -> i)).isEqualTo(squad);
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> TeamTools.onlyWanted(squad, java.util.Set.of(20, 99), i -> i))
                .isInstanceOf(McpToolException.class)
                .hasMessageContaining("[99]");
    }

    @Test
    void aDownloadTaskReportsRunningThenItsRuntimeAndResult() {
        final var task = new TeamTools.DownloadTask("download-7");

        final var running = TeamTools.downloadStatus(task);
        assertThat(running.get("status").getAsString()).isEqualTo("running");
        assertThat(running.get("taskId").getAsString()).isEqualTo("download-7");
        assertThat(running.has("runtimeSeconds")).isFalse();

        final var result = new com.google.gson.JsonObject();
        result.addProperty("hrfId", 151);
        task.result = result;
        task.runtimeMs = 118_342;
        task.finishedAt = core.util.HODateTime.now();
        final var done = TeamTools.downloadStatus(task);
        assertThat(done.get("status").getAsString()).isEqualTo("done");
        assertThat(done.get("runtimeSeconds").getAsDouble()).isEqualTo(118.342);
        assertThat(done.getAsJsonObject("result").get("hrfId").getAsInt()).isEqualTo(151);

        task.error = "CHPP login expired";
        final var failed = TeamTools.downloadStatus(task);
        assertThat(failed.get("status").getAsString()).isEqualTo("failed");
        assertThat(failed.get("error").getAsString()).isEqualTo("CHPP login expired");
        assertThat(failed.has("result")).isFalse();
    }

    /** A squad-wide get_squad is the core every lineup reads; a handful of named players get it all. */
    @Test
    void squadParts_noneByDefaultAllForAFewNamedPlayers() {
        final var none = new com.google.gson.JsonObject();
        assertThat(TeamTools.squadParts(none, 0)).isEmpty();
        assertThat(TeamTools.squadParts(none, 30)).isEmpty();
        assertThat(TeamTools.squadParts(none, TeamTools.FULL_SQUAD_RECORDS))
                .containsExactlyInAnyOrderElementsOf(TeamTools.SQUAD_PARTS);

        final var args = com.google.gson.JsonParser.parseString(
                "{\"include\":[\"positionRatings\",\"character\"]}").getAsJsonObject();
        // character is in every row now; still accepted, so older callers keep working.
        assertThat(TeamTools.squadParts(args, 0)).containsExactly("positionRatings");

        final var bad = com.google.gson.JsonParser.parseString("{\"include\":[\"nope\"]}")
                .getAsJsonObject();
        org.assertj.core.api.Assertions.assertThatIllegalArgumentException()
                .isThrownBy(() -> TeamTools.squadParts(bad, 0)).withMessageContaining("positionRatings");
    }

    @Test
    void compactSquadRow_keepsTheAvailabilityCoreAndTheSkillsAsOneArray() {
        final var row = com.google.gson.JsonParser.parseString("""
                {"playerId":1,"injuryWeeks":0,"available":true,"specialty":4,"specialtyName":"x","suspended":false,"transferListed":true,
                 "skillsWithSublevel":{"keeper":1.0,"defending":3.25,"playmaking":10.97,"winger":4.0,
                   "passing":6.04,"scoring":3.0,"setPieces":3.5,"experience":2.0},
                 "skills":{},"leadership":3,"agreeability":1,"aggressiveness":4,"honesty":2,
                 "loyalty":10,"motherclub":false,"positionRatings":{},"penaltyStrength":4.26}""")
                .getAsJsonObject();

        TeamTools.compactSquadRow(row, java.util.Set.of());

        assertThat(row.keySet()).containsExactlyInAnyOrder(
                "playerId", "injuryWeeks", "available", "specialty", "skills", "character", "loyalty",
                "penaltyStrength", "transferListed");
        // In SKILL_ORDER, sub-level to one decimal.
        assertThat(row.getAsJsonArray("skills")).extracting(com.google.gson.JsonElement::getAsDouble)
                .containsExactly(1.0, 3.3, 11.0, 4.0, 6.0, 3.0, 3.5, 2.0);
        assertThat(row.getAsJsonArray("character")).extracting(com.google.gson.JsonElement::getAsInt)
                .containsExactly(3, 1, 4, 2);
        assertThat(row.get("penaltyStrength").getAsDouble()).isEqualTo(4.3);
    }

    @Test
    void ratingsRow_isThePlayerIdAndTheRatingsInSlotOrder() {
        final var ratings = new com.google.gson.JsonObject();
        for (final var roleId : McpJson.FIELD_ROLES) {
            ratings.addProperty(String.valueOf(roleId), roleId / 10.0 + 0.04);
        }
        final var player = new com.google.gson.JsonObject();
        player.addProperty("playerId", 7);
        player.addProperty("name", "x");
        player.add("positionRatings", ratings);

        final var row = TeamTools.ratingsRow(player);

        assertThat(row.keySet()).containsExactly("playerId", "r");
        assertThat(row.getAsJsonArray("r")).hasSize(McpJson.FIELD_ROLES.length)
                .first().extracting(com.google.gson.JsonElement::getAsDouble).isEqualTo(10.0);
        assertThat(row.getAsJsonArray("r").get(13).getAsDouble()).isEqualTo(11.3);
    }
}
