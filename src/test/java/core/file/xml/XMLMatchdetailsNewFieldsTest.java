package core.file.xml;

import core.model.enums.MatchType;
import core.model.match.ChanceType;
import core.util.ResourceUtils;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

/** The fields matchdetails 3.1 and 3.2 added, read from a real 3.2 response. */
class XMLMatchdetailsNewFieldsTest {

    private static String fixture(String name) throws IOException {
        return ResourceUtils.getResourceFileAsString("chpp/matchdetails/versions/" + name + ".xml");
    }

    @Test
    void chancesAndReplaysPerTeam() throws IOException {
        final var md = XMLMatchdetailsParser.parseMatchdetailsFromString(fixture("league_3.2"), null);

        assertThat(md).isNotNull();
        assertThat(md.getChances(true, ChanceType.LEFT)).isEqualTo(2);
        assertThat(md.getChances(true, ChanceType.CENTER)).isEqualTo(5);
        assertThat(md.getChances(true, ChanceType.RIGHT)).isEqualTo(2);
        assertThat(md.getChances(true, ChanceType.SPECIAL_EVENTS)).isEqualTo(1);
        assertThat(md.getChances(true, ChanceType.OTHER)).isEqualTo(1);
        assertThat(md.getChances(false, ChanceType.CENTER)).isZero();
        assertThat(md.getChances(false, ChanceType.SPECIAL_EVENTS)).isEqualTo(1);
        // the replay pair comes for the user's own team only
        assertThat(md.getHomeHasReplay()).isFalse();
        assertThat(md.getHomeNrOfReplays()).isZero();
        assertThat(md.getGuestHasReplay()).isNull();
        assertThat(md.getGuestNrOfReplays()).isNull();
        assertThat(md.getExpectedResult()).isNull();
    }

    @Test
    void olderVersionsAndYouthMatchesLeaveTheNewFieldsNull() throws IOException {
        final var old = XMLMatchdetailsParser.parseMatchdetailsFromString(fixture("league_3.0"), null);
        final var youth = XMLMatchdetailsParser.parseMatchdetailsFromString(fixture("youthleague_3.2"), null);

        assertThat(old.getChances(true, ChanceType.LEFT)).isNull();
        assertThat(old.getHomeHasReplay()).isNull();
        assertThat(youth.getChances(true, ChanceType.CENTER)).isNotNull();
        assertThat(youth.getHomeHasReplay()).isNull();
        assertThat(youth.getGuestHasReplay()).isNull();
    }

    /** No captured match has one; the block follows the CHPP output tree of matchdetails 3.2. */
    private static final String EXPECTED_RESULT = """
            <ExpectedResult>
              <NrOfSamples>100</NrOfSamples>
              <HomeWins>61</HomeWins>
              <Draws>22</Draws>
              <AwayWins>17</AwayWins>
              <AverageHomeGoals>2.31</AverageHomeGoals>
              <AverageAwayGoals>1,05</AverageAwayGoals>
              <GoalDistribution>
                <Home><Goals0>8</Goals0><Goals1>20</Goals1><Goals2>30</Goals2><Goals3>22</Goals3><Goals4>12</Goals4><Goals5>5</Goals5><GoalsMoreThan5>3</GoalsMoreThan5></Home>
                <Away><Goals0>35</Goals0><Goals1>34</Goals1><Goals2>20</Goals2><Goals3>8</Goals3><Goals4>2</Goals4><Goals5>1</Goals5><GoalsMoreThan5>0</GoalsMoreThan5></Away>
              </GoalDistribution>
              <AverageWinMargin><Home>1.8</Home><Away>1.2</Away></AverageWinMargin>
              <OverUnder>
                <Line05><Over>97</Over><Under>3</Under></Line05>
                <Line15><Over>85</Over><Under>15</Under></Line15>
                <Line25><Over>64</Over><Under>36</Under></Line25>
                <Line35><Over>40</Over><Under>60</Under></Line35>
                <Line45><Over>19</Over><Under>81</Under></Line45>
              </OverUnder>
            </ExpectedResult>
            """;

    @Test
    void expectedResult() throws IOException {
        final var xml = fixture("league_3.2").replace("</Match>", EXPECTED_RESULT + "</Match>");

        final var result = XMLMatchdetailsParser.parseMatchdetailsFromString(xml, null).getExpectedResult();

        assertThat(result).isNotNull();
        assertThat(result.getMatchId()).isEqualTo(768523807);
        assertThat(result.getMatchType()).isEqualTo(MatchType.LEAGUE);
        assertThat(result.getNrOfSamples()).isEqualTo(100);
        assertThat(result.getHomeWins()).isEqualTo(61);
        assertThat(result.getDraws()).isEqualTo(22);
        assertThat(result.getAwayWins()).isEqualTo(17);
        assertThat(result.getAverageHomeGoals()).isEqualTo(2.31);
        assertThat(result.getAverageAwayGoals()).isEqualTo(1.05);
        // FullTime comes for cup-rule matches only
        assertThat(result.getFullTimeHomeWins()).isNull();
        assertThat(result.getGoals(true, 2)).isEqualTo(30);
        assertThat(result.getGoals(true, 6)).isEqualTo(3);
        assertThat(result.getGoals(false, 0)).isEqualTo(35);
        assertThat(result.getAverageWinMarginHome()).isEqualTo(1.8);
        assertThat(result.getAverageWinMarginAway()).isEqualTo(1.2);
        assertThat(result.getOverUnder(true, 0)).isEqualTo(97);
        assertThat(result.getOverUnder(false, 4)).isEqualTo(81);
    }

    @Test
    void expectedResultFullTimeOfACupRuleMatch() throws IOException {
        final var block = EXPECTED_RESULT.replace("<GoalDistribution>",
                "<FullTime><HomeWins>55</HomeWins><Draws>31</Draws><AwayWins>14</AwayWins></FullTime><GoalDistribution>");
        final var xml = fixture("league_3.2").replace("</Match>", block + "</Match>");

        final var result = XMLMatchdetailsParser.parseMatchdetailsFromString(xml, null).getExpectedResult();

        assertThat(result.getFullTimeHomeWins()).isEqualTo(55);
        assertThat(result.getFullTimeDraws()).isEqualTo(31);
        assertThat(result.getFullTimeAwayWins()).isEqualTo(14);
    }
}
