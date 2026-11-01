# AGENTS.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

HattrickOrganizer ("HO!") is a desktop Swing client for the online football manager
[Hattrick](https://www.hattrick.org). It downloads a team's data through the Hattrick CHPP API,
stores it in an embedded HSQLDB database, and provides analysis modules (training prediction,
lineup rating, transfer scouting, statistics, …) on top of that local history.

- Upstream: `https://github.com/ho-dev/HattrickOrganizer`, default branch `master`, LGPL 3.0.
- User documentation: <https://ho.readthedocs.io/> (sources in `docs/*.rst`).
- Developer notes: `docs/current.md` — state of the work currently in progress: what shipped, what is
  blocked and why, and what comes next. **Read it first when picking work back up, and update
  it when you finish or block a piece of work.** It covers only the last one or two pieces of work,
  not a history. When you add a new piece, delete the oldest (git keeps it). A finding that must
  outlive its task belongs in a code comment, AGENTS.md or a topic doc, not here. `docs/roadmap.md`
  holds decided work waiting for a trigger (e.g. enough matches for a rating-model review); check it
  when picking work up. `docs/chpp_files.md` lists the CHPP files HO uses;
  `docs/raw/chpp/` holds the full CHPP docs (inputs, output trees, data types) for every file.
- Java 17 source/target, Kotlin JVM toolchain 17, Gradle 8.13 wrapper. CI builds on Temurin 17.
  Newer local JDKs work for running, but do not raise the language level.

## Build and run

```bash
./gradlew run                     # run HO from the classes dir - the normal dev loop
./gradlew test                    # full unit test suite (JUnit 5)
./gradlew test --tests "core.util.MathUtilsTest"          # single class
./gradlew test --tests "core.util.MathUtilsTest.someCase" # single method
./gradlew tasks                   # task list
./gradlew tiTree <task>           # task graph (taskinfo plugin)
```

### Producing a runnable jar

```bash
./gradlew createVersion createDistribution
cd build/install/XXX && java -jar HO.jar
```

`createVersion` is **not optional**. `core.HO` reads `Implementation-Version` from the jar manifest
and splits it into four parts (`major.minor.build.stage`, `HO.java:118`). The `jar` task alone stamps
`project.version` — just `10.0` — and startup dies with `ArrayIndexOutOfBoundsException`.
Only `createVersion` computes the four-part string, and it rewrites the manifest attribute in
`doLast`, so it must run in the *same* Gradle invocation as `jar`.

`createDistribution` matters too: the jar manifest's `Class-Path` lists ~88 dependency jars by bare
filename, and `FileLoader` resolves `prediction/`, `truststore.jks` and themes from the working
directory before falling back to the classpath. `createDistribution` flattens `lib/` next to `HO.jar`
and copies those resources in. A bare `./gradlew jar` produces a jar that cannot start on its own.

Run the jar from inside its distribution directory — HO writes `db/`, `logs/` and `user.xml`
relative to the working directory, and `./gradlew clean` will delete them along with `build/`.

### Build pipeline quirks

- `compileJava.dependsOn(clean)` — **every build is a full clean rebuild**. Incremental compilation
  does not exist here; expect ~10s minimum turnaround.
- `processResources` → `poeditorPull` → `markdownToHtml` → `pushmd`. So every build hits the POEditor
  API and shells out to `git`. Without `POEDITOR_APIKEY` the pull prints
  `{"response":{"status":"fail","code":"4011","message":"Invalid API Token"}}` and continues — this
  is expected locally and not a failure.
- `gradle.properties` holds the local-only knobs: `DEV_STAGE` (0 DEV / 1 BETA / 2 STABLE),
  `POEDITOR_APIKEY`, `install4jHomeDir`, and `resetDir`/`tempDir` for the `resetDB`/`pushDB` helper
  tasks that copy a database between a real installation and the dev tree.
- Marketing version lives in `build.gradle` (`version = '10.0'`); the build number comes from the
  `RUN_NUMBER` env var (GitHub Actions run number, `0` locally).

### Release

`./gradlew release` runs `clean, createVersion, poeditorPull, processResources,
createLanguageFileList, makeAllMedias` and drives install4j (`utils/buildResources/HO.install4j`) to
produce every platform artefact into `build/artefacts` — Windows setup/archive, macOS single bundle
(buildIds `1225`/`1224`), Unix archive/installer, rpm/deb, each with and without a bundled JRE.
This needs an install4j licence (`INSTALL4J_LICENSE`) and `INSTALL4J_HOME`. `.github/workflows/release-dev.yml`
runs it on every push to `master` and `release/**` and publishes a GitHub prerelease under the
`dev`/`beta`/`stable` tag.

Without an install4j licence, build a local macOS `.app` with the JDK's own `jpackage`:

```bash
./gradlew createVersion createDistribution

# icon (optional) - no .icns is checked in, build one from the logo first
mkdir -p build/HO.iconset
for s in 16 32 128 256 512; do
  sips -z $s $s utils/buildResources/Logo.png --out build/HO.iconset/icon_${s}x${s}.png
  sips -z $((s*2)) $((s*2)) utils/buildResources/Logo.png --out build/HO.iconset/icon_${s}x${s}@2x.png
done
iconutil -c icns build/HO.iconset -o build/HO.icns

jpackage --type app-image --input "$PWD/build/install/XXX" --dest "$PWD/build/macapp" \
  --name HO --main-jar HO.jar --main-class core.HOLauncher --app-version 10.0 \
  --arguments INSTALLED --icon "$PWD/build/HO.icns"
open build/macapp/HO.app

# .dmg for handing around - reuses the app image above, no rebuild
rm -rf build/dmgroot && mkdir build/dmgroot
cp -R build/macapp/HO.app build/dmgroot/
ln -s /Applications build/dmgroot/Applications
hdiutil create -volname "HO 10.0" -srcfolder build/dmgroot -ov -format UDZO \
  -fs HFS+ build/macapp/HO-10.0.dmg
```

`./gradlew dmg` wraps exactly this (app-image + `hdiutil`, not `jpackage --type dmg`) and
writes `build/macapp/HO-10.0.dmg`.

- `--input`/`--dest` must be **absolute** — jpackage reports "main JAR not present in input directory"
  for relative paths even when the jar is there.
- `--arguments INSTALLED` is what makes the bundle usable: without it HO runs portable and writes
  `db/`, `logs/` and `user.xml` into the launch working directory (`/` for a Finder launch). With it
  everything lands in `~/Library/Application Support/HO`.
- `prediction/` and `truststore.jks` are inside `Contents/app/` and resolve via the classpath
  fallback, so no extra copying is needed.
- `--icon` also wants an absolute path, and the file has to exist - there is no `HO.icns` in the
  repo, only `utils/buildResources/Logo.png`. Drop the flag entirely for the default Java icon.
- Unsigned, so the first launch needs right-click -> Open (or `xattr -dr com.apple.quarantine HO.app`).
- Do **not** use `jpackage --type dmg` here. It drives Finder over AppleScript to lay the volume out,
  and the JDK 21 `DMGsetup.scpt` fails to match the `.app` (`"HO.app" ends with "/HO.app"` is never
  true), so the app icon never gets positioned and Finder auto-places it on top of the *Applications*
  icon at ~(390,130). Both icons overlap, and dragging the app onto Applications only works if you
  happen to grab the top one. jpackage also makes *Applications* a Finder **alias file** rather than a
  symlink. The `hdiutil` recipe above avoids both: real symlink, no `.DS_Store`, Finder auto-arranges.
  Cost is the jpackage background image, which was generic anyway.

## Architecture

### Startup sequence

`core.HOLauncher.main` → `core.HO.main`. The launcher is a self-updater: if `update.piz` sits in the
install directory it zips the current install into `backup/`, unpacks the update over it, and only
then hands off to `HO.main`. `HO.main` then, in order: parses log-level args (`INFO`/`DEBUG`/…) and
the `INSTALLED` flag, reads the version from the manifest, offers a user picker if the DB holds
several users, shows `SplashFrame`, backs up the DB, loads `UserParameter` from the DB, applies the
darklaf theme, runs DB migrations, initialises `TrainingManager`, and finally shows `HOMainFrame`.

### Singletons

The codebase is built almost entirely on `X.instance()` singletons — `HOVerwaltung`, `DBManager`,
`UserManager`, `ModuleManager`, `UserParameter`, `TrainingManager`, `HOMainFrame`, `ThemeManager`,
`HOLogger`. There is no dependency injection. New code is expected to follow the same pattern rather
than introduce a container.

`core.model.HOVerwaltung` ("administration") is the in-memory application state: it holds the current
`HOModel` — one snapshot of the team at one point in time — and `loadLatestHoModel()` rebuilds it
from the newest HRF row in the database. Most UI code reaches its data via
`HOVerwaltung.instance().getModel()`.

### Data pipeline

Hattrick CHPP (OAuth 1.0a, scribejava) → `core.net.Connector` / `HattrickAPI` → `core.net.OnlineWorker`
(the download orchestration surface: `getHrf`, `downloadMatchData`, `getMatches`, `downloadMatchLineup`,
`uploadMatchOrder`, …) → `core.file.xml.XML*Parser` (one parser per CHPP file type) →
`ConvertXml2Hrf` / `core.file.hrf` → model objects → `core.db` tables.

HRF is the legacy Hattrick export format and remains the internal snapshot unit: one HRF row per
download, and the training/skill engines replay the HRF history to derive sub-skills.

### Persistence

`core.db.DBManager` is the facade over an embedded HSQLDB (`jdbc:hsqldb:file:<dbFolder>/database`).
Each table is a subclass of `core.db.AbstractTable` that declares its columns as a
`ColumnDescriptor[]` in `initColumns()`; the base class generates the select/insert/update/delete SQL
from that, using `idColumns` to know how many leading columns form the key.

**Schema changes require a migration.** `DBManager.DBVersion` (currently `1008`, "HO 10.0") is the
target; `core.db.DBUpdater.updateDB` is a deliberately fall-through `switch` where each `case` is a
starting version that cascades through every later `updateDBvNNN()` step. Adding a column means:
change the table's `initColumns()`, bump `DBVersion`, and add the matching case plus `ALTER TABLE`
step. Note `DBManager.getDBVersion()` returns `previousDBVersion` when `HO.isDevelopment()`, so DEV
builds intentionally re-run the newest migration on each start.

### Modules

Feature areas are "modules" under `src/main/java/module/*`, each implementing `core.module.IModule`
(usually via `DefaultModule`): a numeric id from the constants in `IModule`, a tab panel, an optional
config panel and menu, and an activation status persisted per user. `core.module.ModuleManager`
instantiates all of them eagerly in `initMap()` — registering a new module means adding an id
constant in `IModule` and a `map.put(...)` line there. Modules are toggled by the user, not
discovered dynamically.

`src/main/java/tool/*` holds smaller standalone utilities (arena sizer, HRF explorer, notepad, DB
cleanup, updater) wired through `tool.ToolManager`.

### Ratings and prediction

Two distinct engines, easily confused:

- `core.rating` — predicts *lineup ratings* from player skills. Configuration is data-driven:
  `RatingPredictionConfig` loads `prediction/<type>/*.conf` from the working directory at runtime, so
  formula tweaks usually mean editing files under `prediction/`, not Java. `AkasolaceRatingPredictionModel.groovy`
  is a Groovy implementation of an alternative model — Groovy is a first-class source language here.
- `core.prediction.engine` — a Monte-Carlo *match outcome* simulator built on those ratings
  (`MatchPredictionManager`, `ActionGenerator`, `CounterAttackGenerator`).

Several `*RatingCache` classes exist because rating calculation is hot and is re-run on every lineup
edit.

### Localization

All UI strings go through `TranslationFacility.tr(key)`. Translations live in
`src/main/resources/language/*.properties` and are **owned by POEditor, not by the repo** — do not
hand-edit them for anything but a new key, since `poeditorPull` overwrites them on every build.
`createLanguageFileList` regenerates `language/ListLanguages.txt`, which is how HO discovers the
available languages at runtime.

### MCP server

`core.mcp` exposes HO's data to AI agents over MCP: `McpHttpServer` serves JSON-RPC, and the
tools live in `TeamTools`, `MatchTools`, `LineupTools`, `TrainingTools`, `TransferTools` and
`WeekTools` (`week_brief`, the aggregate the hattrick skills start the week with). Each is
an `McpTool(name, description, schema, handler)`. `McpServerToolsTest` pins the tool count and a
400-char cap on descriptions.

#### Protocol

The server is *dual-era* (MCP 2026-07-28 terms), and it chooses per request:
- **Modern (2026-07-28):** the request carries `_meta["io.modelcontextprotocol/protocolVersion"]`.
  It is stateless, and the client must mirror `MCP-Protocol-Version`, `Mcp-Method` and `Mcp-Name`
  (the tool name, or the taskId for `tasks/*`) into headers. A mismatch is 400 with `-32020`, a
  version not in `MODERN_VERSIONS` is 400 with `-32022`, and an unknown method is 404 with
  `-32601`. Results carry `resultType` and serverInfo in `_meta`. `server/discover` is mandatory.
- **Legacy (2025-11-25 and earlier):** `initialize`, and anything without the `_meta` version.
  It is synchronous, with `ping` but without tasks. An unknown version gets 2025-11-25.

Bumping the protocol means adding the new version to `MODERN_VERSIONS` once the server
implements its changes, not before.

**Tasks** (the `io.modelcontextprotocol/tasks` extension, `McpTasks`): when a modern client
declares the extension in that request's `clientCapabilities`, a tool marked with
`McpTool.asTask(predicate)` answers with a task handle, and the client polls `tasks/get`. Mark
calls that can outlast a client timeout (CHPP round trips), never local reads. A tool that hands
out its own polling handle for older clients, as `download_team` does, passes a separate task
handler. A tool error (`isError`) completes the task. Only a JSON-RPC error fails it. Tasks are
kept in memory: 1 h TTL, the newest 50.

**One tool handler at a time.** Plain calls and tasks share `McpHttpServer.toolLock`, because
HO's DB layer shares one JDBC statement. The HTTP pool has several threads only so that polls and
downloads never wait behind a running tool.

**Every tool must be token-efficient. This is a project-wide rule, not a per-tool preference.**
The point of the server is that an agent can learn everything about the team, and that only
works if no single call floods its context.

- **Compact by default, detail on request.** Return what the question needs. Put bulky parts
  behind explicit arguments (`full`, `include…`, per-id filters). Lists page with `limit`/`offset`
  and a sane default and maximum, and report `count`/`returned`/`offset`.
- **Budget: 10,000 characters per tool response**, measured on the serialized JSON text a tool
  call returns (roughly 2.5k tokens). Design defaults so a normal call stays well under it.
- **Enforced in one place.** `McpHttpServer.toolsCall` passes every result through
  `McpBudget.fit`. A tool declares its hint and its optional sections with
  `McpTool.withBudget(hint, OptionalSection...)`; without one, the hint lists its arguments.
- **Never cut off partway.** No half lists, no clipped strings, no JSON truncated
  mid-structure. What goes out is always whole.
- **Over budget, optional sections go first.** `McpBudget` drops the tool's declared optional
  sections in order (whole sections, from every row of a list) until the answer fits, and names
  them in `droppedForBudget` with a `budgetNote`. Declare what came by default before what the
  caller asked for, and keep the core an everyday call depends on (get_squad's availability,
  get_training's currentWeek) out of the list.
- **Core still over budget, return next to nothing plus a hint.** Replace the payload with a
  minimal envelope: `truncated: true`, how big the answer would have been, and a `hint` naming
  the exact arguments of *this* tool that get the data efficiently. Example:
  `{"truncated": true, "wouldBeChars": 262144, "hint": "page with limit/offset (max 200) or
  filter by skills/ageMin/ageMax/specialty/onlyOpen; pass full only for a few rows"}`.
  A generic "too large" is not a hint; `McpServerToolsTest` checks every hint names an argument.
- **Bulk data goes out as a download, never in the answer.** Where a script needs everything at
  once, a tool takes a `bulk` argument, puts the full data into `McpDownloads` and answers with a
  small summary plus the `download` path; the script fetches it with an HTTP GET on the MCP
  endpoint (`/mcp/download/<id>`, same Origin/Host guards, newest 20 in memory). Nothing ever
  passes the budget, and nothing relies on the client saving big answers to a file.
  `search_transfers` (the hattrick screening script), `get_squad`, `get_player_history`,
  `get_team_transfers`, `list_scout`, `get_matches`, `get_series`, `get_training` and
  `analyze_opponent` have it, all but the first through `McpDownloads.bulk` (whole answer to
  the file, list cut to a preview, `fileOnly` parts left out of the answer) or `bulkSections`
  (lists inside sections, e.g. `ratings.matches`). `get_player_transfer_history` has none: one
  player per call, and a squad-wide file would cost a CHPP request per player. Add it only where a script needs
  it, never speculatively. `estimate_market_value` (comparables by A/B skill) and `get_match`
  (event rows) always answer that way, by the user's call: their full lists are what gets
  reasoned over, and no default size kept them under budget.
- **New or changed tools** declare their budget and are measured against a real database (the
  headless server on a copy of the dev DB, default arguments) before they ship. `McpBudgetTest`
  covers the mechanism: dropped sections, the envelope, nothing partial leaking through.

#### Headless server

`core.HeadlessMain` runs HO without a window and serves only the MCP tools, e.g. on a Linux box.
Same jar, different main class. `./gradlew headless` (createVersion + `headlessTar`) packs the
distribution plus `utils/buildResources/ho-headless.sh` into `build/headless/HO-headless-<version>.tar.gz`;
`./gradlew docker [-PdockerPlatform=linux/amd64]` (createVersion + `dockerImage`) builds the image
`ho-headless:<version>` from `utils/buildResources/Dockerfile`, with the distribution as the build context:
workdir `/opt/ho` (so `prediction/` and `truststore.jks` resolve), data under the `/data` volume via
`-Dho.dbParentFolder`, and `-Dho.mcpBind=0.0.0.0`, the only non-loopback bind, because the host
cannot reach a container's loopback (publish with `-p 127.0.0.1:8765:8765`).
`utils/buildResources/docker-compose.yml` (Compose v2, `docker compose`) runs it with the data folder bind-mounted from `./data`, as the
owner of that folder (`HO_UID`/`HO_GID`, default 1000). The image's own `ho` user only fits named
volumes. Fatal DB errors (lock held, no write access) log and exit when headless
(`DBManager.exitIfHeadless`) instead of opening their dialog. User-facing steps are
in README.md, "Headless MCP server". The script boils down to:

```bash
java -Djava.awt.headless=true -cp HO.jar core.HeadlessMain authorize   # once: CHPP PIN flow + first download
java -Djava.awt.headless=true -cp HO.jar core.HeadlessMain [--port 8765] [--user 0] [INSTALLED] [DEBUG]
```

- `authorize` prints the Hattrick authorization URL and reads the code from stdin (CHPP's
  out-of-band flow, no browser needed on the server). The token is stored the way `OAuthDialog`
  stores it. It then runs the first team download, which `download_team` refuses on an empty DB.
  With several teams and no picker it takes the primary team, or `-Dho.teamId=<id>`.
- Loopback only, no authentication: reach it through `ssh -L 8765:localhost:8765 server`.
- Publish: `./gradlew dockerPush` builds amd64+arm64 and pushes `docker.rayba.de/ho-headless:<version>`
  and `:latest` (needs a prior `docker login docker.rayba.de`, done by the user). Run Gradle on JDK 17
  (`JAVA_HOME=~/.sdkman/candidates/java/17.0.15-sapmchn`): a newer default JDK fails Gradle 8.13
  with "Type T not present". The server then updates with `docker compose pull && docker compose up -d`.
- `database_backup` (`core.mcp.BackupTools`) restores by exiting, not by swapping the open database:
  HOVerwaltung, UserParameter, TrainingManager and the tables all cache what they loaded. The tool
  backs up into `backups/pre-restore/` and writes `db/restore-pending.properties`;
  `RestoreHelper.applyPending` runs first in `HeadlessMain`, before anything opens the DB, and rolls
  back unless the restored DB opens. It relies on Docker's `restart: unless-stopped`; desktop HO refuses.
- HSQLDB is single-process: desktop HO and the server must not open the same DB at the same time.
- The JVM time zone must be the one the DB was written in: `HODateTime.toDbTimestamp` stores local
  wall-clock time. The image sets `TZ=Europe/Berlin` (the desktop's zone); under UTC every moved-over
  timestamp reads hours off.
- Code the tools reach must not build `HOMainFrame`: under `java.awt.headless` that throws
  `HeadlessException`. Status-line updates go through the static `HOMainFrame.setInformation(...)`
  family (no-ops headless), dialog parents and panel refreshes through `HOMainFrame.shown()`
  (null headless), and CHPP errors stay behind `Connector.setSilentDownload`, which `HeadlessMain`
  keeps on. `HOMainFrameHeadlessTest` covers the status line; the test JVM runs headless.

### Where user data lives

`UserManager.setDBParentFolder()` picks the location, and it depends on how HO was launched.
`HO.main` sets `portable_version = true` by default; passing `INSTALLED` as an argument flips it.

| Mode | Location |
|---|---|
| portable (default, and any dev/jar run) | `System.getProperty("user.dir")` — i.e. the working directory |
| installed, macOS | `~/Library/Application Support/HO` |
| installed, Linux | `~/.ho` |
| installed, Windows | `%AppData%/HO` |

Users are described in `users.json` under that folder; each user gets its own DB subfolder, which is
why the multi-user login dialog appears before anything else at startup.

## Conventions

- Lombok is applied via the `io.freefair.lombok` plugin — annotations work without an explicit
  dependency declaration.
- Java, Kotlin and Groovy coexist in `src/main/java` (~880 `.java`, ~28 `.kt`, plus Groovy under
  `prediction/`). `sourceSets` deliberately maps that one directory as both a source and a resource
  root, so non-`.java` files next to classes are packaged.
- Much of the domain vocabulary is legacy German: `HOVerwaltung` (administration), `LigaTable`
  (league), `FaktorenTable` (factors), `XMLSpielplanParser` (fixture list), `getXtraDaten`,
  `sprachDatei` (language file). Keep the existing names when touching that code; renaming ripples
  into DB column names and persisted config keys.
- Tests use JUnit 5 with AssertJ (`src/test/java`, ~42 files). They are unit tests over pure logic —
  there is no harness for the Swing UI or for live CHPP calls.
- Logging goes through `HOLogger.instance()`, not `System.out`. Uncaught exceptions land in
  `core.util.ExceptionHandler`.
- **No new diagnostics, and clean up old ones where you can.** A change must not add compiler,
  inspection or Sonar findings, warnings included. Fix them in the same change and do not dismiss
  them as style noise. Before committing, check what the branch introduced against its base
  (with Hybe: `diagnostics_tool` check with `sinceRef: master`) and commit only at 0. A rewritten
  file counts all of its lines as changed, so compare with the base before calling a finding
  pre-existing. Findings that already exist in the code you touch get fixed too when the fix is
  local and safe. Leave out what would ripple into DB columns, persisted keys or the legacy
  German names (see above), and say what you left out.
