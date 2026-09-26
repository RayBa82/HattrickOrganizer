package core.mcp;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import core.db.DBManager;
import core.file.xml.XMLManager;
import core.file.xml.XMLMatchArchivParser;
import core.file.xml.XMLMatchdetailsParser;
import core.model.cup.CupLevel;
import core.model.cup.CupLevelIndex;
import core.model.enums.MatchType;
import core.net.ChppWriteService;
import core.net.Connector;
import core.net.OnlineWorker;
import core.model.match.MatchEvent;
import core.model.match.MatchKurzInfo;
import core.model.match.Matchdetails;
import core.prediction.engine.MatchPredictionManager;
import core.rating.RatingPredictionModel.RatingSector;
import core.util.HODateTime;
import module.lineup.Lineup;

import org.w3c.dom.Element;

import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Played matches, and the Monte Carlo simulator on top of them.
 * <p>
 * {@code matchType} is half the key of every match row in the database - a match id alone does not
 * identify one - so it travels with every match this tool hands out.
 */
public final class MatchTools {

    private static final int DEFAULT_COUNT = 10;
    private static final int DEFAULT_SIMULATIONS = 1000;
    static final int DEFAULT_OPPONENT_MATCHES = 5;
    static final int DEFAULT_OPPONENT_DAYS = 120;

    private static final String[] OPPONENT_SECTORS = {
            "opponentMidfield", "opponentDefenceLeft", "opponentDefenceCentral",
            "opponentDefenceRight", "opponentAttackLeft", "opponentAttackCentral",
            "opponentAttackRight"};

    /** The keys {@link #side} writes the same seven sectors under, in the same order. */
    private static final String[] SECTOR_KEYS = {
            "midfield", "defenceLeft", "defenceCentral", "defenceRight",
            "attackLeft", "attackCentral", "attackRight"};

    private MatchTools() {
    }

    public static List<McpTool> tools() {
        return List.of(
                new McpTool("get_matches",
                        "The team's played matches, most recent first: result, competition, "
                                + "location and outcome. Use it for recent form, and for the "
                                + "matchId / matchType pair that get_match needs. CUP rows name "
                                + "the cup (cup, cupId).",
                        McpTool.schema(McpTool.properties(
                                "count", McpTool.prop("integer",
                                        "how many matches to return, default " + DEFAULT_COUNT),
                                "officialOnly", McpTool.prop("boolean",
                                        "league, cup and qualification only, leaving out friendlies "
                                                + "and tournaments; default true"))),
                        MatchTools::matches),
                new McpTool("get_match",
                        "One played match in full: both sides' sector ratings, HatStats, tactic, "
                                + "formation, attendance, and our own players' match ratings; "
                                + "includeEvents adds the event list. Read the top level status: "
                                + "'ok', or 'no_match_details' when HO has the result but never "
                                + "downloaded the details.",
                        McpTool.schema(McpTool.properties(
                                        "matchId", McpTool.prop("integer", "the Hattrick match id"),
                                        "matchType", McpTool.prop("integer",
                                                "the match type id from get_matches; without it the "
                                                        + "id is looked up on its own, which is "
                                                        + "ambiguous across source systems"),
                                        "includeEvents", McpTool.prop("boolean",
                                                "add events: per team goals, chances and special "
                                                        + "events, then every event (minute, name, "
                                                        + "team, player, objectPlayerId, goal/miss, "
                                                        + "specialEvent); default false")),
                                "matchId"),
                        MatchTools::match),
                new McpTool("get_match_weather",
                        "Weather of one of our fixtures, from the home team's region. forecast: "
                                + "HAPPENED (played), TODAY (reliable), TOMORROW (may change) or UNSURE "
                                + "(further out; only the region's tomorrow). An unsure one is refreshed "
                                + "and stored first (a few CHPP requests), so simulate_lineup / "
                                + "apply_lineup with this matchId use it. weatherId 0 rain, 1 overcast, "
                                + "2 partially cloudy, 3 sunny.",
                        McpTool.schema(McpTool.properties(
                                        "matchId", McpTool.prop("integer", "the Hattrick match id"),
                                        "matchType", McpTool.prop("integer",
                                                "the match type id; optional")),
                                "matchId"),
                        MatchTools::matchWeather),
                new McpTool("simulate_lineup",
                        "Score a lineup. Omit positions to score the one HO currently holds. Supply "
                                + "all seven opponent sector ratings (copy them from "
                                + "scout_opponent's ratings) for a Monte Carlo win/draw/loss forecast; "
                                + "omit them for sector ratings alone. Weather is the next match's "
                                + "stored forecast unless matchId or weather says otherwise (both need "
                                + "positions). Nothing in HO changes, so call it in a loop.",
                        McpTool.schema(
                                opponentProperties(LineupTools.lineupSchema().getAsJsonObject("properties"))),
                        MatchTools::simulateLineup),
                new McpTool("list_challenges",
                        "Friendly challenges this team has sent (outgoing) and been sent "
                                + "(incoming). accept / decline / withdraw need a trainingMatchId "
                                + "that only this list provides. checkTeamIds screens up to 20 teams "
                                + "before arrange_challenge (challengeable: whether we may challenge "
                                + "each now, manager login, supporter, bot; best first). A read - "
                                + "does not spend the write cap.",
                        McpTool.schema(McpTool.properties(
                                "weekend", McpTool.prop("boolean",
                                        "weekend friendlies instead of midweek; default false"),
                                CHECK_TEAM_IDS, McpTool.prop("array",
                                        "team ids to screen, 1-" + MAX_CHALLENGEABLE_TEAMS
                                                + "; omit to skip the check"))),
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
        final var checkIds = checkTeamIds(args);
        final core.file.xml.XMLChallengesParser.ChallengeLists lists;
        try {
            lists = ChppWriteService.instance().listChallengesForAgent(weekend);
        } catch (ChppWriteService.ChppWriteException e) {
            throw new McpToolException(e.getMessage());
        }
        final var out = new JsonObject();
        out.add("outgoing", challengeArray(lists.outgoing()));
        out.add("incoming", challengeArray(lists.incoming()));
        if (!checkIds.isEmpty()) {
            out.add("challengeable", challengeable(checkIds, weekend));
        }
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
            if (c.isAgreed() && c.matchId() > 0) {
                // Once agreed, the real match exists - hand back its id for get_match / send_match_order.
                j.addProperty("matchId", c.matchId());
            }
            arr.add(j);
        }
        return arr;
    }

    static final int MAX_CHALLENGEABLE_TEAMS = 20;

    private static final String CHECK_TEAM_IDS = "checkTeamIds";

    /** list_challenges' checkTeamIds: empty when absent, else 1-20 ids - checked before any CHPP call. */
    private static List<Integer> checkTeamIds(JsonObject args) {
        if (!args.has(CHECK_TEAM_IDS) || args.get(CHECK_TEAM_IDS).isJsonNull()) {
            return List.of();
        }
        final var ids = new java.util.ArrayList<Integer>();
        if (args.get(CHECK_TEAM_IDS).isJsonArray()) {
            args.getAsJsonArray(CHECK_TEAM_IDS).forEach(e -> ids.add(e.getAsInt()));
        }
        if (ids.isEmpty() || ids.size() > MAX_CHALLENGEABLE_TEAMS) {
            throw new IllegalArgumentException(
                    CHECK_TEAM_IDS + " must hold 1-" + MAX_CHALLENGEABLE_TEAMS + " team ids, had " + ids.size());
        }
        return ids;
    }

    private static JsonArray challengeable(List<Integer> ids, boolean weekend) throws McpToolException {
        final java.util.List<core.file.xml.XMLChallengesParser.Challengeable> answer;
        try {
            answer = ChppWriteService.instance().checkChallengeableForAgent(ids, weekend);
        } catch (ChppWriteService.ChppWriteException e) {
            throw new McpToolException(e.getMessage());
        }
        final var byId = new java.util.HashMap<Integer, core.file.xml.XMLChallengesParser.Challengeable>();
        answer.forEach(c -> byId.put(c.teamId(), c));

        final var rows = new java.util.ArrayList<JsonObject>();
        for (final var id : ids) {
            rows.add(challengeableRow(id, byId.get(id),
                    ChppWriteService.instance().teamActivityForAgent(id),
                    // Hattrick's dates are Swedish time
                    java.time.LocalDate.now(java.time.ZoneId.of("Europe/Stockholm"))));
        }
        rows.sort(CHALLENGEABLE_ORDER);
        final var teams = new JsonArray();
        rows.forEach(teams::add);
        return teams;
    }

    /** Challengeable first, then the most recent login first; unknown logins last. */
    static final java.util.Comparator<JsonObject> CHALLENGEABLE_ORDER = java.util.Comparator
            .<JsonObject, Boolean>comparing(r -> !McpJson.optBool(r, "challengeable", false))
            .thenComparing(r -> r.get("daysSinceLogin").isJsonNull()
                    ? Integer.MAX_VALUE : r.get("daysSinceLogin").getAsInt());

    /**
     * One team's row. {@code answer} is null when Hattrick left the team out of its reply;
     * {@code details} is empty when teamdetails could not be read. Neither is an error.
     */
    static JsonObject challengeableRow(int teamId,
            core.file.xml.XMLChallengesParser.Challengeable answer,
            java.util.Map<String, String> details, java.time.LocalDate today) {
        final var row = new JsonObject();
        row.addProperty("teamId", teamId);
        final var name = answer != null && !answer.teamName().isBlank()
                ? answer.teamName() : details.get("TeamName");
        row.addProperty("teamName", name);
        row.addProperty("challengeable", answer == null ? null : answer.isChallengeable());
        row.addProperty("series", details.get("LeagueLevelUnitName"));

        final var lastLogin = details.get("LastLoginDate");
        Integer days = null;
        if (lastLogin != null && lastLogin.length() >= 10) {
            try {
                days = (int) java.time.temporal.ChronoUnit.DAYS.between(
                        java.time.LocalDate.parse(lastLogin.substring(0, 10)), today);
            } catch (java.time.format.DateTimeParseException ignored) {
                // keep days null: the date is reported raw below
            }
        }
        row.addProperty("lastLoginDate", lastLogin);
        row.addProperty("daysSinceLogin", days);
        row.addProperty("managerIsSupporter", details.containsKey("HasSupporter")
                ? "True".equalsIgnoreCase(details.get("HasSupporter")) : null);
        final var isBot = "True".equalsIgnoreCase(details.get("IsBot"));
        row.addProperty("isBot", details.containsKey("IsBot") ? isBot : null);

        final String note;
        if (answer == null) {
            note = "Hattrick did not answer for this team";
        } else if (!answer.isChallengeable()) {
            note = "not challengeable now (Hattrick gives no reason)";
        } else if (isBot) {
            note = "bot team: will not answer a challenge";
        } else if (days != null && days > 7) {
            note = "manager inactive for " + days + " days: an answer is unlikely";
        } else {
            note = "ok";
        }
        row.addProperty("note", note);
        return row;
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
        addCup(json, info);
        return json;
    }

    /** One cup of our league, as worlddetails lists it. */
    record LeagueCup(String name, int leagueLevel) {
    }

    /** Our league's cups by cupId; loaded once per session, empty if worlddetails failed. */
    private static Map<Integer, LeagueCup> leagueCups;

    /**
     * Which cup a CUP match belongs to. cupId, cupLevel and cupLevelIndex come with the stored match
     * row (CHPP MatchContextId / CupLevel / CupLevelIndex); CHPP has no cup round on a match. Name and
     * National vs Divisional only exist in worlddetails' Cups list for our league - a cup that has
     * ended can drop out of it, so an old match may come without them.
     */
    static void addCup(JsonObject json, MatchKurzInfo info) {
        if (info.getMatchType() != MatchType.CUP) {
            return;
        }
        final var level = info.getCupLevel();
        final var index = info.getCupLevelIndex();
        final var cupId = info.getMatchContextId();
        json.addProperty("cupId", cupId);
        json.addProperty("cupLevel", level == null ? null : level.name());
        json.addProperty("cupLevelIndex", index == null ? null : index.name());
        final var cup = cupId > 0 ? leagueCups().get(cupId) : null;
        if (cup != null) {
            json.addProperty("cupName", cup.name());
            json.addProperty("cupLeagueLevel", cup.leagueLevel());
        }
        json.addProperty("cup", cupLabel(level, index, cup));
    }

    /** "National", "Divisional", "Emerald Challenger", ... - null when CHPP gave no level. */
    static String cupLabel(CupLevel level, CupLevelIndex index, LeagueCup cup) {
        if (level == null) {
            return null;
        }
        return switch (level) {
            case NATIONALorDIVISIONAL -> {
                if (cup == null) {
                    yield "National/Divisional";
                }
                yield cup.leagueLevel() == 0 ? "National" : "Divisional";
            }
            case CHALLENGER -> index == null || index == CupLevelIndex.NONE ? "Challenger"
                    : index.name().charAt(0) + index.name().substring(1).toLowerCase() + " Challenger";
            case CONSOLATION -> "Consolation";
            case NONE -> null;
        };
    }

    private static synchronized Map<Integer, LeagueCup> leagueCups() {
        if (leagueCups == null) {
            final var wasSilent = Connector.instance().setSilentDownload(true);
            try {
                leagueCups = parseLeagueCups(Connector.instance()
                        .getWorldDetails(McpJson.model().getBasics().getLiga()));
            } catch (Exception e) {
                // ponytail: a failed lookup stays empty for the session; the level fields still say which cup
                leagueCups = Map.of();
            } finally {
                Connector.instance().setSilentDownload(wasSilent);
            }
        }
        return leagueCups;
    }

    /** worlddetails (one league) -> its Cups by CupID. */
    static Map<Integer, LeagueCup> parseLeagueCups(String xml) {
        final var out = new HashMap<Integer, LeagueCup>();
        final var doc = xml == null || xml.isBlank() ? null : XMLManager.parseString(xml);
        if (doc == null) {
            return out;
        }
        final var cups = doc.getElementsByTagName("Cup");
        for (int i = 0; i < cups.getLength(); i++) {
            final var cup = (Element) cups.item(i);
            out.put(XMLManager.xmlIntValue(cup, "CupID"), new LeagueCup(
                    XMLManager.xmlValue(cup, "CupName"), XMLManager.xmlIntValue(cup, "CupLeagueLevel", 0)));
        }
        return out;
    }

    private static JsonObject match(JsonObject args) throws McpToolException {
        final var teamId = McpJson.model().getBasics().getTeamId();
        final var info = storedMatch(args);
        final var matchId = info.getMatchID();

        final var json = summary(info, teamId);
        final var details = storedDetails(info.getMatchType(), matchId);
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
        addSoldByStand(json, details);
        json.addProperty("arena", details.getArenaName());
        json.add("home", side(details, true));
        json.add("away", side(details, false));
        json.add("ourPlayers", playerRatings(matchId, info.getMatchType(), teamId));
        if (McpJson.optBool(args, "includeEvents", false)) {
            final var events = details.getHighlights();
            json.add("events", matchEventsJson(info.getHomeTeamID(), info.getGuestTeamID(),
                    events == null ? List.of() : events));
        }
        return json;
    }

    /**
     * The stored details of a match, or null when HO never downloaded them.
     * <p>
     * {@link DBManager#loadMatchDetails} hands back a blank {@link Matchdetails} rather than null
     * for a match it holds no row for, so "does HO have this" has to be asked of the content - a
     * null check reports a match with every rating at zero as a complete one.
     */
    static Matchdetails storedDetails(MatchType matchType, int matchId) {
        final var details = DBManager.instance().loadMatchDetails(matchType.getId(), matchId);
        return details != null && details.getMatchID() == matchId ? details : null;
    }

    /** The stored row of the match {@code args} names by matchId (+ optional matchType). */
    private static MatchKurzInfo storedMatch(JsonObject args) throws McpToolException {
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
        return info;
    }

    private static JsonObject matchWeather(JsonObject args) throws McpToolException {
        var info = storedMatch(args);
        if (!info.getWeatherForecast().isSure()) {
            // The match download's own path: matchdetails for the home region, then regiondetails,
            // stored on the match row where the Lineup tab reads it too.
            final var wasSilent = Connector.instance().setSilentDownload(true);
            try {
                if (!OnlineWorker.downloadMatchData(info, true)) {
                    throw new McpToolException("Refreshing the weather of match " + info.getMatchID()
                            + " from Hattrick failed; HO's log has the reason.");
                }
            } finally {
                Connector.instance().setSilentDownload(wasSilent);
            }
            info = storedMatch(args);
        }
        final var json = new JsonObject();
        json.addProperty("matchId", info.getMatchID());
        json.addProperty("matchType", info.getMatchType().name());
        json.addProperty("date", info.getMatchSchedule() == null ? null : info.getMatchSchedule().toHT());
        json.addProperty("homeTeamName", info.getHomeTeamName());
        json.addProperty("awayTeamName", info.getGuestTeamName());
        json.addProperty("regionId", info.getRegionId());
        json.addProperty("weatherId", info.getWeather().getId());
        json.addProperty("weather", info.getWeather().name());
        json.addProperty("forecast", info.getWeatherForecast().name());
        return json;
    }


    /**
     * Per-team goals / missed chances / special events, then every event in match order.
     * Event ids 100-199 are goals and 200-299 missed chances (Hattrick's own ranges).
     */
    static JsonObject matchEventsJson(int homeTeamId, int awayTeamId, List<MatchEvent> events) {
        final var json = new JsonObject();
        json.add("home", eventCounts(homeTeamId, events));
        json.add("away", eventCounts(awayTeamId, events));
        final var rows = new JsonArray();
        for (final var e : events) {
            rows.add(eventRow(e, e.getTeamID() == homeTeamId || e.getTeamID() == awayTeamId));
        }
        json.add("events", rows);
        return json;
    }

    /**
     * {@code side}: the event's team is one of the two. Weather / venue events reuse SubjectTeamID
     * for other data, so the team and player fields only count when it is.
     */
    private static JsonObject eventRow(MatchEvent e, boolean side) {
        final var row = new JsonObject();
        row.addProperty("minute", e.getMinute());
        row.addProperty("eventId", e.getiMatchEventID());
        row.addProperty("event", e.getMatchEventID() == null ? null : e.getMatchEventID().name());
        // Team-level events (tactics, possession, ...) carry event values in the player fields.
        // XMLMatchdetailsParser names an id only when it is in one of the two lineups, so a
        // stored name is what tells a real player from such a value.
        final var player = side && isPlayer(e.getPlayerId(), e.getPlayerName());
        row.addProperty("teamId", side ? e.getTeamID() : null);
        row.addProperty("playerId", player ? e.getPlayerId() : null);
        row.addProperty("playerName", player ? e.getPlayerName() : null);
        // Hattrick's ObjectPlayerID: the other player involved - the passer on some goals, but
        // often a player of the other side (the defender beaten, the keeper). Not an assist.
        final var object = side && isPlayer(e.getAssistingPlayerId(), e.getAssistingPlayerName());
        row.addProperty("objectPlayerId", object ? e.getAssistingPlayerId() : null);
        row.addProperty("objectPlayerName", object ? e.getAssistingPlayerName() : null);
        if (e.isGoalEvent() || e.isNonGoalEvent()) {
            row.addProperty("outcome", e.isGoalEvent() ? "goal" : "miss");
        }
        row.addProperty("specialEvent", e.getMatchEventID() != null && e.isSE());
        return row;
    }

    private static boolean isPlayer(int id, String name) {
        return id > 0 && name != null && !name.isBlank();
    }

    private static JsonObject eventCounts(int teamId, List<MatchEvent> events) {
        int goals = 0;
        int misses = 0;
        int specials = 0;
        for (final var e : events) {
            if (e.getTeamID() != teamId) continue;
            if (e.isGoalEvent()) goals++;
            if (e.isNonGoalEvent()) misses++;
            if (e.getMatchEventID() != null && e.isSE()) specials++;
        }
        final var json = new JsonObject();
        json.addProperty("teamId", teamId);
        json.addProperty("goals", goals);
        json.addProperty("chances", goals + misses);
        json.addProperty("missedChances", misses);
        json.addProperty("specialEvents", specials);
        return json;
    }


    /**
     * Adds {@code soldByStand} (terraces / basic / underRoof / vip) when HO stored the split. The
     * fields are 0 for away matches and -1 when never downloaded, so a split that sums to nothing is
     * left out. HO keeps no per-match capacity or prices.
     */
    static void addSoldByStand(JsonObject json, Matchdetails details) {
        final var sold = new int[] {details.getSoldTerraces(), details.getSoldBasic(),
                details.getSoldRoof(), details.getSoldVIP()};
        if (java.util.Arrays.stream(sold).filter(s -> s > 0).sum() <= 0) {
            return;
        }
        final var stands = new JsonObject();
        stands.addProperty("terraces", sold[0]);
        stands.addProperty("basic", sold[1]);
        stands.addProperty("underRoof", sold[2]);
        stands.addProperty("vip", sold[3]);
        json.add("soldByStand", stands);
    }


    /**
     * scout_opponent's ratings half: what a team - any team, not just one we have played - earned in its
     * recent matches, averaged into the arguments simulate_lineup asks for.
     */
    static JsonObject opponentRatings(JsonObject args) throws McpToolException {
        final var teamId = McpJson.reqInt(args, "teamId");
        final var count = Math.max(1, McpJson.optInt(args, "count", DEFAULT_OPPONENT_MATCHES));
        final var days = Math.max(1, McpJson.optInt(args, "days", DEFAULT_OPPONENT_DAYS));
        final var competitiveOnly = McpJson.optBool(args, "competitiveOnly", true);

        final var now = HODateTime.now();
        final var archive = Connector.instance()
                .getMatchesArchive(teamId, now.minus(days, ChronoUnit.DAYS), now);
        if (archive == null || archive.isBlank()) {
            throw new McpToolException("Hattrick returned no match archive for team " + teamId
                    + ". Check the team id.");
        }

        final var candidates = new ArrayList<>(XMLMatchArchivParser.parseMatchesFromString(archive));
        candidates.removeIf(m -> m.getMatchStatus() != MatchKurzInfo.FINISHED
                || m.getMatchType() == null
                || m.getMatchSchedule() == null
                // Reserves play the friendlies and the tournaments, so their ratings say little
                // about the side that will turn up for a league game.
                || (competitiveOnly && !(m.getMatchType().isOfficial() && !m.getMatchType().isFriendly())));
        candidates.sort(Comparator.comparing(MatchKurzInfo::getMatchSchedule).reversed());

        final var details = new ArrayList<Matchdetails>();
        for (final var match : candidates) {
            if (details.size() >= count) {
                break;
            }
            final var loaded = opponentMatchDetails(match);
            if (loaded != null) {
                details.add(loaded);
            }
        }
        if (details.isEmpty()) {
            throw new McpToolException("Team " + teamId + " has no finished match in the last " + days
                    + " days that could be read" + (competitiveOnly ? " (competitiveOnly is on)" : "")
                    + ". Widen days, or set competitiveOnly false.");
        }

        final var json = opponentReport(teamId, details);
        json.addProperty("matchesFound", candidates.size());
        return json;
    }

    /** Whatever HO already stored for the match, else one matchdetails request. */
    private static Matchdetails opponentMatchDetails(MatchKurzInfo match) {
        final var stored = storedDetails(match.getMatchType(), match.getMatchID());
        if (stored != null) {
            return stored;
        }
        final var xml = Connector.instance()
                .downloadMatchdetails(match.getMatchID(), match.getMatchType());
        if (xml == null || xml.isBlank()) {
            return null;
        }
        // No MatchLineup, which only costs the match report and the highlights - the ratings,
        // tactic and formation this reads are parsed either way.
        return XMLMatchdetailsParser.parseMatchdetailsFromString(xml, null);
    }

    /**
     * The report itself, over the matches to average - newest first. Separate from the downloading
     * above so the averaging and the home/away side pick can be tested without a network.
     */
    static JsonObject opponentReport(int teamId, List<Matchdetails> details) {
        final var latest = details.get(0);
        final var latestHome = latest.getHomeTeamId() == teamId;
        final var sums = new double[SECTOR_KEYS.length];
        final var matches = new JsonArray();
        String teamName = null;

        for (final var d : details) {
            final var home = d.getHomeTeamId() == teamId;
            final var entry = side(d, home);
            for (int i = 0; i < SECTOR_KEYS.length; i++) {
                sums[i] += entry.get(SECTOR_KEYS[i]).getAsDouble();
            }
            final var type = d.getMatchType();
            entry.addProperty("matchId", d.getMatchID());
            if (type != null) {
                entry.addProperty("matchTypeId", type.getId());
                entry.addProperty("matchType", type.name());
            }
            entry.addProperty("date", d.getMatchDate() == null ? null : d.getMatchDate().toHT());
            entry.addProperty("location", home ? "home" : "away");
            entry.addProperty("opponentTeamId", home ? d.getGuestTeamId() : d.getHomeTeamId());
            entry.addProperty("opponentTeamName", home ? d.getGuestTeamName() : d.getHomeTeamName());
            matches.add(entry);

            final var name = entry.get("teamName");
            if (teamName == null && name != null && !name.isJsonNull()) {
                teamName = name.getAsString();
            }
        }

        final var ratings = new JsonObject();
        ratings.addProperty("opponentName", teamName == null ? "Opponent" : teamName);
        for (int i = 0; i < SECTOR_KEYS.length; i++) {
            ratings.addProperty(OPPONENT_SECTORS[i],
                    Math.round(sums[i] / details.size() * 100.0) / 100.0);
        }
        // A tactic is a choice, not a quantity - averaging it would invent one nobody played. The
        // most recent match is the best single guess at what they will pick next.
        ratings.addProperty("opponentTacticType",
                latestHome ? latest.getHomeTacticType() : latest.getGuestTacticType());
        ratings.addProperty("opponentTacticSkill",
                latestHome ? latest.getHomeTacticSkill() : latest.getGuestTacticSkill());

        final var json = new JsonObject();
        json.addProperty("teamId", teamId);
        json.addProperty("teamName", teamName);
        json.addProperty("matchesUsed", details.size());
        json.add("opponentRatings", ratings);
        json.add("matches", matches);
        return json;
    }

    /** One side of a played match as Hattrick rated it - not a prediction. */
    private static JsonObject side(Matchdetails details, boolean home) {
        final var json = new JsonObject();
        json.addProperty("teamId", home ? details.getHomeTeamId() : details.getGuestTeamId());
        json.addProperty("teamName", home ? details.getHomeTeamName() : details.getGuestTeamName());
        json.addProperty("goals", home ? details.getHomeGoals() : details.getGuestGoals());
        json.addProperty("hatStats", home ? details.getHomeHatStats() : details.getAwayHatStats());
        json.addProperty("midfield", sectorRating(home ? details.getHomeMidfield() : details.getGuestMidfield()));
        json.addProperty("defenceLeft", sectorRating(home ? details.getHomeLeftDef() : details.getGuestLeftDef()));
        json.addProperty("defenceCentral", sectorRating(home ? details.getHomeMidDef() : details.getGuestMidDef()));
        json.addProperty("defenceRight", sectorRating(home ? details.getHomeRightDef() : details.getGuestRightDef()));
        json.addProperty("attackLeft", sectorRating(home ? details.getHomeLeftAtt() : details.getGuestLeftAtt()));
        json.addProperty("attackCentral", sectorRating(home ? details.getHomeMidAtt() : details.getGuestMidAtt()));
        json.addProperty("attackRight", sectorRating(home ? details.getHomeRightAtt() : details.getGuestRightAtt()));
        json.addProperty("tacticType", home ? details.getHomeTacticType() : details.getGuestTacticType());
        json.addProperty("tacticSkill", home ? details.getHomeTacticSkill() : details.getGuestTacticSkill());
        json.addProperty("attitude", home ? details.getHomeEinstellung() : details.getGuestEinstellung());
        json.addProperty("formation", details.getFormation(home));
        return json;
    }

    /**
     * Hattrick's matchdetails file reports sector ratings on its own raw quarter-point integer
     * scale (0..~80) - {@code module.teamanalyzer.ui.RatingUtil} and
     * {@code module.statistics.MatchesStatisticsPanel} already decode it the same way before
     * showing these numbers to a user. {@link core.rating.RatingPredictionModel#getAverageRating}
     * - what builds "ourRatings" for simulate_lineup's own Monte Carlo - is on that decoded scale,
     * not the raw one, so skipping this here left get_opponent_ratings's numbers roughly 4x hotter
     * than the ones they were compared against. Not a rounding difference: it once turned a
     * lineup that had just won 4-0 into a "99.7% chance to lose" forecast against the same team.
     */
    private static double sectorRating(int raw) {
        return (raw - 1) / 4.0 + 1;
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

    /**
     * simulate_lineup, the one lineup-scoring tool. The lineup is a scratch XI built from the args,
     * or HO's own when no positions are given; the opponent half is optional, and without it the
     * answer is the sector ratings alone - no Monte Carlo run.
     * <p>
     * A partial opponent is refused rather than silently treated as "no opponent": six sectors out
     * of seven is a caller who meant to simulate and dropped one, and answering that with ratings
     * would look like a working call.
     */

    private static JsonObject simulateLineup(JsonObject args) throws McpToolException {
        // Argument shape before any model access, so a malformed call is a -32602 rather than
        // "no snapshot loaded" from whichever lookup happened to run first.
        final List<String> missing = java.util.Arrays.stream(OPPONENT_SECTORS)
                .filter(sector -> !args.has(sector) || args.get(sector).isJsonNull())
                .toList();
        if (!missing.isEmpty() && missing.size() < OPPONENT_SECTORS.length) {
            throw new IllegalArgumentException("All seven opponent sector ratings are needed to "
                    + "simulate, or none of them to score the lineup on its own. Missing: "
                    + String.join(", ", missing) + ". scout_opponent's ratings holds all seven under "
                    + "exactly these names.");
        }

        if (!args.has("positions") && (args.has("weather") || args.has("matchId"))) {
            throw new IllegalArgumentException("weather and matchId need positions: without them "
                    + "HO's live lineup is scored, and a simulation never changes it. Copy the "
                    + "positions from get_lineup.");
        }
        final var lineup = args.has("positions")
                ? LineupTools.scratchLineup(args)
                : McpJson.model().getCurrentLineup();
        if (!missing.isEmpty()) {
            // No opponent: score the lineup on its own, on a private model like the simulation does.
            return McpJson.ratings(McpJson.privateRatingModel(), lineup);
        }
        return simulate(lineup, args);
    }

    private static JsonObject simulate(Lineup lineup, JsonObject args) throws McpToolException {
        final var model = McpJson.model();
        // Private model for the same reason the ratings-only path uses one: never touch HO's own
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
                lineup.getTacticType(), McpJson.tacticLevel(ratingModel.getTacticRating(lineup, 0)));

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
    /**
     * Adds the opponent side and the simulation controls to a properties object - the half of
     * simulate_lineup's schema that turns a scoring call into a simulation. Nothing here is
     * required, but the seven sector ratings ({@link #OPPONENT_SECTORS}) are all-or-nothing.
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

}
