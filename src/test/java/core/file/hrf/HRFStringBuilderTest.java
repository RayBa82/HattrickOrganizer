package core.file.hrf;

import core.file.xml.XMLEconomyParser;
import core.model.misc.Economy;
import core.util.AmountOfMoney;
import org.junit.jupiter.api.Test;

import java.util.Locale;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

class HRFStringBuilderTest {

    /** CHPP economy.xml of a real team (SEK). */
    private static final String ECONOMY_XML = """
            <?xml version="1.0" encoding="utf-8"?>
            <HattrickData>
              <FileName>economy.xml</FileName>
              <Version>1.3</Version>
              <UserID>1357154</UserID>
              <FetchedDate>2026-09-27 14:57:14</FetchedDate>
              <Team>
                <TeamID>2168446</TeamID>
                <TeamName>FC GaisBeach 04</TeamName>
                <Cash>3628160</Cash>
                <ExpectedCash>3498900</ExpectedCash>
                <SponsorsPopularity Available="True">0</SponsorsPopularity>
                <SupportersPopularity Available="True">9</SupportersPopularity>
                <FanClubSize>1674</FanClubSize>
                <IncomeSpectators>0</IncomeSpectators>
                <IncomeSponsors>680000</IncomeSponsors>
                <IncomeFinancial>0</IncomeFinancial>
                <IncomeSoldPlayers>0</IncomeSoldPlayers>
                <IncomeSoldPlayersCommission>0</IncomeSoldPlayersCommission>
                <IncomeTemporary>0</IncomeTemporary>
                <IncomeSum>785000</IncomeSum>
                <CostsArena>146000</CostsArena>
                <CostsPlayers>515860</CostsPlayers>
                <CostsFinancial>0</CostsFinancial>
                <CostsStaff>222400</CostsStaff>
                <CostsBoughtPlayers>0</CostsBoughtPlayers>
                <CostsArenaBuilding>0</CostsArenaBuilding>
                <CostsTemporary>30000</CostsTemporary>
                <CostsYouth>0</CostsYouth>
                <CostsSum>914260</CostsSum>
                <ExpectedWeeksTotal>-129260</ExpectedWeeksTotal>
                <LastIncomeSpectators>1999320</LastIncomeSpectators>
                <LastIncomeSponsors>680000</LastIncomeSponsors>
                <LastIncomeFinancial>0</LastIncomeFinancial>
                <LastIncomeSoldPlayers>0</LastIncomeSoldPlayers>
                <LastIncomeSoldPlayersCommission>0</LastIncomeSoldPlayersCommission>
                <LastIncomeTemporary>2400</LastIncomeTemporary>
                <LastIncomeSum>2786720</LastIncomeSum>
                <LastCostsArena>146000</LastCostsArena>
                <LastCostsPlayers>425820</LastCostsPlayers>
                <LastCostsFinancial>0</LastCostsFinancial>
                <LastCostsStaff>222400</LastCostsStaff>
                <LastCostsBoughtPlayers>9224280</LastCostsBoughtPlayers>
                <LastCostsArenaBuilding>3400000</LastCostsArenaBuilding>
                <LastCostsTemporary>80000</LastCostsTemporary>
                <LastCostsYouth>100000</LastCostsYouth>
                <LastCostsSum>13598500</LastCostsSum>
                <LastWeeksTotal>-10811780</LastWeeksTotal>
              </Team>
            </HattrickData>
            """;

    /** Every economy value CHPP sends must come back out of the HRF the builder writes. */
    @Test
    void economySurvivesTheHrfRoundTrip() {
        final var builder = new HRFStringBuilder();
        builder.createEconomy(XMLEconomyParser.parseEconomyFromString(ECONOMY_XML));
        // HRFStringParser lower-cases the keys
        final var properties = new Properties();
        for (var line : builder.createHRF().toString().split("\n")) {
            final int eq = line.indexOf('=');
            if (eq > 0) properties.setProperty(line.substring(0, eq).toLowerCase(Locale.ENGLISH), line.substring(eq + 1));
        }
        final var economy = new Economy(properties);

        assertThat(economy.getCash()).isEqualTo(new AmountOfMoney(3628160));
        assertThat(economy.getCostsStaff()).isEqualTo(new AmountOfMoney(222400));
        assertThat(economy.getCostsTemporary()).isEqualTo(new AmountOfMoney(30000));
        assertThat(economy.getLastCostsStaff()).isEqualTo(new AmountOfMoney(222400));
        assertThat(economy.getLastCostsTemporary()).isEqualTo(new AmountOfMoney(80000));
        assertThat(economy.getLastCostsArena()).isEqualTo(new AmountOfMoney(146000));
        assertThat(economy.getLastCostsYouth()).isEqualTo(new AmountOfMoney(100000));
        assertThat(economy.getLastWeeksTotal()).isEqualTo(new AmountOfMoney(-10811780));
    }
}
