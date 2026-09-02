package core.mcp;

import com.google.gson.JsonObject;
import core.net.ChppWriteService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * Like the other tool tests: the bodies need a loaded HOModel and a database, so what is asserted
 * here is the surface the client sees. The set_training body is exercised through a stubbed
 * {@link ChppWriteService}.
 */
class TrainingToolsTest {

    private ChppWriteService original;

    @AfterEach
    void restore() {
        if (original != null) {
            ChppWriteService.setInstanceForTest(original);
        }
    }

    private void installService(ChppWriteService stub) {
        original = ChppWriteService.instance();
        ChppWriteService.setInstanceForTest(stub);
    }

    private static JsonObject args(int type, int intensity, int stamina) {
        final var a = new JsonObject();
        a.addProperty("trainingType", type);
        a.addProperty("intensity", intensity);
        a.addProperty("staminaShare", stamina);
        return a;
    }

    private static McpTool tool(String name) {
        return TrainingTools.tools().stream()
                .filter(x -> x.name().equals(name)).findFirst().orElseThrow();
    }

    @Test
    void tools_areTheTrainingTools() {
        assertThat(TrainingTools.tools()).extracting(McpTool::name)
                .containsExactly("get_training", "set_training");
    }

    @Test
    void getTraining_takesNoRequiredArguments() {
        final var getTraining = TrainingTools.tools().get(0);

        assertThat(getTraining.inputSchema().getAsJsonArray("required")).isEmpty();
        final var properties = getTraining.inputSchema().getAsJsonObject("properties");
        assertThat(properties.has("weeks")).isTrue();
        assertThat(properties.has("playerIds")).isTrue();
        assertThat(getTraining.description()).isNotBlank();
    }

    @Test
    void setTraining_rejectsIntensityOutOfRangeWithoutCallingTheService() {
        installService(new ChppWriteService() {
            @Override
            public TrainingCommitted setTrainingForAgent(int t, int i, int s) {
                throw new AssertionError("service must not be called");
            }
        });
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> tool("set_training").handler().call(args(3, 150, 10)))
                .withMessageContaining("0..100");
    }

    @Test
    void setTraining_echoesTheCommittedValues() throws Exception {
        installService(new ChppWriteService() {
            @Override
            public TrainingCommitted setTrainingForAgent(int t, int i, int s) {
                return new TrainingCommitted(t, i, s);
            }
        });
        final var result = tool("set_training").handler().call(args(3, 40, 10));
        assertThat(result.get("status").getAsString()).isEqualTo("ok");
        assertThat(result.getAsJsonObject("committed").get("intensity").getAsInt()).isEqualTo(40);
        assertThat(result.get("note").getAsString()).contains("not refreshed");
    }
}
