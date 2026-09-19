package core.db;

import core.constants.player.PlayerSkill;
import core.model.player.Player;
import core.util.HODateTime;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pure unit tests for {@link TransferListing}'s static factories - no DB fixture, same style as
 * {@code TransferToolsTest}'s {@code listingFromProfile_*} tests.
 */
class TransferListingTest {

    private static Player player() {
        final var p = new Player();
        p.setPlayerId(424242);
        p.setAge(24);
        p.setAgeDays(40);
        p.setTsi(180_000);
        p.setSpecialty(2);
        p.setValue4Skill(PlayerSkill.PLAYMAKING, 14);
        p.setValue4Skill(PlayerSkill.PASSING, 8);
        // Crossed getters: gentleness is Agreeability, honesty is Honesty. Three distinct values.
        p.setGentleness(3);
        p.setAggressivity(2);
        p.setHonesty(4);
        return p;
    }

    /**
     * The motivating case: a player sold and gone from the squad, whom HO never watched as a live
     * listing - his last downloaded SPIELER row is the only source left for specialty/TSI/skills/
     * character, and this is what seeds the corpus with it instead of leaving it unrecoverable.
     */
    @Test
    void fromLocalSale_buildsARowFromTheLastKnownPlayerSnapshot() {
        final var saleDate = HODateTime.fromHT("2026-09-01 12:00:00");

        final var listing = TransferListing.fromLocalSale(player(), saleDate, 1_250_000L);

        assertThat(listing.getPlayerId()).isEqualTo(424242);
        assertThat(listing.getObservedAt()).isEqualTo(saleDate);
        assertThat(listing.getAge()).isEqualTo(24);
        assertThat(listing.getAgeDays()).isEqualTo(40);
        assertThat(listing.getTsi()).isEqualTo(180_000);
        assertThat(listing.getSpecialty()).isEqualTo(2);
        assertThat(listing.getPlaymaker()).isEqualTo(14);
        assertThat(listing.getPassing()).isEqualTo(8);
        assertThat(listing.getAgreeability()).isEqualTo(3);
        assertThat(listing.getAggressiveness()).isEqualTo(2);
        assertThat(listing.getHonesty()).isEqualTo(4);
        assertThat(listing.getSalePrice()).isEqualTo(1_250_000L);
    }

    /**
     * He was never actually listed on the market, so none of the market-observation fields
     * {@link TransferListing#fromHit}/{@link TransferListing#fromPlayerDetails} populate should be
     * fabricated here - null/zero, not a guessed value.
     */
    @Test
    void fromLocalSale_leavesMarketFieldsUnset() {
        final var listing = TransferListing.fromLocalSale(
                player(), HODateTime.fromHT("2026-09-01 12:00:00"), 1_250_000L);

        assertThat(listing.getDeadline()).isNull();
        assertThat(listing.getAskingPrice()).isZero();
        assertThat(listing.getHighestBid()).isZero();
        assertThat(listing.getBidderName()).isNull();
    }
}
