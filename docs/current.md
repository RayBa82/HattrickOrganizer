# Current work

Only the last one or two pieces of work live here. Older notes are dropped rather than
accumulated; git keeps them (`git log -p -- docs/current.md`; the full history file as of
2026-09-26 is `git show f5c51d0d:docs/current.md`).


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

## CHPP 2026-11-10, second part: playerdetails 3.3, stafflist 1.3, matches 3.0, teamhistory (2026-11-14)

Committed on `master`, image pushed, unit-tested, none tried against live CHPP. DB 1009 adds
the columns. The hattrick session was told (tools, currency of the search filters, editorial).
- playerdetails 3.3 (reads; placeBid stays 3.2): `TransferCompareAverage(+Date)` →
  `MarketPlayer.hattrickEstimate(Date)`, kept on playerdetails rows of TRANSFERLISTING
  (`HTESTIMATE`, `HTESTIMATEDATE`). Shown as `hattrickEstimate` in list_scout, manage_scout
  add/refresh and the comparables; estimate_market_value reads it live for a squad player (one
  playerdetails call while `live`). Second opinion only, not in the median. The request also
  fills the assists the parser read since 3.1.
- stafflist 1.3: `StaffMember.contractExpires` through XML → HRF → STAFF; `get_team` staff
  rows carry `contractExpires`, `week_brief` lists `staffContractsEnding` (14 days ahead).
- matches 3.0: `MatchKurzInfo.fanMatchExpectation` (MATCHESKURZINFO.FanMatchExpectation);
  `fanExpectation` ("7 we are favourites") on get_team/download_team nextMatches and get_matches.
  Statements not read. An archive re-store of a match writes null over it.
- teamhistory 1.0: `XMLTeamHistoryParser`, `ChppWriteService.teamHistoryForAgent`,
  analyze_opponent `include: ["history"]` (opt-in, 1 request): newest 10 seasons as rows,
  newest 10 trophies, counts.
- Follow-up after the hattrick session's live test (image pushed): nextMatches rows carry
  `opponentTeamId`, so analyze_opponent can be called from get_team. Transfer price/salary
  filters, playerstats, training history and the scout estimate worked live; staff contract
  ends and fan expectations fill only from the first download with the new image.
- Side fixes for diagnostics on touched code: `DBManager` constants `DB_VERSION` /
  `PREVIOUS_DB_VERSION`, `instance()` split up (the backup dialog is now modal instead of a
  busy-wait), `XMLPlayersParser.parseMarketPlayerFromString` split up.
- Not done: hofplayers 1.3, achievements, nationalprospectplayers (little value), the editorial
  follow-ups below. Budget check of analyze_opponent history and live checks still open.

Committed on `master` with the tournament-week follow-ups below; image pushed. Unit-tested.
- `get_open_bids` adds `ourMaxBid` (the autobid ceiling place_bid sent). `leading` was always
  real (highest bidder's team id = ours); only `category` comes from trackingTypeId, and the
  note now says so. CHPP's currentbids never sends our own bid, so ourBid/ourMaxBid exist only
  for bids HO placed; while leading, highestBid is our current bid.
- `list_scout` `playerIds` filter; ids not on the shortlist come back as `missingPlayerIds`.
- `download_team`'s result ends with `nextMatches` (as get_team: next 5, every competition).
- Not done, no data source: skill loss with age for 36+ players (HO's `SkillDrops` is the
  training drop, not ageing), and resolved sale prices for old players in
  `estimate_market_value` (workaround: get_player_transfer_history per comparable, fed back via
  profile + playerId + salePrice).
- Follow-up (image pushed): `download_team` and `level_up_report` run outside the
  tool lock (`McpTool.outsideToolLock`, `McpToolLock`), so plain calls answer during a download.
  level_up_report locks only its reads, and every download shares one registry: a second
  request joins the running one (`TeamTools.downloadAndWait`).
