package module.transfer.scout;

import core.util.AmountOfMoney;
import core.util.HODateTime;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What a re-listing does to a scout entry. The persistence half of {@link ScoutList} reaches for
 * the Transfers tab when it is open, so only the field mutation is unit-testable - which is the
 * half with the decisions in it.
 */
class ScoutListTest {

    private static final HODateTime OLD_DEADLINE = HODateTime.fromHT("2026-08-20 20:15:00");
    private static final HODateTime NEW_DEADLINE = HODateTime.fromHT("2026-09-12 20:08:31");

    private static ScoutEintrag entry() {
        final var e = new ScoutEintrag();
        e.setPlayerID(476487492);
        e.setName("Bruno Gaspar");
        e.setPrice(new AmountOfMoney(1_500_000L));
        e.setDeadline(OLD_DEADLINE.toDbTimestamp());
        return e;
    }

    @Test
    void aRelistingMovesTheDeadlineAndThePriceForward() {
        final var e = entry();

        ScoutList.applyRelisting(e, 2_850_000L, 0L, NEW_DEADLINE);

        assertThat(e.getDeadline()).isEqualTo(NEW_DEADLINE.toDbTimestamp());
        assertThat(e.getPrice().getSwedishKrona().longValue()).isEqualTo(2_850_000L);
    }

    /** Same rule as a fresh search hit: the column means "what it would cost to take him now". */
    @Test
    void aStandingBidAboveTheAskIsWhatThePriceBecomes() {
        final var e = entry();

        ScoutList.applyRelisting(e, 2_850_000L, 3_100_000L, NEW_DEADLINE);

        assertThat(e.getPrice().getSwedishKrona().longValue()).isEqualTo(3_100_000L);
    }

    /**
     * {@code m_bWecker} is a has-already-rung latch, not a user preference - {@code ScoutThread}
     * fires only when it is false and sets it true, and nothing in src/main/java ever sets it back.
     * Left true, the alarm never fires again for the new deadline, and the watch is pointless.
     */
    @Test
    void aRelistingRearmsTheAlarm() {
        final var e = entry();
        e.setWecker(true);

        ScoutList.applyRelisting(e, 2_850_000L, 0L, NEW_DEADLINE);

        assertThat(e.isWecker()).isFalse();
    }

    /** He has not sold. recordSale owns those two columns. */
    @Test
    void aRelistingLeavesTheSaleColumnsAlone() {
        final var e = entry();

        ScoutList.applyRelisting(e, 2_850_000L, 0L, NEW_DEADLINE);

        assertThat(e.getSalePrice()).isNull();
        assertThat(e.getSoldAt()).isNull();
        assertThat(e.isSold()).isFalse();
    }
}
