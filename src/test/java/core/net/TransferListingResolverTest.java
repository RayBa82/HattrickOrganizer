package core.net;

import core.util.AmountOfMoney;
import core.util.HODateTime;
import module.transfer.PlayerTransfer;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Picking the transfer that belongs to a given listing out of a player's whole history. This is
 * what decides whether a sale price is the right one, and it is also what stops a CHPP call when
 * the answer is already in HO's own transfer table.
 */
class TransferListingResolverTest {

    private static final HODateTime DEADLINE = HODateTime.fromHT("2026-09-02 20:15:00");

    private static PlayerTransfer transfer(String date, long priceSek) {
        final var t = new PlayerTransfer();
        t.setDate(HODateTime.fromHT(date));
        t.setPrice(new AmountOfMoney(priceSek));
        return t;
    }

    @Test
    void findsTheTransferAtTheDeadline() {
        final var sale = transfer("2026-09-02 20:15:00", 1_700_000L);

        assertThat(TransferListingResolver.saleAt(List.of(sale), DEADLINE)).isSameAs(sale);
    }

    /**
     * The list is newest-first, and an earlier version indexed the last element as "the most
     * recent" - which was the player's oldest transfer. Matching on the date is order-independent,
     * so this passes whichever way round the history arrives.
     */
    @Test
    void picksByDateNotByPosition() {
        final var older = transfer("2024-03-11 20:00:00", 250_000L);
        final var wanted = transfer("2026-09-02 20:15:00", 1_700_000L);
        final var oldest = transfer("2022-01-05 20:00:00", 90_000L);

        assertThat(TransferListingResolver.saleAt(List.of(wanted, older, oldest), DEADLINE))
                .isSameAs(wanted);
        assertThat(TransferListingResolver.saleAt(List.of(oldest, older, wanted), DEADLINE))
                .isSameAs(wanted);
    }

    @Test
    void toleratesSkewEitherSideOfTheDeadline() {
        assertThat(TransferListingResolver.saleAt(
                List.of(transfer("2026-09-01 20:15:00", 1L)), DEADLINE)).isNotNull();
        assertThat(TransferListingResolver.saleAt(
                List.of(transfer("2026-09-04 20:15:00", 1L)), DEADLINE)).isNotNull();
    }

    /** A player with a transfer history but none at this deadline: his listing did not sell. */
    @Test
    void returnsNullWhenNoTransferMatchesTheDeadline() {
        assertThat(TransferListingResolver.saleAt(
                List.of(transfer("2024-03-11 20:00:00", 250_000L)), DEADLINE)).isNull();
        assertThat(TransferListingResolver.saleAt(List.of(), DEADLINE)).isNull();
    }

    @Test
    void returnsNullWithoutADeadlineToMatchAgainst() {
        assertThat(TransferListingResolver.saleAt(
                List.of(transfer("2026-09-02 20:15:00", 1_700_000L)), null)).isNull();
    }

    private static final HODateTime NOW = HODateTime.fromHT("2026-09-05 12:00:00");

    /**
     * The sale question is asked whenever the entry's <em>old</em> deadline is still inside
     * SCOUT_RETRY_GRACE, whether or not he is listed again. "He is listed, so he did not sell" is
     * false - a buyer re-listing a player he has just bought is ordinary Hattrick behaviour - and
     * this is the last moment the sale can be recorded: the refresh moves the deadline forward and
     * isTheWatchedTransfer will never line up with that sale again.
     */
    @Test
    void aRelistingInsideTheRetryGraceStillAsksAboutTheSale() {
        final var recent = HODateTime.fromHT("2026-09-01 20:15:00");   // 4 days ago

        final var action = TransferListingResolver.scoutAction(true, recent, NOW);

        assertThat(action.askAboutSale()).isTrue();
        assertThat(action.refreshEntry()).isTrue();
    }

    @Test
    void pastTheRetryGraceTheWatchContinuesAlone() {
        final var old = HODateTime.fromHT("2026-08-01 20:15:00");      // 35 days ago

        final var action = TransferListingResolver.scoutAction(true, old, NOW);

        assertThat(action.askAboutSale()).isFalse();
        assertThat(action.refreshEntry()).isTrue();
    }

    @Test
    void anUnlistedPlayerIsAskedAboutButHisEntryIsLeftAlone() {
        final var recent = HODateTime.fromHT("2026-09-01 20:15:00");

        final var action = TransferListingResolver.scoutAction(false, recent, NOW);

        assertThat(action.askAboutSale()).isTrue();
        assertThat(action.refreshEntry()).isFalse();
    }

    /** Off the market and long past the retry grace: nothing left to do but keep watching. */
    @Test
    void anUnlistedPlayerPastTheRetryGraceCostsNothingBeyondTheLook() {
        final var old = HODateTime.fromHT("2026-08-01 20:15:00");

        final var action = TransferListingResolver.scoutAction(false, old, NOW);

        assertThat(action.askAboutSale()).isFalse();
        assertThat(action.refreshEntry()).isFalse();
    }

    /**
     * {@code scoutCandidates} filters these out, so this is a guard rather than a live path - but
     * the function is package-private and pure, and a null deadline must never become "ask about a
     * sale at no particular date", which is how a player's decade-old transfer ends up recorded as
     * this entry's.
     */
    @Test
    void aDeadlinelessCandidateIsNeverAskedAboutASale() {
        assertThat(TransferListingResolver.scoutAction(true, null, NOW).askAboutSale()).isFalse();
        assertThat(TransferListingResolver.scoutAction(true, null, NOW).refreshEntry()).isTrue();
    }
}
