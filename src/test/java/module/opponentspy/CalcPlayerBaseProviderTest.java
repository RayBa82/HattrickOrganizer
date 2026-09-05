package module.opponentspy;

import core.model.player.IMatchRoleID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The age bands used to be written as a cascade of lower bounds
 * ({@code age < 22 / age >= 22 / age >= 27 / age >= 29}), so the second branch swallowed every
 * age above 21 and the last two were unreachable - every senior player was estimated on the
 * 22-year-old table.
 */
class CalcPlayerBaseProviderTest {

    private static CalcVariables base(int age, int role) {
        return new CalcPlayerBaseProvider()
                .getCalcPlayerBase(age, null, 0, 1d, 1d, 0, role, 0);
    }

    @Test
    void outfieldAgeBandsAreReachable() {
        // central defender: <22 -> 7, 22-26 -> 15, 27-28 -> 17, 29+ -> 18
        assertThat(base(21, IMatchRoleID.CENTRAL_DEFENDER).getDefending()).isEqualTo(7d);
        assertThat(base(24, IMatchRoleID.CENTRAL_DEFENDER).getDefending()).isEqualTo(15d);
        assertThat(base(27, IMatchRoleID.CENTRAL_DEFENDER).getDefending()).isEqualTo(17d);
        assertThat(base(30, IMatchRoleID.CENTRAL_DEFENDER).getDefending()).isEqualTo(18d);
    }

    @Test
    void goalkeeperAgeBandsAreReachableAndUseTheirOwnThresholds() {
        // keeper: <22 -> 7, 22-24 -> 15, 25-27 -> 17, 28+ -> 20
        assertThat(base(21, IMatchRoleID.KEEPER).getGoalkeeping()).isEqualTo(7d);
        assertThat(base(24, IMatchRoleID.KEEPER).getGoalkeeping()).isEqualTo(15d);
        assertThat(base(26, IMatchRoleID.KEEPER).getGoalkeeping()).isEqualTo(17d);
        assertThat(base(29, IMatchRoleID.KEEPER).getGoalkeeping()).isEqualTo(20d);
    }
}
