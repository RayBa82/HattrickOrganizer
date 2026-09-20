package core.rating;

import core.db.DBManager;
import core.db.RatingCalibrationRow;
import core.model.HOVerwaltung;
import core.model.Team;
import core.model.enums.MatchType;
import core.model.match.IMatchDetails;
import core.model.match.MatchKurzInfo;
import core.model.match.MatchLineupPosition;
import core.model.match.Matchdetails;
import core.model.match.Weather;
import core.model.player.MatchRoleID;
import core.model.player.Player;
import core.rating.RatingPredictionModel.RatingSector;
import core.util.HOLogger;
import module.lineup.Lineup;
import module.lineup.substitution.model.MatchOrderType;

import java.time.Duration;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Measures the rating model against Hattrick: for one of our own played matches, rates the
 * starting eleven from the HRF snapshot taken just before the match and stores the result next to
 * the sector ratings Hattrick reported.
 * <p>
 * Measurement only. Nothing here changes the model or its parameters, and nothing is fitted.
 * Opponents are out of scope on purpose: HO holds no skills for other teams.
 */
public final class RatingCalibration {

    private static final RatingSector[] SECTORS = RatingSector.values();

    private RatingCalibration() {
    }

    /** A stored row, or the reason the match could not be rated. */
    public record Outcome(RatingCalibrationRow row, String skipped) {
        static Outcome skip(String reason) {
            return new Outcome(null, reason);
        }
    }

    /**
     * @param matches   our finished matches examined
     * @param stored    rows written this run
     * @param confounded of the stored rows, how many had the lineup change during the match
     * @param skipped   why matches could not be rated, reason to count
     */
    public record BackfillResult(int matches, int stored, int confounded, Map<String, Integer> skipped) {
    }

    /** Rate one match without storing it. */
    public static Outcome calibrate(int matchId, MatchType matchType) {
        final var db = DBManager.instance();
        final var details = db.loadMatchDetails(matchType.getId(), matchId);
        if (details == null || details.getMatchID() != matchId) {
            return Outcome.skip("no match details stored");
        }
        final int teamId = HOVerwaltung.instance().getModel().getBasics().getTeamId();
        final boolean home = details.getHomeTeamId() == teamId;
        if (!home && details.getGuestTeamId() != teamId) {
            return Outcome.skip("not one of our matches");
        }

        final var hrf = db.loadLatestHRFDownloadedBefore(details.getMatchDate().toDbTimestamp());
        if (hrf == null) {
            return Outcome.skip("no HRF snapshot before the match");
        }
        final Team team = db.getTeam(hrf.getHrfId());
        if (team == null) {
            return Outcome.skip("HRF snapshot has no team row");
        }
        final Map<Integer, Player> squad = db.getSpieler(hrf.getHrfId()).stream()
                .collect(Collectors.toMap(Player::getPlayerId, Function.identity(), (a, b) -> a));

        final var lineupTeam = db.loadMatchLineupTeam(matchType.getId(), matchId, teamId);
        final var played = db.getMatchLineupPlayers(matchId, matchType, teamId);

        // The stored lineup is the one at the final whistle; StartPosition/StartBehaviour hold the
        // one the match began with, which is what the sector ratings describe.
        final var lineup = new Lineup();
        final var changes = new LinkedHashSet<String>();
        final var ourPlayerIds = new HashSet<Integer>();
        int starters = 0;
        for (var p : played) {
            ourPlayerIds.add(p.getPlayerId());
            final int start = p.getStartPosition();
            if (MatchRoleID.isFieldMatchRoleId(start)) {
                final var player = squad.get(p.getPlayerId());
                if (player == null) {
                    return Outcome.skip("starter " + p.getPlayerId() + " not in the HRF snapshot");
                }
                lineup.setPosition(new MatchLineupPosition(start, (byte) p.getStartBehavior(), player));
                starters++;
                if (p.getRoleId() != start || p.getBehaviour() != p.getStartBehavior()) {
                    changes.add("starter moved or replaced");
                }
            } else if (p.isFieldMatchRoleId()) {
                changes.add("substitute came on");
            }
        }
        if (starters == 0) {
            return Outcome.skip("no starting lineup stored");
        }
        if (lineupTeam != null) {
            for (var s : lineupTeam.getSubstitutions()) {
                if (s.getOrderType() == MatchOrderType.MAN_MARKING) {
                    changes.add("man marking order");
                }
            }
        }
        final var highlights = details.getHighlights();
        if (highlights != null) {
            for (var hl : highlights) {
                if ((hl.isInjured() || hl.isRedCard()) && ourPlayerIds.contains(hl.getPlayerId())) {
                    changes.add(hl.isRedCard() ? "red card" : "injury");
                }
            }
        }

        final var info = db.getMatchesKurzInfoByMatchID(matchId, matchType);
        lineup.setTacticType(home ? details.getHomeTacticType() : details.getGuestTacticType());
        lineup.setAttitude(home ? details.getHomeEinstellung() : details.getGuestEinstellung());
        if (lineupTeam != null) {
            lineup.setStyleOfPlay(lineupTeam.getLineup().getCoachModifier());
        }
        lineup.setLocation(location(home, info));
        lineup.setWeather(Weather.getById(details.getWetterId()));

        final var model = newModel(team);
        final var row = new RatingCalibrationRow();
        row.setMatchId(matchId);
        row.setMatchType(matchType.getId());
        row.setMatchDate(details.getMatchDate());
        row.setHrfId(hrf.getHrfId());
        row.setSnapshotAgeDays(Duration.between(hrf.getDatum().getInstant(),
                details.getMatchDate().getInstant()).toMinutes() / 1440.0);
        row.setHome(home);
        row.setFormation(details.getFormation(home));
        row.setTactic(lineup.getTacticType());
        row.setStyleOfPlay(lineup.getCoachModifier());
        row.setAttitude(lineup.getAttitude());
        row.setWeatherId(details.getWetterId());
        row.setLocation(lineup.getLocation());
        row.setTeamSpirit(team.getTeamSpirit());
        row.setConfidence(team.getConfidence());
        for (int i = 0; i < SECTORS.length; i++) {
            final int raw = rawReal(details, home, SECTORS[i]);
            if (raw <= 0) {
                return Outcome.skip("match details hold no sector ratings");
            }
            row.getPredicted()[i] = model.getAverageRating(lineup, SECTORS[i], 90);
            row.getReal()[i] = (raw - 1) / 4.0 + 1;
            row.getResidual()[i] = row.getReal()[i] - row.getPredicted()[i];
        }
        row.setConfounded(!changes.isEmpty());
        row.setConfoundedReason(changes.isEmpty() ? null : String.join(", ", changes));
        return new Outcome(row, null);
    }

    /** Rate one match and store the row. Never throws: it runs inside the match download. */
    public static void calibrateAndStore(int matchId, MatchType matchType) {
        try {
            final var outcome = calibrate(matchId, matchType);
            if (outcome.row() != null) {
                DBManager.instance().storeRatingCalibration(outcome.row());
            }
        } catch (Exception e) {
            HOLogger.instance().error(RatingCalibration.class,
                    "rating calibration of match " + matchId + " failed: " + e);
        }
    }

    /**
     * Rate every finished match of ours. Matches that already have a row are left alone unless
     * {@code recompute}, which a change to the model or to the flagging needs.
     */
    public static BackfillResult backfill(boolean recompute) {
        final var db = DBManager.instance();
        final int teamId = HOVerwaltung.instance().getModel().getBasics().getTeamId();
        final var skipped = new TreeMap<String, Integer>();
        int matches = 0;
        int stored = 0;
        int confounded = 0;
        for (var info : db.getMatchesKurzInfo(teamId)) {
            if (info.getMatchStatus() != MatchKurzInfo.FINISHED) {
                continue;
            }
            matches++;
            if (!recompute && db.hasRatingCalibration(info.getMatchID(), info.getMatchType().getId())) {
                skipped.merge("already stored", 1, Integer::sum);
                continue;
            }
            try {
                final var outcome = calibrate(info.getMatchID(), info.getMatchType());
                if (outcome.row() == null) {
                    skipped.merge(outcome.skipped(), 1, Integer::sum);
                    continue;
                }
                db.storeRatingCalibration(outcome.row());
                stored++;
                if (outcome.row().isConfounded()) {
                    confounded++;
                }
            } catch (Exception e) {
                HOLogger.instance().error(RatingCalibration.class,
                        "rating calibration of match " + info.getMatchID() + " failed: " + e);
                skipped.merge("error: " + e, 1, Integer::sum);
            }
        }
        return new BackfillResult(matches, stored, confounded, skipped);
    }

    private static short location(boolean home, MatchKurzInfo info) {
        if (info != null && info.isNeutral()) {
            return IMatchDetails.LOCATION_NEUTRAL;
        }
        if (home) {
            return IMatchDetails.LOCATION_HOME;
        }
        return info != null && info.isDerby() ? IMatchDetails.LOCATION_AWAYDERBY : IMatchDetails.LOCATION_AWAY;
    }

    /**
     * The model class the user has selected (possibly a Groovy one), on the snapshot's team so its
     * spirit and confidence are the ones of the day, and with caches of its own.
     */
    private static RatingPredictionModel newModel(Team team) {
        final var shared = HOVerwaltung.instance().getModel().getRatingPredictionModel();
        try {
            return shared.getClass().getConstructor(Team.class).newInstance(team);
        } catch (ReflectiveOperationException e) {
            HOLogger.instance().log(RatingCalibration.class, e);
            return new RatingPredictionModel(team);
        }
    }

    /** Hattrick's raw quarter-point integer for the sector, from our side of the match. */
    private static int rawReal(Matchdetails d, boolean home, RatingSector s) {
        return switch (s) {
            case DEFENCE_LEFT -> home ? d.getHomeLeftDef() : d.getGuestLeftDef();
            case DEFENCE_CENTRAL -> home ? d.getHomeMidDef() : d.getGuestMidDef();
            case DEFENCE_RIGHT -> home ? d.getHomeRightDef() : d.getGuestRightDef();
            case MIDFIELD -> home ? d.getHomeMidfield() : d.getGuestMidfield();
            case ATTACK_LEFT -> home ? d.getHomeLeftAtt() : d.getGuestLeftAtt();
            case ATTACK_CENTRAL -> home ? d.getHomeMidAtt() : d.getGuestMidAtt();
            case ATTACK_RIGHT -> home ? d.getHomeRightAtt() : d.getGuestRightAtt();
        };
    }
}
