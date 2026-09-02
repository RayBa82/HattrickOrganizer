package core.mcp;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import core.db.DBManager;
import core.db.TransferListing;
import core.file.xml.XMLTransferSearchParser;
import core.model.player.Player;
import core.net.TransferSearchQuery;
import core.net.ChppWriteService;
import core.net.TransferSearchService;
import core.util.HODateTime;
import module.transfer.PlayerTransfer;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Transfer market search and comparables.
 * <p>
 * Every result is snapshotted into the TRANSFERLISTING corpus before being returned - the data is
 * already in hand, so it costs no extra CHPP traffic, and repeated observation of the same listing
 * yields a highest bid ever closer to the sale price.
 */
public final class TransferTools {

    /** The skills that say something about what a player is comparable to. */
    private static final List<TransferSearchQuery.Skill> POSITIONAL_SKILLS = List.of(
            TransferSearchQuery.Skill.KEEPER,
            TransferSearchQuery.Skill.DEFENDING,
            TransferSearchQuery.Skill.PLAYMAKING,
            TransferSearchQuery.Skill.PASSING,
            TransferSearchQuery.Skill.WINGER,
            TransferSearchQuery.Skill.SCORING,
            TransferSearchQuery.Skill.SETPIECES);

    private static final int DEFAULT_PAGE_SIZE = 50;
    private static final int MAX_PAGE_SIZE = 100;     // the CHPP maximum

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
                                + "market search in HO first, and is capped per session.",
                        searchSchema(),
                        args -> search(queryFrom(args))),
                new McpTool("find_comparables",
                        "Find players on the market comparable to one of your own, to value him "
                                + "against live asking prices and bids. Derives the query from his "
                                + "age, his two highest positional skills and his specialty, and "
                                + "echoes the query it built so you can refine it.",
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
                        TransferTools::placeBid)
        );
    }

    private record PendingBid(int playerId, long bidAmount, Long maxBidAmount, long createdAt) {
    }

    private static final Map<String, PendingBid> PENDING_BIDS = new ConcurrentHashMap<>();
    private static final long BID_TTL_MS = 120_000;

    static void clearPendingBidsForTest() {
        PENDING_BIDS.clear();
    }

    static void expirePendingBidsForTest() {
        PENDING_BIDS.replaceAll((k, v) ->
                new PendingBid(v.playerId(), v.bidAmount(), v.maxBidAmount(), 0L));
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
        final var bidAmount = McpJson.reqLong(args, "bidAmount");
        final Long maxBidAmount = McpJson.optLong(args, "maxBidAmount");

        final var token = UUID.randomUUID().toString();
        PENDING_BIDS.put(token,
                new PendingBid(playerId, bidAmount, maxBidAmount, System.currentTimeMillis()));

        final var out = new JsonObject();
        out.addProperty("status", "confirm_required");

        final var listing = DBManager.instance().loadLatestTransferListing(playerId);
        final var player = new JsonObject();
        player.addProperty("id", playerId);
        if (listing != null) {
            player.addProperty("name", listing.getPlayerName());
            player.addProperty("ageYears", listing.getAge());
            player.addProperty("askingPrice", listing.getAskingPrice());
            player.addProperty("highestBid", listing.getHighestBid());
            player.addProperty("deadline",
                    listing.getDeadline() == null ? null : listing.getDeadline().toHT());
        } else {
            player.addProperty("note", "No market snapshot for this player - run search_transfers "
                    + "for him first if you want a preview.");
        }
        out.add("player", player);

        final var bid = new JsonObject();
        bid.addProperty("bidAmount", bidAmount);
        if (maxBidAmount != null) {
            bid.addProperty("maxBidAmount", maxBidAmount);
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
            result = ChppWriteService.instance()
                    .placeBidForAgent(pending.playerId(), pending.bidAmount(), pending.maxBidAmount());
        } catch (ChppWriteService.ChppWriteException e) {
            throw new McpToolException(e.getMessage());
        }
        final var out = new JsonObject();
        out.addProperty("status", "ok");
        final var player = new JsonObject();
        player.addProperty("id", pending.playerId());
        out.add("player", player);
        out.addProperty("highestBid", result.highestBid());
        out.addProperty("deadline", result.deadline());
        out.addProperty("bidderTeamName", result.bidderTeamName());
        out.addProperty("note", "Bid placed. Poll get_player or search_transfers for the updated "
                + "market state.");
        return out;
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
