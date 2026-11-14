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

## CHPP changelog 2026-11-10: docs re-captured, code not started (2026-11-14)

- `docs/raw/chpp/` re-captured from the live docs (60 files + datatypes, three new:
  playerstats, teamhistory, nationalprospectplayers) with the new `utils/chpp-docs/capture.js`
  (runs in a logged-in Chrome tab) and `split.py`. The capture builds from the docs page's own
  `file` object, so it also picked up actions the 2026-09-26 capture missed.
- `docs/chpp_files.md`: new available versions, a 2026-11-10 changelog table with what each
  change means for HO, and the OrderType 4 trap (matchlineup 2.2 uses 4 for a behaviour change,
  HO reads 4 as man marking - stay on 2.0 until a real man-marking match shows what 2.2 sends).
- No parser breaks: every request pins its version.
- Next, agreed with the user: transfersearch 1.2 (prices to SEK in `TransferTools.queryFrom`
  and the search tab, new salary/estimate filters), playerstats (new file), training
  `actionType=history` (Gold only). The comment at `TransferTools.listingFromProfile`
  ("same as queryFrom does for priceMin") is wrong today: queryFrom passes prices unconverted.
