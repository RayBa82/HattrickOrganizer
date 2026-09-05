package core.mcp;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import module.lineup.Lineup;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Replays the multi-slot field write reported to drop the left forward (113): the 3-4-0 that HO
 * held for FC GaisBeach 04 on 2026-09-03, rewritten as the 3-5-2 apply_lineup was asked for in one
 * positions array.
 */
class LineupApplyOrderTest {

    private static Lineup broken340() {
        final var lineup = new Lineup();
        lineup.setSpielerAtPosition(100, 461748897);
        lineup.setSpielerAtPosition(102, 486084140);
        lineup.setSpielerAtPosition(103, 465315671);
        lineup.setSpielerAtPosition(104, 486069728);
        lineup.setSpielerAtPosition(106, 485867546);
        lineup.setSpielerAtPosition(108, 485870031);
        lineup.setSpielerAtPosition(109, 483563274);
        lineup.setSpielerAtPosition(110, 485818207);
        return lineup;
    }

    /** The 14 field slots as they ended up in the database after the successful re-apply. */
    private static Map<Integer, Integer> target352() {
        final var wanted = new LinkedHashMap<Integer, Integer>();
        wanted.put(100, 461748897);
        wanted.put(101, 465315671);
        wanted.put(102, 0);
        wanted.put(103, 486069728);
        wanted.put(104, 0);
        wanted.put(105, 486084140);
        wanted.put(106, 510470571);
        wanted.put(107, 483563274);
        wanted.put(108, 485870031);
        wanted.put(109, 485867546);
        wanted.put(110, 485818207);
        wanted.put(111, 511469860);
        wanted.put(112, 0);
        wanted.put(113, 501164800);
        return wanted;
    }

    private static void assertApplied(Lineup lineup, Map<Integer, Integer> wanted) {
        for (final var slot : wanted.entrySet()) {
            assertThat(lineup.getPositionById(slot.getKey()).getPlayerId())
                    .as("roleId " + slot.getKey())
                    .isEqualTo(slot.getValue());
        }
    }

    @Test
    void oneWritePerSlotInAscendingRoleIdOrderFillsEverySlot() {
        final var lineup = broken340();
        final var wanted = target352();
        wanted.forEach((roleId, playerId) ->
                lineup.setSpielerAtPosition(roleId, playerId, (byte) 0));
        assertApplied(lineup, wanted);
    }

    /**
     * A {@link Lineup}'s bench slots are 200-213; 114-118 are the ids the match lineup file uses
     * and belong to no slot, so {@code setSpielerAtPosition} drops a write to them without a word.
     * {@code LineupTools.SPEC_DOC} used to advertise 114-118, which is why every bench write
     * through apply_lineup vanished. {@code requireSlot} is what now makes that loud.
     */
    @Test
    void aWriteToARoleIdNoSlotCarriesIsRefusedRatherThanDropped() {
        final var lineup = broken340();
        for (int i = 114; i <= 118; i++) {
            final int roleId = i;
            assertThat(lineup.getPositionById(roleId)).as("roleId " + roleId).isNull();
            lineup.setSpielerAtPosition(roleId, 486058977);
            assertThatThrownBy(() -> LineupTools.requireSlot(lineup, roleId))
                    .hasMessageContaining("roleId " + roleId);
        }
        assertThat(lineup.getPositionByPlayerId(486058977)).isNull();

        lineup.setSpielerAtPosition(200, 486058977);
        assertThat(lineup.getPositionById(200).getPlayerId()).isEqualTo(486058977);
        assertThatCode(() -> LineupTools.requireSlot(lineup, 200)).doesNotThrowAnyException();
        assertThatCode(() -> LineupTools.requireSlot(lineup, 213)).doesNotThrowAnyException();
    }

    /**
     * The check behind apply_lineup's {@code notApplied}. HO's Lineup tab clears a slot whose
     * occupant its assistant filter hides, so a write can be undone straight after it lands;
     * apply_lineup refreshes the tab and then reports what survived rather than what was asked for.
     */
    @Test
    void aPositionHoRefusedToKeepIsReported() throws Exception {
        final var lineup = broken340();
        final var positions = new JsonArray();
        positions.add(requested(111, 511469860));
        positions.add(requested(113, 501164800));
        final var spec = new JsonObject();
        spec.add("positions", positions);

        lineup.setSpielerAtPosition(111, 511469860, (byte) 0);
        lineup.setSpielerAtPosition(113, 501164800, (byte) 0);
        assertThat(LineupTools.refusedByHo(lineup, spec)).isEmpty();

        // What PlayerPositionPanel.setPlayersList does to a slot the filter will not offer.
        lineup.setSpielerAtPosition(113, 0);

        final var refused = LineupTools.refusedByHo(lineup, spec);
        assertThat(refused).hasSize(1);
        final var entry = refused.get(0).getAsJsonObject();
        assertThat(entry.get("roleId").getAsInt()).isEqualTo(113);
        assertThat(entry.get("requestedPlayerId").getAsInt()).isEqualTo(501164800);
        assertThat(entry.get("actualPlayerId").getAsInt()).isZero();
    }

    private static JsonObject requested(int roleId, int playerId) {
        final var position = new JsonObject();
        position.addProperty("roleId", roleId);
        position.addProperty("playerId", playerId);
        return position;
    }

    @Test
    void theSameWriteWithTheEmptiedSlotsLastStillFillsEverySlot() {
        final var lineup = broken340();
        final var wanted = target352();
        wanted.forEach((roleId, playerId) -> {
            if (playerId != 0) {
                lineup.setSpielerAtPosition(roleId, playerId, (byte) 0);
            }
        });
        wanted.forEach((roleId, playerId) -> {
            if (playerId == 0) {
                lineup.setSpielerAtPosition(roleId, playerId, (byte) 0);
            }
        });
        assertApplied(lineup, wanted);
    }
}
