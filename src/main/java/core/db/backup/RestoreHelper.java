package core.db.backup;

import core.util.HODateTime;
import core.util.HOLogger;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.sql.DriverManager;
import java.util.Comparator;
import java.util.Properties;
import org.hsqldb.lib.tar.TarReader;

/**
 * Restores a {@code database_backup} archive into the DB folder at startup, before anything opens
 * the database.
 * <p>
 * The swap happens here rather than inside the running HO because nearly everything caches what
 * it loaded from the database - HOVerwaltung's model, UserParameter, TrainingManager, the table
 * objects - and none of it can be told to start over. The MCP tool only records the request in
 * {@link #PENDING} and exits; Docker's restart policy brings HO back, and this runs first.
 * <p>
 * The current files are moved aside, not deleted, until the restored database has been opened and
 * read. If that fails they are moved back. Either way the outcome lands in {@link #RESULT}, which
 * {@code database_backup list} reports as lastRestore.
 */
public final class RestoreHelper {

    public static final String PENDING = "restore-pending.properties";
    public static final String RESULT = "last-restore.properties";
    private static final String ROLLBACK = "restore-rollback";
    private static final String ARCHIVE = "archive";
    private static final String PRE_RESTORE_BACKUP = "preRestoreBackup";
    private static final String STATUS = "status";

    private RestoreHelper() {
    }

    /** Record a restore for the next start. */
    public static void schedule(File dbFolder, File archive, File preRestoreBackup) throws IOException {
        final var props = new Properties();
        props.setProperty(ARCHIVE, archive.getAbsolutePath());
        props.setProperty(PRE_RESTORE_BACKUP, preRestoreBackup.getAbsolutePath());
        props.setProperty("requestedAt", HODateTime.now().toHT());
        try (var out = new FileWriter(new File(dbFolder, PENDING))) {
            props.store(out, "database_backup restore, applied on the next start");
        }
    }

    /** Apply a restore scheduled by {@link #schedule}, if there is one. Never throws. */
    public static void applyPending(File dbFolder) {
        final var pending = new File(dbFolder, PENDING);
        if (!pending.isFile()) {
            return;
        }
        final var request = new Properties();
        try (var in = new FileReader(pending)) {
            request.load(in);
        } catch (IOException e) {
            HOLogger.instance().error(RestoreHelper.class, "Unreadable " + pending + ": " + e);
        }
        // Gone before the swap starts: a restore that kills the JVM must not repeat on every start.
        deleteQuietly(pending);

        final var start = System.nanoTime();
        final var result = new Properties();
        result.setProperty(ARCHIVE, request.getProperty(ARCHIVE, ""));
        result.setProperty(PRE_RESTORE_BACKUP, request.getProperty(PRE_RESTORE_BACKUP, ""));
        final var rollback = new File(dbFolder, ROLLBACK);
        try {
            deleteRecursively(rollback);
            Files.createDirectories(rollback.toPath());
            for (final var f : databaseFiles(dbFolder)) {
                Files.move(f.toPath(), new File(rollback, f.getName()).toPath());
            }
            try {
                new TarReader(new File(request.getProperty(ARCHIVE)), TarReader.EXTRACT_MODE,
                        null, null, dbFolder).read();
                result.setProperty("latestSnapshot", latestSnapshot(dbFolder));
                result.setProperty(STATUS, "restored");
                deleteRecursively(rollback);
            } catch (Exception e) {
                for (final var f : databaseFiles(dbFolder)) {
                    deleteRecursively(f);
                }
                for (final var f : databaseFiles(rollback)) {
                    Files.move(f.toPath(), new File(dbFolder, f.getName()).toPath(),
                            StandardCopyOption.REPLACE_EXISTING);
                }
                deleteRecursively(rollback);
                result.setProperty(STATUS, "rolledBack");
                result.setProperty("error", String.valueOf(e));
            }
        } catch (Exception e) {
            // Could not even move the current files aside: whatever is left in rollback is theirs.
            result.setProperty(STATUS, "failed");
            result.setProperty("error", e + " - the previous files, if any were moved, are in "
                    + rollback);
        }
        final long runtimeMs = (System.nanoTime() - start) / 1_000_000;
        result.setProperty("runtimeSeconds", String.valueOf(runtimeMs / 1000.0));
        result.setProperty("finishedAt", HODateTime.now().toHT());
        try (var out = new FileWriter(new File(dbFolder, RESULT))) {
            result.store(out, "last database_backup restore");
        } catch (IOException e) {
            HOLogger.instance().error(RestoreHelper.class, "Cannot write " + RESULT + ": " + e);
        }
        HOLogger.instance().info(RestoreHelper.class, "Restore: " + result);
    }

    /** The last restore's outcome, or null if there never was one. */
    public static Properties lastResult(File dbFolder) {
        final var file = new File(dbFolder, RESULT);
        if (!file.isFile()) {
            return null;
        }
        final var props = new Properties();
        try (var in = new FileReader(file)) {
            props.load(in);
            return props;
        } catch (IOException e) {
            return null;
        }
    }

    /** Opens the restored database and reads the newest snapshot date - proof it is usable. */
    private static String latestSnapshot(File dbFolder) throws Exception {
        final var url = "jdbc:hsqldb:file:" + dbFolder.getAbsolutePath() + "/database;ifexists=true";
        try (var c = DriverManager.getConnection(url, "sa", "");
             var s = c.createStatement()) {
            String latest;
            try (var rs = s.executeQuery("SELECT MAX(DATUM) FROM HRF")) {
                rs.next();
                final var ts = rs.getTimestamp(1);
                latest = ts == null ? "none" : HODateTime.fromDbTimestamp(ts).toHT();
            }
            s.execute("SHUTDOWN");
            return latest;
        }
    }

    /** HSQLDB's own files: database.script, .data, .properties, .log, .lck, .tmp and the like. */
    private static File[] databaseFiles(File dir) {
        final var files = dir.listFiles(f -> f.getName().startsWith("database."));
        return files == null ? new File[0] : files;
    }

    private static void deleteRecursively(File f) throws IOException {
        if (!f.exists()) {
            return;
        }
        try (var walk = Files.walk(f.toPath())) {
            for (final var p : walk.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(p);
            }
        }
    }

    private static void deleteQuietly(File f) {
        try {
            Files.deleteIfExists(f.toPath());
        } catch (IOException e) {
            HOLogger.instance().error(RestoreHelper.class, "Cannot delete " + f + ": " + e);
        }
    }
}
