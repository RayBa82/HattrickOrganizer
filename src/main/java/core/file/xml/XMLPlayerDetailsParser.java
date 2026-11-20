package core.file.xml;

import core.model.player.PlayerDetails;
import core.model.player.PlayerTrophy;
import core.util.HODateTime;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.util.ArrayList;
import java.util.List;

/**
 * Reads a whole {@code playerdetails} 3.3 response - {@code view}, or {@code placeBid}, whose
 * {@code View} container holds the same tree - into a {@link PlayerDetails}.
 * <p>
 * Every value is read from its own parent by direct child. {@code XMLManager.xmlValue} searches
 * the whole subtree, and this file repeats names: TeamID and TeamName sit under MotherClub,
 * OwningTeam and BidderTeam, TeamId and TeamName under every Trophy, Date under LastMatch.
 */
public final class XMLPlayerDetailsParser {

    private static final String TEAM_ID = "TeamID";
    private static final String TEAM_NAME = "TeamName";

    private XMLPlayerDetailsParser() {
    }

    /**
     * @param fetchedAt when the response was downloaded
     * @return null when the response holds no Player (a CHPP error, an empty answer)
     */
    public static PlayerDetails parse(String xml, HODateTime fetchedAt) {
        if (xml == null || xml.isBlank()) return null;
        final var doc = XMLManager.parseString(xml);
        if (doc == null) return null;
        final var root = doc.getDocumentElement();
        final var player = (Element) root.getElementsByTagName("Player").item(0);
        if (player == null) return null;

        final var d = new PlayerDetails();
        d.setFetchedAt(fetchedAt);
        // under HattrickData for view, under HattrickData/View for placeBid
        d.setUserSupporterTier(text((Element) root.getElementsByTagName("UserSupporterTier").item(0)));

        final var playerId = integer(player, "PlayerID");
        d.setPlayerId(playerId == null ? 0 : playerId);
        d.setFirstName(string(player, "FirstName"));
        d.setNickName(string(player, "NickName"));
        d.setLastName(string(player, "LastName"));
        d.setPlayerNumber(integer(player, "PlayerNumber"));
        d.setPlayerCategoryId(integer(player, "PlayerCategoryID"));
        d.setOwnerNotes(string(player, "OwnerNotes"));
        d.setAge(integer(player, "Age"));
        d.setAgeDays(integer(player, "AgeDays"));
        d.setNextBirthDay(dateTime(player, "NextBirthDay"));
        d.setGenderId(integer(player, "GenderID"));
        d.setArrivalDate(dateTime(player, "ArrivalDate"));
        d.setPlayerForm(integer(player, "PlayerForm"));
        d.setCards(integer(player, "Cards"));
        d.setInjuryLevel(integer(player, "InjuryLevel"));
        d.setStatement(string(player, "Statement"));
        d.setPlayerLanguage(string(player, "PlayerLanguage"));
        d.setPlayerLanguageId(integer(player, "PlayerLanguageID"));

        final var trainer = child(player, "TrainerData");
        d.setTrainerType(integer(trainer, "TrainerType"));
        d.setTrainerSkillLevel(integer(trainer, "TrainerSkillLevel"));

        d.setAgreeability(integer(player, "Agreeability"));
        d.setAggressiveness(integer(player, "Aggressiveness"));
        d.setHonesty(integer(player, "Honesty"));
        d.setExperience(integer(player, "Experience"));
        d.setLoyalty(integer(player, "Loyalty"));
        d.setMotherClubBonus(bool(player, "MotherClubBonus"));
        final var motherClub = child(player, "MotherClub");
        d.setMotherClubTeamId(integer(motherClub, TEAM_ID));
        d.setMotherClubTeamName(string(motherClub, TEAM_NAME));
        d.setLeadership(integer(player, "Leadership"));
        d.setSpecialty(integer(player, "Specialty"));
        d.setNativeCountryId(integer(player, "NativeCountryID"));
        d.setNativeLeagueId(integer(player, "NativeLeagueID"));
        d.setNativeLeagueName(string(player, "NativeLeagueName"));
        d.setTsi(integer(player, "TSI"));

        final var owningTeam = child(player, "OwningTeam");
        d.setOwningTeamId(integer(owningTeam, TEAM_ID));
        d.setOwningTeamName(string(owningTeam, TEAM_NAME));
        d.setOwningTeamLeagueId(integer(owningTeam, "LeagueID"));
        d.setSalary(money(player, "Salary"));
        d.setIsAbroad(bool(player, "IsAbroad"));

        // pruned to StaminaSkill when the skills are not visible: the others stay null
        final var skills = child(player, "PlayerSkills");
        d.setStaminaSkill(integer(skills, "StaminaSkill"));
        d.setKeeperSkill(integer(skills, "KeeperSkill"));
        d.setPlaymakerSkill(integer(skills, "PlaymakerSkill"));
        d.setScorerSkill(integer(skills, "ScorerSkill"));
        d.setPassingSkill(integer(skills, "PassingSkill"));
        d.setWingerSkill(integer(skills, "WingerSkill"));
        d.setDefenderSkill(integer(skills, "DefenderSkill"));
        d.setSetPiecesSkill(integer(skills, "SetPiecesSkill"));

        d.setCaps(integer(player, "Caps"));
        d.setCapsU20(integer(player, "CapsU20"));
        d.setCareerGoals(integer(player, "CareerGoals"));
        d.setCareerHattricks(integer(player, "CareerHattricks"));
        d.setLeagueGoals(integer(player, "LeagueGoals"));
        final var cupGoals = child(player, "CupGoals");
        d.setCupGoals(toInteger(text(cupGoals)));
        d.setCupGoalsAvailable(cupGoals == null || !cupGoals.hasAttribute("Available") ? null
                : Boolean.parseBoolean(cupGoals.getAttribute("Available")));
        d.setFriendliesGoals(integer(player, "FriendliesGoals"));
        d.setMatchesCurrentTeam(integer(player, "MatchesCurrentTeam"));
        d.setGoalsCurrentTeam(integer(player, "GoalsCurrentTeam"));
        d.setAssistsCurrentTeam(integer(player, "AssistsCurrentTeam"));
        d.setCareerAssists(integer(player, "CareerAssists"));
        d.setNationalTeamId(integer(player, "NationalTeamID"));
        d.setNationalTeamName(string(player, "NationalTeamName"));

        d.setTransferListed(bool(player, "TransferListed"));
        // present but empty when he is not listed, so every value reads null then
        final var transfer = child(player, "TransferDetails");
        d.setAskingPrice(money(transfer, "AskingPrice"));
        d.setDeadline(dateTime(transfer, "Deadline"));
        d.setHighestBid(money(transfer, "HighestBid"));
        d.setMaxBid(money(transfer, "MaxBid"));
        final var bidder = child(transfer, "BidderTeam");
        d.setBidderTeamId(integer(bidder, TEAM_ID));
        d.setBidderTeamName(string(bidder, TEAM_NAME));
        d.setTransferCompareAverage(money(player, "TransferCompareAverage"));
        d.setTransferCompareAverageDate(dateTime(player, "TransferCompareAverageDate"));

        final var family = child(player, "FamilyRelation");
        d.setRelatedPlayerId(integer(family, "RelatedPlayerId"));
        d.setRelatedPlayerSourceId(integer(family, "RelatedPlayerSourceId"));
        d.setRelatedPlayerName(string(family, "RelatedPlayerName"));
        d.setRelationshipTypeId(integer(family, "RelationshipTypeId"));

        d.setTrophies(trophies(child(player, "TrophyList"), d.getPlayerId()));

        final var lastMatch = child(player, "LastMatch");
        d.setLastMatchDate(dateTime(lastMatch, "Date"));
        d.setLastMatchId(integer(lastMatch, "MatchId"));
        d.setLastMatchPositionCode(integer(lastMatch, "PositionCode"));
        d.setLastMatchPlayedMinutes(integer(lastMatch, "PlayedMinutes"));
        d.setLastMatchRating(decimal(lastMatch, "Rating"));
        d.setLastMatchRatingEndOfGame(decimal(lastMatch, "RatingEndOfGame"));
        return d;
    }

    private static List<PlayerTrophy> trophies(Element trophyList, int playerId) {
        final var ret = new ArrayList<PlayerTrophy>();
        if (trophyList == null) return ret;
        for (var n = trophyList.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n instanceof Element e && "Trophy".equals(e.getTagName())) {
                final var t = new PlayerTrophy();
                t.setPlayerId(playerId);
                t.setTrophyTypeId(orZero(integer(e, "TrophyTypeId")));
                t.setTrophySeason(orZero(integer(e, "TrophySeason")));
                t.setLeagueLevelUnitId(orZero(integer(e, "LeagueLevelUnitId")));
                t.setTeamId(orZero(integer(e, "TeamId")));
                t.setTeamName(string(e, TEAM_NAME));
                t.setLeagueLevel(integer(e, "LeagueLevel"));
                t.setLeagueLevelUnitName(string(e, "LeagueLevelUnitName"));
                t.setGainedDate(dateTime(e, "GainedDate"));
                ret.add(t);
            }
        }
        return ret;
    }

    /** The first direct child of that name, null when parent or child is missing. */
    private static Element child(Element parent, String name) {
        if (parent == null) return null;
        for (Node n = parent.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n instanceof Element e && name.equals(e.getTagName())) return e;
        }
        return null;
    }

    /** The element's text, trimmed; null when it is missing or empty. */
    private static String text(Element e) {
        if (e == null) return null;
        final var s = e.getTextContent().trim();
        return s.isEmpty() ? null : s;
    }

    private static String string(Element parent, String name) {
        return text(child(parent, name));
    }

    private static Integer integer(Element parent, String name) {
        return toInteger(string(parent, name));
    }

    private static Integer toInteger(String s) {
        if (s == null) return null;
        try {
            return Integer.valueOf(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Long money(Element parent, String name) {
        final var s = string(parent, name);
        if (s == null) return null;
        try {
            return Long.valueOf(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Double decimal(Element parent, String name) {
        final var s = string(parent, name);
        if (s == null) return null;
        try {
            return Double.valueOf(s.replace(',', '.'));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Boolean bool(Element parent, String name) {
        final var s = string(parent, name);
        return s == null ? null : Boolean.parseBoolean(s);
    }

    private static HODateTime dateTime(Element parent, String name) {
        return HODateTime.fromHT(string(parent, name));
    }

    private static int orZero(Integer i) {
        return i == null ? 0 : i;
    }
}
