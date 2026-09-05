package core.db;

import core.constants.player.PlayerSkill;
import core.file.xml.XMLCurrentBidsParser.BidItem;
import core.file.xml.XMLPlayersParser;
import core.file.xml.XMLTransferSearchParser.Hit;
import core.model.player.Player;
import core.util.HODateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * One observation of one player on the transfer market.
 * <p>
 * The skill and detail fields are boxed because Hattrick drops the whole Details block once a
 * deadline has passed - an expired listing is stored with the market data only.
 */
@Getter
@Setter
public class TransferListing extends AbstractTable.Storable {

    private int playerId;
    private HODateTime observedAt;
    private String playerName;
    private HODateTime deadline;
    private long askingPrice;
    private long highestBid;
    private String bidderName;
    private Integer tsi;
    private Integer age;
    private Integer ageDays;
    private Long salary;
    private Integer specialty;
    private Integer form;
    private Integer experience;
    private Integer leadership;
    private Integer cards;
    private Integer injuryLevel;
    private Integer stamina;
    private Integer keeper;
    private Integer playmaker;
    private Integer scorer;
    private Integer passing;
    private Integer winger;
    private Integer defender;
    private Integer setPieces;
    /**
     * Character, 0-5 each. Only {@link #fromPlayerDetails} can fill these in: {@code transfersearch}
     * and {@code currentbids} carry no character at all, so rows from those two paths leave them
     * null. Null means "this observation did not see it", never a low score - 0 is a real level.
     */
    private Integer agreeability;
    private Integer aggressiveness;
    private Integer honesty;
    /** What the player actually sold for, filled in later by the resolve pass. Null until then. */
    private Long salePrice;
    private HODateTime resolvedAt;

    public TransferListing() {
    }

    /** The snapshot of one search hit, as observed now. */
    public static TransferListing fromHit(Hit hit, HODateTime observedAt) {
        final var listing = new TransferListing();
        listing.playerId = hit.playerId();
        listing.observedAt = observedAt;
        listing.playerName = hit.playerName();
        listing.deadline = hit.deadline();
        listing.askingPrice = hit.askingPrice();
        listing.highestBid = hit.highestBid();
        listing.bidderName = hit.bidderTeamName();

        final var details = hit.details();
        if (details == null) {
            return listing;
        }
        listing.age = details.age();
        listing.ageDays = details.ageDays();
        listing.salary = details.salary();
        listing.tsi = details.tsi();
        listing.form = details.form();
        listing.experience = details.experience();
        listing.leadership = details.leadership();
        listing.specialty = details.specialty();
        listing.cards = details.cards();
        listing.injuryLevel = details.injuryLevel();
        listing.stamina = details.stamina();
        listing.keeper = details.keeper();
        listing.playmaker = details.playmaker();
        listing.scorer = details.scorer();
        listing.passing = details.passing();
        listing.winger = details.winger();
        listing.defender = details.defender();
        listing.setPieces = details.setPieces();
        return listing;
    }

    /**
     * A snapshot built from a {@code currentbids} item: the market data is there, the skills are
     * not, so it lands as an unresolved listing the resolve pass fills the sale price in for.
     */
    public static TransferListing fromBidItem(BidItem item, HODateTime observedAt) {
        final var listing = new TransferListing();
        listing.playerId = item.playerId();
        listing.observedAt = observedAt;
        listing.playerName = item.playerName();
        listing.deadline = HODateTime.fromHT(item.deadline());
        listing.highestBid = item.highestBidAmount();
        listing.bidderName = item.highestBidTeamName();
        return listing;
    }

    /**
     * The full market snapshot of a {@code playerdetails} download of a listed player: skills,
     * details <em>and</em> the market data, so a row from this path carries everything one built by
     * {@link #fromHit} does bar {@code bidderName} - {@code playerdetails} names the bidding team in
     * a {@code <BidderTeam>} element this path does not read, and that column stays null.
     * <p>
     * Only for a listed player. Hattrick prunes an unlisted player's skills down to stamina and
     * empties his TransferDetails block, so a row built from one of those would carry six or seven
     * fabricated skills into {@code loadComparables} and drag every valuation using them downward.
     * The correct number of rows to write for an unlisted response is zero.
     *
     * @throws IllegalArgumentException if {@code m} is not transfer-listed
     */
    public static TransferListing fromPlayerDetails(XMLPlayersParser.MarketPlayer m,
                                                    HODateTime observedAt) {
        if (!m.transferListed()) {
            throw new IllegalArgumentException(
                    "playerdetails of an unlisted player carries no corpus-worthy data: "
                            + m.player().getPlayerId());
        }
        final var p = m.player();
        final var listing = new TransferListing();
        listing.playerId = p.getPlayerId();
        listing.observedAt = observedAt;
        listing.playerName = p.getFullName();
        listing.deadline = m.deadline();
        listing.askingPrice = m.askingPrice();
        listing.highestBid = m.highestBid();
        listing.age = p.getAge();
        listing.ageDays = p.getAgeDays();
        listing.tsi = p.getTsi();
        listing.specialty = p.getSpecialty();
        listing.form = p.getValue4Skill(PlayerSkill.FORM);
        listing.experience = p.getValue4Skill(PlayerSkill.EXPERIENCE);
        listing.leadership = p.getValue4Skill(PlayerSkill.LEADERSHIP);
        listing.cards = p.getTotalCards();
        listing.injuryLevel = p.getInjuryWeeks();
        listing.stamina = p.getValue4Skill(PlayerSkill.STAMINA);
        listing.keeper = p.getValue4Skill(PlayerSkill.KEEPER);
        listing.playmaker = p.getValue4Skill(PlayerSkill.PLAYMAKING);
        listing.scorer = p.getValue4Skill(PlayerSkill.SCORING);
        listing.passing = p.getValue4Skill(PlayerSkill.PASSING);
        listing.winger = p.getValue4Skill(PlayerSkill.WINGER);
        listing.defender = p.getValue4Skill(PlayerSkill.DEFENDING);
        listing.setPieces = p.getValue4Skill(PlayerSkill.SETPIECES);
        // Legacy German field names, crossed against Hattrick's: getGentleness() is Agreeability
        // and getHonesty() is Honesty. Do not "fix" these.
        listing.agreeability = p.getGentleness();
        listing.aggressiveness = p.getAggressivity();
        listing.honesty = p.getHonesty();
        listing.salary = baseWage(p, m.isAbroad());
        return listing;
    }

    /**
     * CHPP's {@code Salary} includes the 20% surcharge a club pays for a player abroad;
     * {@code transfersearch}'s does not. {@link #fromHit} stores the base wage, so this one has to
     * as well or the column mixes two quantities and stops being a comparable.
     */
    private static Long baseWage(Player p, boolean isAbroad) {
        final var wage = p.getWage();
        if (wage == null || wage.getSwedishKrona() == null) {
            return null;
        }
        final var sek = wage.getSwedishKrona().longValue();
        return isAbroad ? Math.round(sek / 1.2) : sek;
    }
}
