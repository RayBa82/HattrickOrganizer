# Roadmap

Work that is decided but waits for a trigger. Each item says what starts it, what to do and
where the evidence is. Remove an item once it is done (git keeps it); `docs/current.md` holds
the work in progress.

## Rating model calibration review

**Trigger:** at least ~10 usable league matches in `get_rating_calibration` (`matchTypes [1]`,
non-confounded, snapshot at most 7 days old), and a few MOTS/PIC matches. As of 2026-10-11 the
server had 14 usable rows in all, 3-4 of them league; the dev DB copy has 10.

**Evidence so far** (mean real minus predicted; dev DB copy and the server, 2026-10-11):
- Central attack overrated, worst in league lineups: about -1.4 to -1.5 (n 3-4) against about
  -0.45 in friendlies/cup. Not explained by behaviours (an all-normal match had -2.08).
- Defence underrated: central about +0.5, wings about +0.3.
- Midfield accurate on average (-0.03), but -0.80 in the one MOTS league match (768523841).
- Tactic level predicted about 1.4 too low in league matches (10 vs 12 on 2026-10-10).

**Then check, in `core/rating/RatingPredictionModel.java`:**
1. How high sector values scale: `getRatingSectorScaleFactor` (ATTACK_CENTRAL .513) and the
   transform to the 1-20 scale - the attack error grows with the rating.
2. The MOTS/PIC midfield factors in `calcSector` (1.1149 / 0.83945), with enough MOTS rows.
3. The tactic strength formula (`getTacticRating`, `McpJson.tacticLevel` = rating + 1).

**Measurement limits to account for before changing any factor:**
- Hattrick reports sector ratings in quarter steps (`RatingCalibration`: `(raw - 1) / 4 + 1`).
  If Hattrick truncates rather than rounds, the true value is on average ~0.125 above the stored
  one and every residual is shifted by about -0.1. Test both assumptions.
- `TacticSkill` is a whole level only: single-match tactic residuals are noisy.
- Team spirit and confidence are known only as levels; HO assumes the middle sub-level
  (`Team.getTeamSpirit`, sub index 2; `calcConfidence` +0.5). Up to about ±4.5 % on midfield.
- Player form comes from the last download before the match, its sub-level estimated from TSI
  (`Player.adjustFormSub`).

Measurement only so far: `RatingCalibration` fits nothing. A model change needs a before/after
comparison over the same rows (`get_rating_calibration backfill recompute`).

## MCP aggregate tools for the hattrick skills

**Trigger:** none, worked off in this order. Agreed with the hattrick session on 2026-11-01:
its skills (set-lineup, search-transfers, price-player, buy-/sell-trainee, squad-review,
book-friendly) chain many calls. Its own estimate is a fifth to a third fewer tokens per week,
mostly from the lineup echo (about 2.5k tokens per eleven) and from one round per tool call.
Mechanical rules move to the server, and judgement stays with the agent: the A/B split, tactics,
selling and bid limits. The server never bids. Extra CHPP requests are fine, but the
transfer-search limit is never worked around with a token refresh. HO's consumer key is shared
by all HO users.

1. Done: `get_training` lists tournament fixtures, with `matchTypeId`, `home`, `trains` and
   `injuriesPossible`.
2. Done: `apply_lineup` answers with `changed` (a diff), the ratings and
   `captainInStartingEleven`. With `full` it adds the whole lineup. `get_lineup` has the
   captain flag, and both list occupied field and bench slots only.
3. Done: `week_brief` (`WeekTools`). It covers training, all fixtures of the week, the order
   Hattrick holds per upcoming fixture (`includeOrders`, one CHPP request each, so it runs as a
   task), and per player minutes, maxed, top-3 positions and availability. It adds the level-ups
   since the last training and `sources`.
4. Done: `download_team` `keepLineup` (default true, MCP only; the Download button still
   replaces). HO's lineup is written back when the next match is the same one. The result
   names `lineupMatchId` and has `hattrickOrderDiff`.
5. Done: `level_up_report` (`WeekTools`, a task unless `download: never`). `download: auto`
   downloads only once the snapshot's next training is due, keeping HO's lineup.
6. Built, not live-tested: `screen_market` (`MarketTools`). Open: a live run, and a check of
   `foreignWageFactor` 1.2 after a real foreign purchase: get_squad's wage against the hit's
   `Salary` before the buy. Original brief: run all saved searches with wide CHPP windows and filter on the
   server, so the fewest searches are spent. Searches that do not fit are queued.
   - `specialty` as a list (`[4,5]`), filtered on the server.
   - Filters: a wage limit that adds +20 % for foreigners (`nativeCountryId` differs from
     ours), no injured players, a specialty whitelist and top-N.
   - A fixed table: Player, Specialty, Age, PM, PASS, SC, WG, DEF, KP, SP, EXP, Wage in squad,
     Price, Card risk, Closes.
   - Card risk for the top-N via `playerdetails`, without scouting the players.
   - The expected final price per hit from the comparables: median, range and count. Early
     bids mislead.
   - Buy check: squad size against the cap of 26, and `expectedCash` minus the open bids.
   - Optional `scout: N`, off by default, because it writes to Hattrick.
   - Rules are parameters, not server code: specialty per slot, age/skill windows, caps,
     top-N, squad cap, sort and cash reserve. The training slots are read from
     `get_training`.
7. Done: `estimate_market_value` `summary` (figures, `middleHalf`, top 3 skill groups, the
   download).
