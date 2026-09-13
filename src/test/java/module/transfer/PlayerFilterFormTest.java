package module.transfer;

import core.util.AmountOfMoney;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

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
}
