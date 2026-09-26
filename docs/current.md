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

## Lineup: Hattrick-style tiles, penalty takers and team orders over MCP (2026-09-27, uncommitted)

Asked by the hattrick repo's consumer session, approved by the user.

- **Lineup tab look.** `PlayerPositionPanel.TileRenderer` draws the closed player dropdown as a
  tile (big shirt, bold surname, rating; a faded ghost tile when empty); the open list is HO's
  usual rows. Captain / set-pieces badges sit next to the position name. Formation and its
  experience are a row under the tactic dropdown (`MatchAndLineupSelectionPanel`). New skin
  "IntelliJ Light" (`IntelliJLightTheme`, darklaf). Not yet looked at on screen after the tile
  change.
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
