package core.mcp;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import core.file.xml.XMLTransferSearchParser;
import core.model.player.Specialty;
import core.net.ChppWriteService;
import core.net.TransferSearchService;
import core.model.UserParameter;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * screen_market: the hattrick skills' market screening in one call. It runs a batch of transfer
 * searches, a list of specialties expanded into one search each, and filters the hits by the
 * caller's rules (wage after the foreign markup, injuries, specialty whitelist, per-search skill
 * floors). It ranks them by price plus wages and enriches the top rows with the character
 * (card risk), a local price estimate and a buy check.
 * <p>
 * The rules are arguments, not server code: they belong to the user and change. The server never
 * bids. Searches beyond the hourly cap are not run but handed back as {@code queued}, ready to
 * pass as queries again; the cap is never worked around.
 */
public final class MarketTools {

    private static final String QUERIES = "queries";
    private static final String SPECIALTY = "specialty";
    private static final String SPECIALTIES = "specialties";
    private static final String LABEL = "label";
    private static final String SCREEN = "screen";
    private static final String ERROR = "error";
    private static final String ESTIMATE = "estimate";
    private static final String MIDDLE_HALF = "middleHalf";
    private static final String LISTINGS_ESTIMATE = "listingsEstimate";
    private static final String SAMPLE_SIZE = "sampleSize";
    private static final String COST = "cost";
    private static final String UNKNOWN = "?";
    private static final String MAX_WAGE = "maxWage";
    private static final String FOREIGN_FACTOR = "foreignWageFactor";
    private static final String EXCLUDE_INJURED = "excludeInjured";
    private static final String TOP = "top";
    private static final String CHARACTER_TOP = "characterTop";
    private static final String CARD_RISK_WARN = "cardRiskWarn";
    private static final String WAGE_WEEKS = "wageWeeks";
    private static final String SQUAD_CAP = "squadCap";
    private static final String COUNT_YOUTH = "countYouth";
    private static final String COUNT_OPEN_BIDS = "countOpenBids";
    private static final String RESERVED_CASH = "reservedCash";
    private static final String SCOUT = "scout";
    private static final String BOOLEAN = "boolean";
    private static final String INTEGER = "integer";
    private static final String NUMBER = "number";
    private static final String DEFAULT = "; default ";
    private static final String FREE_CASH = "freeCash";
    private static final String SET_PIECES = "setPieces";
    private static final String EXPERIENCE = "experience";

    /** The skills a screen floor may name, as the hit reports them. */
    private static final Map<String, java.util.function.ToIntFunction<XMLTransferSearchParser.Details>>
            SCREEN_SKILLS = Map.of(
            "keeper", XMLTransferSearchParser.Details::keeper,
            "defending", XMLTransferSearchParser.Details::defender,
            "playmaking", XMLTransferSearchParser.Details::playmaker,
            "winger", XMLTransferSearchParser.Details::winger,
            "passing", XMLTransferSearchParser.Details::passing,
            "scoring", XMLTransferSearchParser.Details::scorer,
            SET_PIECES, XMLTransferSearchParser.Details::setPieces,
            EXPERIENCE, XMLTransferSearchParser.Details::experience);

    private static final int DEFAULT_TOP = 10;
    private static final int MAX_TOP = 25;
    private static final int DEFAULT_MAX_WAGE = 10_000;
    private static final double DEFAULT_FOREIGN_FACTOR = 1.2;
    private static final int DEFAULT_WAGE_WEEKS = 48;
    private static final double DEFAULT_CARD_RISK_WARN = 12;
    private static final int DEFAULT_SQUAD_CAP = 26;
    private static final int DEFAULT_MIN_SAMPLE = 15;
    private static final String MIN_SAMPLE = "minSample";
    /** Foul chance in % per aggressiveness level 0-4 (wiki, personality); level 5 is unpublished. */
    static final double[] FOUL_PCT = {0.3, 1.5, 4, 7.5, 11.5};
    /** Dive chance in % per honesty level 0-4 (wiki; levels 2 and 3 unverified). */
    static final double[] DIVE_PCT = {10, 6, 4, 1.5, 0.3};

    /** The row layout, the hattrick skills' fixed table plus what they compute from it. */
    static final List<String> COLUMNS = List.of("playerId", "player", "foreign", SPECIALTY, "age",
            "PM", "PASS", "SC", "WG", "DEF", "KP", "SP", "EXP", "wageInSquad", "price",
            "priceSource", "cardRisk", "cards", "closes", ESTIMATE, "estimateBasis", MIDDLE_HALF,
            SAMPLE_SIZE, COST, "fundsAfter", "warnings");
    /** Every filteredOut key, reported with 0 too so the caller never has to guess. */
    static final List<String> REJECTIONS = List.of("closed", "injured", SPECIALTY, SCREEN, "wage");
    /** Yellow cards at which a row warns: one more is a ban. Hattrick's 3 means suspended. */
    private static final int CARDS_WARN = 2;
    private static final int SUSPENDED_CARDS = 3;

    private MarketTools() {
    }

    public static List<McpTool> tools() {
        return List.of(
                new McpTool("screen_market",
                        "Market screening in one call: runs the queries (specialty may be a list, "
                                + "one search each; searches over the hourly cap come back as "
                                + "queued), filters by wage after the foreign markup, injury, "
                                + "specialty and screen floors, ranks by price + wages, and adds "
                                + "card risk, cards, a price estimate from stored sales (not an "
                                + "ask) and a buy check for the top rows. Never bids.",
                        schema(),
                        MarketTools::screenMarket)
                        .asTask(args -> true)
                        .withBudget("Lower top, or pass fewer queries.")
        );
    }

    private static JsonObject schema() {
        return McpTool.schema(McpTool.properties(
                QUERIES, McpTool.prop("array", "searches shaped like search_transfers' single "
                        + "search; specialty may be an id list (one search each), plus optional "
                        + "label and screen {skill: min} (keeper, defending, playmaking, winger, "
                        + "passing, scoring, setPieces, experience) applied to the hits"),
                MAX_WAGE, McpTool.prop(INTEGER, "highest wage in our squad, after the foreign "
                        + "markup" + DEFAULT + DEFAULT_MAX_WAGE),
                FOREIGN_FACTOR, McpTool.prop(NUMBER, "wage factor for a player whose "
                        + "nativeCountryId is not ours" + DEFAULT + DEFAULT_FOREIGN_FACTOR),
                SPECIALTIES, McpTool.prop("array", "keep only these specialty ids; default all"),
                EXCLUDE_INJURED, McpTool.prop(BOOLEAN, "drop injured players; default true"),
                TOP, McpTool.prop(INTEGER, "rows to return" + DEFAULT + DEFAULT_TOP
                        + " (max " + MAX_TOP + ")"),
                CHARACTER_TOP, McpTool.prop(INTEGER, "rows that get card risk, one playerdetails "
                        + "request each; default top, 0 for none"),
                CARD_RISK_WARN, McpTool.prop(NUMBER, "warn from this card risk in %"
                        + DEFAULT + DEFAULT_CARD_RISK_WARN),
                ESTIMATE, McpTool.prop(BOOLEAN, "local price estimate per row, free; default true"),
                MIN_SAMPLE, McpTool.prop(INTEGER, "warn when an estimate rests on fewer "
                        + "comparables" + DEFAULT + DEFAULT_MIN_SAMPLE),
                WAGE_WEEKS, McpTool.prop(INTEGER, "weeks of wage in cost = price + wage x weeks"
                        + DEFAULT + DEFAULT_WAGE_WEEKS),
                SQUAD_CAP, McpTool.prop(INTEGER, "squad cap" + DEFAULT + DEFAULT_SQUAD_CAP),
                COUNT_YOUTH, McpTool.prop(BOOLEAN, "youth players count against the cap; default false"),
                COUNT_OPEN_BIDS, McpTool.prop(BOOLEAN, "our open bids count against the cap, "
                        + "one currentbids request; default false"),
                RESERVED_CASH, McpTool.prop(INTEGER, "the approved ceilings of all open bids, "
                        + "taken off expectedCash; default 0"),
                SCOUT, McpTool.prop(INTEGER, "add the first N rows to HO's scout list; default 0")));
    }

    /** One CHPP search to run: the search_transfers arguments with a single specialty. */
    record PlannedSearch(int index, @Nullable String label, JsonObject query, Map<String, Integer> screen) {}

    /** A hit that passed the filters, with what the ranking needs. */
    record Candidate(XMLTransferSearchParser.Hit hit, boolean foreign, long wageInSquad,
                     @Nullable Long price, String priceSource) {}

    /** What running the planned searches left: per-search rows, the queue, and the survivors. */
    private record Sweep(JsonArray searches, JsonArray queued, Map<String, Integer> filteredOut,
                         List<Candidate> candidates) {}

    private static JsonObject screenMarket(JsonObject args) throws McpToolException {
        final var rules = Rules.from(args);
        final var sweep = sweep(plan(McpJson.reqArray(args, QUERIES)), rules, ourCountryId());
        final var json = new JsonObject();
        json.addProperty("currency", McpJson.currency());
        json.add("searches", sweep.searches());
        json.addProperty("searchesRun", sweep.searches().size());
        json.addProperty("searchCap", UserParameter.instance().mcpSearchesPerSession);
        json.addProperty("searchCapLeft", TransferSearchService.instance().remainingSearches());
        json.addProperty("searchCapWindow", "rolling hour, every search_transfers and "
                + "estimate_market_value live search counts");
        if (!sweep.queued().isEmpty()) {
            json.add("queued", sweep.queued());
            json.addProperty("queuedNote", "over the hourly search cap; pass these as queries "
                    + "again once it frees up");
        }
        final var dropped = new JsonObject();
        sweep.filteredOut().forEach(dropped::addProperty);
        json.add("filteredOut", dropped);
        final var candidates = sweep.candidates();
        json.addProperty("passed", candidates.size());

        candidates.sort(Comparator.comparingLong(c -> plainCost(c, rules)));
        final var top = candidates.subList(0, Math.min(rules.top(), candidates.size()));
        final var buyCheck = buyCheck(rules);
        json.add("buyCheck", buyCheck);
        final var freeCash = buyCheck.has(FREE_CASH) ? buyCheck.get(FREE_CASH).getAsLong() : null;

        final var rows = new ArrayList<JsonArray>();
        for (var i = 0; i < top.size(); i++) {
            rows.add(row(top.get(i), i < rules.characterTop(), rules, freeCash));
        }
        // Re-ranked by cost with the estimate in it: early bids understate the final price.
        final var costColumn = COLUMNS.indexOf(COST);
        rows.sort(Comparator.comparingLong(r -> r.get(costColumn).getAsLong()));
        final var columns = new JsonArray();
        COLUMNS.forEach(columns::add);
        json.add("columns", columns);
        final var table = new JsonArray();
        rows.forEach(table::add);
        json.add("rows", table);
        if (rules.scout() > 0 && !rows.isEmpty()) {
            json.add(SCOUT, scout(rows, rules.scout()));
        }
        json.addProperty("note", "cost = max(price, estimate) + wageInSquad x " + rules.wageWeeks()
                + " weeks; fundsAfter = freeCash - max(price, estimate) - one week's wage. "
                + "price/priceSource is the market's live bid or ask; estimate is HO's valuation "
                + "from the stored corpus (live false, estimateBasis sales|thin_sales_plus_listings"
                + "|listings), never an ask. cardRisk is foul % + dive % from aggressiveness/"
                + "honesty, n/a above level 4, ? when not read. cards: yellow cards, 3 suspended.");
        return json;
    }

    /**
     * Runs the planned searches the cap still allows, in order, and queues the rest. A failed
     * search reports its own error and the others go on; a player found twice counts once.
     */
    private static Sweep sweep(List<PlannedSearch> planned, Rules rules, @Nullable Integer ourCountry) {
        final var remaining = TransferSearchService.instance().remainingSearches();
        final var sweep = new Sweep(new JsonArray(), new JsonArray(), emptyRejections(),
                new ArrayList<>());
        final var seen = new LinkedHashSet<Integer>();
        for (var i = 0; i < planned.size(); i++) {
            final var search = planned.get(i);
            if (i >= remaining) {
                sweep.queued().add(search.query());
                continue;
            }
            final var row = new JsonObject();
            row.addProperty("index", search.index());
            if (search.label() != null) {
                row.addProperty(LABEL, search.label());
            }
            row.add(SPECIALTY, search.query().get(SPECIALTY));
            try {
                final var page = TransferTools.search(TransferTools.queryFrom(search.query()));
                row.addProperty("itemCount", page.itemCount());
                row.addProperty("hitCount", page.hits().size());
                page.hits().stream().filter(h -> seen.add(h.playerId()))
                        .forEach(h -> admit(h, search.screen(), rules, ourCountry, sweep));
            } catch (IllegalArgumentException | McpToolException e) {
                row.addProperty(ERROR, e.getMessage());
            }
            sweep.searches().add(row);
        }
        return sweep;
    }

    private static void admit(XMLTransferSearchParser.Hit hit, Map<String, Integer> screen,
            Rules rules, @Nullable Integer ourCountry, Sweep sweep) {
        final var reason = rejection(hit, screen, rules, ourCountry);
        if (reason != null) {
            sweep.filteredOut().merge(reason, 1, Integer::sum);
        } else {
            sweep.candidates().add(candidate(hit, rules, ourCountry));
        }
    }

    static Map<String, Integer> emptyRejections() {
        final var counts = new LinkedHashMap<String, Integer>();
        REJECTIONS.forEach(reason -> counts.put(reason, 0));
        return counts;
    }

    /** The queries as single-specialty searches, in order; a list of specialties expands. */
    static List<PlannedSearch> plan(JsonArray queries) {
        if (queries.isEmpty()) {
            throw new IllegalArgumentException("queries is empty - pass at least one search.");
        }
        final var planned = new ArrayList<PlannedSearch>();
        for (var i = 0; i < queries.size(); i++) {
            if (!queries.get(i).isJsonObject()) {
                throw new IllegalArgumentException("Each entry in queries must be an object, got: "
                        + queries.get(i));
            }
            final var query = queries.get(i).getAsJsonObject().deepCopy();
            final var label = McpJson.optString(query, LABEL, null);
            final var screen = screen(query.has(SCREEN) ? query.getAsJsonObject(SCREEN) : null);
            query.remove(LABEL);
            query.remove(SCREEN);
            final var specialty = query.get(SPECIALTY);
            if (specialty != null && specialty.isJsonArray()) {
                for (final var id : specialty.getAsJsonArray()) {
                    final var single = query.deepCopy();
                    single.add(SPECIALTY, id);
                    planned.add(new PlannedSearch(i, label, single, screen));
                }
            } else {
                planned.add(new PlannedSearch(i, label, query, screen));
            }
        }
        return planned;
    }

    private static Map<String, Integer> screen(@Nullable JsonObject screen) {
        final var floors = new LinkedHashMap<String, Integer>();
        if (screen != null) {
            for (final var entry : screen.entrySet()) {
                if (!SCREEN_SKILLS.containsKey(entry.getKey())) {
                    throw new IllegalArgumentException("Unknown screen skill '" + entry.getKey()
                            + "'. Use one of " + SCREEN_SKILLS.keySet() + ".");
                }
                floors.put(entry.getKey(), entry.getValue().getAsInt());
            }
        }
        return floors;
    }

    /** A hit's level in a screen skill, by its screen name (checked by {@link #screen}). */
    static int skill(XMLTransferSearchParser.Details d, String name) {
        return SCREEN_SKILLS.get(name).applyAsInt(d);
    }

    /** Why a hit is dropped, or null when it passes; the reason is the filteredOut key. */
    static @Nullable String rejection(XMLTransferSearchParser.Hit hit, Map<String, Integer> screen,
            Rules rules, @Nullable Integer ourCountry) {
        final var d = hit.details();
        if (d == null) {
            return "closed";
        }
        if (rules.excludeInjured() && d.injuryLevel() > 0) {
            return "injured";
        }
        if (rules.specialties() != null && !rules.specialties().contains(d.specialty())) {
            return SPECIALTY;
        }
        final var belowFloor = screen.entrySet().stream()
                .anyMatch(floor -> skill(d, floor.getKey()) < floor.getValue());
        if (belowFloor) {
            return SCREEN;
        }
        if (wageInSquad(McpJson.money(d.salary()), isForeign(hit, ourCountry), rules.foreignFactor())
                > rules.maxWage()) {
            return "wage";
        }
        return null;
    }

    static boolean isForeign(XMLTransferSearchParser.Hit hit, @Nullable Integer ourCountry) {
        return ourCountry != null && hit.nativeCountryId() != ourCountry;
    }

    static long wageInSquad(long wage, boolean foreign, double factor) {
        return foreign ? Math.round(wage * factor) : wage;
    }

    /** Price is the highest bid, else the asking price, else none. */
    static Candidate candidate(XMLTransferSearchParser.Hit hit, Rules rules, @Nullable Integer ourCountry) {
        final var foreign = isForeign(hit, ourCountry);
        final var wage = wageInSquad(McpJson.money(hit.details().salary()), foreign, rules.foreignFactor());
        if (hit.highestBid() > 0) {
            return new Candidate(hit, foreign, wage, McpJson.money(hit.highestBid()), "bid");
        }
        if (hit.askingPrice() > 0) {
            return new Candidate(hit, foreign, wage, McpJson.money(hit.askingPrice()), "ask");
        }
        return new Candidate(hit, foreign, wage, null, "no ask / no bid");
    }

    private static long plainCost(Candidate c, Rules rules) {
        return (c.price() == null ? 0 : c.price()) + c.wageInSquad() * rules.wageWeeks();
    }

    /**
     * Card risk in %, foul by aggressiveness plus dive by honesty. "n/a" for a level the tables do
     * not cover, "?" when the character is unknown.
     */
    static JsonElement cardRisk(@Nullable Integer aggressiveness, @Nullable Integer honesty) {
        if (aggressiveness == null || honesty == null) {
            return new JsonPrimitive(UNKNOWN);
        }
        if (aggressiveness < 0 || aggressiveness >= FOUL_PCT.length
                || honesty < 0 || honesty >= DIVE_PCT.length) {
            return new JsonPrimitive("n/a");
        }
        return new JsonPrimitive(Math.round((FOUL_PCT[aggressiveness] + DIVE_PCT[honesty]) * 10) / 10.0);
    }

    private static JsonArray row(Candidate c, boolean readCharacter, Rules rules, @Nullable Long freeCash) {
        final var hit = c.hit();
        final var d = hit.details();
        final var warnings = new JsonArray();
        final var row = new JsonArray();
        row.add(hit.playerId());
        row.add(hit.playerName());
        row.add(c.foreign());
        final var specialty = Specialty.getSpecialty(d.specialty());
        row.add(specialty == null ? "" : specialty.toString());
        row.add(d.age() + "y" + d.ageDays() + "d");
        for (final int level : new int[]{d.playmaker(), d.passing(), d.scorer(), d.winger(),
                d.defender(), d.keeper(), d.setPieces(), d.experience()}) {
            row.add(level);
        }
        row.add(c.wageInSquad());
        row.add(c.price());
        row.add(c.priceSource());
        final var risk = readCharacter ? character(hit.playerId()) : new JsonPrimitive(UNKNOWN);
        row.add(risk);
        if (risk.getAsJsonPrimitive().isNumber() && risk.getAsDouble() >= rules.cardRiskWarn()) {
            warnings.add("card risk " + risk.getAsDouble() + " %");
        }
        row.add(d.cards());
        addCardWarning(d.cards(), warnings);
        row.add(hit.deadline() == null ? null : hit.deadline().toHT().substring(0, 16));
        addEstimateAndCost(row, c, rules, freeCash, warnings);
        row.add(warnings);
        return row;
    }

    static void addCardWarning(int cards, JsonArray warnings) {
        if (cards >= SUSPENDED_CARDS) {
            warnings.add("suspended (" + cards + " cards)");
        } else if (cards == CARDS_WARN) {
            warnings.add(cards + " yellow cards: one more is a ban");
        }
    }

    /** A thin sample is named, so a single estimate is not read as settled. */
    static void addSampleWarning(@Nullable Integer sampleSize, int minSample, JsonArray warnings) {
        if (sampleSize != null && sampleSize < minSample) {
            warnings.add("thin estimate: n=" + sampleSize);
        }
    }

    /** estimate, estimateBasis, middleHalf, sampleSize, cost and fundsAfter, in COLUMNS order. */
    private static void addEstimateAndCost(JsonArray row, Candidate c, Rules rules,
            @Nullable Long freeCash, JsonArray warnings) {
        final var estimate = rules.estimate() ? estimate(c.hit()) : null;
        final var figure = estimate == null ? null : estimateFigure(estimate);
        row.add(figure);
        row.add(estimate == null || !estimate.has("basis") ? null : estimate.get("basis").getAsString());
        row.add(estimate == null ? JsonNull.INSTANCE : middleHalf(estimate));
        final Integer sampleSize = estimate == null || !estimate.has(SAMPLE_SIZE)
                ? null : estimate.get(SAMPLE_SIZE).getAsInt();
        row.add(sampleSize);
        if (figure != null) {
            addSampleWarning(sampleSize, rules.minSample(), warnings);
        }
        final var expected = Math.max(c.price() == null ? 0 : c.price(), figure == null ? 0 : figure);
        row.add(expected + c.wageInSquad() * rules.wageWeeks());
        final Long fundsAfter = freeCash == null ? null : freeCash - expected - c.wageInSquad();
        row.add(fundsAfter);
        if (fundsAfter != null && fundsAfter < 0) {
            warnings.add("not affordable");
        }
    }

    private static JsonElement character(int playerId) {
        final var player = TransferTools.observeMarketPlayer(playerId);
        return player == null ? new JsonPrimitive(UNKNOWN)
                : cardRisk(player.getAggressivity(), player.getHonesty());
    }

    /** A local valuation of the hit as a profile, live: false; null when there is none. */
    private static @Nullable JsonObject estimate(XMLTransferSearchParser.Hit hit) {
        final var d = hit.details();
        final var skills = new JsonObject();
        // The six positional skills; estimate_market_value's profile takes the same names.
        SCREEN_SKILLS.forEach((name, level) -> {
            if (!name.equals(SET_PIECES) && !name.equals(EXPERIENCE)) {
                skills.addProperty(name, level.applyAsInt(d));
            }
        });
        final var profile = new JsonObject();
        profile.addProperty("age", d.age());
        profile.addProperty("ageDays", d.ageDays());
        profile.add("skills", skills);
        profile.addProperty(SPECIALTY, d.specialty());
        profile.addProperty(SET_PIECES, d.setPieces());
        profile.addProperty("tsi", d.tsi());
        final var args = new JsonObject();
        args.add("profile", profile);
        args.addProperty("live", false);
        try {
            return TransferTools.valueForScreen(args);
        } catch (McpToolException | RuntimeException e) {
            return null;
        }
    }

    /** The sale estimate, else the listings figure; null when there is neither. */
    static @Nullable Long estimateFigure(JsonObject summary) {
        if (summary.has(ESTIMATE) && !summary.get(ESTIMATE).isJsonNull()) {
            return summary.get(ESTIMATE).getAsLong();
        }
        if (summary.has(LISTINGS_ESTIMATE)) {
            return summary.getAsJsonObject(LISTINGS_ESTIMATE).get("value").getAsLong();
        }
        return null;
    }

    private static JsonElement middleHalf(JsonObject summary) {
        if (summary.has(MIDDLE_HALF)) {
            return summary.get(MIDDLE_HALF);
        }
        final var listings = summary.has(LISTINGS_ESTIMATE) ? summary.getAsJsonObject(LISTINGS_ESTIMATE) : null;
        return listings != null && listings.has(MIDDLE_HALF) ? listings.get(MIDDLE_HALF) : JsonNull.INSTANCE;
    }

    /** Squad size against the cap, and expectedCash minus the reserved bid ceilings. */
    private static JsonObject buyCheck(Rules rules) throws McpToolException {
        final var json = new JsonObject();
        final var model = McpJson.model();
        var squad = model.getCurrentPlayers().size();
        if (rules.countYouth()) {
            squad += model.getCurrentYouthPlayers().size();
        }
        if (rules.countOpenBids()) {
            try {
                final var bids = ChppWriteService.instance().listCurrentBidsForAgent();
                final var own = (int) bids.items().stream()
                        .filter(b -> TransferTools.isOwnBidCategory(b.trackingTypeId())).count();
                json.addProperty("openBids", own);
                squad += own;
            } catch (ChppWriteService.ChppWriteException e) {
                json.addProperty("openBidsError", e.getMessage());
            }
        }
        json.addProperty("squadSize", squad);
        json.addProperty(SQUAD_CAP, rules.squadCap());
        json.addProperty("roomInSquad", rules.squadCap() - squad);
        if (squad >= rules.squadCap()) {
            json.addProperty("squadNote", "at or over the cap: name the player to cut before buying");
        }
        final var economy = model.getEconomy();
        if (economy != null) {
            final var expectedCash = McpJson.money(economy.getExpectedCash());
            json.addProperty("expectedCash", expectedCash);
            json.addProperty(RESERVED_CASH, rules.reservedCash());
            json.addProperty(FREE_CASH, expectedCash - rules.reservedCash());
        }
        return json;
    }

    private static @Nullable Integer ourCountryId() throws McpToolException {
        final var xtra = McpJson.model().getXtraDaten();
        return xtra == null ? null : xtra.getCountryId();
    }

    private static JsonElement scout(List<JsonArray> rows, int count) {
        final var players = new JsonArray();
        rows.stream().limit(count).forEach(r -> players.add(r.get(0)));
        final var args = new JsonObject();
        args.add("players", players);
        try {
            return TransferTools.scoutForScreen(args);
        } catch (McpToolException e) {
            final var error = new JsonObject();
            error.addProperty(ERROR, e.getMessage());
            return error;
        }
    }

    /** The caller's rules, defaulted. */
    record Rules(long maxWage, double foreignFactor, @Nullable Set<Integer> specialties,
                 boolean excludeInjured, int top, int characterTop, double cardRiskWarn,
                 boolean estimate, int minSample, int wageWeeks, int squadCap, boolean countYouth,
                 boolean countOpenBids, long reservedCash, int scout) {

        static Rules from(JsonObject args) {
            Set<Integer> specialties = null;
            if (args.has(SPECIALTIES)) {
                specialties = new LinkedHashSet<>();
                for (final var id : McpJson.reqArray(args, SPECIALTIES)) {
                    specialties.add(id.getAsInt());
                }
            }
            final var top = Math.max(1, Math.min(McpJson.optInt(args, TOP, DEFAULT_TOP), MAX_TOP));
            return new Rules(
                    McpJson.optInt(args, MAX_WAGE, DEFAULT_MAX_WAGE),
                    optDouble(args, FOREIGN_FACTOR, DEFAULT_FOREIGN_FACTOR),
                    specialties,
                    McpJson.optBool(args, EXCLUDE_INJURED, true),
                    top,
                    Math.max(0, McpJson.optInt(args, CHARACTER_TOP, top)),
                    optDouble(args, CARD_RISK_WARN, DEFAULT_CARD_RISK_WARN),
                    McpJson.optBool(args, ESTIMATE, true),
                    Math.max(0, McpJson.optInt(args, MIN_SAMPLE, DEFAULT_MIN_SAMPLE)),
                    Math.max(0, McpJson.optInt(args, WAGE_WEEKS, DEFAULT_WAGE_WEEKS)),
                    McpJson.optInt(args, SQUAD_CAP, DEFAULT_SQUAD_CAP),
                    McpJson.optBool(args, COUNT_YOUTH, false),
                    McpJson.optBool(args, COUNT_OPEN_BIDS, false),
                    McpJson.optInt(args, RESERVED_CASH, 0),
                    Math.max(0, McpJson.optInt(args, SCOUT, 0)));
        }

        private static double optDouble(JsonObject args, String name, double defaultValue) {
            return args.has(name) && !args.get(name).isJsonNull() ? args.get(name).getAsDouble() : defaultValue;
        }
    }
}
