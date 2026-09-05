package core.db;

import core.file.xml.XMLCurrentBidsParser.BidItem;
import core.util.HODateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * One transfer this team has bid on or is tracking, keyed on the player.
 * <p>
 * The ledger behind "what did the players I bid on actually sell for" - {@code ourBid} is what HO
 * placed through {@code place_bid}, the {@code latest*} fields are refreshed from {@code currentbids}
 * on every {@code get_open_bids}, and {@code finalPrice} / {@code outcome} are filled in once the
 * transfer finishes. One row per player: a re-listing overwrites the previous bid, the full
 * observation history stays in {@link TransferListing}.
 */
@Getter
@Setter
public class TransferBid extends AbstractTable.Storable {

    private int playerId;
    private String playerName;
    /** What HO bid through {@code place_bid}, Swedish krona. Null for a bid placed on the HT site. */
    private Long ourBid;
    private Long ourMaxBid;
    private HODateTime bidPlacedAt;
    private HODateTime deadline;
    private long latestHighestBid;
    private boolean latestLeading;
    private int latestTrackingTypeId;
    /** What the transfer sold for. Null until it finishes. */
    private Long finalPrice;
    /** {@code won} / {@code lost}, null while the transfer is still open. */
    private String outcome;
    private HODateTime firstSeenAt;
    private HODateTime lastSeenAt;

    public TransferBid() {
    }

    /** trackingTypeId 9 is "finished" (CHPP-named); 2 leading bid, 8 losing bid. */
    private static final int TRACKING_FINISHED = 9;

    /**
     * Merge one {@code currentbids} observation into the ledger row for its player.
     *
     * @param existing the current row, or null on the first sighting of this player
     * @param teamId   the team the bids are tracked for, to tell whether HO holds the leading bid
     */
    public static TransferBid fromObservation(TransferBid existing, BidItem item, int teamId,
                                              HODateTime now) {
        final var bid = existing != null ? existing : newRow(item, now);
        bid.playerName = item.playerName();
        bid.deadline = HODateTime.fromHT(item.deadline());
        bid.latestHighestBid = item.highestBidAmount();
        bid.latestLeading = item.highestBidTeamId() != 0 && item.highestBidTeamId() == teamId;
        bid.latestTrackingTypeId = item.trackingTypeId();
        bid.lastSeenAt = now;

        final var finished = item.trackingTypeId() == TRACKING_FINISHED
                && item.highestBidAmount() > 0
                && bid.deadline != null && bid.deadline.isBefore(now);
        if (finished && bid.finalPrice == null) {
            bid.finalPrice = item.highestBidAmount();
            bid.outcome = bid.latestLeading ? "won" : "lost";
        }
        return bid;
    }

    private static TransferBid newRow(BidItem item, HODateTime now) {
        final var bid = new TransferBid();
        bid.playerId = item.playerId();
        bid.firstSeenAt = now;
        return bid;
    }
}
