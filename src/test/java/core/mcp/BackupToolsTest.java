package core.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.File;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BackupToolsTest {

    @TempDir
    File root;

    @Test
    void restoreTakesOnlyArchivesListCouldHaveReturned() throws Exception {
        final var backups = new File(root, "backups");
        Files.createDirectories(backups.toPath());
        Files.writeString(new File(backups, "database-1.tar.gz").toPath(), "x");
        Files.createDirectories(new File(backups, BackupTools.PRE_RESTORE).toPath());
        Files.writeString(new File(backups, "pre-restore/database-2.tar.gz").toPath(), "x");
        Files.writeString(new File(backups, "notes.txt").toPath(), "x");
        Files.writeString(new File(backups.getParentFile(), "outside.tar.gz").toPath(), "x");

        assertThat(BackupTools.resolveArchive(backups, "database-1.tar.gz")).isFile();
        assertThat(BackupTools.resolveArchive(backups, "pre-restore/database-2.tar.gz")).isFile();
        for (final var bad : new String[]{"../outside.tar.gz", "missing.tar.gz", "notes.txt",
                "/etc/passwd", "pre-restore"}) {
            assertThatThrownBy(() -> BackupTools.resolveArchive(backups, bad))
                    .as(bad).isInstanceOf(McpToolException.class);
        }
    }

    @Test
    void oneToolWithAnActionAndADescriptionThatNamesTheRestart() {
        final var tools = BackupTools.tools();
        assertThat(tools).extracting(McpTool::name).containsExactly("database_backup");
        assertThat(tools.get(0).inputSchema().getAsJsonArray("required"))
                .extracting(com.google.gson.JsonElement::getAsString).containsExactly("action");
        assertThat(tools.get(0).description()).contains("confirm").contains("restarts");
    }
}
