package module.transfer;

import core.util.HODateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The paging and stop rule behind {@code updateTeamTransfersIncremental}.
 * <p>
 * What is worth pinning down here is that the loop terminates and that it stops early: the whole
 * point of the incremental version is that a routine download costs one page, where
 * {@code updateTeamTransfers} costs the team's entire history every time.
 */
class XMLParserTest {

    private static final HODateTime NOW = HODateTime.now();
    private static final HODateTime END = NOW.plus(1, ChronoUnit.DAYS);

    @BeforeEach
    void activateFromLongAgo() {
        // Set by the real page download; every transfer below is meant to be inside the window.
        PlayerTransfer.activatedDate = NOW.plus(-3650, ChronoUnit.DAYS);
    }

    private static PlayerTransfer transfer(int id, HODateTime date) {
        final var t = new PlayerTransfer(id, id * 100);
        t.setDate(date);
        return t;
    }

    /** A pager over fixed pages that records how many times it was asked. */
    private static final class Pager {
        final AtomicInteger calls = new AtomicInteger();
        final List<List<PlayerTransfer>> pages;

        Pager(List<List<PlayerTransfer>> pages) {
            this.pages = pages;
        }

        List<PlayerTransfer> page(int index) {
            calls.incrementAndGet();
            return index < pages.size() ? pages.get(index) : List.of();
        }
    }

    @Test
    void stopsAfterTheFirstPageWhenEverythingOnItIsAlreadyStored() {
        var pager = new Pager(List.of(
                List.of(transfer(1, NOW), transfer(2, NOW)),
                List.of(transfer(3, NOW))));
        var known = Set.of(1, 2, 3);

        var fresh = XMLParser.collectNewTransfers(pager::page, known::contains, END);

        assertThat(fresh).isEmpty();
        assertThat(pager.calls).hasValue(1);
    }

    @Test
    void followsAPartlyNewPageIntoTheNextOne() {
        var pager = new Pager(List.of(
                List.of(transfer(1, NOW), transfer(2, NOW)),
                List.of(transfer(3, NOW))));
        var known = Set.of(2, 3);

        var fresh = XMLParser.collectNewTransfers(pager::page, known::contains, END);

        assertThat(fresh).extracting(PlayerTransfer::getTransferId).containsExactly(1);
        assertThat(pager.calls).hasValue(2);
    }

    @Test
    void stopsOnAnEmptyPage() {
        var pager = new Pager(List.of(List.of(transfer(1, NOW))));

        var fresh = XMLParser.collectNewTransfers(pager::page, id -> false, END);

        assertThat(fresh).extracting(PlayerTransfer::getTransferId).containsExactly(1);
        assertThat(pager.calls).hasValue(2);
    }

    @Test
    void keepsOnlyTransfersInsideTheStoredWindow() {
        var old = NOW.plus(-4000, ChronoUnit.DAYS);        // before activation
        var future = NOW.plus(30, ChronoUnit.DAYS);        // past endDate
        var pager = new Pager(List.of(
                List.of(transfer(1, old), transfer(2, NOW), transfer(3, future))));

        var fresh = XMLParser.collectNewTransfers(pager::page, id -> false, END);

        assertThat(fresh).extracting(PlayerTransfer::getTransferId).containsExactly(2);
    }

    /**
     * A transfer the date filter drops is never stored, so it stays unknown for ever. The stop test
     * runs over the unfiltered page precisely so that cannot spin: the page still looks "new", but
     * the next page settles it.
     */
    @Test
    void aPermanentlyUnstorableTransferDoesNotLoopForEver() {
        var future = NOW.plus(30, ChronoUnit.DAYS);
        var pager = new Pager(List.of(
                List.of(transfer(1, future), transfer(2, NOW)),
                List.of(transfer(3, NOW))));
        var known = Set.of(2, 3);

        var fresh = XMLParser.collectNewTransfers(pager::page, known::contains, END);

        assertThat(fresh).isEmpty();
        assertThat(pager.calls).hasValue(2);
    }

    /** The pages a real run walks: it must not stop before it has everything new. */
    @Test
    void walksSeveralConsecutiveNewPages() {
        var pages = new ArrayList<List<PlayerTransfer>>();
        pages.add(List.of(transfer(1, NOW), transfer(2, NOW)));
        pages.add(List.of(transfer(3, NOW), transfer(4, NOW)));
        pages.add(List.of(transfer(5, NOW)));
        var pager = new Pager(pages);
        var known = Set.of(5);

        var fresh = XMLParser.collectNewTransfers(pager::page, known::contains, END);

        assertThat(fresh).extracting(PlayerTransfer::getTransferId)
                .containsExactly(1, 2, 3, 4);
        assertThat(pager.calls).hasValue(3);
    }
}
