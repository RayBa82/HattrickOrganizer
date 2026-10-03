package core.db.backup;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.nio.file.Files;
import java.sql.DriverManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** A real HSQLDB database in a temp folder: backed up, changed, then restored or rolled back. */
class RestoreHelperTest {

    @TempDir
    File dbFolder;

    private String url() {
        return "jdbc:hsqldb:file:" + dbFolder.getAbsolutePath() + "/database";
    }

    /** One snapshot, an online backup of it, then a second snapshot after the backup. */
    private File databaseWithABackupOfOneSnapshot() throws Exception {
        final var backups = new File(dbFolder, "backups");
        Files.createDirectories(backups.toPath());
        try (var c = DriverManager.getConnection(url(), "sa", ""); var s = c.createStatement()) {
            s.execute("CREATE CACHED TABLE HRF(HRF_ID INT, DATUM TIMESTAMP)");
            s.execute("INSERT INTO HRF VALUES (1, TIMESTAMP '2026-09-01 12:00:00')");
            s.execute("BACKUP DATABASE TO '" + backups.getAbsolutePath() + "/' BLOCKING");
            s.execute("INSERT INTO HRF VALUES (2, TIMESTAMP '2026-09-20 12:00:00')");
            s.execute("SHUTDOWN");
        }
        return backups.listFiles()[0];
    }

    private int snapshots() throws Exception {
        try (var c = DriverManager.getConnection(url() + ";ifexists=true", "sa", "");
             var s = c.createStatement();
             var rs = s.executeQuery("SELECT COUNT(*) FROM HRF")) {
            rs.next();
            final var n = rs.getInt(1);
            s.execute("SHUTDOWN");
            return n;
        }
    }

    @Test
    void aScheduledRestoreReplacesTheDatabaseOnTheNextStart() throws Exception {
        final var archive = databaseWithABackupOfOneSnapshot();
        assertThat(snapshots()).isEqualTo(2);

        RestoreHelper.schedule(dbFolder, archive, archive);
        RestoreHelper.applyPending(dbFolder);

        assertThat(snapshots()).isEqualTo(1);
        final var result = RestoreHelper.lastResult(dbFolder);
        assertThat(result.getProperty("status")).isEqualTo("restored");
        assertThat(result.getProperty("latestSnapshot")).isNotEqualTo("none");
        assertThat(new File(dbFolder, RestoreHelper.PENDING)).doesNotExist();
        assertThat(new File(dbFolder, "restore-rollback")).doesNotExist();
    }

    @Test
    void aBrokenArchiveRollsBackToTheDatabaseThatWasThere() throws Exception {
        databaseWithABackupOfOneSnapshot();
        final var broken = new File(dbFolder, "backups/broken.tar.gz");
        Files.writeString(broken.toPath(), "not a tar archive");

        RestoreHelper.schedule(dbFolder, broken, broken);
        RestoreHelper.applyPending(dbFolder);

        assertThat(snapshots()).isEqualTo(2);
        assertThat(RestoreHelper.lastResult(dbFolder).getProperty("status")).isEqualTo("rolledBack");
        assertThat(new File(dbFolder, RestoreHelper.PENDING)).doesNotExist();
        assertThat(new File(dbFolder, "restore-rollback")).doesNotExist();
    }

    @Test
    void withNothingScheduledTheStartIsUntouched() throws Exception {
        databaseWithABackupOfOneSnapshot();

        RestoreHelper.applyPending(dbFolder);

        assertThat(snapshots()).isEqualTo(2);
        assertThat(RestoreHelper.lastResult(dbFolder)).isNull();
    }
}
