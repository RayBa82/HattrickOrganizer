package core.db;

import core.file.xml.XMLTransferSearchParser.Details;
import core.file.xml.XMLTransferSearchParser.Hit;
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
class TransferListingTableTest {

    private Connection conn;
    private TransferListingTable table;

    @BeforeAll
    void setUp() throws SQLException {
        conn = DriverManager.getConnection("jdbc:hsqldb:mem:transferlistingtestdb", "SA", "");
        final var connectionManager = new ConnectionManager();
        connectionManager.connect(conn);
        table = new TransferListingTable(connectionManager);
        table.createTable();
    }

    @AfterAll
    void cleanUp() throws SQLException {
        conn.createStatement().execute("DROP TABLE " + TransferListingTable.TABLENAME);
        conn.close();
    }

    private static Hit hit(int playerId, Details details) {
        return new Hit(playerId, "Sven Persson", 3, 1_500_000L,
                HODateTime.fromHT("2026-09-02 20:15:00"), 1_650_000L, "Bidding Boys",
                555444, "Selling Sam", 2, details);
    }

    private static Details details() {
        return new Details(24, 77, 44_000L, 29_000, 6, 4, 3, 2, 1, -1,
                8, 1, 12, 5, 9, 7, 6, 10);
    }

    @Test
    void roundTripsAHitWithDetails() {
        final var observedAt = HODateTime.fromHT("2026-08-30 12:00:00");

        table.storeListing(TransferListing.fromHit(hit(111222333, details()), observedAt));

        final var loaded = table.loadUnresolved(10).stream()
                .filter(l -> l.getPlayerId() == 111222333).findFirst().orElseThrow();
        assertThat(loaded.getAskingPrice()).isEqualTo(1_500_000L);
        assertThat(loaded.getHighestBid()).isEqualTo(1_650_000L);
        assertThat(loaded.getTsi()).isEqualTo(29_000);
        assertThat(loaded.getPlaymaker()).isEqualTo(12);
        assertThat(loaded.getSalePrice()).isNull();
    }

    /** Details are null once a deadline has passed - every consumer has to survive that. */
    @Test
    void roundTripsAHitWithoutDetails() {
        final var observedAt = HODateTime.fromHT("2026-08-30 13:00:00");

        table.storeListing(TransferListing.fromHit(hit(444555666, null), observedAt));

        final var loaded = table.loadUnresolved(10).stream()
                .filter(l -> l.getPlayerId() == 444555666).findFirst().orElseThrow();
        assertThat(loaded.getTsi()).isNull();
        assertThat(loaded.getPlaymaker()).isNull();
        assertThat(loaded.getAskingPrice()).isEqualTo(1_500_000L);
        assertThat(loaded.getSalePrice()).isNull();
    }

    @Test
    void storesASalePriceAndDropsTheRowOutOfTheUnresolvedList() {
        final var observedAt = HODateTime.fromHT("2026-08-30 14:00:00");
        table.storeListing(TransferListing.fromHit(hit(777888999, details()), observedAt));

        table.storeSalePrice(777888999, observedAt, 1_700_000L);

        assertThat(table.loadUnresolved(10)).noneMatch(l -> l.getPlayerId() == 777888999);
    }
}
