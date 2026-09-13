package core.db;

import core.file.xml.XMLCurrentBidsParser.BidItem;
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
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TransferListingTableTest {

    private static final HODateTime DEADLINE = HODateTime.fromHT("2026-09-02 20:15:00");

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
                DEADLINE, 1_650_000L, "Bidding Boys",
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

    /** A currentbids observation has the market data but no skills - it must still round-trip. */
    @Test
    void snapshotsABidItemWithNoSkillsAsAnUnresolvedListing() {
        final var observedAt = HODateTime.fromHT("2026-08-30 15:00:00");
        final var item = new BidItem(8, 777123, 123123123, "Bid Target", 2_400_000L,
                4242, "Rival FC", "2026-09-03 20:15:00");

        table.storeListing(TransferListing.fromBidItem(item, observedAt));

        final var loaded = table.loadUnresolved(50).stream()
                .filter(l -> l.getPlayerId() == 123123123).findFirst().orElseThrow();
        assertThat(loaded.getHighestBid()).isEqualTo(2_400_000L);
        assertThat(loaded.getBidderName()).isEqualTo("Rival FC");
        assertThat(loaded.getPlaymaker()).isNull();
        assertThat(loaded.getTsi()).isNull();
        assertThat(loaded.getSalePrice()).isNull();
    }

    private static core.file.xml.XMLPlayersParser.MarketPlayer market(
            int playerId, boolean isAbroad, long salarySek) {
        final var p = new core.model.player.Player();
        p.setPlayerId(playerId);
        p.setAge(27);
        p.setWage(new core.util.AmountOfMoney(salarySek));
        p.setValue4Skill(core.constants.player.PlayerSkill.PLAYMAKING, 13);
        p.setValue4Skill(core.constants.player.PlayerSkill.SCORING, 9);
        // Crossed getters: gentleness is Agreeability, honesty is Honesty. Three distinct values.
        p.setGentleness(3);
        p.setAggressivity(2);
        p.setHonesty(4);
        return new core.file.xml.XMLPlayersParser.MarketPlayer(
                p, true, 555444, isAbroad, 2_850_000L,
                HODateTime.fromHT("2026-09-05 20:08:31"), 100_000L);
    }

    /** A listed player's details are a complete market snapshot, not a skills-only one. */
    @Test
    void snapshotsPlayerDetailsAsASkillBearingListing() {
        final var observedAt = HODateTime.fromHT("2026-08-30 16:00:00");

        table.storeListing(TransferListing.fromPlayerDetails(market(88991122, false, 44_000L),
                observedAt));

        final var loaded = table.loadUnresolved(50).stream()
                .filter(l -> l.getPlayerId() == 88991122).findFirst().orElseThrow();
        assertThat(loaded.getAge()).isEqualTo(27);
        assertThat(loaded.getPlaymaker()).isEqualTo(13);
        assertThat(loaded.getScorer()).isEqualTo(9);
        assertThat(loaded.getSalePrice()).isNull();
        assertThat(loaded.getAskingPrice()).isEqualTo(2_850_000L);
        assertThat(loaded.getHighestBid()).isEqualTo(100_000L);
        assertThat(loaded.getDeadline()).isEqualTo(HODateTime.fromHT("2026-09-05 20:08:31"));
        assertThat(loaded.getAgreeability()).isEqualTo(3);
        assertThat(loaded.getAggressiveness()).isEqualTo(2);
        assertThat(loaded.getHonesty()).isEqualTo(4);
    }

    /**
     * A transfersearch hit carries no character at all, so the three columns stay null rather than
     * defaulting to 0 - which is a real level ("nasty fellow", "tranquil", "infamous").
     */
    @Test
    void aSearchHitLeavesTheCharacterColumnsNull() {
        table.storeListing(TransferListing.fromHit(hit(111222444, details()), DEADLINE));

        final var loaded = table.loadUnresolved(50).stream()
                .filter(l -> l.getPlayerId() == 111222444).findFirst().orElseThrow();
        assertThat(loaded.getAgreeability()).isNull();
        assertThat(loaded.getAggressiveness()).isNull();
        assertThat(loaded.getHonesty()).isNull();
    }

    /**
     * CHPP's Salary carries the 20% surcharge for a player abroad; the search path's Salary does
     * not. Mixing the two in one column makes wage an unusable comparable - and it reaches the
     * scout table's baseWage column, which the user reads.
     */
    @Test
    void anAbroadPlayersSalaryIsStoredAsTheBaseWage() {
        final var observedAt = HODateTime.fromHT("2026-08-30 16:00:00");

        table.storeListing(TransferListing.fromPlayerDetails(market(88991123, true, 57_840L),
                observedAt));
        table.storeListing(TransferListing.fromPlayerDetails(market(88991124, false, 57_840L),
                observedAt));

        assertThat(table.loadUnresolved(50).stream()
                .filter(l -> l.getPlayerId() == 88991123).findFirst().orElseThrow().getSalary())
                .isEqualTo(48_200L);
        assertThat(table.loadUnresolved(50).stream()
                .filter(l -> l.getPlayerId() == 88991124).findFirst().orElseThrow().getSalary())
                .isEqualTo(57_840L);
    }

    /** The §2 invariant: an unlisted response has nothing the corpus wants, so it cannot build one. */
    @Test
    void refusesToSnapshotAnUnlistedPlayer() {
        final var p = new core.model.player.Player();
        p.setPlayerId(88991125);
        final var unlisted = new core.file.xml.XMLPlayersParser.MarketPlayer(
                p, false, 555444, false, 0L, null, 0L);

        org.assertj.core.api.Assertions.assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> TransferListing.fromPlayerDetails(
                        unlisted, HODateTime.fromHT("2026-08-30 16:00:00")));
    }


    @Test
    void hasSkillDataIsTrueOnlyOnceASkillBearingSnapshotExists() {
        final var observedAt = HODateTime.fromHT("2026-08-30 17:00:00");
        final var item = new BidItem(2, 601, 55667788, "Skilless Sam", 1_000_000L, 1, "T",
                "2026-09-04 20:00:00");
        table.storeListing(TransferListing.fromBidItem(item, observedAt));
        assertThat(table.hasSkillData(55667788)).isFalse();

        table.storeListing(TransferListing.fromHit(hit(55667788, details()),
                HODateTime.fromHT("2026-08-30 18:00:00")));
        assertThat(table.hasSkillData(55667788)).isTrue();
    }

    @Test
    void storesASalePriceAndDropsTheRowOutOfTheUnresolvedList() {
        final var observedAt = HODateTime.fromHT("2026-08-30 14:00:00");
        table.storeListing(TransferListing.fromHit(hit(777888999, details()), observedAt));

        table.storeSalePrice(777888999, DEADLINE, 1_700_000L, Duration.ofDays(2));

        assertThat(table.loadUnresolved(10)).noneMatch(l -> l.getPlayerId() == 777888999);
    }

    /** One lookup resolves every observation of the listing that sold, not just one row. */
    @Test
    void storesASalePriceOnEveryObservationOfTheSameListing() {
        table.storeListing(TransferListing.fromHit(hit(777888999, details()),
                HODateTime.fromHT("2026-08-30 14:00:00")));
        table.storeListing(TransferListing.fromHit(hit(777888999, details()),
                HODateTime.fromHT("2026-08-31 09:00:00")));

        table.storeSalePrice(777888999, DEADLINE, 1_700_000L, Duration.ofDays(2));

        assertThat(table.loadUnresolved(10)).noneMatch(l -> l.getPlayerId() == 777888999);
    }

    /** A separate, earlier spell on the market is a different listing and must stay unresolved. */
    @Test
    void leavesAListingOutsideTheSaleWindowUnresolved() {
        table.storeListing(TransferListing.fromHit(hit(777888999, details()),
                HODateTime.fromHT("2026-08-30 14:00:00")));

        final var muchLater = HODateTime.fromHT("2026-11-30 20:00:00");
        table.storeSalePrice(777888999, muchLater, 1_700_000L, Duration.ofDays(2));

        assertThat(table.loadUnresolved(10)).anyMatch(l -> l.getPlayerId() == 777888999);
    }

    /**
     * The property that decides whether the corpus improves with age: a listing that never produced
     * a sale must drop out of the queue instead of being retried on every download forever.
     */
    @Test
    void aLookedAtListingWithNoSaleStopsComingBackAsUnresolved() {
        final var afterDeadline = HODateTime.fromHT("2026-09-03 10:00:00");
        table.storeListing(TransferListing.fromHit(hit(424242, details()),
                HODateTime.fromHT("2026-08-30 14:00:00")));
        assertThat(table.loadUnresolved(50)).anyMatch(l -> l.getPlayerId() == 424242);

        table.markResolveAttempted(424242, afterDeadline);

        assertThat(table.loadUnresolved(50)).noneMatch(l -> l.getPlayerId() == 424242);
        // Still unsold - "looked at" is not "sold".
        assertThat(table.loadComparables(23, 25, "PLAYMAKER", 11, 13, "DEFENDER", 5, 7, null,
                HODateTime.fromHT("2026-08-01 00:00:00")))
                .filteredOn(l -> l.getPlayerId() == 424242)
                .allMatch(l -> l.getSalePrice() == null);
    }

    /** A listing whose deadline has not passed is not "looked at" - it has not had its chance. */
    @Test
    void doesNotMarkAListingThatHasNotExpiredYet() {
        table.storeListing(TransferListing.fromHit(hit(434343, details()),
                HODateTime.fromHT("2026-08-30 14:00:00")));

        table.markResolveAttempted(434343, HODateTime.fromHT("2026-09-01 10:00:00"));

        assertThat(table.loadUnresolved(50)).anyMatch(l -> l.getPlayerId() == 434343);
    }

    @Test
    void loadsComparablesInsideTheAgeSkillAndRecencyWindow() {
        final var observedAt = HODateTime.fromHT("2026-08-30 14:00:00");
        table.storeListing(TransferListing.fromHit(hit(1001, details()), observedAt));

        final var d = details();       // age 24, playmaker 12, defender 6, specialty 2
        assertThat(table.loadComparables(23, 25, "PLAYMAKER", 11, 13, "DEFENDER", 5, 7,
                d.specialty(), HODateTime.fromHT("2026-08-01 00:00:00")))
                .anyMatch(l -> l.getPlayerId() == 1001);

        // Outside the age window
        assertThat(table.loadComparables(30, 32, "PLAYMAKER", 11, 13, "DEFENDER", 5, 7,
                d.specialty(), HODateTime.fromHT("2026-08-01 00:00:00")))
                .noneMatch(l -> l.getPlayerId() == 1001);

        // Observed before the recency bound
        assertThat(table.loadComparables(23, 25, "PLAYMAKER", 11, 13, "DEFENDER", 5, 7,
                d.specialty(), HODateTime.fromHT("2026-09-01 00:00:00")))
                .noneMatch(l -> l.getPlayerId() == 1001);
    }

    /**
     * Every column TransferTools maps a market-search skill onto has to exist here, or the query
     * would be malformed SQL. The name reaches the statement by concatenation, so it is validated.
     */
    @Test
    void acceptsEverySkillColumnAndRejectsAnythingElse() {
        final var since = HODateTime.fromHT("2026-08-01 00:00:00");
        for (final var skill : new String[]{"KEEPER", "DEFENDER", "PLAYMAKER", "PASSING",
                "WINGER", "SCORER", "SETPIECES", "STAMINA", "LEADERSHIP", "EXPERIENCE"}) {
            assertThatCode(() -> table.loadComparables(17, 40, skill, 0, 20, skill, 0, 20,
                    null, since)).doesNotThrowAnyException();
        }
        assertThatThrownBy(() -> table.loadComparables(17, 40, "PLAYMAKER); DROP TABLE X --", 0, 20,
                "DEFENDER", 0, 20, null, since))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void loadPlayerIdsMissingCharacterFindsARowWithNullCharacter() {
        table.storeListing(TransferListing.fromHit(hit(900111222, details()), DEADLINE));

        assertThat(table.loadPlayerIdsMissingCharacter(1000)).contains(900111222);
    }

    /** One playerdetails lookup fills character on every stored observation of that player. */
    @Test
    void storeCharacterFillsInEveryMissingObservationOfThatPlayer() {
        table.storeListing(TransferListing.fromHit(hit(900333444, details()),
                HODateTime.fromHT("2026-08-30 14:00:00")));
        table.storeListing(TransferListing.fromHit(hit(900333444, details()),
                HODateTime.fromHT("2026-08-31 09:00:00")));

        table.storeCharacter(900333444, 1, 5, 0);

        assertThat(table.loadPlayerIdsMissingCharacter(1000)).doesNotContain(900333444);
        final var rows = table.loadUnresolved(1000).stream()
                .filter(l -> l.getPlayerId() == 900333444).toList();
        assertThat(rows).hasSize(2);
        assertThat(rows).allSatisfy(l -> {
            assertThat(l.getAgreeability()).isEqualTo(1);
            assertThat(l.getAggressiveness()).isEqualTo(5);
            assertThat(l.getHonesty()).isEqualTo(0);
        });
    }
}
