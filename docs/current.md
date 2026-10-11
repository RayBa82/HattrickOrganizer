# Current work

Only the last one or two pieces of work live here. Older notes are dropped rather than
accumulated; git keeps them (`git log -p -- docs/current.md`; the full history file as of
2026-09-26 is `git show f5c51d0d:docs/current.md`).

## MCP token budget enforced (2026-10-11)

AGENTS.md, "MCP server", has the rule as built: `McpBudget.fit` in `toolsCall` sheds a tool's
declared optional sections first (`droppedForBudget`), and only a core still over 10,000
characters becomes the `{truncated, wouldBeChars, hint}` envelope. The user chose that over
AGENTS.md's earlier all-or-nothing, at the hattrick session's request: squad availability and
the current training week must always arrive.

Defaults reshaped with the hattrick session (field names kept):
- `get_squad`: compact rows, everything but positionRatings squad-wide in one call (user
  request via hattrick): skills as an array in `skillOrder` (1 decimal), `character` in
  `characterOrder`, `specialty` as its number named once in `specialtyNames`, loyalty,
  penaltyStrength (1 decimal); `motherclub`/`transferListed` only when true, `suspended` gone
  (`available` false without injury). `include` takes only positionRatings (old part names
  still accepted); up to 5 `playerIds` get it unasked. 30 players 9.3k, ~300 a row; over
  budget positionRatings, tsi+wage, penaltyStrength, loyalty, character drop in that order.
  `only: "positionRatings"` returns just the slot matrix for set-lineup (hattrick's choice):
  `slotOrder` once, per player `{playerId, r:[14, 1 decimal]}`, unavailable players too;
  30 players 2.7k.
- `get_player_history`: `latestSkills` added; `derived` by default for up to 2 players,
  `coverage` up to 5 (`includeDerived`/`includeCoverage` override). Above 2 players a compact
  `screen` (levelsPerHtYear, rateBasis, weeksSinceLastLevelUp, daysSinceLastSnapshot, gapCount)
  replaces them for the hattrick trainee screen: 14 players 8.5k complete, 20 keep it, the
  whole 29-man squad drops it - full derived+coverage fit only 8 players a call.
- `get_training`: `weeks` default 1, predictions dated on or before the last update dropped.
- `get_series`: `matches` holds our fixtures; `allTeams` the whole series (fits only with
  `include: ["matches"]`, 9.9k).
- `list_scout`: page 20, compact notes cut at 80 chars (`noteCut`).

Nothing is lost, only paged (checked on the DB copy): `get_squad` pages with `limit`/`offset`
(limit 8 carries every include part, 15 the core with skills); `get_player_history` pages back in
time with `weeks` + `weeksAgo`, one player's snapshots a week at a time (up to 28 a week there,
7.6k - a week with more than ~35 downloads would still drop them); `get_training` takes
`playerIds` for long predictions. Whatever a call drops is named in `droppedForBudget`.

Measured on a copy of the dev DB (29 players, data to late September), default arguments:
`get_squad` 8.1k, `get_player_history` 8.5k, `list_scout` 6.8k, `get_training` 5.7k,
`get_series` 4.2k; all other local tools 0.4-6.3k. The CHPP-backed tools were not measured;
they only pass through the same budget step. Measure with the headless server on a DB copy:
`java -Djava.awt.headless=true -Dho.dbParentFolder=<copy> -cp HO.jar core.HeadlessMain --port 18765`
from `build/install/XXX`, then `tools/call` each tool.
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

