package core.net;

import core.constants.player.PlayerSkill;
import core.constants.player.PlayerSpeciality;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class TransferSearchQueryTest {

    /** The example of the CHPP documentation: playmaking 7 to 9, age 20 to 22. */
    @Test
    void toQueryString_onlyMandatoryParameters() {
        final var query = TransferSearchQuery.builder()
                .ageMin(20)
                .ageMax(22)
                .skillType1(TransferSearchQuery.Skill.PLAYMAKING)
                .minSkillValue1(7)
                .maxSkillValue1(9)
                .build();

        assertThat(query.toQueryString())
                .isEqualTo("&ageMin=20&ageMax=22&skillType1=8&minSkillValue1=7&maxSkillValue1=9");
    }

    @Test
    void toQueryString_optionalParameters() {
        final var query = TransferSearchQuery.builder()
                .ageMin(17)
                .ageMax(19)
                .skillType1(TransferSearchQuery.Skill.SCORING)
                .minSkillValue1(10)
                .maxSkillValue1(14)
                .skillType2(TransferSearchQuery.Skill.WINGER)
                .minSkillValue2(8)
                .maxSkillValue2(12)
                .specialty(PlayerSpeciality.QUICK)
                .priceMax(2000000)
                .pageSize(100)
                .build();

        assertThat(query.toQueryString()).isEqualTo("&ageMin=17&ageMax=19&skillType1=5"
                + "&minSkillValue1=10&maxSkillValue1=14&skillType2=6&minSkillValue2=8"
                + "&maxSkillValue2=12&specialty=2&priceMax=2000000&pageSize=100");
    }

    @Test
    void build_missingMandatoryParameterIsRejected() {
        assertThatNullPointerException().isThrownBy(() -> TransferSearchQuery.builder()
                .ageMin(20)
                .ageMax(22)
                .minSkillValue1(7)
                .maxSkillValue1(9)
                .build());
    }

    @Test
    void skill_mapsToTheMatchingHOSkill() {
        assertThat(TransferSearchQuery.Skill.KEEPER.toPlayerSkill()).isEqualTo(PlayerSkill.KEEPER);
        assertThat(TransferSearchQuery.Skill.SETPIECES.toPlayerSkill()).isEqualTo(PlayerSkill.SETPIECES);
    }
}
