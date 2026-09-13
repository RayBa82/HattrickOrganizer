package module.transfer.search;

import core.constants.player.PlayerSpeciality;
import core.gui.InfoPanel;
import core.gui.comp.panel.ImagePanel;
import core.gui.comp.table.FixedColumnsTable;
import core.gui.model.UserColumnController;
import core.model.TranslationFacility;
import core.net.OnlineWorker;
import core.net.TransferSearchQuery;
import module.transfer.PlayerFilterForm;
import module.transfer.scout.ScoutEintrag;
import module.transfer.scout.TransferEingabePanel;
import module.transfer.scout.TransferScoutPanel;
import module.transfer.scout.TransferScoutingTableModel;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

/**
 * Searches the transfer market through the CHPP transfersearch file and shows the hits in a scout
 * table. The details of the selected hit are shown below the table in the same panel the scout tab
 * uses, which is also where a hit is copied into the scout list.
 */
public class TransferSearchPanel extends ImagePanel {

    private final TransferScoutingTableModel resultsModel = emptyResultsModel();
    private final FixedColumnsTable resultsTable = new FixedColumnsTable(resultsModel, 1);
    private final List<ScoutEintrag> results = new ArrayList<>();

    /** Details of the selected hit, and the Add button that copies it into the scout list. */
    private final TransferEingabePanel details;

    private final PlayerFilterForm form = new PlayerFilterForm(true);
    private final JButton search = new JButton(TranslationFacility.tr("ls.transfersearch.search"));
    private final JLabel status = new JLabel(" ");
    private final Color statusDefaultColor = status.getForeground();

    public TransferSearchPanel(TransferScoutPanel scoutPanel) {
        this.details = new TransferEingabePanel(scoutPanel);
        initComponents();
    }

    /**
     * Store the column settings of the results table.
     */
    public void storeUserSettings() {
        resultsModel.storeUserSettings();
    }

    /**
     * The results model, emptied before the table is built over it. The model is a singleton of
     * {@link UserColumnController} and starts out with no data at all.
     * <p>
     * The player id is hidden here rather than in the model's constructor: {@code loadModel} turns
     * every column it finds in the database back on, so a default set before the load is undone as
     * soon as the user has saved the column layout once. It has to happen before {@code setValues},
     * which is what builds the displayed-column list.
     */
    private static TransferScoutingTableModel emptyResultsModel() {
        final var model = UserColumnController.instance().getTransferSearchTableModel();
        model.getColumns()[0].setDisplay(false);    // the id is only of interest in the details panel
        model.setValues(new Vector<>());
        return model;
    }

    private void initComponents() {
        setLayout(new BorderLayout());

        final var split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, false,
                resultsTable.getContainerComponent(), new JScrollPane(details));
        split.setResizeWeight(0.5);

        add(initForm(), BorderLayout.NORTH);
        add(split, BorderLayout.CENTER);
        add(status, BorderLayout.SOUTH);

        search.addActionListener(this::doSearch);
        resultsTable.addListSelectionListener(e -> showDetails());
        details.setScoutEintrag(null);
    }

    private JPanel initForm() {
        final var panel = new JPanel(new BorderLayout());
        panel.setOpaque(false);
        panel.add(form, BorderLayout.CENTER);

        final var buttonRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
        buttonRow.setOpaque(false);
        buttonRow.add(search);
        panel.add(buttonRow, BorderLayout.SOUTH);
        return panel;
    }

    /**
     * Runs the search. Only ever called from the button, the transfer market may not be scanned
     * automatically.
     */
    private void doSearch(ActionEvent event) {
        final var query = buildQuery();
        final var error = query.validationErrorKey();
        if (error != null) {
            status.setForeground(InfoPanel.FEHLERFARBE);
            status.setText(TranslationFacility.tr(error));
            return;
        }

        final var page = OnlineWorker.searchTransfers(query);
        if (page == null) {
            return;     // OnlineWorker has shown the error
        }

        results.clear();
        for (final var hit : page.hits()) {
            results.add(TransferSearchConverter.toScoutEintrag(hit));
        }
        resultsModel.setValues(new Vector<>(results));
        resultsTable.repaint();
        status.setForeground(statusDefaultColor);
        details.setScoutEintrag(null);
        status.setText(resultText(page.itemCount()));
    }

    /**
     * The hit count. Hattrick answers a query it does not accept with an error file rather than with
     * an HTTP error, and the parser turns that into an empty page - so "no hit" is reported as a
     * possible rejection, not as a plain zero.
     */
    private String resultText(int itemCount) {
        if (results.isEmpty()) {
            return TranslationFacility.tr("ls.transfersearch.nohits");
        }
        final var total = itemCount < 0 ? results.size() + "+" : String.valueOf(itemCount);
        return TranslationFacility.tr("ls.transfersearch.results") + ": " + results.size()
                + " / " + total;
    }

    private TransferSearchQuery buildQuery() {
        final var c = form.criteria();
        final var skills = c.skills();
        final var builder = TransferSearchQuery.builder()
                .ageMin(c.ageMin())
                .ageMax(c.ageMax())
                .skillType1(skills.get(0).skill())
                .minSkillValue1(skills.get(0).min())
                .maxSkillValue1(skills.get(0).max())
                .specialty(c.specialityId() == PlayerSpeciality.NO_SPECIALITY ? null : c.specialityId())
                .priceMax(c.maxPriceSek())
                .pageIndex(0);
        if (skills.size() > 1) {
            final var s = skills.get(1);
            builder.skillType2(s.skill()).minSkillValue2(s.min()).maxSkillValue2(s.max());
        }
        if (skills.size() > 2) {
            final var s = skills.get(2);
            builder.skillType3(s.skill()).minSkillValue3(s.min()).maxSkillValue3(s.max());
        }
        if (skills.size() > 3) {
            final var s = skills.get(3);
            builder.skillType4(s.skill()).minSkillValue4(s.min()).maxSkillValue4(s.max());
        }
        return builder.build();
    }

    /**
     * Shows the selected hit in the details panel. The entry is duplicated so that editing it there
     * does not change the search result.
     */
    private void showDetails() {
        final int row = resultsTable.getSelectedRow();
        if (row < 0) {
            details.setScoutEintrag(null);
            return;
        }
        details.setScoutEintrag(results.get(resultsTable.convertRowIndexToModel(row)).duplicate());
    }
}
