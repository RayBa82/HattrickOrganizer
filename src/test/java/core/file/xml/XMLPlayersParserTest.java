package core.file.xml;

import core.util.HODateTime;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The two shapes a {@code playerdetails} response takes for a player who is not ours. Both were
 * fetched live against the same non-supporter account; the differences between them are the whole
 * reason the market parse path exists (design spec §1.2, §1.3).
 */
class XMLPlayersParserTest {

    /** A foreign player who is on the market: full skill block, populated TransferDetails. */
    private static final String LISTED = """
            <?xml version="1.0" encoding="utf-8"?>
            <HattrickData>
              <FileName>playerdetails.xml</FileName>
              <Version>3.0</Version>
              <UserID>1234567</UserID>
              <FetchedDate>2026-09-05 12:00:00</FetchedDate>
              <UserSupporterTier>none</UserSupporterTier>
              <Player>
                <PlayerID>476487492</PlayerID>
                <FirstName>Bruno</FirstName>
                <NickName></NickName>
                <LastName>Gaspar</LastName>
                <PlayerNumber>100</PlayerNumber>
                <PlayerCategoryID>0</PlayerCategoryID>
                <OwnerNotes></OwnerNotes>
                <Age>24</Age>
                <AgeDays>77</AgeDays>
                <ArrivalDate>2025-04-01 10:00:00</ArrivalDate>
                <PlayerForm>6</PlayerForm>
                <Cards>1</Cards>
                <InjuryLevel>-1</InjuryLevel>
                <Statement></Statement>
                <Agreeability>3</Agreeability>
                <Aggressiveness>2</Aggressiveness>
                <Honesty>4</Honesty>
                <Experience>5</Experience>
                <Loyalty>12</Loyalty>
                <MotherClubBonus>False</MotherClubBonus>
                <Leadership>3</Leadership>
                <Specialty>2</Specialty>
                <NativeCountryID>3</NativeCountryID>
                <TSI>29000</TSI>
                <OwningTeam>
                  <TeamID>555444</TeamID>
                  <TeamName>Selling Sam</TeamName>
                  <LeagueID>2</LeagueID>
                </OwningTeam>
                <Salary>57840</Salary>
                <IsAbroad>True</IsAbroad>
                <Caps>0</Caps>
                <CapsU20>0</CapsU20>
                <CareerGoals>12</CareerGoals>
                <CareerHattricks>1</CareerHattricks>
                <MatchesCurrentTeam>30</MatchesCurrentTeam>
                <GoalsCurrentTeam>7</GoalsCurrentTeam>
                <CareerAssists>4</CareerAssists>
                <AssistsCurrentTeam>2</AssistsCurrentTeam>
                <TransferListed>True</TransferListed>
                <TransferDetails>
                  <AskingPrice>2850000</AskingPrice>
                  <Deadline>2026-09-05 20:08:31</Deadline>
                  <HighestBid>0</HighestBid>
                  <BidderTeam />
                </TransferDetails>
                <PlayerSkills>
                  <StaminaSkill>7</StaminaSkill>
                  <KeeperSkill>1</KeeperSkill>
                  <PlaymakerSkill>12</PlaymakerSkill>
                  <ScorerSkill>5</ScorerSkill>
                  <PassingSkill>9</PassingSkill>
                  <WingerSkill>7</WingerSkill>
                  <DefenderSkill>6</DefenderSkill>
                  <SetPiecesSkill>10</SetPiecesSkill>
                </PlayerSkills>
              </Player>
            </HattrickData>
            """;

    /**
     * The same player off the market. PlayerSkills is present but pruned to stamina, and
     * TransferDetails is present but <em>empty</em> - the trap that would break a presence check.
     */
    private static final String UNLISTED = """
            <?xml version="1.0" encoding="utf-8"?>
            <HattrickData>
              <FileName>playerdetails.xml</FileName>
              <Version>3.0</Version>
              <UserID>1234567</UserID>
              <FetchedDate>2026-09-06 12:00:00</FetchedDate>
              <UserSupporterTier>none</UserSupporterTier>
              <Player>
                <PlayerID>476487492</PlayerID>
                <FirstName>Bruno</FirstName>
                <NickName></NickName>
                <LastName>Gaspar</LastName>
                <PlayerNumber>100</PlayerNumber>
                <PlayerCategoryID>0</PlayerCategoryID>
                <OwnerNotes></OwnerNotes>
                <Age>24</Age>
                <AgeDays>78</AgeDays>
                <ArrivalDate>2025-04-01 10:00:00</ArrivalDate>
                <PlayerForm>6</PlayerForm>
                <Cards>1</Cards>
                <InjuryLevel>-1</InjuryLevel>
                <Statement></Statement>
                <Agreeability>3</Agreeability>
                <Aggressiveness>2</Aggressiveness>
                <Honesty>4</Honesty>
                <Experience>5</Experience>
                <Loyalty>12</Loyalty>
                <MotherClubBonus>False</MotherClubBonus>
                <Leadership>3</Leadership>
                <Specialty>2</Specialty>
                <NativeCountryID>3</NativeCountryID>
                <TSI>29000</TSI>
                <OwningTeam>
                  <TeamID>555444</TeamID>
                  <TeamName>Selling Sam</TeamName>
                  <LeagueID>2</LeagueID>
                </OwningTeam>
                <Salary>57840</Salary>
                <IsAbroad>True</IsAbroad>
                <Caps>0</Caps>
                <CapsU20>0</CapsU20>
                <CareerGoals>12</CareerGoals>
                <CareerHattricks>1</CareerHattricks>
                <MatchesCurrentTeam>30</MatchesCurrentTeam>
                <GoalsCurrentTeam>7</GoalsCurrentTeam>
                <CareerAssists>4</CareerAssists>
                <AssistsCurrentTeam>2</AssistsCurrentTeam>
                <TransferListed>False</TransferListed>
                <TransferDetails />
                <PlayerSkills>
                  <StaminaSkill>7</StaminaSkill>
                </PlayerSkills>
              </Player>
            </HattrickData>
            """;

    @Test
    void listedPlayerCarriesTheFullSkillBlockAndTheMarketData() {
        final var m = new XMLPlayersParser().parseMarketPlayerFromString(LISTED);

        assertThat(m.transferListed()).isTrue();
        assertThat(m.owningTeamId()).isEqualTo(555444);
        assertThat(m.isAbroad()).isTrue();
        assertThat(m.askingPrice()).isEqualTo(2_850_000L);
        assertThat(m.deadline()).isEqualTo(HODateTime.fromHT("2026-09-05 20:08:31"));
        assertThat(m.highestBid()).isZero();

        final var p = m.player();
        assertThat(p.getPlayerId()).isEqualTo(476487492);
        assertThat(p.getStamina()).isEqualTo(7);
        assertThat(p.getGoalkeeperSkill()).isEqualTo(1);
        assertThat(p.getPlaymakingSkill()).isEqualTo(12);
        assertThat(p.getScoringSkill()).isEqualTo(5);
        assertThat(p.getPassingSkill()).isEqualTo(9);
        assertThat(p.getWingerSkill()).isEqualTo(7);
        assertThat(p.getDefendingSkill()).isEqualTo(6);
        assertThat(p.getSetPiecesSkill()).isEqualTo(10);
    }

    /**
     * The single most valuable case in this file. Hattrick prunes an unlisted player's skills to
     * stamina, and {@code xmlIntValue} defaults a missing child to <b>0</b> - so parsing the block
     * unconditionally would report seven genuine skills as zero and drag every valuation built on
     * them downward. Leaving them unparsed keeps Player's own default of 1, which is not "unknown"
     * either, but is at least distinguishable; the §2 invariant is what keeps the row out of the
     * corpus.
     */
    @Test
    void unlistedPlayerKeepsStaminaAndLeavesTheSevenHiddenSkillsUnparsed() {
        final var m = new XMLPlayersParser().parseMarketPlayerFromString(UNLISTED);

        assertThat(m.transferListed()).isFalse();

        final var p = m.player();
        assertThat(p.getStamina()).isEqualTo(7);
        assertThat(p.getGoalkeeperSkill()).isNotZero();
        assertThat(p.getPlaymakingSkill()).isNotZero();
        assertThat(p.getScoringSkill()).isNotZero();
        assertThat(p.getPassingSkill()).isNotZero();
        assertThat(p.getWingerSkill()).isNotZero();
        assertThat(p.getDefendingSkill()).isNotZero();
        assertThat(p.getSetPiecesSkill()).isNotZero();
    }

    /**
     * An unlisted response carries no market data at all: no price, no bid, no deadline.
     * <p>
     * Note what this does <em>not</em> reach. {@code <TransferDetails />} is a present, non-null
     * Element for an unlisted player, so a presence check would report everybody as listed - but
     * {@code parseMarketPlayerFromString} short-circuits on {@code <TransferListed>} before it ever
     * looks the container up, so no unit test can exercise that trap through this method. This is a
     * regression guard on the record's null/zero defaults, and a reminder in prose: if the gate is
     * ever refactored into a container-presence check, this test stays green and the corpus rots.
     */
    @Test
    void anUnlistedPlayerCarriesNoMarketData() {
        final var m = new XMLPlayersParser().parseMarketPlayerFromString(UNLISTED);

        assertThat(m.askingPrice()).isZero();
        assertThat(m.highestBid()).isZero();
        assertThat(m.deadline()).isNull();
    }

    /**
     * The three character traits, and the one thing that can go wrong with them: Player's field
     * names are the legacy German ones, so {@code gentleness} holds Agreeability and {@code honesty}
     * holds Honesty. They were transposed here once. Both fixtures give the three distinct values
     * (3 / 2 / 4) precisely so a swap cannot pass.
     * <p>
     * Character sits at {@code <Player>} root, outside {@code <PlayerSkills>}, and both live
     * responses carry it - so unlike the seven hidden skills it survives the unlisted pruning.
     */
    @Test
    void characterIsReadWhetherOrNotHeIsListed() {
        for (final var xml : new String[]{LISTED, UNLISTED}) {
            final var p = new XMLPlayersParser().parseMarketPlayerFromString(xml).player();

            assertThat(p.getGentleness()).isEqualTo(3);    // <Agreeability>
            assertThat(p.getAggressivity()).isEqualTo(2);  // <Aggressiveness>
            assertThat(p.getHonesty()).isEqualTo(4);       // <Honesty>
        }
    }

    /**
     * The own-team guard that makes {@code parsePlayerDetailsFromString} return null for every
     * market player is not on this path - that guard is why the skills-fill pass has never stored
     * anything (design spec §1.4).
     */
    @Test
    void aForeignPlayerParsesAtAll() {
        assertThat(new XMLPlayersParser().parseMarketPlayerFromString(LISTED)).isNotNull();
        assertThat(new XMLPlayersParser().parseMarketPlayerFromString(LISTED).player()
                .getPlayerId()).isEqualTo(476487492);
    }
}
