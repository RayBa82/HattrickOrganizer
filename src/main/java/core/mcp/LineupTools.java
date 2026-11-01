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

    private static final String FULL = "full";
    private static final String POSITIONS = "positions";
    private static final String CAPTAIN_ID = "captainId";
    private static final String TACTIC = "tactic";
    private static final String ATTITUDE = "attitude";
    private static final String SET_PIECES_TAKER_ID = "setPiecesTakerId";
    private static final String ROLE_ID = "roleId";
    private static final String ARRAY = "array";
    private static final String GROUP = "group";
    private static final String ASSIGNMENTS = "assignments";
    private static final String MATCH_ID = "matchId";
    private static final String INTEGER = "integer";
    private static final String MATCH_TYPE = "matchType";
    private static final String ORDER = "order";
    private static final String UNAVAILABLE_PLAYERS = "unavailablePlayers";
    private static final String STATUS = "status";
    private static final String VERIFIED = "verified";
    private static final String VERIFY_MESSAGE = "verifyMessage";
    private static final String BENCH = "bench";
    private static final String STYLE_OF_PLAY = "styleOfPlay";
    private static final String PLAYER_ID = "playerId";
    private static final String BEHAVIOUR = "behaviour";
    private static final String ALLOW_UNAVAILABLE = "allowUnavailable";

    private LineupTools() {
    }

    public static List<McpTool> tools() {
        return List.of(
                new McpTool("get_lineup",
                        "The lineup currently set in HO, with sector ratings, formation, "
                                + "penalty takers (with strength) and team orders. Ratings average "
                                + "90 minutes with every team order firing at its minute; standing "
                                + "and red-card conditions are not modelled. "
                                + "assumptions.weather is the weather they were scored under. "
                                + "unavailablePlayers: injured/suspended in it; "
                                + "captainInStartingEleven: captain on the pitch.",
                        McpTool.schema(new JsonObject()),
                        args -> currentLineup()),
                new McpTool("apply_lineup",
                        "Write a lineup into HO; sends nothing to Hattrick. Answers with what "
                                + "changed against the lineup HO held before, as HO holds it after "
                                + "(its assistant filter can unfield a player), plus ratings and "
                                + "captainInStartingEleven; full adds the whole lineup, so no "
                                + "get_lineup is needed. Read notApplied and orderWarnings before "
                                + "reporting it as set. penaltyTakers / teamOrders replace the list.",
                        applySchema(),
                        LineupTools::apply),
                new McpTool("set_player_group",
                        "File players under a squad group - the A/B/C team markers HO shows beside "
                                + "them and the lineup assistant selects on. An empty group clears "
                                + "it. Only the listed players change. Pass playerIds+group for one "
                                + "group, or assignments ({playerId, group} pairs) to file several "
                                + "groups in one call; get_squad reports each player's current group.",
                        McpTool.schema(McpTool.properties(
                                "playerIds", McpTool.prop(ARRAY,
                                        "player ids to file under group; omit if using assignments"),
                                GROUP, McpTool.prop("string",
                                        "one of: " + String.join(", ", GroupTeamFactory.TEAMS_GROUPS)
                                                + " (the empty one clears the group); omit if using "
                                                + ASSIGNMENTS),
                                ASSIGNMENTS, McpTool.prop(ARRAY,
                                        "{playerId, group} pairs, instead of playerIds+group, for "
                                                + "filing players under different groups in one call"))),
                        LineupTools::setPlayerGroup),
                new McpTool("send_match_order",
                        "Send THE LINEUP CURRENTLY SET IN HO to Hattrick as the match order. This "
                                + "leaves the machine and OVERWRITES the order Hattrick holds for "
                                + "that match. Check it with get_lineup first. "
                                + "matchType: 1 league, others from get_matches. Returns the order as sent, re-read from "
                                + "Hattrick: verified false lists the differences; "
                                + "unavailablePlayers warns. Without "
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
                        MATCH_ID, McpTool.prop(INTEGER, "the Hattrick match id"),
                        MATCH_TYPE, McpTool.prop(INTEGER,
                                "match type id (league = 1; from get_matches for others)")),
                MATCH_ID, MATCH_TYPE);
    }

    private static JsonObject sendMatchOrder(JsonObject args) throws McpToolException {
        final var matchId = McpJson.reqInt(args, MATCH_ID);
        final var matchType = matchType(McpJson.reqInt(args, MATCH_TYPE));
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
            wrapper.addProperty(ORDER, orderString);
            wrapper.add("echo", LineupOrders.echo(orderString, benchRoleIds(lineup)));
            wrapper.add(UNAVAILABLE_PLAYERS, unavailablePlayers(lineup, model));
            return wrapper;
        });
        final var orderString = sentOrder.get(ORDER).getAsString();
        final var echo = sentOrder.getAsJsonObject("echo");

        final var out = new JsonObject();
        out.addProperty(MATCH_ID, matchId);
        final var unavailable = sentOrder.getAsJsonArray(UNAVAILABLE_PLAYERS);
        if (!unavailable.isEmpty()) {
            // Sent anyway: Hattrick takes the order, and the caller may mean it (a sub on the bench).
            out.add(UNAVAILABLE_PLAYERS, unavailable);
        }
        final var mismatch = matchMismatch(lineupMatchId.get(), matchId);
        if (mismatch != null) {
            out.addProperty("warning", mismatch);
        }
        try {
            ChppWriteService.instance().sendMatchOrderForAgent(matchId, matchType, orderString);
        } catch (ChppWriteService.SupporterRequiredException e) {
            // Not a failure to retry: the order is fine, the account cannot upload it. The echo is
            // what the user has to enter on Hattrick by hand.
            out.addProperty(STATUS, "supporterRequired");
            out.addProperty("sent", false);
            out.addProperty("message", e.getMessage() + " Nothing was sent; wouldSend is the order "
                    + "to enter on Hattrick by hand.");
            out.add("wouldSend", echo);
            return out;
        } catch (ChppWriteService.ChppWriteException e) {
            throw new McpToolException(e.getMessage());
        }
        out.addProperty(STATUS, "ok");
        out.addProperty("sent", true);
        out.add(ORDER, echo);
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
                out.addProperty(VERIFIED, "hidden");
                out.addProperty(VERIFY_MESSAGE, HIDDEN);
                return;
            }
            final var differences = orderDifferences(sent, held);
            out.addProperty(VERIFIED, differences.isEmpty());
            if (!differences.isEmpty()) {
                out.add("differences", differences);
                out.addProperty(VERIFY_MESSAGE, "Hattrick holds a different order than the one sent. "
                        + "Do not report it as set; get_match_order shows what Hattrick holds.");
            }
        } catch (McpToolException | RuntimeException e) {
            out.addProperty(VERIFIED, "failed");
            out.addProperty(VERIFY_MESSAGE, "Sent, but the read-back failed: " + e.getMessage()
                    + " Check it with get_match_order.");
        }
    }

    private static final String HIDDEN = "Hattrick no longer shows this order - it hides it "
            + "shortly before kick-off - so it cannot be read back.";

    private static JsonObject getMatchOrder(JsonObject args) throws McpToolException {
        final var matchId = McpJson.reqInt(args, MATCH_ID);
        final var matchType = matchType(McpJson.reqInt(args, MATCH_TYPE));
        final var out = new JsonObject();
        out.addProperty(MATCH_ID, matchId);
        final var held = heldOrder(matchId, matchType);
        if (held == null) {
            out.addProperty(STATUS, "hidden");
            out.addProperty("message", HIDDEN);
        } else {
            out.addProperty(STATUS, "ok");
            out.add(ORDER, held);
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
        for (final var key : List.of(POSITIONS, BENCH)) {
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
        for (final var key : List.of(TACTIC, ATTITUDE, STYLE_OF_PLAY, CAPTAIN_ID,
                SET_PIECES_TAKER_ID, LineupOrders.PENALTY_TAKERS, LineupOrders.TEAM_ORDERS)) {
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
                map.put(slot.get(ROLE_ID).getAsInt(), slot);
            }
        }
        return map;
    }

    private static String slot(@Nullable JsonObject slot) {
        if (slot == null) {
            return "nobody";
        }
        return "player " + slot.get(PLAYER_ID).getAsInt()
                + (slot.has(BEHAVIOUR) ? " behaviour " + slot.get(BEHAVIOUR).getAsInt() : "");
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

    /** apply_lineup takes a lineup spec plus {@code full}, which simulate_lineup has no use for. */
    private static JsonObject applySchema() {
        final var schema = lineupSchema();
        schema.getAsJsonObject("properties").add(FULL, McpTool.prop("boolean",
                "also return the whole lineup as HO holds it after the write. Default false: "
                        + "only what changed"));
        return schema;
    }

    static JsonObject lineupSchema() {
        return McpTool.schema(McpTool.properties(
                        POSITIONS, McpTool.prop(ARRAY, SPEC_DOC),
                        BENCH, McpTool.prop(ARRAY, "bench slots as {roleId, playerId}"),
                        TACTIC, McpTool.prop(INTEGER, "tactic type"),
                        ATTITUDE, McpTool.prop(INTEGER, ATTITUDE),
                        STYLE_OF_PLAY, McpTool.prop(INTEGER, "style of play, -10 defensive to 10 offensive"),
                        ALLOW_UNAVAILABLE, McpTool.prop("boolean", "score the lineup even though it fields an injured or suspended player; rejected by default"),
                        CAPTAIN_ID, McpTool.prop(INTEGER, "player id of the captain"),
                        SET_PIECES_TAKER_ID, McpTool.prop(INTEGER, "player id of the set pieces taker"),
                        LineupOrders.PENALTY_TAKERS, McpTool.prop(ARRAY, LineupOrders.PENALTY_TAKERS_DOC),
                        LineupOrders.TEAM_ORDERS, McpTool.prop(ARRAY, LineupOrders.TEAM_ORDERS_DOC),
                        MATCH_ID, McpTool.prop(INTEGER, "score for this stored fixture: its location "
                                + "and HO's stored weather forecast (get_match_weather refreshes it); "
                                + "default HO's next upcoming match. apply_lineup also stores the lineup for it, "
                                + "so send_match_order for that match finds it"),
                        "weather", McpTool.prop(INTEGER, "0 rain, 1 overcast, 2 partially cloudy, 3 sunny; "
                                + "overrides the fixture's forecast. Technical/Powerful/Quick players "
                                + "gain or lose 5% by it")),
                POSITIONS);
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
        if (!McpJson.optBool(spec, ALLOW_UNAVAILABLE, false)) {
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
        if (!spec.has(SET_PIECES_TAKER_ID) || spec.get(SET_PIECES_TAKER_ID).isJsonNull()) {
            return;
        }
        final var takerId = spec.get(SET_PIECES_TAKER_ID).getAsInt();
        for (final var element : McpJson.reqArray(spec, POSITIONS)) {
            final var position = element.getAsJsonObject();
            if (McpJson.reqInt(position, ROLE_ID) == IMatchRoleID.keeper
                    && McpJson.reqInt(position, PLAYER_ID) == takerId) {
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
    private static String unavailableReason(core.model.player.Player player) {
        return player.isRedCarded() ? "suspended" : "injured for " + player.getInjuryWeeks() + " more week(s)";
    }

    /**
     * Injured or suspended players in {@code lineup}: playerId, name, roleId, reason. get_lineup
     * and send_match_order warn with it rather than refuse - the order may be meant that way.
     */
    static com.google.gson.JsonArray unavailablePlayers(Lineup lineup, core.model.HOModel model) {
        final var out = new com.google.gson.JsonArray();
        for (final var position : lineup.getAllPositions()) {
            final var player = position.getPlayerId() > 0 ? model.getCurrentPlayer(position.getPlayerId()) : null;
            if (player != null && !McpJson.isAvailable(player)) {
                final var row = new JsonObject();
                row.addProperty(PLAYER_ID, player.getPlayerId());
                row.addProperty("name", player.getFullName());
                row.addProperty(ROLE_ID, position.getRoleId());
                row.addProperty("reason", unavailableReason(player));
                out.add(row);
            }
        }
        return out;
    }

    private static void rejectUnavailable(JsonObject spec) throws McpToolException {
        if (McpJson.optBool(spec, ALLOW_UNAVAILABLE, false)) {
            return;
        }
        // Deliberately not McpJson.model(): with no snapshot loaded there is nothing to check the
        // ids against, and the tools that need one fail on their own further down.
        final var model = core.model.HOVerwaltung.instance().getModel();
        if (model == null) {
            return;
        }
        final var complaints = new java.util.ArrayList<String>();
        for (final var element : McpJson.reqArray(spec, POSITIONS)) {
            final var playerId = McpJson.reqInt(element.getAsJsonObject(), PLAYER_ID);
            final var player = playerId > 0 ? model.getCurrentPlayer(playerId) : null;
            if (player != null && !McpJson.isAvailable(player)) {
                complaints.add(player.getFullName() + " (" + playerId + ") is " + unavailableReason(player));
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
        final var matchId = McpJson.optInteger(spec, MATCH_ID);
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
        for (final var element : McpJson.reqArray(spec, POSITIONS)) {
            final var position = element.getAsJsonObject();
            final var roleId = McpJson.reqInt(position, ROLE_ID);
            requireSlot(lineup, roleId);
            lineup.setSpielerAtPosition(roleId, McpJson.reqInt(position, PLAYER_ID),
                    (byte) McpJson.optInt(position, BEHAVIOUR, IMatchRoleID.NORMAL));
        }
        if (spec.has(BENCH)) {
            for (final var element : spec.getAsJsonArray(BENCH)) {
                final var slot = element.getAsJsonObject();
                final var roleId = McpJson.reqInt(slot, ROLE_ID);
                requireSlot(lineup, roleId);
                lineup.setSpielerAtPosition(roleId, McpJson.reqInt(slot, PLAYER_ID));
            }
        }
        if (spec.has(TACTIC)) {
            lineup.setTacticType(spec.get(TACTIC).getAsInt());
        }
        if (spec.has(ATTITUDE)) {
            lineup.setAttitude(spec.get(ATTITUDE).getAsInt());
        }
        if (spec.has(STYLE_OF_PLAY)) {
            lineup.setStyleOfPlay(spec.get(STYLE_OF_PLAY).getAsInt());
        }
        if (spec.has(CAPTAIN_ID)) {
            lineup.setCaptain(spec.get(CAPTAIN_ID).getAsInt());
        }
        if (spec.has(SET_PIECES_TAKER_ID)) {
            lineup.setKicker(spec.get(SET_PIECES_TAKER_ID).getAsInt());
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
            addCaptainCheck(json, team.getLineup());
            addLineupMatch(json, team, next);
            final var unavailable = unavailablePlayers(team.getLineup(), model);
            if (!unavailable.isEmpty()) {
                json.add(UNAVAILABLE_PLAYERS, unavailable);
            }
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
        json.add(MATCH_TYPE, matchId > 0 ? new com.google.gson.JsonPrimitive(team.getMatchType().getId()) : com.google.gson.JsonNull.INSTANCE);
        if (next == null) {
            json.add("nextMatch", com.google.gson.JsonNull.INSTANCE);
            return;
        }
        final var nextJson = new JsonObject();
        nextJson.addProperty(MATCH_ID, next.getMatchID());
        nextJson.addProperty(MATCH_TYPE, next.getMatchType().getId());
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
        if (!McpJson.optBool(spec, ALLOW_UNAVAILABLE, false)) {
            LineupOrders.rejectUnavailable(takers, orders, model);
        }
        return onEdt(() -> {
            // The live lineup is owned by its MatchLineupTeam - HOModel.setLineup takes a
            // MatchLineupTeam, not a Lineup - so mutate it in place and persist through the same
            // path the Lineup tab uses.
            final var team = model.getCurrentLineupTeam();
            final var before = describe(team.getLineup(), null);
            applySpec(team.getLineup(), spec);
            // matchId names the match the lineup is for, not only the fixture it is scored under:
            // without this it stays stored under the match HO loaded on start, and send_match_order
            // for matchId warns that it belongs to another one. That match keeps its stored row.
            final var matchId = McpJson.optInteger(spec, MATCH_ID);
            if (matchId != null && matchId != team.getMatchId()) {
                final var match = DBManager.instance().getMatchesKurzInfoByMatchID(matchId, null);
                team.setMatchId(matchId);
                team.setMatchType(match.getMatchType());
            }
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
            return applyResult(team, before, spec, ratingModel);
        });
    }

    /** What apply_lineup answers once HO has stored the lineup; runs on the EDT. */
    private static JsonObject applyResult(core.model.match.MatchLineupTeam team, JsonObject before,
            JsonObject spec, core.rating.RatingPredictionModel ratingModel) throws McpToolException {
        final var model = McpJson.model();
        // Without the rating model: the diff compares positions and orders, and the ratings
        // are computed once, below.
        final var after = describe(team.getLineup(), null);
        final var result = new JsonObject();
        result.addProperty("lineupMatchId", team.getMatchId());
        result.add("changed", lineupDiff(before, after));
        addCaptainCheck(result, team.getLineup());
        result.add("ratings", McpJson.ratings(ratingModel, team.getLineup()));
        if (McpJson.optBool(spec, FULL, false)) {
            result.add("lineup", after);
        }
        final var orderWarnings = LineupOrders.warnings(team.getLineup(), model);
        if (!orderWarnings.isEmpty()) {
            result.add("orderWarnings", orderWarnings);
        }
        final var refused = refusedByHo(team.getLineup(), spec);
        if (!refused.isEmpty()) {
            result.add("notApplied", refused);
            result.addProperty("warning", "HO did not keep " + refused.size()
                    + " of the requested positions - changed shows what it actually "
                    + "holds, not what was asked for. The Lineup tab's assistant filter "
                    + "unfields a player it hides, so this is usually its squad-group filter "
                    + "or its \"exclude players from the last match\" setting. Turn that "
                    + "setting off in HO, or field a player the filter allows. Do not report "
                    + "these positions as set.");
        }
        return result;
    }

    /**
     * The positions the spec asked for that the lineup does not hold once HO has had its say.
     * <p>
     * Empty in the ordinary case. A non-empty result is the honest answer to "did the write take",
     * and is the reason {@code apply_lineup} refreshes the Lineup tab before reporting: without
     * that the answer would be yes, and the tab would quietly undo it afterwards.
     */
    static JsonArray refusedByHo(Lineup lineup, JsonObject spec) {
        final var refused = new JsonArray();
        for (final var wanted : requestedSlots(spec)) {
            final var slot = lineup.getPositionById(wanted.roleId);
            if (slot != null && slot.getPlayerId() != wanted.playerId) {
                final var entry = new JsonObject();
                entry.addProperty(ROLE_ID, wanted.roleId);
                entry.addProperty("requestedPlayerId", wanted.playerId);
                entry.addProperty("actualPlayerId", slot.getPlayerId());
                refused.add(entry);
            }
        }
        return refused;
    }

    private record RequestedSlot(int roleId, int playerId) {
    }

    private static List<RequestedSlot> requestedSlots(JsonObject spec) {
        final var slots = new ArrayList<RequestedSlot>();
        for (final var element : McpJson.reqArray(spec, POSITIONS)) {
            final var position = element.getAsJsonObject();
            slots.add(new RequestedSlot(McpJson.reqInt(position, ROLE_ID),
                    McpJson.reqInt(position, PLAYER_ID)));
        }
        if (spec.has(BENCH)) {
            for (final var element : spec.getAsJsonArray(BENCH)) {
                final var slot = element.getAsJsonObject();
                slots.add(new RequestedSlot(McpJson.reqInt(slot, ROLE_ID),
                        McpJson.reqInt(slot, PLAYER_ID)));
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
        if (args.has(ASSIGNMENTS)) {
            for (final var element : args.getAsJsonArray(ASSIGNMENTS)) {
                final var entry = element.getAsJsonObject();
                final var playerId = McpJson.reqInt(entry, PLAYER_ID);
                assignments.add(new GroupAssignment(requirePlayer(model, playerId),
                        group(McpJson.reqString(entry, GROUP))));
            }
        } else {
            final var group = group(McpJson.reqString(args, GROUP));
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
                json.addProperty(PLAYER_ID, assignment.player().getPlayerId());
                json.addProperty("name", assignment.player().getFullName());
                json.addProperty(GROUP, assignment.player().getTeamGroup());
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
        json.add(POSITIONS, occupiedPositions(lineup));
        json.addProperty(TACTIC, lineup.getTacticType());
        json.addProperty(ATTITUDE, lineup.getAttitude());
        json.addProperty(CAPTAIN_ID, lineup.getCaptain());
        json.addProperty(SET_PIECES_TAKER_ID, lineup.getKicker());
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

    /** The field and bench slots someone stands in, as {roleId, playerId, behaviour}. */
    private static JsonArray occupiedPositions(Lineup lineup) {
        final var positions = new JsonArray();
        for (final var position : lineup.getAllPositions()) {
            // Field and bench only: penalty takers, set pieces taker and captain have their own
            // fields. Empty slots are left out, so a missing roleId is an empty one.
            final var roleId = position.getRoleId();
            final var field = roleId >= IMatchRoleID.keeper && roleId <= IMatchRoleID.leftForward;
            final var bench = roleId >= IMatchRoleID.startReserves && roleId <= IMatchRoleID.substXT2;
            if (!(field || bench) || position.getPlayerId() <= 0) {
                continue;
            }
            final var positionJson = new JsonObject();
            positionJson.addProperty(ROLE_ID, position.getRoleId());
            positionJson.addProperty(PLAYER_ID, position.getPlayerId());
            positionJson.addProperty(BEHAVIOUR, position.getBehaviour());
            positions.add(positionJson);
        }
        return positions;
    }

    /**
     * What apply_lineup changed, from two {@link #describe} snapshots without ratings. Positions
     * as {@code {roleId, from, to}}, a missing side being an empty slot; scalars as
     * {@code {from, to}}; penalty takers and team orders as the whole new list. Empty when
     * nothing changed.
     */
    static JsonObject lineupDiff(JsonObject before, JsonObject after) {
        final var diff = new JsonObject();
        final var was = byRole(before.getAsJsonArray(POSITIONS));
        final var now = byRole(after.getAsJsonArray(POSITIONS));
        final var roles = new java.util.TreeSet<>(was.keySet());
        roles.addAll(now.keySet());
        final var positions = new JsonArray();
        for (final var role : roles) {
            final var from = was.get(role);
            final var to = now.get(role);
            if (!java.util.Objects.equals(from, to)) {
                final var entry = new JsonObject();
                entry.addProperty(ROLE_ID, role);
                entry.add("from", withoutRole(from));
                entry.add("to", withoutRole(to));
                positions.add(entry);
            }
        }
        if (!positions.isEmpty()) {
            diff.add(POSITIONS, positions);
        }
        for (final var key : List.of(TACTIC, ATTITUDE, CAPTAIN_ID, SET_PIECES_TAKER_ID)) {
            if (!java.util.Objects.equals(before.get(key), after.get(key))) {
                final var change = new JsonObject();
                change.add("from", before.get(key));
                change.add("to", after.get(key));
                diff.add(key, change);
            }
        }
        for (final var key : List.of(LineupOrders.PENALTY_TAKERS, LineupOrders.TEAM_ORDERS)) {
            if (!java.util.Objects.equals(before.get(key), after.get(key))) {
                diff.add(key, after.get(key));
            }
        }
        return diff;
    }

    private static com.google.gson.JsonElement withoutRole(@Nullable JsonObject slot) {
        if (slot == null) {
            return com.google.gson.JsonNull.INSTANCE;
        }
        final var copy = slot.deepCopy();
        copy.remove(ROLE_ID);
        return copy;
    }

    /**
     * Hattrick gives the captain's bonus only to a captain on the pitch, and HO does not stop a
     * benched or unfielded one. No captain counts as false.
     */
    static void addCaptainCheck(JsonObject json, Lineup lineup) {
        final var captain = lineup.getCaptain();
        json.addProperty(CAPTAIN_ID, captain);
        json.addProperty("captainInStartingEleven", captain > 0
                && lineup.getFieldPositions().stream().anyMatch(p -> p.getPlayerId() == captain));
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
