package core.db;

import core.file.xml.XMLMatchdetailsParser;
import core.model.match.ChanceType;
import core.util.ResourceUtils;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

/** The matchdetails 3.1/3.2 fields survive a store and load through MATCHDETAILS and MATCHEXPECTEDRESULT. */
class MatchDetailsNewFieldsStorageTest {

    private static final String EXPECTED_RESULT = """
            <ExpectedResult>
              <NrOfSamples>20</NrOfSamples><HomeWins>11</HomeWins><Draws>5</Draws><AwayWins>4</AwayWins>
              <AverageHomeGoals>2.1</AverageHomeGoals><AverageAwayGoals>0.9</AverageAwayGoals>
              <GoalDistribution>
                <Home><Goals0>1</Goals0><Goals1>4</Goals1><Goals2>7</Goals2><Goals3>5</Goals3><Goals4>2</Goals4><Goals5>1</Goals5><GoalsMoreThan5>0</GoalsMoreThan5></Home>
                <Away><Goals0>8</Goals0><Goals1>7</Goals1><Goals2>3</Goals2><Goals3>1</Goals3><Goals4>1</Goals4><Goals5>0</Goals5><GoalsMoreThan5>0</GoalsMoreThan5></Away>
              </GoalDistribution>
              <AverageWinMargin><Home>1.5</Home><Away>1.25</Away></AverageWinMargin>
              <OverUnder><Line25><Over>9</Over><Under>11</Under></Line25></OverUnder>
            </ExpectedResult>
            """;

    @Test
    void newFieldsRoundTrip() throws IOException {
        final var xml = Objects.requireNonNull(
                        ResourceUtils.getResourceFileAsString("chpp/matchdetails/versions/league_3.2.xml"))
                .replace("</Match>", EXPECTED_RESULT + "</Match>");
        final var parsed = XMLMatchdetailsParser.parseMatchdetailsFromString(xml, null);
        final var db = DBManager.instance();

        db.storeMatchDetails(parsed);
        final var loaded = db.loadMatchDetails(parsed.getMatchType().getId(), parsed.getMatchID());

        assertThat(loaded.getMatchID()).isEqualTo(768523807);
        for (var type : ChanceType.values()) {
            assertThat(loaded.getChances(true, type)).as("home " + type).isEqualTo(parsed.getChances(true, type));
            assertThat(loaded.getChances(false, type)).as("guest " + type).isEqualTo(parsed.getChances(false, type));
        }
        assertThat(loaded.getHomeHasReplay()).isFalse();
        assertThat(loaded.getHomeNrOfReplays()).isZero();
        assertThat(loaded.getGuestHasReplay()).isNull();
        assertThat(loaded.getGuestNrOfReplays()).isNull();

        final var result = loaded.getExpectedResult();
        assertThat(result).isNotNull();
        assertThat(result).usingRecursiveComparison().ignoringFields("isStored")
                .isEqualTo(parsed.getExpectedResult());
        assertThat(result.getOverUnder(true, 2)).isEqualTo(9);
        assertThat(result.getOverUnder(true, 0)).isNull();
        assertThat(result.getAverageWinMarginAway()).isEqualTo(1.25);
    }

    @Test
    void aMatchWithoutExpectedResultLoadsNone() throws IOException {
        final var parsed = XMLMatchdetailsParser.parseMatchdetailsFromString(
                ResourceUtils.getResourceFileAsString("chpp/matchdetails/versions/friendly_3.2.xml"), null);
        final var db = DBManager.instance();

        db.storeMatchDetails(parsed);
        final var loaded = db.loadMatchDetails(parsed.getMatchType().getId(), parsed.getMatchID());

        assertThat(loaded.getMatchID()).isEqualTo(772118612);
        assertThat(loaded.getExpectedResult()).isNull();
    }
}
