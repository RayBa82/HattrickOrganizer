package core.db;

import core.constants.player.PlayerSkill;
import core.file.xml.XMLCurrentBidsParser.BidItem;
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
     * A skills-only snapshot from a {@code playerdetails} download: the skills-fill pass runs this
     * for players HO bid on but never saw on the market, so the valuation corpus has their skills
     * even though the market data (asking price, bids) is not part of a player-details response.
     */
    public static TransferListing fromPlayerDetails(Player p, HODateTime observedAt) {
        final var listing = new TransferListing();
        listing.playerId = p.getPlayerId();
        listing.observedAt = observedAt;
        listing.playerName = p.getFullName();
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
        final var wage = p.getWage();
        listing.salary = wage == null || wage.getSwedishKrona() == null
                ? null : wage.getSwedishKrona().longValue();
        return listing;
    }
}
