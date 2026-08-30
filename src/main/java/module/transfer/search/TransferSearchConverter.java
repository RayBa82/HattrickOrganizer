package module.transfer.search;

import core.file.xml.XMLTransferSearchParser.Details;
import core.file.xml.XMLTransferSearchParser.Hit;
import core.model.TranslationFacility;
import core.util.AmountOfMoney;
import module.transfer.scout.ScoutEintrag;

/**
 * Maps one hit of the CHPP <code>transfersearch</code> file to the scout entry that the transfer
 * scout table displays.
 */
public final class TransferSearchConverter {

    private TransferSearchConverter() {
    }

    /**
     * The scout entry of one search hit. Everything but the market data is missing once the deadline
     * has passed, the entry then keeps the defaults of a fresh {@link ScoutEintrag}.
     */
    public static ScoutEintrag toScoutEintrag(Hit hit) {
        final var entry = new ScoutEintrag();
        entry.setPlayerID(hit.playerId());
        entry.setName(hit.playerName());
        entry.setNationality(hit.nativeCountryId());
        entry.setPrice(new AmountOfMoney(Math.max(hit.askingPrice(), hit.highestBid())));
        if (hit.deadline() != null) {
            entry.setDeadline(hit.deadline().toDbTimestamp());
        }

        final var details = hit.details();
        if (details == null) {
            return entry;
        }
        entry.setAlter(details.age());
        entry.setAgeDays(details.ageDays());
        entry.setbaseWage(new AmountOfMoney(details.salary()));
        entry.setTSI(details.tsi());
        entry.setForm(details.form());
        entry.setErfahrung(details.experience());
        entry.setLeadership(details.leadership());
        entry.setSpeciality(details.specialty());
        entry.setKondition(details.stamina());
        entry.setTorwart(details.keeper());
        entry.setSpielaufbau(details.playmaker());
        entry.setTorschuss(details.scorer());
        entry.setPasspiel(details.passing());
        entry.setFluegelspiel(details.winger());
        entry.setVerteidigung(details.defender());
        entry.setStandards(details.setPieces());
        entry.setInfo(notes(details));
        return entry;
    }

    /**
     * Injury and cards, in the notes column of the scout entry. A healthy player has an injury level
     * of -1.
     */
    private static String notes(Details details) {
        final var notes = new StringBuilder();
        if (details.injuryLevel() > 0) {
            notes.append(TranslationFacility.tr("scout_injury")
                            .replaceAll("%weeks%", String.valueOf(details.injuryLevel())))
                    .append("\n");
        }
        if (details.cards() > 0) {
            notes.append(details.cards())
                    .append(" ")
                    .append(TranslationFacility.tr("GelbeKarten"))
                    .append("\n");
        }
        return notes.toString();
    }
}
