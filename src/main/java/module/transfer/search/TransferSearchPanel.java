package module.transfer.search;

import core.constants.player.PlayerAbility;
import core.constants.player.PlayerSpeciality;
import core.datatype.CBItem;
import core.gui.comp.panel.ImagePanel;
import core.gui.comp.table.FixedColumnsTable;
import core.gui.model.UserColumnController;
import core.model.TranslationFacility;
import core.net.OnlineWorker;
import core.net.TransferSearchQuery;
import core.util.AmountOfMoney;
import core.util.Helper;
import module.transfer.scout.ScoutEintrag;
import module.transfer.scout.TransferEingabePanel;
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
 * table. The details of the selected hit are shown below the table in the same panel the scout tab
 * uses, which is also where a hit is copied into the scout list.
 */
public class TransferSearchPanel extends ImagePanel {

    /** The number of skills a CHPP transfer search accepts. */
    private static final int MAX_SKILLS = 4;

    private final TransferScoutingTableModel resultsModel = emptyResultsModel();
    private final FixedColumnsTable resultsTable = new FixedColumnsTable(resultsModel, 1);
    private final List<ScoutEintrag> results = new ArrayList<>();

    /** Details of the selected hit, and the Add button that copies it into the scout list. */
    private final TransferEingabePanel details;

    // Defaults are the age pair of Hattrick's own documented transfersearch example.
    private final JSpinner ageMin = new JSpinner(new SpinnerNumberModel(20, 17, 40, 1));
    private final JSpinner ageMax = new JSpinner(new SpinnerNumberModel(22, 17, 40, 1));
    private final SkillRow[] skillRows = new SkillRow[MAX_SKILLS];
    private final JComboBox<CBItem> speciality = new JComboBox<>(PlayerSpeciality.ITEMS);
    private final JTextField maxPrice = new JTextField(10);
    private final JButton addSkill = new JButton("+");
    private final JButton removeSkill = new JButton("-");
    private final JButton search = new JButton(TranslationFacility.tr("ls.transfersearch.search"));
    private final JLabel status = new JLabel(" ");

    /** How many of the skill rows are in use, the first one always is. */
    private int usedSkills = 1;

    /** One searchable skill: which skill, and the level range it has to be in. */
    private static class SkillRow {
        final JLabel label = new JLabel(TranslationFacility.tr("ls.transfersearch.skill"));
        final JComboBox<TransferSearchQuery.Skill> skill = new JComboBox<>(TransferSearchQuery.Skill.values());
        final JComboBox<CBItem> min = new JComboBox<>(PlayerAbility.ITEMS);
        final JComboBox<CBItem> max = new JComboBox<>(PlayerAbility.ITEMS);

        void setVisible(boolean visible) {
            label.setVisible(visible);
            skill.setVisible(visible);
            min.setVisible(visible);
            max.setVisible(visible);
        }
    }

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

        // the skill rows have to exist before initForm() lays them out
        for (int i = 0; i < skillRows.length; i++) {
            final var row = skillRows[i] = new SkillRow();
            // The skill combo shows the translated HO skill name
            row.skill.setRenderer(new DefaultListCellRenderer() {
                @Override
                public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                              boolean isSelected, boolean cellHasFocus) {
                    final var text = value == null ? ""
                            : ((TransferSearchQuery.Skill) value).toPlayerSkill().getLanguageString();
                    return super.getListCellRendererComponent(list, text, index, isSelected, cellHasFocus);
                }
            });
            row.skill.setSelectedItem(TransferSearchQuery.Skill.PLAYMAKING);
            Helper.setComboBoxFromID(row.min, 7);
            Helper.setComboBoxFromID(row.max, 9);
            row.setVisible(i < usedSkills);
        }

        final var split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, false,
                resultsTable.getContainerComponent(), new JScrollPane(details));
        split.setResizeWeight(0.5);

        add(initForm(), BorderLayout.NORTH);
        add(split, BorderLayout.CENTER);
        add(status, BorderLayout.SOUTH);


        addSkill.addActionListener(e -> setUsedSkills(usedSkills + 1));
        removeSkill.addActionListener(e -> setUsedSkills(usedSkills - 1));
        setUsedSkills(usedSkills);

        search.addActionListener(this::doSearch);
        resultsTable.addListSelectionListener(e -> showDetails());
        details.setScoutEintrag(null);
    }

    /**
     * Shows or hides skill rows so that the first {@code count} of them are in use.
     */
    private void setUsedSkills(int count) {
        usedSkills = Math.min(Math.max(count, 1), MAX_SKILLS);
        for (int i = 0; i < skillRows.length; i++) {
            skillRows[i].setVisible(i < usedSkills);
        }
        addSkill.setEnabled(usedSkills < MAX_SKILLS);
        removeSkill.setEnabled(usedSkills > 1);
        revalidate();
        repaint();
    }

    private JPanel initForm() {
        final var form = new JPanel(new GridBagLayout());
        form.setOpaque(false);
        final var c = new GridBagConstraints();
        c.insets = new Insets(2, 4, 2, 4);
        c.anchor = GridBagConstraints.WEST;

        c.gridy = 0;
        c.gridx = 0;
        form.add(new JLabel(TranslationFacility.tr("ls.player.age")), c);
        c.gridx = 1;
        form.add(ageMin, c);
        c.gridx = 2;
        form.add(ageMax, c);
        c.gridx = 3;
        form.add(new JLabel(TranslationFacility.tr("ls.player.speciality")), c);
        c.gridx = 4;
        form.add(speciality, c);
        c.gridx = 5;
        form.add(new JLabel(TranslationFacility.tr("ls.transfersearch.maxprice")
                + " (" + AmountOfMoney.Companion.getCurrencyName() + ")"), c);
        c.gridx = 6;
        form.add(maxPrice, c);

        for (final var row : skillRows) {
            c.gridy++;
            c.gridx = 0;
            form.add(row.label, c);
            c.gridx = 1;
            c.gridwidth = 2;
            form.add(row.skill, c);
            c.gridwidth = 1;
            c.gridx = 3;
            form.add(row.min, c);
            c.gridx = 4;
            form.add(row.max, c);
            if (row == skillRows[0]) {
                c.gridx = 5;
                form.add(addSkill, c);
                c.gridx = 6;
                form.add(removeSkill, c);
            }
        }

        c.gridy++;
        c.gridx = 0;
        form.add(search, c);

        // soak up the remaining width so the fields stay left aligned
        c.gridx = 7;
        c.gridy = 0;
        c.weightx = 1.0;
        c.fill = GridBagConstraints.HORIZONTAL;
        form.add(Box.createHorizontalGlue(), c);
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
        final var specialityId = ((CBItem) speciality.getSelectedItem()).getId();
        final var builder = TransferSearchQuery.builder()
                .ageMin((Integer) ageMin.getValue())
                .ageMax((Integer) ageMax.getValue())
                .skillType1(skillOf(0))
                .minSkillValue1(minOf(0))
                .maxSkillValue1(maxOf(0))
                .specialty(specialityId == PlayerSpeciality.NO_SPECIALITY ? null : specialityId)
                .priceMax(maxPriceOrNull(maxPrice.getText()))
                .pageIndex(0);
        if (usedSkills > 1) {
            builder.skillType2(skillOf(1)).minSkillValue2(minOf(1)).maxSkillValue2(maxOf(1));
        }
        if (usedSkills > 2) {
            builder.skillType3(skillOf(2)).minSkillValue3(minOf(2)).maxSkillValue3(maxOf(2));
        }
        if (usedSkills > 3) {
            builder.skillType4(skillOf(3)).minSkillValue4(minOf(3)).maxSkillValue4(maxOf(3));
        }
        return builder.build();
    }

    private TransferSearchQuery.Skill skillOf(int row) {
        return (TransferSearchQuery.Skill) skillRows[row].skill.getSelectedItem();
    }

    private Integer minOf(int row) {
        return ((CBItem) skillRows[row].min.getSelectedItem()).getId();
    }

    private Integer maxOf(int row) {
        return ((CBItem) skillRows[row].max.getSelectedItem()).getId();
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
