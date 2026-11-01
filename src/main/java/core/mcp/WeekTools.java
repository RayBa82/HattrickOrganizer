package core.mcp;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import core.db.DBManager;
import core.model.HOModel;
import core.model.enums.MatchType;
import core.model.player.IMatchRoleID;
import core.model.player.Player;
import core.rating.RatingPredictionModel;
import core.util.HODateTime;

import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/**
 * week_brief: what the hattrick skills read at the start of every week, in one call. It replaces
 * get_training, get_series, get_squad and per-fixture get_match_order, plus the scripts that
 * boiled those down. Rules are mechanical only: what trains, who is maxed, who is available.
 * The lineup choice stays with the agent.
 */
public final class WeekTools {

    private static final String PLAYER_ID = "playerId";
    private static final String PLAYER_IDS = "playerIds";
    private static final String INCLUDE_ORDERS = "includeOrders";
    private static final String POSITIONS = "positions";
    private static final String STATUS = "status";
    private static final String FIXTURES = "fixtures";
    private static final String PLAYERS = "players";
    private static final String BEST_POSITIONS = "bestPositions";
    private static final String MESSAGE = "message";
    private static final String LEVEL_UPS = "levelUps";
    private static final int TOP_POSITIONS = 3;
    private static final int SHOWN_DIFFERENCES = 5;
    private static final int ELEVEN = 11;

    private WeekTools() {
    }

    public static List<McpTool> tools() {
        return List.of(
                new McpTool("week_brief",
                        "The week in one call: training type and trained roles; every fixture of "
                                + "the training week (tournaments too) with matchTypeId, trains, "
                                + "injuriesPossible and the order Hattrick holds (players, captain "
                                + "on the pitch, hoLineupDiffers); per player banked minutes, "
                                + "maxedForWeek, top-3 positions, availability; level-ups since "
                                + "the last training. sources dates every part.",
                        McpTool.schema(McpTool.properties(
                                PLAYER_IDS, McpTool.prop("array",
                                        "only these players in players and levelUps; default "
                                                + "the whole squad"),
                                INCLUDE_ORDERS, McpTool.prop("boolean",
                                        "read the order Hattrick holds for each upcoming fixture, "
                                                + "one CHPP request each. Default true"))),
                        WeekTools::weekBrief)
                        .asTask(args -> McpJson.optBool(args, INCLUDE_ORDERS, true))
                        .withBudget("Pass playerIds for fewer player rows, or includeOrders false.",
                                McpTool.OptionalSection.perItem(PLAYERS, BEST_POSITIONS, BEST_POSITIONS))
        );
    }

    private static JsonObject weekBrief(JsonObject args) throws McpToolException {
        final var model = McpJson.model();
        final var includeOrders = McpJson.optBool(args, INCLUDE_ORDERS, true);
        final var ids = TrainingTools.historyPlayerIds(args, model);
        final var json = new JsonObject();
        json.add("sources", sources(model, includeOrders));
        json.add("training", training(model));

        final var week = TrainingTools.currentWeek(model, null, true);
        if (!"ok".equals(week.get(STATUS).getAsString())) {
            json.add("week", week);
            return json;
        }
        json.addProperty("weekStart", week.get("weekStart").getAsString());
        json.addProperty("weekEnd", week.get("weekEnd").getAsString());
        json.add("fullTrainingRoleIds", week.get("fullTrainingRoleIds"));
        final var fixtures = week.getAsJsonArray(FIXTURES);
        if (includeOrders) {
            final var teamId = model.getBasics().getTeamId();
            for (final var element : fixtures) {
                final var fixture = element.getAsJsonObject();
                if (!fixture.get("played").getAsBoolean()) {
                    fixture.add("order", order(fixture, teamId));
                }
            }
        }
        json.add(FIXTURES, fixtures);
        json.add("lineupDataComplete", week.get("lineupDataComplete"));
        json.add(PLAYERS, players(model, week.getAsJsonArray(PLAYERS), new HashSet<>(ids)));

        json.add(LEVEL_UPS, compactLevelUps(TrainingTools.sinceLastTraining(model, ids)));
        return json;
    }

    /** Where each part comes from, so the agent does not have to re-check a number. */
    private static JsonObject sources(HOModel model, boolean includeOrders) {
        final var sources = new JsonObject();
        final var basics = model.getBasics();
        sources.addProperty("snapshot", basics == null || basics.getDatum() == null
                ? null : basics.getDatum().toHT());
        sources.addProperty("training", "HO snapshot (get_training current)");
        sources.addProperty("minutes", "HO's stored match lineups (get_training currentWeek)");
        sources.addProperty("orders", includeOrders
                ? "Hattrick match orders, read " + HODateTime.now().toHT() : "not read");
        sources.addProperty(LEVEL_UPS, "HO snapshots (get_player_history sinceLastTraining)");
        return sources;
    }

    private static JsonObject training(HOModel model) {
        final var current = TrainingTools.current(model);
        final var training = new JsonObject();
        for (final var key : List.of(STATUS, "trainingType", "trainingName", "intensity",
                "staminaShare", "nextTrainingDate")) {
            if (current.has(key)) {
                training.add(key, current.get(key));
            }
        }
        return training;
    }

    /**
     * What Hattrick holds for one upcoming fixture, boiled down: how many players, whether the
     * captain is on the pitch, and whether HO's stored lineup for that match says the same. A
     * failed read is reported in the row and does not fail the brief.
     */
    private static JsonObject order(JsonObject fixture, int teamId) {
        final var order = new JsonObject();
        try {
            final var matchId = fixture.get("matchId").getAsInt();
            final var type = MatchType.getById(fixture.get("matchTypeId").getAsInt());
            final var held = LineupTools.heldOrder(matchId, type);
            if (held == null) {
                order.addProperty(STATUS, "hidden");
                return order;
            }
            order.addProperty(STATUS, "ok");
            final var field = held.getAsJsonArray(POSITIONS);
            order.addProperty("fieldPlayers", field.size());
            order.addProperty("missingFieldPlayers", Math.max(0, ELEVEN - field.size()));
            order.addProperty("benchPlayers", held.getAsJsonArray("bench").size());
            final var captain = held.get("captainId").getAsInt();
            order.addProperty("captainInStartingEleven", captain > 0 && contains(field, captain));
            addHoComparison(order, held, DBManager.instance().loadMatchLineupTeam(type.getId(), matchId, teamId));
        } catch (McpToolException | RuntimeException e) {
            order.addProperty(STATUS, "failed");
            order.addProperty(MESSAGE, e.getMessage());
        }
        return order;
    }

    static void addHoComparison(JsonObject order, JsonObject held,
                                @org.jetbrains.annotations.Nullable core.model.match.MatchLineupTeam ho) {
        if (ho == null || ho.getLineup().getFieldPositions().stream().noneMatch(p -> p.getPlayerId() > 0)) {
            order.add("hoLineupDiffers", com.google.gson.JsonNull.INSTANCE);
            order.addProperty(MESSAGE, "HO holds no lineup for this match.");
            return;
        }
        final var differences = LineupTools.orderDifferences(LineupTools.orderOf(ho.getLineup()), held);
        order.addProperty("hoLineupDiffers", !differences.isEmpty());
        if (!differences.isEmpty()) {
            // "sent" in these lines is HO's lineup; the first few say enough to act on.
            final var shown = new JsonArray();
            for (var i = 0; i < differences.size() && i < SHOWN_DIFFERENCES; i++) {
                shown.add(differences.get(i).getAsString().replace("sent ", "HO has "));
            }
            order.add("differences", shown);
            order.addProperty("differenceCount", differences.size());
        }
    }

    private static boolean contains(JsonArray positions, int playerId) {
        for (final var element : positions) {
            if (element.getAsJsonObject().get(PLAYER_ID).getAsInt() == playerId) {
                return true;
            }
        }
        return false;
    }

    /**
     * One compact row per player: defaults are left out (no injury, no cards, not listed, no
     * group, no minutes), so a fit squad stays short.
     */
    private static JsonArray players(HOModel model, JsonArray weekRows, java.util.Set<Integer> wanted)
            throws McpToolException {
        final Map<Integer, JsonObject> minutes = new HashMap<>();
        for (final var element : weekRows) {
            final var row = element.getAsJsonObject();
            minutes.put(row.get(PLAYER_ID).getAsInt(), row);
        }
        final var ratingModel = McpJson.privateRatingModel();
        final var rows = new JsonArray();
        for (final var player : model.getCurrentPlayers()) {
            if (!player.isCoach() && wanted.contains(player.getPlayerId())) {
                rows.add(playerRow(player, minutes.get(player.getPlayerId()), ratingModel));
            }
        }
        return rows;
    }

    static JsonObject playerRow(Player player, JsonObject minutes, RatingPredictionModel ratingModel) {
        final var row = new JsonObject();
        row.addProperty(PLAYER_ID, player.getPlayerId());
        row.addProperty("name", player.getFullName());
        if (player.getSpecialty() > 0) {
            row.addProperty("specialty", player.getSpecialtyName());
        }
        row.addProperty("available", McpJson.isAvailable(player));
        if (player.getInjuryWeeks() >= 0) {
            row.addProperty("injuryWeeks", player.getInjuryWeeks());
        }
        if (player.getTotalCards() > 0) {
            row.addProperty("cards", player.getTotalCards());
        }
        if (player.isRedCarded()) {
            row.addProperty("suspended", true);
        }
        if (player.getTransferListed() > 0) {
            row.addProperty("transferListed", true);
        }
        if (player.getTeamGroup() != null && !player.getTeamGroup().isEmpty()) {
            row.addProperty("group", player.getTeamGroup());
        }
        addMinutes(row, minutes);
        row.add(BEST_POSITIONS, bestPositions(player, ratingModel));
        return row;
    }

    static void addMinutes(JsonObject row, @org.jetbrains.annotations.Nullable JsonObject minutes) {
        final var full = minutes == null ? 0 : minutes.get("fullTrainingMinutes").getAsInt();
        final var partly = minutes == null ? 0 : minutes.get("partlyTrainingMinutes").getAsInt();
        if (full > 0) {
            row.addProperty("fullTrainingMinutes", full);
        }
        if (partly > 0) {
            row.addProperty("partlyTrainingMinutes", partly);
        }
        row.addProperty("maxedForWeek", full >= 90);
    }

    /**
     * The three field roles he rates best in at normal behaviour, as roleId to rating, best
     * first. A map rather than rows: a squad of them is a large share of the answer.
     */
    private static JsonObject bestPositions(Player player, RatingPredictionModel ratingModel) {
        final var best = new JsonObject();
        Arrays.stream(McpJson.FIELD_ROLES)
                .mapToObj(roleId -> new RoleRating(roleId,
                        ratingModel.getPlayerRatingMatchBeginning(player, roleId, IMatchRoleID.NORMAL)))
                .sorted(Comparator.comparingDouble(RoleRating::rating).reversed())
                .limit(TOP_POSITIONS)
                .forEach(r -> best.addProperty(String.valueOf(r.roleId()),
                        Math.round(r.rating() * 100) / 100.0));
        return best;
    }

    private record RoleRating(int roleId, double rating) {
    }

    /**
     * sinceLastTraining without what week_brief does not need: the note, and per player the
     * snapshot dates and age at change. Each level-up keeps skill, from and to.
     */
    static JsonObject compactLevelUps(JsonObject sinceLastTraining) {
        final var compact = sinceLastTraining.deepCopy();
        compact.remove("note");
        if (compact.has(PLAYERS)) {
            for (final var element : compact.getAsJsonArray(PLAYERS)) {
                final var row = element.getAsJsonObject();
                row.remove("baselineDate");
                row.remove("latestDate");
                for (final var levelUp : row.getAsJsonArray(LEVEL_UPS)) {
                    levelUp.getAsJsonObject().remove("date");
                    levelUp.getAsJsonObject().remove("ageAtChange");
                }
            }
        }
        return compact;
    }
}
