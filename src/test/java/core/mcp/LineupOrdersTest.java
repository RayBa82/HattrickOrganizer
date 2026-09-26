package core.mcp;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import core.model.player.IMatchRoleID;
import module.lineup.Lineup;
import module.lineup.substitution.model.GoalDiffCriteria;
import module.lineup.substitution.model.MatchOrderType;
import module.lineup.substitution.model.RedCardCriteria;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class LineupOrdersTest {

    private static JsonObject spec(String json) {
        return JsonParser.parseString(json).getAsJsonObject();
    }

    @Test
    void absentListsAreLeftAlone_emptyListsClear() throws McpToolException {
        assertThat(LineupOrders.parsePenaltyTakers(spec("{}"), null)).isNull();
        assertThat(LineupOrders.parseTeamOrders(spec("{}"), null, 0)).isNull();
        assertThat(LineupOrders.parsePenaltyTakers(spec("{\"penaltyTakers\":[]}"), null)).isEmpty();
        assertThat(LineupOrders.parseTeamOrders(spec("{\"teamOrders\":[]}"), null, 0)).isEmpty();
    }

    @Test
    void penaltyTakers_inKickingOrder_atMostElevenAndNoneTwice() throws McpToolException {
        final var takers = LineupOrders.parsePenaltyTakers(spec("{\"penaltyTakers\":[7,3]}"), null);
        assertThat(takers).extracting(t -> t.getRoleId(), t -> t.getPlayerId())
                .containsExactly(org.assertj.core.groups.Tuple.tuple(IMatchRoleID.penaltyTaker1, 7),
                        org.assertj.core.groups.Tuple.tuple(IMatchRoleID.penaltyTaker1 + 1, 3));

        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> LineupOrders.parsePenaltyTakers(
                        spec("{\"penaltyTakers\":[1,2,3,4,5,6,7,8,9,10,11,12]}"), null))
                .withMessageContaining("at most 11");
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> LineupOrders.parsePenaltyTakers(spec("{\"penaltyTakers\":[4,4]}"), null))
                .withMessageContaining("twice");
    }

    @Test
    void teamOrders_readBackAsTheyWereWritten() throws McpToolException {
        final var orders = LineupOrders.parseTeamOrders(spec("""
                {"teamOrders":[
                  {"type":"substitution","minute":60,"playerOut":1,"playerIn":2,"roleId":111,
                   "behaviour":1,"redCard":"myPlayer","standing":"down"},
                  {"type":"behaviour","minute":70,"playerOut":3,"behaviour":2},
                  {"type":"swap","minute":80,"playerOut":4,"playerIn":5},
                  {"type":"manMarking","minute":0,"playerOut":6,"playerIn":99999}]}"""), null, 0);

        assertThat(orders).extracting(o -> o.getOrderType()).containsExactly(MatchOrderType.SUBSTITUTION,
                MatchOrderType.NEW_BEHAVIOUR, MatchOrderType.POSITION_SWAP, MatchOrderType.MAN_MARKING);
        assertThat(orders.get(0).getRedCardCriteria()).isEqualTo(RedCardCriteria.MY_PLAYER);
        assertThat(orders.get(0).getStanding()).isEqualTo(GoalDiffCriteria.DOWN);
        assertThat(orders.get(1).getObjectPlayerID()).isEqualTo(3);   // behaviour: same player in and out

        final var json = LineupOrders.teamOrders(orders);
        final var first = json.get(0).getAsJsonObject();
        assertThat(first.get("type").getAsString()).isEqualTo("substitution");
        assertThat(first.get("minute").getAsInt()).isEqualTo(60);
        assertThat(first.get("playerOut").getAsInt()).isEqualTo(1);
        assertThat(first.get("playerIn").getAsInt()).isEqualTo(2);
        assertThat(first.get("roleId").getAsInt()).isEqualTo(111);
        assertThat(first.get("behaviour").getAsInt()).isEqualTo(1);
        assertThat(first.get("redCard").getAsString()).isEqualTo("myPlayer");
        assertThat(first.get("standing").getAsString()).isEqualTo("down");
        final var second = json.get(1).getAsJsonObject();
        assertThat(second.get("type").getAsString()).isEqualTo("behaviour");
        assertThat(second.get("roleId").getAsInt()).isEqualTo(-1);
        assertThat(second.get("redCard").getAsString()).isEqualTo("ignore");
        assertThat(second.get("standing").getAsString()).isEqualTo("anyStanding");
    }

    @Test
    void teamOrders_refuseAFourthSubstitution() {
        final var four = spec("""
                {"teamOrders":[
                  {"type":"substitution","minute":46,"playerOut":1,"playerIn":2},
                  {"type":"substitution","minute":60,"playerOut":3,"playerIn":4},
                  {"type":"substitution","minute":70,"playerOut":5,"playerIn":6},
                  {"type":"substitution","minute":80,"playerOut":7,"playerIn":8}]}""");
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> LineupOrders.parseTeamOrders(four, null, 5))
                .withMessageContaining("teamOrders[3]")
                .withMessageContaining("3 of them substitutions");
    }

    @Test
    void teamOrders_refuseWhatHattrickDoesNotKnow() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> LineupOrders.parseTeamOrders(spec("""
                        {"teamOrders":[{"type":"sub","minute":46,"playerOut":1,"playerIn":2}]}"""), null, 0))
                .withMessageContaining("substitution, behaviour, swap or manMarking");
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> LineupOrders.parseTeamOrders(spec("""
                        {"teamOrders":[{"type":"swap","minute":46,"playerOut":1,"playerIn":2,
                                        "standing":"winning"}]}"""), null, 0))
                .withMessageContaining("inTheLead");
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> LineupOrders.parseTeamOrders(spec("""
                        {"teamOrders":[{"type":"swap","minute":120,"playerOut":1,"playerIn":2}]}"""), null, 0))
                .withMessageContaining("0-119");
    }

    @Test
    void criteriaNames_roundTripAndHideTheEnumsTypo() {
        assertThat(LineupOrders.name(RedCardCriteria.OPPONENT_FORAWARD)).isEqualTo("opponentForward");
        assertThat(LineupOrders.name(GoalDiffCriteria.IN_THE_LEAD_BY_MORE_THAN_ONE)).isEqualTo("inTheLeadByMoreThanOne");
    }

    /** The echo is read back from the very string toJson builds, padding included. */
    @Test
    void echo_isWhatTheOrderStringCarries() throws McpToolException {
        final var lineup = new Lineup();
        lineup.setSpielerAtPosition(IMatchRoleID.keeper, 11);
        lineup.setSpielerAtPosition(IMatchRoleID.leftForward, 22, (byte) 1);
        lineup.setSpielerAtPosition(IMatchRoleID.substFW1, 33);
        lineup.setCaptain(22);
        lineup.setKicker(22);
        lineup.setPenaltyTakers(LineupOrders.parsePenaltyTakers(spec("{\"penaltyTakers\":[22,11]}"), null));
        lineup.setSubstitionList(LineupOrders.parseTeamOrders(spec("""
                {"teamOrders":[
                  {"type":"substitution","minute":65,"playerOut":22,"playerIn":33,"standing":"inTheLead"},
                  {"type":"behaviour","minute":80,"playerOut":11,"behaviour":1}]}"""), null, 0));

        final var benchRoleIds = new java.util.ArrayList<Integer>();
        for (var roleId = IMatchRoleID.startReserves; roleId <= IMatchRoleID.substXT2; roleId++) {
            if (lineup.getPositionById(roleId) != null) {
                benchRoleIds.add(roleId);
            }
        }
        final var echo = LineupOrders.echo(lineup.toJson(), benchRoleIds);

        assertThat(echo.getAsJsonArray("positions").toString())
                .isEqualTo("[{\"roleId\":100,\"playerId\":11,\"behaviour\":0},"
                        + "{\"roleId\":113,\"playerId\":22,\"behaviour\":1}]");
        assertThat(echo.getAsJsonArray("bench").toString()).isEqualTo("[{\"roleId\":204,\"playerId\":33}]");
        assertThat(echo.get("captainId").getAsInt()).isEqualTo(22);
        assertThat(echo.get("setPiecesTakerId").getAsInt()).isEqualTo(22);
        assertThat(echo.getAsJsonArray("penaltyTakers").toString()).isEqualTo("[22,11]");
        assertThat(echo.get("penaltySlotsSent").getAsInt()).isEqualTo(11);
        final var orders = echo.getAsJsonArray("teamOrders");
        assertThat(orders.get(0).getAsJsonObject().get("type").getAsString()).isEqualTo("substitution");
        assertThat(orders.get(0).getAsJsonObject().get("standing").getAsString()).isEqualTo("inTheLead");
        assertThat(orders.get(1).getAsJsonObject().get("type").getAsString()).isEqualTo("behaviour");
        assertThat(orders.get(1).getAsJsonObject().get("behaviour").getAsInt()).isEqualTo(1);
    }

    @Test
    void anEmptyPenaltyListStillSendsElevenSlots() {
        final var lineup = new Lineup();
        final var sent = JsonParser.parseString(lineup.toJson()).getAsJsonObject();
        assertThat(sent.getAsJsonArray("kickers")).hasSize(11);
        // the padding keeps the penalty slots' role ids, never 0
        assertThat(lineup.getPenaltyTakers()).extracting(p -> p.getRoleId())
                .containsExactlyElementsOf(java.util.stream.IntStream.rangeClosed(
                        IMatchRoleID.penaltyTaker1, IMatchRoleID.penaltyTaker11).boxed().toList());
    }

    /** The largest lists Hattrick allows stay far inside the 10,000-char tool budget (AGENTS.md). */
    @Test
    void theLargestOrderListFitsTheToolBudget() throws McpToolException {
        final var orders = new StringBuilder("{\"teamOrders\":[");
        for (var i = 0; i < 10; i++) {
            orders.append(i > 0 ? "," : "").append("{\"type\":\"swap\",\"minute\":").append(i + 10)
                    .append(",\"playerOut\":").append(400000000 + i).append(",\"playerIn\":")
                    .append(500000000 + i).append(",\"redCard\":\"opponentCentralDefender\","
                            + "\"standing\":\"notInTheLeadByMoreThanOne\"}");
        }
        orders.append("]}");
        final var json = LineupOrders.teamOrders(LineupOrders.parseTeamOrders(spec(orders.toString()), null, 5));
        assertThat(json.toString().length()).isLessThan(2_500);
        assertThat(json).hasSize(10);
    }
}
