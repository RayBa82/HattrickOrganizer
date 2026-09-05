package core.mcp;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import core.db.DBManager;
import core.db.TransferBid;
import core.db.TransferListing;
import core.file.xml.XMLCurrentBidsParser;
import core.file.xml.XMLTransferSearchParser;
import core.model.player.Player;
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

    private static final int DEFAULT_PAGE_SIZE = 50;
    private static final int MAX_PAGE_SIZE = 100;     // the CHPP maximum

    /** How long a losing bid keeps showing in the default view after its deadline. */
    private static final java.time.Duration LOSING_BID_GRACE = java.time.Duration.ofHours(24);

    private TransferTools() {
    }

    public static List<McpTool> tools() {
        return List.of(
                new McpTool("search_transfers",
                        "Search the Hattrick transfer market. Age and one skill are mandatory. Skill "
                                + "names are KEEPER, STAMINA, SETPIECES, DEFENDING, SCORING, WINGER, "
                                + "PASSING, PLAYMAKING, LEADERSHIP, EXPERIENCE. Hattrick rejects a "
                                + "skill window whose min and max are more than 3 levels apart, "
                                + "set pieces / leadership / "
                                + "experience below level 5 and keeper below level 3. An age range "
                                + "that includes 17 or 18 may span at most one year (17-18 is fine, "
                                + "17-19 is not); scan a wider young range in one-year windows. "
                                + "Narrow the "
                                + "query rather than paging: Hattrick reports no exact total once "
                                + "more than 100 players match. Searching needs the user to unlock "
                                + "market search in HO first, and is capped per session. "
                                + "Every hit is also stored permanently, with its full skill set - "
                                + "including the skills you did not query on - and after the "
                                + "transfer completes HO fills in what the player actually sold "
                                + "for. So a search keeps paying off: it is what estimate_market_value "
                                + "later reads, for these players and for others of a similar "
                                + "profile.",
                        searchSchema(),
                        args -> search(queryFrom(args))),
                new McpTool("find_comparables",
                        "Find players CURRENTLY on the market comparable to one of your own, to "
                                + "value him against live asking prices and bids. Derives the query "
                                + "from his age, his two highest positional skills and his "
                                + "specialty, and echoes the query it built so you can refine it. "
                                + "This spends one of the session's transfer searches. "
                                + "estimate_market_value does the same matching against listings HO "
                                + "has already stored, for free and including what players actually "
                                + "sold for - try that first, and come here when you need today's "
                                + "live market rather than recent history, or when it reports too "
                                + "thin a sample.",
                        McpTool.schema(McpTool.properties(
                                        "playerId", McpTool.prop("integer", "the player to value"),
                                        "ageTolerance", McpTool.prop("integer", "age window in years, default 1"),
                                        "skillTolerance", McpTool.prop("integer", "skill window in levels, default 1")),
                                "playerId"),
                        TransferTools::findComparables),
                new McpTool("get_team_transfers",
                        "The managed team's own transfer history - every player it bought or sold, "
                                + "newest first, with price, TSI at transfer time and the mother-club "
                                + "and previous-club fees. HO refreshes this from Hattrick on every "
                                + "team download, so it needs no live call. Returns the most recent "
                                + "page only (default 20); page back with offset. Optionally filter "
                                + "by Hattrick season number and by direction (buy or sell).",
                        McpTool.schema(McpTool.properties(
                                "season", McpTool.prop("integer", "Hattrick season number; omit for the full history"),
                                "direction", McpTool.prop("string", "buy, sell, or omit for both"),
                                "limit", McpTool.prop("integer", "how many transfers to return, newest first; default 20, max 100"),
                                "offset", McpTool.prop("integer", "how many of the newest transfers to skip, for paging; default 0"))),
                        TransferTools::teamTransfers),
                new McpTool("place_bid",
                        "Place a transfer bid. TWO STEPS so a stray call cannot spend money: call "
                                + "once with playerId + bidAmount to get a preview and a "
                                + "confirmToken, then call again with the same playerId and that "
                                + "confirmToken to actually place the bid. The second call leaves "
                                + "the machine and commits money in Hattrick. bidAmount is in the "
                                + "user's currency. maxBidAmount is an optional autobid ceiling.",
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
                        "Players this team has an active transfer bid on, from Hattrick's own bid "
                                + "tracking (file=currentbids) - so it also sees bids placed on the "
                                + "Hattrick site, not just through place_bid. By default returns "
                                + "transfers you have bid on whose deadline has not passed, plus "
                                + "bids you were losing for up to 24h after their deadline so a "
                                + "recent loss still shows, each flagged with whether you are the "
                                + "current leading bidder. "
                                + "Pass includeAll for every tracked transfer - your listed "
                                + "players, bookmarked players, mother-club and finished transfers "
                                + "too - each with its raw trackingTypeId. "
                                + "Hattrick sends only the highest bid, so HO adds what it already "
                                + "holds: askingPrice from the stored market snapshot (absent for a "
                                + "player never seen by search_transfers), and, once the transfer "
                                + "finished, salePrice, outcome (won/lost) and ourBid - what you "
                                + "bid, if it went through place_bid. A lost bid next to the price "
                                + "it lost to is the cheapest read you get on what this market "
                                + "actually pays.",
                        McpTool.schema(McpTool.properties(
                                "includeAll", McpTool.prop("boolean",
                                        "return every tracked transfer, not just your open bids; "
                                                + "default false"))),
                        TransferTools::openBids),
                new McpTool("add_to_scout",
                        "Put players on HO's transfer scout list, so a shortlist survives the "
                                + "session. This is HO's OWN local list - it is not a Hattrick "
                                + "bookmark, costs no CHPP call, and the user will not see these "
                                + "players on hattrick.org, only on HO's Transfers -> "
                                + "TransferScout tab (get_open_bids reads Hattrick's separate "
                                + "tracking list). Each player must have been seen by an earlier "
                                + "search_transfers in this database - the entry is built from that "
                                + "stored market snapshot. Adding a player already on the list "
                                + "replaces his entry.",
                        McpTool.schema(McpTool.properties(
                                        "players", McpTool.prop("array",
                                                "the players to add, either bare player ids or "
                                                        + "{playerId, note} objects; the note is "
                                                        + "free text kept in the scout entry's "
                                                        + "notes column, e.g. why he is a target")),
                                "players"),
                        TransferTools::addToScout),
                new McpTool("list_scout",
                        "Read back HO's local transfer scout list - the shortlist add_to_scout "
                                + "writes, with the market data and skills as they were when each "
                                + "player was added. price and deadline are that snapshot, not "
                                + "live; re-run search_transfers for the current market state. "
                                + "price is what it would have cost to take him when he was added "
                                + "- the higher of the seller's asking price and the highest bid, "
                                + "so it silently becomes the bid once bidding passes the ask. "
                                + "askingPrice and highestBid are those two kept apart, taken from "
                                + "the newest stored observation of him rather than from the "
                                + "moment he was added - observedAt says when that was, and may be "
                                + "later than the deadline beside it if he was re-listed. Both are "
                                + "missing for an entry typed into the UI by hand, and askingPrice "
                                + "alone is missing when Hattrick sent none for that listing, in "
                                + "which case price is the highest bid. "
                                + "salePrice and soldAt are different: they are filled in after the "
                                + "watched transfer completed and say what the player actually went "
                                + "for, so a target you lost still tells you the price it took. "
                                + "Both are null while he is still listed. "
                                + "Distinct from get_open_bids, which reads Hattrick's own tracking "
                                + "list.",
                        McpTool.schema(McpTool.properties()),
                        args -> listScout()),
                new McpTool("estimate_market_value",
                        "Evidence for valuing one of your players, from the transfer listings HO "
                                + "has already seen - no CHPP call, no search cap. Matches him "
                                + "against the stored corpus on the same criteria find_comparables "
                                + "uses (age window, his two best positional skills, his specialty) "
                                + "and returns the matching listings themselves, each with wage, "
                                + "TSI, form, specialty, its three best positional skills "
                                + "(topSkills), set pieces, asking price, highest bid and - once "
                                + "his transfer completed - what he actually sold for. Set pieces "
                                + "is NOT part of the matching, so the comparables spread across "
                                + "it freely: use it to explain a row that priced away from the "
                                + "rest, and note that it is null where Hattrick sent no details. "
                                + "YOU do the "
                                + "valuation from those rows: weigh wage against skills, discount "
                                + "stale observations, notice injuries and specialties. The "
                                + "estimate field is only a median and is a starting anchor, not an "
                                + "answer. basis says what it rests on: resolved_sales means real "
                                + "sale prices, observed_listings means asking prices and bids, "
                                + "which run high because an asking price is what a seller wants. "
                                + "Sale prices fill in after each team download, so this improves "
                                + "on its own. A thin sampleSize means widen the tolerances, or run "
                                + "search_transfers over that profile to feed the corpus.",
                        McpTool.schema(McpTool.properties(
                                        "playerId", McpTool.prop("integer", "the player to value"),
                                        "ageTolerance", McpTool.prop("integer", "age window in years, default 1"),
                                        "skillTolerance", McpTool.prop("integer", "skill window in levels, default 1"),
                                        "observedWithinDays", McpTool.prop("integer",
                                                "ignore listings older than this many days, default "
                                                        + DEFAULT_CORPUS_DAYS + " (one Hattrick season)")),
                                "playerId"),
                        TransferTools::estimateMarketValue)
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

    private static JsonObject estimateMarketValue(JsonObject args) throws McpToolException {
        final var playerId = McpJson.reqInt(args, "playerId");
        final var player = McpJson.model().getCurrentPlayer(playerId);
        if (player == null) {
            throw new McpToolException("No player with id " + playerId + " is in the squad. "
                    + "Call get_squad for the ids HO knows about.");
        }
        final var query = comparableQuery(player,
                McpJson.optInt(args, "ageTolerance", 1),
                McpJson.optInt(args, "skillTolerance", 1));
        final var days = Math.max(McpJson.optInt(args, "observedWithinDays", DEFAULT_CORPUS_DAYS), 1);
        final var since = new HODateTime(HODateTime.now().getInstant()
                .minus(java.time.Duration.ofDays(days)));

        final var observations = DBManager.instance().loadComparableTransferListings(
                query.ageMin(), query.ageMax(),
                column(query.skillType1()), query.minSkillValue1(), query.maxSkillValue1(),
                column(query.skillType2()), query.minSkillValue2(), query.maxSkillValue2(),
                query.specialty(), since);

        // One vote per player. The corpus appends on every observation, so a player who turned up
        // in four searches is four rows: without this he moves the median four times and
        // sampleSize counts rows rather than the players a caller reads it as. Keep his sale where
        // we know it, else his newest observation, and hand the list over newest-first.
        final var comparables = observations.stream()
                .collect(Collectors.toMap(TransferListing::getPlayerId, l -> l,
                        TransferTools::betterObservation))
                .values().stream()
                .sorted(Comparator.comparing(TransferTools::observedAt).reversed())
                .toList();

        final var sales = comparables.stream()
                .filter(l -> l.getSalePrice() != null)
                .map(TransferListing::getSalePrice)
                .sorted()
                .toList();
        // An expired listing keeps its last observed state, so the highest bid is the better
        // reading of what it was worth whenever bidding got past the asking price.
        final var asks = comparables.stream()
                .filter(l -> l.getSalePrice() == null)
                .map(l -> Math.max(l.getAskingPrice(), l.getHighestBid()))
                .filter(p -> p > 0)
                .sorted()
                .toList();

        final var useSales = sales.size() >= MIN_RESOLVED_SAMPLE;
        final var sample = useSales ? sales : asks;

        final var json = new JsonObject();
        json.addProperty("currency", McpJson.currency());
        // The player being valued, so the comparison is self-contained in one call.
        json.add("player", topSkillsOnly(McpJson.player(player), playerSkillLevels(player)));
        json.addProperty("basis", useSales ? "resolved_sales" : "observed_listings");
        json.addProperty("sampleSize", sample.size());
        json.addProperty("resolvedSalesAvailable", sales.size());
        json.addProperty("observedListingsAvailable", asks.size());
        json.addProperty("comparableQuery", query.toQueryString());
        json.addProperty("observedWithinDays", days);

        // The evidence, not just the summary. The median below is a crude anchor: it collapses
        // wage, TSI, form, the other skills and how long ago each was seen, all of which change
        // what a player is worth. Reason over these rows instead of trusting the number.
        final var evidence = new JsonArray();
        comparables.stream()
                .limit(MAX_COMPARABLES)
                .forEach(l -> evidence.add(topSkillsOnly(McpJson.listing(l), listingSkillLevels(l))));
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
        json.addProperty("estimate", McpJson.money(median(sample)));
        json.addProperty("estimateNote", "Median over all " + sample.size() + " comparables, as a "
                + "starting anchor only - it ignores wage, TSI, form, specialty, set pieces and how "
                + "stale each observation is. The comparables array carries those, and is the "
                + "better basis for a judgement. Each row lists its three best positional skills "
                + "plus set pieces; the positional skills below the top three are not returned.");
        final var range = new JsonArray();
        range.add(McpJson.money(sample.get(0)));
        range.add(McpJson.money(sample.get(sample.size() - 1)));
        json.add("range", range);
        if (!useSales) {
            json.addProperty("note", sales.isEmpty()
                    ? "No resolved sale prices for this profile yet, so this is what sellers asked "
                    + "and bidders bid, not what players went for - read it as an upper bound."
                    : sales.size() + " resolved sale(s) is too thin a sample, so asking prices and "
                    + "bids were used instead - read the estimate as an upper bound.");
        }
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

    /** The middle value; for an even sample the mean of the two middle ones. */
    private static long median(List<Long> sorted) {
        final var middle = sorted.size() / 2;
        return sorted.size() % 2 == 1
                ? sorted.get(middle)
                : (sorted.get(middle - 1) + sorted.get(middle)) / 2;
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

            final var added = new JsonArray();
            for (final var entry : entries.values()) {
                final var json = new JsonObject();
                json.addProperty("playerId", entry.getPlayerID());
                json.addProperty("name", entry.getName());
                added.add(json);
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
                "skillType1", McpTool.prop("string", "first skill name"),
                "minSkillValue1", McpTool.prop("integer", "minimum level of the first skill"),
                "maxSkillValue1", McpTool.prop("integer", "maximum level of the first skill"),
                "skillType2", McpTool.prop("string", "second skill name"),
                "minSkillValue2", McpTool.prop("integer", "minimum level of the second skill"),
                "maxSkillValue2", McpTool.prop("integer", "maximum level of the second skill"),
                "skillType3", McpTool.prop("string", "third skill name"),
                "minSkillValue3", McpTool.prop("integer", "minimum level of the third skill"),
                "maxSkillValue3", McpTool.prop("integer", "maximum level of the third skill"),
                "skillType4", McpTool.prop("string", "fourth skill name"),
                "minSkillValue4", McpTool.prop("integer", "minimum level of the fourth skill"),
                "maxSkillValue4", McpTool.prop("integer", "maximum level of the fourth skill"),
                "specialty", McpTool.prop("integer", "specialty id, 0 none"),
                "nativeCountryId", McpTool.prop("integer", "native country id"),
                "tsiMin", McpTool.prop("integer", "minimum TSI"),
                "tsiMax", McpTool.prop("integer", "maximum TSI"),
                "priceMin", McpTool.prop("integer", "minimum asking price, in the user's currency"),
                "priceMax", McpTool.prop("integer", "maximum asking price, in the user's currency"),
                "pageSize", McpTool.prop("integer", "results per page, default 50, maximum 100"));
        return McpTool.schema(properties,
                "ageMin", "ageMax", "skillType1", "minSkillValue1", "maxSkillValue1");
    }

    /** The query the client asked for, with the page size defaulted and capped. */
    static TransferSearchQuery queryFrom(JsonObject args) {
        final var builder = TransferSearchQuery.builder()
                .ageMin(McpJson.reqInt(args, "ageMin"))
                .ageMax(McpJson.reqInt(args, "ageMax"))
                .skillType1(skill(args, "skillType1"))
                .minSkillValue1(McpJson.reqInt(args, "minSkillValue1"))
                .maxSkillValue1(McpJson.reqInt(args, "maxSkillValue1"))
                .skillType2(optionalSkill(args, "skillType2"))
                .minSkillValue2(McpJson.optInteger(args, "minSkillValue2"))
                .maxSkillValue2(McpJson.optInteger(args, "maxSkillValue2"))
                .skillType3(optionalSkill(args, "skillType3"))
                .minSkillValue3(McpJson.optInteger(args, "minSkillValue3"))
                .maxSkillValue3(McpJson.optInteger(args, "maxSkillValue3"))
                .skillType4(optionalSkill(args, "skillType4"))
                .minSkillValue4(McpJson.optInteger(args, "minSkillValue4"))
                .maxSkillValue4(McpJson.optInteger(args, "maxSkillValue4"))
                .specialty(McpJson.optInteger(args, "specialty"))
                .nativeCountryId(McpJson.optInteger(args, "nativeCountryId"))
                .tsiMin(McpJson.optInteger(args, "tsiMin"))
                .tsiMax(McpJson.optInteger(args, "tsiMax"))
                // The form takes the user's currency; the CHPP query is in swedish krona.
                .priceMin(McpJson.swedishKrona(McpJson.optInteger(args, "priceMin")))
                .priceMax(McpJson.swedishKrona(McpJson.optInteger(args, "priceMax")))
                // Paging is not exposed: ItemCount comes back as -1 once more than 100 players
                // match, so a second page buys little and would eat the per session cap.
                .pageSize(Math.min(McpJson.optInt(args, "pageSize", DEFAULT_PAGE_SIZE), MAX_PAGE_SIZE));
        return builder.build();
    }

    private static TransferSearchQuery.Skill skill(JsonObject args, String name) {
        final var value = McpJson.optString(args, name, null);
        if (value == null) {
            throw new IllegalArgumentException("Missing required argument: " + name);
        }
        return parseSkill(value);
    }

    private static TransferSearchQuery.Skill optionalSkill(JsonObject args, String name) {
        final var value = McpJson.optString(args, name, null);
        return value == null ? null : parseSkill(value);
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

    private static JsonObject findComparables(JsonObject args) throws McpToolException {
        final var model = McpJson.model();
        final var playerId = McpJson.reqInt(args, "playerId");
        final var player = model.getCurrentPlayer(playerId);
        if (player == null) {
            throw new McpToolException("No player with id " + playerId + " is in the squad. "
                    + "Call get_squad for the ids HO knows about.");
        }

        final var query = comparableQuery(player,
                McpJson.optInt(args, "ageTolerance", 1),
                McpJson.optInt(args, "skillTolerance", 1));

        final var result = search(query);
        result.addProperty("echo", query.toQueryString());
        return result;
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
        final var second = ranked.get(1);

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
                .minSkillValue2(minLevel(second, player, tolerance))
                .maxSkillValue2(maxLevel(second, player, tolerance))
                .specialty(player.getSpecialty() > 0 ? player.getSpecialty() : null)
                .pageSize(DEFAULT_PAGE_SIZE)
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
