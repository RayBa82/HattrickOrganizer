# Current work

Only the last one or two pieces of work live here. Older notes are dropped rather than
accumulated; git keeps them (`git log -p -- docs/current.md`; the full history file as of
2026-09-26 is `git show f5c51d0d:docs/current.md`).


## Tool gaps from the hattrick session's keeper/CD search (2026-11-09)

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
