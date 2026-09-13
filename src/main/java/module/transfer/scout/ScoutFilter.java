package module.transfer.scout;

import core.constants.player.PlayerSpeciality;
import core.net.TransferSearchQuery;
import core.util.AmountOfMoney;
import module.transfer.PlayerFilterForm;

import java.util.Date;
import java.util.function.Predicate;

/**
 * Builds a {@link Predicate} that filters scout list entries by the same age/skill/speciality/price
 * criteria the CHPP transfer search accepts, plus a scout-only "still on the market" flag. Kept pure
 * and Swing-free so it is unit-testable: {@link module.transfer.scout.TransferScoutingTable} applies
 * the result as a {@code RowFilter} without ever touching the underlying scout list.
 */
public final class ScoutFilter {

    private ScoutFilter() {
    }

    /**
     * @param criteria           the age/skill/speciality/price criteria from {@link PlayerFilterForm}
     * @param futureDeadlineOnly when true, also require a deadline that has not passed yet
     */
    public static Predicate<ScoutEintrag> fromCriteria(PlayerFilterForm.Criteria criteria,
                                                        boolean futureDeadlineOnly) {
        Predicate<ScoutEintrag> predicate = entry -> matches(entry, criteria);
        if (futureDeadlineOnly) {
            predicate = predicate.and(ScoutFilter::hasFutureDeadline);
        }
        return predicate;
    }

    private static boolean matches(ScoutEintrag entry, PlayerFilterForm.Criteria criteria) {
        final var age = entry.getAlterWithAgeDays();
        if (age < criteria.ageMin() || age > criteria.ageMax()) {
            return false;
        }
        for (final var range : criteria.skills()) {
            final var level = levelOf(entry, range.skill());
            if (level < range.min() || level > range.max()) {
                return false;
            }
        }
        if (criteria.specialityId() != PlayerSpeciality.NO_SPECIALITY
                && entry.getSpeciality() != criteria.specialityId()) {
            return false;
        }
        if (criteria.maxPriceSek() != null && entry.getPrice() != null
                && entry.getPrice().isGreaterThan(new AmountOfMoney(criteria.maxPriceSek().longValue()))) {
            return false;
        }
        return true;
    }

    private static boolean hasFutureDeadline(ScoutEintrag entry) {
        return entry.getDeadline() != null && entry.getDeadline().after(new Date());
    }

    /** The scout entry's own level for a transfer-search skill - all ten already have a field. */
    private static int levelOf(ScoutEintrag entry, TransferSearchQuery.Skill skill) {
        return switch (skill) {
            case KEEPER -> entry.getTorwart();
            case STAMINA -> entry.getKondition();
            case SETPIECES -> entry.getStandards();
            case DEFENDING -> entry.getVerteidigung();
            case SCORING -> entry.getTorschuss();
            case WINGER -> entry.getFluegelspiel();
            case PASSING -> entry.getPasspiel();
            case PLAYMAKING -> entry.getSpielaufbau();
            case LEADERSHIP -> entry.getLeadership();
            case EXPERIENCE -> entry.getErfahrung();
        };
    }
}
