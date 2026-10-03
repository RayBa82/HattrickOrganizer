package core.mcp;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import core.db.DBManager;
import core.db.backup.RestoreHelper;
import core.db.user.UserManager;
import core.util.HODateTime;
import core.util.HOLogger;
import java.awt.GraphicsEnvironment;
import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * database_backup: create, list and restore archives of HO's whole database, over MCP.
 * <p>
 * Archives are HSQLDB's own online backups ({@code BACKUP DATABASE ... BLOCKING}), consistent
 * while HO keeps running. They live in {@code <db folder>/backups} - on the Docker data volume -
 * and every restore first backs up the current database into {@code backups/pre-restore}, a
 * separate folder with its own rotation, so a pre-restore backup can never push the archive being
 * restored out of the newest {@link #KEPT}. The restore itself runs at the next start, see
 * {@link RestoreHelper}.
 */
final class BackupTools {

    /** Archives kept per folder; older ones are deleted after each new one. */
    static final int KEPT = 10;
    static final String PRE_RESTORE = "pre-restore";
    private static final String ACTION = "action";
    private static final String FILE = "file";

    private BackupTools() {
    }

    static List<McpTool> tools() {
        return List.of(new McpTool("database_backup",
                "Backs up or restores HO's whole database (snapshots, matches, transfers, settings, "
                        + "CHPP login). action create: new archive while HO runs. list: archives to "
                        + "pick from, plus lastRestore. restore: file from list plus confirm true - backs "
                        + "up the current DB first, then HO restarts (~30s) and swaps; without confirm "
                        + "it only says what it would do.",
                McpTool.schema(McpTool.properties(
                        ACTION, McpTool.prop("string", "create, list or restore"),
                        FILE, McpTool.prop("string", "restore: an archive name exactly as list returns it"),
                        "confirm", McpTool.prop("boolean", "restore: true to really restore")),
                        ACTION),
                BackupTools::databaseBackup));
    }

    private static JsonObject databaseBackup(JsonObject args) throws McpToolException {
        final var action = McpJson.reqString(args, ACTION);
        return switch (action) {
            case "create" -> create();
            case "list" -> list();
            case "restore" -> restore(McpJson.reqString(args, FILE), McpJson.optBool(args, "confirm", false));
            default -> throw new McpToolException("Unknown action " + action
                    + ". Use create, list or restore.");
        };
    }

    private static File dbFolder() {
        return new File(UserManager.instance().getCurrentUser().getDbFolder());
    }

    private static File backupsDir() {
        return new File(dbFolder(), "backups");
    }

    private static JsonObject create() throws McpToolException {
        final var start = System.nanoTime();
        final var archive = backupInto(backupsDir(), null);
        final var json = describe(archive);
        json.addProperty("runtimeSeconds", (System.nanoTime() - start) / 1_000_000 / 1000.0);
        return json;
    }

    /**
     * HSQLDB's online backup into {@code dir}, then that folder's rotation - sparing {@code keep},
     * the archive a restore is about to use.
     */
    private static File backupInto(File dir, File keep) throws McpToolException {
        if (!dir.isDirectory() && !dir.mkdirs()) {
            throw new McpToolException("Cannot create the backup folder " + dir);
        }
        try {
            Objects.requireNonNull(DBManager.instance().getConnectionManager()).executeUpdate(
                    "BACKUP DATABASE TO '" + dir.getAbsolutePath().replace("'", "''")
                            + File.separator + "' BLOCKING");
        } catch (Exception e) {
            throw new McpToolException("The backup failed: " + e.getMessage());
        }
        final var archives = archives(dir);
        if (archives.isEmpty()) {
            throw new McpToolException("HSQLDB reported no error, but no archive is in " + dir);
        }
        // By name: keep is canonical, the listing may not be (symlinked paths).
        archives.stream().skip(KEPT).filter(f -> keep == null || !f.getName().equals(keep.getName())).forEach(f -> {
            if (!f.delete()) {
                HOLogger.instance().warning(BackupTools.class, "Could not delete old backup " + f);
            }
        });
        return archives.get(0);
    }

    /** A folder's archives, newest first. */
    private static List<File> archives(File dir) {
        final var found = dir.listFiles(f -> f.isFile() && f.getName().endsWith(".tar.gz"));
        return found == null ? List.of() : Arrays.stream(found)
                .sorted(Comparator.comparingLong(File::lastModified).reversed())
                .toList();
    }

    private static JsonObject describe(File archive) {
        final var json = new JsonObject();
        json.addProperty(FILE, name(archive));
        json.addProperty("sizeBytes", archive.length());
        json.addProperty("createdAt", new HODateTime(Instant.ofEpochMilli(archive.lastModified())).toHT());
        return json;
    }

    /** The archive's name relative to the backups folder - what list shows and restore takes. */
    private static String name(File archive) {
        return archive.getParentFile().getName().equals(PRE_RESTORE)
                ? PRE_RESTORE + "/" + archive.getName() : archive.getName();
    }

    private static JsonObject list() {
        final var backups = new JsonArray();
        archives(backupsDir()).forEach(f -> backups.add(describe(f)));
        archives(new File(backupsDir(), PRE_RESTORE)).forEach(f -> backups.add(describe(f)));
        final var json = new JsonObject();
        json.add("backups", backups);
        json.addProperty("count", backups.size());
        final var last = RestoreHelper.lastResult(dbFolder());
        if (last != null) {
            final var lastJson = new JsonObject();
            last.stringPropertyNames().stream().sorted()
                    .forEach(k -> lastJson.addProperty(k, last.getProperty(k)));
            json.add("lastRestore", lastJson);
        }
        return json;
    }

    /**
     * {@code name} as an archive inside the backups folder, or a refusal. Only names list returns
     * pass: a plain file name, or one under pre-restore/ - nothing that climbs out.
     */
    static File resolveArchive(File backupsDir, String name) throws McpToolException {
        try {
            final var base = backupsDir.getCanonicalFile();
            final var file = new File(base, name).getCanonicalFile();
            final var parent = file.getParentFile();
            final var inside = parent.equals(base) || parent.equals(new File(base, PRE_RESTORE));
            if (!inside || !file.getName().endsWith(".tar.gz") || !file.isFile()) {
                throw new McpToolException("No archive " + name + " in the backups folder. Use a "
                        + "file name exactly as database_backup list returns it.");
            }
            return file;
        } catch (IOException e) {
            throw new McpToolException("Cannot read the backups folder: " + e.getMessage());
        }
    }

    private static JsonObject restore(String name, boolean confirm) throws McpToolException {
        final var archive = resolveArchive(backupsDir(), name);
        // Only the headless server restarts itself (Docker's restart policy) and applies the
        // restore on start; desktop HO would just quit.
        if (!GraphicsEnvironment.isHeadless()) {
            throw new McpToolException("Restore runs only on the headless server. In desktop HO, "
                    + "close HO and extract the archive into the db folder by hand.");
        }
        final var restarts = new File("/.dockerenv").exists();
        final var json = new JsonObject();
        json.addProperty("restoring", name(archive));
        json.addProperty("restartsAutomatically", restarts);
        if (!confirm) {
            json.addProperty("dryRun", true);
            json.addProperty("wouldDo", "back up the current database into " + PRE_RESTORE
                    + "/, stop HO, replace the database with " + name(archive) + " on the next "
                    + "start, and roll back automatically if it cannot be opened. Call again with "
                    + "confirm true to do it.");
            return json;
        }
        final var preRestore = backupInto(new File(backupsDir(), PRE_RESTORE), archive);
        try {
            RestoreHelper.schedule(dbFolder(), archive, preRestore);
        } catch (IOException e) {
            throw new McpToolException("Could not schedule the restore: " + e.getMessage()
                    + ". Nothing was changed; the pre-restore backup is " + name(preRestore));
        }
        json.addProperty("preRestoreBackup", name(preRestore));
        json.addProperty("status", "restarting");
        json.addProperty("hint", restarts
                ? "HO restarts now and swaps the database on start (~30s). Then call "
                        + "database_backup list: lastRestore has status, latestSnapshot and runtime."
                : "HO stops now. Start it again to apply the restore, then call database_backup "
                        + "list for lastRestore.");
        // Let this answer reach the client first. The shutdown hook saves settings and closes
        // the database cleanly before the swap.
        final var exit = new Thread(() -> {
            try {
                Thread.sleep(1500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            HOLogger.instance().info(BackupTools.class, "Exiting for the scheduled restore");
            System.exit(0);
        }, "database-restore-exit");
        exit.setDaemon(true);
        exit.start();
        return json;
    }
}
