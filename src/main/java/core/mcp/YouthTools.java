package core.mcp;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import core.constants.player.PlayerSkill;
import core.db.DBManager;
import core.file.xml.XMLCHPPPreParser;
import core.file.xml.XMLMatchesParser;
import core.model.HOModel;
import core.model.match.MatchKurzInfo;
import core.model.player.IMatchRoleID;
import core.model.player.Specialty;
import core.net.Connector;
import core.util.HODateTime;
import module.lineup.Lineup;
import module.lineup.substitution.model.MatchOrderType;
import module.youth.YouthLeague;
import module.youth.YouthPlayer;
import module.youth.YouthScout;
import module.youth.YouthSkillInfo;
import module.youth.YouthTeamDetails;
import module.youth.YouthTrainerComment;
import module.youth.YouthTraining;
import module.youth.YouthTrainingDevelopmentEntry;
import module.youth.YouthTrainingType;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static core.mcp.McpTool.ARRAY;
import static core.mcp.McpTool.BOOLEAN;
import static core.mcp.McpTool.INTEGER;
import static core.mcp.McpTool.STRING;

/**
 * The youth academy: squad with HO's skill estimates, one player in detail, the team with its
 * scouts and league, the training HO holds per youth match, the live match list, and the match
 * order. Youth training is not in CHPP: HO's estimates rest on the training the user files per
 * match, which set_youth_training writes.
 */
public final class YouthTools {

    private static final String PLAYER_ID = "playerId";
    private static final String PLAYERS = "players";
    private static final String MATCH_ID = "matchId";
    private static final String MATCH_TYPE = "matchType";
    private static final String DATE = "date";
    private static final String NAME = "name";
    private static final String LIMIT = "limit";
    private static final String PRIMARY = "primary";
    private static final String SECONDARY = "secondary";
    private static final String POSITIONS = "positions";
    private static final String BENCH = "bench";
    private static final String ROLE_ID = "roleId";
    private static final String BEHAVIOUR = "behaviour";
    private static final String TACTIC = "tactic";
    private static final String ATTITUDE = "attitude";
    private static final String CAPTAIN_ID = "captainId";
    private static final String SET_PIECES_TAKER_ID = "setPiecesTakerId";
    private static final String ALLOW_UNAVAILABLE = "allowUnavailable";
    private static final String STATUS = "status";
    private static final String LAST_MATCH = "lastMatch";
    private static final String COUNT = "count";
    private static final String LEVEL = "level";
    private static final String DEVELOPMENT = "development";
    private static final String TRAINER_REPORTS = "trainerReports";
    private static final String SCOUT_COMMENTS = "scoutComments";
    private static final String FIXTURES = "fixtures";
    private static final String ALL_FIXTURES = "allFixtures";
    private static final int DEFAULT_TRAININGS = 10;
    private static final int MAX_TRAININGS = 50;
    private static final int NEXT_FIXTURES = 3;

    /** The JSON key of each youth skill, in YouthPlayer.skillIds order. */
    private static final Map<PlayerSkill, String> SKILL_KEYS = Map.of(
            PlayerSkill.KEEPER, "keeper",
            PlayerSkill.DEFENDING, "defending",
            PlayerSkill.PLAYMAKING, "playmaking",
            PlayerSkill.WINGER, "winger",
            PlayerSkill.PASSING, "passing",
            PlayerSkill.SCORING, "scoring",
            PlayerSkill.SETPIECES, "setPieces");

    private YouthTools() {
    }

    public static List<McpTool> tools() {
        return List.of(
                new McpTool("get_youth_squad",
                        "The youth academy players with HO's skill estimates: per skill est (HO's "
                                + "estimate), level (known current level), max (known potential), "
                                + "maxReached, top3; skills HO knows nothing of are left out. Plus "
                                + "age, promotable in (days), specialty, available false if out, "
                                + "and the last youth match. get_youth_player has comments and "
                                + "the per-match development.",
                        McpTool.schema(new JsonObject()),
                        args -> youthSquad())
                        .withBudget("Ask get_youth_player for one playerId instead.",
                                McpTool.OptionalSection.perItem(PLAYERS, LAST_MATCH, LAST_MATCH)),
                new McpTool("get_youth_player",
                        "One youth player in detail: skills with start values, the scout's "
                                + "comments, the coach's training reports (Gold supporters), and "
                                + "the development per youth match (newest first, limit default "
                                + DEFAULT_TRAININGS + ").",
                        McpTool.schema(McpTool.properties(
                                PLAYER_ID, McpTool.prop(INTEGER, "youth player id from get_youth_squad"),
                                LIMIT, McpTool.prop(INTEGER, "development rows, newest first; default "
                                        + DEFAULT_TRAININGS + ", max " + MAX_TRAININGS)),
                                PLAYER_ID),
                        YouthTools::youthPlayer)
                        .withBudget("Lower limit.", McpTool.OptionalSection.top(DEVELOPMENT),
                                McpTool.OptionalSection.top(TRAINER_REPORTS)),
                new McpTool("get_youth_team",
                        "The youth academy as of the last download: team, trainer, next training "
                                + "match date, scouts, and the youth league table with the next "
                                + "fixtures (allFixtures for the whole season).",
                        McpTool.schema(McpTool.properties(
                                ALL_FIXTURES, McpTool.prop(BOOLEAN, "every fixture of the season "
                                        + "instead of the next " + NEXT_FIXTURES))),
                        YouthTools::youthTeam)
                        .withBudget("Leave allFixtures off.", McpTool.OptionalSection.top(FIXTURES)),
                new McpTool("get_youth_training",
                        "The training HO holds per youth match, newest first: primary and "
                                + "secondary type, or null where none is filed. Hattrick does not "
                                + "hand youth training to CHPP, so HO's skill estimates rest on "
                                + "these; set_youth_training files them.",
                        McpTool.schema(McpTool.properties(
                                LIMIT, McpTool.prop(INTEGER, "matches, newest first; default "
                                        + DEFAULT_TRAININGS + ", max " + MAX_TRAININGS))),
                        YouthTools::youthTraining),
                new McpTool("set_youth_training",
                        "File the training of a played youth match in HO (local only, nothing "
                                + "goes to Hattrick) and recalculate the skill estimates of the "
                                + "players who played it. Types: " + trainingTypeNames() + ".",
                        McpTool.schema(McpTool.properties(
                                MATCH_ID, McpTool.prop(INTEGER, "youth match id from get_youth_training"),
                                PRIMARY, McpTool.prop(STRING, "primary training type"),
                                SECONDARY, McpTool.prop(STRING, "secondary training type")),
                                MATCH_ID, PRIMARY, SECONDARY),
                        YouthTools::setYouthTraining),
                new McpTool("get_youth_matches",
                        "The youth team's recent and upcoming matches, read live from Hattrick "
                                + "(friendlies included): matchId and matchType for "
                                + "send_youth_match_order, date, teams, status, result, "
                                + "ordersGiven.",
                        McpTool.schema(new JsonObject()),
                        args -> youthMatches())
                        .asTask(args -> true),
                new McpTool("send_youth_match_order",
                        "Send a youth match order to Hattrick. It OVERWRITES the order Hattrick "
                                + "holds. Same shape as apply_lineup (positions, bench, tactic, "
                                + "attitude, captainId, setPiecesTakerId, penaltyTakers, "
                                + "teamOrders) with youth player ids; no style of play. Returns "
                                + "the order as sent, re-read from Hattrick: verified false lists "
                                + "the differences.",
                        youthOrderSchema(),
                        YouthTools::sendYouthMatchOrder)
                        .asTask(args -> true)
        );
    }

    private static JsonObject youthOrderSchema() {
        return McpTool.schema(McpTool.properties(
                        MATCH_ID, McpTool.prop(INTEGER, "youth match id from get_youth_matches"),
                        MATCH_TYPE, McpTool.prop(INTEGER, "100 youth league, 101/103/105/106 friendlies"),
                        POSITIONS, McpTool.prop(ARRAY, "{roleId, playerId, behaviour}: roleId 100 "
                                + "keeper through 113 left forward, at most 11; behaviour 0 normal, "
                                + "1 offensive, 2 defensive, 3 towards middle, 4 towards wing"),
                        BENCH, McpTool.prop(ARRAY, "{roleId, playerId}: 200 keeper, 201 central "
                                + "defender, 202 wing back, 203 inner midfielder, 204 forward, "
                                + "205 winger, 206 extra"),
                        TACTIC, McpTool.prop(INTEGER, "tactic type, 0 normal"),
                        ATTITUDE, McpTool.prop(INTEGER, "-1 play it cool, 0 normal, 1 match of the season"),
                        CAPTAIN_ID, McpTool.prop(INTEGER, "youth player id of the captain"),
                        SET_PIECES_TAKER_ID, McpTool.prop(INTEGER, "youth player id of the set pieces taker"),
                        LineupOrders.PENALTY_TAKERS, McpTool.prop(ARRAY, LineupOrders.PENALTY_TAKERS_DOC),
                        LineupOrders.TEAM_ORDERS, McpTool.prop(ARRAY, LineupOrders.TEAM_ORDERS_DOC),
                        ALLOW_UNAVAILABLE, McpTool.prop(BOOLEAN, "send although it fields an "
                                + "injured or suspended player; rejected by default")),
                MATCH_ID, MATCH_TYPE, POSITIONS);
    }

    // ===================== squad and player =====================

    private static HOModel youthModel() throws McpToolException {
        final var model = McpJson.model();
        if (!model.getBasics().hasYouthTeam()) {
            throw new McpToolException("This team has no youth academy.");
        }
        return model;
    }

    private static JsonObject youthSquad() throws McpToolException {
        final var model = youthModel();
        final var players = new JsonArray();
        model.getCurrentYouthPlayers().stream()
                .sorted(Comparator.comparingInt(YouthPlayer::getCanBePromotedIn))
                .forEach(p -> players.add(playerRow(p, false)));
        final var out = new JsonObject();
        out.addProperty(COUNT, players.size());
        out.add(PLAYERS, players);
        return out;
    }

    private static JsonObject youthPlayer(JsonObject args) throws McpToolException {
        final var model = youthModel();
        final var playerId = McpJson.reqInt(args, PLAYER_ID);
        final var limit = Math.max(0, Math.min(MAX_TRAININGS, McpJson.optInt(args, LIMIT, DEFAULT_TRAININGS)));
        final var player = model.getCurrentYouthPlayer(playerId);
        if (player == null) {
            throw new McpToolException("No youth player with id " + playerId + " is in the academy; "
                    + "get_youth_squad lists them.");
        }
        final var out = playerRow(player, true);
        final var potential = player.getPotential();
        out.addProperty("potential", potential);
        out.addProperty("minimumOverallSkillsLevel", round2(player.getMinimumOverallSkillsLevel()));
        addIfPresent(out, "statement", player.getStatement());
        addIfPresent(out, "nativeCountry", player.getNativeCountryName());
        addIfPresent(out, "scout", player.getScoutName());
        out.add(SCOUT_COMMENTS, scoutComments(player));
        out.add(TRAINER_REPORTS, trainerReports(player));
        out.add(DEVELOPMENT, development(player, limit));
        return out;
    }

    static JsonObject playerRow(YouthPlayer p, boolean full) {
        p.getTrainingDevelopment(); // fills the current estimates and specialties seen in matches
        final var json = new JsonObject();
        json.addProperty("id", p.getId());
        json.addProperty(NAME, p.getFullName());
        json.addProperty("age", p.getAgeYears());
        json.addProperty("ageDays", p.getAgeDays());
        json.addProperty("promotableInDays", Math.max(0, p.getCanBePromotedIn()));
        if (p.getSpecialty() != null && p.getSpecialty() != Specialty.NoSpecialty) {
            json.addProperty("specialty", p.getSpecialty().name());
        }
        // CHPP InjuryLevel: -1 none, 0 bruised (can play), else weeks out.
        if (p.getInjuryLevel() >= 0) {
            json.addProperty("injuryLevel", p.getInjuryLevel());
        }
        if (p.getCards() > 0) {
            json.addProperty("cards", p.getCards());
        }
        if (isUnavailable(p)) {
            json.addProperty("available", false);
        }
        final var overall = p.getOverallSkillsLevel();
        if (overall != null) {
            json.addProperty("overallSkillsLevel", overall);
        }
        json.add("skills", skills(p, full));
        final var lastMatch = lastMatch(p);
        if (lastMatch != null) {
            json.add(LAST_MATCH, lastMatch);
        }
        return json;
    }

    static boolean isUnavailable(YouthPlayer p) {
        return p.getInjuryLevel() > 0 || p.isSuspended();
    }

    private static JsonObject skills(YouthPlayer p, boolean full) {
        final var json = new JsonObject();
        for (final var skillId : YouthPlayer.skillIds) {
            final var info = p.getSkillInfo(skillId);
            // The squad leaves out what HO knows nothing about: no estimate, level or maximum.
            final var unknown = info == null || info.getCurrentValue() == 0 && info.getCurrentLevel() == null
                    && info.getMax() == null;
            if (info != null && (full || !unknown)) {
                json.add(SKILL_KEYS.get(skillId), skill(info, full));
            }
        }
        return json;
    }

    private static JsonObject skill(YouthSkillInfo info, boolean full) {
        final var json = new JsonObject();
        json.addProperty("est", round2(info.getCurrentValue()));
        if (info.getCurrentLevel() != null) {
            json.addProperty(LEVEL, info.getCurrentLevel());
        }
        if (info.getMax() != null) {
            json.addProperty("max", info.getMax());
        }
        if (info.isMaxReached()) {
            json.addProperty("maxReached", true);
        }
        if (Boolean.TRUE.equals(info.isTop3())) {
            json.addProperty("top3", true);
        }
        if (full) {
            json.addProperty("start", round2(info.getStartValue()));
            if (info.getStartLevel() != null) {
                json.addProperty("startLevel", info.getStartLevel());
            }
        }
        return json;
    }

    private static @Nullable JsonObject lastMatch(YouthPlayer p) {
        if (p.getYouthMatchID() == null || p.getYouthMatchID() <= 0) {
            return null;
        }
        final var json = new JsonObject();
        json.addProperty(MATCH_ID, p.getYouthMatchID());
        if (p.getYouthMatchDate() != null) {
            json.addProperty(DATE, p.getYouthMatchDate().toHT());
        }
        if (p.getPositionCode() != null) {
            json.addProperty(ROLE_ID, p.getPositionCode());
        }
        json.addProperty("minutes", p.getPlayedMinutes());
        if (p.getRating() != null) {
            json.addProperty("rating", p.getRating());
        }
        return json;
    }

    private static JsonArray scoutComments(YouthPlayer p) {
        final var out = new JsonArray();
        for (final var c : p.getScoutComments()) {
            final var json = new JsonObject();
            if (c.getType() != null) {
                json.addProperty("type", c.getType().name());
            }
            if (c.getSkillType() != null && c.getSkillType().toHTSkillId() != null) {
                json.addProperty("skill", SKILL_KEYS.getOrDefault(c.getSkillType().toHTSkillId(),
                        c.getSkillType().name()));
            }
            if (c.getSkillLevel() != null && c.getSkillLevel() > 0) {
                json.addProperty(LEVEL, c.getSkillLevel());
            }
            json.addProperty("text", c.getText());
            out.add(json);
        }
        return out;
    }

    private static JsonArray trainerReports(YouthPlayer p) {
        final var out = new JsonArray();
        p.getTrainerComments().stream()
                .sorted(Comparator.comparing(YouthTrainerComment::getReportDate,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .forEach(c -> {
                    final var json = new JsonObject();
                    if (c.getReportDate() != null) {
                        json.addProperty(DATE, c.getReportDate().toHT());
                    }
                    // ReportType codes are undocumented; passed raw.
                    json.addProperty("reportType", c.getReportType());
                    final var skill = c.getSkill();
                    if (skill != null) {
                        json.addProperty("skill", SKILL_KEYS.getOrDefault(skill, skill.name()));
                    }
                    if (c.getSkillLevel() > 0) {
                        json.addProperty(LEVEL, c.getSkillLevel());
                    }
                    json.addProperty("text", c.getText());
                    out.add(json);
                });
        return out;
    }

    private static JsonArray development(YouthPlayer p, int limit) {
        final var out = new JsonArray();
        for (final var entry : p.getTrainingDevelopment().descendingMap().values()) {
            if (out.size() >= limit) {
                break;
            }
            out.add(developmentRow(p, entry));
        }
        return out;
    }

    private static JsonObject developmentRow(YouthPlayer p, YouthTrainingDevelopmentEntry entry) {
        final var json = new JsonObject();
        json.addProperty(DATE, entry.getMatchDate().toHT());
        json.addProperty(MATCH_ID, entry.getMatchId());
        if (entry.getMatchType() != null) {
            json.addProperty(MATCH_TYPE, entry.getMatchType().getId());
        }
        json.addProperty("rating", entry.getRating());
        final var training = entry.getTraining();
        addTraining(json, training);
        final var youthTeamId = core.model.HOVerwaltung.instance().getModel().getBasics().getYouthTeamId();
        final var team = training.getTeam(youthTeamId);
        if (team != null) {
            final var sectors = new JsonObject();
            team.getTrainMinutesPlayedInSectors(p.getId())
                    .forEach((sector, minutes) -> sectors.addProperty(sector.name(), minutes));
            json.add("minutesInSectors", sectors);
        }
        final var skills = entry.getSkills();
        if (skills != null) {
            final var values = new JsonObject();
            for (final var skillId : YouthPlayer.skillIds) {
                final var info = skills.get(skillId);
                if (info != null) {
                    values.addProperty(SKILL_KEYS.get(skillId), round2(info.getCurrentValue()));
                }
            }
            json.add("skills", values);
        }
        return json;
    }

    // ===================== team and league =====================

    private static JsonObject youthTeam(JsonObject args) throws McpToolException {
        final var model = youthModel();
        final var details = DBManager.instance().loadYouthTeamDetails(model.getHrfId());
        if (details == null) {
            throw new McpToolException("HO holds no youth team details yet; download_team fetches them.");
        }
        final var out = teamJson(details);
        if (details.getYouthLeagueId() != null) {
            final var league = DBManager.instance().loadYouthLeague(details.getYouthLeagueId());
            if (league != null) {
                out.add("league", leagueJson(league));
                out.add(FIXTURES, fixtures(league, McpJson.optBool(args, ALL_FIXTURES, false)));
            }
        }
        return out;
    }

    private static JsonObject teamJson(YouthTeamDetails d) {
        final var json = new JsonObject();
        json.addProperty("youthTeamId", d.getYouthTeamId());
        json.addProperty(NAME, d.getYouthTeamName());
        if (d.getCreatedDate() != null) {
            json.addProperty("created", d.getCreatedDate().toHT());
        }
        addIfPresent(json, "arena", d.getYouthArenaName());
        if (d.getTrainerYouthPlayerId() != null) {
            json.addProperty("trainerYouthPlayerId", d.getTrainerYouthPlayerId());
        }
        if (d.getNextTrainingMatchDate() != null) {
            json.addProperty("nextTrainingMatchDate", d.getNextTrainingMatchDate().toHT());
        }
        if (d.getPlayingMatch() != null) {
            json.addProperty("playingMatch", d.getPlayingMatch());
        }
        final var scouts = new JsonArray();
        for (final var scout : d.getScouts()) {
            scouts.add(scoutJson(scout));
        }
        json.add("scouts", scouts);
        return json;
    }

    private static JsonObject scoutJson(YouthScout s) {
        final var json = new JsonObject();
        json.addProperty("id", s.getYouthScoutId());
        json.addProperty(NAME, s.getScoutName());
        addIfPresent(json, "inCountry", s.getInCountryName());
        addIfPresent(json, "inRegion", s.getInRegionName());
        if (s.getHiredDate() != null) {
            json.addProperty("hired", s.getHiredDate().toHT());
        }
        if (s.getLastCalled() != null) {
            json.addProperty("lastCalled", s.getLastCalled().toHT());
        }
        if (s.getPlayerTypeSearch() != null) {
            json.addProperty("playerTypeSearch", s.getPlayerTypeSearch());
        }
        if (s.getTravelStartDate() != null) {
            json.addProperty("travelStart", s.getTravelStartDate().toHT());
            json.addProperty("travelLength", s.getTravelLength());
        }
        return json;
    }

    private static JsonObject leagueJson(YouthLeague league) {
        final var json = new JsonObject();
        json.addProperty("id", league.getYouthLeagueId());
        json.addProperty(NAME, league.getYouthLeagueName());
        json.addProperty("season", league.getSeason());
        if (league.getLastMatchRound() != null) {
            json.addProperty("lastMatchRound", league.getLastMatchRound());
        }
        final var table = new JsonArray();
        for (final var t : league.getTeams()) {
            table.add(tableRow(t));
        }
        json.add("table", table);
        return json;
    }

    private static JsonObject tableRow(YouthLeague.Team t) {
        final var row = new JsonObject();
        row.addProperty("position", t.getPosition());
        row.addProperty("teamId", t.getTeamId());
        row.addProperty(NAME, t.getTeamName());
        row.addProperty("matches", t.getMatches());
        row.addProperty("won", t.getWon());
        row.addProperty("draws", t.getDraws());
        row.addProperty("lost", t.getLost());
        row.addProperty("goalsFor", t.getGoalsFor());
        row.addProperty("goalsAgainst", t.getGoalsAgainst());
        row.addProperty("points", t.getPoints());
        return row;
    }

    private static JsonArray fixtures(YouthLeague league, boolean all) {
        final var now = HODateTime.now();
        final var out = new JsonArray();
        for (final var f : league.getFixtures()) {
            final var upcoming = f.getMatchDate() == null || f.getMatchDate().isAfter(now);
            if (!all && (!upcoming || out.size() >= NEXT_FIXTURES)) {
                continue;
            }
            final var json = new JsonObject();
            json.addProperty(MATCH_ID, f.getMatchId());
            if (f.getMatchRound() != null) {
                json.addProperty("round", f.getMatchRound());
            }
            if (f.getMatchDate() != null) {
                json.addProperty(DATE, f.getMatchDate().toHT());
            }
            json.addProperty("home", f.getHomeTeamName());
            json.addProperty("away", f.getAwayTeamName());
            if (f.getHomeGoals() != null && f.getAwayGoals() != null) {
                json.addProperty("result", f.getHomeGoals() + "-" + f.getAwayGoals());
            }
            out.add(json);
        }
        return out;
    }

    // ===================== training =====================

    private static JsonObject youthTraining(JsonObject args) throws McpToolException {
        final var model = youthModel();
        final var limit = Math.max(1, Math.min(MAX_TRAININGS, McpJson.optInt(args, LIMIT, DEFAULT_TRAININGS)));
        final var trainings = model.getYouthTrainings().stream()
                .filter(t -> t.getMatchDate() != null)
                .sorted(Comparator.comparing(YouthTraining::getMatchDate).reversed())
                .toList();
        final var rows = new JsonArray();
        for (final var t : trainings.subList(0, Math.min(limit, trainings.size()))) {
            final var json = new JsonObject();
            json.addProperty(MATCH_ID, t.getYouthMatchId());
            json.addProperty(DATE, t.getMatchDate().toHT());
            if (t.getMatchType() != null) {
                json.addProperty(MATCH_TYPE, t.getMatchType().getId());
            }
            json.addProperty("home", t.getHomeTeamName());
            json.addProperty("away", t.getGuestTeamName());
            addTraining(json, t);
            rows.add(json);
        }
        final var out = new JsonObject();
        out.addProperty(COUNT, trainings.size());
        out.addProperty("returned", rows.size());
        out.add("trainings", rows);
        return out;
    }

    private static void addTraining(JsonObject json, YouthTraining t) {
        final var primary = t.getTraining(YouthTraining.Priority.Primary);
        final var secondary = t.getTraining(YouthTraining.Priority.Secondary);
        json.addProperty(PRIMARY, primary != null ? primary.name() : null);
        json.addProperty(SECONDARY, secondary != null ? secondary.name() : null);
    }

    private static JsonObject setYouthTraining(JsonObject args) throws McpToolException {
        final var model = youthModel();
        final var matchId = McpJson.reqInt(args, MATCH_ID);
        final var primary = trainingType(McpJson.reqString(args, PRIMARY), PRIMARY);
        final var secondary = trainingType(McpJson.reqString(args, SECONDARY), SECONDARY);
        final var training = model.getYouthTrainings().stream()
                .filter(t -> t.getYouthMatchId() == matchId)
                .findFirst()
                .orElseThrow(() -> new McpToolException("HO holds no youth match " + matchId
                        + "; get_youth_training lists the ones it has."));
        final var changed = training.getTraining(YouthTraining.Priority.Primary) != primary
                || training.getTraining(YouthTraining.Priority.Secondary) != secondary;
        if (changed) {
            training.setTraining(YouthTraining.Priority.Primary, primary);
            training.setTraining(YouthTraining.Priority.Secondary, secondary);
            training.recalcSkills();
            training.store();
        }
        final var out = new JsonObject();
        out.addProperty(MATCH_ID, matchId);
        out.addProperty("changed", changed);
        addTraining(out, training);
        return out;
    }

    private static YouthTrainingType trainingType(String name, String argument) {
        for (final var type : YouthTrainingType.values()) {
            if (type.name().equalsIgnoreCase(name)) {
                return type;
            }
        }
        throw new IllegalArgumentException(argument + " is one of " + trainingTypeNames()
                + ", not '" + name + "'.");
    }

    private static String trainingTypeNames() {
        final var names = new ArrayList<String>();
        for (final var type : YouthTrainingType.values()) {
            names.add(type.name());
        }
        return String.join(", ", names);
    }

    // ===================== matches and orders =====================

    private static JsonObject youthMatches() throws McpToolException {
        final var model = youthModel();
        final var xml = Connector.instance().getYouthMatches(model.getBasics().getYouthTeamId());
        if (xml == null || xml.isEmpty()) {
            throw new McpToolException("Hattrick did not return the youth matches"
                    + (Connector.instance().lastError() != null ? ": " + Connector.instance().lastError() : "."));
        }
        final var error = XMLCHPPPreParser.getError(xml);
        if (!error.isEmpty()) {
            throw new McpToolException("Hattrick refused the youth matches: " + error);
        }
        final var rows = new JsonArray();
        XMLMatchesParser.parseMatchesFromString(xml).stream()
                .sorted(Comparator.comparing(MatchKurzInfo::getMatchSchedule,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .forEach(m -> rows.add(matchRow(m)));
        final var out = new JsonObject();
        out.addProperty(COUNT, rows.size());
        out.add("matches", rows);
        return out;
    }

    private static JsonObject matchRow(MatchKurzInfo m) {
        final var json = new JsonObject();
        json.addProperty(MATCH_ID, m.getMatchID());
        if (m.getMatchType() != null) {
            json.addProperty(MATCH_TYPE, m.getMatchType().getId());
        }
        if (m.getMatchSchedule() != null) {
            json.addProperty(DATE, m.getMatchSchedule().toHT());
        }
        json.addProperty("home", m.getHomeTeamName());
        json.addProperty("away", m.getGuestTeamName());
        json.addProperty(STATUS, switch (m.getMatchStatus()) {
            case MatchKurzInfo.FINISHED -> "finished";
            case MatchKurzInfo.ONGOING -> "ongoing";
            default -> "upcoming";
        });
        if (m.getMatchStatus() == MatchKurzInfo.FINISHED) {
            json.addProperty("result", m.getHomeTeamGoals() + "-" + m.getGuestTeamGoals());
        } else {
            json.addProperty("ordersGiven", m.isOrdersGiven());
        }
        return json;
    }

    private static JsonObject sendYouthMatchOrder(JsonObject args) throws McpToolException {
        final var model = youthModel();
        final var matchId = McpJson.reqInt(args, MATCH_ID);
        final var matchType = LineupTools.matchType(McpJson.reqInt(args, MATCH_TYPE));
        if (!matchType.isYouth()) {
            throw new IllegalArgumentException("matchType " + matchType.getId() + " is not a youth "
                    + "match; send_match_order sends senior orders.");
        }
        final var lineup = youthLineup(args, model);
        final var out = new JsonObject();
        out.addProperty(MATCH_ID, matchId);
        return LineupTools.sendAndVerify(out, matchId, matchType, lineup.toJson(),
                LineupTools.orderOf(lineup));
    }

    /**
     * The order as a throwaway Lineup, which serialises to the CHPP order JSON by ids alone.
     * Every id must be a current academy player, each fielded once, and the fielded ones fit.
     */
    static Lineup youthLineup(JsonObject spec, HOModel model) throws McpToolException {
        final var allowUnavailable = McpJson.optBool(spec, ALLOW_UNAVAILABLE, false);
        final var lineup = new Lineup();
        final var fielded = placeField(lineup, spec, model, allowUnavailable);
        placeBench(lineup, spec, model, allowUnavailable, fielded);
        lineup.setTacticType(McpJson.optInt(spec, TACTIC, 0));
        lineup.setAttitude(McpJson.optInt(spec, ATTITUDE, 0));
        if (spec.has(CAPTAIN_ID)) {
            lineup.setCaptain(requireFielded(fielded, spec.get(CAPTAIN_ID).getAsInt(), CAPTAIN_ID));
        }
        if (spec.has(SET_PIECES_TAKER_ID)) {
            lineup.setKicker(requireFielded(fielded, spec.get(SET_PIECES_TAKER_ID).getAsInt(),
                    SET_PIECES_TAKER_ID));
        }
        placeOrders(lineup, spec, model, allowUnavailable);
        return lineup;
    }

    private static Set<Integer> placeField(Lineup lineup, JsonObject spec, HOModel model,
                                           boolean allowUnavailable) throws McpToolException {
        final var fieldPositions = McpJson.reqArray(spec, POSITIONS);
        if (fieldPositions.isEmpty() || fieldPositions.size() > 11) {
            throw new IllegalArgumentException("positions holds 1 to 11 players, not "
                    + fieldPositions.size() + ".");
        }
        final Set<Integer> fielded = new HashSet<>();
        for (final var element : fieldPositions) {
            final var slot = element.getAsJsonObject();
            final var roleId = requireRole(slot, IMatchRoleID.keeper, IMatchRoleID.leftForward, POSITIONS);
            final var playerId = requireYouthPlayer(model, McpJson.reqInt(slot, PLAYER_ID), POSITIONS,
                    allowUnavailable);
            if (!fielded.add(playerId)) {
                throw new IllegalArgumentException("positions names player " + playerId + " twice.");
            }
            final var behaviour = McpJson.optInt(slot, BEHAVIOUR, IMatchRoleID.NORMAL);
            if (behaviour < IMatchRoleID.NORMAL || behaviour > IMatchRoleID.TOWARDS_WING) {
                throw new IllegalArgumentException("positions behaviour is 0-4, not " + behaviour + ".");
            }
            lineup.setSpielerAtPosition(roleId, playerId, (byte) behaviour);
        }
        return fielded;
    }

    private static void placeBench(Lineup lineup, JsonObject spec, HOModel model,
                                   boolean allowUnavailable, Set<Integer> fielded) throws McpToolException {
        if (!spec.has(BENCH)) {
            return;
        }
        for (final var element : spec.getAsJsonArray(BENCH)) {
            final var slot = element.getAsJsonObject();
            final var roleId = requireRole(slot, IMatchRoleID.startReserves, IMatchRoleID.substXT2, BENCH);
            final var playerId = requireYouthPlayer(model, McpJson.reqInt(slot, PLAYER_ID), BENCH,
                    allowUnavailable);
            if (fielded.contains(playerId)) {
                throw new IllegalArgumentException("bench names player " + playerId
                        + ", who is already on the pitch.");
            }
            lineup.setSpielerAtPosition(roleId, playerId);
        }
    }

    private static void placeOrders(Lineup lineup, JsonObject spec, HOModel model,
                                    boolean allowUnavailable) throws McpToolException {
        final var takers = LineupOrders.parsePenaltyTakers(spec, null);
        if (takers != null) {
            for (final var taker : takers) {
                requireYouthPlayer(model, taker.getPlayerId(), LineupOrders.PENALTY_TAKERS, allowUnavailable);
            }
            lineup.setPenaltyTakers(takers);
        }
        // Youth teams have no tactical assistant.
        final var orders = LineupOrders.parseTeamOrders(spec, null, 0);
        if (orders != null) {
            for (final var order : orders) {
                requireYouthPlayer(model, order.getSubjectPlayerID(), LineupOrders.TEAM_ORDERS, true);
                if (order.getOrderType() != MatchOrderType.MAN_MARKING) {
                    requireYouthPlayer(model, order.getObjectPlayerID(), LineupOrders.TEAM_ORDERS,
                            allowUnavailable);
                }
            }
            lineup.setSubstitionList(orders);
        }
    }

    private static int requireRole(JsonObject slot, int from, int to, String where) {
        final var roleId = McpJson.reqInt(slot, ROLE_ID);
        if (roleId < from || roleId > to) {
            throw new IllegalArgumentException(where + " roleId is " + from + "-" + to + ", not "
                    + roleId + ".");
        }
        return roleId;
    }

    private static int requireYouthPlayer(HOModel model, int playerId, String where,
                                          boolean allowUnavailable) throws McpToolException {
        final var player = model.getCurrentYouthPlayer(playerId);
        if (player == null) {
            throw new McpToolException(where + ": no youth player with id " + playerId
                    + " is in the academy; get_youth_squad lists them.");
        }
        if (!allowUnavailable && isUnavailable(player)) {
            throw new McpToolException(where + ": " + player.getFullName() + " is "
                    + (player.isSuspended() ? "suspended" : "injured")
                    + "; pass allowUnavailable to send anyway.");
        }
        return playerId;
    }

    private static int requireFielded(Set<Integer> fielded, int playerId, String where) {
        if (!fielded.contains(playerId)) {
            throw new IllegalArgumentException(where + " " + playerId + " is not in positions.");
        }
        return playerId;
    }

    // ===================== helpers =====================

    private static void addIfPresent(JsonObject json, String key, @Nullable String value) {
        if (value != null && !value.isBlank()) {
            json.addProperty(key, value);
        }
    }

    private static double round2(double value) {
        return Math.round(value * 100) / 100.0;
    }

}
