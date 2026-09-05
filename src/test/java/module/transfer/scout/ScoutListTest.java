package module.transfer.scout;

import core.util.HODateTime;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The guard that decides whether a transfer CHPP reports is the one a scout entry was watching.
 * Getting it wrong writes a price from an unrelated, possibly seasons-old transfer.
 */
class ScoutListTest {

    private static final Duration WINDOW = Duration.ofDays(2);
    private static final HODateTime DEADLINE = HODateTime.fromHT("2026-09-02 20:15:00");

    private static ScoutEintrag watching(HODateTime deadline) {
        final var entry = new ScoutEintrag();
        entry.setPlayerID(1001);
        entry.setDeadline(deadline == null ? null : deadline.toDbTimestamp());
        return entry;
    }

    @Test
    void acceptsASaleAtTheDeadline() {
        assertThat(ScoutList.isTheWatchedTransfer(watching(DEADLINE), DEADLINE, WINDOW)).isTrue();
    }

    @Test
    void acceptsASaleWithinTheWindowEitherSide() {
        assertThat(ScoutList.isTheWatchedTransfer(watching(DEADLINE),
                HODateTime.fromHT("2026-09-01 20:15:00"), WINDOW)).isTrue();
        assertThat(ScoutList.isTheWatchedTransfer(watching(DEADLINE),
                HODateTime.fromHT("2026-09-04 20:15:00"), WINDOW)).isTrue();
    }

    /**
     * The case that matters: a hand-typed entry keeps ScoutEintrag's default deadline of "now", so
     * it looks expired immediately. Its player's last transfer must not be taken as this sale.
     */
    @Test
    void rejectsASaleFarFromTheDeadline() {
        assertThat(ScoutList.isTheWatchedTransfer(watching(DEADLINE),
                HODateTime.fromHT("2026-05-02 20:15:00"), WINDOW)).isFalse();
        assertThat(ScoutList.isTheWatchedTransfer(watching(DEADLINE),
                HODateTime.fromHT("2026-11-02 20:15:00"), WINDOW)).isFalse();
    }

    @Test
    void rejectsWhenEitherDateIsMissing() {
        assertThat(ScoutList.isTheWatchedTransfer(watching(DEADLINE), null, WINDOW)).isFalse();
        assertThat(ScoutList.isTheWatchedTransfer(watching(null), DEADLINE, WINDOW)).isFalse();
    }
}
