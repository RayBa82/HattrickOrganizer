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

## Follow-ups from the hattrick session's tournament week (2026-11-04)

Unit-tested (`core.mcp.*`), not run against a live DB.
- `get_series` in week 16 says it is the season break: `downloadCurrentFixtures` skips the
  fixtures then on purpose, and the new series has none published yet.
- Matches HO still holds as unplayed after kick-off get `kickedOff: true` (get_series
  `otherUpcoming`, get_training/week_brief fixtures); week_brief reads no order for them.
- week_brief orders carry `complete` and a `warning` when short of eleven (a tournament
  carry-over read as status ok, hoLineupDiffers false).
- download_team with an unknown non-`download-N` id points at `tasks/get`.
- get_match folds the captain (18) and set-pieces (17) rows into `captain`/`setPieces` flags.
- Tournament matches without week_brief: the server now sends `instructions` (initialize and
  server/discover) naming week_brief as the entry point, get_team has `nextMatches` (next 5
  own matches of every type, local data, `kickedOff`), and get_matches' description points there.
- Not done: apply_lineup's bench-merge warning (same-day starter on the bench), an empty start
  lineup for new tournament matches.
