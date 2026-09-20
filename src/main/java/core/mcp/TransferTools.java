package core.mcp;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import core.db.DBManager;
import core.db.TransferBid;
import core.db.TransferListing;
import core.file.xml.XMLCurrentBidsParser;
import core.file.xml.XMLTransferSearchParser;
import core.model.player.Player;
import core.net.OnlineWorker;
import core.net.TransferListingResolver;
import core.net.TransferSearchQuery;
import core.net.ChppWriteService;
import core.net.TransferSearchService;
import core.util.HODateTime;
import core.util.HOLogger;
import module.transfer.PlayerTransfer;
import module.transfer.scout.ScoutEintrag;
import module.transfer.scout.ScoutList;
import module.transfer.search.TransferSearchConverter;

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
                                + "Narrow rather than page - there is no exact total above 100 "
                                + "matches. Capped per HO session. Every hit is stored and feeds "
                                + "estimate_market_value.",
                        searchSchema(),
                        args -> search(queryFrom(args))),
                new McpTool("search_transfers_batch",
                        "Run several search_transfers queries in one call - for sweeping "
                                + "specialty x age-window combos Hattrick's own span limits force "
                                + "apart. Same CHPP calls and per-session cap as calling "
                                + "separately; only saves round trips. Each result echoes its "
                                + "query; a failed one (cap exhausted, invalid range) reports its "
                                + "own error without stopping the rest.",
                        McpTool.schema(McpTool.properties(
                                        "queries", McpTool.prop("array", "the searches to run, "
                                                + "each shaped exactly like search_transfers's own "
                                                + "arguments (ageMin, ageMax, skills required; "
                                                + "specialty, nativeCountryId, tsiMin, tsiMax, "
                                                + "priceMin, priceMax, pageSize optional)")),
                                "queries"),
                        TransferTools::searchBatch),
                new McpTool("get_team_transfers",
                        "The managed team's own transfer history, newest first, with price, TSI at "
                                + "transfer time and the mother-club and previous-club fees. A local "
                                + "read - HO refreshes it on every team download. Returns one page "
                                + "(default 20); page back with offset.",
                        McpTool.schema(McpTool.properties(
                                "season", McpTool.prop("integer", "Hattrick season number; omit for the full history"),
                                "direction", McpTool.prop("string", "buy, sell, or omit for both"),
                                "limit", McpTool.prop("integer", "how many transfers to return, newest first; default 20, max 100"),
                                "offset", McpTool.prop("integer", "how many of the newest transfers to skip, for paging; default 0"))),
                        TransferTools::teamTransfers),
                new McpTool("place_bid",
                        "Place a transfer bid. TWO STEPS so a stray call cannot spend money: call "
                                + "with playerId + bidAmount for a preview and a confirmToken, then "
                                + "again with the same playerId and that token to place it. The "
                                + "second call leaves the machine and commits money in Hattrick. "
                                + "Amounts are in the user's currency.",
                        McpTool.schema(McpTool.properties(
                                        "playerId", McpTool.prop("integer", "the player to bid on"),
                                        "bidAmount", McpTool.prop("integer",
                                                "the bid, in the user's currency (required in step 1)"),
                                        "maxBidAmount", McpTool.prop("integer",
                                                "optional autobid ceiling"),
                                        "confirmToken", McpTool.prop("string",
                                                "the token from step 1 (required in step 2, omit in step 1)")),
                                "playerId"),
                        TransferTools::placeBid),
                new McpTool("get_open_bids",
                        "Transfers this team has an active bid on, from Hattrick's own tracking - "
                                + "so it sees bids placed on the website too. Each row says whether "
                                + "you are the leading bidder, plus what HO holds locally: "
                                + "askingPrice, and once finished salePrice, outcome and ourBid. "
                                + "includeAll returns every tracked transfer with its raw "
                                + "trackingTypeId.",
                        McpTool.schema(McpTool.properties(
                                "includeAll", McpTool.prop("boolean",
                                        "return every tracked transfer, not just your open bids; "
                                                + "default false"))),
                        TransferTools::openBids),
                new McpTool("add_to_scout",
                        "Put players on HO's OWN local shortlist (the Transfers -> TransferScout "
                                + "tab) - not a Hattrick bookmark; get_open_bids reads Hattrick's "
                                + "separate list. Each player must have been seen by an earlier "
                                + "search_transfers. Adding one already listed replaces his entry. "
                                + "One CHPP call per player for character traits, echoed back per "
                                + "player (same shape as list_scout) - no read-back call needed.",
                        McpTool.schema(McpTool.properties(
                                        "players", McpTool.prop("array",
                                                "the players to add, either bare player ids or "
                                                        + "{playerId, note} objects; the note is "
                                                        + "free text kept in the scout entry's "
                                                        + "notes column, e.g. why he is a target")),
                                "players"),
                        TransferTools::addToScout),
                new McpTool("remove_from_scout",
                        "Take players off HO's local scout shortlist. This also stops the "
                                + "re-listing watch each entry costs on every team download, which "
                                + "is the reason to prune a list grown past what is actually being "
                                + "tracked.",
                        McpTool.schema(McpTool.properties(
                                        "playerIds", McpTool.prop("array", "the player ids to remove")),
                                "playerIds"),
                        TransferTools::removeFromScout),
                new McpTool("list_scout",
                        "Read back HO's local scout shortlist, with the market data and skills "
                                + "frozen as they were when each player was added. price and "
                                + "deadline are that snapshot; askingPrice, highestBid and "
                                + "observedAt come from the newest stored observation. salePrice and "
                                + "soldAt fill in once the watched transfer completed. Character "
                                + "traits are 0-5, null where never fetched.",
                        McpTool.schema(McpTool.properties()),
                        args -> listScout()),
                new McpTool("estimate_market_value",
                        "Value one of your players, or via profile a player outside the squad, "
                                + "against stored transfer listings. Returns the matching listings; "
                                + "reason over those, not the median in `estimate`. basis says real "
                                + "sales or asking prices, which run high. Also searches today's "
                                + "market first and stores the hits - one of the session's capped "
                                + "searches; pass live: false to read the stored corpus only, for "
                                + "free.",
                        McpTool.schema(McpTool.properties(
                                        "playerId", McpTool.prop("integer",
                                                "the player to value - a squad player id"),
                                        "profile", McpTool.prop("object",
                                                "value a hypothetical player instead of a squad "
                                                        + "player: {age, skills: {keeper|defending|"
                                                        + "playmaking|passing|winger|scoring -> "
                                                        + "level, at least one}, plus optional "
                                                        + "playerName, ageDays, specialty, tsi, "
                                                        + "agreeability, aggressiveness, honesty}. "
                                                        + "Omitting specialty means unknown (no "
                                                        + "specialty filter applied, not the same as "
                                                        + "confirmed none) - pass specialty: 0 only "
                                                        + "when you actually know the player has none. "
                                                        + "Unset skills default to level 0. Provide "
                                                        + "playerId or profile, not both. Also "
                                                        + "optional: playerId (this player's real "
                                                        + "Hattrick id, e.g. from get_team_transfers "
                                                        + "or get_opponent_squad - bookkeeping only, "
                                                        + "never used to look him up in the squad) "
                                                        + "and salePrice - given both, this profile "
                                                        + "is recorded into HO's local transfer-"
                                                        + "listing corpus as a real data point, so a "
                                                        + "player who left the squad without ever "
                                                        + "being tracked still teaches future "
                                                        + "valuations something."),
                                        "ageTolerance", McpTool.prop("integer", "age window in years, default 1"),
                                        "skillTolerance", McpTool.prop("integer", "skill window in levels, default 1"),
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
                                                        + "as before."),
                                        "observedWithinDays", McpTool.prop("integer",
                                                "ignore listings older than this many days, default "
                                                        + DEFAULT_CORPUS_DAYS + " (one Hattrick season)"))),
                        TransferTools::estimateMarketValue),
                new McpTool("backfill_character",
                        "Fetch character (agreeability/aggressiveness/honesty) for corpus players "
                                + "estimate_market_value has never had it for, oldest observation "
                                + "first. One CHPP call per player, capped at "
                                + TransferListingResolver.MAX_CHARACTER_BACKFILL
                                + " per call regardless of maxPlayers, and time-bounded - the same "
                                + "pass also runs automatically after every team download. Call "
                                + "again if moreLikely comes back true.",
                        McpTool.schema(McpTool.properties(
                                "maxPlayers", McpTool.prop("integer",
                                        "how many players to look up this call; default and hard "
                                                + "cap " + TransferListingResolver.MAX_CHARACTER_BACKFILL))),
                        TransferTools::backfillCharacter)
        );
    }

    // ------------------------------------------------------------------ market value

    /** One Hattrick season. Older listings priced a market that has moved on. */
    private static final int DEFAULT_CORPUS_DAYS = 112;

    /**
     * Below this many resolved sales the observed listings are the better estimate, thin as they
     * are: one real sale is a single draw, twenty asking prices at least bracket the market.
     * ponytail: a flat threshold, not a weighting. Blend the two if the corpus ever gets big
     * enough for the difference to matter.
     */
    private static final int MIN_RESOLVED_SAMPLE = 3;

    /** How many comparable rows come back as evidence. Enough to reason over, small enough to read. */
    private static final int MAX_COMPARABLES = 40;

    /** A Hattrick season year is 112 days (ageDays runs 0-111) - see docs/current.md. Used to turn
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
     */
    private static final double FALLBACK_MAX_CLIMB = 1.0;
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

    /** The third-highest value, or null if fewer than three non-null values are present - a listing
     * with no skill data at all (a bid-only observation) rather than a real, low third skill. */
    static Integer thirdHighestOrNull(java.util.Collection<Integer> levels) {
        final var sorted = levels.stream().filter(java.util.Objects::nonNull)
                .sorted(Comparator.reverseOrder()).toList();
        return sorted.size() >= 3 ? sorted.get(2) : null;
    }

    /**
     * The three independent similarity kernels {@link #comparableWeight} multiplies together, kept
     * separate so a caller can see *why* a comparable was down-weighted rather than only by how much.
     */
    record WeightBreakdown(double age, double skill, double character) {
        /** The combined weight {@link #comparableWeight} returns - floored at {@link #WEIGHT_FLOOR}. */
        double combined() {
            return Math.max(WEIGHT_FLOOR, age * skill * character);
        }
    }

    /**
     * The age/third-skill/character similarity kernels for one comparable, each defaulting to a
     * neutral 1.0 when the listing lacks the data to judge it rather than penalising the gap - a bid-
     * only observation has no skills at all, and most of the corpus still has no character (see the
     * backfill_character work in docs/current.md).
     */
    static WeightBreakdown comparableWeightBreakdown(TransferListing listing, int targetAgeDays,
                                            double ageSigma, Integer targetCSkill, int[] targetChar) {
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

        return new WeightBreakdown(ageWeight, skillWeight, charWeight);
    }

    /** How much this comparable should count toward the weighted median - see {@link WeightBreakdown}. */
    static double comparableWeight(TransferListing listing, int targetAgeDays,
                                            double ageSigma, Integer targetCSkill, int[] targetChar) {
        return comparableWeightBreakdown(listing, targetAgeDays, ageSigma, targetCSkill, targetChar)
                .combined();
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

    /**
     * {@code comparables}, with the rows {@code sample} was actually computed from surfacing
     * first - a stable sort, so each of the two groups keeps {@code comparables}' own newest-
     * first order. Without this, a caller who trusts {@code basis}/{@code sampleSize} could find
     * none of the resolved sales they describe within the {@link #MAX_COMPARABLES}-row window: a
     * sale's observedAt freezes once it resolves, so live re-searches of asks that were never
     * part of the sample keep pushing their own observedAt ahead of it.
     */
    static List<TransferListing> evidenceOrder(List<TransferListing> comparables,
                                                List<PricedComparable> sample) {
        final var sampleListings = sample.stream()
                .map(PricedComparable::listing)
                .collect(Collectors.toSet());
        return comparables.stream()
                .sorted(Comparator.comparing(l -> !sampleListings.contains(l)))
                .toList();
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

    static JsonObject estimateMarketValue(JsonObject args) throws McpToolException {
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
        final var query = comparableQuery(player,
                McpJson.optInt(args, "ageTolerance", 1),
                McpJson.optInt(args, "skillTolerance", 1));
        final var days = Math.max(McpJson.optInt(args, "observedWithinDays", DEFAULT_CORPUS_DAYS), 1);
        final var since = new HODateTime(HODateTime.now().getInstant()
                .minus(java.time.Duration.ofDays(days)));

        final var live = runLiveSearch(args, query);
        final var liveSearch = live.hits();
        final var liveSearchSkipped = live.skippedNote();

        // corpusSpecialtyFilter, not player.getSpecialty(): a squad player's specialty is always
        // definitively known, but a profile that never mentions specialty could mean either "HO
        // never got a reading for him" (a departed player, most of the time) or "confirmed none" -
        // conflating those two into player's own 0 default would wrongly exclude specialty-having
        // comparables for a player whose specialty is simply unknown, not none. The live CHPP
        // search above still asks for "any specialty" either way (query.specialty() is null there -
        // Hattrick's API has no "must have none" filter), but the local corpus read below can and
        // should be fairer about it - see loadComparables's doc for the null/0/positive distinction.
        final var observations = DBManager.instance().loadComparableTransferListings(
                query.ageMin(), query.ageMax(),
                column(query.skillType1()), query.minSkillValue1(), query.maxSkillValue1(),
                query.skillType2() == null ? null : column(query.skillType2()),
                query.skillType2() == null ? 0 : query.minSkillValue2(),
                query.skillType2() == null ? 0 : query.maxSkillValue2(),
                corpusSpecialtyFilter(hasProfile, profile, player), since);


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
                .sorted(Comparator.comparing(TransferTools::observedAt).reversed())
                .toList();

        // The target's own features, computed once and shared by every comparable's weight below.
        final var playerLevels = playerSkillLevels(player);
        final var playerCSkill = thirdHighestOrNull(playerLevels.values());
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
                        comparableWeight(l, playerAgeDays, ageSigma, playerCSkill, playerChar)))
                .sorted(Comparator.comparingLong(PricedComparable::price))
                .toList();
        // An expired listing keeps its last observed state, so the highest bid is the better
        // reading of what it was worth whenever bidding got past the asking price.
        final var asksPriced = comparables.stream()
                .filter(l -> l.getSalePrice() == null)
                .map(l -> new PricedComparable(l, Math.max(l.getAskingPrice(), l.getHighestBid()),
                        comparableWeight(l, playerAgeDays, ageSigma, playerCSkill, playerChar)))
                .filter(pc -> pc.price() > 0)
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
        json.addProperty("basis", useSales ? "resolved_sales" : "observed_listings");
        json.addProperty("sampleSize", sample.size());
        json.addProperty("resolvedSalesAvailable", salesPriced.size());
        json.addProperty("observedListingsAvailable", asksPriced.size());
        json.addProperty("comparableQuery", query.toQueryString());
        json.addProperty("observedWithinDays", days);
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
        // weight is the same combined similarity weight the estimate was computed with, so a row
        // that barely counted is visible as such rather than looking like equal evidence.
        // weightBreakdown says *why*: which of age/skill/character similarity - each 0-1, 1 meaning
        // no gap at all - pulled it down, rather than leaving that to be inferred from the raw
        // numbers next to the target player's own.
        // adjustedPrice is what this row would contribute to the estimate: the sale price unchanged,
        // or an ask/bid nudged by fallbackClimb for time still left on the clock - so the climb
        // documented in estimateNote/note can be checked per row instead of only taken on faith.
        // The sample the estimate above was actually computed from surfaces first, so a caller
        // who trusts basis/sampleSize sees those rows within the cap - see evidenceOrder's doc.
        final var evidence = new JsonArray();
        evidenceOrder(comparables, sample).stream()
                .limit(MAX_COMPARABLES)
                .forEach(l -> {
                    final var row = topSkillsOnly(McpJson.listing(l), listingSkillLevels(l));
                    final var breakdown = comparableWeightBreakdown(
                            l, playerAgeDays, ageSigma, playerCSkill, playerChar);
                    row.addProperty("weight", round2(breakdown.combined()));
                    final var weightBreakdown = new JsonObject();
                    weightBreakdown.addProperty("age", round2(breakdown.age()));
                    weightBreakdown.addProperty("skill", round2(breakdown.skill()));
                    weightBreakdown.addProperty("character", round2(breakdown.character()));
                    row.add("weightBreakdown", weightBreakdown);
                    final var rawPrice = l.getSalePrice() != null ? l.getSalePrice()
                            : Math.max(l.getAskingPrice(), l.getHighestBid());
                    final var adjustedPrice = l.getSalePrice() != null ? rawPrice
                            : Math.round(rawPrice * fallbackClimb(l));
                    row.addProperty("adjustedPrice", McpJson.money(adjustedPrice));
                    evidence.add(row);
                });
        json.add("comparables", evidence);
        json.addProperty("comparablesReturned", evidence.size());
        json.addProperty("comparablesMatched", comparables.size());
        // How many rows those distinct players were collapsed from, so the gap is visible.
        json.addProperty("observationsMatched", observations.size());

        if (sample.isEmpty()) {
            json.add("estimate", com.google.gson.JsonNull.INSTANCE);
            json.addProperty("note", "Nothing comparable in the corpus yet. Widen ageTolerance / "
                    + "skillTolerance, or run search_transfers over this profile - every hit it "
                    + "returns is stored and becomes a comparable.");
            return json;
        }

        // Winsorized against the sample's own weighted center before the weighted median is
        // computed - see clampedWeightedMedian's doc. The reported range below is the real,
        // un-clamped spread.
        json.addProperty("estimate", McpJson.money(clampedWeightedMedian(sample)));
        json.addProperty("estimateNote", "Weighted median over " + sample.size() + " comparables - "
                + "weighted by closeness in age (day precision), third-best skill and character "
                + "where known, and clamped against a wild outlier - as a starting anchor only. It "
                + "still ignores wage, TSI, form and how stale each observation is. The comparables "
                + "array carries those plus each row's own weight (weightBreakdown says which of "
                + "age/skill/character pulled it down) and adjustedPrice (what it actually "
                + "contributed, after the time-left climb below where that applies) - check the "
                + "number instead of trusting it. Each row lists its three best positional skills "
                + "plus set pieces; the positional skills below the top three are not returned.");
        final var range = new JsonArray();
        range.add(McpJson.money(sample.get(0).price()));
        range.add(McpJson.money(sample.get(sample.size() - 1).price()));
        json.add("range", range);
        if (!useSales) {
            json.addProperty("note", salesPriced.isEmpty()
                    ? "No resolved sale prices for this profile yet, so this is what sellers asked "
                    + "and bidders bid, nudged up for time still left on the clock at the time each "
                    + "was observed (a snapshot taken early in a listing's life undersells it) - "
                    + "still an approximation, not what players went for."
                    : salesPriced.size() + " resolved sale(s) is too thin a sample, so asking prices "
                    + "and bids were used instead, with the same time-left adjustment - read the "
                    + "estimate as approximate.");
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

    /** {@link #search}'s hits (or null), and null unless a search-cap refusal was swallowed. */
    private record LiveSearchOutcome(JsonObject hits, String skippedNote) {}

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
            return new LiveSearchOutcome(search(query), null);
        }
        try {
            return new LiveSearchOutcome(search(query), null);
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
     * Fill in character for corpus players that have never had it fetched. Thin wrapper: the actual
     * lookup is {@link TransferListingResolver#backfillCharacter}, shared with the same pass running
     * automatically after every team download, so both respect the same time budget rather than a
     * tool call being able to run longer than a download-triggered one would.
     */
    private static JsonObject backfillCharacter(JsonObject args) {
        final var max = Math.min(Math.max(
                McpJson.optInt(args, "maxPlayers", TransferListingResolver.MAX_CHARACTER_BACKFILL), 0),
                TransferListingResolver.MAX_CHARACTER_BACKFILL);
        final var result = TransferListingResolver.backfillCharacter(max);
        final var json = new JsonObject();
        json.addProperty("checked", result.checked());
        json.addProperty("updated", result.updated());
        // A full batch means the cap or the time budget was hit, not necessarily that the corpus is
        // exhausted - call again to keep going.
        json.addProperty("moreLikely", result.checked() >= max);
        return json;
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

    /** {@code SCORING} -> {@code "scoring"}: the name get_player reports the skill under. */
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
            if (m.transferListed()) {
                DBManager.instance().storeTransferListing(
                        TransferListing.fromPlayerDetails(m, HODateTime.now()));
            }
        } catch (Exception e) {
            HOLogger.instance().error(TransferTools.class, e);
        }
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
            // making the caller round-trip through list_scout to read what add_to_scout just fetched.
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
        final var requested = McpJson.reqArray(args, "playerIds");
        if (requested.isEmpty()) {
            throw new IllegalArgumentException("playerIds is empty - pass at least one player id.");
        }
        final var wanted = new java.util.LinkedHashSet<Integer>();
        requested.forEach(e -> wanted.add(e.getAsInt()));

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

    private static JsonObject listScout() throws McpToolException {
        final var out = LineupTools.onEdt(() -> {
            final var players = new JsonArray();
            for (final var entry : ScoutList.entries()) {
                players.add(McpJson.scoutEntry(entry));
            }
            final var json = new JsonObject();
            json.addProperty("currency", McpJson.currency());
            json.addProperty("count", players.size());
            json.add("players", players);
            return json;
        });

        // The scout table has one price column and it holds max(asking price, highest bid), so it
        // stops saying what the seller asked the moment bidding passes it. The corpus keeps the two
        // apart - read them back off the EDT, where the database reads belong. Missing for an entry
        // typed into the TransferScout tab by hand.
        //
        // This is the NEWEST observation of the player, not necessarily the one add_to_scout built
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
        return out;
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
        if (!includeAll) {
            out.addProperty("note", "leading_bid / losing_bid are inferred from trackingTypeId "
                    + "2 / 8; pass includeAll to see every tracked transfer.");
        }
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
                DBManager.instance().storeTransferListing(TransferListing.fromBidItem(item, now));
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
        // The model bids in the user's currency; placeBid is sent in swedish krona - the CHPP-wide
        // money unit HO already converts transfersearch priceMin/priceMax to. Not confirmed against
        // a captured placeBid request (docs/current.md); adjust if a real response says otherwise.
        final var bidAmountLocal = McpJson.reqLong(args, "bidAmount");
        final Long maxBidAmountLocal = McpJson.optLong(args, "maxBidAmount");
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
        out.addProperty("deadline", result.deadline());
        out.addProperty("bidderTeamName", result.bidderTeamName());
        out.addProperty("note", "Bid placed. Poll get_player or search_transfers for the updated "
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
                "priceMin", McpTool.prop("integer", "minimum asking price, in the user's currency"),
                "priceMax", McpTool.prop("integer", "maximum asking price, in the user's currency"),
                "pageSize", McpTool.prop("integer", "results per page, default and maximum 100"));
        return McpTool.schema(properties, "ageMin", "ageMax", "skills");
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
                // The form takes the user's currency; the CHPP query is in swedish krona.
                .priceMin(McpJson.swedishKrona(McpJson.optInteger(args, "priceMin")))
                .priceMax(McpJson.swedishKrona(McpJson.optInteger(args, "priceMax")))
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

    private static JsonObject search(TransferSearchQuery query) throws McpToolException {
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
        final var hits = new JsonArray();
        for (final var hit : page.hits()) {
            DBManager.instance().storeTransferListing(TransferListing.fromHit(hit, observedAt));
            hits.add(McpJson.hit(hit));
        }

        final var json = new JsonObject();
        json.addProperty("currency", McpJson.currency());
        // -1 means "more than 100", Hattrick's analogy to "many"
        json.addProperty("itemCount", page.itemCount());
        json.addProperty("pageSize", page.pageSize());
        json.add("hits", hits);
        return json;
    }

    /**
     * A sweep of {@link #search} calls in one round trip. Each query is independent: one hitting
     * the per-session cap or an invalid range reports its own {@code error} rather than losing the
     * queries already answered, since discarding those would cost the caller exactly the round
     * trips this tool exists to save.
     */
    private static JsonObject searchBatch(JsonObject args) {
        final var queries = McpJson.reqArray(args, "queries");
        if (queries.isEmpty()) {
            throw new IllegalArgumentException("queries is empty - pass at least one search.");
        }
        final var results = new JsonArray();
        for (final var element : queries) {
            final var item = new JsonObject();
            item.add("query", element.deepCopy());
            try {
                if (!element.isJsonObject()) {
                    throw new IllegalArgumentException(
                            "Each entry in queries must be an object, got: " + element);
                }
                final var result = search(queryFrom(element.getAsJsonObject()));
                for (final var entry : result.entrySet()) {
                    if (!entry.getKey().equals("currency")) {
                        item.add(entry.getKey(), entry.getValue());
                    }
                }
            } catch (IllegalArgumentException | McpToolException e) {
                item.addProperty("error", e.getMessage());
            }
            results.add(item);
        }
        final var json = new JsonObject();
        json.addProperty("currency", McpJson.currency());
        json.add("results", results);
        return json;
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
        final var offset = Math.max(McpJson.optInt(args, "offset", 0), 0);
        final var limit = Math.min(Math.max(McpJson.optInt(args, "limit", 20), 1), 100);
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
        return json;
    }

    /**
     * The market query that describes a player: his age window, his two highest positional skills
     * and his specialty.
     * <p>
     * Clamped so that Hattrick accepts it - min and max may be at most 3 levels apart, keeper /
     * set pieces carry a minimum level, and an age window reaching down to 17 or 18 may span at
     * most a year.
     */
    static TransferSearchQuery comparableQuery(Player player, int ageTolerance, int skillTolerance) {
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
        final var tolerance = Math.min(Math.max(skillTolerance, 0), 2);
        final var ageMin = Math.max(player.getAge() - ageTolerance, 17);
        // Hattrick rejects a young age window wider than a year, so clamp it like the skill windows.
        final var ageMax = ageMin <= 18
                ? Math.min(player.getAge() + ageTolerance, ageMin + 1)
                : player.getAge() + ageTolerance;
        return TransferSearchQuery.builder()
                .ageMin(ageMin)
                .ageMax(ageMax)
                .skillType1(first)
                .minSkillValue1(minLevel(first, player, tolerance))
                .maxSkillValue1(maxLevel(first, player, tolerance))
                .skillType2(second)
                .minSkillValue2(second == null ? null : minLevel(second, player, tolerance))
                .maxSkillValue2(second == null ? null : maxLevel(second, player, tolerance))
                .specialty(player.getSpecialty() > 0 ? player.getSpecialty() : null)
                .pageSize(MAX_PAGE_SIZE)
                .build();
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

    /** The window's upper bound, kept within the 3 levels of the lower one Hattrick accepts. */
    private static int maxLevel(TransferSearchQuery.Skill skill, Player player, int tolerance) {
        final var value = player.getValue4Skill(skill.toPlayerSkill());
        return Math.min(value + tolerance, minLevel(skill, player, tolerance) + 3);
    }
}
