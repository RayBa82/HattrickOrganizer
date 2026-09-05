package core.file.xml;

import core.util.HOLogger;
import org.w3c.dom.Element;

import java.util.ArrayList;
import java.util.List;

/**
 * Parses a {@code currentbids} {@code view} response (version 1.0) - the transfers Hattrick is
 * tracking for a team: the ones it holds the leading bid on, the ones it has been outbid on,
 * bookmarked players, and recently finished transfers.
 * <p>
 * Built like {@link XMLChallengesParser}: a static {@code parse} that never throws and returns an
 * empty result on anything it cannot read. {@code deadline} is kept as the raw CHPP
 * {@code yyyy-MM-dd HH:mm:ss} string, same as {@link XMLChallengesParser} does with match times.
 * <p>
 * {@code trackingTypeId} is the category a transfer is grouped under. Only 5 / 8 / 9 are named by
 * the CHPP doc (hotlisted / losing bids / finished, the ones {@code ignoreTransfer} accepts); the
 * rest here (1 selling, 2 leading bid, 3 mother club, 4 previous club, 10 transfer prospect) are
 * carried straight through as the integer and are not CHPP-verified.
 */
public final class XMLCurrentBidsParser {

    /** One tracked transfer. {@code highestBid*} is all zero / empty when there are no bids yet. */
    public record BidItem(int trackingTypeId, long transferId, int playerId, String playerName,
                          long highestBidAmount, int highestBidTeamId, String highestBidTeamName,
                          String deadline) {
    }

    /** {@code teamId} is the team the bids are tracked for - use it to tell whose bid is leading. */
    public record CurrentBids(int teamId, List<BidItem> items) {
    }

    private XMLCurrentBidsParser() {
    }

    public static CurrentBids parse(String xml) {
        final var doc = XMLManager.parseString(xml);
        if (doc == null) {
            return new CurrentBids(0, List.of());
        }
        final var root = doc.getDocumentElement();
        // The first <TeamId> in document order is HattrickData/TeamId; the others sit inside
        // each <HighestBid> and come later.
        final var teamId = parseInt(XMLManager.xmlValue(root, "TeamId"));

        final var items = new ArrayList<BidItem>();
        final var groups = root.getElementsByTagName("BidItems");
        for (int g = 0; g < groups.getLength(); g++) {
            final var group = (Element) groups.item(g);
            final var trackingTypeId = parseInt(trackingTypeAttr(group));
            final var bidItems = group.getElementsByTagName("BidItem");
            for (int i = 0; i < bidItems.getLength(); i++) {
                try {
                    items.add(parseItem((Element) bidItems.item(i), trackingTypeId));
                } catch (RuntimeException e) {
                    HOLogger.instance().log(XMLCurrentBidsParser.class, e);
                }
            }
        }
        return new CurrentBids(teamId, items);
    }

    private static BidItem parseItem(Element item, int trackingTypeId) {
        // TeamId / TeamName also appear inside <HighestBid>, so read those from that element
        // rather than from <BidItem> (getElementsByTagName is recursive).
        final var highestBid = (Element) item.getElementsByTagName("HighestBid").item(0);
        return new BidItem(
                trackingTypeId,
                parseLong(XMLManager.xmlValue(item, "TransferId")),
                parseInt(XMLManager.xmlValue(item, "PlayerId")),
                XMLManager.xmlValue(item, "PlayerName"),
                parseLong(XMLManager.xmlValue(highestBid, "Amount")),
                parseInt(XMLManager.xmlValue(highestBid, "TeamId")),
                XMLManager.xmlValue(highestBid, "TeamName"),
                XMLManager.xmlValue(item, "Deadline"));
    }

    /**
     * The real CHPP response writes the attribute as {@code TrackingTypeID} (capital {@code ID},
     * like {@code TeamID} / {@code MatchID} elsewhere), not {@code TrackingTypeId}. Reading the
     * wrong casing gave every group id 0, so the whole {@code get_open_bids} default filter
     * dropped every row. Tolerate both spellings - no live round-trip to pin it down further.
     */
    private static String trackingTypeAttr(Element group) {
        final var v = group.getAttribute("TrackingTypeID");
        return v.isBlank() ? group.getAttribute("TrackingTypeId") : v;
    }

    private static long parseLong(String s) {
        return s == null || s.isBlank() ? 0L : Long.parseLong(s.trim());
    }

    private static int parseInt(String s) {
        return s == null || s.isBlank() ? 0 : Integer.parseInt(s.trim());
    }
}
