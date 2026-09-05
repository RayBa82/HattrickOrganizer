package module.transfer.scout;

import core.gui.comp.entry.IHOTableCellEntry;
import core.gui.model.UserColumnController.ColumnModelId;
import core.model.TranslationFacility;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Vector;

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

    /**
     * The search panel hides the player id by switching the first column off, so it has to stay the
     * first one.
     */
    @Test
    void thePlayerIdIsTheFirstColumn() {
        assertThat(new TransferScoutingTableModel(ColumnModelId.TRANSFERSEARCH).getColumns()[0].getColumnName())
                .isEqualTo(TranslationFacility.tr("ls.player.id"));
    }

    /**
     * Skill columns carry a numeric sort key. Without it the cells compare as text and 10 sorts
     * before 5.
     */
    @Test
    void skillColumnsSortNumerically() {
        final var model = new TransferScoutingTableModel(ColumnModelId.TRANSFERSEARCH);
        // the position columns need a loaded HOModel to compute their ratings, so show only the
        // skill column under test
        final var playmaking = TranslationFacility.tr("ls.player.skill_short.playmaking");
        for (var column : model.getColumns()) {
            column.setDisplay(playmaking.equals(column.getColumnName()));
        }
        model.setValues(new Vector<>(List.of(withPlaymaking(5), withPlaymaking(10))));

        final var five = (IHOTableCellEntry) model.getValueAt(0, 0);
        final var ten = (IHOTableCellEntry) model.getValueAt(1, 0);

        assertThat(five.compareTo(ten)).isNegative();
    }

    private static ScoutEintrag withPlaymaking(int level) {
        final var eintrag = new ScoutEintrag();
        eintrag.setPlayerID(level);
        eintrag.setSpielaufbau(level);
        return eintrag;
    }
}
