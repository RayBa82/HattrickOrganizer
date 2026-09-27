package core.mcp;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import core.util.HODateTime;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * get_finances' week-by-week cash projection. Plain arithmetic over figures TeamTools gathers,
 * so it can be checked without a database.
 * <p>
 * Gates follow Hattrick's split (wiki: Stadium): league home 100 %, cup home 67 % / away 33 %,
 * friendlies and qualifiers 50 % either way. A match's gate is our own recent home crowd of that
 * kind at HO's admission prices; for an away match that is a stand-in for the host's crowd.
 * Takings are booked in the week of the match - the week the last statement shows them in.
 * <p>
 * Conservative where it has to guess: recurring costs take the higher of this and last week's
 * line, a kind without a recent home match to learn from earns nothing, cup semi-finals and
 * finals (neutral, 50 / 50) are priced as ordinary cup ties, and new seats cost upkeep from the
 * day they open but earn nothing until demand grows into them.
 */
final class FinanceForecast {

    static final int MAX_WEEKS = 16;

    /** How a match's gate is shared; NONE for the kinds that pay us nothing (tournaments...). */
    enum Kind {
        LEAGUE(1.0, 0.0), CUP(0.67, 0.33), EVEN_SPLIT(0.5, 0.5), NONE(0.0, 0.0);

        final double homeShare;
        final double awayShare;

        Kind(double homeShare, double awayShare) {
            this.homeShare = homeShare;
            this.awayShare = awayShare;
        }
    }

    record Fixture(HODateTime date, boolean home, Kind kind) {
    }

    /**
     * Weekly amounts in the user's currency. {@code expansionDate} null = seats already open.
     * {@code homeGates}: the full gate of a recent home match per kind; a kind missing from it
     * has no sample and is not counted.
     */
    record Inputs(long startCash, long sponsors, long wages, long staff, long youth,
                  long arenaNow, long arenaAfterExpansion, @Nullable HODateTime expansionDate,
                  Map<Kind, Long> homeGates, List<Fixture> fixtures, HODateTime start) {
    }

    private FinanceForecast() {
    }

    static JsonObject forecast(Inputs in, int weeks) {
        final var json = new JsonObject();
        json.add("assumptions", assumptions(in));
        json.addProperty("startCash", in.startCash());

        var cash = in.startCash();
        var lowest = cash;
        var weeksWithoutLeagueGate = 0;
        final var rows = new JsonArray();
        for (var week = 0; week < weeks; week++) {
            final var from = in.start().plusDaysAtSameLocalTime(7L * week);
            final var to = in.start().plusDaysAtSameLocalTime(7L * (week + 1));
            final var gates = gates(in, from, to);
            final var expanded = in.expansionDate() == null || !in.expansionDate().isAfter(from);
            final var arena = expanded ? in.arenaAfterExpansion() : in.arenaNow();
            final var income = in.sponsors() + gates.income;
            final var costs = in.wages() + in.staff() + in.youth() + arena;
            cash += income - costs;
            lowest = Math.min(lowest, cash);
            if (!gates.homeLeague) {
                weeksWithoutLeagueGate++;
            }

            final var row = new JsonObject();
            row.addProperty("week", week + 1);
            row.addProperty("from", from.toHT());
            row.addProperty("gateMatches", gates.counted);
            row.addProperty("gateIncome", gates.income);
            if (gates.notCounted > 0) {
                row.addProperty("matchesNotCounted", gates.notCounted);
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
        json.addProperty("weeksWithoutHomeLeagueGate", weeksWithoutLeagueGate);
        return json;
    }

    private record WeekGates(long income, int counted, int notCounted, boolean homeLeague) {
    }

    private static WeekGates gates(Inputs in, HODateTime from, HODateTime to) {
        long income = 0;
        var counted = 0;
        var notCounted = 0;
        var homeLeague = false;
        for (final var fixture : in.fixtures()) {
            if (fixture.date().isBefore(from) || !fixture.date().isBefore(to)) {
                continue;
            }
            final var kind = fixture.kind();
            final var share = fixture.home() ? kind.homeShare : kind.awayShare;
            if (share == 0) {
                continue;   // an away league match or a kind without a gate: nothing is owed to us
            }
            final var gate = in.homeGates().get(kind);
            if (gate == null) {
                notCounted++;
                continue;
            }
            income += Math.round(gate * share);
            counted++;
            homeLeague |= fixture.home() && kind == Kind.LEAGUE;
        }
        return new WeekGates(income, counted, notCounted, homeLeague);
    }

    private static JsonObject assumptions(Inputs in) {
        final var json = new JsonObject();
        json.addProperty("weeklySponsors", in.sponsors());
        json.addProperty("weeklyWages", in.wages());
        json.addProperty("weeklyStaff", in.staff());
        json.addProperty("weeklyYouth", in.youth());
        json.addProperty("weeklyArena", in.arenaNow());
        if (in.arenaAfterExpansion() != in.arenaNow()) {
            json.addProperty("weeklyArenaAfterExpansion", in.arenaAfterExpansion());
            json.addProperty("expansionDate",
                    in.expansionDate() == null ? null : in.expansionDate().toHT());
        }
        final var gates = new JsonObject();
        final var names = new EnumMap<>(Map.of(Kind.LEAGUE, "league", Kind.CUP, "cup",
                Kind.EVEN_SPLIT, "friendlyOrQualifier"));
        names.forEach((kind, name) -> gates.addProperty(name, in.homeGates().get(kind)));
        json.add("fullHomeGate", gates);
        json.addProperty("gateShares", "league home 100%; cup home 67%, away 33%; friendlies and "
                + "qualifiers 50% either way. Away gates use our own home crowd of that kind as a "
                + "stand-in; a kind with no recent home match (null above) is not counted");
        return json;
    }
}
