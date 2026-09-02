package core.net;

import core.file.xml.XMLCHPPPreParser;
import core.file.xml.XMLChallengesParser;
import core.file.xml.XMLManager;
import core.file.xml.XMLTrainingParser;
import core.model.HOVerwaltung;
import core.model.UserParameter;
import core.util.HOLogger;
import org.w3c.dom.Document;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

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

    // ===================== challenges =====================

    private static final Set<String> RESPONSE_VERBS = Set.of("accept", "decline", "withdraw");

    /** A read. Shares the silent-fetch handling but does not count against the write cap. */
    public XMLChallengesParser.ChallengeLists listChallengesForAgent(boolean weekend)
            throws ChppWriteException {
        final Map<String, String> params = weekend
                ? Map.of("isWeekendFriendly", "1") : Map.of();
        final var xml = fetchChallenges("view", params);
        if (xml == null || xml.isBlank()) {
            throw new ChppWriteException(null, "Hattrick rejected the challenge list: " + reason());
        }
        return XMLChallengesParser.parse(xml);
    }

    public void arrangeChallengeForAgent(int opponentTeamId, int friendlyType, int matchPlace,
            Integer neutralArenaId, boolean weekend) throws ChppWriteException {
        if (matchPlace == 2 && neutralArenaId == null) {
            throw new ChppWriteException(null,
                    "A neutral-ground challenge needs neutralArenaId.");
        }
        checkAndCountWrite();
        final var params = new LinkedHashMap<String, String>();
        params.put("opponentTeamId", String.valueOf(opponentTeamId));
        params.put("matchType", String.valueOf(friendlyType));   // 0 normal rules, 1 cup rules
        params.put("matchPlace", String.valueOf(matchPlace));    // 0 home, 1 away, 2 neutral
        if (matchPlace == 2) {
            params.put("neutralArenaId", String.valueOf(neutralArenaId));
        }
        if (weekend) {
            params.put("isWeekendFriendly", "1");
        }
        requireAccepted(fetchChallenges("challenge", params), "arrange challenge");
    }

    public void respondToChallengeForAgent(String verb, long trainingMatchId)
            throws ChppWriteException {
        if (!RESPONSE_VERBS.contains(verb)) {
            throw new ChppWriteException(null,
                    "verb must be accept, decline or withdraw, was " + verb);
        }
        checkAndCountWrite();
        requireAccepted(
                fetchChallenges(verb, Map.of("trainingMatchId", String.valueOf(trainingMatchId))),
                "challenge " + verb);
    }

    /** No {@code <error>} block and a non-blank body means Hattrick took the write. */
    private static void requireAccepted(String xml, String what) throws ChppWriteException {
        if (xml == null || xml.isBlank()) {
            throw new ChppWriteException(null, "Hattrick rejected the " + what + ": " + reason());
        }
        final var error = XMLCHPPPreParser.getError(xml);
        if (!error.isEmpty()) {
            throw new ChppWriteException(null, "Hattrick rejected the " + what + ": " + error);
        }
    }

    protected String fetchChallenges(String actionType, Map<String, String> params) {
        final var was = Connector.instance().setSilentDownload(true);
        try {
            return Connector.instance().challenges(actionType, params);
        } finally {
            Connector.instance().setSilentDownload(was);
        }
    }
}
