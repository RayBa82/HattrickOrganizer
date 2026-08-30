package core.file.xml;

import core.util.HODateTime;
import org.w3c.dom.Element;

import java.util.ArrayList;
import java.util.List;

import static core.file.xml.XMLManager.*;

/**
 * Parser of the CHPP <code>transfersearch</code> file, version 1.1.
 * <p>
 * Money is kept as the plain number Hattrick sends, the caller wraps it into an
 * {@link core.util.AmountOfMoney} - that class reads the currency settings from the database on
 * class initialisation, which a parser should not depend on.
 */
public class XMLTransferSearchParser {

    /**
     * One page of search results. Hattrick returns an itemCount of -1 instead of the real number
     * as soon as more than 100 players match, as an analogy to "many".
     */
    public record Page(int itemCount, int pageSize, int pageIndex, List<Hit> hits) {}

    /**
     * A player on the transfer list. Details are null once the deadline has passed, Hattrick then
     * sends the market data below only.
     */
    public record Hit(int playerId, String playerName, int nativeCountryId, long askingPrice,
                      HODateTime deadline, long highestBid, String bidderTeamName,
                      int sellerTeamId, String sellerTeamName, int sellerLeagueId,
                      Details details) {}

    /** Everything Hattrick shows about a listed player, the skills included. */
    public record Details(int age, int ageDays, long salary, int tsi, int form, int experience,
                          int leadership, int specialty, int cards, int injuryLevel,
                          int stamina, int keeper, int playmaker, int scorer, int passing,
                          int winger, int defender, int setPieces) {}

    private XMLTransferSearchParser() {
    }

    public static Page parse(String xml) {
        final var doc = XMLManager.parseString(xml);
        if (doc == null) {
            return new Page(0, 0, 0, List.of());
        }

        final var root = doc.getDocumentElement();
        final var hits = new ArrayList<Hit>();
        final var results = root.getElementsByTagName("TransferResult");
        for (int i = 0; i < results.getLength(); i++) {
            hits.add(parseHit((Element) results.item(i)));
        }
        return new Page(xmlIntValue(root, "ItemCount"), xmlIntValue(root, "PageSize"),
                xmlIntValue(root, "PageIndex"), hits);
    }

    private static Hit parseHit(Element result) {
        final var details = container(result, "Details");
        final var bidder = container(result, "BidderTeam");
        final var seller = details != null ? container(details, "SellerTeam") : null;

        return new Hit(
                xmlIntValue(result, "PlayerId"),
                name(result),
                xmlIntValue(result, "NativeCountryID"),
                xmlLongValue(result, "AskingPrice"),
                xmlHODateTimeValue(result, "Deadline"),
                xmlLongValue(result, "HighestBid"),
                bidder != null ? xmlValue(bidder, "TeamName") : "",
                seller != null ? xmlIntValue(seller, "TeamID") : 0,
                seller != null ? xmlValue(seller, "TeamName") : "",
                seller != null ? xmlIntValue(seller, "LeagueId") : 0,
                details != null ? parseDetails(details) : null);
    }

    private static Details parseDetails(Element details) {
        return new Details(
                xmlIntValue(details, "Age"),
                xmlIntValue(details, "AgeDays"),
                xmlLongValue(details, "Salary"),
                xmlIntValue(details, "TSI"),
                xmlIntValue(details, "PlayerForm"),
                xmlIntValue(details, "Experience"),
                xmlIntValue(details, "Leadership"),
                xmlIntValue(details, "Specialty"),
                xmlIntValue(details, "Cards"),
                // healthy is -1, and the value is missing while a match of the player is running
                xmlIntValue(details, "InjuryLevel", -1),
                xmlIntValue(details, "StaminaSkill"),
                xmlIntValue(details, "KeeperSkill"),
                xmlIntValue(details, "PlaymakerSkill"),
                xmlIntValue(details, "ScorerSkill"),
                xmlIntValue(details, "PassingSkill"),
                xmlIntValue(details, "WingerSkill"),
                xmlIntValue(details, "DefenderSkill"),
                xmlIntValue(details, "SetPiecesSkill"));
    }

    /**
     * The named container element, or null if it is missing or empty. Hattrick sends the details
     * of an expired transfer and the bidder of an unbid player as an empty element rather than
     * leaving it out.
     */
    private static Element container(Element parent, String tagName) {
        final var element = (Element) parent.getElementsByTagName(tagName).item(0);
        if (element == null || element.getElementsByTagName("*").getLength() == 0) {
            return null;
        }
        return element;
    }

    private static String name(Element result) {
        final var firstName = xmlValue(result, "FirstName");
        final var nickName = xmlValue(result, "NickName");
        final var lastName = xmlValue(result, "LastName");
        if (nickName.isEmpty()) {
            return (firstName + " " + lastName).trim();
        }
        return firstName + " '" + nickName + "' " + lastName;
    }

    private static long xmlLongValue(Element element, String xmlKey) {
        final var value = XMLManager.xmlLongValue(element, xmlKey);
        return value != null ? value : 0L;
    }
}
