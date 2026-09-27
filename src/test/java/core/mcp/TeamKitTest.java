package core.mcp;

import com.google.gson.JsonObject;
import core.model.XtraData;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TeamKitTest {

    @Test
    void protocolRelativeUrisGetHttps_andTheColoursAreAnObject() {
        final var xtra = new XtraData();
        xtra.setLogoURL("//res.hattrick.org/teamlogo/1500.png");
        xtra.setDressURI("//res.hattrick.org/kits/matchKitSmall_1500.png");
        xtra.setDressAlternateURI("https://res.hattrick.org/kits/alt.png");
        xtra.setTeamColorBackground("1a2b3c");
        xtra.setTeamColorText("ffffff");

        final var json = new JsonObject();
        TeamTools.addKit(json, xtra);

        assertThat(json.get("logoUrl").getAsString()).isEqualTo("https://res.hattrick.org/teamlogo/1500.png");
        assertThat(json.get("dressUri").getAsString()).isEqualTo("https://res.hattrick.org/kits/matchKitSmall_1500.png");
        assertThat(json.get("dressAlternateUri").getAsString()).isEqualTo("https://res.hattrick.org/kits/alt.png");
        assertThat(json.getAsJsonObject("teamColors").get("background").getAsString()).isEqualTo("1a2b3c");
        assertThat(json.getAsJsonObject("teamColors").get("text").getAsString()).isEqualTo("ffffff");
    }

    /** No Supporter, or no download since the columns exist: every field is there, as null. */
    @Test
    void whatIsMissingIsNull_notAnError() {
        final var xtra = new XtraData();
        xtra.setTeamColorBackground("");

        final var json = new JsonObject();
        TeamTools.addKit(json, xtra);
        assertThat(json.get("teamColors").isJsonNull()).isTrue();
        assertThat(json.get("dressUri").isJsonNull()).isTrue();

        final var none = new JsonObject();
        TeamTools.addKit(none, null);
        assertThat(none.keySet()).containsExactlyInAnyOrder("logoUrl", "dressUri", "dressAlternateUri", "teamColors");
        assertThat(none.get("logoUrl").isJsonNull()).isTrue();
    }
}
