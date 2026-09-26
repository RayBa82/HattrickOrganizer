package core.mcp;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import core.db.DBManager;
import core.gui.HOMainFrame;
import core.gui.theme.GroupTeamFactory;
import core.model.HOModel;
import core.model.match.Weather;
import core.model.player.IMatchRoleID;
import core.model.player.Player;
import core.net.ChppWriteService;
import module.lineup.Lineup;
import org.jetbrains.annotations.Nullable;

import javax.swing.SwingUtilities;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Scoring and setting lineups.
 * <p>
 * {@link #scratchLineup} builds a hypothetical {@link Lineup} for simulate_lineup to score on a
 * private rating model, so a model can try any number of variants without touching, or being
 * polluted by, the live lineup. {@code apply_lineup} and {@code set_player_group} write, and
 * they do so on the EDT.
 */
public final class LineupTools {

    private static final String SPEC_DOC =
            "A lineup: positions is a list of {roleId, playerId, behaviour}, roleId 100 keeper "
                    + "through 113 left forward for the field. bench takes the same shape, with "
                    + "roleId 200 keeper, 201 central defender, 202 wing back, 203 inner "
                    + "midfielder, 204 forward, 205 winger, 206 extra, and 207-213 for the "
                    + "matching backups in that order. behaviour is 0 normal, 1 offensive, "
                    + "2 defensive, 3 towards middle, 4 towards wing. tactic, attitude, "
                    + "styleOfPlay, captainId and setPiecesTakerId are optional.";

    private LineupTools() {
    }

    public static List<McpTool> tools() {
        return List.of(
                new McpTool("get_lineup",
                        "The lineup currently set in HO, with its sector ratings and formation. "
                                + "assumptions.weather is the weather they were scored under. "
                                + "simulate_lineup scores a hypothetical one instead.",
                        McpTool.schema(new JsonObject()),
                        args -> currentLineup()),
                new McpTool("apply_lineup",
                        "Write a lineup into HO. Changes what the Lineup tab shows and what HO "
                                + "would upload; sends nothing to Hattrick. HO can refuse a "
                                + "position - the Lineup tab's assistant filter unfields a player it "
                                + "hides - so the returned lineup is what HO holds afterwards, not "
                                + "an echo. Read notApplied before reporting the lineup as set. "
                                + "matchId / weather set its fixture and weather.",
                        lineupSchema(),
                        LineupTools::apply),
                new McpTool("set_player_group",
                        "File players under a squad group - the A/B/C team markers HO shows beside "
                                + "them and the lineup assistant selects on. An empty group clears "
                                + "it. Only the listed players change. Pass playerIds+group for one "
                                + "group, or assignments ({playerId, group} pairs) to file several "
                                + "groups in one call; get_squad reports each player's current group.",
                        McpTool.schema(McpTool.properties(
                                "playerIds", McpTool.prop("array",
                                        "player ids to file under group; omit if using assignments"),
                                "group", McpTool.prop("string",
                                        "one of: " + String.join(", ", GroupTeamFactory.TEAMS_GROUPS)
                                                + " (the empty one clears the group); omit if using "
                                                + "assignments"),
                                "assignments", McpTool.prop("array",
                                        "{playerId, group} pairs, instead of playerIds+group, for "
                                                + "filing players under different groups in one call"))),
                        LineupTools::setPlayerGroup),
                new McpTool("send_match_order",
                        "Send THE LINEUP CURRENTLY SET IN HO to Hattrick as the match order. This "
                                + "leaves the machine and OVERWRITES the order Hattrick holds for "
                                + "that match. Build it with apply_lineup and re-read it with "
                                + "get_lineup first. League matches are matchType 1; take other ids "
                                + "from get_matches.",
                        McpTool.schema(McpTool.properties(
                                        "matchId", McpTool.prop("integer", "the Hattrick match id"),
                                        "matchType", McpTool.prop("integer",
                                                "match type id (league = 1; from get_matches for others)")),
                                "matchId", "matchType"),
                        LineupTools::sendMatchOrder)
        );
    }

    private static JsonObject sendMatchOrder(JsonObject args) throws McpToolException {
        final var matchId = McpJson.reqInt(args, "matchId");
        final var matchTypeId = McpJson.reqInt(args, "matchType");
        final var matchType = core.model.enums.MatchType.getById(matchTypeId);
        if (matchType == null) {
            throw new IllegalArgumentException("Unknown matchType " + matchTypeId
                    + "; take the matchType id from get_matches (league matches are 1).");
        }
        final var model = McpJson.model();
        // apply_lineup mutates the live Lineup on the EDT, so serialise it there too.
        final var orderString = onEdt(() -> {
            final var wrapper = new JsonObject();
            wrapper.addProperty("order", model.getCurrentLineup().toJson());
            return wrapper;
        }).get("order").getAsString();

        final String response;
        try {
            response = ChppWriteService.instance()
                    .sendMatchOrderForAgent(matchId, matchType, orderString);
        } catch (ChppWriteService.ChppWriteException e) {
            throw new McpToolException(e.getMessage());
        }
        final var out = new JsonObject();
        out.addProperty("status", "ok");
        out.addProperty("matchId", matchId);
        out.addProperty("response", response);
        return out;
    }

    static JsonObject lineupSchema() {
        return McpTool.schema(McpTool.properties(
                        "positions", McpTool.prop("array", SPEC_DOC),
                        "bench", McpTool.prop("array", "bench slots as {roleId, playerId}"),
                        "tactic", McpTool.prop("integer", "tactic type"),
                        "attitude", McpTool.prop("integer", "attitude"),
                        "styleOfPlay", McpTool.prop("integer", "style of play, -10 defensive to 10 offensive"),
                        "allowUnavailable", McpTool.prop("boolean", "score the lineup even though it fields an injured or suspended player; rejected by default"),
                        "captainId", McpTool.prop("integer", "player id of the captain"),
                        "setPiecesTakerId", McpTool.prop("integer", "player id of the set pieces taker"),
                        "matchId", McpTool.prop("integer", "score for this stored fixture: its location "
                                + "and HO's stored weather forecast (get_match_weather refreshes it); "
                                + "default HO's next upcoming match"),
                        "weather", McpTool.prop("integer", "0 rain, 1 overcast, 2 partially cloudy, 3 sunny; "
                                + "overrides the fixture's forecast. Technical/Powerful/Quick players "
                                + "gain or lose 5% by it")),
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
     * {@code allowUnavailable}. Both simulate_lineup and apply_lineup route through here, so the
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

    /**
     * Rejects a roleId no slot of the lineup carries. {@link Lineup#setSpielerAtPosition} resolves
     * through {@code getPositionById} and returns without a word when that finds nothing, so an
     * off-by-one bench id used to be dropped in silence.
     */
    static void requireSlot(Lineup lineup, int roleId) throws McpToolException {
        if (lineup.getPositionById(roleId) == null) {
            throw new McpToolException("No lineup slot has roleId " + roleId
                    + "; the field is 100-113 and the bench 200-213.");
        }
    }

    /**
     * The optional matchId and weather of a spec. Both are resolved before the lineup is touched,
     * so a bad id leaves apply_lineup's live lineup alone.
     */
    private static void applyFixture(Lineup lineup, JsonObject spec) throws McpToolException {
        final var matchId = McpJson.optInteger(spec, "matchId");
        final var match = matchId == null ? null
                : DBManager.instance().getMatchesKurzInfoByMatchID(matchId, null);
        if (matchId != null && match == null) {
            throw new McpToolException("HO holds no match with id " + matchId
                    + ". Take the id from get_series.");
        }
        final var weatherId = McpJson.optInteger(spec, "weather");
        if (weatherId != null && (weatherId < 0 || weatherId > 3)) {
            throw new IllegalArgumentException("weather is 0 rain, 1 overcast, 2 partially cloudy "
                    + "or 3 sunny, not " + weatherId + ".");
        }
        if (match != null) {
            lineup.setUpcomingMatch(match);
        } else if (weatherId != null) {
            // A fresh Lineup loads HO's next match lazily - on the first getLocation(), which the
            // rating model calls mid-evaluation - and that load overwrites the weather with the
            // match's forecast. Trigger it now so the weather set below is the one that stays.
            lineup.getLocation();
        }
        if (weatherId != null) {
            lineup.setWeather(Weather.getById(weatherId));
        }
    }

    private static void applySpec(Lineup lineup, JsonObject spec) throws McpToolException {
        rejectUnavailable(spec);
        applyFixture(lineup, spec);
        for (final var element : McpJson.reqArray(spec, "positions")) {
            final var position = element.getAsJsonObject();
            final var roleId = McpJson.reqInt(position, "roleId");
            requireSlot(lineup, roleId);
            lineup.setSpielerAtPosition(roleId, McpJson.reqInt(position, "playerId"),
                    (byte) McpJson.optInt(position, "behaviour", IMatchRoleID.NORMAL));
        }
        if (spec.has("bench")) {
            for (final var element : spec.getAsJsonArray("bench")) {
                final var slot = element.getAsJsonObject();
                final var roleId = McpJson.reqInt(slot, "roleId");
                requireSlot(lineup, roleId);
                lineup.setSpielerAtPosition(roleId, McpJson.reqInt(slot, "playerId"));
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
            // Give the Lineup tab its say before storing or reporting anything. Its assistant
            // filters do not merely hide a player: PlayerPositionPanel.setPlayersList clears any
            // slot whose occupant the active filter would not offer, so a player placed here can
            // be unfielded again the moment the tab refreshes. Refreshing now makes that happen
            // inside the call, so what gets stored and reported is the lineup HO is really left
            // holding. It also makes the tab show the write, the way set_player_group already does.
            final var lineupPanel = HOMainFrame.instance().getLineupPanel();
            if (lineupPanel != null) {
                lineupPanel.update();
            }
            model.storeLineup(team);
            final var result = describe(team.getLineup(), ratingModel);
            final var refused = refusedByHo(team.getLineup(), spec);
            if (!refused.isEmpty()) {
                result.add("notApplied", refused);
                result.addProperty("warning", "HO did not keep " + refused.size()
                        + " of the requested positions - the lineup above is what it actually "
                        + "holds, not what was asked for. The Lineup tab's assistant filter "
                        + "unfields a player it hides, so this is usually its squad-group filter "
                        + "or its \"exclude players from the last match\" setting. Turn that "
                        + "setting off in HO, or field a player the filter allows. Do not report "
                        + "these positions as set.");
            }
            return result;
        });
    }

    /**
     * The positions the spec asked for that the lineup does not hold once HO has had its say.
     * <p>
     * Empty in the ordinary case. A non-empty result is the honest answer to "did the write take",
     * and is the reason {@code apply_lineup} refreshes the Lineup tab before reporting: without
     * that the answer would be yes, and the tab would quietly undo it afterwards.
     */
    static JsonArray refusedByHo(Lineup lineup, JsonObject spec) throws McpToolException {
        final var refused = new JsonArray();
        for (final var wanted : requestedSlots(spec)) {
            final var slot = lineup.getPositionById(wanted.roleId);
            if (slot != null && slot.getPlayerId() != wanted.playerId) {
                final var entry = new JsonObject();
                entry.addProperty("roleId", wanted.roleId);
                entry.addProperty("requestedPlayerId", wanted.playerId);
                entry.addProperty("actualPlayerId", slot.getPlayerId());
                refused.add(entry);
            }
        }
        return refused;
    }

    private record RequestedSlot(int roleId, int playerId) {
    }

    private static List<RequestedSlot> requestedSlots(JsonObject spec) throws McpToolException {
        final var slots = new ArrayList<RequestedSlot>();
        for (final var element : McpJson.reqArray(spec, "positions")) {
            final var position = element.getAsJsonObject();
            slots.add(new RequestedSlot(McpJson.reqInt(position, "roleId"),
                    McpJson.reqInt(position, "playerId")));
        }
        if (spec.has("bench")) {
            for (final var element : spec.getAsJsonArray("bench")) {
                final var slot = element.getAsJsonObject();
                slots.add(new RequestedSlot(McpJson.reqInt(slot, "roleId"),
                        McpJson.reqInt(slot, "playerId")));
            }
        }
        return slots;
    }

    private record GroupAssignment(Player player, String group) {
    }

    private static JsonObject setPlayerGroup(JsonObject args) throws McpToolException {
        final var model = McpJson.model();

        // Resolve everyone up front: a bad id must not leave half the squad refiled.
        final var assignments = new ArrayList<GroupAssignment>();
        if (args.has("assignments")) {
            for (final var element : args.getAsJsonArray("assignments")) {
                final var entry = element.getAsJsonObject();
                final var playerId = McpJson.reqInt(entry, "playerId");
                assignments.add(new GroupAssignment(requirePlayer(model, playerId),
                        group(McpJson.reqString(entry, "group"))));
            }
        } else {
            final var group = group(McpJson.reqString(args, "group"));
            for (final var id : McpJson.reqArray(args, "playerIds")) {
                assignments.add(new GroupAssignment(requirePlayer(model, id.getAsInt()), group));
            }
        }
        if (assignments.isEmpty()) {
            throw new IllegalArgumentException(
                    "Nothing to file - pass playerIds + group, or assignments.");
        }

        return onEdt(() -> {
            final var changed = new JsonArray();
            for (final var assignment : assignments) {
                // The setter persists to SPIELERNOTIZEN itself - there is nothing else to store.
                assignment.player().setTeamInfoSmilie(assignment.group());
                final var json = new JsonObject();
                json.addProperty("playerId", assignment.player().getPlayerId());
                json.addProperty("name", assignment.player().getFullName());
                json.addProperty("group", assignment.player().getTeamGroup());
                changed.add(json);
            }
            // What HO's own group buttons do afterwards - the tables render the group icons.
            final var lineupPanel = HOMainFrame.instance().getLineupPanel();
            if (lineupPanel != null) {
                lineupPanel.update();
            }
            final var result = new JsonObject();
            result.add("players", changed);
            return result;
        });
    }

    private static Player requirePlayer(HOModel model, int playerId) throws McpToolException {
        final var player = model.getCurrentPlayer(playerId);
        if (player == null) {
            throw new McpToolException("No player with id " + playerId + " is in the squad. "
                    + "Call get_squad for the ids HO knows about.");
        }
        return player;
    }

    private static String group(String group) throws McpToolException {
        for (final var known : GroupTeamFactory.TEAMS_GROUPS) {
            if (known.equals(group)) {
                return known;
            }
        }
        throw new McpToolException("Unknown group '" + group + "'. Use one of: "
                + String.join(", ", GroupTeamFactory.TEAMS_GROUPS));
    }

    /**
     * Package-private so {@code TeamTools.download_team} can snapshot the lineup a download
     * replaces.
     *
     * @param ratingModel null to leave the {@code ratings} block out entirely - what the download
     *                    snapshot wants, because it compares two snapshots and ratings move on
     *                    nearly every download
     */
    static JsonObject describe(Lineup lineup, @Nullable core.rating.RatingPredictionModel ratingModel) {
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
        if (ratingModel != null) {
            json.add("ratings", McpJson.ratings(ratingModel, lineup));
        }
        return json;
    }

    /**
     * Run on the event dispatch thread and propagate any failure back to the caller. Shared with
     * the other tool classes that reconcile against a live HO panel.
     */
    static JsonObject onEdt(EdtTask task) throws McpToolException {
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
            // getMessage() is null for a bare NullPointerException; toString() at least names the
            // class. Every McpToolException already carries a message, so this changes nothing for
            // the deliberate failures.
            final var e = failure.get();
            throw new McpToolException(e.getMessage() != null ? e.getMessage() : e.toString());
        }
        return result.get();
    }

    @FunctionalInterface
    interface EdtTask {
        JsonObject run() throws McpToolException;
    }
}
