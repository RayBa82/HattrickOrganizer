package core.mcp;

import core.mcp.FinanceForecast.Fixture;
import core.mcp.FinanceForecast.Kind;
import core.model.enums.MatchType;
import core.util.HODateTime;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class FinanceForecastTest {

    private static final HODateTime START = HODateTime.fromHT("2026-09-27 12:00:00");
    private static final long FIXED = 51_586 + 22_240 + 10_000 + 14_600;   // 98,426 a week

    private static HODateTime day(int offset) {
        return START.plusDaysAtSameLocalTime(offset);
    }

    private static FinanceForecast.Inputs inputs(Map<Kind, Long> gates, List<Fixture> fixtures) {
        return new FinanceForecast.Inputs(350_000, 78_500, 51_586, 22_240, 10_000,
                14_600, 14_600, null, gates, fixtures, START);
    }

    @Test
    void eachMatchEarnsHattricksShareOfItsGate() {
        final var gates = Map.of(Kind.LEAGUE, 180_000L, Kind.CUP, 60_000L, Kind.EVEN_SPLIT, 30_000L);
        final var forecast = FinanceForecast.forecast(inputs(gates, List.of(
                new Fixture(day(1), true, Kind.LEAGUE),        // 180,000 - home league keeps it all
                new Fixture(day(2), false, Kind.LEAGUE),       // 0 - away league pays the host
                new Fixture(day(3), true, Kind.CUP),           // 40,200 - home cup 67 %
                new Fixture(day(4), false, Kind.CUP),          // 19,800 - away cup 33 %
                new Fixture(day(5), true, Kind.EVEN_SPLIT),    // 15,000 - friendly 50 %
                new Fixture(day(6), false, Kind.EVEN_SPLIT),   // 15,000 - either way
                new Fixture(day(6), true, Kind.NONE))), 1);    // 0 - a tournament pays nothing

        final var week = forecast.getAsJsonArray("weeks").get(0).getAsJsonObject();
        assertThat(week.get("gateIncome").getAsLong()).isEqualTo(180_000 + 40_200 + 19_800 + 15_000 + 15_000);
        assertThat(week.get("gateMatches").getAsInt()).isEqualTo(5);
        assertThat(week.get("net").getAsLong()).isEqualTo(78_500 + 270_000 - FIXED);
        assertThat(forecast.get("weeksWithoutHomeLeagueGate").getAsInt()).isZero();
    }

    @Test
    void aKindWithNoRecentHomeMatchIsNotCounted_butSaidSo() {
        final var forecast = FinanceForecast.forecast(inputs(Map.of(Kind.LEAGUE, 180_000L),
                List.of(new Fixture(day(3), true, Kind.CUP))), 1);

        final var week = forecast.getAsJsonArray("weeks").get(0).getAsJsonObject();
        assertThat(week.get("gateIncome").getAsLong()).isZero();
        assertThat(week.get("matchesNotCounted").getAsInt()).isEqualTo(1);
        assertThat(forecast.getAsJsonObject("assumptions").getAsJsonObject("fullHomeGate")
                .get("cup").isJsonNull()).isTrue();
        assertThat(forecast.get("weeksWithoutHomeLeagueGate").getAsInt()).isEqualTo(1);
    }

    @Test
    void theNewSeatsCostUpkeepFromTheWeekTheyOpen() {
        final var in = new FinanceForecast.Inputs(350_000, 78_500, 51_586, 22_240, 10_000,
                14_600, 20_600, day(10), Map.of(), List.of(), START);
        final var weeks = FinanceForecast.forecast(in, 3).getAsJsonArray("weeks");

        assertThat(weeks.get(1).getAsJsonObject().get("costs").getAsLong()).isEqualTo(FIXED);           // days 7-13
        assertThat(weeks.get(2).getAsJsonObject().get("costs").getAsLong()).isEqualTo(FIXED + 6_000);   // days 14-20
    }

    @Test
    void matchTypesMapToHattricksGateSplits() {
        assertThat(TeamTools.gateKind(MatchType.LEAGUE)).isEqualTo(Kind.LEAGUE);
        assertThat(TeamTools.gateKind(MatchType.CUP)).isEqualTo(Kind.CUP);
        assertThat(TeamTools.gateKind(MatchType.QUALIFICATION)).isEqualTo(Kind.EVEN_SPLIT);
        assertThat(TeamTools.gateKind(MatchType.FRIENDLYNORMAL)).isEqualTo(Kind.EVEN_SPLIT);
        assertThat(TeamTools.gateKind(MatchType.INTFRIENDLYCUPRULES)).isEqualTo(Kind.EVEN_SPLIT);
        assertThat(TeamTools.gateKind(MatchType.TOURNAMENTGROUP)).isEqualTo(Kind.NONE);
    }

    /** A season's projection is the largest there is, and it stays far inside the tool budget. */
    @Test
    void aFullSeasonFitsTheToolBudget() {
        final var in = new FinanceForecast.Inputs(0, 1, 1, 1, 1, 1, 1, null,
                Map.of(Kind.LEAGUE, 1L), List.of(new Fixture(day(1), true, Kind.CUP)), START);
        assertThat(FinanceForecast.forecast(in, FinanceForecast.MAX_WEEKS).toString().length())
                .isLessThan(4_000);
    }
}
