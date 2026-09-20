package core.db;

import core.rating.RatingPredictionModel.RatingSector;
import core.util.HODateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * One of our own played matches, scored by the rating model as the lineup was started and set
 * against the sector ratings Hattrick actually gave it.
 * <p>
 * The three arrays run in {@link RatingSector#values()} order. {@code residual} is real minus
 * predicted, so a positive number means the model was too low. {@code confounded} rows had the
 * lineup change during the match (substitution, position swap, injury, red card): the model only
 * sees the starting eleven, so those rows say nothing about its parameters and must stay out of
 * any fit.
 */
@Getter
@Setter
public class RatingCalibrationRow extends AbstractTable.Storable {

    private int matchId;
    private int matchType;
    private HODateTime matchDate;
    /** The HRF snapshot the prediction used: the last one downloaded before the match. */
    private int hrfId;
    private double snapshotAgeDays;
    private boolean home;
    private String formation;
    private int tactic;
    private int styleOfPlay;
    private int attitude;
    private int weatherId;
    private int location;
    private double teamSpirit;
    private double confidence;
    private double[] predicted = new double[RatingSector.values().length];
    private double[] real = new double[RatingSector.values().length];
    private double[] residual = new double[RatingSector.values().length];
    private boolean confounded;
    private String confoundedReason;

    public RatingCalibrationRow() {
    }
}
