package core.mcp;

import com.google.gson.JsonObject;
import core.model.player.IMatchRoleID;
import core.model.player.Player;
import module.lineup.Lineup;

import javax.swing.SwingUtilities;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Scoring and setting lineups.
 * <p>
 * {@code evaluate_lineup} and {@code optimize_lineup} work on a scratch {@link Lineup} scored with a
 * private rating model, so a model can try any number of variants without touching, or being
 * polluted by, the live lineup. Only {@code apply_lineup} writes, and it does so on the EDT.
 */
public final class LineupTools {

    private static final String SPEC_DOC =
            "A lineup: positions is a list of {roleId, playerId, behaviour}, roleId 100 keeper "
                    + "through 113 left forward for the field and 114-118 for the bench, behaviour "
                    + "0 normal, 1 offensive, 2 defensive, 3 towards middle, 4 towards wing. "
                    + "tactic, attitude, styleOfPlay, captainId and setPiecesTakerId are optional.";

    private LineupTools() {
    }

    public static List<McpTool> tools() {
        return List.of(
                new McpTool("get_lineup",
                        "The lineup currently set in HO, with its sector ratings and formation.",
                        McpTool.schema(new JsonObject()),
                        args -> currentLineup()),
                new McpTool("evaluate_lineup",
                        "Score a hypothetical lineup without changing anything in HO. Returns the "
                                + "seven sector ratings over 90 minutes and the formation. Call it "
                                + "as often as you like - it has no side effects.",
                        lineupSchema(),
                        LineupTools::evaluate),
                new McpTool("optimize_lineup",
                        "Let HO's lineup assistant build a baseline lineup out of the given players, "
                                + "then refine it yourself with evaluate_lineup. Unlike "
                                + "evaluate_lineup this one runs through HO's shared rating state, "
                                + "so call it once for a starting point rather than in a loop.",
                        McpTool.schema(McpTool.properties(
                                        "availablePlayerIds", McpTool.prop("array", "player ids to choose from; the whole squad if omitted"),
                                        "sectorsStrengthPriority", McpTool.prop("integer", "0 balanced, 1 defence, 2 midfield, 3 attack"),
                                        "withForm", McpTool.prop("boolean", "weigh current form"),
                                        "idealPosFirst", McpTool.prop("boolean", "fill each player's best position first"),
                                        "considerInjured", McpTool.prop("boolean", "allow injured players"),
                                        "considerSuspended", McpTool.prop("boolean", "allow suspended players"),
                                        "useAverageRating", McpTool.prop("boolean", "optimise the match average rather than the kick off rating")),
                                "sectorsStrengthPriority"),
                        LineupTools::optimize),
                new McpTool("apply_lineup",
                        "Write a lineup into HO. This changes what the Lineup tab shows and what HO "
                                + "would upload; it does not send anything to Hattrick.",
                        lineupSchema(),
                        LineupTools::apply)
        );
    }

    private static JsonObject lineupSchema() {
        return McpTool.schema(McpTool.properties(
                        "positions", McpTool.prop("array", SPEC_DOC),
                        "bench", McpTool.prop("array", "bench slots as {roleId, playerId}"),
                        "tactic", McpTool.prop("integer", "tactic type"),
                        "attitude", McpTool.prop("integer", "attitude"),
                        "styleOfPlay", McpTool.prop("integer", "style of play, -10 defensive to 10 offensive"),
                        "allowUnavailable", McpTool.prop("boolean", "score the lineup even though it fields an injured or suspended player; rejected by default"),
                        "captainId", McpTool.prop("integer", "player id of the captain"),
                        "setPiecesTakerId", McpTool.prop("integer", "player id of the set pieces taker")),
                "positions");
    }

    /** A throwaway lineup built from a lineup spec. Never the live one. */
    static Lineup scratchLineup(JsonObject spec) throws McpToolException {
        final var lineup = new Lineup();
        applySpec(lineup, spec);
        return lineup;
    }

    /**
     * Rejects a spec that fields an injured or suspended player, unless the caller opted out with
     * {@code allowUnavailable}. Both evaluate_lineup and apply_lineup route through here, so the
     * check sits here rather than in either tool.
     */
    private static void rejectUnavailable(JsonObject spec) throws McpToolException {
        if (McpJson.optBool(spec, "allowUnavailable", false)) {
            return;
        }
        // Deliberately not McpJson.model(): with no snapshot loaded there is nothing to check the
        // ids against, and the tools that need one fail on their own further down.
        final var model = core.model.HOVerwaltung.instance().getModel();
        if (model == null) {
            return;
        }
        final var complaints = new java.util.ArrayList<String>();
        for (final var element : McpJson.reqArray(spec, "positions")) {
            final var playerId = McpJson.reqInt(element.getAsJsonObject(), "playerId");
            final var player = playerId > 0 ? model.getCurrentPlayer(playerId) : null;
            if (player != null && !McpJson.isAvailable(player)) {
                complaints.add(player.getFullName() + " (" + playerId + ") is "
                        + (player.isRedCarded() ? "suspended" : "injured for "
                        + player.getInjuryWeeks() + " more week(s)"));
            }
        }
        if (!complaints.isEmpty()) {
            throw new McpToolException("Cannot field: " + String.join("; ", complaints)
                    + ". Pass allowUnavailable true to score the lineup anyway.");
        }
    }

    private static void applySpec(Lineup lineup, JsonObject spec) throws McpToolException {
        rejectUnavailable(spec);
        for (final var element : McpJson.reqArray(spec, "positions")) {
            final var position = element.getAsJsonObject();
            lineup.setSpielerAtPosition(McpJson.reqInt(position, "roleId"),
                    McpJson.reqInt(position, "playerId"),
                    (byte) McpJson.optInt(position, "behaviour", IMatchRoleID.NORMAL));
        }
        if (spec.has("bench")) {
            for (final var element : spec.getAsJsonArray("bench")) {
                final var slot = element.getAsJsonObject();
                lineup.setSpielerAtPosition(McpJson.reqInt(slot, "roleId"), McpJson.reqInt(slot, "playerId"));
            }
        }
        if (spec.has("tactic")) {
            lineup.setTacticType(spec.get("tactic").getAsInt());
        }
        if (spec.has("attitude")) {
            lineup.setAttitude(spec.get("attitude").getAsInt());
        }
        if (spec.has("styleOfPlay")) {
            lineup.setStyleOfPlay(spec.get("styleOfPlay").getAsInt());
        }
        if (spec.has("captainId")) {
            lineup.setCaptain(spec.get("captainId").getAsInt());
        }
        if (spec.has("setPiecesTakerId")) {
            lineup.setKicker(spec.get("setPiecesTakerId").getAsInt());
        }
    }

    private static JsonObject evaluate(JsonObject spec) throws McpToolException {
        return McpJson.ratings(McpJson.privateRatingModel(), scratchLineup(spec));
    }

    private static JsonObject optimize(JsonObject args) throws McpToolException {
        final var model = McpJson.model();

        List<Player> candidates = new ArrayList<>(model.getCurrentPlayers());
        if (args.has("availablePlayerIds")) {
            final var wanted = new ArrayList<Integer>();
            args.getAsJsonArray("availablePlayerIds").forEach(e -> wanted.add(e.getAsInt()));
            candidates = candidates.stream().filter(p -> wanted.contains(p.getPlayerId())).toList();
        }

        final var lineup = new Lineup();
        lineup.optimizeLineup(candidates,
                (byte) McpJson.reqInt(args, "sectorsStrengthPriority"),
                args.has("withForm") && args.get("withForm").getAsBoolean(),
                args.has("idealPosFirst") && args.get("idealPosFirst").getAsBoolean(),
                args.has("considerInjured") && args.get("considerInjured").getAsBoolean(),
                args.has("considerSuspended") && args.get("considerSuspended").getAsBoolean(),
                args.has("useAverageRating") && args.get("useAverageRating").getAsBoolean());

        // describe() re-scores on a private model, but optimizeLineup itself already went through
        // HOVerwaltung's shared one - see the note at the top of this task.
        return describe(lineup, McpJson.privateRatingModel());
    }

    private static JsonObject currentLineup() throws McpToolException {
        final var model = McpJson.model();
        final var ratingModel = McpJson.privateRatingModel();
        // apply_lineup mutates the live Lineup on the EDT, so read it there too.
        return onEdt(() -> describe(model.getCurrentLineup(), ratingModel));
    }

    private static JsonObject apply(JsonObject spec) throws McpToolException {
        final var model = McpJson.model();
        final var ratingModel = McpJson.privateRatingModel();
        return onEdt(() -> {
            // The live lineup is owned by its MatchLineupTeam - HOModel.setLineup takes a
            // MatchLineupTeam, not a Lineup - so mutate it in place and persist through the same
            // path the Lineup tab uses.
            final var team = model.getCurrentLineupTeam();
            applySpec(team.getLineup(), spec);
            model.storeLineup(team);
            return describe(team.getLineup(), ratingModel);
        });
    }

    private static JsonObject describe(Lineup lineup, core.rating.RatingPredictionModel ratingModel) {
        final var json = new JsonObject();
        final var positions = new com.google.gson.JsonArray();
        for (final var position : lineup.getAllPositions()) {
            final var positionJson = new JsonObject();
            positionJson.addProperty("roleId", position.getRoleId());
            positionJson.addProperty("playerId", position.getPlayerId());
            positionJson.addProperty("behaviour", position.getBehaviour());
            positions.add(positionJson);
        }
        json.add("positions", positions);
        json.addProperty("tactic", lineup.getTacticType());
        json.addProperty("attitude", lineup.getAttitude());
        json.addProperty("captainId", lineup.getCaptain());
        json.addProperty("setPiecesTakerId", lineup.getKicker());
        // styleOfPlay is settable on Lineup but readable only from MatchLineupTeam, so it is an
        // input here and not part of the output.
        json.add("ratings", McpJson.ratings(ratingModel, lineup));
        return json;
    }

    /** Run on the event dispatch thread and propagate any failure back to the caller. */
    private static JsonObject onEdt(EdtTask task) throws McpToolException {
        if (SwingUtilities.isEventDispatchThread()) {
            return task.run();
        }
        final var result = new AtomicReference<JsonObject>();
        final var failure = new AtomicReference<Exception>();
        try {
            SwingUtilities.invokeAndWait(() -> {
                try {
                    result.set(task.run());
                } catch (Exception e) {
                    failure.set(e);
                }
            });
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new McpToolException("Interrupted while waiting for HO's UI thread.");
        } catch (InvocationTargetException e) {
            throw new McpToolException("HO's UI thread rejected the request: " + e.getMessage());
        }
        if (failure.get() != null) {
            throw new McpToolException(failure.get().getMessage());
        }
        return result.get();
    }

    @FunctionalInterface
    private interface EdtTask {
        JsonObject run() throws McpToolException;
    }
}
