package core.mcp;

import core.db.RatingCalibrationRow;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class CalibrationSummaryTest {

    private static RatingCalibrationRow row(String formation, boolean home, boolean confounded, double residual) {
        final var r = new RatingCalibrationRow();
        r.setFormation(formation);
        r.setHome(home);
        r.setConfounded(confounded);
        r.setConfoundedReason(confounded ? "substitute came on" : null);
        Arrays.fill(r.getResidual(), residual);
        return r;
    }

    @Test
    void confoundedRowsAreCountedAndListedButNeverAveraged() {
        final var json = CalibrationSummary.summarize(List.of(
                row("4-4-2", true, false, 1.0),
                row("4-4-2", true, false, 3.0),
                row("5-3-2", false, true, 100.0)), null);

        assertThat(json.get("rowsStored").getAsInt()).isEqualTo(3);
        assertThat(json.get("rowsExcluded").getAsInt()).isEqualTo(1);
        assertThat(json.get("rowsUsed").getAsInt()).isEqualTo(2);
        assertThat(json.getAsJsonArray("excluded")).hasSize(1);
        final var mid = json.getAsJsonObject("overall").getAsJsonObject("sectors").getAsJsonObject("midfield");
        assertThat(mid.get("mean").getAsDouble()).isCloseTo(2.0, within(1e-9));
        assertThat(mid.get("sd").getAsDouble()).isCloseTo(Math.sqrt(2.0), within(1e-9));
        assertThat(json.getAsJsonObject("byFormation").has("5-3-2")).isFalse();
    }

    @Test
    void snapshotAgeLimitDropsOldRowsAndCountsThem() {
        final var old = row("4-4-2", true, false, 9.0);
        old.setSnapshotAgeDays(30);
        final var fresh = row("4-4-2", true, false, 1.0);
        fresh.setSnapshotAgeDays(2);

        final var json = CalibrationSummary.summarize(List.of(old, fresh), 7.0);

        assertThat(json.get("rowsSnapshotTooOld").getAsInt()).isEqualTo(1);
        assertThat(json.get("rowsUsed").getAsInt()).isEqualTo(1);
        assertThat(json.getAsJsonObject("overall").getAsJsonObject("sectors")
                .getAsJsonObject("midfield").get("mean").getAsDouble()).isEqualTo(1.0);
    }

    @Test
    void splitsByFormationAndLocation_andSdIsNullForASingleRow() {
        final var json = CalibrationSummary.summarize(List.of(
                row("4-4-2", true, false, 1.0),
                row("5-3-2", false, false, -2.0)), null);

        assertThat(json.getAsJsonObject("byLocation").keySet()).containsExactly("away", "home");
        final var away = json.getAsJsonObject("byLocation").getAsJsonObject("away");
        assertThat(away.get("n").getAsInt()).isEqualTo(1);
        final var def = away.getAsJsonObject("sectors").getAsJsonObject("defenceLeft");
        assertThat(def.get("mean").getAsDouble()).isEqualTo(-2.0);
        assertThat(def.get("sd").isJsonNull()).isTrue();
    }
}
