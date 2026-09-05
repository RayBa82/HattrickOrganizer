package core.net;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ConnectorWriteUrlTest {

    @Test
    void setTrainingUrl_hasFileVersionActionAndEveryParameter() {
        final var url = Connector.setTrainingUrl(1500, 3, 40, 10);
        assertThat(url).contains("file=training")
                .contains("version=2.2")
                .contains("actionType=setTraining")
                .contains("teamId=1500")
                .contains("trainingType=3")
                .contains("trainingLevel=40")
                .contains("trainingLevelStamina=10");
    }

    @Test
    void challengesUrl_appendsEveryParam() {
        final var params = new LinkedHashMap<String, String>();
        params.put("opponentTeamId", "999");
        params.put("matchType", "0");
        params.put("matchPlace", "2");
        params.put("neutralArenaId", "42");
        final var url = Connector.challengesUrl("challenge", params);
        assertThat(url).contains("file=challenges")
                .contains("version=1.6")
                .contains("actionType=challenge")
                .contains("opponentTeamId=999")
                .contains("matchType=0")
                .contains("matchPlace=2")
                .contains("neutralArenaId=42");
    }

    @Test
    void challengesUrl_viewWithNoParamsIsJustFileVersionAction() {
        final var url = Connector.challengesUrl("view", Map.of());
        assertThat(url).contains("file=challenges").contains("version=1.6").contains("actionType=view");
        assertThat(url).doesNotContain("&opponentTeamId");
    }

    @Test
    void placeBidUrl_includesMaxBidAmountOnlyWhenGiven() {
        assertThat(Connector.placeBidUrl(111, 222, 1_500_000L, null))
                .contains("file=playerdetails")
                .contains("version=3.2")
                .contains("actionType=placeBid")
                .contains("playerID=111")
                .contains("teamId=222")
                .contains("bidAmount=1500000")
                .doesNotContain("maxBidAmount");
        assertThat(Connector.placeBidUrl(111, 222, 1_500_000L, 2_000_000L))
                .contains("bidAmount=1500000")
                .contains("maxBidAmount=2000000");
    }

    @Test
    void currentBidsUrl_isJustFileAndVersion() {
        assertThat(Connector.currentBidsUrl())
                .contains("file=currentbids")
                .contains("version=1.0");
    }
}
