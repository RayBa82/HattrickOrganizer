package core.mcp;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import core.constants.player.PlayerSkill;
import core.db.DBManager;
import core.model.misc.Economy;
import core.model.player.IMatchRoleID;
import core.model.player.MatchRoleID;
import core.model.player.Specialty;
import core.util.AmountOfMoney;
import module.opponentspy.OppPlayerSkillEstimator;
import module.transfer.XMLParser;
import module.teamanalyzer.ht.HattrickManager;
import module.teamanalyzer.manager.PlayerDataManager;
import module.teamanalyzer.vo.PlayerInfo;
import tool.arenasizer.ArenaAdmission;
import tool.arenasizer.ArenaMaintenance;
import tool.arenasizer.ArenaSizer;
import tool.arenasizer.Stadium;

import com.google.gson.JsonNull;
import core.gui.HOMainFrame;
import core.gui.RefreshManager;
import core.model.HOVerwaltung;
import core.model.UserParameter;
import core.model.match.MatchKurzInfo;
import core.net.Connector;
import core.net.OnlineWorker;
import core.util.HODateTime;
import core.util.HOLogger;

import java.util.List;

/** The read only view of the team HO already knows about. */
public final class TeamTools {

    private static final int DEFAULT_HOME_MATCHES = 5;

    /** The seven skills the opponent estimator fills in, and the names {@code get_squad} uses. */
    private static final PlayerSkill[] ESTIMATED_SKILLS = {
            PlayerSkill.KEEPER, PlayerSkill.DEFENDING, PlayerSkill.PLAYMAKING, PlayerSkill.WINGER,
            PlayerSkill.PASSING, PlayerSkill.SCORING, PlayerSkill.SETPIECES};

    private static final String[] ESTIMATED_SKILL_NAMES = {
            "keeper", "defending", "playmaking", "winger", "passing", "scoring", "setPieces"};

    private TeamTools() {
    }

    public static List<McpTool> tools() {
        return List.of(
                new McpTool("get_team",
                        "The user's team as of the latest download: name, league, cash, stadium, "
                                + "team spirit and confidence, the head coach and the rest of the "
                                + "staff. get_finances has the full statement.",
                        McpTool.schema(new JsonObject()),
                        args -> team()),
                new McpTool("get_squad",
                        "Every player in the squad with skills, form, stamina, loyalty, TSI, wage, "
                                + "specialty, injuries, cards, squad group and the three character "
                                + "traits (0-5), plus a positionRatings map giving what he is worth "
                                + "in each of the fourteen field slots (100 keeper through 113 left "
                                + "forward) at normal behaviour.",
                        McpTool.schema(McpTool.properties(
                                "playerIds", McpTool.prop("array",
                                        "only these players; the whole squad if omitted. An id not "
                                                + "in the squad is an error"),
                                "includeTransfers", McpTool.prop("boolean",
                                        "add each player's transfers as HO recorded them; default "
                                                + "false"))),
                        TeamTools::squad),
                new McpTool("scout_opponent",
                        "Pre-match scouting of another team. ratings: the sector ratings it earned "
                                + "in recent matches, averaged and named as simulate_lineup wants "
                                + "them (~1 CHPP request per match). squad: visible attributes, "
                                + "injuries, cards, lastMatch per player (highest matchId = last "
                                + "eleven); estimatedSkills is HO's guess (1 request).",
                        McpTool.schema(McpTool.properties(
                                        "teamId", McpTool.prop("integer",
                                                "the Hattrick team id, e.g. an opponent id from "
                                                        + "get_series"),
                                        "include", McpTool.prop("array",
                                                "ratings and/or squad; default both"),
                                        "count", McpTool.prop("integer",
                                                "ratings: how many recent matches to average, "
                                                        + "default " + MatchTools.DEFAULT_OPPONENT_MATCHES + "; keep it small"),
                                        "competitiveOnly", McpTool.prop("boolean",
                                                "ratings: league, cup, qualification and masters "
                                                        + "only, leaving out friendlies and "
                                                        + "tournaments; default true"),
                                        "days", McpTool.prop("integer",
                                                "ratings: how far back to search, in days; "
                                                        + "default " + MatchTools.DEFAULT_OPPONENT_DAYS)),
                                "teamId"),
                        TeamTools::scoutOpponent),
                new McpTool("get_series",
                        "Our series: table (all eight teams with position, record, points, goals), "
                                + "matches (the season's fixtures), otherUpcoming (our cup and "
                                + "friendly matches, soonest first) and nextCupMatch (null = "
                                + "eliminated or not yet drawn); cup rows name the cup (cup, cupId). "
                                + "As of HO's last download; get_match_weather refreshes a forecast.",
                        McpTool.schema(new JsonObject()),
                        args -> series()),
                new McpTool("get_bookmarks",
                        "The user's Hattrick bookmarks, read-only. type is team, player, match, "
                                + "user, league, youth*, forumPost or forumThread; objectId is that "
                                + "object's id (team: text=team name, text2=manager; player: "
                                + "text2=transfer deadline; match: text2=match date; league: "
                                + "objectId2=LeagueID). One CHPP request.",
                        McpTool.schema(new JsonObject()),
                        args -> bookmarks()),
                new McpTool("get_finances",
                        "The full weekly financial statement with last week's copy beside it, the "
                                + "fan club, the staff levels that drive the cost line, and the "
                                + "stadium with its recent home attendance and utilisation. Pass "
                                + "sizeArena to add HO's arena sizer - what to build, and what it "
                                + "costs to build and to keep. Amounts are in the currency field's "
                                + "currency.",
                        McpTool.schema(McpTool.properties(
                                "homeMatches", McpTool.prop("integer",
                                        "how many recent home matches to read the attendance of, "
                                                + "default " + DEFAULT_HOME_MATCHES),
                                "sizeArena", McpTool.prop("boolean",
                                        "add the arena sizer block: three target sizes "
                                                + "(pessimistic, normal, optimistic - attendance "
                                                + "swings with form and opponent), each with its "
                                                + "rebuild cost, weekly maintenance and sold-out "
                                                + "gate; default false"),
                                "supporters", McpTool.prop("integer",
                                        "size the arena for this fan club instead of the current "
                                                + "one; implies sizeArena"),
                                "capacity", McpTool.prop("integer",
                                        "price this exact total capacity instead of the three "
                                                + "scenarios; implies sizeArena"))),
                        TeamTools::finances),
                new McpTool("download_team",
                        "Runs HO's own Download and rewrites the local database - squad, finances, "
                                + "staff, stadium, matches, fixtures, transfers, sub-skills. Call it "
                                + "when the user changed something in Hattrick that HO cannot see. "
                                + "Twenty to sixty-plus CHPP requests with HO's window frozen "
                                + "throughout, so not a routine refresh. It replaces HO's stored "
                                + "lineup with Hattrick's match order: check lineupReplaced.",
                        McpTool.schema(McpTool.properties()),
                        TeamTools::downloadTeam)
        );
    }

    /** CHPP BookmarkTypeID 1..11; index 0 unused. */
    private static final String[] BOOKMARK_TYPES = {null, "team", "player", "match", "user",
            "league", "youthTeam", "youthPlayer", "youthMatch", "youthLeague", "forumPost",
            "forumThread"};

    private static JsonObject bookmarks() throws McpToolException {
        try {
            return bookmarksJson(core.net.ChppWriteService.instance().bookmarksForAgent());
        } catch (core.net.ChppWriteService.ChppWriteException e) {
            throw new McpToolException(e.getMessage());
        }
    }

    static JsonObject bookmarksJson(List<core.file.xml.XMLBookmarksParser.Bookmark> bookmarks) {
        final var rows = new JsonArray();
        for (final var b : bookmarks) {
            final var json = new JsonObject();
            json.addProperty("type", b.typeId() > 0 && b.typeId() < BOOKMARK_TYPES.length
                    ? BOOKMARK_TYPES[b.typeId()] : "type" + b.typeId());
            json.addProperty("text", b.text());
            json.addProperty("text2", b.text2());
            json.addProperty("objectId", b.objectId());
            json.addProperty("objectId2", b.objectId2());
            json.addProperty("objectId3", b.objectId3());
            json.addProperty("comment", b.comment());
            rows.add(json);
        }
        final var out = new JsonObject();
        out.add("bookmarks", rows);
        return out;
    }

    /**
     * HO's arena sizer: what to build, and what that costs to build and to keep.
     * <p>
     * A block inside {@code get_finances} rather than a tool of its own. The stands it reasons
     * about are already one line of the statement, and shipping them from two tools meant two
     * byte-identical copies of the same stadium block in every session.
     */
    private static JsonObject arenaSizer(Stadium current, int currentFans, JsonObject args) {
        final var json = new JsonObject();
        json.addProperty("weeklyMaintenance", money(ArenaMaintenance.calculateCosts(current)));
        json.addProperty("soldOutGateReceipts", money(ArenaAdmission.calculateIncome(current)));

        final var supporters = McpJson.optInt(args, "supporters", currentFans);
        json.addProperty("supporters", supporters);

        final var capacity = McpJson.optInteger(args, "capacity");
        final var plans = new JsonArray();
        if (capacity != null) {
            // The sizer only knows how to build three at once; ask it for the same size thrice.
            plans.add(plan("target",
                    ArenaSizer.calcConstructionArenas(current, capacity, capacity, capacity)[0]));
        } else {
            final var sized = ArenaSizer.calcConstructionArenas(current, supporters);
            plans.add(plan("optimistic", sized[0]));
            plans.add(plan("normal", sized[1]));
            plans.add(plan("pessimistic", sized[2]));
        }
        json.add("recommendations", plans);
        return json;
    }

    /** One sized arena: the stands it would have, what has to be built, and what that costs. */
    static JsonObject plan(String scenario, Stadium sized) {
        final var json = new JsonObject();
        json.addProperty("scenario", scenario);
        json.addProperty("capacity", sized.getTotalSize());
        json.addProperty("terraces", sized.getTerraces());
        json.addProperty("basicSeating", sized.getBasicSeating());
        json.addProperty("underRoofSeating", sized.getUnderRoofSeating());
        json.addProperty("vipBoxes", sized.getVipBox());
        json.addProperty("terracesToBuild", sized.getTerracesUnderConstruction());
        json.addProperty("basicSeatingToBuild", sized.getBasicSeatingUnderConstruction());
        json.addProperty("underRoofSeatingToBuild", sized.getUnderRoofSeatingUnderConstruction());
        json.addProperty("vipBoxesToBuild", sized.getVipBoxUnderConstruction());
        json.addProperty("rebuildCost", money(sized.getExpansionCosts()));
        json.addProperty("weeklyMaintenance", money(ArenaMaintenance.calculateCosts(sized)));
        json.addProperty("soldOutGateReceipts", money(ArenaAdmission.calculateIncome(sized)));
        return json;
    }

    private static JsonObject team() throws McpToolException {
        final var model = McpJson.model();
        final var basics = model.getBasics();
        final var economy = model.getEconomy();
        final var league = model.getLeague();
        final var stadium = model.getStadium();

        final var json = new JsonObject();
        json.addProperty("teamId", basics.getTeamId());
        json.addProperty("teamName", basics.getTeamName());
        json.addProperty("manager", basics.getManager());
        json.addProperty("season", basics.getSeason());
        json.addProperty("week", basics.getSpieltag());

        json.addProperty("leagueId", basics.getLiga());
        json.addProperty("countryId", basics.getLand());

        if (league != null) {
            // core.model.series.Liga: getLiga() is the series name, not an id
            final var leagueJson = new JsonObject();
            leagueJson.addProperty("seriesName", league.getLiga());
            leagueJson.addProperty("position", league.getPlatzierung());
            leagueJson.addProperty("points", league.getPunkte());
            leagueJson.addProperty("goalsFor", league.getToreFuer());
            leagueJson.addProperty("goalsAgainst", league.getToreGegen());
            json.add("league", leagueJson);
        }
        if (economy != null) {
            final var economyJson = new JsonObject();
            economyJson.addProperty("currency", McpJson.currency());
            economyJson.addProperty("cash", money(economy.getCash()));
            economyJson.addProperty("expectedWeeksTotal", money(economy.getExpectedWeeksTotal()));
            json.add("economy", economyJson);
        }
        if (stadium != null) {
            final var stadiumJson = new JsonObject();
            stadiumJson.addProperty("name", stadium.getStadiumName());
            stadiumJson.addProperty("capacity", stadium.getTotalSize());
            json.add("stadium", stadiumJson);
        }

        final var team = model.getTeam();
        if (team != null) {
            // Team spirit and confidence are inputs to every lineup rating, so they belong here
            // rather than only inside evaluate_lineup's output.
            final var moodJson = new JsonObject();
            moodJson.addProperty("teamSpirit", team.getTeamSpiritLevel());
            moodJson.addProperty("subTeamSpirit", team.getSubTeamSpirit());
            moodJson.addProperty("confidence", team.getConfidence());
            json.add("mood", moodJson);
        }

        // The head coach is not a staff member - he is a Player with a coach skill - and his level
        // drives both training speed and the team spirit ceiling.
        // getTrainer() never returns null - it hands back an empty Player for teams without a
        // coach - so a team with no coach shows up as coachSkill 0 rather than a missing block.
        final var trainer = model.getTrainer();
        final var coachJson = new JsonObject();
        coachJson.addProperty("playerId", trainer.getPlayerId());
        coachJson.addProperty("name", trainer.getFullName());
        coachJson.addProperty("coachSkill", trainer.getCoachSkill());
        coachJson.addProperty("leadership", trainer.getLeadership());
        coachJson.addProperty("trainerType",
                trainer.getTrainerType() == null ? null : trainer.getTrainerType().toString());
        if (model.getClub() != null) {
            coachJson.addProperty("assistantCoaches", model.getClub().getCoTrainer());
        }
        json.add("coach", coachJson);

        final var staff = new JsonArray();
        if (model.getStaff() != null) {
            for (final var member : model.getStaff()) {          // core.model.StaffMember
                final var staffJson = new JsonObject();
                staffJson.addProperty("name", member.getName());
                // XMLStaffParser files the head coach as StaffType 0 (NONE)
                staffJson.addProperty("type", member.getStaffType() == core.model.StaffType.NONE
                        ? "HEAD_COACH" : String.valueOf(member.getStaffType()));
                staffJson.addProperty("level", member.getLevel());
                staffJson.addProperty("weeklyCost", McpJson.money(member.getCost()));
                staff.add(staffJson);
            }
        }
        json.add("staff", staff);
        return json;
    }

    /** An amount in the user's currency; see {@link McpJson#money}. */
    private static long money(AmountOfMoney amount) {
        return McpJson.money(amount);
    }

    private static JsonObject squad(JsonObject args) throws McpToolException {
        final var model = McpJson.model();
        final var ratingModel = McpJson.privateRatingModel();
        final var withTransfers = McpJson.optBool(args, "includeTransfers", false);
        final var wanted = new java.util.LinkedHashSet<Integer>();
        if (args.has("playerIds") && args.get("playerIds").isJsonArray()) {
            args.getAsJsonArray("playerIds").forEach(e -> wanted.add(e.getAsInt()));
        }

        final var players = new JsonArray();
        for (final var player : model.getCurrentPlayers()) {
            if (!wanted.isEmpty() && !wanted.remove(player.getPlayerId())) {
                continue;
            }
            final var json = McpJson.player(player, ratingModel);
            if (withTransfers) {
                json.add("transfers", transfers(player.getPlayerId()));
            }
            players.add(json);
        }
        if (!wanted.isEmpty()) {
            throw new McpToolException("Not in the squad: " + wanted + ". Call get_squad without "
                    + "playerIds for the ids HO knows about.");
        }
        final var json = new JsonObject();
        json.add("players", players);
        return json;
    }

    /** A player's transfers as HO recorded them locally. */
    private static JsonArray transfers(int playerId) {
        final var transfers = new JsonArray();
        for (final var transfer : DBManager.instance().getTransfers(playerId, true)) {
            final var transferJson = new JsonObject();
            transferJson.addProperty("date", transfer.getDate() == null ? null : transfer.getDate().toHT());
            transferJson.addProperty("price", money(transfer.getPrice()));
            transferJson.addProperty("tsi", transfer.getTsi());
            transferJson.addProperty("type", transfer.getType());
            transferJson.addProperty("buyerTeamName", transfer.getBuyerName());
            transferJson.addProperty("sellerTeamName", transfer.getSellerName());
            transfers.add(transferJson);
        }
        return transfers;
    }

    /**
     * scout_opponent's squad half: the players file for a team that is not ours.
     * <p>
     * Routed through {@link HattrickManager#downloadPlayers} rather than {@code Connector} directly
     * because that is the one call in the codebase that knows what the players file yields for a
     * *foreign* team — {@link PlayerInfo} deliberately reads only the fields Hattrick publishes for
     * one, which is why there are no skills here. It also files the result in the Team Analyzer's
     * player store, the same rows that module writes when the user opens its tab.
     */
    private static JsonObject opponentSquad(JsonObject args) throws McpToolException {
        // The estimator and the Team Analyzer store both read the current season and week off the
        // loaded snapshot, so fail with the usual message rather than a NullPointerException.
        McpJson.model();
        final var teamId = McpJson.reqInt(args, "teamId");

        final var players = HattrickManager.downloadPlayers(teamId);
        if (players == null || players.isEmpty()) {
            throw new McpToolException("Hattrick returned no squad for team " + teamId
                    + ". Check the team id - get_series lists the ones in our series.");
        }

        final var estimator = new OppPlayerSkillEstimator();
        final var squad = new JsonArray();
        for (final var info : players) {
            squad.add(opponentPlayer(info, estimator));
        }

        final var json = new JsonObject();
        json.addProperty("teamId", teamId);
        json.addProperty("playerCount", players.size());
        json.addProperty("currency", McpJson.currency());
        json.add("players", squad);
        json.addProperty("note", "Hattrick publishes no skills for another team's players. "
                + "estimatedSkills is HO's opponent-spy estimate from age, wage, TSI, form, "
                + "stamina, specialty and the position the player last played (estimatedForRoleId) "
                + "- a guess, and absent entirely for a player whose last position is unknown. The "
                + "players sharing the highest lastMatch.matchId were that match's eleven.");
        return json;
    }

    /** scout_opponent: ratings and/or squad (default both), one round trip. */
    private static JsonObject scoutOpponent(JsonObject args) throws McpToolException {
        final var include = new java.util.HashSet<String>();
        if (args.has("include") && args.get("include").isJsonArray()) {
            args.getAsJsonArray("include").forEach(e -> include.add(e.getAsString()));
        }
        if (include.isEmpty()) {
            include.addAll(SCOUT_PARTS);
        } else if (!SCOUT_PARTS.containsAll(include)) {
            throw new IllegalArgumentException("include takes ratings and/or squad, had " + include);
        }
        final var json = new JsonObject();
        if (include.contains("ratings")) {
            json.add("ratings", MatchTools.opponentRatings(args));
        }
        if (include.contains("squad")) {
            json.add("squad", opponentSquad(args));
        }
        return json;
    }

    private static final java.util.Set<String> SCOUT_PARTS = java.util.Set.of("ratings", "squad");

    static JsonObject opponentPlayer(PlayerInfo info, OppPlayerSkillEstimator estimator) {
        final var json = new JsonObject();
        json.addProperty("playerId", info.getPlayerId());
        json.addProperty("name", info.getName());
        json.addProperty("age", info.getAge());
        json.addProperty("tsi", info.getTsi());
        json.addProperty("wage", money(info.getSalary()));
        json.addProperty("form", info.getForm());
        json.addProperty("stamina", info.getStamina());
        json.addProperty("experience", info.getExperience());
        json.addProperty("loyalty", info.getLoyalty());
        json.addProperty("motherclub", info.isMotherClubBonus());
        json.addProperty("specialty", info.getSpecialEvent());
        final var specialty = Specialty.getSpecialty(info.getSpecialEvent());
        json.addProperty("specialtyName", specialty == null ? null : specialty.toString());
        // -1 fit, 0 bruised, above that the weeks left - the same scale get_squad reports.
        json.addProperty("injuryWeeks", info.getInjuryLevel());
        json.addProperty("bookingStatus", bookingStatus(info.getBookingStatus()));
        json.addProperty("transferListed", info.isTransferListed());
        json.addProperty("available", info.getInjuryLevel() <= 0
                && info.getBookingStatus() != PlayerDataManager.SUSPENDED);

        // Where and how well he last played: the only performance data Hattrick publishes about
        // another team's player, and collectively the eleven that started their last match.
        final var last = new JsonObject();
        last.addProperty("matchId", info.getLastMatchId());
        last.addProperty("date",
                info.getLastMatchDate() == null ? null : info.getLastMatchDate().toHT());
        last.addProperty("roleId", info.getLastMatchPosition());
        last.addProperty("playedMinutes", info.getLastMatchPlayedMinutes());
        last.addProperty("rating", info.getRating());
        last.addProperty("ratingEndOfMatch", info.getLastMatchRatingEndOfGame());
        json.add("lastMatch", last);

        // The estimator picks its starting skills off the position, and its fall-through branch is
        // "goalkeeper" - so an unknown position has to skip the estimate rather than be given a
        // keeper's. A player who has never played, or came off the bench, lands here.
        final var position = MatchRoleID.getPosition(info.getLastMatchPosition(), IMatchRoleID.NORMAL);
        if (position != IMatchRoleID.UNKNOWN && position != IMatchRoleID.EXTRA) {
            final var estimated = estimator.calcPlayer(info.getAge(), info.getSalary(),
                    info.getTsi(), info.getForm(), info.getStamina(), info.getSpecialEvent(),
                    position, info.getInjuryLevel());
            final var skills = new JsonObject();
            for (int i = 0; i < ESTIMATED_SKILLS.length; i++) {
                skills.addProperty(ESTIMATED_SKILL_NAMES[i],
                        estimated.getValue4Skill(ESTIMATED_SKILLS[i]));
            }
            json.add("estimatedSkills", skills);
            json.addProperty("estimatedForRoleId", info.getLastMatchPosition());
        }
        return json;
    }

    /** The card state as a word, rather than the packed int teamAnalyzer stores it as. */
    private static String bookingStatus(int bookingStatus) {
        return switch (bookingStatus) {
            case PlayerDataManager.YELLOW -> "yellow";
            case PlayerDataManager.DOUBLE_YELLOW -> "doubleYellow";
            case PlayerDataManager.SUSPENDED -> "suspended";
            default -> "none";
        };
    }

    /**
     * get_series. Table and fixtures come from the one stored plan keyed on our league level unit
     * and the current season - not {@code HOModel.getFixtures()}, which is whichever plan was
     * fetched last and can be an old season's series until the next team download.
     */
    private static JsonObject series() throws McpToolException {
        final var model = McpJson.model();
        final var xtra = model.getXtraDaten();
        // The league LEVEL UNIT id, not Basics.getLiga() - that one is the league, not the series.
        final var plan = xtra == null ? null
                : DBManager.instance().getSpielplan(xtra.getLeagueLevelUnitID(), model.getBasics().getSeason());

        final var json = new JsonObject();
        final var matches = new JsonArray();
        if (plan != null) {
            json.addProperty("seriesName", plan.getLigaName());
            json.addProperty("leagueLevelUnitId", plan.getLigaId());
            json.addProperty("season", plan.getSaison());
            for (final var pairing : plan.getMatches()) {
                final var matchJson = new JsonObject();
                matchJson.addProperty("matchId", pairing.getMatchId());
                matchJson.addProperty("matchDay", pairing.getSpieltag());
                matchJson.addProperty("date", pairing.getDatum() == null ? null : pairing.getDatum().toHT());
                matchJson.addProperty("homeTeamId", pairing.getHeimId());
                matchJson.addProperty("homeTeamName", pairing.getHeimName());
                matchJson.addProperty("awayTeamId", pairing.getGastId());
                matchJson.addProperty("awayTeamName", pairing.getGastName());
                matches.add(matchJson);
            }
        }
        json.add("matches", matches);
        if (plan == null || plan.getTable() == null) {
            json.add("table", JsonNull.INSTANCE);
            json.addProperty("tableNote", "HO has not stored this season's series yet - "
                    + "download_team fetches it.");
        } else {
            json.add("table", tableRows(plan));
        }

        final var teamId = model.getBasics().getTeamId();
        final var others = ownNonLeagueUpcoming(
                DBManager.instance().getMatchesKurzInfoUpComing(teamId), teamId);
        json.add("otherUpcoming", others);
        JsonElement nextCup = JsonNull.INSTANCE;
        for (final var m : others) {
            if ("CUP".equals(m.getAsJsonObject().get("matchType").getAsString())) {
                nextCup = m;
                break;
            }
        }
        json.add("nextCupMatch", nextCup);
        return json;
    }

    /**
     * Our own upcoming cup, friendly and other non-league matches, soonest first, as HO last
     * downloaded them. No cup match here means eliminated - or a download older than the draw.
     */
    static JsonArray ownNonLeagueUpcoming(java.util.List<MatchKurzInfo> upcoming, int teamId) {
        final var sorted = new java.util.ArrayList<>(upcoming);
        sorted.sort(java.util.Comparator.comparing(MatchKurzInfo::getMatchSchedule,
                java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder())));
        final var out = new JsonArray();
        sorted.forEach(m -> out.add(upcomingRow(m, teamId)));
        return out;
    }

    private static JsonObject upcomingRow(MatchKurzInfo m, int teamId) {
        final var home = m.getHomeTeamID() == teamId;
        final var row = new JsonObject();
        row.addProperty("matchId", m.getMatchID());
        row.addProperty("matchType", m.getMatchType().name());
        row.addProperty("date", m.getMatchSchedule() == null ? null : m.getMatchSchedule().toHT());
        final var where = home ? "home" : "away";
        row.addProperty("location", m.isNeutral() ? "neutral" : where);
        row.addProperty("opponentTeamId", home ? m.getGuestTeamID() : m.getHomeTeamID());
        row.addProperty("opponentTeamName", home ? m.getGuestTeamName() : m.getHomeTeamName());
        // As stored at the last download; get_match_weather refreshes a forecast that is not sure yet.
        row.addProperty("weather", m.getWeather().name());
        row.addProperty("weatherForecast", m.getWeatherForecast().name());
        MatchTools.addCup(row, m);
        return row;
    }

    private static JsonArray tableRows(module.series.MatchFixtures plan) {
        final var rows = new JsonArray();
        for (final var entry : plan.getTable().getEntries()) {
            final var row = new JsonObject();
            row.addProperty("position", entry.getPosition());
            row.addProperty("previousPosition", entry.getPreviousPosition());
            row.addProperty("teamId", entry.getTeamId());
            row.addProperty("teamName", entry.getTeamName());
            row.addProperty("played", entry.getAnzSpiele());
            row.addProperty("won", entry.getG_Siege());
            row.addProperty("drawn", entry.getG_Un());
            row.addProperty("lost", entry.getG_Nied());
            row.addProperty("points", entry.getPoints());
            row.addProperty("goalsFor", entry.getGoalsFor());
            row.addProperty("goalsAgainst", entry.getGoalsAgainst());
            row.addProperty("goalDifference", entry.getGoalsDiff());
            rows.add(row);
        }
        return rows;
    }

    private static JsonObject finances(JsonObject args) throws McpToolException {
        final var model = McpJson.model();
        final var economy = model.getEconomy();
        if (economy == null) {
            throw new McpToolException("HO holds no financial statement for the latest download.");
        }

        final var json = new JsonObject();
        json.addProperty("currency", McpJson.currency());
        json.addProperty("cash", money(economy.getCash()));
        json.addProperty("expectedCash", money(economy.getExpectedCash()));
        json.addProperty("expectedWeeksTotal", money(economy.getExpectedWeeksTotal()));
        json.addProperty("sponsorsPopularity", economy.getSponsorsPopularity());
        json.addProperty("supportersPopularity", economy.getSupportersPopularity());
        // Last week's copy is what makes the statement readable as a trend rather than a snapshot.
        json.add("thisWeek", thisWeek(economy));
        json.add("lastWeek", lastWeek(economy));

        final var club = model.getClub();
        if (club != null) {
            // The staff levels next door on Verein are what drives the cost line above.
            final var clubJson = new JsonObject();
            clubJson.addProperty("fans", club.getFans());
            clubJson.addProperty("doctors", club.getAerzte());
            clubJson.addProperty("sportPsychologists", club.getPsychologen());
            clubJson.addProperty("spokespersons", club.getPRManager());
            clubJson.addProperty("assistantCoaches", club.getCoTrainer());
            clubJson.addProperty("financialDirectors", club.getFinancialDirectorLevels());
            clubJson.addProperty("formCoaches", club.getFormCoachLevels());
            clubJson.addProperty("tacticalAssistants", club.getTacticalAssistantLevels());
            json.add("club", clubJson);
        }

        if (model.getStadium() != null) {
            json.add("stadium", stadium(model.getStadium(), model.getBasics().getTeamId(),
                    McpJson.optInt(args, "homeMatches", DEFAULT_HOME_MATCHES)));
            // Off by default - the sizer is three more objects of arithmetic nobody asked for when
            // the question was "how much cash do we have". Naming either knob implies wanting it.
            if (McpJson.optBool(args, "sizeArena", false)
                    || args.has("supporters") || args.has("capacity")) {
                json.add("arenaSizer", arenaSizer(model.getStadium(),
                        model.getClub() == null ? 0 : model.getClub().getFans(), args));
            }
        }
        return json;
    }

    private static JsonObject thisWeek(Economy economy) {
        final var json = new JsonObject();
        json.addProperty("incomeSum", money(economy.getIncomeSum()));
        json.addProperty("incomeSponsors", money(economy.getIncomeSponsors()));
        json.addProperty("incomeSponsorsBonus", money(economy.getIncomeSponsorsBonus()));
        json.addProperty("incomeSpectators", money(economy.getIncomeSpectators()));
        json.addProperty("incomeFinancial", money(economy.getIncomeFinancial()));
        json.addProperty("incomeSoldPlayers", money(economy.getIncomeSoldPlayers()));
        json.addProperty("incomeSoldPlayersCommission", money(economy.getIncomeSoldPlayersCommission()));
        json.addProperty("costsSum", money(economy.getCostsSum()));
        json.addProperty("costsPlayers", money(economy.getCostsPlayers()));
        json.addProperty("costsArena", money(economy.getCostsArena()));
        json.addProperty("costsStaff", money(economy.getCostsStaff()));
        json.addProperty("costsYouth", money(economy.getCostsYouth()));
        json.addProperty("costsFinancial", money(economy.getCostsFinancial()));
        json.addProperty("costsBoughtPlayers", money(economy.getCostsBoughtPlayers()));
        json.addProperty("costsArenaBuilding", money(economy.getCostsArenaBuilding()));
        return json;
    }

    private static JsonObject lastWeek(Economy economy) {
        final var json = new JsonObject();
        json.addProperty("incomeSum", money(economy.getLastIncomeSum()));
        json.addProperty("incomeSponsors", money(economy.getLastIncomeSponsors()));
        json.addProperty("incomeSponsorsBonus", money(economy.getLastIncomeSponsorsBonus()));
        json.addProperty("incomeSpectators", money(economy.getLastIncomeSpectators()));
        json.addProperty("incomeFinancial", money(economy.getLastIncomeFinancial()));
        json.addProperty("incomeSoldPlayers", money(economy.getLastIncomeSoldPlayers()));
        json.addProperty("incomeSoldPlayersCommission", money(economy.getLastIncomeSoldPlayersCommission()));
        json.addProperty("costsSum", money(economy.getLastCostsSum()));
        json.addProperty("costsPlayers", money(economy.getLastCostsPlayers()));
        json.addProperty("costsArena", money(economy.getLastCostsArena()));
        json.addProperty("costsStaff", money(economy.getLastCostsStaff()));
        json.addProperty("costsYouth", money(economy.getLastCostsYouth()));
        json.addProperty("costsFinancial", money(economy.getLastCostsFinancial()));
        json.addProperty("costsBoughtPlayers", money(economy.getLastCostsBoughtPlayers()));
        json.addProperty("costsArenaBuilding", money(economy.getLastCostsArenaBuilding()));
        json.addProperty("weeksTotal", money(economy.getLastWeeksTotal()));
        return json;
    }

    /**
     * The stands, plus what actually turned up at the last home matches. Capacity alone cannot
     * answer an expansion question - the ratio between the two can, so both sources are joined here.
     */
    private static JsonObject stadium(Stadium stadium, int teamId, int homeMatches) {
        final var json = new JsonObject();
        json.addProperty("name", stadium.getStadiumName());
        json.addProperty("capacity", stadium.getTotalSize());
        json.addProperty("terraces", stadium.getTerraces());
        json.addProperty("basicSeating", stadium.getBasicSeating());
        json.addProperty("underRoofSeating", stadium.getUnderRoofSeating());
        json.addProperty("vipBoxes", stadium.getVipBox());
        stadium.getTotalSizeUnderConstruction()
                .ifPresent(size -> json.addProperty("capacityUnderConstruction", size));
        stadium.getFutureTotalSize().ifPresent(size -> json.addProperty("futureCapacity", size));
        if (stadium.getExpansionCostsInSwedishKrona() != null) {
            json.addProperty("expansionCosts",
                    money(new AmountOfMoney(stadium.getExpansionCostsInSwedishKrona())));
        }

        var sum = 0;
        final var attendances = new JsonArray();
        // Scan wider than we need: only about half of the played matches are at home.
        for (final var info : DBManager.instance().getOwnPlayedMatchInfo(homeMatches * 4, true)) {
            if (attendances.size() >= homeMatches) {
                break;
            }
            if (info.getHomeTeamID() != teamId) {
                continue;
            }
            final var details = DBManager.instance()
                    .loadMatchDetails(info.getMatchType().getId(), info.getMatchID());
            if (details == null || details.getZuschauer() <= 0) {
                continue;
            }
            final var entry = new JsonObject();
            entry.addProperty("matchId", info.getMatchID());
            entry.addProperty("date",
                    info.getMatchSchedule() == null ? null : info.getMatchSchedule().toHT());
            entry.addProperty("attendance", details.getZuschauer());
            entry.addProperty("weatherId", details.getWetterId());
            MatchTools.addSoldByStand(entry, details);
            attendances.add(entry);
            sum += details.getZuschauer();
        }
        json.add("recentHomeAttendance", attendances);
        if (!attendances.isEmpty()) {
            final var average = (double) sum / attendances.size();
            json.addProperty("averageHomeAttendance", average);
            if (stadium.getTotalSize() > 0) {
                json.addProperty("utilisation", average / stadium.getTotalSize());
            }
        }
        return json;
    }

    /** Team downloads issued over MCP in this HO session. Manual downloads do not count. */
    static int downloadsThisSession;

    /**
     * Refuse before spending anything when the session's downloads are used up.
     * <p>
     * Not synchronized, and it does not need to be: the only caller is inside
     * {@link LineupTools#onEdt}, so the check and the increment both happen on the event dispatch
     * thread. The counter moves only after a download actually succeeded - a Hattrick outage must
     * not eat the session's whole allowance.
     */
    private static void checkDownloadCap() throws McpToolException {
        final var cap = UserParameter.instance().mcpDownloadsPerSession;
        if (downloadsThisSession >= cap) {
            throw new McpToolException("The team-download cap of " + cap + " downloads per HO "
                    + "session is used up. Raise it in HO's options under MCP server, or restart "
                    + "HO, before downloading again.");
        }
    }

    /**
     * Refuse when HO holds no team id.
     * <p>
     * {@code ConvertXml2Hrf.createHrf():79} opens a modal {@code TeamSelectionDialog} in exactly
     * that case when the CHPP account has more than one team, and unlike {@code Connector}'s
     * dialogs this one is not gated on {@code silentDownload}. The only way to keep it off this
     * path is not to reach it.
     */
    private static void requireTeamSelected() throws McpToolException {
        final var model = HOVerwaltung.instance().getModel();
        if (model == null || model.getBasics() == null || model.getBasics().getTeamId() <= 0) {
            throw new McpToolException(advice(OnlineWorker.Failure.NO_TEAM, null).message());
        }
    }

    /**
     * Refresh HO's local database from Hattrick - everything the Download button downloads.
     * <p>
     * All of it runs inside one {@link LineupTools#onEdt} hop, because
     * {@link OnlineWorker#downloadTeamData()} touches HO's info line and the lineup panel and
     * because the model swap must not race the UI. HO's window is frozen for the duration - the
     * manual Download button already works that way.
     * <p>
     * The body runs with {@link Connector#setSilentDownload}, restored in a {@code finally}.
     * {@code Connector} opens {@code OAuthDialog} on an HTTP 401 and a {@code JOptionPane} on
     * every other CHPP failure, both gated on that flag alone, and a modal dialog on the EDT would
     * block the EDT and the MCP server's single handler thread together, with nothing on the
     * agent's side able to dismiss it. Since the parity sections were added the flag has to cover
     * {@code Helper.showMessage} too - see the guard there - because the match, fixture and lineup
     * downloads raise their errors through that rather than through {@code Connector}. It sits
     * here rather than inside the download routines because HO's Download button must keep those
     * dialogs.
     * <p>
     * <b>Parity, and the two sections that cannot have it.</b> The sections below are
     * {@code DownloadDialog.startDownload}'s, in its order, through the same extracted methods, and
     * driven by the same saved {@link UserParameter} filter flags - so what comes down is whatever
     * the user last ticked in the Download dialog. The match archive and previous-season fixtures
     * are the exceptions: both are driven by dialog widgets with no persisted form, so a fresh
     * dialog always starts them unticked and "whatever the user last ticked" is "neither". Old
     * fixtures could not run here anyway - {@code downloadOldFixtures} opens a modal
     * {@code LigaAuswahlDialog} per season.
     * <p>
     * The one deliberate deviation: team data is downloaded unconditionally, where the dialog
     * skips it when {@code xmlDownload} is unticked. A tool called {@code download_team} that
     * silently declined to download the team because of a checkbox in a dialog the agent cannot
     * see would be worse than useless.
     */
    private static JsonObject downloadTeam(JsonObject args) throws McpToolException {
        return LineupTools.onEdt(() -> {
            // Both before any CHPP traffic, so a refusal costs nothing.
            checkDownloadCap();
            requireTeamSelected();

            final var before = lineupSnapshot();
            // Counted around the WHOLE call, not around the incremental pass, because three
            // different things write TRANSFER during one download and only the last is ours:
            // ConvertXml2Hrf.createHrf reconciles the economy's transfer sums against the table and
            // calls PlayerTransfer.downloadMissingTransfers when they disagree (so a purchase made
            // since the last download is usually already stored by the time the incremental pass
            // runs, and it correctly finds nothing); updateTeamTransfers stores the pages the
            // incremental pass fetched; and updatePlayerTransfers then stores each affected
            // player's full history. Reporting only the middle one made this field read 0 on a
            // download that had in fact just fetched a new transfer.
            final var transfersBefore = DBManager.instance().getTransfers(0, true, true).size();
            final var wasSilent = Connector.instance().setSilentDownload(true);
            try {
                final OnlineWorker.TeamDownload download;
                try {
                    download = OnlineWorker.downloadTeamData();
                } catch (OnlineWorker.DownloadFailed e) {
                    throw new McpToolException(explain(e));
                }
                downloadsThisSession++;

                // Settled here rather than at the end of the call, and that is not just tidiness.
                // downloadTeamData holds the only writer of HO's stored lineup (HRFStringParser
                // -> HOModel.storeLineup); none of the parity sections below touches it -
                // getAllLineups stores *match* lineups in MATCHLINEUP, and the dialog never
                // downloads a next match order at all. Bracketing the whole widened call would
                // therefore compare the same two things across a much longer window, and would
                // start reporting an unrelated edit as a replacement the moment anything else on
                // the EDT touched the lineup mid-download.
                //
                // No becameCurrent guard any more. Both snapshots read the stored row rather than
                // the live model (see lineupSnapshot), and storeModel writes that row whether or
                // not the model swap happened - so the case that used to report null, "HO cannot
                // tell", is now answerable. Still null when there was nothing stored to compare
                // against, which is a fresh install rather than an unknown.
                Boolean lineupReplaced = null;
                if (before != null) {
                    final var after = lineupSnapshot();
                    lineupReplaced = after != null && !before.equals(after);
                }

                final var model = HOVerwaltung.instance().getModel();
                final var sections = new JsonObject();
                OnlineWorker.downloadNtMatchesOfCappedPlayers(model);
                sections.addProperty("nationalTeamMatches", true);

                // The dialog guards everything from here on behind teamId > 0. requireTeamSelected
                // already established it, but the model may have been swapped since.
                if (model.getBasics().getTeamId() > 0) {
                    OnlineWorker.downloadHallOfFameIfActive(model);
                    sections.addProperty("hallOfFame", true);

                    if (OnlineWorker.anyCurrentMatchTypeSelected()) {
                        sections.addProperty("matches", OnlineWorker.downloadCurrentMatches(model));
                    } else {
                        sections.addProperty("matches", false);
                    }

                    if (UserParameter.instance().fixtures) {
                        sections.addProperty("fixtures", OnlineWorker.downloadCurrentFixtures(model));
                    } else {
                        sections.addProperty("fixtures", false);
                    }

                    OnlineWorker.refreshWorldDetailsIfDue();
                    sections.addProperty("worldDetails", true);
                }

                // Without this the training tools read sub-skills computed against the previous
                // HRF. DownloadDialog does the same after every download - and on the live
                // model, which is the downloaded one only when the swap actually happened.
                model.calcSubskills();

                // No longer optional. The button runs it on every download, and it is what fills
                // in the sale price of a watched market player - the scout list's half of a
                // refresh. Bounded by its own 60s budget, and in the steady
                // state it costs nothing, because only an expired-and-unresolved entry is a
                // candidate.
                OnlineWorker.resolveTransferListings();
                sections.addProperty("transferPrices", true);

                // Not part of button parity: the dialog never refreshes the transfer history
                // wholesale, and the Transfers tab only backfills it once, on a database that
                // holds none at all (TransfersPanel.refresh guards on getTransfers(...).isEmpty()).
                // So a player bought since the last download could show up in the squad and
                // nowhere else.
                final int addedHere = XMLParser.updateTeamTransfersIncremental(
                        model.getBasics().getTeamId());
                sections.addProperty("teamTransfers", true);
                if (addedHere > 0) {
                    HOLogger.instance().info(TeamTools.class,
                            "download_team: incremental pass stored " + addedHere + " transfer(s)");
                }

                // The download only wrote the database. Without this every HO tab keeps showing
                // the old squad while the agent talks about the new one - worse than not
                // refreshing at all, because the user's screen now disagrees with the agent.
                RefreshManager.instance().doReInit();

                final var json = new JsonObject();
                json.addProperty("hrfId", download.model().getHrfId());
                json.addProperty("downloadedAt", HODateTime.now().toHT());
                // false means the download stored an HRF that is not newer than the one HO already
                // held, so nothing became live - do not report that as a refresh.
                json.addProperty("becameCurrent", download.becameCurrent());
                json.addProperty("players", download.model().getCurrentPlayers().size());
                json.addProperty("subskillsRecalculated", true);
                json.addProperty("newTransfers",
                        DBManager.instance().getTransfers(0, true, true).size() - transfersBefore);
                json.add("sections", sections);
                json.addProperty("notRefreshed", "match archive and previous-season fixtures - "
                        + "both are Download-dialog checkboxes that HO does not persist, so they "
                        + "start off, and old fixtures need a league picker the agent cannot "
                        + "answer. Ask the user to run HO's own Download for those.");
                json.addProperty("lineupReplaced", lineupReplaced);
                json.add("previousLineup",
                        Boolean.TRUE.equals(lineupReplaced) ? before : JsonNull.INSTANCE);
                return json;
            } finally {
                Connector.instance().setSilentDownload(wasSilent);
                // DownloadDialog ends this way. Here it sits in the finally rather than on
                // the success path: createHrf drives HO's progress bar as it goes, so a failure
                // part-way through would otherwise leave the info panel stuck mid-download for
                // the rest of the session.
                HOMainFrame.instance().setInformationCompleted();
            }
        });
    }

    /**
     * The message an agent gets for a failed download.
     * <p>
     * With {@code silentDownload} on, {@code Connector} swallowed the dialog it would otherwise
     * have shown and handed back an empty string; {@code createHrf} then returned null exactly the
     * way it does when no team is selected, so {@code downloadTeamData} reported
     * {@link OnlineWorker.Failure#NO_TEAM}. {@link Connector#lastError()} is what tells the two
     * apart - it is cleared at the start of every CHPP request and left set only by a failed one,
     * so a non-null value here means Hattrick, not the team selection, is the reason. Without this
     * an expired OAuth token - the likeliest real failure - would be reported to the agent as "no
     * team is selected in HO", which the user cannot act on.
     */
    private static String explain(OnlineWorker.DownloadFailed failure) {
        final var chppError = Connector.instance().lastError();
        final var advice = failure.reason == OnlineWorker.Failure.NO_TEAM && chppError != null
                ? advice(OnlineWorker.Failure.CHPP_ERROR, chppError)
                : advice(failure.reason, failure.getMessage());
        return advice.message() + (advice.retryable()
                ? " Nothing was stored; the same call may work in a few minutes."
                : " Nothing was stored.");
    }

    /**
     * The <b>stored</b> lineup's positions, or null when HO holds no team or has nothing stored -
     * on a fresh install the first download has nothing to compare against.
     * <p>
     * Read straight from the database rather than from {@code HOVerwaltung}'s live model, and that
     * is the whole point. The live model gets its lineup from one of two places: after a download
     * it is the object {@code HRFStringParser} just built, but after a restart it is
     * {@code HOModel(int)} calling {@code loadNextMatchLineup} - a database round trip. Comparing a
     * round-tripped object against a freshly parsed one compares two construction paths, not two
     * lineups, so any field the round trip does not preserve exactly shows up as a difference. That
     * made the first download after every HO restart report {@code lineupReplaced: true} whether or
     * not anything had changed, which is worth no more than a flag that never fires.
     * <p>
     * Both snapshots now come through {@code loadNextMatchLineup}, so representation cancels and a
     * difference means the stored row really did change. It also removes the old {@code null}
     * answer: the comparison no longer depends on the model swap having happened, because
     * {@code storeModel} writes the lineup either way.
     * <p>
     * No rating model, so no {@code ratings} block: this exists to be compared, and two full
     * rating passes on the EDT to produce a field that would then have to be ignored is a poor
     * trade. The positions are also what an agent needs to rebuild a lineup a download replaced.
     */
    private static JsonObject lineupSnapshot() {
        final var model = HOVerwaltung.instance().getModel();
        if (model == null || model.getBasics() == null) {
            return null;
        }
        final var stored = DBManager.instance().loadNextMatchLineup(model.getBasics().getTeamId());
        if (stored == null || stored.getLineup() == null) {
            return null;
        }
        return LineupTools.describe(stored.getLineup(), null);
    }

    /** What the agent is told about a failed download, and whether trying again could help. */
    record DownloadAdvice(String message, boolean retryable) {
    }

    /**
     * The agent-facing rendering of an {@link OnlineWorker.Failure}. Unlike HO's Download button,
     * all five reasons are errors here, because in all five nothing was stored.
     * <p>
     * A switch expression with no {@code default} on purpose: a constant added to
     * {@link OnlineWorker.Failure} without a case here must fail the build, not return a blank
     * error to an agent.
     *
     * @param detail the exception's own message, used where Hattrick's wording is the only thing
     *               that says what actually went wrong
     */
    static DownloadAdvice advice(OnlineWorker.Failure reason, String detail) {
        return switch (reason) {
            case PLAYING_MATCH -> new DownloadAdvice(
                    "Hattrick is playing your match right now and will not export team data. "
                            + "Try again after it finishes.", true);
            case NO_DATA -> new DownloadAdvice(
                    "Hattrick returned no team data.", true);
            case CHPP_ERROR -> new DownloadAdvice(
                    "Hattrick refused the download: " + detail, false);
            case UNPARSEABLE -> new DownloadAdvice(
                    "HO could not read what Hattrick sent back. Retrying will not help - this is "
                            + "worth reporting to the user, who can check HO's log.", false);
            case NO_TEAM -> new DownloadAdvice(
                    "No team is selected in HO, so there is nothing to download. Only the user can "
                            + "fix this, in HO.", false);
        };
    }
}
