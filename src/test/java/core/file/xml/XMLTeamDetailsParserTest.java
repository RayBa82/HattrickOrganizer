package core.file.xml;

import core.util.ResourceUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.of;

class XMLTeamDetailsParserTest {

    private static Map<String, String> createTeamJuventusBrenk() {
        var ret = new HashMap<String, String>();
        ret.put("FetchedDate", "2026-10-03 18:12:24");
        ret.put("Loginname", "wsbrenk");
        ret.put("LastLoginDate", "2026-10-03 18:07:11");
        ret.put("HasSupporter", "True");
        ret.put("TeamID", "520472");
        ret.put("TeamName", "Juventus Brenk");
        ret.put("ActivationDate", "2004-09-25 02:57:00");
        ret.put("HomePage", "");
        ret.put("LogoURL", "//res.hattrick.org/teamlogo/6/53/521/520472/520472.JPG");
        ret.put("DressURI", "//res.hattrick.org/kits/16/155/1543/1542781/matchKitSmall.png");
        ret.put("DressAlternateURI", "//res.hattrick.org/kits/2/13/130/129787/matchKitSmall.png");
        ret.put("TeamColorBackground", "");
        ret.put("TeamColorText", "");
        ret.put("YouthTeamID", "76155");
        ret.put("YouthTeamName", "Brenk Street Boys");
        ret.put("LeagueID", "3");
        ret.put("LeagueLevel", "5");
        ret.put("LeagueLevelUnitName", "V.162");
        ret.put("LeagueLevelUnitID", "6253");
        ret.put("NumberOfVictories", "");
        ret.put("NumberOfUndefeated", "");
        ret.put("CountryName", "Deutschland");
        ret.put("FanclubSize", "2537");
        ret.put("TrainerID", "438940596");
        ret.put("ArenaName", "Ali-Jans-Arena");
        ret.put("ArenaID", "520472");
        ret.put("RegionID", "238");
        ret.put("IsBot", "False");
        ret.put("BotSince", "");
        ret.put("GlobalRanking", "17151");
        ret.put("LeagueRanking", "1203");
        ret.put("RegionRanking", "52");
        ret.put("PowerRating", "1002");
        ret.put("TeamRank", "");
        return ret;
    }

    private static Map<String, String> createTeamJuventusBrenk2() {
        var ret = new HashMap<String, String>();
        ret.put("FetchedDate", "2026-10-03 18:12:24");
        ret.put("Loginname", "wsbrenk");
        ret.put("LastLoginDate", "2026-10-03 18:07:11");
        ret.put("HasSupporter", "True");
        ret.put("TeamID", "1242154");
        ret.put("TeamName", "Juventus Brenk 2");
        ret.put("ActivationDate", "2013-05-06 16:49:00");
        ret.put("HomePage", "");
        ret.put("LogoURL", "//res.hattrick.org/teamlogo/13/125/1243/1242154/1242154.png");
        ret.put("DressURI", "//res.hattrick.org/kits/1/1/1/1/matchKitSmall.png");
        ret.put("DressAlternateURI", "//res.hattrick.org/kits/1/1/1/2/matchKitSmall.png");
        ret.put("TeamColorBackground", "0c69b0");
        ret.put("TeamColorText", "ffffff");
        ret.put("YouthTeamID", "2325763");
        ret.put("YouthTeamName", "Brenk Street Boys 2");
        ret.put("LeagueID", "3");
        ret.put("LeagueLevel", "5");
        ret.put("LeagueLevelUnitName", "V.104");
        ret.put("LeagueLevelUnitID", "6195");
        ret.put("NumberOfVictories", "");
        ret.put("NumberOfUndefeated", "");
        ret.put("CountryName", "Deutschland");
        ret.put("FanclubSize", "2448");
        ret.put("TrainerID", "453014817");
        ret.put("ArenaName", "Juventus Brenk 2 Arena");
        ret.put("ArenaID", "1238716");
        ret.put("RegionID", "238");
        ret.put("IsBot", "False");
        ret.put("BotSince", "");
        ret.put("GlobalRanking", "14867");
        ret.put("LeagueRanking", "1022");
        ret.put("RegionRanking", "42");
        ret.put("PowerRating", "1011");
        ret.put("TeamRank", "");
        return ret;
    }

    private static Map<String, String> createTeamJuventusBrenkIII() {
        var ret = new HashMap<String, String>();
        ret.put("FetchedDate", "2026-10-03 18:12:24");
        ret.put("Loginname", "wsbrenk");
        ret.put("LastLoginDate", "2026-10-03 18:07:11");
        ret.put("HasSupporter", "True");
        ret.put("TeamID", "1136288");
        ret.put("TeamName", "Juventus Brenk III");
        ret.put("ActivationDate", "2018-11-14 18:54:00");
        ret.put("HomePage", "");
        ret.put("LogoURL", "//res.hattrick.org/teamlogo/12/114/1137/1136288/1136288.png");
        ret.put("DressURI", "//res.hattrick.org/kits/1/1/1/1/matchKitSmall.png");
        ret.put("DressAlternateURI", "//res.hattrick.org/kits/1/1/1/2/matchKitSmall.png");
        ret.put("TeamColorBackground", "cc5669");
        ret.put("TeamColorText", "ffffff");
        ret.put("YouthTeamID", "2730774");
        ret.put("YouthTeamName", "Brenk Street Boys 3");
        ret.put("LeagueID", "3");
        ret.put("LeagueLevel", "5");
        ret.put("LeagueLevelUnitName", "V.88");
        ret.put("LeagueLevelUnitID", "6179");
        ret.put("NumberOfVictories", "");
        ret.put("NumberOfUndefeated", "");
        ret.put("CountryName", "Deutschland");
        ret.put("FanclubSize", "2491");
        ret.put("TrainerID", "445292433");
        ret.put("ArenaName", "Juventus Brenk III Arena");
        ret.put("ArenaID", "1132850");
        ret.put("RegionID", "238");
        ret.put("IsBot", "False");
        ret.put("BotSince", "");
        ret.put("GlobalRanking", "3054");
        ret.put("LeagueRanking", "161");
        ret.put("RegionRanking", "8");
        ret.put("PowerRating", "1110");
        ret.put("TeamRank", "");
        return ret;
    }

    private static Map<String, String> createTeamJuventusBrenkIV() {
        var ret = new HashMap<String, String>();
        ret.put("FetchedDate", "2026-10-03 18:12:24");
        ret.put("Loginname", "wsbrenk");
        ret.put("LastLoginDate", "2026-10-03 18:07:11");
        ret.put("HasSupporter", "True");
        ret.put("TeamID", "2230994");
        ret.put("TeamName", "Juventus Brenk IV");
        ret.put("ActivationDate", "2024-09-16 18:52:00");
        ret.put("HomePage", "");
        ret.put("LogoURL", "https://res.hattrick.org/teamlogo/23/224/2231/2230994/638621100989598166.png");
        ret.put("DressURI", "//res.hattrick.org/kits/1/1/1/1/matchKitSmall.png");
        ret.put("DressAlternateURI", "//res.hattrick.org/kits/1/1/1/2/matchKitSmall.png");
        ret.put("TeamColorBackground", "a89832");
        ret.put("TeamColorText", "ffffff");
        ret.put("YouthTeamID", "3142093");
        ret.put("YouthTeamName", "Brenk Street Boys IV");
        ret.put("LeagueID", "3");
        ret.put("LeagueLevel", "6");
        ret.put("LeagueLevelUnitName", "VI.104");
        ret.put("LeagueLevelUnitID", "15446");
        ret.put("NumberOfVictories", "");
        ret.put("NumberOfUndefeated", "");
        ret.put("CountryName", "Deutschland");
        ret.put("FanclubSize", "1877");
        ret.put("TrainerID", "391305821");
        ret.put("ArenaName", "Juventus Stadium");
        ret.put("ArenaID", "2227736");
        ret.put("RegionID", "238");
        ret.put("IsBot", "False");
        ret.put("BotSince", "");
        ret.put("GlobalRanking", "105260");
        ret.put("LeagueRanking", "8407");
        ret.put("RegionRanking", "372");
        ret.put("PowerRating", "838");
        ret.put("TeamRank", "");
        return ret;
    }

    private static Stream<Arguments> parseString() {
        return Stream.of(
            of(520472, createTeamJuventusBrenk()),
            of(1242154, createTeamJuventusBrenk2()),
            of(1136288, createTeamJuventusBrenkIII()),
            of(2230994, createTeamJuventusBrenkIV())
        );
    }

    @ParameterizedTest
    @MethodSource
    void parseString(int teamId, Map<String, String> expected) throws IOException {
        // given
        final var content = ResourceUtils.getResourceFileAsString("chpp/teamdetails/teamDetails_Juventus.xml");

        // when
        final var result = XMLTeamDetailsParser.parseTeamDetailsFromString(content, teamId);

        // then
        assertThat(result).isEqualTo(expected);
    }

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
        final var hash = XMLTeamDetailsParser.parseTeamDetailsFromString(teamdetails(
                "<TeamColors><BackgroundColor>1a2b3c</BackgroundColor><Color>ffffff</Color></TeamColors>"), 1500);

        assertThat(hash)
                .containsEntry("DressURI", "//res.hattrick.org/kits/1/2/3/matchKitSmall_1500.png")
                .containsEntry("DressAlternateURI", "//res.hattrick.org/kits/1/2/3/matchKitSmall_alt_1500.png")
                .containsEntry("LogoURL", "//res.hattrick.org/teamlogo/1/15/150/1500/1500.png")
                .containsEntry("TeamColorBackground", "1a2b3c")
                .containsEntry("TeamColorText", "ffffff");
    }

    /** Non-Supporters get no TeamColors container: the kit still comes through, the colours stay absent. */
    @Test
    void withoutTeamColorsTheKitStillComesThrough() {
        final var hash = XMLTeamDetailsParser.parseTeamDetailsFromString(teamdetails(""), 1500);

        assertThat(hash).containsEntry("DressURI", "//res.hattrick.org/kits/1/2/3/matchKitSmall_1500.png");
        assertThat(hash.get("TeamColorBackground")).isNullOrEmpty();
        assertThat(hash.get("TeamColorText")).isNullOrEmpty();
    }
}
