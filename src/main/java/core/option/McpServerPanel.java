package core.option;

import core.db.DBManager;
import core.gui.comp.panel.ImagePanel;
import core.mcp.McpHttpServer;
import core.model.TranslationFacility;
import core.model.UserParameter;
import core.net.TransferSearchService;
import core.util.HODateTime;
import core.util.HOLogger;

import javax.swing.*;
import java.awt.*;

/**
 * The MCP server switch, and the resolve button the design deliberately keeps in HO's own UI rather
 * than exposing as a tool: it starts a batch of CHPP calls, so it stays a human action.
 */
public final class McpServerPanel extends ImagePanel {

    private final JCheckBox enabled = new JCheckBox(TranslationFacility.tr("ls.options.mcp.enabled"));
    private final JTextField port = new JTextField(6);
    private final JTextField searchCap = new JTextField(6);
    private final JTextField resolveCap = new JTextField(6);
    private final JTextField writeCap = new JTextField(6);
    private final JLabel status = new JLabel();
    private final JTextField url = new JTextField(34);

    public McpServerPanel() {
        initComponents();
    }

    private void initComponents() {
        setLayout(new GridBagLayout());
        final var c = new GridBagConstraints();
        c.anchor = GridBagConstraints.NORTHWEST;
        c.insets = new Insets(4, 6, 4, 6);
        c.gridx = 0;
        c.gridy = 0;
        c.gridwidth = 2;

        enabled.setOpaque(false);
        enabled.setToolTipText(TranslationFacility.tr("ls.options.mcp.enabled.tt"));
        enabled.setSelected(UserParameter.instance().mcpServerEnabled);
        enabled.addItemListener(e -> toggleServer());
        add(enabled, c);

        c.gridwidth = 1;
        addField(c, ++c.gridy, "ls.options.mcp.port", port,
                String.valueOf(UserParameter.instance().mcpServerPort));
        addField(c, ++c.gridy, "ls.options.mcp.searchcap", searchCap,
                String.valueOf(UserParameter.instance().mcpSearchesPerSession));
        addField(c, ++c.gridy, "ls.options.mcp.writecap", writeCap,
                String.valueOf(UserParameter.instance().mcpChppWritesPerSession));

        c.gridx = 0;
        c.gridy++;
        add(status, c);

        c.gridy++;
        final var resolve = new JButton(TranslationFacility.tr("ls.options.mcp.resolve"));
        resolve.setToolTipText(TranslationFacility.tr("ls.options.mcp.resolve.tt"));
        resolve.addActionListener(e -> resolveExpiredListings());
        add(resolve, c);
        c.gridx = 1;
        addField(c, c.gridy, "ls.options.mcp.resolve.cap", resolveCap, "20");

        c.gridx = 0;
        c.gridy++;
        add(new JLabel(TranslationFacility.tr("ls.options.mcp.url")), c);
        c.gridx = 1;
        url.setEditable(false);
        add(url, c);

        c.gridx = 0;
        c.gridy++;
        c.weightx = 1;
        c.weighty = 1;
        add(new JLabel(""), c);

        refreshStatus();
    }

    private void addField(GridBagConstraints c, int row, String labelKey, JTextField field, String value) {
        c.gridx = 0;
        c.gridy = row;
        add(new JLabel(TranslationFacility.tr(labelKey)), c);
        c.gridx = 1;
        field.setText(value);
        field.addActionListener(e -> saveFields());
        field.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override
            public void focusLost(java.awt.event.FocusEvent e) {
                saveFields();
            }
        });
        add(field, c);
        c.gridx = 0;
    }

    /**
     * Written to both parameter objects on purpose. {@link UserParameter#temp()} returns a separate
     * copy that the options dialog persists on OK; {@link UserParameter#instance()} is what the
     * running server and {@link TransferSearchService} actually read. This panel takes effect the
     * moment a field is edited - the server starts on the checkbox, not on OK - so writing only
     * temp() would leave the cap inert until the dialog was committed.
     */
    private void saveFields() {
        final var portValue = parse(port, 8765);
        final var capValue = parse(searchCap, 20);
        final var writeCapValue = parse(writeCap, 20);
        for (final var p : new UserParameter[]{UserParameter.instance(), UserParameter.temp()}) {
            p.mcpServerPort = portValue;
            p.mcpSearchesPerSession = capValue;
            p.mcpChppWritesPerSession = writeCapValue;
        }
    }

    /** See {@link #saveFields()} - the enable flag has to reach both objects too. */
    private static void storeEnabled(boolean value) {
        UserParameter.instance().mcpServerEnabled = value;
        UserParameter.temp().mcpServerEnabled = value;
    }

    private static int parse(JTextField field, int defaultValue) {
        try {
            final var value = Integer.parseInt(field.getText().trim());
            return value > 0 ? value : defaultValue;
        } catch (NumberFormatException e) {
            field.setText(String.valueOf(defaultValue));
            return defaultValue;
        }
    }

    private void toggleServer() {
        saveFields();
        storeEnabled(enabled.isSelected());
        if (enabled.isSelected()) {
            try {
                McpHttpServer.instance().registerAllTools();
                McpHttpServer.instance().start(UserParameter.instance().mcpServerPort);
            } catch (Exception e) {
                // A port already in use is reported here, not swallowed.
                HOLogger.instance().error(getClass(), e);
                status.setText(TranslationFacility.tr("ls.options.mcp.status.failed")
                        .replace("%error%", String.valueOf(e.getMessage())));
                enabled.setSelected(false);
                storeEnabled(false);
                return;
            }
        } else {
            McpHttpServer.instance().stop();
        }
        refreshStatus();
    }

    private void refreshStatus() {
        final var running = McpHttpServer.instance().isRunning();
        status.setText(TranslationFacility.tr(running
                ? "ls.options.mcp.status.running" : "ls.options.mcp.status.stopped"));
        url.setText(running ? "http://127.0.0.1:" + McpHttpServer.instance().port() + "/mcp" : "");
    }

    /**
     * Fill in what expired listings actually sold for.
     * <p>
     * One CHPP transfersPlayer call per listing, capped per run, only ever from this button - an
     * agent must not be able to start a batch of CHPP calls.
     */
    private void resolveExpiredListings() {
        final var cap = parse(resolveCap, 20);
        final var now = HODateTime.now();
        var resolved = 0;
        for (final var listing : DBManager.instance().loadUnresolvedTransferListings(cap)) {
            if (listing.getDeadline() == null || listing.getDeadline().compareTo(now) >= 0) {
                continue;                      // not expired yet, nothing to resolve
            }
            try {
                // module.transfer.XMLParser makes the transfersPlayer call itself
                final var transfers = module.transfer.XMLParser.getAllPlayerTransfers(listing.getPlayerId());
                if (transfers.isEmpty()) {
                    continue;
                }
                final var sale = transfers.get(transfers.size() - 1);   // the most recent one
                DBManager.instance().storeTransferListingSalePrice(listing.getPlayerId(),
                        listing.getObservedAt(), sale.getPrice().getSwedishKrona().longValue());
                resolved++;
            } catch (Exception e) {
                HOLogger.instance().error(getClass(), e);
            }
        }
        status.setText(TranslationFacility.tr("ls.options.mcp.resolve.done")
                .replace("%count%", String.valueOf(resolved)));
    }
}
