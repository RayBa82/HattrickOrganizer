package core.mcp;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** The parts of week_brief that need no loaded HOModel: its surface and its row rules. */
class WeekToolsTest {

    private static McpTool weekBrief() {
        return WeekTools.tools().get(0);
    }

    /** The order reads are CHPP round trips, so they run as a task; without them it is local. */
    @Test
    void weekBrief_runsAsATaskOnlyWhileItReadsOrders() {
        assertThat(weekBrief().name()).isEqualTo("week_brief");
        assertThat(weekBrief().taskEligible(new JsonObject())).isTrue();
        assertThat(weekBrief().taskEligible(JsonParser.parseString("""
                {"includeOrders":false}""").getAsJsonObject())).isFalse();
        assertThat(weekBrief().inputSchema().getAsJsonObject("properties").keySet())
                .containsExactlyInAnyOrder("playerIds", "includeOrders");
    }

    /** A player with no minutes this week is not maxed, and zero minutes are left out. */
    @Test
    void minutes_leaveZerosOutAndMarkAFullSlotAsMaxed() {
        final var none = new JsonObject();
        WeekTools.addMinutes(none, null);
        assertThat(none.keySet()).containsExactly("maxedForWeek");
        assertThat(none.get("maxedForWeek").getAsBoolean()).isFalse();

        final var full = new JsonObject();
        WeekTools.addMinutes(full, JsonParser.parseString("""
                {"fullTrainingMinutes":90,"partlyTrainingMinutes":0}""").getAsJsonObject());
        assertThat(full.get("fullTrainingMinutes").getAsInt()).isEqualTo(90);
        assertThat(full.has("partlyTrainingMinutes")).isFalse();
        assertThat(full.get("maxedForWeek").getAsBoolean()).isTrue();
    }

    /** The brief keeps what changed and drops the dates the agent does not act on. */
    @Test
    void levelUps_keepSkillFromAndToOnly() {
        final var compact = WeekTools.compactLevelUps(JsonParser.parseString("""
                {"status":"ok","note":"long","players":[{"playerId":1,"name":"A",
                 "baselineDate":"x","latestDate":"y",
                 "levelUps":[{"skill":"STAMINA","from":3,"to":4,"date":"y","ageAtChange":36}]}]}
                """).getAsJsonObject());
        assertThat(compact.has("note")).isFalse();
        assertThat(compact.getAsJsonArray("players").get(0)).hasToString(
                "{\"playerId\":1,\"name\":\"A\",\"levelUps\":[{\"skill\":\"STAMINA\",\"from\":3,\"to\":4}]}");
    }

    /** No HO lineup for the match is "unknown", not "differs" and not "agrees". */
    @Test
    void hoComparison_withoutAStoredLineupIsNull() {
        final var order = new JsonObject();
        WeekTools.addHoComparison(order, new JsonObject(), null);
        assertThat(order.get("hoLineupDiffers").isJsonNull()).isTrue();
        assertThat(order.get("message").getAsString()).contains("no lineup");
    }
}
