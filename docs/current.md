# Current work

Only the last one or two pieces of work live here. Older notes are dropped rather than
accumulated; git keeps them (`git log -p -- docs/current.md`; the full history file as of
2026-09-26 is `git show f5c51d0d:docs/current.md`).

## Next up: enforce the MCP token budget

The rule is in AGENTS.md, "MCP server". Every tool response must fit 10,000 characters of
serialized JSON, all or nothing. Over budget, the tool returns only a `truncated` envelope with a
hint naming its own paging/filter arguments. The rule is documented but not implemented.

- The natural choke point is `McpHttpServer.toolsCall`, where every result is serialized
  (`GSON.toJson(tool.handler().call(arguments))`). Measure there and swap in the envelope; each
  tool supplies its own hint.
- Then audit every tool's defaults against it, starting with the list-shaped ones. Today only
  `list_scout` pages (it was ~260k chars before paging).

## MCP requests from the hattrick session (2026-10-11)

Five tool requests, all on `lineup-tv-view`, unit-tested (`core.mcp.*` green), not yet run
against live CHPP or deployed to the server (needs `./gradlew dockerPush` + `docker compose pull`).

1. `get_match_order(matchId, matchType)` reads the order Hattrick holds (matchorders view, through
   `OnlineWorker.lineupFromOrder`, the download's own HRF route) in send_match_order's echo shape.
   `send_match_order` re-reads after sending: `verified` true/false/`hidden`/`failed`, plus
   `differences`. Trigger: the 2026-10-11 friendly (771815754) returned ok twice but never showed.
2. `download_team`: `lineupReplaced` was null whenever no lineup was stored for the next match
   before the download - exactly when Hattrick's default order (3-4-2) came in. Now true in that
   case, with the new lineup as `storedLineup`; snapshots carry `matchId`.
3. `get_player_history sinceLastTraining`: per player, newest snapshot before the last training
   update against the newest overall; changed players only, `unconfirmed` for missing sides.
4. `simulate_lineup` takes matchId/weather without positions: scores a copy of the live lineup.
5. `get_training wholeSquadWeek`: every squad player in `currentWeek.players`, ignoring playerIds.

Open: request 6 (multi-week training history per player: minutes, maxed, resulting change) -
scope not yet confirmed by the user.

## Headless finances (2026-10-03)

Reported by the hattrick session: `get_finances` after a headless `download_team` was wrong. Three
bugs in the economy path, all also hitting desktop HO, fixed and verified on the live server
(HRF 159: every cost line adds up to `costsSum` for both weeks):

- `AmountOfMoney.parse` returned 0 while no currency formatter existed yet - always so headless,
  so every ECONOMY field was stored as 0 (`adc77098`).
- `HRFStringBuilder` wrote `LastCostsStaff` under the pre-v500 key `lastCostsPersonal` (`96423bb3`).
- `XMLEconomyParser` never read CHPP's `CostsTemporary`/`LastCostsTemporary`; the HRF stored bought
  players + arena building in their place, which `FinancePanel` then showed twice (`fd6a672a`).
  `get_finances` now reports `costsTemporary`.

`HRFStringBuilderTest` round-trips a real economy.xml through parser, HRF and `Economy`. ECONOMY rows
from before these fixes keep their wrong values; only new downloads are right.
