package core.mcp;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import core.db.DBManager;
import core.gui.HOMainFrame;
import core.gui.theme.GroupTeamFactory;
import core.model.HOModel;
import core.model.match.MatchLineupPosition;
import core.model.match.Weather;
import core.model.player.IMatchRoleID;
import core.model.player.Player;
import core.file.xml.XMLCHPPPreParser;
import core.file.xml.XMLMatchOrderParser;
import core.net.ChppWriteService;
import core.net.Connector;
import core.net.OnlineWorker;
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
                        "The lineup currently set in HO, with its sector ratings and formation, "
                                + "penalty takers (with strength) and team orders. Ratings average "
                                + "90 minutes with every team order firing at its minute; standing "
                                + "and red-card conditions are not modelled. "
                                + "assumptions.weather is the weather they were scored under. "
                                + "simulate_lineup scores a hypothetical one instead.",
                        McpTool.schema(new JsonObject()),
                        args -> currentLineup()),
                new McpTool("apply_lineup",
                        "Write a lineup into HO. Changes what the Lineup tab shows and what HO "
                                + "would upload; sends nothing to Hattrick. HO can refuse a "
                                + "position - the Lineup tab's assistant filter unfields a player it "
                                + "hides - so the returned lineup is what HO holds afterwards, not "
                                + "an echo. Read notApplied and orderWarnings before reporting it "
                                + "as set. penaltyTakers / teamOrders replace the whole list.",
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
                                + "that match. Check it with get_lineup first. "
                                + "League matches are matchType 1; take other ids "
                                + "from get_matches. Returns the order as sent, re-read from "
                                + "Hattrick: verified false lists the differences. Without "
                                + "Supporter: status supporterRequired, nothing sent.",
                        matchOrderSchema(),
                        LineupTools::sendMatchOrder),
                new McpTool("get_match_order",
                        "The match order Hattrick holds for a match, read from Hattrick (not HO's "
                                + "lineup), in send_match_order's order shape. Use it to check what "
                                + "a send left at Hattrick. status hidden: Hattrick no longer shows "
                                + "the order shortly before kick-off.",
                        matchOrderSchema(),
                        LineupTools::getMatchOrder)
        );
    }

    private static JsonObject matchOrderSchema() {
        return McpTool.schema(McpTool.properties(
                        "matchId", McpTool.prop("integer", "the Hattrick match id"),
                        "matchType", McpTool.prop("integer",
                                "match type id (league = 1; from get_matches for others)")),
                "matchId", "matchType");
    }

    private static JsonObject sendMatchOrder(JsonObject args) throws McpToolException {
        final var matchId = McpJson.reqInt(args, "matchId");
        final var matchType = matchType(McpJson.reqInt(args, "matchType"));
        final var model = McpJson.model();
        final var lineupMatchId = new java.util.concurrent.atomic.AtomicInteger();
        // apply_lineup mutates the live Lineup on the EDT, so serialise it there too.
        final var sentOrder = onEdt(() -> {
            final var team = model.getCurrentLineupTeam();
            final var lineup = team.getLineup();
            requireFieldPlayers(lineup);
            lineupMatchId.set(team.getMatchId());
            final var wrapper = new JsonObject();
            final var orderString = lineup.toJson();
            wrapper.addProperty("order", orderString);
            wrapper.add("echo", LineupOrders.echo(orderString, benchRoleIds(lineup)));
            return wrapper;
        });
        final var orderString = sentOrder.get("order").getAsString();
        final var echo = sentOrder.getAsJsonObject("echo");

        final var out = new JsonObject();
        out.addProperty("matchId", matchId);
        final var mismatch = matchMismatch(lineupMatchId.get(), matchId);
        if (mismatch != null) {
            out.addProperty("warning", mismatch);
        }
        try {
            ChppWriteService.instance().sendMatchOrderForAgent(matchId, matchType, orderString);
        } catch (ChppWriteService.SupporterRequiredException e) {
            // Not a failure to retry: the order is fine, the account cannot upload it. The echo is
            // what the user has to enter on Hattrick by hand.
            out.addProperty("status", "supporterRequired");
            out.addProperty("sent", false);
            out.addProperty("message", e.getMessage() + " Nothing was sent; wouldSend is the order "
                    + "to enter on Hattrick by hand.");
            out.add("wouldSend", echo);
            return out;
        } catch (ChppWriteService.ChppWriteException e) {
            throw new McpToolException(e.getMessage());
        }
        out.addProperty("status", "ok");
        out.addProperty("sent", true);
        out.add("order", echo);
        addReadBack(out, echo, matchId, matchType);
        return out;
    }

    /**
     * Hattrick answers a setmatchorder with success even when the order does not end up where it
     * was meant to, so the send re-reads what Hattrick holds and names every difference. A failed
     * re-read is reported, not thrown: the order did go out.
     */
    private static void addReadBack(JsonObject out, JsonObject sent, int matchId,
                                    core.model.enums.MatchType matchType) {
        try {
            final var held = heldOrder(matchId, matchType);
            if (held == null) {
                out.addProperty("verified", "hidden");
                out.addProperty("verifyMessage", HIDDEN);
                return;
            }
            final var differences = orderDifferences(sent, held);
            out.addProperty("verified", differences.isEmpty());
            if (!differences.isEmpty()) {
                out.add("differences", differences);
                out.addProperty("verifyMessage", "Hattrick holds a different order than the one sent. "
                        + "Do not report it as set; get_match_order shows what Hattrick holds.");
            }
        } catch (McpToolException | RuntimeException e) {
            out.addProperty("verified", "failed");
            out.addProperty("verifyMessage", "Sent, but the read-back failed: " + e.getMessage()
                    + " Check it with get_match_order.");
        }
    }

    private static final String HIDDEN = "Hattrick no longer shows this order - it hides it "
            + "shortly before kick-off - so it cannot be read back.";

    private static JsonObject getMatchOrder(JsonObject args) throws McpToolException {
        final var matchId = McpJson.reqInt(args, "matchId");
        final var matchType = matchType(McpJson.reqInt(args, "matchType"));
        final var out = new JsonObject();
        out.addProperty("matchId", matchId);
        final var held = heldOrder(matchId, matchType);
        if (held == null) {
            out.addProperty("status", "hidden");
            out.addProperty("message", HIDDEN);
        } else {
            out.addProperty("status", "ok");
            out.add("order", held);
        }
        return out;
    }

    private static core.model.enums.MatchType matchType(int matchTypeId) {
        final var matchType = core.model.enums.MatchType.getById(matchTypeId);
        if (matchType == null) {
            throw new IllegalArgumentException("Unknown matchType " + matchTypeId
                    + "; take the matchType id from get_matches (league matches are 1).");
        }
        return matchType;
    }

    /**
     * The order Hattrick holds for the match, in send_match_order's order shape, or null when
     * Hattrick hides it before kick-off. Read from Hattrick, never from HO's database.
     */
    static @Nullable JsonObject heldOrder(int matchId, core.model.enums.MatchType matchType)
            throws McpToolException {
        final var teamId = McpJson.model().getBasics().getTeamId();
        final var connector = Connector.instance();
        final var wasSilent = connector.setSilentDownload(true);
        final String xml;
        try {
            xml = connector.downloadMatchOrder(matchId, matchType, teamId);
        } finally {
            connector.setSilentDownload(wasSilent);
        }
        if (xml == null || xml.isEmpty()) {
            throw new McpToolException("Hattrick did not return the match order"
                    + (connector.lastError() != null ? ": " + connector.lastError() : "."));
        }
        final var error = XMLCHPPPreParser.getError(xml);
        if (!error.isEmpty()) {
            throw new McpToolException("Hattrick refused to show the match order: " + error);
        }
        final var parsed = XMLMatchOrderParser.parseMatchOrderFromString(xml);
        if (XMLMatchOrderParser.isHidden(parsed)) {
            return null;
        }
        try {
            return orderOf(OnlineWorker.lineupFromOrder(parsed, teamId).getLineup());
        } catch (java.io.IOException e) {
            throw new McpToolException("HO could not read the match order Hattrick sent: "
                    + e.getMessage());
        }
    }

    /** A lineup in the order shape send_match_order echoes, built the way it is sent. */
    static JsonObject orderOf(Lineup lineup) {
        final var orderString = lineup.toJson();
        return LineupOrders.echo(orderString, benchRoleIds(lineup));
    }

    /** toJson sends the bench sorted by roleId; the echo names the slots in that order. */
    private static List<Integer> benchRoleIds(Lineup lineup) {
        return lineup.getAllPositions().stream().map(MatchLineupPosition::getRoleId)
                .filter(r -> r >= IMatchRoleID.startReserves && r <= IMatchRoleID.substXT2)
                .sorted().toList();
    }

    /** Every way the order Hattrick holds differs from the one sent; empty when they agree. */
    static JsonArray orderDifferences(JsonObject sent, JsonObject held) {
        final var differences = new JsonArray();
        for (final var key : List.of("positions", "bench")) {
            final var sentSlots = byRole(sent.getAsJsonArray(key));
            final var heldSlots = byRole(held.getAsJsonArray(key));
            final var roles = new java.util.TreeSet<>(sentSlots.keySet());
            roles.addAll(heldSlots.keySet());
            for (final var role : roles) {
                final var a = sentSlots.get(role);
                final var b = heldSlots.get(role);
                if (!java.util.Objects.equals(a, b)) {
                    differences.add(key + " roleId " + role + ": sent " + slot(a)
                            + ", Hattrick holds " + slot(b));
                }
            }
        }
        for (final var key : List.of("tactic", "attitude", "styleOfPlay", "captainId",
                "setPiecesTakerId", LineupOrders.PENALTY_TAKERS, LineupOrders.TEAM_ORDERS)) {
            if (!java.util.Objects.equals(sent.get(key), held.get(key))) {
                differences.add(key + ": sent " + sent.get(key) + ", Hattrick holds " + held.get(key));
            }
        }
        return differences;
    }

    private static java.util.Map<Integer, JsonObject> byRole(@Nullable JsonArray slots) {
        final var map = new java.util.HashMap<Integer, JsonObject>();
        if (slots != null) {
            for (final var element : slots) {
                final var slot = element.getAsJsonObject();
                map.put(slot.get("roleId").getAsInt(), slot);
            }
        }
        return map;
    }

    private static String slot(@Nullable JsonObject slot) {
        if (slot == null) {
            return "nobody";
        }
        return "player " + slot.get("playerId").getAsInt()
                + (slot.has("behaviour") ? " behaviour " + slot.get("behaviour").getAsInt() : "");
    }

    /** An order without field players would wipe the one Hattrick holds, so it never leaves. */
    static void requireFieldPlayers(Lineup lineup) throws McpToolException {
        if (lineup.getFieldPositions().stream().noneMatch(p -> p.getPlayerId() > 0)) {
            throw new McpToolException("Empty lineup, nothing sent: HO holds no field players. "
                    + "Build the lineup with apply_lineup, check it with get_lineup, then send.");
        }
    }

    /** Null when the lineup HO holds was stored for matchId, else the warning sent with the order. */
    static @Nullable String matchMismatch(int lineupMatchId, int matchId) {
        if (lineupMatchId == matchId) {
            return null;
        }
        return "The lineup HO holds belongs to "
                + (lineupMatchId > 0 ? "match " + lineupMatchId : "no match")
                + ", not " + matchId + ". Check get_lineup's lineupMatchId and nextMatch.";
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
                        LineupOrders.PENALTY_TAKERS, McpTool.prop("array", LineupOrders.PENALTY_TAKERS_DOC),
                        LineupOrders.TEAM_ORDERS, McpTool.prop("array", LineupOrders.TEAM_ORDERS_DOC),
                        "matchId", McpTool.prop("integer", "score for this stored fixture: its location "
                                + "and HO's stored weather forecast (get_match_weather refreshes it); "
                                + "default HO's next upcoming match"),
                        "weather", McpTool.prop("integer", "0 rain, 1 overcast, 2 partially cloudy, 3 sunny; "
                                + "overrides the fixture's forecast. Technical/Powerful/Quick players "
                                + "gain or lose 5% by it")),
                "positions");
    }

    /**
     * A throwaway lineup built from a lineup spec, team orders included since they move the
     * ratings. Never the live one.
     */
    static Lineup scratchLineup(JsonObject spec) throws McpToolException {
        final var model = core.model.HOVerwaltung.instance().getModel();
        final var club = model != null ? model.getClub() : null;
        final var orders = LineupOrders.parseTeamOrders(spec, model,
                club != null ? club.getTacticalAssistantLevels() : 0);
        if (!McpJson.optBool(spec, "allowUnavailable", false)) {
            LineupOrders.rejectUnavailable(null, orders, model);
        }
        final var lineup = new Lineup();
        applySpec(lineup, spec);
        if (orders != null) {
            lineup.setSubstitionList(orders);
        }
        return lineup;
    }

    /**
     * A scratch copy of a lineup with everything that moves a rating: positions, behaviours,
     * tactic, attitude, style of play, captain, set pieces taker and team orders.
     */
    static Lineup copyOf(Lineup source) {
        final var copy = new Lineup();
        for (final var position : source.getAllPositions()) {
            if (copy.getPositionById(position.getRoleId()) != null) {
                copy.setSpielerAtPosition(position.getRoleId(), position.getPlayerId(),
                        position.getBehaviour());
            }
        }
        copy.setTacticType(source.getTacticType());
        copy.setAttitude(source.getAttitude());
        copy.setStyleOfPlay(source.getCoachModifier());
        copy.setCaptain(source.getCaptain());
        copy.setKicker(source.getKicker());
        copy.setSubstitionList(new ArrayList<>(source.getSubstitutionList()));
        return copy;
    }

    /**
     * Hattrick has not let the keeper take set pieces since 2008, so a lineup naming him is refused
     * up front - before apply_lineup touches the live lineup - rather than sent and silently ignored.
     */
    static void rejectKeeperAsSetPiecesTaker(JsonObject spec) {
        if (!spec.has("setPiecesTakerId") || spec.get("setPiecesTakerId").isJsonNull()) {
            return;
        }
        final var takerId = spec.get("setPiecesTakerId").getAsInt();
        for (final var element : McpJson.reqArray(spec, "positions")) {
            final var position = element.getAsJsonObject();
            if (McpJson.reqInt(position, "roleId") == IMatchRoleID.keeper
                    && McpJson.reqInt(position, "playerId") == takerId) {
                throw new IllegalArgumentException("setPiecesTakerId " + takerId + " is the keeper "
                        + "(roleId 100) - a keeper cannot take set pieces. Name an outfield player.");
            }
        }
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
    static void applyFixture(Lineup lineup, JsonObject spec) throws McpToolException {
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
        rejectKeeperAsSetPiecesTaker(spec);
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
        final var next = DBManager.instance().getNextMatchesKurzInfo(model.getBasics().getTeamId());
        // apply_lineup mutates the live Lineup on the EDT, so read it there too.
        return onEdt(() -> {
            final var team = model.getCurrentLineupTeam();
            final var json = describe(team.getLineup(), ratingModel);
            addLineupMatch(json, team, next);
            return json;
        });
    }

    /**
     * HO stores a lineup per match and, on start, loads the one of the next upcoming match - an
     * empty one if that match has none yet. Naming both makes a lineup left on an earlier match,
     * or never stored, show at once.
     */
    static void addLineupMatch(JsonObject json, core.model.match.MatchLineupTeam team,
                               @Nullable core.model.match.MatchKurzInfo next) {
        final var matchId = team.getMatchId();
        json.add("lineupMatchId", matchId > 0 ? new com.google.gson.JsonPrimitive(matchId) : com.google.gson.JsonNull.INSTANCE);
        json.add("matchType", matchId > 0 ? new com.google.gson.JsonPrimitive(team.getMatchType().getId()) : com.google.gson.JsonNull.INSTANCE);
        if (next == null) {
            json.add("nextMatch", com.google.gson.JsonNull.INSTANCE);
            return;
        }
        final var nextJson = new JsonObject();
        nextJson.addProperty("matchId", next.getMatchID());
        nextJson.addProperty("matchType", next.getMatchType().getId());
        nextJson.addProperty("date", next.getMatchSchedule() == null ? null : next.getMatchSchedule().toHT());
        json.add("nextMatch", nextJson);
    }

    private static JsonObject apply(JsonObject spec) throws McpToolException {
        final var model = McpJson.model();
        final var ratingModel = McpJson.privateRatingModel();
        // Parsed and checked before the live lineup is touched, like the positions' own checks.
        final var takers = LineupOrders.parsePenaltyTakers(spec, model);
        final var orders = LineupOrders.parseTeamOrders(spec, model,
                model.getClub() != null ? model.getClub().getTacticalAssistantLevels() : 0);
        if (!McpJson.optBool(spec, "allowUnavailable", false)) {
            LineupOrders.rejectUnavailable(takers, orders, model);
        }
        return onEdt(() -> {
            // The live lineup is owned by its MatchLineupTeam - HOModel.setLineup takes a
            // MatchLineupTeam, not a Lineup - so mutate it in place and persist through the same
            // path the Lineup tab uses.
            final var team = model.getCurrentLineupTeam();
            applySpec(team.getLineup(), spec);
            if (takers != null) {
                team.getLineup().setPenaltyTakers(takers);
            }
            if (orders != null) {
                team.getLineup().setSubstitionList(orders);
            }
            // Give the Lineup tab its say before storing or reporting anything. Its assistant
            // filters do not merely hide a player: PlayerPositionPanel.setPlayersList clears any
            // slot whose occupant the active filter would not offer, so a player placed here can
            // be unfielded again the moment the tab refreshes. Refreshing now makes that happen
            // inside the call, so what gets stored and reported is the lineup HO is really left
            // holding. It also makes the tab show the write, the way set_player_group already does.
            final var frame = HOMainFrame.shown();
            final var lineupPanel = frame == null ? null : frame.getLineupPanel();
            if (lineupPanel != null) {
                lineupPanel.update();
            }
            model.storeLineup(team);
            final var result = describe(team.getLineup(), ratingModel);
            final var orderWarnings = LineupOrders.warnings(team.getLineup(), model);
            if (!orderWarnings.isEmpty()) {
                result.add("orderWarnings", orderWarnings);
            }
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
            final var frame = HOMainFrame.shown();
            final var lineupPanel = frame == null ? null : frame.getLineupPanel();
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
            // Penalty takers have their own list below; roleId 0 is an empty kicker slot's padding.
            final var roleId = position.getRoleId();
            if (roleId == 0 || (roleId >= IMatchRoleID.penaltyTaker1 && roleId <= IMatchRoleID.penaltyTaker11)) {
                continue;
            }
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
        json.add(LineupOrders.PENALTY_TAKERS, LineupOrders.penaltyTakers(lineup,
                core.model.HOVerwaltung.instance().getModel(), ratingModel));
        json.add(LineupOrders.TEAM_ORDERS, LineupOrders.teamOrders(lineup.getSubstitutionList()));
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
