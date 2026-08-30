package core.db;

import core.file.xml.XMLTransferSearchParser.Hit;
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
}
