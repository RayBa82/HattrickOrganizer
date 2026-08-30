package core.mcp;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Like the other tool tests: the bodies need a loaded HOModel and a database, so what is asserted
 * here is the surface the client sees.
 */
class TrainingToolsTest {

    @Test
    void tools_isTheSingleTrainingTool() {
        assertThat(TrainingTools.tools()).extracting(McpTool::name)
                .containsExactly("get_training");
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
}
