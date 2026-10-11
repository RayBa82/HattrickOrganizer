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

## Rating calibration back as an MCP tool (2026-10-11)

`get_rating_calibration` (MatchTools, summary in `core.mcp.CalibrationSummary`) is registered
again, reshaped with the hattrick session, which wants to measure HO's rating bias. The
RATING_CALIBRATION rows have existed since DB 1005 and are written at every match-details
download (`RatingCalibration.calibrateAndStore`); only the tool had been retired in the 40-to-30 trim.
- stats over non-confounded rows with a snapshot at most `maxSnapshotAgeDays` (7) old: n, date
  range, mean/sd of real minus predicted per sector in `sectorOrder`, and the same for the
  tactic level. `includeConfounded`, `matchTypes` filter stats and rows.
- rows: paged (20, max 30), newest first, pred/resid arrays, `tactic` [predicted, real],
  `attitude`, `behaviours` (a char per slot 100-113, read per page row from the stored match
  lineup, so no column), `confounded` reason only when set. `attitude` filters stats and rows.
  20 rows 5.5k, 40 rows were 9.7k on the dev DB copy.
- `backfill` rates stored matches without a row; with `recompute` it redoes all of them.
- DB 1008 adds PRED_TACTIC (the raw tactic rating; level = rating + 1) and REAL_TACTIC
  (Hattrick's tacticSkill). Rows from before stay null until a `backfill` + `recompute`.

Dev DB copy (36 rated matches; 128 others had no snapshot before kick-off): HO overrates our
attack (central -0.78, sd 0.6) and underrates the defence (central +0.49); midfield -0.03.
League is the worst (attack central -1.52, n 3), friendlies nearly neutral. The tactic level
comes out 1.4 too low in league matches (n 3), matching the 2026-10-10 observation (10 vs 12).
