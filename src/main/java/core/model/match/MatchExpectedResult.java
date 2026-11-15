package core.model.match;

import core.db.AbstractTable;
import core.model.enums.MatchType;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

/**
 * The statistics of the simulated replays of one match (matchdetails 3.2 {@code ExpectedResult}).
 * Hattrick sends them only where the site shows them: national team matches, a Diamond supporter's
 * own matches, or a match an own or mentored team bought replay credits for. All counts are numbers
 * of simulated replays out of {@link #nrOfSamples}.
 */
@Getter
@Setter
public class MatchExpectedResult extends AbstractTable.Storable {

    /** Goal counts 0 to 5 plus "more than 5", as in GoalDistribution. */
    public static final int GOAL_BUCKETS = 7;
    /** The over/under lines 0.5, 1.5, 2.5, 3.5 and 4.5. */
    public static final int GOAL_LINES = 5;

    private int matchId;
    private MatchType matchType = MatchType.NONE;
    private int nrOfSamples;
    private int homeWins;
    private int draws;
    private int awayWins;
    private Double averageHomeGoals;
    private Double averageAwayGoals;
    /** Before any penalty shootout; only for cup-rule matches, null otherwise. */
    private Integer fullTimeHomeWins;
    private Integer fullTimeDraws;
    private Integer fullTimeAwayWins;
    private Double averageWinMarginHome;
    private Double averageWinMarginAway;

    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private final Integer[] homeGoals = new Integer[GOAL_BUCKETS];
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private final Integer[] awayGoals = new Integer[GOAL_BUCKETS];
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private final Integer[] over = new Integer[GOAL_LINES];
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private final Integer[] under = new Integer[GOAL_LINES];

    /** Replays in which the team scored {@code bucket} goals; bucket 6 is "more than 5". */
    public Integer getGoals(boolean home, int bucket) {
        return (home ? homeGoals : awayGoals)[bucket];
    }

    public void setGoals(boolean home, int bucket, Integer count) {
        (home ? homeGoals : awayGoals)[bucket] = count;
    }

    /** Replays with more ({@code isOver}) or fewer total goals than line {@code line} + 0.5. */
    public Integer getOverUnder(boolean isOver, int line) {
        return (isOver ? over : under)[line];
    }

    public void setOverUnder(boolean isOver, int line, Integer count) {
        (isOver ? over : under)[line] = count;
    }
}
