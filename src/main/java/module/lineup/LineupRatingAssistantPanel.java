package module.lineup;

import core.gui.RefreshManager;
import core.gui.Updatable;
import core.model.TranslationFacility;
import core.util.HOLogger;
import module.lineup.assistant.LineupAssistantPanel;
import module.lineup.lineup.MatchAndLineupSelectionPanel;
import module.lineup.lineup.MatchBanner;
import module.lineup.ratings.LineupRatingPanel;

import javax.swing.*;
import java.awt.*;


/**
 * Create the panel displaying ratings, settings and assistant
 */
public class LineupRatingAssistantPanel extends JPanel implements core.gui.Refreshable, Updatable {

    private final LineupPanel m_clLineupPanel;
    private LineupRatingPanel lineupRatingPanel;
    private LineupSettingsPanel lineupSettingsPanel;
    private LineupAssistantPanel lineupAssistantPanel;
    private MatchAndLineupSelectionPanel matchAndLineupPanel;
    @lombok.Getter
    private MatchBanner matchBanner;

    public final LineupRatingPanel getLineupRatingPanel() {
        if (lineupRatingPanel == null) {
            lineupRatingPanel = new LineupRatingPanel(this.m_clLineupPanel);
        }
        return lineupRatingPanel;
    }

    public final LineupSettingsPanel getLineupSettingsPanel() {
        if (lineupSettingsPanel == null) {
            lineupSettingsPanel = new LineupSettingsPanel(m_clLineupPanel);
        }
        return lineupSettingsPanel;
    }

    public final LineupAssistantPanel getLineupAssistantPanel() {
        if (lineupAssistantPanel == null) {
            lineupAssistantPanel = new LineupAssistantPanel();
        }
        return lineupAssistantPanel;
    }

    public MatchAndLineupSelectionPanel getMatchAndLineupSelectionPanel() {
        if ( matchAndLineupPanel == null){
            new MatchAndLineupSelectionPanel(m_clLineupPanel);
        }
        return matchAndLineupPanel;
    }

    public LineupRatingAssistantPanel(LineupPanel parent) {
        m_clLineupPanel = parent;
        initComponents();
        RefreshManager.instance().registerRefreshable(this);
    }

    private void initComponents() {

        var pane = new JPanel(new BorderLayout());

        var lineupRatingPanel = getLineupRatingPanel();
        var lineupSettingsPanel = getLineupSettingsPanel();
        var lineupAssistantPanel = getLineupAssistantPanel();

        LineupDatabasePanel lineupDatabasePanel = new LineupDatabasePanel(m_clLineupPanel);
        pane.add(lineupRatingPanel, BorderLayout.NORTH);

        var tabView = new JTabbedPane();
        tabView.addTab(TranslationFacility.tr("ls.module.lineup.assistant"), new JScrollPane(lineupAssistantPanel));
        tabView.addTab(TranslationFacility.tr("ls.module.lineup.lineup_simulator"), new JScrollPane(lineupSettingsPanel));
        tabView.addTab(TranslationFacility.tr("ls.menu.file.database"), new JScrollPane(lineupDatabasePanel));
        pane.add(tabView, BorderLayout.CENTER);

        // the match banner is shown over the pitch, see LineupPanel
        this.matchAndLineupPanel = new MatchAndLineupSelectionPanel(m_clLineupPanel);
        matchBanner = new MatchBanner(matchAndLineupPanel);
        pane.add(matchAndLineupPanel, BorderLayout.SOUTH);

        setLayout(new BorderLayout());
        add(new JScrollPane(pane), BorderLayout.CENTER);
    }

    public void refresh() {
        HOLogger.instance().log(getClass(), " refresh() has been called");
        lineupSettingsPanel.refresh(false);
        lineupRatingPanel.refresh();
        matchBanner.refresh();
        getMatchAndLineupSelectionPanel().refresh();
    }

    @Override
    public final void reInit() {
        refresh();
    }

    @Override
    public final void update() {
        m_clLineupPanel.update();
    }

}
