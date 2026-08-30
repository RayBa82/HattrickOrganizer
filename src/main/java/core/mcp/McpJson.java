package core.mcp;

import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import core.constants.player.PlayerSkill;
import core.file.xml.XMLTransferSearchParser.Hit;
import core.model.HOModel;
import core.model.HOVerwaltung;
import core.model.Team;
import core.model.player.IMatchRoleID;
import core.model.player.Player;
import core.rating.RatingPredictionModel;
import core.util.HOLogger;
import module.lineup.Lineup;

/**
 * Serialization shared by the tool groups, plus the two lookups every tool starts with.
 */
public final class McpJson {

    /** The fourteen field slots Hattrick numbers, keeper (100) through left forward (113). */
    public static final int[] FIELD_ROLES = {
            IMatchRoleID.keeper, 101, 102, 103, 104, 105, 106, 107, 108, 109, 110, 111, 112,
            IMatchRoleID.leftForward
    };

    private McpJson() {
    }

    /** The loaded team snapshot. */
    public static HOModel model() throws McpToolException {
        final var model = HOVerwaltung.instance().getModel();
        if (model == null || model.getBasics() == null) {
            throw new McpToolException("No team data is loaded in HO yet. Download the team first.");
        }
        return model;
    }

    /**
     * A rating model private to one tool call.
     * <p>
     * Never use {@link HOModel#getRatingPredictionModel()} for a scratch lineup: its
     * {@code LineupRatingCache} is keyed on {@link Lineup#getRatingRevision()} alone and holds one
     * revision at a time, so a scratch lineup at revision 0 and the live lineup at revision 0 are
     * the same cache entry - the scratch score would come back as the live one and would overwrite
     * it. A fresh instance per call sidesteps that and costs nothing measurable.
     * <p>
     * Built from the class the manager currently holds, so that a user running the Groovy
     * {@code AkasolaceRatingPredictionModel} gets the same numbers here as in the Lineup tab.
     */
    public static RatingPredictionModel privateRatingModel() throws McpToolException {
        final var team = model().getTeam();
        final var shared = model().getRatingPredictionModel();
        try {
            return shared.getClass().getConstructor(Team.class).newInstance(team);
        } catch (ReflectiveOperationException e) {
            HOLogger.instance().log(McpJson.class, e);
            return new RatingPredictionModel(team);
        }
    }

    public static JsonObject player(Player p) {
        final var json = new JsonObject();
        json.addProperty("playerId", p.getPlayerId());
        json.addProperty("name", p.getFullName());
        json.addProperty("ageYears", p.getAge());
        json.addProperty("ageDays", p.getAgeDays());
        json.addProperty("tsi", p.getTsi());
        json.addProperty("wage", p.getWage() == null ? 0L : p.getWage().getSwedishKrona().longValue());
        json.addProperty("form", p.getForm());
        json.addProperty("stamina", p.getStamina());
        json.addProperty("loyalty", p.getLoyalty());
        json.addProperty("motherclub", p.isHomeGrown());
        json.addProperty("specialty", p.getSpecialty());
        json.addProperty("specialtyName", p.getSpecialtyName());
        json.addProperty("injuryWeeks", p.getInjuryWeeks());
        json.addProperty("cards", p.getTotalCards());
        json.addProperty("transferListed", p.getTransferListed() > 0);

        final var skills = new JsonObject();
        skills.addProperty("keeper", p.getValue4Skill(PlayerSkill.KEEPER));
        skills.addProperty("defending", p.getValue4Skill(PlayerSkill.DEFENDING));
        skills.addProperty("playmaking", p.getValue4Skill(PlayerSkill.PLAYMAKING));
        skills.addProperty("winger", p.getValue4Skill(PlayerSkill.WINGER));
        skills.addProperty("passing", p.getValue4Skill(PlayerSkill.PASSING));
        skills.addProperty("scoring", p.getValue4Skill(PlayerSkill.SCORING));
        skills.addProperty("setPieces", p.getValue4Skill(PlayerSkill.SETPIECES));
        skills.addProperty("experience", p.getValue4Skill(PlayerSkill.EXPERIENCE));
        json.add("skills", skills);
        return json;
    }

    /**
     * The player plus what he is worth in each of the fourteen field slots at normal behaviour -
     * the single most useful thing a model can be handed in one call, since it otherwise has to
     * guess role fit from raw skills.
     */
    public static JsonObject player(Player p, RatingPredictionModel ratingModel) {
        final var json = player(p);
        final var ratings = new JsonObject();
        for (final var roleId : FIELD_ROLES) {
            ratings.addProperty(String.valueOf(roleId),
                    ratingModel.getPlayerRatingMatchBeginning(p, roleId, IMatchRoleID.NORMAL));
        }
        json.add("positionRatings", ratings);
        return json;
    }

    /** One transfer market hit. Details are null once the deadline has passed. */
    public static JsonObject hit(Hit hit) {
        final var json = new JsonObject();
        json.addProperty("playerId", hit.playerId());
        json.addProperty("playerName", hit.playerName());
        json.addProperty("nativeCountryId", hit.nativeCountryId());
        json.addProperty("askingPrice", hit.askingPrice());
        json.addProperty("highestBid", hit.highestBid());
        json.addProperty("bidderTeamName", hit.bidderTeamName());
        json.addProperty("sellerTeamId", hit.sellerTeamId());
        json.addProperty("sellerTeamName", hit.sellerTeamName());
        json.addProperty("sellerLeagueId", hit.sellerLeagueId());
        json.addProperty("deadline", hit.deadline() == null ? null : hit.deadline().toHT());

        final var d = hit.details();
        if (d == null) {
            json.add("details", JsonNull.INSTANCE);
            return json;
        }
        final var details = new JsonObject();
        details.addProperty("age", d.age());
        details.addProperty("ageDays", d.ageDays());
        details.addProperty("salary", d.salary());
        details.addProperty("tsi", d.tsi());
        details.addProperty("form", d.form());
        details.addProperty("experience", d.experience());
        details.addProperty("leadership", d.leadership());
        details.addProperty("specialty", d.specialty());
        details.addProperty("cards", d.cards());
        details.addProperty("injuryLevel", d.injuryLevel());
        details.addProperty("stamina", d.stamina());
        details.addProperty("keeper", d.keeper());
        details.addProperty("playmaker", d.playmaker());
        details.addProperty("scorer", d.scorer());
        details.addProperty("passing", d.passing());
        details.addProperty("winger", d.winger());
        details.addProperty("defender", d.defender());
        details.addProperty("setPieces", d.setPieces());
        json.add("details", details);
        return json;
    }

    /** The seven sector ratings over 90 minutes, plus the derived formation. */
    public static JsonObject ratings(RatingPredictionModel ratingModel, Lineup lineup) {
        final var json = new JsonObject();
        for (final var sector : RatingPredictionModel.RatingSector.values()) {
            json.addProperty(sector.name().toLowerCase(java.util.Locale.ENGLISH),
                    ratingModel.getAverageRating(lineup, sector, 90));
        }
        json.addProperty("formation", lineup.getCurrentTeamFormationString());
        return json;
    }

    // ---- argument readers -------------------------------------------------------------------

    public static int reqInt(JsonObject args, String name) {
        if (!args.has(name) || args.get(name).isJsonNull()) {
            throw new IllegalArgumentException("Missing required argument: " + name);
        }
        return args.get(name).getAsInt();
    }

    public static int optInt(JsonObject args, String name, int defaultValue) {
        return args.has(name) && !args.get(name).isJsonNull() ? args.get(name).getAsInt() : defaultValue;
    }

    public static Integer optInteger(JsonObject args, String name) {
        return args.has(name) && !args.get(name).isJsonNull() ? args.get(name).getAsInt() : null;
    }

    public static String optString(JsonObject args, String name, String defaultValue) {
        return args.has(name) && !args.get(name).isJsonNull() ? args.get(name).getAsString() : defaultValue;
    }

    public static JsonArray reqArray(JsonObject args, String name) {
        if (!args.has(name) || !args.get(name).isJsonArray()) {
            throw new IllegalArgumentException("Missing required array argument: " + name);
        }
        return args.getAsJsonArray(name);
    }
}
