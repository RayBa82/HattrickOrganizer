package core.net;

import core.db.DBManager;
import core.db.user.UserManager;
import core.file.ExampleFileFilter;
import core.file.hrf.HRFStringBuilder;
import core.file.hrf.HRFStringParser;
import core.file.xml.*;
import core.gui.HOMainFrame;
import core.gui.InfoPanel;
import core.gui.theme.ThemeManager;
import core.model.*;
import core.model.tournament.TournamentDetails;
import core.model.enums.MatchType;
import core.model.enums.MatchTypeExtended;
import core.model.match.*;
import core.model.misc.Regiondetails;
import core.model.player.Player;
import core.module.ModuleManager;
import core.rating.RatingCalibration;
import core.util.HODateTime;
import core.model.UserParameter;
import core.util.HOLogger;
import core.util.Helper;
import core.util.StringUtils;
import module.halloffame.HallOfFamePlayer;
import module.lineup.Lineup;
import module.nthrf.NtTeamDetails;
import module.series.MatchFixtures;
import org.apache.commons.lang3.math.NumberUtils;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.*;

/**
 * @author thomas.werth
 */
public class OnlineWorker {


	/**
	 * Utility class - private constructor enforces noninstantiability.
	 */
	private OnlineWorker() {
	}

	/** Why a team download produced nothing. The dialog wording stays in the UI layer. */
	public enum Failure { NO_TEAM, CHPP_ERROR, PLAYING_MATCH, NO_DATA, UNPARSEABLE }

	/**
	 * A team download that stored nothing, carrying a machine-readable reason.
	 * <p>
	 * One exception type with a reason code rather than one type per outcome, because
	 * {@link #getHrf} has to reproduce five different existing responses and because the MCP tool
	 * wants something to switch on rather than a string to match against.
	 */
	public static class DownloadFailed extends Exception {
		public final Failure reason;

		public DownloadFailed(Failure reason, String message) {
			this(reason, message, null);
		}

		public DownloadFailed(Failure reason, String message, Throwable cause) {
			super(message, cause);
			this.reason = reason;
		}
	}

	/** What one team download produced. */
	public record TeamDownload(String hrf, HOModel model, boolean becameCurrent) {
	}

	/**
	 * Download the team from Hattrick, parse it and store it: everything {@link #getHrf} does apart
	 * from the dialogs and the .hrf file save, in the same order.
	 * <p>
	 * Silent, not headless. It still writes HO's info line and calls
	 * {@code backupRealGameSettings()} on the lineup panel, so it must run on the event dispatch
	 * thread. What it never does is open a modal dialog: on the MCP server's single handler thread
	 * that would block the EDT and the server together, with nothing on the agent's side able to
	 * dismiss it.
	 */
	public static TeamDownload downloadTeamData() throws DownloadFailed {
		final var hov = HOVerwaltung.instance();
		final String hrf;
		try {
			hrf = ConvertXml2Hrf.createHrf();
		} catch (IOException e) {
			throw new DownloadFailed(Failure.CHPP_ERROR, getLangString("Downloadfehler")
					+ " : Error converting xml 2 HRF. Corrupt/Missing Data : ", e);
		}
		if (hrf == null) {
			throw new DownloadFailed(Failure.NO_TEAM, "No team selected.");
		}
		if (hrf.contains("playingMatch=true")) {
			throw new DownloadFailed(Failure.PLAYING_MATCH, getLangString("NO_HRF_Spiel"));
		}
		if (hrf.contains("NOT AVAILABLE")) {
			throw new DownloadFailed(Failure.NO_DATA, getLangString("NO_HRF_ERROR"));
		}

		// Create HOModel from the hrf data
		final var homodel = HRFStringParser.parse(hrf);
		if (homodel == null) {
			throw new DownloadFailed(Failure.UNPARSEABLE, getLangString("Importfehler"));
		}

		// save the model in the database
		homodel.storeModel();
		homodel.setFixtures(hov.getModel().getFixtures());
		// Add old players to the model
		homodel.setFormerPlayers(DBManager.instance().loadAllPlayers());
		// Only update when the model is newer than existing
		final var becameCurrent = HOVerwaltung.isNewModel(homodel);
		if (becameCurrent) {
			hov.setModel(homodel);
			// reset value of TS, confidence in Lineup Settings Panel after data download
			final var frame = HOMainFrame.shown();
			if (frame != null) {
				Objects.requireNonNull(frame.getLineupPanel()).backupRealGameSettings();
			}
		}
		return new TeamDownload(hrf, homodel, becameCurrent);
	}

	/**
	 * Get and optionally save HRF.
	 * <p>
	 * Behaviour is unchanged: the download itself moved to {@link #downloadTeamData()} and what is
	 * left here is the dialogs that used to sit inline in it.
	 */
	public static boolean getHrf(JDialog parent) {
		try {
			saveHRFToFile(parent, downloadTeamData().hrf());
			return true;
		} catch (DownloadFailed e) {
			return report(parent, e);
		} finally {
			HOMainFrame.setInformation(getLangString("HRFErfolg"), 0);
			//HOMainFrame.resetInformation();
		}
	}

	/**
	 * The dialog {@link #getHrf} used to show for each failure, and the boolean it used to return.
	 * <p>
	 * The three {@code true} returns are deliberate. After a mid-match skip, a NOT AVAILABLE answer
	 * or a parse failure nothing was stored, yet the old code left {@code ok} true and
	 * {@link DownloadDialog} carried on downloading matches, fixtures and prices. That oddity is
	 * preserved: this method's contract is that the Download button behaves exactly as before.
	 * Changing when it flips is a separate decision with its own testing. The MCP tool does not
	 * inherit it - to it all five reasons are errors, because in all five nothing was stored.
	 */
	private static boolean report(JDialog parent, DownloadFailed failure) {
		switch (failure.reason) {
			case NO_TEAM -> {
				return false;
			}
			case CHPP_ERROR -> {
				setInfoMsg(failure.getMessage(), InfoPanel.FEHLERFARBE);
				Helper.showMessage(parent, failure.getMessage() + "\n" + failure.getCause() + "\n",
						getLangString("Fehler"), JOptionPane.ERROR_MESSAGE);
				return false;
			}
			case PLAYING_MATCH -> {
				HOMainFrame.resetInformation();
				JOptionPane.showMessageDialog(parent, getLangString("NO_HRF_Spiel"),
						getLangString("NO_HRF_ERROR"), JOptionPane.INFORMATION_MESSAGE);
			}
			case NO_DATA -> {
				HOMainFrame.resetInformation();
				JOptionPane.showMessageDialog(parent, getLangString("NO_HRF_ERROR"),
						getLangString("NO_HRF_ERROR"), JOptionPane.INFORMATION_MESSAGE);
			}
			case UNPARSEABLE -> {
				setInfoMsg(getLangString("Importfehler"), InfoPanel.FEHLERFARBE);
				Helper.showMessage(parent, getLangString("Importfehler"), getLangString("Fehler"),
						JOptionPane.ERROR_MESSAGE);
			}
		}
		return true;
	}

	/**
	 * Fill the real sale price into expired transfer listings and scout entries, then fill in
	 * character for corpus players that have never had it fetched.
	 * <p>
	 * Neither is part of the HRF. A team download brings this team's own transfers, but a player HO
	 * merely watched on the market belongs to somebody else, so what he went for - and his character
	 * - takes a separate CHPP call each. This piggybacks on the download because that is a moment
	 * the user chose to spend CHPP traffic, not because the data arrives with it. Each pass has its
	 * own time budget in {@code TransferListingResolver} - these files have no CHPP rate limit - and
	 * runs as an independent attempt, so a failure or timeout in one does not cost the other its
	 * turn.
	 * <p>
	 * A failure here must never fail the download, so it is logged and swallowed.
	 */
	public static void resolveTransferListings() {
		try {
			final var resolved = TransferListingResolver.resolve();
			if (resolved > 0) {
				HOLogger.instance().info(OnlineWorker.class,
						"Resolved sale price of " + resolved + " transfer listing(s)");
			}
		} catch (Exception e) {
			HOLogger.instance().error(OnlineWorker.class, e);
		}
		try {
			final var filled = TransferListingResolver.backfillCharacter(
					TransferListingResolver.MAX_CHARACTER_BACKFILL);
			if (filled.updated() > 0) {
				HOLogger.instance().info(OnlineWorker.class,
						"Filled in character for " + filled.updated() + " transfer listing player(s)");
			}
		} catch (Exception e) {
			HOLogger.instance().error(OnlineWorker.class, e);
		}
	}

	/**
	 * How much of HO's progress bar each download section advances.
	 * <p>
	 * The dialog's local {@code progressIncrement}, hoisted so the extracted sections keep driving
	 * the same bar at the same rate whichever caller runs them.
	 */
	private static final int DOWNLOAD_PROGRESS_STEP = 3;

	/*
	 * The sections below are {@code DownloadDialog.startDownload} taken apart, one method per block,
	 * in its order. They were extracted rather than copied so {@code download_team} and the Download
	 * button cannot drift: the dialog now calls these too, and its own body is the same sequence of
	 * calls.
	 *
	 * Two of the dialog's sections deliberately did NOT move. The match archive is driven by a
	 * checkbox and a date spinner that live only on the dialog and start unticked, and old-season
	 * fixtures go through {@code downloadOldFixtures}, which opens the modal {@code LigaAuswahlDialog}
	 * per season. Both are dialog state with no persisted form, so "whatever the user last ticked"
	 * is "nothing" for a caller that has no dialog - and the second one could not run silently even
	 * if it were ticked.
	 */

	/**
	 * Whether the dialog's "Current Matches" node is ticked, without a dialog.
	 * <p>
	 * That node is not persisted and has no flag of its own. {@code CheckBoxTree} marks a parent
	 * selected when any descendant is ({@code CheckBoxTree.updatePredecessorsWithCheckMode}), and the
	 * six leaves under it are exactly the six saved flags below - which is what the comment in
	 * {@code startDownload} means by "checked, if any of the following is checked".
	 */
	public static boolean anyCurrentMatchTypeSelected() {
		final var p = UserParameter.instance();
		return p.downloadCurrentMatchlist || p.downloadSingleMatches || p.downloadLadderMatches
				|| p.downloadTournamentGroupMatches || p.downloadTournamentPlayoffMatches
				|| p.downloadDivisionBattleMatches;
	}

	/**
	 * The current matches of every national team one of our players is capped for.
	 * <p>
	 * In the dialog this sits inside the "team data" branch, because a capped player is only known
	 * once the squad has been downloaded.
	 */
	public static void downloadNtMatchesOfCappedPlayers(HOModel model) {
		for (Player p : model.getCurrentPlayers()) {
			if (p.getNationalTeamId() != null && p.getNationalTeamId() != 0) {
				getMatches(p.getNationalTeamId(), false, true, true);
			}
		}
	}

	/** The hall of fame, when that module is switched on - the dialog's own condition. */
	public static void downloadHallOfFameIfActive(HOModel model) {
		if (ModuleManager.instance().getModule(core.module.IModule.HALL_OF_FAME).getStatus()
				== core.module.IModule.STATUS_DEACTIVATED) {
			return;
		}
		HOMainFrame.setInformation(
				Helper.getTranslation("ls.update_status.hall_of_fame"), DOWNLOAD_PROGRESS_STEP);
		var hallOfFamesPlayer = downloadHallOfFame(model.getBasics().getTeamId());
		model.setHallOfFamesPlayers(hallOfFamesPlayer);
		DBManager.instance().storeHallOfFame(model.getHrfId(), hallOfFamesPlayer);
	}

	/**
	 * This team's current matches, their lineups, and the youth side's missing match data.
	 * <p>
	 * The youth block runs even when the match list failed, which is what the dialog does - kept
	 * deliberately, so extracting this changed no behaviour.
	 *
	 * @return false when the match list could not be fetched
	 */
	public static boolean downloadCurrentMatches(HOModel model) {
		final int teamId = model.getBasics().getTeamId();
		HOMainFrame.setInformation(
				Helper.getTranslation("ls.update_status.match_info"), DOWNLOAD_PROGRESS_STEP);
		// Only get lineups for own fixtures
		boolean bOK = getMatches(teamId, false, true, true) != null;
		if (bOK) {
			getAllLineups(10);
		}

		if (model.getBasics().hasYouthTeam()) {
			var dateSinceTimestamp = DBManager.instance().getMinScoutingDate();
			var dateSince = HODateTime.fromDbTimestamp(dateSinceTimestamp);
			downloadMissingYouthMatchData(model, dateSince);
			// delete old youth match lineups, no longer needed (no current youth player has trained then)
			DBManager.instance().deleteYouthMatchDataBefore(dateSinceTimestamp);
		}
		return bOK;
	}

	/**
	 * This season's league fixtures, plus the matches other teams in the series have played since
	 * the previous download - which is what turns the stored league table from predicted to actual.
	 *
	 * @return false when the fixture list could not be fetched
	 */
	public static boolean downloadCurrentFixtures(HOModel model) {
		// in the last week of a season the LeagueLevelUnitID switches to the next season's value
		// (no fixtures are available then)
		if (model.getBasics().getSpieltag() >= 16) {
			return true;
		}
		final int teamId = model.getBasics().getTeamId();
		HOMainFrame.setInformation(
				Helper.getTranslation("ls.update_status.fixtures"), DOWNLOAD_PROGRESS_STEP);
		var leagueId = model.getXtraDaten().getLeagueLevelUnitID();
		var fixtures = downloadLeagueFixtures(-1, leagueId);
		if (fixtures == null) {
			return false;
		}
		if (fixtures.getMatches().isEmpty()) {
			// Matches are not available from hattrick. Initialize them
			var teamStats = getSeriesDetails(leagueId);
			var newFixtures = MatchFixtures.createFixtures(model.getXtraDaten().getSeriesMatchDate(),
					teamStats.values().stream().sorted(Comparator.comparing(TeamStats::getPosition)).toList());
			fixtures.addFixtures(newFixtures);
		}
		final MatchFixtures modelFixtures = model.getFixtures();
		if (modelFixtures != null) {
			// state of previous download
			var oldDownloadedFixtures = modelFixtures.getMatches();
			// extract played matches of foreign teams
			var newPlayedMatchesOfOtherTeams = fixtures.getMatches().stream()
					.filter(i -> i.getToreHeim() >= 0 && i.getHeimId() != teamId && i.getGastId() != teamId)
					.toList();
			if (oldDownloadedFixtures != null) {
				// matches that were not played on previous download
				var notPlayedYet = oldDownloadedFixtures.stream()
						.filter(i -> i.getToreHeim() < 0)
						.toList();
				// intersection of both lists gives the list of played matches since previous download
				var latestPlayedMatches = newPlayedMatchesOfOtherTeams.stream()
						.filter(i -> notPlayedYet.stream().anyMatch(j -> j.getMatchId() == i.getMatchId()))
						.toList();
				for (var m : latestPlayedMatches) {
					if (!downloadMatchData(m.getMatchId(), MatchType.LEAGUE, true)) {
						HOLogger.instance().error(OnlineWorker.class, "Error fetching Match: " + m.getMatchId());
						break;
					}
				}
			}
		}
		model.saveFixtures(fixtures);
		return true;
	}

	/** The world's league metadata, when it is stale enough to be worth a refresh. */
	public static void refreshWorldDetailsIfDue() {
		if (!WorldDetailLeague.checkWorldDetailsDownload()) {
			return;
		}
		HOMainFrame.setInformation(
				Helper.getTranslation("ls.update_status.world_details"), DOWNLOAD_PROGRESS_STEP);
		var allLeagues = downloadWorldDetails();
		DBManager.instance().storeWorldDetailLeagues(allLeagues);
		WorldDetailsManager.instance().refresh();
		WorldDetailLeague.setLatestDownloadNow();
	}

	/**
	 * saugt das Archiv
	 *
	 * @param teamId
	 *            null falls unnötig sonst im Format 2004-02-01
	 * @param firstDate
	 *            null falls unnötig sonst im Format 2004-02-01
	 * @param store
	 *            True if matches are to be downloaded and stored. False if only
	 *            a match list is wanted.
	 *
	 * @return The list of MatchKurzInfo. This can be null on error, or empty.
	 */
	public static List<MatchKurzInfo> getMatchArchive(int teamId, HODateTime firstDate, boolean store) {

		List<MatchKurzInfo> allMatches = new ArrayList<>();
		var endDate = HODateTime.now();

		// Show wait Dialog
		HOMainFrame.resetInformation();

		try {
			String matchesString;

			while (firstDate.isBefore(endDate)) {
				var lastDate=firstDate.plus(90, ChronoUnit.DAYS);
				if (!lastDate.isBefore(endDate)) {
					lastDate = endDate;
				}

				try {
					HOMainFrame.setInformation(TranslationFacility.tr("ls.update_status.match_info"), 20);
					matchesString = Connector.instance().getMatchesArchive(teamId, firstDate,	lastDate);
					HOMainFrame.setInformation(TranslationFacility.tr("ls.update_status.match_info"), 20);
				} catch (Exception e) {
					// Info
					String msg = getLangString("Downloadfehler")
							+ " : Error fetching MatchArchiv : ";
					setInfoMsg(msg, InfoPanel.FEHLERFARBE);
					Helper.showMessage(HOMainFrame.shown(), msg, getLangString("Fehler"),
							JOptionPane.ERROR_MESSAGE);
					return null;
				}

				HOMainFrame.setInformation(TranslationFacility.tr("ls.update_status.match_info"), 20);
				List<MatchKurzInfo> matches = XMLMatchArchivParser
						.parseMatchesFromString(matchesString);

				// Add the new matches to the list of all matches
				allMatches.addAll(matches);

				// Zeitfenster neu setzen
				firstDate = firstDate.plus(90, ChronoUnit.DAYS);
			}

			// Store in the db if store is true
			if (store && (!allMatches.isEmpty())) {

				HOMainFrame.setInformation(TranslationFacility.tr("ls.update_status.match_info"), 20);
				DBManager.instance().storeMatchKurzInfos(allMatches);

				// Store full info for all matches
				for (MatchKurzInfo match : allMatches) {
					// if match is available and match is finished
					if ((DBManager.instance().isMatchInDB(match.getMatchID(), match.getMatchType()))
							&& (match.getMatchStatus() == MatchKurzInfo.FINISHED)) {
						downloadMatchData(match.getMatchID(), match.getMatchType(), true);
					}
				}
			}
		} finally {
			HOMainFrame.setInformationCompleted();
		}
		return allMatches;
	}

	public static boolean downloadMatchData(int matchId, SourceSystem sourceSystem, boolean refresh) {
		var info = DBManager.instance().loadMatchKurzInfo(sourceSystem, matchId);
		if (info == null) {
			var details = OnlineWorker.downloadMatchDetails(sourceSystem, matchId);
			if (details != null) {
				info = new MatchKurzInfo();
				info.setMatchID(details.getMatchID());
				info.setMatchType(details.getMatchType());
			}
			else {
				return false;
			}
		}
		return downloadMatchData(info, refresh);
	}

	private static Matchdetails downloadMatchDetails(SourceSystem sourceSystem, int matchId) {
		var matchDetails = Connector.instance().downloadMatchDetails(sourceSystem.to_string(), matchId);
		if (downloadOfMatchDetailsFailed(matchDetails, matchId)) {
			return null;
		}
		return XMLMatchdetailsParser.parseMatchdetailsFromString(matchDetails, null);
	}

	/**
	 * Downloads a match with the given criteria and stores it in the database.
	 * If a match is already in the db, and refresh is false, nothing is
	 * downloaded.
	 *
	 * @param matchid
	 *            ID for the match to be downloaded
	 * @param matchType
	 *            matchType for the match to be downloaded.
	 * @param refresh
	 *            If true the match will always be downloaded.
	 *
	 * @return true if the match is in the db afterwards
	 */
	public static boolean downloadMatchData(int matchid, MatchType matchType, boolean refresh) {
		MatchKurzInfo info;
		if (DBManager.instance().isMatchInDB(matchid, matchType)) {
			info = DBManager.instance().getMatchesKurzInfoByMatchID(matchid, matchType);
		}
		else {
			info = new MatchKurzInfo();
			info.setMatchID(matchid);
			info.setMatchType(matchType);
		}
		return downloadMatchData(info, refresh);
	}

	/**
	 * Downloads all the data for a match whose <code>kurzInfo</code> is passed.
	 *
	 * <p>This includes:</p>
	 * <ul>
	 *     <li>{@link Matchdetails} for that match, incl. opponents, date, location, weather, events, etc.;</li>
	 *     <li>The teams' logo;</li>
	 *     <li>The match lineup for both teams;</li>
	 *     <li>The team details (for ratings) for both teams;</li>
	 *     <li>and {@link Matchdetails} again for highlights?</li>
	 * </ul>
	 * @param info High level match info.
	 * @param refresh Forces a new download of the match details, even if they already exist.
	 * @return boolean – <code>true</code> if download is successful, <code>false</code> otherwise.
	 */
	public static boolean downloadMatchData(MatchKurzInfo info, boolean refresh) {
		if (info.isObsolet()) {
			return true;
		}

		HOLogger.instance().debug(OnlineWorker.class, "Get Lineup : " + info.getMatchID());

		int matchID = info.getMatchID();
		if (matchID < 0) {
			return false;
		}

		HOMainFrame.setWaitInformation();
		// Only download if not present in the database, or if refresh is true or if match not obsolete
		if (refresh
				|| !DBManager.instance().isMatchInDB(matchID, info.getMatchType())
				|| DBManager.instance().hasUnsureWeatherForecast(matchID)
				|| DBManager.instance().matchLineupIsNotStored(info.getMatchType(), matchID)
		) {
			try {
				Matchdetails details;

				// If ids not found, download matchdetails to obtain them.
				// Highlights will be missing.
				// ArenaId==0 in division battles
				boolean incompleteInfo = info.getHomeTeamID() <= 0 || info.getGuestTeamID() <= 0;
				Weather.Forecast weatherDetails = info.getWeatherForecast();
				boolean bWeatherKnown = ((weatherDetails != null) && weatherDetails.isSure());

				if (incompleteInfo || !bWeatherKnown) {
					HOMainFrame.setWaitInformation();
					details = downloadMatchDetails(matchID, info.getMatchType(), null);
					if (details != null) {
						info.setHomeTeamID(details.getHomeTeamId());
						info.setGuestTeamID(details.getGuestTeamId());
						info.setArenaId(details.getArenaID());
						info.setMatchSchedule(details.getMatchDate());
						info.setMatchType(details.getMatchType());
						int wetterId = details.getWetterId();
						if (wetterId != -1) {
							info.setMatchStatus(MatchKurzInfo.FINISHED);
							info.setWeather(Weather.getById(details.getWetterId()));
							info.setWeatherForecast(Weather.Forecast.HAPPENED);
						} else if (info.getArenaId() > 0) {
							info.setRegionId(details.getRegionId());

							if (!info.getWeatherForecast().isSure()) {
								Regiondetails regiondetails = getRegionDetails(info.getRegionId());
								if (regiondetails != null) {
									var matchDate = info.getMatchSchedule().toLocaleDate();
									var weatherDate = regiondetails.getFetchDatum().toLocaleDate();
									if (matchDate.equals(weatherDate)) {
										info.setWeatherForecast(Weather.Forecast.TODAY);
										info.setWeather(regiondetails.getWeather());
									} else {
										var forecastDate = regiondetails.getFetchDatum().plus(1, ChronoUnit.DAYS).toLocaleDate();
										if (matchDate.equals(forecastDate)) {
											info.setWeatherForecast(Weather.Forecast.TOMORROW);
										} else {
											info.setWeatherForecast((Weather.Forecast.UNSURE));
										}
										info.setWeather(regiondetails.getWeatherTomorrow());
									}
								}
							}
						}

						if (info.getHomeTeamID() > 0) {
							int guestRegionId;
							if (info.isAwayMatch()) {
								guestRegionId = HOVerwaltung.instance().getModel().getBasics().getRegionId();
							} else {
								var guestTeamInfo = getTeam(info.getGuestTeamID());
								guestRegionId = getRegionId(guestTeamInfo);
								downloadTeamLogo(guestTeamInfo);
							}
							info.setIsDerby(guestRegionId == info.getRegionId());

							int homeArenaId;
							if (info.isHomeMatch()) {
								homeArenaId = HOVerwaltung.instance().getModel().getStadium().getArenaId();
							} else {
								var homeTeamInfo = getTeam(info.getHomeTeamID());
								homeArenaId = getArenaId(homeTeamInfo);
								downloadTeamLogo(homeTeamInfo);
							}
							info.setIsNeutral(info.getArenaId() != homeArenaId);
						} else {
							// Negative team ids are Verlegenheitstruppe 08/15
							info.setIsDerby(false);
							info.setIsNeutral(false);
						}
					}
				}

				MatchLineup lineup;
				boolean success;
				if ((info.getMatchStatus() == MatchKurzInfo.FINISHED) && (!info.isObsolet())) {
					lineup = downloadMatchlineup(matchID, info.getMatchType(), info.getHomeTeamID(), info.getGuestTeamID());
					if (lineup == null) {
						if (!isSilentDownload()) {
							String msg = getLangString("Downloadfehler") + " : Error fetching Matchlineup :";
							// Info
							setInfoMsg(msg, InfoPanel.FEHLERFARBE);
							Helper.showMessage(HOMainFrame.shown(), msg, getLangString("Fehler"),
									JOptionPane.ERROR_MESSAGE);
						}
						return false;
					}

					downloadTeamRatings(matchID, info.getMatchType(), info.getHomeTeamID());
					downloadTeamRatings(matchID, info.getMatchType(), info.getGuestTeamID());

					// Get details with highlights.
					HOMainFrame.setWaitInformation();
					details = downloadMatchDetails(matchID, info.getMatchType(), lineup);

					if (details == null) {
						HOLogger.instance().error(OnlineWorker.class,
								"Error downloading match. Details is null: " + matchID);
						return false;
					}
					info.setDuration(details.getLastMinute());
					info.setGuestTeamGoals(details.getGuestGoals());
					info.setHomeTeamGoals(details.getHomeGoals());
					info.setGuestTeamID(lineup.getGuestTeamId());
					info.setGuestTeamName(lineup.getGuestTeamName());
					info.setHomeTeamID(lineup.getHomeTeamId());
					info.setHomeTeamName(lineup.getHomeTeamName());
						success = DBManager.instance().storeMatch(info, details, lineup);
						if (success) {
							RatingCalibration.calibrateAndStore(info.getMatchID(), info.getMatchType());
						}
				} else {
					// Update arena and region ids
					var matches = new ArrayList<MatchKurzInfo>();
					matches.add(info);
					DBManager.instance().storeMatchKurzInfos(matches);
					success = true;
				}
				if (!success) {
					return false;
				}
			} catch (Exception ex) {
				HOLogger.instance().error(OnlineWorker.class,
						"downloadMatchData:  Error in downloading match: " + ex);
				return false;
			}
		}
		return true;
	}

	private static void downloadTeamRatings(int matchID, MatchType matchType, int teamID) {
		try {
			var xml = Connector.instance().getTeamDetails(teamID);
			var teamrating = new MatchTeamRating(matchID, matchType, XMLTeamDetailsParser.parseTeamdetailsFromString(xml, teamID));
			DBManager.instance().storeTeamRatings(teamrating);
		} catch (Exception e) {
			String msg = getLangString("Downloadfehler") + " : Error fetching Team ratings :";
			// Info
			setInfoMsg(msg, InfoPanel.FEHLERFARBE);
			Helper.showMessage(HOMainFrame.shown(), msg, getLangString("Fehler"),
					JOptionPane.ERROR_MESSAGE);
		}
	}

	public static Map<String, String> getTeam(int teamId) {
		String str = Connector.instance().getTeamDetails(teamId);
		return XMLTeamDetailsParser.parseTeamdetailsFromString(str, teamId);
	}

	private static int getRegionId(Map<String, String> team) {
		String  str = team.get("RegionID");
		if ( str != null ) return Integer.parseInt(str);
		return 0;
	}

	private  static int getArenaId(Map<String, String> team) {
		String str = team.get("ArenaID");
		if ( str != null ) return Integer.parseInt(str);
		return 0;
	}


	/**
	 * Loads the data for the given match from HT and updates the data for this
	 * match in the DB.
	 *
	 * @param teamId
	 *            the id of the team
	 * @param match
	 *            the match to update
	 * @return a new MatchKurzInfo object with the current data from HT or null
	 *         if match could not be downloaded.
	 */
	public static @Nullable MatchKurzInfo updateMatch(int teamId, MatchKurzInfo match) {
		var matchDate = match.getMatchSchedule();
		// At the moment, HT does not support getting a single match.
		List<MatchKurzInfo> matches = getMatches(teamId, matchDate.plus(1, ChronoUnit.MINUTES));
		for (MatchKurzInfo m : matches) {
			if (m.getMatchID() == match.getMatchID()) {
				//DBManager.instance().updateMatchKurzInfo(m);
				return m;
			}
		}
		return null;
	}

	/**
	 * Gets the most recent and upcoming matches for a given teamId and up to a
	 * specific date. Nothing is stored to DB.
	 *
	 * @param teamId
	 *            the id of the team.
	 * @param date
	 *            last date (+time) to get matches to.
	 * @return the most recent and upcoming matches up to the given date.
	 */
	public static List<MatchKurzInfo> getMatches(int teamId, HODateTime date) {
		String matchesString = null;
		try {
			matchesString = Connector.instance().getMatches(teamId, true, date);
		} catch (IOException e) {
			Helper.showMessage(HOMainFrame.shown(), getLangString("Downloadfehler")
					+ " : Error fetching matches : " + e, getLangString("Fehler"),
					JOptionPane.ERROR_MESSAGE);
			HOLogger.instance().log(OnlineWorker.class, e);
		}

		if (!StringUtils.isEmpty(matchesString)) {
			return XMLMatchesParser.parseMatchesFromString(matchesString);
		}

		return new ArrayList<>();
	}

	/**
	 * Download information about a given tournament
	 */
	public static TournamentDetails getTournamentDetails(int tournamentId) {
		TournamentDetails oTournamentDetails = null;
		String tournamentString = "";

		try {
			tournamentString = Connector.instance().getTournamentDetails(tournamentId);
		} catch (IOException e) {
			Helper.showMessage(HOMainFrame.shown(), getLangString("Downloadfehler")
							+ " : Error fetching Tournament Details : " + e, getLangString("Fehler"),
					JOptionPane.ERROR_MESSAGE);
			HOLogger.instance().log(OnlineWorker.class, e);
		}

		if (!StringUtils.isEmpty(tournamentString)) {
			oTournamentDetails = XMLTournamentDetailsParser.parseTournamentDetailsFromString(tournamentString);  // TODO: create function parseTournamentDetailsFromString
		}

		return oTournamentDetails;
	}

	/**
	 * Download series data
	 *
	 * @param teamId
	 *            team id (optional <1 for current team
	 * @param forceRefresh (unused?!)
	 * @param store
	 *            true if the full match details are to be stored, false if not.
	 * @param upcoming
	 *            true if upcoming matches should be included
	 *
	 * @return The list of MatchKurzInfos found or null if an exception
	 *         occurred.
	 */
	public static List<MatchKurzInfo> getMatches(int teamId, boolean forceRefresh, boolean store, boolean upcoming) {
		String matchesString;
		List<MatchKurzInfo> matches = new ArrayList<>();
		boolean bOK;
		HOMainFrame.setWaitInformation();

		try {
			matchesString = Connector.instance().getMatches(teamId, forceRefresh, upcoming);
			bOK = (matchesString != null && !matchesString.isEmpty());
			if (bOK)
				HOMainFrame.setWaitInformation();
		} catch (Exception e) {
			String msg = getLangString("Downloadfehler") + " : Error fetching matches: "
					+ e.getMessage();
			// Info
			setInfoMsg(msg, InfoPanel.FEHLERFARBE);
			Helper.showMessage(HOMainFrame.shown(), msg, getLangString("Fehler"),
					JOptionPane.ERROR_MESSAGE);
			HOLogger.instance().log(OnlineWorker.class, e);
			return null;
		}
		if (bOK) {
			matches.addAll(XMLMatchesParser.parseMatchesFromString(matchesString));

			// Store in DB if store is true
			if (store) {
				HOMainFrame.setWaitInformation();

				matches = FilterUserSelection(matches);
				DBManager.instance().storeMatchKurzInfos(matches);

				HOMainFrame.setWaitInformation();

				// Automatically download additional match infos (lineup + arena)
				for (MatchKurzInfo match : matches) {
					int curMatchId = match.getMatchID();
					boolean refresh = !DBManager.instance().isMatchInDB(curMatchId, match.getMatchType())
							|| (match.getMatchStatus() != MatchKurzInfo.FINISHED && DBManager.instance().hasUnsureWeatherForecast(curMatchId))
							|| DBManager.instance().matchLineupIsNotStored(match.getMatchType(), curMatchId);

					if (refresh) {
						// No lineup or arenaId in DB
						boolean result = downloadMatchData(curMatchId, match.getMatchType(), refresh);
						if (!result) {
							break;
						}
					}
				}
			}
		}
		HOMainFrame.setWaitInformation();
		return matches;
	}

	public static List<MatchKurzInfo> FilterUserSelection(List<MatchKurzInfo> matches) {
		ArrayList<MatchKurzInfo> ret = new ArrayList<>();
		for (MatchKurzInfo m: matches) {
			switch (m.getMatchType()) {
				case INTSPIEL, NATIONALCOMPNORMAL, NATIONALCOMPCUPRULES, NATIONALFRIENDLY, PREPARATION, LEAGUE, QUALIFICATION, CUP, FRIENDLYNORMAL, FRIENDLYCUPRULES, INTFRIENDLYNORMAL, INTFRIENDLYCUPRULES, MASTERS -> {
					if (UserParameter.instance().downloadCurrentMatchlist) {
						ret.add(m);
					}
				}
				case TOURNAMENTGROUP -> {
					// this is TOURNAMENTGROUP but more specifically a division battle
					if (m.getMatchTypeExtended() == MatchTypeExtended.DIVISIONBATTLE) {
						if (UserParameter.instance().downloadDivisionBattleMatches) {
							// we add the game only if user selected division battle category
							ret.add(m);
						}
					} else {
						// this is TOURNAMENTGROUP but not a division battle
						if (UserParameter.instance().downloadTournamentGroupMatches) {
							ret.add(m);
						}
					}
				}
				case TOURNAMENTPLAYOFF -> {
					if (UserParameter.instance().downloadTournamentPlayoffMatches) {
						ret.add(m);
					}
				}
				case SINGLE -> {
					if (UserParameter.instance().downloadSingleMatches) {
						ret.add(m);
					}
				}
				case LADDER -> {
					if (UserParameter.instance().downloadLadderMatches) {
						ret.add(m);
					}
				}
				default ->
						HOLogger.instance().warning(OnlineWorker.class, "Unknown Matchtyp:" + m.getMatchType() + ". Is not downloaded!");
			}
		}
		return ret;
	}

	/**
	 * Download match lineup
	 *
	 * @param matchId
	 * 			Match Id
	 * @param matchType
	 * 			MatchType
	 * @param teamId1
	 * 			Id of first team to include to the returned lineup
	 * @param teamId2
	 * 			Optional id of second team
	 * @return
	 * 			MatchLineup containing specified teams
	 */
	private static MatchLineup downloadMatchlineup(int matchId, MatchType matchType, int teamId1,
												   int teamId2) {
		MatchLineup lineUp2 = null;

		HOMainFrame.setWaitInformation();

		// Lineups holen
		var lineUp1 = downloadMatchLineup(matchId, teamId1, matchType);
		if (lineUp1 != null) {
			HOMainFrame.setWaitInformation();
			if (teamId2 > 0)
				lineUp2 = downloadMatchLineup(matchId, teamId2, matchType);

			// Merge the two
			if ((lineUp2 != null)) {
				if (lineUp1.isHomeTeamNotLoaded())
					lineUp1.setHomeTeam(lineUp2.getHomeTeam());
				else if (!lineUp1.isGuestTeamLoaded())
					lineUp1.setGuestTeam(lineUp2.getGuestTeam());
			} else {
				// Get the 2nd lineup
				if (lineUp1.isHomeTeamNotLoaded()) {
					lineUp2 = downloadMatchLineup(matchId, lineUp1.getHomeTeamId(), matchType);
					if (lineUp2 != null)
						lineUp1.setHomeTeam(lineUp2.getHomeTeam());
				} else {
					lineUp2 = downloadMatchLineup(matchId, lineUp1.getGuestTeamId(), matchType);
					if (lineUp2 != null)
						lineUp1.setGuestTeam(lineUp2.getGuestTeam());
				}
			}
		}
		HOMainFrame.setWaitInformation();
		return lineUp1;
	}

	/**
	 * Get the Fixtures list
	 *
	 * @param season
	 *            - The season, -1 for current
	 * @param leagueID
	 *            - The ID of the league to get the fixtures for
	 *
	 * @return true on sucess, false on failure
	 */
	public static MatchFixtures downloadLeagueFixtures(int season, int leagueID) {
		String leagueFixtures;
		try {
			HOMainFrame.setWaitInformation();
			leagueFixtures = Connector.instance().getLeagueFixtures(season, leagueID);
			HOMainFrame.setWaitInformation();
			return XMLSpielplanParser.parseSpielplanFromString(leagueFixtures);
		} catch (Exception e) {
			HOLogger.instance().log(OnlineWorker.class, e);
			String msg = getLangString("Downloadfehler") + " : Error fetching leagueFixture: "
					+ e.getMessage();
			setInfoMsg(msg, InfoPanel.FEHLERFARBE);
			Helper.showMessage(HOMainFrame.shown(), msg, getLangString("Fehler"),
					JOptionPane.ERROR_MESSAGE);
		}
		return null;
	}

	/**
	 * Search the transfer market.
	 * <p>
	 * CHPP does not allow the transfer market to be scanned automatically, so this may only be
	 * called on an explicit user request.
	 *
	 * @param query the search parameters
	 * @return one page of search results, or null if the download failed
	 */
	public static XMLTransferSearchParser.Page searchTransfers(TransferSearchQuery query) {
		try {
			HOMainFrame.setWaitInformation();
			return TransferSearchService.instance().search(query);
		} catch (TransferSearchService.SearchFailed e) {
			String msg = e.errorKey() != null
					? getLangString(e.errorKey())
					: getLangString("Downloadfehler") + " : " + e.getMessage();
			setInfoMsg(msg, InfoPanel.FEHLERFARBE);
			Helper.showMessage(HOMainFrame.shown(), msg, getLangString("Fehler"),
					JOptionPane.ERROR_MESSAGE);
		} finally {
			HOMainFrame.setInformationCompleted();
		}
		return null;
	}

	/**
	 * Uploads the given order to Hattrick
	 *
	 * @param matchId
	 *            The id of the match in question. If left at 0 the match ID
	 *            from the model will be used (next match).
	 * @param lineup
	 *            The lineup object to be uploaded
	 * @return A string response with any error message
	 */
	public static String uploadMatchOrder(int matchId, MatchType matchType, Lineup lineup) {
		String result;
		String orders = lineup.toJson();
		try {
			result = Connector.instance().uploadMatchOrder(matchId, HOVerwaltung.instance().getModel().getBasics().getTeamId(), matchType, orders);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}

		return result;
	}

	/**
	 * Try to recover missing matchType information by querying HT with different source system and returning first result
	 *
	 * @param _match the match id
	 * @return the match type
	 */
	public static MatchKurzInfo inferMissingMatchType(MatchKurzInfo _match) {
		String matchDetails;
		Matchdetails details;
		var conn = Connector.instance();
		conn.setSilentDownload(true);

		try {
			matchDetails = conn.downloadMatchdetails(_match.getMatchID(), MatchType.LEAGUE);
			if((matchDetails != null) && (!matchDetails.isEmpty())) {
				details = XMLMatchdetailsParser.parseMatchdetailsFromString(matchDetails, null);
			}
			else{
				details = null;
			}
			if (details != null) {
				if (details.getHomeTeamId() == _match.getHomeTeamID()) {
					_match.setMatchType(details.getMatchType());
					_match.setMatchContextId(details.getMatchContextId());
					_match.setCupLevel(details.getCupLevel());
					_match.setCupLevelIndex(details.getCupLevelIndex());
					conn.setSilentDownload(false);
					return _match;
				}
			}

			matchDetails = conn.downloadMatchdetails(_match.getMatchID(), MatchType.LADDER);
			if((matchDetails != null) && (!matchDetails.isEmpty())) {
				details = XMLMatchdetailsParser.parseMatchdetailsFromString(matchDetails, null);
			}
			else{
				details = null;
			}
			if (details != null) {
				if (details.getHomeTeamId() == _match.getHomeTeamID()) {
					_match.setMatchType(details.getMatchType());
					_match.setMatchContextId(details.getMatchContextId());
					_match.setCupLevel(details.getCupLevel());
					_match.setCupLevelIndex(details.getCupLevelIndex());
					conn.setSilentDownload(false);
					return _match;
				}
			}

			matchDetails = conn.downloadMatchdetails(_match.getMatchID(), MatchType.YOUTHLEAGUE);
			if((matchDetails != null) && (!matchDetails.isEmpty())) {
				details = XMLMatchdetailsParser.parseMatchdetailsFromString(matchDetails, null);
			}
			else{
				details = null;
			}
			if (details != null) {
				if (details.getHomeTeamId() == _match.getHomeTeamID()) {
					_match.setMatchType(details.getMatchType());
					_match.setMatchContextId(details.getMatchContextId());
					_match.setCupLevel(details.getCupLevel());
					_match.setCupLevelIndex(details.getCupLevelIndex());
					conn.setSilentDownload(false);
					return _match;
				}
			}
			_match.setMatchType(MatchType.TOURNAMENTGROUP);
			_match.setTournamentTypeID(TournamentType.DIVISIONBATTLE.getId());
			_match.setisObsolet(true);
			conn.setSilentDownload(false);
			return _match;
		}
		catch (Exception e) {
			HOLogger.instance().error(OnlineWorker.class, "can't infer MatchType of match: " + _match.getMatchID());
			_match.setMatchType(MatchType.NONE);
			conn.setSilentDownload(false);
			return _match;
		}
	}

	private static Matchdetails downloadMatchDetails(int matchID, MatchType matchType, MatchLineup lineup) {
		String matchDetails;
		Matchdetails details;

		try {
			matchDetails = Connector.instance().downloadMatchdetails(matchID, matchType);
			if (downloadOfMatchDetailsFailed(matchDetails, matchID)) {
				return null;
			}
			HOMainFrame.setWaitInformation();
			details = XMLMatchdetailsParser.parseMatchdetailsFromString(matchDetails, lineup);
			HOMainFrame.setWaitInformation();
			if (details == null) {
				HOLogger.instance().warning(OnlineWorker.class, "Error parsing match details for match " + matchID);
				return null;
			}
			String arenaString = Connector.instance().downloadArena(details.getArenaID());
			HOMainFrame.setWaitInformation();
			details.setRegionId(XMLArenaParser.parseArenaFromString(arenaString).getRight().getRegion().id());
		} catch (Exception e) {
			String msg = getLangString("Downloadfehler") + ": Error fetching Matchdetails XML.: ";
			// Info
			setInfoMsg(msg, InfoPanel.FEHLERFARBE);
			Helper.showMessage(HOMainFrame.shown(), msg, getLangString("Fehler"), JOptionPane.ERROR_MESSAGE);
			return null;
		}
		return details;
	}

	private static boolean downloadOfMatchDetailsFailed(String matchDetails, int matchId) {
		if (StringUtils.isEmpty(matchDetails)) {
			HOLogger.instance().warning(OnlineWorker.class, "Unable to download details for match " + matchId);
			return true;
		}
		return false;
	}

	public static MatchLineup downloadMatchLineup(int matchID, int teamID, MatchType matchType) {
		String matchLineup;
		MatchLineup lineUp=null;
		boolean bOK;
		try {
			matchLineup = Connector.instance().downloadMatchLineup(matchID, teamID, matchType);
			bOK = (matchLineup != null && !matchLineup.isEmpty());
		} catch (Exception e) {
			String msg = getLangString("Downloadfehler") + " : Error fetching Matchlineup :";
			// Info
			setInfoMsg(msg, InfoPanel.FEHLERFARBE);
			Helper.showMessage(HOMainFrame.shown(), msg, getLangString("Fehler"),
					JOptionPane.ERROR_MESSAGE);
			return null;
		}
		if (bOK) {
			lineUp = XMLMatchLineupParser.parseMatchLineupFromString(matchLineup);
		}
		return lineUp;
	}

	/**
	 * Get all lineups for MatchKurzInfos, if they're not there already
	 */
	public static void getAllLineups(@Nullable Integer nbGames) {

		final List<MatchKurzInfo> infos;

		if (nbGames == null){
			infos = DBManager.instance().getMatchesKurzInfo(-1);
		}
		else{
			infos = DBManager.instance().getPlayedMatchInfo(nbGames, false, false);
		}

		boolean bOK;
		for (MatchKurzInfo info : infos) {
			int curMatchId = info.getMatchID();
			if ((!(info.isObsolet())) && (DBManager.instance().matchLineupIsNotStored(info.getMatchType(), curMatchId))) {
				if (info.getMatchStatus() == MatchKurzInfo.FINISHED) {
					bOK = downloadMatchData(curMatchId, info.getMatchType(), false);
					if (!bOK) {
						HOLogger.instance().error(OnlineWorker.class, "Error fetching Match: " + curMatchId);
						break;
					}
				}
			}
		}
	}

	/**
	 *
	 * @param matchId
	 *            The match ID for the match to download
	 * @param matchType
	 *            The matchTyp for the match to download
	 * @return The Lineup object with the downloaded match data
	 */
	public static MatchLineupTeam getLineupbyMatchId(int matchId, MatchType matchType) {

		try {
			var teamId = HOVerwaltung.instance().getModel().getBasics().getTeamId();
			String xml = Connector.instance().downloadMatchOrder(matchId, matchType, teamId);

			if (!StringUtils.isEmpty(xml)) {
				Map<String, String> map = XMLMatchOrderParser.parseMatchOrderFromString(xml);
				String trainerID = "-1";
				try {
					trainerID = String.valueOf(HOVerwaltung.instance().getModel().getTrainer()
							.getPlayerId());

				} catch (Exception e) {
					//It is possible that NTs struggle here.
				}
				var hrfStringBuilder = new HRFStringBuilder();
				hrfStringBuilder.createLineUp(trainerID, teamId, map);
				return new MatchLineupTeam(getProperties(hrfStringBuilder.createHRF().toString()));
			}
		} catch (Exception e) {
			String msg = getLangString("Downloadfehler") + " : Error fetching Matchorder :";
			setInfoMsg(msg, InfoPanel.FEHLERFARBE);
			Helper.showMessage(HOMainFrame.shown(), msg, getLangString("Fehler"),
					JOptionPane.ERROR_MESSAGE);
			HOLogger.instance().error(OnlineWorker.class, e.getMessage());
		}

		return null;
	}

	public static Regiondetails getRegionDetails(int regionId)
	{
		try {
			String xml = Connector.instance().getRegion(regionId);
			if ( !StringUtils.isEmpty(xml)){
				return new Regiondetails(XMLRegionParser.parseRegionDetailsFromString(xml));
			}
		}
		catch(Exception e){
			String msg = getLangString("Downloadfehler") + " : Error fetching region details :";
			setInfoMsg(msg, InfoPanel.FEHLERFARBE);
			Helper.showMessage(HOMainFrame.shown(), msg, getLangString("Fehler"),
					JOptionPane.ERROR_MESSAGE);
			HOLogger.instance().error(OnlineWorker.class, e.getMessage());
		}
		return null;
	}

	private static Properties getProperties(String data) throws IOException {
		ByteArrayInputStream bis = new ByteArrayInputStream(data.getBytes(StandardCharsets.UTF_8));
		InputStreamReader isr = new InputStreamReader(bis, StandardCharsets.UTF_8);
		BufferedReader hrfReader = new BufferedReader(isr);
		Properties properties = new Properties();

		// Lose the first line
		hrfReader.readLine();
		while (hrfReader.ready()) {
			String lineString = hrfReader.readLine();
			// Ignore empty lines
			if (!StringUtils.isEmpty(lineString)) {
				int indexEqualsSign = lineString.indexOf('=');
				if (indexEqualsSign > 0) {
					properties.setProperty(
							lineString.substring(0, indexEqualsSign).toLowerCase(
									Locale.ENGLISH),
							lineString.substring(indexEqualsSign + 1));
				}
			}
		}
		return properties;
	}

	/**
	 * Shows a file chooser asking for the location for the HRF file and saves
	 * it to the location chosen by the user.
	 *
	 * @param hrfData
	 *            the HRF data as string
	 */
	private static void saveHRFToFile(JDialog parent, String hrfData) {
		setInfoMsg(getLangString("HRFSave"));

		File path = new File(UserParameter.instance().hrfImport_HRFPath);
		File file = new File(path, getHRFFileName());
		// Show dialog if path not set or the file already exists
		if (UserParameter.instance().showHRFSaveDialog || !path.exists() || !path.isDirectory()
				|| file.exists()) {
			file = askForHRFPath(parent, file);
		}

		if (file != null) {
			// Save Path
			UserParameter.instance().hrfImport_HRFPath = file.getParentFile().getAbsolutePath();

			// File exists?
			int value = JOptionPane.OK_OPTION;
			if (file.exists()) {
				value = JOptionPane.showConfirmDialog(HOMainFrame.instance(),
						getLangString("overwrite"), TranslationFacility.tr("confirmation.title"), JOptionPane.YES_NO_OPTION);
			}

			// Save
			if (value == JOptionPane.OK_OPTION) {
				try {
					saveFile(file.getPath(), hrfData);
				} catch (IOException e) {
					Helper.showMessage(HOMainFrame.shown(),
							TranslationFacility.tr("Show_SaveHRF_Failed") + " " + file.getParentFile() + ".\nError: " + e.getMessage(),
							getLangString("Fehler"), JOptionPane.ERROR_MESSAGE);
				}
			} else {
				// Canceled
				setInfoMsg(getLangString("HRFAbbruch"), InfoPanel.FEHLERFARBE);
			}
		}

	}

	/**
	 * Gets a HRF file name, based on the current date.
	 *
	 * @return the HRF file name.
	 */
	private static String getHRFFileName() {
		GregorianCalendar calendar = (GregorianCalendar) Calendar.getInstance();
		StringBuilder builder = new StringBuilder();

		builder.append(HOVerwaltung.instance().getModel().getBasics().getTeamId());
		builder.append('-');

		builder.append(calendar.get(Calendar.YEAR));
		builder.append('-');
		int month = calendar.get(Calendar.MONTH) + 1;
		if (month < 10) {
			builder.append('0');
		}
		builder.append(month);
		builder.append('-');
		int day = calendar.get(Calendar.DAY_OF_MONTH);
		if (day < 10) {
			builder.append('0');
		}
		builder.append(day);
		builder.append(".hrf");
		return builder.toString();
	}

	/**
	 * Shows a file chooser dialog to ask the user for the location to save the
	 * HRF file.
	 *
	 * @param file
	 *            the recommendation for the file name/location.
	 * @return the file location choosen by the user or null if the canceled the
	 *         dialog.
	 */
	private static File askForHRFPath(JDialog parent, File file) {
		final JFileChooser fileChooser = new JFileChooser();
		fileChooser.setDialogType(JFileChooser.SAVE_DIALOG);
		fileChooser.setDialogTitle(getLangString("FileExport"));
		ExampleFileFilter filter = new ExampleFileFilter();
		filter.addExtension("hrf");
		filter.setDescription(TranslationFacility.tr("filetypedescription.hrf"));
		fileChooser.setFileFilter(filter);
		File path = file.getParentFile();
		if (path.exists() && path.isDirectory()) {
			fileChooser.setCurrentDirectory(path);
		}
		fileChooser.setSelectedFile(file);
		int returnVal = fileChooser.showSaveDialog(parent);
		if (returnVal == JFileChooser.APPROVE_OPTION) {
			File f = fileChooser.getSelectedFile();
			// File doesn't end with .hrf?
			if (!f.getPath().endsWith(".hrf")) {
				f = new java.io.File(file.getAbsolutePath() + ".hrf");
			}
			return f;
		}
		return null;
	}

	/**
	 * Convenience method for
	 * HOMainFrame.instance().getInfoPanel().setLangInfoText(msg);
	 *
	 * @param msg
	 *            the message to show
	 */
	private static void setInfoMsg(String msg) {
		HOMainFrame.setInformation(msg);
	}

	/**
	 * Convenience method for
	 * HOMainFrame.instance().getInfoPanel().setLangInfoText(msg, color);
	 *
	 * @param msg
	 *            the message to show
	 * @param color
	 *            the color
	 */
	private static void setInfoMsg(String msg, Color color) {
		HOMainFrame.setInformation(msg, color);
	}

	/**
	 * Convenience method for TranslationFacility.tr(key)
	 *
	 * @param key
	 *            the key for the language string
	 * @return the string for the current language
	 */
	private static String getLangString(String key) {
		return TranslationFacility.tr(key);
	}

	/**
	 * Save the passed in data to the passed in file
	 *
	 * @param fileName Name of the file to save the data to
	 * @param content  The content to write to the file
	 */
	private static void saveFile(String fileName, String content) throws IOException {
		File outFile = new File(fileName);
		if (outFile.exists()) {
			outFile.delete();
		}
		outFile.createNewFile();
		OutputStreamWriter outWrit = new OutputStreamWriter(new FileOutputStream(outFile), StandardCharsets.UTF_8);
		BufferedWriter out = new BufferedWriter(outWrit);
		out.write(content);
		out.newLine();
		out.close();
	}

	public static boolean isSilentDownload() {
		return Connector.instance().isSilentDownload();
	}

	public static void setSilentDownload(boolean silentDownload) {
		Connector.instance().setSilentDownload(silentDownload);
	}

	public static List<MatchKurzInfo> downloadMatchesOfSeason(int teamId, int season){
		try{
			var xml = Connector.instance().getMatchesOfSeason(teamId, season);
			return XMLMatchArchivParser.parseMatchesFromString(xml);
		}
		catch (Exception exception) {
			HOLogger.instance().error(OnlineWorker.class,"downloadMatchData:  Error in downloading matches of season: " + exception);
		}
		return null;
	}

	public static void downloadMissingYouthMatchData(HOModel model, HODateTime dateSince) {
		var youthteamid = model.getBasics().getYouthTeamId();
		var lastStoredYouthMatchDate = HODateTime.fromDbTimestamp(DBManager.instance().getLastYouthMatchDate());

		if ( dateSince == null || lastStoredYouthMatchDate != null && lastStoredYouthMatchDate.isAfter(dateSince) ){
			// if there are no youth matches in database, take the limit from arrival date of 'oldest' youth players
			dateSince = lastStoredYouthMatchDate;
		}

		for (HODateTime dateUntil; dateSince != null; dateSince = dateUntil) {
			if (dateSince.isBefore(HODateTime.now().minus(90, ChronoUnit.DAYS))){
				dateUntil = dateSince.plus(90, ChronoUnit.DAYS);
			}
			else {
				dateUntil = null;	// until now
			}
			var mc = Connector.instance();
			try {
				var xml = mc.getMatchesArchive(SourceSystem.YOUTH, youthteamid, dateSince, dateUntil);
				var youthMatches = XMLMatchArchivParser.parseMatchesFromString(xml);
				for ( var match: youthMatches){
					MatchLineup lineup = downloadMatchlineup(match.getMatchID(), match.getMatchType(), match.getHomeTeamID(), match.getGuestTeamID());
					if (lineup != null) {
						var details = downloadMatchDetails(match.getMatchID(), match.getMatchType(), lineup);
						DBManager.instance().storeMatchDetails(details);
						DBManager.instance().storeMatchLineup(lineup, youthteamid);
						lineup.setMatchDetails(details);
						model.addYouthMatchLineup(lineup);
					}
				}
			} catch (IOException e) {
				HOLogger.instance().error(OnlineWorker.class, "Error downloading youth match data: " + e);
			}
		}
	}

	public static void downloadNtTeams(List<NtTeamDetails> ntTeams, List<MatchKurzInfo> matches) {
		var ret = new ArrayList<NtTeamDetails>();
		for ( var team : ntTeams){
			ret.add(downloadNtTeam(team.getTeamId()));
		}
		for ( var match : matches){
			for ( var teamid: match.getTeamIds()) {
				if ( teamid != HOVerwaltung.instance().getModel().getBasics().getTeamId()){
					// not the own team
					var found = ret.stream().anyMatch(t->t.getTeamId()==teamid);
					if (!found){
						// new team in match found
						ret.add(downloadNtTeam(teamid));
					}
				}
			}
		}
	}

	private static NtTeamDetails downloadNtTeam(int teamId) {
		var xml = Connector.instance().downloadNtTeamDetails(teamId);
		NtTeamDetails details = new NtTeamDetails();
		details.parseDetails(xml);
		details.setHrfId(DBManager.instance().getLatestHRF().getHrfId());
		DBManager.instance().storeNtTeamDetails(details);
		return details;
	}

	public static Player downloadPlayerDetails(String playerID) {
		var xml = Connector.instance().downloadPlayerDetails(playerID);
		return new XMLPlayersParser().parsePlayerDetailsFromString(xml);
	}

	/**
	 * A player-details download read as market data, so it works for somebody else's player.
	 * {@link #downloadPlayerDetails} returns null for those.
	 * <p>
	 * Silent: both callers ({@code TransferListingResolver.observe}, backing the sale/character/
	 * skill-fill passes and {@code backfill_character}; {@code TransferTools.observeForScout},
	 * backing {@code add_to_scout}) already treat a failed lookup as best-effort - logged and
	 * skipped, never surfaced. Without this, any CHPP error on this file - reproduced as error 56,
	 * plausibly Hattrick refusing to answer for a player mid-match, though this project has no
	 * confirmed mapping for that code - pops a blocking modal dialog for every failure. Harmless for
	 * the single call {@code add_to_scout} makes per player; not for {@code backfill_character},
	 * which can look up dozens of players in one call and would freeze on each one in turn, waiting
	 * for someone at the keyboard to click through it.
	 */
	public static XMLPlayersParser.MarketPlayer downloadMarketPlayer(String playerID) {
		var silent = Connector.instance().setSilentDownload(true);
		try {
			var xml = Connector.instance().downloadPlayerDetails(playerID);
			return new XMLPlayersParser().parseMarketPlayerFromString(xml);
		} finally {
			Connector.instance().setSilentDownload(silent);
		}
	}

	public static void downloadTeamLogo(Map<String, String> team) {
		String url = team.get("LogoURL");
		if (StringUtils.isEmpty(url)) return;
		var teamIdString = team.get("TeamID");
		var teamId = NumberUtils.toInt(teamIdString);

		DBManager.instance().storeTeamLogoInfo(teamId, url, null);
		var logoFilename = ThemeManager.instance().getTeamLogoFilename(teamId);
		if (logoFilename != null &&
				teamId == HOVerwaltung.instance().getModel().getBasics().getTeamId() &&
				!logoFilename.equals(UserManager.instance().getCurrentUser().getClubLogo())) {
			UserManager.instance().getCurrentUser().setClubLogo(logoFilename);
			UserManager.instance().save();
		}
	}

	public static MatchLineupTeam downloadLastLineup(List<MatchKurzInfo> matches, int teamId){
		MatchLineupTeam matchLineupTeam = null;
		MatchLineup matchLineup = null;
		var lastFinishedMatch = matches.stream().filter(f->f.getMatchStatus()==MatchKurzInfo.FINISHED).max(MatchKurzInfo::compareTo);
		if ( lastFinishedMatch.isPresent()) {
			var match = lastFinishedMatch.get();
			var matchLineupString = Connector.instance().downloadMatchLineup(match.getMatchID(), teamId, match.getMatchType());
			if (!matchLineupString.isEmpty()) {
				matchLineup = XMLMatchLineupParser.parseMatchLineupFromString(matchLineupString);
			}
		}

		int lastAttitude = 0;
		int lastTactic = 0;
		// Identify team, important for player ratings
		if (matchLineup != null) {
			Matchdetails md = XMLMatchdetailsParser
					.parseMatchdetailsFromString(
							Connector.instance().downloadMatchdetails(matchLineup.getMatchID(),
									matchLineup.getMatchTyp()), null);

			if (matchLineup.getHomeTeamId() == teamId) {
				matchLineupTeam = matchLineup.getHomeTeam();
				if (md != null) {
					lastAttitude = md.getHomeEinstellung();
					lastTactic = md.getHomeTacticType();
				}
			} else {
				matchLineupTeam = matchLineup.getGuestTeam();
				if (md != null) {
					lastAttitude = md.getGuestEinstellung();
					lastTactic = md.getGuestTacticType();
				}
			}
			matchLineupTeam.setMatchTeamAttitude(MatchTeamAttitude.fromInt(lastAttitude));
			matchLineupTeam.setMatchTacticType(MatchTacticType.fromInt(lastTactic));
		}

		return matchLineupTeam;
	}

	public static Map<String, String> downloadNextMatchOrder(List<MatchKurzInfo> matches, int teamId) {
		try {
			// next upcoming match
			var nextMatch = matches.stream().filter(f -> f.getMatchStatus() == MatchKurzInfo.UPCOMING).min(MatchKurzInfo::compareTo);
			if ( nextMatch.isPresent()){
				var match = nextMatch.get();
				return XMLMatchOrderParser.parseMatchOrderFromString(Connector.instance().downloadMatchOrder(
						match.getMatchID(), match.getMatchType(), teamId));
			}
		}
		catch (Exception ignore) {
		}
		return new SafeInsertMap();
	}

	public static Map<String, TeamStats> downloadLeagueDetails(int leagueId) {
		String leagueDetailsXml = Connector.instance().getLeagueDetails(String.valueOf(leagueId));
		return XMLLeagueDetailsParser.parseLeagueDetails(leagueDetailsXml);
	}

	private static final Map<Integer, Map<String, TeamStats>> seriesDetailsCache = new HashMap<>();

	public static Map<String, TeamStats> getSeriesDetails(int seriesId) {
		if (!seriesDetailsCache.containsKey(seriesId)) {
			var teamStatsMap = OnlineWorker.downloadLeagueDetails(seriesId);
			seriesDetailsCache.put(seriesId, teamStatsMap);
		}
		return seriesDetailsCache.get(seriesId);
	}

	public static List<WorldDetailLeague> downloadWorldDetails() {
		try {
			var worldDetails = Connector.instance().getWorldDetails(0);
			return XMLWorldDetailsParser.parseDetails(XMLManager.parseString(worldDetails));
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

    public static List<HallOfFamePlayer> downloadHallOfFame(int teamId) {
        String xmlString = Connector.instance().downloadHallOfFamePlayers(teamId);
        return XMLPlayersParser.parseHallOfFamesPlayersFromString(xmlString);
    }
}
