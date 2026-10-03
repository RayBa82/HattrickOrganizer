package core.file.hrf;

import core.model.misc.Economy;
import core.util.AmountOfMoney;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Locale;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

class HRFStringBuilderTest {

    /** Every [economy] key the builder writes must be the one Economy reads back. */
    @Test
    void economySurvivesTheHrfRoundTrip() {
        // CHPP economy.xml values of a real team (SEK)
        final var map = new HashMap<String, String>();
        final String[] keyValues = {
                "Cash", "3628160", "ExpectedCash", "3498900", "SponsorsPopularity", "0", "SupportersPopularity", "9",
                "IncomeSpectators", "0", "IncomeSponsors", "680000", "IncomeSponsorsBonus", "105000", "IncomeFinancial", "0",
                "IncomeSoldPlayers", "0", "IncomeSoldPlayersCommission", "0", "IncomeTemporary", "0", "IncomeSum", "785000",
                "CostsArena", "146000", "CostsPlayers", "515860", "CostsFinancial", "0", "CostsStaff", "222400",
                "CostsBoughtPlayers", "0", "CostsArenaBuilding", "0", "CostsYouth", "0", "CostsSum", "914260",
                "ExpectedWeeksTotal", "-129260",
                "LastIncomeSpectators", "1999320", "LastIncomeSponsors", "680000", "LastIncomeSponsorsBonus", "105000",
                "LastIncomeFinancial", "0", "LastIncomeSoldPlayers", "0", "LastIncomeSoldPlayersCommission", "0",
                "LastIncomeTemporary", "2400", "LastIncomeSum", "2786720",
                "LastCostsArena", "146000", "LastCostsPlayers", "425820", "LastCostsFinancial", "0", "LastCostsStaff", "222400",
                "LastCostsBoughtPlayers", "9224280", "LastCostsArenaBuilding", "3400000", "LastCostsYouth", "100000",
                "LastCostsSum", "13598500", "LastWeeksTotal", "-10811780"};
        for (int i = 0; i < keyValues.length; i += 2) map.put(keyValues[i], keyValues[i + 1]);

        final var builder = new HRFStringBuilder();
        builder.createEconomy(map);
        // HRFStringParser lower-cases the keys
        final var properties = new Properties();
        for (var line : builder.createHRF().toString().split("\n")) {
            final int eq = line.indexOf('=');
            if (eq > 0) properties.setProperty(line.substring(0, eq).toLowerCase(Locale.ENGLISH), line.substring(eq + 1));
        }
        final var economy = new Economy(properties);

        assertThat(economy.getCash()).isEqualTo(new AmountOfMoney(3628160));
        assertThat(economy.getCostsStaff()).isEqualTo(new AmountOfMoney(222400));
        assertThat(economy.getLastCostsStaff()).isEqualTo(new AmountOfMoney(222400));
        assertThat(economy.getLastCostsArena()).isEqualTo(new AmountOfMoney(146000));
        assertThat(economy.getLastCostsYouth()).isEqualTo(new AmountOfMoney(100000));
        assertThat(economy.getLastWeeksTotal()).isEqualTo(new AmountOfMoney(-10811780));
    }
}
