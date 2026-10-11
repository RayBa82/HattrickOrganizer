package core.db;

import core.rating.RatingPredictionModel.RatingSector;
import core.util.HODateTime;

import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.UnaryOperator;

/**
 * Predicted versus real sector ratings of our own played matches, one row per match.
 * <p>
 * Filled when a match's details are downloaded and by {@code DBManager#backfillRatingCalibration};
 * summarised by {@code core.mcp.CalibrationSummary}.
 */
final class RatingCalibrationTable extends AbstractTable {

    static final String TABLENAME = "RATING_CALIBRATION";

    private static final RatingSector[] SECTORS = RatingSector.values();

    RatingCalibrationTable(ConnectionManager adapter) {
        super(TABLENAME, adapter);
        idColumns = 2;
    }

    @Override
    protected void initColumns() {
        final var cols = new ArrayList<ColumnDescriptor>();
        cols.add(col("MATCHID", Types.INTEGER, o -> row(o).getMatchId(), (o, v) -> row(o).setMatchId((int) v)));
        cols.add(col("MATCHTYP", Types.INTEGER, o -> row(o).getMatchType(), (o, v) -> row(o).setMatchType((int) v)));
        cols.add(col("MATCHDATE", Types.TIMESTAMP, o -> HODateTime.toDbTimestamp(row(o).getMatchDate()),
                (o, v) -> row(o).setMatchDate((HODateTime) v)));
        cols.add(col("HRFID", Types.INTEGER, o -> row(o).getHrfId(), (o, v) -> row(o).setHrfId((int) v)));
        cols.add(col("SNAPSHOTAGEDAYS", Types.DOUBLE, o -> row(o).getSnapshotAgeDays(),
                (o, v) -> row(o).setSnapshotAgeDays((double) v)));
        cols.add(col("HOME", Types.BOOLEAN, o -> row(o).isHome(), (o, v) -> row(o).setHome((boolean) v)));
        cols.add(ColumnDescriptor.Builder.newInstance().setColumnName("FORMATION")
                .setGetter(o -> truncateString(row(o).getFormation(), 8))
                .setSetter((o, v) -> row(o).setFormation((String) v))
                .setType(Types.VARCHAR).setLength(8).isNullable(true).build());
        cols.add(col("TACTIC", Types.INTEGER, o -> row(o).getTactic(), (o, v) -> row(o).setTactic((int) v)));
        cols.add(col("STYLEOFPLAY", Types.INTEGER, o -> row(o).getStyleOfPlay(), (o, v) -> row(o).setStyleOfPlay((int) v)));
        cols.add(col("ATTITUDE", Types.INTEGER, o -> row(o).getAttitude(), (o, v) -> row(o).setAttitude((int) v)));
        cols.add(col("WEATHERID", Types.INTEGER, o -> row(o).getWeatherId(), (o, v) -> row(o).setWeatherId((int) v)));
        cols.add(col("LOCATION", Types.INTEGER, o -> row(o).getLocation(), (o, v) -> row(o).setLocation((int) v)));
        cols.add(col("TEAMSPIRIT", Types.DOUBLE, o -> row(o).getTeamSpirit(), (o, v) -> row(o).setTeamSpirit((double) v)));
        cols.add(col("CONFIDENCE", Types.DOUBLE, o -> row(o).getConfidence(), (o, v) -> row(o).setConfidence((double) v)));
        for (int i = 0; i < SECTORS.length; i++) {
            final int s = i;
            final var name = SECTORS[i].name();
            cols.add(col("PRED_" + name, Types.DOUBLE, o -> row(o).getPredicted()[s],
                    (o, v) -> row(o).getPredicted()[s] = (double) v));
            cols.add(col("REAL_" + name, Types.DOUBLE, o -> row(o).getReal()[s],
                    (o, v) -> row(o).getReal()[s] = (double) v));
            cols.add(col("RESID_" + name, Types.DOUBLE, o -> row(o).getResidual()[s],
                    (o, v) -> row(o).getResidual()[s] = (double) v));
        }
        cols.add(col("CONFOUNDED", Types.BOOLEAN, o -> row(o).isConfounded(), (o, v) -> row(o).setConfounded((boolean) v)));
        cols.add(ColumnDescriptor.Builder.newInstance().setColumnName("CONFOUNDEDREASON")
                .setGetter(o -> truncateString(row(o).getConfoundedReason(), 128))
                .setSetter((o, v) -> row(o).setConfoundedReason((String) v))
                .setType(Types.VARCHAR).setLength(128).isNullable(true).build());
        cols.add(ColumnDescriptor.Builder.newInstance().setColumnName("PRED_TACTIC")
                .setGetter(o -> row(o).getPredictedTactic())
                .setSetter((o, v) -> row(o).setPredictedTactic((Double) v))
                .setType(Types.DOUBLE).isNullable(true).build());
        cols.add(ColumnDescriptor.Builder.newInstance().setColumnName("REAL_TACTIC")
                .setGetter(o -> row(o).getRealTactic())
                .setSetter((o, v) -> row(o).setRealTactic((Integer) v))
                .setType(Types.INTEGER).isNullable(true).build());
        this.columns = cols.toArray(new ColumnDescriptor[0]);
    }

    private static RatingCalibrationRow row(Object o) {
        return (RatingCalibrationRow) o;
    }

    private static ColumnDescriptor col(String name, int type, UnaryOperator<Object> getter, BiConsumer<Object, Object> setter) {
        return ColumnDescriptor.Builder.newInstance().setColumnName(name).setGetter(getter)
                .setSetter(setter).setType(type).isNullable(false).build();
    }

    @Override
    protected String[] getConstraintStatements() {
        return new String[]{"  PRIMARY KEY (MATCHID, MATCHTYP)"};
    }

    void storeRow(RatingCalibrationRow row) {
        row.setIsStored(isStored(row.getMatchId(), row.getMatchType()));
        store(row);
    }

    boolean hasRow(int matchId, int matchType) {
        return isStored(matchId, matchType);
    }

    List<RatingCalibrationRow> loadAll() {
        return load(RatingCalibrationRow.class,
                connectionManager.executePreparedQuery(createSelectStatement("ORDER BY MATCHDATE ASC")));
    }
}
