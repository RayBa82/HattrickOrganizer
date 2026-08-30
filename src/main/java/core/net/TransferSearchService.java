package core.net;

import core.file.xml.XMLTransferSearchParser;
import core.util.HOLogger;

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
     * Search the transfer market on behalf of the user, who asked for it in HO's own UI.
     *
     * @param query the search parameters
     * @return one page of results
     * @throws SearchFailed if the query is invalid or the download failed
     */
    public XMLTransferSearchParser.Page search(TransferSearchQuery query) throws SearchFailed {
        final var validationError = query.validationErrorKey();
        if (validationError != null) {
            throw new SearchFailed(validationError, "Invalid transfer search: " + validationError);
        }
        try {
            return XMLTransferSearchParser.parse(fetch(query));
        } catch (Exception e) {
            HOLogger.instance().log(TransferSearchService.class, e);
            throw new SearchFailed("Error searching transfers: " + e.getMessage(), e);
        }
    }

    /** The CHPP call. Overridden in tests so that no network access is needed. */
    protected String fetch(TransferSearchQuery query) {
        return Connector.instance().getTransferSearch(query);
    }
}
