package module.transfer.search;

import core.db.TransferListing;
import core.file.xml.XMLTransferSearchParser.Details;
import core.file.xml.XMLTransferSearchParser.Hit;
import core.model.TranslationFacility;
import core.model.Translator;
import core.util.HODateTime;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TransferSearchConverterTest {

    private static final HODateTime DEADLINE = HODateTime.fromHT("2026-09-02 20:15:00");

    /**
     * The notes are built from translated strings, and tests run against the NoTranslation bundle,
     * which is empty: every key would come back as <code>!key!</code> and the <code>%weeks%</code>
     * placeholder of the injury message would never be replaced.
     */
    @BeforeAll
    static void useEnglish() {
        TranslationFacility.setLanguage(Translator.LANGUAGE_DEFAULT);
    }

    private static Details details() {
        return new Details(24, 77, 44000L, 29000, 6, 4, 3, 2, 0, -1,
                8, 1, 12, 5, 9, 7, 6, 10);
    }

    private static Hit hit(Details details) {
        return new Hit(111222333, "Sven Persson", 3, 1500000L, DEADLINE, 1650000L,
                "Bidding Boys", 555444, "Selling Sam", 2, details);
    }

    @Test
    void toScoutEintrag_copiesTheMarketData() {
        final var entry = TransferSearchConverter.toScoutEintrag(hit(details()));

        assertThat(entry.getPlayerID()).isEqualTo(111222333);
        assertThat(entry.getName()).isEqualTo("Sven Persson");
        assertThat(entry.getNationality()).isEqualTo(3);
        assertThat(entry.getDeadline()).isEqualTo(DEADLINE.toDbTimestamp());
    }

    @Test
    void toScoutEintrag_copiesTheSkills() {
        final var entry = TransferSearchConverter.toScoutEintrag(hit(details()));

        assertThat(entry.getAlter()).isEqualTo(24);
        assertThat(entry.getAgeDays()).isEqualTo(77);
        assertThat(entry.getTSI()).isEqualTo(29000);
        assertThat(entry.getForm()).isEqualTo(6);
        assertThat(entry.getErfahrung()).isEqualTo(4);
        assertThat(entry.getLeadership()).isEqualTo(3);
        assertThat(entry.getSpeciality()).isEqualTo(2);
        assertThat(entry.getKondition()).isEqualTo(8);
        assertThat(entry.getTorwart()).isEqualTo(1);
        assertThat(entry.getSpielaufbau()).isEqualTo(12);
        assertThat(entry.getTorschuss()).isEqualTo(5);
        assertThat(entry.getPasspiel()).isEqualTo(9);
        assertThat(entry.getFluegelspiel()).isEqualTo(7);
        assertThat(entry.getVerteidigung()).isEqualTo(6);
        assertThat(entry.getStandards()).isEqualTo(10);
    }

    @Test
    void toScoutEintrag_leavesTheNotesEmptyForAHealthyPlayerWithoutCards() {
        assertThat(TransferSearchConverter.toScoutEintrag(hit(details())).getInfo()).isEmpty();
    }

    @Test
    void toScoutEintrag_notesInjuryAndCards() {
        final var injured = new Details(24, 77, 44000L, 29000, 6, 4, 3, 2, 2, 3,
                8, 1, 12, 5, 9, 7, 6, 10);

        final var info = TransferSearchConverter.toScoutEintrag(hit(injured)).getInfo();

        assertThat(info).contains("3").contains("2");
    }

    @Test
    void toScoutEintrag_survivesAnExpiredTransferWithoutDetails() {
        final var entry = TransferSearchConverter.toScoutEintrag(hit(null));

        assertThat(entry.getPlayerID()).isEqualTo(111222333);
        assertThat(entry.getAlter()).isEqualTo(17);
    }

    // --- from a stored listing, the path manage_scout add takes -----------------------------------

    @Test
    void toScoutEintrag_ofAListing_copiesTheMarketDataAndSkills() {
        final var entry = TransferSearchConverter.toScoutEintrag(
                TransferListing.fromHit(hit(details()), DEADLINE));

        assertThat(entry.getPlayerID()).isEqualTo(111222333);
        assertThat(entry.getName()).isEqualTo("Sven Persson");
        assertThat(entry.getDeadline()).isEqualTo(DEADLINE.toDbTimestamp());
        assertThat(entry.getAlter()).isEqualTo(24);
        assertThat(entry.getAgeDays()).isEqualTo(77);
        assertThat(entry.getTSI()).isEqualTo(29000);
        assertThat(entry.getKondition()).isEqualTo(8);
        assertThat(entry.getSpielaufbau()).isEqualTo(12);
        assertThat(entry.getStandards()).isEqualTo(10);
        // The highest bid is above the asking price, so it is what the scout entry shows.
        assertThat(entry.getPrice().getSwedishKrona().longValue()).isEqualTo(1650000L);
    }

    /**
     * A listing snapshotted from {@code currentbids} carries market data only. It must not blow up
     * on the boxed skill fields, and must keep the defaults of a fresh entry.
     */
    @Test
    void toScoutEintrag_ofASkilllessListing_keepsTheDefaults() {
        final var listing = new TransferListing();
        listing.setPlayerId(111222333);
        listing.setPlayerName("Sven Persson");

        final var entry = TransferSearchConverter.toScoutEintrag(listing);

        assertThat(entry.getPlayerID()).isEqualTo(111222333);
        assertThat(entry.getAlter()).isEqualTo(17);
        assertThat(entry.getInfo()).isEmpty();
    }
}
