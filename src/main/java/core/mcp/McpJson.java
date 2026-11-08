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

    private static final String AGE_DAYS = "ageDays";
    private static final String AGGRESSIVENESS = "aggressiveness";
    private static final String AGREEABILITY = "agreeability";
    private static final String CARDS = "cards";
    private static final String DEADLINE = "deadline";
    private static final String EXPERIENCE_KEY = "experience";
    private static final String HONESTY = "honesty";
    private static final String KEEPER_KEY = "keeper";
    private static final String LEADERSHIP = "leadership";
    private static final String PASSING_KEY = "passing";
    private static final String PLAYER_ID = "playerId";
    private static final String PLAYER_NAME = "playerName";
    private static final String SALARY = "salary";
    private static final String SET_PIECES = "setPieces";
    private static final String SPECIALTY = "specialty";
    private static final String STAMINA = "stamina";
    private static final String WINGER_KEY = "winger";

    /** The fourteen field slots Hattrick numbers, keeper (100) through left forward (113). */
    public static final java.util.List<Integer> FIELD_ROLES = java.util.List.of(
            IMatchRoleID.keeper, 101, 102, 103, 104, 105, 106, 107, 108, 109, 110, 111, 112,
            IMatchRoleID.leftForward);

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

    /**
     * The reverse of {@link #money}: an amount a model passed in, as the krona CHPP expects. A long,
     * because in a weak-currency league the local number can exceed the int range.
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
            KEEPER_KEY, "defending", "playmaking", WINGER_KEY,
            PASSING_KEY, "scoring", SET_PIECES, EXPERIENCE_KEY
    };

    /** Whether the player may be fielded: not injured and not suspended. */
    public static boolean isAvailable(Player p) {
        return p.getInjuryWeeks() <= 0 && !p.isRedCarded();
    }

    public static JsonObject player(Player p) {
        final var json = new JsonObject();
        json.addProperty(PLAYER_ID, p.getPlayerId());
        json.addProperty("name", p.getFullName());
        json.addProperty("ageYears", p.getAge());
        json.addProperty(AGE_DAYS, p.getAgeDays());
        json.addProperty("tsi", p.getTsi());
        json.addProperty("wage", money(p.getWage()));
        json.addProperty("form", p.getForm());
        json.addProperty(STAMINA, p.getStamina());
        json.addProperty("loyalty", p.getLoyalty());
        json.addProperty(LEADERSHIP, p.getLeadership());
        // Character, 0-5 each. HO's field names are the legacy German ones and do not line up with
        // Hattrick's: getGentleness() is Agreeability and getHonesty() is Honesty - see
        // HRFStringBuilder:492-497, which pairs each with its own label table. Do not "fix" these.
        json.addProperty(AGREEABILITY, p.getGentleness());
        json.addProperty(AGGRESSIVENESS, p.getAggressivity());
        json.addProperty(HONESTY, p.getHonesty());
        json.addProperty("motherclub", p.isHomeGrown());
        json.addProperty(SPECIALTY, p.getSpecialty());
        json.addProperty("specialtyName", p.getSpecialtyName());
        json.addProperty("injuryWeeks", p.getInjuryWeeks());
        json.addProperty(CARDS, p.getTotalCards());
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
            // Two decimals say how close a skill is to popping; the full double only adds length.
            precise.addProperty(SKILL_NAMES[i],
                    Math.round((p.getValue4Skill(SKILLS[i]) + p.getSub4Skill(SKILLS[i])) * 100) / 100.0);
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
        json.addProperty("penaltyStrength", Math.round(ratingModel.getPlayerPenaltyStrength(p) * 100) / 100.0);
        return json;
    }

    /** One transfer market hit. Details are null once the deadline has passed. */
    public static JsonObject hit(Hit hit) {
        final var json = new JsonObject();
        json.addProperty(PLAYER_ID, hit.playerId());
        json.addProperty(PLAYER_NAME, hit.playerName());
        json.addProperty("nativeCountryId", hit.nativeCountryId());
        json.addProperty("askingPrice", money(hit.askingPrice()));
        json.addProperty("highestBid", money(hit.highestBid()));
        json.addProperty("bidderTeamName", hit.bidderTeamName());
        json.addProperty("sellerTeamId", hit.sellerTeamId());
        json.addProperty("sellerTeamName", hit.sellerTeamName());
        json.addProperty("sellerLeagueId", hit.sellerLeagueId());
        json.addProperty(DEADLINE, hit.deadline() == null ? null : hit.deadline().toHT());

        final var d = hit.details();
        if (d == null) {
            json.add("details", JsonNull.INSTANCE);
            return json;
        }
        final var details = new JsonObject();
        details.addProperty("age", d.age());
        details.addProperty(AGE_DAYS, d.ageDays());
        details.addProperty(SALARY, money(d.salary()));
        details.addProperty("tsi", d.tsi());
        details.addProperty("form", d.form());
        details.addProperty(EXPERIENCE_KEY, d.experience());
        details.addProperty(LEADERSHIP, d.leadership());
        details.addProperty(SPECIALTY, d.specialty());
        details.addProperty(CARDS, d.cards());
        details.addProperty("injuryLevel", d.injuryLevel());
        details.addProperty(STAMINA, d.stamina());
        details.addProperty(KEEPER_KEY, d.keeper());
        details.addProperty("playmaker", d.playmaker());
        details.addProperty("scorer", d.scorer());
        details.addProperty(PASSING_KEY, d.passing());
        details.addProperty(WINGER_KEY, d.winger());
        details.addProperty("defender", d.defender());
        details.addProperty(SET_PIECES, d.setPieces());
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
        json.addProperty(PLAYER_ID, l.getPlayerId());
        json.addProperty(PLAYER_NAME, l.getPlayerName());
        json.addProperty("observedAt", l.getObservedAt() == null ? null : l.getObservedAt().toHT());
        json.addProperty(DEADLINE, l.getDeadline() == null ? null : l.getDeadline().toHT());
        json.addProperty("askingPrice", money(l.getAskingPrice()));
        json.addProperty("highestBid", money(l.getHighestBid()));
        // Who was leading when this was observed. The seller is not kept - a listing snapshot
        // stores the bidder only, so seller-side signals are available on a live search_transfers
        // hit and nowhere else.
        json.addProperty("bidderName", l.getBidderName());
        json.addProperty("sold", l.getSalePrice() != null);
        json.addProperty("salePrice", l.getSalePrice() == null ? null : money(l.getSalePrice()));
        json.addProperty("age", l.getAge());
        json.addProperty(AGE_DAYS, l.getAgeDays());
        json.addProperty(SALARY, l.getSalary() == null ? null : money(l.getSalary()));
        json.addProperty("tsi", l.getTsi());
        json.addProperty("form", l.getForm());
        json.addProperty(EXPERIENCE_KEY, l.getExperience());
        json.addProperty(LEADERSHIP, l.getLeadership());
        // Character, 0-5 each. Null on a row observed through transfersearch or currentbids, which
        // carry none - only a playerdetails observation of him fills these in.
        json.addProperty(AGREEABILITY, l.getAgreeability());
        json.addProperty(AGGRESSIVENESS, l.getAggressiveness());
        json.addProperty(HONESTY, l.getHonesty());
        json.addProperty(SPECIALTY, l.getSpecialty());
        json.addProperty(CARDS, l.getCards());
        json.addProperty("injuryLevel", l.getInjuryLevel());
        json.addProperty(STAMINA, l.getStamina());
        // Under get_squad's names, not the CHPP's own (playmaker / scorer / defender), so a
        // comparable and the player it is compared to can be read side by side.
        json.addProperty(KEEPER_KEY, l.getKeeper());
        json.addProperty("defending", l.getDefender());
        json.addProperty("playmaking", l.getPlaymaker());
        json.addProperty(PASSING_KEY, l.getPassing());
        json.addProperty(WINGER_KEY, l.getWinger());
        json.addProperty("scoring", l.getScorer());
        json.addProperty(SET_PIECES, l.getSetPieces());
        return json;
    }

    /**
     * One entry of HO's local transfer scout list. Price and deadline are the snapshot taken when
     * the player was added, not the live market state.
     * <p>
     * {@code price} is the scout table's own column, which holds the higher of the asking price and
     * the highest bid; the seller's asking price on its own lives in the TRANSFERLISTING snapshot.
     */
    public static JsonObject scoutEntry(module.transfer.scout.ScoutEintrag entry) {
        final var json = new JsonObject();
        json.addProperty(PLAYER_ID, entry.getPlayerID());
        json.addProperty(PLAYER_NAME, entry.getName());
        // The scout's single price column is max(asking price, highest bid) - it is what it would
        // cost to take the player now, not what the seller asked. TransferTools.listScout adds the
        // two apart from the listing snapshot the entry was built from.
        json.addProperty("price", money(entry.getPrice()));
        json.addProperty(DEADLINE, entry.getDeadline() == null ? null
                : HODateTime.fromDbTimestamp(entry.getDeadline()).toHT());
        json.addProperty("note", entry.getInfo());
        // Filled in once the watched transfer completed; null while he is still listed.
        json.addProperty("salePrice", entry.isSold() ? money(entry.getSalePrice()) : null);
        json.addProperty("soldAt", entry.getSoldAt() == null ? null
                : HODateTime.fromDbTimestamp(entry.getSoldAt()).toHT());
        json.addProperty("age", entry.getAlter());
        json.addProperty(AGE_DAYS, entry.getAgeDays());
        json.addProperty(SALARY, money(entry.getbaseWage()));
        json.addProperty("tsi", entry.getTSI());
        json.addProperty("form", entry.getForm());
        json.addProperty(EXPERIENCE_KEY, entry.getErfahrung());
        json.addProperty(LEADERSHIP, entry.getLeadership());
        // Character, 0-5 each, from the playerdetails fetch manage_scout add makes. All three null when
        // nobody has looked: the honesty column is the one that only this fetch ever writes, so it
        // is what says whether the older, always-present agreeability column holds an observation
        // or its 0 default - and 0 is "nasty fellow", a claim we would not be entitled to make.
        final var observed = entry.getHonesty() != null;
        json.addProperty(AGREEABILITY, observed ? entry.getAgreeability() : null);
        json.addProperty(AGGRESSIVENESS, entry.getAggressiveness());
        json.addProperty(HONESTY, entry.getHonesty());
        json.addProperty(SPECIALTY, entry.getSpeciality());
        json.addProperty(STAMINA, entry.getKondition());
        json.addProperty(KEEPER_KEY, entry.getTorwart());
        json.addProperty("playmaker", entry.getSpielaufbau());
        json.addProperty("scorer", entry.getTorschuss());
        json.addProperty(PASSING_KEY, entry.getPasspiel());
        json.addProperty(WINGER_KEY, entry.getFluegelspiel());
        json.addProperty("defender", entry.getVerteidigung());
        json.addProperty(SET_PIECES, entry.getStandards());
        return json;
    }

    /** The tactic level as the Lineup tab shows it: the raw rating, rounded the way HO rounds it. */
    public static int tacticLevel(double tacticRating) {
        return (int) (tacticRating > 0 ? tacticRating + 1 : tacticRating);
    }

    /** The seven sector ratings over 90 minutes, plus the derived formation and tactic strength. */
    public static JsonObject ratings(RatingPredictionModel ratingModel, Lineup lineup) {
        final var json = new JsonObject();
        for (final var sector : RatingPredictionModel.RatingSector.values()) {
            json.addProperty(sector.name().toLowerCase(java.util.Locale.ENGLISH),
                    ratingModel.getAverageRating(lineup, sector, 90));
        }
        // tacticRating is the raw tactic strength (Play Creatively: sum over field players of
        // 4 x passing + experience, doubled for Unpredictable, / 50 - see calcCreative); tacticLevel
        // is what the Lineup tab shows. Minute 0 = the starting eleven, as simulate_lineup uses.
        final var tacticRating = ratingModel.getTacticRating(lineup, 0);
        json.addProperty("tacticType", lineup.getTacticType());
        json.addProperty("tacticRating", tacticRating);
        json.addProperty("tacticLevel", tacticLevel(tacticRating));
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
