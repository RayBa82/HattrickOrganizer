package core.file.xml;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class XMLPlayerStatsParserTest {

    /** Shaped from the playerstats 1.0 doc (docs/raw/chpp/playerstats.md); no live sample yet. */
    private static final String STATS = """
            <?xml version="1.0" encoding="utf-8"?>
            <HattrickData>
              <FileName>playerstats.xml</FileName>
              <Version>1.0</Version>
              <Player>
                <PlayerID>4711</PlayerID>
                <FirstName>Kalle</FirstName>
                <NickName></NickName>
                <LastName>Anka</LastName>
                <PlayerNumber>7</PlayerNumber>
                <OwningTeam>
                  <TeamID>123</TeamID>
                  <TeamName>Ducks FC</TeamName>
                  <LeagueID>3</LeagueID>
                </OwningTeam>
                <MatchStats>
                  <Match>
                    <MatchID>900</MatchID>
                    <MatchType>1</MatchType>
                    <SourceSystem>Hattrick</SourceSystem>
                    <MatchDate>2026-11-01 15:00:00</MatchDate>
                    <HomeTeam><HomeTeamName>Ducks FC</HomeTeamName></HomeTeam>
                    <AwayTeam><AwayTeamName>Geese</AwayTeamName></AwayTeam>
                    <IsWalkover>False</IsWalkover>
                    <Age>21</Age>
                    <AgeDays>34</AgeDays>
                    <TSI>5400</TSI>
                    <PlayerForm>6</PlayerForm>
                    <Stamina>7</Stamina>
                    <Loyalty>12</Loyalty>
                    <Experience>4</Experience>
                    <GoalsInMatch>1</GoalsInMatch>
                    <YellowCardsInMatch>0</YellowCardsInMatch>
                    <RedCardsInMatch>0</RedCardsInMatch>
                    <InjuryLevel>-1</InjuryLevel>
                    <PlayedMinutes>90</PlayedMinutes>
                    <Rating>3.5</Rating>
                    <RatingEndOfGame>3</RatingEndOfGame>
                    <Positions>
                      <Position><PositionCode>107</PositionCode><Minutes>60</Minutes></Position>
                      <Position><PositionCode>111</PositionCode><Minutes>30</Minutes></Position>
                    </Positions>
                  </Match>
                  <Match>
                    <MatchID>901</MatchID>
                    <MatchType>4</MatchType>
                    <SourceSystem>Hattrick</SourceSystem>
                    <MatchDate>2026-11-05 19:00:00</MatchDate>
                    <HomeTeam><HomeTeamName>Swans</HomeTeamName></HomeTeam>
                    <AwayTeam><AwayTeamName>Ducks FC</AwayTeamName></AwayTeam>
                    <IsWalkover>False</IsWalkover>
                    <Age>21</Age>
                    <AgeDays>38</AgeDays>
                    <TSI>5450</TSI>
                    <PlayerForm>6</PlayerForm>
                    <Stamina></Stamina>
                    <Loyalty></Loyalty>
                    <Experience>4</Experience>
                    <GoalsInMatch>0</GoalsInMatch>
                    <YellowCardsInMatch>0</YellowCardsInMatch>
                    <RedCardsInMatch>0</RedCardsInMatch>
                    <InjuryLevel>-1</InjuryLevel>
                    <PlayedMinutes>0</PlayedMinutes>
                    <Rating>0</Rating>
                    <RatingEndOfGame>0</RatingEndOfGame>
                    <Positions></Positions>
                  </Match>
                </MatchStats>
              </Player>
            </HattrickData>
            """;

    @Test
    void parse_readsThePlayerAndEveryMatch() {
        final var stats = XMLPlayerStatsParser.parse(STATS);

        assertThat(stats.playerId()).isEqualTo(4711);
        assertThat(stats.lastName()).isEqualTo("Anka");
        assertThat(stats.owningTeamId()).isEqualTo(123);
        assertThat(stats.matches()).hasSize(2);

        final var played = stats.matches().get(0);
        assertThat(played.matchId()).isEqualTo(900);
        assertThat(played.homeTeamName()).isEqualTo("Ducks FC");
        assertThat(played.awayTeamName()).isEqualTo("Geese");
        assertThat(played.walkover()).isFalse();
        assertThat(played.stamina()).isEqualTo(7);
        assertThat(played.rating()).isEqualTo(3.5);
        assertThat(played.ratingEndOfGame()).isEqualTo(3.0);
        assertThat(played.injuryLevel()).isEqualTo(-1);
        assertThat(played.positions()).containsExactly(
                java.util.Map.entry(107, 60), java.util.Map.entry(111, 30));
    }

    @Test
    void parse_leavesEmptyStaminaAndLoyaltyNull() {
        final var benched = XMLPlayerStatsParser.parse(STATS).matches().get(1);

        assertThat(benched.stamina()).isNull();
        assertThat(benched.loyalty()).isNull();
        assertThat(benched.positions()).isEmpty();
    }

    @Test
    void parse_isEmptyForAnErrorFile() {
        final var stats = XMLPlayerStatsParser.parse(
                "<HattrickData><ErrorCode>50</ErrorCode></HattrickData>");

        assertThat(stats.playerId()).isZero();
        assertThat(stats.matches()).isEmpty();
    }
}
