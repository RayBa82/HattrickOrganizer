package core.mcp;

import core.file.xml.SafeInsertMap;
import core.util.AmountOfMoney;
import module.opponentspy.OppPlayerSkillEstimator;
import module.teamanalyzer.vo.PlayerInfo;
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
    void tools_areTheSevenReadTools() {
        assertThat(TeamTools.tools()).extracting(McpTool::name)
                .containsExactly("get_team", "get_squad", "get_player", "get_opponent_squad",
                        "get_upcoming_matches", "get_league_table", "get_finances");
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
    void getOpponentSquad_requiresATeamId() {
        final var tool = TeamTools.tools().stream()
                .filter(t -> t.name().equals("get_opponent_squad")).findFirst().orElseThrow();

        assertThat(tool.inputSchema().getAsJsonArray("required"))
                .extracting(element -> element.getAsString())
                .containsExactly("teamId");
    }

    @Test
    void everyToolHasANonEmptyDescription() {
        assertThat(TeamTools.tools()).allSatisfy(t -> assertThat(t.description()).isNotBlank());
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
}
