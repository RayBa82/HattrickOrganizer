# Current work

Only the last one or two pieces of work live here. Older notes are dropped rather than
accumulated; git keeps them (`git log -p -- docs/current.md`; the full history file as of
2026-09-26 is `git show f5c51d0d:docs/current.md`).

## Next up: enforce the MCP token budget

The rule is in AGENTS.md, "MCP server". Every tool response must fit 10,000 characters of
serialized JSON, all or nothing. Over budget, the tool returns only a `truncated` envelope with a
hint naming its own paging/filter arguments. The rule is documented but not implemented; the user
asked to continue with it next session. The plan and the choke point are under "Open: MCP token
budget" at the end of the section below.

## MCP trim: 40 tools down to 30 (2026-09-26)

Asked by the hattrick repo's consumer session and approved by the user. Every read stayed a read and
every write kept its own tool; no field was dropped.

| Removed | Now |
|---|---|
| `get_opponent_ratings`, `get_opponent_squad` | `scout_opponent(include=[ratings,squad])`, default both |
| `search_transfers_batch` | `search_transfers(queries[])`; without `queries` the single-search shape is unchanged |
| `get_match_events` | `get_match(includeEvents)` → `events` |
| `get_player` | `get_squad(playerIds[], includeTransfers)`; an id not in the squad is an error |
| `get_training_events` | `get_player_history(includeEvents)` → per player `trainingEvents`, at most `MAX_EVENT_PLAYERS` = 5 ids |
| `check_challengeable` | `list_challenges(checkTeamIds[1-20])` → `challengeable` |
| `get_upcoming_matches`, `get_league_table` | `get_series`: `matches`, `otherUpcoming`, `nextCupMatch`, `seriesName`, `leagueLevelUnitId`, `season`, `table` (null + `tableNote` when the series is not downloaded) |
| `backfill_character` | gone; the same pass still runs after every team download |
| `get_rating_calibration` | unregistered; `CalibrationSummary` and the `RATING_CALIBRATION` rows stay, re-register in `MatchTools` to use it |

`place_bid` stays even though it is Supporter-only (CHPP `playerdetails` placeBid); `send_match_order`
is not Supporter-gated at all, it needs only the `set_matchorder` scope.

Follow-ups from the live test:

- `get_series.matches` showed a 2024 series before a fresh download. It (and `get_upcoming_matches`
  before it) read `HOModel.getFixtures()` = `SpielplanTable.getLatestSpielplan()`, which is
  `ORDER BY FetchDate DESC LIMIT 1` - the plan fetched last, not the current one. `get_series` now
  takes table and fixtures from `getSpielplan(leagueLevelUnitId, season)`. `HOModel.getFixtures()`
  now does the same (`loadCurrentFixtures`, falling back to the fetched-last plan only without
  XtraData/Basics), which fixes `OnlineWorker`'s played-since-last-download diff, and the Series
  tab's season box defaults to that plan instead of whichever series sorts first in the season.
- `list_scout` returned ~260k chars for 455 entries. It now pages (`limit` 50 / max 200, `offset`),
  filters (`onlyOpen`), and returns compact market rows unless `full`.
- Down to 29: `arrange_challenge` + `respond_to_challenge` became `manage_challenge(action=challenge|
  accept|decline|withdraw)`, each action's arguments checked before the write. `list_scout` gained
  local filters (`skills[{skill,min?,max?}]` ANDed with no span limit, `ageMin`, `ageMax`,
  `specialty`) applied before paging; compact rows add age, specialty and every filtered skill.
- `list_scout.onlyOpen` means still on the market (no sale recorded and deadline not passed), not
  just "no sale recorded". `add_to_scout` writes the character it fetches onto the player's
  TRANSFERLISTING rows too (`storeTransferListingCharacter`), no extra CHPP call.
- `estimate_market_value`:
  - The live search no longer filters on the target's specialty (`comparableQuery`). It used to,
    so every call stored only same-specialty listings and skewed the corpus. The soft specialty
    weight stays.
  - Resolved sales are never blended with asks. With 3 or more sales, `basis: "sales"` and
    `estimate` works as before.
  - Below 3 sales, `estimate` is null, `resolvedSales` lists each sale, and the ask/bid figure
    moves to a labelled `listingsEstimate`. `basis` is `thin_sales_plus_listings`, or `listings`
    with no sales at all.
  - `fallbackClimb` is capped at 1.5x.
  - Set pieces still doesn't change the number; the user's view is one specialist taker per team.
    `setPiecesNote` warns when the target's SP is 12 or more, or when comparables sit 4+ levels
    away. Both thresholds are placeholders. A profile may pass `setPieces`.
- `estimate_market_value(profile)` rejects an outfield profile with fewer than three positional
  skills (`requireThirdSkill`; an explicit 0 counts, keeper profiles are exempt). The check runs
  before the record-a-sale path.
  - Treated as 0, an omitted third skill floored every weight and turned the median unweighted.
  - Treated as unknown, it let all-rounders with a strong third skill count fully. Gkatsilakis
    went from EUR 787k with winger 6 to EUR 1.08M without it. Either way the estimate inflates.
- Asks/bids below 10,000 SEK (~EUR 1,000, a guess) are dropped from the listings figure.
- **Open: MCP token budget (rule in AGENTS.md, "MCP server").** Every tool response must fit a
  per-call budget, all or nothing. Over budget, the tool returns only a `truncated` envelope with
  a hint naming its own paging/filter arguments.
  - Not enforced in code yet. Today only `list_scout` pages; other tools can still return
    unbounded payloads (`list_scout` was ~260k chars before paging).
  - The natural choke point is `McpHttpServer.toolsCall`, where every result is serialized
    (`GSON.toJson(tool.handler().call(arguments))`). Measure there and swap in the envelope; each
    tool supplies its own hint.
  - Budget decided by the user: 10,000 characters of serialized JSON per response. Next, once
    implementation is greenlit: enforce it at that choke point, then audit every tool's defaults
    against it, starting with the list-shaped ones.
- `apply_lineup` / `simulate_lineup` reject a `setPiecesTakerId` who plays roleId 100
  (`LineupTools.rejectKeeperAsSetPiecesTaker`, called before the live lineup is touched).
- Who takes over from a departed set-pieces taker, first or second on the penalty list? The DB
  can't tell. Event 81 (NEW_SET_PIECES_TAKER) is stored with the old taker in SPIELERID and the
  new one in GEHILFEID. HO never stores the penalty-taker order for a played match, though:
  MATCHLINEUPPLAYER has no roleId 22-32 rows at all, and the HRF table keeps only an id and a date.
  To settle it, snapshot the match orders (roleIds 22-32) before each match.
