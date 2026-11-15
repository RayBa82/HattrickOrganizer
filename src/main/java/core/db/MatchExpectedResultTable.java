package core.db;

import core.model.enums.MatchType;
import core.model.match.MatchExpectedResult;

import java.sql.Types;
import java.util.ArrayList;
import java.util.function.BiConsumer;
import java.util.function.UnaryOperator;

/**
 * The simulated replays' statistics of a match (matchdetails 3.2 ExpectedResult), one row per
 * match, keyed like MATCHDETAILS. Most matches have none; see {@link MatchExpectedResult}.
 */
final class MatchExpectedResultTable extends AbstractTable {

    static final String TABLENAME = "MATCHEXPECTEDRESULT";

    MatchExpectedResultTable(ConnectionManager adapter) {
        super(TABLENAME, adapter);
        idColumns = 2;
    }

    @Override
    protected void initColumns() {
        final var cols = new ArrayList<ColumnDescriptor>();
        cols.add(col("MatchID", Types.INTEGER, false, o -> row(o).getMatchId(), (o, v) -> row(o).setMatchId((int) v)));
        cols.add(col("MatchTyp", Types.INTEGER, false, o -> row(o).getMatchType().getId(),
                (o, v) -> row(o).setMatchType(MatchType.getById((int) v))));
        cols.add(col("NrOfSamples", Types.INTEGER, false, o -> row(o).getNrOfSamples(), (o, v) -> row(o).setNrOfSamples((int) v)));
        cols.add(col("HomeWins", Types.INTEGER, false, o -> row(o).getHomeWins(), (o, v) -> row(o).setHomeWins((int) v)));
        cols.add(col("Draws", Types.INTEGER, false, o -> row(o).getDraws(), (o, v) -> row(o).setDraws((int) v)));
        cols.add(col("AwayWins", Types.INTEGER, false, o -> row(o).getAwayWins(), (o, v) -> row(o).setAwayWins((int) v)));
        cols.add(col("AverageHomeGoals", Types.DOUBLE, true, o -> row(o).getAverageHomeGoals(),
                (o, v) -> row(o).setAverageHomeGoals((Double) v)));
        cols.add(col("AverageAwayGoals", Types.DOUBLE, true, o -> row(o).getAverageAwayGoals(),
                (o, v) -> row(o).setAverageAwayGoals((Double) v)));
        cols.add(col("FullTimeHomeWins", Types.INTEGER, true, o -> row(o).getFullTimeHomeWins(),
                (o, v) -> row(o).setFullTimeHomeWins((Integer) v)));
        cols.add(col("FullTimeDraws", Types.INTEGER, true, o -> row(o).getFullTimeDraws(),
                (o, v) -> row(o).setFullTimeDraws((Integer) v)));
        cols.add(col("FullTimeAwayWins", Types.INTEGER, true, o -> row(o).getFullTimeAwayWins(),
                (o, v) -> row(o).setFullTimeAwayWins((Integer) v)));
        for (var home : new boolean[]{true, false}) {
            final var side = home ? "Home" : "Away";
            for (int b = 0; b < MatchExpectedResult.GOAL_BUCKETS; b++) {
                final int bucket = b;
                final var name = side + (b == MatchExpectedResult.GOAL_BUCKETS - 1 ? "GoalsMoreThan5" : "Goals" + b);
                cols.add(col(name, Types.INTEGER, true, o -> row(o).getGoals(home, bucket),
                        (o, v) -> row(o).setGoals(home, bucket, (Integer) v)));
            }
        }
        cols.add(col("AverageWinMarginHome", Types.DOUBLE, true, o -> row(o).getAverageWinMarginHome(),
                (o, v) -> row(o).setAverageWinMarginHome((Double) v)));
        cols.add(col("AverageWinMarginAway", Types.DOUBLE, true, o -> row(o).getAverageWinMarginAway(),
                (o, v) -> row(o).setAverageWinMarginAway((Double) v)));
        for (int l = 0; l < MatchExpectedResult.GOAL_LINES; l++) {
            final int line = l;
            // Over05/Under05 ... Over45/Under45
            cols.add(col("Over" + l + "5", Types.INTEGER, true, o -> row(o).getOverUnder(true, line),
                    (o, v) -> row(o).setOverUnder(true, line, (Integer) v)));
            cols.add(col("Under" + l + "5", Types.INTEGER, true, o -> row(o).getOverUnder(false, line),
                    (o, v) -> row(o).setOverUnder(false, line, (Integer) v)));
        }
        this.columns = cols.toArray(new ColumnDescriptor[0]);
    }

    private static MatchExpectedResult row(Object o) {
        return (MatchExpectedResult) o;
    }

    private static ColumnDescriptor col(String name, int type, boolean nullable,
                                        UnaryOperator<Object> getter, BiConsumer<Object, Object> setter) {
        return ColumnDescriptor.Builder.newInstance().setColumnName(name).setGetter(getter)
                .setSetter(setter).setType(type).isNullable(nullable).build();
    }

    @Override
    protected String[] getConstraintStatements() {
        return new String[]{"  PRIMARY KEY (MATCHID, MATCHTYP)"};
    }

    void store(MatchExpectedResult result) {
        result.setIsStored(isStored(result.getMatchId(), result.getMatchType().getId()));
        super.store(result);
    }

    MatchExpectedResult load(int matchId, int matchType) {
        return loadOne(MatchExpectedResult.class, matchId, matchType);
    }
}
