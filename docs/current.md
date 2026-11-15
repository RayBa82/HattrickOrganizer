# Current work

Only the last one or two pieces of work live here. Older notes are dropped rather than
accumulated; git keeps them (`git log -p -- docs/current.md`; the full history file as of
2026-09-26 is `git show f5c51d0d:docs/current.md`).


## estimate_market_value: hattrickEstimate split from live (2026-11-15)

Asked for by the hattrick session: `live` bundled the transfer search (one of 20 per rolling hour)
with the playerdetails read of Hattrick's own estimate, so the estimate cost a search. `live` is now
the search only. The new `hattrickEstimate` argument (default true) reads it for a squad `playerId`
or for `profile.playerId`, i.e. any team's player, which Hattrick shows when the player is listed.
Tool and parameter descriptions say when the field is absent: no visible skills, search-only
comparables. Unit-tested, shipped as `ho-headless:11.0.0.2`, and checked live on the server (2026-11-15): a listed
player of another team via `profile.playerId` with `live: false` returned `hattrickEstimate` beside
the median, no search spent. The hattrick session's price-player skill uses these names.

## #2429 gender and league system merged from the upstream PR (2026-11-14)

Upstream PR ho-dev#2595 (branch `feature/2429-gender-league-system`, against ho-dev master)
merged into `master`. CHPP teamdetails 4.0, worlddetails 2.1, players 2.8, youthplayerlist 1.4;
`GenderID`/`LeagueSystemID` stored in Basics, players, youth players and world details. The
upstream step is `case 1000 → updateDBv1100`; here it runs as `case 1009 → updateDBv1100` and
`DB_VERSION` is 1100. When upstream merges the PR, keep our chain (1001–1009, then 1100) and
our `DBManager.instance()` helpers (headless exits) on the next upstream merge. get_team shows
`gender` and `leagueSystem` (male/female), left out on an old snapshot. Not checked against a
women's team: the dev DB is a men's team.

Still open from earlier pieces: compare the get_training predictions with the real training on
2026-11-20; the #2587 season-offset fix (ho-dev#2594) was not checked on the headless server's DB.
