package core.mcp;

import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import core.constants.player.PlayerSkill;
import core.file.xml.XMLTransferSearchParser.Hit;
import core.model.HOModel;
import core.model.HOVerwaltung;
import core.model.Team;
import core.model.match.IMatchDetails;
import core.model.player.IMatchRoleID;
import core.model.player.Player;
import core.rating.RatingPredictionModel;
import core.util.AmountOfMoney;
import core.util.HODateTime;
import core.util.HOLogger;
import module.lineup.Lineup;

import java.math.BigDecimal;
import java.math.RoundingMode;

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

    /**
     * An amount in the currency the user sees, which is the one both HO and Hattrick display.
     * <p>
     * Every amount HO holds is stored in swedish krona - the ones read straight out of a CHPP file
     * included - and the local currency is that divided by the league's exchange rate. Handing a
     * model the raw krona overstates every wage, cost and price by that rate (a factor of ten for
     * a euro league), so the conversion happens once, here.
     */
    public static long money(AmountOfMoney amount) {
        return amount == null ? 0L : amount.toLocale().setScale(0, RoundingMode.HALF_UP).longValue();
    }

    /** The same, for the raw swedish krona a CHPP record carries as a plain number. */
    public static long money(long swedishKrona) {
        return money(new AmountOfMoney(swedishKrona));
    }

    /** The currency every amount in a tool result is denominated in, so the model can quote it. */
    public static String currency() {
        return AmountOfMoney.Companion.getCurrencyName();
    }

    /** The reverse of {@link #money}: an amount a model passed in, as the krona CHPP expects. */
    public static Integer swedishKrona(Integer localAmount) {
        return localAmount == null ? null
                : BigDecimal.valueOf(localAmount)
                        .multiply(AmountOfMoney.Companion.getExchangeRate()).intValue();
    }

    /**
     * {@link #swedishKrona(Integer)} for a long amount. Transfer bids need this: in a weak-currency
     * league the local number can be larger than the krona value and exceed the int range.
     */
    public static long swedishKrona(long localAmount) {
        return BigDecimal.valueOf(localAmount)
                .multiply(AmountOfMoney.Companion.getExchangeRate()).longValue();
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

    /** The eight skills HO reports, and the names the client sees them under. */
    private static final PlayerSkill[] SKILLS = {
            PlayerSkill.KEEPER, PlayerSkill.DEFENDING, PlayerSkill.PLAYMAKING, PlayerSkill.WINGER,
            PlayerSkill.PASSING, PlayerSkill.SCORING, PlayerSkill.SETPIECES, PlayerSkill.EXPERIENCE
    };

    private static final String[] SKILL_NAMES = {
            "keeper", "defending", "playmaking", "winger",
            "passing", "scoring", "setPieces", "experience"
    };

    /** Whether the player may be fielded: not injured and not suspended. */
    public static boolean isAvailable(Player p) {
        return p.getInjuryWeeks() <= 0 && !p.isRedCarded();
    }

    public static JsonObject player(Player p) {
        final var json = new JsonObject();
        json.addProperty("playerId", p.getPlayerId());
        json.addProperty("name", p.getFullName());
        json.addProperty("ageYears", p.getAge());
        json.addProperty("ageDays", p.getAgeDays());
        json.addProperty("tsi", p.getTsi());
        json.addProperty("wage", money(p.getWage()));
        json.addProperty("form", p.getForm());
        json.addProperty("stamina", p.getStamina());
        json.addProperty("loyalty", p.getLoyalty());
        json.addProperty("leadership", p.getLeadership());
        json.addProperty("motherclub", p.isHomeGrown());
        json.addProperty("specialty", p.getSpecialty());
        json.addProperty("specialtyName", p.getSpecialtyName());
        json.addProperty("injuryWeeks", p.getInjuryWeeks());
        json.addProperty("cards", p.getTotalCards());
        json.addProperty("suspended", p.isRedCarded());
        json.addProperty("available", isAvailable(p));
        json.addProperty("transferListed", p.getTransferListed() > 0);
        // The squad group the user filed him under ("A-Team" ... "F-Team", empty for none).
        json.addProperty("group", p.getTeamGroup());

        // Integer levels for reading, level+subskill for judging how close a skill is to popping.
        final var skills = new JsonObject();
        final var precise = new JsonObject();
        for (int i = 0; i < SKILLS.length; i++) {
            skills.addProperty(SKILL_NAMES[i], p.getValue4Skill(SKILLS[i]));
            precise.addProperty(SKILL_NAMES[i], p.getValue4Skill(SKILLS[i]) + p.getSub4Skill(SKILLS[i]));
        }
        json.add("skills", skills);
        json.add("skillsWithSublevel", precise);
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
        json.addProperty("askingPrice", money(hit.askingPrice()));
        json.addProperty("highestBid", money(hit.highestBid()));
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
        details.addProperty("salary", money(d.salary()));
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

    /**
     * One stored observation of a player on the transfer market - a comparable, as evidence rather
     * than as a number. {@code salePrice} is what he actually went for and is null until the
     * resolve pass has seen his transfer complete; until then asking price and highest bid say only
     * what was wanted and offered.
     */
    public static JsonObject listing(core.db.TransferListing l) {
        final var json = new JsonObject();
        json.addProperty("playerId", l.getPlayerId());
        json.addProperty("playerName", l.getPlayerName());
        json.addProperty("observedAt", l.getObservedAt() == null ? null : l.getObservedAt().toHT());
        json.addProperty("deadline", l.getDeadline() == null ? null : l.getDeadline().toHT());
        json.addProperty("askingPrice", money(l.getAskingPrice()));
        json.addProperty("highestBid", money(l.getHighestBid()));
        // Who was leading when this was observed. The seller is not kept - a listing snapshot
        // stores the bidder only, so seller-side signals are available on a live search_transfers
        // hit and nowhere else.
        json.addProperty("bidderName", l.getBidderName());
        json.addProperty("sold", l.getSalePrice() != null);
        json.addProperty("salePrice", l.getSalePrice() == null ? null : money(l.getSalePrice()));
        json.addProperty("age", l.getAge());
        json.addProperty("ageDays", l.getAgeDays());
        json.addProperty("salary", l.getSalary() == null ? null : money(l.getSalary()));
        json.addProperty("tsi", l.getTsi());
        json.addProperty("form", l.getForm());
        json.addProperty("experience", l.getExperience());
        json.addProperty("leadership", l.getLeadership());
        json.addProperty("specialty", l.getSpecialty());
        json.addProperty("cards", l.getCards());
        json.addProperty("injuryLevel", l.getInjuryLevel());
        json.addProperty("stamina", l.getStamina());
        // Under get_player's names, not the CHPP's own (playmaker / scorer / defender), so a
        // comparable and the player it is compared to can be read side by side.
        json.addProperty("keeper", l.getKeeper());
        json.addProperty("defending", l.getDefender());
        json.addProperty("playmaking", l.getPlaymaker());
        json.addProperty("passing", l.getPassing());
        json.addProperty("winger", l.getWinger());
        json.addProperty("scoring", l.getScorer());
        json.addProperty("setPieces", l.getSetPieces());
        return json;
    }

    /**
     * One entry of HO's local transfer scout list. Price and deadline are the snapshot taken when
     * the player was added, not the live market state.
     */
    public static JsonObject scoutEntry(module.transfer.scout.ScoutEintrag entry) {
        final var json = new JsonObject();
        json.addProperty("playerId", entry.getPlayerID());
        json.addProperty("playerName", entry.getName());
        json.addProperty("askingPrice", money(entry.getPrice()));
        json.addProperty("deadline", entry.getDeadline() == null ? null
                : HODateTime.fromDbTimestamp(entry.getDeadline()).toHT());
        json.addProperty("note", entry.getInfo());
        // Filled in once the watched transfer completed; null while he is still listed.
        json.addProperty("salePrice", entry.isSold() ? money(entry.getSalePrice()) : null);
        json.addProperty("soldAt", entry.getSoldAt() == null ? null
                : HODateTime.fromDbTimestamp(entry.getSoldAt()).toHT());
        json.addProperty("age", entry.getAlter());
        json.addProperty("ageDays", entry.getAgeDays());
        json.addProperty("salary", money(entry.getbaseWage()));
        json.addProperty("tsi", entry.getTSI());
        json.addProperty("form", entry.getForm());
        json.addProperty("experience", entry.getErfahrung());
        json.addProperty("leadership", entry.getLeadership());
        json.addProperty("specialty", entry.getSpeciality());
        json.addProperty("stamina", entry.getKondition());
        json.addProperty("keeper", entry.getTorwart());
        json.addProperty("playmaker", entry.getSpielaufbau());
        json.addProperty("scorer", entry.getTorschuss());
        json.addProperty("passing", entry.getPasspiel());
        json.addProperty("winger", entry.getFluegelspiel());
        json.addProperty("defender", entry.getVerteidigung());
        json.addProperty("setPieces", entry.getStandards());
        return json;
    }

    /** The seven sector ratings over 90 minutes, plus the derived formation. */
    public static JsonObject ratings(RatingPredictionModel ratingModel, Lineup lineup) {
        final var json = new JsonObject();
        for (final var sector : RatingPredictionModel.RatingSector.values()) {
            json.addProperty(sector.name().toLowerCase(java.util.Locale.ENGLISH),
                    ratingModel.getAverageRating(lineup, sector, 90));
        }
        json.addProperty("hatStats90", ratingModel.getAverage90HatStats(lineup));
        json.addProperty("formation", lineup.getCurrentTeamFormationString());
        json.add("assumptions", assumptions(lineup));
        return json;
    }

    /**
     * What the sector ratings above were computed against. Team spirit and confidence feed the
     * rating model through the {@link Team} it was built from, so they are an input to every
     * number in this object, not decoration - a client comparing two evaluations has to know they
     * were scored under the same ones.
     */
    private static JsonObject assumptions(Lineup lineup) {
        final var json = new JsonObject();
        final var model = HOVerwaltung.instance().getModel();
        final var team = model == null ? null : model.getTeam();
        if (team != null) {
            json.addProperty("teamSpirit", team.getTeamSpiritLevel());
            json.addProperty("subTeamSpirit", team.getSubTeamSpirit());
            json.addProperty("confidence", team.getConfidence());
            // Which download these were read at. Two evaluations are only comparable when this
            // matches - team spirit and confidence move between downloads.
            json.addProperty("hrfId", team.getHrfId());
        }
        if (model != null && model.getBasics() != null && model.getBasics().getDatum() != null) {
            json.addProperty("snapshotDate", model.getBasics().getDatum().toHT());
        }
        json.addProperty("location", location(lineup.getLocation()));
        json.addProperty("weather", String.valueOf(lineup.getWeather()));
        return json;
    }

    private static String location(short location) {
        return switch (location) {
            case IMatchDetails.LOCATION_AWAY -> "away";
            case IMatchDetails.LOCATION_HOME -> "home";
            case IMatchDetails.LOCATION_AWAYDERBY -> "awayDerby";
            case IMatchDetails.LOCATION_TOURNAMENT -> "tournament";
            default -> "unknown";
        };
    }

    // ---- argument readers -------------------------------------------------------------------

    public static int reqInt(JsonObject args, String name) {
        if (!args.has(name) || args.get(name).isJsonNull()) {
            throw new IllegalArgumentException("Missing required argument: " + name);
        }
        return args.get(name).getAsInt();
    }

    public static long reqLong(JsonObject args, String name) {
        if (!args.has(name) || args.get(name).isJsonNull()) {
            throw new IllegalArgumentException("Missing required argument: " + name);
        }
        return args.get(name).getAsLong();
    }

    public static Long optLong(JsonObject args, String name) {
        return args.has(name) && !args.get(name).isJsonNull() ? args.get(name).getAsLong() : null;
    }

    public static String reqString(JsonObject args, String name) {
        if (!args.has(name) || args.get(name).isJsonNull()) {
            throw new IllegalArgumentException("Missing required argument: " + name);
        }
        return args.get(name).getAsString();
    }

    public static double reqDouble(JsonObject args, String name) {
        if (!args.has(name) || args.get(name).isJsonNull()) {
            throw new IllegalArgumentException("Missing required argument: " + name);
        }
        return args.get(name).getAsDouble();
    }

    public static int optInt(JsonObject args, String name, int defaultValue) {
        return args.has(name) && !args.get(name).isJsonNull() ? args.get(name).getAsInt() : defaultValue;
    }

    public static boolean optBool(JsonObject args, String name, boolean defaultValue) {
        return args.has(name) && !args.get(name).isJsonNull() ? args.get(name).getAsBoolean() : defaultValue;
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
