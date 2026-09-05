package core.db;

import core.util.AmountOfMoney;
import core.util.HODateTime;
import module.transfer.scout.ScoutEintrag;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Vector;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The sale price and sale date added in DB version 1003 are the only nullable columns of this
 * table, so they are the ones worth round-tripping.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ScoutTableTest {

    private static final HODateTime SOLD_AT = HODateTime.fromHT("2026-09-02 20:15:00");

    private Connection conn;
    private ScoutTable table;

    @BeforeAll
    void setUp() throws SQLException {
        conn = DriverManager.getConnection("jdbc:hsqldb:mem:scouttabletestdb", "SA", "");
        final var connectionManager = new ConnectionManager();
        connectionManager.connect(conn);
        table = new ScoutTable(connectionManager);
        table.createTable();
    }

    @AfterAll
    void cleanUp() throws SQLException {
        conn.createStatement().execute("DROP TABLE " + ScoutTable.TABLENAME);
        conn.close();
    }

    private static ScoutEintrag entry(int playerId) {
        final var entry = new ScoutEintrag();
        entry.setPlayerID(playerId);
        entry.setName("Sven Persson");
        entry.setPrice(new AmountOfMoney(1_500_000L));
        entry.setbaseWage(new AmountOfMoney(44_000L));
        entry.setDeadline(SOLD_AT.toDbTimestamp());
        return entry;
    }

    private void save(ScoutEintrag... entries) {
        table.saveScoutList(new Vector<>(java.util.List.of(entries)));
    }

    @Test
    void anUnsoldEntryRoundTripsWithNoSalePrice() {
        save(entry(1001));

        final var loaded = table.getScoutList().stream()
                .filter(e -> e.getPlayerID() == 1001).findFirst().orElseThrow();

        assertThat(loaded.isSold()).isFalse();
        assertThat(loaded.getSalePrice()).isNull();
        assertThat(loaded.getSoldAt()).isNull();
    }

    @Test
    void aSoldEntryRoundTripsThePriceAndDate() {
        final var sold = entry(1002);
        sold.setSalePrice(new AmountOfMoney(1_700_000L));
        sold.setSoldAt(SOLD_AT.toDbTimestamp());
        save(sold);

        final var loaded = table.getScoutList().stream()
                .filter(e -> e.getPlayerID() == 1002).findFirst().orElseThrow();

        assertThat(loaded.isSold()).isTrue();
        assertThat(loaded.getSalePrice().getSwedishKrona().longValue()).isEqualTo(1_700_000L);
        assertThat(loaded.getSoldAt()).isEqualTo(SOLD_AT.toDbTimestamp());
    }

    /** duplicate() feeds the table model and the details panel; it must carry the sale across. */
    @Test
    void duplicateCarriesTheSale() {
        final var sold = entry(1003);
        sold.setSalePrice(new AmountOfMoney(1_700_000L));
        sold.setSoldAt(SOLD_AT.toDbTimestamp());

        final var copy = sold.duplicate();

        assertThat(copy.isSold()).isTrue();
        assertThat(copy.getSalePrice().getSwedishKrona().longValue()).isEqualTo(1_700_000L);
        assertThat(copy.getSoldAt()).isEqualTo(SOLD_AT.toDbTimestamp());
        assertThat(entry(1004).duplicate().isSold()).isFalse();
    }
}
