# Current work

Only the last one or two pieces of work live here. Older notes are dropped rather than
accumulated; git keeps them (`git log -p -- docs/current.md`; the full history file as of
2026-09-26 is `git show f5c51d0d:docs/current.md`).

## get_match minutes, estimate_market_value as a download (2026-10-18)

At the hattrick session's request, with the user's decisions.
- `get_match` ourPlayers: `startMinute` (always 0) is gone. Rows now carry `started` and `minutesPlayed`
  (`MatchLineupTeam.getTrainMinutesPlayedInPositions`), plus `cameOn`/`wentOff` from the stored
  substitution orders and `injured`/`bruised`/`yellowCards`/`redCard` from the events, each only
  when it happened. `includeEvents` keeps the per-team counts inline. Every event row (filtered by
  `eventTypes`) goes into a download, always, by the user's call.
- `MatchEvent.isInjured` counts events 95 and 97. The parser never typed them as injuries, so
  training minutes ran to the final whistle for those players. The fix also applies to events
  already stored.
- `estimate_market_value`: the 40-row `comparables` array is gone (a 17y PM6/PS6 profile was
  24k). The answer has `skillGroups`, one line per A/B skill pair (best/second-best positional
  skill: count, sold, weighted median price). The download holds every comparable, untrimmed with
  weightBreakdown and `inSample`, grouped the same way. "Pro Skill" was the user's wording, and
  "A and B skill" is their answer.
- Checked live on match 768523845: wentOff/injured 65 and cameOn 65 are right. `startRoleId` was
  added. The skillGroups median is now the estimate's own method over the group's in-sample rows.
- Follow-ups from the hattrick session, all four requested by the user:
  get_lineup / send_match_order list `unavailablePlayers` (warning only, nothing refused).
  get_squad rows carry `injuredInLastMatch` {matchId, minute} while injuryWeeks is still 0, from
  the newest played match if it is at most a week old. Own sales reach the corpus on every
  download: `updateTeamTransfersIncremental`, the path download_team takes, never called
  `recordLocalSaleObservation`, which is why sales were entered by hand. Sales from before this
  fix are not backfilled. get_team_transfers rows add specialty + skills from HO's snapshot
  around the transfer, declared optional for the budget. `manage_scout action: refresh` re-reads
  ask/bid/deadline with one playerdetails call each (default: open entries) and stores each
  response as a corpus row.
- Not measured on a DB copy yet.
- Transfer search cap: 50 per rolling hour (`TransferSearchService.WINDOW`), not per HO process,
  since the headless container never restarts. User's call after CHPP's "no bot scanning" rule
  was weighed: CHPP documents no numeric limit. A DB still on the old default 20 moves to 50 once
  (`mcpSearchCapRaised`). The options label still says "per session" (POEditor text).
  lineup, so no column), `confounded` reason only when set. `attitude` filters stats and rows.
  20 rows 5.5k, 40 rows were 9.7k on the dev DB copy.
- `backfill` rates stored matches without a row; with `recompute` it redoes all of them.
- DB 1008 adds PRED_TACTIC (the raw tactic rating; level = rating + 1) and REAL_TACTIC
  (Hattrick's tacticSkill). Rows from before stay null until a `backfill` + `recompute`.

Dev DB copy (36 rated matches; 128 others had no snapshot before kick-off): HO overrates our
attack (central -0.78, sd 0.6) and underrates the defence (central +0.49); midfield -0.03.
League is the worst (attack central -1.52, n 3), friendlies nearly neutral. The tactic level
comes out 1.4 too low in league matches (n 3), matching the 2026-10-10 observation (10 vs 12).

## MCP 2026-07-28 and the tasks extension (2026-11-01)

Branch `mcp-tasks`, cut from `master` after `lineup-tv-view` was fast-forwarded into it, then
fast-forwarded into `master` (local, not pushed to GitHub; the image `ho-headless:10.0`/`latest`
is pushed). AGENTS.md, "Protocol", has the design as built.
- `McpHttpServer` serves both eras. Requests with the `_meta` protocol version get the stateless
  2026-07-28 rules: `server/discover`, header checks (`-32020`), version check (`-32022`),
  `resultType`, serverInfo in `_meta`, and cacheable `tools/list`. `initialize` stays legacy
  but no longer echoes an unknown version (it answers 2025-11-25), and `ping` was added.
- Tasks (`McpTasks`, extension `io.modelcontextprotocol/tasks`): `download_team` (without
  taskId), `search_transfers` (without resultId) and `analyze_opponent` answer with a task
  handle when the client declares the extension. `download_team` then runs the download inside
  the task, and its old taskId polling stays for every other client.
- Concurrency changed: tool handlers run one at a time under `toolLock`, and the HTTP pool has 4
  threads so `tasks/get` never waits. A plain call that arrives while a task is running waits
  for it. The old taskId download still runs outside the lock, as before.
- Not done: the legacy experimental tasks (2025-11-25 `tasks/result`/`tasks/list`), SSE,
  `subscriptions/listen`, and `notifications/tasks`.
- Checked live on a DB copy (headless, port 8799): discover, tools/list, get_team modern and
  legacy, header mismatch, unknown task and method. The task path is covered by unit tests only.
  Every task tool spends CHPP requests, so a live run needs the user's go-ahead and a client
  that speaks the extension.
- Next: the aggregate tools in `docs/roadmap.md` ("MCP aggregate tools for the hattrick
  skills"). Item 1 is done: `get_training` `currentWeek.fixtures` now lists tournament and
  ladder matches too (they were missing because `TrainingPerWeek` loads only training types).
  Each row has `matchTypeId` (what `send_match_order` takes), `home`, `trains` and
  `injuriesPossible`.
  The minutes still come from the training matches only. Checked on the DB copy, which has no
  tournament match, so the tournament row is covered by the unit test.
- Item 2 is done: `apply_lineup` answers with `changed` (a diff against the lineup HO held
  before), the ratings and `captainInStartingEleven`; `full` adds the whole lineup.
  `LineupTools.describe` lists only occupied field (100-113) and bench (200-213) slots. The
  captain and set pieces pseudo-roles 17/18 are gone from `positions`; `captainId` and
  `setPiecesTakerId` carry them. Measured on the DB copy: one swap answers with 703 chars
  instead of about 1,950.
- Item 3 is done: `week_brief` in the new `WeekTools`. It reuses `TrainingTools.current`,
  `currentWeek` and `sinceLastTraining` (now package-private). It reads the held orders through
  `LineupTools.heldOrder` and compares them with HO's stored lineup for the match
  (`loadMatchLineupTeam`). Player rows leave defaults out, and `bestPositions` is a roleId ->
  rating map. On the DB copy (29 players, `includeOrders` false) it answers with 6,856 chars,
  against about 21,000 for get_training + get_series + get_squad + sinceLastTraining. The
  order part is unit-tested only: reading it live costs CHPP requests.
- Item 4 is done: `download_team` `keepLineup`, default true in the MCP tool only (the user's
  call; the Download button still replaces). `runDownload` loads HO's next-match lineup before
  the download and stores it back when Hattrick's differs for the same match. When the next match
  changed, Hattrick's order for the new one stays and `lineupNote` says so. The result has
  `lineupReplaced`, `lineupKept`, `lineupMatchId` and `hattrickOrderDiff`
  (`LineupTools.lineupDiff`); `previousLineup` only when replaced, and `storedLineup` is gone.
  Unit-tested only: a download spends CHPP requests.
- Item 5 is done: `level_up_report` in `WeekTools`. `download` auto (default) runs
  `TeamTools.downloadTeamTask` (now package-private, keepLineup true) only when now is at or past
  the snapshot's `nextTrainingDate`; always / never force it. It answers with a cut download
  summary (`DOWNLOAD_KEYS`), the snapshot date and `compactLevelUps(sinceLastTraining)`.
  Measured on the DB copy with `download: never`: 775 chars for 29 players. The download path
  is unit-tested only.
- Item 7 is done before 6: `estimate_market_value` `summary: true` keeps playerId/name, the
  figures, `sampleSize`, the top 3 `skillGroups` (+ `skillGroupCount`) and the download, and
  drops the notes and the player block. Every valuation now has `middleHalf` (unweighted
  quartiles of the sorted sample) beside `range`, which one stray sale stretches (1,000 to
  377,400 on the DB copy). Measured: 2,252 chars by default, about 420 with summary.
- Item 6 is built: `screen_market` in the new `MarketTools`, with the hattrick session's rules
  (docs/transfer-searches.md over there) as arguments. `queries` are search_transfers searches;
  `specialty` may be a list (one CHPP search each), `label` and `screen` ({skill: min}) are
  stripped before the search. Searches beyond `TransferSearchService.remainingSearches()` come
  back as `queued`. Filters: closed, injured, specialty whitelist, screen, wage after
  `foreignWageFactor` (nativeCountryId against `XtraData.getCountryId`). Ranked by price +
  wage x wageWeeks, cut to `top`, then per row card risk (`TransferTools.observeMarketPlayer`,
  one playerdetails each, `characterTop`), a local `valueForScreen` estimate and cost/fundsAfter
  against `buyCheck` (squad vs cap, expectedCash - reservedCash), and re-ranked with the
  estimate. Rows are arrays in `COLUMNS` order. Unit-tested only: every run spends searches.
  Foreign bonus: the hattrick session found hit Salary and playerdetails Salary equal for the
  same foreigners, so neither adds the bonus twice. Whether Salary is the base wage is still
  unproven. `foreignWageFactor` stays 1.2 (the cautious side) until a real foreign purchase
  shows get_squad's wage against the hit's Salary. "Ours" is `XtraData.getCountryId`, the
  team's CountryID (3 for Germany), the same id space as nativeCountryId.
- First real run (hattrick session): figures checked by hand. Follow-ups built:
  screen_market columns `cards` (2 yellows warn, 3 suspended) and `estimateBasis`, a
  `minSample` warning (default 15), `searchesRun`/`searchCap`/`searchCapLeft`, and every
  `filteredOut` key even at 0. week_brief fixtures run oldest first (get_training too) and
  carry `derby`/`neutral`: HO rates every derby as awayDerby, home or not, so `home: true`
  next to apply_lineup's awayDerby is right. Player rows add `age`/`ageDays` and
  `trainingSpeed` (HO's plan for next week). 8,126 chars for 29 players without orders.
- Second run: week_brief `includeLevelUps` (default true). A played derby lost `isDerby` on
  every later matches download: `downloadMatchData` set the region from the match details only
  for upcoming matches, so the derby check compared against no region. `OnlineWorker.copyRegion`
  now sets it for played matches too. Stored rows heal when the match is loaded again; rating
  calibration rows of past derbies keep the location they were stored with.
