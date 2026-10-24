package core.file.xml;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class XMLMatchdetailsParserTest {

    private static String matchdetails(String finishedDate) {
        return """
            <?xml version="1.0" encoding="utf-8"?>
            <HattrickData>
              <FileName>matchdetails.xml</FileName>
              <Version>3.0</Version>
              <FetchedDate>2026-10-23 18:20:12</FetchedDate>
              <Match>
                <MatchID>768523852</MatchID>
                <MatchType>1</MatchType>
                <MatchContextId>0</MatchContextId>
                <MatchDate>2026-10-24 18:15:00</MatchDate>
                %s
                <HomeTeam><HomeTeamID>2168446</HomeTeamID><HomeTeamName>Home</HomeTeamName></HomeTeam>
                <AwayTeam><AwayTeamID>2524225</AwayTeamID><AwayTeamName>Away</AwayTeamName></AwayTeam>
                <Arena><ArenaID>2168445</ArenaID><ArenaName>Arena</ArenaName><WeatherID>2</WeatherID></Arena>
              </Match>
            </HattrickData>
            """.formatted(finishedDate);
    }

    @Test
    void weatherBeforeKickOffIsNoPlayedMatch() {
        final var details = XMLMatchdetailsParser.parseMatchdetailsFromString(matchdetails(""), null);

        assertThat(details).isNotNull();
        assertThat(details.getWetterId()).isEqualTo(2);
        assertThat(details.isFinished()).isFalse();
    }

    @Test
    void finishedDateMarksAPlayedMatch() {
        final var details = XMLMatchdetailsParser.parseMatchdetailsFromString(
                matchdetails("<FinishedDate>2026-10-24 20:05:00</FinishedDate>"), null);

        assertThat(details).isNotNull();
        assertThat(details.isFinished()).isTrue();
    }
}
