package module.transfer.search;

import core.constants.player.PlayerAbility;
import core.constants.player.PlayerSpeciality;
import core.datatype.CBItem;
import core.gui.HOMainFrame;
import core.gui.comp.panel.ImagePanel;
import core.gui.comp.table.FixedColumnsTable;
import core.gui.model.UserColumnController;
import core.model.TranslationFacility;
import core.net.OnlineWorker;
import core.net.TransferSearchQuery;
import core.util.AmountOfMoney;
import core.util.Helper;
import module.transfer.scout.ScoutEintrag;
import module.transfer.scout.TransferScoutPanel;
import module.transfer.scout.TransferScoutingTableModel;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.math.BigDecimal;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

/**
 * Searches the transfer market through the CHPP transfersearch file and shows the hits in a scout
 * table. Hits are not part of the scout list until the user copies one over.
 */
public class TransferSearchPanel extends ImagePanel {

    /** The scout list a hit is copied into. */
    private final TransferScoutPanel scoutPanel;

    private final TransferScoutingTableModel resultsModel = emptyResultsModel();
    private final FixedColumnsTable resultsTable = new FixedColumnsTable(resultsModel, 2);
    private final List<ScoutEintrag> results = new ArrayList<>();

    private final JSpinner ageMin = new JSpinner(new SpinnerNumberModel(17, 17, 40, 1));
    private final JSpinner ageMax = new JSpinner(new SpinnerNumberModel(20, 17, 40, 1));
    private final JComboBox<TransferSearchQuery.Skill> skill = new JComboBox<>(TransferSearchQuery.Skill.values());
    private final JComboBox<CBItem> skillMin = new JComboBox<>(PlayerAbility.ITEMS);
    private final JComboBox<CBItem> skillMax = new JComboBox<>(PlayerAbility.ITEMS);
    private final JComboBox<CBItem> speciality = new JComboBox<>(PlayerSpeciality.ITEMS);
    private final JTextField maxPrice = new JTextField(10);
    private final JButton search = new JButton(TranslationFacility.tr("ls.transfersearch.search"));
    private final JButton addToScout = new JButton(TranslationFacility.tr("ls.transfersearch.addtoscout"));
    private final JLabel status = new JLabel(" ");

    public TransferSearchPanel(TransferScoutPanel scoutPanel) {
        this.scoutPanel = scoutPanel;
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
     */
    private static TransferScoutingTableModel emptyResultsModel() {
        final var model = UserColumnController.instance().getTransferSearchTableModel();
        model.setValues(new Vector<>());
        return model;
    }

    private void initComponents() {
        setLayout(new BorderLayout());
        add(initForm(), BorderLayout.NORTH);
        add(resultsTable.getContainerComponent(), BorderLayout.CENTER);
        add(status, BorderLayout.SOUTH);

        // The skill combo shows the translated HO skill name
        skill.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                          boolean isSelected, boolean cellHasFocus) {
                final var text = value == null ? ""
                        : ((TransferSearchQuery.Skill) value).toPlayerSkill().getLanguageString();
                return super.getListCellRendererComponent(list, text, index, isSelected, cellHasFocus);
            }
        });
        skill.setSelectedItem(TransferSearchQuery.Skill.PLAYMAKING);
        Helper.setComboBoxFromID(skillMin, 7);
        Helper.setComboBoxFromID(skillMax, 9);

        search.addActionListener(this::doSearch);
        addToScout.addActionListener(this::doAddToScout);
        addToScout.setEnabled(false);
        resultsTable.addListSelectionListener(
                e -> addToScout.setEnabled(resultsTable.getSelectedRow() > -1));
    }

    private JPanel initForm() {
        final var form = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 4));
        form.setOpaque(false);
        form.add(new JLabel(TranslationFacility.tr("ls.player.age")));
        form.add(ageMin);
        form.add(ageMax);
        form.add(new JLabel(TranslationFacility.tr("ls.transfersearch.skill")));
        form.add(skill);
        form.add(skillMin);
        form.add(skillMax);
        form.add(new JLabel(TranslationFacility.tr("ls.player.speciality")));
        form.add(speciality);
        form.add(new JLabel(TranslationFacility.tr("ls.transfersearch.maxprice")
                + " (" + AmountOfMoney.Companion.getCurrencyName() + ")"));
        form.add(maxPrice);
        form.add(search);
        form.add(addToScout);
        return form;
    }

    /**
     * Runs the search. Only ever called from the button, the transfer market may not be scanned
     * automatically.
     */
    private void doSearch(ActionEvent event) {
        final var query = buildQuery();
        final var error = query.validationErrorKey();
        if (error != null) {
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
        final var specialityId = ((CBItem) speciality.getSelectedItem()).getId();
        return TransferSearchQuery.builder()
                .ageMin((Integer) ageMin.getValue())
                .ageMax((Integer) ageMax.getValue())
                .skillType1((TransferSearchQuery.Skill) skill.getSelectedItem())
                .minSkillValue1(((CBItem) skillMin.getSelectedItem()).getId())
                .maxSkillValue1(((CBItem) skillMax.getSelectedItem()).getId())
                .specialty(specialityId == PlayerSpeciality.NO_SPECIALITY ? null : specialityId)
                .priceMax(maxPriceOrNull(maxPrice.getText()))
                .pageSize(50)
                .pageIndex(0)
                .build();
    }

    /**
     * The price limit of the form, converted from the user's currency into the swedish krona the
     * CHPP query expects, or null if the field is empty or unreadable.
     * <p>
     * {@code AmountOfMoney.parse} is deliberately not used: it takes the typed number for swedish
     * krona without applying the exchange rate, and as long as its currency formatter has not been
     * initialised it returns 0 instead of null for an empty field - which would silently limit
     * every search to a price of 0.
     */
    static Integer maxPriceOrNull(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            final var local = Helper.getNumberFormat(0).parse(text.trim()).doubleValue();
            return BigDecimal.valueOf(local)
                    .multiply(AmountOfMoney.Companion.getExchangeRate())
                    .intValue();
        } catch (ParseException e) {
            return null;
        }
    }

    private void doAddToScout(ActionEvent event) {
        final int row = resultsTable.getSelectedRow();
        if (row < 0) {
            return;
        }
        scoutPanel.addScoutEintrag(results.get(resultsTable.convertRowIndexToModel(row)));
        HOMainFrame.instance().setInformation(TranslationFacility.tr("ls.button.add"));
    }
}
