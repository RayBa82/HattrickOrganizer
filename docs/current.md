# Current work

Only the last one or two pieces of work live here. Older notes are dropped rather than
accumulated; git keeps them (`git log -p -- docs/current.md`; the full history file as of
2026-09-26 is `git show f5c51d0d:docs/current.md`).


## #2429 gender and league system merged from the upstream PR (2026-11-14)

Upstream PR ho-dev#2595 (branch `feature/2429-gender-league-system`, against ho-dev master)
merged into `master`. CHPP teamdetails 4.0, worlddetails 2.1, players 2.8, youthplayerlist 1.4;
`GenderID`/`LeagueSystemID` stored in Basics, players, youth players and world details. The
upstream step is `case 1000 → updateDBv1100`; here it runs as `case 1009 → updateDBv1100` and
`DB_VERSION` is 1100. When upstream merges the PR, keep our chain (1001–1009, then 1100) and
our `DBManager.instance()` helpers (headless exits) on the next upstream merge. get_team shows
`gender` and `leagueSystem` (male/female), left out on an old snapshot. Not checked against a
women's team: the dev DB is a men's team.

## #2587 season offset 0 no longer re-derived on load (2026-11-14)

On `master`, unit-tested; upstream PR ho-dev#2594 against `release/10`. `BasicsTable.loadBasics` re-derived the
offset from the download date whenever it was 0. Germany's real offset is 0, so every load
guessed, and a download between the league's season change and HO's Monday changeover came out
±1 after a restart (training weeks, get_training, week_brief, level_up_report, weekStart).
`Basics.seasonOffset` is now an `Integer`: null = unknown (only then derived), 0 stays 0.
The local DB had no download in that window; the headless server's DB was not checked.

Open from the previous piece (upstream merge, training prediction): compare the get_training
predictions with the real training on 2026-11-20.
