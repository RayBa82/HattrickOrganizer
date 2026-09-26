package core.mcp;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import core.model.HOModel;
import core.model.TranslationFacility;
import core.model.match.MatchLineupPosition;
import core.model.player.IMatchRoleID;
import core.model.player.Player;
import core.rating.RatingPredictionModel;
import module.lineup.Lineup;
import module.lineup.substitution.model.GoalDiffCriteria;
import module.lineup.substitution.model.MatchOrderType;
import module.lineup.substitution.model.RedCardCriteria;
import module.lineup.substitution.model.Substitution;
import module.lineup.substitution.plausibility.OrderLimits;
import module.lineup.substitution.plausibility.PlausibilityCheck;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;

/**
 * Penalty takers and team orders (substitutions, behaviour changes, swaps, man marking) for
 * get_lineup, apply_lineup and send_match_order - what the Lineup tab's two other tabs hold.
 * <p>
 * Both lists are replaced whole by apply_lineup: an order has no key a partial update could
 * address, unlike a position's roleId.
 */
final class LineupOrders {

    static final String PENALTY_TAKERS = "penaltyTakers";
    static final String TEAM_ORDERS = "teamOrders";
    static final int MAX_PENALTY_TAKERS = 11;

    static final String PENALTY_TAKERS_DOC = "player ids in kicking order, at most 11; replaces "
            + "the whole list, [] clears it, omit to leave it as it is";
    static final String TEAM_ORDERS_DOC = "team orders, each {type: substitution|behaviour|swap|"
            + "manMarking, minute 0-119, playerOut, playerIn (not for behaviour; for manMarking the "
            + "opponent's player id), roleId 100-113 or -1 no change, behaviour 0-4 or -1 no change, "
            + "redCard, standing (the names get_lineup reports; omitted: any)}. At most 5 orders "
            + "plus 1 per Tactical Assistant level, 3 of them substitutions, 1 man marking. "
            + "Replaces the whole list, [] clears it, omit to leave it as it is";

    private LineupOrders() {
    }

    // ===================== output =====================

    static JsonArray penaltyTakers(Lineup lineup, @Nullable HOModel model,
                                   @Nullable RatingPredictionModel ratingModel) {
        final var takers = new JsonArray();
        for (final var slot : lineup.getPenaltyTakers()) {
            final var playerId = slot.getPlayerId();
            if (playerId <= 0) {
                continue;
            }
            final var json = new JsonObject();
            json.addProperty("playerId", playerId);
            final var player = model != null ? model.getCurrentPlayer(playerId) : null;
            if (player != null) {
                json.addProperty("name", player.getFullName());
                if (ratingModel != null) {
                    json.addProperty("strength",
                            Math.round(ratingModel.getPlayerPenaltyStrength(player) * 100) / 100.0);
                }
            }
            takers.add(json);
        }
        return takers;
    }

    static JsonArray teamOrders(List<Substitution> orders) {
        final var json = new JsonArray();
        for (final var order : orders) {
            final var entry = new JsonObject();
            entry.addProperty("type", typeName(order.getOrderType()));
            entry.addProperty("minute", order.getMatchMinuteCriteria());
            entry.addProperty("playerOut", order.getSubjectPlayerID());
            entry.addProperty("playerIn", order.getObjectPlayerID());
            entry.addProperty("roleId", order.getRoleId());
            entry.addProperty("behaviour", order.getBehaviour());
            entry.addProperty("redCard", name(order.getRedCardCriteria()));
            entry.addProperty("standing", name(order.getStanding()));
            json.add(entry);
        }
        return json;
    }

    private static String typeName(@Nullable MatchOrderType type) {
        if (type == null) {
            return "unknown";
        }
        return switch (type) {
            case SUBSTITUTION -> "substitution";
            case NEW_BEHAVIOUR -> "behaviour";
            case POSITION_SWAP -> "swap";
            case MAN_MARKING -> "manMarking";
        };
    }

    /** MY_CENTRAL_DEFENDER -> myCentralDefender; the enum's own typo is not passed on. */
    static String name(Enum<?> value) {
        final var words = value.name().replace("FORAWARD", "FORWARD").toLowerCase(Locale.ROOT).split("_");
        final var name = new StringBuilder(words[0]);
        for (var i = 1; i < words.length; i++) {
            name.append(Character.toUpperCase(words[i].charAt(0))).append(words[i].substring(1));
        }
        return name.toString();
    }

    // ===================== input =====================

    /** The spec's penalty takers, or null when it has none - the lineup's list stays then. */
    static @Nullable List<MatchLineupPosition> parsePenaltyTakers(JsonObject spec, @Nullable HOModel model)
            throws McpToolException {
        if (!spec.has(PENALTY_TAKERS)) {
            return null;
        }
        final var ids = McpJson.reqArray(spec, PENALTY_TAKERS);
        if (ids.size() > MAX_PENALTY_TAKERS) {
            throw new IllegalArgumentException("penaltyTakers holds " + ids.size()
                    + " players; Hattrick takes at most " + MAX_PENALTY_TAKERS + ".");
        }
        final var seen = new HashSet<Integer>();
        final var takers = new ArrayList<MatchLineupPosition>();
        for (final var element : ids) {
            final var playerId = element.getAsInt();
            if (!seen.add(playerId)) {
                throw new IllegalArgumentException("penaltyTakers names player " + playerId + " twice.");
            }
            requireOwnPlayer(model, playerId, "penaltyTakers");
            takers.add(new MatchLineupPosition(IMatchRoleID.penaltyTaker1 + takers.size(), playerId,
                    IMatchRoleID.NORMAL));
        }
        return takers;
    }

    /**
     * The spec's team orders, or null when it has none. Refuses what Hattrick would refuse -
     * a malformed order or one past the order limits - before anything is written.
     */
    static @Nullable List<Substitution> parseTeamOrders(JsonObject spec, @Nullable HOModel model,
                                                        int tacticalAssistantLevels) throws McpToolException {
        if (!spec.has(TEAM_ORDERS)) {
            return null;
        }
        final var orders = new ArrayList<Substitution>();
        for (final var element : McpJson.reqArray(spec, TEAM_ORDERS)) {
            orders.add(parseOrder(element.getAsJsonObject(), orders.size(), model));
        }
        final var problems = OrderLimits.check(orders, tacticalAssistantLevels);
        for (var i = 0; i < problems.size(); i++) {
            if (problems.get(i) != null) {
                throw new IllegalArgumentException("teamOrders[" + i + "] is one order too many: "
                        + "Hattrick allows " + OrderLimits.BASE_ORDERS + " orders plus 1 per "
                        + "Tactical Assistant level (this team: " + tacticalAssistantLevels + "), "
                        + "at most " + OrderLimits.MAX_SUBSTITUTIONS + " of them substitutions, and "
                        + OrderLimits.MAX_MAN_MARKINGS + " man marking order.");
            }
        }
        return orders;
    }

    private static Substitution parseOrder(JsonObject json, int index, @Nullable HOModel model)
            throws McpToolException {
        final var where = "teamOrders[" + index + "]";
        final var type = orderType(McpJson.reqString(json, "type"), where);
        final var minute = McpJson.reqInt(json, "minute");
        if (minute < 0 || minute > 119) {
            throw new IllegalArgumentException(where + ".minute is 0-119, not " + minute + ".");
        }
        final var playerOut = McpJson.reqInt(json, "playerOut");
        requireOwnPlayer(model, playerOut, where + ".playerOut");
        final int playerIn;
        if (type == MatchOrderType.NEW_BEHAVIOUR) {
            // Hattrick's convention for a behaviour change: the same player in both boxes.
            playerIn = playerOut;
        } else {
            playerIn = McpJson.reqInt(json, "playerIn");
            if (type != MatchOrderType.MAN_MARKING) {
                requireOwnPlayer(model, playerIn, where + ".playerIn");
            } else if (playerIn <= 0) {
                throw new IllegalArgumentException(where + ".playerIn is the opponent player to mark.");
            }
        }
        final var roleId = McpJson.optInt(json, "roleId", -1);
        if (roleId != -1 && (roleId < IMatchRoleID.keeper || roleId > IMatchRoleID.leftForward)) {
            throw new IllegalArgumentException(where + ".roleId is 100-113 or -1, not " + roleId + ".");
        }
        final var behaviour = McpJson.optInt(json, "behaviour", -1);
        if (behaviour < -1 || behaviour > IMatchRoleID.TOWARDS_WING) {
            throw new IllegalArgumentException(where + ".behaviour is 0-4 or -1, not " + behaviour + ".");
        }

        final var order = new Substitution(type);
        order.setPlayerOrderId(index);
        order.setMatchMinuteCriteria(minute);
        order.setSubjectPlayerID(playerOut);
        order.setObjectPlayerID(playerIn);
        order.setRoleId((byte) roleId);
        order.setBehaviour((byte) behaviour);
        order.setRedCardCriteria(byName(RedCardCriteria.values(),
                McpJson.optString(json, "redCard", name(RedCardCriteria.IGNORE)), where + ".redCard"));
        order.setStanding(byName(GoalDiffCriteria.values(),
                McpJson.optString(json, "standing", name(GoalDiffCriteria.ANY_STANDING)), where + ".standing"));
        return order;
    }

    private static MatchOrderType orderType(String type, String where) {
        return switch (type) {
            case "substitution" -> MatchOrderType.SUBSTITUTION;
            case "behaviour" -> MatchOrderType.NEW_BEHAVIOUR;
            case "swap" -> MatchOrderType.POSITION_SWAP;
            case "manMarking" -> MatchOrderType.MAN_MARKING;
            default -> throw new IllegalArgumentException(where + ".type is substitution, behaviour, "
                    + "swap or manMarking, not '" + type + "'.");
        };
    }

    private static <E extends Enum<E>> E byName(E[] values, String wanted, String where) {
        final var names = new ArrayList<String>();
        for (final var value : values) {
            if (name(value).equals(wanted)) {
                return value;
            }
            names.add(name(value));
        }
        throw new IllegalArgumentException(where + " is one of " + String.join(", ", names)
                + ", not '" + wanted + "'.");
    }

    private static void requireOwnPlayer(@Nullable HOModel model, int playerId, String where)
            throws McpToolException {
        if (playerId <= 0) {
            throw new IllegalArgumentException(where + " needs a player id, not " + playerId + ".");
        }
        // No snapshot loaded: nothing to check the id against, as in LineupTools.rejectUnavailable.
        if (model != null && model.getCurrentPlayer(playerId) == null) {
            throw new McpToolException(where + ": no player with id " + playerId + " is in the squad.");
        }
    }

    /**
     * Refuses an injured or suspended penalty taker or incoming player, the check positions get
     * in LineupTools. Players leaving the pitch and man-marking targets are not fielded by the order.
     */
    static void rejectUnavailable(@Nullable List<MatchLineupPosition> takers,
                                  @Nullable List<Substitution> orders, @Nullable HOModel model)
            throws McpToolException {
        if (model == null) {
            return;
        }
        final var complaints = new ArrayList<String>();
        if (takers != null) {
            for (final var taker : takers) {
                complainIfUnavailable(model.getCurrentPlayer(taker.getPlayerId()), "penalty taker", complaints);
            }
        }
        if (orders != null) {
            for (final var order : orders) {
                if (order.getOrderType() == MatchOrderType.SUBSTITUTION) {
                    complainIfUnavailable(model.getCurrentPlayer(order.getObjectPlayerID()),
                            "coming on", complaints);
                }
            }
        }
        if (!complaints.isEmpty()) {
            throw new McpToolException("Cannot use: " + String.join("; ", complaints)
                    + ". Pass allowUnavailable true to set them anyway.");
        }
    }

    private static void complainIfUnavailable(@Nullable Player player, String role, List<String> complaints) {
        if (player != null && !McpJson.isAvailable(player)) {
            complaints.add(player.getFullName() + " (" + player.getPlayerId() + ", " + role + ") is "
                    + (player.isRedCarded() ? "suspended" : "injured for " + player.getInjuryWeeks()
                    + " more week(s)"));
        }
    }

    /**
     * What Hattrick would accept but HO's own tabs flag, for the lineup as it now stands: an order
     * that can never fire (PlausibilityCheck, the team orders tab's check), a penalty taker not in
     * the lineup, a transfer-listed player. Warnings only - Hattrick takes all of these.
     */
    static JsonArray warnings(Lineup lineup, @Nullable HOModel model) {
        final var warnings = new JsonArray();
        final var orders = lineup.getSubstitutionList();
        for (var i = 0; i < orders.size(); i++) {
            final var order = orders.get(i);
            final var problem = PlausibilityCheck.checkForProblem(lineup, order);
            if (problem != null) {
                final var comment = PlausibilityCheck.getComment(problem, order);
                warnings.add("teamOrders[" + i + "]: "
                        + (comment != null ? comment : TranslationFacility.tr(problem.getLanguageKey())));
            }
            if (order.getOrderType() == MatchOrderType.SUBSTITUTION) {
                warnIfTransferListed(model, order.getObjectPlayerID(), "teamOrders[" + i + "].playerIn", warnings);
            }
        }
        for (final var taker : lineup.getPenaltyTakers()) {
            final var playerId = taker.getPlayerId();
            if (playerId <= 0) {
                continue;
            }
            if (!lineup.isPlayerInLineup(playerId)) {
                warnings.add("penalty taker " + playerId + " is neither on the pitch nor on the bench.");
            }
            warnIfTransferListed(model, playerId, "penalty taker", warnings);
        }
        return warnings;
    }

    private static void warnIfTransferListed(@Nullable HOModel model, int playerId, String role,
                                             JsonArray warnings) {
        final var player = model != null ? model.getCurrentPlayer(playerId) : null;
        if (player != null && player.getTransferListed() > 0) {
            warnings.add(role + " " + player.getFullName() + " (" + playerId + ") is transfer-listed.");
        }
    }

    // ===================== echo =====================

    /**
     * What a match order string sends, in get_lineup's shape. Built from the string itself, so it
     * is what left HO and not what HO meant to send.
     *
     * @param benchRoleIds the bench slots' role ids in the order the string lists them
     */
    static JsonObject echo(String orderString, List<Integer> benchRoleIds) {
        final var sent = JsonParser.parseString(orderString).getAsJsonObject();
        final var echo = new JsonObject();

        final var positions = new JsonArray();
        final var field = array(sent, "positions");
        for (var i = 0; i < field.size(); i++) {
            final var slot = field.get(i).getAsJsonObject();
            if (intOf(slot, "id", 0) > 0) {
                final var json = new JsonObject();
                json.addProperty("roleId", IMatchRoleID.keeper + i);
                json.addProperty("playerId", intOf(slot, "id", 0));
                json.addProperty("behaviour", intOf(slot, "behaviour", 0));
                positions.add(json);
            }
        }
        echo.add("positions", positions);

        final var bench = new JsonArray();
        final var benchSlots = array(sent, "bench");
        for (var i = 0; i < benchSlots.size() && i < benchRoleIds.size(); i++) {
            final var playerId = intOf(benchSlots.get(i).getAsJsonObject(), "id", 0);
            if (playerId > 0) {
                final var json = new JsonObject();
                json.addProperty("roleId", benchRoleIds.get(i));
                json.addProperty("playerId", playerId);
                bench.add(json);
            }
        }
        echo.add("bench", bench);

        echo.addProperty("captainId", intOf(sent, "captain", 0));
        echo.addProperty("setPiecesTakerId", intOf(sent, "setPieces", 0));
        final var settings = sent.has("settings") ? sent.getAsJsonObject("settings") : new JsonObject();
        echo.addProperty("tactic", intOf(settings, "tactic", 0));
        echo.addProperty("attitude", intOf(settings, "speechLevel", 0));
        echo.addProperty("styleOfPlay", intOf(settings, "coachModifier", 0));

        final var takers = new JsonArray();
        final var kickers = array(sent, "kickers");
        for (final var kicker : kickers) {
            final var playerId = intOf(kicker.getAsJsonObject(), "id", 0);
            if (playerId > 0) {
                takers.add(playerId);
            }
        }
        echo.add(PENALTY_TAKERS, takers);
        echo.addProperty("penaltySlotsSent", kickers.size());

        final var orders = new ArrayList<Substitution>();
        for (final var element : array(sent, "substitutions")) {
            final var order = element.getAsJsonObject();
            final var pos = intOf(order, "pos", -1);
            orders.add(new Substitution(orders.size(), intOf(order, "playerin", -1),
                    intOf(order, "playerout", -1), (byte) intOf(order, "orderType", 1),
                    intOf(order, "min", -1), (byte) (pos >= 0 ? IMatchRoleID.keeper + pos : -1),
                    (byte) intOf(order, "beh", -1),
                    RedCardCriteria.getById((byte) intOf(order, "card", -1)),
                    GoalDiffCriteria.getById((byte) intOf(order, "standing", -1))));
        }
        echo.add(TEAM_ORDERS, teamOrders(orders));
        return echo;
    }

    private static JsonArray array(JsonObject json, String name) {
        final var element = json.get(name);
        return element != null && element.isJsonArray() ? element.getAsJsonArray() : new JsonArray();
    }

    /** Gson writes numbers, and enums as their serialized name strings; getAsInt reads both. */
    private static int intOf(JsonObject json, String name, int fallback) {
        final JsonElement element = json.get(name);
        return element == null || element.isJsonNull() ? fallback : element.getAsInt();
    }
}
