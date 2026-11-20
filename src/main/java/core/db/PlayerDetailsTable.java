package core.db;

import core.model.player.PlayerDetails;
import core.util.HODateTime;

import java.sql.Types;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * Every {@code playerdetails} 3.3 download, one row per download, every output element a column
 * (see {@link PlayerDetails}). Kept whole so that what HO uses can be decided later; the trophies
 * live in {@link PlayerTrophyTable}.
 */
public class PlayerDetailsTable extends AbstractTable {

    static final String TABLENAME = "PLAYERDETAILS";
    private static final int NAME_LENGTH = 256;
    private static final int TEXT_LENGTH = 2048;
    private static final int SHORT_LENGTH = 64;

    PlayerDetailsTable(ConnectionManager adapter) {
        super(TABLENAME, adapter);
        idColumns = 2;
    }

    @Override
    protected void initColumns() {
        columns = new ColumnDescriptor[]{
                ColumnDescriptor.Builder.newInstance().setColumnName("PLAYERID")
                        .setGetter(p -> row(p).getPlayerId()).setSetter((p, v) -> row(p).setPlayerId((Integer) v))
                        .setType(Types.INTEGER).isNullable(false).build(),
                ColumnDescriptor.Builder.newInstance().setColumnName("FETCHEDAT")
                        .setGetter(p -> HODateTime.toDbTimestamp(row(p).getFetchedAt()))
                        .setSetter((p, v) -> row(p).setFetchedAt((HODateTime) v))
                        .setType(Types.TIMESTAMP).isNullable(false).build(),
                string("USERSUPPORTERTIER", SHORT_LENGTH, PlayerDetails::getUserSupporterTier, PlayerDetails::setUserSupporterTier),
                string("FIRSTNAME", NAME_LENGTH, PlayerDetails::getFirstName, PlayerDetails::setFirstName),
                string("NICKNAME", NAME_LENGTH, PlayerDetails::getNickName, PlayerDetails::setNickName),
                string("LASTNAME", NAME_LENGTH, PlayerDetails::getLastName, PlayerDetails::setLastName),
                integer("PLAYERNUMBER", PlayerDetails::getPlayerNumber, PlayerDetails::setPlayerNumber),
                integer("PLAYERCATEGORYID", PlayerDetails::getPlayerCategoryId, PlayerDetails::setPlayerCategoryId),
                string("OWNERNOTES", TEXT_LENGTH, PlayerDetails::getOwnerNotes, PlayerDetails::setOwnerNotes),
                integer("AGE", PlayerDetails::getAge, PlayerDetails::setAge),
                integer("AGEDAYS", PlayerDetails::getAgeDays, PlayerDetails::setAgeDays),
                time("NEXTBIRTHDAY", PlayerDetails::getNextBirthDay, PlayerDetails::setNextBirthDay),
                integer("GENDERID", PlayerDetails::getGenderId, PlayerDetails::setGenderId),
                time("ARRIVALDATE", PlayerDetails::getArrivalDate, PlayerDetails::setArrivalDate),
                integer("PLAYERFORM", PlayerDetails::getPlayerForm, PlayerDetails::setPlayerForm),
                integer("CARDS", PlayerDetails::getCards, PlayerDetails::setCards),
                integer("INJURYLEVEL", PlayerDetails::getInjuryLevel, PlayerDetails::setInjuryLevel),
                string("STATEMENT", TEXT_LENGTH, PlayerDetails::getStatement, PlayerDetails::setStatement),
                string("PLAYERLANGUAGE", SHORT_LENGTH, PlayerDetails::getPlayerLanguage, PlayerDetails::setPlayerLanguage),
                integer("PLAYERLANGUAGEID", PlayerDetails::getPlayerLanguageId, PlayerDetails::setPlayerLanguageId),
                integer("TRAINERTYPE", PlayerDetails::getTrainerType, PlayerDetails::setTrainerType),
                integer("TRAINERSKILLLEVEL", PlayerDetails::getTrainerSkillLevel, PlayerDetails::setTrainerSkillLevel),
                integer("AGREEABILITY", PlayerDetails::getAgreeability, PlayerDetails::setAgreeability),
                integer("AGGRESSIVENESS", PlayerDetails::getAggressiveness, PlayerDetails::setAggressiveness),
                integer("HONESTY", PlayerDetails::getHonesty, PlayerDetails::setHonesty),
                integer("EXPERIENCE", PlayerDetails::getExperience, PlayerDetails::setExperience),
                integer("LOYALTY", PlayerDetails::getLoyalty, PlayerDetails::setLoyalty),
                bool("MOTHERCLUBBONUS", PlayerDetails::getMotherClubBonus, PlayerDetails::setMotherClubBonus),
                integer("MOTHERCLUBTEAMID", PlayerDetails::getMotherClubTeamId, PlayerDetails::setMotherClubTeamId),
                string("MOTHERCLUBTEAMNAME", NAME_LENGTH, PlayerDetails::getMotherClubTeamName, PlayerDetails::setMotherClubTeamName),
                integer("LEADERSHIP", PlayerDetails::getLeadership, PlayerDetails::setLeadership),
                integer("SPECIALTY", PlayerDetails::getSpecialty, PlayerDetails::setSpecialty),
                integer("NATIVECOUNTRYID", PlayerDetails::getNativeCountryId, PlayerDetails::setNativeCountryId),
                integer("NATIVELEAGUEID", PlayerDetails::getNativeLeagueId, PlayerDetails::setNativeLeagueId),
                string("NATIVELEAGUENAME", NAME_LENGTH, PlayerDetails::getNativeLeagueName, PlayerDetails::setNativeLeagueName),
                integer("TSI", PlayerDetails::getTsi, PlayerDetails::setTsi),
                integer("OWNINGTEAMID", PlayerDetails::getOwningTeamId, PlayerDetails::setOwningTeamId),
                string("OWNINGTEAMNAME", NAME_LENGTH, PlayerDetails::getOwningTeamName, PlayerDetails::setOwningTeamName),
                integer("OWNINGTEAMLEAGUEID", PlayerDetails::getOwningTeamLeagueId, PlayerDetails::setOwningTeamLeagueId),
                money("SALARY", PlayerDetails::getSalary, PlayerDetails::setSalary),
                bool("ISABROAD", PlayerDetails::getIsAbroad, PlayerDetails::setIsAbroad),
                integer("STAMINASKILL", PlayerDetails::getStaminaSkill, PlayerDetails::setStaminaSkill),
                integer("KEEPERSKILL", PlayerDetails::getKeeperSkill, PlayerDetails::setKeeperSkill),
                integer("PLAYMAKERSKILL", PlayerDetails::getPlaymakerSkill, PlayerDetails::setPlaymakerSkill),
                integer("SCORERSKILL", PlayerDetails::getScorerSkill, PlayerDetails::setScorerSkill),
                integer("PASSINGSKILL", PlayerDetails::getPassingSkill, PlayerDetails::setPassingSkill),
                integer("WINGERSKILL", PlayerDetails::getWingerSkill, PlayerDetails::setWingerSkill),
                integer("DEFENDERSKILL", PlayerDetails::getDefenderSkill, PlayerDetails::setDefenderSkill),
                integer("SETPIECESSKILL", PlayerDetails::getSetPiecesSkill, PlayerDetails::setSetPiecesSkill),
                integer("CAPS", PlayerDetails::getCaps, PlayerDetails::setCaps),
                integer("CAPSU20", PlayerDetails::getCapsU20, PlayerDetails::setCapsU20),
                integer("CAREERGOALS", PlayerDetails::getCareerGoals, PlayerDetails::setCareerGoals),
                integer("CAREERHATTRICKS", PlayerDetails::getCareerHattricks, PlayerDetails::setCareerHattricks),
                integer("LEAGUEGOALS", PlayerDetails::getLeagueGoals, PlayerDetails::setLeagueGoals),
                integer("CUPGOALS", PlayerDetails::getCupGoals, PlayerDetails::setCupGoals),
                bool("CUPGOALSAVAILABLE", PlayerDetails::getCupGoalsAvailable, PlayerDetails::setCupGoalsAvailable),
                integer("FRIENDLIESGOALS", PlayerDetails::getFriendliesGoals, PlayerDetails::setFriendliesGoals),
                integer("MATCHESCURRENTTEAM", PlayerDetails::getMatchesCurrentTeam, PlayerDetails::setMatchesCurrentTeam),
                integer("GOALSCURRENTTEAM", PlayerDetails::getGoalsCurrentTeam, PlayerDetails::setGoalsCurrentTeam),
                integer("ASSISTSCURRENTTEAM", PlayerDetails::getAssistsCurrentTeam, PlayerDetails::setAssistsCurrentTeam),
                integer("CAREERASSISTS", PlayerDetails::getCareerAssists, PlayerDetails::setCareerAssists),
                integer("NATIONALTEAMID", PlayerDetails::getNationalTeamId, PlayerDetails::setNationalTeamId),
                string("NATIONALTEAMNAME", NAME_LENGTH, PlayerDetails::getNationalTeamName, PlayerDetails::setNationalTeamName),
                bool("TRANSFERLISTED", PlayerDetails::getTransferListed, PlayerDetails::setTransferListed),
                money("ASKINGPRICE", PlayerDetails::getAskingPrice, PlayerDetails::setAskingPrice),
                time("DEADLINE", PlayerDetails::getDeadline, PlayerDetails::setDeadline),
                money("HIGHESTBID", PlayerDetails::getHighestBid, PlayerDetails::setHighestBid),
                money("MAXBID", PlayerDetails::getMaxBid, PlayerDetails::setMaxBid),
                integer("BIDDERTEAMID", PlayerDetails::getBidderTeamId, PlayerDetails::setBidderTeamId),
                string("BIDDERTEAMNAME", NAME_LENGTH, PlayerDetails::getBidderTeamName, PlayerDetails::setBidderTeamName),
                money("TRANSFERCOMPAREAVERAGE", PlayerDetails::getTransferCompareAverage, PlayerDetails::setTransferCompareAverage),
                time("TRANSFERCOMPAREAVERAGEDATE", PlayerDetails::getTransferCompareAverageDate, PlayerDetails::setTransferCompareAverageDate),
                integer("RELATEDPLAYERID", PlayerDetails::getRelatedPlayerId, PlayerDetails::setRelatedPlayerId),
                integer("RELATEDPLAYERSOURCEID", PlayerDetails::getRelatedPlayerSourceId, PlayerDetails::setRelatedPlayerSourceId),
                string("RELATEDPLAYERNAME", NAME_LENGTH, PlayerDetails::getRelatedPlayerName, PlayerDetails::setRelatedPlayerName),
                integer("RELATIONSHIPTYPEID", PlayerDetails::getRelationshipTypeId, PlayerDetails::setRelationshipTypeId),
                time("LASTMATCHDATE", PlayerDetails::getLastMatchDate, PlayerDetails::setLastMatchDate),
                integer("LASTMATCHID", PlayerDetails::getLastMatchId, PlayerDetails::setLastMatchId),
                integer("LASTMATCHPOSITIONCODE", PlayerDetails::getLastMatchPositionCode, PlayerDetails::setLastMatchPositionCode),
                integer("LASTMATCHPLAYEDMINUTES", PlayerDetails::getLastMatchPlayedMinutes, PlayerDetails::setLastMatchPlayedMinutes),
                decimal("LASTMATCHRATING", PlayerDetails::getLastMatchRating, PlayerDetails::setLastMatchRating),
                decimal("LASTMATCHRATINGENDOFGAME", PlayerDetails::getLastMatchRatingEndOfGame, PlayerDetails::setLastMatchRatingEndOfGame)
        };
    }

    private static PlayerDetails row(Object o) {
        return (PlayerDetails) o;
    }

    private static ColumnDescriptor column(String name, int type, Function<PlayerDetails, Object> getter,
                                           BiConsumer<PlayerDetails, Object> setter) {
        return ColumnDescriptor.Builder.newInstance().setColumnName(name)
                .setGetter(p -> getter.apply(row(p))).setSetter((p, v) -> setter.accept(row(p), v))
                .setType(type).isNullable(true).build();
    }

    private static ColumnDescriptor integer(String name, Function<PlayerDetails, Integer> getter,
                                            BiConsumer<PlayerDetails, Integer> setter) {
        return column(name, Types.INTEGER, getter::apply, (p, v) -> setter.accept(p, (Integer) v));
    }

    private static ColumnDescriptor money(String name, Function<PlayerDetails, Long> getter,
                                          BiConsumer<PlayerDetails, Long> setter) {
        return column(name, Types.BIGINT, getter::apply,
                (p, v) -> setter.accept(p, v == null ? null : ((Number) v).longValue()));
    }

    private static ColumnDescriptor decimal(String name, Function<PlayerDetails, Double> getter,
                                            BiConsumer<PlayerDetails, Double> setter) {
        return column(name, Types.DOUBLE, getter::apply, (p, v) -> setter.accept(p, (Double) v));
    }

    private static ColumnDescriptor bool(String name, Function<PlayerDetails, Boolean> getter,
                                         BiConsumer<PlayerDetails, Boolean> setter) {
        return column(name, Types.BOOLEAN, getter::apply, (p, v) -> setter.accept(p, (Boolean) v));
    }

    private static ColumnDescriptor time(String name, Function<PlayerDetails, HODateTime> getter,
                                         BiConsumer<PlayerDetails, HODateTime> setter) {
        return column(name, Types.TIMESTAMP, p -> HODateTime.toDbTimestamp(getter.apply(p)),
                (p, v) -> setter.accept(p, (HODateTime) v));
    }

    private static ColumnDescriptor string(String name, int length, Function<PlayerDetails, String> getter,
                                           BiConsumer<PlayerDetails, String> setter) {
        return ColumnDescriptor.Builder.newInstance().setColumnName(name)
                .setGetter(p -> getter.apply(row(p))).setSetter((p, v) -> setter.accept(row(p), (String) v))
                .setType(Types.VARCHAR).setLength(length).isNullable(true).build();
    }

    @Override
    protected String[] getConstraintStatements() {
        return new String[]{"  PRIMARY KEY (PLAYERID, FETCHEDAT)"};
    }

    private final String loadLatestSql = createSelectStatement("WHERE PLAYERID=? ORDER BY FETCHEDAT DESC LIMIT 1");

    void storePlayerDetails(PlayerDetails details) {
        store(details);
    }

    /** The player's newest download without its trophies, null when there is none. */
    PlayerDetails loadLatestPlayerDetails(int playerId) {
        return loadOne(PlayerDetails.class, connectionManager.executePreparedQuery(loadLatestSql, playerId));
    }
}
