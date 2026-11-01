package core.net;

import core.file.xml.XMLTransferSearchParser;
import core.model.TranslationFacility;
import core.model.UserParameter;
import core.util.HOLogger;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Headless transfer market search.
 * <p>
 * The network and parse core of {@link OnlineWorker#searchTransfers(TransferSearchQuery)}, without
 * any Swing call: {@code setWaitInformation} and the error dialog stay in OnlineWorker, so this can
 * be called from a plain worker thread.
 * <p>
 * Searches are capped per rolling hour ({@link core.model.UserParameter#mcpSearchesPerSession})
 * so that no caller can scan the market in bulk. A window rather than a per-process count, since
 * the headless server runs for weeks and a process count would never come back.
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

    static final Duration WINDOW = Duration.ofHours(1);

    /** When the searches of the last {@link #WINDOW} went out, by either caller, oldest first. */
    private final Deque<Instant> recentSearches = new ArrayDeque<>();

    /**
     * Search the transfer market on behalf of an MCP client.
     * <p>
     * Refused only when the cap has no room left in the current window. The message says why, so the model
     * can act on it rather than retrying.
     */
    public XMLTransferSearchParser.Page searchForAgent(TransferSearchQuery query) throws SearchFailed {
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
            // The translated rule, not the bare key: an MCP client only ever sees this message,
            // and a key tells the model nothing about what to change.
            throw new SearchFailed(validationError,
                    "Invalid transfer search: " + TranslationFacility.tr(validationError));
        }
        synchronized (this) {
            final var cap = UserParameter.instance().mcpSearchesPerSession;
            final var now = now();
            while (!recentSearches.isEmpty() && !recentSearches.peekFirst().plus(WINDOW).isAfter(now)) {
                recentSearches.removeFirst();
            }
            if (recentSearches.size() >= cap) {
                // empty only with a cap of 0, which never frees up; the message then says "now"
                final var free = recentSearches.isEmpty() ? now : recentSearches.peekFirst().plus(WINDOW);
                throw new SearchFailed(null, "The transfer search cap of " + cap + " searches per "
                        + "hour is used up; the next one frees up in "
                        + Duration.between(now, free).toMinutes() + " minutes (" + free + "). Use the "
                        + "local corpus (live:false) or page earlier results by resultId meanwhile.");
            }
            recentSearches.addLast(now);
        }
        try {
            final var xml = fetch(query, silent);
            if (xml == null || xml.isBlank()) {
                // An empty answer is always a failed download, never a search without hits: a
                // search with no match still comes back as a well formed file with ItemCount 0.
                final var cause = Connector.instance().lastError();
                throw new SearchFailed(null, "Hattrick rejected the transfer search: "
                        + (cause == null ? "no answer, see HO's log." : cause));
            }
            return XMLTransferSearchParser.parse(xml);
        } catch (SearchFailed e) {
            throw e;
        } catch (Exception e) {
            HOLogger.instance().log(TransferSearchService.class, e);
            throw new SearchFailed("Error searching transfers: " + e.getMessage(), e);
        }
    }

    /** How many searches the cap still allows in the current window, for planning a sweep. */
    public int remainingSearches() {
        synchronized (this) {
            final var now = now();
            while (!recentSearches.isEmpty() && !recentSearches.peekFirst().plus(WINDOW).isAfter(now)) {
                recentSearches.removeFirst();
            }
            return Math.max(0, UserParameter.instance().mcpSearchesPerSession - recentSearches.size());
        }
    }

    /** The clock the window runs on. Overridden in tests. */
    protected Instant now() {
        return Instant.now();
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
