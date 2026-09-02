package core.net;

import core.file.xml.XMLManager;
import core.file.xml.XMLTrainingParser;
import core.model.HOVerwaltung;
import core.model.UserParameter;
import core.util.HOLogger;
import org.w3c.dom.Document;

/**
 * The network and parse core of the CHPP write families HO exposes over MCP: set training, match
 * order upload, friendly challenges, and transfer bids.
 * <p>
 * Shaped exactly like {@link TransferSearchService}: a {@code fetch*} seam overridden in tests, a
 * per-session write cap ({@link UserParameter#mcpChppWritesPerSession}), and a typed
 * {@link ChppWriteException}. Every {@code fetch*} runs with {@link Connector#setSilentDownload}
 * on - a modal CHPP error dialog off the EDT would hang the MCP request until it times out
 * (see the spec, section 1.4).
 */
public class ChppWriteService {

    /** A CHPP write that Hattrick rejected or did not confirm, with a readable reason. */
    public static class ChppWriteException extends Exception {
        private final String errorKey;

        ChppWriteException(String errorKey, String message) {
            super(message);
            this.errorKey = errorKey;
        }

        /** The translation key of the message, or null if there is no translated form. */
        public String errorKey() {
            return errorKey;
        }
    }

    private static ChppWriteService instance;

    public static synchronized ChppWriteService instance() {
        if (instance == null) {
            instance = new ChppWriteService();
        }
        return instance;
    }

    /** Package private: production code uses {@link #instance()}, tests subclass. */
    ChppWriteService() {
    }

    /** Network writes issued in this HO session. Reads (challenge list, bid preview) do not count. */
    int writesThisSession;

    private synchronized void checkAndCountWrite() throws ChppWriteException {
        final var cap = UserParameter.instance().mcpChppWritesPerSession;
        if (writesThisSession >= cap) {
            throw new ChppWriteException(null, "The CHPP write cap of " + cap + " writes per HO "
                    + "session is used up. Raise it in HO's options under MCP server, or restart HO, "
                    + "before writing again.");
        }
        writesThisSession++;
    }

    /** {@link Connector#lastError()} if there is one, else a pointer to the log. */
    private static String reason() {
        final var cause = Connector.instance().lastError();
        return cause == null ? "no answer, see HO's log." : cause;
    }

    private static int currentTeamId() {
        final var model = HOVerwaltung.instance().getModel();
        return model != null ? model.getBasics().getTeamId() : 0;
    }

    // ===================== training =====================

    public record TrainingCommitted(int trainingType, int intensity, int staminaShare) {
    }

    public TrainingCommitted setTrainingForAgent(int trainingType, int intensity, int staminaShare)
            throws ChppWriteException {
        if (intensity < 0 || intensity > 100) {
            throw new ChppWriteException(null, "intensity must be 0..100, was " + intensity);
        }
        if (staminaShare < 5 || staminaShare > 100) {
            throw new ChppWriteException(null, "staminaShare must be 5..100, was " + staminaShare);
        }
        final var teamId = currentTeamId();
        checkAndCountWrite();

        final String xml;
        try {
            xml = fetchSetTraining(teamId, trainingType, intensity, staminaShare);
        } catch (Exception e) {
            HOLogger.instance().log(ChppWriteService.class, e);
            throw new ChppWriteException(null, "Error setting training: " + e.getMessage());
        }
        if (xml == null || xml.isBlank()) {
            throw new ChppWriteException(null, "Hattrick rejected the training change: " + reason());
        }

        final Document doc = XMLManager.parseString(xml);
        if (doc != null && "false".equalsIgnoreCase(
                XMLManager.xmlValue(doc.getDocumentElement(), "TrainingSet"))) {
            throw new ChppWriteException(null,
                    "Hattrick did not apply the training change (TrainingSet=false).");
        }

        final var echo = XMLTrainingParser.parseTrainingFromString(xml);
        if (!echo.containsKey("TrainingType") || !echo.containsKey("TrainingLevel")
                || !echo.containsKey("StaminaTrainingPart")) {
            throw new ChppWriteException(null,
                    "Hattrick did not echo the new training state: " + reason());
        }
        return new TrainingCommitted(
                Integer.parseInt(echo.get("TrainingType").trim()),
                Integer.parseInt(echo.get("TrainingLevel").trim()),
                Integer.parseInt(echo.get("StaminaTrainingPart").trim()));
    }

    /** The CHPP call. Overridden in tests so that no network access is needed. */
    protected String fetchSetTraining(int teamId, int trainingType, int trainingLevel, int staminaLevel) {
        final var was = Connector.instance().setSilentDownload(true);
        try {
            return Connector.instance().setTraining(teamId, trainingType, trainingLevel, staminaLevel);
        } finally {
            Connector.instance().setSilentDownload(was);
        }
    }
}
