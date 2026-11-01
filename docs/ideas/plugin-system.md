# Idea: a plugin system, with the MCP server as the first plugin

**Status:** idea, not decided (2026-11-01). Nothing is built. If it gets picked up, check the
numbers below against the code first, because they were taken from `b4f11eff`.

## Goal

- HO loads plugin jars automatically from a `plugins/` folder in the user folder.
- The MCP server becomes such a plugin, in its own git repository.
- **HO has zero dependencies on the plugin:** no `core.mcp` import, no `mcp*` field, no MCP
  panel, no MCP naming. The plugin depends on HO and compiles against HO's jar `compileOnly`;
  that direction is unavoidable.
- Gains: the fork's diff against upstream shrinks, and upstream merges stop conflicting in
  `core.mcp`. Other tools could use the same mechanism.

## Proposed shape

1. **Loader.** At startup HO scans `plugins/*.jar`, opens each in its own `URLClassLoader` and
   finds `HOPlugin` implementations via `java.util.ServiceLoader`.
   - Hooks: start, model loaded, shutdown. Optional: an options tab and headless services.
   - A plugin declares the HO and API version it needs.
   - HO catches `LinkageError` while loading, logs it and disables the plugin, instead of
     failing later with `NoSuchMethodError`.
   - It loads only from a local folder the user controls, never downloads.
2. **Generic headless host.** `HeadlessMain`, `Connector.authorizeOnConsole` and `RestoreHelper`
   stay in HO as "HO without a window, running the plugins' services". Starting the MCP server
   moves into the plugin.
3. **Plugin settings.** A plugin stores its own keys, prefixed with its id, in HO's configuration
   table.
4. **DB lock in the API.** HO's DB layer shares one JDBC statement, so HO offers the lock that is
   `McpHttpServer.toolLock` today, and GUI and plugins use the same one.
5. **CHPP gateway instead of plugin-specific calls in HO.**
   - **The risk:** every plugin runs under HO's consumer key, which all HO users share. A
     misbehaving plugin can get the key blocked for everyone, so HO controls *how* plugins
     call CHPP, without knowing *which* calls.
   - **The entry point:** one generic request, `chpp.request(file, version, params)`. HO signs it,
     and the plugin never sees the token.
   - **Read or write:** HO decides from a small table of CHPP files and actions.
   - **Caps in core:** request and write limits per plugin. A plugin cannot raise them.
   - **Scopes:** the plugin's manifest declares the OAuth scopes it needs. HO requests them when
     the user authorizes and shows them to the user. Without such a plugin, HO requests no write
     scopes, which keeps upstream's behaviour.
   - **Audit log:** each call is logged with the plugin, the file, read or write, and the time.
   - Our own rules hold either way: the server never bids, and the transfer-search cap is never
     worked around.

## Inventory (as of `b4f11eff`)

**The plugin itself:** `core.mcp` has ~11,500 lines in 18 files, plus 19 test files with ~5,500
lines.

**What HO knows about the MCP (to cut):**
- `HO.java:251-254` starts the server.
- `HOMainFrame.java:308` stops it.
- `OptionenDialog.java:104` adds `McpServerPanel`.
- `HeadlessMain` imports `McpHttpServer`.
- `UserParameter` has 7 `mcp*` fields.
- A few javadoc mentions: `ChppError`, `RatingCalibrationTable`, `ScoutEintrag`,
  `OnlineWorker`, `Helper`.

**What the plugin uses from HO (its effective API):** 85 classes in ~30 packages.
- Used more than 30 times: `HODateTime`, `DBManager`, `Player`, `Lineup`, `PlayerSkill`,
  `HOModel`, `TransferSearchQuery`, `ChppWriteService`, `XMLTransferSearchParser`,
  `TransferListing`.
- Also reached: GUI classes, namely `HOMainFrame`, `RefreshManager`, `GroupTeamFactory`,
  `MatchesStatisticsPanel` and teamanalyzer `RatingUtil`. Untangling these is the main open
  technical question.

**Fork additions outside `core.mcp`:** 29 new and 84 changed files, many of them unrelated GUI
work.

| Part | Where it goes |
|---|---|
| Transfer search (`TransferSearchQuery`/`Service`, `XMLTransferSearchParser`, `TransferSearchPanel`, `PlayerFilterForm`, `ScoutList`, `ScoutFilter`), `TransferListing`(+`Table`, `Resolver`), `RatingCalibration`, `OrderLimits`, the new `OnlineWorker` steps | stays in HO: the GUI or the download uses it |
| `ChppWriteService`, the write calls in `Connector` (training, match order, challenges, bids, current bids), `XMLChallengesParser`, `XMLCurrentBidsParser`, `XMLTrainingEventsParser`, `TransferBid` | moves into the plugin, behind the CHPP gateway; `TRANSFERBID` data goes to the plugin's own store |
| `mcpSearchesPerSession`, `mcpChppWritesPerSession`, `mcpScoutResolvedThrough`, `mcpNoSaleFoundReset` | stay in HO, renamed |
| `mcpServerEnabled`, `mcpServerPort`, `mcpDownloadsPerSession`, `McpServerPanel` | move into the plugin |
| Tables `TRANSFERLISTING`, `RATINGCALIBRATION` | stay in HO's schema |

**Persisted keys:** the key is the field name, e.g. `saveUserParameter("mcpNoSaleFoundReset")`.
A rename therefore needs a key migration, or the field keeps its old key string.

## Caveats

- **The fork stays.** The plugin removes only the MCP part of the fork's diff. Transfer search,
  the listing tables and calibration are fork features, not upstream. The fork disappears only
  if ho-dev also accepts those, not just the loader. Ask ho-dev first: HO reportedly once had a
  plugin system and folded the plugins in as modules.
- **Compile breaks instead of merge conflicts.** The plugin still uses HO internals, so HO
  refactors break it at compile time. CI in the plugin repo that builds against HO master
  catches that early.
- **Test fixtures.** HO publishes no test fixtures (`HOModelBuilder` and friends). The plugin
  repo needs a published test artifact or its own fixtures.
