package core.mcp;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import core.db.DBManager;
import core.model.enums.MatchType;
import core.model.match.MatchKurzInfo;
import core.model.match.Matchdetails;
import core.prediction.engine.MatchPredictionManager;
import core.rating.RatingPredictionModel.RatingSector;

import java.util.List;

/**
 * Played matches, and the Monte Carlo simulator on top of them.
 * <p>
 * {@code matchType} is half the key of every match row in the database - a match id alone does not
 * identify one - so it travels with every match this tool hands out.
 */
public final class MatchTools {

    private static final int DEFAULT_COUNT = 10;
    private static final int DEFAULT_SIMULATIONS = 1000;

    private MatchTools() {
    }

    public static List<McpTool> tools() {
        return List.of(
                new McpTool("get_matches",
                        "The team's played matches, most recent first: result, competition, "
                                + "location and outcome. Use it for recent form, and for the "
                                + "matchId / matchType pair that get_match needs.",
                        McpTool.schema(McpTool.properties(
                                "count", McpTool.prop("integer",
                                        "how many matches to return, default " + DEFAULT_COUNT),
                                "officialOnly", McpTool.prop("boolean",
                                        "league, cup and qualification only, leaving out friendlies "
                                                + "and tournaments; default true"))),
                        MatchTools::matches),
                new McpTool("get_match",
                        "One played match in full: both sides' sector ratings, HatStats, tactic and "
                                + "formation, the attendance, and our own players' match ratings. "
                                + "This is what makes a prediction checkable against what happened, "
                                + "and it is where the opponent ratings predict_match wants come "
                                + "from. Read the top level status: 'ok', or 'no_match_details' "
                                + "when HO has the result but never downloaded the details.",
                        McpTool.schema(McpTool.properties(
                                        "matchId", McpTool.prop("integer", "the Hattrick match id"),
                                        "matchType", McpTool.prop("integer",
                                                "the match type id from get_matches; without it the "
                                                        + "id is looked up on its own, which is "
                                                        + "ambiguous across source systems")),
                                "matchId"),
                        MatchTools::match),
                new McpTool("predict_match",
                        "Run HO's Monte Carlo match simulator: our current lineup against an "
                                + "opponent whose seven sector ratings you supply, and get win, "
                                + "draw and loss probabilities plus goals and chances per match. "
                                + "Take the opponent's ratings from get_match on one of their "
                                + "recent games.",
                        McpTool.schema(McpTool.properties(
                                        "opponentMidfield", McpTool.prop("number", "opponent midfield rating"),
                                        "opponentDefenceLeft", McpTool.prop("number", "opponent left defence rating"),
                                        "opponentDefenceCentral", McpTool.prop("number", "opponent central defence rating"),
                                        "opponentDefenceRight", McpTool.prop("number", "opponent right defence rating"),
                                        "opponentAttackLeft", McpTool.prop("number", "opponent left attack rating"),
                                        "opponentAttackCentral", McpTool.prop("number", "opponent central attack rating"),
                                        "opponentAttackRight", McpTool.prop("number", "opponent right attack rating"),
                                        "opponentName", McpTool.prop("string", "opponent team name, for the output only"),
                                        "opponentTacticType", McpTool.prop("integer", "opponent tactic type, 0 normal"),
                                        "opponentTacticSkill", McpTool.prop("integer", "opponent tactic level, default 1"),
                                        "location", McpTool.prop("string", "'home' or 'away', from our point of view; default home"),
                                        "simulations", McpTool.prop("integer",
                                                "how many matches to simulate, default " + DEFAULT_SIMULATIONS)),
                                "opponentMidfield", "opponentDefenceLeft", "opponentDefenceCentral",
                                "opponentDefenceRight", "opponentAttackLeft", "opponentAttackCentral",
                                "opponentAttackRight"),
                        MatchTools::predict)
        );
    }

    private static JsonObject matches(JsonObject args) throws McpToolException {
        final var teamId = McpJson.model().getBasics().getTeamId();
        final var count = McpJson.optInt(args, "count", DEFAULT_COUNT);
        final var officialOnly = McpJson.optBool(args, "officialOnly", true);

        final var played = new JsonArray();
        for (final var info : DBManager.instance().getOwnPlayedMatchInfo(count, officialOnly)) {
            played.add(summary(info, teamId));
        }
        final var json = new JsonObject();
        json.add("matches", played);
        return json;
    }

    /** Enough of a match to rank it and to ask for it in full. */
    private static JsonObject summary(MatchKurzInfo info, int teamId) {
        final var json = new JsonObject();
        json.addProperty("matchId", info.getMatchID());
        json.addProperty("matchTypeId", info.getMatchType().getId());
        json.addProperty("matchType", info.getMatchType().name());
        json.addProperty("date", info.getMatchSchedule() == null ? null : info.getMatchSchedule().toHT());
        json.addProperty("homeTeamId", info.getHomeTeamID());
        json.addProperty("homeTeamName", info.getHomeTeamName());
        json.addProperty("awayTeamId", info.getGuestTeamID());
        json.addProperty("awayTeamName", info.getGuestTeamName());
        json.addProperty("homeGoals", info.getHomeTeamGoals());
        json.addProperty("awayGoals", info.getGuestTeamGoals());

        final var atHome = info.getHomeTeamID() == teamId;
        final var ours = atHome ? info.getHomeTeamGoals() : info.getGuestTeamGoals();
        final var theirs = atHome ? info.getGuestTeamGoals() : info.getHomeTeamGoals();
        json.addProperty("location", atHome ? "home" : "away");
        json.addProperty("outcome", ours > theirs ? "win" : ours < theirs ? "loss" : "draw");
        return json;
    }

    private static JsonObject match(JsonObject args) throws McpToolException {
        final var teamId = McpJson.model().getBasics().getTeamId();
        final var matchId = McpJson.reqInt(args, "matchId");
        final var requestedType = McpJson.optInteger(args, "matchType");
        // A null matchType makes the lookup fall back to matching on the id alone, which is all a
        // caller holding a bare id can do.
        final var info = DBManager.instance().getMatchesKurzInfoByMatchID(matchId,
                requestedType == null ? null : MatchType.getById(requestedType));
        if (info == null) {
            throw new McpToolException("HO holds no match with id " + matchId
                    + ". Call get_matches for the ids it has downloaded.");
        }

        final var json = summary(info, teamId);
        final var details = DBManager.instance().loadMatchDetails(info.getMatchType().getId(), matchId);
        if (details == null) {
            // The result row and the details are downloaded separately, so "no details" is a real
            // state the user can fix, not an empty match.
            json.addProperty("status", "no_match_details");
            json.addProperty("note", "HO stored the result but not the match details. Download the "
                    + "match details for this match to get ratings, tactics and attendance.");
            return json;
        }

        json.addProperty("status", "ok");
        json.addProperty("attendance", details.getZuschauer());
        json.addProperty("weatherId", details.getWetterId());
        json.addProperty("arena", details.getArenaName());
        json.add("home", side(details, true));
        json.add("away", side(details, false));
        json.add("ourPlayers", playerRatings(matchId, info.getMatchType(), teamId));
        return json;
    }

    /** One side of a played match as Hattrick rated it - not a prediction. */
    private static JsonObject side(Matchdetails details, boolean home) {
        final var json = new JsonObject();
        json.addProperty("teamId", home ? details.getHomeTeamId() : details.getGuestTeamId());
        json.addProperty("teamName", home ? details.getHomeTeamName() : details.getGuestTeamName());
        json.addProperty("goals", home ? details.getHomeGoals() : details.getGuestGoals());
        json.addProperty("hatStats", home ? details.getHomeHatStats() : details.getAwayHatStats());
        json.addProperty("midfield", home ? details.getHomeMidfield() : details.getGuestMidfield());
        json.addProperty("defenceLeft", home ? details.getHomeLeftDef() : details.getGuestLeftDef());
        json.addProperty("defenceCentral", home ? details.getHomeMidDef() : details.getGuestMidDef());
        json.addProperty("defenceRight", home ? details.getHomeRightDef() : details.getGuestRightDef());
        json.addProperty("attackLeft", home ? details.getHomeLeftAtt() : details.getGuestLeftAtt());
        json.addProperty("attackCentral", home ? details.getHomeMidAtt() : details.getGuestMidAtt());
        json.addProperty("attackRight", home ? details.getHomeRightAtt() : details.getGuestRightAtt());
        json.addProperty("tacticType", home ? details.getHomeTacticType() : details.getGuestTacticType());
        json.addProperty("tacticSkill", home ? details.getHomeTacticSkill() : details.getGuestTacticSkill());
        json.addProperty("attitude", home ? details.getHomeEinstellung() : details.getGuestEinstellung());
        json.addProperty("formation", details.getFormation(home));
        return json;
    }

    /** Our own players' match ratings, which is what makes predicted versus actual checkable. */
    private static JsonArray playerRatings(int matchId, MatchType matchType, int teamId) {
        final var players = new JsonArray();
        for (final var position : DBManager.instance().getMatchLineupPlayers(matchId, matchType, teamId)) {
            if (position.getPlayerId() <= 0) {
                continue;
            }
            final var json = new JsonObject();
            json.addProperty("playerId", position.getPlayerId());
            json.addProperty("name", position.getSpielerName());
            json.addProperty("roleId", position.getRoleId());
            json.addProperty("behaviour", position.getBehaviour());
            json.addProperty("rating", position.getRating());
            json.addProperty("ratingEndOfMatch", position.getRatingStarsEndOfMatch());
            json.addProperty("startMinute", position.getStartMinute());
            players.add(json);
        }
        return players;
    }

    private static JsonObject predict(JsonObject args) throws McpToolException {
        final var model = McpJson.model();
        final var lineup = model.getCurrentLineup();
        // Private model for the same reason evaluate_lineup uses one: never touch HO's own rating
        // state from a tool call.
        final var ratingModel = McpJson.privateRatingModel();
        final var manager = MatchPredictionManager.instance();

        final var ourRatings = manager.generateTeamRatings(
                ratingModel.getAverageRating(lineup, RatingSector.MIDFIELD, 90),
                ratingModel.getAverageRating(lineup, RatingSector.DEFENCE_LEFT, 90),
                ratingModel.getAverageRating(lineup, RatingSector.DEFENCE_CENTRAL, 90),
                ratingModel.getAverageRating(lineup, RatingSector.DEFENCE_RIGHT, 90),
                ratingModel.getAverageRating(lineup, RatingSector.ATTACK_LEFT, 90),
                ratingModel.getAverageRating(lineup, RatingSector.ATTACK_CENTRAL, 90),
                ratingModel.getAverageRating(lineup, RatingSector.ATTACK_RIGHT, 90));
        final var ourTeam = manager.generateTeamData(model.getBasics().getTeamName(), ourRatings,
                lineup.getTacticType(), tacticLevel(ratingModel.getTacticRating(lineup, 0)));

        final var theirRatings = manager.generateTeamRatings(
                McpJson.reqDouble(args, "opponentMidfield"),
                McpJson.reqDouble(args, "opponentDefenceLeft"),
                McpJson.reqDouble(args, "opponentDefenceCentral"),
                McpJson.reqDouble(args, "opponentDefenceRight"),
                McpJson.reqDouble(args, "opponentAttackLeft"),
                McpJson.reqDouble(args, "opponentAttackCentral"),
                McpJson.reqDouble(args, "opponentAttackRight"));
        final var theirTeam = manager.generateTeamData(
                McpJson.optString(args, "opponentName", "Opponent"), theirRatings,
                McpJson.optInt(args, "opponentTacticType", 0),
                McpJson.optInt(args, "opponentTacticSkill", 1));

        final var weAreHome = !"away".equalsIgnoreCase(McpJson.optString(args, "location", "home"));
        final var simulations = Math.max(1, McpJson.optInt(args, "simulations", DEFAULT_SIMULATIONS));
        final var result = weAreHome
                ? manager.calculateNMatches(simulations, ourTeam, theirTeam)
                : manager.calculateNMatches(simulations, theirTeam, ourTeam);

        final var played = Math.max(1, result.getMatchNumber());
        final var ourWins = weAreHome ? result.getHomeWin() : result.getAwayWin();
        final var ourGoals = weAreHome ? result.getHomeGoals() : result.getGuestGoals();
        final var ourChances = weAreHome ? result.getHomeChances() : result.getGuestChances();
        final var theirGoals = weAreHome ? result.getGuestGoals() : result.getHomeGoals();
        final var theirChances = weAreHome ? result.getGuestChances() : result.getHomeChances();

        final var json = new JsonObject();
        json.addProperty("simulations", played);
        json.addProperty("location", weAreHome ? "home" : "away");
        json.addProperty("winProbability", (double) ourWins / played);
        json.addProperty("drawProbability", (double) result.getDraw() / played);
        json.addProperty("lossProbability",
                (double) (weAreHome ? result.getAwayWin() : result.getHomeWin()) / played);
        json.addProperty("goalsPerMatch", (double) ourGoals / played);
        json.addProperty("chancesPerMatch", (double) ourChances / played);
        json.addProperty("opponentGoalsPerMatch", (double) theirGoals / played);
        json.addProperty("opponentChancesPerMatch", (double) theirChances / played);
        json.add("ourRatings", McpJson.ratings(ratingModel, lineup));
        return json;
    }

    /** The tactic level as the Lineup tab shows it: the raw rating, rounded the way HO rounds it. */
    private static int tacticLevel(double tacticRating) {
        return (int) (tacticRating > 0 ? tacticRating + 1 : tacticRating);
    }
}
