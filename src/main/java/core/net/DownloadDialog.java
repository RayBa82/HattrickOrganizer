package core.net;

import core.db.DBManager;
import core.db.user.UserManager;
import core.file.hrf.HRFStringParser;
import core.gui.HOMainFrame;
import core.gui.RefreshManager;
import core.gui.comp.CheckBoxTree.CheckBoxTree;
import core.gui.comp.panel.ImagePanel;
import core.model.HOModel;
import core.model.HOVerwaltung;
import core.model.TranslationFacility;
import core.model.UserParameter;
import core.model.enums.MatchType;
import core.model.match.MatchKurzInfo;
import core.net.login.ProxyDialog;
import core.util.HODateTime;
import core.util.HOLogger;
import core.util.Helper;
import module.nthrf.MainPanel;
import module.nthrf.NtTeamChooser;
import module.nthrf.NthrfUtil;
import tool.updater.UpdateController;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Map;

/**
 * User dialog to download different hattrick data
 */
public class DownloadDialog extends JDialog implements ActionListener {

	// ~ Instance fields--/
	private static final String DOWNLOAD_KEY = "ls.button.download";
	private static DownloadDialog m_clDownloadDialog;
	private static final HOVerwaltung hov = HOVerwaltung.instance();
	private final JButton abortButton = new JButton(TranslationFacility.tr("ls.button.cancel"));
	private final JButton downloadButton = new JButton(TranslationFacility.tr(DOWNLOAD_KEY));
	private final JButton proxyButton = new JButton(TranslationFacility.tr("ConfigureProxy"));
	private final DownloadFilter filterRoot = new DownloadFilter();
	private final CheckBoxTree downloadFilter = new CheckBoxTree();

	private final JCheckBox matchArchiveCheckBox = new JCheckBox(TranslationFacility.tr("download.oldmatches"), false);
	private final SpinnerDateModel archiveDateModel = new SpinnerDateModel();
	private final JSpinner archiveDateSpinner = new JSpinner(archiveDateModel);
	private final JCheckBox showSaveDialogCheckBox = new JCheckBox(TranslationFacility.tr("Show_SaveHRF_Dialog"), core.model.UserParameter.instance().showHRFSaveDialog);
	private final boolean isNtTeam;
	/** Progress bar step of each download stage. */
	private static final int PROGRESS_INCREMENT = 3;

	/**
	 * Getter for the singleton HOMainFrame instance.
	 */
	public static DownloadDialog instance() {
		if (m_clDownloadDialog == null) {
			m_clDownloadDialog = new DownloadDialog();
		}

		return m_clDownloadDialog;
	}

	private static void clearInstance() {
		m_clDownloadDialog = null;
	}

	/**
	 * Singleton
	 */
	private DownloadDialog() {
		super(HOMainFrame.instance(), TranslationFacility.tr("ls.menu.file.download"), ModalityType.MODELESS);
		this.isNtTeam = UserManager.instance().getCurrentUser().isNtTeam();
		setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
		initComponents();
	}

	@Override
	public final void actionPerformed(ActionEvent e) {
		if (e.getSource().equals(downloadButton)) {
			if ( isNtTeam){
				startNtDownload();
			}
			else {
				startDownload();
			}
			RefreshManager.instance().doReInit();
			close();
			if (UserParameter.instance().updateCheck) {
				UpdateController.check4update(false);
			}
		} else if (e.getSource().equals(abortButton)) {
			close();
		} else if (e.getSource().equals(proxyButton)) {
			new ProxyDialog(HOMainFrame.instance());
		}
	}

	private void close(){
		setVisible(false);
		dispose();
	}

	@Override
	public void dispose() {
		super.dispose();
		clearInstance();
	}

	/**
	 * Initialize the GUI components.
	 */
	private void initComponents() {
		setContentPane(new ImagePanel(new GridBagLayout()));
		var c = new GridBagConstraints();

		if (!isNtTeam) {
			final JPanel normalDownloadPanel = new ImagePanel(new BorderLayout());
			normalDownloadPanel.setBorder(BorderFactory.createTitledBorder(TranslationFacility.tr(DOWNLOAD_KEY)));

			// Download Filter
			final DefaultTreeModel newModel = new DefaultTreeModel(filterRoot);
			downloadFilter.setModel(newModel);
			newModel.reload();

			// Current Matches
			// - Official Matches
			//    currentMatchlist selects now the node OfficialMatches.
			//    It is the first subitem of current Matches in the Filter tree
			downloadFilter.checkNode(filterRoot.getOfficialMatches(), UserParameter.instance().downloadCurrentMatchlist);
			// - Integrated matches
			downloadFilter.checkNode(filterRoot.getSingleMatches(), UserParameter.instance().downloadSingleMatches);
			downloadFilter.checkNode(filterRoot.getLadderMatches(), UserParameter.instance().downloadLadderMatches);
			downloadFilter.checkNode(filterRoot.getTournamentGroupMatches(), UserParameter.instance().downloadTournamentGroupMatches);
			downloadFilter.checkNode(filterRoot.getTournamentPlayoffMatches(), UserParameter.instance().downloadTournamentPlayoffMatches);
			downloadFilter.checkNode(filterRoot.getDivisionBattleMatches(), UserParameter.instance().downloadDivisionBattleMatches);

			// Team Data
			downloadFilter.checkNode(filterRoot.getTeamData(), UserParameter.instance().xmlDownload);

			// Series Data (fixtures)
			downloadFilter.checkNode(filterRoot.getCurrentSeriesData(), UserParameter.instance().fixtures);

			var filterPanel = new JPanel(new BorderLayout());
			filterPanel.add(downloadFilter);
			var scrollPanel = new JScrollPane(filterPanel);
			scrollPanel.setPreferredSize(new Dimension(240,280));
			normalDownloadPanel.add(scrollPanel, BorderLayout.CENTER);
			c.gridx = 0;
			c.gridy = 0;
			c.weighty = 1;
			c.fill = GridBagConstraints.VERTICAL;
			c.anchor = GridBagConstraints.NORTHEAST;
			c.insets = new Insets(10, 10, 10, 10);
			getContentPane().add(normalDownloadPanel, c);

			final JPanel specialDownload = new ImagePanel(new BorderLayout());
			specialDownload.setBorder(BorderFactory.createTitledBorder(TranslationFacility.tr("Verschiedenes")));

			// Alte Spielpläne
			final JPanel oldFixturePanel = new ImagePanel(new BorderLayout());

			// MatchArchive
			final JPanel matchArchivePanel = new JPanel(new BorderLayout(1, 2));
			matchArchivePanel.setOpaque(false);

			matchArchiveCheckBox.setToolTipText(TranslationFacility.tr("download.oldmatches.tt"));
			matchArchiveCheckBox.addActionListener(this);
			matchArchiveCheckBox.setOpaque(false);
			matchArchivePanel.add(matchArchiveCheckBox, BorderLayout.WEST);

			archiveDateModel.setCalendarField(Calendar.MONTH); // NOSONAR Swing's SpinnerDateModel takes a Calendar field
			((JSpinner.DateEditor) archiveDateSpinner.getEditor()).getFormat().applyPattern("dd.MM.yyyy");
			matchArchivePanel.add(archiveDateSpinner, BorderLayout.EAST);

			// Show HRF FileDialog
			showSaveDialogCheckBox.setToolTipText(TranslationFacility.tr("tt_Optionen_Show_SaveHRF_Dialog"));
			showSaveDialogCheckBox.setOpaque(false);
			showSaveDialogCheckBox.addActionListener(this);

			final JPanel diverseOptionsPanel = new ImagePanel(new BorderLayout(1, 2));
			diverseOptionsPanel.add(matchArchivePanel, BorderLayout.NORTH);
			diverseOptionsPanel.add(showSaveDialogCheckBox, BorderLayout.SOUTH);

			oldFixturePanel.add(diverseOptionsPanel, BorderLayout.NORTH);

			specialDownload.add(oldFixturePanel);
			c.gridx = 1;
			getContentPane().add(specialDownload, c);
		} else {
			// isNtTeam
			final JPanel normalDownloadPanel = new ImagePanel(new GridLayout(3, 1, 4, 4));
			normalDownloadPanel.setBorder(BorderFactory.createTitledBorder(TranslationFacility.tr(DOWNLOAD_KEY)));
			normalDownloadPanel.setLayout(new BorderLayout());
			normalDownloadPanel.add(new JScrollPane(MainPanel.createHintArea()), BorderLayout.CENTER);
			c.gridx = 0;
			c.gridy = 0;
			c.gridwidth = 2;
			getContentPane().add(normalDownloadPanel, c);

		}
		var buttonRowPanel = new JPanel(new GridLayout(1, 3, 10, 10));
		downloadButton.setToolTipText(TranslationFacility.tr("tt_Download_Start"));
		downloadButton.addActionListener(this);
		downloadButton.setFont(downloadButton.getFont().deriveFont(Font.BOLD));
		InputMap buttonKeys = downloadButton.getInputMap(JComponent.WHEN_FOCUSED);
		buttonKeys.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0, false), "pressed");
		buttonKeys.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0, true), "released");
		buttonRowPanel.add(downloadButton);

		proxyButton.setToolTipText(TranslationFacility.tr("tt_ConfigureProxy"));
		proxyButton.addActionListener(this);
		proxyButton.setFont(proxyButton.getFont().deriveFont(Font.BOLD));
		buttonRowPanel.add(proxyButton);

		abortButton.setToolTipText(TranslationFacility.tr("tt_Download_Abbrechen"));
		abortButton.addActionListener(this);
		buttonRowPanel.add(abortButton);

		c.gridx = 0;
		c.gridy = 1;
		c.gridwidth = 2;
		c.anchor = GridBagConstraints.CENTER;
		getContentPane().add(buttonRowPanel, c);
		pack();

		final Dimension size = getToolkit().getScreenSize();
		if (size.width > this.getSize().width) {
			// Center
			this.setLocation((size.width / 2) - (this.getSize().width / 2), (size.height / 2) - (this.getSize().height / 2));
		}

		addWindowListener(new WindowAdapter() {
			@Override
			public void windowOpened(WindowEvent e) {
				downloadButton.requestFocusInWindow();
			}
		});

		setVisible(true);
	}

    /**
     * The download action.
     */
    private void startDownload() {
        saveDialogSettings();

        boolean bOK = true;
        if (UserParameter.instance().xmlDownload) {
            bOK = OnlineWorker.getHrf(this);
            OnlineWorker.downloadNtMatchesOfCappedPlayers(hov.getModel());
        }

        HOModel model = hov.getModel();
        int teamId = model.getBasics().getTeamId();
        if (teamId > 0) {
            bOK = downloadTeamRelatedData(model, teamId, bOK);
        }

        HOMainFrame.setInformation(Helper.getTranslation("ls.update_status.calc_subskills"), PROGRESS_INCREMENT);
        model.calcSubskills();

        // Last, and outside the team-data branch: what a watched market player sold for is not in
        // the HRF, it is a separate lookup per player. Running it here means it happens on any
        // download the user asked for, not only one with "team data" ticked, and it does not delay
        // the parts they are watching for. Skipped when something upstream failed - a broken
        // download should not be followed by a burst of further CHPP calls.
        if (bOK) {
            HOMainFrame.setInformation(Helper.getTranslation("ls.update_status.transfer_prices"), PROGRESS_INCREMENT);
            OnlineWorker.resolveTransferListings();
        }

        HOMainFrame.setInformationCompleted();
    }


    /**
     * Saves the dialog's settings.
     * The chpp-api only allows to download all current matches, the dialog settings only filter the match
     * types which of them should be stored in HO database. The settings are interpreted elsewhere.
     * Dialog's node "Current Matches" is checked, if any of the match types is checked.
     */
    private void saveDialogSettings() {
        var parameter = UserParameter.instance();
        parameter.downloadCurrentMatchlist = downloadFilter.isChecked(filterRoot.getOfficialMatches());
        parameter.downloadDivisionBattleMatches = downloadFilter.isChecked(filterRoot.getDivisionBattleMatches());
        parameter.downloadTournamentPlayoffMatches = downloadFilter.isChecked(filterRoot.getTournamentPlayoffMatches());
        parameter.downloadTournamentGroupMatches = downloadFilter.isChecked(filterRoot.getTournamentGroupMatches());
        parameter.downloadLadderMatches = downloadFilter.isChecked(filterRoot.getLadderMatches());
        parameter.downloadSingleMatches = downloadFilter.isChecked(filterRoot.getSingleMatches());

        parameter.showHRFSaveDialog = showSaveDialogCheckBox.isSelected();
        parameter.xmlDownload = downloadFilter.isChecked(filterRoot.getTeamData());
        parameter.fixtures = downloadFilter.isChecked(filterRoot.getCurrentSeriesData());
    }

    /**
     * Downloads everything that needs the own team id, in the order of the dialog. The steps
     * themselves live in {@link OnlineWorker}, which the headless server runs as well.
     *
     * @param ok false, if a previous download failed
     * @return false, if this or a previous download failed
     */
    private boolean downloadTeamRelatedData(HOModel model, int teamId, boolean ok) {
        OnlineWorker.downloadHallOfFameIfActive(model);

        boolean bOK = ok;
        if (this.downloadFilter.isChecked(filterRoot.getCurrentMatches())) {
            bOK = OnlineWorker.downloadCurrentMatches(model);
        }
        if (bOK && matchArchiveCheckBox.isSelected()) {
            downloadMatchArchive(teamId);
        }
        if (bOK && UserParameter.instance().fixtures) {
            bOK = OnlineWorker.downloadCurrentFixtures(model);
        }
        if (bOK) {
            HOMainFrame.setInformation(Helper.getTranslation("ls.update_status.fixtures"), PROGRESS_INCREMENT);
            downloadOldFixtures(teamId, selectedPreviousSeries());
        }

        OnlineWorker.refreshWorldDetailsIfDue();
        return bOK;
    }

    private void downloadMatchArchive(int teamId) {
        HOMainFrame.setInformation(Helper.getTranslation("ls.update_status.match_info"), PROGRESS_INCREMENT);
        var date = new HODateTime(archiveDateModel.getDate().toInstant());
        List<MatchKurzInfo> allMatches = OnlineWorker.getMatchArchive(teamId, date, false);
        if (allMatches != null) {
            for (MatchKurzInfo matchKurzInfo : OnlineWorker.FilterUserSelection(allMatches)) {
                OnlineWorker.downloadMatchData(matchKurzInfo, true);
            }
        }
    }

    /**
     * @return user objects of the checked nodes below "Previous Series Data"
     */
    private List<Object> selectedPreviousSeries() {
        var selection = new ArrayList<>();
        var e = filterRoot.getPreviousSeriesData().children();
        while (e.hasMoreElements()) {
            DefaultMutableTreeNode child = (DefaultMutableTreeNode) e.nextElement();
            if (downloadFilter.isChecked(child)) {
                selection.add(child.getUserObject());
            }
        }
        return selection;
    }


	private void downloadOldFixtures(int teamId, List<Object> selection) {
		var download = new OldFixturesDownload(teamId, selection.size() > 1);
		for (Object s : selection) {
			if (s instanceof Map.Entry<?, ?> entry && entry.getValue() instanceof Integer seasonId
					&& !download.downloadSeason(seasonId)) {
				break;
			}
		}
	}

	/**
	 * Downloads the fixtures of previous seasons, asking the user which league to take.
	 * The user may reuse the choice for the following seasons.
	 */
	private final class OldFixturesDownload {
		private final int teamId;
		private final boolean multipleSeasons;
		private LigaAuswahlDialog leagueSelectionDialog;
		private boolean useOwnLeague = true;
		private int leagueId = hov.getModel().getXtraDaten().getLeagueLevelUnitID();

		OldFixturesDownload(int teamId, boolean multipleSeasons) {
			this.teamId = teamId;
			this.multipleSeasons = multipleSeasons;
		}

		/**
		 * @return false, if the user aborted or the download failed: no further season is downloaded
		 */
		boolean downloadSeason(int seasonId) {
			if (useOwnLeague || !leagueSelectionDialog.getReuseSelection()) {
				leagueId = ownLeagueOfSeason(seasonId);
			}
			if (leagueSelectionDialog == null || !leagueSelectionDialog.getReuseSelection()) {
				leagueSelectionDialog = new LigaAuswahlDialog(DownloadDialog.this, seasonId, leagueId, multipleSeasons);
				if (leagueSelectionDialog.isAborted()) {
					return false;
				}
				leagueId = leagueSelectionDialog.getLigaID();
				useOwnLeague = leagueSelectionDialog.isOwnLeagueSelected();
			}
			if (leagueId <= -2) {
				return true;
			}
			var fixtures = OnlineWorker.downloadLeagueFixtures(seasonId, leagueId);
			if (fixtures == null) {
				return false;
			}
			hov.getModel().saveFixtures(fixtures);
			return true;
		}

		/**
		 * Downloads the own team's matches of the season to get its league id.
		 *
		 * @return league id of the own team in that season, the current one if no finished league match is found
		 */
		private int ownLeagueOfSeason(int seasonId) {
			var matches = OnlineWorker.downloadMatchesOfSeason(teamId, seasonId);
			if (matches == null) {
				return leagueId;
			}
			matches.stream().filter(i -> i.getMatchType().isTournament()).forEach(MatchKurzInfo::loadTournamentDetails);
			DBManager.instance().storeMatchKurzInfos(matches);
			return matches.stream()
					.filter(i -> i.getMatchType() == MatchType.LEAGUE && i.getMatchStatus() == MatchKurzInfo.FINISHED)
					.findFirst()
					.map(MatchKurzInfo::getMatchContextId)
					.orElse(leagueId);
		}
	}

	private void startNtDownload() {
		try {
			var teams = NthrfUtil.getNtTeams();
			if (teams == null || teams.isEmpty() || teams.get(0)[0] == null || teams.get(0)[0].isEmpty()) {
				return;
			}
			final long teamId = chooseNtTeam(teams);
			var hrf = NthrfUtil.createNthrf(teamId);
			if (hrf.isEmpty()) {
				HOLogger.instance().warning(getClass(), "No NT data: no players in team or training area closed");
				return;
			}
			HOModel homodel = HRFStringParser.parse(hrf);
			if (homodel == null) {
				HOLogger.instance().error(getClass(), "Download error: " + hrf);
				return;
			}
			storeNtModel(homodel, teamId);
		} catch (Exception e) {
			HOLogger.instance().error(getClass(), "Cannot download NT data from hattrick: " + e);
		}
	}

	/**
	 * @return the id of the only national team, or of the one the user picks
	 */
	private static long chooseNtTeam(List<String[]> teams) {
		if (teams.size() <= 1) {
			return Long.parseLong(teams.get(0)[0]);
		}
		NtTeamChooser chooser = new NtTeamChooser(teams);
		chooser.setModal(true);
		chooser.setVisible(true);
		long teamId = chooser.getSelectedTeamId();
		chooser.dispose();
		return teamId;
	}

	/**
	 * Stores the national team's model and downloads its matches and the opponent teams.
	 */
	private static void storeNtModel(HOModel homodel, long teamId) {
		var ntTeams = DBManager.instance().loadAllNtTeamDetails();
		// save the model in the database
		homodel.storeModel();
		// Only update when the model is newer than existing
		if (HOVerwaltung.isNewModel(homodel)) {
			hov.setModel(homodel);
		}
		var matches = OnlineWorker.getMatches((int) teamId, false, true, true);
		if (matches != null) {
			OnlineWorker.getAllLineups(null);
			OnlineWorker.downloadNtTeams(ntTeams, matches);
		}
	}
}
