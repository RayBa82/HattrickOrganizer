package core.file.xml;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class XMLTrainingHistoryParserTest {

    /** Shaped from the training 2.3 doc, actionType=history (docs/raw/chpp/training.md). */
    private static final String HISTORY = """
            <?xml version="1.0" encoding="utf-8"?>
            <HattrickData>
              <FileName>training.xml</FileName>
              <Version>2.3</Version>
              <ActionType>history</ActionType>
              <TeamID>123</TeamID>
              <FromDate>2026-07-20 00:00:00</FromDate>
              <TrainingHistory>
                <Training>
                  <HattrickSeason>92</HattrickSeason>
                  <HattrickWeek>3</HattrickWeek>
                  <TrainingType>8</TrainingType>
                  <TrainingLevel>100</TrainingLevel>
                  <TrainingLevelStamina>15</TrainingLevelStamina>
                  <TrainingDate>2026-07-23 10:00:00</TrainingDate>
                </Training>
                <Training>
                  <HattrickSeason>92</HattrickSeason>
                  <HattrickWeek>9</HattrickWeek>
                  <TrainingType>9</TrainingType>
                  <TrainingLevel>100</TrainingLevel>
                  <TrainingLevelStamina>10</TrainingLevelStamina>
                  <TrainingDate>2026-09-03 10:00:00</TrainingDate>
                </Training>
              </TrainingHistory>
            </HattrickData>
            """;

    @Test
    void parse_readsEveryChangeOldestFirst() {
        final var history = XMLTrainingHistoryParser.parse(HISTORY);

        assertThat(history.available()).isTrue();
        assertThat(history.teamId()).isEqualTo(123);
        assertThat(history.fromDate()).isEqualTo("2026-07-20 00:00:00");
        assertThat(history.changes()).extracting(XMLTrainingHistoryParser.TrainingChange::week)
                .containsExactly(3, 9);
        final var first = history.changes().get(0);
        assertThat(first.season()).isEqualTo(92);
        assertThat(first.trainingType()).isEqualTo(8);
        assertThat(first.intensity()).isEqualTo(100);
        assertThat(first.staminaShare()).isEqualTo(15);
        assertThat(first.trainingDate()).isEqualTo("2026-07-23 10:00:00");
    }

    @Test
    void parse_isUnavailableForAnAccessDeniedFile() {
        final var history = XMLTrainingHistoryParser.parse("""
                <HattrickData><FileName>chpperror.xml</FileName><ErrorCode>53</ErrorCode>
                <Error>AccessDenied</Error></HattrickData>""");

        assertThat(history.available()).isFalse();
        assertThat(history.changes()).isEmpty();
    }
}
