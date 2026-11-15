package core.mcp;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import core.HOModelBuilder;
import core.model.HOModel;
import core.model.HOVerwaltung;
import core.model.enums.MatchType;
import core.model.player.IMatchRoleID;
import core.net.ChppWriteService;
import module.youth.YouthPlayer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class YouthToolsTest {

    private HOModel model;

    @BeforeEach
    void academy() {
        model = new HOModelBuilder().hrfId(42).build();
        model.getBasics().setTeamId(1000);
        model.getBasics().setYouthTeamId(2000);
        for (var id = 1; id <= 13; id++) {
            model.addYouthPlayer(youthPlayer(id, -1, 0));
        }
        model.addYouthPlayer(youthPlayer(20, 2, 0));  // injured two weeks
        model.addYouthPlayer(youthPlayer(21, -1, 3)); // suspended
        HOVerwaltung.instance().setModel(model);
    }

    private static YouthPlayer youthPlayer(int id, int injuryLevel, int cards) {
        final var p = new YouthPlayer();
        p.setId(id);
        p.setFirstName("Youth");
        p.setNickName("");
        p.setLastName("No" + id);
        p.setInjuryLevel(injuryLevel);
        p.setCards(cards);
        return p;
    }

    private static JsonObject spec(String json) {
        return JsonParser.parseString(json).getAsJsonObject();
    }

    @Test
    void sendMatchOrder_refusesAYouthMatchType() {
        final var sendTool = LineupTools.tools().stream()
                .filter(t -> t.name().equals("send_match_order")).findFirst().orElseThrow();
        final var a = spec("{\"matchId\": 1, \"matchType\": 100}");
        final var handler = sendTool.handler();
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> handler.call(a))
                .withMessageContaining("send_youth_match_order");
    }

    @Test
    void youthLineup_serialisesTheOrderByYouthIds() throws McpToolException {
        final var lineup = YouthTools.youthLineup(spec("""
                {"positions": [{"roleId": 100, "playerId": 1}, {"roleId": 101, "playerId": 2, "behaviour": 1},
                               {"roleId": 111, "playerId": 3}],
                 "bench": [{"roleId": 200, "playerId": 4}],
                 "tactic": 0, "attitude": 1, "captainId": 2, "setPiecesTakerId": 3,
                 "penaltyTakers": [3, 2]}"""), model);

        final var order = LineupTools.orderOf(lineup);

        assertThat(order.getAsJsonArray("positions")).hasSize(3);
        final var rightBack = slot(order.getAsJsonArray("positions"), IMatchRoleID.rightBack);
        assertThat(rightBack.get("playerId").getAsInt()).isEqualTo(2);
        assertThat(rightBack.get("behaviour").getAsInt()).isEqualTo(IMatchRoleID.OFFENSIVE);
        assertThat(slot(order.getAsJsonArray("bench"), IMatchRoleID.substGK1).get("playerId").getAsInt())
                .isEqualTo(4);
        assertThat(order.get("captainId").getAsInt()).isEqualTo(2);
        assertThat(order.get("setPiecesTakerId").getAsInt()).isEqualTo(3);
        assertThat(order.get("attitude").getAsInt()).isEqualTo(1);
        // CHPP wants all 11 kicker slots, padded with id 0.
        assertThat(JsonParser.parseString(lineup.toJson()).getAsJsonObject().getAsJsonArray("kickers"))
                .hasSize(11);
    }

    private static JsonObject slot(JsonArray slots, int roleId) {
        for (final var e : slots) {
            if (e.getAsJsonObject().get("roleId").getAsInt() == roleId) {
                return e.getAsJsonObject();
            }
        }
        throw new AssertionError("no slot " + roleId + " in " + slots);
    }

    @Test
    void youthLineup_refusesWhatHattrickWouldNotTake() {
        assertRefused(McpToolException.class, "no youth player with id 999",
                "{\"positions\": [{\"roleId\": 100, \"playerId\": 999}]}");
        assertRefused(IllegalArgumentException.class, "twice",
                "{\"positions\": [{\"roleId\": 100, \"playerId\": 1}, {\"roleId\": 101, \"playerId\": 1}]}");
        assertRefused(IllegalArgumentException.class, "roleId is 100-113",
                "{\"positions\": [{\"roleId\": 200, \"playerId\": 1}]}");
        assertRefused(IllegalArgumentException.class, "not in positions",
                "{\"positions\": [{\"roleId\": 100, \"playerId\": 1}], \"captainId\": 2}");
        assertRefused(IllegalArgumentException.class, "already on the pitch",
                "{\"positions\": [{\"roleId\": 100, \"playerId\": 1}], \"bench\": [{\"roleId\": 200, \"playerId\": 1}]}");
        final var twelve = new StringBuilder("{\"positions\": [");
        for (var i = 0; i < 12; i++) {
            twelve.append(i > 0 ? "," : "").append("{\"roleId\": ").append(100 + i)
                    .append(", \"playerId\": ").append(i + 1).append('}');
        }
        assertRefused(IllegalArgumentException.class, "1 to 11", twelve.append("]}").toString());
    }

    private void assertRefused(Class<? extends Exception> type, String message, String json) {
        final var order = spec(json);
        assertThatExceptionOfType(type)
                .isThrownBy(() -> YouthTools.youthLineup(order, model))
                .withMessageContaining(message);
    }

    @Test
    void youthLineup_refusesInjuredAndSuspendedUnlessAllowed() throws McpToolException {
        assertRefused(McpToolException.class, "injured",
                "{\"positions\": [{\"roleId\": 100, \"playerId\": 20}]}");
        assertRefused(McpToolException.class, "suspended",
                "{\"positions\": [{\"roleId\": 100, \"playerId\": 21}]}");
        final var lineup = YouthTools.youthLineup(spec(
                "{\"positions\": [{\"roleId\": 100, \"playerId\": 20}], \"allowUnavailable\": true}"), model);
        assertThat(java.util.Objects.requireNonNull(lineup.getPositionById(IMatchRoleID.keeper))
                .getPlayerId()).isEqualTo(20);
    }

    @Test
    void matchOrdersGoToTheYouthTeamForYouthMatches() {
        assertThat(ChppWriteService.teamIdFor(MatchType.YOUTHLEAGUE)).isEqualTo(2000);
        assertThat(ChppWriteService.teamIdFor(MatchType.YOUTHFRIENDLY)).isEqualTo(2000);
        assertThat(ChppWriteService.teamIdFor(MatchType.LEAGUE)).isEqualTo(1000);
    }
}
