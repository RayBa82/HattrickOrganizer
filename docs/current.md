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
- Measured 2026-10-11 on a copy of the dev DB (headless, default arguments, local tools only):
  over budget are `get_squad` 31k, `list_scout` 20k (even paged), `get_player_history` 22k
  (`includeSnapshots` 766k), `get_series` 11.4k, `get_training` 11.1k. Under: `get_team` 1k,
  `get_finances` 2.3k, `get_team_transfers` 5.8k, `get_lineup` 2k, `get_matches` 2.8k,
  `simulate_lineup` 0.4k, `get_training wholeSquadWeek` 6.3k, `sinceLastTraining` 1.6k.
  `includeTraining` adds ~2.3k at its 4-week default, ~17k over 26 weeks. Enforcing the
  envelope first would turn the five over-budget defaults into hints on the live server, so the
  defaults have to shrink in the same change. The CHPP-backed tools were not measured.

## MCP requests from the hattrick session (2026-10-11)

Six tool requests, all on `lineup-tv-view`, unit-tested (`core.mcp.*` green), pushed with
`./gradlew dockerPush`; the server picks them up with `docker compose pull && docker compose up -d`.
Not yet run against live CHPP: the send_match_order read-back can still report differences
Hattrick normalises away (team orders and penalty takers are untested).

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
6. "All training data per player reachable": `get_player_history includeTraining`
   adds the stored training weeks (TRAININGS: type, intensity, stamina share, coach/assistant
   levels) and per player the minutes each week banked, rebuilt from stored lineups like
   currentWeek; `weeks` defaults to 4 with it to stay inside the budget. includeEvents now
   covers up to 30 players, events cut to the `weeks` window.
   Not available anywhere: Hattrick keeps no per-player training-minutes history and no past
   training settings beyond the last week (training.xml LastTraining*); sub-skills are HO's
   estimate, never Hattrick data; minutes exist only for current squad players and only for
   matches whose lineup HO downloaded. Not yet exposed: training.xml SpecialTraining (stamina
   special training, at most 3 players) - HO does not parse it.

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
