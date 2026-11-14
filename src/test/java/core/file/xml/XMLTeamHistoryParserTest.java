package core.file.xml;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** teamhistory 1.0: seasons oldest first, and the trophies. */
public class XMLTeamHistoryParserTest {

    /** Also read by TeamToolsTest. */
    public static final String HISTORY = """
            <?xml version="1.0" encoding="utf-8"?>
            <HattrickData>
              <FileName>teamhistory.xml</FileName>
              <Version>1.0</Version>
              <Team>
                <TeamID>520472</TeamID>
                <TeamName>Juventus Brenk</TeamName>
                <League><LeagueID>3</LeagueID><LeagueName>Deutschland</LeagueName></League>
                <FoundedDate>2019-03-02 10:00:00</FoundedDate>
                <TeamHistory>
                  <Season Index="0">
                    <SeasonNumber>78</SeasonNumber>
                    <LeagueLevelUnitID>6253</LeagueLevelUnitID>
                    <LeagueLevelUnitName>VI.12</LeagueLevelUnitName>
                    <LeagueLevel>6</LeagueLevel>
                    <Position>1</Position>
                    <Points>38</Points>
                    <GoalsFor>51</GoalsFor>
                    <GoalsAgainst>12</GoalsAgainst>
                    <CupRound>5</CupRound>
                    <CupRoundsLeft>0</CupRoundsLeft>
                  </Season>
                  <Season Index="1">
                    <SeasonNumber>79</SeasonNumber>
                    <LeagueLevelUnitID>427</LeagueLevelUnitID>
                    <LeagueLevelUnitName>V.3</LeagueLevelUnitName>
                    <LeagueLevel>5</LeagueLevel>
                    <Position>4</Position>
                    <Points>24</Points>
                    <GoalsFor>30</GoalsFor>
                    <GoalsAgainst>28</GoalsAgainst>
                    <CupRound>-1</CupRound>
                    <CupRoundsLeft>-1</CupRoundsLeft>
                  </Season>
                </TeamHistory>
                <TrophyList>
                  <Trophy>
                    <TrophyTypeId>17</TrophyTypeId>
                    <TrophySeason>78</TrophySeason>
                    <LeagueLevel>6</LeagueLevel>
                    <LeagueLevelUnitId>6253</LeagueLevelUnitId>
                    <LeagueLevelUnitName>VI.12</LeagueLevelUnitName>
                    <GainedDate>2021-05-01 18:00:00</GainedDate>
                  </Trophy>
                </TrophyList>
              </Team>
            </HattrickData>
            """;

    @Test
    void readsSeasonsAndTrophies() {
        final var h = XMLTeamHistoryParser.parse(HISTORY);

        assertThat(h.teamId()).isEqualTo(520472);
        assertThat(h.foundedDate()).isEqualTo("2019-03-02 10:00:00");
        assertThat(h.seasons()).hasSize(2);
        final var first = h.seasons().get(0);
        assertThat(first.season()).isEqualTo(78);
        assertThat(first.leagueLevelUnitName()).isEqualTo("VI.12");
        assertThat(first.points()).isEqualTo(38);
        assertThat(first.cupRoundsLeft()).isZero();
        assertThat(h.seasons().get(1).cupRound()).isEqualTo(-1);
        assertThat(h.trophies()).singleElement()
                .satisfies(t -> assertThat(t.trophyTypeId()).isEqualTo(17));
    }

    @Test
    void anErrorFileGivesAnEmptyHistory() {
        final var h = XMLTeamHistoryParser.parse("<HattrickData><Error>x</Error></HattrickData>");

        assertThat(h.teamId()).isZero();
        assertThat(h.seasons()).isEmpty();
        assertThat(h.trophies()).isEmpty();
    }
}
