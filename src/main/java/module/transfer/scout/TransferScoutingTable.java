package module.transfer.scout;

import core.db.DBManager;
import core.gui.RefreshManager;
import core.gui.Refreshable;
import core.gui.comp.table.FixedColumnsTable;
import core.gui.model.UserColumnController;

import javax.swing.RowFilter;
import javax.swing.table.TableRowSorter;
import java.util.function.Predicate;

public class TransferScoutingTable extends FixedColumnsTable implements Refreshable {

    private TransferScoutingTableModel m_clTableModel;

    TransferScoutingTable() {
        super(UserColumnController.instance().getTransferScoutingTableModel(), 2);
        setOpaque(false);
        m_clTableModel = (TransferScoutingTableModel)this.getModel();
        m_clTableModel.setValues(DBManager.instance().getScoutList());
        RefreshManager.instance().registerRefreshable(this);
    }
    public final TransferScoutingTableModel getTransferTableModel() {
        return m_clTableModel;
    }

    public final void reInit() {
        refresh();
    }

    public final void refresh() {
        m_clTableModel.initData();
        repaint();
    }

    /**
     * Filters the rows shown, without touching the underlying scout list - {@code null} shows every
     * entry again. Applied to the {@link TableRowSorter} that {@code HOTableModel.initTable} already
     * installs, so {@link ScoutList#save} still persists the full list regardless of what is filtered.
     */
    @SuppressWarnings("unchecked")
    public final void setEntryFilter(Predicate<ScoutEintrag> filter) {
        final var sorter = (TableRowSorter<TransferScoutingTableModel>) getRowSorter();
        sorter.setRowFilter(filter == null ? null : new RowFilter<>() {
            @Override
            public boolean include(Entry<? extends TransferScoutingTableModel, ? extends Integer> entry) {
                return filter.test(entry.getModel().getScoutListe().get(entry.getIdentifier()));
            }
        });
    }
}
