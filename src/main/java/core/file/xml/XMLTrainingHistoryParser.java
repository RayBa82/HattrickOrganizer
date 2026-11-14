package core.file.xml;

import core.util.HOLogger;
import org.w3c.dom.Element;

import java.util.ArrayList;
import java.util.List;

/**
 * Parses a {@code training} response with {@code actionType=history} (version 2.3): one row per
 * change of training type, intensity or stamina share, oldest first. The first row is the
 * training the team had in the week of {@code fromDate}. Built like
 * {@link XMLTrainingEventsParser}: never throws, empty on anything unreadable.
 * <p>
 * {@code season} is the global Hattrick season, not the one local to the team's league, and
 * {@code week} its week 1-16.
 */
public final class XMLTrainingHistoryParser {

    public record TrainingChange(int season, int week, int trainingType, int intensity,
                                 int staminaShare, String trainingDate) {
    }

    /**
     * {@code available} is false when the answer carried no history at all - an error file such
     * as AccessDenied for a team without Gold supporter; an empty list with {@code available}
     * true means no training reports in the period.
     */
    public record TrainingHistory(boolean available, int teamId, String fromDate,
                                  List<TrainingChange> changes) {
    }

    private XMLTrainingHistoryParser() {
    }

    public static TrainingHistory parse(String xml) {
        final var doc = XMLManager.parseString(xml);
        final var root = doc == null ? null : doc.getDocumentElement();
        final var container = root == null ? null
                : (Element) root.getElementsByTagName("TrainingHistory").item(0);
        if (container == null) {
            return new TrainingHistory(false, 0, null, List.of());
        }
        final var changes = new ArrayList<TrainingChange>();
        final var items = container.getElementsByTagName("Training");
        for (int i = 0; i < items.getLength(); i++) {
            try {
                final var e = (Element) items.item(i);
                changes.add(new TrainingChange(
                        parseInt(XMLManager.xmlValue(e, "HattrickSeason")),
                        parseInt(XMLManager.xmlValue(e, "HattrickWeek")),
                        parseInt(XMLManager.xmlValue(e, "TrainingType")),
                        parseInt(XMLManager.xmlValue(e, "TrainingLevel")),
                        parseInt(XMLManager.xmlValue(e, "TrainingLevelStamina")),
                        XMLManager.xmlValue(e, "TrainingDate")));
            } catch (RuntimeException ex) {
                HOLogger.instance().log(XMLTrainingHistoryParser.class, ex);
            }
        }
        return new TrainingHistory(true, parseInt(XMLManager.xmlValue(root, "TeamID")),
                XMLManager.xmlValue(root, "FromDate"), changes);
    }

    private static int parseInt(String s) {
        return s == null || s.isBlank() ? 0 : Integer.parseInt(s.trim());
    }
}
