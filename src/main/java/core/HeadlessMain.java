package core;

import core.db.DBManager;
import core.db.backup.BackupHelper;
import core.db.backup.RestoreHelper;
import core.db.user.UserManager;
import core.mcp.McpHttpServer;
import core.model.HOVerwaltung;
import core.model.TranslationFacility;
import core.model.UserParameter;
import core.net.Connector;
import core.net.OnlineWorker;
import core.training.TrainingManager;
import core.util.HOLogger;

import java.io.BufferedReader;
import java.util.Arrays;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * HO without a window: loads the database and serves only the MCP tools, for a server with no
 * display. Run from a createDistribution directory:
 * <pre>
 *   java -Djava.awt.headless=true -cp HO.jar core.HeadlessMain authorize   # once, interactive
 *   java -Djava.awt.headless=true -cp HO.jar core.HeadlessMain [--port N] [--user N] [INSTALLED] [DEBUG]
 *   java -Djava.awt.headless=true -cp HO.jar core.HeadlessMain delete-hrf [hrfId]   # server stopped
 * </pre>
 * {@code delete-hrf} without an id lists the newest snapshots; with one it deletes that snapshot
 * the way the desktop DB cleanup tool does (after the usual startup backup) - for a download that
 * went wrong, e.g. one written under the wrong time zone.
 * {@code authorize} runs CHPP's PIN flow on the console and the first team download, which
 * {@code download_team} refuses to do on an empty database. The server binds to loopback only
 * and has no authentication - reach it through an SSH tunnel. HSQLDB is single-process, so desktop
 * HO must not open the same database at the same time.
 */
public final class HeadlessMain {

    private HeadlessMain() {
    }

    public static void main(String[] args) throws Exception {
        System.setProperty("java.awt.headless", "true");
        HO.initRuntime(args);

        boolean authorize = false;
        Integer deleteHrf = null;
        int port = -1;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "authorize" -> authorize = true;
                case "delete-hrf" -> deleteHrf = i + 1 < args.length && args[i + 1].matches("\\d+")
                        ? Integer.parseInt(args[++i]) : 0;
                case "--port" -> port = Integer.parseInt(args[++i]);
                case "--user" -> UserManager.instance().setINDEX(Integer.parseInt(args[++i]));
                default -> {
                    // HO's own flags (INSTALLED, DEBUG, ...), handled by initRuntime
                }
            }
        }

        // HO.main's startup, minus everything that needs a screen. A restore scheduled by
        // database_backup goes first, before anything opens the database.
        final var dbFolder = new File(UserManager.instance().getCurrentUser().getDbFolder());
        RestoreHelper.applyPending(dbFolder);
        BackupHelper.backup(dbFolder);
        DBManager.instance().loadUserParameter();
        HOVerwaltung.checkLanguageFile(UserParameter.instance().sprachDatei);
        TranslationFacility.setLanguage(UserParameter.instance().sprachDatei);
        HOVerwaltung.instance().loadLatestHoModel();
        if (!DBManager.instance().isFirstStart()) {
            DBManager.instance().updateConfig();
        }
        TrainingManager.instance();
        // No one can answer a dialog: CHPP failures become tool errors instead.
        Connector.instance().setSilentDownload(true);

        if (deleteHrf != null) {
            deleteHrf(deleteHrf);
            DBManager.instance().disconnect();
            return;
        }

        if (authorize) {
            final var in = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
            Connector.instance().authorizeOnConsole(in, System.out);
            System.out.println("Authorized. Downloading team data...");
            final var download = OnlineWorker.downloadTeamData();
            HOVerwaltung.instance().getModel().calcSubskills();
            System.out.println("Stored " + download.model().getCurrentPlayers().size()
                    + " players. Start without 'authorize' to serve MCP; download_team fetches the rest.");
            DBManager.instance().disconnect();
            return;
        }

        final var server = McpHttpServer.instance();
        server.registerAllTools();
        server.start(port > 0 ? port : UserParameter.instance().mcpServerPort);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            server.stop();
            DBManager.instance().saveUserParameter();
            DBManager.instance().disconnect();
            HOLogger.instance().info(HeadlessMain.class, "Headless HO stopped");
        }));
        // The server's own threads keep the JVM alive until SIGTERM/Ctrl-C.
    }

    /** 0 lists the newest snapshots, anything else deletes that one. */
    private static void deleteHrf(int hrfId) {
        final var hrfs = DBManager.instance().loadAllHRFs(false);
        if (hrfId == 0) {
            System.out.println("Newest snapshots (hrfId  downloaded at):");
            Arrays.stream(hrfs).limit(10).forEach(h ->
                    System.out.println("  " + h.getHrfId() + "  " + h.getDatum().toLocaleDateTime()));
            System.out.println("Delete one with: delete-hrf <hrfId>");
            return;
        }
        final var hrf = Arrays.stream(hrfs).filter(h -> h.getHrfId() == hrfId).findFirst();
        if (hrf.isEmpty()) {
            System.out.println("No snapshot with hrfId " + hrfId);
            return;
        }
        DBManager.instance().deleteHRF(hrfId);
        System.out.println("Deleted snapshot " + hrfId + " from " + hrf.get().getDatum().toLocaleDateTime());
    }
}
