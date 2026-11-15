# Current work

Only the last one or two pieces of work live here. Older notes are dropped rather than
accumulated; git keeps them (`git log -p -- docs/current.md`; the full history file as of
2026-09-26 is `git show f5c51d0d:docs/current.md`).


## Youth CHPP files and the shared match files on their latest versions (2026-11-15)

Branch `feature/youth-chpp`, DB step 1101; storage only, no UI. `matchesarchive` 1.5 and
`matchdetails` 3.2 everywhere (senior and youth), with the chances per side, the replay pair and
`ExpectedResult` stored. Checked on real responses of the same matches at old and new versions
(`XMLMatchVersionRegressionTest`). All six youth files at their latest version and stored:
native country, coach reports (raw), youth team with scouts, league standings and fixtures,
avatars. Details and request costs in `docs/chpp_files.md`, "Youth files".

**Not checked on real youth data:** the development club has had no youth academy since
2026-09-22, so the youth fixtures are built from the docs, and the undocumented coach
`ReportType` codes feed no skill estimate yet. Both are in `docs/roadmap.md`, as are the MCP
youth tools that come next.

Merged with it: gone players on the scout list (CHPP error 56, fired or retired) are kept in
`mcpScoutGonePlayers` and no longer asked about on every download for the 90-day re-listing
watch; checked on a DB copy (the next download asks once more, the one after asks none). The 40
other gone players the desktop app kept asking about since 2026-10-16 were already handled by
`e9aaf907`: the installed desktop HO predates it, so it needs a new build.

## estimate_market_value: hattrickEstimate split from live (2026-11-15)

Asked for by the hattrick session: `live` bundled the transfer search (one of 20 per rolling hour)
with the playerdetails read of Hattrick's own estimate, so the estimate cost a search. `live` is now
the search only. The new `hattrickEstimate` argument (default true) reads it for a squad `playerId`
or for `profile.playerId`, i.e. any team's player, which Hattrick shows when the player is listed.
Tool and parameter descriptions say when the field is absent: no visible skills, search-only
comparables. Unit-tested, shipped as `ho-headless:11.0.0.2`, and checked live on the server (2026-11-15): a listed
player of another team via `profile.playerId` with `live: false` returned `hattrickEstimate` beside
the median, no search spent. The hattrick session's price-player skill uses these names.

Still open: on the next upstream merge, keep our DB chain (1001–1009, 1100, then 1101; upstream
has `case 1000 → updateDBv1100`) and our `DBManager.instance()` helpers (headless exits). The
#2429 gender/league system fields are not checked against a women's team. Also open from earlier pieces: compare the get_training predictions with the real training on
2026-11-20; the #2587 season-offset fix (ho-dev#2594) was not checked on the headless server's DB.
