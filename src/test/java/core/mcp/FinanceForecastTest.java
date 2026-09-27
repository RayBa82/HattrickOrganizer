package core.mcp;

import core.util.HODateTime;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FinanceForecastTest {

    private static final HODateTime START = HODateTime.fromHT("2026-09-27 12:00:00");

    private static HODateTime day(int offset) {
        return START.plusDaysAtSameLocalTime(offset);
    }

    @Test
    void onlyHomeLeagueMatchesEarnAGate_andTheNewSeatsCostFromTheirOpeningWeek() {
        final var inputs = new FinanceForecast.Inputs(350_000, 78_500, 51_586, 22_240, 10_000,
                14_600, 20_600, day(10), 180_000,
                List.of(new FinanceForecast.Fixture(day(3), true, true),     // week 1: home league
                        new FinanceForecast.Fixture(day(6), true, false),    // week 1: home cup, not counted
                        new FinanceForecast.Fixture(day(10), false, true),   // week 2: away league
                        new FinanceForecast.Fixture(day(17), true, true)),   // week 3: home league
                START);

        final var forecast = FinanceForecast.forecast(inputs, 3);
        final var weeks = forecast.getAsJsonArray("weeks");

        final var fixedBefore = 51_586 + 22_240 + 10_000 + 14_600;   // 98,426
        final var fixedAfter = 51_586 + 22_240 + 10_000 + 20_600;    // 104,426
        final var week1 = weeks.get(0).getAsJsonObject();
        assertThat(week1.get("homeLeagueMatches").getAsInt()).isEqualTo(1);
        assertThat(week1.get("otherHomeMatchesNotCounted").getAsInt()).isEqualTo(1);
        assertThat(week1.get("net").getAsLong()).isEqualTo(78_500 + 180_000 - fixedBefore);

        final var week2 = weeks.get(1).getAsJsonObject();   // starts day 7, expansion opens day 10
        assertThat(week2.get("homeLeagueMatches").getAsInt()).isZero();
        assertThat(week2.get("costs").getAsLong()).isEqualTo(fixedBefore);

        final var week3 = weeks.get(2).getAsJsonObject();   // starts day 14, after the opening
        assertThat(week3.get("costs").getAsLong()).isEqualTo(fixedAfter);

        final long end = 350_000 + (78_500 + 180_000 - fixedBefore) + (78_500 - fixedBefore)
                + (78_500 + 180_000 - fixedAfter);
        assertThat(forecast.get("endCash").getAsLong()).isEqualTo(end);
        assertThat(forecast.get("lowestCash").getAsLong()).isEqualTo(350_000);
        assertThat(forecast.get("weeksWithoutHomeLeagueGate").getAsInt()).isEqualTo(1);
        assertThat(forecast.getAsJsonObject("assumptions").get("weeklyArenaAfterExpansion").getAsLong())
                .isEqualTo(20_600);
    }

    /** A season's projection is the largest there is, and it stays far inside the tool budget. */
    @Test
    void aFullSeasonFitsTheToolBudget() {
        final var inputs = new FinanceForecast.Inputs(0, 1, 1, 1, 1, 1, 1, null, 1, List.of(), START);
        assertThat(FinanceForecast.forecast(inputs, FinanceForecast.MAX_WEEKS).toString().length())
                .isLessThan(3_500);
    }
}
