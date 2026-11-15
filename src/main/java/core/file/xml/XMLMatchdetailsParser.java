package core.file.xml;

import core.model.cup.CupLevel;
import core.model.cup.CupLevelIndex;
import core.model.enums.MatchType;
import core.model.match.*;
import core.util.HOLogger;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import core.util.XMLUtils;
import org.jetbrains.annotations.Nullable;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * @author thomas.werth
 */
public class XMLMatchdetailsParser {
    private static final String MATCH = "Match";

    /**
     * Utility class - private constructor enforces noninstantiability.
     */
    private XMLMatchdetailsParser() {
    }

    public static Matchdetails parseMatchdetailsFromString(String input, @Nullable MatchLineup matchLineup) {
        return createMatchdetails(XMLManager.parseString(input), matchLineup);
    }

    private static Matchdetails createMatchdetails(Document doc, MatchLineup matchLineup) {
        Matchdetails md = null;

        if (doc != null) {
            try {
                md = new Matchdetails();

                readGeneral(doc, md);
                readArena(doc, md);
                readGuestTeam(doc, md);
                readHomeTeam(doc, md);
                readInjuries(doc, md);
                readExpectedResult(doc, md);

                if (matchLineup != null) {
                    // Match lineup needs to be available, if not -> ignore match highlights/report
                    readHighlights(doc, md, matchLineup);
                    parseMatchReport(md);

                    var guest = matchLineup.getGuestTeam();
                    guest.setMatchTeamAttitude(MatchTeamAttitude.fromInt(md.getGuestEinstellung()));
                    guest.setMatchTacticType(MatchTacticType.fromInt(md.getGuestTacticType()));

                    var home = matchLineup.getHomeTeam();
                    home.setMatchTeamAttitude(MatchTeamAttitude.fromInt(md.getHomeEinstellung()));
                    home.setMatchTacticType(MatchTacticType.fromInt(md.getHomeTacticType()));
                }

                md.calculateHatStats();

            } catch (Exception e) {
                HOLogger.instance().log(XMLMatchdetailsParser.class, e);
                return null;
            }
        }

        return md;
    }

    /**
     * read the match injuries from XML
     *
     * @param doc XML document
     * @param md  match details
     *
     */
    private static void readInjuries(Document doc, Matchdetails md) {
        final ArrayList<Matchdetails.Injury> mdInjuries = new ArrayList<>();
        try {
            var root = doc.getDocumentElement();
            var matchElement = XMLUtils.getChildElement(root, MATCH);
            var injuriesElement = XMLUtils.findChildElement(matchElement, "Injuries");
            if (injuriesElement.isEmpty()) {
                return;
            }
            var injuryList = XMLUtils.getChildElements(injuriesElement.get(), "Injury");
            for (var injuryELement : injuryList) {
                var injuryPlayerID = XMLUtils.getIntValue(injuryELement, "InjuryPlayerID");
                var injuryTeamID = XMLUtils.getIntValue(injuryELement, "InjuryTeamID");
                var injuryType = XMLUtils.getIntValue(injuryELement, "InjuryType");
                var injuryMinute = XMLUtils.getIntValue(injuryELement, "InjuryMinute");
                var matchPart = XMLUtils.getIntValue(injuryELement, "MatchPart");
                var injury = new Matchdetails.Injury(injuryPlayerID, injuryTeamID, injuryType, injuryMinute, matchPart);
                mdInjuries.add(injury);
            }
            md.setInjuries(mdInjuries);
        } catch (
            Exception e) {
            HOLogger.instance().log(XMLMatchdetailsParser.class, e);
        }
    }

    private static void readHighlights(Document doc, Matchdetails md, MatchLineup lineup) {
        final ArrayList<MatchEvent> matchEvents = new ArrayList<>();
        try {
            //get Root element
            var root = doc.getDocumentElement();
            var matchElement = XMLUtils.getChildElement(root, MATCH);
            var eventListElement = XMLUtils.findChildElement(matchElement, "EventList");
            if (eventListElement.isEmpty()) {
                return;
            }
            for (var eventNode : XMLUtils.getChildElements(eventListElement.get(), "Event")) {
                matchEvents.add(readHighlight(eventNode, md, lineup));
            }
            md.setHighlights(matchEvents);
        } catch (Exception e) {
            HOLogger.instance().log(XMLMatchdetailsParser.class, e);
        }
    }

    /**
     * One {@code Event} of the EventList, with its player names looked up in the lineup.
     */
    private static MatchEvent readHighlight(Element eventNode, Matchdetails md, MatchLineup lineup) {
        //get values from xml
        var eventIndex = XMLUtils.getAttributeIntValue(eventNode, "Index");
        int iMinute = XMLUtils.getIntValue(eventNode, "Minute");
        var iSubjectPlayerID = XMLUtils.getIntValue(eventNode, "SubjectPlayerID");
        var iSubjectTeamID = XMLUtils.getIntValue(eventNode, "SubjectTeamID");
        var iObjectPlayerID = XMLUtils.getIntValue(eventNode, "ObjectPlayerID");
        var iMatchPart = XMLUtils.getIntValue(eventNode, "MatchPart");
        var iEventVariation = XMLUtils.getIntValue(eventNode, "EventVariation");
        var eventtext = unescape(XMLUtils.getStringValue(eventNode, "EventText", ""));

        // Convert the ID to type and subtype.
        int iMatchEventID = XMLUtils.getIntValue(eventNode, "EventTypeID");

        //get players
        boolean subHome = true;
        boolean objHome = true;
        MatchLineupPosition subjectPlayer = null;
        MatchLineupPosition objectPlayer = null;
        if (iMinute > 0) {
            subjectPlayer = lineup.getHomeTeam().getPlayerByID(iSubjectPlayerID, true);
            objectPlayer = lineup.getHomeTeam().getPlayerByID(iObjectPlayerID, true);
            if (subjectPlayer == null) {
                subjectPlayer = lineup.getGuestTeam().getPlayerByID(iSubjectPlayerID, true);
                subHome = false;
            }
            if (objectPlayer == null) {
                objectPlayer = lineup.getGuestTeam().getPlayerByID(iObjectPlayerID, true);
                objHome = false;
            }
        }

        //modify eventtext
        if (subjectPlayer != null) {
            eventtext = colorPlayerLinks(eventtext, iSubjectPlayerID, subHome, iObjectPlayerID, objHome);
        }

        //generate MatchEvent
        final MatchEvent myHighlight = new MatchEvent();
        myHighlight.setMatchEventIndex(eventIndex);
        myHighlight.setMatchEventID(iMatchEventID);
        myHighlight.setMinute(iMinute);
        myHighlight.setPlayerId(iSubjectPlayerID);
        myHighlight.setPlayerName(subjectPlayer != null ? subjectPlayer.getSpielerName() : "");
        myHighlight.setSpielerHeim(subHome);
        myHighlight.setTeamID(iSubjectTeamID);
        myHighlight.setAssistingPlayerId(iObjectPlayerID);
        myHighlight.setAssistingPlayerName(objectPlayer != null ? objectPlayer.getSpielerName() : "");
        myHighlight.setGehilfeHeim(objHome);
        myHighlight.setEventText(eventtext);
        myHighlight.setMatchPartId(MatchEvent.MatchPartId.fromMatchPartId(iMatchPart));
        myHighlight.setEventVariation(iEventVariation);

        if (myHighlight.getMatchEventID() == MatchEventID.UNKNOWN_MATCHEVENT) {
            HOLogger.instance().warning(XMLMatchdetailsParser.class, "Unknown event id found in match " +
                md.getHomeTeamName() + "-" + md.getGuestTeamName() +
                " in minute " + myHighlight.getMinute() +
                " text: " + myHighlight.getEventText());
        }
        myHighlight.setInjuryType(injuryTypeOf(iMatchEventID, iSubjectPlayerID, md));
        return myHighlight;
    }

    private static String unescape(String eventtext) {
        return eventtext.replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("/>", ">")
            .replace("&quot;", "\"")
            .replace("&amp;", "&");
    }

    /**
     * Replaces the player links of the subject and object player by coloured names (home blue, away red).
     */
    private static String colorPlayerLinks(String eventtext, int subjectPlayerId, boolean subHome, int objectPlayerId, boolean objHome) {
        final String subplayerColor = subHome ? "#000099" : "#990000";
        final String objplayerColor = objHome ? "#000099" : "#990000";
        boolean replaceend = false;
        var text = eventtext;
        if (text.contains(String.valueOf(subjectPlayerId))) {
            text = text.replaceAll("(?i)<A HREF=\"/Club/Players/Player\\.aspx\\?playerId="
                    + subjectPlayerId + ".*?>",
                "<FONT COLOR=" + subplayerColor + "#><B>");
            replaceend = true;
        }
        if (text.contains(String.valueOf(objectPlayerId))) {
            text = text.replaceAll("(?i)<A HREF=\"/Club/Players/Player\\.aspx\\?playerId="
                    + objectPlayerId + ".*?>",
                "<FONT COLOR=" + objplayerColor + "#><B>");
            replaceend = true;
        }
        if (replaceend) {
            text = text.replaceAll("(?i)</A>", "</B></FONT>");
        }
        return text;
    }

    private static Matchdetails.eInjuryType injuryTypeOf(int matchEventId, int subjectPlayerId, Matchdetails md) {
        if ((matchEventId == 90) || (matchEventId == 94)) {
            return Matchdetails.eInjuryType.BRUISE;
        } else if ((matchEventId == 91) || (matchEventId == 92) || (matchEventId == 93) || (matchEventId == 96)) {
            return Matchdetails.eInjuryType.INJURY;
        } else if ((matchEventId >= 401) && (matchEventId <= 422)) {
            return getInjuryType(subjectPlayerId, md.getInjuries());
        }
        return Matchdetails.eInjuryType.NA;
    }

    /**
     * parse match report from previously parsed highlights
     *
     * @param md	match details
     */
    private static void parseMatchReport(Matchdetails md) {
        List<MatchEvent> highlights = md.getHighlights();
        final StringBuilder report = new StringBuilder();
		for (MatchEvent highlight : highlights) {
			report.append(highlight.getEventText()).append(" ");
		}
        md.setMatchreport(report.toString());
    }

    private static void readArena(Document doc, Matchdetails md) {
        var root = doc.getDocumentElement();
        try {
            //Match data
            var matchElement = XMLUtils.getChildElement(root, MATCH);
            var arenaElement = XMLUtils.getChildElement(matchElement, "Arena");

            try {
                md.setArenaID(XMLUtils.getIntValue(arenaElement, "ArenaID"));
                md.setArenaName(XMLUtils.getStringValue(arenaElement, "ArenaName"));
            } catch (Exception e) {
                // This fails at tournament matches - ignore
            }

            var integerValue = XMLUtils.findIntegerValue(arenaElement, "WeatherID");
            integerValue.ifPresent(integer -> md.setWetterId(integer));
            integerValue = XMLUtils.findIntegerValue(arenaElement, "SoldTotal");
            integerValue.ifPresent(integer -> md.setZuschauer(integer));
            // Get spectator distribution, if available
            var soldTerraces = XMLUtils.findIntegerValue(arenaElement, "SoldTerraces");
            if (soldTerraces.isPresent()) {
                md.setSoldTerraces(soldTerraces.get());
                md.setSoldBasic(XMLUtils.getIntValue(arenaElement, "SoldBasic"));
                md.setSoldRoof(XMLUtils.getIntValue(arenaElement, "SoldRoof"));
                md.setSoldVIP(XMLUtils.getIntValue(arenaElement, "SoldVIP"));
            }
        } catch (Exception e) {
            HOLogger.instance().log(XMLMatchdetailsParser.class, e);
        }
    }

    private static void readGeneral(Document doc, Matchdetails md) {
        Element root = doc.getDocumentElement();

        try {
            md.setFetchDatumFromString(XMLUtils.getStringValue(root, "FetchedDate"));

            var matchElement = XMLUtils.getChildElement(root, MATCH);
            var iMatchType = XMLUtils.getIntValue(matchElement, "MatchType");
            var matchType = Objects.requireNonNull(MatchType.getById(iMatchType));
            md.setMatchType(matchType);

            if (matchType == MatchType.CUP) {
                md.setCupLevel(CupLevel.fromInt(XMLUtils.getIntValue(matchElement, "CupLevel")));
                md.setCupLevelIndex(CupLevelIndex.fromInt(XMLUtils.getIntValue(matchElement, "CupLevelIndex")));
            }
            md.setMatchID(XMLUtils.getIntValue(matchElement, "MatchID"));
            md.setSpielDatumFromString(XMLUtils.getStringValue(matchElement, "MatchDate"));
            md.setMatchContextId(XMLUtils.getIntValue(matchElement, "MatchContextId"));
            // WeatherID can come before kick-off; only FinishedDate marks a played match
            md.setFinished(XMLUtils.findChildElement(matchElement, "FinishedDate")
                    .map(e -> !e.getTextContent().isEmpty()).orElse(false));
        } catch (Exception e) {
            HOLogger.instance().log(XMLMatchdetailsParser.class, e);
        }
    }

    private static void readGuestTeam(Document doc, Matchdetails md) {
        try {
            var root = doc.getDocumentElement();
            var matchElement = XMLUtils.getChildElement(root, MATCH);
            var awayTeamElement = XMLUtils.getChildElement(matchElement, "AwayTeam");
            md.setAwayFormation(XMLUtils.getStringValue(awayTeamElement, "Formation", ""));
            md.setGastId(XMLUtils.getIntValue(awayTeamElement, "AwayTeamID"));
            md.setGastName(XMLUtils.getStringValue(awayTeamElement, "AwayTeamName"));
            md.setGuestGoals(XMLUtils.getIntValue(awayTeamElement, "AwayGoals", -1));
            md.setGuestTacticType(XMLUtils.getIntValue(awayTeamElement, "TacticType", -1));
            md.setGuestTacticSkill(XMLUtils.getIntValue(awayTeamElement, "TacticSkill", -1));
            md.setGuestMidfield(XMLUtils.getIntValue(awayTeamElement, "RatingMidfield", -1));
            md.setGuestRightDef(XMLUtils.getIntValue(awayTeamElement, "RatingRightDef", -1));
            md.setGuestMidDef(XMLUtils.getIntValue(awayTeamElement, "RatingMidDef", -1));
            md.setGuestLeftDef(XMLUtils.getIntValue(awayTeamElement, "RatingLeftDef", -1));
            md.setGuestRightAtt(XMLUtils.getIntValue(awayTeamElement, "RatingRightAtt", -1));
            md.setGuestMidAtt(XMLUtils.getIntValue(awayTeamElement, "RatingMidAtt", -1));
            md.setGuestLeftAtt(XMLUtils.getIntValue(awayTeamElement, "RatingLeftAtt", -1));
            md.setGuestRatingIndirectSetPiecesAtt(XMLUtils.getIntValue(awayTeamElement, "RatingIndirectSetPiecesAtt", -1));
            md.setGuestRatingIndirectSetPiecesDef(XMLUtils.getIntValue(awayTeamElement, "RatingIndirectSetPiecesDef", -1));
            md.setGuestEinstellung(XMLUtils.getIntValue(awayTeamElement, "TeamAttitude", Matchdetails.EINSTELLUNG_UNBEKANNT));
            readChancesAndReplays(awayTeamElement, md, false);
        } catch (Exception e) {
            HOLogger.instance().log(XMLMatchdetailsParser.class, e);
        }
    }

    private static void readHomeTeam(Document doc, Matchdetails md) {
        try {
            var root = doc.getDocumentElement();
            var matchElement = XMLUtils.getChildElement(root, MATCH);
            var homeTeamElement = XMLUtils.getChildElement(matchElement, "HomeTeam");
            md.setHomeFormation(XMLUtils.getStringValue(homeTeamElement, "Formation", ""));
            md.setHeimId(XMLUtils.getIntValue(homeTeamElement, "HomeTeamID"));
            md.setHeimName(XMLUtils.getStringValue(homeTeamElement, "HomeTeamName"));
            md.setHomeGoals(XMLUtils.getIntValue(homeTeamElement, "HomeGoals", -1));
            md.setHomeTacticType(XMLUtils.getIntValue(homeTeamElement, "TacticType", -1));
            md.setHomeTacticSkill(XMLUtils.getIntValue(homeTeamElement, "TacticSkill", -1));
            md.setHomeMidfield(XMLUtils.getIntValue(homeTeamElement, "RatingMidfield", -1));
            md.setHomeRightDef(XMLUtils.getIntValue(homeTeamElement, "RatingRightDef", -1));
            md.setHomeMidDef(XMLUtils.getIntValue(homeTeamElement, "RatingMidDef", -1));
            md.setHomeLeftDef(XMLUtils.getIntValue(homeTeamElement, "RatingLeftDef", -1));
            md.setHomeRightAtt(XMLUtils.getIntValue(homeTeamElement, "RatingRightAtt", -1));
            md.setHomeMidAtt(XMLUtils.getIntValue(homeTeamElement, "RatingMidAtt", -1));
            md.setHomeLeftAtt(XMLUtils.getIntValue(homeTeamElement, "RatingLeftAtt", -1));
            md.setHomeRatingIndirectSetPiecesAtt(XMLUtils.getIntValue(homeTeamElement, "RatingIndirectSetPiecesAtt", -1));
            md.setHomeRatingIndirectSetPiecesDef(XMLUtils.getIntValue(homeTeamElement, "RatingIndirectSetPiecesDef", -1));
            md.setHomeEinstellung(XMLUtils.getIntValue(homeTeamElement, "TeamAttitude", Matchdetails.EINSTELLUNG_UNBEKANNT));
            readChancesAndReplays(homeTeamElement, md, true);
        } catch (Exception e) {
            HOLogger.instance().log(XMLMatchdetailsParser.class, e);
        }
    }

    /**
     * NrOfChances (3.1) and HasReplay/NrOfReplays (3.2) of one team. All are absent from older
     * versions, and the replay pair is never sent for youth matches; absent stays null.
     */
    private static void readChancesAndReplays(Element teamElement, Matchdetails md, boolean home) {
        for (var type : ChanceType.values()) {
            md.setChances(home, type, optionalInt(teamElement, type.getXmlElement()));
        }
        final var hasReplay = XMLUtils.findChildElement(teamElement, "HasReplay")
                .map(e -> Boolean.parseBoolean(e.getTextContent().trim()))
                .orElse(null);
        final var nrOfReplays = optionalInt(teamElement, "NrOfReplays");
        if (home) {
            md.setHomeHasReplay(hasReplay);
            md.setHomeNrOfReplays(nrOfReplays);
        } else {
            md.setGuestHasReplay(hasReplay);
            md.setGuestNrOfReplays(nrOfReplays);
        }
    }

    /**
     * ExpectedResult (3.2), the statistics of the simulated replays. Hattrick sends it only for
     * the few matches the site shows it for, so its absence is the normal case.
     */
    private static void readExpectedResult(Document doc, Matchdetails md) {
        try {
            final var matchElement = XMLUtils.getChildElement(doc.getDocumentElement(), MATCH);
            final var element = XMLUtils.findChildElement(matchElement, "ExpectedResult");
            if (element.isEmpty()) {
                return;
            }
            final var e = element.get();
            final var result = new MatchExpectedResult();
            result.setMatchId(md.getMatchID());
            result.setMatchType(md.getMatchType());
            result.setNrOfSamples(XMLUtils.getIntValue(e, "NrOfSamples"));
            result.setHomeWins(XMLUtils.getIntValue(e, "HomeWins"));
            result.setDraws(XMLUtils.getIntValue(e, "Draws"));
            result.setAwayWins(XMLUtils.getIntValue(e, "AwayWins"));
            result.setAverageHomeGoals(optionalDouble(e, "AverageHomeGoals"));
            result.setAverageAwayGoals(optionalDouble(e, "AverageAwayGoals"));
            XMLUtils.findChildElement(e, "FullTime").ifPresent(fullTime -> {
                result.setFullTimeHomeWins(optionalInt(fullTime, "HomeWins"));
                result.setFullTimeDraws(optionalInt(fullTime, "Draws"));
                result.setFullTimeAwayWins(optionalInt(fullTime, "AwayWins"));
            });
            XMLUtils.findChildElement(e, "GoalDistribution").ifPresent(distribution -> {
                readGoalDistribution(distribution, "Home", true, result);
                readGoalDistribution(distribution, "Away", false, result);
            });
            XMLUtils.findChildElement(e, "AverageWinMargin").ifPresent(margin -> {
                result.setAverageWinMarginHome(optionalDouble(margin, "Home"));
                result.setAverageWinMarginAway(optionalDouble(margin, "Away"));
            });
            XMLUtils.findChildElement(e, "OverUnder").ifPresent(overUnder -> {
                for (int line = 0; line < MatchExpectedResult.GOAL_LINES; line++) {
                    final int index = line;
                    // Line05, Line15, ... Line45
                    XMLUtils.findChildElement(overUnder, "Line" + line + "5").ifPresent(l -> {
                        result.setOverUnder(true, index, optionalInt(l, "Over"));
                        result.setOverUnder(false, index, optionalInt(l, "Under"));
                    });
                }
            });
            md.setExpectedResult(result);
        } catch (Exception e) {
            HOLogger.instance().log(XMLMatchdetailsParser.class, e);
        }
    }

    private static void readGoalDistribution(Element distribution, String side, boolean home, MatchExpectedResult result) {
        XMLUtils.findChildElement(distribution, side).ifPresent(goals -> {
            for (int bucket = 0; bucket < MatchExpectedResult.GOAL_BUCKETS - 1; bucket++) {
                result.setGoals(home, bucket, optionalInt(goals, "Goals" + bucket));
            }
            result.setGoals(home, MatchExpectedResult.GOAL_BUCKETS - 1, optionalInt(goals, "GoalsMoreThan5"));
        });
    }

    /** The element's integer, null when it is absent or empty. */
    private static Integer optionalInt(Element parent, String tagName) {
        final var text = XMLUtils.getStringValue(parent, tagName, "").trim();
        return text.isEmpty() ? null : Integer.valueOf(text);
    }

    /** The element's decimal, null when it is absent or empty; tolerates a decimal comma. */
    private static Double optionalDouble(Element parent, String tagName) {
        final var text = XMLUtils.getStringValue(parent, tagName, "").trim();
        return text.isEmpty() ? null : Double.valueOf(text.replace(',', '.'));
    }

	private static Matchdetails.eInjuryType getInjuryType(int iPlayerID, ArrayList<Matchdetails.Injury> injuries) {
        for (Matchdetails.Injury injury : injuries) {
            if (injury.getInjuryPlayerID() == iPlayerID) {
                return injury.getInjuryType();
            }
        }
        HOLogger.instance().log(XMLMatchdetailsParser.class, "the injured player was not listed !!! This is not normal ");
        return Matchdetails.eInjuryType.NA;
    }
}
