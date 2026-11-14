package core.training;

import core.model.enums.DBDataSource;
import core.util.HODateTime;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FutureTrainingPlanTest {

    private static final HODateTime NEXT = HODateTime.fromHT("2026-11-20 00:30:00");

    /** A week the user planned by hand, all with type 9 so they differ from the next training's 7. */
    private static TrainingPerWeek week(String date) {
        return new TrainingPerWeek(HODateTime.fromHT(date), 9, 100, 10, 5, 7, DBDataSource.MANUAL);
    }

    @Test
    void stalePlanIsRolledForwardFromTheNextTraining() {
        // stored weeks all before the last training: what the headless server held
        final var stored = List.of(week("2026-08-07 00:30:00"), week("2026-08-14 00:30:00"));
        final var next = new TrainingPerWeek(NEXT, 7, 100, 10, 5, 7);

        final var plan = FutureTrainingPlan.adjust(stored, next, 3);

        assertThat(plan).extracting(t -> t.getTrainingDate().toHT()).containsExactly(
                "2026-11-27 00:30:00", "2026-12-04 00:30:00", "2026-12-11 00:30:00");
        assertThat(plan).allSatisfy(t -> {
            assertThat(t.getTrainingType()).isEqualTo(7);
            assertThat(t.getSource()).isEqualTo(DBDataSource.GUESS);
        });
    }

    @Test
    void storedWeeksInTheWindowAreKeptAndCarriedForward() {
        final var planned = week("2026-12-04 00:30:00");
        final var next = new TrainingPerWeek(NEXT, 7, 100, 10, 5, 7);

        final var plan = FutureTrainingPlan.adjust(List.of(planned), next, 3);

        assertThat(plan.get(0).getTrainingType()).isEqualTo(7);
        assertThat(plan.get(1)).isSameAs(planned);
        assertThat(plan.get(2).getTrainingType()).isEqualTo(9);
    }

    @Test
    void noNextTrainingGivesNoPlan() {
        assertThat(FutureTrainingPlan.adjust(List.of(week("2026-12-04 00:30:00")), null, 3)).isEmpty();
    }
}
