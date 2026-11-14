package core.file.xml;

import module.lineup.substitution.model.GoalDiffCriteria;
import module.lineup.substitution.model.MatchOrderType;
import module.lineup.substitution.model.RedCardCriteria;
import module.lineup.substitution.model.Substitution;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class XMLMatchLineupParserTest {

    /** The player the order is about: leaving, changing behaviour, or marking. */
    private static final int SUBJECT = 11;

    /** What createSubstitution builds from one Substitution row of matchlineup. */
    private static MatchOrderType parsed(int objectPlayerId, int orderType) {
        return new Substitution(0, objectPlayerId, SUBJECT,
                XMLMatchLineupParser.legacyOrderType((byte) orderType, objectPlayerId, SUBJECT),
                60, (byte) 107, (byte) 1, RedCardCriteria.IGNORE, GoalDiffCriteria.ANY_STANDING)
                .getOrderType();
    }

    @Test
    void orderType4WithTheSamePlayerIsABehaviourChange() {
        // matchlineup 2.2: a behaviour change without a substitution has OrderType 4
        assertThat(parsed(SUBJECT, 4)).isEqualTo(MatchOrderType.NEW_BEHAVIOUR);
    }

    @Test
    void orderType4WithAnotherPlayerStaysManMarking() {
        assertThat(parsed(99, 4)).isEqualTo(MatchOrderType.MAN_MARKING);
    }

    @Test
    void theOtherOrderTypesAreUnchanged() {
        assertThat(parsed(12, 1)).isEqualTo(MatchOrderType.SUBSTITUTION);
        assertThat(parsed(SUBJECT, 1)).isEqualTo(MatchOrderType.NEW_BEHAVIOUR);
        assertThat(parsed(12, 3)).isEqualTo(MatchOrderType.POSITION_SWAP);
    }
}
