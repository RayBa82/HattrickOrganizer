# Current work

Only the last one or two pieces of work live here. Older notes are dropped rather than
accumulated; git keeps them (`git log -p -- docs/current.md`; the full history file as of
2026-09-26 is `git show f5c51d0d:docs/current.md`).


## #2587 season offset 0 no longer re-derived on load (2026-11-14)

On `master`, unit-tested, not yet reported upstream. `BasicsTable.loadBasics` re-derived the
offset from the download date whenever it was 0. Germany's real offset is 0, so every load
guessed, and a download between the league's season change and HO's Monday changeover came out
±1 after a restart (training weeks, get_training, week_brief, level_up_report, weekStart).
`Basics.seasonOffset` is now an `Integer`: null = unknown (only then derived), 0 stays 0.
The local DB had no download in that window; the headless server's DB was not checked.

## Upstream merge (ho-dev master + release/10) and training prediction (2026-11-14)

Merged on `master` from branch `merge-upstream`, image pushed and checked live by the hattrick session.
- Upstream up to #2588 and #2580 (release/10 only so far): match XML parsers on `XMLUtils`,
  `HOBuild`/`HOBuildLoader` (serverInfo version comes from it), `core.util.HTWeek` record,
  `toLocal*` renames, lineup download for upcoming matches, tournament details loaded by
  `MatchKurzInfo.loadTournamentDetails` (null-safe on a failed CHPP call). Upstream's own
  warnings on its lines were left alone to keep later merges conflict-free.
- get_training skillChanges were empty headless: only the Training tab rolled the
  FUTURETRAINING rows forward. `core.training.FutureTrainingPlan` now does it for the tab,
  get_training and week_brief, and stores the rolled plan back (never an empty one).
- get_training flags a drop on a skill HO holds no subskill for (exactly .0, untrained) as
  `uncertain: true` plus `uncertainNote`: HO assumes .0, so it predicts the drop in week one.
- Open: compare the predictions with the real training on 2026-11-20.
