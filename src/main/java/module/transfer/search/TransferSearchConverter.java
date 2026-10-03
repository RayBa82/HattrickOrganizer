package module.transfer.search;

import core.db.TransferListing;
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
        entry.setInfo(notes(details.injuryLevel(), details.cards()));
        return entry;
    }

    /**
     * The scout entry of a stored market observation, as {@code manage_scout} add pushes it into the
     * transfer scout. A listing that carries no age was snapshotted after its deadline had passed
     * (or came from {@code currentbids}), and keeps the defaults of a fresh {@link ScoutEintrag}.
     * Nationality is not part of a listing snapshot and stays at its default.
     */
    public static ScoutEintrag toScoutEintrag(TransferListing listing) {
        final var entry = new ScoutEintrag();
        entry.setPlayerID(listing.getPlayerId());
        entry.setName(listing.getPlayerName());
        entry.setPrice(new AmountOfMoney(
                Math.max(listing.getAskingPrice(), listing.getHighestBid())));
        if (listing.getDeadline() != null) {
            entry.setDeadline(listing.getDeadline().toDbTimestamp());
        }
        if (listing.getAge() == null) {
            return entry;
        }
        entry.setAlter(listing.getAge());
        entry.setAgeDays(listing.getAgeDays());
        if (listing.getSalary() != null) {
            entry.setbaseWage(new AmountOfMoney(listing.getSalary()));
        }
        entry.setTSI(listing.getTsi());
        entry.setForm(listing.getForm());
        entry.setErfahrung(listing.getExperience());
        entry.setLeadership(listing.getLeadership());
        entry.setSpeciality(listing.getSpecialty());
        entry.setKondition(listing.getStamina());
        entry.setTorwart(listing.getKeeper());
        entry.setSpielaufbau(listing.getPlaymaker());
        entry.setTorschuss(listing.getScorer());
        entry.setPasspiel(listing.getPassing());
        entry.setFluegelspiel(listing.getWinger());
        entry.setVerteidigung(listing.getDefender());
        entry.setStandards(listing.getSetPieces());
        entry.setInfo(notes(listing.getInjuryLevel(), listing.getCards()));
        return entry;
    }

    /**
     * Injury and cards, in the notes column of the scout entry. A healthy player has an injury level
     * of -1.
     */
    private static String notes(Integer injuryLevel, Integer cards) {
        final var notes = new StringBuilder();
        if (injuryLevel != null && injuryLevel > 0) {
            notes.append(TranslationFacility.tr("scout_injury")
                            .replaceAll("%weeks%", String.valueOf(injuryLevel)))
                    .append("\n");
        }
        if (cards != null && cards > 0) {
            notes.append(cards)
                    .append(" ")
                    .append(TranslationFacility.tr("GelbeKarten"))
                    .append("\n");
        }
        return notes.toString();
    }
}
