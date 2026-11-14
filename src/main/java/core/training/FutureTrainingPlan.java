package core.training;

import core.db.DBManager;
import core.model.UserParameter;
import core.model.enums.DBDataSource;
import core.util.HODateTime;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The future training plan, rolled forward to the coming weeks.
 * <p>
 * The FUTURETRAINING table only holds the weeks of the last time the plan was saved; nothing moves
 * it on when a training passes. The Training tab and the MCP tools both read it through here, so
 * the headless server never predicts from weeks that are already over.
 */
public final class FutureTrainingPlan {

    private FutureTrainingPlan() {
    }

    /**
     * The plan for {@code UserParameter.futureWeeks} weeks from the next training on, stored back
     * when it differs from the table. An empty plan (nothing downloaded yet) is never stored, so
     * the user's weeks are not wiped.
     */
    public static List<TrainingPerWeek> current() {
        final var stored = DBManager.instance().getFutureTrainingsVector();
        final var plan = adjust(stored, TrainingManager.instance().getNextWeekTraining(),
                UserParameter.instance().futureWeeks);
        if (!plan.isEmpty() && !sameDates(stored, plan)) {
            DBManager.instance().saveFutureTrainings(plan);
        }
        return plan;
    }

    /**
     * {@code weeks} entries, each seven days after the one before, starting seven days after
     * {@code nextWeekTraining}. A stored week with that date is kept; a missing one copies the
     * week before it (source GUESS). Stored weeks outside that window are dropped.
     *
     * @return the plan, empty without a next week training
     */
    public static List<TrainingPerWeek> adjust(List<TrainingPerWeek> stored,
                                               @Nullable TrainingPerWeek nextWeekTraining, int weeks) {
        final List<TrainingPerWeek> plan = new ArrayList<>();
        var previous = nextWeekTraining;
        while (previous != null && plan.size() < weeks) {
            final HODateTime date = previous.getTrainingDate().plusDaysAtSameLocalTime(7);
            final var matching = stored.stream().filter(t -> date.equals(t.getTrainingDate())).toList();
            final TrainingPerWeek week;
            if (matching.size() == 1) {
                week = matching.get(0);
            } else {
                week = new TrainingPerWeek(date, previous.getTrainingType(), previous.getTrainingIntensity(),
                        previous.getStaminaShare(), previous.getTrainingAssistantsLevel(),
                        previous.getCoachLevel(), DBDataSource.GUESS);
            }
            plan.add(week);
            previous = week;
        }
        return plan;
    }

    private static boolean sameDates(List<TrainingPerWeek> stored, List<TrainingPerWeek> plan) {
        if (stored.size() != plan.size()) {
            return false;
        }
        for (var i = 0; i < plan.size(); i++) {
            if (!plan.get(i).getTrainingDate().equals(stored.get(i).getTrainingDate())) {
                return false;
            }
        }
        return true;
    }
}
