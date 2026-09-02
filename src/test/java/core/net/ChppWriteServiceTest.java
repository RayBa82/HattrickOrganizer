package core.net;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class ChppWriteServiceTest {

    /** A training echo with the three values and TrainingSet=true. */
    private static final String TRAINING_OK = """
            <?xml version="1.0" encoding="utf-8"?>
            <HattrickData>
              <FileName>training.xml</FileName>
              <Version>2.1</Version>
              <FetchedDate>2026-09-03 12:00:00</FetchedDate>
              <View>
                <Team>
                  <TeamID>1500</TeamID>
                  <TeamName>HO FC</TeamName>
                  <TrainingLevel>40</TrainingLevel>
                  <StaminaTrainingPart>10</StaminaTrainingPart>
                  <TrainingType>3</TrainingType>
                  <Trainer><TrainerID>1</TrainerID><TrainerName>T</TrainerName><ArrivalDate>2026-01-01 00:00:00</ArrivalDate></Trainer>
                </Team>
              </View>
              <TrainingSet>true</TrainingSet>
            </HattrickData>
            """;

    private static final String TRAINING_NOT_SET = TRAINING_OK.replace(
            "<TrainingSet>true</TrainingSet>", "<TrainingSet>false</TrainingSet>");

    /** A service whose fetch* methods answer from canned strings, never the network. */
    static class StubService extends ChppWriteService {
        String trainingResponse = TRAINING_OK;
        int trainingFetches;

        @Override
        protected String fetchSetTraining(int teamId, int trainingType, int trainingLevel, int staminaLevel) {
            trainingFetches++;
            return trainingResponse;
        }
    }

    private StubService service;

    @BeforeEach
    void setUp() {
        service = new StubService();
        core.model.UserParameter.instance().mcpChppWritesPerSession = 20;
    }

    @Test
    void setTraining_returnsTheCommittedValuesFromTheEcho() throws Exception {
        final var committed = service.setTrainingForAgent(3, 40, 10);
        assertThat(committed.trainingType()).isEqualTo(3);
        assertThat(committed.intensity()).isEqualTo(40);
        assertThat(committed.staminaShare()).isEqualTo(10);
        assertThat(service.writesThisSession).isEqualTo(1);
    }

    @Test
    void setTraining_trainingSetFalseIsAFailure() {
        service.trainingResponse = TRAINING_NOT_SET;
        assertThatExceptionOfType(ChppWriteService.ChppWriteException.class)
                .isThrownBy(() -> service.setTrainingForAgent(3, 40, 10));
    }

    @Test
    void setTraining_blankResponseIsAFailureCarryingLastError() {
        service.trainingResponse = "";
        assertThatExceptionOfType(ChppWriteService.ChppWriteException.class)
                .isThrownBy(() -> service.setTrainingForAgent(3, 40, 10));
    }

    @Test
    void setTraining_rejectsOutOfRangeBeforeAnyFetch() {
        assertThatExceptionOfType(ChppWriteService.ChppWriteException.class)
                .isThrownBy(() -> service.setTrainingForAgent(3, 200, 10));
        assertThatExceptionOfType(ChppWriteService.ChppWriteException.class)
                .isThrownBy(() -> service.setTrainingForAgent(3, 40, 1));
        assertThat(service.trainingFetches).isZero();
    }

    @Test
    void cap_refusesThe21stWriteAndNamesTheOptionsPanel() throws Exception {
        core.model.UserParameter.instance().mcpChppWritesPerSession = 20;
        for (int i = 0; i < 20; i++) {
            service.setTrainingForAgent(3, 40, 10);
        }
        assertThatExceptionOfType(ChppWriteService.ChppWriteException.class)
                .isThrownBy(() -> service.setTrainingForAgent(3, 40, 10))
                .withMessageContaining("20")
                .withMessageContaining("MCP server");
    }
}
