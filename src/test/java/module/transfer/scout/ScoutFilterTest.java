package module.transfer.scout;

import core.constants.player.PlayerSpeciality;
import core.net.TransferSearchQuery;
import core.util.AmountOfMoney;
import module.transfer.PlayerFilterForm;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ScoutFilterTest {

    private static ScoutEintrag entry(int age, int playmaking, int speciality, long priceSek,
                                      long deadlineOffsetMillis) {
        final var entry = new ScoutEintrag();
        entry.setAlter(age);
        entry.setSpielaufbau(playmaking);
        entry.setSpeciality(speciality);
        entry.setPrice(new AmountOfMoney(priceSek));
        entry.setDeadline(new Timestamp(System.currentTimeMillis() + deadlineOffsetMillis));
        return entry;
    }

    private static PlayerFilterForm.Criteria criteria(int ageMin, int ageMax, int skillMin,
                                                       int skillMax, int specialityId,
                                                       Integer maxPriceSek) {
        return new PlayerFilterForm.Criteria(ageMin, ageMax,
                List.of(new PlayerFilterForm.SkillRange(TransferSearchQuery.Skill.PLAYMAKING, skillMin, skillMax)),
                specialityId, maxPriceSek);
    }

    @Test
    void fromCriteria_rejectsAgeOutsideTheRange() {
        final var criteria = criteria(20, 25, 0, 20, PlayerSpeciality.NO_SPECIALITY, null);
        final var predicate = ScoutFilter.fromCriteria(criteria, false);

        assertThat(predicate.test(entry(19, 10, PlayerSpeciality.NO_SPECIALITY, 100_000L, 0))).isFalse();
        assertThat(predicate.test(entry(22, 10, PlayerSpeciality.NO_SPECIALITY, 100_000L, 0))).isTrue();
    }

    @Test
    void fromCriteria_rejectsASkillOutsideItsRange() {
        final var criteria = criteria(17, 40, 7, 9, PlayerSpeciality.NO_SPECIALITY, null);
        final var predicate = ScoutFilter.fromCriteria(criteria, false);

        assertThat(predicate.test(entry(22, 6, PlayerSpeciality.NO_SPECIALITY, 100_000L, 0))).isFalse();
        assertThat(predicate.test(entry(22, 8, PlayerSpeciality.NO_SPECIALITY, 100_000L, 0))).isTrue();
    }

    @Test
    void fromCriteria_matchesAnyPlayerWhenNoSpecialityIsSelected() {
        final var criteria = criteria(17, 40, 0, 20, PlayerSpeciality.NO_SPECIALITY, null);
        final var predicate = ScoutFilter.fromCriteria(criteria, false);

        assertThat(predicate.test(entry(22, 10, PlayerSpeciality.TECHNICAL, 100_000L, 0))).isTrue();
    }

    @Test
    void fromCriteria_rejectsAMismatchedSpeciality() {
        final var criteria = criteria(17, 40, 0, 20, PlayerSpeciality.TECHNICAL, null);
        final var predicate = ScoutFilter.fromCriteria(criteria, false);

        assertThat(predicate.test(entry(22, 10, PlayerSpeciality.QUICK, 100_000L, 0))).isFalse();
        assertThat(predicate.test(entry(22, 10, PlayerSpeciality.TECHNICAL, 100_000L, 0))).isTrue();
    }

    @Test
    void fromCriteria_rejectsAPriceAboveTheMax() {
        final var criteria = criteria(17, 40, 0, 20, PlayerSpeciality.NO_SPECIALITY, 500_000);
        final var predicate = ScoutFilter.fromCriteria(criteria, false);

        assertThat(predicate.test(entry(22, 10, PlayerSpeciality.NO_SPECIALITY, 600_000L, 0))).isFalse();
        assertThat(predicate.test(entry(22, 10, PlayerSpeciality.NO_SPECIALITY, 400_000L, 0))).isTrue();
    }

    @Test
    void fromCriteria_futureDeadlineOnlyRejectsAnExpiredEntry() {
        final var criteria = criteria(17, 40, 0, 20, PlayerSpeciality.NO_SPECIALITY, null);
        final var predicate = ScoutFilter.fromCriteria(criteria, true);

        assertThat(predicate.test(entry(22, 10, PlayerSpeciality.NO_SPECIALITY, 100_000L, -60_000))).isFalse();
        assertThat(predicate.test(entry(22, 10, PlayerSpeciality.NO_SPECIALITY, 100_000L, 60_000))).isTrue();
    }
}
