package core.file.xml;

import core.util.HOLogger;
import org.w3c.dom.Element;

import java.util.ArrayList;
import java.util.List;

/**
 * Parses a {@code challenges} {@code view} response (version 1.6) into the outgoing challenges
 * this team has sent and the offers other teams have sent it.
 * <p>
 * Built like {@link XMLTransferSearchParser}: a static {@code parse} that never throws and returns
 * empty lists on anything it cannot read.
 */
public class XMLChallengesParser {

    /**
     * One challenge or offer. {@code matchId} is 0 until the challenge is agreed, then it is the
     * real match you can pass to {@code get_match} / {@code send_match_order}. {@code matchTime} is
     * the raw CHPP value (a {@code yyyy-MM-dd HH:mm:ss} string in practice, though the schema types
     * it as an integer).
     */
    public record Challenge(long trainingMatchId, long matchId, String matchTime, int friendlyType,
                            int opponentTeamId, String opponentName, String arenaName,
                            boolean isAgreed) {
    }

    public record ChallengeLists(List<Challenge> outgoing, List<Challenge> incoming) {
    }

    /** One team from a {@code challengeable} answer. Hattrick gives no reason when it is false. */
    public record Challengeable(int teamId, String teamName, int userId, boolean isChallengeable) {
    }

    private XMLChallengesParser() {
    }

    public static ChallengeLists parse(String xml) {
        final var doc = XMLManager.parseString(xml);
        if (doc == null) {
            return new ChallengeLists(List.of(), List.of());
        }
        final var root = doc.getDocumentElement();
        return new ChallengeLists(
                parseList(root, "ChallengesByMe", "Challenge"),
                parseList(root, "OffersByOthers", "Offer"));
    }

    /** A {@code challengeable} response: {@code ChallengeableResult/Opponent}, one per team asked. */
    public static List<Challengeable> parseChallengeable(String xml) {
        final var result = new ArrayList<Challengeable>();
        final var doc = XMLManager.parseString(xml);
        final var container = doc == null ? null
                : (Element) doc.getDocumentElement().getElementsByTagName("ChallengeableResult").item(0);
        if (container == null) {
            return result;
        }
        final var items = container.getElementsByTagName("Opponent");
        for (int i = 0; i < items.getLength(); i++) {
            try {
                final var o = (Element) items.item(i);
                // Tolerant of casing: real CHPP responses do not always match the docs
                // (currentbids writes TrackingTypeID where the docs say TrackingTypeId).
                result.add(new Challengeable(
                        parseInt(anyCase(o, "TeamID", "TeamId")),
                        XMLManager.xmlValue(o, "TeamName"),
                        parseInt(anyCase(o, "UserId", "UserID")),
                        "true".equalsIgnoreCase(anyCase(o, "IsChallengeable", "IsChallengable"))));
            } catch (RuntimeException e) {
                HOLogger.instance().log(XMLChallengesParser.class, e);
            }
        }
        return result;
    }

    private static String anyCase(Element e, String name, String alt) {
        final var v = XMLManager.xmlValue(e, name);
        return v == null || v.isBlank() ? XMLManager.xmlValue(e, alt) : v;
    }

    private static List<Challenge> parseList(Element root, String containerTag, String itemTag) {
        final var result = new ArrayList<Challenge>();
        final var container = (Element) root.getElementsByTagName(containerTag).item(0);
        if (container == null) {
            return result;
        }
        final var items = container.getElementsByTagName(itemTag);
        for (int i = 0; i < items.getLength(); i++) {
            try {
                result.add(parseItem((Element) items.item(i)));
            } catch (RuntimeException e) {
                HOLogger.instance().log(XMLChallengesParser.class, e);
            }
        }
        return result;
    }

    private static Challenge parseItem(Element item) {
        // TrainingMatchID / MatchID / MatchTime / FriendlyType / IsAgreed and the <Arena> block are
        // direct children of <Challenge>/<Offer>; only TeamID/TeamName sit inside <Opponent>.
        final var opponent = (Element) item.getElementsByTagName("Opponent").item(0);
        return new Challenge(
                parseLong(XMLManager.xmlValue(item, "TrainingMatchID")),
                parseLong(XMLManager.xmlValue(item, "MatchID")),
                XMLManager.xmlValue(item, "MatchTime"),
                parseInt(XMLManager.xmlValue(item, "FriendlyType")),
                opponent == null ? 0 : parseInt(XMLManager.xmlValue(opponent, "TeamID")),
                opponent == null ? "" : XMLManager.xmlValue(opponent, "TeamName"),
                XMLManager.xmlValue(item, "ArenaName"),
                "true".equalsIgnoreCase(XMLManager.xmlValue(item, "IsAgreed")));
    }

    private static long parseLong(String s) {
        return s == null || s.isBlank() ? 0L : Long.parseLong(s.trim());
    }

    private static int parseInt(String s) {
        return s == null || s.isBlank() ? 0 : Integer.parseInt(s.trim());
    }
}
