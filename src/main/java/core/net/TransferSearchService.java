package core.net;

import core.file.xml.XMLTransferSearchParser;
import core.model.UserParameter;
import core.util.HOLogger;

import java.time.Duration;
import java.time.Instant;

/**
 * Headless transfer market search.
 * <p>
 * The network and parse core of {@link OnlineWorker#searchTransfers(TransferSearchQuery)}, without
 * any Swing call: {@code setWaitInformation} and the error dialog stay in OnlineWorker, so this can
 * be called from a plain worker thread.
 * <p>
 * CHPP does not allow the transfer market to be scanned automatically. This class exposes no entry
 * point reachable from a timer, a {@link Thread} or a scheduled task, and nothing in HO may call it
 * from one.
 */
public class TransferSearchService {

    /** A search that did not produce a result page, with a readable reason for the caller to show. */
    public static class SearchFailed extends Exception {
        private final String errorKey;

        SearchFailed(String errorKey, String message) {
            super(message);
            this.errorKey = errorKey;
        }

        SearchFailed(String message, Throwable cause) {
            super(message, cause);
            this.errorKey = null;
        }

        /** The translation key of the message, or null if there is no translated form. */
        public String errorKey() {
            return errorKey;
        }
    }

    private static TransferSearchService instance;

    public static synchronized TransferSearchService instance() {
        if (instance == null) {
            instance = new TransferSearchService();
        }
        return instance;
    }

    /** Package private: production code uses {@link #instance()}, tests subclass. */
    TransferSearchService() {
    }

    /**
     * When the user's market search unlock expires. Null while locked. Deliberately in memory only:
     * an unlock must not survive a restart.
     */
    private Instant marketUnlockedUntil;

    /** Searches issued in this HO session, by either caller. Bounds total CHPP traffic. */
    private int searchesThisSession;

    /**
     * Open market search for {@link core.model.UserParameter#mcpMarketUnlockMinutes} minutes.
     * Only ever called from the options panel button - a human action in HO's own UI is the
     * precondition CHPP asks for.
     */
    public synchronized void unlockMarketSearch() {
        final var minutes = Math.max(1, UserParameter.instance().mcpMarketUnlockMinutes);
        marketUnlockedUntil = Instant.now().plus(Duration.ofMinutes(minutes));
    }

    /** Seconds left on the unlock window, 0 if market search is locked. */
    public synchronized long marketUnlockSecondsRemaining() {
        if (marketUnlockedUntil == null) {
            return 0;
        }
        final var remaining = Duration.between(Instant.now(), marketUnlockedUntil).toSeconds();
        return Math.max(remaining, 0);
    }

    /**
     * Search the transfer market on behalf of an MCP client.
     * <p>
     * Refuses unless the user has unlocked market search in HO, and unless the per-session cap has
     * room left. The message says why, so the model can act on it rather than retrying.
     */
    public XMLTransferSearchParser.Page searchForAgent(TransferSearchQuery query) throws SearchFailed {
        synchronized (this) {
            if (marketUnlockSecondsRemaining() == 0) {
                throw new SearchFailed(null, "Transfer market search is locked. Hattrick only allows "
                        + "the market to be searched on an explicit user request, so the user has to "
                        + "press \"unlock market search\" in HO's options under MCP server first.");
            }
        }
        // No Swing from this thread: Connector pops a modal error dialog on a CHPP error, which
        // blocks HO until a human clicks OK and leaves the MCP request hanging until it times out.
        return search(query, true);
    }

    /**
     * Search the transfer market on behalf of the user, who asked for it in HO's own UI.
     *
     * @param query the search parameters
     * @return one page of results
     * @throws SearchFailed if the query is invalid or the download failed
     */
    public XMLTransferSearchParser.Page search(TransferSearchQuery query) throws SearchFailed {
        return search(query, false);
    }

    private XMLTransferSearchParser.Page search(TransferSearchQuery query, boolean silent) throws SearchFailed {
        final var validationError = query.validationErrorKey();
        if (validationError != null) {
            throw new SearchFailed(validationError, "Invalid transfer search: " + validationError);
        }
        synchronized (this) {
            final var cap = UserParameter.instance().mcpSearchesPerSession;
            if (searchesThisSession >= cap) {
                throw new SearchFailed(null, "The transfer search cap of " + cap + " searches per HO "
                        + "session is used up. Raise it in HO's options under MCP server, or restart "
                        + "HO, before searching again.");
            }
            searchesThisSession++;
        }
        try {
            final var xml = fetch(query, silent);
            if (xml == null || xml.isBlank()) {
                // Silent downloads swallow the CHPP error into an empty answer; it is in the log.
                throw new SearchFailed(null, "Hattrick rejected the transfer search. "
                        + "The CHPP error is in HO's log.");
            }
            return XMLTransferSearchParser.parse(xml);
        } catch (SearchFailed e) {
            throw e;
        } catch (Exception e) {
            HOLogger.instance().log(TransferSearchService.class, e);
            throw new SearchFailed("Error searching transfers: " + e.getMessage(), e);
        }
    }

    /**
     * The CHPP call. Overridden in tests so that no network access is needed.
     *
     * @param silent suppress the modal error dialog Connector shows on a CHPP error - mandatory
     *               off the EDT, where it would block whoever called us
     */
    protected String fetch(TransferSearchQuery query, boolean silent) {
        if (!silent) {
            return Connector.instance().getTransferSearch(query);
        }
        final var wasSilent = Connector.instance().setSilentDownload(true);
        try {
            return Connector.instance().getTransferSearch(query);
        } finally {
            Connector.instance().setSilentDownload(wasSilent);
        }
    }
}
