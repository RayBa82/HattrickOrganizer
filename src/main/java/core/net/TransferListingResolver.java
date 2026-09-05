package core.net;

import core.db.DBManager;
import core.db.TransferListing;
import core.util.HODateTime;
import core.util.HOLogger;
import module.transfer.XMLParser;

import java.util.HashSet;

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

        // Rows, not players: one player repeatedly observed holds several. Over-fetch so a handful
        // of duplicates cannot starve the run, and let the call count be what the cap bounds.
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
            if (storeSalePrice(listing.getPlayerId())) {
                resolved++;
            }
        }
        return resolved;
    }

    /**
     * A player HO bid on but never searched for has no skills on any snapshot. One
     * {@code playerdetails} call gives the corpus his skills, so his sale price becomes a usable
     * comparable rather than a price with nothing attached.
     *
     * @return how many CHPP calls this made
     */
    private static int fillMissingSkills(TransferListing listing, HODateTime now) {
        if (DBManager.instance().transferListingHasSkillData(listing.getPlayerId())
                || DBManager.instance().loadTransferBid(listing.getPlayerId()) == null) {
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
    private static boolean storeSalePrice(int playerId) {
        try {
            final var transfers = XMLParser.updatePlayerTransfers(playerId);
            if (transfers.isEmpty()) {
                return false;
            }
            final var sale = transfers.get(transfers.size() - 1);      // the most recent one
            if (sale.getPrice() == null || sale.getPrice().getSwedishKrona() == null) {
                return false;
            }
            DBManager.instance().storeTransferListingSalePrice(playerId, sale.getDate(),
                    sale.getPrice().getSwedishKrona().longValue(), SALE_WINDOW);
            return true;
        } catch (Exception e) {
            HOLogger.instance().error(TransferListingResolver.class, e);
            return false;
        }
    }
}
