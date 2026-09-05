package core.mcp;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import core.constants.player.PlayerSkill;
import core.db.DBManager;
import core.model.misc.Economy;
import core.model.player.IMatchRoleID;
import core.model.player.MatchRoleID;
import core.model.player.Specialty;
import core.util.AmountOfMoney;
import module.opponentspy.OppPlayerSkillEstimator;
import module.teamanalyzer.ht.HattrickManager;
import module.teamanalyzer.manager.PlayerDataManager;
import module.teamanalyzer.vo.PlayerInfo;
import tool.arenasizer.Stadium;

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
                        "The user's team: name, league, finances, stadium, team spirit and "
                                + "confidence, the head coach and the rest of the staff, as of the "
                                + "latest download.",
                        McpTool.schema(new JsonObject()),
                        args -> team()),
                new McpTool("get_squad",
                        "Every player in the squad with skills, form, stamina, loyalty, TSI, wage "
                                + "(in the user's currency, as get_team reports it), "
                                + "specialty, injuries, cards and squad group, plus a positionRatings map giving "
                                + "what the player is worth in each of the fourteen field slots "
                                + "(100 keeper through 113 left forward) at normal behaviour.",
                        McpTool.schema(new JsonObject()),
                        args -> squad()),
                new McpTool("get_player",
                        "One player in full, together with his transfer history as HO recorded it.",
                        McpTool.schema(
                                McpTool.properties("playerId", McpTool.prop("integer", "the Hattrick player id")),
                                "playerId"),
                        args -> player(McpJson.reqInt(args, "playerId"))),
                new McpTool("get_opponent_squad",
                        "Another team's squad, fetched from Hattrick: every player with age, TSI, "
                                + "form, stamina, experience, loyalty, specialty, wage, injuries "
                                + "and cards, plus where and how long he last played. Two things "
                                + "it gives that nothing else does: who is unavailable for their "
                                + "next match (injured or suspended), and their last eleven - the "
                                + "players sharing the highest lastMatch.matchId started that "
                                + "match, and lastMatch.roleId says where (100 keeper through 113 "
                                + "left forward). Hattrick does not publish another team's skills, "
                                + "so unlike get_squad there are none; estimatedSkills is HO's own "
                                + "opponent-spy guess inferred from the visible attributes, not "
                                + "data. One CHPP request. Pair it with get_opponent_ratings, "
                                + "which gives the sector ratings predict_match needs.",
                        McpTool.schema(McpTool.properties(
                                        "teamId", McpTool.prop("integer",
                                                "the Hattrick team id, e.g. an opponent id from "
                                                        + "get_upcoming_matches")),
                                "teamId"),
                        TeamTools::opponentSquad),
                new McpTool("get_upcoming_matches",
                        "The fixture list of the current series.",
                        McpTool.schema(new JsonObject()),
                        args -> fixtures()),
                new McpTool("get_league_table",
                        "The standings of our series: all eight teams with position, record, "
                                + "points and goals. get_team only reports our own row, and "
                                + "promotion, relegation and the season expectations all hang off "
                                + "the whole table.",
                        McpTool.schema(new JsonObject()),
                        args -> leagueTable()),
                new McpTool("get_finances",
                        "The full weekly financial statement with last week's copy beside it, the "
                                + "fan club and the staff levels that drive the cost line, and the "
                                + "stadium: its stands, what is under construction, and what "
                                + "actually turned up at the last home matches. Capacity on its "
                                + "own cannot answer an expansion question - the utilisation ratio "
                                + "can. Every amount is in the currency field's currency, the one "
                                + "the user sees in Hattrick.",
                        McpTool.schema(McpTool.properties(
                                "homeMatches", McpTool.prop("integer",
                                        "how many recent home matches to read the attendance of, "
                                                + "default " + DEFAULT_HOME_MATCHES))),
                        TeamTools::finances)
        );
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
            economyJson.addProperty("playersSalary", money(economy.getCostsPlayers()));
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
                staffJson.addProperty("type", String.valueOf(member.getStaffType()));
                staffJson.addProperty("level", member.getLevel());
                staffJson.addProperty("cost", member.getCost());
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

    private static JsonObject squad() throws McpToolException {
        final var model = McpJson.model();
        final var ratingModel = McpJson.privateRatingModel();

        final var players = new JsonArray();
        for (final var player : model.getCurrentPlayers()) {
            players.add(McpJson.player(player, ratingModel));
        }
        final var json = new JsonObject();
        json.add("players", players);
        return json;
    }

    private static JsonObject player(int playerId) throws McpToolException {
        final var model = McpJson.model();
        final var player = model.getCurrentPlayer(playerId);
        if (player == null) {
            throw new McpToolException("No player with id " + playerId + " is in the squad. "
                    + "Call get_squad for the ids HO knows about.");
        }
        final var json = McpJson.player(player, McpJson.privateRatingModel());

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
        json.add("transfers", transfers);
        return json;
    }

    /**
     * get_opponent_squad: the players file for a team that is not ours.
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
                    + ". Check the team id - get_upcoming_matches lists the ones in our series.");
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

    private static JsonObject fixtures() throws McpToolException {
        final var model = McpJson.model();
        final var matches = new JsonArray();
        final var fixtures = model.getFixtures();
        if (fixtures != null) {
            for (final var pairing : fixtures.getMatches()) {
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
        final var json = new JsonObject();
        json.add("matches", matches);
        return json;
    }

    private static JsonObject leagueTable() throws McpToolException {
        final var model = McpJson.model();
        final var xtra = model.getXtraDaten();
        if (xtra == null) {
            throw new McpToolException("HO holds no series data yet. Download the team first.");
        }
        // The league LEVEL UNIT id, not Basics.getLiga() - that one is the league, not the series.
        final var fixtures = DBManager.instance()
                .getSpielplan(xtra.getLeagueLevelUnitID(), model.getBasics().getSeason());
        if (fixtures == null || fixtures.getTable() == null) {
            throw new McpToolException("HO has not downloaded this season's series yet.");
        }

        final var json = new JsonObject();
        json.addProperty("seriesName", fixtures.getLigaName());
        json.addProperty("leagueLevelUnitId", fixtures.getLigaId());
        json.addProperty("season", fixtures.getSaison());

        final var rows = new JsonArray();
        for (final var entry : fixtures.getTable().getEntries()) {
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
        json.add("table", rows);
        return json;
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
}
