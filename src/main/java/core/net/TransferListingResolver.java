package core.net;

import core.db.DBManager;
import core.db.TransferListing;
import core.util.HODateTime;
import core.util.HOLogger;
import module.transfer.XMLParser;
import module.transfer.scout.ScoutList;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/**
 * Fills the real sale price into the transfer listing corpus, so a valuation has actual transfer
 * prices to work from rather than only asking prices and bids.
 * <p>
 * One {@code transfersPlayer} call per player answers "what did he actually go for", and
 * {@link XMLParser#updatePlayerTransfers} already makes exactly that call and stores the resulting
 * transfers in HO's own TRANSFER table - where the per-player transfer history UI reads them. So
 * this pass reuses it and takes the sale price out of what it returns, instead of fetching the same
 * file for itself and throwing the records away. Team-scoped views are unaffected: they filter on
 * buyerid / sellerid being the managed team, which a market player's transfer never is.
 * <p>
 * Runs after a team download and from the button in Options - never from a tool call, so an agent
 * still cannot start a batch of CHPP calls.
 */
public final class TransferListingResolver {

    /**
     * How much of a spread there may be between a listing's deadline and the sale date before the
     * two are taken to be different listings. A Hattrick transfer completes at its deadline, so
     * this only absorbs clock and timezone skew.
     */
    private static final java.time.Duration SALE_WINDOW = java.time.Duration.ofDays(2);

    /**
     * How long after a deadline a {@code playerdetails} fetch still describes the player that was
     * on the market. Past that he has been at his new club long enough to have trained, so filling
     * his skills in would file today's player against a months-old listing and let him vote in a
     * valuation under skills he did not have when he sold. No skills at all is the better answer:
     * a row with NULL skills cannot match {@code loadComparables}, so it stays out rather than
     * lying.
     */
    private static final java.time.Duration SKILL_FILL_GRACE = java.time.Duration.ofDays(14);

    /** How long after its deadline a scout entry is still worth asking about. */
    private static final java.time.Duration SCOUT_RETRY_GRACE = java.time.Duration.ofDays(14);

    /**
     * What the cap is actually protecting, and it is not a CHPP quota.
     * <p>
     * {@code transfersPlayer} is an ordinary read file: HO's own Transfers module already loops it
     * over every outdated player with no limit at all ({@code XMLParser.updateTeamTransfers},
     * {@code TransfersPanel}), and there is no rate limiting anywhere in {@code core.net}. The
     * limit that matters is that a download runs on the event dispatch thread, so every lookup is
     * time the UI is frozen. A count alone cannot express that - the same 200 calls are half a
     * minute on a fast link and five minutes on a bad one - so the run also stops on the clock.
     */
    private static final java.time.Duration TIME_BUDGET = java.time.Duration.ofSeconds(60);

    private TransferListingResolver() {
    }

    /**
     * @param cap the most CHPP calls this run may make; 0 or less does nothing
     * @return how many listings got a sale price
     */
    public static int resolve(int cap) {
        if (cap <= 0) {
            return 0;
        }
        final var now = HODateTime.now();
        final var deadline = System.nanoTime() + TIME_BUDGET.toNanos();
        var resolved = 0;
        var calls = 0;
        final var seen = new HashSet<Integer>();

        // The scout list first. It is short, it is what the user is actually waiting on, and its
        // entries are not necessarily in TRANSFERLISTING at all: only the MCP search snapshots hits
        // into that corpus, so a player added from HO's own transfer search tab or typed in by hand
        // would otherwise never be resolved by the loop below.
        for (final var candidate : scoutCandidates(now)) {
            if (spent(calls, cap, deadline)) {
                break;
            }
            if (!seen.add(candidate.playerId())) {
                continue;
            }
            final var outcome = resolvePlayer(candidate.playerId(), candidate.deadline(), now);
            calls += outcome.calls();
            if (outcome.resolved()) {
                resolved++;
            }
        }

        // Then the valuation corpus, with whatever is left of the budget. Rows, not players: one
        // player repeatedly observed holds several. Over-fetch so a handful of duplicates cannot
        // starve the run, and let the call count be what the cap bounds.
        for (final var listing : DBManager.instance().loadUnresolvedTransferListings(cap * 5)) {
            if (spent(calls, cap, deadline)) {
                break;
            }
            if (!seen.add(listing.getPlayerId())) {
                continue;                      // already handled this player in this run
            }
            calls += fillMissingSkills(listing, now);
            if (spent(calls, cap, deadline)) {
                break;
            }
            if (listing.getDeadline() == null || listing.getDeadline().compareTo(now) >= 0) {
                continue;                      // not expired yet, nothing to resolve
            }
            final var outcome = resolvePlayer(listing.getPlayerId(), listing.getDeadline(), now);
            calls += outcome.calls();
            if (outcome.resolved()) {
                resolved++;
            }
        }
        return resolved;
    }

    /**
     * Scouted players whose watched transfer has ended and whose sale price HO does not know.
     * <p>
     * Only for a while after the deadline. A scout entry has nowhere to record "looked, no sale"
     * the way a listing row does, so without a cut-off an entry that never sold - withdrawn, or no
     * bidder - would cost a CHPP call on every download for as long as it stayed on the list.
     * A completed transfer is visible immediately, so a fortnight is generous.
     */
    private static List<Candidate> scoutCandidates(HODateTime now) {
        final var giveUpBefore = new HODateTime(now.getInstant().minus(SCOUT_RETRY_GRACE));
        final var candidates = new ArrayList<Candidate>();
        for (final var entry : ScoutList.entries()) {
            if (entry.isSold() || entry.getDeadline() == null) {
                continue;
            }
            final var deadline = HODateTime.fromDbTimestamp(entry.getDeadline());
            if (deadline.compareTo(now) < 0 && deadline.compareTo(giveUpBefore) > 0) {
                candidates.add(new Candidate(entry.getPlayerID(), deadline));
            }
        }
        return candidates;
    }

    /** A scouted player and the deadline of the transfer that was being watched. */
    private record Candidate(int playerId, HODateTime deadline) {}

    /** Whether this run is out of budget - either bound stops it, whichever comes first. */
    private static boolean spent(int calls, int cap, long deadlineNanos) {
        return calls >= cap || System.nanoTime() - deadlineNanos >= 0;
    }

    /**
     * Give the corpus this player's skills if no snapshot of him carries any, so his price becomes
     * a usable comparable rather than a number with nothing attached.
     * <p>
     * A search hit arrives with the full skill block, so this is for the rows that do not: anything
     * from {@code currentbids} - bookmarks, mother-club, finished transfers, your own listings -
     * and the occasional search hit whose deadline passed before it was fetched, which Hattrick
     * returns stripped of everything but its market data.
     * <p>
     * Runs before the sale resolve, so a row cannot be marked looked-at while still skill-less.
     *
     * @return how many CHPP calls this made
     */
    private static int fillMissingSkills(TransferListing listing, HODateTime now) {
        if (DBManager.instance().transferListingHasSkillData(listing.getPlayerId())) {
            return 0;
        }
        if (listing.getDeadline() != null && listing.getDeadline().getInstant()
                .plus(SKILL_FILL_GRACE).isBefore(now.getInstant())) {
            return 0;                          // long sold; today's skills are not the sale's
        }
        try {
            final var m = OnlineWorker.downloadMarketPlayer(
                    String.valueOf(listing.getPlayerId()));
            if (m != null && m.player().getPlayerId() != 0 && m.transferListed()) {
                DBManager.instance().storeTransferListing(
                        TransferListing.fromPlayerDetails(m, now));
            }
        } catch (Exception e) {
            HOLogger.instance().error(TransferListingResolver.class, e);
        }
        return 1;
    }

    /** What one player cost us, and whether it produced a price. */
    private record Outcome(int calls, boolean resolved) {}

    /**
     * Fill in what this player's expired listing sold for.
     * <p>
     * Local first, always: HO's own TRANSFER table may already hold the very transfer being asked
     * about - certainly so if we won the auction, since a team download stores our own transfers,
     * and also whenever an earlier pass fetched this player's history. Spending a CHPP call on a
     * sale already sitting in the database is exactly the waste worth avoiding, and it would hit
     * hardest on the players we actually bought.
     */
    private static Outcome resolvePlayer(int playerId, HODateTime deadline, HODateTime now) {
        final var known = saleAt(DBManager.instance().getTransfers(playerId, true), deadline);
        if (known != null) {
            applySale(playerId, known);
            DBManager.instance().markTransferListingResolveAttempted(playerId, now);
            return new Outcome(0, true);
        }

        final List<module.transfer.PlayerTransfer> fetched;
        try {
            fetched = XMLParser.updatePlayerTransfers(playerId);
        } catch (Exception e) {
            // The lookup failed rather than answered - leave the rows alone so a network blip does
            // not burn their one attempt.
            HOLogger.instance().error(TransferListingResolver.class, e);
            return new Outcome(1, false);
        }
        final var sale = saleAt(fetched, deadline);
        if (sale != null) {
            applySale(playerId, sale);
        }
        // The call answered. Whatever it did not account for - a listing that was withdrawn, or
        // expired with no bidder - is marked looked-at so it is never asked about again.
        DBManager.instance().markTransferListingResolveAttempted(playerId, now);
        return new Outcome(1, sale != null);
    }

    /**
     * The transfer that completed at this deadline, or null if none of them did.
     * <p>
     * Searched rather than indexed. The previous version took {@code transfers.get(size - 1)} as
     * "the most recent", but {@link XMLParser#updatePlayerTransfers} treats that same element as
     * the player's *first* ever transfer when deciding whether he is homegrown - so the list is
     * newest-first and that index was the oldest transfer, not the newest. Matching on the date
     * instead does not care about the order at all.
     */
    static module.transfer.PlayerTransfer saleAt(
            List<module.transfer.PlayerTransfer> transfers, HODateTime deadline) {
        if (deadline == null) {
            return null;
        }
        final var from = deadline.getInstant().minus(SALE_WINDOW);
        final var to = deadline.getInstant().plus(SALE_WINDOW);
        for (final var transfer : transfers) {
            if (transfer.getDate() == null || transfer.getPrice() == null
                    || transfer.getPrice().getSwedishKrona() == null) {
                continue;
            }
            final var date = transfer.getDate().getInstant();
            if (!date.isBefore(from) && !date.isAfter(to)) {
                return transfer;
            }
        }
        return null;
    }

    private static void applySale(int playerId, module.transfer.PlayerTransfer sale) {
        final var salePriceSek = sale.getPrice().getSwedishKrona().longValue();
        DBManager.instance().storeTransferListingSalePrice(playerId, sale.getDate(),
                salePriceSek, SALE_WINDOW);
        recordOnScoutList(playerId, salePriceSek, sale.getDate());
    }

    /**
     * If the sold player was on the scout list, record what he went for. That list is a watch list
     * of targets, and without this it keeps showing the asking price of a transfer that is long
     * over. {@link ScoutList} ignores a sale that does not line up with the entry's deadline.
     * <p>
     * Best effort: a failure here must not undo a sale price that is already stored.
     */
    private static void recordOnScoutList(int playerId, long salePriceSek, HODateTime soldAt) {
        try {
            ScoutList.recordSale(playerId, salePriceSek, soldAt, SALE_WINDOW);
        } catch (Exception e) {
            HOLogger.instance().error(TransferListingResolver.class, e);
        }
    }
}
