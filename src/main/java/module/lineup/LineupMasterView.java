package module.lineup;

import core.gui.RefreshManager;
import core.gui.Refreshable;
import core.model.HOVerwaltung;
import core.model.TranslationFacility;
import module.lineup.penalties.PenaltyTakersView;
import module.lineup.substitution.SubstitutionOverview;

import javax.swing.*;
import java.awt.*;

/**
 * Top-Level Container for the Lineups (contains a tab for the lineup, a tab for
 * the match orders...)
 *
 * @author kruescho
 *
 */
public class LineupMasterView extends JPanel {

	private JTabbedPane tabbedPane;
	private LineupPanel lineupPanel;
	private SubstitutionOverview substitutionOverview;
	private PenaltyTakersView penaltyTakersView;

	public LineupMasterView() {
		initComponents();
		addListeners();
	}

	public LineupPanel getLineupPanel() {
		return this.lineupPanel;
	}

	private void initComponents() {
		this.tabbedPane = new JTabbedPane();
		HOVerwaltung hov = HOVerwaltung.instance();

		this.lineupPanel = new LineupPanel();
		this.tabbedPane.addTab(TranslationFacility.tr("Aufstellung"), this.lineupPanel);

		this.substitutionOverview = new SubstitutionOverview(hov.getModel().getCurrentLineup());
		this.tabbedPane.addTab(TranslationFacility.tr("subs.Title"), this.substitutionOverview);

		this.penaltyTakersView = new PenaltyTakersView();
		this.penaltyTakersView.setPlayers(hov.getModel().getCurrentPlayers());
		this.penaltyTakersView.setLineup(hov.getModel().getCurrentLineup());
		this.tabbedPane.addTab(TranslationFacility.tr("lineup.penaltytakers.tab.title"), this.penaltyTakersView);

		setLayout(new BorderLayout());
		add(this.tabbedPane, BorderLayout.CENTER);
	}

	private void addListeners() {
		this.lineupPanel.addUpdatable(this::refreshView);

		RefreshManager.instance().registerRefreshable(new Refreshable() {

			@Override
			public void refresh() {
				refreshView();

			}

			@Override
			public void reInit() {
				refreshView();
			}
		});
	}

	private void refreshView() {
		this.substitutionOverview.setLineup(HOVerwaltung.instance().getModel().getCurrentLineup());
		this.penaltyTakersView.setPlayers(HOVerwaltung.instance().getModel().getCurrentPlayers());
		this.penaltyTakersView.setLineup(HOVerwaltung.instance().getModel().getCurrentLineup());
	}

    public void storeUserSettings() {
        this.lineupPanel.storeUserSettings();
    }
}
