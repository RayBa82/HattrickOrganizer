package core.db;

import core.file.xml.XMLPlayerDetailsParser;
import core.model.player.PlayerTrophy;
import core.util.HODateTime;
import core.util.ResourceUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/** PLAYERDETAILS keeps every download, PLAYERTROPHY each trophy once. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PlayerDetailsTableTest {

    private Connection conn;
    private PlayerDetailsTable details;
    private PlayerTrophyTable trophies;

    @BeforeAll
    void setUp() throws SQLException {
        conn = DriverManager.getConnection("jdbc:hsqldb:mem:playerdetailstestdb", "SA", "");
        final var connectionManager = new ConnectionManager();
        connectionManager.connect(conn);
        details = new PlayerDetailsTable(connectionManager);
        details.createTable();
        trophies = new PlayerTrophyTable(connectionManager);
        trophies.createTable();
    }

    @AfterAll
    void cleanUp() throws SQLException {
        details.tryDropTable();
        trophies.tryDropTable();
        conn.close();
    }

    private static String full() throws IOException {
        return Objects.requireNonNull(ResourceUtils.getResourceFileAsString("chpp/playerdetails_3.3.xml"));
    }

    @Test
    void roundTripsADownloadAndKeepsEachTrophyOnce() throws IOException {
        final var first = XMLPlayerDetailsParser.parse(full(), HODateTime.fromHT("2026-11-20 12:00:00"));
        details.storePlayerDetails(first);
        trophies.storePlayerTrophies(first.getTrophies());
        final var second = XMLPlayerDetailsParser.parse(full(), HODateTime.fromHT("2026-11-21 12:00:00"));
        second.setHighestBid(3_200_000L);
        details.storePlayerDetails(second);
        trophies.storePlayerTrophies(second.getTrophies());

        final var latest = details.loadLatestPlayerDetails(476487492);
        assertThat(latest.getFetchedAt()).isEqualTo(HODateTime.fromHT("2026-11-21 12:00:00"));
        assertThat(latest.getHighestBid()).isEqualTo(3_200_000L);
        assertThat(latest.getMaxBid()).isEqualTo(3_500_000L);
        assertThat(latest.getTrainerSkillLevel()).isEqualTo(4);
        assertThat(latest.getMotherClubBonus()).isFalse();
        assertThat(latest.getStatement()).isEqualTo("Ready.");
        assertThat(latest.getDeadline()).isEqualTo(HODateTime.fromHT("2026-11-22 20:08:31"));
        assertThat(latest.getRelatedPlayerName()).isEqualTo("Rui Gaspar");
        assertThat(latest.getLastMatchRating()).isEqualTo(4.5);
        assertThat(latest.getKeeperSkill()).isEqualTo(1);

        assertThat(trophies.loadPlayerTrophies(476487492))
                .extracting(PlayerTrophy::getTrophySeason, PlayerTrophy::getTrophyTypeId)
                .containsExactly(tuple(92, 17), tuple(91, 19));
    }

    @Test
    void keepsNullsOfAPrunedDownload() {
        final var xml = """
                <HattrickData><Player><PlayerID>6</PlayerID>
                  <PlayerSkills><StaminaSkill>6</StaminaSkill></PlayerSkills>
                  <TransferListed>False</TransferListed><TransferDetails />
                </Player></HattrickData>
                """;
        details.storePlayerDetails(XMLPlayerDetailsParser.parse(xml, HODateTime.fromHT("2026-11-20 12:00:00")));

        final var loaded = details.loadLatestPlayerDetails(6);
        assertThat(loaded.getStaminaSkill()).isEqualTo(6);
        assertThat(loaded.getKeeperSkill()).isNull();
        assertThat(loaded.getAskingPrice()).isNull();
        assertThat(loaded.getTransferListed()).isFalse();
        assertThat(loaded.getLastMatchRating()).isNull();
        assertThat(trophies.loadPlayerTrophies(6)).isEmpty();
    }
}
