package core.file.xml;

import core.constants.player.PlayerSpeciality;
import core.util.HODateTime;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class XMLTransferSearchParserTest {

    private static final String TWO_RESULTS = """
            <?xml version="1.0" encoding="utf-8"?>
            <HattrickData>
              <FileName>transfersearch.xml</FileName>
              <Version>1.1</Version>
              <UserID>1234567</UserID>
              <FetchedDate>2026-08-30 12:00:00</FetchedDate>
              <TransferSearch>
                <ItemCount>-1</ItemCount>
                <PageSize>25</PageSize>
                <PageIndex>0</PageIndex>
                <TransferResults>
                  <TransferResult>
                    <PlayerId>111222333</PlayerId>
                    <FirstName>Sven</FirstName>
                    <NickName>Der Turm</NickName>
                    <LastName>Persson</LastName>
                    <NativeCountryID>3</NativeCountryID>
                    <AskingPrice>1500000</AskingPrice>
                    <Deadline>2026-09-02 20:15:00</Deadline>
                    <HighestBid>1650000</HighestBid>
                    <BidderTeam>
                      <TeamID>999888</TeamID>
                      <TeamName>Bidding Boys</TeamName>
                    </BidderTeam>
                    <Details>
                      <Age>24</Age>
                      <AgeDays>77</AgeDays>
                      <Salary>44000</Salary>
                      <TSI>29000</TSI>
                      <PlayerForm>6</PlayerForm>
                      <Experience>4</Experience>
                      <Leadership>3</Leadership>
                      <Specialty>2</Specialty>
                      <Cards>1</Cards>
                      <InjuryLevel>-1</InjuryLevel>
                      <StaminaSkill>8</StaminaSkill>
                      <KeeperSkill>1</KeeperSkill>
                      <PlaymakerSkill>12</PlaymakerSkill>
                      <ScorerSkill>5</ScorerSkill>
                      <PassingSkill>9</PassingSkill>
                      <WingerSkill>7</WingerSkill>
                      <DefenderSkill>6</DefenderSkill>
                      <SetPiecesSkill>10</SetPiecesSkill>
                      <SellerTeam>
                        <TeamID>555444</TeamID>
                        <TeamName>Selling Sam</TeamName>
                        <LeagueId>2</LeagueId>
                      </SellerTeam>
                    </Details>
                  </TransferResult>
                  <TransferResult>
                    <PlayerId>444555666</PlayerId>
                    <FirstName>Ola</FirstName>
                    <NickName></NickName>
                    <LastName>Nilsson</LastName>
                    <NativeCountryID>3</NativeCountryID>
                    <AskingPrice>250000</AskingPrice>
                    <Deadline>2026-08-30 11:00:00</Deadline>
                    <HighestBid>0</HighestBid>
                    <BidderTeam></BidderTeam>
                    <Details></Details>
                  </TransferResult>
                </TransferResults>
              </TransferSearch>
            </HattrickData>
            """;

    @Test
    void parse_readsPagingAndListedPlayer() {
        final var page = XMLTransferSearchParser.parse(TWO_RESULTS);

        assertThat(page.itemCount()).isEqualTo(-1); // more than 100 matches
        assertThat(page.pageSize()).isEqualTo(25);
        assertThat(page.pageIndex()).isZero();
        assertThat(page.hits()).hasSize(2);

        final var hit = page.hits().get(0);
        assertThat(hit.playerId()).isEqualTo(111222333);
        assertThat(hit.playerName()).isEqualTo("Sven 'Der Turm' Persson");
        assertThat(hit.nativeCountryId()).isEqualTo(3);
        assertThat(hit.askingPrice()).isEqualTo(1500000L);
        assertThat(hit.deadline()).isEqualTo(HODateTime.fromHT("2026-09-02 20:15:00"));
        assertThat(hit.highestBid()).isEqualTo(1650000L);
        assertThat(hit.bidderTeamName()).isEqualTo("Bidding Boys");
        assertThat(hit.sellerTeamId()).isEqualTo(555444);
        assertThat(hit.sellerTeamName()).isEqualTo("Selling Sam");
        assertThat(hit.sellerLeagueId()).isEqualTo(2);

        final var details = hit.details();
        assertThat(details).isNotNull();
        assertThat(details.age()).isEqualTo(24);
        assertThat(details.ageDays()).isEqualTo(77);
        assertThat(details.salary()).isEqualTo(44000L);
        assertThat(details.tsi()).isEqualTo(29000);
        assertThat(details.form()).isEqualTo(6);
        assertThat(details.experience()).isEqualTo(4);
        assertThat(details.leadership()).isEqualTo(3);
        assertThat(details.specialty()).isEqualTo(PlayerSpeciality.QUICK);
        assertThat(details.cards()).isEqualTo(1);
        assertThat(details.injuryLevel()).isEqualTo(-1);
        assertThat(details.stamina()).isEqualTo(8);
        assertThat(details.keeper()).isEqualTo(1);
        assertThat(details.playmaker()).isEqualTo(12);
        assertThat(details.scorer()).isEqualTo(5);
        assertThat(details.passing()).isEqualTo(9);
        assertThat(details.winger()).isEqualTo(7);
        assertThat(details.defender()).isEqualTo(6);
        assertThat(details.setPieces()).isEqualTo(10);
    }

    @Test
    void parse_expiredResultKeepsMarketDataWithoutDetails() {
        final var hit = XMLTransferSearchParser.parse(TWO_RESULTS).hits().get(1);

        assertThat(hit.details()).isNull();
        assertThat(hit.playerName()).isEqualTo("Ola Nilsson");
        assertThat(hit.askingPrice()).isEqualTo(250000L);
        assertThat(hit.highestBid()).isZero();
        assertThat(hit.bidderTeamName()).isEmpty();
        assertThat(hit.sellerTeamName()).isEmpty();
    }

    @Test
    void parse_garbageIsAnEmptyPage() {
        assertThat(XMLTransferSearchParser.parse("not xml at all").hits()).isEmpty();
    }
}
