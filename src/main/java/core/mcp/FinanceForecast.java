package core.mcp;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import core.util.HODateTime;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * get_finances' week-by-week cash projection. Plain arithmetic over figures TeamTools gathers,
 * so it can be checked without a database.
 * <p>
 * Deliberately conservative: recurring costs take the higher of this and last week's line, only
 * home league matches earn a gate (cup and friendly gates are small and split), and the gate
 * assumes attendance stays where it was - new seats cost upkeep from the day they open but earn
 * nothing until demand grows into them.
 */
final class FinanceForecast {

    static final int MAX_WEEKS = 16;

    /** An upcoming match: only a home league match is counted as income. */
    record Fixture(HODateTime date, boolean home, boolean league) {
    }

    /** Weekly amounts in the user's currency. {@code expansionDate} null = seats already open. */
    record Inputs(long startCash, long sponsors, long wages, long staff, long youth,
                  long arenaNow, long arenaAfterExpansion, @Nullable HODateTime expansionDate,
                  long gatePerHomeLeagueMatch, List<Fixture> fixtures, HODateTime start) {
    }

    private FinanceForecast() {
    }

    static JsonObject forecast(Inputs in, int weeks) {
        final var json = new JsonObject();
        final var assumptions = new JsonObject();
        assumptions.addProperty("weeklySponsors", in.sponsors());
        assumptions.addProperty("weeklyWages", in.wages());
        assumptions.addProperty("weeklyStaff", in.staff());
        assumptions.addProperty("weeklyYouth", in.youth());
        assumptions.addProperty("weeklyArena", in.arenaNow());
        if (in.arenaAfterExpansion() != in.arenaNow()) {
            assumptions.addProperty("weeklyArenaAfterExpansion", in.arenaAfterExpansion());
            assumptions.addProperty("expansionDate",
                    in.expansionDate() == null ? null : in.expansionDate().toHT());
        }
        assumptions.addProperty("gatePerHomeLeagueMatch", in.gatePerHomeLeagueMatch());
        json.add("assumptions", assumptions);
        json.addProperty("startCash", in.startCash());

        var cash = in.startCash();
        var lowest = cash;
        var weeksWithoutGate = 0;
        final var rows = new JsonArray();
        for (var week = 0; week < weeks; week++) {
            final var from = in.start().plusDaysAtSameLocalTime(7L * week);
            final var to = in.start().plusDaysAtSameLocalTime(7L * (week + 1));
            var leagueHome = 0;
            var otherHome = 0;
            for (final var fixture : in.fixtures()) {
                if (fixture.home() && !fixture.date().isBefore(from) && fixture.date().isBefore(to)) {
                    if (fixture.league()) {
                        leagueHome++;
                    } else {
                        otherHome++;
                    }
                }
            }
            final var expanded = in.expansionDate() == null || !in.expansionDate().isAfter(from);
            final var arena = expanded ? in.arenaAfterExpansion() : in.arenaNow();
            final var income = in.sponsors() + leagueHome * in.gatePerHomeLeagueMatch();
            final var costs = in.wages() + in.staff() + in.youth() + arena;
            cash += income - costs;
            lowest = Math.min(lowest, cash);
            if (leagueHome == 0) {
                weeksWithoutGate++;
            }

            final var row = new JsonObject();
            row.addProperty("week", week + 1);
            row.addProperty("from", from.toHT());
            row.addProperty("homeLeagueMatches", leagueHome);
            if (otherHome > 0) {
                row.addProperty("otherHomeMatchesNotCounted", otherHome);
            }
            row.addProperty("income", income);
            row.addProperty("costs", costs);
            row.addProperty("net", income - costs);
            row.addProperty("cash", cash);
            rows.add(row);
        }
        json.add("weeks", rows);
        json.addProperty("lowestCash", lowest);
        json.addProperty("endCash", cash);
        json.addProperty("weeksWithoutHomeLeagueGate", weeksWithoutGate);
        return json;
    }
}
