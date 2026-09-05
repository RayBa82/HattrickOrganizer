package core.net;

import core.util.AmountOfMoney;
import core.util.HODateTime;
import module.transfer.PlayerTransfer;
import module.transfer.scout.ScoutEintrag;
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

    private static ScoutEintrag entry(int playerId, String deadline) {
        final var e = new ScoutEintrag();
        e.setPlayerID(playerId);
        e.setDeadline(HODateTime.fromHT(deadline).toDbTimestamp());
        return e;
    }

    /**
     * The sale pass asks only about transfers that ended since it last ran, so the set is the
     * handful that expired between two downloads rather than the whole scout list. That is what
     * makes it complete: walked under the call cap instead, it spent every download on whichever
     * entries sat near the head of the list and never reached the rest - a player who had sold
     * twenty minutes earlier sat at position 85 of 92 and was skipped download after download.
     */
    @Test
    void onlyTransfersThatEndedSinceTheLastRunAreAskedAbout() {
        final var entries = List.of(
                entry(1, "2026-09-05 07:00:00"),      // asked about on an earlier run
                entry(2, "2026-09-05 11:30:00"),
                entry(3, "2026-09-05 18:00:00"));     // still running

        assertThat(TransferListingResolver.salesToAskAbout(
                entries, HODateTime.fromHT("2026-09-05 10:00:00"), NOW))
                .extracting(TransferListingResolver.Candidate::playerId)
                .containsExactly(2);
    }

    /**
     * Oldest first, because the watermark is a resume point: a run cut short by the cap parks it on
     * the first candidate it could not afford, and everything before that is done.
     */
    @Test
    void theSalePassIsOrderedSoATruncatedRunCanResume() {
        final var entries = List.of(
                entry(1, "2026-09-05 11:30:00"),
                entry(2, "2026-09-05 10:15:00"),
                entry(3, "2026-09-05 11:00:00"));

        assertThat(TransferListingResolver.salesToAskAbout(
                entries, HODateTime.fromHT("2026-09-05 10:00:00"), NOW))
                .extracting(TransferListingResolver.Candidate::playerId)
                .containsExactly(2, 3, 1);
    }

    /**
     * Inclusive of the watermark. The candidate a truncated run stopped on is the one the watermark
     * names, and it has to come back - asking twice costs a CHPP call, not asking loses the sale.
     */
    @Test
    void theCandidateTheWatermarkStoppedOnComesBack() {
        final var stoppedOn = HODateTime.fromHT("2026-09-05 10:15:00");

        assertThat(TransferListingResolver.salesToAskAbout(
                List.of(entry(2, "2026-09-05 10:15:00")), stoppedOn, NOW))
                .extracting(TransferListingResolver.Candidate::playerId)
                .containsExactly(2);
    }

    /** A sold entry is finished with, whichever pass is asking. */
    @Test
    void aSoldEntryIsNeverACandidate() {
        final var sold = entry(1, "2026-09-05 11:30:00");
        sold.setSalePrice(new AmountOfMoney(1_000_000L));

        assertThat(TransferListingResolver.salesToAskAbout(
                List.of(sold), HODateTime.fromHT("2026-09-05 10:00:00"), NOW)).isEmpty();
        assertThat(TransferListingResolver.relistingsToWatch(List.of(sold), NOW)).isEmpty();
    }

    /**
     * The watch keeps asking long after the sale question is closed, so its window is the wide one
     * and it is ordered newest-first: when the budget runs out it is the stalest entries that go
     * unasked, and a target withdrawn from the market yesterday is watched most closely.
     */
    @Test
    void theWatchSpansMonthsAndPutsTheFreshestExpiryFirst() {
        final var entries = List.of(
                entry(1, "2026-07-01 10:00:00"),      // two months ago, still watched
                entry(2, "2026-01-01 10:00:00"),      // past SCOUT_WATCH_GRACE, given up on
                entry(3, "2026-09-05 11:30:00"),
                entry(4, "2026-09-05 18:00:00"));     // still running

        assertThat(TransferListingResolver.relistingsToWatch(entries, NOW))
                .extracting(TransferListingResolver.Candidate::playerId)
                .containsExactly(3, 1);
    }

    /**
     * {@code ScoutEintrag} defaults its deadline to the moment it was created, so a hand-typed
     * entry looks expired the instant it is made - and a deadline that has been cleared entirely
     * must never become "ask about a sale at no particular date", which is how a player's
     * decade-old transfer ends up recorded as this entry's.
     */
    @Test
    void anEntryWithNoDeadlineIsNeverACandidate() {
        final var noDeadline = entry(1, "2026-09-05 11:30:00");
        noDeadline.setDeadline(null);

        assertThat(TransferListingResolver.salesToAskAbout(
                List.of(noDeadline), HODateTime.fromHT("2026-09-05 10:00:00"), NOW)).isEmpty();
        assertThat(TransferListingResolver.relistingsToWatch(List.of(noDeadline), NOW)).isEmpty();
    }
}

