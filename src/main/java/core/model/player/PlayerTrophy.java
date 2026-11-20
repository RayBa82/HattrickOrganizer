package core.model.player;

import core.db.AbstractTable;
import core.util.HODateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * One trophy of a player (playerdetails 3.3 TrophyList), kept once: a trophy does not change, so
 * a re-download adds only the new ones. CHPP sends the list only when the player's club has Gold
 * supporter.
 */
@Getter
@Setter
public class PlayerTrophy extends AbstractTable.Storable {

    private int playerId;
    /** trophyID, see the CHPP datatypes. */
    private int trophyTypeId;
    private int trophySeason;
    /** The series, cup or tournament id; 0 when the trophy names none. */
    private int leagueLevelUnitId;
    /** The club it was won with; 0 for national team and top scorer trophies. */
    private int teamId;
    private String teamName;
    /** Division level for series titles, the medal (1-3) for national team trophies. */
    private Integer leagueLevel;
    private String leagueLevelUnitName;
    private HODateTime gainedDate;
}
