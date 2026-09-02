package core.mcp;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import core.db.DBManager;
import core.model.HOModel;
import core.model.player.Player;
import core.net.ChppWriteService;
import core.training.FutureTrainingManager;
import core.training.TrainingPerWeek;
import core.training.WeeklyTrainingType;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * What HO's training engine knows: the settings the team trains under, and what those settings do
 * to each player over the coming weeks.
 */
public final class TrainingTools {

    /** How many weeks ahead to predict when the caller does not say. */
    private static final int DEFAULT_WEEKS = 8;

    private TrainingTools() {
    }

    public static List<McpTool> tools() {
        return List.of(
                new McpTool("get_training",
                        "The team's training: what is trained at which intensity and stamina share, "
                                + "which coach and assistants drive it, which field positions get "
                                + "full or partial training out of it, and per player the predicted "
                                + "skill changes over the coming weeks together with a training "
                                + "speed to rank them by. Read the top level status first: 'ok', "
                                + "or 'no_future_training_plan' when HO holds no training plan to "
                                + "predict from, which is not the same thing as nobody being "
                                + "trained - the accompanying note says what the user has to do.",
                        McpTool.schema(McpTool.properties(
                                "weeks", McpTool.prop("integer",
                                        "how many weeks to predict, default " + DEFAULT_WEEKS
                                                + "; capped at the number of future weeks HO has planned"),
                                "playerIds", McpTool.prop("array",
                                        "restrict the prediction to these player ids; the whole squad if omitted"))),
                        TrainingTools::training),
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
        out.add("committed", c);
        out.addProperty("note", "HO's snapshot is not refreshed until the next team download.");
        return out;
    }

    private static JsonObject training(JsonObject args) throws McpToolException {
        final var model = McpJson.model();
        final var json = new JsonObject();
        json.add("current", current(model));

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

        final var wanted = wantedPlayerIds(args);
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
        json.addProperty("trainingDate",
                training.getTrainingDate() == null ? null : training.getTrainingDate().toHT());

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
