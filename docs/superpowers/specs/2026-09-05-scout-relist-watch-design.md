# Watching a scout entry for a re-listing — design

A player on the scout list whose transfer ended unsold is a target we still want. Today nothing
notices when his seller puts him back on the market: the scout entry's price and deadline are frozen
at the moment he was added, and only a fresh `search_transfers` that happens to match him updates
anything. This spec makes HO notice.

It also fixes what the investigation turned up on the way. Those fixes are not optional decoration —
without them the watch would write false data into the valuation corpus, so they come first.

Evidence for every claim in §1 is two live `playerdetails` responses fetched 2026-09-05 17:57:23,
one for a listed foreign player (476487492, Gaspar) and one for an unlisted foreign player
(513044843, Vignaud). Both are quoted where they matter.

## Goals

- A scout entry whose transfer ended unsold is re-checked on each team download, and when the player
  is listed again HO records the new asking price, the new deadline and his current skills.
- `list_scout` stops presenting an expired deadline as if it were live.
- A `playerdetails` response can never contribute a skill vector that Hattrick did not actually
  publish.
- No new CHPP request type, no background poller, no database migration.

## Non-goals

- Notifying the user beyond the alarm that already exists. `ScoutThread` fires five minutes before
  an entry's deadline and polls the live `ScoutEintrag` objects every 30s, so a refreshed deadline
  is seen without a new notification path. It is not quite free, though: `m_bWecker` is a
  *has-already-rung* latch, not a user preference — `ScoutThread.java:31` fires only when
  `!se.isWecker()`, sets it true at `:36`, and nothing in `src/main/java` ever sets it back. §6 has
  to clear it. Building a second notification path is still out of scope.
- Watching anything that is not on the scout list. The scout list is the user's own statement of
  intent and is the natural bound on how many players get a call spent on them.
- Detecting a sale without a CHPP call (§7.1 explains what that would cost and why it is deferred).
- Fixing the `<TrainerData />` trap (§1.2 applies to it too — `parsePlayerDetailsFromString` reads
  children out of a present-but-empty `TrainerData` element the same way). Pre-existing, unrelated to
  the market path, reported here and left alone.

## 1. Constraints discovered

### 1.1 `playerdetails` is the only file that answers the question

`transfersearch` has no `playerId` parameter at all (`docs/chpp_files.md:68-114`) — it cannot be
asked about one named player, only re-run over a profile in the hope he matches, and its own CHPP
description says it is not meant to be polled by bots. `currentbids`, which `get_open_bids` reads, is
Hattrick's own tracking list but is read-and-delete only: its actionTypes are `view` and
`ignoreTransfer`. There is no add-to-hotlist action, so a player cannot be pushed onto Hattrick's
watchlist to let Hattrick do the watching.

That leaves `playerdetails`, which HO already requests at version 3.0
(`Connector.java:502-504`). Both fetches confirm it answers for a foreign player on a non-supporter
account (`UserSupporterTier: none`), and that it carries `OwningTeam/TeamID`.

### 1.2 `<TransferDetails />` is present-but-empty when the player is not listed

This is the constraint that would have broken the obvious implementation. For the unlisted player the
response contains:

```xml
<TransferListed>False</TransferListed>
<TransferDetails />
```

`root.getElementsByTagName("TransferDetails").item(0)` returns a **non-null** Element there. A
presence check reports every unlisted player as listed. The detection signal must be
`<TransferListed>`, which is a clean `True`/`False` and which
`XMLPlayersParser.parsePlayerDetailsFromString` already parses (`XMLPlayersParser.java:484`).

For the listed player the same block is populated, and this is the exact shape to parse:

```xml
<TransferListed>True</TransferListed>
<TransferDetails>
  <AskingPrice>2850000</AskingPrice>
  <Deadline>2026-09-05 20:08:31</Deadline>
  <HighestBid>0</HighestBid>
  <BidderTeam />
</TransferDetails>
```

Note `<BidderTeam />` is itself empty when there are no bids — same trap one level down.

The same block is read a third time, by `XMLPlayersParser.createPlayerDetails` (`:93-97`), which
pushes `AskingPrice` / `Deadline` / `HighestBid` into the HRF hash by container. That one is safe
only by accident — `xmlValue` returns `""` for a missing child, so it writes empty strings rather
than throwing — and it is named here so nobody fixes §3.2's version and walks away believing the
trap is gone.

### 1.3 Skills are pruned, not withheld — and `xmlIntValue` turns that into zeros

The shared assumption going in was "a stranger's skills are only visible while he is listed". That is
half right, and the half that is wrong is dangerous. `<PlayerSkills>` is present in **both**
responses. The unlisted player's contains exactly one child:

```xml
<PlayerSkills>
  <StaminaSkill>7</StaminaSkill>
</PlayerSkills>
```

The listed player's contains all eight. So stamina is public always; the other seven are published
only while the player is on the market.

`XMLManager.xmlIntValue(ele, key)` delegates to the three-argument overload with a default of **0**
(`XMLManager.java:86-88`), and `parsePlayerDetailsFromString` guards only the container, not its
children (`XMLPlayersParser.java:502-511`). So parsing an unlisted player yields keeper, playmaking,
scoring, passing, winger, defending and set pieces all equal to 0, with only stamina real — and
nothing in the resulting `Player` distinguishes that from a genuinely terrible footballer.

A zero-skill row is worse than an absent one. An absent row cannot match `loadComparables`; a
zero-skill row matches and drags every valuation built on it downward. This is why §2 exists.

`Player`'s own skill fields default to **1**, not 0 (`Player.java:229-236`), which is what makes
"leave them unparsed" distinguishable from "parsed as 0". Distinguishable, not meaningful: 1 reads
as *disastrous*, not as *unknown*. §2 is what keeps it out of the corpus.

### 1.4 `parsePlayerDetailsFromString` returns null for a foreign player

```java
if (!UserManager.instance().getCurrentUser().isNtTeam() &&
     teamID != HOVerwaltung.instance().getModel().getBasics().getTeamId()) return null; // foreign player
```

(`XMLPlayersParser.java:411-412`.) `OnlineWorker.downloadPlayerDetails` runs through it, so it
returns null for every market player.

`TransferListingResolver.fillMissingSkills` (`TransferListingResolver.java:176`) is aimed exclusively
at market players. It therefore spends one CHPP call per corpus row and stores nothing, silently, on
every run. `docs/current.md:1202` records the pass as working (§11).

The guard has exactly one other consumer worth checking, and it is safe:
`Player.downloadMotherClubInfoIfMissing` (`Player.java:487-495`) calls `downloadPlayerDetails` only
when `HOVerwaltung.instance().getModel().getCurrentPlayer(playerId) != null` — an own player by
construction. Nothing else depends on the null.

### 1.5 A scout entry has nowhere to record "looked, no sale"

`markTransferListingResolveAttempted` marks TRANSFERLISTING rows. A scout entry added by hand or from
HO's own transfer search tab has no such row, so `scoutCandidates` re-selects it on every download
until `SCOUT_RETRY_GRACE` (14 days) expires. The resolver's own comment on `scoutCandidates` says so.

The practical consequence: an unsold scout entry is **already** costing one `transfersPlayer` call
per download today. That is the baseline the watch is priced against (§7).

### 1.6 CHPP `Salary` carries the abroad bonus; the scout list stores the base wage

Live values against the stored scout snapshot:

| | `playerdetails` Salary (SEK) | ÷ 10 (EUR) | scout `baseWage` (EUR) | ratio |
|---|---|---|---|---|
| Gaspar 476487492 | 57840 | 5784 | 4820 | 1.2 |
| Vignaud 513044843 | 4320 | 432 | 360 | 1.2 |

The currency rate is 10, fixed independently by the same response's `AskingPrice` of 2850000 SEK
against the stored 285,000 EUR. So the residual factor of exactly 1.2 on two players with different
wages is not a rate difference — it is the 20% surcharge for a player abroad, and both carry
`<IsAbroad>True</IsAbroad>`.

`TransferListing.salary` is filled from `Hit.details().salary()` on the search path and from
`p.getWage()` on the player-details path. Those are different quantities. Mixing them in one column
makes wage an unusable comparable. `parsePlayerDetailsFromString` currently has `IsAbroad` commented
out (`XMLPlayersParser.java:469-470`), so a `Player` built from it cannot make the correction at all.

The 1.2 is the **only** correction needed — there is no hidden ×10 to undo as well. Both paths
already store SEK: `fromHit` takes `xmlLongValue(details, "Salary")` straight off the search XML
(`XMLTransferSearchParser.java:83`), `fromPlayerDetails` takes `wage.getSwedishKrona()`, and the
scout `baseWage` quoted in the table above is that same search value, unmodified
(`TransferSearchConverter.java:39`). The ÷10 column is display currency only.

This is worth more than the corpus. `TransferSearchConverter.java:78` builds a scout entry's
`baseWage` out of `listing.getSalary()`, so the inflated number is already reaching a column the user
reads.

### 1.7 Everything else needed already exists

- `ScoutEintrag.setDeadline` / `setPrice`, backed by the `Deadline` (TIMESTAMP) and `Price` (DECIMAL)
  columns already in `ScoutTable.initColumns()` — **no migration** to refresh an entry in place.
- `ScoutList.entries()` / `save()` handle the open-panel-vs-database split.
- `TransferTools.listScout` already joins `loadLatestTransferListing` onto each entry and publishes
  `askingPrice`, `highestBid` and `observedAt`, so a fresh TRANSFERLISTING row surfaces with no tool
  change.
- `UserParameter.mcpResolveCap` (default 200, `UserParameter.java:139`) and the resolver's 60s
  `TIME_BUDGET` already bound the pass.
- `xmlValue` returns `""` for a missing child, and `xmlLongValue` / `xmlHODateTimeValue` /
  `xmlBoolValue` turn that into `null` / `null` / `false` without throwing (`XMLManager.java:62-120`,
  `HODateTime.java:75`). The guarded reads in §3.2 need no defensive code of their own beyond the
  `transferListed` gate.

## 2. The invariant

> **A TRANSFERLISTING row is only ever written from a response in which `<TransferListed>` is true.**

Everything in §3 and §4 follows from it. When the flag is true, Hattrick publishes the full skill
block *and* the market block, so a row built from such a response is complete and comparable to one
built from a `transfersearch` hit. When the flag is false, the response has nothing the corpus wants,
and the correct number of rows to write is zero.

This is also what makes lifting the §1.4 guard safe. Lifting it without the invariant is strictly
worse than the current no-op.

## 3. Parser changes

`XMLPlayersParser` gains one parse path and keeps the existing one intact.

### 3.1 New record

```java
/**
 * A playerdetails response read as market data. {@code transferListed} decides what the rest of it
 * is worth: Hattrick publishes the full skill block and the TransferDetails block only while the
 * player is on the market, and prunes PlayerSkills down to stamina otherwise (see the design spec).
 * When it is false the market fields are 0 / null, and the seven hidden skills on {@code player}
 * are a meaningless 1 - Player's own field default, left unparsed rather than read as 0. Only
 * stamina is real. Build nothing out of this record's skills unless the flag is true.
 */
public record MarketPlayer(Player player, boolean transferListed, int owningTeamId,
                           boolean isAbroad, long askingPrice, HODateTime deadline,
                           long highestBid) {}
```

### 3.2 `parseMarketPlayerFromString(String xml)`

The body of today's `parsePlayerDetailsFromString` with four changes:

1. No own-team guard. `owningTeamId` is read into the record instead of being compared and discarded.
2. `IsAbroad` is parsed (`xmlBoolValue(root, "IsAbroad")`), not commented out.
3. The `PlayerSkills` block is read **only when `transferListed` is true**. When it is false the
   seven hidden skills are left at the `Player` default of 1 rather than parsed as 0 — stamina may
   still be taken, since §1.3 shows it is always published. 1 is not "unknown" either; §2 is what
   keeps it out of the corpus.
4. `TransferDetails` is read by *child*, never by container presence: `xmlLongValue(details,
   "AskingPrice")`, `xmlHODateTimeValue(details, "Deadline")`, `xmlLongValue(details, "HighestBid")`,
   all guarded by `transferListed` rather than by `details != null` (§1.2).

### 3.3 `parsePlayerDetailsFromString` is unchanged behaviourally

It becomes a thin wrapper: call `parseMarketPlayerFromString`, then apply the existing own-team
check and return `null` or `MarketPlayer.player()`. Its callers — `OnlineWorker.downloadPlayerDetails`
and through it `Player.downloadMotherClubInfoIfMissing` — see exactly what they see today. No caller
of the existing method changes.

`OnlineWorker` gains a sibling, `downloadMarketPlayer(String playerId)`, returning `MarketPlayer`.

## 4. `TransferListing.fromPlayerDetails` becomes a full snapshot

Its javadoc currently describes a "skills-only snapshot" because the market data "is not part of a
player-details response". §1.2 shows that is wrong: for a listed player the market data is right
there.

New signature `fromPlayerDetails(MarketPlayer m, HODateTime observedAt)`, and it fills the market
fields as well as the skills. It must only be called when `m.transferListed()` is true (§2); an
assertion or an early return documents that rather than trusting the caller.

Two corrections to what it stores:

- `askingPrice` / `deadline` / `highestBid` from the `MarketPlayer`, so a row from this path is
  indistinguishable from one built by `fromHit`.
- `salary` as the **base** wage: `m.isAbroad() ? round(wage / 1.2) : wage` (§1.6). `fromHit` already
  stores the base wage; without this the column mixes two quantities.

## 5. `TransferListingResolver`: one observation pass

`fillMissingSkills` and the proposed watch are the same operation — "call `playerdetails`, and if he
is listed store a full row" — so they collapse into one method rather than two that each spend a
call.

```java
/** @return the observation, or null if the call failed */
private static MarketPlayer observe(int playerId, HODateTime now)
```

One `playerdetails` call. When `transferListed` is true it stores
`TransferListing.fromPlayerDetails(...)`. When false it stores nothing (§2). Either way it returns
the record so the caller can branch.

`observe` itself is unguarded, deliberately. `fillMissingSkills`' two early returns — the
`transferListingHasSkillData` short-circuit and the `SKILL_FILL_GRACE` cut-off — **stay at the corpus
call site (§5.2) and must not move inside it**. Inside, they would silence the watch: a scout entry
normally *does* have skill data, from the hit it was created from, and it is exactly the entry worth
re-checking. Dropped altogether, they would turn the corpus pass into one `playerdetails` call per
unresolved row.

### 5.1 The scout pass

`scoutCandidates` keeps its shape but is bounded by a new constant:

```java
/**
 * How long after its deadline an unsold scout entry is still watched for a re-listing.
 *
 * Longer than SCOUT_RETRY_GRACE on purpose: that one bounds "what did he sell for", a question a
 * completed transfer answers immediately, so a fortnight is generous. This bounds "is he back",
 * which a seller may not answer for a month — a young prospect withdrawn from the market is exactly
 * the case where the wait is long and the payoff is highest.
 */
private static final Duration SCOUT_WATCH_GRACE = Duration.ofDays(90);
```

Widening the window widens the candidate set, and `resolve` gives the scout loop first claim on a
shared budget — `cap` (default 200) and the 60s `TIME_BUDGET`, which exists because the run blocks
the download. So **the scout loop needs a sub-bound of its own**: a fraction of `cap`, or move it
behind the corpus loop. Otherwise a long scout list consumes the whole run and the corpus pass never
starts. The pressure is not hypothetical: `ScoutEintrag` defaults its deadline to the moment the
entry was created (`ScoutEintrag.java:22`), so — as `isTheWatchedTransfer`'s own javadoc puts it — an
entry typed in by hand "looks expired straight away", and is therefore a candidate from day one, now
for 90 days instead of 14.

Per candidate:

1. `observe(...)`.
2. The sale question, whenever the entry's *old* deadline is still inside `SCOUT_RETRY_GRACE`:
   today's `resolvePlayer`, which checks HO's own TRANSFER table for free first and only then spends
   a `transfersPlayer` call. **This runs whether or not he is listed, and before the refresh.**
   "He is listed again, so he did not sell" is false — a buyer re-listing a player he has just
   bought is ordinary Hattrick behaviour — and this is the only moment the sale can still be
   recorded: step 3 moves the deadline forward, the entry leaves the candidate set, and
   `isTheWatchedTransfer` will never line up with that sale again. `salePrice` would stay null for
   good. After 14 days the sale question stops and the watch continues alone.
3. **Listed** → refresh the entry (§6).

A re-list resets both windows naturally: the entry's deadline moves into the future, so it leaves the
candidate set until that new transfer ends, and when it does it is a new transfer with a new sale
question.

### 5.2 The corpus pass

Unchanged except that `fillMissingSkills` is replaced by `observe`. Rows whose player is no longer
listed now correctly store nothing instead of storing zeros — which is what they already effectively
do today via the §1.4 null, except that the intent is now expressed rather than accidental.

## 6. What a re-list does to the scout entry

In place, on `ScoutList.entries()`, saved through `ScoutList.save(...)`:

| Field | New value | Why |
|---|---|---|
| `Deadline` | the new deadline | The stale expired one is the thing `list_scout` currently presents as live. |
| `Price` | `max(askingPrice, highestBid)` | Exactly what `TransferSearchConverter.java:28` stores for a fresh hit, so the column keeps meaning "what it would cost to take him now". |
| `Wecker` | `false` | It is a has-already-rung latch, not a preference (Non-goals). Left true, the alarm never fires again for the new deadline. |
| `SalePrice` / `SoldAt` | untouched (null) | He has not sold. `recordSale` owns these. |
| everything else | untouched | Skills and TSI ride on the new TRANSFERLISTING row, which `listScout` joins in. |

`Price` is the one field here that a user may have typed themselves: an entry added by hand in
`TransferEingabePanel` carries whatever price they entered, and thanks to the default deadline (§5.1)
it is a candidate immediately. Overwriting it with the live market number is the intended
behaviour — the column means "what it would cost to take him now" everywhere else — but it is a
change to user-entered data, recorded here rather than discovered later.

`ScoutList.save` touches Swing (`panel.getTransferTable().refresh()`) on whatever thread the resolver
runs on. `recordSale` already does this, so the hazard is not new — but a sale happens once, while
this path runs on every download for as long as an entry is watched, so it exercises it far harder.

`list_scout` then reports a deadline in the future, and an `askingPrice` / `highestBid` /
`observedAt` triple from the new observation. Its tool description already warns that `observedAt`
may be later than the deadline beside it "if he was re-listed" — after this change that sentence
describes something that actually happens, and the deadline is no longer stale.

## 7. Cost

Per watched scout entry, per download:

| Situation | Today | After |
|---|---|---|
| unsold entry, still off market | 1 `transfersPlayer` (for 14 days, §1.5) | 1 `playerdetails` (for 90 days) + 1 `transfersPlayer` (for 14 days) |
| unsold entry, re-listed, within 14 days | 1 `transfersPlayer`, learns nothing | 1 `playerdetails` (price + deadline + skills) + 1 `transfersPlayer` (§5.1 step 2, often answered free from the local TRANSFER table) |
| unsold entry, re-listed, after 14 days | nothing, the entry has aged out | 1 `playerdetails`, learns price + deadline + skills |
| corpus row, player unlisted | 1 `playerdetails`, stores nothing (§1.4) | 1 `playerdetails`, stores nothing — intentionally |
| corpus row, player listed | 1 `playerdetails`, stores nothing (§1.4) | 1 `playerdetails`, stores a full row |

So the watch is honestly **+1 call per watched entry per download** for the first fortnight and +1
thereafter, not the free ride the first sketch suggested. What bounds it is that the scout list is
short and the user chose its contents, plus the existing `mcpResolveCap` and 60s `TIME_BUDGET`, which
this spec does not change — and the scout-loop sub-bound of §5.1, which it does add, because those
two existing bounds are shared with the corpus pass and the scout loop claims them first.

### 7.1 The migration that would remove the extra call, deliberately not taken

`XMLTransferSearchParser.Hit` already parses `sellerTeamId` and TRANSFERLISTING discards it. With a
`SELLERTEAMID` column, `observe`'s `owningTeamId` would answer "did he sell?" by comparison instead
of by a `transfersPlayer` call — removing that call entirely for the 14-day window in row 1 above.

Not done here: it costs a `DBVersion` bump and a migration step to save one call on a short list, and
the comparison is only as good as the stored seller id, which is absent for every row already in the
corpus. Worth revisiting if the scout list ever gets long.

`ponytail: no seller-team column, so a sale still costs a transfersPlayer call — add SELLERTEAMID and
compare owningTeamId if the scout list grows.`

## 8. Settings

None. `mcpResolveCap` and `TIME_BUDGET` still bound the run as a whole; `SCOUT_WATCH_GRACE` and the
scout loop's share of that budget (§5.1) are constants until somebody asks for a knob.

## 9. Testing

The parser is the part worth testing and the part that is cheap to test — it is pure, it is where
both live-API surprises live, and the two fetched responses are the fixtures.

`XMLPlayersParserTest` (new), inline XML in the house style of `XMLTransferSearchParserTest`:

1. A listed foreign player parses with all eight skills, `transferListed` true, and asking price /
   deadline / highest bid off `TransferDetails`.
2. An unlisted foreign player parses with `transferListed` false, stamina 7, and the other seven
   skills **not** set to 0 — the §1.3 regression, and the single most valuable case here.
3. `<TransferDetails />` empty on an unlisted player yields no market data and does not throw — the
   §1.2 regression.
4. A foreign player parses at all, i.e. `parseMarketPlayerFromString` does not return null (§1.4),
   while `parsePlayerDetailsFromString` on the same XML still does.
5. `IsAbroad` true halves-by-1.2 into the base wage in `TransferListing.fromPlayerDetails`; false
   leaves it alone (§1.6).

`TransferListingResolverTest` currently covers only `saleAt`. Add the branch table of §5.1 as a test
over `observe`'s result, with the CHPP call stubbed, in the style `TransferToolsTest` already uses to
install a fake service:

- listed, inside `SCOUT_RETRY_GRACE` → sale lookup **and** entry refreshed, in that order;
- listed, past `SCOUT_RETRY_GRACE` → entry refreshed, no sale lookup;
- not listed → sale lookup, entry untouched;
- a re-list clears `Wecker` — one assertion, and the only thing that proves the alarm still fires.

Not tested, for the existing reasons: anything needing a live CHPP session or a Swing frame. The
scout-entry refresh in §6 touches `ScoutList`, which reaches for the panel when the tab is open; the
test exercises the database path (`ScoutList.isLive()` false), same as `ScoutTableTest`.

Manual verification, since the interesting behaviour is a market event:

1. Put a listed player on the scout list, let his deadline pass unsold, download. `list_scout` shows
   a past deadline and no salePrice.
2. When he is listed again, download. `list_scout` shows a future deadline, the new asking price, and
   an `observedAt` later than the old deadline.
3. Confirm a player who sold still gets his `salePrice` filled, including the awkward case: one who
   sold and was re-listed by his buyer before the next download. Both the sale price and the new
   deadline have to land.

## 10. Files touched

| File | Change |
|---|---|
| `core/file/xml/XMLPlayersParser.java` | `MarketPlayer` record, `parseMarketPlayerFromString`, `parsePlayerDetailsFromString` reduced to the own-team wrapper (§3) |
| `core/net/OnlineWorker.java` | `downloadMarketPlayer` beside `downloadPlayerDetails` (§3.3) |
| `core/db/TransferListing.java` | `fromPlayerDetails(MarketPlayer, HODateTime)`: market fields, base-wage conversion, corrected javadoc (§4) |
| `core/net/TransferListingResolver.java` | `observe` replaces `fillMissingSkills`; `SCOUT_WATCH_GRACE`; scout pass branches on `transferListed` (§5) |
| `module/transfer/scout/ScoutList.java` | `recordRelisting(playerId, askingPrice, highestBid, deadline)` beside `recordSale`, clearing `Wecker` (§6) |
| `src/test/java/core/file/xml/XMLPlayersParserTest.java` | new (§9) |
| `src/test/java/core/net/TransferListingResolverTest.java` | branch cases (§9) |
| `src/test/java/core/db/TransferListingTableTest.java` | `snapshotsPlayerDetailsAsASkillBearingListing` (`:113`) calls `fromPlayerDetails(Player, …)` — a compile break on the §4 signature change, not optional |
| `docs/current.md` | correct the `fillMissingSkills` claim (§11) |
| `docs/chpp_files.md` | a `playerdetails` section recording §1.2, §1.3 and §1.6 |

No database migration (§1.7). No change to `Player`, which already carries `transferListed` and has
no business growing three market fields.

## 11. Documentation to correct

`docs/current.md:1198-1204` says the rows arriving without skills are covered by
`fillMissingSkills`'s one `playerdetails` call. That is wrong twice over, and the replacement should
say so plainly:

- the call has never worked for a market player at all (§1.4);
- and it could not work even with the guard lifted, because those rows are unlisted players by
  definition and Hattrick prunes an unlisted player's skills to stamina (§1.3). That slice of the
  corpus is unfillable by any means, and the pass's value is limited to rows whose player happens to
  be listed again when it runs.

`docs/chpp_files.md` has no `playerdetails` section. It should gain one, recording the version HO
requests, the `TransferListed` / `TransferDetails` / `PlayerSkills` behaviour above, and the
`IsAbroad` wage surcharge — all four are things that cost a live fetch to learn and that no CHPP doc
page in `docs/raw/` currently covers.
