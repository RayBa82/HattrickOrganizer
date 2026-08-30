package module.transfer.scout;

import core.gui.model.UserColumnController.ColumnModelId;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TransferScoutingTableModelTest {

    /**
     * The transfer search shows its hits in a second table of the same kind. Both tables must not
     * share the column model id: the settings are saved per id, so the search table would overwrite
     * the column layout of the scout table.
     */
    @Test
    void theSearchModelUsesItsOwnColumnModelId() {
        final var scout = new TransferScoutingTableModel();
        final var search = new TransferScoutingTableModel(ColumnModelId.TRANSFERSEARCH);

        assertThat(search.id).isNotEqualTo(scout.id);
        assertThat(search.getColumns()).hasSameSizeAs(scout.getColumns());
    }
}
