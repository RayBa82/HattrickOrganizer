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

    /** How long after its deadline a scout entry is still worth asking about. */
    private static final java.time.Duration SCOUT_RETRY_GRACE = java.time.Duration.ofDays(14);

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
        var resolved = 0;
        var calls = 0;
        final var seen = new HashSet<Integer>();

        // The scout list first. It is short, it is what the user is actually waiting on, and its
        // entries are not necessarily in TRANSFERLISTING at all: only the MCP search snapshots hits
        // into that corpus, so a player added from HO's own transfer search tab or typed in by hand
        // would otherwise never be resolved by the loop below.
        for (final var playerId : scoutCandidates(now)) {
            if (calls >= cap) {
                break;
            }
            if (!seen.add(playerId)) {
                continue;
            }
            calls++;
            if (resolvePlayer(playerId, now)) {
                resolved++;
            }
        }

        // Then the valuation corpus, with whatever is left of the budget. Rows, not players: one
        // player repeatedly observed holds several. Over-fetch so a handful of duplicates cannot
        // starve the run, and let the call count be what the cap bounds.
        for (final var listing : DBManager.instance().loadUnresolvedTransferListings(cap * 5)) {
            if (calls >= cap) {
                break;
            }
            if (!seen.add(listing.getPlayerId())) {
                continue;                      // already handled this player in this run
            }
            calls += fillMissingSkills(listing, now);
            if (calls >= cap) {
                break;
            }
            if (listing.getDeadline() == null || listing.getDeadline().compareTo(now) >= 0) {
                continue;                      // not expired yet, nothing to resolve
            }
            calls++;
            if (resolvePlayer(listing.getPlayerId(), now)) {
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
    private static List<Integer> scoutCandidates(HODateTime now) {
        final var giveUpBefore = new HODateTime(now.getInstant().minus(SCOUT_RETRY_GRACE));
        final var ids = new ArrayList<Integer>();
        for (final var entry : ScoutList.entries()) {
            if (entry.isSold() || entry.getDeadline() == null) {
                continue;
            }
            final var deadline = HODateTime.fromDbTimestamp(entry.getDeadline());
            if (deadline.compareTo(now) < 0 && deadline.compareTo(giveUpBefore) > 0) {
                ids.add(entry.getPlayerID());
            }
        }
        return ids;
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
        try {
            final var player = OnlineWorker.downloadPlayerDetails(
                    String.valueOf(listing.getPlayerId()));
            if (player != null && player.getPlayerId() != 0) {
                DBManager.instance().storeTransferListing(
                        TransferListing.fromPlayerDetails(player, now));
            }
        } catch (Exception e) {
            HOLogger.instance().error(TransferListingResolver.class, e);
        }
        return 1;
    }

    /** @return whether a sale price was found and stored */
    private static boolean resolvePlayer(int playerId, HODateTime now) {
        final boolean found;
        try {
            found = applySale(playerId, XMLParser.updatePlayerTransfers(playerId));
        } catch (Exception e) {
            // The lookup failed rather than answered - leave the rows alone so a network blip does
            // not burn their one attempt.
            HOLogger.instance().error(TransferListingResolver.class, e);
            return false;
        }
        // The call answered. Whatever it did not account for - a listing that was withdrawn, or
        // expired with no bidder - is marked looked-at so it is never asked about again.
        DBManager.instance().markTransferListingResolveAttempted(playerId, now);
        return found;
    }

    private static boolean applySale(int playerId, List<module.transfer.PlayerTransfer> transfers) {
        if (transfers.isEmpty()) {
            return false;
        }
        final var sale = transfers.get(transfers.size() - 1);          // the most recent one
        if (sale.getPrice() == null || sale.getPrice().getSwedishKrona() == null) {
            return false;
        }
        final var salePriceSek = sale.getPrice().getSwedishKrona().longValue();
        DBManager.instance().storeTransferListingSalePrice(playerId, sale.getDate(),
                salePriceSek, SALE_WINDOW);
        recordOnScoutList(playerId, salePriceSek, sale.getDate());
        return true;
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
