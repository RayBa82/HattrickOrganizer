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

## Headless MCP server (2026-09-27, uncommitted)

**Live since 2026-09-27** on the user's Linux server (Ubuntu 24.04, x86_64), in `~/ho` via
`docker compose` (Compose v2 plugin; Ubuntu's `docker-compose` v1 cannot talk to the current engine).
It runs the desktop HO's live DB (history since 2026-04-11), and `~/ho/data` is its only copy. Desktop
HO on the Mac must stay unused. The code is still uncommitted: it lives only in the Mac's working tree.

Asked by the user: HO on a Linux server with no display, serving only the MCP tools. Documented
in AGENTS.md, "Headless server". `core.HeadlessMain` reuses `HO.initRuntime` (extracted from
`HO.main`), then does the non-UI startup steps and starts `McpHttpServer`.

- Verified with `-Djava.awt.headless=true` against a copy of the dev DB: all 29 tools listed, every
  read tool, `apply_lineup`, `set_player_group`, `list_challenges` (live CHPP) and a full
  `download_team` (all sections) worked; SIGTERM stops the server and disconnects the DB. On an
  empty DB, `authorize` creates the database and prints the authorization URL. The PIN exchange
  itself has not been run end to end yet: it needs a human with a Hattrick login.
- Not exercised headless: the CHPP writes (`send_match_order`, `place_bid`, `set_training`,
  `manage_challenge`). Their 401 path now throws under `silentDownload` like the GET path
  did already, where it used to open `OAuthDialog`.
- Packaging: `./gradlew headless` (tarball + `ho-headless.sh`) and `./gradlew docker`
  (image `ho-headless`, Temurin 17 JRE, volume `/data`, `-Dho.mcpBind`). The image was verified on
  Docker Desktop (arm64) with a copy of the dev DB: tools answer on the host through
  `-p 127.0.0.1:8765:8765`, `simulate_lineup` (prediction/) and a full `download_team` work, a write
  survives stop/start, `docker stop` shuts down cleanly, and `authorize` on a fresh volume prints the
  URL. The `-PdockerPlatform=linux/amd64` image was only started under emulation.
- `docker-compose.yml` with `./data` bind mount, for moving the live desktop DB (with its history) to the
  server. Rehearsed with a copy of the live `~/Library/Application Support/HO` data: packed as in the
  README, served through `docker compose up`, history tools answered (26 weeks). Ownership was
  simulated with a volume: matching uid works, a wrong owner exits with HO's "Could not write to
  database" message. A real Linux host has not been tried yet.
- `./gradlew dockerPush` (buildx, amd64 + arm64, `-PdockerRegistry`, default docker.rayba.de);
  compose takes `HO_IMAGE`. Pushed to docker.rayba.de on 2026-09-27 (digest sha256:7be84146…); pulled
  back, both architectures run. That needed the registry's nginx to forward `Host` and
  `X-Forwarded-Host` as `$http_host`: with `:443` in the upload URLs, Docker sent the upload PUT
  without credentials (401).
- Pre-existing, seen in desktop logs too: `WorldDetailsTable` CURRENCY_RATE NPE and CHPP error 56
  from `TransferListingResolver`.

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
