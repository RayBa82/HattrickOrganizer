package core.file.xml;

import core.util.HOLogger;
import org.w3c.dom.Element;

import java.util.ArrayList;
import java.util.List;

/**
 * Parses a {@code trainingevents} response (version 1.3): Hattrick's own record of a player's
 * skill changes. Built like {@link XMLChallengesParser}: never throws, empty on anything unreadable.
 * <p>
 * {@code skillId} is the CHPP SkillID (1 keeper ... 11 experience), not HO's PlayerSkill id.
 * {@code season} / {@code matchRound} / {@code dayNumber} are relative to the league of the
 * player's team, so a local season, not the global one.
 */
public final class XMLTrainingEventsParser {

    public record TrainingEvent(int skillId, int oldLevel, int newLevel, int season, int matchRound,
                                int dayNumber) {
    }

    /** {@code available} is false while the player is in a match - Hattrick hides the events then. */
    public record TrainingEvents(boolean available, List<TrainingEvent> events) {
    }

    private XMLTrainingEventsParser() {
    }

    public static TrainingEvents parse(String xml) {
        final var doc = XMLManager.parseString(xml);
        final var container = doc == null ? null
                : (Element) doc.getDocumentElement().getElementsByTagName("TrainingEvents").item(0);
        if (container == null) {
            return new TrainingEvents(false, List.of());
        }
        final var events = new ArrayList<TrainingEvent>();
        final var items = container.getElementsByTagName("TrainingEvent");
        for (int i = 0; i < items.getLength(); i++) {
            try {
                final var e = (Element) items.item(i);
                events.add(new TrainingEvent(
                        parseInt(XMLManager.xmlValue(e, "SkillID")),
                        parseInt(XMLManager.xmlValue(e, "OldLevel")),
                        parseInt(XMLManager.xmlValue(e, "NewLevel")),
                        parseInt(XMLManager.xmlValue(e, "Season")),
                        parseInt(XMLManager.xmlValue(e, "MatchRound")),
                        parseInt(XMLManager.xmlValue(e, "DayNumber"))));
            } catch (RuntimeException ex) {
                HOLogger.instance().log(XMLTrainingEventsParser.class, ex);
            }
        }
        return new TrainingEvents("true".equalsIgnoreCase(container.getAttribute("Available")), events);
    }

    private static int parseInt(String s) {
        return s == null || s.isBlank() ? 0 : Integer.parseInt(s.trim());
    }
}
