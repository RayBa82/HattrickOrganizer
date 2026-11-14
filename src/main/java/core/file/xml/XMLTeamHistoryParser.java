package core.file.xml;

import core.util.HOLogger;
import org.w3c.dom.Element;

import java.util.ArrayList;
import java.util.List;

/**
 * Parses a {@code teamhistory} response (version 1.0, new in the 2026-11-10 changelog): a club's
 * league and cup result per season since its current owner took over, oldest first, and the
 * trophies won in that time. Never throws: empty on anything unreadable, and a row that does not
 * parse is logged and skipped.
 */
public final class XMLTeamHistoryParser {

    /**
     * One season. {@code cupRound} is the national cup round reached (the last one when the cup was
     * won), {@code cupRoundsLeft} the rounds still to go when knocked out, counting the lost one
     * (0 = won); both -1 without a national cup result.
     */
    public record Season(int season, int leagueLevelUnitId, String leagueLevelUnitName,
                         int leagueLevel, int position, int points, int goalsFor,
                         int goalsAgainst, int cupRound, int cupRoundsLeft) {
    }

    /** {@code trophyTypeId} as the trophyID data type; level and unit depend on the type. */
    public record Trophy(int trophyTypeId, int season, int leagueLevel, int leagueLevelUnitId,
                         String leagueLevelUnitName, String gainedDate) {
    }

    /** {@code teamId} 0 means the answer carried no team, e.g. an error file. */
    public record TeamHistory(int teamId, String teamName, String foundedDate,
                              List<Season> seasons, List<Trophy> trophies) {
    }

    private XMLTeamHistoryParser() {
    }

    public static TeamHistory parse(String xml) {
        final var doc = XMLManager.parseString(xml);
        final var team = doc == null ? null
                : (Element) doc.getDocumentElement().getElementsByTagName("Team").item(0);
        if (team == null) {
            return new TeamHistory(0, null, null, List.of(), List.of());
        }
        final var seasons = new ArrayList<Season>();
        final var seasonItems = team.getElementsByTagName("Season");
        for (int i = 0; i < seasonItems.getLength(); i++) {
            try {
                seasons.add(season((Element) seasonItems.item(i)));
            } catch (RuntimeException ex) {
                HOLogger.instance().log(XMLTeamHistoryParser.class, ex);
            }
        }
        final var trophies = new ArrayList<Trophy>();
        final var trophyItems = team.getElementsByTagName("Trophy");
        for (int i = 0; i < trophyItems.getLength(); i++) {
            try {
                trophies.add(trophy((Element) trophyItems.item(i)));
            } catch (RuntimeException ex) {
                HOLogger.instance().log(XMLTeamHistoryParser.class, ex);
            }
        }
        return new TeamHistory(
                parseInt(XMLManager.xmlValue(team, "TeamID"), 0),
                XMLManager.xmlValue(team, "TeamName"),
                XMLManager.xmlValue(team, "FoundedDate"),
                seasons, trophies);
    }

    private static Season season(Element e) {
        return new Season(
                parseInt(XMLManager.xmlValue(e, "SeasonNumber"), 0),
                parseInt(XMLManager.xmlValue(e, "LeagueLevelUnitID"), 0),
                XMLManager.xmlValue(e, "LeagueLevelUnitName"),
                parseInt(XMLManager.xmlValue(e, "LeagueLevel"), 0),
                parseInt(XMLManager.xmlValue(e, "Position"), 0),
                parseInt(XMLManager.xmlValue(e, "Points"), 0),
                parseInt(XMLManager.xmlValue(e, "GoalsFor"), 0),
                parseInt(XMLManager.xmlValue(e, "GoalsAgainst"), 0),
                parseInt(XMLManager.xmlValue(e, "CupRound"), -1),
                parseInt(XMLManager.xmlValue(e, "CupRoundsLeft"), -1));
    }

    private static Trophy trophy(Element e) {
        return new Trophy(
                parseInt(XMLManager.xmlValue(e, "TrophyTypeId"), 0),
                parseInt(XMLManager.xmlValue(e, "TrophySeason"), 0),
                parseInt(XMLManager.xmlValue(e, "LeagueLevel"), 0),
                parseInt(XMLManager.xmlValue(e, "LeagueLevelUnitId"), 0),
                XMLManager.xmlValue(e, "LeagueLevelUnitName"),
                XMLManager.xmlValue(e, "GainedDate"));
    }

    private static int parseInt(String s, int empty) {
        return s == null || s.isBlank() ? empty : Integer.parseInt(s.trim());
    }
}
