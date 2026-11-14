# Current work

Only the last one or two pieces of work live here. Older notes are dropped rather than
accumulated; git keeps them (`git log -p -- docs/current.md`; the full history file as of
2026-09-26 is `git show f5c51d0d:docs/current.md`).


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

## CHPP changelog 2026-11-10: docs, transfersearch 1.2, playerstats, training history (2026-11-14)

- `docs/raw/chpp/` re-captured from the live docs (60 files + datatypes, three new:
  playerstats, teamhistory, nationalprospectplayers) with the new `utils/chpp-docs/capture.js`
  (runs in a logged-in Chrome tab) and `split.py`. The capture builds from the docs page's own
  `file` object, so it also picked up actions the 2026-09-26 capture missed.
- `docs/chpp_files.md`: new available versions and a 2026-11-10 changelog table with what each
  change means for HO.
- matchlineup 2.2 (committed): 2.2 uses OrderType 4 for a behaviour change, HO read 4 as man
  marking. `XMLMatchLineupParser.legacyOrderType` now splits 4 by subject == object (behaviour
  change, handed on as 1) vs another player (man marking). How 2.2 reports man marking is still unverified - check the
  first match with a man-marking order.
- Committed in 9838f54e, unit-tested, none tried against live CHPP yet:
  the parsers are shaped from the docs, there is no real sample.
- transfersearch 1.2: `TransferSearchQuery` money bounds are `Long` krona, plus
  `salaryMin/Max` and `transferCompareAvgMin/Max`. The search tab sends the form's krona value
  (`criteria().maxPriceSek()`, `maxPriceLocal` is gone); `search_transfers`/`screen_market` take
  `priceMin/Max`, `salaryMin/Max`, `estimateMin/Max` in the user's currency and `queryFrom`
  converts.
- playerstats 1.0: `Connector.getPlayerStats`, `XMLPlayerStatsParser`,
  `ChppWriteService.playerStatsForAgent`, new MCP tool `get_player_match_stats` (MatchTools;
  limit default 20, max 25 by the ~330-char row; playedOnly). Not stored in the DB.
- training 2.3 history: `Connector.getTrainingHistory`, `XMLTrainingHistoryParser`,
  `ChppWriteService.trainingHistoryForAgent`; `get_training historyWeeks` adds
  `hattrickHistory` (Gold only, a refusal comes back as `available: false`). Not used to
  backfill HO's own training weeks - that would feed the training engine and is a separate
  decision.
- Next, proposed after the 2026-11-10 editorial (engine changes from the new season):
  `TransferTools` specialty scores for estimate_market_value - raise Support (0.1 everywhere,
  support events no longer need a second support player, opponent experience counts half, the
  bonus doubles) and Technical slightly (tech vs head checks experience less strictly, more
  extra counters, reacts to man marking like no specialty). To docs/roadmap.md, waiting for
  numbers: the experience weight in `TechnicalEventPredictionAnalyzer`, and support events in
  the special-events prediction (`TODO: Support Player`). Confidence now fades older results;
  HO only reads the current value, nothing to change.
- Still open: budget measurement of both tools on a real team (needs a CHPP call), and a live
  check that the transfersearch price filter now matches in krona.
