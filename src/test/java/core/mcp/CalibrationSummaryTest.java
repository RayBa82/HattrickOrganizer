package core.mcp;

import core.db.RatingCalibrationRow;
import core.util.HODateTime;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class CalibrationSummaryTest {

    private static final CalibrationSummary.Query ALL =
            new CalibrationSummary.Query(Set.of(), 7.0, false, 20, 0);

    private static RatingCalibrationRow row(int matchId, int matchType, String day, boolean confounded,
                                            double residual) {
        final var r = new RatingCalibrationRow();
        r.setMatchId(matchId);
        r.setMatchType(matchType);
        r.setMatchDate(HODateTime.fromHT(day + " 18:00:00"));
        r.setConfounded(confounded);
        r.setConfoundedReason(confounded ? "substitute came on" : null);
        Arrays.fill(r.getResidual(), residual);
        return r;
    }

    @Test
    void confoundedRowsAreListedButNeverAveraged() {
        final var json = CalibrationSummary.summarize(List.of(
                row(1, 1, "2026-09-01", false, 1.0),
                row(2, 1, "2026-09-08", false, 3.0),
                row(3, 1, "2026-09-15", true, 100.0)), ALL);

        final var stats = json.getAsJsonObject("stats");
        assertThat(stats.get("n").getAsInt()).isEqualTo(2);
        assertThat(stats.get("from").getAsString()).isEqualTo("2026-09-01");
        assertThat(stats.get("to").getAsString()).isEqualTo("2026-09-08");
        assertThat(stats.getAsJsonArray("mean").get(0).getAsDouble()).isEqualTo(2.0);
        assertThat(stats.getAsJsonArray("sd").get(0).getAsDouble()).isCloseTo(Math.sqrt(2.0), within(0.01));
        assertThat(json.get("leftOutConfounded").getAsInt()).isEqualTo(1);
        // Rows: all of them, newest first, the confounded one flagged with its reason.
        assertThat(json.get("count").getAsInt()).isEqualTo(3);
        final var first = json.getAsJsonArray("rows").get(0).getAsJsonObject();
        assertThat(first.get("matchId").getAsInt()).isEqualTo(3);
        assertThat(first.get("confounded").getAsString()).isEqualTo("substitute came on");
        assertThat(json.getAsJsonArray("rows").get(1).getAsJsonObject().has("confounded")).isFalse();
        assertThat(first.get("location").getAsString()).isEqualTo("away");
        assertThat(CalibrationSummary.location(1)).isEqualTo("home");

        final var with = CalibrationSummary.summarize(List.of(
                row(1, 1, "2026-09-01", false, 1.0),
                row(3, 1, "2026-09-15", true, 100.0)),
                new CalibrationSummary.Query(Set.of(), 7.0, true, 20, 0));
        assertThat(with.getAsJsonObject("stats").get("n").getAsInt()).isEqualTo(2);
    }

    @Test
    void snapshotAgeAndMatchTypeFilter() {
        final var old = row(1, 1, "2026-09-01", false, 9.0);
        old.setSnapshotAgeDays(30);
        final var fresh = row(2, 1, "2026-09-08", false, 1.0);
        fresh.setSnapshotAgeDays(2);
        final var friendly = row(3, 4, "2026-09-10", false, 5.0);

        final var json = CalibrationSummary.summarize(List.of(old, fresh, friendly),
                new CalibrationSummary.Query(Set.of(1), 7.0, false, 20, 0));

        assertThat(json.get("leftOutSnapshotTooOld").getAsInt()).isEqualTo(1);
        assertThat(json.getAsJsonObject("stats").get("n").getAsInt()).isEqualTo(1);
        assertThat(json.getAsJsonObject("stats").getAsJsonArray("mean").get(0).getAsDouble()).isEqualTo(1.0);
        assertThat(json.get("count").getAsInt()).isEqualTo(2);
    }

    @Test
    void pagesTheRowsAndNullsAnSdBelowTwoRows() {
        final var json = CalibrationSummary.summarize(List.of(
                row(1, 1, "2026-09-01", false, 1.0),
                row(2, 1, "2026-09-08", false, 2.0),
                row(3, 1, "2026-09-15", false, 3.0)),
                new CalibrationSummary.Query(Set.of(), 7.0, false, 1, 1));

        assertThat(json.get("returned").getAsInt()).isEqualTo(1);
        assertThat(json.getAsJsonArray("rows").get(0).getAsJsonObject().get("matchId").getAsInt()).isEqualTo(2);

        final var one = CalibrationSummary.stats(List.of(row(1, 1, "2026-09-01", false, 1.0)));
        assertThat(one.getAsJsonArray("sd").get(0).isJsonNull()).isTrue();
        assertThat(one.getAsJsonObject("tactic").get("n").getAsInt()).isZero();
        assertThat(one.getAsJsonObject("tactic").get("mean").isJsonNull()).isTrue();
    }

    @Test
    void tacticLevelIsTheRawRatingPlusOneAgainstTheRealLevel() {
        final var r = row(1, 1, "2026-09-01", false, 0.0);
        r.setTactic(2);
        r.setPredictedTactic(9.0);
        r.setRealTactic(12);
        final var normal = row(2, 1, "2026-09-08", false, 0.0);
        normal.setPredictedTactic(0.0);
        normal.setRealTactic(0);

        final var json = CalibrationSummary.summarize(List.of(r, normal), ALL);

        final var tactic = json.getAsJsonObject("stats").getAsJsonObject("tactic");
        assertThat(tactic.get("n").getAsInt()).isEqualTo(1);
        assertThat(tactic.get("mean").getAsDouble()).isEqualTo(2.0);
        final var rows = json.getAsJsonArray("rows");
        assertThat(rows.get(1).getAsJsonObject().getAsJsonArray("tactic"))
                .extracting(com.google.gson.JsonElement::getAsDouble).containsExactly(10.0, 12.0);
        assertThat(rows.get(0).getAsJsonObject().has("tactic")).isFalse();
    }
}
