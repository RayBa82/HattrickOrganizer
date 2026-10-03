package core.mcp;

import core.db.TransferListing;
import core.util.HODateTime;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MarketCorpusStatsTest {

    private static final HODateTime NOW = HODateTime.fromHT("2026-10-03 12:00:00");

    private static HODateTime daysAgo(int days) {
        return new HODateTime(NOW.getInstant().minus(Duration.ofDays(days)));
    }

    private static TransferListing row(int playerId, HODateTime observedAt, HODateTime deadline) {
        final var l = new TransferListing();
        l.setPlayerId(playerId);
        l.setObservedAt(observedAt);
        l.setDeadline(deadline);
        return l;
    }

    /** A search hit: age, specialty and the six positional skills. */
    private static TransferListing hit(int playerId, HODateTime observedAt, HODateTime deadline,
                                       int age, int specialty, int playmaker, int winger) {
        final var l = row(playerId, observedAt, deadline);
        l.setAge(age);
        l.setSpecialty(specialty);
        l.setKeeper(1);
        l.setDefender(3);
        l.setPlaymaker(playmaker);
        l.setPassing(3);
        l.setWinger(winger);
        l.setScorer(3);
        return l;
    }

    @Test
    void countsListingsByOutcomeAndBreaksTheWindowDown() {
        final var sold1 = hit(1, daysAgo(10), daysAgo(5), 18, 0, 10, 4);
        sold1.setSalePrice(500_000L);
        final var sold2 = hit(1, daysAgo(8), daysAgo(5), 18, 0, 10, 4);   // same listing, seen again
        sold2.setSalePrice(500_000L);
        final var open = hit(2, daysAgo(1), new HODateTime(NOW.getInstant().plus(Duration.ofDays(2))),
                21, 5, 4, 9);
        final var old = hit(3, daysAgo(200), daysAgo(199), 19, 0, 8, 2);
        old.setResolvedAt(daysAgo(190));
        final var fromBids = row(4, daysAgo(3), daysAgo(2));   // currentbids: no age, no skills

        final var stats = TransferTools.corpusStats(List.of(sold1, sold2, open, old, fromBids), NOW, 112);

        assertThat(stats.get("observations").getAsInt()).isEqualTo(5);
        assertThat(stats.get("listings").getAsInt()).isEqualTo(4);
        assertThat(stats.get("sold").getAsInt()).isEqualTo(1);
        assertThat(stats.get("open").getAsInt()).isEqualTo(1);
        assertThat(stats.get("noSaleFound").getAsInt()).isEqualTo(1);
        assertThat(stats.get("awaitingResolve").getAsInt()).isEqualTo(1);
        assertThat(stats.get("oldestObservedAt").getAsString()).isEqualTo(daysAgo(200).toHT());
        assertThat(stats.get("newestObservedAt").getAsString()).isEqualTo(daysAgo(1).toHT());

        final var window = stats.getAsJsonObject("window");
        assertThat(window.get("days").getAsInt()).isEqualTo(112);
        assertThat(window.get("observations").getAsInt()).isEqualTo(4);
        assertThat(window.get("listings").getAsInt()).isEqualTo(3);
        assertThat(window.get("sold").getAsInt()).isEqualTo(1);

        final var byAge = window.getAsJsonObject("byAge");
        assertThat(byAge.getAsJsonObject("17").get("listings").getAsInt()).isZero();
        assertThat(byAge.getAsJsonObject("18").get("listings").getAsInt()).isEqualTo(1);
        assertThat(byAge.getAsJsonObject("18").get("sold").getAsInt()).isEqualTo(1);
        assertThat(byAge.getAsJsonObject("19").get("listings").getAsInt()).isZero();   // outside window
        assertThat(byAge.getAsJsonObject("20+").get("listings").getAsInt()).isEqualTo(1);
        assertThat(byAge.getAsJsonObject("unknown").get("listings").getAsInt()).isEqualTo(1);

        final var byGroup = window.getAsJsonObject("byPositionGroup");
        assertThat(byGroup.getAsJsonObject("inner_mid").get("sold").getAsInt()).isEqualTo(1);
        assertThat(byGroup.getAsJsonObject("winger").get("listings").getAsInt()).isEqualTo(1);
        assertThat(byGroup.getAsJsonObject("keeper").get("listings").getAsInt()).isZero();
        assertThat(byGroup.getAsJsonObject("unknown").get("listings").getAsInt()).isEqualTo(1);

        final var bySpecialty = window.getAsJsonObject("bySpecialty");
        assertThat(bySpecialty.getAsJsonObject("0").get("listings").getAsInt()).isEqualTo(1);
        assertThat(bySpecialty.getAsJsonObject("5").get("listings").getAsInt()).isEqualTo(1);
        assertThat(bySpecialty.getAsJsonObject("unknown").get("listings").getAsInt()).isEqualTo(1);
    }

    /**
     * The answer is a fixed set of buckets, so its size does not grow with the corpus: there is no
     * over-budget path to page or truncate. This pins that.
     */
    @Test
    void aLargeCorpusStaysFarUnderTheResponseBudget() {
        final var rows = new ArrayList<TransferListing>();
        for (int i = 0; i < 5_000; i++) {
            final var l = hit(i, daysAgo(i % 300), daysAgo(i % 300 - 3), 17 + i % 20, i % 9, i % 20, i % 17);
            if (i % 3 == 0) l.setSalePrice(100_000L + i);
            rows.add(l);
        }
        final var json = TransferTools.corpusStats(rows, NOW, 112).toString();
        assertThat(json.length()).isLessThan(10_000 / 4);
    }

    @Test
    void anEmptyCorpusAnswersWithZeros() {
        final var stats = TransferTools.corpusStats(List.of(), NOW, 112);
        assertThat(stats.get("listings").getAsInt()).isZero();
        assertThat(stats.get("oldestObservedAt").isJsonNull()).isTrue();
    }
}
