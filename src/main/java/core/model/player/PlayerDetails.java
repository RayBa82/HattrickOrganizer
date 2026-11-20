package core.model.player;

import core.db.AbstractTable;
import core.util.HODateTime;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * One {@code playerdetails} 3.3 download, every element of the {@code view} output kept as CHPP
 * sent it: ids and levels raw, money in SEK, absent elements null. Stored one row per download
 * (PLAYERDETAILS) so nothing the file carries is lost; what HO does with it is decided by the
 * readers, not here. The trophies go to their own table, see {@link PlayerTrophy}.
 * <p>
 * What is null and when, per the CHPP docs: the seven outfield skills unless the skills are
 * visible (own player, listed player, national team coach - stamina is always there); the
 * TransferDetails values unless the player is listed; {@code transferCompareAverage} unless the
 * skills are visible and Hattrick has an estimate; the family relation unless he has one;
 * {@code playerLanguage} unless the user is a Supporter; the LastMatch values unless
 * {@code includeMatchInfo} was set; owner-only fields (category, notes) for somebody else's player.
 */
@Getter
@Setter
public class PlayerDetails extends AbstractTable.Storable {

    private int playerId;
    /** When HO downloaded it: the second half of the key. */
    private HODateTime fetchedAt;

    private String userSupporterTier;

    private String firstName;
    private String nickName;
    private String lastName;
    private Integer playerNumber;
    private Integer playerCategoryId;
    private String ownerNotes;
    private Integer age;
    private Integer ageDays;
    private HODateTime nextBirthDay;
    private Integer genderId;
    private HODateTime arrivalDate;
    private Integer playerForm;
    private Integer cards;
    private Integer injuryLevel;
    private String statement;
    private String playerLanguage;
    private Integer playerLanguageId;

    /** TrainerData, only for a player who is a trainer. */
    private Integer trainerType;
    private Integer trainerSkillLevel;

    private Integer agreeability;
    private Integer aggressiveness;
    private Integer honesty;
    private Integer experience;
    private Integer loyalty;
    private Boolean motherClubBonus;
    private Integer motherClubTeamId;
    private String motherClubTeamName;
    private Integer leadership;
    private Integer specialty;
    private Integer nativeCountryId;
    private Integer nativeLeagueId;
    private String nativeLeagueName;
    private Integer tsi;

    private Integer owningTeamId;
    private String owningTeamName;
    private Integer owningTeamLeagueId;
    /** SEK, with the abroad surcharge when {@code isAbroad}. */
    private Long salary;
    private Boolean isAbroad;

    private Integer staminaSkill;
    private Integer keeperSkill;
    private Integer playmakerSkill;
    private Integer scorerSkill;
    private Integer passingSkill;
    private Integer wingerSkill;
    private Integer defenderSkill;
    private Integer setPiecesSkill;

    private Integer caps;
    private Integer capsU20;
    private Integer careerGoals;
    private Integer careerHattricks;
    private Integer leagueGoals;
    private Integer cupGoals;
    /** The {@code Available} attribute of CupGoals. */
    private Boolean cupGoalsAvailable;
    private Integer friendliesGoals;
    private Integer matchesCurrentTeam;
    private Integer goalsCurrentTeam;
    private Integer assistsCurrentTeam;
    private Integer careerAssists;
    private Integer nationalTeamId;
    private String nationalTeamName;

    private Boolean transferListed;
    private Long askingPrice;
    private HODateTime deadline;
    private Long highestBid;
    /** The user's own autobid, only while one is set. */
    private Long maxBid;
    private Integer bidderTeamId;
    private String bidderTeamName;

    /** Hattrick's transfer estimate, SEK (3.3). */
    private Long transferCompareAverage;
    private HODateTime transferCompareAverageDate;

    /** FamilyRelation (3.3): at most one. */
    private Integer relatedPlayerId;
    private Integer relatedPlayerSourceId;
    private String relatedPlayerName;
    private Integer relationshipTypeId;

    private HODateTime lastMatchDate;
    private Integer lastMatchId;
    private Integer lastMatchPositionCode;
    private Integer lastMatchPlayedMinutes;
    private Double lastMatchRating;
    private Double lastMatchRatingEndOfGame;

    /** TrophyList (3.3), empty unless the player's club has Gold supporter. Not a column. */
    private List<PlayerTrophy> trophies = new ArrayList<>();
}
