package core.mcp;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import core.db.DBManager;
import core.model.enums.MatchType;
import core.net.ChppWriteService;
import core.model.match.MatchKurzInfo;
import core.model.match.Matchdetails;
import core.prediction.engine.MatchPredictionManager;
import core.rating.RatingPredictionModel.RatingSector;
import module.lineup.Lineup;

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

    private static final String[] OPPONENT_SECTORS = {
            "opponentMidfield", "opponentDefenceLeft", "opponentDefenceCentral",
            "opponentDefenceRight", "opponentAttackLeft", "opponentAttackCentral",
            "opponentAttackRight"};

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
                        McpTool.schema(opponentProperties(new JsonObject()), OPPONENT_SECTORS),
                        MatchTools::predict),
                new McpTool("simulate_lineup",
                        "predict_match for a lineup you have not set: a hypothetical XI, supplied "
                                + "the same way as evaluate_lineup, against an opponent whose seven "
                                + "sector ratings you supply. Nothing in HO changes - the scratch "
                                + "lineup is scored on a private model - so call it in a loop to "
                                + "compare XIs against the next opponent. Omit positions and it "
                                + "falls back to the current lineup, exactly like predict_match.",
                        McpTool.schema(
                                opponentProperties(LineupTools.lineupSchema().getAsJsonObject("properties")),
                                OPPONENT_SECTORS),
                        MatchTools::simulateLineup),
                new McpTool("list_challenges",
                        "Friendly challenges: the ones this team has sent (outgoing) and the ones "
                                + "other teams have sent it (incoming). accept / decline / withdraw "
                                + "need a trainingMatchId that only this list provides. A read - "
                                + "does not spend the write cap.",
                        McpTool.schema(McpTool.properties(
                                "weekend", McpTool.prop("boolean",
                                        "list weekend friendly challenges instead of midweek; default false"))),
                        MatchTools::listChallenges),
                new McpTool("arrange_challenge",
                        "Send a friendly challenge to another team. Leaves the machine. Poll "
                                + "list_challenges afterwards for whether the opponent agreed.",
                        McpTool.schema(McpTool.properties(
                                        "opponentTeamId", McpTool.prop("integer", "the team to challenge"),
                                        "friendlyType", McpTool.prop("integer",
                                                "0 normal rules, 1 cup rules; default 0"),
                                        "location", McpTool.prop("string",
                                                "home, away or neutral; default home"),
                                        "neutralArenaId", McpTool.prop("integer",
                                                "arena id, required when location is neutral"),
                                        "weekend", McpTool.prop("boolean",
                                                "arrange it as a weekend friendly; default false")),
                                "opponentTeamId"),
                        MatchTools::arrangeChallenge),
                new McpTool("respond_to_challenge",
                        "Accept, decline or withdraw a friendly challenge by its trainingMatchId "
                                + "(from list_challenges). Leaves the machine.",
                        McpTool.schema(McpTool.properties(
                                        "trainingMatchId", McpTool.prop("integer",
                                                "the challenge id from list_challenges"),
                                        "action", McpTool.prop("string",
                                                "accept, decline or withdraw")),
                                "trainingMatchId", "action"),
                        MatchTools::respondToChallenge)
        );
    }

    private static JsonObject listChallenges(JsonObject args) throws McpToolException {
        final var weekend = McpJson.optBool(args, "weekend", false);
        final core.file.xml.XMLChallengesParser.ChallengeLists lists;
        try {
            lists = ChppWriteService.instance().listChallengesForAgent(weekend);
        } catch (ChppWriteService.ChppWriteException e) {
            throw new McpToolException(e.getMessage());
        }
        final var out = new JsonObject();
        out.add("outgoing", challengeArray(lists.outgoing()));
        out.add("incoming", challengeArray(lists.incoming()));
        return out;
    }

    private static JsonArray challengeArray(
            java.util.List<core.file.xml.XMLChallengesParser.Challenge> challenges) {
        final var arr = new JsonArray();
        for (final var c : challenges) {
            final var j = new JsonObject();
            j.addProperty("trainingMatchId", c.trainingMatchId());
            j.addProperty("opponentTeamId", c.opponentTeamId());
            j.addProperty("opponentName", c.opponentName());
            j.addProperty("matchTime", c.matchTime());
            j.addProperty("friendlyType", c.friendlyType());
            j.addProperty("arenaName", c.arenaName());
            j.addProperty("isAgreed", c.isAgreed());
            arr.add(j);
        }
        return arr;
    }

    private static JsonObject arrangeChallenge(JsonObject args) throws McpToolException {
        final var opponentTeamId = McpJson.reqInt(args, "opponentTeamId");
        final var friendlyType = McpJson.optInt(args, "friendlyType", 0);
        final var location = McpJson.optString(args, "location", "home");
        final int matchPlace = switch (location) {
            case "home" -> 0;
            case "away" -> 1;
            case "neutral" -> 2;
            default -> throw new IllegalArgumentException(
                    "location must be home, away or neutral, was " + location);
        };
        final Integer neutralArenaId = McpJson.optInteger(args, "neutralArenaId");
        if (matchPlace == 2 && neutralArenaId == null) {
            throw new IllegalArgumentException("location=neutral needs neutralArenaId.");
        }
        final var weekend = McpJson.optBool(args, "weekend", false);
        try {
            ChppWriteService.instance().arrangeChallengeForAgent(
                    opponentTeamId, friendlyType, matchPlace, neutralArenaId, weekend);
        } catch (ChppWriteService.ChppWriteException e) {
            throw new McpToolException(e.getMessage());
        }
        final var out = new JsonObject();
        out.addProperty("status", "ok");
        out.addProperty("note", "challenge sent; poll list_challenges for the opponent's response");
        return out;
    }

    private static JsonObject respondToChallenge(JsonObject args) throws McpToolException {
        final var trainingMatchId = McpJson.reqLong(args, "trainingMatchId");
        final var action = McpJson.reqString(args, "action");
        if (!java.util.Set.of("accept", "decline", "withdraw").contains(action)) {
            throw new IllegalArgumentException(
                    "action must be accept, decline or withdraw, was " + action);
        }
        try {
            ChppWriteService.instance().respondToChallengeForAgent(action, trainingMatchId);
        } catch (ChppWriteService.ChppWriteException e) {
            throw new McpToolException(e.getMessage());
        }
        final var out = new JsonObject();
        out.addProperty("status", "ok");
        out.addProperty("action", action);
        out.addProperty("trainingMatchId", trainingMatchId);
        return out;
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

    /** predict_match: our current HO lineup versus the supplied opponent. */
    private static JsonObject predict(JsonObject args) throws McpToolException {
        return simulate(McpJson.model().getCurrentLineup(), args);
    }

    /**
     * simulate_lineup: same simulator, but on a scratch lineup built from the args - or, when no
     * positions are given, on the current lineup, which makes it a drop-in for predict_match.
     */
    private static JsonObject simulateLineup(JsonObject args) throws McpToolException {
        final var lineup = args.has("positions")
                ? LineupTools.scratchLineup(args)
                : McpJson.model().getCurrentLineup();
        return simulate(lineup, args);
    }

    private static JsonObject simulate(Lineup lineup, JsonObject args) throws McpToolException {
        final var model = McpJson.model();
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

    /**
     * Adds the opponent side and the simulation controls to a properties object - the half of the
     * schema that predict_match and simulate_lineup share. The seven sector ratings are the only
     * required arguments ({@link #OPPONENT_SECTORS}); everything else has a default.
     */
    private static JsonObject opponentProperties(JsonObject properties) {
        properties.add("opponentMidfield", McpTool.prop("number", "opponent midfield rating"));
        properties.add("opponentDefenceLeft", McpTool.prop("number", "opponent left defence rating"));
        properties.add("opponentDefenceCentral", McpTool.prop("number", "opponent central defence rating"));
        properties.add("opponentDefenceRight", McpTool.prop("number", "opponent right defence rating"));
        properties.add("opponentAttackLeft", McpTool.prop("number", "opponent left attack rating"));
        properties.add("opponentAttackCentral", McpTool.prop("number", "opponent central attack rating"));
        properties.add("opponentAttackRight", McpTool.prop("number", "opponent right attack rating"));
        properties.add("opponentName", McpTool.prop("string", "opponent team name, for the output only"));
        properties.add("opponentTacticType", McpTool.prop("integer", "opponent tactic type, 0 normal"));
        properties.add("opponentTacticSkill", McpTool.prop("integer", "opponent tactic level, default 1"));
        properties.add("location", McpTool.prop("string",
                "'home' or 'away', from our point of view; default home"));
        properties.add("simulations", McpTool.prop("integer",
                "how many matches to simulate, default " + DEFAULT_SIMULATIONS));
        return properties;
    }

    /** The tactic level as the Lineup tab shows it: the raw rating, rounded the way HO rounds it. */
    private static int tacticLevel(double tacticRating) {
        return (int) (tacticRating > 0 ? tacticRating + 1 : tacticRating);
    }
}
