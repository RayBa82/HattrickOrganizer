package core.net;

import core.file.xml.XMLCHPPPreParser;
import core.file.xml.XMLChallengesParser;
import core.file.xml.XMLCurrentBidsParser;
import core.file.xml.XMLManager;
import core.file.xml.XMLTrainingParser;
import core.model.HOVerwaltung;
import core.model.enums.MatchType;
import core.model.UserParameter;
import core.util.HOLogger;
import org.w3c.dom.Element;

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

        public ChppWriteException(String errorKey, String message) {
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

    /** Test-only: swap the singleton so a tool test can inject a stub. */
    public static synchronized void setInstanceForTest(ChppWriteService replacement) {
        instance = replacement;
    }

    /** Production code uses {@link #instance()}; tests subclass this (also from other packages). */
    protected ChppWriteService() {
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

    private static long parseLongOrZero(String s) {
        try {
            return s == null || s.isBlank() ? 0L : Long.parseLong(s.trim());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private static int currentTeamId() {
        final var model = HOVerwaltung.instance().getModel();
        return model != null ? model.getBasics().getTeamId() : 0;
    }

    // ===================== training =====================

    /**
     * The training state Hattrick echoed back after the set. {@code intensity} is the value that
     * will be in effect: the pending {@code NewTrainingLevel} when a level change has not taken
     * hold yet ({@code intensityChangePending} true), otherwise the current {@code TrainingLevel}.
     */
    public record TrainingCommitted(int trainingType, int intensity, int staminaShare,
                                    boolean intensityChangePending) {
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

        final var error = XMLCHPPPreParser.getError(xml);
        if (!error.isEmpty()) {
            // Missing set_training scope, out-of-range value, teamId not a managed senior team.
            throw new ChppWriteException(null, "Hattrick rejected the training change: " + error);
        }

        // The setTraining response is the training "view" output: <Team> carries the current
        // TrainingType / TrainingLevel / StaminaTrainingPart, plus NewTrainingLevel when a level
        // change is pending (training doc, version 2.2). There is no TrainingSet flag.
        final var echo = XMLTrainingParser.parseTrainingFromString(xml);
        if (!echo.containsKey("TrainingType") || !echo.containsKey("TrainingLevel")
                || !echo.containsKey("StaminaTrainingPart")) {
            throw new ChppWriteException(null,
                    "Hattrick did not echo the new training state: " + reason());
        }
        final var pendingLevel = echo.get("NewTrainingLevel");   // present only when Available=true
        return new TrainingCommitted(
                Integer.parseInt(echo.get("TrainingType").trim()),
                Integer.parseInt((pendingLevel != null ? pendingLevel
                        : echo.get("TrainingLevel")).trim()),
                Integer.parseInt(echo.get("StaminaTrainingPart").trim()),
                pendingLevel != null);
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

    // ===================== current bids =====================

    /** A read. Shares the silent-fetch handling but does not count against the write cap. */
    public XMLCurrentBidsParser.CurrentBids listCurrentBidsForAgent() throws ChppWriteException {
        final var xml = fetchCurrentBids();
        if (xml == null || xml.isBlank()) {
            throw new ChppWriteException(null,
                    "Hattrick rejected the current-bids list: " + reason());
        }
        return XMLCurrentBidsParser.parse(xml);
    }

    protected String fetchCurrentBids() {
        final var was = Connector.instance().setSilentDownload(true);
        try {
            return Connector.instance().currentBids();
        } finally {
            Connector.instance().setSilentDownload(was);
        }
    }

    // ===================== match order =====================

    public String sendMatchOrderForAgent(int matchId, MatchType matchType, String orderString)
            throws ChppWriteException {
        if (matchType == null) {
            throw new ChppWriteException(null, "Unknown matchType.");
        }
        checkAndCountWrite();
        try {
            final var response = fetchMatchOrder(matchId, matchType, orderString);
            if (response == null || response.isBlank()) {
                throw new ChppWriteException(null,
                        "Hattrick rejected the match order: " + reason());
            }
            return response;
        } catch (ChppWriteException e) {
            throw e;
        } catch (Exception e) {
            HOLogger.instance().log(ChppWriteService.class, e);
            throw new ChppWriteException(null,
                    "Hattrick rejected the match order: " + e.getMessage());
        }
    }

    protected String fetchMatchOrder(int matchId, MatchType matchType, String orderString)
            throws Exception {
        final var teamId = currentTeamId();
        final var was = Connector.instance().setSilentDownload(true);
        try {
            return Connector.instance().uploadMatchOrder(matchId, teamId, matchType, orderString, false);
        } finally {
            Connector.instance().setSilentDownload(was);
        }
    }

    // ===================== transfer bid =====================

    /**
     * {@code highestBid} is Swedish krona, the raw CHPP value; the tool layer converts to the user's
     * currency. {@code bidderTeamName} is the team currently holding the highest bid ("" if none).
     */
    public record BidResult(long highestBid, String deadline, String bidderTeamName) {
    }

    public BidResult placeBidForAgent(int playerId, long bidAmount, Long maxBidAmount)
            throws ChppWriteException {
        final var teamId = currentTeamId();
        checkAndCountWrite();

        final String xml;
        try {
            xml = fetchPlaceBid(playerId, teamId, bidAmount, maxBidAmount);
        } catch (Exception e) {
            HOLogger.instance().log(ChppWriteService.class, e);
            throw new ChppWriteException("BidFailed", "Error placing the bid: " + e.getMessage());
        }
        if (xml == null || xml.isBlank()) {
            throw new ChppWriteException("BidFailed", "Hattrick rejected the bid: " + reason());
        }
        final var error = XMLCHPPPreParser.getError(xml);
        if (!error.isEmpty()) {
            // placeBid fails for many reasons: not a Supporter, bid below the minimum, deadline
            // passed, already the highest bidder. The CHPP <error> carries which.
            throw new ChppWriteException("BidFailed", "Hattrick rejected the bid: " + error);
        }
        final var doc = XMLManager.parseString(xml);
        final var player = doc == null ? null
                : (Element) doc.getDocumentElement().getElementsByTagName("Player").item(0);
        if (player == null) {
            throw new ChppWriteException("BidFailed",
                    "Hattrick did not return the player after the bid: " + reason());
        }
        // The bid state lives under <Player><TransferDetails>, the leading bidder under <BidderTeam>
        // (playerdetails 3.2). getElementsByTagName is recursive, but <TeamName> is not unique in
        // the file, so read it from inside <BidderTeam>.
        final var bidderTeam = (Element) player.getElementsByTagName("BidderTeam").item(0);
        return new BidResult(
                parseLongOrZero(XMLManager.xmlValue(player, "HighestBid")),
                XMLManager.xmlValue(player, "Deadline"),
                bidderTeam == null ? "" : XMLManager.xmlValue(bidderTeam, "TeamName"));
    }

    protected String fetchPlaceBid(int playerId, int teamId, long bidAmount, Long maxBidAmount) {
        final var was = Connector.instance().setSilentDownload(true);
        try {
            return Connector.instance().placeBid(playerId, teamId, bidAmount, maxBidAmount);
        } finally {
            Connector.instance().setSilentDownload(was);
        }
    }
}
