package core.file.xml;

import core.model.cup.CupLevel;
import core.model.enums.MatchType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class XMLMatchesParserTest {

    /** MatchContextId used to be read for tournaments only, so cup and league rows lost their id. */
    @Test
    void parseMatchesFromString_keepsTheContextIdOfACupMatch() {
        final var matches = XMLMatchesParser.parseMatchesFromString("""
                <HattrickData><Team><MatchList><Match>
                <MatchID>770755312</MatchID>
                <HomeTeam><HomeTeamID>1</HomeTeamID><HomeTeamName>A</HomeTeamName></HomeTeam>
                <AwayTeam><AwayTeamID>2</AwayTeamID><AwayTeamName>B</AwayTeamName></AwayTeam>
                <MatchDate>2026-08-04 18:00:00</MatchDate><SourceSystem>Hattrick</SourceSystem>
                <MatchType>3</MatchType><MatchContextId>1330</MatchContextId>
                <CupLevel>1</CupLevel><CupLevelIndex>1</CupLevelIndex>
                <HomeGoals>1</HomeGoals><AwayGoals>2</AwayGoals><Status>FINISHED</Status>
                </Match></MatchList></Team></HattrickData>""");

        assertThat(matches).hasSize(1);
        final var cup = matches.get(0);
        assertThat(cup.getMatchType()).isEqualTo(MatchType.CUP);
        assertThat(cup.getCupLevel()).isEqualTo(CupLevel.NATIONALorDIVISIONAL);
        assertThat(cup.getMatchContextId()).isEqualTo(1330);
    }
}
