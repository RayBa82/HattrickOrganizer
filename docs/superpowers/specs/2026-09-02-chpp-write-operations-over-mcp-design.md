# CHPP write operations over MCP — design

Status: approved for planning, 2026-09-02. Reviewed against the code twice (2026-09-02, and again
2026-09-03 with the IDE index — §1.3–§1.5, §2, §3, §7 and §8 changed materially). Branch `macos-fixes`.
Builds on `2026-08-30-mcp-server-design.md`, whose non-goal "uploading match orders to Hattrick,
placing bids, or any other write that leaves the machine" this design deliberately reverses.

Lets an MCP client drive the CHPP write actions HO can perform on behalf of a team: set the senior
training, send the match order for an upcoming game, arrange / accept / decline / withdraw friendly
challenges, and place a transfer bid. Every one of these leaves the machine and changes state in
Hattrick.

## Goals

- Expose over MCP the four CHPP write families: `set_training`, match-order upload, challenges
  (all four verbs), and transfer bids.
- Route them through one `core.net.ChppWriteService` shaped like the existing
  `TransferSearchService` — a `fetch` seam for tests, a per-session cap, typed failures — with the
  raw calls and URL building in `Connector` and response parsing in `XMLxxxParser` classes.
- Keep the transfer bid behind a two-step confirm so a single stray tool call cannot commit money.

## Non-goals

- Bid-tracking maintenance (`deleteAllFinished`, `ignoreTransfer`). Not asked for; marginal value.
- A standalone "is this team challengeable" pre-check tool. Folded into `arrange_challenge`'s error.
- Youth-team training, NT match orders, or any write not in the list above.
- A confirmation gate on the non-bid writes. Per the product decision (2026-09-02) they fire
  directly, matching the existing `apply_lineup` / `set_player_group` writers.
- Any handling of an OAuth token that predates the new scopes. Adding the scopes to `OAuthDialog`
  is HO-internal auth plumbing (§5); a freshly authorized token carries them. This design assumes
  the running token already holds the scope by the time a tool is called (§1.3).
- New DB tables or a schema migration. Nothing here persists locally beyond what a normal
  download already stores.

## 1. Constraints discovered

Verified against the code, most recently 2026-09-03 via the IDE index.

### 1.1 HO performs exactly one CHPP write today

`Connector.uploadMatchOrder` (`Connector.java:314`) → `OnlineWorker.uploadMatchOrder` →
`MatchAndLineupSelectionPanel:370`. It POSTs to `?file=matchorders&version=3.0&actionType=setmatchorder`
with the lineup JSON in the body and passes scope `set_matchorder` to
`postWebFileWithBodyParameters`. `set_training`, `manage_challenges` and `place_bid` have **no code
anywhere in HO** — no `Connector` method, no service, no parser.

### 1.2 The OAuth scope string is hard-coded in one place

`OAuthDialog.java:54`:

```java
// Hard code set_matchorder due to authorization woes.
// scope manage_youthplayers is needed to use action type unlockskills
scopes = "&scope=" + "set_matchorder,manage_youthplayers";
```

The `scope` constructor parameter (`OAuthDialog(mainFrame, service, scope)`) is **dead** — never
read; `obtainUserURL()` appends the hard-coded `scopes` field to the authorization URL. So the
scope set is a one-line edit to that string, and only there (§5).

### 1.3 A missing scope is just another CHPP error — and out of scope here

The fresh-install authorization flow requests whatever `OAuthDialog`'s string holds, so a newly
authorized token carries the expanded scope set. This design **assumes the running token already
has the scope** when an MCP client calls a write tool — it does not detect, prompt for, or recover
a stale token. If a call is somehow made without the scope, Hattrick answers with an `<error>`
block and it surfaces through the ordinary failure path (§6): a blank / error response →
`ChppWriteException` carrying `Connector.instance().lastError()`, no different from any other CHPP
rejection.

### 1.4 Tool handlers run off the EDT — CHPP calls must run silent

`McpHttpServer` handles each request on an HTTP-pool thread. `Connector`'s CHPP methods do network
I/O and are safe there, **but** on a CHPP error `getCHPPWebFile` and `postWebFileWithBodyParameters`
pop a modal `JOptionPane` / `OAuthDialog`, which off the EDT blocks HO until a human clicks OK and
leaves the MCP request hanging until it times out. `TransferSearchService.searchForAgent` already
solved this: it wraps the call in `Connector.setSilentDownload(true)` and its `fetch` comment spells
out why. `ChppWriteService` does the same — every `fetch` runs silent (§3). Reads of `HOModel` /
`HOVerwaltung` that a tool needs (e.g. the current lineup for `send_match_order`) still go through
the `LineupTools.onEdt` helper.

### 1.5 Current training values are read from `Team`; there is no light training refresh

`HOModel.getTraining()` (`HOModel.java:704`) *constructs* a `TrainingPerWeek` each call from
`getTeam().getTrainingsArtAsInt()`, `getTrainingslevel()`, `getStaminaTrainingPart()`,
`getClub().getCoTrainer()` and `getTrainer().getCoachSkill()` — no setter path. `Connector.getTraining`
*does* exist (`?file=training&version=2.1`), but its only caller is `ConvertXml2Hrf.trainingDataMap`
(`ConvertXml2Hrf.java:166`) — it is a step of the full HRF conversion, not a standalone refresh into
the live model. So after a successful `setTraining` CHPP call the local snapshot is stale until the
next full download. §3.1 returns the values Hattrick echoes and flags the staleness rather than
faking a local update.

### 1.6 CHPP request formats

Taken from a current, live-verified reference implementation (`github.com/lucianoq/hattrick`,
`chpp/*` and `api/*`, checked 2026-09-02; its challenges structs carry live-response casing notes
dated 2026-08-27). Every write below is a `GET` with query parameters — only match-order upload is a
POST.

| Family | file | version | actionType | Parameters |
|---|---|---|---|---|
| set training | `training` | 2.1 (HO's current) | `setTraining` | `teamId`, `trainingType` (int), `trainingLevel` (0–100), `trainingLevelStamina` (5–100) |
| list challenges | `challenges` | 1.6 | `view` | `isWeekendFriendly=1` (optional) |
| arrange challenge | `challenges` | 1.6 | `challenge` | `opponentTeamId`, `matchType` (0 normal / 1 cup rules), `matchPlace` (0 home / 1 away / 2 neutral), `neutralArenaId` (only when `matchPlace=2`), `isWeekendFriendly=1` (optional) |
| accept / decline / withdraw | `challenges` | 1.6 | `accept` \| `decline` \| `withdraw` | `trainingMatchId` |
| place bid | `playerdetails` | 3.0 (HO's current) | `placeBid` | `playerID`, `teamId`, `bidAmount` (int), `maxBidAmount` (int, optional autobid ceiling) |

`training` `setTraining` echoes the new state under `<View>` (same schema as an ordinary `training`
`view`, so `XMLTrainingParser` reads it) plus `<TrainingSet>true|false</TrainingSet>`. `challenges`
`view` returns `<ChallengesByMe><Challenge>` and `<OffersByOthers><Offer>` lists, each with
`<TrainingMatchID>`, `<MatchTime>` (datetime string, *not* the int the doc claims), `<FriendlyType>`,
`<Opponent>` (team, arena, league, `<IsAgreed>`). `placeBid` returns the updated `<Player>` block
from `playerdetails`.

`FriendlyType`: 0 normal rules, 1 cup rules, 12 NT friendly (not offered by our tool).
`MatchPlace`: 0 home, 1 away, 2 neutral.

## 2. Layering

```
core.mcp.*Tools        thin tool: validate args, call ChppWriteService, shape JSON result
core.net.ChppWriteService   singleton like TransferSearchService: fetch() seam, per-session cap,
                            typed ChppWriteException; every fetch() runs silent (§1.4)
core.net.Connector          raw call + package-private URL builder (the wire-format test seam)
core.file.xml.XML*Parser    response -> records (XMLChallengesParser new; XMLTrainingParser reused)
core.net.login.OAuthDialog  +3 scopes in the hard-coded string (HO-internal, §5)
```

`ChppWriteService` is the deliberate choice over adding static methods to `OnlineWorker`:
`TransferSearchService` was extracted from `OnlineWorker.searchTransfers` precisely because a
static `OnlineWorker` method that touches Swing and `Connector` directly is not testable and not
safe off the EDT. The new writes have the same shape, so they get the same treatment — one service,
four families, a `protected fetch(...)` per family overridden in tests, a `synchronized` session
counter, and `ChppWriteException(errorKey, message)` mirroring `TransferSearchService.SearchFailed`.

Rejected: tools calling `Connector` directly (untestable, unsafe off the EDT); a generic
`chpp_request` passthrough tool (no validation, no typing, hands raw CHPP semantics to the model).

## 3. ChppWriteService, Connector, parsers

`ChppWriteService` (in `core.net`, next to `TransferSearchService`):

- `public static synchronized ChppWriteService instance()`; package-private constructor; tests
  subclass and override `fetch`.
- `private int writesThisSession` — `synchronized`, checked against
  `UserParameter.instance().mcpChppWritesPerSession` before each network write, incremented after
  the cap check, exactly as `TransferSearchService.searchesThisSession` /
  `mcpSearchesPerSession`. Step-1 bid previews do not touch the network and do not count.
- One `protected String fetch<Family>(...)` per family. Each sets `silentDownload` around the
  `Connector` call (`final var was = Connector.instance().setSilentDownload(true); try { … } finally
  { Connector.instance().setSilentDownload(was); }`).
- `ChppWriteException extends Exception` with an `errorKey()` for a translatable message, like
  `SearchFailed`. A blank / error response → `ChppWriteException` with
  `Connector.instance().lastError()` as the reason.
- `*ForAgent` public methods, one per tool, returning small records / DTOs.

Connector gets the raw calls and their package-private URL builders (the wire-format test seam):

```java
// training
String setTrainingUrl(int teamId, int trainingType, int trainingLevel, int staminaLevel);   // package-private
String setTraining(int teamId, int trainingType, int trainingLevel, int staminaLevel);       // getCHPPWebFile(url)
// challenges
String challengesUrl(String actionType, Map<String,String> params);                          // package-private
String challenges(String actionType, Map<String,String> params);                             // getCHPPWebFile(url)
// bid
String placeBidUrl(int playerId, int teamId, long bidAmount, Long maxBidAmount);             // package-private
String placeBid(int playerId, int teamId, long bidAmount, Long maxBidAmount);                // getCHPPWebFile(url)
```

Parsers, following the house one-`XMLxxxParser`-per-file convention:

- **`XMLChallengesParser` (new)** — `challenges` `view` → `{ outgoing: List<Challenge>, incoming:
  List<Offer> }` records `{trainingMatchId, matchTime, friendlyType, opponentTeamId, opponentName,
  arenaName, isAgreed}`. Built and tested like `XMLTransferSearchParser` / `XMLTransferSearchParserTest`.
- **`XMLTrainingParser` (exists)** — reused to read the `<View>` block `setTraining` echoes.
- match-order and bid responses need only `XMLCHPPPreParser.getError` plus one or two fields pulled
  with `XMLManager` helpers, the way `uploadMatchOrder` already works — no new parser.

### 3.1 Training

`ChppWriteService.setTrainingForAgent(int trainingType, int intensity, int staminaShare)`:

- Range-checked in the tool already (§4.1); the service re-guards and throws `ChppWriteException`.
- Team id from `HOVerwaltung.instance().getModel().getBasics().getTeamId()`. Cap check. `fetch`.
- Blank / `<error>` → `ChppWriteException` with `Connector.lastError()`.
- `XMLTrainingParser` on the response; `<TrainingSet>false</TrainingSet>` → `ChppWriteException`.
- Returns the committed `{trainingType, intensity, staminaShare}` from `<View>`. Local snapshot is
  not refreshed (§1.5) — the tool says so.

### 3.2 Match order

`Connector.uploadMatchOrder` exists but calls `postWebFileWithBodyParameters(..., showErrorMessage:
true, "set_matchorder")`, which pops a modal dialog on a CHPP error — unacceptable on the MCP
handler thread (§1.4). Add a `boolean showErrorMessage` overload (the four-arg method delegates with
`true`). `ChppWriteService.sendMatchOrderForAgent(int matchId, MatchType matchType)`: cap check,
call the overload with `false`, current lineup from `model.getCurrentLineup()` read via
`LineupTools.onEdt`. `set_matchorder` is already in the token's scopes, so nothing changes in §5
for this family.

### 3.3 Challenges

`ChppWriteService`:

- `listChallengesForAgent(boolean weekend)` → `XMLChallengesParser` → the outgoing / incoming
  record lists. (A read, but it lives here so the cap and silent-fetch handling are shared; reads
  do **not** increment the write counter.)
- `arrangeChallengeForAgent(int opponentTeamId, int friendlyType, int matchPlace, Integer
  neutralArenaId, boolean weekend)` — guard: `matchPlace == 2` requires a non-null `neutralArenaId`,
  else `ChppWriteException` before the call. Success = no `<error>`.
- `respondToChallengeForAgent(String verb, long trainingMatchId)` — `verb` in
  `{accept, decline, withdraw}`, whitelisted in the tool.

No local refresh — challenges are not part of `HOModel`.

### 3.4 Bid

`ChppWriteService.placeBidForAgent(int playerId, long bidAmount, Long maxBidAmount)`:

- Team id from the model. Cap check. `fetch`.
- `<error>` → `ChppWriteException` (`BidFailed` is CHPP error code 71 — its text is surfaced).
- Parse the returned `<Player>` for the post-bid `<HighestBid>` / `<Deadline>` / bidder team.
- No local write.

## 4. MCP tools

Money is `long` throughout — add `McpJson.reqLong` / `McpJson.optLong` (Hattrick prices fit in `int`
today, but `long` costs nothing and matches `TransferListing`). Every tool below catches
`ChppWriteException` and rethrows `McpToolException` with the Hattrick text intact; a cap-exhausted
failure says so and names HO's Options → MCP server panel, exactly as the transfer-search cap does.

### 4.1 `TrainingTools.set_training`

```
set_training  { trainingType: int (required),
                intensity:    int 0..100 (required),
                staminaShare: int 5..100 (required) }
```

Description tells the model to read `get_training` first for the current values and the
`trainingType` id meanings. Validates the two ranges locally (fail with the allowed range in the
message) before any call. On success returns
`{ status:"ok", committed:{trainingType,intensity,staminaShare}, note:"HO's snapshot is not
refreshed until the next team download." }`. Fires directly.

### 4.2 `LineupTools.send_match_order`

```
send_match_order  { matchId:   int (required),
                    matchType: int (required — the id from get_matches / get_upcoming_matches) }
```

Sends **the lineup currently set in HO** for that match via
`ChppWriteService.sendMatchOrderForAgent(matchId, MatchType.getById(matchType))`. No lineup
argument: compose with `apply_lineup` first — `get_lineup` / `apply_lineup` / `send_match_order` is
the build-review-send sequence, and it keeps one lineup schema in the codebase.
`model.getCurrentLineup()` returns the same `Lineup` instance `apply_lineup` mutates
(`getCurrentLineupTeam().getLineup()`), so the composition is sound. Description is explicit that
this leaves the machine and overwrites whatever order is set in Hattrick for that match. Returns
`{ status:"ok", matchId, response:<hattrick confirmation text> }`. Fires directly.

### 4.3 `MatchTools` — challenges

```
list_challenges       { weekend: bool (default false) }
  → { outgoing:[{trainingMatchId,opponentTeamId,opponentName,matchTime,friendlyType,arenaName,isAgreed}],
      incoming:[ ... same shape ... ] }

arrange_challenge     { opponentTeamId: int (required),
                        friendlyType:   int 0|1 (default 0),
                        location:       "home"|"away"|"neutral" (default "home"),
                        neutralArenaId: int (required iff location=neutral),
                        weekend:        bool (default false) }
  → { status:"ok", note:"challenge sent; poll list_challenges for the opponent's response" }

respond_to_challenge  { trainingMatchId: int (required),
                        action: "accept"|"decline"|"withdraw" (required) }
  → { status:"ok", action, trainingMatchId }
```

`list_challenges` exists because `accept` / `decline` / `withdraw` need a `trainingMatchId` that
only comes from the challenges file. `location` maps to `matchPlace` 0/1/2. All fire directly.

### 4.4 `TransferTools.place_bid` — two-step

```
place_bid  { playerId:     int  (required),
             bidAmount:    long (required in step 1),
             maxBidAmount: long (optional autobid ceiling),
             confirmToken: string (required in step 2, forbidden in step 1) }
```

**Step 1** (`confirmToken` absent): does **not** call CHPP and does not spend cap. Reads the player
from `HOVerwaltung` (if not present, tells the caller to `search_transfers` for him first). Returns
a preview plus a token:

```
{ status:"confirm_required",
  player:  { id, name, ageYears, ... },
  bid:     { bidAmount, maxBidAmount },
  confirmToken: "<opaque>",
  expiresInSeconds: 120,
  note: "Call place_bid again with this confirmToken (and the same playerId) to place the bid." }
```

The token is `UUID` → `{playerId, bidAmount, maxBidAmount, createdAt}` in a static
`Map<String,PendingBid>` on `TransferTools`, single-use, 120 s TTL, swept on each call. Step 2
re-derives the amounts from the pending entry, ignoring any re-sent, so the confirmed bid is exactly
the previewed bid.

**Step 2** (`confirmToken` present): validates the token (exists, not expired, `playerId` matches),
removes it, calls `ChppWriteService.placeBidForAgent`, returns
`{ status:"ok", player:{id,name}, highestBid, deadline, note }`. An invalid / expired token fails
with "start again with a step-1 place_bid call".

This is the only tool with a gate; the product decision put the others at fire-directly.

## 5. OAuth scopes (HO-internal)

`OAuthDialog.java:54` — one line, plus the adjacent comment:

```java
scopes = "&scope=" + "set_matchorder,manage_youthplayers,set_training,manage_challenges,place_bid";
```

That is the whole change. The fresh-install / re-authorization flow already requests whatever this
string holds, so a token authorized after this ships carries the new scopes; a token authorized
before it does not, and per the non-goals this design does not handle that case.

New per-session cap parameter, following `mcpSearchesPerSession` exactly:

- `UserParameter.mcpChppWritesPerSession` (default e.g. 20) — declared at `UserParameter.java:117`
  area, serialized in the `map.put` block (~`:451`), read back in the parse block (~`:701`).
- `McpServerPanel` — a `writeCap` field next to `searchCap` / `resolveCap`, written to both
  `UserParameter.instance()` and `.temp()` in `saveFields()`.

`docs/chpp_files.md`: mark `challenges` used-version `1.6` (was `-`) and note the write actionTypes
on `training`, `playerdetails`, `challenges`.

## 6. Error handling

- CHPP `<error>` → `getCHPPWebFile` throws `RuntimeException(sError)`; `ChppWriteService.fetch`
  catches it (and the blank-response case, via `Connector.instance().lastError()`) and throws
  `ChppWriteException`. The tool layer catches that and wraps in `McpToolException`, Hattrick text
  intact.
- Missing scope: not special-cased — it arrives as one of the above (§1.3).
- Cap exhausted: `ChppWriteException` naming the Options → MCP server panel, like the search cap.
- `arrange_challenge` `location=neutral` with no `neutralArenaId`: rejected in `ChppWriteService`
  before the call.
- `set_training` out-of-range: rejected in the tool before the call.
- Bad / expired `confirmToken`: rejected in the tool, no call, no cap spend.
- `MatchType.getById(matchType)` null in `send_match_order`: `McpToolException` naming
  `get_upcoming_matches` as the source of valid ids.

## 7. Testing

JUnit 5 + AssertJ, no live CHPP, no Swing. Templates named per family:

- **`ChppWriteServiceTest`** (template: `TransferSearchServiceTest`) — subclass `ChppWriteService`,
  override each `fetch<Family>` to return a canned XML string. Asserts: cap enforcement and the
  `writesThisSession` increment (and that `list_challenges` / bid step 1 do **not** increment);
  `<TrainingSet>false>` → `ChppWriteException`; blank response → `ChppWriteException` carrying
  `lastError()`; `arrange` neutral-without-arena rejection; `verb` whitelist.
- **`Connector` URL builders** (`setTrainingUrl`, `challengesUrl`, `placeBidUrl`) — package-private,
  asserted directly: file, version, actionType, every parameter, `neutralArenaId` present iff
  neutral. The wire-format seam.
- **`XMLChallengesParserTest`** (template: `XMLTransferSearchParserTest`) — a captured `challenges`
  `view` response fixture → the outgoing / incoming record lists, including the lowercase-`d`
  `MatchTime` / `IsAgreed` casing quirks noted in §1.6.
- **`TrainingToolsTest`** — range validation both bounds; committed-values echo shape.
- **`LineupToolsTest`** — `send_match_order` rejects an unknown `matchType`; calls
  `ChppWriteService.sendMatchOrderForAgent` (spy on an overridden service instance).
- **`MatchToolsTest`** — `location` → `matchPlace` mapping; error passthrough from the service.
- **`TransferToolsTest`** — step 1 returns a token and places nothing; step 2 with a good token
  places exactly the previewed amounts; expired token rejected; token is single-use; re-sent
  amounts in step 2 are ignored in favour of the pending entry.

## 8. Files touched

| File | Change |
|---|---|
| `core/net/login/OAuthDialog.java` | +3 scopes in the hard-coded string, comment |
| `core/net/Connector.java` | `setTraining` + `setTrainingUrl`; `challenges` + `challengesUrl`; `placeBid` + `placeBidUrl`; `uploadMatchOrder` gets a `showErrorMessage` overload |
| `core/net/ChppWriteService.java` | **new** — service + `ChppWriteException`, `*ForAgent` methods, `fetch` seam, session cap |
| `core/file/xml/XMLChallengesParser.java` | **new** — `challenges` `view` → records |
| `core/model/UserParameter.java` | `mcpChppWritesPerSession` (declare + serialize + parse) |
| `core/option/McpServerPanel.java` | `writeCap` field, wired in `saveFields()` |
| `core/mcp/McpJson.java` | `reqLong`, `optLong` |
| `core/mcp/TrainingTools.java` | `set_training` tool |
| `core/mcp/LineupTools.java` | `send_match_order` tool |
| `core/mcp/MatchTools.java` | `list_challenges`, `arrange_challenge`, `respond_to_challenge` tools |
| `core/mcp/TransferTools.java` | `place_bid` tool + pending-bid map |
| `docs/chpp_files.md` | challenges used-version + write actionType notes |
| `src/test/java/core/net/ChppWriteServiceTest.java`, `src/test/java/core/file/xml/XMLChallengesParserTest.java`, `src/test/java/core/mcp/*` | tests per §7 |

No `McpHttpServer` change — `registerAllTools()` already calls each `*Tools.tools()`, and the new
tools are added to existing tool classes.
