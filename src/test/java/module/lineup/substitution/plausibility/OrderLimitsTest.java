package module.lineup.substitution.plausibility;

import module.lineup.substitution.model.MatchOrderType;
import module.lineup.substitution.model.Substitution;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OrderLimitsTest {

    private static List<Substitution> orders(MatchOrderType... types) {
        final var list = new ArrayList<Substitution>();
        for (var type : types) {
            list.add(new Substitution(type));
        }
        return list;
    }

    @Test
    void aFourthSubstitutionIsOneTooMany_whateverTheTacticalAssistant() {
        final var subs = orders(MatchOrderType.SUBSTITUTION, MatchOrderType.SUBSTITUTION,
                MatchOrderType.SUBSTITUTION, MatchOrderType.SUBSTITUTION);
        assertThat(OrderLimits.check(subs, 5)).containsExactly(null, null, null, Error.TOO_MANY_ORDERS);
    }

    @Test
    void fiveOrdersPlusOnePerTacticalAssistantLevel() {
        final var six = orders(MatchOrderType.NEW_BEHAVIOUR, MatchOrderType.NEW_BEHAVIOUR,
                MatchOrderType.POSITION_SWAP, MatchOrderType.SUBSTITUTION,
                MatchOrderType.NEW_BEHAVIOUR, MatchOrderType.POSITION_SWAP);
        assertThat(OrderLimits.check(six, 0).get(5)).isEqualTo(Error.TOO_MANY_ORDERS);
        assertThat(OrderLimits.check(six, 1)).containsOnlyNulls();
    }

    @Test
    void oneManMarkingOrder_notCountedAgainstTheFive() {
        final var list = orders(MatchOrderType.MAN_MARKING, MatchOrderType.NEW_BEHAVIOUR,
                MatchOrderType.NEW_BEHAVIOUR, MatchOrderType.NEW_BEHAVIOUR,
                MatchOrderType.NEW_BEHAVIOUR, MatchOrderType.NEW_BEHAVIOUR, MatchOrderType.MAN_MARKING);
        assertThat(OrderLimits.check(list, 0))
                .containsExactly(null, null, null, null, null, null, Error.TOO_MANY_ORDERS);
    }

    @Test
    void whatTheTeamOrdersTabMayStillAdd() {
        final var limits = new OrderLimits(0);
        for (var order : orders(MatchOrderType.SUBSTITUTION, MatchOrderType.SUBSTITUTION,
                MatchOrderType.SUBSTITUTION, MatchOrderType.MAN_MARKING)) {
            limits.add(order);
        }
        assertThat(limits.canAddSubstitution()).isFalse();
        assertThat(limits.canAddOrder()).isTrue();
        assertThat(limits.canAddManMarking()).isFalse();
    }
}
