package core.net;

import core.db.DBManager;
import core.db.TransferListing;
import core.file.xml.XMLPlayersParser;
import core.gui.HOMainFrame;
import core.model.UserParameter;
import core.util.HODateTime;
import core.util.HOLogger;
import core.util.Helper;
import module.transfer.XMLParser;
import module.transfer.scout.ScoutEintrag;
import module.transfer.scout.ScoutList;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;

/**
 * Fills the real sale price into the transfer listing corpus, so a valuation has actual transfer
 * prices to work from rather than only asking prices and bids.
 * <p>
 * One {@code transfersPlayer} call per player answers "what did he actually go for", and
 * {@link XMLParser#updatePlayerTransfers} already makes exactly that call and stores the resulting
 * transfers in HO's own TRANSFER table - where the per-player transfer history UI reads them. So
 * this pass reuses it and takes the sale price out of what it returns, instead of fetching the same
 * file for itself and throwing the records away. Team-scoped views are unaffected: they filter on
 * buyerid / sellerid being the managed team, which a market player's transfer never is.
 * <p>
 * Runs after a team download and from the button in Options - never from a tool call, so an agent
 * still cannot start a batch of CHPP calls.
 */
public final class TransferListingResolver {

    /**
     * How much of a spread there may be between a listing's deadline and the sale date before the
     * two are taken to be different listings. A Hattrick transfer completes at its deadline, so
     * this only absorbs clock and timezone skew.
     */
    private static final java.time.Duration SALE_WINDOW = java.time.Duration.ofDays(2);

    /**
     * How long after its deadline a transfer takes to show up in {@code transfersPlayer}.
     * <p>
     * Not instant: 93 scout sales recorded their sale up to 13 minutes after the listed deadline.
     * Asked inside that gap, the lookup answers "no sale", the listing is marked looked-at and the
     * watermark moves past it - the price is lost for good. So an entry is a sale candidate only
     * once this has passed.
     */
    private static final java.time.Duration SALE_SETTLE = java.time.Duration.ofMinutes(20);

    /**
     * How long after a deadline a {@code playerdetails} fetch still describes the player that was
     * on the market. Past that he has been at his new club long enough to have trained, so filling
     * his skills in would file today's player against a months-old listing and let him vote in a
     * valuation under skills he did not have when he sold. No skills at all is the better answer:
     * a row with NULL skills cannot match {@code loadComparables}, so it stays out rather than
     * lying.
     */
    private static final java.time.Duration SKILL_FILL_GRACE = java.time.Duration.ofDays(14);

    /**
     * How far back a stale watermark may reach when picking entries for the sale pass.
     * <p>
     * Normally the watermark is the last run, an hour or two ago. After a long absence it is
     * whatever it was when HO was last opened, and without a floor the first run back would ask
     * about every entry that expired in the meantime. A fortnight is generous for a question a
     * completed transfer answers immediately.
     */
    private static final java.time.Duration SCOUT_RETRY_GRACE = java.time.Duration.ofDays(14);

    /**
     * How long after its deadline an unsold scout entry is still watched for a re-listing.
     * <p>
     * Longer than {@link #SCOUT_RETRY_GRACE} on purpose: that one bounds "what did he sell for", a
     * question a completed transfer answers immediately, so a fortnight is generous. This bounds
     * "is he back", which a seller may take months to answer - a young prospect withdrawn from the
     * market is exactly the case where the wait is long and the payoff is highest.
     */
    private static final java.time.Duration SCOUT_WATCH_GRACE = java.time.Duration.ofDays(90);

    /**
     * The only bound on a run, and it is not a CHPP quota.
     * <p>
     * {@code transfersPlayer} and {@code playerdetails} are ordinary read files - Hattrick limits
     * only {@code transfersearch} - and HO's own Transfers module already loops them with no limit
     * at all ({@code XMLParser.updateTeamTransfers}, {@code TransfersPanel}). What matters is that a
     * download runs on the event dispatch thread, so every lookup is time the UI is frozen. A call
     * count cannot express that - the same 200 calls are half a minute on a fast link and five
     * minutes on a bad one - and the count cap this replaced did real harm: at 20 calls it filled
     * five scout sales per download, so the user had to press Download over and over.
     */
    private static final java.time.Duration TIME_BUDGET = java.time.Duration.ofSeconds(60);

    private TransferListingResolver() {
    }

    /** @return how many listings got a sale price */
    public static int resolve() {
        final var now = HODateTime.now();
        final var deadline = System.nanoTime() + TIME_BUDGET.toNanos();
        var resolved = 0;
        final var seen = new HashSet<Integer>();
        final var looked = new HashSet<Integer>();   // playerdetails fetched for him this run
        final var entries = ScoutList.entries();

        // All three work lists up front, so the status bar can count through the whole run - it can
        // take a minute, and a bar that stands still for that long looks like HO has hung. Loading
        // the corpus before the scout passes loses nothing: the rows they add are live listings,
        // which the corpus pass would skip, and a player they resolve is skipped through seen.
        final var sales = salesToAskAbout(entries, saleWatermark(now), now);
        final var watch = relistingsToWatch(entries, now);
        final var corpus = DBManager.instance().loadUnresolvedTransferListings(Integer.MAX_VALUE);
        final var progress = new Progress(sales.size() + watch.size() + corpus.size());

        // Pass one: the sale, for entries whose watched transfer ended since the last run.
        //
        // Scoped by a watermark, because "what did he sell for" has exactly one moment to be
        // answered and the set that needs asking is small - the handful that expired since the last
        // download. The earlier version walked the whole list under a call cap instead, which meant
        // it spent every download re-asking about whichever entries sat near the head and never
        // reached the rest: a player who had sold twenty minutes before sat at position 85 of 92 and
        // was skipped download after download, indefinitely.
        var resolvedThrough = settled(now);
        for (final var candidate : sales) {
            progress.step();
            if (outOfTime(deadline)) {
                // Leave the watermark at the first candidate not reached, not at now. The window
                // is inclusive of it, so a truncated run resumes next time. Erring towards asking
                // twice costs a CHPP call; erring the other way loses a sale price for good.
                resolvedThrough = candidate.deadline();
                break;
            }
            looked.add(candidate.playerId());
            // seen means "his sale has been asked about this run", which stops the corpus loop
            // below spending a second call on the same player's TRANSFERLISTING rows.
            seen.add(candidate.playerId());
            final var observation = observe(candidate.playerId(), now);
            // Free: the call has already been made, and character comes back whether or not he is
            // listed. Fills in entries added before add_to_scout started fetching it.
            if (observation != null) {
                recordCharacterOnScoutList(candidate.playerId(), observation);
            }
            // Asked whether or not he is listed again: a buyer re-listing a player he has just
            // bought is ordinary Hattrick behaviour, and this is the last moment the sale can be
            // recorded - the refresh below moves the deadline forward, after which
            // isTheWatchedTransfer can never line this sale up with this entry again.
            //
            // ponytail: no SELLERTEAMID column on TRANSFERLISTING, so this still costs a
            // transfersPlayer call - add the column and compare observation.owningTeamId() against
            // it if the scout list ever grows enough for one call per watched entry to matter.
            final var outcome = resolvePlayer(candidate.playerId(), candidate.deadline(), now);
            if (outcome.resolved()) {
                resolved++;
            }
            if (outcome.failed()) {
                // The lookup did not answer. Stop here and leave the watermark on this candidate:
                // a network blip must not silently consume the one moment his sale was askable.
                resolvedThrough = candidate.deadline();
                break;
            }
            if (observation != null && observation.transferListed()) {
                recordRelistingOnScoutList(candidate.playerId(), observation);
            }
        }
        setSaleWatermark(resolvedThrough);

        // Pass two: the re-listing watch. Unlike the sale it has no single moment and no answer
        // that ends it - "is he back on the market" is asked on every download for as long as
        // SCOUT_WATCH_GRACE. Newest expiry first so a run cut short by the clock skips the stalest
        // entries, and it asks nothing about sales: pass one has already done that, once, for
        // every entry here.
        for (final var candidate : watch) {
            progress.step();
            if (outOfTime(deadline)) {
                break;
            }
            if (!looked.add(candidate.playerId())) {
                continue;
            }
            final var observation = observe(candidate.playerId(), now);
            if (observation == null) {
                continue;
            }
            recordCharacterOnScoutList(candidate.playerId(), observation);
            if (observation.transferListed()) {
                recordRelistingOnScoutList(candidate.playerId(), observation);
            }
        }

        // Then the valuation corpus, on its own budget rather than whatever passes one and two left
        // of TIME_BUDGET - see CORPUS_BACKFILL_BUDGET's doc for why. Rows, not players: one player
        // repeatedly observed holds several.
        final var corpusDeadline = System.nanoTime() + CORPUS_BACKFILL_BUDGET.toNanos();
        for (final var listing : corpus) {
            progress.step();
            if (outOfTime(corpusDeadline)) {
                break;
            }
            if (!seen.add(listing.getPlayerId())) {
                continue;                      // his sale was already asked about this run
            }
            if (looked.add(listing.getPlayerId())) {
                observeForCorpus(listing, now);
            }
            if (listing.getDeadline() == null
                    || listing.getDeadline().compareTo(settled(now)) >= 0) {
                continue;                      // not settled yet, nothing to resolve
            }
            if (resolvePlayer(listing.getPlayerId(), listing.getDeadline(), now).resolved()) {
                resolved++;
            }
        }
        return resolved;
    }

    /**
     * How long the general-corpus passes may run: the valuation corpus's sale-price backfill inside
     * {@link #resolve}, and {@link #backfillCharacter}. Deliberately its own budget, smaller than
     * and separate from {@link #TIME_BUDGET}'s 60 seconds, rather than either one sharing whatever
     * the scout-list passes above it in {@code resolve()} leave over.
     * <p>
     * Scout-list entries are urgent (a sale has exactly one moment it can still be asked about) and
     * few, so they get first claim on {@code TIME_BUDGET} and can legitimately spend the whole 60
     * seconds on a busy run. The general corpus - which is what {@code estimate_market_value}
     * actually reads - has no such urgency, but starving it of any budget at all just because the
     * scout list was busy is the wrong trade: it grew by 19 rows over several downloads while
     * character, freed of that competition by this same budget, grew by over a thousand. Neither
     * pass needs a window that closes the way a sale price freshly past its deadline does, so a
     * player or row this budget runs out on is simply picked up again next call.
     */
    private static final java.time.Duration CORPUS_BACKFILL_BUDGET = java.time.Duration.ofSeconds(20);

    /**
     * Hard cap on how many players {@link core.mcp.TransferTools}'s {@code backfill_character} tool
     * call may look up in one call, regardless of the time budget.
     */
    public static final int MAX_CHARACTER_BACKFILL = 200;

    /** @param checked how many players were actually looked up before time or the cap ran out
     *  @param updated how many of those got character filled in */
    public record CharacterBackfillResult(int checked, int updated) {
    }

    /**
     * Fill in character (agreeability/aggressiveness/honesty) for corpus players that have never
     * had it fetched, oldest observation first. One {@code playerdetails} call per player.
     * <p>
     * Character survives Hattrick's unlisted-player pruning - unlike the seven hidden skills, a
     * {@code playerdetails} call answers it for any player, listed or not - so unlike the skills-fill
     * pass in {@link #observe} this is not limited to players back on the market right now; it can
     * work through the whole corpus.
     * <p>
     * Time-bounded for the reason documented on {@link #TIME_BUDGET}: a fixed call count is half a
     * minute on a fast link and much longer on a slow one, and this runs on the caller's thread -
     * the download's event-dispatch thread when called from {@link OnlineWorker#resolveTransferListings},
     * or an MCP request thread when called from the tool. Best effort per player: a failed lookup is
     * logged and skipped, not retried within this run.
     * <p>
     * Delegates the actual lookup to {@link #observe}, the same method {@link #resolve}'s three
     * passes use - not a separate {@code playerdetails} call of its own. {@code resolve()} runs
     * first in {@link OnlineWorker#resolveTransferListings}, and {@code observe()} persists
     * character unconditionally, so a player any of its passes already looked up this download is
     * no longer missing character by the time this pass queries the corpus - it is never asked
     * about twice in the same run.
     *
     * @param max upper bound on players looked up, independent of the time budget
     */
    public static CharacterBackfillResult backfillCharacter(int max) {
        final var deadline = System.nanoTime() + CORPUS_BACKFILL_BUDGET.toNanos();
        final var now = HODateTime.now();
        final var playerIds = DBManager.instance().loadTransferListingPlayerIdsMissingCharacter(max);
        var checked = 0;
        var updated = 0;
        for (final var playerId : playerIds) {
            if (outOfTime(deadline)) {
                break;
            }
            checked++;
            // observe() itself persists character (and, if he is listed again, a fresh snapshot
            // row) - reused rather than duplicated so this pass and resolve()'s three passes never
            // each spend their own playerdetails call on the same player.
            if (observe(playerId, now) != null) {
                updated++;
            }
        }
        return new CharacterBackfillResult(checked, updated);
    }

    /**
     * How far the sale pass got last time, so this run picks up where it left off.
     * <p>
     * Floored at {@link #SCOUT_RETRY_GRACE}: a watermark from before a long absence would otherwise
     * make the first run back ask about every entry that expired in the meantime. Missing means
     * "never run", which the floor turns into the last fortnight.
     */
    private static HODateTime saleWatermark(HODateTime now) {
        final var floor = new HODateTime(now.getInstant().minus(SCOUT_RETRY_GRACE));
        final var stored = HODateTime.fromHT(UserParameter.instance().mcpScoutResolvedThrough);
        return stored == null || stored.compareTo(floor) < 0 ? floor : stored;
    }

    /**
     * Written through to the database rather than left for {@code HOMainFrame.shutdown()}: a
     * download can come from the MCP server with no window to close, and a watermark lost to a
     * crash means every sale in the window is asked about twice.
     */
    private static void setSaleWatermark(HODateTime through) {
        UserParameter.instance().mcpScoutResolvedThrough = through.toHT();
        DBManager.instance().saveUserParameter("mcpScoutResolvedThrough", through.toHT());
    }

    /**
     * Scout entries whose watched transfer ended since the last sale pass, oldest first.
     * <p>
     * Oldest first so the watermark can be a resume point: the loop stops at the first candidate it
     * cannot afford and parks the watermark there, and everything before it is done. The window is
     * inclusive of {@code since} so that candidate comes back next run rather than falling through
     * the gap - two entries sharing a deadline cost one repeat call, which is the cheap side of the
     * mistake to make.
     */
    static List<Candidate> salesToAskAbout(List<ScoutEintrag> entries, HODateTime since,
                                           HODateTime now) {
        final var candidates = expired(entries, since, settled(now));
        candidates.sort(Comparator.comparing(Candidate::deadline));
        return candidates;
    }

    /** The latest deadline whose sale Hattrick has had time to record - see {@link #SALE_SETTLE}. */
    private static HODateTime settled(HODateTime now) {
        return new HODateTime(now.getInstant().minus(SALE_SETTLE));
    }

    /**
     * Unsold scout entries still inside the re-listing watch, newest expiry first.
     * <p>
     * Bounded by {@link #SCOUT_WATCH_GRACE}, because a scout entry has nowhere to record "looked,
     * not listed" - without a cut-off an entry that never comes back would cost a CHPP call on
     * every download for as long as it stayed on the list. Newest first so that when the budget
     * runs out it is the stalest entries that go unasked, and so a target that has just been
     * withdrawn is watched most closely.
     */
    static List<Candidate> relistingsToWatch(List<ScoutEintrag> entries, HODateTime now) {
        final var candidates = expired(entries,
                new HODateTime(now.getInstant().minus(SCOUT_WATCH_GRACE)), now);
        candidates.sort(Comparator.comparing(Candidate::deadline).reversed());
        return candidates;
    }

    /** Unsold entries whose deadline has passed and is no older than {@code since}. */
    private static List<Candidate> expired(List<ScoutEintrag> entries, HODateTime since,
                                           HODateTime now) {
        final var candidates = new ArrayList<Candidate>();
        for (final var entry : entries) {
            if (entry.isSold() || entry.getDeadline() == null) {
                continue;
            }
            final var deadline = HODateTime.fromDbTimestamp(entry.getDeadline());
            if (deadline.compareTo(now) < 0 && deadline.compareTo(since) >= 0) {
                candidates.add(new Candidate(entry.getPlayerID(), deadline));
            }
        }
        return candidates;
    }

    /** A scouted player and the deadline of the transfer that was being watched. */
    record Candidate(int playerId, HODateTime deadline) {}


    /**
     * Counts the run through HO's status bar. Repainted only when the percentage moves, so a long
     * corpus list costs at most a hundred repaints.
     */
    private static final class Progress {
        private final int total;
        private int done;
        private int shown = -1;

        Progress(int total) {
            this.total = total;
        }

        void step() {
            final var percent = ++done * 100 / total;   // total > 0: step() only runs inside a loop
            if (percent == shown) {
                return;
            }
            shown = percent;
            HOMainFrame.instance().setInformation(Helper.getTranslation(
                    "ls.update_status.transfer_prices") + " " + done + " / " + total);
            HOMainFrame.instance().updateProgress(percent);
        }
    }

    /** Whether this run has used up {@link #TIME_BUDGET}. */
    private static boolean outOfTime(long deadlineNanos) {
        return System.nanoTime() - deadlineNanos >= 0;
    }

    /**
     * Look at one player on the market and, if he is actually listed, snapshot him into the corpus.
     * Either way, backfill his character onto every row the corpus already holds for him.
     * <p>
     * The skills-fill pass and the re-listing watch are the same operation - "call playerdetails,
     * and if he is listed store a full row" - so they share one call rather than spending two. The
     * character backfill piggybacks on that same call for the same reason: {@code playerdetails}
     * answers it whether or not he is listed (unlike the seven hidden skills, character survives
     * Hattrick's unlisted pruning), so every call this method ever makes, from any of its three call
     * sites, is a free chance to close the gap {@code backfill_character}'s own pass would otherwise
     * have to spend a second, separate {@code playerdetails} call on for the very same player.
     * <p>
     * Deliberately unguarded. The corpus pass's two short-circuits live at its own call site (see
     * {@link #observeForCorpus}) and must not move in here: a scout entry normally <em>does</em>
     * already have skill data, from the search hit it was created from, and it is exactly the entry
     * worth re-checking.
     *
     * @return what Hattrick said, or null if the call failed
     */
    private static XMLPlayersParser.MarketPlayer observe(int playerId, HODateTime now) {
        try {
            final var m = OnlineWorker.downloadMarketPlayer(String.valueOf(playerId));
            if (m == null || m.player().getPlayerId() == 0) {
                return null;
            }
            if (m.transferListed()) {
                DBManager.instance().storeTransferListing(
                        TransferListing.fromPlayerDetails(m, now));
            }
            DBManager.instance().storeTransferListingCharacter(playerId,
                    m.player().getGentleness(), m.player().getAggressivity(), m.player().getHonesty());
            return m;
        } catch (Exception e) {
            HOLogger.instance().error(TransferListingResolver.class, e);
            return null;
        }
    }

    /**
     * The corpus pass's use of {@link #observe}, with the two guards that belong to it and not to
     * the watch.
     * <p>
     * A row already backed by a skill-bearing snapshot needs nothing, and a row whose deadline is
     * more than {@link #SKILL_FILL_GRACE} old describes a player who has since been at his new club
     * long enough to train - filing today's skills against that listing would let him vote in a
     * valuation under skills he did not have when he sold.
     * <p>
     * Note what this pass can and cannot do, which the old code obscured: it stores something only
     * when the player happens to be on the market again right now. The rows it was written for are
     * unlisted players by definition, and Hattrick prunes an unlisted player's skills to stamina.
     * That slice of the corpus is unfillable by any means.
     */
    private static void observeForCorpus(TransferListing listing, HODateTime now) {
        if (DBManager.instance().transferListingHasSkillData(listing.getPlayerId())) {
            return;
        }
        if (listing.getDeadline() != null && listing.getDeadline().getInstant()
                .plus(SKILL_FILL_GRACE).isBefore(now.getInstant())) {
            return;                            // long sold; today's skills are not the sale's
        }
        observe(listing.getPlayerId(), now);
    }


    /**
     * Whether one player's lookup produced a price, and whether it answered at all.
     * <p>
     * {@code failed} is not {@code !resolved}: "he did not sell" is an answer and consumes the
     * question, while "the call threw" leaves it open and must not let the sale watermark move past
     * him.
     */
    private record Outcome(boolean resolved, boolean failed) {}

    /**
     * Fill in what this player's expired listing sold for.
     * <p>
     * Local first, always: HO's own TRANSFER table may already hold the very transfer being asked
     * about - certainly so if we won the auction, since a team download stores our own transfers,
     * and also whenever an earlier pass fetched this player's history. Spending a CHPP call on a
     * sale already sitting in the database is exactly the waste worth avoiding, and it would hit
     * hardest on the players we actually bought.
     */
    private static Outcome resolvePlayer(int playerId, HODateTime deadline, HODateTime now) {
        final var known = saleAt(DBManager.instance().getTransfers(playerId, true), deadline);
        if (known != null) {
            applySale(playerId, known);
            DBManager.instance().markTransferListingResolveAttempted(playerId, now);
            return new Outcome(true, false);
        }

        final List<module.transfer.PlayerTransfer> fetched;
        try {
            fetched = XMLParser.updatePlayerTransfers(playerId);
        } catch (Exception e) {
            // The lookup failed rather than answered - leave the rows alone so a network blip does
            // not burn their one attempt.
            HOLogger.instance().error(TransferListingResolver.class, e);
            return new Outcome(false, true);
        }
        final var sale = saleAt(fetched, deadline);
        if (sale != null) {
            applySale(playerId, sale);
        }
        // The call answered. Whatever it did not account for - a listing that was withdrawn, or
        // expired with no bidder - is marked looked-at so it is never asked about again.
        DBManager.instance().markTransferListingResolveAttempted(playerId, now);
        return new Outcome(sale != null, false);
    }

    /**
     * The transfer that completed at this deadline, or null if none of them did.
     * <p>
     * Searched rather than indexed. The previous version took {@code transfers.get(size - 1)} as
     * "the most recent", but {@link XMLParser#updatePlayerTransfers} treats that same element as
     * the player's *first* ever transfer when deciding whether he is homegrown - so the list is
     * newest-first and that index was the oldest transfer, not the newest. Matching on the date
     * instead does not care about the order at all.
     */
    static module.transfer.PlayerTransfer saleAt(
            List<module.transfer.PlayerTransfer> transfers, HODateTime deadline) {
        if (deadline == null) {
            return null;
        }
        final var from = deadline.getInstant().minus(SALE_WINDOW);
        final var to = deadline.getInstant().plus(SALE_WINDOW);
        for (final var transfer : transfers) {
            if (transfer.getDate() == null || transfer.getPrice() == null
                    || transfer.getPrice().getSwedishKrona() == null) {
                continue;
            }
            final var date = transfer.getDate().getInstant();
            if (!date.isBefore(from) && !date.isAfter(to)) {
                return transfer;
            }
        }
        return null;
    }

    private static void applySale(int playerId, module.transfer.PlayerTransfer sale) {
        final var salePriceSek = sale.getPrice().getSwedishKrona().longValue();
        DBManager.instance().storeTransferListingSalePrice(playerId, sale.getDate(),
                salePriceSek, SALE_WINDOW);
        recordOnScoutList(playerId, salePriceSek, sale.getDate());
    }

    /**
     * If the sold player was on the scout list, record what he went for. That list is a watch list
     * of targets, and without this it keeps showing the asking price of a transfer that is long
     * over. {@link ScoutList} ignores a sale that does not line up with the entry's deadline.
     * <p>
     * Best effort: a failure here must not undo a sale price that is already stored.
     */
    private static void recordOnScoutList(int playerId, long salePriceSek, HODateTime soldAt) {
        try {
            ScoutList.recordSale(playerId, salePriceSek, soldAt, SALE_WINDOW);
        } catch (Exception e) {
            HOLogger.instance().error(TransferListingResolver.class, e);
        }
    }

    /**
     * If the re-listed player is on the scout list, move his entry onto the new transfer.
     * <p>
     * Best effort, for the same reason {@link #recordOnScoutList} is: a failure here must not undo
     * the TRANSFERLISTING row that {@link #observe} has already stored.
     */
    private static void recordRelistingOnScoutList(int playerId,
                                                   XMLPlayersParser.MarketPlayer m) {
        try {
            ScoutList.recordRelisting(playerId, m.askingPrice(), m.highestBid(), m.deadline());
        } catch (Exception e) {
            HOLogger.instance().error(TransferListingResolver.class, e);
        }
    }

    /** Best effort, for the same reason the two above are: character is a bonus on the call. */
    private static void recordCharacterOnScoutList(int playerId,
                                                   XMLPlayersParser.MarketPlayer m) {
        try {
            ScoutList.recordCharacter(playerId, m.player());
        } catch (Exception e) {
            HOLogger.instance().error(TransferListingResolver.class, e);
        }
    }
}
