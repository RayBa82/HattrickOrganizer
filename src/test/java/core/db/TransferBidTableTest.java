package core.db;

import core.file.xml.XMLCurrentBidsParser.BidItem;
import core.util.HODateTime;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TransferBidTableTest {

    private static final int MY_TEAM = 1234;

    private Connection conn;
    private TransferBidTable table;

    @BeforeAll
    void setUp() throws SQLException {
        conn = DriverManager.getConnection("jdbc:hsqldb:mem:transferbidtestdb", "SA", "");
        final var connectionManager = new ConnectionManager();
        connectionManager.connect(conn);
        table = new TransferBidTable(connectionManager);
        table.createTable();
    }

    @AfterAll
    void cleanUp() throws SQLException {
        conn.createStatement().execute("DROP TABLE " + TransferBidTable.TABLENAME);
        conn.close();
    }

    private static BidItem item(int trackingTypeId, int playerId, long highestBid,
                                int highestBidTeamId, String deadline) {
        return new BidItem(trackingTypeId, 9_000_000_000L + playerId, playerId, "Bid Target",
                highestBid, highestBidTeamId, "Some Team", deadline);
    }

    @Test
    void firstObservationOfATransferInsertsARowWithNoOwnBidYet() {
        final var now = HODateTime.fromHT("2026-09-01 12:00:00");
        table.storeBid(TransferBid.fromObservation(null,
                item(8, 111, 1_500_000L, 9999, "2030-01-01 20:00:00"), MY_TEAM, now));

        final var loaded = table.loadBid(111);
        assertThat(loaded).isNotNull();
        assertThat(loaded.getLatestHighestBid()).isEqualTo(1_500_000L);
        assertThat(loaded.isLatestLeading()).isFalse();
        assertThat(loaded.getOurBid()).isNull();
        assertThat(loaded.getFinalPrice()).isNull();
        assertThat(loaded.getFirstSeenAt()).isEqualTo(now);
    }

    @Test
    void asecondObservationUpdatesTheMarketStateButKeepsOurBid() {
        final var first = HODateTime.fromHT("2026-09-01 12:00:00");
        final var later = HODateTime.fromHT("2026-09-01 18:00:00");
        table.storeBid(TransferBid.fromObservation(null,
                item(2, 222, 1_000_000L, MY_TEAM, "2030-01-01 20:00:00"), MY_TEAM, first));
        final var withOurBid = table.loadBid(222);
        withOurBid.setOurBid(1_200_000L);
        withOurBid.setOurMaxBid(1_500_000L);
        table.storeBid(withOurBid);

        table.storeBid(TransferBid.fromObservation(table.loadBid(222),
                item(8, 222, 1_600_000L, 9999, "2030-01-01 20:00:00"), MY_TEAM, later));

        final var loaded = table.loadBid(222);
        assertThat(loaded.getOurBid()).isEqualTo(1_200_000L);
        assertThat(loaded.getOurMaxBid()).isEqualTo(1_500_000L);
        assertThat(loaded.getLatestHighestBid()).isEqualTo(1_600_000L);
        assertThat(loaded.getLatestTrackingTypeId()).isEqualTo(8);
        assertThat(loaded.getFirstSeenAt()).isEqualTo(first);
        assertThat(loaded.getLastSeenAt()).isEqualTo(later);
    }

    @Test
    void afinishedTransferPastItsDeadlineRecordsTheFinalPriceAndOutcome() {
        final var now = HODateTime.now();
        table.storeBid(TransferBid.fromObservation(null,
                item(2, 333, 2_000_000L, MY_TEAM, "2000-01-01 20:00:00"), MY_TEAM, now));

        table.storeBid(TransferBid.fromObservation(table.loadBid(333),
                item(9, 333, 2_000_000L, MY_TEAM, "2000-01-01 20:00:00"), MY_TEAM, now));

        final var loaded = table.loadBid(333);
        assertThat(loaded.getFinalPrice()).isEqualTo(2_000_000L);
        assertThat(loaded.getOutcome()).isEqualTo("won");
    }

    @Test
    void afinishedTransferWeWereOutbidOnRecordsAlostOutcome() {
        final var now = HODateTime.now();
        table.storeBid(TransferBid.fromObservation(null,
                item(9, 444, 3_000_000L, 9999, "2000-01-01 20:00:00"), MY_TEAM, now));

        final var loaded = table.loadBid(444);
        assertThat(loaded.getFinalPrice()).isEqualTo(3_000_000L);
        assertThat(loaded.getOutcome()).isEqualTo("lost");
    }

    @Test
    void afinishedTransferWithNoBidsIsNotTreatedAsASale() {
        final var now = HODateTime.now();
        table.storeBid(TransferBid.fromObservation(null,
                item(9, 555, 0L, 0, "2000-01-01 20:00:00"), MY_TEAM, now));

        final var loaded = table.loadBid(555);
        assertThat(loaded.getFinalPrice()).isNull();
        assertThat(loaded.getOutcome()).isNullOrEmpty();
    }

    @Test
    void afinishedFinalPriceIsNotOverwrittenByALaterObservation() {
        final var now = HODateTime.now();
        table.storeBid(TransferBid.fromObservation(null,
                item(9, 666, 2_500_000L, MY_TEAM, "2000-01-01 20:00:00"), MY_TEAM, now));

        table.storeBid(TransferBid.fromObservation(table.loadBid(666),
                item(9, 666, 9_999_999L, 9999, "2000-01-01 20:00:00"), MY_TEAM, now));

        assertThat(table.loadBid(666).getFinalPrice()).isEqualTo(2_500_000L);
    }

    @Test
    void loadBidReturnsNullForAnUntrackedPlayer() {
        assertThat(table.loadBid(999999)).isNull();
    }
}
