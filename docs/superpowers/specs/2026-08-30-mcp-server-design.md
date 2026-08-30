# MCP server for HO — design

Status: approved for planning, 2026-08-30. Reviewed against the code 2026-08-30 — findings folded
in below; §3 and §5.3 changed materially. Branch `macos-fixes`.

Lets an AI assistant (Claude Code or any MCP client) read the team HO already knows about,
search the transfer market, value players against live comparables, and build lineups —
by talking to the running HO process over MCP.

## Goals

- Read squad, team, finances, fixtures and the current lineup.
- Search the transfer market and find comparables for a player being valued.
- Build a lineup: score candidates without side effects, then commit the chosen one to HO.
- Accumulate a price corpus so valuation gets better over time.

## Non-goals

- Uploading match orders to Hattrick, placing bids, or any other write that leaves the machine.
- Authentication. Explicitly declined; see §2.3 for what replaces it.
- MCP `resources` and `prompts`. Tools only.
- A training planner, a youth-squad surface, or an SSE/streaming transport.

## 1. Constraints discovered

These are load-bearing. Each one was verified against the code, not assumed.

### 1.1 The DB forces the server in-process

HO opens HSQLDB in file mode (`jdbc:hsqldb:file:<dbFolder>/database`) and holds an exclusive
lock. A standalone MCP process cannot read the team data while HO is running. The server
therefore lives inside the HO JVM and reaches the singletons directly. This is also the only
way it can see the live in-memory lineup and trigger CHPP downloads.

### 1.2 CHPP forbids automated transfer-market scanning

`OnlineWorker.searchTransfers` carries:

> CHPP does not allow the transfer market to be scanned automatically, so this may only be
> called on an explicit user request.

`docs/chpp_files.md` confirms it from the CHPP file description: *"not meant to be polled by
bots, only fetched on manual user request."*

An MCP server that hands an agent a market-search tool is structurally a bot searching the
market. The distinction this design draws: a search issued **during a user turn**, because a
human asked a question, is that human's manual request expressed through a different UI. A
search issued by a timer, a background job, or an unattended loop is not. §4 makes that
distinction enforceable rather than aspirational.

### 1.3 `searchTransfers` is not callable off the EDT

It calls `HOMainFrame.instance().setWaitInformation()` on entry and exit, and on failure pops a
`JOptionPane` via `Helper.showMessage`. From an HTTP handler thread that is a Swing threading
violation, and the modal dialog would block a thread nobody is watching. It needs a headless
sibling; see §7.

### 1.4 There is no valuation engine in the codebase

`Marktwert` throughout `SpielerTable`, `ScoutTable` and `UserColumnFactory` is the stored TSI
column, not a computed price. `TransferTable` holds only the user's *own* transfers — price and
TSI, no skills — so it is a personal history, not a comparables corpus. Valuation has to come
from live market data. §5.

## 2. Architecture

One new package, `core.mcp`, inside the running HO JVM.

```
Claude Code ──HTTP POST /mcp──▶ McpHttpServer   (com.sun.net.httpserver, 127.0.0.1:8765)
                                     │           JSON-RPC 2.0 framing, gson
                                     ▼
                                McpToolRegistry  name → handler dispatch table
                                     │
                    ┌────────────────┼──────────────────┐
                    ▼                ▼                  ▼
             HOVerwaltung      TransferSearch      HOModel
              .instance()        Service           .getRatingPredictionModel()
               .getModel()     (§4, §7)            module.lineup.Lineup
                                     │
                                     ▼
                            TransferListingTable (§5)
```

### 2.1 No new dependencies

- `com.sun.net.httpserver` is JDK stdlib. `CachedUrlImageProviderTest`
  (`src/test/java/core/gui/image/`) is the only use in the repo today — but that is **test** scope
  on the full local JDK, so it does not prove `jdk.httpserver` is in the *shipped* runtime. There
  is no `module-info.java` and no jlink configuration anywhere in the build, so an install4j or
  `jpackage` bundle almost certainly carries the module; the plan must confirm that against a real
  bundle rather than against the test.
- gson 2.12.1 is already a compile dependency.

The official Java MCP SDK is deliberately not used. It pulls Reactor into a desktop Swing app to
provide what is a few hundred lines of `initialize` / `tools/list` / `tools/call` dispatch here.

### 2.2 Transport

MCP Streamable HTTP, single endpoint `POST /mcp`, responses as `application/json`.

No SSE. Nothing in this design pushes server-initiated messages, so the event-stream half of the
transport is dead weight. `GET /mcp` returns `405`.

Client config:

```json
{ "mcpServers": { "ho": { "type": "http", "url": "http://127.0.0.1:8765/mcp" } } }
```

### 2.3 Security posture

No token, by explicit decision. Two guards remain, and they are not optional:

1. **Bind `127.0.0.1` explicitly**, never `0.0.0.0`.
2. **Reject any request carrying an `Origin` header** with `403`. Real MCP clients do not send
   one; browsers always do. This is the DNS-rebinding guard the MCP spec requires of local
   servers, and without it any page open in the user's browser can POST to the port and read the
   whole squad.

These are ~4 lines total and are the entire perimeter. They must not be dropped as "simplification".

## 3. Tool surface

Ten tools. Every one returns JSON; every one returns a structured error rather than throwing
(§8).

### Read

| Tool | Input | Backed by |
|---|---|---|
| `get_team` | — | `HOModel.getBasics()`, `.getEconomy()`, `.getLeague()`, `.getStadium()`, `.getStaff()` |
| `get_squad` | — | `HOModel.getCurrentPlayers()` |
| `get_player` | `playerId` | `HOModel.getCurrentPlayer(id)` + `DBManager.getTransfers(id, true)` |
| `get_upcoming_matches` | — | `HOModel.getFixtures()` |
| `get_lineup` | — | `HOModel.getCurrentLineup()` + ratings |

`get_squad` returns per player: id, name, age (years + days), the eight skills, form, stamina,
loyalty, motherclub, TSI, wage, specialty, injury level, cards, transfer-listed flag — plus a
`positionRatings` map computed with
`RatingPredictionModel.getPlayerRatingMatchBeginning(Player, roleId, behaviour)` over the eleven
field roles `IMatchRoleID.keeper` (100) through `leftForward` (113), at `NORMAL` behaviour.
Positional ratings are the single most useful thing the model can get in one call — without them
it has to guess role fit from raw skills.

### Transfer

| Tool | Input | Backed by |
|---|---|---|
| `search_transfers` | a `TransferSearchQuery` as JSON | `TransferSearchService` → `OnlineWorker` core (§7) |
| `find_comparables` | `playerId`, optional `ageTolerance` (default 1), `skillTolerance` (default 1) | builds a query from the player's own attributes, then `search_transfers` |

`search_transfers` input mirrors the existing `TransferSearchQuery` record: `ageMin`, `ageMax`,
`skillType1`, `minSkillValue1`, `maxSkillValue1` mandatory; `skillType2..4` with their min/max,
`specialty`, `nativeCountryId`, `tsiMin/Max`, `priceMin/Max`, `pageSize`, `pageIndex` optional.
Skill names are accepted as the `TransferSearchQuery.Skill` enum names (`PLAYMAKING`, `WINGER`, …),
not raw CHPP ints, so the model does not have to know the numbering.

Validation runs through the existing `TransferSearchQuery.validationErrorKey()` **before** the
call goes out. Hattrick answers an invalid combination with an error file, not an empty result,
so a wasted round trip is also a wasted CHPP call. Server-side rules already encoded there: min
and max of a skill pair may differ by at most 4 levels; set pieces, leadership and experience
need a minimum level of 5; keeper a minimum of 3.

`find_comparables` derives the query from the player: age ± `ageTolerance`, the player's two
highest **positional** skills as `skillType1`/`skillType2` each ± `skillTolerance` (clamped to the
4-level span rule and to each skill's minimum level), and the player's specialty. It returns the
hits together with an `echo` of the query it built, so the model can see and refine what was
actually asked.

"Two highest" must be taken over the positional skills only — `KEEPER`, `DEFENDING`, `PLAYMAKING`,
`PASSING`, `WINGER`, `SCORING`, `SETPIECES`. `TransferSearchQuery.Skill` also contains `STAMINA`,
`LEADERSHIP` and `EXPERIENCE`; a naive maximum over the whole enum will happily pick stamina or
experience, which say nothing about comparability, and leadership/experience additionally carry a
minimum level of 5 (`TransferSearchQuery.minimumLevel`) that would then reject the query.

Results carry the full `XMLTransferSearchParser.Hit`: `playerId`, `playerName`,
`nativeCountryId`, `askingPrice`, `deadline`, `highestBid`, `bidderTeamName`, seller fields, and
`Details` (age, ageDays, salary, TSI, form, experience, leadership, specialty, cards,
injuryLevel, and the eight searchable skills — stamina, keeper, playmaker, scorer, passing,
winger, defender, setPieces). `Details` is null once a deadline has passed —
every consumer must handle that, as `TransferSearchConverter` already does.

### Lineup

| Tool | Input | Backed by |
|---|---|---|
| `optimize_lineup` | formation/priority flags, `availablePlayerIds` | `Lineup.optimizeLineup(...)` on a scratch lineup |
| `evaluate_lineup` | a lineup spec | `getRatingPredictionModel()` on a **throwaway** `Lineup` |
| `apply_lineup` | a lineup spec | mutates `HOModel.getCurrentLineup()` in place, on the EDT |

`apply_lineup` does **not** call `HOModel.setLineup(...)` — that takes a `MatchLineupTeam`, not a
`Lineup`. The live lineup is `getCurrentLineupTeam().getLineup()`, a `Lineup` owned by its team.
So `apply_lineup` mutates that instance in place (`setSpielerAtPosition`, `setTacticType`,
`setAttitude`, `setCaptain`, `setSetPiecesTaker`) and then persists via
`HOModel.storeLineup(getCurrentLineupTeam())`, which is the same path the Lineup tab uses.

`evaluate_lineup` is the load-bearing tool. It builds a scratch `Lineup`, scores it, and never
touches the live model — so the model can try ten variants in one turn for free. This is where an
LLM beats the combinatorial assistant: reasoning about a specific player in a specific role,
specialty interactions, and weather, rather than only turning the knobs the assistant exposes.

`optimize_lineup` wraps
`Lineup.optimizeLineup(List<Player>, byte sectorsStrengthPriority, boolean withForm,
boolean idealPosFirst, boolean considerInjured, boolean considereSuspended, boolean useAverageRating)`
to give the model a strong baseline to refine rather than starting from an empty pitch.

**Lineup spec** — the shared input shape for `evaluate_lineup` and `apply_lineup`:

```json
{
  "positions": [ { "roleId": 100, "playerId": 12345, "behaviour": 0 } ],
  "bench":     [ { "roleId": 114, "playerId": 67890 } ],
  "tactic": 0, "attitude": 0, "styleOfPlay": 0,
  "captainId": 12345, "setPiecesTakerId": 67890
}
```

`roleId` uses `IMatchRoleID` (100 keeper … 113 leftForward for the field, 114–118 for the bench
slots). `behaviour` uses `IMatchRoleID.NORMAL` (0), `OFFENSIVE` (1), `DEFENSIVE` (2),
`TOWARDS_MIDDLE` (3), `TOWARDS_WING` (4).

**Ratings output** — for both `evaluate_lineup` and `get_lineup`, the seven
`RatingPredictionModel.RatingSector` values (`DEFENCE_LEFT`, `DEFENCE_CENTRAL`, `DEFENCE_RIGHT`,
`MIDFIELD`, `ATTACK_LEFT`, `ATTACK_CENTRAL`, `ATTACK_RIGHT`) via
`getAverageRating(lineup, sector, 90)`, plus the derived formation string from
`getCurrentTeamFormationString()`.

**Resolved during review — the shared model instance cannot be used for throwaway lineups.**
`Lineup.ratingRevision` is a **per-instance** counter starting at `0` (`Lineup.java:157`), and
`LineupRatingCache` is a `HashMap<Long, …>` keyed on that number alone, holding exactly one
revision at a time — a miss calls `clear()` (`LineupRatingCache.java:10-14`). `verifyCaches()`
does not separate them either: it compares `Team.getRatingRevision()`, not the lineup's
(`RatingPredictionModel.java:123`). So a scratch `Lineup` at revision 0 and the live `Lineup` at
revision 0 are indistinguishable to the cache: `evaluate_lineup` can be served the live lineup's
ratings, and can poison the live lineup's cache with the scratch one's. Alternating between the
two also drops the hit rate to zero.

`evaluate_lineup` and `optimize_lineup` must therefore score on a **private**
`RatingPredictionModel`, never on `HOModel.getRatingPredictionModel()`.

That private instance must not be built with a bare `new RatingPredictionModel(team)`, which
silently drops the user's model selection: `RatingPredictionManager.getRatingPredictionModel()`
returns a shared instance that may be the Groovy `AkasolaceRatingPredictionModel`
(`RatingPredictionManager.java:49-50, 84-95`). The plan picks one of:

- construct a fresh instance of the *same* class the manager currently holds (reflection or a
  small factory hook in `RatingPredictionManager`) — preferred, keeps the MCP ratings identical to
  what the Lineup tab shows; or
- key `LineupRatingCache` / `StatsCache` / `TacticRatingCache` on lineup identity as well as
  revision — correct at the root and fixes the same latent collision for existing UI code, but a
  wider change to hot rating code than this feature should carry on its own.

## 4. Transfer-search guards

These implement §1.2 and are a hard requirement, not a policy preference. They live in
`TransferSearchService`, so no caller can route around them.

1. **No unattended entry point.** `TransferSearchService` exposes no entry point reachable from a
   timer, a `Thread`, or a scheduled task, and nothing in HO may call it from one.

   This is a code-structure convention, not enforcement, and the design must not pretend
   otherwise: `tools/call` over stateless HTTP carries no evidence of a user turn, so an
   unattended loop and a human question produce byte-identical requests. Given §1.2 treats the
   CHPP restriction as load-bearing, the plan must add one real gate on the HO side. Cheapest that
   actually works: an **"unlock market search for N minutes"** toggle in `McpServerPanel`, default
   locked, which the user flips when they sit down to ask questions. `search_transfers` refuses
   while locked with a structured error saying so. A human action in HO's own UI is then a
   precondition for every search, which is what CHPP is asking for.
2. **Per-session cap**, default 20 searches, stored in `UserParameter` and editable in the
   options panel. On exhaustion the tool returns a structured error naming the cap and how to
   raise it — the model must be told *why* it was refused, or it will retry.
3. **No scheduler, ever.** The price corpus in §5 is fed only by searches the user already asked
   for. There is no crawl, no warm-up, no prefetch.

The price-resolution pass in §5.2 uses `transfersPlayer`, a different CHPP file not covered by
the market-scanning restriction. It is still throttled and still manual.

## 5. Price corpus

### 5.1 Snapshot on every search — free

A new table `TRANSFERLISTING`, one row per listing per observation. Every `search_transfers`
result is written before being returned. Zero extra CHPP traffic: the data is already in hand.

Columns: `playerid`, `observedat` (timestamp), `deadline`, `askingprice`, `highestbid`,
`biddername`, `tsi`, `age`, `agedays`, `salary`, `specialty`, `form`, `experience`, `leadership`,
`cards`, `injurylevel`, the eight searchable skills (`stamina`, `keeper`, `playmaker`, `scorer`,
`passing`, `winger`, `defender`, `setpieces`), `saleprice` (nullable), `resolvedat` (nullable).
Primary key `(playerid, observedat)`.

`highestbid` matters more than `askingprice`. A listing observed shortly before its deadline has
a highest bid very close to what it actually sold for — which means repeated observation alone
yields near-sale prices without any follow-up call at all.

### 5.2 Resolve expired listings — opt-in, manual

A bounded user-triggered action walks rows where `deadline < now AND saleprice IS NULL`, calls
`Connector.getTransfersForPlayer(playerId)` (CHPP `transfersPlayer`, parsed by the existing
`module.transfer.XMLParser`), and fills in `saleprice` + `resolvedat`. Rate-limited, capped per
run, never automatic, never on a timer.

Its trigger is a button in `McpServerPanel` (§9) — the same panel that owns the enable toggle —
with the per-run cap next to it. It is deliberately not an MCP tool: an agent must not be able to
start a batch of CHPP calls.

### 5.3 Migration

Per `AGENTS.md`:

- New `core.db.TransferListingTable extends AbstractTable` declaring the above in `initColumns()`.
- Register in `DBManager.initAllTables()`.
- `DBManager.DBVersion` 1000 → 1001, **and `DBManager.previousDBVersion` 901 → 1000**.
- New `DBUpdater.updateDBv1001(int)` calling `transferListingTable.createTable()`, plus
  `case 1000:` invoking it and a new empty `case 1001:` terminating the fall-through — matching
  how `HallOfFamePlayersTable` was added in `updateDBv900`.

Bumping `previousDBVersion` is not optional. `DBUpdater.updateDB` clamps the stored version down
to `DBManager.getVersion()` (`DBUpdater.java:39-42`), and `getVersion()` returns
`previousDBVersion` under `HO.isDevelopment()` (`DBManager.java:106-109`). Leaving it at 901 makes
every DEV start re-run `updateDBv1000` *and* `updateDBv1001` rather than just the newest step.

(The method is `DBManager.getVersion()`, not `getDBVersion()` — `AGENTS.md` names it wrongly.)

`AbstractTable.createTable()` is guarded by `tableExists()`, so the new step is idempotent, which
is what makes the DEV re-run harmless rather than fatal.

## 6. Threading

HO is Swing; `HttpServer` handlers are not on the EDT.

- **Read-only** model access runs on the handler thread — but "nothing mutates it concurrently"
  stops being true the moment `apply_lineup` exists: it mutates the live `Lineup` on the EDT while
  a `get_lineup` or `get_squad` reads it from one of the 4 pool threads, which a single client
  that pipelines can reach. Reads that touch the live lineup (`get_lineup`, and the
  `positionRatings` in `get_squad`) therefore also marshal through the EDT; only reads of
  immutable snapshots (`get_team`, `get_upcoming_matches`, `get_player`) stay on the handler.
- **Mutating** tools — `apply_lineup` — marshal through `SwingUtilities.invokeAndWait` and
  propagate any exception back as a JSON-RPC error.
- The `HttpServer` gets a small fixed thread pool (4), not the default single-threaded executor,
  so one slow CHPP call does not stall `tools/list`.

## 7. Headless transfer search

Extract the network + parse core out of `OnlineWorker.searchTransfers` into
`TransferSearchService.search(TransferSearchQuery)`, which:

- validates via `validationErrorKey()`,
- calls `Connector.instance().getTransferSearch(query)`,
- parses via `XMLTransferSearchParser.parse(xml)`,
- returns the `Page` or throws a checked failure carrying the message,
- **shows no dialog and touches no Swing.**

`OnlineWorker.searchTransfers` keeps its current signature and behaviour, reimplemented on top of
the service: it wraps the call in `setWaitInformation()` and converts a thrown failure into the
existing `setInfoMsg` + `JOptionPane`. The transfer search *tab* shipped last week keeps working
unchanged. The MCP path calls the service directly.

The session cap and the turn-scoped check (§4) live in the service, so both callers are covered.

## 8. Errors

JSON-RPC 2.0 error objects. `-32700` malformed JSON, `-32601` unknown method, `-32602` invalid
params (including a failed `validationErrorKey()`, with the key and a readable message), `-32603`
internal. Tool-level
failures that are expected — no model loaded, search cap reached, unknown player id — return an
`isError: true` tool result with a plain-English explanation rather than a protocol error, so the
model can read and act on it instead of treating it as a transport fault.

No stack trace crosses the wire. Everything is logged through `HOLogger.instance()`.

## 9. Enablement and configuration

Off by default.

- `UserParameter` gains `mcpServerEnabled` (default `false`), `mcpServerPort` (default `8765`),
  `mcpSearchesPerSession` (default `20`), and `mcpMarketUnlockMinutes` (default `30`) for the §4
  unlock window. The unlock's expiry timestamp itself is in-memory only — it must not survive a
  restart.
- A new `core.option.McpServerPanel`, registered in `core.option.OptionenDialog` alongside
  `DownloadPanel` and `ReleaseChannelPanel`: enable checkbox, port field, search-cap field, the
  §4 "unlock market search" button with its remaining-time label, the §5.2 "resolve expired
  listings" button with its per-run cap, and a read-only line showing the client config URL.
- Toggling starts/stops the `HttpServer`. Port already in use is reported in the panel, not
  swallowed.
- HO shuts the server down cleanly on exit.

Nothing binds a port unless the user asked for it.

## 10. Testing

JUnit 5 + AssertJ, matching the existing ~42 test files. Unit tests over pure logic; no Swing
harness and no live CHPP, consistent with the repo.

- JSON-RPC framing: `initialize` handshake, `tools/list` schema shape, unknown method → `-32601`,
  malformed JSON → `-32700`.
- The `Origin` rejection returns `403`.
- `find_comparables` builds the expected `TransferSearchQuery` from a fixture player, including
  the 4-level span clamp and per-skill minimum levels.
- `find_comparables` builds the expected `TransferSearchQuery` from a fixture player, including
  the 4-level span clamp, per-skill minimum levels, and that it never picks `STAMINA`,
  `LEADERSHIP` or `EXPERIENCE` even when those are the player's highest values.
- The per-session search cap refuses the 21st call and says so.
- `search_transfers` refuses while the §4 market-search unlock is expired, and succeeds while it
  is live.
- `TransferListingTable` round-trips a `Hit` including a null `Details` and a null `saleprice`.
- `TransferSearchService` surfaces a validation failure without any Swing call — the regression
  guard for §1.3.
- **Rating cache isolation** — the §3 regression guard: score a scratch `Lineup` through
  `evaluate_lineup`, then read the live lineup's ratings, and assert the live values are unchanged.
  Both lineups sit at `ratingRevision` 0 in a fresh fixture, which is exactly the colliding case.

## 11. Files touched

New:

- `src/main/java/core/mcp/McpHttpServer.java`
- `src/main/java/core/mcp/McpToolRegistry.java`
- `src/main/java/core/mcp/tools/` — one class per tool group (team, transfer, lineup)
- `src/main/java/core/net/TransferSearchService.java`
- `src/main/java/core/db/TransferListingTable.java`
- `src/main/java/core/option/McpServerPanel.java`
- matching tests under `src/test/java/core/mcp/`

Modified:

- `core/net/OnlineWorker.java` — `searchTransfers` reimplemented on the service
- `core/db/DBManager.java` — register table, bump `DBVersion` to 1001 and `previousDBVersion` to 1000
- `core/db/DBUpdater.java` — `updateDBv1001`, `case 1000:`, `case 1001:`
- `core/model/UserParameter.java` — new fields (see §9, plus the market-search unlock)
- `core/option/OptionenDialog.java` — register the panel
- `core/HO.java` — start on launch when enabled, stop on shutdown
- `src/main/resources/language/English.properties` — new option-panel keys
- `core/rating/RatingPredictionManager.java` — only if §3 takes the factory-hook route for private
  rating models; not needed if the reflection route is taken

New `ls.*` keys go into `English.properties` only. As with the transfer-search tab, a maintainer
must add them to POEditor before release or other languages show `!key!`.

## 12. Open items for the plan

1. Which of the two §3 routes to take for private rating models — fresh instance of the manager's
   current model class, or keying the rating caches on lineup identity. The first is the smaller
   diff; the second fixes a latent collision that already exists for UI code.
2. Whether the §4 unlock window is a duration ("unlocked for 30 minutes") or a plain checkbox the
   user is expected to turn off. A duration cannot be forgotten in the on position, so it is the
   default assumption unless the plan finds it annoying in practice.
3. Whether `previousDBVersion` should track `DBVersion - 1` generally rather than being bumped by
   hand each migration. Out of scope here; noted because §5.3 is the second migration in a row to
   trip over it.
4. `pageSize` defaults to 50 for MCP searches, matching the tab, and is capped at the CHPP
   maximum of 100. Paging is not exposed: `ItemCount` comes back as `-1` whenever more than 100
   players match, so exact totals are unavailable and a second page buys little. A model that
   wants different results should narrow the query, not walk pages — which also keeps the
   per-session cap in §4 meaningful.
