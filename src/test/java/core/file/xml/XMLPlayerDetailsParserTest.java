package core.file.xml;

import core.model.player.PlayerTrophy;
import core.util.HODateTime;
import core.util.ResourceUtils;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

/** playerdetails 3.3 read whole (fixture built from the CHPP docs, see its header). */
class XMLPlayerDetailsParserTest {

    private static final HODateTime FETCHED = HODateTime.fromHT("2026-11-20 12:00:00");

    private static String full() throws IOException {
        return Objects.requireNonNull(ResourceUtils.getResourceFileAsString("chpp/playerdetails_3.3.xml"));
    }

    @Test
    void readsIdentityCharacterAndSkills() throws IOException {
        final var d = XMLPlayerDetailsParser.parse(full(), FETCHED);

        assertThat(d).isNotNull();
        assertThat(d.getPlayerId()).isEqualTo(476487492);
        assertThat(d.getFetchedAt()).isEqualTo(FETCHED);
        assertThat(d.getUserSupporterTier()).isEqualTo("gold");
        assertThat(d.getNickName()).isEqualTo("Bru");
        assertThat(d.getPlayerCategoryId()).isEqualTo(2);
        assertThat(d.getNextBirthDay()).isEqualTo(HODateTime.fromHT("2027-01-15 08:00:00"));
        assertThat(d.getGenderId()).isEqualTo(1);
        assertThat(d.getPlayerLanguage()).isEqualTo("Português");
        assertThat(d.getPlayerLanguageId()).isEqualTo(36);
        assertThat(d.getTrainerType()).isEqualTo(2);
        assertThat(d.getTrainerSkillLevel()).isEqualTo(4);
        assertThat(d.getMotherClubBonus()).isFalse();
        assertThat(d.getNativeLeagueName()).isEqualTo("Portugal");
        assertThat(d.getSalary()).isEqualTo(57_840L);
        assertThat(d.getIsAbroad()).isTrue();
        assertThat(d.getPlaymakerSkill()).isEqualTo(12);
        assertThat(d.getSetPiecesSkill()).isEqualTo(3);
    }

    @Test
    void readsCareerMarketFamilyAndLastMatch() throws IOException {
        final var d = XMLPlayerDetailsParser.parse(full(), FETCHED);

        assertThat(d.getCapsU20()).isEqualTo(11);
        assertThat(d.getCupGoals()).isEqualTo(1);
        assertThat(d.getCupGoalsAvailable()).isTrue();
        assertThat(d.getAssistsCurrentTeam()).isEqualTo(6);
        assertThat(d.getCareerAssists()).isEqualTo(21);
        assertThat(d.getNationalTeamName()).isEqualTo("Portugal");
        assertThat(d.getTransferListed()).isTrue();
        assertThat(d.getMaxBid()).isEqualTo(3_500_000L);
        assertThat(d.getTransferCompareAverage()).isEqualTo(3_100_000L);
        assertThat(d.getTransferCompareAverageDate()).isEqualTo(HODateTime.fromHT("2026-11-19 06:00:00"));
        assertThat(d.getRelatedPlayerId()).isEqualTo(476487493);
        assertThat(d.getRelatedPlayerSourceId()).isEqualTo(1);
        assertThat(d.getRelatedPlayerName()).isEqualTo("Rui Gaspar");
        assertThat(d.getRelationshipTypeId()).isEqualTo(2);
        assertThat(d.getLastMatchId()).isEqualTo(765432100);
        assertThat(d.getLastMatchPositionCode()).isEqualTo(107);
        assertThat(d.getLastMatchRating()).isEqualTo(4.5);
        assertThat(d.getLastMatchRatingEndOfGame()).isEqualTo(4.0);
    }

    /** TeamID/TeamName appear under MotherClub, OwningTeam and BidderTeam: each from its own parent. */
    @Test
    void readsRepeatedNamesFromTheirOwnContainer() throws IOException {
        final var d = XMLPlayerDetailsParser.parse(full(), FETCHED);

        assertThat(d.getMotherClubTeamId()).isEqualTo(111222);
        assertThat(d.getMotherClubTeamName()).isEqualTo("Mother FC");
        assertThat(d.getOwningTeamId()).isEqualTo(555444);
        assertThat(d.getOwningTeamName()).isEqualTo("Selling Sam");
        assertThat(d.getOwningTeamLeagueId()).isEqualTo(2);
        assertThat(d.getBidderTeamId()).isEqualTo(4711);
        assertThat(d.getBidderTeamName()).isEqualTo("HO FC");
        assertThat(d.getLastMatchDate()).isEqualTo(HODateTime.fromHT("2026-11-15 15:00:00"));
    }

    @Test
    void readsTheTrophyList() throws IOException {
        final var trophies = XMLPlayerDetailsParser.parse(full(), FETCHED).getTrophies();

        assertThat(trophies).extracting(PlayerTrophy::getTrophyTypeId).containsExactly(17, 19);
        final var title = trophies.get(0);
        assertThat(title.getPlayerId()).isEqualTo(476487492);
        assertThat(title.getTeamId()).isEqualTo(555444);
        assertThat(title.getTeamName()).isEqualTo("Selling Sam");
        assertThat(title.getTrophySeason()).isEqualTo(92);
        assertThat(title.getLeagueLevel()).isEqualTo(4);
        assertThat(title.getLeagueLevelUnitId()).isEqualTo(3456);
        assertThat(title.getLeagueLevelUnitName()).isEqualTo("IV.12");
        assertThat(title.getGainedDate()).isEqualTo(HODateTime.fromHT("2026-03-14 18:00:00"));
        final var topScorer = trophies.get(1);
        assertThat(topScorer.getTeamId()).isZero();
        assertThat(topScorer.getTeamName()).isNull();
        assertThat(topScorer.getLeagueLevelUnitName()).isNull();
    }

    /** An unlisted foreign player: skills pruned to stamina, empty containers, nothing optional sent. */
    @Test
    void leavesWhatIsNotSentNull() {
        final var xml = """
                <HattrickData><UserSupporterTier /><Player>
                  <PlayerID>5</PlayerID>
                  <PlayerSkills><StaminaSkill>6</StaminaSkill></PlayerSkills>
                  <CupGoals>0</CupGoals>
                  <TransferListed>False</TransferListed>
                  <TransferDetails />
                  <FamilyRelation />
                  <TrophyList />
                </Player></HattrickData>
                """;
        final var d = XMLPlayerDetailsParser.parse(xml, FETCHED);

        assertThat(d.getUserSupporterTier()).isNull();
        assertThat(d.getStaminaSkill()).isEqualTo(6);
        assertThat(d.getKeeperSkill()).isNull();
        assertThat(d.getCupGoalsAvailable()).isNull();
        assertThat(d.getTransferListed()).isFalse();
        assertThat(d.getAskingPrice()).isNull();
        assertThat(d.getBidderTeamId()).isNull();
        assertThat(d.getRelatedPlayerId()).isNull();
        assertThat(d.getTrainerSkillLevel()).isNull();
        assertThat(d.getLastMatchId()).isNull();
        assertThat(d.getTrophies()).isEmpty();
    }

    /** placeBid answers with the same tree inside a View container. */
    @Test
    void readsThePlaceBidAnswer() {
        final var xml = """
                <HattrickData><View><UserSupporterTier>silver</UserSupporterTier><Player>
                  <PlayerID>111</PlayerID>
                  <TransferListed>True</TransferListed>
                  <TransferDetails><HighestBid>1750000</HighestBid><MaxBid>2000000</MaxBid></TransferDetails>
                </Player></View></HattrickData>
                """;
        final var d = XMLPlayerDetailsParser.parse(xml, FETCHED);

        assertThat(d.getPlayerId()).isEqualTo(111);
        assertThat(d.getUserSupporterTier()).isEqualTo("silver");
        assertThat(d.getHighestBid()).isEqualTo(1_750_000L);
        assertThat(d.getMaxBid()).isEqualTo(2_000_000L);
    }

    @Test
    void returnsNullWithoutAPlayer() {
        assertThat(XMLPlayerDetailsParser.parse("", FETCHED)).isNull();
        assertThat(XMLPlayerDetailsParser.parse(
                "<HattrickData><Error>Player not found</Error></HattrickData>", FETCHED)).isNull();
    }
}
