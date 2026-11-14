package core.file.xml;

import core.util.HOLogger;
import org.w3c.dom.Element;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Parses a {@code playerstats} response (version 1.0): one player's state and performance in
 * every match Hattrick keeps for him. Built like {@link XMLTrainingEventsParser}: never throws,
 * empty on anything unreadable, and a match row that does not parse is logged and skipped.
 * <p>
 * Stamina and loyalty are empty when Hattrick has no value (stamina when the player was not in
 * the lineup), so they are nullable. Positions come grouped: central defenders, inner midfielders
 * and forwards each under the code of their right-side position (102, 107, 111).
 */
public final class XMLPlayerStatsParser {

    /** {@code positions} maps a MatchPositionID to the minutes played there, in document order. */
    public record MatchStat(int matchId, int matchType, String sourceSystem, String matchDate,
                            String homeTeamName, String awayTeamName, boolean walkover,
                            int age, int ageDays, int tsi, int form, Integer stamina,
                            Integer loyalty, int experience, int goals, int yellowCards,
                            int redCards, int injuryLevel, int playedMinutes, double rating,
                            double ratingEndOfGame, Map<Integer, Integer> positions) {
    }

    /** {@code playerId} 0 means the answer carried no player, e.g. an error file. */
    public record PlayerStats(int playerId, String firstName, String nickName, String lastName,
                              int owningTeamId, String owningTeamName, List<MatchStat> matches) {
    }

    private XMLPlayerStatsParser() {
    }

    public static PlayerStats parse(String xml) {
        final var doc = XMLManager.parseString(xml);
        final var player = doc == null ? null
                : (Element) doc.getDocumentElement().getElementsByTagName("Player").item(0);
        if (player == null) {
            return new PlayerStats(0, null, null, null, 0, null, List.of());
        }
        final var team = (Element) player.getElementsByTagName("OwningTeam").item(0);
        final var matches = new ArrayList<MatchStat>();
        final var items = player.getElementsByTagName("Match");
        for (int i = 0; i < items.getLength(); i++) {
            try {
                matches.add(match((Element) items.item(i)));
            } catch (RuntimeException ex) {
                HOLogger.instance().log(XMLPlayerStatsParser.class, ex);
            }
        }
        return new PlayerStats(
                parseInt(XMLManager.xmlValue(player, "PlayerID")),
                XMLManager.xmlValue(player, "FirstName"),
                XMLManager.xmlValue(player, "NickName"),
                XMLManager.xmlValue(player, "LastName"),
                team == null ? 0 : parseInt(XMLManager.xmlValue(team, "TeamID")),
                team == null ? null : XMLManager.xmlValue(team, "TeamName"),
                matches);
    }

    private static MatchStat match(Element e) {
        final var positions = new LinkedHashMap<Integer, Integer>();
        final var items = e.getElementsByTagName("Position");
        for (int i = 0; i < items.getLength(); i++) {
            final var p = (Element) items.item(i);
            positions.put(parseInt(XMLManager.xmlValue(p, "PositionCode")),
                    parseInt(XMLManager.xmlValue(p, "Minutes")));
        }
        return new MatchStat(
                parseInt(XMLManager.xmlValue(e, "MatchID")),
                parseInt(XMLManager.xmlValue(e, "MatchType")),
                XMLManager.xmlValue(e, "SourceSystem"),
                XMLManager.xmlValue(e, "MatchDate"),
                XMLManager.xmlValue(e, "HomeTeamName"),
                XMLManager.xmlValue(e, "AwayTeamName"),
                "true".equalsIgnoreCase(XMLManager.xmlValue(e, "IsWalkover")),
                parseInt(XMLManager.xmlValue(e, "Age")),
                parseInt(XMLManager.xmlValue(e, "AgeDays")),
                parseInt(XMLManager.xmlValue(e, "TSI")),
                parseInt(XMLManager.xmlValue(e, "PlayerForm")),
                parseInteger(XMLManager.xmlValue(e, "Stamina")),
                parseInteger(XMLManager.xmlValue(e, "Loyalty")),
                parseInt(XMLManager.xmlValue(e, "Experience")),
                parseInt(XMLManager.xmlValue(e, "GoalsInMatch")),
                parseInt(XMLManager.xmlValue(e, "YellowCardsInMatch")),
                parseInt(XMLManager.xmlValue(e, "RedCardsInMatch")),
                parseInt(XMLManager.xmlValue(e, "InjuryLevel")),
                parseInt(XMLManager.xmlValue(e, "PlayedMinutes")),
                parseDouble(XMLManager.xmlValue(e, "Rating")),
                parseDouble(XMLManager.xmlValue(e, "RatingEndOfGame")),
                positions);
    }

    private static int parseInt(String s) {
        final var value = parseInteger(s);
        return value == null ? 0 : value;
    }

    private static Integer parseInteger(String s) {
        return s == null || s.isBlank() ? null : Integer.valueOf(s.trim());
    }

    private static double parseDouble(String s) {
        return s == null || s.isBlank() ? 0 : Double.parseDouble(s.trim().replace(',', '.'));
    }
}
