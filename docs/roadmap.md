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
