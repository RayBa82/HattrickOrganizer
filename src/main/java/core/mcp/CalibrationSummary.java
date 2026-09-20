package core.mcp;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import core.db.RatingCalibrationRow;
import core.rating.RatingPredictionModel.RatingSector;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Residual statistics of the rating-model calibration rows, for {@code get_rating_calibration}.
 * <p>
 * Pure over the rows so it can be tested without a database. Rows with a mid-match lineup change
 * never enter a statistic; they are only counted and listed.
 */
final class CalibrationSummary {

    private static final RatingSector[] SECTORS = RatingSector.values();

    private CalibrationSummary() {
    }

    /** The key a sector goes by in tool results, matching get_match. */
    static String key(RatingSector s) {
        return switch (s) {
            case MIDFIELD -> "midfield";
            case DEFENCE_LEFT -> "defenceLeft";
            case DEFENCE_CENTRAL -> "defenceCentral";
            case DEFENCE_RIGHT -> "defenceRight";
            case ATTACK_LEFT -> "attackLeft";
            case ATTACK_CENTRAL -> "attackCentral";
            case ATTACK_RIGHT -> "attackRight";
        };
    }

    /**
     * @param maxSnapshotAgeDays when not null, rows whose HRF snapshot is older than this are left
     *                           out too: skills drift between the snapshot and the match
     */
    static JsonObject summarize(List<RatingCalibrationRow> rows, Double maxSnapshotAgeDays) {
        final var clean = rows.stream().filter(r -> !r.isConfounded()).toList();
        final var used = clean.stream()
                .filter(r -> maxSnapshotAgeDays == null || r.getSnapshotAgeDays() <= maxSnapshotAgeDays)
                .toList();
        final var json = new JsonObject();
        json.addProperty("rowsStored", rows.size());
        json.addProperty("rowsExcluded", rows.size() - clean.size());
        json.addProperty("rowsSnapshotTooOld", clean.size() - used.size());
        json.addProperty("rowsUsed", used.size());
        json.addProperty("residual", "real minus predicted, on the 1-20 rating scale; positive means the "
                + "model is too low. sd is the sample standard deviation, null below two rows.");
        json.add("overall", stats(used));
        json.add("byFormation", grouped(used, r -> r.getFormation() == null ? "unknown" : r.getFormation()));
        json.add("byLocation", grouped(used, r -> r.isHome() ? "home" : "away"));

        final var age = used.stream().mapToDouble(RatingCalibrationRow::getSnapshotAgeDays).summaryStatistics();
        if (age.getCount() > 0) {
            final var a = new JsonObject();
            a.addProperty("mean", age.getAverage());
            a.addProperty("max", age.getMax());
            json.add("snapshotAgeDays", a);
        }

        final var excluded = new JsonArray();
        rows.stream().filter(RatingCalibrationRow::isConfounded).forEach(r -> {
            final var e = new JsonObject();
            e.addProperty("matchId", r.getMatchId());
            e.addProperty("matchType", r.getMatchType());
            e.addProperty("reason", r.getConfoundedReason());
            excluded.add(e);
        });
        json.add("excluded", excluded);
        return json;
    }

    private static JsonObject grouped(List<RatingCalibrationRow> rows, Function<RatingCalibrationRow, String> by) {
        final Map<String, List<RatingCalibrationRow>> groups = rows.stream()
                .collect(Collectors.groupingBy(by, TreeMap::new, Collectors.toList()));
        final var json = new JsonObject();
        groups.forEach((name, group) -> json.add(name, stats(group)));
        return json;
    }

    /** Row count, and per sector the mean and spread of the residual. */
    static JsonObject stats(List<RatingCalibrationRow> rows) {
        final var json = new JsonObject();
        json.addProperty("n", rows.size());
        final var sectors = new JsonObject();
        for (int i = 0; i < SECTORS.length; i++) {
            final int s = i;
            final double mean = rows.stream().mapToDouble(r -> r.getResidual()[s]).average().orElse(Double.NaN);
            final var one = new JsonObject();
            if (rows.isEmpty()) {
                one.add("mean", null);
            } else {
                one.addProperty("mean", mean);
            }
            if (rows.size() < 2) {
                one.add("sd", null);
            } else {
                final double ss = rows.stream().mapToDouble(r -> Math.pow(r.getResidual()[s] - mean, 2)).sum();
                one.addProperty("sd", Math.sqrt(ss / (rows.size() - 1)));
            }
            sectors.add(key(SECTORS[i]), one);
        }
        json.add("sectors", sectors);
        return json;
    }
}
