package core.net;

import core.db.DBManager;
import core.db.TransferListing;
import core.file.xml.XMLPlayersParser;
import core.util.HODateTime;
import core.util.HOLogger;
import module.transfer.XMLParser;
import module.transfer.scout.ScoutList;

import java.util.ArrayList;
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
     * How long after a deadline a {@code playerdetails} fetch still describes the player that was
     * on the market. Past that he has been at his new club long enough to have trained, so filling
     * his skills in would file today's player against a months-old listing and let him vote in a
     * valuation under skills he did not have when he sold. No skills at all is the better answer:
     * a row with NULL skills cannot match {@code loadComparables}, so it stays out rather than
     * lying.
     */
    private static final java.time.Duration SKILL_FILL_GRACE = java.time.Duration.ofDays(14);

    /** How long after its deadline a scout entry is still worth asking about. */
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
     * What the cap is actually protecting, and it is not a CHPP quota.
     * <p>
     * {@code transfersPlayer} is an ordinary read file: HO's own Transfers module already loops it
     * over every outdated player with no limit at all ({@code XMLParser.updateTeamTransfers},
     * {@code TransfersPanel}), and there is no rate limiting anywhere in {@code core.net}. The
     * limit that matters is that a download runs on the event dispatch thread, so every lookup is
     * time the UI is frozen. A count alone cannot express that - the same 200 calls are half a
     * minute on a fast link and five minutes on a bad one - so the run also stops on the clock.
     */
    private static final java.time.Duration TIME_BUDGET = java.time.Duration.ofSeconds(60);

    private TransferListingResolver() {
    }

    /**
     * @param cap the most CHPP calls this run may make; 0 or less does nothing
     * @return how many listings got a sale price
     */
    public static int resolve(int cap) {
        if (cap <= 0) {
            return 0;
        }
        final var now = HODateTime.now();
        final var deadline = System.nanoTime() + TIME_BUDGET.toNanos();
        var resolved = 0;
        var calls = 0;
        final var seen = new HashSet<Integer>();

        // Half the cap, not all of it. SCOUT_WATCH_GRACE is six and a half times SCOUT_RETRY_GRACE,
        // and a fresh ScoutEintrag defaults its deadline to the moment it was created
        // (ScoutEintrag.java:22), so a hand-typed entry is a candidate from day one and stays one
        // for 90 days. Unbounded, a long scout list would eat the whole run and the corpus pass
        // below would never start.
        //
        // The per-candidate cost also doubled with this pass - one observe, plus one resolvePlayer
        // while the entry is still inside SCOUT_RETRY_GRACE - so against the halved cap the scout
        // pass gets through roughly a quarter as many entries per run as it used to. Ample at the
        // default mcpResolveCap of 200; worth knowing if a user turns that right down.
        final var scoutCap = Math.max(1, cap / 2);
        final var looked = new HashSet<Integer>();   // playerdetails fetched for him this run
        for (final var candidate : scoutCandidates(now)) {
            if (spent(calls, scoutCap, deadline)) {
                break;
            }
            if (!looked.add(candidate.playerId())) {
                continue;
            }
            final var observation = observe(candidate.playerId(), now);
            calls++;
            // Free: the call has already been made, and character comes back whether or not he is
            // listed. Fills in entries added before add_to_scout started fetching it.
            if (observation != null) {
                recordCharacterOnScoutList(candidate.playerId(), observation);
            }
            final var action = scoutAction(observation != null && observation.transferListed(),
                    candidate.deadline(), now);
            // The sale question first, and before the refresh: step 3 moves the deadline forward,
            // after which isTheWatchedTransfer can never line this sale up with this entry again.
            //
            // ponytail: no SELLERTEAMID column on TRANSFERLISTING, so this still costs a
            // transfersPlayer call - add the column and compare observation.owningTeamId() against
            // it if the scout list ever grows enough for one call per watched entry to matter.
            if (action.askAboutSale()) {
                // Only here does he become "seen", because seen means "his sale has been asked
                // about this run" and nothing else. Marking him on the look alone would be a
                // regression: SCOUT_WATCH_GRACE is far wider than the window scoutAction asks in,
                // so most candidates fall past this branch, and seen would then make the corpus
                // loop below skip their TRANSFERLISTING rows for up to 90 days - rows the old,
                // narrower candidate window left it free to resolve.
                seen.add(candidate.playerId());
                final var outcome = resolvePlayer(candidate.playerId(), candidate.deadline(), now);
                calls += outcome.calls();
                if (outcome.resolved()) {
                    resolved++;
                }
            }
            if (action.refreshEntry()) {
                recordRelistingOnScoutList(candidate.playerId(), observation);
            }
        }

        // Then the valuation corpus, with whatever is left of the budget. Rows, not players: one
        // player repeatedly observed holds several. Over-fetch so a handful of duplicates cannot
        // starve the run, and let the call count be what the cap bounds.
        for (final var listing : DBManager.instance().loadUnresolvedTransferListings(cap * 5)) {
            if (spent(calls, cap, deadline)) {
                break;
            }
            if (!seen.add(listing.getPlayerId())) {
                continue;                      // his sale was already asked about this run
            }
            if (looked.add(listing.getPlayerId())) {
                calls += observeForCorpus(listing, now);
            }
            if (spent(calls, cap, deadline)) {
                break;
            }
            if (listing.getDeadline() == null || listing.getDeadline().compareTo(now) >= 0) {
                continue;                      // not expired yet, nothing to resolve
            }
            final var outcome = resolvePlayer(listing.getPlayerId(), listing.getDeadline(), now);
            calls += outcome.calls();
            if (outcome.resolved()) {
                resolved++;
            }
        }
        return resolved;
    }

    /**
     * Scouted players whose watched transfer has ended: either his sale price is still unknown, or
     * he is worth re-checking in case the seller has put him back on the market.
     * <p>
     * Bounded, because a scout entry has nowhere to record "looked, no sale" the way a listing row
     * does - without a cut-off an entry that never sold would cost a CHPP call on every download
     * for as long as it stayed on the list. {@link #SCOUT_WATCH_GRACE} is the wider of the two
     * windows and so is the one that bounds the candidate set; {@link #scoutAction} decides which
     * of the two questions each candidate is still inside.
     */
    private static List<Candidate> scoutCandidates(HODateTime now) {
        final var giveUpBefore = new HODateTime(now.getInstant().minus(SCOUT_WATCH_GRACE));
        final var candidates = new ArrayList<Candidate>();
        for (final var entry : ScoutList.entries()) {
            if (entry.isSold() || entry.getDeadline() == null) {
                continue;
            }
            final var deadline = HODateTime.fromDbTimestamp(entry.getDeadline());
            if (deadline.compareTo(now) < 0 && deadline.compareTo(giveUpBefore) > 0) {
                candidates.add(new Candidate(entry.getPlayerID(), deadline));
            }
        }
        return candidates;
    }

    /** A scouted player and the deadline of the transfer that was being watched. */
    private record Candidate(int playerId, HODateTime deadline) {}

    /** What the scout pass does about one candidate, once the observation has come back. */
    record ScoutAction(boolean askAboutSale, boolean refreshEntry) {}

    /**
     * The two independent questions a scout candidate raises, decided together so the branch table
     * is one testable thing rather than a shape spread across the loop.
     * <p>
     * They are independent because "he is listed again" does not answer "did he sell": a buyer
     * re-listing a player he has just bought is ordinary Hattrick behaviour, and once the refresh
     * has moved the deadline forward the sale can never be matched to this entry again.
     *
     * @param oldDeadline the deadline the entry was watching, before any refresh
     */
    static ScoutAction scoutAction(boolean transferListed, HODateTime oldDeadline, HODateTime now) {
        final var stillWorthAsking = oldDeadline != null
                && oldDeadline.getInstant().plus(SCOUT_RETRY_GRACE).isAfter(now.getInstant());
        return new ScoutAction(stillWorthAsking, transferListed);
    }


    /** Whether this run is out of budget - either bound stops it, whichever comes first. */
    private static boolean spent(int calls, int cap, long deadlineNanos) {
        return calls >= cap || System.nanoTime() - deadlineNanos >= 0;
    }

    /**
     * Look at one player on the market and, if he is actually listed, snapshot him into the corpus.
     * <p>
     * The skills-fill pass and the re-listing watch are the same operation - "call playerdetails,
     * and if he is listed store a full row" - so they share one call rather than spending two.
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
     *
     * @return how many CHPP calls this made
     */
    private static int observeForCorpus(TransferListing listing, HODateTime now) {
        if (DBManager.instance().transferListingHasSkillData(listing.getPlayerId())) {
            return 0;
        }
        if (listing.getDeadline() != null && listing.getDeadline().getInstant()
                .plus(SKILL_FILL_GRACE).isBefore(now.getInstant())) {
            return 0;                          // long sold; today's skills are not the sale's
        }
        observe(listing.getPlayerId(), now);
        return 1;
    }


    /** What one player cost us, and whether it produced a price. */
    private record Outcome(int calls, boolean resolved) {}

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
            return new Outcome(0, true);
        }

        final List<module.transfer.PlayerTransfer> fetched;
        try {
            fetched = XMLParser.updatePlayerTransfers(playerId);
        } catch (Exception e) {
            // The lookup failed rather than answered - leave the rows alone so a network blip does
            // not burn their one attempt.
            HOLogger.instance().error(TransferListingResolver.class, e);
            return new Outcome(1, false);
        }
        final var sale = saleAt(fetched, deadline);
        if (sale != null) {
            applySale(playerId, sale);
        }
        // The call answered. Whatever it did not account for - a listing that was withdrawn, or
        // expired with no bidder - is marked looked-at so it is never asked about again.
        DBManager.instance().markTransferListingResolveAttempted(playerId, now);
        return new Outcome(1, sale != null);
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
