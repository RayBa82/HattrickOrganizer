package core.mcp;

import com.google.gson.JsonObject;
import core.constants.player.PlayerSkill;
import core.file.xml.XMLTransferSearchParser.Hit;
import core.model.player.Player;
import core.util.HODateTime;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.within;

class McpJsonTest {

    @Test
    void player_carriesIdentityAgeAndTheEightSkills() {
        final var player = new Player();
        player.setPlayerId(4711);
        player.setAge(27);
        player.setAgeDays(42);
        player.setValue4Skill(PlayerSkill.PLAYMAKING, 11);
        player.setValue4Skill(PlayerSkill.DEFENDING, 7);

        final var json = McpJson.player(player);

        assertThat(json.get("playerId").getAsInt()).isEqualTo(4711);
        assertThat(json.get("ageYears").getAsInt()).isEqualTo(27);
        assertThat(json.get("ageDays").getAsInt()).isEqualTo(42);
        assertThat(json.getAsJsonObject("skills").get("playmaking").getAsInt()).isEqualTo(11);
        assertThat(json.getAsJsonObject("skills").get("defending").getAsInt()).isEqualTo(7);
        assertThat(json.has("positionRatings")).isFalse();
    }

    @Test
    void player_reportsSublevelsSoABreakpointIsVisible() {
        final var player = new Player();
        player.setValue4Skill(PlayerSkill.SCORING, 9);
        player.setSubskill4PlayerSkill(PlayerSkill.SCORING, 0.87);

        final var json = McpJson.player(player);

        assertThat(json.getAsJsonObject("skills").get("scoring").getAsInt()).isEqualTo(9);
        assertThat(json.getAsJsonObject("skillsWithSublevel").get("scoring").getAsDouble())
                .isCloseTo(9.87, within(0.01));
    }

    @Test
    void isAvailable_isFalseWhenInjuredOrSuspended() {
        final var fit = new Player();
        final var injured = new Player();
        injured.setInjuryWeeks(2);
        final var suspended = new Player();
        suspended.setTotalCards(3);

        assertThat(McpJson.isAvailable(fit)).isTrue();
        assertThat(McpJson.isAvailable(injured)).isFalse();
        assertThat(McpJson.isAvailable(suspended)).isFalse();
        assertThat(McpJson.player(injured).get("available").getAsBoolean()).isFalse();
        assertThat(McpJson.player(suspended).get("suspended").getAsBoolean()).isTrue();
    }

    @Test
    void hit_survivesANullDetailsBlock() {
        final var expired = new Hit(999, "Gone Guy", 3, 100_000L,
                HODateTime.fromHT("2026-08-01 20:00:00"), 120_000L, "", 0, "", 0, null);

        final var json = McpJson.hit(expired);

        assertThat(json.get("playerId").getAsInt()).isEqualTo(999);
        assertThat(json.get("highestBid").getAsLong()).isEqualTo(120_000L);
        assertThat(json.get("details").isJsonNull()).isTrue();
    }

    @Test
    void reqInt_rejectsAMissingArgument() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> McpJson.reqInt(new JsonObject(), "playerId"))
                .withMessageContaining("playerId");
    }

    @Test
    void optInt_fallsBackToTheDefault() {
        assertThat(McpJson.optInt(new JsonObject(), "ageTolerance", 1)).isEqualTo(1);
    }
}
