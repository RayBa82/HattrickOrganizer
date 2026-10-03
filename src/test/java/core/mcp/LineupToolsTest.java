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
                .containsExactly("get_lineup", "apply_lineup",
                        "set_player_group", "send_match_order");
    }

    @Test
    void setPlayerGroup_acceptsEitherOneGroupOrMixedAssignments() {
        final var setPlayerGroup = LineupTools.tools().stream()
                .filter(t -> t.name().equals("set_player_group")).findFirst().orElseThrow();

        assertThat(setPlayerGroup.inputSchema().getAsJsonArray("required")).isEmpty();
        final var properties = setPlayerGroup.inputSchema().getAsJsonObject("properties");
        assertThat(properties.keySet()).contains("playerIds", "group", "assignments");
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
    void requireFieldPlayers_refusesALineupWithoutFieldPlayers() throws McpToolException {
        final var empty = new module.lineup.Lineup();
        assertThatExceptionOfType(McpToolException.class)
                .isThrownBy(() -> LineupTools.requireFieldPlayers(empty))
                .withMessageContaining("Empty lineup, nothing sent")
                .withMessageContaining("apply_lineup");

        final var keeperOnly = new module.lineup.Lineup();
        keeperOnly.setSpielerAtPosition(IMatchRoleID.keeper, 11);
        LineupTools.requireFieldPlayers(keeperOnly);   // no throw
    }

    @Test
    void matchMismatch_warnsOnlyWhenTheLineupBelongsElsewhere() {
        assertThat(LineupTools.matchMismatch(100, 100)).isNull();
        assertThat(LineupTools.matchMismatch(100, 200))
                .contains("match 100").contains("not 200").contains("lineupMatchId");
        assertThat(LineupTools.matchMismatch(-1, 200)).contains("no match");
    }

    @Test
    void lineupMatch_namesTheLineupsMatchAndTheNextOne() {
        final var team = new core.model.match.MatchLineupTeam(
                core.model.enums.MatchType.LEAGUE, 100, "", 1, 0);
        final var next = new core.model.match.MatchKurzInfo();
        next.setMatchID(200);
        next.setMatchType(core.model.enums.MatchType.CUP);
        next.setMatchSchedule(core.util.HODateTime.fromHT("2026-10-07 20:00:00"));

        final var json = new JsonObject();
        LineupTools.addLineupMatch(json, team, next);

        assertThat(json.get("lineupMatchId").getAsInt()).isEqualTo(100);
        assertThat(json.get("matchType").getAsInt()).isEqualTo(1);
        final var nextMatch = json.getAsJsonObject("nextMatch");
        assertThat(nextMatch.get("matchId").getAsInt()).isEqualTo(200);
        assertThat(nextMatch.get("matchType").getAsInt()).isEqualTo(3);
        assertThat(nextMatch.get("date").getAsString()).isEqualTo("2026-10-07 20:00:00");

        final var unstored = new JsonObject();
        LineupTools.addLineupMatch(unstored, new core.model.match.MatchLineupTeam(), null);
        assertThat(unstored.get("lineupMatchId").isJsonNull()).isTrue();
        assertThat(unstored.get("nextMatch").isJsonNull()).isTrue();
    }

    @Test
    void applySpec_rejectsTheKeeperAsSetPiecesTaker() {
        final var keeperTakes = JsonParser.parseString("""
                {"positions":[{"roleId":100,"playerId":11},{"roleId":113,"playerId":22}],
                 "setPiecesTakerId":11}""").getAsJsonObject();
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> LineupTools.scratchLineup(keeperTakes))
                .withMessageContaining("keeper cannot take set pieces");

        final var outfielderTakes = JsonParser.parseString("""
                {"positions":[{"roleId":100,"playerId":11},{"roleId":113,"playerId":22}],
                 "setPiecesTakerId":22}""").getAsJsonObject();
        LineupTools.rejectKeeperAsSetPiecesTaker(outfielderTakes);   // no throw
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

    /** Team orders move the ratings, so the scratch lineup simulate_lineup scores carries them. */
    @Test
    void scratchLineup_carriesTheTeamOrders() throws McpToolException {
        final var lineup = LineupTools.scratchLineup(JsonParser.parseString("""
                {"positions":[{"roleId":100,"playerId":11},{"roleId":113,"playerId":22}],
                 "bench":[{"roleId":204,"playerId":33}],
                 "teamOrders":[{"type":"substitution","minute":60,"playerOut":22,"playerIn":33}]}""")
                .getAsJsonObject());

        assertThat(lineup.getSubstitutionList()).hasSize(1);
        assertThat(lineup.getFieldPlayers(59)).extracting(p -> p.getPlayerId()).contains(22).doesNotContain(33);
        assertThat(lineup.getFieldPlayers(60)).extracting(p -> p.getPlayerId()).contains(33).doesNotContain(22);
    }

    @Test
    void scratchLineup_scoresUnderTheGivenWeatherAndRejectsAnUnknownOne() throws McpToolException {
        final var sunny = LineupTools.scratchLineup(JsonParser.parseString("""
                {"positions":[{"roleId":100,"playerId":11}],"weather":3}""").getAsJsonObject());
        assertThat(sunny.getWeather()).isEqualTo(core.model.match.Weather.SUNNY);

        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> LineupTools.scratchLineup(JsonParser.parseString("""
                        {"positions":[],"weather":4}""").getAsJsonObject()))
                .withMessageContaining("3 sunny");
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
