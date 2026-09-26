package core.mcp;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import core.constants.player.PlayerSkill;
import core.db.DBManager;
import core.model.HOModel;
import core.model.enums.MatchType;
import core.model.match.MatchKurzInfo;
import core.model.match.MatchLineupTeam;
import core.model.match.Matchdetails;
import core.model.player.Player;
import core.net.ChppWriteService;
import core.training.FutureTrainingManager;
import core.training.TrainingPerWeek;
import core.training.WeeklyTrainingType;
import core.util.HODateTime;

import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What HO's training engine knows: the settings the team trains under, what those settings do to
 * each player over the coming weeks, and - out of the HRF snapshot history - what training has
 * already done to him.
 */
public final class TrainingTools {

    /** How many weeks ahead to predict when the caller does not say. */
    private static final int DEFAULT_WEEKS = 8;

    /** How far back {@code get_player_history} looks when the caller does not say. */
    private static final int DEFAULT_HISTORY_WEEKS = 26;
    static final int MAX_EVENT_PLAYERS = 5;

    /** A Hattrick year, which is what a growth rate is quoted per. */
    private static final int HT_WEEKS_PER_YEAR = 16;

    /**
     * A hole in the snapshot series worth reporting. Training resolves weekly, so anything longer
     * than eight days leaves at least one training update unobserved - which is precisely when a
     * level-up cannot be dated and a rate cannot be trusted.
     */
    private static final int GAP_DAYS = 8;

    /** Below this observed span a growth rate is arithmetic noise rather than a trend. */
    private static final double MIN_RATE_WEEKS = 3.0;

    /**
     * The skills a snapshot series is diffed on: the seven that make ratings, plus stamina, which
     * the stamina share trains. Form is weather, and experience is matches rather than training.
     */
    private static final PlayerSkill[] TRACKED_SKILLS = {
            PlayerSkill.KEEPER, PlayerSkill.DEFENDING, PlayerSkill.PLAYMAKING, PlayerSkill.WINGER,
            PlayerSkill.PASSING, PlayerSkill.SCORING, PlayerSkill.SETPIECES, PlayerSkill.STAMINA};

    private TrainingTools() {
    }

    private static JsonObject trainingEvents(int playerId) throws McpToolException {
        final core.file.xml.XMLTrainingEventsParser.TrainingEvents events;
        try {
            events = ChppWriteService.instance().trainingEventsForAgent(playerId);
        } catch (ChppWriteService.ChppWriteException e) {
            throw new McpToolException(e.getMessage());
        }
        final var model = core.model.HOVerwaltung.instance().getModel();
        return trainingEventsJson(playerId, events,
                model == null ? null : model.getBasics().getSeasonOffset());
    }

    /**
     * {@code seasonOffset} turns the local season Hattrick reports into the global one
     * {@link HODateTime#fromHTWeek} counts in; null leaves weekStart out.
     */
    static JsonObject trainingEventsJson(int playerId,
            core.file.xml.XMLTrainingEventsParser.TrainingEvents events, Integer seasonOffset) {
        final var sorted = new ArrayList<>(events.events());
        sorted.sort(java.util.Comparator
                .comparingInt(core.file.xml.XMLTrainingEventsParser.TrainingEvent::season)
                .thenComparingInt(core.file.xml.XMLTrainingEventsParser.TrainingEvent::matchRound)
                .thenComparingInt(core.file.xml.XMLTrainingEventsParser.TrainingEvent::dayNumber)
                .reversed());
        final var rows = new JsonArray();
        for (final var e : sorted) {
            final var row = new JsonObject();
            row.addProperty("skill", chppSkillName(e.skillId()));
            row.addProperty("oldLevel", e.oldLevel());
            row.addProperty("newLevel", e.newLevel());
            row.addProperty("season", e.season());
            row.addProperty("matchRound", e.matchRound());
            row.addProperty("dayNumber", e.dayNumber());
            if (seasonOffset != null && e.season() > 0 && e.matchRound() > 0) {
                row.addProperty("weekStart", HODateTime.fromHTWeek(new HODateTime.HTWeek(
                        e.season() - seasonOffset, e.matchRound())).toHT());
            }
            rows.add(row);
        }
        final var out = new JsonObject();
        out.addProperty("playerId", playerId);
        out.addProperty("available", events.available());
        out.add("events", rows);
        return out;
    }

    /** CHPP SkillID (datatypes.md), which is not HO's PlayerSkill numbering. */
    static String chppSkillName(int skillId) {
        return switch (skillId) {
            case 1 -> "keeper";
            case 2 -> "stamina";
            case 3 -> "setpieces";
            case 4 -> "defending";
            case 5 -> "scoring";
            case 6 -> "winger";
            case 7 -> "passing";
            case 8 -> "playmaking";
            case 9 -> "trainer";
            case 10 -> "leadership";
            case 11 -> "experience";
            default -> "unknown_" + skillId;
        };
    }

    public static List<McpTool> tools() {
        return List.of(
                new McpTool("get_training",
                        // 400 chars is the hard ceiling McpServerToolsTest enforces. The
                        // precedence line earns its space: skillChanges and currentWeek contradict
                        // each other by construction, and nothing in the payload says which wins.
                        "Training: what is trained, at which intensity and stamina share, "
                                + "which positions get full or partial training, and predicted "
                                + "skill changes per player. skillChanges assume 90 minutes "
                                + "played; currentWeek has the minutes each player ALREADY banked this "
                                + "training week, plus every fixture in it. Where they disagree "
                                + "currentWeek wins - read it before any lineup. "
                                + "status: 'ok'|'no_future_training_plan'.",
                        McpTool.schema(McpTool.properties(
                                "weeks", McpTool.prop("integer",
                                        "how many weeks to predict, default " + DEFAULT_WEEKS
                                                + "; capped at the number of future weeks HO has planned"),
                                "playerIds", McpTool.prop("array",
                                        "restrict the prediction to these player ids; the whole squad if omitted"))),
                        TrainingTools::training),
                new McpTool("get_player_history",
                        "What training has already done to a player, from HO's own snapshot "
                                + "history - a dated skill series no CHPP file gives you. Per "
                                + "player: every level-up with its date, a growth rate in levels per "
                                + "Hattrick year, TSI and wage drift, and the series coverage. Read "
                                + "coverage before the rate - a download gap means a level-up "
                                + "inside it cannot be dated. Local unless includeEvents.",
                        McpTool.schema(McpTool.properties(
                                "playerIds", McpTool.prop("array",
                                        "the player ids to report on; the current squad if omitted. "
                                                + "An id that has left the squad still works - the "
                                                + "snapshots outlive the player"),
                                "weeks", McpTool.prop("integer",
                                        "how far back to look, in weeks; default "
                                                + DEFAULT_HISTORY_WEEKS),
                                "includeEvents", McpTool.prop("boolean",
                                        "add trainingEvents per player: Hattrick's own skill changes "
                                                + "(skill, old -> new level, season / matchRound / "
                                                + "dayNumber, weekStart), which date level-ups a "
                                                + "download gap hides. One CHPP request per player, "
                                                + "at most " + MAX_EVENT_PLAYERS + " playerIds; "
                                                + "empty while the player is in a match. Default false"),
                                "includeSnapshots", McpTool.prop("boolean",
                                        "add the raw dated series per player; default false, "
                                                + "because levelUps, derived and coverage answer "
                                                + "the growth question on their own and the series "
                                                + "is a wall of numbers"))),
                        TrainingTools::playerHistory),
                new McpTool("set_training",
                        "Set the senior team's training. Call get_training first for the current "
                                + "values and for what each trainingType id trains. This leaves the "
                                + "machine and changes training in Hattrick immediately. HO's local "
                                + "snapshot is NOT refreshed until the next team download.",
                        McpTool.schema(McpTool.properties(
                                        "trainingType", McpTool.prop("integer",
                                                "training type id; see get_training for the meanings"),
                                        "intensity", McpTool.prop("integer",
                                                "training intensity, 0..100"),
                                        "staminaShare", McpTool.prop("integer",
                                                "stamina share, 5..100")),
                                "trainingType", "intensity", "staminaShare"),
                        TrainingTools::setTraining)
        );
    }

    private static JsonObject setTraining(JsonObject args) throws McpToolException {
        final var trainingType = McpJson.reqInt(args, "trainingType");
        final var intensity = McpJson.reqInt(args, "intensity");
        final var staminaShare = McpJson.reqInt(args, "staminaShare");
        if (intensity < 0 || intensity > 100) {
            throw new IllegalArgumentException("intensity must be 0..100, was " + intensity);
        }
        if (staminaShare < 5 || staminaShare > 100) {
            throw new IllegalArgumentException("staminaShare must be 5..100, was " + staminaShare);
        }
        final ChppWriteService.TrainingCommitted committed;
        try {
            committed = ChppWriteService.instance()
                    .setTrainingForAgent(trainingType, intensity, staminaShare);
        } catch (ChppWriteService.ChppWriteException e) {
            throw new McpToolException(e.getMessage());
        }
        final var out = new JsonObject();
        out.addProperty("status", "ok");
        final var c = new JsonObject();
        c.addProperty("trainingType", committed.trainingType());
        c.addProperty("intensity", committed.intensity());
        c.addProperty("staminaShare", committed.staminaShare());
        c.addProperty("intensityChangePending", committed.intensityChangePending());
        out.add("committed", c);
        final var note = new StringBuilder("HO's snapshot is not refreshed until the next team download.");
        if (committed.intensityChangePending()) {
            note.append(" The training intensity change takes effect at the next training update; "
                    + "'intensity' above is the pending value Hattrick will apply.");
        }
        out.addProperty("note", note.toString());
        return out;
    }

    private static JsonObject training(JsonObject args) throws McpToolException {
        final var model = McpJson.model();
        final var json = new JsonObject();
        final var wanted = wantedPlayerIds(args);
        json.add("current", current(model));
        json.add("currentWeek", currentWeek(model, wanted));

        // The future training plan is what the Training tab edits; it is empty until the user has
        // opened that tab once, and then predictions are simply unavailable.
        final var plan = DBManager.instance().getFutureTrainingsVector();
        final var weeks = Math.min(McpJson.optInt(args, "weeks", DEFAULT_WEEKS), plan.size());
        json.addProperty("predictedWeeks", weeks);
        if (weeks <= 0) {
            json.addProperty("status", "no_future_training_plan");
            json.addProperty("note", "HO has no future training weeks planned, so no skill "
                    + "prediction is possible. Open the Training tab in HO once to create the plan.");
            return json;
        }
        json.addProperty("status", "ok");

        final var players = new JsonArray();
        for (final var player : model.getCurrentPlayers()) {
            if (player.isCoach()) {
                continue;
            }
            if (wanted != null && !wanted.contains(player.getPlayerId())) {
                continue;
            }
            players.add(prediction(player, plan, weeks));
        }
        json.add("players", players);
        return json;
    }

    /**
     * get_player_history: the stored snapshot series, diffed.
     * <p>
     * The series is whatever HRFs the user downloaded - irregular by nature, and with holes wherever
     * he did not play for a while. Every number derived here is therefore reported next to the
     * coverage it was derived from, and a rate is left null rather than quoted off a span too short
     * to mean anything.
     */
    private static JsonObject playerHistory(JsonObject args) throws McpToolException {
        final var model = McpJson.model();
        final var weeks = Math.max(1, McpJson.optInt(args, "weeks", DEFAULT_HISTORY_WEEKS));
        final var includeSnapshots = McpJson.optBool(args, "includeSnapshots", false);
        final var asOf = HODateTime.now();
        final var since = asOf.minus(weeks * 7, ChronoUnit.DAYS);

        // What is being trained now, which is the skill a growth rate is worth quoting for. It can
        // have been something else earlier in the window - levelUps shows where the growth went.
        final var training = model.getTraining();
        final var weekly = training == null
                ? null : WeeklyTrainingType.instance(training.getTrainingType());
        final var trained = weekly == null ? null : weekly.getPrimaryTrainingSkill();

        final var json = new JsonObject();
        json.addProperty("asOf", asOf.toHT());
        json.addProperty("weeks", weeks);
        json.addProperty("currency", McpJson.currency());
        json.addProperty("trainedSkill", trained == null ? null : String.valueOf(trained));
        json.addProperty("secondarySkill", weekly == null || weekly.getSecondaryTrainingSkill() == null
                ? null : String.valueOf(weekly.getSecondaryTrainingSkill()));

        final var players = new JsonArray();
        final var ids = historyPlayerIds(args, model);
        final var includeEvents = McpJson.optBool(args, "includeEvents", false);
        if (includeEvents && ids.size() > MAX_EVENT_PLAYERS) {
            throw new IllegalArgumentException("includeEvents costs one CHPP request per player, so "
                    + "it takes at most " + MAX_EVENT_PLAYERS + " playerIds, had " + ids.size() + ".");
        }
        for (final var playerId : ids) {
            final var row = history(playerId, since, trained, includeSnapshots);
            if (includeEvents) {
                row.add("trainingEvents", trainingEvents(playerId));
            }
            players.add(row);
        }
        json.add("players", players);
        json.addProperty("note", "levelsPerHtYear is the trained skill's sub-skill-inclusive gain "
                + "between coverage.firstDate and coverage.lastDate, divided by coverage."
                + "observedWeeks and multiplied by " + HT_WEEKS_PER_YEAR + ". It is null when "
                + "there is no trained skill, fewer than two snapshots, or an observed span under "
                + MIN_RATE_WEEKS + " weeks. Check derived.rateBasis before using it: 'subskill' "
                + "means HO had sub-skill estimates for this player and the rate is measured to "
                + "fractions of a level, 'integer' means it has none and the rate is really a "
                + "count of whole levels - same-looking number, much blunter. "
                + "weeksSinceLastLevelUp is measured from the last "
                + "snapshot, not from today - compare coverage.lastDate against asOf before "
                + "calling a player stalled. An empty levelUps over a long observedWeeks is the "
                + "real 'growth has stopped' signal; an empty one next to a short span, or one "
                + "with gaps, means it cannot be told.");
        return json;
    }

    /** The explicit ids in the caller's order, else every non-coach in the current squad. */
    private static List<Integer> historyPlayerIds(JsonObject args, HOModel model) {
        final var ids = new ArrayList<Integer>();
        if (args.has("playerIds") && !args.get("playerIds").isJsonNull()) {
            for (final var element : args.getAsJsonArray("playerIds")) {
                ids.add(element.getAsInt());
            }
            return ids;
        }
        for (final var player : model.getCurrentPlayers()) {
            if (!player.isCoach()) {
                ids.add(player.getPlayerId());
            }
        }
        return ids;
    }

    private static JsonObject history(int playerId, HODateTime since, PlayerSkill trained,
                                      boolean includeSnapshots) {
        // Ordered by date ascending, which everything below relies on.
        final var series = DBManager.instance().loadPlayerHistory(playerId).stream()
                .filter(p -> p.getHrfDate() != null && !p.getHrfDate().isBefore(since))
                .toList();
        return historyOf(playerId, series, trained, includeSnapshots);
    }

    /**
     * One player's report over an already-selected series, oldest snapshot first. Split from the
     * database read above so the diffing and the coverage arithmetic can be tested without one.
     */
    static JsonObject historyOf(int playerId, List<Player> series, PlayerSkill trained,
                                boolean includeSnapshots) {
        final var json = new JsonObject();
        json.addProperty("playerId", playerId);
        if (series.isEmpty()) {
            json.addProperty("status", "no_history");
            json.addProperty("note", "HO holds no snapshot of this player inside the window. Either "
                    + "the id is wrong, or he arrived and left before it, or no team download "
                    + "happened in it.");
            return json;
        }

        final var first = series.get(0);
        final var last = series.get(series.size() - 1);
        final var observedWeeks = weeksBetween(first.getHrfDate(), last.getHrfDate());

        // Identity off the newest snapshot, so a player who has since been sold still has a name.
        json.addProperty("status", series.size() < 2 ? "single_snapshot" : "ok");
        json.addProperty("name", last.getFullName());
        json.addProperty("age", last.getAge());
        json.addProperty("ageDays", last.getAgeDays());

        json.add("levelUps", levelUps(series));
        json.add("derived", derived(series, trained, observedWeeks));
        json.add("coverage", coverage(series, first, last, observedWeeks));
        if (includeSnapshots) {
            final var snapshots = new JsonArray();
            for (final var snapshot : series) {
                snapshots.add(snapshot(snapshot));
            }
            json.add("snapshots", snapshots);
        }
        return json;
    }

    /**
     * Every whole-level change between consecutive snapshots, in order. Drops are reported too -
     * skills do fall back with age, and a drop is as much a sell signal as a rise.
     */
    private static JsonArray levelUps(List<Player> series) {
        final var changes = new JsonArray();
        for (int i = 1; i < series.size(); i++) {
            final var before = series.get(i - 1);
            final var after = series.get(i);
            for (final var skill : TRACKED_SKILLS) {
                final var from = before.getValue4Skill(skill);
                final var to = after.getValue4Skill(skill);
                if (from == to) {
                    continue;
                }
                final var change = new JsonObject();
                change.addProperty("skill", String.valueOf(skill));
                change.addProperty("from", from);
                change.addProperty("to", to);
                // The date of the snapshot that first showed it, which is the earliest the change
                // can be proven - not necessarily the training update that caused it.
                change.addProperty("date", after.getHrfDate().toHT());
                change.addProperty("ageAtChange", after.getAge());
                changes.add(change);
            }
        }
        return changes;
    }

    private static JsonObject derived(List<Player> series, PlayerSkill trained,
                                      double observedWeeks) {
        final var first = series.get(0);
        final var last = series.get(series.size() - 1);

        final var json = new JsonObject();
        json.addProperty("trainedSkill", trained == null ? null : String.valueOf(trained));
        if (trained != null) {
            final var start = precise(first, trained);
            final var end = precise(last, trained);
            json.addProperty("trainedSkillStart", round2(start));
            json.addProperty("trainedSkillEnd", round2(end));
            json.addProperty("trainedSkillLevelUps", countLevelUps(series, trained));
            // Two snapshots a day apart would give a rate in the hundreds. Refusing to answer is
            // the whole point of the coverage block.
            final Double rate = observedWeeks >= MIN_RATE_WEEKS
                    ? round2((end - start) / observedWeeks * HT_WEEKS_PER_YEAR)
                    : null;
            json.addProperty("levelsPerHtYear", rate);
            json.addProperty("rateBasis", rateBasis(series, trained));
            json.addProperty("weeksSinceLastLevelUp", weeksSinceLastLevelUp(series, trained));
        }
        json.add("tsi", startEnd(first.getTsi(), last.getTsi()));
        json.add("wage", startEnd(McpJson.money(first.getWage()), McpJson.money(last.getWage())));
        return json;
    }

    /**
     * What the start, end and rate above are actually made of.
     * <p>
     * Sub-skills are not downloaded, they are HO's own estimates from replaying the training
     * history, and a player HO has never replayed carries zero in every one of them. The rate then
     * silently becomes a count of whole levels while still coming back as a plausible float - and
     * no caller can tell from the numbers, because a genuine 5.00 and an estimate-less 5 are the
     * same value. Hence a field saying which it is.
     * <p>
     * Asked of the whole series rather than of the two endpoints: the replay populates every row
     * for a player at once, so this is all-or-nothing in practice, and testing only the endpoints
     * would call a player who happens to have just levelled at both ends estimate-less.
     */
    private static String rateBasis(List<Player> series, PlayerSkill trained) {
        for (final var snapshot : series) {
            if (snapshot.getSub4Skill(trained) > 0) {
                return "subskill";
            }
        }
        return "integer";
    }

    /**
     * How long the trained skill has been flat, measured from the newest snapshot rather than from
     * today - the series may end weeks ago, and {@code coverage.daysSinceLastSnapshot} says so.
     */
    private static Double weeksSinceLastLevelUp(List<Player> series, PlayerSkill trained) {
        HODateTime latest = null;
        for (int i = 1; i < series.size(); i++) {
            if (series.get(i).getValue4Skill(trained) != series.get(i - 1).getValue4Skill(trained)) {
                latest = series.get(i).getHrfDate();
            }
        }
        return latest == null
                ? null
                : round2(weeksBetween(latest, series.get(series.size() - 1).getHrfDate()));
    }

    private static int countLevelUps(List<Player> series, PlayerSkill trained) {
        var count = 0;
        for (int i = 1; i < series.size(); i++) {
            if (series.get(i).getValue4Skill(trained) > series.get(i - 1).getValue4Skill(trained)) {
                count++;
            }
        }
        return count;
    }

    /**
     * What the series can and cannot support. The gaps are the load-bearing part: a level-up inside
     * one cannot be dated, and a series that ends well before {@code asOf} says nothing about now.
     */
    private static JsonObject coverage(List<Player> series, Player first, Player last,
                                       double observedWeeks) {
        final var json = new JsonObject();
        json.addProperty("snapshotCount", series.size());
        // The player's real first snapshot in the window, not the window start - he may have been
        // bought halfway through it.
        json.addProperty("firstDate", first.getHrfDate().toHT());
        json.addProperty("lastDate", last.getHrfDate().toHT());
        json.addProperty("observedWeeks", round2(observedWeeks));
        json.addProperty("daysSinceLastSnapshot",
                round2(weeksBetween(last.getHrfDate(), HODateTime.now()) * 7));

        final var gaps = new JsonArray();
        for (int i = 1; i < series.size(); i++) {
            final var from = series.get(i - 1).getHrfDate();
            final var to = series.get(i).getHrfDate();
            final var days = weeksBetween(from, to) * 7;
            if (days > GAP_DAYS) {
                final var gap = new JsonObject();
                gap.addProperty("from", from.toHT());
                gap.addProperty("to", to.toHT());
                gap.addProperty("days", round2(days));
                gaps.add(gap);
            }
        }
        json.add("gaps", gaps);
        return json;
    }

    /** One stored snapshot: the skills carry their sub-level, so the curve is smooth. */
    private static JsonObject snapshot(Player player) {
        final var json = new JsonObject();
        json.addProperty("date", player.getHrfDate().toHT());
        json.addProperty("age", player.getAge());
        json.addProperty("ageDays", player.getAgeDays());
        json.addProperty("tsi", player.getTsi());
        json.addProperty("wage", McpJson.money(player.getWage()));
        json.addProperty("form", player.getForm());
        final var skills = new JsonObject();
        for (final var skill : TRACKED_SKILLS) {
            skills.addProperty(String.valueOf(skill), round2(precise(player, skill)));
        }
        json.add("skills", skills);
        return json;
    }

    private static JsonObject startEnd(long start, long end) {
        final var json = new JsonObject();
        json.addProperty("start", start);
        json.addProperty("end", end);
        json.addProperty("change", end - start);
        return json;
    }

    /** The skill with HO's sub-skill estimate on top - the whole point of asking HO and not CHPP. */
    private static double precise(Player player, PlayerSkill skill) {
        return player.getValue4Skill(skill) + player.getSub4Skill(skill);
    }

    private static double weeksBetween(HODateTime from, HODateTime to) {
        return (HODateTime.toEpochSecond(to) - HODateTime.toEpochSecond(from)) / (7d * 24 * 3600);
    }

    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private static Set<Integer> wantedPlayerIds(JsonObject args) {
        if (!args.has("playerIds") || args.get("playerIds").isJsonNull()) {
            return null;
        }
        final var ids = new HashSet<Integer>();
        for (final var element : args.getAsJsonArray("playerIds")) {
            ids.add(element.getAsInt());
        }
        return ids;
    }

    /** The training settings themselves, as HO derived them from the latest download. */
    private static JsonObject current(HOModel model) {
        final var json = new JsonObject();
        final var training = model.getTraining();
        if (training == null) {
            json.addProperty("status", "no_training_settings");
            json.addProperty("note", "No training settings in the current snapshot.");
            return json;
        }
        json.addProperty("status", "ok");
        json.addProperty("trainingType", training.getTrainingType());
        json.addProperty("intensity", training.getTrainingIntensity());
        json.addProperty("staminaShare", training.getStaminaShare());
        json.addProperty("coachLevel", training.getCoachLevel());
        json.addProperty("assistantsLevel", training.getTrainingAssistantsLevel());
        // trainingDate is the update that CLOSED the week reported here - HOModel.getTraining()
        // deliberately steps back one week. The week now in progress is under "currentWeek".
        final var trainingDate = training.getTrainingDate();
        json.addProperty("trainingDate", trainingDate == null ? null : trainingDate.toHT());
        json.addProperty("weekStart",
                trainingDate == null ? null : trainingDate.minus(7, ChronoUnit.DAYS).toHT());
        json.addProperty("weekEnd", trainingDate == null ? null : trainingDate.toHT());
        final var xtra = model.getXtraDaten();
        final var nextTrainingDate = xtra == null ? null : xtra.getTrainingDateAfterWeeks(0);
        json.addProperty("nextTrainingDate",
                nextTrainingDate == null ? null : nextTrainingDate.toHT());

        final var weekly = WeeklyTrainingType.instance(training.getTrainingType());
        if (weekly != null) {
            json.addProperty("trainingName", weekly.getName());
            json.addProperty("primarySkill", String.valueOf(weekly.getPrimaryTrainingSkill()));
            json.addProperty("secondarySkill", weekly.getSecondaryTrainingSkill() == null
                    ? null : weekly.getSecondaryTrainingSkill().toString());
            json.add("fullTrainingRoleIds", roleIds(weekly.getTrainingSkillPositions()));
            json.add("partialTrainingRoleIds", roleIds(weekly.getTrainingSkillPartlyTrainingPositions()));
        }
        return json;
    }

    private static JsonArray roleIds(int[] positions) {
        final var array = new JsonArray();
        if (positions != null) {
            for (final var position : positions) {
                array.add(position);
            }
        }
        return array;
    }

    /**
     * What the squad has already banked in the training week currently in progress.
     * <p>
     * This is the week that closes at the <em>next</em> training update, so it is not the week
     * {@link #current} reports - that one has already been applied. HO itself never computes real
     * minutes for the week in progress. The future plan's first entry <em>is</em> this week
     * (TrainingModel.adjustFutureTrainingsVector starts at nextWeekTraining + 7 days), and
     * {@code FutureTrainingManager} credits a flat 90 minutes to every player whose ideal position
     * falls in a trained sector - see {@code Player.getFuturePlayerTrainingPriority} - whether or
     * not he played. That is what makes {@code skillChanges} confidently wrong for a player who has
     * already spent his trained slot, and it is not limited to the MCP: HO's own Training tab
     * renders the same first row from the same code.
     * <p>
     * The minutes come from HO's own appearance timeline
     * ({@code MatchLineupTeam.getTrainingMinutesPlayedInSectors}), so substitutions, position
     * swaps and red cards are accounted for rather than approximated from the starting eleven.
     */
    private static JsonObject currentWeek(HOModel model, Set<Integer> wanted) {
        final var json = new JsonObject();
        final var xtra = model.getXtraDaten();
        final var team = model.getTeam();
        final var weekEnd = xtra == null ? null : xtra.getTrainingDateAfterWeeks(0);
        final var weekly = team == null
                ? null : WeeklyTrainingType.instance(team.getTrainingsArtAsInt());
        if (weekEnd == null || weekly == null) {
            json.addProperty("status", "no_training_settings");
            json.addProperty("note", "No training settings in the current snapshot, so minutes "
                    + "already played cannot be attributed to a trained sector.");
            return json;
        }

        // TrainingPerWeek loads the matches of the week ENDING at the date it is given, which is
        // exactly the window we want: everything between the last update and the next one.
        final var club = model.getClub();
        final var trainer = model.getTrainer();
        final var week = new TrainingPerWeek(weekEnd, team.getTrainingsArtAsInt(),
                team.getTrainingslevel(), team.getStaminaTrainingPart(),
                club == null ? 0 : club.getCoTrainer(),
                trainer == null ? 0 : trainer.getCoachSkill());
        json.addProperty("status", "ok");
        json.addProperty("weekStart", weekEnd.minus(7, ChronoUnit.DAYS).toHT());
        json.addProperty("weekEnd", weekEnd.toHT());
        json.add("fullTrainingRoleIds", roleIds(weekly.getTrainingSkillPositions()));

        final var teamId = model.getBasics().getTeamId();
        final var banked = new LinkedHashMap<Integer, WeekMinutes>();
        final var fixtures = new JsonArray();
        var missingLineups = 0;
        for (final var match : week.getMatches()) {
            final var played = match.getMatchStatus() == MatchKurzInfo.FINISHED;
            final var details = played ? match.getMatchdetails() : null;
            final var lineup = details == null ? null : details.getOwnTeamLineup();

            final var fixture = new JsonObject();
            fixture.addProperty("matchId", match.getMatchID());
            fixture.addProperty("matchType",
                    match.getMatchType() == null ? null : match.getMatchType().name());
            fixture.addProperty("date",
                    match.getMatchSchedule() == null ? null : match.getMatchSchedule().toHT());
            fixture.addProperty("opponent", match.getOpponentTeamName());
            fixture.addProperty("played", played);
            fixture.addProperty("lineupAvailable", lineup != null);
            fixtures.add(fixture);

            if (lineup == null) {
                if (played) {
                    missingLineups++;
                }
                continue;
            }
            addMatchMinutes(model, wanted, banked, match, details, lineup, weekly, teamId);
        }
        json.add("fixtures", fixtures);

        final var players = new JsonArray();
        for (final var entry : banked.entrySet()) {
            players.add(entry.getValue().toJson(entry.getKey()));
        }
        json.add("players", players);
        json.addProperty("lineupDataComplete", missingLineups == 0);
        json.addProperty("note", missingLineups == 0
                ? "Minutes already banked in the week now in progress; a player not listed here "
                + "has banked nothing. maxedForWeek means a full trained slot is already spent "
                + "on him - starting him in one again trains nobody. startRoleIds are starting "
                + "positions only; the minutes themselves account for substitutions and red cards."
                : missingLineups + " played match(es) in this week have no downloaded lineup, so "
                + "these minutes are UNDERSTATED and maxedForWeek is a possible FALSE NEGATIVE - "
                + "an already maxed player will look available. Download the fixtures marked "
                + "lineupAvailable:false before building a lineup on this.");
        return json;
    }

    /** Adds one match's minutes to the running per-player totals. */
    private static void addMatchMinutes(HOModel model, Set<Integer> wanted,
            Map<Integer, WeekMinutes> banked, MatchKurzInfo match, Matchdetails details,
            MatchLineupTeam lineup, WeeklyTrainingType weekly, int teamId) {
        final var walkover = details.isWalkoverMatchWin(teamId);
        // Masters counts for experience only, never for training.
        final var trains = match.getMatchType() != MatchType.MASTERS;
        for (final var player : model.getCurrentPlayers()) {
            final var playerId = player.getPlayerId();
            if (player.isCoach() || (wanted != null && !wanted.contains(playerId))) {
                continue;
            }
            // A walkover win reports 90 trained minutes but no played minutes, so all three have
            // to be read before deciding the player did not appear.
            final var playedMinutes = lineup.getTrainingMinutesPlayedInSectors(playerId, null, walkover);
            final var fullMinutes = !trains ? 0 : lineup.getTrainingMinutesPlayedInSectors(
                    playerId, weekly.getFullTrainingSectors(), walkover);
            final var partlyMinutes = !trains ? 0 : lineup.getTrainingMinutesPlayedInSectors(
                    playerId, weekly.getPartlyTrainingSectors(), walkover);
            if (playedMinutes <= 0 && fullMinutes <= 0 && partlyMinutes <= 0) {
                continue;
            }
            final var minutes = banked.computeIfAbsent(playerId,
                    key -> new WeekMinutes(player.getFullName()));
            minutes.add(fullMinutes, partlyMinutes, playedMinutes, match.getMatchID());
            final var position = lineup.getPlayerByID(playerId, true);
            if (position != null && position.getStartPosition() >= 0) {
                minutes.startRoleIds.add(position.getStartPosition());
            }
        }
    }

    /**
     * One player's running totals over the week. Each trained total caps at 90 the way
     * {@code TrainingWeekPlayer} does - a second full match in a trained slot banks nothing.
     */
    static final class WeekMinutes {

        private final String name;
        private final JsonArray matchIds = new JsonArray();
        private final JsonArray startRoleIds = new JsonArray();
        private int full;
        private int partly;
        private int played;

        WeekMinutes(String name) {
            this.name = name;
        }

        void add(int fullMinutes, int partlyMinutes, int playedMinutes, int matchId) {
            this.full = Math.min(90, this.full + fullMinutes);
            this.partly = Math.min(90, this.partly + partlyMinutes);
            this.played += playedMinutes;
            this.matchIds.add(matchId);
        }

        JsonObject toJson(int playerId) {
            final var json = new JsonObject();
            json.addProperty("playerId", playerId);
            json.addProperty("name", this.name);
            json.addProperty("fullTrainingMinutes", this.full);
            json.addProperty("partlyTrainingMinutes", this.partly);
            json.addProperty("playedMinutes", this.played);
            json.addProperty("maxedForWeek", this.full >= 90);
            json.add("matchIds", this.matchIds);
            json.add("startRoleIds", this.startRoleIds);
            return json;
        }
    }

    /**
     * One player's predicted skill changes.
     * <p>
     * The constructor previews over {@code UserParameter.futureWeeks} before we ask for our own
     * window; when the stored plan is shorter than that it logs a warning and changes nothing, so
     * the second preview below is what the numbers come from.
     */
    private static JsonObject prediction(Player player, List<TrainingPerWeek> plan, int weeks) {
        final var manager = new FutureTrainingManager(player, plan);
        manager.previewPlayer(weeks);

        final var json = new JsonObject();
        json.addProperty("playerId", player.getPlayerId());
        json.addProperty("name", player.getFullName());
        json.addProperty("trainingSpeed", manager.getTrainingSpeed());

        final var changes = new JsonArray();
        for (final var change : manager.getFutureSkillChanges()) {
            final var changeJson = new JsonObject();
            changeJson.addProperty("date", change.getDate() == null ? null : change.getDate().toHT());
            changeJson.addProperty("skill", String.valueOf(change.getType()));
            changeJson.addProperty("newValue", change.getValue());
            changeJson.addProperty("change", change.getChange());
            changes.add(changeJson);
        }
        json.add("skillChanges", changes);
        return json;
    }
}
