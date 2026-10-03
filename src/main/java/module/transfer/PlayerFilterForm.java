package module.transfer;

import core.constants.player.PlayerAbility;
import core.constants.player.PlayerSpeciality;
import core.datatype.CBItem;
import core.model.TranslationFacility;
import core.net.TransferSearchQuery;
import core.util.AmountOfMoney;
import core.util.Helper;

import javax.swing.*;
import java.awt.*;
import java.math.BigDecimal;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * The age/skill/speciality/price criteria shared by the CHPP transfer search and the local scout
 * list filter. Holds only the input controls - a caller adds its own action button(s) around it,
 * since the two callers trigger the criteria differently (a CHPP call vs. an in-memory filter).
 */
public class PlayerFilterForm extends JPanel {

    /** The number of skills a CHPP transfer search accepts, and so the most a filter can use too. */
    public static final int MAX_SKILLS = 4;

    /** One skill and the level range it has to be in. */
    public record SkillRange(TransferSearchQuery.Skill skill, int min, int max) {
    }

    /** The criteria as currently set in the form. {@code skills} always has at least one entry. */
    public record Criteria(int ageMin, int ageMax, List<SkillRange> skills, int specialityId,
                            Integer maxPriceSek) {
    }

    // Defaults are the age pair of Hattrick's own documented transfersearch example.
    private final JSpinner ageMin = new JSpinner(new SpinnerNumberModel(20, 17, 40, 1));
    private final JSpinner ageMax = new JSpinner(new SpinnerNumberModel(22, 17, 40, 1));
    private final SkillRow[] skillRows = new SkillRow[MAX_SKILLS];
    private final JComboBox<CBItem> speciality = new JComboBox<>(PlayerSpeciality.ITEMS);
    private final JTextField maxPrice = new JTextField(10);
    private final JButton addSkill = new JButton("+");
    private final JButton removeSkill = new JButton("-");

    /** How many of the skill rows are in use, the first one always is. */
    private int usedSkills = 1;

    /** One searchable skill row: which skill, and the level range it has to be in. */
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

    /** Hattrick's transfersearch rule: a skill window may span at most this many levels. */
    private static final int MAX_CHPP_SKILL_SPAN = 3;

    public PlayerFilterForm() {
        this(false);
    }

    /**
     * @param enforceChppSkillSpan clamp each skill row's min/max as the user edits them so the pair
     *                             never breaks Hattrick's transfersearch rule (span of at most 4
     *                             levels, counted inclusively). The CHPP search needs this - a
     *                             wider pair always comes back as a CHPP error, never as an empty
     *                             result. The local scout-list filter must not have it:
     *                             {@link #resetToWideOpen()} deliberately spans the whole scale, and
     *                             a local, in-memory filter is not bound by a rule that exists only
     *                             because the CHPP file itself rejects the request.
     */
    public PlayerFilterForm(boolean enforceChppSkillSpan) {
        setLayout(new GridBagLayout());
        setOpaque(false);

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
            if (enforceChppSkillSpan) {
                row.min.addActionListener(e -> clampSkillSpan(row.min, row.max, true));
                row.max.addActionListener(e -> clampSkillSpan(row.min, row.max, false));
            }
        }

        layoutFields();

        addSkill.addActionListener(e -> setUsedSkills(usedSkills + 1));
        removeSkill.addActionListener(e -> setUsedSkills(usedSkills - 1));
        setUsedSkills(usedSkills);
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

    private void layoutFields() {
        final var c = new GridBagConstraints();
        c.insets = new Insets(2, 4, 2, 4);
        c.anchor = GridBagConstraints.WEST;

        c.gridy = 0;
        c.gridx = 0;
        add(new JLabel(TranslationFacility.tr("ls.player.age")), c);
        c.gridx = 1;
        add(ageMin, c);
        c.gridx = 2;
        add(ageMax, c);
        c.gridx = 3;
        add(new JLabel(TranslationFacility.tr("ls.player.speciality")), c);
        c.gridx = 4;
        add(speciality, c);
        c.gridx = 5;
        add(new JLabel(TranslationFacility.tr("ls.transfersearch.maxprice")
                + " (" + AmountOfMoney.Companion.getCurrencyName() + ")"), c);
        c.gridx = 6;
        add(maxPrice, c);

        for (final var row : skillRows) {
            c.gridy++;
            c.gridx = 0;
            add(row.label, c);
            c.gridx = 1;
            c.gridwidth = 2;
            add(row.skill, c);
            c.gridwidth = 1;
            c.gridx = 3;
            add(row.min, c);
            c.gridx = 4;
            add(row.max, c);
            if (row == skillRows[0]) {
                c.gridx = 5;
                add(addSkill, c);
                c.gridx = 6;
                add(removeSkill, c);
            }
        }

        // soak up the remaining width so the fields stay left aligned
        c.gridx = 7;
        c.gridy = 0;
        c.weightx = 1.0;
        c.fill = GridBagConstraints.HORIZONTAL;
        add(Box.createHorizontalGlue(), c);
    }

    /**
     * Notified whenever a control changes - age, a skill row's skill/min/max, the skill row count,
     * speciality or price. A caller doing live filtering re-reads {@link #criteria()} from it; one
     * doing filtering on a button click does not need it at all.
     */
    public void addChangeListener(Runnable listener) {
        ageMin.addChangeListener(e -> listener.run());
        ageMax.addChangeListener(e -> listener.run());
        speciality.addActionListener(e -> listener.run());
        maxPrice.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override
            public void insertUpdate(javax.swing.event.DocumentEvent e) {
                listener.run();
            }

            @Override
            public void removeUpdate(javax.swing.event.DocumentEvent e) {
                listener.run();
            }

            @Override
            public void changedUpdate(javax.swing.event.DocumentEvent e) {
                listener.run();
            }
        });
        for (final var row : skillRows) {
            row.skill.addActionListener(e -> listener.run());
            row.min.addActionListener(e -> listener.run());
            row.max.addActionListener(e -> listener.run());
        }
        // registered after the constructor's own listener, so usedSkills is already up to date
        addSkill.addActionListener(e -> listener.run());
        removeSkill.addActionListener(e -> listener.run());
    }

    /**
     * Widens every field to match everything: the full age and skill ranges, no speciality, no
     * price limit. The form's own field defaults are Hattrick's documented transfer-search example
     * instead, which is the right starting point for a CHPP query but would silently hide most of
     * the scout list the moment a caller starts filtering it live.
     */
    public void resetToWideOpen() {
        ageMin.setValue(17);
        ageMax.setValue(40);
        for (final var row : skillRows) {
            Helper.setComboBoxFromID(row.min, PlayerAbility.NON_EXISTENT);
            Helper.setComboBoxFromID(row.max, PlayerAbility.DIVINE);
        }
        speciality.setSelectedIndex(0);    // PlayerSpeciality.ITEMS[0] is NO_SPECIALITY
        maxPrice.setText("");
    }

    /**
     * The criteria as currently set in the form.
     */
    public Criteria criteria() {
        final var skills = new ArrayList<SkillRange>();
        for (int i = 0; i < usedSkills; i++) {
            skills.add(new SkillRange(skillOf(i), minOf(i), maxOf(i)));
        }
        final var specialityId = ((CBItem) speciality.getSelectedItem()).getId();
        return new Criteria((Integer) ageMin.getValue(), (Integer) ageMax.getValue(), skills,
                specialityId, maxPriceOrNull(maxPrice.getText()));
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
     * Keeps one skill row's min/max pair inside Hattrick's transfersearch rule as the user edits
     * either combo: {@code max} at most {@link #MAX_CHPP_SKILL_SPAN} levels above {@code min}, and
     * never below it. {@code minChanged} says which combo the user just touched, so the other one is
     * the one moved - moving the one the user is not looking at, rather than snapping back the one
     * they just picked.
     */
    private static void clampSkillSpan(JComboBox<CBItem> min, JComboBox<CBItem> max, boolean minChanged) {
        final int minId = ((CBItem) min.getSelectedItem()).getId();
        final int maxId = ((CBItem) max.getSelectedItem()).getId();
        if (minChanged) {
            if (maxId < minId || maxId - minId > MAX_CHPP_SKILL_SPAN) {
                Helper.setComboBoxFromID(max, Math.min(minId + MAX_CHPP_SKILL_SPAN, PlayerAbility.DIVINE));
            }
        } else {
            if (minId > maxId || maxId - minId > MAX_CHPP_SKILL_SPAN) {
                Helper.setComboBoxFromID(min, Math.max(maxId - MAX_CHPP_SKILL_SPAN, PlayerAbility.NON_EXISTENT));
            }
        }
    }

    /**
     * The price limit as typed, in the user's currency, for {@code transfersearch}: unlike every
     * other CHPP money value its priceMin/priceMax are read in the user's currency - a 407000 euro
     * bid only matched 40600-40800 while HO still sent krona (2026-10-03).
     */
    public Integer maxPriceLocal() {
        return localPriceOrNull(maxPrice.getText());
    }

    static Integer localPriceOrNull(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return Helper.getNumberFormat(0).parse(text.trim()).intValue();
        } catch (ParseException e) {
            return null;
        }
    }

    /**
     * The price limit of the form, converted from the user's currency into swedish krona for the
     * local scout filter, or null if the field is empty or unreadable.
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
}
