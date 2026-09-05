# Team download over MCP — design

`download_team`: refresh HO's local database from CHPP on the agent's request, without a human
clicking Download and without any modal dialog opening on the tool's path.

The problem it solves is already live. Every read tool answers from `DBManager`/`HOVerwaltung`, so
after the user fires a player in Hattrick, `get_squad` keeps returning the old squad and `get_team`
keeps reporting a salary consistent with it — until someone clicks Download in HO's UI. The
downstream `~/hattrick` session hit exactly this and had no way out from the tool surface.

## Goals

- One new tool, `download_team`, doing what the Download dialog's **team data** tick does: the CHPP
  fetch, the HRF parse, the model store, and the sub-skill recalculation that follows it.
- No modal dialog can open on that path, ever. Not the save chooser, not an error box.
- The Download button's behaviour is unchanged — including the parts of it that look like bugs.
- The agent is told what the download changed underneath it, in particular the lineup.

## Non-goals

- Matches, match archive, fixtures, hall of fame, youth data, world details, NT download. Those are
  the other ~150 lines of `DownloadDialog.startDownload` and each carries its own filter settings.
  A later `download_matches` can follow the same split; this spec does not do it.
- Writing an `.hrf` file. The manual download keeps doing it; the tool never does (§1.3).
- Any scheduling or auto-refresh. The agent asks, or nothing happens.
- Fixing the lineup clobber (§1.6). It predates this work and applies to every manual download too.
  This spec reports it; it does not change it.

## 1. Constraints discovered

Established with `impact_analysis` / `search_tool(references)` over the current tree, not from
reading call sites by eye.

### 1.1 The blast radius is five methods with one caller each

| Symbol | Callers |
|---|---|
| `OnlineWorker.getHrf` | `DownloadDialog.startDownload` — only |
| `OnlineWorker.saveHRFToFile` | `OnlineWorker.getHrf` — only |
| `ConvertXml2Hrf.createHrf` | `OnlineWorker.getHrf` — only |
| `OnlineWorker.resolveTransferListings` | `DownloadDialog.startDownload` — only |
| `HOModel.calcSubskills()` | `DownloadDialog.startDownload` — only |

Across ~880 Java files nothing else touches any of them. The split in §3 cannot break a caller
outside `OnlineWorker` and `DownloadDialog`, and no test covers them today (§7).

### 1.2 Five modal dialogs sit on the download path

| Where | Trigger |
|---|---|
| `ConvertXml2Hrf.java:78` `TeamSelectionDialog` | no stored team id **and** the account has ≥2 teams |
| `OnlineWorker.java:72` `Helper.showMessage` | `createHrf` threw `IOException` |
| `OnlineWorker.java:80` `JOptionPane` | HRF contains `playingMatch=true` |
| `OnlineWorker.java:84` `JOptionPane` | HRF contains `NOT AVAILABLE` |
| `OnlineWorker.java:93` `Helper.showMessage` | `HRFStringParser.parse` returned null |
| `OnlineWorker.java:1044` `askForHRFPath` (`JFileChooser`) + `:1054` overwrite confirm | see §1.3 |

The first is unreachable on an established install — `teamId` is stored after the first ever
download. The rest are all reachable in normal operation.

### 1.3 The save chooser cannot be switched off from settings

`saveHRFToFile` opens the chooser when **any** of three conditions holds (`OnlineWorker.java:1042`):
`showHRFSaveDialog`, a missing or non-directory path, or **the target file already exists**. The
filename is `<teamId>-YYYY-MM-DD.hrf` (`OnlineWorker.java:1080`) — one per calendar day. So the
second download on any given day opens the chooser regardless of the flags. For an agent that
refreshes more than once a day that is the normal case, not an edge case.

Neutralising the flags is therefore not a fix. The tool must not enter `saveHRFToFile` at all.

### 1.4 The download already runs on the EDT — that is why the dialogs are fatal here

`DownloadDialog.actionPerformed` calls `startDownload()` directly (`DownloadDialog.java:88`), so
HO's UI already freezes for the ten-to-twenty seconds a manual download takes. The tool inherits
that and adds nothing new.

What is new is the consequence of a dialog. `McpHttpServer` runs handlers on a single-threaded
executor by design (`McpHttpServer.java:110`), and the tool must run on the EDT (§2). A modal dialog
there blocks the EDT *and* the MCP server, and nothing on the agent's side can dismiss it: the call
never returns and HO appears hung. In the UI the same dialog is merely a prompt.

### 1.5 Nothing refreshes HO's tabs

`RefreshManager.instance().doReInit()` is called by `actionPerformed` **after** `startDownload()`
returns (`DownloadDialog.java:88`), outside the download itself. A tool that only performs the
download leaves every HO tab showing the old squad while the database holds the new one — worse than
not refreshing at all, because the user's screen now disagrees with the agent. The tool must call
`doReInit()` itself.

### 1.6 The HRF overwrites the stored lineup, and "dirty" is not detectable

`HRFStringParser.java:177` calls `hoModel.storeLineup(...)` from the HRF's `[lineup]` block — that is
the match order **Hattrick currently holds**. `HOModel.storeLineup` (`HOModel.java:210`) sets the
live lineup and writes it straight to the database. So a download replaces HO's lineup with the
server's, and a lineup the user built in HO but never uploaded is gone.

Three call sites write that lineup — `HRFStringParser:177`, `LineupTools.apply:287`,
`MatchAndLineupSelectionPanel:332` — and none records where it came from. There is no dirty flag and
no provenance, so "refuse when the local lineup is unsaved" is not implementable. §4 snapshots the
lineup before the download and reports the change afterwards instead, which needs no new state.

### 1.7 Everything below `getHrf` is already dialog-free

`HRFStringParser` and `HOModel` contain no `JOptionPane`, `Helper.showMessage` or `JDialog`. Once the
dialogs listed in §1.2 are lifted out of `getHrf` and `saveHRFToFile` is skipped, the path is silent.

### 1.8 Per-session caps are the house pattern

`mcpSearchesPerSession` and `mcpChppWritesPerSession` (`UserParameter.java:117`, `:119`) are enforced
in `TransferSearchService.java:88` and `ChppWriteService.java:67` and surfaced in `McpServerPanel`. A
team download is roughly a dozen CHPP requests — teamdetails, logo, club, leaguedetails,
worlddetails, players, avatars, youth players, economy, sometimes transfers — making it the most
expensive single call on the tool surface. It gets its own cap (§6).

### 1.9 A payoff elsewhere

The previous spec records (`2026-09-02-chpp-write-operations-over-mcp-design.md` §1.5) that after
`set_training` succeeds "the local snapshot is stale until the next full download", and that the tool
flags the staleness rather than faking an update. `download_team` is that next full download, and the
agent can now perform it. No change to `set_training` is needed; its warning simply becomes
actionable.

## 2. Layering

```
download_team (TeamTools)          ── argument checks, cap, lineup snapshot, JSON
   └─ LineupTools.onEdt            ── existing helper, EDT hop + failure propagation
        └─ OnlineWorker.downloadTeamData()   ── NEW: silent, throws DownloadFailed
             └─ ConvertXml2Hrf.createHrf → HRFStringParser.parse → HOModel.storeModel
        └─ HOModel.calcSubskills()
        └─ OnlineWorker.resolveTransferListings()   ── opt-in
        └─ RefreshManager.doReInit()

getHrf(JDialog)                    ── unchanged signature and behaviour
   └─ OnlineWorker.downloadTeamData()
   └─ the five dialogs, now in the catch
   └─ saveHRFToFile
```

`downloadTeamData` is **silent**, not headless: it still writes HO's info line and calls
`backupRealGameSettings()` on the lineup panel, so it must run on the EDT. That is the deliberate
line — no *modal* dialog, but the progress text in HO's window stays, because a user watching HO
while an agent downloads should see it happening.

## 3. The `OnlineWorker` split

### 3.1 New types

```java
/** Why a team download produced nothing. The dialog wording stays in the UI layer. */
public enum Failure { NO_TEAM, CHPP_ERROR, PLAYING_MATCH, NO_DATA, UNPARSEABLE }

public static class DownloadFailed extends Exception {
    public final Failure reason;
    ...
}

/** What one team download produced. */
public record TeamDownload(String hrf, HOModel model, boolean becameCurrent) {}
```

One exception type with a reason code, not one type per outcome. The reason exists because `getHrf`
has to reproduce five *different* existing responses (§3.3) and because the tool wants something
machine-readable rather than a string to match on. The dialog titles and message types stay in
`getHrf`, where they already are — they are UI, not service.

### 3.2 `downloadTeamData`

Everything `getHrf` does today except the dialogs and the file save, in the same order:

```java
public static TeamDownload downloadTeamData() throws DownloadFailed {
    final var hov = HOVerwaltung.instance();
    final String hrf;
    try {
        hrf = ConvertXml2Hrf.createHrf();
    } catch (IOException e) {
        throw new DownloadFailed(Failure.CHPP_ERROR, <the existing "Error converting xml 2 HRF" text>, e);
    }
    if (hrf == null)                        throw new DownloadFailed(Failure.NO_TEAM, ...);
    if (hrf.contains("playingMatch=true"))  throw new DownloadFailed(Failure.PLAYING_MATCH, ...);
    if (hrf.contains("NOT AVAILABLE"))      throw new DownloadFailed(Failure.NO_DATA, ...);

    final var homodel = HRFStringParser.parse(hrf);
    if (homodel == null)                    throw new DownloadFailed(Failure.UNPARSEABLE, ...);

    homodel.storeModel();
    homodel.setFixtures(hov.getModel().getFixtures());
    homodel.setFormerPlayers(DBManager.instance().loadAllPlayers());
    final var becameCurrent = HOVerwaltung.isNewModel(homodel);
    if (becameCurrent) {
        hov.setModel(homodel);
        Objects.requireNonNull(HOMainFrame.instance().getLineupPanel()).backupRealGameSettings();
    }
    return new TeamDownload(hrf, homodel, becameCurrent);
}
```

`becameCurrent` is not decoration: `isNewModel` (`OnlineWorker.java:102`) can be false — a re-download
of the same HRF stores the model but does not make it live. The tool has to report which happened,
or it would claim a refresh that did not occur.

### 3.3 `getHrf`, behaviour preserved exactly

```java
public static boolean getHrf(JDialog parent) {
    try {
        saveHRFToFile(parent, downloadTeamData().hrf());
        return true;
    } catch (DownloadFailed e) {
        return report(parent, e);
    } finally {
        HOMainFrame.instance().setInformation(getLangString("HRFErfolg"), 0);
    }
}
```

`report` switches on `e.reason` and reproduces today's response case for case:

| Reason | Dialog today | Returns today |
|---|---|---|
| `NO_TEAM` | none — silent early `return false` (`OnlineWorker.java:64`) | `false` |
| `CHPP_ERROR` | `setInfoMsg(FEHLERFARBE)` + `Helper.showMessage`, title *Fehler*, ERROR | `false` |
| `PLAYING_MATCH` | `resetInformation()` + `JOptionPane`, `NO_HRF_Spiel`, INFORMATION | **`true`** |
| `NO_DATA` | `resetInformation()` + `JOptionPane`, `NO_HRF_ERROR`, INFORMATION | **`true`** |
| `UNPARSEABLE` | `setInfoMsg` + `Helper.showMessage`, *Importfehler*, ERROR | **`true`** |

The three `true` rows are why the reason code exists. They look wrong — after a mid-match skip or a
parse failure nothing was stored, yet `DownloadDialog` treats `bOK` as true and carries on
downloading matches, fixtures and prices. **That oddity is preserved deliberately.** This refactor's
contract is "the button behaves identically"; changing when `bOK` flips is a separate decision with
its own testing, and silently folding it into a refactor is how a regression gets shipped. Recorded
here so the next person finds it on purpose rather than by surprise.

The tool does not inherit the oddity: to it, all five reasons are errors, because in all five cases
nothing was stored (§4.3).

## 4. `TeamTools.download_team`

Lives in `TeamTools.tools()` beside `get_team`. `TeamTools` already makes CHPP calls
(`get_opponent_squad`), so "read tools" there has never meant "offline"; what is new is that this one
writes the local database.

### 4.1 Schema

```
download_team
  resolvePrices : boolean, optional, default false
```

Description, in substance: *Re-downloads the team from Hattrick and rewrites HO's local database —
squad, finances, staff, stadium, team spirit — then recalculates sub-skills. Every other tool reads
that database, so call this when the user has done something in Hattrick that HO cannot yet see (a
transfer, a sacking, a new week). Roughly a dozen CHPP requests and ten to twenty seconds; HO's
window is frozen for the duration. It also replaces HO's stored lineup with the match order Hattrick
currently holds — see `lineupReplaced` in the result.*

`resolvePrices` runs `OnlineWorker.resolveTransferListings()` (capped by the existing
`mcpResolveCap`) for the sale prices of watched market players. Off by default: it is a burst of
extra per-player CHPP lookups that has nothing to do with why the tool was called.

### 4.2 Body

Inside a single `LineupTools.onEdt(...)`:

1. Check and spend the cap (§6). Refuse before any CHPP traffic.
2. Snapshot the current lineup — `LineupTools.describe(model.getCurrentLineupTeam().getLineup(), …)`.
   `describe` is currently private and becomes package-private; both classes are in `core.mcp`.
3. `OnlineWorker.downloadTeamData()`.
4. `model.calcSubskills()` on the resulting model — without it the training tools read sub-skills
   computed against the previous HRF (`DownloadDialog.java:400` does the same).
5. `OnlineWorker.resolveTransferListings()` if `resolvePrices`.
6. `RefreshManager.instance().doReInit()` (§1.5).
7. Snapshot the lineup again and compare.

Result:

```json
{
  "hrfId": 1487,
  "downloadedAt": "2026-09-05 14:02:00",
  "becameCurrent": true,
  "players": 38,
  "subskillsRecalculated": true,
  "pricesResolved": null,
  "lineupReplaced": true,
  "previousLineup": { ... }
}
```

`lineupReplaced` is true when the two snapshots differ, and `previousLineup` then carries the one
that was overwritten, so an agent that had just set a lineup can put it back. This is the honest
answer to §1.6: the clobber cannot be predicted, but it can be reported with the evidence attached.
When nothing changed, `lineupReplaced` is false and `previousLineup` is `null` — the server
serializes nulls on purpose (`McpHttpServer.java:50`), so an absent field is never confused with an
unknown one.

### 4.3 Failures

Every `DownloadFailed` becomes an `McpToolException`, which the transport renders as
`isError: true` (`McpHttpServer.java:260`). Hattrick's own wording is kept, with the reason spelled
out so the agent can tell "try again later" from "this will not work":

- `PLAYING_MATCH` — *Hattrick is playing your match right now and will not export team data. Try
  again after it finishes.* Retryable.
- `NO_DATA` — Hattrick returned no data. Retryable.
- `CHPP_ERROR` — the underlying message, verbatim. Usually not retryable.
- `UNPARSEABLE` — HO could not read what came back. Not retryable; the download is worth reporting
  to the user.
- `NO_TEAM` — no team is selected in HO. Not retryable from the agent side.

The cap is spent in all five cases *except* when the refusal happens before the call: a
cap-exhausted refusal costs nothing and names the Options → MCP server panel, matching the search
and write caps.

## 5. What the tool deliberately does not refresh

Matches, fixtures, the league table's played results, hall of fame and youth data all come from the
other branches of `startDownload` and stay stale after `download_team`. The tool's description says
so, and `get_upcoming_matches` remains a manual-download job. Better a tool with a stated edge than
one that quietly reproduces half of a 150-line method.

## 6. Settings

New `UserParameter.mcpDownloadsPerSession`, default **10** — half the house default of 20 for the
other two caps, because one download costs roughly a dozen CHPP requests where a search costs one.
Reset per HO session like the others.

`UserParameter` persists through a plain string map (`UserParameter.java:462`), so this needs **no
`DBVersion` bump and no `DBUpdater` case** — the usual rule for adding a column does not apply here.

`McpServerPanel` gets one more `addField` row and one more line in `saveFields`, writing to both
`instance()` and `temp()` like its neighbours. Two new translation keys,
`ls.options.mcp.downloadcap` and its `.tt` tooltip — new keys are the one edit allowed against the
POEditor-owned language files.

## 7. Testing

There is no existing coverage of `OnlineWorker`, `DownloadDialog` or `ConvertXml2Hrf`, and none is
added: they need a live CHPP session and a Swing frame, which the suite has no harness for.

`TeamToolsTest` covers the tool surface, and its `tools_areTheEightReadTools` case asserts the exact
tool list, so it must be updated rather than merely extended:

- rename to reflect nine tools and assert `download_team` is present;
- `download_team` takes no *required* arguments and offers `resolvePrices`;
- `everyToolHasANonEmptyDescription` covers the new one for free.

Worth a test because it is pure and it is the part most likely to rot: the mapping from `Failure` to
the message and retryability in §4.3. Extract it as a static function over the enum and assert all
five constants are handled — an unhandled constant added later should fail the build, not return a
blank error to an agent.

Manual verification, since the important behaviour is exactly what unit tests cannot reach:

1. `download_team` twice in one day. The second call must return normally — this is the case §1.3
   says would open the file chooser today.
2. During the call, HO's window shows the progress line and no dialog appears.
3. After it, HO's squad tab shows the new squad without a manual refresh (§1.5).
4. Set a lineup in HO, do not upload it, call `download_team`: `lineupReplaced` is true and
   `previousLineup` holds what was there.
5. Click Download in HO's UI and confirm it behaves as before, including the HRF save dialog.

## 8. Files touched

| File | Change |
|---|---|
| `core/net/OnlineWorker.java` | `Failure`, `DownloadFailed`, `TeamDownload`, `downloadTeamData()`; `getHrf` reduced to the dialog mapping (§3) |
| `core/mcp/TeamTools.java` | `download_team` tool and its handler (§4) |
| `core/mcp/LineupTools.java` | `describe` private → package-private |
| `core/model/UserParameter.java` | `mcpDownloadsPerSession` field, `map.put`, read-back |
| `core/option/McpServerPanel.java` | one settings row |
| `src/main/resources/language/English.properties` | two new keys |
| `src/test/java/core/mcp/TeamToolsTest.java` | tool-list assertion, new cases (§7) |
| `docs/mcp-tools.md` (in `~/hattrick`) | the new tool, and the §1.6 lineup hazard beside the existing `apply_lineup` note |

No file outside this list has a caller of anything being changed (§1.1). No database migration (§6).
