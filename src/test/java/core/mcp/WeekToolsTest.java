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
                .containsExactlyInAnyOrder("playerIds", "includeOrders", "includeLevelUps");
    }

    /** Only contracts ending within two weeks are flagged; unknown ones never. */
    @Test
    void staffContractsEnding_flagsOnlyTheNextTwoWeeks() {
        final var now = core.util.HODateTime.fromHT("2026-11-14 12:00:00");
        final var soon = staff("Soon", "2026-11-20 03:00:00");
        final var later = staff("Later", "2026-12-20 03:00:00");
        final var unknown = staff("Unknown", null);

        final var rows = WeekTools.staffContractsEnding(java.util.List.of(soon, later, unknown), now);

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getAsJsonObject().get("name").getAsString()).isEqualTo("Soon");
        assertThat(rows.get(0).getAsJsonObject().get("contractExpires").getAsString())
                .isEqualTo("2026-11-20 03:00:00");
        assertThat(WeekTools.staffContractsEnding(null, now)).isEmpty();
    }

    private static core.model.StaffMember staff(String name, String expires) {
        final var member = new core.model.StaffMember();
        member.setName(name);
        member.setStaffType(core.model.StaffType.ASSISTANTTRAINER);
        member.setContractExpires(core.util.HODateTime.fromHT(expires));
        return member;
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

        final var partway = new JsonObject();
        WeekTools.addMinutes(partway, JsonParser.parseString("""
                {"fullTrainingMinutes":45,"partlyTrainingMinutes":30}""").getAsJsonObject());
        assertThat(partway.get("fullTrainingMinutes").getAsInt()).isEqualTo(45);
        assertThat(partway.get("partlyTrainingMinutes").getAsInt()).isEqualTo(30);
        assertThat(partway.get("maxedForWeek").getAsBoolean()).isFalse();
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

    /** A carried-over order short of eleven reads fine but must not look complete. */
    @Test
    void completeness_warnsOnAnOrderShortOfEleven() {
        final var shortOrder = new JsonObject();
        WeekTools.addCompleteness(shortOrder, 9);
        assertThat(shortOrder.get("missingFieldPlayers").getAsInt()).isEqualTo(2);
        assertThat(shortOrder.get("complete").getAsBoolean()).isFalse();
        assertThat(shortOrder.get("warning").getAsString()).contains("2 short");

        final var full = new JsonObject();
        WeekTools.addCompleteness(full, 11);
        assertThat(full.get("complete").getAsBoolean()).isTrue();
        assertThat(full.has("warning")).isFalse();
    }

    /** No HO lineup for the match is "unknown", not "differs" and not "agrees". */
    @Test
    void hoComparison_withoutAStoredLineupIsNull() {
        final var order = new JsonObject();
        WeekTools.addHoComparison(order, new JsonObject(), null);
        assertThat(order.get("hoLineupDiffers").isJsonNull()).isTrue();
        assertThat(order.get("message").getAsString()).contains("no lineup");
    }

    /** A download is a CHPP round trip, so the report is a task unless it is told not to download. */
    @Test
    void levelUpReport_runsAsATaskUnlessDownloadIsNever() {
        final var report = WeekTools.tools().get(1);
        assertThat(report.name()).isEqualTo("level_up_report");
        assertThat(report.taskEligible(new JsonObject())).isTrue();
        assertThat(report.taskEligible(JsonParser.parseString("""
                {"download":"never"}""").getAsJsonObject())).isFalse();
        assertThat(report.inputSchema().getAsJsonObject("properties").keySet())
                .containsExactlyInAnyOrder("playerIds", "download");
    }

    /** auto downloads once the snapshot's next training is due, or when HO cannot tell. */
    @Test
    void trainedSince_onceTheSnapshotsNextTrainingIsDue() {
        final var now = core.util.HODateTime.now();
        assertThat(WeekTools.trainedSince(now.plusDaysAtSameLocalTime(1), now)).isFalse();
        assertThat(WeekTools.trainedSince(now.plusDaysAtSameLocalTime(-1), now)).isTrue();
        assertThat(WeekTools.trainedSince(now, now)).isTrue();
        assertThat(WeekTools.trainedSince(null, now)).isTrue();
    }

    /** The report repeats only the download facts it needs, and says it ran. */
    @Test
    void downloadSummary_keepsTheCountsAndTheLineupOutcomeOnly() {
        final var summary = WeekTools.downloadSummary(JsonParser.parseString("""
                {"hrfId":5,"becameCurrent":true,"players":29,"newTransfers":0,
                 "sections":{"matches":true},"notRefreshed":"long text",
                 "lineupReplaced":false,"lineupKept":true,"lineupMatchId":9}""").getAsJsonObject());
        assertThat(summary.keySet()).containsExactly("status", "hrfId", "becameCurrent", "players",
                "newTransfers", "lineupReplaced", "lineupKept", "lineupMatchId");
    }
}
