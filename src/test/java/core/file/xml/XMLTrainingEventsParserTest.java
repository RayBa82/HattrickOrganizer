package core.file.xml;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class XMLTrainingEventsParserTest {

    /** Shaped from the trainingevents 1.3 doc (docs/raw/chpp/trainingevents.md). */
    private static final String EVENTS = """
            <?xml version="1.0" encoding="utf-8"?>
            <HattrickData>
              <FileName>trainingevents.xml</FileName>
              <Version>1.3</Version>
              <UserSupporterTier></UserSupporterTier>
              <Player>
                <PlayerID>4711</PlayerID>
                <TrainingEvents Available="True">
                  <TrainingEvent Index="1">
                    <SkillID>8</SkillID>
                    <OldLevel>6</OldLevel>
                    <NewLevel>7</NewLevel>
                    <Season>92</Season>
                    <MatchRound>5</MatchRound>
                    <DayNumber>5</DayNumber>
                  </TrainingEvent>
                  <TrainingEvent Index="2">
                    <SkillID>2</SkillID>
                    <OldLevel>5</OldLevel>
                    <NewLevel>6</NewLevel>
                    <Season>91</Season>
                    <MatchRound>14</MatchRound>
                    <DayNumber>5</DayNumber>
                  </TrainingEvent>
                </TrainingEvents>
              </Player>
            </HattrickData>
            """;

    @Test
    void parse_readsEveryEvent() {
        final var result = XMLTrainingEventsParser.parse(EVENTS);
        assertThat(result.available()).isTrue();
        assertThat(result.events()).containsExactly(
                new XMLTrainingEventsParser.TrainingEvent(8, 6, 7, 92, 5, 5),
                new XMLTrainingEventsParser.TrainingEvent(2, 5, 6, 91, 14, 5));
    }

    /** While the player is in a match Hattrick withholds the events. */
    @Test
    void parse_unavailableWhilePlaying() {
        final var result = XMLTrainingEventsParser.parse(
                "<HattrickData><Player><TrainingEvents Available=\"False\"/></Player></HattrickData>");
        assertThat(result.available()).isFalse();
        assertThat(result.events()).isEmpty();
    }

    @Test
    void parse_garbageIsUnavailableAndEmpty() {
        final var result = XMLTrainingEventsParser.parse("not xml");
        assertThat(result.available()).isFalse();
        assertThat(result.events()).isEmpty();
    }
}
