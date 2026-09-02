package core.mcp;

import com.google.gson.JsonParser;
import core.constants.player.PlayerSkill;
import core.constants.player.PlayerSpeciality;
import core.model.player.Player;
import core.net.TransferSearchQuery;
import core.util.AmountOfMoney;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class TransferToolsTest {

    private static Player player(int age, int... skillValues) {
        final var p = new Player();
        p.setPlayerId(4711);
        p.setAge(age);
        p.setAgeDays(0);
        final var skills = new PlayerSkill[]{
                PlayerSkill.KEEPER, PlayerSkill.DEFENDING, PlayerSkill.PLAYMAKING,
                PlayerSkill.PASSING, PlayerSkill.WINGER, PlayerSkill.SCORING,
                PlayerSkill.SETPIECES, PlayerSkill.STAMINA, PlayerSkill.EXPERIENCE,
                PlayerSkill.LEADERSHIP};
        for (int i = 0; i < skillValues.length && i < skills.length; i++) {
            p.setValue4Skill(skills[i], skillValues[i]);
        }
        return p;
    }

    @Test
    void tools_areTheTransferTools() {
        assertThat(TransferTools.tools()).extracting(McpTool::name)
                .containsExactly("search_transfers", "find_comparables", "get_team_transfers");
    }

    @Test
    void comparableQuery_usesAgeAndTheTwoHighestPositionalSkills() {
        //                      kp de pm pa wi sc sp st ex ld
        final var p = player(24, 1, 7, 12, 9, 6, 5, 3, 8, 4, 2);
        p.setSpecialty(PlayerSpeciality.QUICK);

        final var query = TransferTools.comparableQuery(p, 1, 1);

        assertThat(query.ageMin()).isEqualTo(23);
        assertThat(query.ageMax()).isEqualTo(25);
        assertThat(query.skillType1()).isEqualTo(TransferSearchQuery.Skill.PLAYMAKING);
        assertThat(query.minSkillValue1()).isEqualTo(11);
        assertThat(query.maxSkillValue1()).isEqualTo(13);
        assertThat(query.skillType2()).isEqualTo(TransferSearchQuery.Skill.PASSING);
        assertThat(query.minSkillValue2()).isEqualTo(8);
        assertThat(query.maxSkillValue2()).isEqualTo(10);
        assertThat(query.specialty()).isEqualTo(PlayerSpeciality.QUICK);
        assertThat(query.validationErrorKey()).isNull();
    }

    /** Stamina, experience and leadership are never comparability criteria, however high they are. */
    @Test
    void comparableQuery_neverPicksStaminaExperienceOrLeadership() {
        //                      kp de pm pa wi sc sp st ex ld
        final var p = player(28, 1, 4, 3, 2, 1, 1, 1, 20, 15, 18);

        final var query = TransferTools.comparableQuery(p, 1, 1);

        assertThat(query.skillType1()).isEqualTo(TransferSearchQuery.Skill.DEFENDING);
        assertThat(query.skillType2()).isEqualTo(TransferSearchQuery.Skill.PLAYMAKING);
        assertThat(query.validationErrorKey()).isNull();
    }

    /** Hattrick will not accept a keeper query below level 3 or a span wider than 4 levels. */
    @Test
    void comparableQuery_clampsToTheKeeperMinimumAndTheFourLevelSpan() {
        //                      kp de pm pa wi sc sp
        final var p = player(19, 2, 1, 1, 1, 1, 1, 1);

        final var query = TransferTools.comparableQuery(p, 1, 5);

        assertThat(query.skillType1()).isEqualTo(TransferSearchQuery.Skill.KEEPER);
        assertThat(query.minSkillValue1()).isEqualTo(3);       // keeper minimum level
        assertThat(query.maxSkillValue1() - query.minSkillValue1()).isLessThanOrEqualTo(4);
        assertThat(query.validationErrorKey()).isNull();
    }

    /** Hattrick rejects a young age window wider than a year, so an 18yo's window is clamped. */
    @Test
    void comparableQuery_clampsTheYoungAgeWindowToAYear() {
        //                      kp de pm pa wi sc sp
        final var p = player(18, 1, 4, 9, 5, 3, 2, 1);

        final var query = TransferTools.comparableQuery(p, 1, 1);

        assertThat(query.ageMin()).isEqualTo(17);
        assertThat(query.ageMax()).isEqualTo(18);
        assertThat(query.validationErrorKey()).isNull();
    }

    @Test
    void queryFrom_readsSkillNamesRatherThanChppNumbers() {
        final var args = JsonParser.parseString("""
                {"ageMin":20,"ageMax":22,"skillType1":"PLAYMAKING","minSkillValue1":7,
                 "maxSkillValue1":9,"priceMax":2000000}""").getAsJsonObject();

        AmountOfMoney.Companion.setExchangeRate(BigDecimal.TEN);   // a euro league

        final var query = TransferTools.queryFrom(args);

        assertThat(query.skillType1()).isEqualTo(TransferSearchQuery.Skill.PLAYMAKING);
        // The model asks in the user's currency, Hattrick is asked in swedish krona.
        assertThat(query.priceMax()).isEqualTo(20_000_000);
        assertThat(query.pageSize()).isEqualTo(50);            // default
    }

    @Test
    void queryFrom_capsPageSizeAtTheChppMaximum() {
        final var args = JsonParser.parseString("""
                {"ageMin":20,"ageMax":22,"skillType1":"SCORING","minSkillValue1":7,
                 "maxSkillValue1":9,"pageSize":500}""").getAsJsonObject();

        assertThat(TransferTools.queryFrom(args).pageSize()).isEqualTo(100);
    }
}
