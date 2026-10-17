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
- `search_transfers`: hits as arrays in `hitFields` (~115 chars; 50 a page, max 70), `full`
  for the old objects (10, max 12). Every answer keeps its hits under a `resultId` (last 10,
  in memory): `resultId` + `limit`/`offset`/`sort` pages without a CHPP search, so not
  against the cap. queries[] merges the hits (a player once), results[] keeps per-query
  itemCount/hitCount/error. estimate_market_value's `liveSearch` is now that summary plus a
  resultId instead of up to 100 full hits. Before, over-budget searches came back as the bare
  envelope - the hattrick screening script read Claude Code's saved oversize-result file.
  `bulk: true` (agreed with hattrick) puts every hit, compact and sorted, into a download
  (`McpDownloads`, `GET /mcp/download/<id>` on the MCP port, newest 20 in memory); the answer
  keeps resultId, count, hitFields, a 3-hit preview and the path. The user ruled out relying on
  Claude Code's saved oversize-result file. AGENTS.md has the rule; no other tool has bulk yet
  (candidates if a script needs them: list_scout full, get_player_history long histories).

Nothing is lost, only paged (checked on the DB copy): `get_squad` pages with `limit`/`offset`
(limit 8 carries every include part, 15 the core with skills); `get_player_history` pages back in
time with `weeks` + `weeksAgo`, one player's snapshots a week at a time (up to 28 a week there,
7.6k - a week with more than ~35 downloads would still drop them); `get_training` takes
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
  lineup, so no column), `confounded` reason only when set. `attitude` filters stats and rows.
  20 rows 5.5k, 40 rows were 9.7k on the dev DB copy.
- `backfill` rates stored matches without a row; with `recompute` it redoes all of them.
- DB 1008 adds PRED_TACTIC (the raw tactic rating; level = rating + 1) and REAL_TACTIC
  (Hattrick's tacticSkill). Rows from before stay null until a `backfill` + `recompute`.

Dev DB copy (36 rated matches; 128 others had no snapshot before kick-off): HO overrates our
attack (central -0.78, sd 0.6) and underrates the defence (central +0.49); midfield -0.03.
League is the worst (attack central -1.52, n 3), friendlies nearly neutral. The tactic level
comes out 1.4 too low in league matches (n 3), matching the 2026-10-10 observation (10 vs 12).
