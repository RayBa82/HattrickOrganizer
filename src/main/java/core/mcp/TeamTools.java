package core.mcp;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import core.db.DBManager;
import core.model.misc.Economy;
import core.util.AmountOfMoney;
import tool.arenasizer.Stadium;

import java.util.List;

/** The read only view of the team HO already knows about. */
public final class TeamTools {

    private static final int DEFAULT_HOME_MATCHES = 5;

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
                                + "specialty, injuries and cards, plus a positionRatings map giving "
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
