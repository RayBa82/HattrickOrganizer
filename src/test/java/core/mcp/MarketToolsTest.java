package core.mcp;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import core.file.xml.XMLTransferSearchParser;
import core.util.AmountOfMoney;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/** screen_market's rules, on hits built in memory: no search, no database. */
class MarketToolsTest {

    private static final int OURS = 3;

    @BeforeEach
    void baselineExchangeRate() {
        AmountOfMoney.Companion.setExchangeRate(BigDecimal.ONE);
    }

    private static XMLTransferSearchParser.Hit hit(int country, long salary, int specialty,
            int injury, int defender, long ask, long bid) {
        final var details = new XMLTransferSearchParser.Details(19, 40, salary, 1000, 5, 3, 3,
                specialty, 0, injury, 5, 1, 9, 2, 5, 3, defender, 4);
        return new XMLTransferSearchParser.Hit(7, "A", country, ask, null, bid, null, 1, "S", 1,
                details);
    }

    private static MarketTools.Rules rules(String json) {
        return MarketTools.Rules.from(JsonParser.parseString(json).getAsJsonObject());
    }

    @Test
    void screenMarket_isATaskAndTakesTheRulesAsArguments() {
        final var tool = MarketTools.tools().get(0);
        assertThat(tool.name()).isEqualTo("screen_market");
        assertThat(tool.taskEligible(new JsonObject())).isTrue();
        assertThat(tool.inputSchema().getAsJsonObject("properties").keySet()).contains("queries",
                "maxWage", "specialties", "top", "characterTop", "reservedCash", "scout");
    }

    @Test
    void plan_expandsASpecialtyListIntoOneSearchEach() {
        final var planned = MarketTools.plan(JsonParser.parseString("""
                [{"label":"S1","ageMin":17,"ageMax":18,"specialty":[4,5],"screen":{"defending":12},
                  "skills":[{"skill":"playmaking","min":8,"max":11}]},
                 {"ageMin":25,"ageMax":27,"skills":[{"skill":"defending","min":3,"max":6}]}]
                """).getAsJsonArray());
        assertThat(planned).hasSize(3);
        assertThat(planned.get(0).query().get("specialty").getAsInt()).isEqualTo(4);
        assertThat(planned.get(1).query().get("specialty").getAsInt()).isEqualTo(5);
        assertThat(planned.get(0).query().has("label")).isFalse();
        assertThat(planned.get(0).query().has("screen")).isFalse();
        assertThat(planned.get(0).screen()).containsEntry("defending", 12);
        assertThat(planned.get(2).index()).isEqualTo(1);
        assertThat(planned.get(2).query().has("specialty")).isFalse();

        final var unknownSkill = JsonParser.parseString("[{\"screen\":{\"speed\":3}}]").getAsJsonArray();
        assertThatIllegalArgumentException().isThrownBy(() -> MarketTools.plan(unknownSkill));
    }

    @Test
    void rejection_namesTheFirstRuleAHitFails() {
        final var rules = rules("{\"maxWage\":10000,\"specialties\":[4,5]}");
        final var screen = Map.of("defending", 5);
        assertThat(MarketTools.rejection(hit(OURS, 9000, 4, -1, 6, 0, 0), screen, rules, OURS)).isNull();
        assertThat(MarketTools.rejection(hit(OURS, 9000, 4, 2, 6, 0, 0), screen, rules, OURS)).isEqualTo("injured");
        assertThat(MarketTools.rejection(hit(OURS, 9000, 3, -1, 6, 0, 0), screen, rules, OURS)).isEqualTo("specialty");
        assertThat(MarketTools.rejection(hit(OURS, 9000, 4, -1, 4, 0, 0), screen, rules, OURS)).isEqualTo("screen");
        // 9,000 is fine at home and 10,800 abroad
        assertThat(MarketTools.rejection(hit(9, 9000, 4, -1, 6, 0, 0), screen, rules, OURS)).isEqualTo("wage");
        assertThat(MarketTools.rejection(hit(9, 9000, 4, 2, 6, 0, 0), Map.of(),
                rules("{\"excludeInjured\":false,\"foreignWageFactor\":1.0}"), OURS)).isNull();
        final var closed = new XMLTransferSearchParser.Hit(7, "A", OURS, 0, null, 0, null, 1, "S", 1, null);
        assertThat(MarketTools.rejection(closed, Map.of(), rules, OURS)).isEqualTo("closed");
    }

    @Test
    void candidate_pricesByTheBidElseTheAskAndMarksForeigners() {
        final var rules = rules("{}");
        final var bid = MarketTools.candidate(hit(9, 1000, 4, -1, 6, 50_000, 70_000), rules, OURS);
        assertThat(bid.price()).isEqualTo(70_000L);
        assertThat(bid.priceSource()).isEqualTo("bid");
        assertThat(bid.foreign()).isTrue();
        assertThat(bid.wageInSquad()).isEqualTo(1200);
        final var ask = MarketTools.candidate(hit(OURS, 1000, 4, -1, 6, 50_000, 0), rules, OURS);
        assertThat(ask.priceSource()).isEqualTo("ask");
        assertThat(ask.wageInSquad()).isEqualTo(1000);
        assertThat(MarketTools.candidate(hit(OURS, 1000, 4, -1, 6, 0, 0), rules, OURS).price()).isNull();
        // without our country nobody counts as foreign
        assertThat(MarketTools.candidate(hit(9, 1000, 4, -1, 6, 0, 0), rules, null).foreign()).isFalse();
    }

    @Test
    void cardRisk_isFoulPlusDiveAndSaysWhenItCannotTell() {
        assertThat(MarketTools.cardRisk(4, 0).getAsDouble()).isEqualTo(21.5);
        assertThat(MarketTools.cardRisk(1, 3).getAsDouble()).isEqualTo(3.0);
        assertThat(MarketTools.cardRisk(5, 3).getAsString()).isEqualTo("n/a");
        assertThat(MarketTools.cardRisk(null, 3).getAsString()).isEqualTo("?");
    }

    @Test
    void rules_defaultToTheSkillsStandards() {
        final var rules = rules("{\"top\":99}");
        assertThat(rules.maxWage()).isEqualTo(10_000);
        assertThat(rules.foreignFactor()).isEqualTo(1.2);
        assertThat(rules.top()).isEqualTo(25);
        assertThat(rules.characterTop()).isEqualTo(25);
        assertThat(rules.wageWeeks()).isEqualTo(48);
        assertThat(rules.squadCap()).isEqualTo(26);
        assertThat(rules.specialties()).isNull();
        assertThat(rules.scout()).isZero();
        assertThat(rules.minSample()).isEqualTo(15);
        assertThat(rules("{\"specialties\":[2,5]}").specialties()).isEqualTo(Set.of(2, 5));
    }

    @Test
    void estimateFigure_prefersTheSaleEstimateOverTheListingsFigure() {
        assertThat(MarketTools.estimateFigure(JsonParser.parseString(
                "{\"estimate\":35000}").getAsJsonObject())).isEqualTo(35_000L);
        assertThat(MarketTools.estimateFigure(JsonParser.parseString(
                "{\"estimate\":null,\"listingsEstimate\":{\"value\":20000}}").getAsJsonObject()))
                .isEqualTo(20_000L);
        assertThat(MarketTools.estimateFigure(new JsonObject())).isNull();
    }

    /** Every reason comes with 0 too, in a fixed order. */
    @Test
    void emptyRejections_listEveryReasonAtZero() {
        assertThat(MarketTools.emptyRejections()).containsExactly(Map.entry("closed", 0),
                Map.entry("injured", 0), Map.entry("specialty", 0), Map.entry("screen", 0),
                Map.entry("wage", 0));
    }

    @Test
    void warnings_nameTwoYellowsASuspensionAndAThinSample() {
        final var warnings = new JsonArray();
        MarketTools.addCardWarning(1, warnings);
        assertThat(warnings).isEmpty();
        MarketTools.addCardWarning(2, warnings);
        MarketTools.addCardWarning(3, warnings);
        MarketTools.addSampleWarning(6, 15, warnings);
        MarketTools.addSampleWarning(15, 15, warnings);
        MarketTools.addSampleWarning(null, 15, warnings);
        assertThat(warnings).hasSize(3);
        assertThat(warnings.get(0).getAsString()).startsWith("2 yellow cards");
        assertThat(warnings.get(1).getAsString()).startsWith("suspended");
        assertThat(warnings.get(2).getAsString()).isEqualTo("thin estimate: n=6");
    }

    @Test
    void columns_carryCardsAndTheEstimateBasis() {
        assertThat(MarketTools.COLUMNS).containsSubsequence("cardRisk", "cards", "closes",
                "estimate", "estimateBasis", "middleHalf");
    }

    /** The first live run had to learn a query's fields from one error message after another. */
    @Test
    void schemaSpellsOutTheFieldsOfAQuery() {
        final var queries = MarketTools.tools().get(0).inputSchema()
                .getAsJsonObject("properties").getAsJsonObject("queries");
        final var entry = queries.getAsJsonObject("items");

        assertThat(entry.getAsJsonObject("properties").keySet())
                .contains("ageMin", "ageMax", "skills", "specialty", "priceMax", "label", "screen");
        assertThat(entry.getAsJsonArray("required")).hasSize(3);
        assertThat(entry.getAsJsonObject("properties").getAsJsonObject("skills")
                .getAsJsonObject("items").getAsJsonObject("properties").keySet())
                .containsExactly("skill", "min", "max");
    }
}
