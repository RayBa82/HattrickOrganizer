package module.transfer;

import core.constants.player.PlayerAbility;
import core.datatype.CBItem;
import core.util.AmountOfMoney;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.swing.JComboBox;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class PlayerFilterFormTest {

    @BeforeAll
    static void fixTheExchangeRate() {
        AmountOfMoney.Companion.setExchangeRate(BigDecimal.TEN);
    }

    @Test
    void maxPriceOrNull_isNullForAnEmptyField() {
        assertThat(PlayerFilterForm.maxPriceOrNull("   ")).isNull();
        assertThat(PlayerFilterForm.maxPriceOrNull(null)).isNull();
    }

    @Test
    void maxPriceOrNull_isNullForAnUnreadableField() {
        assertThat(PlayerFilterForm.maxPriceOrNull("no money")).isNull();
    }

    @Test
    void maxPriceOrNull_convertsTheLocalCurrencyIntoSwedishKrona() {
        assertThat(PlayerFilterForm.maxPriceOrNull("100000")).isEqualTo(1000000);
    }

    @Test
    void chppSkillSpan_isClampedWhenMinIsRaisedPastMax() throws Exception {
        final var form = new PlayerFilterForm(true);
        setSkillMin(form, 0, PlayerAbility.OUTSTANDING); // min=10, old max=9 -> max must follow

        final var skill = form.criteria().skills().get(0);
        assertThat(skill.min()).isEqualTo(PlayerAbility.OUTSTANDING);
        assertThat(skill.max() - skill.min()).isBetween(0, 3);
    }

    @Test
    void chppSkillSpan_isClampedWhenMaxIsLoweredBelowMin() throws Exception {
        final var form = new PlayerFilterForm(true);
        setSkillMax(form, 0, PlayerAbility.NON_EXISTENT); // max=0, old min=7 -> min must follow

        final var skill = form.criteria().skills().get(0);
        assertThat(skill.max()).isEqualTo(PlayerAbility.NON_EXISTENT);
        assertThat(skill.max() - skill.min()).isBetween(0, 3);
    }

    @Test
    void chppSkillSpan_isUntouchedOnTheScoutFilterForm() throws Exception {
        // resetToWideOpen() spans the whole scale on purpose - the local scout filter must not clamp it.
        final var form = new PlayerFilterForm(false);
        form.resetToWideOpen();

        final var skill = form.criteria().skills().get(0);
        assertThat(skill.min()).isEqualTo(PlayerAbility.NON_EXISTENT);
        assertThat(skill.max()).isEqualTo(PlayerAbility.DIVINE);
    }

    @SuppressWarnings("unchecked")
    private static void setSkillMin(PlayerFilterForm form, int row, int abilityId) throws Exception {
        skillCombo(form, row, "min").setSelectedItem(itemFor(abilityId));
    }

    @SuppressWarnings("unchecked")
    private static void setSkillMax(PlayerFilterForm form, int row, int abilityId) throws Exception {
        skillCombo(form, row, "max").setSelectedItem(itemFor(abilityId));
    }

    private static CBItem itemFor(int abilityId) {
        return PlayerAbility.ITEMS[abilityId];
    }

    @SuppressWarnings("unchecked")
    private static JComboBox<CBItem> skillCombo(PlayerFilterForm form, int row, String fieldName) throws Exception {
        final Field skillRowsField = PlayerFilterForm.class.getDeclaredField("skillRows");
        skillRowsField.setAccessible(true);
        final Object skillRow = Array.get(skillRowsField.get(form), row);
        final Field comboField = skillRow.getClass().getDeclaredField(fieldName);
        comboField.setAccessible(true);
        return (JComboBox<CBItem>) comboField.get(skillRow);
    }
}
