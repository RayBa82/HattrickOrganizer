# Current work

Only the last one or two pieces of work live here. Older notes are dropped rather than
accumulated; git keeps them (`git log -p -- docs/current.md`; the full history file as of
2026-09-26 is `git show f5c51d0d:docs/current.md`).

## Next up: enforce the MCP token budget

The rule is in AGENTS.md, "MCP server". Every tool response must fit 10,000 characters of
serialized JSON, all or nothing. Over budget, the tool returns only a `truncated` envelope with a
hint naming its own paging/filter arguments. The rule is documented but not implemented.

- The natural choke point is `McpHttpServer.toolsCall`, where every result is serialized
  (`GSON.toJson(tool.handler().call(arguments))`). Measure there and swap in the envelope; each
  tool supplies its own hint.
- Then audit every tool's defaults against it, starting with the list-shaped ones. Today only
  `list_scout` pages (it was ~260k chars before paging).

## Lineup tab in the TV-view look (2026-09-27, uncommitted)

Asked by the hattrick session, after the user approved the plan there: make the lineup tab look like the
hattrick repo's `scripts/lineup-page`. System fonts only (no bundled Saira/Barlow), crest = HO's
downloaded club logo. Checked on screen by the user with real data (screenshots, 2026-09-27).
Hattrick sends no kit colour: the shirt is the more saturated of the club page's two theme colours,
else brand green. The tile combo reports a wider `getSize()` so its popup list fits a player row.

- `PitchStyle` (module.lineup.lineup) holds the palette, the pitch painter, the kit icon and the flags.
  Kits use the club colours from XtraData (`TeamColorBackground`/`Text`, Supporters only), else
  brand green; the keeper wears sea. Dark themes (`DarkTheme`) get the page's dark grass and
  specialty colours.
- `LineupPositionsPanel` is a plain JPanel now (was `RasenPanel`) and paints a half pitch behind
  the union of the eleven field slots, flipped for keeper-at-top.
- `PlayerPositionPanel`: field and bench slots draw their player combo as a `SlotView` (kit, C/SP/
  behaviour flags, name plate with specialty dot, position and rating; ghost circle when empty).
  The combo paints only that view (no box, no arrow) but still opens the usual popup. Captain and
  set-pieces slots stay plain dropdowns. The position label is hidden on tiles; training positions
  get a rounded frame.
- `MatchBanner` moved over the pitch (`LineupPanel`): brand band with crest, HOME vs AWAY,
  date/type, and a tactic line from `MatchAndLineupSelectionPanel.getTacticBarItems()`.
- `LineupRatingPanel`: each sector shows a bar (rating / 20) in place of the coloured dot.
- Not done: the page's A/B squad badge (HO has no such concept), restyling the penalty-takers
  and team-orders tabs.

## Lineup: Hattrick-style tiles, penalty takers and team orders over MCP (2026-09-27, uncommitted)

Asked by the hattrick repo's consumer session, approved by the user.

- **Lineup tab look.** Superseded by the TV-view section above. Formation and its experience
  are a row under the tactic dropdown (`MatchAndLineupSelectionPanel`). New skin "IntelliJ Light"
  (`IntelliJLightTheme`, darklaf).
- **MCP.** `get_lineup` reports `penaltyTakers` [{playerId, name, strength}] and `teamOrders`
  [{type, minute, playerOut, playerIn, roleId, behaviour, redCard, standing}] (`LineupOrders`).
  `apply_lineup` takes both, each replacing the whole list (omit = untouched, [] = clear), refuses
  what Hattrick would refuse before writing, and returns `orderWarnings` for what HO's tabs flag.
  `simulate_lineup` lists them under `ignored`.
- **send_match_order** echoes the order as sent (`order`, parsed from the upload string). Without
  Supporter it returns `status: supporterRequired`, `sent: false` and `wouldSend`: setmatchorder is
  Supporter-only (CHPP error 6), which the CHPP docs do not say. An `OrdersSet="False"` answer used
  to come back as "ok"; it is now a failure with Hattrick's Reason.
- **Limits** in one place, `OrderLimits`, used by the team orders tab and MCP: 5 orders + 1 per
  Tactical Assistant level, at most 3 substitutions (wiki: Substitutions; the tab did not enforce
  this before), 1 man marking.
- **Silent losses fixed.** `Lineup.toJson` always sends 11 kicker slots. The penalty tab writes
  every edit into the lineup at once instead of on leaving the tab.
