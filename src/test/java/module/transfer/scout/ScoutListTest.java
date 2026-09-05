package module.transfer.scout;

import core.util.AmountOfMoney;
import core.util.HODateTime;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Two things {@link ScoutList} decides on its own, both pure: what a re-listing does to an entry,
 * and whether a transfer CHPP reports is the one that entry was watching. The persistence half
 * reaches for the Transfers tab when it is open, so only the field logic is unit-testable - which
 * is the half with the decisions in it.
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

    /**
     * A re-listing whose market data came back empty must not wipe the column. A hand-added entry
     * carries a price the user typed, and "free" is never a truthful reading of a missing
     * AskingPrice - {@code xmlLongValue} defaulting to 0 is not the seller giving him away.
     */
    @Test
    void aRelistingWithNoPriceInItLeavesTheOldPriceStanding() {
        final var e = entry();

        ScoutList.applyRelisting(e, 0L, 0L, NEW_DEADLINE);

        assertThat(e.getDeadline()).isEqualTo(NEW_DEADLINE.toDbTimestamp());
        assertThat(e.getPrice().getSwedishKrona().longValue()).isEqualTo(1_500_000L);
    }

    // ---- isTheWatchedTransfer: the guard that decides whether a transfer CHPP reports is the one
    // this entry was watching. Getting it wrong writes a price from an unrelated, possibly
    // seasons-old transfer - and the re-listing watch leans on it harder than ever, because once
    // applyRelisting has moved the deadline forward it can never match that sale again.

    private static final Duration WINDOW = Duration.ofDays(2);
    private static final HODateTime SALE_DEADLINE = HODateTime.fromHT("2026-09-02 20:15:00");

    private static ScoutEintrag watching(HODateTime deadline) {
        final var entry = new ScoutEintrag();
        entry.setPlayerID(1001);
        entry.setDeadline(deadline == null ? null : deadline.toDbTimestamp());
        return entry;
    }

    @Test
    void acceptsASaleAtTheDeadline() {
        assertThat(ScoutList.isTheWatchedTransfer(watching(SALE_DEADLINE), SALE_DEADLINE, WINDOW))
                .isTrue();
    }

    @Test
    void acceptsASaleWithinTheWindowEitherSide() {
        assertThat(ScoutList.isTheWatchedTransfer(watching(SALE_DEADLINE),
                HODateTime.fromHT("2026-09-01 20:15:00"), WINDOW)).isTrue();
        assertThat(ScoutList.isTheWatchedTransfer(watching(SALE_DEADLINE),
                HODateTime.fromHT("2026-09-04 20:15:00"), WINDOW)).isTrue();
    }

    /**
     * The case that matters: a hand-typed entry keeps ScoutEintrag's default deadline of "now", so
     * it looks expired immediately. Its player's last transfer must not be taken as this sale.
     */
    @Test
    void rejectsASaleFarFromTheDeadline() {
        assertThat(ScoutList.isTheWatchedTransfer(watching(SALE_DEADLINE),
                HODateTime.fromHT("2026-05-02 20:15:00"), WINDOW)).isFalse();
        assertThat(ScoutList.isTheWatchedTransfer(watching(SALE_DEADLINE),
                HODateTime.fromHT("2026-11-02 20:15:00"), WINDOW)).isFalse();
    }

    @Test
    void rejectsWhenEitherDateIsMissing() {
        assertThat(ScoutList.isTheWatchedTransfer(watching(SALE_DEADLINE), null, WINDOW)).isFalse();
        assertThat(ScoutList.isTheWatchedTransfer(watching(null), SALE_DEADLINE, WINDOW)).isFalse();
    }
}

