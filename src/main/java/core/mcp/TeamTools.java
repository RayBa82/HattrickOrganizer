package core.mcp;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import core.db.DBManager;

import java.util.List;

/** The read only view of the team HO already knows about. */
public final class TeamTools {

    private TeamTools() {
    }

    public static List<McpTool> tools() {
        return List.of(
                new McpTool("get_team",
                        "The user's team: name, league, finances, stadium and staff, as of the "
                                + "latest download.",
                        McpTool.schema(new JsonObject()),
                        args -> team()),
                new McpTool("get_squad",
                        "Every player in the squad with skills, form, stamina, loyalty, TSI, wage, "
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
                        args -> fixtures())
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
            // Economy returns AmountOfMoney, whose raw value is swedish krona
            final var economyJson = new JsonObject();
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

    /** The raw amount behind an AmountOfMoney, which stores swedish krona. */
    private static long money(core.util.AmountOfMoney amount) {
        return amount == null ? 0L : amount.getSwedishKrona().longValue();
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
}
