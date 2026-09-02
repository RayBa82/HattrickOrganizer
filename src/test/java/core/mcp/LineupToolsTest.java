package core.mcp;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import core.model.player.IMatchRoleID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class LineupToolsTest {

    @Test
    void tools_areTheLineupTools() {
        assertThat(LineupTools.tools()).extracting(McpTool::name)
                .containsExactly("get_lineup", "evaluate_lineup", "optimize_lineup", "apply_lineup",
                        "set_player_group", "send_match_order");
    }

    @Test
    void sendMatchOrder_rejectsAnUnknownMatchType() {
        final var sendTool = LineupTools.tools().stream()
                .filter(t -> t.name().equals("send_match_order")).findFirst().orElseThrow();
        final var a = new JsonObject();
        a.addProperty("matchId", 12345);
        a.addProperty("matchType", 999);   // not a MatchType id
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> sendTool.handler().call(a))
                .withMessageContaining("get_matches");
    }

    @Test
    void scratchLineup_placesPlayersAtTheGivenRolesAndSetsTheOrders() throws McpToolException {
        final var spec = JsonParser.parseString("""
                {"positions":[{"roleId":100,"playerId":11},{"roleId":113,"playerId":22,"behaviour":1}],
                 "tactic":3,"attitude":1,"captainId":11,"setPiecesTakerId":22}""").getAsJsonObject();

        final var lineup = LineupTools.scratchLineup(spec);

        assertThat(lineup.getPositionById(IMatchRoleID.keeper).getPlayerId()).isEqualTo(11);
        assertThat(lineup.getPositionById(IMatchRoleID.leftForward).getPlayerId()).isEqualTo(22);
        assertThat((int) lineup.getPositionById(IMatchRoleID.leftForward).getBehaviour()).isEqualTo(1);
        assertThat(lineup.getTacticType()).isEqualTo(3);
        assertThat(lineup.getAttitude()).isEqualTo(1);
        assertThat(lineup.getCaptain()).isEqualTo(11);
        assertThat(lineup.getKicker()).isEqualTo(22);
    }

    /**
     * The regression guard for spec section 3: a scratch lineup and the live lineup both sit at
     * rating revision 0 in a fresh fixture, which is exactly the case where a shared
     * LineupRatingCache would confuse them. Two scratch lineups must stay distinguishable.
     */
    @Test
    void scratchLineupsAreIndependentInstances() throws McpToolException {
        final var a = LineupTools.scratchLineup(JsonParser.parseString("""
                {"positions":[{"roleId":100,"playerId":11}]}""").getAsJsonObject());
        final var b = LineupTools.scratchLineup(JsonParser.parseString("""
                {"positions":[{"roleId":100,"playerId":22}]}""").getAsJsonObject());

        assertThat(a).isNotSameAs(b);
        assertThat(a.getPositionById(IMatchRoleID.keeper).getPlayerId()).isEqualTo(11);
        assertThat(b.getPositionById(IMatchRoleID.keeper).getPlayerId()).isEqualTo(22);
    }
}
