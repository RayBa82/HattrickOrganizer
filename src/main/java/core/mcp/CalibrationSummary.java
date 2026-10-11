package core.mcp;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import core.db.RatingCalibrationRow;
import core.rating.RatingPredictionModel.RatingSector;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.function.ToDoubleFunction;

/**
 * get_rating_calibration's answer over the stored calibration rows: residual statistics per
 * sector and for the tactic level, and the rows themselves, paged, as compact arrays in
 * sectorOrder.
 * <p>
 * Pure over the rows so it can be tested without a database.
 */
final class CalibrationSummary {

    private static final RatingSector[] SECTORS = RatingSector.values();

    private CalibrationSummary() {
    }

    /** What filters the statistics and pages the rows. Empty matchTypes, null attitude: all. */
    record Query(Set<Integer> matchTypes, Integer attitude, double maxSnapshotAgeDays,
                 boolean includeConfounded, int limit, int offset) {
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

    static JsonObject summarize(List<RatingCalibrationRow> stored, Query q) {
        final var rows = stored.stream()
                .filter(r -> q.matchTypes().isEmpty() || q.matchTypes().contains(r.getMatchType()))
                .filter(r -> q.attitude() == null || q.attitude() == r.getAttitude())
                .sorted(Comparator.comparing(RatingCalibrationRow::getMatchDate,
                        Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                .toList();
        final var used = rows.stream()
                .filter(r -> q.includeConfounded() || !r.isConfounded())
                .filter(r -> r.getSnapshotAgeDays() <= q.maxSnapshotAgeDays())
                .toList();

        final var json = new JsonObject();
        final var sectorOrder = new JsonArray();
        for (final var s : SECTORS) {
            sectorOrder.add(key(s));
        }
        json.add("sectorOrder", sectorOrder);
        json.add("stats", stats(used));
        json.addProperty("leftOutConfounded", q.includeConfounded() ? 0
                : (int) rows.stream().filter(RatingCalibrationRow::isConfounded).count());
        json.addProperty("leftOutSnapshotTooOld", (int) rows.stream()
                .filter(r -> q.includeConfounded() || !r.isConfounded())
                .filter(r -> r.getSnapshotAgeDays() > q.maxSnapshotAgeDays()).count());

        final var page = new JsonArray();
        rows.stream().skip(q.offset()).limit(q.limit()).forEach(r -> page.add(row(r)));
        json.addProperty("count", rows.size());
        json.addProperty("offset", q.offset());
        json.addProperty("returned", page.size());
        json.add("rows", page);
        return json;
    }

    /**
     * n, the date range, and per sector the mean and sample sd of real minus predicted, in
     * sectorOrder; tactic the same for the level, over rows that played a tactic and have both
     * values. sd is null below two rows, mean below one.
     */
    static JsonObject stats(List<RatingCalibrationRow> rows) {
        final var json = new JsonObject();
        json.addProperty("n", rows.size());
        rows.stream().map(RatingCalibrationRow::getMatchDate).filter(java.util.Objects::nonNull)
                .min(Comparator.naturalOrder()).ifPresent(d -> json.addProperty("from", day(d)));
        rows.stream().map(RatingCalibrationRow::getMatchDate).filter(java.util.Objects::nonNull)
                .max(Comparator.naturalOrder()).ifPresent(d -> json.addProperty("to", day(d)));
        final var mean = new JsonArray();
        final var sd = new JsonArray();
        for (int i = 0; i < SECTORS.length; i++) {
            final int s = i;
            addMeanAndSd(rows, r -> r.getResidual()[s], mean, sd);
        }
        json.add("mean", mean);
        json.add("sd", sd);

        final var tacticRows = rows.stream().filter(CalibrationSummary::hasTactic).toList();
        final var tactic = new JsonObject();
        tactic.addProperty("n", tacticRows.size());
        final var tMean = new JsonArray();
        final var tSd = new JsonArray();
        addMeanAndSd(tacticRows, CalibrationSummary::tacticResidual, tMean, tSd);
        tactic.add("mean", tMean.get(0));
        tactic.add("sd", tSd.get(0));
        json.add("tactic", tactic);
        return json;
    }

    private static void addMeanAndSd(List<RatingCalibrationRow> rows, ToDoubleFunction<RatingCalibrationRow> value,
                                     JsonArray means, JsonArray sds) {
        if (rows.isEmpty()) {
            means.add(com.google.gson.JsonNull.INSTANCE);
            sds.add(com.google.gson.JsonNull.INSTANCE);
            return;
        }
        final double mean = rows.stream().mapToDouble(value).average().orElseThrow();
        means.add(round2(mean));
        if (rows.size() < 2) {
            sds.add(com.google.gson.JsonNull.INSTANCE);
        } else {
            final double ss = rows.stream().mapToDouble(r -> Math.pow(value.applyAsDouble(r) - mean, 2)).sum();
            sds.add(round2(Math.sqrt(ss / (rows.size() - 1))));
        }
    }

    /** One row: arrays in sectorOrder; confounded and tactic only where they say something. */
    static JsonObject row(RatingCalibrationRow r) {
        final var json = new JsonObject();
        json.addProperty("matchId", r.getMatchId());
        json.addProperty("date", r.getMatchDate() == null ? null : day(r.getMatchDate()));
        json.addProperty("matchType", r.getMatchType());
        json.addProperty("location", location(r.getLocation()));
        json.addProperty("attitude", r.getAttitude());
        json.addProperty("ageDays", Math.round(r.getSnapshotAgeDays() * 10) / 10.0);
        if (r.isConfounded()) {
            json.addProperty("confounded", r.getConfoundedReason());
        }
        final var pred = new JsonArray();
        final var resid = new JsonArray();
        for (int i = 0; i < SECTORS.length; i++) {
            pred.add(round2(r.getPredicted()[i]));
            resid.add(round2(r.getResidual()[i]));
        }
        json.add("pred", pred);
        json.add("resid", resid);
        if (hasTactic(r)) {
            final var tactic = new JsonArray();
            tactic.add(round2(r.getPredictedTactic() + 1));
            tactic.add(r.getRealTactic());
            json.add("tactic", tactic);
        }
        return json;
    }

    /** Both levels stored, and a tactic other than normal was played. */
    private static boolean hasTactic(RatingCalibrationRow r) {
        return r.getPredictedTactic() != null && r.getRealTactic() != null && r.getTactic() != 0;
    }

    /** Real level minus the predicted one; the model's level is its raw tactic rating + 1. */
    private static double tacticResidual(RatingCalibrationRow r) {
        return r.getRealTactic() - (r.getPredictedTactic() + 1);
    }

    /**
     * The starters' behaviours as one string over the slots 100-113: a digit per filled slot
     * (0 normal, 1 offensive, 2 defensive, 3 towards middle, 4 towards wing), '.' for an empty one.
     */
    static String behaviours(List<core.model.match.MatchLineupPosition> played) {
        final var slots = new char[14];
        java.util.Arrays.fill(slots, '.');
        for (final var p : played) {
            final int slot = p.getStartPosition() - 100;
            if (slot >= 0 && slot < slots.length) {
                slots[slot] = Character.forDigit(p.getStartBehavior(), 10);
            }
        }
        return new String(slots);
    }

    /** IMatchDetails' location codes, named: away 0, home 1, derby 2, tournament 3, neutral 4. */
    static String location(int code) {
        return switch (code) {
            case 0 -> "away";
            case 1 -> "home";
            case 2 -> "derby";
            case 3 -> "tournament";
            case 4 -> "neutral";
            default -> String.valueOf(code);
        };
    }

    private static String day(core.util.HODateTime d) {
        return d.toHT().substring(0, 10);
    }

    private static double round2(double v) {
        return Math.round(v * 100) / 100.0;
    }
}
