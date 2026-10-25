package core.mcp;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class McpDownloadsTest {

    @Test
    void bulk_putsTheWholeAnswerIntoTheFileAndKeepsAPreview() {
        final var rows = new JsonArray();
        for (int i = 1; i <= 25; i++) {
            final var row = new JsonObject();
            row.addProperty("playerId", i);
            rows.add(row);
        }
        final var full = new JsonObject();
        full.addProperty("count", 25);
        full.add("players", rows);

        final var answer = McpDownloads.bulk(full, "players");

        assertThat(answer.has("players")).isFalse();
        assertThat(answer.get("count").getAsInt()).isEqualTo(25);
        assertThat(answer.getAsJsonArray("preview")).hasSize(McpDownloads.PREVIEW);
        final var file = JsonParser.parseString(McpDownloads.get(answer.get("download").getAsString()))
                .getAsJsonObject();
        assertThat(file.get("count").getAsInt()).isEqualTo(25);
        assertThat(file.getAsJsonArray("players")).hasSize(25);
        assertThat(file.has("download")).isFalse();
    }

    @Test
    void bulk_leavesFileOnlyPartsOutOfHeaderAndPreviewButNotTheFile() {
        final var row = new JsonObject();
        row.addProperty("playerId", 1);
        row.add("snapshots", new JsonArray());
        final var rows = new JsonArray();
        rows.add(row);
        final var full = new JsonObject();
        full.add("trainingWeeks", new JsonArray());
        full.add("players", rows);

        final var answer = McpDownloads.bulk(full, "players", "trainingWeeks", "snapshots");

        assertThat(answer.has("trainingWeeks")).isFalse();
        assertThat(answer.getAsJsonArray("preview").get(0).getAsJsonObject().has("snapshots")).isFalse();
        final var file = JsonParser.parseString(McpDownloads.get(answer.get("download").getAsString()))
                .getAsJsonObject();
        assertThat(file.has("trainingWeeks")).isTrue();
        assertThat(file.getAsJsonArray("players").get(0).getAsJsonObject().has("snapshots")).isTrue();
    }

    @Test
    void bulkSections_cutsEachSectionsListAndSkipsAMissingSection() {
        final var matches = new JsonArray();
        for (int i = 1; i <= 8; i++) {
            matches.add(i);
        }
        final var ratings = new JsonObject();
        ratings.addProperty("matchesUsed", 8);
        ratings.add("matches", matches);
        final var full = new JsonObject();
        full.add("ratings", ratings);

        final var answer = McpDownloads.bulkSections(full, "ratings.matches", "squad.players");

        final var cut = answer.getAsJsonObject("ratings");
        assertThat(cut.has("matches")).isFalse();
        assertThat(cut.get("matchesUsed").getAsInt()).isEqualTo(8);
        assertThat(cut.getAsJsonArray("preview")).hasSize(McpDownloads.PREVIEW);
        assertThat(answer.has("squad")).isFalse();
        final var file = JsonParser.parseString(McpDownloads.get(answer.get("download").getAsString()))
                .getAsJsonObject();
        assertThat(file.getAsJsonObject("ratings").getAsJsonArray("matches")).hasSize(8);
    }
}
