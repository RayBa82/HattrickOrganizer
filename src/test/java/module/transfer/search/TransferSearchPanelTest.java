package module.transfer.search;

import core.util.AmountOfMoney;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class TransferSearchPanelTest {

    @BeforeAll
    static void fixTheExchangeRate() {
        AmountOfMoney.Companion.setExchangeRate(BigDecimal.TEN);
    }

    @Test
    void maxPriceOrNull_isNullForAnEmptyField() {
        assertThat(TransferSearchPanel.maxPriceOrNull("   ")).isNull();
        assertThat(TransferSearchPanel.maxPriceOrNull(null)).isNull();
    }

    @Test
    void maxPriceOrNull_isNullForAnUnreadableField() {
        assertThat(TransferSearchPanel.maxPriceOrNull("no money")).isNull();
    }

    @Test
    void maxPriceOrNull_convertsTheLocalCurrencyIntoSwedishKrona() {
        assertThat(TransferSearchPanel.maxPriceOrNull("100000")).isEqualTo(1000000);
    }
}
