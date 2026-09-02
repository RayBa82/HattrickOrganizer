# CHPP write operations over MCP — design

Status: approved for planning, 2026-09-02. Reviewed against the code 2026-09-02. Branch `macos-fixes`.
Builds on `2026-08-30-mcp-server-design.md`, whose non-goal "uploading match orders to Hattrick,
placing bids, or any other write that leaves the machine" this design deliberately reverses.

Lets an MCP client drive the CHPP write actions HO can perform on behalf of a team: set the senior
training, send the match order for an upcoming game, arrange / accept / decline / withdraw friendly
challenges, and place a transfer bid. Every one of these leaves the machine and changes state in
Hattrick.

## Goals

- Expose over MCP the five CHPP write families: `set_training`, match-order upload, challenges
  (all four verbs), and transfer bids.
- Reuse HO's existing CHPP layering — `Connector` (raw call) → `OnlineWorker` (orchestration) →
  caller — rather than letting tools reach the wire directly.
- Keep the transfer bid behind a two-step confirm so a single stray tool call cannot commit money.
- Add the three missing OAuth scopes and make the existing re-authorization path carry them.

## Non-goals

- Bid-tracking maintenance (`deleteAllFinished`, `ignoreTransfer`). Not asked for; marginal value.
- A standalone "is this team challengeable" pre-check tool. Folded into `arrange_challenge`'s error.
- Youth-team training, NT match orders, or any write not in the list above.
- A confirmation gate on the non-bid writes. Per the product decision (2026-09-02) they fire
  directly, matching the existing `apply_lineup` / `set_player_group` writers.
- New DB tables or a schema migration. Nothing here persists locally beyond what a normal
  download already stores.

## 1. Constraints discovered

Verified against the code on 2026-09-02.

### 1.1 HO performs exactly one CHPP write today

`Connector.uploadMatchOrder` (`Connector.java:314`) → `OnlineWorker.uploadMatchOrder` →
`MatchAndLineupSelectionPanel:370`. It POSTs to `?file=matchorders&version=3.0&actionType=setmatchorder`
with the lineup JSON in the body and passes scope `set_matchorder` to
`postWebFileWithBodyParameters`. `set_training`, `manage_challenges` and `place_bid` have **no code
anywhere in HO** — no `Connector` method, no `OnlineWorker` method, no parser.

### 1.2 The OAuth scope string is hard-coded in one place

`OAuthDialog.java:54`:

```java
// Hard code set_matchorder due to authorization woes.
// scope manage_youthplayers is needed to use action type unlockskills
scopes = "&scope=" + "set_matchorder,manage_youthplayers";
```

The `scope` constructor parameter (`OAuthDialog(mainFrame, service, scope)`) is **dead** — it is
never read; `obtainUserURL()` appends the hard-coded `scopes` field to the authorization URL. Both
call sites in `Connector` (`:668` passing `""`, `:790` passing the real scope) are equally ignored.
So adding scopes is a one-line edit to that string, and only there.

### 1.3 A missing scope surfaces as HTTP 401, which the GET path already recovers from

`getCHPPWebFile` (`Connector.java:618`) runs a `while (tryAgain)` loop: on `401` it constructs
`new OAuthDialog(mainFrame, m_OAService, "")`, shows it, stores the new access token and retries the
same request. Because the OAuthDialog always authorizes for the full (now expanded) scope string, an
existing user whose token predates this change will, on their first `set_training` / challenge / bid
call, get a 401, be walked through re-authorization, and have the call succeed on retry — no new UI.

**This is the one assumption to verify against live CHPP during implementation.** If CHPP instead
answers a scope-less write with `HTTP 200` + an `<error>` block (error code, not 401), the retry
loop does not fire and the call throws `RuntimeException(sError)`. Fallback (§6): the `OnlineWorker`
methods catch that, recognize the scope-error text/code, and rethrow a message that tells the user to
re-authorize HO (Options → CHPP connection, or delete `users.json`'s token). The MCP tool turns that
into an `McpToolException`.

`postWebFileWithBodyParameters` (used only by match-order upload) has the **same** 401→OAuthDialog
recovery (`Connector.java:793`), so match-order upload needs no scope change — `set_matchorder` is
already held.

### 1.4 Tool handlers run off the EDT; CHPP calls from them are fine, UI touches are not

`McpHttpServer` handles each request on an HTTP thread. `Connector`'s CHPP methods are network I/O
and safe there. The trap is the recovery dialog: `getCHPPWebFile` pops a modal `OAuthDialog` on that
thread. It already does this today for reads and `MatchLineupSelectionPanel` uploads, and
`LineupTools.onEdt` shows the project's accepted pattern for the reverse case. No new mechanism —
but the tools must not themselves call Swing. Re-download-after-write (§3.1) must route through the
same `onEdt` helper the lineup tools use if it touches `HOVerwaltung`.

### 1.5 Current training values are read from `Team`, not a `Training` object

`HOModel.getTraining()` (`HOModel.java:704`) *constructs* a `TrainingPerWeek` each call from
`getTeam().getTrainingsArtAsInt()`, `getTrainingslevel()`, `getStaminaTrainingPart()`,
`getClub().getCoTrainer()` and `getTrainer().getCoachSkill()`. There is no setter path and no
partial "download training only" call — training arrives with the full HRF/team download. So after a
successful `setTraining` CHPP call the local snapshot is stale until the next full download. §3.1
decides what the tool returns in the meantime.

### 1.6 CHPP request formats

Taken from a current, live-verified reference implementation (`github.com/lucianoq/hattrick`,
`chpp/*` and `api/*`, checked 2026-09-02; its challenges structs carry live-response casing notes
dated 2026-08-27). All four are `GET` with query parameters — only match-order upload is a POST.

| Family | file | version | actionType | Parameters |
|---|---|---|---|---|
| set training | `training` | 2.1 (HO's current) | `setTraining` | `teamId`, `trainingType` (int), `trainingLevel` (0–100), `trainingLevelStamina` (5–100) |
| list challenges | `challenges` | 1.6 | `view` | `isWeekendFriendly=1` (optional) |
| arrange challenge | `challenges` | 1.6 | `challenge` | `opponentTeamId`, `matchType` (0 normal / 1 cup rules), `matchPlace` (0 home / 1 away / 2 neutral), `neutralArenaId` (only when `matchPlace=2`), `isWeekendFriendly=1` (optional) |
| accept / decline / withdraw | `challenges` | 1.6 | `accept` \| `decline` \| `withdraw` | `trainingMatchId` |
| place bid | `playerdetails` | 3.0 (HO's current) | `placeBid` | `playerID`, `teamId`, `bidAmount` (int), `maxBidAmount` (int, optional autobid ceiling) |

`training` `setTraining` response nests the new state under `<View>` and carries
`<TrainingSet>true|false</TrainingSet>`. `challenges` `view` returns `<ChallengesByMe><Challenge>`
and `<OffersByOthers><Offer>` lists, each with `<TrainingMatchID>`, `<MatchTime>` (datetime string,
*not* the int the doc claims), `<FriendlyType>`, `<Opponent>` (team, arena, league, `<IsAgreed>`).
`placeBid` returns the updated `<Player>` block from `playerdetails`.

`FriendlyType`: 0 normal rules, 1 cup rules, 12 NT friendly (not offered by our tool).
`MatchPlace`: 0 home, 1 away, 2 neutral.

## 2. Layering

For each operation:

```
core.mcp.*Tools          thin tool: validate args, call OnlineWorker, shape JSON result
core.net.OnlineWorker     static method: call Connector, translate errors, trigger local refresh
core.net.Connector        build URL (package-private, testable), getCHPPWebFile / post, check <error>
core.net.login.OAuthDialog  +3 scopes in the hard-coded string
```

Rejected alternatives: tools calling `Connector` directly (skips `OnlineWorker`, the documented
download-orchestration surface, and the post-write refresh); a generic `chpp_request` passthrough
tool (no validation, no typing, hands raw CHPP semantics to the model — against the grain of the
existing typed, well-described tools).

## 3. Connector + OnlineWorker

### 3.1 Training — `Connector.setTraining`, `OnlineWorker.setTraining`

```java
// Connector — mirrors uploadMatchOrder's structure, but GET
String setTrainingUrl(int teamId, int trainingType, int trainingLevel, int staminaLevel);  // package-private, testable
String setTraining(int teamId, int trainingType, int trainingLevel, int staminaLevel);     // getCHPPWebFile(url)
```

`OnlineWorker.setTraining(int trainingType, int intensity, int staminaShare)`:

- Team id from `HOVerwaltung.instance().getModel().getBasics().getTeamId()`.
- Call `Connector.setTraining`. `getCHPPWebFile` throws `RuntimeException` on any `<error>`.
- Parse `<TrainingSet>`; if `false`, throw with the response text.
- **Local refresh:** do *not* attempt a partial update — §1.5 says training rides the full
  download. Return the values Hattrick echoed under `<View>` (`<TrainingType>`, `<TrainingLevel>`,
  `<StaminaTrainingPart>`) so the caller sees the committed state, and let the tool flag that HO's
  snapshot is stale until the next team download.

### 3.2 Match order — no new Connector code

`OnlineWorker.uploadMatchOrder(int matchId, MatchType matchType, Lineup lineup)` already exists and
already carries `set_matchorder`. The tool (§4.2) calls it directly with the current lineup.

### 3.3 Challenges — `Connector.listChallenges` / `arrangeChallenge` / `respondToChallenge`

```java
String challengesUrl(String actionType, Map<String,String> params);  // package-private
String listChallenges(boolean weekend);
String arrangeChallenge(int opponentTeamId, int friendlyType, int matchPlace, Integer neutralArenaId, boolean weekend);
String respondToChallenge(String verb, long trainingMatchId);  // verb in {accept, decline, withdraw}
```

`OnlineWorker`:

- `listChallenges(boolean weekend)` → parse into a small record list
  `{trainingMatchId, matchTime, friendlyType, opponentTeamId, opponentName, arenaName, isAgreed,
  direction}` where `direction` is `outgoing` (ChallengesByMe) or `incoming` (OffersByOthers).
- `arrangeChallenge(...)` / `respondToChallenge(...)` → success = no `<error>`; return the raw
  response for the tool to confirm from. No local refresh — challenges are not in `HOModel`.
- Guard in `arrangeChallenge`: `matchPlace == 2` requires a non-null `neutralArenaId`; throw a
  clear error otherwise rather than letting CHPP reject it opaquely.

### 3.4 Bid — `Connector.placeBid`, `OnlineWorker.placeBid`

```java
String placeBidUrl(int playerId, int teamId, long bidAmount, Long maxBidAmount);  // package-private
String placeBid(int playerId, int teamId, long bidAmount, Long maxBidAmount);
```

`OnlineWorker.placeBid(int playerId, long bidAmount, Long maxBidAmount)`:

- Team id from the model.
- Call `Connector.placeBid`; `<error>` throws (`BidFailed` is CHPP error code 71 — surface its text).
- Parse the returned `<Player>` for the post-bid `<HighestBid>` / `<Deadline>` / bidder team and
  return those.
- No local write. If the player is one of ours on the transfer list this changes nothing locally;
  if it is a target, HO learns the outcome on the next relevant download.

## 4. MCP tools

Money is `long` throughout — add `McpJson.reqLong` / `McpJson.optLong` (Hattrick prices fit in
`int` today, but `long` costs nothing and matches `TransferListing`).

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

Sends **the lineup currently set in HO** for that match — `OnlineWorker.uploadMatchOrder(matchId,
MatchType.getById(matchType), model.getCurrentLineup())`. No lineup argument: compose with
`apply_lineup` first. This keeps one lineup schema in the codebase and makes the two-step explicit
(build, review, then send). Reads the current lineup on the EDT via the existing `onEdt` helper.
Description is explicit that this leaves the machine and overwrites whatever order is set in
Hattrick for that match. Returns `{ status:"ok", matchId, response:<hattrick confirmation text> }`.
Fires directly.

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

**Step 1** (`confirmToken` absent): does **not** call CHPP write. Reads the player from
`HOVerwaltung` (and, if not present, does nothing network-side — tells the caller to look him up
via `search_transfers` first). Returns a preview plus a token:

```
{ status:"confirm_required",
  player:  { id, name, ageYears, ... },
  bid:     { bidAmount, maxBidAmount },
  confirmToken: "<opaque>",
  expiresInSeconds: 120,
  note: "Call place_bid again with this confirmToken (and the same playerId) to place the bid." }
```

The token is `UUID` → `{playerId, bidAmount, maxBidAmount, createdAt}` in a static
`Map<String,PendingBid>` on `TransferTools`, single-use, 120 s TTL, swept on each call. It binds the
exact amounts: step 2 re-derives them from the pending entry, ignoring any amounts re-sent, so the
confirmed bid is exactly the previewed bid.

**Step 2** (`confirmToken` present): validates the token (exists, not expired, `playerId` matches),
removes it, calls `OnlineWorker.placeBid`, returns
`{ status:"ok", player:{id,name}, highestBid, deadline, note }`. An invalid/expired token fails with
"start again with a step-1 place_bid call".

This is the only tool with a gate; the product decision put the others at fire-directly.

## 5. OAuth scopes

`OAuthDialog.java:54`:

```java
scopes = "&scope=" + "set_matchorder,manage_youthplayers,set_training,manage_challenges,place_bid";
```

Update the adjacent comment. Nothing else changes — §1.2 and §1.3 establish that this string is the
only knob and that the existing 401 retry loop re-authorizes idle tokens on first use.

`docs/chpp_files.md`: bump the "Used Version" note for `challenges` (was `-`, now `1.6`) and note
the new write actionTypes on `training`, `playerdetails`, `challenges`.

## 6. Error handling

- CHPP `<error>` → `getCHPPWebFile` / post path throws `RuntimeException(sError)` (existing).
  `OnlineWorker` methods let `IOException` / `RuntimeException` propagate; the tool layer catches and
  wraps in `McpToolException` with the Hattrick text intact.
- Missing scope: if §1.3's 401 assumption holds, invisible (auto re-auth). If not, the
  `OnlineWorker` method matches the known scope-error signature and throws
  "HO is not authorized for this action in Hattrick — re-authorize via Options → CHPP connection".
- `arrange_challenge` with `location=neutral` and no `neutralArenaId`: rejected in `OnlineWorker`
  before the call.
- `set_training` out-of-range: rejected in the tool before the call.
- Bad `confirmToken`: rejected in the tool, no call.
- `MatchType.getById(matchType)` returning null in `send_match_order`: `McpToolException` naming
  `get_upcoming_matches` as the source of valid ids.

## 7. Testing

Per the existing `core.mcp.*ToolsTest` pattern (JUnit 5 + AssertJ, no live CHPP, no Swing):

- `Connector` URL builders (`setTrainingUrl`, `challengesUrl`, `placeBidUrl`) are package-private and
  asserted directly — file, version, actionType, every parameter, and `neutralArenaId` present iff
  neutral. This is the seam that keeps the wire format under test without the network.
- `TrainingToolsTest`: range validation both bounds; committed-values echo shape.
- `LineupToolsTest`: `send_match_order` rejects an unknown `matchType`; calls `uploadMatchOrder`
  with the current lineup (via a seam / spy on `OnlineWorker`).
- `MatchToolsTest`: `location` → `matchPlace` mapping; `arrange_challenge` neutral-without-arena
  rejection; `respond_to_challenge` verb whitelist.
- `TransferToolsTest`: step-1 returns a token and places nothing; step-2 with a good token places
  exactly the previewed amounts; expired token rejected; token is single-use; re-sent amounts in
  step 2 are ignored in favor of the pending entry.
- Challenges XML parsing: a captured `challenges` `view` response fixture → the outgoing/incoming
  record lists (mirrors how `matchesarchive` fixtures are used in `MatchesArchiveTest`).

## 8. Files touched

| File | Change |
|---|---|
| `core/net/login/OAuthDialog.java` | +3 scopes in the hard-coded string, comment |
| `core/net/Connector.java` | `setTraining` + URL builder; `listChallenges` / `arrangeChallenge` / `respondToChallenge` + `challengesUrl`; `placeBid` + URL builder |
| `core/net/OnlineWorker.java` | `setTraining`, `listChallenges` (+ result record), `arrangeChallenge`, `respondToChallenge`, `placeBid` |
| `core/mcp/McpJson.java` | `reqLong`, `optLong` |
| `core/mcp/TrainingTools.java` | `set_training` tool |
| `core/mcp/LineupTools.java` | `send_match_order` tool |
| `core/mcp/MatchTools.java` | `list_challenges`, `arrange_challenge`, `respond_to_challenge` tools |
| `core/mcp/TransferTools.java` | `place_bid` tool + pending-bid map |
| `docs/chpp_files.md` | challenges used-version + write actionType notes |
| `src/test/java/core/mcp/*` | tests per §7 |

No `McpHttpServer` change — `registerAll` already picks up whatever each `*Tools.tools()` returns.
