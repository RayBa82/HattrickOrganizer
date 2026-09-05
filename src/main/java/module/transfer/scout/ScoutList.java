package module.transfer.scout;

import core.db.DBManager;
import core.gui.HOMainFrame;
import core.module.IModule;
import core.util.AmountOfMoney;
import core.util.HODateTime;

import java.util.List;
import java.util.Vector;

/**
 * Reaching HO's transfer scout list from outside the Transfers module.
 * <p>
 * The list lives in one of two places and which one is the authority depends on the UI:
 * <ul>
 * <li><b>Transfers tab open</b> - the panel's in-memory {@code Vector} wins.
 *     {@link TransferScoutPanel#saveScoutListe()} is a delete-all-then-insert of it and
 *     {@code HOMainFrame.shutdown()} runs that at exit, so a row written straight to the SCOUT
 *     table behind an open tab is silently dropped hours later.</li>
 * <li><b>Transfers tab closed</b> - the table wins. There is no in-memory list to disagree with,
 *     and the panel loads from the table when the user opens it.</li>
 * </ul>
 * The tab must not be created just to reach the list: {@code HOTabbedPane.getModulePanel} calls
 * {@code setSelectedIndex}, which would switch the user's visible tab. {@code storeUserSettings}
 * guards the same call with {@code isModuleTabVisible} for that reason.
 * <p>
 * Everything here touches Swing state, so call it on the event dispatch thread.
 */
public final class ScoutList {

    private ScoutList() {
    }

    /** Whether the Transfers tab is showing, and so holds the authoritative list. */
    public static boolean isLive() {
        return HOMainFrame.instance().getTabbedPane().isModuleTabVisible(IModule.TRANSFERS);
    }

    /** The scout entries - the open panel's live list, or the stored one. */
    public static List<ScoutEintrag> entries() {
        return isLive()
                ? panel().getTransferTable().getTransferTableModel().getScoutListe()
                : DBManager.instance().getScoutList();
    }

    /** Persist entries taken from {@link #entries()}, repainting the table when it is showing. */
    public static void save(List<ScoutEintrag> entries) {
        final var vector = entries instanceof Vector<ScoutEintrag> v ? v : new Vector<>(entries);
        if (isLive()) {
            final var panel = panel();
            final var model = panel.getTransferTable().getTransferTableModel();
            if (model.getScoutListe() != vector) {
                model.setValues(vector);
            }
            panel.saveScoutListe();
            panel.getTransferTable().refresh();
        } else {
            DBManager.instance().saveScoutList(vector);
        }
    }

    /**
     * Record on a scouted player what he actually went for, so the watch list says how a target
     * ended rather than only what his seller was asking.
     * <p>
     * Its own column, not a line of free text in the notes: the price is a number the UI can sort
     * and format and a tool can compare against a bid. The asking price column is left alone - it
     * means "asking price" everywhere else in this table.
     *
     * @param salePriceSek the sale price in swedish krona, the unit CHPP reports
     * @param soldAt       when the transfer completed
     * @param window       how far {@code soldAt} may sit from the entry's deadline and still be
     *                     the sale of the transfer that was watched
     * @return whether a scout entry for that player was found and updated
     */
    public static boolean recordSale(int playerId, long salePriceSek, HODateTime soldAt,
                                     java.time.Duration window) {
        final var entries = entries();
        for (final var entry : entries) {
            if (entry.getPlayerID() != playerId) {
                continue;
            }
            if (entry.isSold() || !isTheWatchedTransfer(entry, soldAt, window)) {
                return false;
            }
            entry.setSalePrice(new AmountOfMoney(salePriceSek));
            entry.setSoldAt(soldAt.toDbTimestamp());
            save(entries);
            return true;
        }
        return false;
    }

    /**
     * Whether a sale is the one this entry was watching, rather than some earlier transfer of the
     * same player. A Hattrick transfer completes at its deadline, so the two line up.
     * <p>
     * This matters more than it looks: a fresh {@link ScoutEintrag} defaults its deadline to the
     * moment it was created, so an entry typed in by hand and never given a real deadline looks
     * "expired" straight away. Without this check its player's last transfer - possibly seasons
     * old - would be recorded as the sale.
     */
    static boolean isTheWatchedTransfer(ScoutEintrag entry, HODateTime soldAt,
                                        java.time.Duration window) {
        if (soldAt == null || entry.getDeadline() == null) {
            return false;
        }
        final var deadline = HODateTime.fromDbTimestamp(entry.getDeadline()).getInstant();
        return !soldAt.getInstant().isBefore(deadline.minus(window))
                && !soldAt.getInstant().isAfter(deadline.plus(window));
    }

    /** Only safe once {@link #isLive()} is true - otherwise it creates and selects the tab. */
    private static TransferScoutPanel panel() {
        return HOMainFrame.instance().getTransferScoutPanel().getScoutPanel();
    }
}
