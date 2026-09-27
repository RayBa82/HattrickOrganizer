package core.file.xml;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class XMLTeamDetailsParserTest {

    /** Trimmed teamdetails 3.x: only what the kit fields need, plus what the parser walks past. */
    private static String teamdetails(String teamColors) {
        return """
                <?xml version="1.0" encoding="utf-8"?>
                <HattrickData>
                  <FetchedDate>2026-09-27 10:00:00</FetchedDate>
                  <User><Loginname>rb</Loginname><SupporterTier>gold</SupporterTier></User>
                  <Teams><Team>
                    <TeamID>1500</TeamID>
                    <TeamName>FC GaisBeach 04</TeamName>
                    <LogoURL>//res.hattrick.org/teamlogo/1/15/150/1500/1500.png</LogoURL>
                    %s
                    <DressURI>//res.hattrick.org/kits/1/2/3/matchKitSmall_1500.png</DressURI>
                    <DressAlternateURI>//res.hattrick.org/kits/1/2/3/matchKitSmall_alt_1500.png</DressAlternateURI>
                    <League><LeagueID>3</LeagueID></League>
                  </Team></Teams>
                </HattrickData>
                """.formatted(teamColors);
    }

    @Test
    void readsTheKitAndTheSupportersClubColours() {
        final var hash = XMLTeamDetailsParser.parseTeamdetailsFromString(teamdetails(
                "<TeamColors><BackgroundColor>1a2b3c</BackgroundColor><Color>ffffff</Color></TeamColors>"), 1500);

        assertThat(hash.get("DressURI")).isEqualTo("//res.hattrick.org/kits/1/2/3/matchKitSmall_1500.png");
        assertThat(hash.get("DressAlternateURI")).isEqualTo("//res.hattrick.org/kits/1/2/3/matchKitSmall_alt_1500.png");
        assertThat(hash.get("LogoURL")).isEqualTo("//res.hattrick.org/teamlogo/1/15/150/1500/1500.png");
        assertThat(hash.get("TeamColorBackground")).isEqualTo("1a2b3c");
        assertThat(hash.get("TeamColorText")).isEqualTo("ffffff");
    }

    /** Non-Supporters get no TeamColors container: the kit still comes through, the colours stay absent. */
    @Test
    void withoutTeamColorsTheKitStillComesThrough() {
        final var hash = XMLTeamDetailsParser.parseTeamdetailsFromString(teamdetails(""), 1500);

        assertThat(hash.get("DressURI")).isEqualTo("//res.hattrick.org/kits/1/2/3/matchKitSmall_1500.png");
        assertThat(hash.get("TeamColorBackground")).isNullOrEmpty();
        assertThat(hash.get("TeamColorText")).isNullOrEmpty();
    }
}
