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

## Headless finances (2026-10-03)

Reported by the hattrick session: `get_finances` after a headless `download_team` was wrong. Three
bugs in the economy path, all also hitting desktop HO, fixed and verified on the live server
(HRF 159: every cost line adds up to `costsSum` for both weeks):

- `AmountOfMoney.parse` returned 0 while no currency formatter existed yet - always so headless,
  so every ECONOMY field was stored as 0 (`adc77098`).
- `HRFStringBuilder` wrote `LastCostsStaff` under the pre-v500 key `lastCostsPersonal` (`96423bb3`).
- `XMLEconomyParser` never read CHPP's `CostsTemporary`/`LastCostsTemporary`; the HRF stored bought
  players + arena building in their place, which `FinancePanel` then showed twice (`fd6a672a`).
  `get_finances` now reports `costsTemporary`.

`HRFStringBuilderTest` round-trips a real economy.xml through parser, HRF and `Economy`. ECONOMY rows
from before these fixes keep their wrong values; only new downloads are right.

## Headless MCP server (2026-09-27, committed 2026-10-03)

**Live since 2026-09-27** on the user's Linux server (Ubuntu 24.04, x86_64), in `~/ho` via
`docker compose` (Compose v2 plugin; Ubuntu's `docker-compose` v1 cannot talk to the current engine).
It runs the desktop HO's live DB (history since 2026-04-11), and `~/ho/data` is its only copy. Desktop
HO on the Mac must stay unused. Committed on `lineup-tv-view` (`ed2f4059`, `742c2ef9`).

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
- The `WorldDetailsTable` CURRENCY_RATE NPE is guarded (`742c2ef9`); players CHPP answers with error
  56 are marked and no longer re-asked (`e9aaf907`).
