package core.mcp;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import core.constants.player.PlayerSkill;
import core.db.TransferListing;
import core.constants.player.PlayerSpeciality;
import core.file.xml.XMLCurrentBidsParser;
import core.model.player.Player;
import core.net.ChppWriteService;
import core.net.TransferSearchQuery;
import core.util.AmountOfMoney;
import core.util.HODateTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class TransferToolsTest {

    private ChppWriteService originalService;

    @org.junit.jupiter.api.BeforeEach
    void baselineExchangeRate() {
        AmountOfMoney.Companion.setExchangeRate(BigDecimal.ONE);
    }

    @AfterEach
    void restore() {
        if (originalService != null) ChppWriteService.setInstanceForTest(originalService);
        TransferTools.clearPendingBidsForTest();
        AmountOfMoney.Companion.setExchangeRate(BigDecimal.ONE);   // other tests set it; reset
    }

    private static TransferListing observation(String observedAt, Long salePrice) {
        final var listing = new TransferListing();
        listing.setPlayerId(1);
        listing.setObservedAt(HODateTime.fromHT(observedAt));
        listing.setSalePrice(salePrice);
        return listing;
    }

    /**
     * The corpus appends, so one player can hold several rows and would otherwise vote once per
     * row in estimate_market_value's median.
     */
    @Test
    void betterObservation_keepsTheSaleThenTheNewest() {
        final var older = observation("2026-08-01 10:00:00", null);
        final var newer = observation("2026-09-01 10:00:00", null);
        final var sold = observation("2026-07-01 10:00:00", 1_000_000L);

        assertThat(TransferTools.betterObservation(older, newer)).isSameAs(newer);
        assertThat(TransferTools.betterObservation(newer, older)).isSameAs(newer);
        // What he went for beats a newer asking price, however recent that ask is.
        assertThat(TransferTools.betterObservation(newer, sold)).isSameAs(sold);
        assertThat(TransferTools.betterObservation(sold, newer)).isSameAs(sold);
    }

    private static McpTool bidTool() {
        return TransferTools.tools().stream()
                .filter(t -> t.name().equals("place_bid")).findFirst().orElseThrow();
    }

    private int placeCalls;
    private long placedAmount;
    private Long placedMax;

    private void installRecordingService() {
        originalService = ChppWriteService.instance();
        ChppWriteService.setInstanceForTest(new ChppWriteService() {
            @Override
            public BidResult placeBidForAgent(int playerId, long bidAmount, Long maxBidAmount) {
                placeCalls++;
                placedAmount = bidAmount;
                placedMax = maxBidAmount;
                return new BidResult(1_750_000L, "2026-09-05 20:00:00", "HO FC");
            }
        });
    }

    private static JsonObject step1(int playerId, long amount) {
        final var a = new JsonObject();
        a.addProperty("playerId", playerId);
        a.addProperty("bidAmount", amount);
        return a;
    }

    private static Player player(int age, int... skillValues) {
        final var p = new Player();
        p.setPlayerId(4711);
        p.setAge(age);
        p.setAgeDays(0);
        final var skills = new PlayerSkill[]{
                PlayerSkill.KEEPER, PlayerSkill.DEFENDING, PlayerSkill.PLAYMAKING,
                PlayerSkill.PASSING, PlayerSkill.WINGER, PlayerSkill.SCORING,
                PlayerSkill.SETPIECES, PlayerSkill.STAMINA, PlayerSkill.EXPERIENCE,
                PlayerSkill.LEADERSHIP};
        for (int i = 0; i < skillValues.length && i < skills.length; i++) {
            p.setValue4Skill(skills[i], skillValues[i]);
        }
        return p;
    }

    @Test
    void tools_areTheTransferTools() {
        assertThat(TransferTools.tools()).extracting(McpTool::name)
                .containsExactly("search_transfers", "get_team_transfers",
                        "place_bid", "get_open_bids", "add_to_scout", "remove_from_scout",
                        "list_scout", "estimate_market_value");
    }

    /**
     * find_comparables was estimate_market_value's live twin: the same three arguments and the same
     * matching, differing only in corpus versus a live transfersearch. It is now the {@code live}
     * flag, which is strictly better - the search writes its hits into TRANSFERLISTING first, so
     * one call returns today's market AND a median that already counts it.
     */
    @Test
    void estimateMarketValue_absorbedFindComparablesAsAFlag() {
        assertThat(TransferTools.tools()).extracting(McpTool::name)
                .doesNotContain("find_comparables");

        final var tool = TransferTools.tools().stream()
                .filter(t -> t.name().equals("estimate_market_value")).findFirst().orElseThrow();
        final var properties = tool.inputSchema().getAsJsonObject("properties");
        assertThat(properties.keySet())
                .contains("playerId", "ageTolerance", "skillTolerance", "live");
        assertThat(tool.inputSchema().getAsJsonArray("required"))
                .extracting(com.google.gson.JsonElement::getAsString)
                .containsExactly("playerId");
    }

    @Test
    void removeFromScout_requiresPlayerIdsAndRejectsAnEmptyList() {
        final var tool = TransferTools.tools().stream()
                .filter(t -> t.name().equals("remove_from_scout")).findFirst().orElseThrow();

        assertThat(tool.inputSchema().getAsJsonArray("required"))
                .extracting(com.google.gson.JsonElement::getAsString)
                .containsExactly("playerIds");

        final var empty = JsonParser.parseString("{\"playerIds\":[]}").getAsJsonObject();
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> tool.handler().call(empty))
                .withMessageContaining("at least one player id");
    }

    private static McpTool openBidsTool() {
        return TransferTools.tools().stream()
                .filter(t -> t.name().equals("get_open_bids")).findFirst().orElseThrow();
    }

    private void installBidsService(XMLCurrentBidsParser.CurrentBids bids) {
        originalService = ChppWriteService.instance();
        ChppWriteService.setInstanceForTest(new ChppWriteService() {
            @Override
            public XMLCurrentBidsParser.CurrentBids listCurrentBidsForAgent() {
                return bids;
            }
        });
    }

    /** My team is 1234. A mix of categories, deadlines in the far future and far past. */
    private static XMLCurrentBidsParser.CurrentBids sampleBids() {
        final var future = "2030-01-01 20:00:00";
        final var past = "2000-01-01 20:00:00";
        return new XMLCurrentBidsParser.CurrentBids(1234, List.of(
                new XMLCurrentBidsParser.BidItem(2, 500, 111, "Leading Larry",
                        1_500_000, 1234, "My Team", future),
                new XMLCurrentBidsParser.BidItem(8, 501, 222, "Outbid Ollie",
                        2_000_000, 9999, "Rival FC", future),
                new XMLCurrentBidsParser.BidItem(8, 502, 223, "Expired Ed",
                        3_000_000, 9999, "Rival FC", past),
                new XMLCurrentBidsParser.BidItem(5, 503, 333, "Hotlist Harry",
                        0, 0, "", future),
                new XMLCurrentBidsParser.BidItem(1, 504, 444, "Selling Sam",
                        800_000, 7777, "Buyer FC", future),
                new XMLCurrentBidsParser.BidItem(9, 505, 555, "Finished Fred",
                        900_000, 1234, "My Team", past)));
    }

    private static List<Integer> playerIds(JsonArray bids) {
        return StreamSupport.stream(bids.spliterator(), false)
                .map(b -> b.getAsJsonObject().get("playerId").getAsInt())
                .toList();
    }

    @Test
    void openBids_returnsOnlyActiveBidCategoriesWithAFutureDeadline() throws Exception {
        installBidsService(sampleBids());
        final var result = openBidsTool().handler().call(new JsonObject());
        assertThat(playerIds(result.getAsJsonArray("bids")))
                .containsExactlyInAnyOrder(111, 222);   // leading + losing, both future
    }

    @Test
    void openBids_keepsALosingBidForADayAfterItsDeadlineButNotALeadingOne() throws Exception {
        final var threeHoursAgo = new core.util.HODateTime(
                core.util.HODateTime.now().getInstant().minus(java.time.Duration.ofHours(3))).toHT();
        installBidsService(new XMLCurrentBidsParser.CurrentBids(1234, List.of(
                new XMLCurrentBidsParser.BidItem(8, 600, 666, "Just Lost Lou",
                        2_000_000, 9999, "Rival FC", threeHoursAgo),
                new XMLCurrentBidsParser.BidItem(2, 601, 667, "Just Won Wade",
                        2_000_000, 1234, "My Team", threeHoursAgo))));
        final var result = openBidsTool().handler().call(new JsonObject());
        assertThat(playerIds(result.getAsJsonArray("bids"))).containsExactly(666);
    }

    @Test
    void openBids_flagsWhetherYouAreTheLeadingBidder() throws Exception {
        installBidsService(sampleBids());
        final var bids = openBidsTool().handler().call(new JsonObject()).getAsJsonArray("bids");
        for (final var b : bids) {
            final var o = b.getAsJsonObject();
            assertThat(o.get("leading").getAsBoolean())
                    .isEqualTo(o.get("playerId").getAsInt() == 111);
        }
    }

    @Test
    void openBids_includeAllReturnsEveryTrackedTransfer() throws Exception {
        installBidsService(sampleBids());
        final var a = new JsonObject();
        a.addProperty("includeAll", true);
        final var result = openBidsTool().handler().call(a);
        assertThat(playerIds(result.getAsJsonArray("bids")))
                .containsExactly(111, 222, 223, 333, 444, 555);
    }

    @Test
    void openBids_recordsEveryTrackedTransferInTheBidLedger() throws Exception {
        installBidsService(sampleBids());
        openBidsTool().handler().call(new JsonObject());

        // "Finished Fred" (555): trackingTypeId 9, we held the leading bid, deadline past.
        final var fred = core.db.DBManager.instance().loadTransferBid(555);
        assertThat(fred).isNotNull();
        assertThat(fred.getFinalPrice()).isEqualTo(900_000L);
        assertThat(fred.getOutcome()).isEqualTo("won");

        // "Hotlist Harry" (333): tracked, never bid on - still recorded, with no own bid.
        final var harry = core.db.DBManager.instance().loadTransferBid(333);
        assertThat(harry).isNotNull();
        assertThat(harry.getOurBid()).isNull();
        assertThat(harry.getFinalPrice()).isNull();
    }

    @Test
    void step2_recordsOurBidInTheLedger() throws Exception {
        installRecordingService();   // placeBidForAgent -> BidResult(1_750_000, ...)
        final var token = bidTool().handler().call(step1(515151, 1_500_000L))
                .get("confirmToken").getAsString();
        final var a = new JsonObject();
        a.addProperty("playerId", 515151);
        a.addProperty("confirmToken", token);
        bidTool().handler().call(a);

        final var bid = core.db.DBManager.instance().loadTransferBid(515151);
        assertThat(bid).isNotNull();
        assertThat(bid.getOurBid()).isEqualTo(1_500_000L);
        assertThat(bid.getLatestHighestBid()).isEqualTo(1_750_000L);
    }

    @Test
    void openBids_convertsHighestBidToTheUsersCurrency() throws Exception {
        AmountOfMoney.Companion.setExchangeRate(BigDecimal.TEN);   // a euro league
        installBidsService(sampleBids());
        final var bids = openBidsTool().handler().call(new JsonObject()).getAsJsonArray("bids");
        final var larry = StreamSupport.stream(bids.spliterator(), false)
                .map(com.google.gson.JsonElement::getAsJsonObject)
                .filter(o -> o.get("playerId").getAsInt() == 111).findFirst().orElseThrow();
        assertThat(larry.get("highestBid").getAsLong()).isEqualTo(150_000);   // 1.5M krona / 10
    }

    @Test
    void step1_issuesATokenAndPlacesNothing() throws Exception {
        installRecordingService();
        final var result = bidTool().handler().call(step1(111, 1_500_000L));
        assertThat(result.get("status").getAsString()).isEqualTo("confirm_required");
        assertThat(result.has("confirmToken")).isTrue();
        assertThat(placeCalls).isZero();
    }

    @Test
    void step2_placesExactlyThePreviewedAmounts() throws Exception {
        installRecordingService();
        final var token = bidTool().handler().call(step1(111, 1_500_000L))
                .get("confirmToken").getAsString();

        final var a = new JsonObject();
        a.addProperty("playerId", 111);
        a.addProperty("bidAmount", 9_999_999L);   // must be ignored in favour of the pending entry
        a.addProperty("confirmToken", token);
        final var result = bidTool().handler().call(a);

        assertThat(result.get("status").getAsString()).isEqualTo("ok");
        assertThat(placeCalls).isEqualTo(1);
        assertThat(placedAmount).isEqualTo(1_500_000L);
    }

    @Test
    void step2_tokenIsSingleUse() throws Exception {
        installRecordingService();
        final var token = bidTool().handler().call(step1(111, 1_500_000L))
                .get("confirmToken").getAsString();
        final var a = new JsonObject();
        a.addProperty("playerId", 111);
        a.addProperty("confirmToken", token);
        bidTool().handler().call(a);
        assertThatExceptionOfType(McpToolException.class)
                .isThrownBy(() -> bidTool().handler().call(a))
                .withMessageContaining("step-1");
    }

    @Test
    void step2_rejectsAWrongPlayerId() throws Exception {
        installRecordingService();
        final var token = bidTool().handler().call(step1(111, 1_500_000L))
                .get("confirmToken").getAsString();
        final var a = new JsonObject();
        a.addProperty("playerId", 222);
        a.addProperty("confirmToken", token);
        assertThatExceptionOfType(McpToolException.class)
                .isThrownBy(() -> bidTool().handler().call(a));
    }

    @Test
    void step2_rejectsAnExpiredToken() throws Exception {
        installRecordingService();
        final var token = bidTool().handler().call(step1(111, 1_500_000L))
                .get("confirmToken").getAsString();
        TransferTools.expirePendingBidsForTest();
        final var a = new JsonObject();
        a.addProperty("playerId", 111);
        a.addProperty("confirmToken", token);
        assertThatExceptionOfType(McpToolException.class)
                .isThrownBy(() -> bidTool().handler().call(a));
    }

    /** The model bids in the user's currency; Hattrick's placeBid is asked in swedish krona. */
    @Test
    void bid_convertsTheModelsCurrencyToSwedishKronaBothWays() throws Exception {
        AmountOfMoney.Companion.setExchangeRate(BigDecimal.TEN);   // a euro league
        installRecordingService();

        final var preview = bidTool().handler().call(step1(111, 1_000_000L));
        // preview echoes the local number the model passed, not the krona it will send
        assertThat(preview.getAsJsonObject("bid").get("bidAmount").getAsLong()).isEqualTo(1_000_000L);

        final var a = new JsonObject();
        a.addProperty("playerId", 111);
        a.addProperty("confirmToken", preview.get("confirmToken").getAsString());
        final var result = bidTool().handler().call(a);

        assertThat(placedAmount).isEqualTo(10_000_000L);                       // sent in krona
        assertThat(result.get("highestBid").getAsLong()).isEqualTo(175_000L); // 1_750_000 krona back to local
    }

    @Test
    void bid_previewConvertsMaxBidAmountToo() throws Exception {
        AmountOfMoney.Companion.setExchangeRate(BigDecimal.TEN);
        installRecordingService();
        final var a1 = step1(111, 1_000_000L);
        a1.addProperty("maxBidAmount", 2_000_000L);
        final var token = bidTool().handler().call(a1).get("confirmToken").getAsString();

        final var a2 = new JsonObject();
        a2.addProperty("playerId", 111);
        a2.addProperty("confirmToken", token);
        bidTool().handler().call(a2);

        assertThat(placedMax).isEqualTo(20_000_000L);
    }

    @Test
    void comparableQuery_usesAgeAndTheTwoHighestPositionalSkills() {
        //                      kp de pm pa wi sc sp st ex ld
        final var p = player(24, 1, 7, 12, 9, 6, 5, 3, 8, 4, 2);
        p.setSpecialty(PlayerSpeciality.QUICK);

        final var query = TransferTools.comparableQuery(p, 1, 1);

        assertThat(query.ageMin()).isEqualTo(23);
        assertThat(query.ageMax()).isEqualTo(25);
        assertThat(query.skillType1()).isEqualTo(TransferSearchQuery.Skill.PLAYMAKING);
        assertThat(query.minSkillValue1()).isEqualTo(11);
        assertThat(query.maxSkillValue1()).isEqualTo(13);
        assertThat(query.skillType2()).isEqualTo(TransferSearchQuery.Skill.PASSING);
        assertThat(query.minSkillValue2()).isEqualTo(8);
        assertThat(query.maxSkillValue2()).isEqualTo(10);
        assertThat(query.specialty()).isEqualTo(PlayerSpeciality.QUICK);
        assertThat(query.validationErrorKey()).isNull();
    }

    /** Stamina, experience and leadership are never comparability criteria, however high they are. */
    @Test
    void comparableQuery_neverPicksStaminaExperienceOrLeadership() {
        //                      kp de pm pa wi sc sp st ex ld
        final var p = player(28, 1, 4, 3, 2, 1, 1, 1, 20, 15, 18);

        final var query = TransferTools.comparableQuery(p, 1, 1);

        assertThat(query.skillType1()).isEqualTo(TransferSearchQuery.Skill.DEFENDING);
        assertThat(query.skillType2()).isEqualTo(TransferSearchQuery.Skill.PLAYMAKING);
        assertThat(query.validationErrorKey()).isNull();
    }

    /** Hattrick will not accept a keeper query below level 3 or a span wider than 4 levels. */
    @Test
    void comparableQuery_clampsToTheKeeperMinimumAndTheFourLevelSpan() {
        //                      kp de pm pa wi sc sp
        final var p = player(19, 2, 1, 1, 1, 1, 1, 1);

        final var query = TransferTools.comparableQuery(p, 1, 5);

        assertThat(query.skillType1()).isEqualTo(TransferSearchQuery.Skill.KEEPER);
        assertThat(query.minSkillValue1()).isEqualTo(3);       // keeper minimum level
        assertThat(query.maxSkillValue1() - query.minSkillValue1()).isLessThanOrEqualTo(4);
        assertThat(query.validationErrorKey()).isNull();
    }

    /**
     * estimate_market_value hands a model up to forty rows; seven skill levels each is noise it has
     * no use for, so every row keeps its three best positional skills and nothing else. Set pieces
     * is the exception: it prices a player whatever position he plays, so it comes out of the
     * nested skills block and stays as a field of its own, beside topSkills rather than in it.
     */
    @Test
    void topSkillsOnly_keepsTheThreeBestPositionalSkillsAndSetPieces() {
        //                      kp de pm pa wi sc sp st ex ld
        final var p = player(24, 1, 7, 12, 9, 6, 5, 20, 8, 4, 2);

        final var json = TransferTools.topSkillsOnly(McpJson.player(p),
                TransferTools.playerSkillLevels(p));

        assertThat(json.getAsJsonObject("topSkills").keySet())
                .containsExactly("playmaking", "passing", "defending");
        assertThat(json.getAsJsonObject("topSkills").get("playmaking").getAsInt()).isEqualTo(12);
        assertThat(json.has("skills")).isFalse();
        assertThat(json.has("skillsWithSublevel")).isFalse();
        // Lifted out of the skills block: 20 outranks every positional skill and still must not
        // displace one in topSkills.
        assertThat(json.get("setPieces").getAsInt()).isEqualTo(20);
        assertThat(json.getAsJsonObject("topSkills").has("setPieces")).isFalse();
        // Specialty and the price-bearing fields are what is left to judge on.
        assertThat(json.has("specialtyName")).isTrue();
        assertThat(json.has("wage")).isTrue();
        assertThat(json.has("tsi")).isTrue();
    }

    /** A listing carries nullable skills, and its flat skill keys have to go with the trim. */
    @Test
    void topSkillsOnly_dropsAListingsFlatSkillsAndIgnoresNulls() {
        final var l = new TransferListing();
        l.setPlayerId(7);
        l.setKeeper(3);
        l.setDefender(11);
        l.setPlaymaker(6);
        l.setScorer(14);
        l.setSetPieces(13);
        l.setWinger(null);            // an observation can be missing a skill
        l.setPassing(null);

        final var json = TransferTools.topSkillsOnly(McpJson.listing(l),
                TransferTools.listingSkillLevels(l));

        assertThat(json.getAsJsonObject("topSkills").keySet())
                .containsExactly("scoring", "defending", "playmaking");
        assertThat(json.has("defending")).isFalse();
        // A listing writes set pieces flat rather than nested, and it survives the trim there too.
        assertThat(json.get("setPieces").getAsInt()).isEqualTo(13);
        assertThat(json.has("askingPrice")).isTrue();
    }

    /** Hattrick rejects a young age window wider than a year, so an 18yo's window is clamped. */
    @Test
    void comparableQuery_clampsTheYoungAgeWindowToAYear() {
        //                      kp de pm pa wi sc sp
        final var p = player(18, 1, 4, 9, 5, 3, 2, 1);

        final var query = TransferTools.comparableQuery(p, 1, 1);

        assertThat(query.ageMin()).isEqualTo(17);
        assertThat(query.ageMax()).isEqualTo(18);
        assertThat(query.validationErrorKey()).isNull();
    }

    @Test
    void queryFrom_readsSkillNamesRatherThanChppNumbers() {
        final var args = JsonParser.parseString("""
                {"ageMin":20,"ageMax":22,"skills":[{"skill":"PLAYMAKING","min":7,"max":9}],
                 "priceMax":2000000}""").getAsJsonObject();

        AmountOfMoney.Companion.setExchangeRate(BigDecimal.TEN);   // a euro league

        final var query = TransferTools.queryFrom(args);

        assertThat(query.skillType1()).isEqualTo(TransferSearchQuery.Skill.PLAYMAKING);
        // The model asks in the user's currency, Hattrick is asked in swedish krona.
        assertThat(query.priceMax()).isEqualTo(20_000_000);
        assertThat(query.pageSize()).isEqualTo(100);           // default
    }

    @Test
    void queryFrom_capsPageSizeAtTheChppMaximum() {
        final var args = JsonParser.parseString("""
                {"ageMin":20,"ageMax":22,"skills":[{"skill":"SCORING","min":7,"max":9}],
                 "pageSize":500}""").getAsJsonObject();

        assertThat(TransferTools.queryFrom(args).pageSize()).isEqualTo(100);
    }

    /**
     * The skills array replaced twelve flat skillType/minSkillValue/maxSkillValue properties. The
     * builder still has the indexed setters - they mirror the CHPP parameter names and the search
     * tab shares them - so the mapping from list position to slot is what can silently go wrong.
     */
    @Test
    void queryFrom_mapsTheSkillsArrayOntoTheFourIndexedSlotsInOrder() {
        final var args = JsonParser.parseString("""
                {"ageMin":20,"ageMax":22,"skills":[
                    {"skill":"PLAYMAKING","min":7,"max":9},
                    {"skill":"PASSING","min":4,"max":7},
                    {"skill":"WINGER","min":3,"max":6},
                    {"skill":"DEFENDING","min":5,"max":8}]}""").getAsJsonObject();

        final var query = TransferTools.queryFrom(args);

        assertThat(query.skillType1()).isEqualTo(TransferSearchQuery.Skill.PLAYMAKING);
        assertThat(query.minSkillValue1()).isEqualTo(7);
        assertThat(query.maxSkillValue1()).isEqualTo(9);
        assertThat(query.skillType2()).isEqualTo(TransferSearchQuery.Skill.PASSING);
        assertThat(query.minSkillValue2()).isEqualTo(4);
        assertThat(query.skillType3()).isEqualTo(TransferSearchQuery.Skill.WINGER);
        assertThat(query.skillType4()).isEqualTo(TransferSearchQuery.Skill.DEFENDING);
        assertThat(query.maxSkillValue4()).isEqualTo(8);
    }

    @Test
    void queryFrom_leavesTheUnusedSkillSlotsEmpty() {
        final var args = JsonParser.parseString("""
                {"ageMin":20,"ageMax":22,"skills":[{"skill":"KEEPER","min":8,"max":10}]}""")
                .getAsJsonObject();

        final var query = TransferTools.queryFrom(args);

        assertThat(query.skillType2()).isNull();
        assertThat(query.skillType3()).isNull();
        assertThat(query.skillType4()).isNull();
    }

    @Test
    void queryFrom_rejectsAnEmptySkillsArrayAndAFifthFilter() {
        final var none = JsonParser.parseString("""
                {"ageMin":20,"ageMax":22,"skills":[]}""").getAsJsonObject();
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> TransferTools.queryFrom(none))
                .withMessageContaining("1 to 4");

        final var five = JsonParser.parseString("""
                {"ageMin":20,"ageMax":22,"skills":[
                    {"skill":"PLAYMAKING","min":7,"max":9},{"skill":"PASSING","min":4,"max":7},
                    {"skill":"WINGER","min":3,"max":6},{"skill":"DEFENDING","min":5,"max":8},
                    {"skill":"SCORING","min":5,"max":8}]}""").getAsJsonObject();
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> TransferTools.queryFrom(five))
                .withMessageContaining("1 to 4");
    }

    @Test
    void queryFrom_namesTheUnknownSkill() {
        final var args = JsonParser.parseString("""
                {"ageMin":20,"ageMax":22,"skills":[{"skill":"DRIBBLING","min":7,"max":9}]}""")
                .getAsJsonObject();

        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> TransferTools.queryFrom(args))
                .withMessageContaining("DRIBBLING");
    }
}
