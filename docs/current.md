# Current work

Only the last one or two pieces of work live here. Older notes are dropped rather than
accumulated; git keeps them (`git log -p -- docs/current.md`; the full history file as of
2026-09-26 is `git show f5c51d0d:docs/current.md`).


## Scout entries no longer built from a skill-less currentbids row (2026-11-16)

Reported by the hattrick session: `manage_scout` add/refresh and `list_scout` gave Simone
Rotatori (476480190, a player with an open bid) age 17, TSI 1000 and every skill 0. Cause: add
built the entry from the player's newest corpus row, and once we bid, `get_open_bids` stores a
`currentbids` row (bid and deadline, no age or skills) that is usually the newest, so the entry
kept `ScoutEintrag`'s defaults. Fix: add reads `playerdetails` first (a full row while he is
listed), then takes the market data from the newest row and the skills from the newest row with
an age (`loadLatestTransferListingWithDetails`). refresh now also writes the fresh skills, TSI,
wage and character onto each listed, unsold entry (`TransferSearchConverter.applyDetails`, which
leaves the notes column alone), so skills are no longer frozen at add. Unit-tested; **not yet
deployed** to the headless server. Rotatori's entry repairs itself on the next refresh after the
deploy, but his old note is gone (the subagent's re-add replaced the entry).

## MCP youth tools (2026-11-15)

Branch `feature/youth-mcp`, `YouthTools`: `get_youth_squad`, `get_youth_player`,
`get_youth_team`, `get_youth_training`, `set_youth_training` (local only: CHPP has no youth
training, and HO's estimates rest on it), `get_youth_matches` (live `matches` with `isYouth`)
and `send_youth_match_order` (the senior order JSON with youth ids, to the youth team, read
back like `send_match_order`). `send_match_order` now refuses youth match types, and
`get_match_order` reads a youth order from the youth team. Measured on a DB copy grafted with
the last real squad (HRF 126): squad 6.2k chars, player 3.3k, training 1.5k; `set_youth_training`
moved the estimates. **Not checked against Hattrick:** team, live matches and the order send,
for want of an academy (`docs/roadmap.md`).

## Still open from earlier pieces

The youth CHPP files (DB step 1100) are not checked on real youth data: no academy since
2026-09-22, see `docs/roadmap.md`. The installed desktop HO predates `e9aaf907` (gone scout
players) and needs a new build. On the next upstream merge, keep our DB chain (1001–1009, then 1100; upstream
has `case 1000 → updateDBv1100`) and our `DBManager.instance()` helpers (headless exits). The
#2429 gender/league system fields are not checked against a women's team. Also open from earlier pieces: compare the get_training predictions with the real training on
2026-11-20; the #2587 season-offset fix (ho-dev#2594) was not checked on the headless server's DB.
