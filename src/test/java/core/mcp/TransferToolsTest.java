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
import static org.assertj.core.api.Assertions.within;

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

    private static TransferListing listingWithSkillsAndCharacter(int age, int ageDays,
            int keeper, int defending, int playmaking, int passing, int winger, int scoring,
            Integer agreeability, Integer aggressiveness, Integer honesty) {
        final var l = new TransferListing();
        l.setPlayerId(2);
        l.setAge(age);
        l.setAgeDays(ageDays);
        l.setKeeper(keeper);
        l.setDefender(defending);
        l.setPlaymaker(playmaking);
        l.setPassing(passing);
        l.setWinger(winger);
        l.setScorer(scoring);
        l.setAgreeability(agreeability);
        l.setAggressiveness(aggressiveness);
        l.setHonesty(honesty);
        return l;
    }

    @Test
    void thirdHighestOrNull_picksTheThirdHighestAndIgnoresNulls() {
        assertThat(TransferTools.thirdHighestOrNull(List.of(10, 8, 5, 3))).isEqualTo(5);
        assertThat(TransferTools.thirdHighestOrNull(
                java.util.Arrays.asList(10, null, 8, null, 5))).isEqualTo(5);
    }

    /** A bid-only observation has no skill data at all, not a real, terrible third skill. */
    @Test
    void thirdHighestOrNull_isNullWithFewerThanThreeNonNullValues() {
        assertThat(TransferTools.thirdHighestOrNull(
                java.util.Arrays.asList(10, null, null))).isNull();
        assertThat(TransferTools.thirdHighestOrNull(List.of())).isNull();
    }

    @Test
    void ageSigmaDays_isTighterForYoungerPlayers() {
        assertThat(TransferTools.ageSigmaDays(17)).isLessThan(TransferTools.ageSigmaDays(20));
        assertThat(TransferTools.ageSigmaDays(20)).isLessThan(TransferTools.ageSigmaDays(30));
    }

    /**
     * comparableWeight only reports the product - a bug swapping two kernels (age too high,
     * skill too low) could still cancel out and pass that check alone, so the breakdown itself
     * needs its own assertion per factor.
     */
    @Test
    void comparableWeightBreakdown_reportsEachKernelSeparately() {
        // Age matches exactly (20y0d both sides); skill is 2 levels off (third-best 10 vs 8, not
        // so far that the product gets floor-clamped and hides the per-factor values below);
        // character is untouched by the listing having none at all.
        final var listing = new TransferListing();
        listing.setAge(20);
        listing.setAgeDays(0);
        listing.setKeeper(1);
        listing.setDefender(15);
        listing.setPlaymaker(12);
        listing.setPassing(8);
        listing.setWinger(3);
        listing.setScorer(2);

        final var breakdown = TransferTools.comparableWeightBreakdown(listing, 2240,
                TransferTools.ageSigmaDays(20), 10, new int[]{2, 2, 2});

        assertThat(breakdown.age()).isCloseTo(1.0, within(0.001));
        assertThat(breakdown.skill()).isLessThan(1.0);
        assertThat(breakdown.character()).isEqualTo(1.0);   // no character data - neutral, not 0
        assertThat(breakdown.combined())
                .isCloseTo(breakdown.age() * breakdown.skill() * breakdown.character(), within(0.001));
    }

    @Test
    void comparableWeight_isMaximalWhenAgeSkillAndCharacterAllMatch() {
        // 20 years, 0 days = 2240 total days (HATTRICK_YEAR_DAYS=112); third-best skill 10; matched
        // exactly by the listing below, so all three kernels sit at diff=0.
        final var listing = listingWithSkillsAndCharacter(20, 0, 1, 10, 15, 8, 6, 20, 2, 2, 2);

        final var weight = TransferTools.comparableWeight(listing, 2240,
                TransferTools.ageSigmaDays(20), 10, new int[]{2, 2, 2});

        assertThat(weight).isCloseTo(1.0, within(0.001));
    }

    @Test
    void comparableWeight_decaysWithAgeGapButNeverBelowTheFloor() {
        // Same skill/character match as above, but 10 years and 0 days older - far past any of the
        // age kernel's sigma bands.
        final var listing = listingWithSkillsAndCharacter(30, 0, 1, 10, 15, 8, 6, 20, 2, 2, 2);

        final var weight = TransferTools.comparableWeight(listing, 2240,
                TransferTools.ageSigmaDays(20), 10, new int[]{2, 2, 2});

        assertThat(weight).isLessThan(0.5).isGreaterThanOrEqualTo(0.05);
    }

    /** A bid-only observation (no skills, no character) still gets a weight, from age alone. */
    @Test
    void comparableWeight_defaultsToNeutralWhenSkillOrCharacterDataIsMissing() {
        final var listing = new TransferListing();
        listing.setAge(20);
        listing.setAgeDays(0);

        final var weight = TransferTools.comparableWeight(listing, 2240,
                TransferTools.ageSigmaDays(20), 10, new int[]{2, 2, 2});

        assertThat(weight).isCloseTo(1.0, within(0.001));
    }

    @Test
    void specialtyWeight_sameOrUnknownIsNeutralDifferentIsSoft() {
        assertThat(TransferTools.specialtyWeight(4, 4, null)).isEqualTo(1.0);
        assertThat(TransferTools.specialtyWeight(null, 4, null)).isEqualTo(1.0);
        assertThat(TransferTools.specialtyWeight(4, null, null)).isEqualTo(1.0);
        assertThat(TransferTools.specialtyWeight(0, 4, null)).isEqualTo(TransferTools.SPECIALTY_MISMATCH);
        assertThat(TransferTools.specialtyWeight(2, 4, null)).isEqualTo(TransferTools.SPECIALTY_MISMATCH);
    }

    @Test
    void specialtyWeight_withPositionGroupFallsWithTheScoreGap() {
        // inner_mid scores: Unpredictable 1.0, Head 0.9, Quick 0.4, none 0.2
        assertThat(TransferTools.specialtyWeight(0, 4, "inner_mid")).isCloseTo(0.44, within(0.001));
        assertThat(TransferTools.specialtyWeight(5, 2, "inner_mid")).isCloseTo(0.65, within(0.001));
        assertThat(TransferTools.specialtyWeight(5, 4, "inner_mid")).isCloseTo(0.93, within(0.001));
        assertThat(TransferTools.specialtyWeight(0, 2, "inner_mid")).isCloseTo(0.86, within(0.001));
        assertThat(TransferTools.specialtyWeight(4, 4, "inner_mid")).isEqualTo(1.0);
        // back_line: Powerful 1.0 vs Head 0.25 (a liability there) - same pair reversed is symmetric
        assertThat(TransferTools.specialtyWeight(5, 3, "back_line")).isCloseTo(0.475, within(0.001));
        assertThat(TransferTools.specialtyWeight(3, 5, "back_line")).isCloseTo(0.475, within(0.001));
        // the same pair is nearly interchangeable at a position that values both
        assertThat(TransferTools.specialtyWeight(5, 2, "winger")).isCloseTo(0.93, within(0.001));
    }

    @Test
    void comparableWeightBreakdown_reportsSpecialtyKernelAndFoldsItIntoCombined() {
        final var listing = new TransferListing();
        listing.setAge(20);
        listing.setAgeDays(0);
        listing.setSpecialty(0);

        final var breakdown = TransferTools.comparableWeightBreakdown(listing, 2240,
                TransferTools.ageSigmaDays(20), null, null, 4, null);

        assertThat(breakdown.specialty()).isEqualTo(TransferTools.SPECIALTY_MISMATCH);
        assertThat(breakdown.combined()).isCloseTo(TransferTools.SPECIALTY_MISMATCH, within(0.001));
    }

    @Test
    void derivePositionGroup_readsTheTopSkillAndTiesGoToInnerMid() {
        final var pm = new java.util.LinkedHashMap<String, Integer>();
        pm.put("keeper", 1); pm.put("defending", 3); pm.put("playmaking", 7);
        pm.put("passing", 6); pm.put("winger", 5); pm.put("scoring", 2);
        assertThat(TransferTools.derivePositionGroup(pm)).isEqualTo("inner_mid");

        pm.put("winger", 7);   // PM 7 = winger 7: playmaking comes first in skill order
        assertThat(TransferTools.derivePositionGroup(pm)).isEqualTo("inner_mid");
        pm.put("winger", 8);
        assertThat(TransferTools.derivePositionGroup(pm)).isEqualTo("winger");
        pm.put("scoring", 9);
        assertThat(TransferTools.derivePositionGroup(pm)).isEqualTo("forward");
        pm.put("defending", 10);
        assertThat(TransferTools.derivePositionGroup(pm)).isEqualTo("back_line");
        pm.put("keeper", 11);
        assertThat(TransferTools.derivePositionGroup(pm)).isEqualTo("keeper");
    }

    @Test
    void positionGroupOrNull_rejectsUnknownGroup() {
        final var args = new com.google.gson.JsonObject();
        args.addProperty("positionGroup", "striker");
        org.junit.jupiter.api.Assertions.assertThrows(McpToolException.class,
                () -> TransferTools.positionGroupOrNull(args));
    }

    /** A candidate whose own two best skills are exactly the target's queried pair is a match. */
    @Test
    void sameArchetype_isTrueWhenOwnTopTwoMatchesTheTargetPair() {
        final var listing = listingWithSkillsAndCharacter(20, 0,
                1, 2, 15, 3, 10, 4, null, null, null);   // playmaking 15, winger 10 are the top two

        assertThat(TransferTools.sameArchetype(listing,
                TransferSearchQuery.Skill.PLAYMAKING, TransferSearchQuery.Skill.WINGER)).isTrue();
    }

    /** The pair's order doesn't matter - only which two skills they are. */
    @Test
    void sameArchetype_isOrderIndependent() {
        final var listing = listingWithSkillsAndCharacter(20, 0,
                1, 2, 15, 3, 10, 4, null, null, null);   // same listing: playmaking 15, winger 10

        assertThat(TransferTools.sameArchetype(listing,
                TransferSearchQuery.Skill.WINGER, TransferSearchQuery.Skill.PLAYMAKING)).isTrue();
    }

    /**
     * A scorer whose secondary skill (playmaking) happens to sit in a playmaker's queried window
     * is not a comparable - his own top two skills are a different pair entirely.
     */
    @Test
    void sameArchetype_isFalseWhenOwnTopTwoIsADifferentPair() {
        final var listing = listingWithSkillsAndCharacter(18, 0,
                1, 2, 7, 5, 3, 8, null, null, null);   // scoring 8, playmaking 7 are the top two

        assertThat(TransferTools.sameArchetype(listing,
                TransferSearchQuery.Skill.PLAYMAKING, TransferSearchQuery.Skill.WINGER)).isFalse();
    }

    /** A listing with no positional skill data at all can't be judged, so it passes - neutral. */
    @Test
    void sameArchetype_isTrueWhenTheListingHasNoSkillDataAtAll() {
        final var listing = new TransferListing();

        assertThat(TransferTools.sameArchetype(listing,
                TransferSearchQuery.Skill.PLAYMAKING, TransferSearchQuery.Skill.WINGER)).isTrue();
    }


    @Test
    void fallbackClimb_isNeutralWithoutADeadlineOrOnceItHasPassed() {
        final var noDeadline = new TransferListing();
        noDeadline.setObservedAt(HODateTime.fromHT("2026-09-01 00:00:00"));
        assertThat(TransferTools.fallbackClimb(noDeadline)).isEqualTo(1.0);

        final var alreadyDue = new TransferListing();
        alreadyDue.setObservedAt(HODateTime.fromHT("2026-09-01 00:00:00"));
        alreadyDue.setDeadline(HODateTime.fromHT("2026-08-30 00:00:00"));
        assertThat(TransferTools.fallbackClimb(alreadyDue)).isEqualTo(1.0);
    }

    @Test
    void fallbackClimb_climbsWithTimeLeftOnTheClockUpToACap() {
        final var halfWindow = new TransferListing();
        halfWindow.setObservedAt(HODateTime.fromHT("2026-09-01 00:00:00"));
        halfWindow.setDeadline(HODateTime.fromHT("2026-09-03 12:00:00"));   // 2.5 of 5 days
        assertThat(TransferTools.fallbackClimb(halfWindow)).isCloseTo(1.25, within(0.01));

        final var beyondFullWindow = new TransferListing();
        beyondFullWindow.setObservedAt(HODateTime.fromHT("2026-09-01 00:00:00"));
        beyondFullWindow.setDeadline(HODateTime.fromHT("2026-09-20 00:00:00"));   // far past 5 days
        assertThat(TransferTools.fallbackClimb(beyondFullWindow)).isEqualTo(1.5);
    }

    /** Omitted is neither 0 nor safely unknown - both inflate the estimate - so it is refused. */
    @Test
    void requireThirdSkill_rejectsAnOutfieldProfileWithFewerThanThreeSkills() throws McpToolException {
        final var twoGiven = JsonParser.parseString("""
                {"skills":{"playmaking":11,"passing":10}}""").getAsJsonObject();
        assertThatExceptionOfType(McpToolException.class)
                .isThrownBy(() -> TransferTools.requireThirdSkill(twoGiven))
                .withMessageContaining("third-best positional skill");

        TransferTools.requireThirdSkill(JsonParser.parseString("""
                {"skills":{"playmaking":11,"passing":10,"winger":0}}""").getAsJsonObject());  // explicit 0 counts
        TransferTools.requireThirdSkill(JsonParser.parseString("""
                {"skills":{"keeper":13}}""").getAsJsonObject());                              // keeper exempt
    }

    /** Resolved sales are never blended with asks: enough of them decide alone, fewer stand apart. */
    @Test
    void basis_isSalesOnlyFromThreeResolvedSales() {
        assertThat(TransferTools.basis(0)).isEqualTo("listings");
        assertThat(TransferTools.basis(1)).isEqualTo("thin_sales_plus_listings");
        assertThat(TransferTools.basis(2)).isEqualTo("thin_sales_plus_listings");
        assertThat(TransferTools.basis(3)).isEqualTo("sales");
    }

    @Test
    void setPiecesNote_onlyForAHighTakerOrComparablesFarOff() {
        final var near = java.util.Arrays.asList(5, 6, null, 8);
        assertThat(TransferTools.setPiecesNote(null, near)).isNull();           // unknown target
        assertThat(TransferTools.setPiecesNote(6, near)).isNull();              // all within 3
        assertThat(TransferTools.setPiecesNote(6, java.util.Arrays.asList(5, 10, null)))
                .contains("1 of 2 comparables");                                // 10 is 4 away
        assertThat(TransferTools.setPiecesNote(12, List.of(11, 12)))
                .contains("set-pieces specialist").contains("0 of 2");          // high target alone
    }

    @Test
    void weightedMedian_returnsTheSinglePricePresent() {
        assertThat(TransferTools.weightedMedian(List.of(new double[]{100_000.0, 1.0})))
                .isEqualTo(100_000L);
    }

    /** A heavily-weighted comparable pulls the result to itself, unlike a plain median. */
    @Test
    void weightedMedian_isPulledTowardsTheHeaviestWeightedPrice() {
        final var pairs = List.of(
                new double[]{100_000.0, 1.0},
                new double[]{200_000.0, 1.0},
                new double[]{300_000.0, 100.0});

        assertThat(TransferTools.weightedMedian(pairs)).isEqualTo(300_000L);
    }

    /**
     * A plain-median anchor let one poorly-matched comparable's wild price drag even a dominant,
     * well-matched comparable's own price upward before weighting applied - anchoring the
     * winsorize band on the sample's own weighted median instead keeps it centered on where the
     * weight actually is. Numbers are a real estimate_market_value sample: the dominant
     * comparable (weight 0.93) priced at 5,073 got clamped up to 5,313 by a plain median of just
     * these two prices (15,939.5) before the fix.
     */
    @Test
    void clampedWeightedMedian_anchorsOnTheWeightedCenterNotThePlainMedian() {
        final var listing = new TransferListing();
        final var sample = List.of(
                new TransferTools.PricedComparable(listing, 5_073L, 0.93),
                new TransferTools.PricedComparable(listing, 26_806L, 0.25));

        assertThat(TransferTools.clampedWeightedMedian(sample)).isEqualTo(5_073L);
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
                        "get_player_transfer_history", "place_bid", "get_open_bids", "add_to_scout",
                        "remove_from_scout", "list_scout", "estimate_market_value", "market_corpus_stats");
    }

    @Test
    void listScout_pagesAndFiltersWithNothingRequired() {
        final var schema = tool("list_scout").inputSchema();
        assertThat(schema.getAsJsonArray("required")).isEmpty();
        assertThat(schema.getAsJsonObject("properties").keySet())
                .containsExactlyInAnyOrder("skills", "ageMin", "ageMax", "specialty", "onlyOpen",
                        "full", "limit", "offset");
    }

    @Test
    void scoutFilter_andsAgeSpecialtyAndOpenEndedSkillRanges() {
        final var keeper = new module.transfer.scout.ScoutEintrag();
        keeper.setAlter(19);
        keeper.setSpeciality(1);
        keeper.setTorwart(9);
        keeper.setVerteidigung(3);

        final var filter = TransferTools.ScoutFilter.from(com.google.gson.JsonParser.parseString("""
                {"ageMax":20,"specialty":1,"skills":[{"skill":"keeper","min":8},
                 {"skill":"DEFENDING","max":4}]}""").getAsJsonObject());
        assertThat(filter.test(keeper, java.time.Instant.now())).isTrue();

        keeper.setTorwart(7);
        assertThat(filter.test(keeper, java.time.Instant.now())).isFalse();
        keeper.setTorwart(9);
        keeper.setAlter(21);
        assertThat(filter.test(keeper, java.time.Instant.now())).isFalse();
    }

    @Test
    void scoutFilter_onlyOpenMeansStillOnTheMarket() {
        final var now = java.time.Instant.parse("2026-09-26T12:00:00Z");
        final var onlyOpen = TransferTools.ScoutFilter.from(
                com.google.gson.JsonParser.parseString("{\"onlyOpen\":true}").getAsJsonObject());
        final var entry = new module.transfer.scout.ScoutEintrag();

        entry.setDeadline(java.sql.Timestamp.from(now.plusSeconds(3600)));
        assertThat(onlyOpen.test(entry, now)).isTrue();

        // expired weeks ago with no sale recorded: not on the market any more
        entry.setDeadline(java.sql.Timestamp.from(now.minusSeconds(21 * 86400L)));
        assertThat(onlyOpen.test(entry, now)).isFalse();
        assertThat(TransferTools.ScoutFilter.from(new JsonObject()).test(entry, now)).isTrue();
    }

    @Test
    void playerTransferHistory_newestFirstWithBothClubs() {
        final var older = new module.transfer.PlayerTransfer(1, 7);
        older.setDate(core.util.HODateTime.fromHT("2025-03-01 12:00:00"));
        // setSellerid / setBuyerid read the loaded HOModel, which the unit suite has none of
        older.setSellerName("Old Club");
        older.setBuyerName("Mid Club");
        older.setPlayerName("Some Player");
        final var newer = new module.transfer.PlayerTransfer(2, 7);
        newer.setDate(core.util.HODateTime.fromHT("2026-01-10 12:00:00"));
        newer.setTsi(4200);

        final var json = TransferTools.playerTransferHistoryJson(7, List.of(older, newer));
        final var rows = json.getAsJsonArray("transfers");
        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).getAsJsonObject().get("transferId").getAsInt()).isEqualTo(2);
        assertThat(rows.get(0).getAsJsonObject().get("tsi").getAsInt()).isEqualTo(4200);
        assertThat(rows.get(1).getAsJsonObject().get("sellerTeamName").getAsString()).isEqualTo("Old Club");
        assertThat(rows.get(1).getAsJsonObject().get("buyerTeamName").getAsString()).isEqualTo("Mid Club");
    }

    private static McpTool tool(String name) {
        return TransferTools.tools().stream().filter(t -> t.name().equals(name)).findFirst()
                .orElseThrow();
    }

    @Test
    void searchBatch_rejectsEmptyQueries() {
        final var args = new JsonObject();
        args.add("queries", new JsonArray());

        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> tool("search_transfers").handler().call(args));
    }

    /**
     * One bad query must not cost the queries around it their own round trip - the whole point of
     * batching is not re-paying for the ones that already succeeded.
     */
    @Test
    void searchBatch_isolatesAFailingQueryInsteadOfThrowing() throws Exception {
        final var badQuery = new JsonObject();
        badQuery.addProperty("ageMin", 20);   // ageMax missing - queryFrom() rejects this
        final var queries = new JsonArray();
        queries.add(badQuery);
        final var args = new JsonObject();
        args.add("queries", queries);

        final var result = tool("search_transfers").handler().call(args);

        final var results = result.getAsJsonArray("results");
        assertThat(results).hasSize(1);
        final var item = results.get(0).getAsJsonObject();
        assertThat(item.getAsJsonObject("query")).isEqualTo(badQuery);
        assertThat(item.get("error").getAsString()).contains("ageMax");
        assertThat(item.has("hits")).isFalse();
    }

    /**
     * A non-object entry (a stray string, number or null from a malformed call) must not throw
     * Gson's IllegalStateException past the per-item try/catch - that would discard every query
     * already answered earlier in the array, exactly the round trip this tool exists to save.
     */
    @Test
    void searchBatch_isolatesANonObjectEntryInsteadOfDiscardingEarlierResults() throws Exception {
        final var badQuery = new JsonObject();
        badQuery.addProperty("ageMin", 20);   // ageMax missing - queryFrom() rejects this too,
        final var queries = new JsonArray();  // but it must be reached: it comes first in the array
        queries.add(badQuery);
        queries.add("oops");   // not a {ageMin, ageMax, skills} object
        final var args = new JsonObject();
        args.add("queries", queries);

        final var result = tool("search_transfers").handler().call(args);

        final var results = result.getAsJsonArray("results");
        assertThat(results).hasSize(2);
        assertThat(results.get(0).getAsJsonObject().get("error").getAsString()).contains("ageMax");
        assertThat(results.get(1).getAsJsonObject().get("error").getAsString()).contains("object");
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
                .contains("playerId", "profile", "ageTolerance", "skillTolerance", "live");
        // Neither is unconditionally required at the schema level - profile makes playerId
        // optional, so the mutual-exclusion / required-field checks live in estimateMarketValue
        // itself (see the validation tests below) rather than in the schema.
        assertThat(tool.inputSchema().getAsJsonArray("required")).isEmpty();
    }

    @Test
    void estimateMarketValue_requiresPlayerIdOrProfile() {
        assertThatExceptionOfType(McpToolException.class)
                .isThrownBy(() -> TransferTools.estimateMarketValue(new JsonObject()))
                .withMessageContaining("playerId")
                .withMessageContaining("profile");
    }

    @Test
    void estimateMarketValue_rejectsPlayerIdAndProfileTogether() {
        final var args = JsonParser.parseString(
                """
                {"playerId":1,"profile":{}}""").getAsJsonObject();

        assertThatExceptionOfType(McpToolException.class)
                .isThrownBy(() -> TransferTools.estimateMarketValue(args))
                .withMessageContaining("not both");
    }

    @Test
    void estimateMarketValue_profileRequiresAge() {
        final var args = JsonParser.parseString(
                """
                {"profile":{"skills":{"playmaking":10}}}""").getAsJsonObject();

        assertThatExceptionOfType(McpToolException.class)
                .isThrownBy(() -> TransferTools.estimateMarketValue(args))
                .withMessageContaining("age");
    }

    @Test
    void estimateMarketValue_profileRequiresAtLeastOneSkill() {
        final var args = JsonParser.parseString(
                """
                {"profile":{"age":24,"skills":{}}}""").getAsJsonObject();

        assertThatExceptionOfType(McpToolException.class)
                .isThrownBy(() -> TransferTools.estimateMarketValue(args))
                .withMessageContaining("skills");
    }

    @Test
    void estimateMarketValue_namesAnUnrecognizedSkillInProfile() {
        final var args = JsonParser.parseString(
                """
                {"profile":{"age":24,"skills":{"tackling":10}}}""").getAsJsonObject();

        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> TransferTools.estimateMarketValue(args))
                .withMessageContaining("tackling");
    }

    @Test
    void playerFromProfile_setsAgeSkillsSpecialtyAndCharacterFromTheProfile() throws McpToolException {
        final var profile = JsonParser.parseString(
                """
                {"age":24,"ageDays":40,"skills":{"playmaking":14,"passing":8},"specialty":2,
                 "agreeability":3,"aggressiveness":2,"honesty":4}""").getAsJsonObject();

        final var player = TransferTools.playerFromProfile(profile);

        assertThat(player.getAge()).isEqualTo(24);
        assertThat(player.getAgeDays()).isEqualTo(40);
        assertThat(player.getValue4Skill(PlayerSkill.PLAYMAKING)).isEqualTo(14);
        assertThat(player.getValue4Skill(PlayerSkill.PASSING)).isEqualTo(8);
        // unset skill -> Player's own untrained default (1 - Hattrick has no skill level 0)
        assertThat(player.getValue4Skill(PlayerSkill.SCORING)).isEqualTo(1);
        assertThat(player.getSpecialty()).isEqualTo(2);
        assertThat(player.getGentleness()).isEqualTo(3);
        assertThat(player.getAggressivity()).isEqualTo(2);
        assertThat(player.getHonesty()).isEqualTo(4);
    }

    /**
     * No fabricated data: a profile that never mentions specialty or character leaves the
     * synthetic Player at whatever its own unset defaults are - it is estimateMarketValue's job
     * (via targetChar == null, see comparableWeightBreakdown_ignoresCharacterWhenTargetCharIsNull)
     * to keep that default out of the weighting math, not this constructor's.
     */
    @Test
    void playerFromProfile_leavesCharacterAndSpecialtyAtPlayersOwnDefaultsWhenOmitted()
            throws McpToolException {
        final var profile = JsonParser.parseString(
                """
                {"age":24,"skills":{"playmaking":14}}""").getAsJsonObject();

        final var player = TransferTools.playerFromProfile(profile);

        assertThat(player.getSpecialty()).isZero();
        assertThat(player.getGentleness()).isEqualTo(1);   // Player's own unset default
        assertThat(player.getAggressivity()).isZero();     // Player's own unset default
        assertThat(player.getHonesty()).isEqualTo(1);      // Player's own unset default
    }

    /**
     * The fix for the gap comparableWeightBreakdown had before targetChar could be null: a
     * candidate with genuine character data must not be penalised against a fabricated {0,0,0}
     * target just because the profile never gave a character.
     */
    @Test
    void comparableWeightBreakdown_ignoresCharacterWhenTargetCharIsNull() {
        final var listing = listingWithSkillsAndCharacter(20, 0, 1, 15, 12, 8, 3, 2, 3, 2, 4);

        final var breakdown = TransferTools.comparableWeightBreakdown(listing, 2240,
                TransferTools.ageSigmaDays(20), 10, null);

        assertThat(breakdown.character()).isEqualTo(1.0);
    }

    /**
     * Reported against the profile feature: three basis: resolved_sales calls each came back with
     * a 40-row comparables array where every row had sold: false - the resolved sales backing
     * sampleSize were real, just crowded out of the array by unrelated live re-searches with a
     * fresher observedAt. evidenceOrder is the fix: the sample surfaces within the row cap
     * regardless of how the rest of the corpus was re-observed since, and the untouched rows keep
     * their relative (newest-first) order around it.
     */
    @Test
    void evidenceOrder_surfacesTheSampleAheadOfRowsThatWerentUsed() {
        final var used = new TransferListing();
        used.setPlayerId(1);
        final var unused1 = new TransferListing();
        unused1.setPlayerId(2);
        final var unused2 = new TransferListing();
        unused2.setPlayerId(3);

        // Newest-first, as estimateMarketValue hands it in - the two unused rows sort ahead of
        // the one actually backing the estimate.
        final var comparables = List.of(unused1, unused2, used);
        final var sample = List.of(new TransferTools.PricedComparable(used, 500_000L, 1.0));

        final var ordered = TransferTools.evidenceOrder(comparables, sample);

        assertThat(ordered).containsExactly(used, unused1, unused2);
    }

    @Test
    void listingFromProfile_isNullWithoutBothPlayerIdAndSalePrice() {
        final var noPlayerId = JsonParser.parseString(
                """
                {"age":24,"skills":{"playmaking":14},"salePrice":500000}""").getAsJsonObject();
        final var noSalePrice = JsonParser.parseString(
                """
                {"age":24,"skills":{"playmaking":14},"playerId":424242}""").getAsJsonObject();

        // player is never touched before the playerId/salePrice check, so null stands in fine.
        assertThat(TransferTools.listingFromProfile(noPlayerId, null)).isNull();
        assertThat(TransferTools.listingFromProfile(noSalePrice, null)).isNull();
    }

    /**
     * The motivating case: Škerbinek et al, sold and gone from the squad, valued once through
     * profile with his real sale price from get_team_transfers - this is what makes that call
     * teach future valuations something instead of being spent and discarded.
     */
    @Test
    void listingFromProfile_buildsARowFromTheGivenPlayerIdAndSalePrice() throws McpToolException {
        final var profile = JsonParser.parseString(
                """
                {"playerId":424242,"playerName":"Škerbinek","age":24,"ageDays":40,
                 "skills":{"playmaking":14,"passing":8},"specialty":2,"tsi":180000,
                 "agreeability":3,"aggressiveness":2,"honesty":4,"salePrice":1250000}""")
                .getAsJsonObject();
        final var player = TransferTools.playerFromProfile(profile);

        final var listing = TransferTools.listingFromProfile(profile, player);

        assertThat(listing).isNotNull();
        assertThat(listing.getPlayerId()).isEqualTo(424242);
        assertThat(listing.getPlayerName()).isEqualTo("Škerbinek");
        assertThat(listing.getAge()).isEqualTo(24);
        assertThat(listing.getAgeDays()).isEqualTo(40);
        assertThat(listing.getPlaymaker()).isEqualTo(14);
        assertThat(listing.getPassing()).isEqualTo(8);
        assertThat(listing.getSpecialty()).isEqualTo(2);
        assertThat(listing.getTsi()).isEqualTo(180000);
        assertThat(listing.getAgreeability()).isEqualTo(3);
        assertThat(listing.getAggressiveness()).isEqualTo(2);
        assertThat(listing.getHonesty()).isEqualTo(4);
        assertThat(listing.getSalePrice()).isEqualTo(1_250_000L);
        assertThat(listing.getObservedAt()).isNotNull();
    }

    /**
     * No fabrication into the corpus: an omitted skill must stay null here even though
     * playerFromProfile's own synthetic Player defaults it to 1 for the weighting math - a
     * fabricated "1" written to this row would mislead every future query that reads it back.
     */
    @Test
    void listingFromProfile_onlyWritesSkillsTheProfileActuallyGave() throws McpToolException {
        final var profile = JsonParser.parseString(
                """
                {"playerId":424242,"age":24,"skills":{"playmaking":14},"salePrice":1250000}""")
                .getAsJsonObject();
        final var player = TransferTools.playerFromProfile(profile);

        final var listing = TransferTools.listingFromProfile(profile, player);

        assertThat(listing.getPlaymaker()).isEqualTo(14);
        assertThat(listing.getScorer()).isNull();
        assertThat(listing.getKeeper()).isNull();
        assertThat(listing.getSpecialty()).isNull();
        assertThat(listing.getAgreeability()).isNull();
    }

    /**
     * Caught by the hattrick session live: a profile's salePrice arrives in the user's local
     * currency, same as every amount this MCP surface hands the model, but TransferListing's own
     * column holds swedish krona - storing it raw corrupted every recorded row by the league's
     * exchange rate once the response converted it back for display (677000 in, 67700 shown).
     */
    @Test
    void listingFromProfile_convertsSalePriceFromLocalCurrencyToSwedishKrona() throws McpToolException {
        AmountOfMoney.Companion.setExchangeRate(BigDecimal.TEN);   // a euro league
        final var profile = JsonParser.parseString(
                """
                {"playerId":424242,"age":24,"skills":{"playmaking":14},"salePrice":677000}""")
                .getAsJsonObject();
        final var player = TransferTools.playerFromProfile(profile);

        final var listing = TransferTools.listingFromProfile(profile, player);

        assertThat(listing.getSalePrice()).isEqualTo(6_770_000L);
    }

    /**
     * The gap the hattrick session's re-verification surfaced: playerFromProfile collapses "never
     * given" and "confirmed none" into the same 0 for its own weighting math, but the corpus
     * filter must not make that same conflation - a departed player HO never profiled (specialty
     * genuinely unknown) must not be treated as confirmed specialty-free.
     */
    @Test
    void corpusSpecialtyFilter_treatsAnOmittedProfileSpecialtyAsUnknownNotZero() {
        final var profile = JsonParser.parseString(
                """
                {"age":24,"skills":{"playmaking":14}}""").getAsJsonObject();

        assertThat(TransferTools.corpusSpecialtyFilter(true, profile, player(24, 14))).isNull();
    }

    @Test
    void corpusSpecialtyFilter_honorsAConfirmedZeroFromTheProfile() {
        final var profile = JsonParser.parseString(
                """
                {"age":24,"skills":{"playmaking":14},"specialty":0}""").getAsJsonObject();

        assertThat(TransferTools.corpusSpecialtyFilter(true, profile, player(24, 14))).isZero();
    }

    @Test
    void corpusSpecialtyFilter_passesThroughAConfirmedSpecificSpecialty() {
        final var profile = JsonParser.parseString(
                """
                {"age":24,"skills":{"playmaking":14},"specialty":4}""").getAsJsonObject();

        assertThat(TransferTools.corpusSpecialtyFilter(true, profile, player(24, 14))).isEqualTo(4);
    }

    /** A squad player's specialty is always definitive, never "unknown" - read straight off Player. */
    @Test
    void corpusSpecialtyFilter_forAPlayerIdReadsStraightOffThePlayer() {
        final var squadPlayer = player(24, 14);
        squadPlayer.setSpecialty(3);

        assertThat(TransferTools.corpusSpecialtyFilter(false, null, squadPlayer)).isEqualTo(3);
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

    /** Our own listed players come back in their own list whatever includeAll says. */
    @Test
    void openBids_listsOurOwnListedPlayersUnderSelling() throws Exception {
        installBidsService(sampleBids());
        final var selling = openBidsTool().handler().call(new JsonObject()).getAsJsonArray("selling");
        assertThat(playerIds(selling)).containsExactly(444);
        final var sam = selling.get(0).getAsJsonObject();
        assertThat(sam.get("hasBids").getAsBoolean()).isTrue();
        assertThat(sam.get("highestBidTeamName").getAsString()).isEqualTo("Buyer FC");
        assertThat(sam.get("deadline").getAsString()).isEqualTo("2030-01-01 20:00:00");
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
        // Never the player's own: every live hit becomes a comparable, so a filter would skew the corpus.
        assertThat(query.specialty()).isNull();
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
