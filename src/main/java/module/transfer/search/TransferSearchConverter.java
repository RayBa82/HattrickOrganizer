package module.transfer.search;

import core.db.TransferListing;
import core.file.xml.XMLTransferSearchParser.Details;
import core.file.xml.XMLTransferSearchParser.Hit;
import core.model.TranslationFacility;
import core.util.AmountOfMoney;
import module.transfer.scout.ScoutEintrag;
import org.jetbrains.annotations.Nullable;

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
     * The scout entry of one stored market observation, market data and details from the same row.
     * A listing that carries no age was snapshotted after its deadline had passed (or came from
     * {@code currentbids}), and keeps the defaults of a fresh {@link ScoutEintrag}.
     */
    public static ScoutEintrag toScoutEintrag(TransferListing listing) {
        return toScoutEintrag(listing, listing);
    }

    /**
     * The scout entry {@code manage_scout} add builds: the market data from {@code market}, the
     * player's newest observation, and his details from {@code details}, his newest observation
     * that carries any. The two differ once we have bid on him: every {@code get_open_bids} stores a
     * {@code currentbids} row with the bid and the deadline but no age or skills, and that row is
     * usually the newest. Built from it alone, the entry kept a fresh {@link ScoutEintrag}'s
     * defaults - age 17, TSI 1000, every skill 0 - which read as a real, hopeless player.
     * <p>
     * With {@code details} null or skill-less too, those defaults are all there is. Nationality is
     * not part of a listing snapshot and stays at its default.
     */
    public static ScoutEintrag toScoutEintrag(TransferListing market,
                                              @Nullable TransferListing details) {
        final var entry = new ScoutEintrag();
        entry.setPlayerID(market.getPlayerId());
        entry.setName(market.getPlayerName());
        entry.setPrice(new AmountOfMoney(
                Math.max(market.getAskingPrice(), market.getHighestBid())));
        if (market.getDeadline() != null) {
            entry.setDeadline(market.getDeadline().toDbTimestamp());
        }
        if (details != null && applyDetails(entry, details)) {
            entry.setInfo(notes(details.getInjuryLevel(), details.getCards()));
        }
        return entry;
    }

    /**
     * Copy a stored observation's age, wage, TSI, form, experience, leadership, specialty and skills
     * onto a scout entry. Nothing when the row carries no age - a {@code currentbids} row, or a
     * search hit past its deadline - so a skill-less row never overwrites real values with
     * defaults. The notes column is left alone: once the entry exists it holds the user's text.
     *
     * @return whether the row carried details and they were copied
     */
    public static boolean applyDetails(ScoutEintrag entry, TransferListing listing) {
        if (listing.getAge() == null) {
            return false;
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
        return true;
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
