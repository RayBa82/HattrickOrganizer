package core.mcp;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import core.constants.player.PlayerSpeciality;
import core.db.DBManager;
import core.db.TransferBid;
import core.db.TransferListing;
import core.file.xml.XMLCurrentBidsParser;
import core.file.xml.XMLTransferSearchParser;
import core.model.player.Player;
import core.net.ChppWriteService;
import core.net.OnlineWorker;
import core.net.TransferSearchQuery;
import core.net.TransferSearchService;
import core.util.HODateTime;
import core.util.HOLogger;
import module.transfer.PlayerTransfer;
import module.transfer.scout.ScoutEintrag;
import module.transfer.scout.ScoutList;
import module.transfer.search.TransferSearchConverter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Transfer market search and comparables.
 * <p>
 * Every result is snapshotted into the TRANSFERLISTING corpus before being returned - the data is
 * already in hand, so it costs no extra CHPP traffic, and repeated observation of the same listing
 * yields a highest bid ever closer to the sale price.
 */
public final class TransferTools {

    /**
     * The skills that say something about what a player is comparable to.
     * <p>
     * Set pieces is deliberately not among them. It is a specialist skill that says nothing about
     * the position a player is bought for, and it is often his accidental second-highest: ranking
     * it in matches a scoring-14 striker against set-piece takers instead of against strikers, and
     * for a low-skilled player it could even take the primary slot.
     */
    private static final List<TransferSearchQuery.Skill> POSITIONAL_SKILLS = List.of(
            TransferSearchQuery.Skill.KEEPER,
            TransferSearchQuery.Skill.DEFENDING,
            TransferSearchQuery.Skill.PLAYMAKING,
            TransferSearchQuery.Skill.PASSING,
            TransferSearchQuery.Skill.WINGER,
            TransferSearchQuery.Skill.SCORING);

    private static final int MAX_PAGE_SIZE = 100;     // the CHPP maximum, and HO's default

    /** How long a losing bid keeps showing in the default view after its deadline. */
    private static final java.time.Duration LOSING_BID_GRACE = java.time.Duration.ofHours(24);

    private TransferTools() {
    }

    public static List<McpTool> tools() {
        return List.of(
                new McpTool("search_transfers",
                        "Search the Hattrick transfer market; age and at least one skill are "
                                + "mandatory. Hattrick refuses a skill window wider than 3 levels, "
                                + "and an age range including 17 or 18 may span at most one year. "
                                + "queries[] runs several searches in one call (hits merged). Searches "
                                + "are capped per hour; page and sort hits with resultId for "
                                + "free. Every hit is stored and feeds estimate_market_value.",
                        searchSchema(),
                        TransferTools::searchTool)
                        .withBudget("Page the hits with resultId + limit/offset (no new search, "
                                + "not counted against the cap); pass full only with a small limit.")
                        // a fresh search is one CHPP request per queries entry plus the resolver;
                        // paging a kept result with resultId is a local read
                        .asTask(args -> !args.has("resultId")),
                new McpTool("get_team_transfers",
                        "The managed team's own transfer history, newest first, with price, TSI at "
                                + "transfer time, the mother-club and previous-club fees, and specialty "
                                + "+ skills from HO's own snapshot of the player around the transfer. "
                                + "A local read - HO refreshes it, and records our sales into the "
                                + "market corpus, on every team download. One page (default 20); "
                                + "page back with offset.",
                        McpTool.schema(McpTool.properties(
                                "season", McpTool.prop("integer", "Hattrick season number; omit for the full history"),
                                "direction", McpTool.prop("string", "buy, sell, or omit for both"),
                                "limit", McpTool.prop("integer", "how many transfers to return, newest first; default 20, max 100"),
                                "offset", McpTool.prop("integer", "how many of the newest transfers to skip, for paging; default 0"),
                                "bulk", McpTool.prop("boolean", "for scripts: every transfer as a JSON file "
                                        + "to download (HTTP GET of the returned download path on the MCP "
                                        + "endpoint); the answer keeps the totals and a 3-row preview. "
                                        + "Default false"))),
                        TransferTools::teamTransfers)
                        .withBudget("Page with a smaller limit and offset, or narrow by season / direction.",
                                McpTool.OptionalSection.perItem("transfers", "skills", "skills", "specialty")),
                new McpTool("get_player_transfer_history",
                        "Every Hattrick transfer of any player, ours or not, newest first: date, "
                                + "price, TSI at the time, seller and buyer. One CHPP request per call. "
                                + "An empty list means the player was never sold.",
                        McpTool.schema(McpTool.properties(
                                        "playerId", McpTool.prop("integer", "the Hattrick player id")),
                                "playerId"),
                        TransferTools::playerTransferHistory),
                new McpTool("place_bid",
                        "Place a transfer bid. TWO STEPS so a stray call cannot spend money: call "
                                + "with playerId + bidAmount for a preview and a confirmToken, then "
                                + "again with the same playerId and that token to place it. The "
                                + "second call leaves the machine and commits money in Hattrick. "
                                + "Amounts are in the user's currency. Step 2 echoes Hattrick's "
                                + "confirmed maxBid, or a warning if the autobid is missing.",
                        McpTool.schema(McpTool.properties(
                                        "playerId", McpTool.prop("integer", "the player to bid on"),
                                        "bidAmount", McpTool.prop("integer",
                                                "the bid, in the user's currency (required in step 1)"),
                                        "maxBidAmount", McpTool.prop("integer",
                                                "optional autobid ceiling, at least bidAmount "
                                                        + "(step 1 refuses a lower one)"),
                                        "confirmToken", McpTool.prop("string",
                                                "the token from step 1 (required in step 2, omit in step 1)")),
                                "playerId"),
                        TransferTools::placeBid),
                new McpTool("get_open_bids",
                        "Transfers this team has an active bid on, from Hattrick's own tracking - "
                                + "so it sees bids placed on the website too. Each row says whether "
                                + "you are the leading bidder, plus askingPrice and, once finished, "
                                + "salePrice, outcome and ourBid. selling lists our own listed players "
                                + "(sellingCount) with askingPrice and highest bid. includeAll returns "
                                + "every tracked transfer.",
                        McpTool.schema(McpTool.properties(
                                "includeAll", McpTool.prop("boolean",
                                        "return every tracked transfer, not just your open bids; "
                                                + "default false"))),
                        TransferTools::openBids),
                new McpTool("manage_scout",
                        "Edit HO's OWN local shortlist (Transfers -> TransferScout tab), not a "
                                + "Hattrick bookmark. add: each player must have been seen by an "
                                + "earlier search_transfers; re-adding replaces the entry; one CHPP "
                                + "call each for character, echoed in list_scout's shape. remove: "
                                + "also stops each entry's re-listing watch on team downloads. "
                                + "refresh: current ask/bid/deadline, a CHPP read each (default: "
                                + "open entries).",
                        McpTool.schema(McpTool.properties(
                                        "action", McpTool.prop("string", "add, remove or refresh"),
                                        "players", McpTool.prop("array",
                                                "bare player ids, or for add {playerId, note} "
                                                        + "objects; the note is free text kept in "
                                                        + "the entry's notes column, e.g. why he "
                                                        + "is a target; optional for refresh")),
                                "action"),
                        TransferTools::manageScout),
                new McpTool("list_scout",
                        "Read back HO's local scout shortlist, filtered, one page (count = the "
                                + "filtered total). Rows: playerId, playerName, note, age, specialty, "
                                + "price and deadline (frozen when added), askingPrice, highestBid, "
                                + "observedAt (newest observation), salePrice, soldAt, plus each skill "
                                + "filtered on. full adds every frozen skill, TSI, wage and character.",
                        McpTool.schema(McpTool.properties(
                                "skills", McpTool.prop("array", "skill filters, all must hold: "
                                        + "{skill, min?, max?}, skill one of " + SKILL_NAMES
                                        + "; levels, any span"),
                                "ageMin", McpTool.prop("integer", "minimum age in years"),
                                "ageMax", McpTool.prop("integer", "maximum age in years"),
                                "specialty", McpTool.prop("integer",
                                        "specialty id as in search_transfers (0 none)"),
                                "onlyOpen", McpTool.prop("boolean",
                                        "only players still on the market: no sale recorded and "
                                                + "deadline not passed; default false"),
                                "full", McpTool.prop("boolean",
                                        "every stored field per player and the whole note; "
                                                + "default false, notes cut to " + COMPACT_NOTE_CHARS
                                                + " chars"),
                                "limit", McpTool.prop("integer", "page size, default "
                                        + DEFAULT_SCOUT_PAGE + ", max " + MAX_SCOUT_PAGE),
                                "offset", McpTool.prop("integer",
                                        "how many matching entries to skip, in shortlist order; "
                                                + "default 0"),
                                "bulk", McpTool.prop("boolean",
                                        "for scripts: every matching entry, full, as a JSON file to "
                                                + "download (HTTP GET of the returned download path on "
                                                + "the MCP endpoint); the answer keeps count and a 3-row "
                                                + "preview. Default false"))),
                        TransferTools::listScout)
                        .withBudget("Page with a smaller limit and offset, filter with skills, "
                                + "ageMin/ageMax, specialty or onlyOpen; pass full only with a small limit."),
                new McpTool("estimate_market_value",
                        "Value a squad player, or via profile any player, against stored "
                                + "listings: the estimate, a summary per A/B skill group, and every "
                                + "comparable as a download - reason over those, not the median. "
                                + "Below 3 sales, see resolvedSales + listingsEstimate. Searches "
                                + "today's market first and stores the hits (one of the session's "
                                + "capped searches); live: false reads the stored corpus only, free.",
                        McpTool.schema(McpTool.properties(
                                        "playerId", McpTool.prop("integer",
                                                "the player to value - a squad player id"),
                                        "profile", McpTool.prop("object",
                                                "value a hypothetical player instead of a squad "
                                                        + "player: {age, skills: {keeper|defending|"
                                                        + "playmaking|passing|winger|scoring -> "
                                                        + "level, at least one}, plus optional "
                                                        + "playerName, ageDays, specialty, setPieces, tsi, "
                                                        + "agreeability, aggressiveness, honesty}. "
                                                        + "Omitting specialty means unknown (no "
                                                        + "specialty filter applied, not the same as "
                                                        + "confirmed none) - pass specialty: 0 only "
                                                        + "when you actually know the player has none. "
                                                        + "Unset skills default to level 0. Provide "
                                                        + "playerId or profile, not both. Also "
                                                        + "optional: playerId (this player's real "
                                                        + "Hattrick id, e.g. from get_team_transfers "
                                                        + "or analyze_opponent - bookkeeping only, "
                                                        + "never used to look him up in the squad) "
                                                        + "and salePrice - given both, this profile "
                                                        + "is recorded into HO's local transfer-"
                                                        + "listing corpus as a real data point, so a "
                                                        + "player who left the squad without ever "
                                                        + "being tracked still teaches future "
                                                        + "valuations something."),
                                        "ageTolerance", McpTool.prop("integer", "age window in years, default 1"),
                                        "ageWindowDays", McpTool.prop("integer",
                                                "age window in days either side of the player's "
                                                        + "age (112 days a year); replaces ageTolerance. "
                                                        + "Comparables without age days are left out"),
                                        "primarySkillTolerance", McpTool.prop("integer",
                                                "window in levels for the best positional skill (A), "
                                                        + "default 0"),
                                        "skillTolerance", McpTool.prop("integer",
                                                "window in levels for the second skill (B), default 1"),
                                        "live", McpTool.prop("boolean",
                                                "run the comparable query against today's market "
                                                        + "first, store the hits, and estimate over "
                                                        + "the refreshed corpus; spends one of the "
                                                        + "session's transfer searches. Default true - "
                                                        + "pass false for a free, local-only read. When "
                                                        + "true only by this default (not passed "
                                                        + "explicitly) a used-up search cap is reported "
                                                        + "as liveSearchSkipped instead of failing the "
                                                        + "call; passed explicitly, it fails the call "
                                                        + "as before. liveSearch reports itemCount, "
                                                        + "hitCount and a resultId: page the hits with "
                                                        + "search_transfers resultId."),
                                        "specialtyFilter", McpTool.prop("boolean",
                                                "hard-filter the corpus to the target's exact specialty "
                                                        + "(the old behaviour; thin cells). Default false: "
                                                        + "all specialties are pooled and a comparable with "
                                                        + "a different or no specialty is down-weighted "
                                                        + "softly (weightBreakdown.specialty), same "
                                                        + "specialty weighs 1.0. An unknown specialty on "
                                                        + "either side is neutral."),
                                        "positionGroup", McpTool.prop("string",
                                                "optional override: keeper, back_line, winger, inner_mid or "
                                                        + "forward. Default: derived from the player's top "
                                                        + "positional skill and echoed as positionGroup / "
                                                        + "positionGroupDerived. Each specialty is scored "
                                                        + "0-1 for the position (e.g. inner_mid: Unpredictable "
                                                        + "1.0, Head 0.9, Powerful 0.7, Technical 0.6, Quick "
                                                        + "0.4, none 0.2; winger Unpredictable/Head/Quick; "
                                                        + "forward Unpredictable/Head/Quick/Technical; back_line "
                                                        + "Powerful/Technical/Quick; Head and Unpredictable "
                                                        + "at the back are liabilities) and a comparable's "
                                                        + "weight falls with the score gap to the target's "
                                                        + "specialty (1 - 0.7 x gap, floor 0.3). Scores are "
                                                        + "ordinal placeholders; weather is not modelled."),
                                        "observedWithinDays", McpTool.prop("integer",
                                                "ignore listings older than this many days, default "
                                                        + DEFAULT_CORPUS_DAYS + " (one Hattrick season)"),
                                        "includeCorpus", McpTool.prop("boolean",
                                                "add corpus: how much stored market data there is - "
                                                        + "listings by outcome (sold, open, "
                                                        + "noSaleFound, awaitingResolve), oldest/newest "
                                                        + "observation, and for the window counts by "
                                                        + "age, position group and specialty id with "
                                                        + "how many sold. Local and free; alone, without "
                                                        + "playerId or profile, it returns only corpus. "
                                                        + "Default false"))),
                        TransferTools::estimateMarketValue)
        );
    }

    // ------------------------------------------------------------------ market value

    /** One Hattrick season. Older listings priced a market that has moved on. */
    private static final int DEFAULT_CORPUS_DAYS = 112;

    // ------------------------------------------------------------------ corpus stats

    private static final List<String> AGE_BUCKETS = List.of("17", "18", "19", "20+", "unknown");
    private static final List<String> POSITION_GROUPS =
            List.of("keeper", "back_line", "inner_mid", "winger", "forward", "unknown");

    private static JsonObject marketCorpusStats(JsonObject args) {
        final var days = Math.max(McpJson.optInt(args, "observedWithinDays", DEFAULT_CORPUS_DAYS), 1);
        // ponytail: loads the whole table, since the position group is a Java heuristic; move the
        // counts to SQL GROUP BY if the corpus ever gets big enough for this to be slow.
        return corpusStats(DBManager.instance().loadAllTransferListings(), HODateTime.now(), days);
    }

    /**
     * The size of the corpus, per listing rather than per row: a listing seen in four searches is
     * ahead), noSaleFound (looked up after the deadline, no sale), awaitingResolve (deadline passed,
     * not looked up yet). suspectedLostSales counts the noSaleFound listings that ever showed a bid,
     * which in Hattrick means they sold. The window - what a valuation reads - is broken down by the
     * listing's newest skill-bearing observation; a listing only ever seen through currentbids has
     * no age or skills and counts as unknown.
     */
    static JsonObject corpusStats(List<TransferListing> rows, HODateTime now, int days) {
        final var since = new HODateTime(now.getInstant().minus(java.time.Duration.ofDays(days)));
        final var listings = new LinkedHashMap<String, List<TransferListing>>();
        HODateTime oldest = null;
        HODateTime newest = null;
        int windowObservations = 0;
        for (final var r : rows) {
            final var deadline = r.getDeadline() == null ? "" : r.getDeadline().toHT();
            listings.computeIfAbsent(r.getPlayerId() + "@" + deadline, k -> new ArrayList<>()).add(r);
            final var at = r.getObservedAt();
            if (at == null) {
                continue;
            }
            if (oldest == null || at.isBefore(oldest)) oldest = at;
            if (newest == null || newest.isBefore(at)) newest = at;
            if (!at.isBefore(since)) windowObservations++;
        }

        final var outcomes = new LinkedHashMap<String, Integer>();
        List.of("sold", "open", "noSaleFound", "awaitingResolve").forEach(k -> outcomes.put(k, 0));
        int suspectedLostSales = 0;
        int windowListings = 0;
        int windowSold = 0;
        int windowSuspected = 0;
        final var byAge = buckets(AGE_BUCKETS);
        final var byGroup = buckets(POSITION_GROUPS);
        final var bySpecialty = new JsonObject();
        final var newestFirst = Comparator.comparing(TransferListing::getObservedAt,
                Comparator.nullsFirst(Comparator.naturalOrder())).reversed();
        for (final var observations : listings.values()) {
            final var outcome = outcome(observations, now);
            outcomes.merge(outcome, 1, Integer::sum);
            // A Hattrick bid cannot be withdrawn: a listing that ever showed one sold. Filed
            // noSaleFound, it is a sale the resolver failed to match.
            final var suspect = outcome.equals("noSaleFound")
                    && observations.stream().anyMatch(o -> o.getHighestBid() > 0);
            if (suspect) suspectedLostSales++;
            if (observations.stream().noneMatch(o -> o.getObservedAt() != null && !o.getObservedAt().isBefore(since))) {
                continue;
            }
            final var isSold = outcome.equals("sold");
            windowListings++;
            if (isSold) windowSold++;
            if (suspect) windowSuspected++;
            final var rep = observations.stream().filter(o -> o.getPlaymaker() != null).min(newestFirst)
                    .orElse(observations.stream().min(newestFirst).orElseThrow());
            count(byAge, ageBucket(rep.getAge()), isSold, suspect);
            final var levels = listingSkillLevels(rep);
            count(byGroup, levels.containsValue(null) ? "unknown" : derivePositionGroup(levels), isSold, suspect);
            count(bySpecialty, rep.getSpecialty() == null ? "unknown" : rep.getSpecialty().toString(), isSold, suspect);
        }

        final var window = new JsonObject();
        window.addProperty("days", days);
        window.addProperty("observations", windowObservations);
        window.addProperty("listings", windowListings);
        window.addProperty("sold", windowSold);
        window.addProperty("suspectedLostSales", windowSuspected);
        window.add("byAge", byAge);
        window.add("byPositionGroup", byGroup);
        window.add("bySpecialty", bySpecialty);

        final var json = new JsonObject();
        json.addProperty("observations", rows.size());
        json.addProperty("listings", listings.size());
        outcomes.forEach(json::addProperty);
        json.addProperty("suspectedLostSales", suspectedLostSales);
        json.addProperty("oldestObservedAt", oldest == null ? null : oldest.toHT());
        json.addProperty("newestObservedAt", newest == null ? null : newest.toHT());
        json.add("window", window);
        return json;
    }

    /** sold, open, noSaleFound or awaitingResolve - see {@link #corpusStats}. */
    private static String outcome(List<TransferListing> observations, HODateTime now) {
        if (observations.stream().anyMatch(o -> o.getSalePrice() != null)) {
            return "sold";
        }
        final var deadline = observations.get(0).getDeadline();
        if (deadline != null && now.isBefore(deadline)) {
            return "open";
        }
        return observations.stream().anyMatch(o -> o.getResolvedAt() != null) ? "noSaleFound" : "awaitingResolve";
    }

    private static String ageBucket(Integer age) {
        if (age == null) return "unknown";
        if (age <= 17) return "17";
        return age <= 19 ? age.toString() : "20+";
    }

    private static JsonObject buckets(List<String> keys) {
        final var json = new JsonObject();
        for (final var key : keys) {
            json.add(key, emptyBucket());
        }
        return json;
    }

    private static JsonObject emptyBucket() {
        final var bucket = new JsonObject();
        bucket.addProperty("listings", 0);
        bucket.addProperty("sold", 0);
        bucket.addProperty("suspectedLostSales", 0);
        return bucket;
    }

    private static void count(JsonObject buckets, String key, boolean sold, boolean suspectedLostSale) {
        if (!buckets.has(key)) {
            buckets.add(key, emptyBucket());
        }
        final var bucket = buckets.getAsJsonObject(key);
        bucket.addProperty("listings", bucket.get("listings").getAsInt() + 1);
        if (sold) bucket.addProperty("sold", bucket.get("sold").getAsInt() + 1);
        if (suspectedLostSale) {
            bucket.addProperty("suspectedLostSales", bucket.get("suspectedLostSales").getAsInt() + 1);
        }
    }

    /**
     * Below this many resolved sales the observed listings are the better estimate, thin as they
     * are: one real sale is a single draw, twenty asking prices at least bracket the market.
     * Never blended: resolved sales are the truth, so below this they are listed on their own and
     * the listings figure stands apart - see {@link #basis}.
     */
    private static final int MIN_RESOLVED_SAMPLE = 3;

    /** A Hattrick season year is 112 days (ageDays runs 0-111) - see docs/current.md @ f5c51d0d. Used to turn
     * age + ageDays into one continuous day count, so a 23y111 and a 24y0 comparable are recognised
     * as a day apart rather than a full year. */
    private static final int HATTRICK_YEAR_DAYS = 112;

    /**
     * Gaussian sigma (in days) for the age-similarity kernel below, banded by the target player's
     * own age. A one-year age gap moves a trainee's value far more than a veteran's - Hattrick's own
     * transfer-search enforces a similar shape (a 1-year window cap at 17-18, 2 years from 19) - and
     * the peak/decline band (docs/transfer-searches.md's untrained-slot section) starts around 26-28,
     * past which age barely matters. This only sharpens the ordering *within* the hard age filter
     * above (comparableQuery already excludes anyone outside ageTolerance); it does not replace it.
     * ponytail: three discrete bands, not a smooth curve fit to real outcomes - there is no resolved-
     * sale history yet broken down by age to fit one against. Revisit once there is.
     */
    private static final int YOUNG_AGE_SIGMA_DAYS = 30;
    private static final int MID_AGE_SIGMA_DAYS = 75;
    private static final int VETERAN_AGE_SIGMA_DAYS = 150;
    private static final int YOUNG_AGE_CUTOFF = 19;
    private static final int VETERAN_AGE_CUTOFF = 26;

    /** Gaussian sigma (in skill levels) for the third-best-skill kernel - softer than the ±1-level
     * hard window comparableQuery already applies to the top two skills. */
    private static final double C_SKILL_SIGMA = 1.5;

    /** Gaussian sigma over the summed absolute gap across all three character traits (0-5 each).
     * Wide: character is public on any player's page (see "Character over MCP" above) so the market
     * could in principle price on it, but coverage is still thin, so this is deliberately a weak
     * signal - see comparableWeight's doc. */
    private static final double CHARACTER_SIGMA = 4.0;

    /** No comparable's combined weight may fall below this. A soft heuristic across three kernels
     * should narrow how much a row counts, never zero it out entirely - it still sold or was asked
     * for something. */
    private static final double WEIGHT_FLOOR = 0.05;

    /** How far a price may be winsorized from the sample's own plain median before the weighted
     * median is computed over it. The age/skill/character kernel only measures how similar a player
     * is, not whether his price is sane - a troll ask sitting close on every one of those axes would
     * still pass it undamped. Applied uniformly to whichever sample (sales or asks) ends up used;
     * the untouched range and comparables evidence still show the real observed spread. */
    private static final double WINSORIZE_BAND = 3.0;

    /**
     * How much an ask/bid snapshot is nudged up for time still left on the clock, standing in for a
     * real sale price when {@link #MIN_RESOLVED_SAMPLE} resolved sales are not available. A snapshot
     * taken early, with days left to bid, systematically undersells what a listing goes for.
     * ponytail: a single linear ramp fit to four data points (Bulnys 59k->152k, Knop 100k->273k,
     * Pritchin 204k->466k, Außerbauer 266k->396k, a 1.5x-2.7x spread) the hattrick peer session
     * collected from its own scouting, not a real regression - revisit once enough resolved sales
     * exist in the corpus to calibrate against actual outcomes instead of a handful of anecdotes.
     * Capped at 1.5x (the anecdotes spread to 2.7x) until then, so an uncalibrated guess cannot
     * double a price.
     */
    private static final double FALLBACK_MAX_CLIMB = 0.5;
    private static final java.time.Duration FALLBACK_FULL_WINDOW = java.time.Duration.ofDays(5);

    /** exp(-diff^2 / (2*sigma^2)): 1.0 at diff=0, decaying smoothly as diff grows past sigma. */
    private static double gaussianWeight(double diff, double sigma) {
        return Math.exp(-(diff * diff) / (2 * sigma * sigma));
    }

    /** Two decimal places - plenty for a weight in [0,1], too fine to matter beyond that. */
    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private static int totalAgeDays(int age, int ageDays) {
        return age * HATTRICK_YEAR_DAYS + ageDays;
    }

    static double ageSigmaDays(int ageYears) {
        if (ageYears < YOUNG_AGE_CUTOFF) {
            return YOUNG_AGE_SIGMA_DAYS;
        }
        return ageYears < VETERAN_AGE_CUTOFF ? MID_AGE_SIGMA_DAYS : VETERAN_AGE_SIGMA_DAYS;
    }

    /** ponytail: a guessed floor (~EUR 1,000) against joke asks - an ask/bid below it is dropped from listings. */
    static final long MIN_PLAUSIBLE_PRICE_SEK = 10_000;

    /**
     * A profile must name its third-best positional skill (an explicit 0 counts). Omitted, it is
     * neither a real 0 nor safely "unknown": as 0 it floors every comparable's weight, as unknown
     * it lets all-rounders with a strong third skill and far higher prices count fully - both
     * inflate the estimate. Checked before the record-a-sale path too, so a two-skill profile never
     * enters the corpus with fabricated zeros either. A keeper profile is exempt: his outfield
     * skills are noise, see comparableQuery.
     */
    static void requireThirdSkill(JsonObject profile) throws McpToolException {
        final var skills = profile.getAsJsonObject("skills");
        if (skills.size() >= 3) {
            return;
        }
        final var top = skills.entrySet().stream()
                .max(Comparator.comparingInt((Map.Entry<String, com.google.gson.JsonElement> e) -> e.getValue().getAsInt()))
                .map(Map.Entry::getKey).orElse("");
        if (!"keeper".equals(top)) {
            throw new McpToolException("profile.skills needs the third-best positional skill too (0 "
                    + "counts) - without it all-rounders inflate the estimate.");
        }
    }

    /** The third-highest value, or null if fewer than three non-null values are present - a listing
     * with no skill data at all (a bid-only observation) rather than a real, low third skill. */
    static Integer thirdHighestOrNull(java.util.Collection<Integer> levels) {
        final var sorted = levels.stream().filter(java.util.Objects::nonNull)
                .sorted(Comparator.reverseOrder()).toList();
        return sorted.size() >= 3 ? sorted.get(2) : null;
    }

    /**
     * The independent similarity kernels {@link #comparableWeight} multiplies together, kept
     * separate so a caller can see *why* a comparable was down-weighted rather than only by how much.
     */
    record WeightBreakdown(double age, double skill, double character, double specialty) {
        /** The combined weight {@link #comparableWeight} returns - floored at {@link #WEIGHT_FLOOR}. */
        double combined() {
            return Math.max(WEIGHT_FLOOR, age * skill * character * specialty);
        }
    }

    /** Weight of a comparable whose specialty differs from the target's when no position is known - soft, not a filter. */
    static final double SPECIALTY_MISMATCH = 0.6;
    /** With a position: weight lost per 1.0 of score gap between the two specialties. */
    static final double SPECIALTY_SCORE_SLOPE = 0.7;
    static final double SPECIALTY_FLOOR = 0.3;
    private static final double UNKNOWN_SPECIALTY_SCORE = 0.3;

    /**
     * How good each specialty is for a position, 0 (liability) - 1 (perfect). The ORDER comes from the
     * team's buy tables and the wiki; the spacing is an ordinal placeholder, not fitted to prices - tune
     * against resolved sales. Args are in specialty-id order: none, Technical, Quick, Powerful,
     * Unpredictable, Head, Resilient (6), Support (8).
     */
    private static final Map<String, Map<Integer, Double>> POSITION_SCORE = Map.of(
            "back_line", specialtyScores(0.3, 0.8, 0.6, 1.0, 0.2, 0.25, 0.4, 0.1),
            "winger", specialtyScores(0.2, 0.4, 0.8, 0.4, 1.0, 0.9, 0.4, 0.1),
            "inner_mid", specialtyScores(0.2, 0.6, 0.4, 0.7, 1.0, 0.9, 0.4, 0.1),
            "forward", specialtyScores(0.2, 0.7, 0.85, 0.6, 1.0, 0.95, 0.4, 0.1),
            "keeper", specialtyScores(0.5, 0.4, 0.6, 0.4, 0.3, 0.1, 0.4, 0.1));

    private static Map<Integer, Double> specialtyScores(double none, double technical, double quick,
                                                        double powerful, double unpredictable, double head,
                                                        double resilient, double support) {
        return Map.of(PlayerSpeciality.NO_SPECIALITY, none, PlayerSpeciality.TECHNICAL, technical,
                PlayerSpeciality.QUICK, quick, PlayerSpeciality.POWERFUL, powerful,
                PlayerSpeciality.UNPREDICTABLE, unpredictable, PlayerSpeciality.HEAD, head,
                PlayerSpeciality.REGAINER, resilient, PlayerSpeciality.SUPPORT, support);
    }

    /**
     * The position a player is valued as when the caller gave no positionGroup: his top positional
     * skill decides (keeper -> keeper, defending -> back_line, scoring -> forward, winger -> winger,
     * playmaking or passing -> inner_mid). {@code levels} iterates in POSITIONAL_SKILLS order and the
     * first maximum wins, so a playmaking/winger tie goes to inner_mid - the trained slot.
     * ponytail: top-skill heuristic; a mixed profile (PM 6, winger 6) is a coin flip, callers override.
     */
    static String derivePositionGroup(Map<String, Integer> levels) {
        String top = "playmaking";
        int best = -1;
        for (final var e : levels.entrySet()) {
            if (e.getValue() > best) {
                best = e.getValue();
                top = e.getKey();
            }
        }
        return switch (top) {
            case "keeper" -> "keeper";
            case "defending" -> "back_line";
            case "scoring" -> "forward";
            case "winger" -> "winger";
            default -> "inner_mid";
        };
    }

    /** Validates a caller's positionGroup (null passes through) and returns it. */
    static String positionGroupOrNull(JsonObject args) throws McpToolException {
        if (!args.has("positionGroup") || args.get("positionGroup").isJsonNull()) {
            return null;
        }
        final var group = args.get("positionGroup").getAsString();
        if (!POSITION_SCORE.containsKey(group)) {
            throw new McpToolException("positionGroup must be one of " + new java.util.TreeSet<>(POSITION_SCORE.keySet())
                    + ", got '" + group + "'.");
        }
        return group;
    }

    /**
     * 1.0 for the same specialty, or when either is unknown (null - a bid-only observation, or a
     * profile that never gave one). Otherwise {@link #SPECIALTY_MISMATCH} without a positionGroup; with
     * one, the gap between how good each specialty is for that position ({@link #POSITION_SCORE})
     * sets the weight: 1 - {@link #SPECIALTY_SCORE_SLOPE} x gap, never below {@link #SPECIALTY_FLOOR}.
     * Weather is deliberately not modelled - the hattrick session's rules treat it as a +-5% tiebreak.
     */
    static double specialtyWeight(Integer listingSpecialty, Integer targetSpecialty, String positionGroup) {
        if (listingSpecialty == null || targetSpecialty == null || listingSpecialty.equals(targetSpecialty)) {
            return 1.0;
        }
        if (positionGroup == null) {
            return SPECIALTY_MISMATCH;
        }
        final var scores = POSITION_SCORE.get(positionGroup);
        final var gap = Math.abs(scores.getOrDefault(listingSpecialty, UNKNOWN_SPECIALTY_SCORE)
                - scores.getOrDefault(targetSpecialty, UNKNOWN_SPECIALTY_SCORE));
        return Math.max(SPECIALTY_FLOOR, 1.0 - SPECIALTY_SCORE_SLOPE * gap);
    }

    /**
     * The age/third-skill/character/specialty similarity kernels for one comparable, each defaulting to a
     * neutral 1.0 when the listing lacks the data to judge it rather than penalising the gap - a bid-
     * only observation has no skills at all, and most of the corpus still has no character (see the
     * backfill_character work in git show f5c51d0d:docs/current.md).
     */
    static WeightBreakdown comparableWeightBreakdown(TransferListing listing, int targetAgeDays,
                                            double ageSigma, Integer targetCSkill, int[] targetChar) {
        return comparableWeightBreakdown(listing, targetAgeDays, ageSigma, targetCSkill, targetChar, null, null);
    }

    static WeightBreakdown comparableWeightBreakdown(TransferListing listing, int targetAgeDays,
                                            double ageSigma, Integer targetCSkill, int[] targetChar,
                                            Integer targetSpecialty, String positionGroup) {
        final var listingAgeDays = totalAgeDays(listing.getAge(),
                listing.getAgeDays() == null ? 0 : listing.getAgeDays());
        final var ageWeight = gaussianWeight(Math.abs(listingAgeDays - targetAgeDays), ageSigma);

        final var listingCSkill = thirdHighestOrNull(listingSkillLevels(listing).values());
        final var skillWeight = listingCSkill == null || targetCSkill == null ? 1.0
                : gaussianWeight(Math.abs(listingCSkill - targetCSkill), C_SKILL_SIGMA);

        final var charWeight = listing.getAgreeability() == null || targetChar == null ? 1.0
                : gaussianWeight(Math.abs(listing.getAgreeability() - targetChar[0])
                        + Math.abs(listing.getAggressiveness() - targetChar[1])
                        + Math.abs(listing.getHonesty() - targetChar[2]), CHARACTER_SIGMA);

        return new WeightBreakdown(ageWeight, skillWeight, charWeight,
                specialtyWeight(listing.getSpecialty(), targetSpecialty, positionGroup));
    }

    /** How much this comparable should count toward the weighted median - see {@link WeightBreakdown}. */
    static double comparableWeight(TransferListing listing, int targetAgeDays,
                                            double ageSigma, Integer targetCSkill, int[] targetChar) {
        return comparableWeightBreakdown(listing, targetAgeDays, ageSigma, targetCSkill, targetChar).combined();
    }

    /**
     * A snapshot ask/bid's price, nudged up for time still left on the clock - see
     * {@link #FALLBACK_MAX_CLIMB}'s doc. 1.0 (no change) once the deadline has passed or is unknown.
     */
    static double fallbackClimb(TransferListing listing) {
        if (listing.getDeadline() == null) {
            return 1.0;
        }
        final var remaining = java.time.Duration.between(
                observedAt(listing).getInstant(), listing.getDeadline().getInstant());
        if (remaining.isNegative() || remaining.isZero()) {
            return 1.0;
        }
        final var fraction = Math.min(
                (double) remaining.toMinutes() / FALLBACK_FULL_WINDOW.toMinutes(), 1.0);
        return 1.0 + FALLBACK_MAX_CLIMB * fraction;
    }

    /** One comparable's contribution: its (possibly fallback-adjusted) price and its weight. */
    record PricedComparable(TransferListing listing, long price, double weight) {}

    /** One comparable as the download lists it; {@code sampled} is its entry in the estimate's sample, or null. */
    record EvidenceRow(TransferListing listing, JsonObject json, double weight, PricedComparable sampled) {}

    /**
     * A listing's A and B skill - its best and second best positional skill, ties by name the way
     * {@link #topSkillsOnly} orders them - as {@code "playmaking/passing"}.
     */
    static String skillPair(Map<String, Integer> levels) {
        final var top = levels.entrySet().stream()
                .filter(e -> e.getValue() != null)
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed()
                        .thenComparing(Map.Entry.comparingByKey()))
                .limit(2)
                .map(Map.Entry::getKey)
                .toList();
        return (top.isEmpty() ? "unknown" : top.get(0)) + "/" + (top.size() < 2 ? "unknown" : top.get(1));
    }

    /**
     * aSkill, bSkill, count, sold, inSample and weightedMedianPrice of one skill group. The median
     * is the estimate's own, restricted to the group: only its rows in the estimate's sample (sales
     * when there are enough, else the climb-adjusted asks), same weights, same outlier clamp - see
     * {@link #clampedWeightedMedian}. Null when none of the group's rows are in the sample.
     */
    static JsonObject skillGroupSummary(String pair, List<EvidenceRow> rows) {
        final var skills = pair.split("/");
        final var json = new JsonObject();
        json.addProperty("aSkill", skills[0]);
        json.addProperty("bSkill", skills[1]);
        json.addProperty("count", rows.size());
        json.addProperty("sold", rows.stream().filter(r -> r.listing().getSalePrice() != null).count());
        final var sampled = rows.stream()
                .map(EvidenceRow::sampled)
                .filter(java.util.Objects::nonNull)
                .sorted(Comparator.comparingLong(PricedComparable::price))
                .toList();
        json.addProperty("inSample", sampled.size());
        json.addProperty("weightedMedianPrice", sampled.isEmpty() ? null : McpJson.money(clampedWeightedMedian(sampled)));
        return json;
    }


    /**
     * The value where cumulative weight first reaches half the total - the weighted analogue of a
     * plain median. {@code pairs} must already be sorted by price ascending.
     */
    static long weightedMedian(List<double[]> pairs) {
        final var totalWeight = pairs.stream().mapToDouble(p -> p[1]).sum();
        var cumulative = 0.0;
        for (final var pair : pairs) {
            cumulative += pair[1];
            if (cumulative >= totalWeight / 2.0) {
                return Math.round(pair[0]);
            }
        }
        return Math.round(pairs.get(pairs.size() - 1)[0]);
    }

    /**
     * The weighted median over {@code sample}, winsorized against a wild outlier first - see
     * {@link #WINSORIZE_BAND}. The clamp band is anchored on this same weighted median, computed
     * once unclamped, rather than the sample's plain median: a plain-median anchor lets a single
     * poorly-matched comparable (low weight, wild price) shift the band enough to distort even a
     * dominant, well-matched comparable's own price before weighting is ever applied. {@code
     * sample} must already be sorted by price ascending, as {@link #weightedMedian} requires.
     */
    static long clampedWeightedMedian(List<PricedComparable> sample) {
        final var rawPairs = sample.stream()
                .map(pc -> new double[]{(double) pc.price(), pc.weight()})
                .toList();
        final var anchor = weightedMedian(rawPairs);
        final var lo = anchor / WINSORIZE_BAND;
        final var hi = anchor * WINSORIZE_BAND;
        final var clampedPairs = sample.stream()
                .map(pc -> new double[]{Math.max(lo, Math.min(hi, (double) pc.price())), pc.weight()})
                .toList();
        return weightedMedian(clampedPairs);
    }


    /**
     * "sales" at {@link #MIN_RESOLVED_SAMPLE} or more resolved sales (they alone make the estimate),
     * "thin_sales_plus_listings" below it (sales listed apart, a separate listings figure), or
     * "listings" with none at all. Never a blend: a resolved sale is the truth, an ask is not.
     */
    static String basis(int resolvedSales) {
        if (resolvedSales >= MIN_RESOLVED_SAMPLE) {
            return "sales";
        }
        return resolvedSales == 0 ? "listings" : "thin_sales_plus_listings";
    }

    /** ponytail: placeholder thresholds, not fitted to prices - tune against resolved sales. */
    static final int SET_PIECES_GAP_NOTE = 4;
    static final int SET_PIECES_HIGH_NOTE = 12;

    /**
     * A warning, never an adjustment: set pieces is kept out of the estimate (one specialist taker
     * per team is what it is worth to us), but the market does pay for a high-SP player, so the
     * median can be off when the target is one or the comparables sit far from him. Null when
     * neither applies or the target's SP is unknown; comparables without SP are skipped.
     */
    static String setPiecesNote(Integer targetSetPieces, List<Integer> comparableSetPieces) {
        if (targetSetPieces == null) {
            return null;
        }
        final var known = comparableSetPieces.stream().filter(java.util.Objects::nonNull).toList();
        final var farOff = known.stream()
                .filter(sp -> Math.abs(sp - targetSetPieces) >= SET_PIECES_GAP_NOTE).count();
        final var high = targetSetPieces >= SET_PIECES_HIGH_NOTE;
        if (!high && farOff == 0) {
            return null;
        }
        return "Set pieces is not part of the estimate. Target SP " + targetSetPieces
                + (high ? " - a set-pieces specialist, which the market pays a premium for" : "")
                + "; " + farOff + " of " + known.size() + " comparables with known SP are "
                + SET_PIECES_GAP_NOTE + "+ levels away. The number may be off - check their setPieces.";
    }

    /** The target's set pieces: a squad player's own, a profile's only when it gives setPieces. */
    static Integer targetSetPieces(boolean hasProfile, JsonObject profile, Player player) {
        if (!hasProfile) {
            return player.getValue4Skill(core.constants.player.PlayerSkill.SETPIECES);
        }
        return profile.has("setPieces") && !profile.get("setPieces").isJsonNull()
                ? profile.get("setPieces").getAsInt() : null;
    }

    static JsonObject estimateMarketValue(JsonObject args) throws McpToolException {
        final var withCorpus = McpJson.optBool(args, "includeCorpus", false);
        final var hasPlayer = args.has("playerId") && !args.get("playerId").isJsonNull()
                || args.has("profile") && !args.get("profile").isJsonNull();
        final var json = withCorpus && !hasPlayer ? new JsonObject() : valuation(args);
        if (withCorpus) {
            json.add("corpus", marketCorpusStats(args));
        }
        return json;
    }

    private static JsonObject valuation(JsonObject args) throws McpToolException {
        final var hasPlayerId = args.has("playerId") && !args.get("playerId").isJsonNull();
        final var hasProfile = args.has("profile") && !args.get("profile").isJsonNull();
        if (!hasPlayerId && !hasProfile) {
            throw new McpToolException(
                    "Provide either playerId (a squad player) or profile (age + skills) to value.");
        }
        if (hasPlayerId && hasProfile) {
            throw new McpToolException("Provide playerId or profile, not both.");
        }

        final JsonObject profile = hasProfile ? args.getAsJsonObject("profile") : null;
        final Player player;
        if (hasProfile) {
            player = playerFromProfile(profile);
            requireThirdSkill(profile);
        } else {
            final var playerId = McpJson.reqInt(args, "playerId");
            player = McpJson.model().getCurrentPlayer(playerId);
            if (player == null) {
                throw new McpToolException("No player with id " + playerId + " is in the squad. "
                        + "Call get_squad for the ids HO knows about.");
            }
        }
        // A profile with both playerId and salePrice is a real, already-known data point (the
        // motivating case: a player sold this season, gone from the squad, that HO never tracked
        // as a listing) - record it once so it teaches future valuations something too, rather
        // than being spent and discarded on this one call.
        final var recordedToCorpus = hasProfile && recordProfileObservation(profile, player);
        final var requestedGroup = positionGroupOrNull(args);
        final var hardSpecialtyFilter = args.has("specialtyFilter") && args.get("specialtyFilter").getAsBoolean();
        final var targetSpecialty = corpusSpecialtyFilter(hasProfile, profile, player);
        // Unset: the age window stays the whole years of ageTolerance. Negative is refused, not
        // clamped - a window that silently became 0 days would match next to nobody.
        final Integer ageWindowDays = args.has("ageWindowDays") ? McpJson.optInt(args, "ageWindowDays", 0) : null;
        if (ageWindowDays != null && ageWindowDays < 0) {
            throw new McpToolException("ageWindowDays must be 0 or more.");
        }
        final var query = comparableQuery(player,
                McpJson.optInt(args, "ageTolerance", 1),
                McpJson.optInt(args, "primarySkillTolerance", 0),
                McpJson.optInt(args, "skillTolerance", 1),
                ageWindowDays);
        final var days = Math.max(McpJson.optInt(args, "observedWithinDays", DEFAULT_CORPUS_DAYS), 1);
        final var since = new HODateTime(HODateTime.now().getInstant()
                .minus(java.time.Duration.ofDays(days)));

        final var live = runLiveSearch(args, query);
        final var liveSearch = live.hits();
        final var liveSearchSkipped = live.skippedNote();

        // Specialty is a soft weight by default (see specialtyWeight); the corpus is only hard-filtered
        // to it when the caller passes specialtyFilter: true. corpusSpecialtyFilter, not player.getSpecialty():
        // definitively known, but a profile that never mentions specialty could mean either "HO
        // never got a reading for him" (a departed player, most of the time) or "confirmed none" -
        // conflating those two into player's own 0 default would wrongly exclude specialty-having
        // comparables for a player whose specialty is simply unknown, not none. The live CHPP
        // search above asks for any specialty (comparableQuery sets none - see its doc), so it
        // cannot skew the corpus toward the target's own; only this local read can filter, and only
        // on request - see loadComparables's doc for the null/0/positive distinction.
        final var observations = DBManager.instance().loadComparableTransferListings(
                query.ageMin(), query.ageMax(),
                column(query.skillType1()), query.minSkillValue1(), query.maxSkillValue1(),
                query.skillType2() == null ? null : column(query.skillType2()),
                query.skillType2() == null ? 0 : query.minSkillValue2(),
                query.skillType2() == null ? 0 : query.maxSkillValue2(),
                hardSpecialtyFilter ? targetSpecialty : null, since);


        // One vote per player. The corpus appends on every observation, so a player who turned up
        // in four searches is four rows: without this he moves the median four times and
        // sampleSize counts rows rather than the players a caller reads it as. Keep his sale where
        // we know it, else his newest observation, and hand the list over newest-first.
        //
        // Then dropped unless his OWN top two skills are the same pair queried for - the DB window
        // above matches skill1/skill2 as two independent bands, so a player of a completely
        // different archetype (his primary at skill2's level, secondary at skill1's) can otherwise
        // enter the pool and count at floor weight. See sameArchetype's doc.
        final var comparables = observations.stream()
                .collect(Collectors.toMap(TransferListing::getPlayerId, l -> l,
                        TransferTools::betterObservation))
                .values().stream()
                .filter(l -> sameArchetype(l, query.skillType1(), query.skillType2()))
                .filter(l -> ageWindowDays == null
                        || withinAgeWindow(l, totalAgeDays(player.getAge(), player.getAgeDays()), ageWindowDays))
                .sorted(Comparator.comparing(TransferTools::observedAt).reversed())
                .toList();

        // The target's own features, computed once and shared by every comparable's weight below.
        final var playerLevels = playerSkillLevels(player);
        final var playerCSkill = thirdHighestOrNull(playerLevels.values());
        final var positionGroup = requestedGroup != null ? requestedGroup : derivePositionGroup(playerLevels);
        final var playerAgeDays = totalAgeDays(player.getAge(), player.getAgeDays());
        final var hasFullCharacter = !hasProfile
                || (profile.has("agreeability") && profile.has("aggressiveness") && profile.has("honesty"));
        final var playerChar = hasFullCharacter
                ? new int[]{player.getGentleness(), player.getAggressivity(), player.getHonesty()}
                : null;
        final var ageSigma = ageSigmaDays(player.getAge());

        final var salesPriced = comparables.stream()
                .filter(l -> l.getSalePrice() != null)
                .map(l -> new PricedComparable(l, l.getSalePrice(),
                        comparableWeightBreakdown(l, playerAgeDays, ageSigma, playerCSkill, playerChar,
                                targetSpecialty, positionGroup).combined()))
                .sorted(Comparator.comparingLong(PricedComparable::price))
                .toList();
        // An expired listing keeps its last observed state, so the highest bid is the better
        // reading of what it was worth whenever bidding got past the asking price.
        final var asksPriced = comparables.stream()
                .filter(l -> l.getSalePrice() == null)
                .map(l -> new PricedComparable(l, Math.max(l.getAskingPrice(), l.getHighestBid()),
                        comparableWeightBreakdown(l, playerAgeDays, ageSigma, playerCSkill, playerChar,
                                targetSpecialty, positionGroup).combined()))
                .filter(pc -> pc.price() >= MIN_PLAUSIBLE_PRICE_SEK)
                .sorted(Comparator.comparingLong(PricedComparable::price))
                .toList();

        final var useSales = salesPriced.size() >= MIN_RESOLVED_SAMPLE;
        // Only the ask/bid fallback gets the climb adjustment - a resolved sale already is what it
        // went for, nothing to nudge.
        final var sample = useSales ? salesPriced
                : asksPriced.stream()
                        .map(pc -> new PricedComparable(pc.listing(),
                                Math.round(pc.price() * fallbackClimb(pc.listing())), pc.weight()))
                        .sorted(Comparator.comparingLong(PricedComparable::price))
                        .toList();

        final var json = new JsonObject();
        json.addProperty("currency", McpJson.currency());
        // The player being valued, so the comparison is self-contained in one call.
        json.add("player", hasProfile
                ? profilePlayerJson(profile)
                : topSkillsOnly(McpJson.player(player), playerLevels));
        if (hasProfile) {
            // True only when profile carried both playerId and salePrice - see
            // recordProfileObservation's doc for why the other cases stay false rather than
            // erroring, and for what "recorded" actually writes.
            json.addProperty("recordedToCorpus", recordedToCorpus);
        }
        json.addProperty("basis", basis(salesPriced.size()));
        json.addProperty("sampleSize", sample.size());
        json.addProperty("resolvedSalesAvailable", salesPriced.size());
        json.addProperty("observedListingsAvailable", asksPriced.size());
        json.addProperty("comparableQuery", query.toQueryString());
        // Which position the specialty fit was judged for, and whether the caller said so or it was
        // read off the player's top skill - see derivePositionGroup.
        json.addProperty("positionGroup", positionGroup);
        json.addProperty("positionGroupDerived", requestedGroup == null);
        json.addProperty("observedWithinDays", days);
        if (ageWindowDays != null) {
            json.addProperty("ageWindowDays", ageWindowDays);
        }
        if (liveSearch != null) {
            // The raw hits beside the corpus view of them: itemCount says how much of the market
            // matched, which the deduplicated comparables list below can no longer tell you.
            json.add("liveSearch", liveSearch);
        }
        if (liveSearchSkipped != null) {
            // Only ever set when live was defaulted, not asked for - see the comment above.
            json.addProperty("liveSearchSkipped", liveSearchSkipped);
        }

        // The evidence, not just the summary. The median below is a crude anchor: it collapses
        // wage, TSI, form, the other skills and how long ago each was seen, all of which change
        // what a player is worth. Reason over these rows instead of trusting the number.
        //
        // Every comparable goes into a download, never into the answer, whatever its size: grouped
        // by A skill (best positional skill) and B skill (second best), so a playmaking-led and a
        // passing-led player read apart. The answer keeps one summary line per group.
        // weight is the combined similarity weight the estimate was computed with; weightBreakdown
        // says which of age/skill/character/specialty - each 0-1, 1 meaning no gap - pulled it down.
        // adjustedPrice is what the row contributes: the sale price unchanged, or an ask/bid nudged
        // by fallbackClimb for time still left on the clock. inSample: the estimate used this row.
        final var sampleByListing = sample.stream()
                .collect(Collectors.toMap(PricedComparable::listing, pc -> pc, (a, b) -> a));
        final var groups = new LinkedHashMap<String, List<EvidenceRow>>();
        for (final var l : comparables) {
            final var breakdown = comparableWeightBreakdown(
                    l, playerAgeDays, ageSigma, playerCSkill, playerChar, targetSpecialty, positionGroup);
            final var row = McpJson.listing(l);
            row.addProperty("weight", round2(breakdown.combined()));
            final var weightBreakdown = new JsonObject();
            weightBreakdown.addProperty("age", round2(breakdown.age()));
            weightBreakdown.addProperty("skill", round2(breakdown.skill()));
            weightBreakdown.addProperty("character", round2(breakdown.character()));
            weightBreakdown.addProperty("specialty", round2(breakdown.specialty()));
            row.add("weightBreakdown", weightBreakdown);
            final var rawPrice = l.getSalePrice() != null ? l.getSalePrice()
                    : Math.max(l.getAskingPrice(), l.getHighestBid());
            final var adjustedPrice = l.getSalePrice() != null ? rawPrice
                    : Math.round(rawPrice * fallbackClimb(l));
            row.addProperty("adjustedPrice", McpJson.money(adjustedPrice));
            row.addProperty("inSample", sampleByListing.containsKey(l));
            groups.computeIfAbsent(skillPair(listingSkillLevels(l)), k -> new ArrayList<>())
                    .add(new EvidenceRow(l, row, breakdown.combined(), sampleByListing.get(l)));
        }
        final var summary = new JsonArray();
        final var fileGroups = new JsonArray();
        groups.entrySet().stream()
                .sorted(Comparator.comparingDouble(e -> -e.getValue().stream().mapToDouble(EvidenceRow::weight).sum()))
                .forEach(e -> {
                    final var head = skillGroupSummary(e.getKey(), e.getValue());
                    summary.add(head);
                    final var group = head.deepCopy();
                    final var rows = new JsonArray();
                    e.getValue().stream()
                            .sorted(Comparator.comparingDouble(EvidenceRow::weight).reversed())
                            .forEach(r -> rows.add(r.json()));
                    group.add("comparables", rows);
                    fileGroups.add(group);
                });
        json.add("skillGroups", summary);
        json.addProperty("comparablesMatched", comparables.size());
        // How many rows those distinct players were collapsed from, so the gap is visible.
        json.addProperty("observationsMatched", observations.size());
        final var file = new JsonObject();
        file.addProperty("currency", McpJson.currency());
        file.add("player", json.get("player").deepCopy());
        file.addProperty("positionGroup", positionGroup);
        file.add("skillGroups", fileGroups);
        McpDownloads.attach(json, file);

        if (!useSales) {
            // Resolved sales are the truth: below MIN_RESOLVED_SAMPLE they are neither blended with
            // asks nor dropped, but listed one by one beside the listings figure.
            final var sales = new JsonArray();
            salesPriced.forEach(pc -> sales.add(
                    topSkillsOnly(McpJson.listing(pc.listing()), listingSkillLevels(pc.listing()))));
            json.add("resolvedSales", sales);
        }
        if (sample.isEmpty()) {
            json.add("estimate", com.google.gson.JsonNull.INSTANCE);
            json.addProperty("note", (salesPriced.isEmpty() ? "Nothing comparable in the corpus yet."
                    : "Only the resolvedSales above, too few for an estimate, and no listings.")
                    + " Widen primarySkillTolerance / skillTolerance / ageTolerance (or ageWindowDays),"
                    + " or run search_transfers over this profile - "
                    + "every hit it returns is stored and becomes a comparable.");
            return json;
        }

        // Winsorized against the sample's own weighted center before the weighted median is
        // computed - see clampedWeightedMedian's doc. The reported range below is the real,
        // un-clamped spread.
        final var figure = McpJson.money(clampedWeightedMedian(sample));
        final var range = new JsonArray();
        range.add(McpJson.money(sample.get(0).price()));
        range.add(McpJson.money(sample.get(sample.size() - 1).price()));
        if (useSales) {
            json.addProperty("estimate", figure);
            json.add("range", range);
        } else {
            // Never in `estimate`: a figure built from asks and bids must not pass for a sale price.
            json.add("estimate", com.google.gson.JsonNull.INSTANCE);
            final var listingsEstimate = new JsonObject();
            listingsEstimate.addProperty("value", figure);
            listingsEstimate.add("range", range);
            listingsEstimate.addProperty("label", "listings only, not sale prices - approximate");
            json.add("listingsEstimate", listingsEstimate);
            json.addProperty("note", (salesPriced.isEmpty()
                    ? "No resolved sale prices for this profile yet."
                    : salesPriced.size() + " resolved sale(s) is too thin for an estimate - they are "
                    + "listed in resolvedSales, read them first.")
                    + " listingsEstimate is what sellers asked and bidders bid, nudged up (at most "
                    + "1.5x) for time still left on the clock when each was observed - an "
                    + "approximation, not what players went for.");
        }
        json.addProperty("estimateNote", "Weighted median over " + sample.size() + " comparables - "
                + "weighted by closeness in age (day precision), third-best skill, character "
                + "and specialty where known (a different specialty is down-weighted, not "
                + "excluded - see specialtyFilter/positionGroup), and clamped against a wild outlier - as a starting anchor only. It "
                + "still ignores wage, TSI, form, set pieces and how stale each observation is. The "
                + "download carries those per comparable, grouped by A/B skill, plus each row's own "
                + "weight (weightBreakdown says which of age/skill/character/specialty pulled it "
                + "down) and adjustedPrice (what it actually contributed, after the time-left climb "
                + "where that applies) - check the number instead of trusting it.");
        final var spNote = setPiecesNote(targetSetPieces(hasProfile, profile, player),
                comparables.stream().map(TransferListing::getSetPieces).toList());
        if (spNote != null) {
            json.addProperty("setPiecesNote", spNote);
        }
        return json;
    }

    /**
     * The specialty argument for {@link DBManager#loadComparableTransferListings} - see the caller's
     * comment for why this can't just be {@code player.getSpecialty()} in profile mode. A squad
     * player's specialty is read off {@code player} (always definitive - HO knows it or it is
     * genuinely none). A profile's is read off {@code profile} directly, not off the synthetic
     * {@code player} {@link #playerFromProfile} built (which already collapsed "never given" into
     * a 0 default for its own weighting math): present (0 included) is confirmed, absent is
     * unknown and passes {@code null} - no filter, same as the corpus has always done for data it
     * does not have an opinion on.
     */
    static Integer corpusSpecialtyFilter(boolean hasProfile, JsonObject profile, Player player) {
        if (!hasProfile) {
            return player.getSpecialty();
        }
        return profile.has("specialty") ? profile.get("specialty").getAsInt() : null;
    }

    /** {@link #liveSearchSummary} of the search (or null), and null unless a search-cap refusal was swallowed. */
    private record LiveSearchOutcome(JsonObject hits, String skippedNote) {}

    /**
     * The live search as a count and a resultId: the hits themselves page through
     * search_transfers resultId, where up to 100 full hits cannot blow the response budget.
     */
    private static JsonObject liveSearchSummary(XMLTransferSearchParser.Page page) {
        final var json = new JsonObject();
        json.addProperty("itemCount", page.itemCount());
        json.addProperty("hitCount", page.hits().size());
        json.addProperty("resultId", keepResult(page.hits()));
        return json;
    }

    /**
     * The live comparable search {@code estimateMarketValue} folds in before reading the local
     * corpus - honouring "live"'s default-true/explicit-true distinction (see the tool's own
     * schema doc): an explicit {@code live: true} lets a search-cap refusal fail the call, since
     * the caller knowingly spent it; defaulted-on swallows that same refusal into
     * {@code skippedNote} instead, since a caller who never asked for it should still get their
     * free, local-only estimate rather than a hard failure.
     */
    private static LiveSearchOutcome runLiveSearch(JsonObject args, TransferSearchQuery query)
            throws McpToolException {
        if (!McpJson.optBool(args, "live", true)) {
            return new LiveSearchOutcome(null, null);
        }
        final var explicit = args.has("live") && !args.get("live").isJsonNull();
        if (explicit) {
            return new LiveSearchOutcome(liveSearchSummary(search(query)), null);
        }
        try {
            return new LiveSearchOutcome(liveSearchSummary(search(query)), null);
        } catch (McpToolException e) {
            return new LiveSearchOutcome(null, e.getMessage());
        }
    }

    /**
     * A synthetic {@link Player} built from a {@code profile} argument rather than the DB - the
     * same throwaway-construction pattern {@link module.transfer.scout.PlayerConverter}'s
     * {@code buildHTCopyButton} uses for a copy-pasted transfer listing. Not squad-backed, not
     * persisted anywhere.
     */
    static Player playerFromProfile(JsonObject profile) throws McpToolException {
        if (!profile.has("age") || profile.get("age").isJsonNull()) {
            throw new McpToolException("profile.age is required.");
        }
        if (!profile.has("skills") || !profile.get("skills").isJsonObject()
                || profile.getAsJsonObject("skills").isEmpty()) {
            throw new McpToolException("profile.skills is required and must list at least one skill.");
        }
        final var player = new Player();
        player.setAge(profile.get("age").getAsInt());
        player.setAgeDays(McpJson.optInt(profile, "ageDays", 0));
        for (final var entry : profile.getAsJsonObject("skills").entrySet()) {
            player.setValue4Skill(positionalSkill(entry.getKey()).toPlayerSkill(),
                    entry.getValue().getAsInt());
        }
        player.setSpecialty(McpJson.optInt(profile, "specialty", 0));
        if (profile.has("tsi")) {
            player.setTsi(profile.get("tsi").getAsInt());
        }
        if (profile.has("agreeability")) {
            player.setGentleness(profile.get("agreeability").getAsInt());
        }
        if (profile.has("aggressiveness")) {
            player.setAggressivity(profile.get("aggressiveness").getAsInt());
        }
        if (profile.has("honesty")) {
            player.setHonesty(profile.get("honesty").getAsInt());
        }
        return player;
    }

    /**
     * {@code "playmaking"} -> {@link TransferSearchQuery.Skill#PLAYMAKING} - one of the six
     * positional skills {@code profile.skills} may be keyed by, the same vocabulary
     * {@link #skillName} produces. Deliberately narrower than every {@code Skill} constant: set
     * pieces, stamina, leadership and experience are not comparable-matching criteria (see
     * {@link #POSITIONAL_SKILLS}) and are rejected here rather than silently accepted.
     */
    static TransferSearchQuery.Skill positionalSkill(String name) {
        return POSITIONAL_SKILLS.stream()
                .filter(s -> skillName(s).equals(name))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown skill '" + name
                        + "' in profile.skills. Use one of " + POSITIONAL_SKILLS.stream()
                        .map(TransferTools::skillName).collect(Collectors.joining(", "))));
    }

    /**
     * The {@code "player"} field for a {@code profile}-based valuation - built only from the
     * fields the caller actually supplied, so nothing is echoed back with an invented value (see
     * {@link #playerFromProfile} for the defaults used to run the actual comparable matching).
     */
    static JsonObject profilePlayerJson(JsonObject profile) {
        final var json = new JsonObject();
        if (profile.has("playerName")) {
            json.addProperty("playerName", profile.get("playerName").getAsString());
        }
        json.addProperty("age", profile.get("age").getAsInt());
        json.addProperty("ageDays", McpJson.optInt(profile, "ageDays", 0));
        json.add("skills", profile.getAsJsonObject("skills"));
        if (profile.has("specialty")) {
            json.addProperty("specialty", profile.get("specialty").getAsInt());
        }
        if (profile.has("tsi")) {
            json.addProperty("tsi", profile.get("tsi").getAsInt());
        }
        if (profile.has("agreeability")) {
            json.addProperty("agreeability", profile.get("agreeability").getAsInt());
        }
        if (profile.has("aggressiveness")) {
            json.addProperty("aggressiveness", profile.get("aggressiveness").getAsInt());
        }
        if (profile.has("honesty")) {
            json.addProperty("honesty", profile.get("honesty").getAsInt());
        }
        return json;
    }

    /**
     * {@code profile} as a corpus row, but only when it carries both {@code playerId} (this
     * player's real Hattrick id - never used to look him up in the squad, only to identify the
     * row) and {@code salePrice} (a confirmed real price, e.g. from {@code get_team_transfers}) -
     * anything less is either unidentifiable or nothing worth recording. Missing either one is
     * not an error: {@code profile} usually exists purely to value a hypothetical, with no player
     * id and no known price at all - null means "nothing to write", not a failure.
     * <p>
     * Deliberately narrower than {@link #playerFromProfile}: only fields the caller actually gave
     * are written - no defaulted skill level, no defaulted specialty or character. A fabricated
     * value there only affects this one call's own weighting; written to the corpus it would
     * corrupt every future query that reads this row back.
     */
    static TransferListing listingFromProfile(JsonObject profile, Player player) {
        if (!profile.has("playerId") || !profile.has("salePrice")) {
            return null;
        }
        final var listing = new TransferListing();
        listing.setPlayerId(profile.get("playerId").getAsInt());
        listing.setObservedAt(HODateTime.now());
        if (profile.has("playerName")) {
            listing.setPlayerName(profile.get("playerName").getAsString());
        }
        // age/ageDays from the already-built player, not profile directly: age is mandatory and
        // ageDays' own 0 default is a real value, same as profilePlayerJson treats them.
        listing.setAge(player.getAge());
        listing.setAgeDays(player.getAgeDays());
        applySkillsToListing(listing, profile.getAsJsonObject("skills"));
        if (profile.has("specialty")) {
            listing.setSpecialty(profile.get("specialty").getAsInt());
        }
        if (profile.has("tsi")) {
            listing.setTsi(profile.get("tsi").getAsInt());
        }
        if (profile.has("agreeability")) {
            listing.setAgreeability(profile.get("agreeability").getAsInt());
        }
        if (profile.has("aggressiveness")) {
            listing.setAggressiveness(profile.get("aggressiveness").getAsInt());
        }
        if (profile.has("honesty")) {
            listing.setHonesty(profile.get("honesty").getAsInt());
        }
        // salePrice arrives in the user's local currency, same as every other amount this MCP
        // surface hands the model (get_team_transfers included) - swedishKrona converts it to
        // what TransferListing's own column actually holds, same as queryFrom does for priceMin/
        // priceMax. Storing the raw local-currency number here corrupted every stored row by the
        // league's exchange rate once McpJson.money() converted it back for display.
        listing.setSalePrice(McpJson.swedishKrona(profile.get("salePrice").getAsLong()));
        return listing;
    }

    /**
     * Writes {@link #listingFromProfile}'s row into HO's local transfer-listing corpus, if it
     * built one - see that method's doc for when it does and doesn't.
     *
     * @return whether a row was written
     */
    static boolean recordProfileObservation(JsonObject profile, Player player) {
        final var listing = listingFromProfile(profile, player);
        if (listing == null) {
            return false;
        }
        DBManager.instance().storeTransferListing(listing);
        return true;
    }

    /** {@code skills} onto {@code listing}'s own columns, under {@link #positionalSkill}'s names. */
    private static void applySkillsToListing(TransferListing listing, JsonObject skills) {
        for (final var entry : skills.entrySet()) {
            final var value = entry.getValue().getAsInt();
            switch (positionalSkill(entry.getKey())) {
                case KEEPER -> listing.setKeeper(value);
                case DEFENDING -> listing.setDefender(value);
                case PLAYMAKING -> listing.setPlaymaker(value);
                case PASSING -> listing.setPassing(value);
                case WINGER -> listing.setWinger(value);
                case SCORING -> listing.setScorer(value);
                default -> throw new IllegalStateException(
                        "positionalSkill only ever returns the six positional skills");
            }
        }
    }


    /**
     * Drops every positional skill from a serialised player or listing except his three best, which
     * move to {@code topSkills}. A valuation turns on what a player would be bought for, and seven
     * levels a row over forty rows buries that in numbers the reader has no use for - the fourth
     * positional skill down has never decided a price, being largely what the top three and the TSI
     * already say.
     * <p>
     * Set pieces survives the trim as a field of its own. It is the one skill that says nothing the
     * rest of the row says: a buyer wants a set piece taker whatever position he plays, so unlike
     * the fourth positional skill it can move a price on its own. It stays out of {@code topSkills}
     * and out of {@link #comparableQuery}'s matching for the same reason - it is a specialist skill,
     * not a position - so it is evidence for reading an outlier, not a criterion the corpus was
     * selected on. It is null on an observation Hattrick sent no details for.
     * <p>
     * Specialty, wage, TSI, form and the prices stay - they are what the remaining judgement rests
     * on.
     */
    static JsonObject topSkillsOnly(JsonObject json, Map<String, Integer> levels) {
        // A player nests his skills in two blocks, a listing writes them flat. Set pieces is in
        // neither levels map, so it is lifted out by name before the blocks go, and put back after.
        final var setPieces = json.has("skills")
                ? json.getAsJsonObject("skills").get("setPieces")
                : json.get("setPieces");
        json.remove("skills");
        json.remove("skillsWithSublevel");
        levels.keySet().forEach(json::remove);
        // Kept even when it is JsonNull: an absent key reads as trimmed away, a null as unknown.
        if (setPieces != null) {
            json.add("setPieces", setPieces);
        }

        // Ties break by name so the same player always serialises the same way.
        final var top = new JsonObject();
        levels.entrySet().stream()
                .filter(e -> e.getValue() != null)
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed()
                        .thenComparing(Map.Entry.comparingByKey()))
                .limit(3)
                .forEach(e -> top.addProperty(e.getKey(), e.getValue()));
        json.add("topSkills", top);
        return json;
    }

    /** The positional skills of a squad player, under the names {@link McpJson#player} writes. */
    static Map<String, Integer> playerSkillLevels(Player player) {
        final var levels = new LinkedHashMap<String, Integer>();
        for (final var skill : POSITIONAL_SKILLS) {
            levels.put(skillName(skill), player.getValue4Skill(skill.toPlayerSkill()));
        }
        return levels;
    }

    /** The positional skills of a stored listing, under the names {@link #playerSkillLevels} uses. */
    static Map<String, Integer> listingSkillLevels(TransferListing l) {
        final var levels = new LinkedHashMap<String, Integer>();
        levels.put("keeper", l.getKeeper());
        levels.put("defending", l.getDefender());
        levels.put("playmaking", l.getPlaymaker());
        levels.put("passing", l.getPassing());
        levels.put("winger", l.getWinger());
        levels.put("scoring", l.getScorer());
        return levels;
    }

    /** {@code SCORING} -> {@code "scoring"}: the name get_squad reports the skill under. */
    private static String skillName(TransferSearchQuery.Skill skill) {
        return skill.name().toLowerCase(Locale.ROOT);
    }

    /**
     * Whether {@code listing}'s own two best positional skills are the same pair {@code skill1}
     * and {@code skill2} - order doesn't matter, only the archetype identity. Without this, a
     * candidate whose SECONDARY skill happens to sit in the target's PRIMARY skill's queried band
     * still counts as a comparable even though he plays an entirely different role (a scorer with
     * a decent secondary playmaking level matched against a playmaker's own primary/secondary
     * window). A listing with no positional skill data at all can't be judged either way, so it
     * passes - the same neutral default {@link #comparableWeightBreakdown} uses when data is
     * missing, rather than penalising a thin observation for something it never recorded.
     */
    static boolean sameArchetype(TransferListing listing, TransferSearchQuery.Skill skill1,
                                  TransferSearchQuery.Skill skill2) {
        final var levels = listingSkillLevels(listing);
        if (POSITIONAL_SKILLS.stream().allMatch(s -> levels.get(skillName(s)) == null)) {
            return true;
        }
        final var ownTopTwo = POSITIONAL_SKILLS.stream()
                .sorted(Comparator.comparingInt(
                        (TransferSearchQuery.Skill s) -> {
                            final var level = levels.get(skillName(s));
                            return level == null ? -1 : level;
                        }).reversed())
                .limit(2)
                .collect(Collectors.toSet());
        // skill2 null = one-skill archetype (keeper): only his primary skill has to match.
        return skill2 == null ? ownTopTwo.contains(skill1) : ownTopTwo.equals(Set.of(skill1, skill2));
    }


    /**
     * Which of two observations of the same player represents him: the one that carries what he
     * actually sold for, else the newest.
     */
    static TransferListing betterObservation(TransferListing a, TransferListing b) {
        final var aSold = a.getSalePrice() != null;
        final var bSold = b.getSalePrice() != null;
        if (aSold != bSold) {
            return aSold ? a : b;
        }
        return observedAt(a).compareTo(observedAt(b)) >= 0 ? a : b;
    }

    /** A listing's observation date, never null, so it can be sorted and compared. */
    private static HODateTime observedAt(TransferListing listing) {
        return listing.getObservedAt() == null
                ? HODateTime.fromHT("1990-01-01 00:00:00") : listing.getObservedAt();
    }


    /** The TRANSFERLISTING column a market search skill is stored in. */
    private static String column(TransferSearchQuery.Skill skill) {
        return switch (skill) {
            case KEEPER -> "KEEPER";
            case DEFENDING -> "DEFENDER";
            case PLAYMAKING -> "PLAYMAKER";
            case PASSING -> "PASSING";
            case WINGER -> "WINGER";
            case SCORING -> "SCORER";
            case SETPIECES -> "SETPIECES";
            case STAMINA -> "STAMINA";
            case LEADERSHIP -> "LEADERSHIP";
            case EXPERIENCE -> "EXPERIENCE";
        };
    }

    // ------------------------------------------------------------------ transfer scout list
    //
    // Which copy of the list is authoritative depends on whether the Transfers tab is open, and
    // reaching for the panel when it is not would create and select that tab. ScoutList owns both
    // of those rules; see its javadoc. Everything here runs on the EDT because it does.

    /**
     * Look the player up on the market as he is right now, for the three character traits.
     * {@code playerdetails} is the only file that carries them - {@code transfersearch} does not, so
     * the TRANSFERLISTING snapshot the entry was built from cannot supply them.
     * <p>
     * The same call is also a fresh, character-bearing corpus observation, so it stores one rather
     * than throwing the rest of the response away - the same sharing {@code TransferListingResolver}
     * does. Only when he is actually listed: Hattrick prunes an unlisted player's skills down to
     * stamina, and {@link TransferListing#fromPlayerDetails} refuses such a response for that
     * reason.
     * <p>
     * One CHPP call per player added, and best effort throughout: character is a bonus on top of
     * the add, so a failed or refused call leaves the three fields null and the entry is stored
     * regardless. The resolve pass fills them in later anyway, but only once the watched deadline
     * has passed - too late to inform the buy this entry was created for.
     */
    private static void observeForScout(ScoutEintrag entry) {
        try {
            final var m = OnlineWorker.downloadMarketPlayer(String.valueOf(entry.getPlayerID()));
            if (m == null || m.player().getPlayerId() == 0) {
                return;
            }
            ScoutList.applyCharacter(entry, m.player());
            // Same fetch, onto his stored search-hit rows too, so valuation has it before the next
            // download's backfill would get there. Legacy name: getGentleness() is Agreeability.
            DBManager.instance().storeTransferListingCharacter(entry.getPlayerID(),
                    m.player().getGentleness(), m.player().getAggressivity(), m.player().getHonesty());
            if (m.transferListed()) {
                DBManager.instance().storeTransferListing(
                        TransferListing.fromPlayerDetails(m, HODateTime.now()));
            }
        } catch (Exception e) {
            HOLogger.instance().error(TransferTools.class, e);
        }
    }

    private static JsonObject manageScout(JsonObject args) throws McpToolException {
        return switch (McpJson.reqString(args, "action")) {
            case "add" -> addToScout(args);
            case "remove" -> removeFromScout(args);
            case "refresh" -> refreshScout(args);
            default -> throw new IllegalArgumentException("action must be add, remove or refresh.");
        };
    }

    /**
     * Re-read the current ask, bid and deadline of scout entries - the named players, or every
     * entry not sold whose deadline is still ahead - with one {@code playerdetails} call each,
     * no transfer search. Each listed response is also stored as a corpus observation.
     */
    private static JsonObject refreshScout(JsonObject args) throws McpToolException {
        final var wanted = new java.util.LinkedHashSet<Integer>();
        if (args.has("players") && args.get("players").isJsonArray()) {
            args.getAsJsonArray("players").forEach(e -> wanted.add(e.isJsonObject()
                    ? e.getAsJsonObject().get("playerId").getAsInt() : e.getAsInt()));
        }
        final var now = new java.sql.Timestamp(System.currentTimeMillis());
        // onEdt hands back JSON only, so the ids travel as an array.
        final var picked = LineupTools.onEdt(() -> {
            final var json = new JsonObject();
            final var array = new JsonArray();
            ScoutList.entries().stream()
                    .filter(e -> wanted.isEmpty() ? !e.isSold() && e.getDeadline() != null && e.getDeadline().after(now)
                            : wanted.contains(e.getPlayerID()))
                    .forEach(e -> array.add(e.getPlayerID()));
            json.add("ids", array);
            return json;
        });
        final var ids = new ArrayList<Integer>();
        picked.getAsJsonArray("ids").forEach(e -> ids.add(e.getAsInt()));

        // Off the EDT: one CHPP read per player.
        final var listed = new LinkedHashMap<Integer, core.file.xml.XMLPlayersParser.MarketPlayer>();
        final var notListed = new JsonArray();
        for (final var id : ids) {
            final var m = OnlineWorker.downloadMarketPlayer(String.valueOf(id));
            if (m != null && m.transferListed() && m.deadline() != null) {
                listed.put(id, m);
                DBManager.instance().storeTransferListing(TransferListing.fromPlayerDetails(m, HODateTime.now()));
            } else {
                notListed.add(id);
            }
        }

        return LineupTools.onEdt(() -> {
            listed.forEach((id, m) -> ScoutList.recordRelisting(id, m.askingPrice(), m.highestBid(), m.deadline()));
            final var refreshed = new JsonArray();
            ScoutList.entries().stream()
                    .filter(e -> listed.containsKey(e.getPlayerID()))
                    .forEach(e -> refreshed.add(McpJson.scoutEntry(e)));
            final var out = new JsonObject();
            out.addProperty("status", "ok");
            out.addProperty("refreshedCount", refreshed.size());
            out.add("refreshed", refreshed);
            if (!notListed.isEmpty()) {
                // Sold, withdrawn, or the read failed (HO's log says which); the entry is unchanged.
                out.add("notListed", notListed);
            }
            if (!wanted.isEmpty()) {
                final var notOnList = new JsonArray();
                wanted.stream().filter(id -> !ids.contains(id)).forEach(notOnList::add);
                if (!notOnList.isEmpty()) {
                    out.add("notOnList", notOnList);
                }
            }
            return out;
        });
    }

    private static JsonObject addToScout(JsonObject args) throws McpToolException {
        final var requested = McpJson.reqArray(args, "players");
        if (requested.isEmpty()) {
            throw new IllegalArgumentException("players is empty - pass at least one player id.");
        }

        // Resolve off the EDT: this is the part that hits the database.
        final var entries = new java.util.LinkedHashMap<Integer, ScoutEintrag>();
        final var notFound = new JsonArray();
        for (final var element : requested) {
            final int playerId;
            final String note;
            if (element.isJsonObject()) {
                final var object = element.getAsJsonObject();
                playerId = McpJson.reqInt(object, "playerId");
                note = McpJson.optString(object, "note", null);
            } else {
                playerId = element.getAsInt();
                note = null;
            }
            final var listing = DBManager.instance().loadLatestTransferListing(playerId);
            if (listing == null) {
                notFound.add(playerId);
                continue;
            }
            final var entry = TransferSearchConverter.toScoutEintrag(listing);
            observeForScout(entry);
            if (note != null && !note.isBlank()) {
                entry.setInfo(truncateInfo(entry.getInfo() + note));
            }
            entries.put(playerId, entry);
        }

        return LineupTools.onEdt(() -> {
            final var tabOpen = ScoutList.isLive();
            final var list = ScoutList.entries();
            for (final var entry : entries.values()) {
                list.remove(entry);                // equals is by player id, so this replaces
                list.add(entry);
            }
            // The panel only persists at shutdown; save now so the shortlist survives a crash.
            ScoutList.save(list);

            // Full entry, not just the id: observeForScout already paid for the character fields
            // (and everything else scoutEntry reads off entry), so echo them back instead of
            // making the caller round-trip through list_scout to read what manage_scout add just fetched.
            final var added = new JsonArray();
            for (final var entry : entries.values()) {
                added.add(McpJson.scoutEntry(entry));
            }
            final var out = new JsonObject();
            out.addProperty("status", "ok");
            out.addProperty("addedCount", added.size());
            out.add("added", added);
            out.addProperty("scoutListSize", list.size());
            out.addProperty("visibleInUi", tabOpen ? "yes, the TransferScout table refreshed"
                    : "the Transfers tab is closed; the entries are stored and show when it opens");
            if (!notFound.isEmpty()) {
                out.add("notFound", notFound);
                out.addProperty("notFoundNote", "No stored market snapshot for these players - run "
                        + "search_transfers for them first, then add them.");
            }
            return out;
        });
    }

    /**
     * The counterpart to {@link #addToScout}. Not tidiness: every entry on the list is watched for a
     * re-listing on each team download, so a list nobody prunes is a recurring CHPP bill, and until
     * now only a human clicking the F7 tab could stop it.
     * <p>
     * Removing an entry loses nothing. The market observations it was built from stay in
     * TRANSFERLISTING and keep feeding {@code estimate_market_value}; only the watch goes.
     */
    private static JsonObject removeFromScout(JsonObject args) throws McpToolException {
        final var requested = McpJson.reqArray(args, "players");
        if (requested.isEmpty()) {
            throw new IllegalArgumentException("players is empty - pass at least one player id.");
        }
        final var wanted = new java.util.LinkedHashSet<Integer>();
        requested.forEach(e -> wanted.add(e.isJsonObject()
                ? e.getAsJsonObject().get("playerId").getAsInt() : e.getAsInt()));

        return LineupTools.onEdt(() -> {
            final var tabOpen = ScoutList.isLive();
            final var list = ScoutList.entries();
            final var removed = new JsonArray();
            // Walk the list rather than the request, so the response names who actually went and
            // an id that was never on the list falls out as notOnList instead of a silent success.
            list.removeIf(entry -> {
                if (!wanted.contains(entry.getPlayerID())) {
                    return false;
                }
                final var json = new JsonObject();
                json.addProperty("playerId", entry.getPlayerID());
                json.addProperty("name", entry.getName());
                removed.add(json);
                return true;
            });
            ScoutList.save(list);

            final var out = new JsonObject();
            out.addProperty("status", "ok");
            out.addProperty("removedCount", removed.size());
            out.add("removed", removed);
            out.addProperty("scoutListSize", list.size());
            out.addProperty("visibleInUi", tabOpen ? "yes, the TransferScout table refreshed"
                    : "the Transfers tab is closed; the change is stored and shows when it opens");
            final var stillWanted = new java.util.LinkedHashSet<>(wanted);
            removed.forEach(e -> stillWanted.remove(e.getAsJsonObject().get("playerId").getAsInt()));
            if (!stillWanted.isEmpty()) {
                final var notOnList = new JsonArray();
                stillWanted.forEach(notOnList::add);
                out.add("notOnList", notOnList);
            }
            return out;
        });
    }

    private static JsonObject listScout(JsonObject args) throws McpToolException {
        final var filter = ScoutFilter.from(args);
        // bulk: every matching entry, full, into the download.
        final var bulk = McpJson.optBool(args, "bulk", false);
        final var full = bulk || McpJson.optBool(args, "full", false);
        final var offset = bulk ? 0 : Math.max(McpJson.optInt(args, "offset", 0), 0);
        final var limit = bulk ? Integer.MAX_VALUE
                : Math.min(Math.max(McpJson.optInt(args, "limit", DEFAULT_SCOUT_PAGE), 1), MAX_SCOUT_PAGE);
        final var out = LineupTools.onEdt(() -> {
            final var now = java.time.Instant.now();
            final var entries = ScoutList.entries().stream().filter(e -> filter.test(e, now)).toList();
            final var players = new JsonArray();
            entries.stream().skip(offset).limit(limit).forEach(e -> players.add(McpJson.scoutEntry(e)));
            final var json = new JsonObject();
            json.addProperty("currency", McpJson.currency());
            json.addProperty("count", entries.size());
            json.addProperty("offset", offset);
            json.addProperty("returned", players.size());
            json.add("players", players);
            return json;
        });

        // The scout table has one price column and it holds max(asking price, highest bid), so it
        // stops saying what the seller asked the moment bidding passes it. The corpus keeps the two
        // apart - read them back off the EDT, where the database reads belong. Missing for an entry
        // typed into the TransferScout tab by hand.
        //
        // This is the NEWEST observation of the player, not necessarily the one manage_scout add built
        // the entry from: a later search_transfers over the same profile stores a fresher row, and a
        // re-listed player's newest row belongs to a different transfer altogether. Fresher is the
        // better number, but it is no longer the same instant as the frozen price and deadline
        // beside it, so observedAt ships with it and the model can see which is which. Storing the
        // pair on the scout entry instead would need a SCOUT column and a DBVersion migration to buy
        // a staler answer.
        for (final var element : out.getAsJsonArray("players")) {
            final var player = element.getAsJsonObject();
            final var listing = DBManager.instance()
                    .loadLatestTransferListing(player.get("playerId").getAsInt());
            if (listing == null) {
                continue;
            }
            player.addProperty("observedAt",
                    listing.getObservedAt() == null ? null : listing.getObservedAt().toHT());
            // A stored 0 means Hattrick sent no AskingPrice for this listing, not that the seller
            // asked nothing - about 6% of observed search hits, plus every snapshot that came from
            // currentbids, which carries no asking price at all. Leave the key out rather than
            // publish a zero the model would read as a price. A highestBid of 0 is left in: there,
            // zero really does mean nobody has bid yet.
            if (listing.getAskingPrice() > 0) {
                player.addProperty("askingPrice", McpJson.money(listing.getAskingPrice()));
            }
            player.addProperty("highestBid", McpJson.money(listing.getHighestBid()));
        }
        if (!full) {
            final var keys = new java.util.ArrayList<>(COMPACT_SCOUT_KEYS);
            filter.skills().forEach(r -> keys.add(scoutSkillKey(r.skill())));
            final var compact = new JsonArray();
            for (final var element : out.getAsJsonArray("players")) {
                final var player = element.getAsJsonObject();
                final var row = new JsonObject();
                keys.stream().filter(player::has).forEach(k -> row.add(k, player.get(k)));
                compactNote(row);
                compact.add(row);
            }
            out.add("players", compact);
        }
        return bulk ? McpDownloads.bulk(out, "players") : out;
    }

    /** list_scout's default row: the market state plus age and specialty, not the frozen skills. */
    private static final List<String> COMPACT_SCOUT_KEYS = List.of("playerId", "playerName", "note",
            "age", "specialty", "price", "deadline", "askingPrice", "highestBid", "observedAt",
            "salePrice", "soldAt");
    private static final int DEFAULT_SCOUT_PAGE = 20;
    /** A compact row's note is cut here; full returns it whole. */
    static final int COMPACT_NOTE_CHARS = 80;

    /** Cuts a long note for a compact row and says so, so a cut note is never read as the whole. */
    static void compactNote(JsonObject row) {
        if (!row.has("note") || !row.get("note").isJsonPrimitive()) {
            return;
        }
        final var note = row.get("note").getAsString();
        if (note.length() > COMPACT_NOTE_CHARS) {
            row.addProperty("note", note.substring(0, COMPACT_NOTE_CHARS) + "...");
            row.addProperty("noteCut", true);
        }
    }
    private static final int MAX_SCOUT_PAGE = 200;

    /** One list_scout skill filter; a null bound is open. */
    record SkillRange(TransferSearchQuery.Skill skill, Integer min, Integer max) {
        boolean test(Integer level) {
            return level != null && (min == null || level >= min) && (max == null || level <= max);
        }
    }

    /** list_scout's local filters, all ANDed; checked before paging so count is the filtered total. */
    record ScoutFilter(boolean onlyOpen, Integer ageMin, Integer ageMax, Integer specialty,
                       List<SkillRange> skills) {
        static ScoutFilter from(JsonObject args) {
            final var skills = new java.util.ArrayList<SkillRange>();
            if (args.has("skills") && !args.get("skills").isJsonNull()) {
                final var array = McpJson.reqArray(args, "skills");
                for (int i = 0; i < array.size(); i++) {
                    if (!array.get(i).isJsonObject()) {
                        throw new IllegalArgumentException("skills[" + i + "] must be an object of "
                                + "the shape {skill, min?, max?}.");
                    }
                    final var f = array.get(i).getAsJsonObject();
                    skills.add(new SkillRange(parseSkill(McpJson.reqString(f, "skill")),
                            McpJson.optInteger(f, "min"), McpJson.optInteger(f, "max")));
                }
            }
            return new ScoutFilter(McpJson.optBool(args, "onlyOpen", false),
                    McpJson.optInteger(args, "ageMin"), McpJson.optInteger(args, "ageMax"),
                    McpJson.optInteger(args, "specialty"), List.copyOf(skills));
        }

        /** onlyOpen = still on the market: no sale recorded and the deadline not yet passed. */
        boolean test(module.transfer.scout.ScoutEintrag e, java.time.Instant now) {
            return (!onlyOpen || (!e.isSold()
                            && (e.getDeadline() == null || e.getDeadline().toInstant().isAfter(now))))
                    && (ageMin == null || e.getAlter() >= ageMin)
                    && (ageMax == null || e.getAlter() <= ageMax)
                    && (specialty == null || e.getSpeciality() == specialty)
                    && skills.stream().allMatch(r -> r.test(scoutSkill(e, r.skill())));
        }
    }

    static Integer scoutSkill(module.transfer.scout.ScoutEintrag e, TransferSearchQuery.Skill skill) {
        return switch (skill) {
            case KEEPER -> e.getTorwart();
            case DEFENDING -> e.getVerteidigung();
            case PLAYMAKING -> e.getSpielaufbau();
            case WINGER -> e.getFluegelspiel();
            case PASSING -> e.getPasspiel();
            case SCORING -> e.getTorschuss();
            case SETPIECES -> e.getStandards();
            case STAMINA -> e.getKondition();
            case EXPERIENCE -> e.getErfahrung();
            case LEADERSHIP -> e.getLeadership();
        };
    }

    /** The key {@link McpJson#scoutEntry} writes that skill under. */
    private static String scoutSkillKey(TransferSearchQuery.Skill skill) {
        return switch (skill) {
            case KEEPER -> "keeper";
            case DEFENDING -> "defender";
            case PLAYMAKING -> "playmaker";
            case WINGER -> "winger";
            case PASSING -> "passing";
            case SCORING -> "scorer";
            case SETPIECES -> "setPieces";
            case STAMINA -> "stamina";
            case EXPERIENCE -> "experience";
            case LEADERSHIP -> "leadership";
        };
    }

    /** The SCOUT.Info column is VARCHAR(256); an over-long note would fail the insert. */
    private static String truncateInfo(String info) {
        return info.length() <= 256 ? info : info.substring(0, 256);
    }

    /** trackingTypeId 2 (leading bid) / 8 (losing bid) are the ones you have actually bid on. */
    private static boolean isOwnBidCategory(int trackingTypeId) {
        return trackingTypeId == 2 || trackingTypeId == 8;
    }

    /** Only 5 / 8 / 9 are named by the CHPP doc; the rest are carried through as inferred labels. */
    private static String bidCategory(int trackingTypeId) {
        return switch (trackingTypeId) {
            case 1 -> "selling";
            case 2 -> "leading_bid";
            case 3 -> "mother_club";
            case 4 -> "previous_club";
            case 5 -> "hotlisted";
            case 8 -> "losing_bid";
            case 9 -> "finished";
            case 10 -> "transfer_prospect";
            default -> "unknown_" + trackingTypeId;
        };
    }

    private static JsonObject openBids(JsonObject args) throws McpToolException {
        final var includeAll = McpJson.optBool(args, "includeAll", false);
        final XMLCurrentBidsParser.CurrentBids current;
        try {
            current = ChppWriteService.instance().listCurrentBidsForAgent();
        } catch (ChppWriteService.ChppWriteException e) {
            throw new McpToolException(e.getMessage());
        }

        final var now = HODateTime.now();
        recordBidObservations(current, now);

        final var bids = new JsonArray();
        for (final var item : current.items()) {
            if (!includeAll) {
                if (!isOwnBidCategory(item.trackingTypeId())) {
                    continue;
                }
                final var deadline = HODateTime.fromHT(item.deadline());
                // Leading bids drop the moment they expire; losing bids linger a day so a recent
                // loss is still reported on the next check.
                final var cutoff = item.trackingTypeId() == 8
                        ? new HODateTime(now.getInstant().minus(LOSING_BID_GRACE))
                        : now;
                if (deadline != null && deadline.isBefore(cutoff)) {
                    continue;
                }
            }
            final var json = new JsonObject();
            json.addProperty("playerId", item.playerId());
            json.addProperty("playerName", item.playerName());
            json.addProperty("transferId", item.transferId());
            json.addProperty("deadline", item.deadline());
            json.addProperty("highestBid", McpJson.money(item.highestBidAmount()));
            json.addProperty("highestBidTeamName", item.highestBidTeamName());
            json.addProperty("leading",
                    item.highestBidTeamId() != 0 && item.highestBidTeamId() == current.teamId());
            json.addProperty("trackingTypeId", item.trackingTypeId());
            json.addProperty("category", bidCategory(item.trackingTypeId()));
            addLocalBidContext(json, item.playerId());
            bids.add(json);
        }

        final var out = new JsonObject();
        out.addProperty("currency", McpJson.currency());
        out.addProperty("count", bids.size());
        out.add("bids", bids);
        final var selling = sellingRows(current);
        out.addProperty("sellingCount", selling.size());
        out.add("selling", selling);
        if (!includeAll) {
            out.addProperty("note", "leading_bid / losing_bid are inferred from trackingTypeId "
                    + "2 / 8; pass includeAll to see every tracked transfer.");
        }
        return out;
    }

    /**
     * Our own transfer-listed players (trackingTypeId 1, "Selling" in the CHPP data types) with the
     * asking price and the current highest bid. Not Supporter-gated, unlike the 5 / 8 / 9 / 10
     * categories.
     */
    static JsonArray sellingRows(XMLCurrentBidsParser.CurrentBids current) {
        final var rows = new JsonArray();
        for (final var item : current.items()) {
            if (item.trackingTypeId() != 1) {
                continue;
            }
            final var json = new JsonObject();
            json.addProperty("playerId", item.playerId());
            json.addProperty("playerName", item.playerName());
            json.addProperty("deadline", item.deadline());
            final var askingPrice = ownAskingPrice(item);
            if (askingPrice > 0) {
                json.addProperty("askingPrice", McpJson.money(askingPrice));
            }
            json.addProperty("hasBids", item.highestBidAmount() > 0);
            json.addProperty("highestBid", McpJson.money(item.highestBidAmount()));
            json.addProperty("highestBidTeamId", item.highestBidTeamId());
            json.addProperty("highestBidTeamName", item.highestBidTeamName());
            rows.add(json);
        }
        return rows;
    }

    /**
     * What we asked for one of our own listed players. currentbids does not say, and nothing else
     * HO does observes our own players on the market, so the first check of a new listing makes
     * one playerdetails call and stores the answer; every later check reads it back for free.
     * 0 when Hattrick refuses - the row then simply has no askingPrice.
     */
    private static long ownAskingPrice(XMLCurrentBidsParser.BidItem item) {
        final var deadline = HODateTime.fromHT(item.deadline());
        if (deadline == null) {
            return 0;
        }
        final var stored = DBManager.instance().loadTransferListingAskingPrice(item.playerId(), deadline);
        if (stored > 0) {
            return stored;
        }
        final var m = ChppWriteService.instance().marketPlayerForAgent(item.playerId());
        if (m == null || !m.transferListed() || m.askingPrice() <= 0) {
            return 0;
        }
        try {
            DBManager.instance().storeTransferListing(TransferListing.fromPlayerDetails(m, HODateTime.now()));
        } catch (RuntimeException e) {
            HOLogger.instance().log(TransferTools.class, e);   // the price is still good for this answer
        }
        return m.askingPrice();
    }

    private static JsonObject playerTransferHistory(JsonObject args) throws McpToolException {
        final var playerId = McpJson.reqInt(args, "playerId");
        try {
            return playerTransferHistoryJson(playerId,
                    core.net.ChppWriteService.instance().playerTransfersForAgent(playerId));
        } catch (core.net.ChppWriteService.ChppWriteException e) {
            throw new McpToolException(e.getMessage());
        }
    }

    /** Newest first. Seasons are local to the user's league, as HO's transfer module stores them. */
    static JsonObject playerTransferHistoryJson(int playerId, List<module.transfer.PlayerTransfer> transfers) {
        final var sorted = new ArrayList<>(transfers);
        sorted.sort(Comparator.comparing(module.transfer.PlayerTransfer::getDate,
                Comparator.nullsLast(Comparator.reverseOrder())));
        final var rows = new JsonArray();
        for (final var t : sorted) {
            final var json = new JsonObject();
            json.addProperty("transferId", t.getTransferId());
            json.addProperty("date", t.getDate() == null ? null : t.getDate().toHT());
            json.addProperty("season", t.getSeason());
            json.addProperty("week", t.getWeek());
            json.addProperty("price", McpJson.money(t.getPrice()));
            json.addProperty("tsi", t.getTsi());
            json.addProperty("sellerTeamId", t.getSellerid());
            json.addProperty("sellerTeamName", t.getSellerName());
            json.addProperty("buyerTeamId", t.getBuyerid());
            json.addProperty("buyerTeamName", t.getBuyerName());
            rows.add(json);
        }
        final var out = new JsonObject();
        out.addProperty("playerId", playerId);
        out.addProperty("playerName", sorted.isEmpty() ? null : sorted.get(0).getPlayerName());
        out.add("transfers", rows);
        return out;
    }

    /**
     * The two prices {@code currentbids} does not carry, from what HO already stored: the seller's
     * asking price out of the market corpus, and - once the transfer finished - what it went for
     * next to what we bid, out of the bid ledger {@link #recordBidObservations} has just refreshed.
     * <p>
     * A bid is only worth what it is measured against. Asking price says how far bidding has run
     * past what the seller wanted, and {@code salePrice} against {@code ourBid} is the one honest
     * signal about a market a losing bid ever produces. Both are free here - already in the
     * database, no CHPP call.
     */
    private static void addLocalBidContext(JsonObject json, int playerId) {
        final var listing = DBManager.instance().loadLatestTransferListing(playerId);
        // Asking price is 0 on a listing seen only through currentbids, which does not send one.
        if (listing != null && listing.getAskingPrice() > 0) {
            json.addProperty("askingPrice", McpJson.money(listing.getAskingPrice()));
        }
        final var ledger = DBManager.instance().loadTransferBid(playerId);
        if (ledger == null) {
            return;
        }
        if (ledger.getOurBid() != null) {
            json.addProperty("ourBid", McpJson.money(ledger.getOurBid()));
        }
        if (ledger.getFinalPrice() != null) {
            json.addProperty("salePrice", McpJson.money(ledger.getFinalPrice()));
            json.addProperty("outcome", ledger.getOutcome());
        }
    }

    /**
     * Snapshot every tracked transfer into the bid ledger ({@link core.db.TransferBid}) and the
     * market corpus ({@link TransferListing}) - the data is already in hand, so it costs no CHPP
     * traffic, and a losing bid still leaves a record of what the player sold for.
     * <p>
     * ponytail: best-effort. The CHPP call has already succeeded and consumed its rate budget, so a
     * database hiccup logs and is dropped rather than failing the tool call.
     */
    private static void recordBidObservations(XMLCurrentBidsParser.CurrentBids current, HODateTime now) {
        try {
            for (final var item : current.items()) {
                final var existing = DBManager.instance().loadTransferBid(item.playerId());
                DBManager.instance().storeTransferBid(
                        TransferBid.fromObservation(existing, item, current.teamId(), now));
                final var listing = TransferListing.fromBidItem(item, now);
                // currentbids sends no asking price. This row becomes the player's newest, which is
                // what every "latest snapshot" reader takes, so carry over what an earlier
                // observation of the same listing had rather than hide it behind a 0.
                if (listing.getDeadline() != null) {
                    listing.setAskingPrice(DBManager.instance()
                            .loadTransferListingAskingPrice(item.playerId(), listing.getDeadline()));
                }
                DBManager.instance().storeTransferListing(listing);
            }
        } catch (RuntimeException e) {
            HOLogger.instance().log(TransferTools.class, e);
        }
    }

    /** Amounts are in Swedish krona - already converted from the user's currency in {@link #previewBid}. */
    private record PendingBid(int playerId, long bidAmountSek, Long maxBidAmountSek, long createdAt) {
    }

    private static final Map<String, PendingBid> PENDING_BIDS = new ConcurrentHashMap<>();
    private static final long BID_TTL_MS = 120_000;

    static void clearPendingBidsForTest() {
        PENDING_BIDS.clear();
    }

    static void expirePendingBidsForTest() {
        PENDING_BIDS.replaceAll((k, v) ->
                new PendingBid(v.playerId(), v.bidAmountSek(), v.maxBidAmountSek(), 0L));
    }

    private static void sweepExpiredBids() {
        final var now = System.currentTimeMillis();
        PENDING_BIDS.values().removeIf(b -> now - b.createdAt() > BID_TTL_MS);
    }

    private static JsonObject placeBid(JsonObject args) throws McpToolException {
        sweepExpiredBids();
        final var playerId = McpJson.reqInt(args, "playerId");
        final var confirmToken = McpJson.optString(args, "confirmToken", null);

        if (confirmToken == null) {
            return previewBid(args, playerId);
        }
        return commitBid(playerId, confirmToken);
    }

    private static JsonObject previewBid(JsonObject args, int playerId) throws McpToolException {
        // The model bids in the user's currency; placeBid is sent in swedish krona, as playerdetails
        // documents ("Bid amount in SEK"). transfersearch's price bounds turned out to be local, so
        // check the first real bid's echoed amount (docs/current.md @ f5c51d0d).
        final var bidAmountLocal = McpJson.reqLong(args, "bidAmount");
        final Long maxBidAmountLocal = McpJson.optLong(args, "maxBidAmount");
        if (maxBidAmountLocal != null && maxBidAmountLocal < bidAmountLocal) {
            throw new McpToolException("maxBidAmount " + maxBidAmountLocal + " is below bidAmount "
                    + bidAmountLocal + ": the autobid ceiling must be at least the bid. Nothing was "
                    + "placed.");
        }
        final var bidAmountSek = McpJson.swedishKrona(bidAmountLocal);
        final Long maxBidAmountSek =
                maxBidAmountLocal == null ? null : McpJson.swedishKrona(maxBidAmountLocal);

        final var token = UUID.randomUUID().toString();
        PENDING_BIDS.put(token,
                new PendingBid(playerId, bidAmountSek, maxBidAmountSek, System.currentTimeMillis()));

        final var out = new JsonObject();
        out.addProperty("status", "confirm_required");
        out.addProperty("currency", McpJson.currency());

        final var listing = DBManager.instance().loadLatestTransferListing(playerId);
        final var player = new JsonObject();
        player.addProperty("id", playerId);
        if (listing != null) {
            player.addProperty("name", listing.getPlayerName());
            player.addProperty("ageYears", listing.getAge());
            player.addProperty("askingPrice", McpJson.money(listing.getAskingPrice()));
            player.addProperty("highestBid", McpJson.money(listing.getHighestBid()));
            player.addProperty("deadline",
                    listing.getDeadline() == null ? null : listing.getDeadline().toHT());
        } else {
            player.addProperty("note", "No market snapshot for this player - run search_transfers "
                    + "for him first if you want a preview.");
        }
        out.add("player", player);

        // Echoed back in the user's currency - the numbers the model passed in.
        final var bid = new JsonObject();
        bid.addProperty("bidAmount", bidAmountLocal);
        if (maxBidAmountLocal != null) {
            bid.addProperty("maxBidAmount", maxBidAmountLocal);
        }
        out.add("bid", bid);

        out.addProperty("confirmToken", token);
        out.addProperty("expiresInSeconds", 120);
        out.addProperty("note", "Call place_bid again with this confirmToken and the same playerId "
                + "to place the bid.");
        return out;
    }

    private static JsonObject commitBid(int playerId, String confirmToken) throws McpToolException {
        final var pending = PENDING_BIDS.remove(confirmToken);
        if (pending == null
                || System.currentTimeMillis() - pending.createdAt() > BID_TTL_MS
                || pending.playerId() != playerId) {
            throw new McpToolException("That confirmToken is invalid, expired, or for a different "
                    + "player - start again with a step-1 place_bid call.");
        }
        final ChppWriteService.BidResult result;
        try {
            result = ChppWriteService.instance().placeBidForAgent(
                    pending.playerId(), pending.bidAmountSek(), pending.maxBidAmountSek());
        } catch (ChppWriteService.ChppWriteException e) {
            throw new McpToolException(e.getMessage());
        }
        recordOurBid(pending, result);
        final var out = new JsonObject();
        out.addProperty("status", "ok");
        out.addProperty("currency", McpJson.currency());
        final var player = new JsonObject();
        player.addProperty("id", pending.playerId());
        out.add("player", player);
        out.addProperty("highestBid", McpJson.money(result.highestBid()));
        if (result.maxBid() != null) {
            out.addProperty("maxBid", McpJson.money(result.maxBid()));
        }
        // Hattrick answers a placed bid either way, so the autobid is only known to be set when the
        // answer shows it. Compared in krona, the unit both sides of the call use.
        if (pending.maxBidAmountSek() != null && !pending.maxBidAmountSek().equals(result.maxBid())) {
            out.addProperty("warning", "You asked for maxBidAmount "
                    + McpJson.money(pending.maxBidAmountSek()) + " but Hattrick's answer shows "
                    + (result.maxBid() == null ? "no autobid"
                            : "a max bid of " + McpJson.money(result.maxBid()))
                    + ". The bid itself is placed; check the autobid on the Hattrick website.");
        }
        out.addProperty("deadline", result.deadline());
        out.addProperty("bidderTeamName", result.bidderTeamName());
        out.addProperty("note", "Bid placed. Poll get_open_bids or search_transfers for the updated "
                + "market state.");
        return out;
    }

    /**
     * Record what HO just bid in the ledger, so the eventual sale price can be compared against it
     * even if the bid is lost. Keyed on the player; the market state is refreshed by the next
     * {@code get_open_bids}. ponytail: best-effort, same reasoning as {@link #recordBidObservations}.
     */
    private static void recordOurBid(PendingBid pending, ChppWriteService.BidResult result) {
        try {
            final var now = HODateTime.now();
            var bid = DBManager.instance().loadTransferBid(pending.playerId());
            if (bid == null) {
                bid = new TransferBid();
                bid.setPlayerId(pending.playerId());
                bid.setFirstSeenAt(now);
            }
            bid.setOurBid(pending.bidAmountSek());
            bid.setOurMaxBid(pending.maxBidAmountSek());
            bid.setBidPlacedAt(now);
            bid.setDeadline(HODateTime.fromHT(result.deadline()));
            bid.setLatestHighestBid(result.highestBid());
            bid.setLastSeenAt(now);
            DBManager.instance().storeTransferBid(bid);
        } catch (RuntimeException e) {
            HOLogger.instance().log(TransferTools.class, e);
        }
    }

    private static JsonObject searchSchema() {
        final var properties = McpTool.properties(
                "ageMin", McpTool.prop("integer", "minimum age in years. Hattrick limits how wide "
                        + "the age window may be: up to ageMin 18 only ageMax - ageMin <= 1, from "
                        + "ageMin 19 a width of 2. A wider window is refused outright, not answered "
                        + "with an empty result - split it into several searches"),
                "ageMax", McpTool.prop("integer", "maximum age in years, see ageMin for how far it "
                        + "may sit above it"),
                "skills", McpTool.prop("array", "one to four skill filters, each "
                        + "{skill, min, max}. skill is one of " + SKILL_NAMES + ". min and max are "
                        + "levels and may be at most 3 apart. The first entry is the primary skill "
                        + "and is mandatory; set pieces, leadership and experience have a floor of "
                        + "level 5, keeper of level 3"),
                "specialty", McpTool.prop("integer",
                        "specialty id: 0 none, 1 technical, 2 quick, 3 powerful, 4 unpredictable, "
                                + "5 head, 6 resilient (regainer), 8 support. 7 is not a specialty. "
                                + "Leave unset to match any, including the two rare ones"),
                "nativeCountryId", McpTool.prop("integer", "native country id"),
                "tsiMin", McpTool.prop("integer", "minimum TSI"),
                "tsiMax", McpTool.prop("integer", "maximum TSI"),
                "priceMin", McpTool.prop("integer", "minimum current price (highest bid, else asking price), in the user's currency"),
                "priceMax", McpTool.prop("integer", "maximum current price (highest bid, else asking price), in the user's currency"),
                "pageSize", McpTool.prop("integer", "hits Hattrick returns per search, default and "
                        + "maximum 100. itemCount is the market total, -1 above 100 - narrow the "
                        + "search then, there is no exact total"),
                "resultId", McpTool.prop("string", "page or re-sort the hits of an earlier call instead "
                        + "of searching: no CHPP request, not counted against the cap. Last "
                        + KEPT_RESULTS + " kept"),
                "limit", McpTool.prop("integer", "hits per answer, default " + HIT_PAGE + " (max "
                        + HIT_MAX_PAGE + "), with full " + FULL_HIT_PAGE + " (max " + FULL_HIT_MAX_PAGE + ")"),
                "offset", McpTool.prop("integer", "hits to skip; default 0. count is the total"),
                "sort", McpTool.prop("string", "deadline, age, tsi, price or salary, ascending; a "
                        + "leading - for descending. Default Hattrick's order"),
                "full", McpTool.prop("boolean", "each hit as an object with seller and bidder; default "
                        + "false: arrays in hitFields order"),
                "bulk", McpTool.prop("boolean", "for scripts: every hit, compact, as a JSON file to "
                        + "download (HTTP GET of the returned download path on the MCP endpoint); "
                        + "the answer keeps resultId, count, hitFields and a 3-hit preview. Default false"),
                "queries", McpTool.prop("array", "several searches in one call, each shaped like "
                        + "the single-search arguments above; replaces them. bulk, full, limit, offset "
                        + "and sort go at the top level, not in an entry. Returns results[], "
                        + "each echoing its query; a failed one carries its own error"));
        // No required list: a single search needs ageMin, ageMax and skills, a batch needs queries,
        // and queryFrom rejects either one incomplete.
        return McpTool.schema(properties);
    }

    /** The searchable skill names, for the schema - {@code Skill} is the single source. */
    private static final String SKILL_NAMES =
            Arrays.stream(TransferSearchQuery.Skill.values())
                    .map(Enum::name)
                    .collect(Collectors.joining(", "));

    /** How many skill filters {@code transfersearch} accepts. */
    private static final int MAX_SKILL_FILTERS = 4;


    /** The query the client asked for, with the page size defaulted and capped. */
    static TransferSearchQuery queryFrom(JsonObject args) {
        final var builder = TransferSearchQuery.builder()
                .ageMin(McpJson.reqInt(args, "ageMin"))
                .ageMax(McpJson.reqInt(args, "ageMax"))
                .specialty(McpJson.optInteger(args, "specialty"))
                .nativeCountryId(McpJson.optInteger(args, "nativeCountryId"))
                .tsiMin(McpJson.optInteger(args, "tsiMin"))
                .tsiMax(McpJson.optInteger(args, "tsiMax"))
                // Passed through unconverted: transfersearch reads these in the user's currency,
                // unlike every other CHPP money value (PlayerFilterForm.maxPriceLocal).
                .priceMin(McpJson.optInteger(args, "priceMin"))
                .priceMax(McpJson.optInteger(args, "priceMax"))
                // Paging is not exposed: ItemCount comes back as -1 once more than 100 players
                // match, so a second page buys little and would eat the per session cap.
                .pageSize(Math.min(McpJson.optInt(args, "pageSize", MAX_PAGE_SIZE), MAX_PAGE_SIZE));
        applySkills(builder, McpJson.reqArray(args, "skills"));
        return builder.build();
    }

    /**
     * The {@code skills} array onto the builder's four indexed slots.
     * <p>
     * The wire shape is a list because that is what the query is - up to four filters, the first
     * one primary. The builder keeps the twelve flat setters because they mirror the CHPP
     * parameter names one for one, and {@code TransferSearchQuery} is shared with the search tab.
     */
    private static void applySkills(TransferSearchQuery.TransferSearchQueryBuilder builder,
                                    JsonArray skills) {
        if (skills.isEmpty() || skills.size() > MAX_SKILL_FILTERS) {
            throw new IllegalArgumentException("skills takes 1 to " + MAX_SKILL_FILTERS
                    + " filters, got " + skills.size() + ".");
        }
        for (int i = 0; i < skills.size(); i++) {
            if (!skills.get(i).isJsonObject()) {
                throw new IllegalArgumentException("skills[" + i + "] must be an object of the "
                        + "shape {skill, min, max}.");
            }
            final var filter = skills.get(i).getAsJsonObject();
            final var skill = parseSkill(McpJson.reqString(filter, "skill"));
            final var min = McpJson.reqInt(filter, "min");
            final var max = McpJson.reqInt(filter, "max");
            switch (i) {
                case 0 -> builder.skillType1(skill).minSkillValue1(min).maxSkillValue1(max);
                case 1 -> builder.skillType2(skill).minSkillValue2(min).maxSkillValue2(max);
                case 2 -> builder.skillType3(skill).minSkillValue3(min).maxSkillValue3(max);
                default -> builder.skillType4(skill).minSkillValue4(min).maxSkillValue4(max);
            }
        }
    }


    private static TransferSearchQuery.Skill parseSkill(String value) {
        try {
            return TransferSearchQuery.Skill.valueOf(value.toUpperCase(java.util.Locale.ENGLISH));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown skill '" + value + "'. Use one of "
                    + Arrays.toString(TransferSearchQuery.Skill.values()));
        }
    }

    /**
     * search_transfers: a new search (single or queries[]), or with resultId a page of one already
     * run. Either way the hits are kept under a resultId so the caller can page and sort them
     * without a new CHPP search - those count against the hourly cap, pages do not.
     */
    private static JsonObject searchTool(JsonObject args) throws McpToolException {
        // A bad sort is refused before the search: after it, the search would be spent and its
        // resultId lost with the error.
        final var sort = McpJson.optString(args, "sort", "");
        if (!sort.isEmpty()) {
            hitOrder(sort);
        }
        if (args.has("queries")) {
            rejectCallArgsInQueries(McpJson.reqArray(args, "queries"));
        }
        if (args.has("resultId")) {
            final var id = McpJson.reqString(args, "resultId");
            final List<XMLTransferSearchParser.Hit> hits;
            synchronized (SEARCH_RESULTS) {
                hits = SEARCH_RESULTS.get(id);
            }
            if (hits == null) {
                throw new McpToolException("No search result " + id + " - only the last "
                        + KEPT_RESULTS + " are kept, and none survive a restart. Search again.");
            }
            final var json = new JsonObject();
            json.addProperty("currency", McpJson.currency());
            addHitPage(json, id, hits, args);
            return json;
        }
        final var json = new JsonObject();
        json.addProperty("currency", McpJson.currency());
        final var hits = new ArrayList<XMLTransferSearchParser.Hit>();
        if (args.has("queries")) {
            json.add("results", searchBatch(McpJson.reqArray(args, "queries"), hits));
        } else {
            final var page = search(queryFrom(args));
            // -1 means "more than 100", Hattrick's analogy to "many"
            json.addProperty("itemCount", page.itemCount());
            json.addProperty("pageSize", page.pageSize());
            hits.addAll(page.hits());
        }
        addHitPage(json, keepResult(hits), hits, args);
        return json;
    }

    /** Arguments that shape the whole answer, not one search: only read at the top level. */
    static final List<String> CALL_ARGS = List.of("bulk", "full", "limit", "offset", "sort", "resultId");

    /**
     * Refuses a call argument inside a queries[] entry before any search runs. queryFrom ignores
     * keys it does not know, so bulk there used to be dropped silently and the answer paged inline
     * - with the searches already spent against the hourly cap.
     */
    static void rejectCallArgsInQueries(JsonArray queries) {
        for (final var element : queries) {
            if (!element.isJsonObject()) {
                continue;
            }
            final var misplaced = CALL_ARGS.stream().filter(element.getAsJsonObject()::has).toList();
            if (!misplaced.isEmpty()) {
                throw new IllegalArgumentException(String.join(", ", misplaced) + " inside a queries "
                        + "entry is not read - pass it once at the top level, next to queries. "
                        + "Nothing was searched.");
            }
        }
    }

    /** Keeps hits pageable under a new resultId; the oldest beyond {@link #KEPT_RESULTS} go. */
    private static String keepResult(List<XMLTransferSearchParser.Hit> hits) {
        synchronized (SEARCH_RESULTS) {
            final var id = "s" + (++searchResultCounter);
            SEARCH_RESULTS.put(id, List.copyOf(hits));
            return id;
        }
    }

    /** How many search results stay pageable, newest kept. */
    static final int KEPT_RESULTS = 10;
    private static final Map<String, List<XMLTransferSearchParser.Hit>> SEARCH_RESULTS =
            new LinkedHashMap<>() {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, List<XMLTransferSearchParser.Hit>> eldest) {
                    return size() > KEPT_RESULTS;
                }
            };
    private static int searchResultCounter;
    /** Compact hits per page: ~130 chars each, so the default stays near 6.5k. */
    static final int HIT_PAGE = 50;
    static final int HIT_MAX_PAGE = 70;
    /** Full hits are ~600 chars each: 15 were already ~9k. */
    static final int FULL_HIT_PAGE = 10;
    static final int FULL_HIT_MAX_PAGE = 12;
    /** The order of a compact hit's values, sent once as hitFields; the details from age on. */
    static final List<String> HIT_FIELDS = List.of("playerId", "playerName", "nativeCountryId",
            "askingPrice", "highestBid", "deadline", "age", "ageDays", "salary", "tsi", "specialty",
            "cards", "injuryLevel", "form", "stamina", "experience", "leadership", "keeper",
            "defender", "playmaker", "winger", "passing", "scorer", "setPieces");

    /** resultId, count, the sorted page of hits (compact unless full) and its offset. */
    static void addHitPage(JsonObject json, String resultId, List<XMLTransferSearchParser.Hit> all,
                           JsonObject args) {
        if (McpJson.optBool(args, "bulk", false)) {
            addBulkHits(json, resultId, all, args);
            return;
        }
        final var full = McpJson.optBool(args, "full", false);
        final var limit = Math.min(Math.max(McpJson.optInt(args, "limit",
                full ? FULL_HIT_PAGE : HIT_PAGE), 1), full ? FULL_HIT_MAX_PAGE : HIT_MAX_PAGE);
        final var offset = Math.max(McpJson.optInt(args, "offset", 0), 0);
        final var sort = McpJson.optString(args, "sort", "");
        final var sorted = new ArrayList<>(all);
        if (!sort.isEmpty()) {
            sorted.sort(hitOrder(sort));
        }
        final var hits = new JsonArray();
        sorted.stream().skip(offset).limit(limit)
                .forEach(hit -> hits.add(full ? McpJson.hit(hit) : compactHit(hit)));
        json.addProperty("resultId", resultId);
        json.addProperty("count", all.size());
        json.addProperty("offset", offset);
        json.addProperty("returned", hits.size());
        if (!full) {
            final var fields = new JsonArray();
            HIT_FIELDS.forEach(fields::add);
            json.add("hitFields", fields);
        }
        json.add("hits", hits);
    }

    /**
     * bulk: every hit, compact and sorted, as a file to download ({@link McpDownloads}) - for a
     * script, which reads it whole without it ever entering a model's context. The answer itself
     * stays small: resultId, count, hitFields, a 3-hit preview and the download path.
     */
    private static void addBulkHits(JsonObject json, String resultId, List<XMLTransferSearchParser.Hit> all,
                                    JsonObject args) {
        final var sort = McpJson.optString(args, "sort", "");
        final var sorted = new ArrayList<>(all);
        if (!sort.isEmpty()) {
            sorted.sort(hitOrder(sort));
        }
        final var fields = new JsonArray();
        HIT_FIELDS.forEach(fields::add);
        final var hits = new JsonArray();
        sorted.forEach(hit -> hits.add(compactHit(hit)));
        final var file = new JsonObject();
        file.addProperty("currency", McpJson.currency());
        file.addProperty("resultId", resultId);
        file.addProperty("count", all.size());
        file.add("hitFields", fields);
        file.add("hits", hits);

        json.addProperty("resultId", resultId);
        json.addProperty("count", all.size());
        json.add("hitFields", fields);
        final var preview = new JsonArray();
        for (int i = 0; i < Math.min(BULK_PREVIEW, hits.size()); i++) {
            preview.add(hits.get(i));
        }
        json.add("preview", preview);
        McpDownloads.attach(json, file);
    }

    static final int BULK_PREVIEW = McpDownloads.PREVIEW;

    /**
     * The sort argument: deadline, age, tsi, price (highest bid, else asking price) or salary,
     * ascending, or descending with a leading '-'. Hits without details (deadline passed) go last.
     */
    static Comparator<XMLTransferSearchParser.Hit> hitOrder(String sort) {
        final var descending = sort.startsWith("-");
        final var key = descending ? sort.substring(1) : sort;
        final java.util.function.Function<XMLTransferSearchParser.Hit, Long> value = switch (key) {
            case "deadline" -> h -> h.deadline() == null ? null : h.deadline().getInstant().toEpochMilli();
            case "age" -> h -> h.details() == null ? null : h.details().age() * 112L + h.details().ageDays();
            case "tsi" -> h -> h.details() == null ? null : (long) h.details().tsi();
            case "price" -> h -> h.highestBid() > 0 ? h.highestBid() : h.askingPrice();
            case "salary" -> h -> h.details() == null ? null : h.details().salary();
            default -> throw new IllegalArgumentException("sort takes deadline, age, tsi, price or "
                    + "salary, with a leading - for descending; had " + sort);
        };
        final Comparator<Long> direction = descending ? Comparator.reverseOrder() : Comparator.naturalOrder();
        return Comparator.comparing(value, Comparator.nullsLast(direction));
    }

    /**
     * One hit as an array in {@link #HIT_FIELDS} order. The player details come last, so a hit
     * whose deadline has passed (Hattrick then sends no details) simply ends after deadline.
     */
    static JsonArray compactHit(XMLTransferSearchParser.Hit hit) {
        final var row = new JsonArray();
        row.add(hit.playerId());
        row.add(hit.playerName());
        row.add(hit.nativeCountryId());
        row.add(McpJson.money(hit.askingPrice()));
        row.add(McpJson.money(hit.highestBid()));
        row.add(hit.deadline() == null ? null : hit.deadline().toHT().substring(0, 16));
        final var d = hit.details();
        if (d != null) {
            for (final long v : new long[]{d.age(), d.ageDays(), McpJson.money(d.salary()), d.tsi(),
                    d.specialty(), d.cards(), d.injuryLevel(), d.form(), d.stamina(), d.experience(),
                    d.leadership(), d.keeper(), d.defender(), d.playmaker(), d.winger(), d.passing(),
                    d.scorer(), d.setPieces()}) {
                row.add(v);
            }
        }
        return row;
    }

    private static XMLTransferSearchParser.Page search(TransferSearchQuery query) throws McpToolException {
        final XMLTransferSearchParser.Page page;
        try {
            page = TransferSearchService.instance().searchForAgent(query);
        } catch (TransferSearchService.SearchFailed e) {
            if (e.errorKey() != null) {
                // A query Hattrick would reject is an invalid parameter, JSON-RPC -32602, and
                // McpHttpServer maps IllegalArgumentException onto that.
                throw new IllegalArgumentException(e.getMessage());
            }
            // An exhausted cap is an expected refusal the model must be able to read and act on,
            // so it comes back as an isError tool result instead.
            throw new McpToolException(e.getMessage());
        }
        final var observedAt = HODateTime.now();
        for (final var hit : page.hits()) {
            DBManager.instance().storeTransferListing(TransferListing.fromHit(hit, observedAt));
        }
        return page;
    }

    /**
     * A sweep of {@link #search} calls in one round trip. Each query is independent: one hitting
     * the hourly cap or an invalid range reports its own {@code error} rather than losing the
     * queries already answered, since discarding those would cost the caller exactly the round
     * trips this tool exists to save. The hits go into {@code hits}, a player found twice once.
     */
    private static JsonArray searchBatch(JsonArray queries, List<XMLTransferSearchParser.Hit> hits) {
        if (queries.isEmpty()) {
            throw new IllegalArgumentException("queries is empty - pass at least one search.");
        }
        final var seen = new java.util.HashSet<Integer>();
        final var results = new JsonArray();
        for (final var element : queries) {
            final var item = new JsonObject();
            item.add("query", element.deepCopy());
            try {
                if (!element.isJsonObject()) {
                    throw new IllegalArgumentException(
                            "Each entry in queries must be an object, got: " + element);
                }
                final var page = search(queryFrom(element.getAsJsonObject()));
                item.addProperty("itemCount", page.itemCount());
                item.addProperty("hitCount", page.hits().size());
                page.hits().stream().filter(h -> seen.add(h.playerId())).forEach(hits::add);
            } catch (IllegalArgumentException | McpToolException e) {
                item.addProperty("error", e.getMessage());
            }
            results.add(item);
        }
        return results;
    }

    private static JsonObject teamTransfers(JsonObject args) throws McpToolException {
        final var teamId = McpJson.model().getBasics().getTeamId();
        final var season = McpJson.optInt(args, "season", 0);
        final var direction = McpJson.optString(args, "direction", "").toLowerCase(Locale.ENGLISH);
        final var bought = direction.isEmpty() || direction.equals("buy");
        final var sold = direction.isEmpty() || direction.equals("sell");

        // ponytail: whole history is loaded and player-info-hydrated before the page is sliced,
        // same as HO's own Transfers tab. Push LIMIT/OFFSET into TransferTable if a team's history
        // ever gets big enough to matter.
        final var all = DBManager.instance().getTransfers(season, bought, sold);
        // bulk: every transfer into the download, so no page.
        final var bulk = McpJson.optBool(args, "bulk", false);
        final var offset = bulk ? 0 : Math.max(McpJson.optInt(args, "offset", 0), 0);
        final var limit = bulk ? all.size() : Math.min(Math.max(McpJson.optInt(args, "limit", 20), 1), 100);
        final var page = offset >= all.size() ? List.<PlayerTransfer>of()
                : all.subList(offset, Math.min(offset + limit, all.size()));

        final var transfers = new JsonArray();
        for (final var t : page) {
            final var isBuy = t.getBuyerid() == teamId;
            final var json = new JsonObject();
            json.addProperty("transferId", t.getTransferId());
            json.addProperty("date", t.getDate() == null ? null : t.getDate().toHT());
            json.addProperty("week", t.getWeek());
            json.addProperty("season", t.getSeason());
            json.addProperty("direction", isBuy ? "buy" : "sell");
            json.addProperty("playerId", t.getPlayerId());
            json.addProperty("playerName", t.getPlayerName());
            json.addProperty("price", McpJson.money(t.getPrice()));
            json.addProperty("tsi", t.getTsi());
            json.addProperty("counterpartyTeamId", isBuy ? t.getSellerid() : t.getBuyerid());
            json.addProperty("counterpartyTeamName", isBuy ? t.getSellerName() : t.getBuyerName());
            json.addProperty("motherClubFee", McpJson.money(t.getMotherClubFee()));
            json.addProperty("previousClubCommission", McpJson.money(t.getPreviousClubFee()));
            // HO's own snapshot of him: the last download before a sale, the first after a buy.
            final var snapshot = t.getDate() == null ? null
                    : DBManager.instance().getSpielerAtDate(t.getPlayerId(), t.getDate().toDbTimestamp());
            if (snapshot != null) {
                json.addProperty("specialty", snapshot.getSpecialty());
                final var skills = new JsonObject();
                playerSkillLevels(snapshot).forEach(skills::addProperty);
                skills.addProperty("setPieces", snapshot.getValue4Skill(core.constants.player.PlayerSkill.SETPIECES));
                json.add("skills", skills);
            }
            transfers.add(json);
        }

        final var json = new JsonObject();
        json.addProperty("currency", McpJson.currency());
        json.addProperty("totalCount", all.size());
        json.addProperty("offset", offset);
        json.addProperty("returned", transfers.size());
        json.addProperty("hasMore", offset + transfers.size() < all.size());
        json.addProperty("totalSpent", McpJson.money(DBManager.instance().getSumTransferPrices(teamId, false)));
        json.addProperty("totalEarned", McpJson.money(DBManager.instance().getSumTransferPrices(teamId, true)));
        json.add("transfers", transfers);
        return bulk ? McpDownloads.bulk(json, "transfers") : json;
    }

    /** Both skill windows alike and an age window in whole years - see the full overload. */
    static TransferSearchQuery comparableQuery(Player player, int ageTolerance, int skillTolerance) {
        return comparableQuery(player, ageTolerance, skillTolerance, skillTolerance, null);
    }

    /**
     * The market query that describes a player: his age window and his two highest positional
     * skills. Deliberately no specialty: every live hit is stored and becomes a comparable, so a
     * specialty filter here would fill the corpus with the target's own specialty only, and the
     * soft {@link #specialtyWeight} would have nothing different left to weigh.
     * <p>
     * primarySkillTolerance widens the best skill's window, skillTolerance the second's: one level
     * off in the primary skill moves the price far more than in the secondary. Given
     * ageWindowDays, the years are the ones that window touches and ageTolerance is ignored;
     * Hattrick searches whole years only, so the caller cuts the hits to the days itself.
     * <p>
     * Clamped so that Hattrick accepts it - min and max may be at most 3 levels apart, keeper /
     * set pieces carry a minimum level, and an age window reaching down to 17 or 18 may span at
     * most a year.
     */
    static TransferSearchQuery comparableQuery(Player player, int ageTolerance, int primarySkillTolerance,
                                               int skillTolerance, Integer ageWindowDays) {
        final var ranked = POSITIONAL_SKILLS.stream()
                .sorted(Comparator.comparingInt(
                        (TransferSearchQuery.Skill s) -> player.getValue4Skill(s.toPlayerSkill())).reversed())
                .toList();
        final var first = ranked.get(0);
        // A keeper's outfield skills are noise - a keeper 13 has 0-4 in everything else, so a
        // second window (which for an all-zero profile is an arbitrary skill at 0-2) only drops
        // real keepers from the pool. Keeper is his whole archetype.
        final var second = first == TransferSearchQuery.Skill.KEEPER ? null : ranked.get(1);

        // maxLevel() clamps the window to the 3 levels Hattrick accepts, so a wide tolerance
        // only widens it downwards.
        final var primary = Math.min(Math.max(primarySkillTolerance, 0), 2);
        final var tolerance = Math.min(Math.max(skillTolerance, 0), 2);
        final int lowest;
        final int highest;
        if (ageWindowDays != null) {
            final var ageDays = totalAgeDays(player.getAge(), player.getAgeDays());
            final var window = Math.max(ageWindowDays, 0);
            lowest = Math.max(ageDays - window, 0) / HATTRICK_YEAR_DAYS;
            highest = (ageDays + window) / HATTRICK_YEAR_DAYS;
        } else {
            lowest = player.getAge() - ageTolerance;
            highest = player.getAge() + ageTolerance;
        }
        final var ageMin = Math.max(lowest, 17);
        // Hattrick rejects a young age window wider than a year, so clamp it like the skill windows.
        final var ageMax = Math.max(ageMin <= 18 ? Math.min(highest, ageMin + 1) : highest, ageMin);
        return TransferSearchQuery.builder()
                .ageMin(ageMin)
                .ageMax(ageMax)
                .skillType1(first)
                .minSkillValue1(minLevel(first, player, primary))
                .maxSkillValue1(maxLevel(first, player, primary))
                .skillType2(second)
                .minSkillValue2(second == null ? null : minLevel(second, player, tolerance))
                .maxSkillValue2(second == null ? null : maxLevel(second, player, tolerance))
                .pageSize(MAX_PAGE_SIZE)
                .build();
    }

    /**
     * Whether a comparable's age, as observed, lies within ageWindowDays of the target's. A
     * listing without days only knows its year, so it cannot be placed inside a window of days.
     */
    static boolean withinAgeWindow(TransferListing listing, int targetAgeDays, int ageWindowDays) {
        return listing.getAgeDays() != null
                && Math.abs(totalAgeDays(listing.getAge(), listing.getAgeDays()) - targetAgeDays) <= ageWindowDays;
    }

    /** The window's lower bound, lifted to whatever minimum Hattrick enforces for the skill. */
    private static int minLevel(TransferSearchQuery.Skill skill, Player player, int tolerance) {
        final var minimum = switch (skill) {
            case SETPIECES, LEADERSHIP, EXPERIENCE -> 5;
            case KEEPER -> 3;
            default -> 0;
        };
        return Math.max(player.getValue4Skill(skill.toPlayerSkill()) - tolerance, minimum);
    }

    /**
     * The window's upper bound, kept within the 3 levels of the lower one Hattrick accepts and
     * never below it (a keeper 2 at tolerance 0 still searches keeper 3).
     */
    private static int maxLevel(TransferSearchQuery.Skill skill, Player player, int tolerance) {
        final var value = player.getValue4Skill(skill.toPlayerSkill());
        final var min = minLevel(skill, player, tolerance);
        return Math.max(Math.min(value + tolerance, min + 3), min);
    }
}
