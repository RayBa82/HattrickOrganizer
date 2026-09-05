# Team download over MCP Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add an MCP tool `download_team` that refreshes HO's local database from Hattrick on the agent's request, with no modal dialog able to open on its path.

**Architecture:** `OnlineWorker.getHrf` is split in two. The download itself — CHPP fetch, HRF parse, model store — moves into a new silent `OnlineWorker.downloadTeamData()` that throws a typed `DownloadFailed` instead of showing anything; `getHrf` keeps its exact signature and behaviour and becomes only the five dialogs plus the `.hrf` file save. The new `TeamTools.download_team` runs the whole thing on the EDT via the existing `LineupTools.onEdt` helper, wrapped in `Connector.setSilentDownload(true)` so that `Connector`'s own error and re-authorisation dialogs stay shut, then recalculates sub-skills, refreshes HO's tabs, and reports what the download changed underneath the agent — in particular whether it overwrote the stored lineup.

**Tech Stack:** Java 17, Gson, Swing (EDT), JUnit 5 + AssertJ, Gradle 8.13 wrapper.

**Spec:** `docs/superpowers/specs/2026-09-05-team-download-over-mcp-design.md`

## Global Constraints

- Java 17 source/target. Do not raise the language level.
- **No modal dialog may be reachable from the `download_team` path.** The MCP server runs handlers on a single-threaded executor and the tool runs on the EDT; a modal dialog there blocks both and the call never returns. `downloadTeamData()` is **not** dialog-free on its own — three sources sit underneath it, and each is closed separately in Task 4:
  - `Connector.getCHPPWebFile` opens `OAuthDialog` on an HTTP 401 (`Connector.java:740`) and a `JOptionPane` on every other failure (`Connector.java:765`). Both are gated on `Connector.silentDownload` alone, and `downloadTeamData()` makes roughly a dozen requests through that one method. Closed by wrapping the handler in `Connector.instance().setSilentDownload(true)` — the same save/restore `ChppWriteService.java:154` and `TransferSearchService.java:124` already use.
  - `ConvertXml2Hrf.createHrf():79` opens a modal `TeamSelectionDialog` when HO holds no team id and the CHPP account has more than one team. `silentDownload` does **not** gate this one, so the handler refuses up front rather than reaching it.
  - `DBManager.java:150` shows a "Fatal DB Error" box if the embedded HSQLDB write fails. Left alone deliberately: HO is unusable at that point either way, and suppressing it would hide a corrupt database from the user.
- **The silent flag belongs to the MCP handler, not to `downloadTeamData()`.** Setting it inside `downloadTeamData()` would strip the Download button's error dialogs too, which breaks Task 2's contract.
- **`getHrf(JDialog)` keeps its exact signature and its exact observable behaviour**, including the three failure cases that return `true` even though nothing was stored. That oddity is preserved deliberately (spec §3.3).
- The tool never writes an `.hrf` file — it must not enter `saveHRFToFile` at all (spec §1.3).
- New per-session cap `UserParameter.mcpDownloadsPerSession`, default **10**.
- **No database migration.** `UserParameter` persists through a plain string map, so no `DBManager.DBVersion` bump and no `DBUpdater` case (spec §6).
- Translation keys: only *new* keys may be added to `src/main/resources/language/English.properties`. Never edit existing ones — POEditor owns that file and `poeditorPull` overwrites it on every build.
- Every build is a full clean rebuild (`compileJava.dependsOn(clean)`); expect ~10s minimum turnaround. `poeditorPull` printing `{"response":{"status":"fail","code":"4011",...}}` without `POEDITOR_APIKEY` is expected and is not a failure.
- Gradle needs JDK 17 (sdkman). If `./gradlew` dies with "Type T not present", the wrong JDK is on `JAVA_HOME`.
- There is **no** test harness for Swing UI or live CHPP calls. Do not add tests for `OnlineWorker`, `DownloadDialog` or `ConvertXml2Hrf` — they cannot run.

## File Structure

| File | Change | Task |
|---|---|---|
| `src/main/java/core/model/UserParameter.java` | `mcpDownloadsPerSession` field + serialize + parse | 1 |
| `src/main/java/core/option/McpServerPanel.java` | one settings row | 1 |
| `src/main/resources/language/English.properties` | two new keys | 1 |
| `src/main/java/core/net/OnlineWorker.java` | `Failure`, `DownloadFailed`, `TeamDownload`, `downloadTeamData()`; `getHrf` reduced to `report()` | 2 |
| `src/main/java/core/mcp/TeamTools.java` | `DownloadAdvice` + `advice()` mapping | 3 |
| `src/test/java/core/mcp/TeamToolsTest.java` | mapping tests | 3 |
| `src/main/java/core/mcp/TeamTools.java` | `download_team` tool, handler, cap, snapshots | 4 |
| `src/main/java/core/mcp/LineupTools.java` | `describe` opened up and made ratings-optional; `onEdt` error text | 4 |
| `src/test/java/core/mcp/TeamToolsTest.java` | tool-list assertion, schema case | 4 |
| `docs/current.md` | new dated section | 5 |

---

### Task 1: The per-session download cap and its settings row

**Files:**
- Modify: `src/main/java/core/model/UserParameter.java` (three places: field near `:119`, `map.put` near `:463`, parse near `:715`)
- Modify: `src/main/java/core/option/McpServerPanel.java` (field declaration, `initComponents`, `saveFields`)
- Modify: `src/main/resources/language/English.properties:2558` (after `ls.options.mcp.writecap`)
- Test: none — see note below

**Interfaces:**
- Produces: `UserParameter.instance().mcpDownloadsPerSession` (`public int`, default `10`) — read by `TeamTools.checkAndCountDownload()` in Task 4.

**Note on testing:** this task is configuration plumbing with no logic of its own. Its correctness is verified by the build compiling and by Task 4's cap behaviour, which reads `mcpDownloadsPerSession`. This mirrors how `mcpChppWritesPerSession` and `mcpSearchesPerSession` were added — neither has a `UserParameter` test.

- [ ] **Step 1: Declare the field**

In `src/main/java/core/model/UserParameter.java`, find this block (around line 117):

```java
    /** How many transfer searches one HO session may issue in total. */
    public int mcpSearchesPerSession = 20;
    /** How many CHPP writes (training, match order, challenges, bids) one HO session may issue in total. */
    public int mcpChppWritesPerSession = 20;
```

Add immediately after `mcpChppWritesPerSession`:

```java
    /**
     * How many full team downloads one HO session may issue over MCP. Manual downloads from the
     * Download button do not count.
     * <p>
     * Half the other two caps because one download is roughly a dozen CHPP requests where a search
     * or a write is one.
     */
    public int mcpDownloadsPerSession = 10;
```

- [ ] **Step 2: Serialize it**

In the same file, find (around line 463):

```java
        map.put("mcpChppWritesPerSession", String.valueOf(mcpChppWritesPerSession));
        map.put("mcpResolveCap", String.valueOf(mcpResolveCap));
```

Insert between those two lines:

```java
        map.put("mcpDownloadsPerSession", String.valueOf(mcpDownloadsPerSession));
```

- [ ] **Step 3: Parse it back**

In the same file, find (around line 715):

```java
        mcpChppWritesPerSession = orDefault(getIntValue(values, "mcpChppWritesPerSession"), 20);
        mcpResolveCap = orDefault(getIntValue(values, "mcpResolveCap"), 200);
```

Insert between those two lines:

```java
        mcpDownloadsPerSession = orDefault(getIntValue(values, "mcpDownloadsPerSession"), 10);
```

- [ ] **Step 4: Add the two translation keys**

In `src/main/resources/language/English.properties`, find:

```properties
ls.options.mcp.writecap=CHPP writes per session
```

Insert immediately after it:

```properties
ls.options.mcp.downloadcap=Team downloads per session
ls.options.mcp.downloadcap.tt=How many full team downloads your assistant may run per HO session. One download is roughly a dozen Hattrick requests and freezes HO's window for ten to twenty seconds.
```

Do not touch any other line in this file.

- [ ] **Step 5: Add the Options field**

In `src/main/java/core/option/McpServerPanel.java`, find the field declarations near the top of the class:

```java
    private final JTextField writeCap = new JTextField(6);
```

Add after it:

```java
    private final JTextField downloadCap = new JTextField(6);
```

If the existing declaration differs in width or modifiers, copy the `writeCap` line exactly and change only the name.

- [ ] **Step 6: Add the row to `initComponents`**

In the same file, find (in `initComponents`, around line 53):

```java
        addField(c, ++c.gridy, "ls.options.mcp.writecap", writeCap,
                String.valueOf(UserParameter.instance().mcpChppWritesPerSession));
```

Add immediately after it:

```java
        // addField takes no tooltip - set it here, the way the enabled checkbox does.
        downloadCap.setToolTipText(TranslationFacility.tr("ls.options.mcp.downloadcap.tt"));
        addField(c, ++c.gridy, "ls.options.mcp.downloadcap", downloadCap,
                String.valueOf(UserParameter.instance().mcpDownloadsPerSession));
```

- [ ] **Step 7: Persist it in `saveFields`**

In the same file, replace the body of `saveFields` (around line 109) so it reads:

```java
    private void saveFields() {
        final var portValue = parse(port, 8765);
        final var capValue = parse(searchCap, 20);
        final var writeCapValue = parse(writeCap, 20);
        final var downloadCapValue = parse(downloadCap, 10);
        final var resolveCapValue = parse(resolveCap, 200);
        for (final var p : new UserParameter[]{UserParameter.instance(), UserParameter.temp()}) {
            p.mcpServerPort = portValue;
            p.mcpSearchesPerSession = capValue;
            p.mcpChppWritesPerSession = writeCapValue;
            p.mcpDownloadsPerSession = downloadCapValue;
            p.mcpResolveCap = resolveCapValue;
        }
    }
```

Leave the javadoc above the method untouched.

- [ ] **Step 8: Compile**

Run: `./gradlew compileJava`
Expected: `BUILD SUCCESSFUL`. A `4011` POEditor line in the output is expected and is not a failure.

- [ ] **Step 9: Commit**

```bash
git add src/main/java/core/model/UserParameter.java \
        src/main/java/core/option/McpServerPanel.java \
        src/main/resources/language/English.properties
git commit -m "feat(mcp): add mcpDownloadsPerSession cap and its options row"
```

---

### Task 2: Split the download out of `OnlineWorker.getHrf`

**Files:**
- Modify: `src/main/java/core/net/OnlineWorker.java:51-118` (`getHrf`)
- Test: none — `OnlineWorker` needs a live CHPP session and a Swing frame, which the suite has no harness for (spec §7). Verification is a compile plus the manual check in Task 5.

**Interfaces:**
- Produces, all read by Tasks 3 and 4:
  - `public enum OnlineWorker.Failure { NO_TEAM, CHPP_ERROR, PLAYING_MATCH, NO_DATA, UNPARSEABLE }`
  - `public static class OnlineWorker.DownloadFailed extends Exception` with `public final Failure reason`, constructors `(Failure, String)` and `(Failure, String, Throwable)`
  - `public record OnlineWorker.TeamDownload(String hrf, HOModel model, boolean becameCurrent)`
  - `public static TeamDownload OnlineWorker.downloadTeamData() throws DownloadFailed`
- Consumes: nothing from earlier tasks.

**Why no behaviour may change here:** `getHrf` has exactly one caller, `DownloadDialog.startDownload:279` (`bOK = OnlineWorker.getHrf(this);`). Three of its five failure paths return `true` even though nothing was stored, and `DownloadDialog` then carries on downloading matches, fixtures and prices. That looks wrong and is preserved anyway — this task's contract is "the Download button behaves identically". Changing when `bOK` flips is a separate decision with its own testing.

**Why the silent flag is not set here:** `downloadTeamData()` is silent in the sense that *it* shows nothing; the code underneath it still can. `Connector` decides whether to show its own error and re-authorisation dialogs from `Connector.silentDownload`, and the Download button needs those. So `downloadTeamData()` leaves the flag alone and Task 4's handler sets it around the call. Do **not** add a `setSilentDownload` in this task.

**Why `hrf == null` still maps to `NO_TEAM` here:** with the flag on, a CHPP failure makes `getCHPPWebFile` return `""`, `createHrf` bails at `teamDetails.isEmpty()` and returns null — indistinguishable, at this level, from "no team selected". Telling the two apart needs `Connector.lastError()`, and doing it here would make the Download button pop an error dialog where it currently returns quietly (it has already shown one from inside `Connector`). Task 4 does that disambiguation on the MCP side instead.

- [ ] **Step 1: Add the three new types**

In `src/main/java/core/net/OnlineWorker.java`, immediately above the `getHrf` javadoc (around line 51), insert:

```java
	/** Why a team download produced nothing. The dialog wording stays in the UI layer. */
	public enum Failure { NO_TEAM, CHPP_ERROR, PLAYING_MATCH, NO_DATA, UNPARSEABLE }

	/**
	 * A team download that stored nothing, carrying a machine-readable reason.
	 * <p>
	 * One exception type with a reason code rather than one type per outcome, because
	 * {@link #getHrf} has to reproduce five different existing responses and because the MCP tool
	 * wants something to switch on rather than a string to match against.
	 */
	public static class DownloadFailed extends Exception {
		public final Failure reason;

		public DownloadFailed(Failure reason, String message) {
			this(reason, message, null);
		}

		public DownloadFailed(Failure reason, String message, Throwable cause) {
			super(message, cause);
			this.reason = reason;
		}
	}

	/** What one team download produced. */
	public record TeamDownload(String hrf, HOModel model, boolean becameCurrent) {
	}
```

- [ ] **Step 2: Add `downloadTeamData`**

In the same file, immediately after the block you just inserted, add:

```java
	/**
	 * Download the team from Hattrick, parse it and store it: everything {@link #getHrf} does apart
	 * from the dialogs and the .hrf file save, in the same order.
	 * <p>
	 * Silent, not headless. It still writes HO's info line and calls
	 * {@code backupRealGameSettings()} on the lineup panel, so it must run on the event dispatch
	 * thread. What it never does is open a modal dialog: on the MCP server's single handler thread
	 * that would block the EDT and the server together, with nothing on the agent's side able to
	 * dismiss it.
	 */
	public static TeamDownload downloadTeamData() throws DownloadFailed {
		final var hov = HOVerwaltung.instance();
		final String hrf;
		try {
			hrf = ConvertXml2Hrf.createHrf();
		} catch (IOException e) {
			throw new DownloadFailed(Failure.CHPP_ERROR, getLangString("Downloadfehler")
					+ " : Error converting xml 2 HRF. Corrupt/Missing Data : ", e);
		}
		if (hrf == null) {
			throw new DownloadFailed(Failure.NO_TEAM, "No team selected.");
		}
		if (hrf.contains("playingMatch=true")) {
			throw new DownloadFailed(Failure.PLAYING_MATCH, getLangString("NO_HRF_Spiel"));
		}
		if (hrf.contains("NOT AVAILABLE")) {
			throw new DownloadFailed(Failure.NO_DATA, getLangString("NO_HRF_ERROR"));
		}

		// Create HOModel from the hrf data
		final var homodel = HRFStringParser.parse(hrf);
		if (homodel == null) {
			throw new DownloadFailed(Failure.UNPARSEABLE, getLangString("Importfehler"));
		}

		// save the model in the database
		homodel.storeModel();
		homodel.setFixtures(hov.getModel().getFixtures());
		// Add old players to the model
		homodel.setFormerPlayers(DBManager.instance().loadAllPlayers());
		// Only update when the model is newer than existing
		final var becameCurrent = HOVerwaltung.isNewModel(homodel);
		if (becameCurrent) {
			hov.setModel(homodel);
			// reset value of TS, confidence in Lineup Settings Panel after data download
			Objects.requireNonNull(HOMainFrame.instance().getLineupPanel()).backupRealGameSettings();
		}
		return new TeamDownload(hrf, homodel, becameCurrent);
	}
```

- [ ] **Step 3: Replace `getHrf` with the dialog mapping**

In the same file, replace the whole of the existing `getHrf` method — from its javadoc `/**\n\t * Get and optionally save HRF` down to and including its closing brace — with:

```java
	/**
	 * Get and optionally save HRF.
	 * <p>
	 * Behaviour is unchanged: the download itself moved to {@link #downloadTeamData()} and what is
	 * left here is the dialogs that used to sit inline in it.
	 */
	public static boolean getHrf(JDialog parent) {
		try {
			saveHRFToFile(parent, downloadTeamData().hrf());
			return true;
		} catch (DownloadFailed e) {
			return report(parent, e);
		} finally {
			HOMainFrame.instance().setInformation(getLangString("HRFErfolg"), 0);
			//HOMainFrame.instance().resetInformation();
		}
	}

	/**
	 * The dialog {@link #getHrf} used to show for each failure, and the boolean it used to return.
	 * <p>
	 * The three {@code true} returns are deliberate. After a mid-match skip, a NOT AVAILABLE answer
	 * or a parse failure nothing was stored, yet the old code left {@code ok} true and
	 * {@link DownloadDialog} carried on downloading matches, fixtures and prices. That oddity is
	 * preserved: this method's contract is that the Download button behaves exactly as before.
	 * Changing when it flips is a separate decision with its own testing. The MCP tool does not
	 * inherit it - to it all five reasons are errors, because in all five nothing was stored.
	 */
	private static boolean report(JDialog parent, DownloadFailed failure) {
		switch (failure.reason) {
			case NO_TEAM -> {
				return false;
			}
			case CHPP_ERROR -> {
				setInfoMsg(failure.getMessage(), InfoPanel.FEHLERFARBE);
				Helper.showMessage(parent, failure.getMessage() + "\n" + failure.getCause() + "\n",
						getLangString("Fehler"), JOptionPane.ERROR_MESSAGE);
				return false;
			}
			case PLAYING_MATCH -> {
				HOMainFrame.instance().resetInformation();
				JOptionPane.showMessageDialog(parent, getLangString("NO_HRF_Spiel"),
						getLangString("NO_HRF_ERROR"), JOptionPane.INFORMATION_MESSAGE);
			}
			case NO_DATA -> {
				HOMainFrame.instance().resetInformation();
				JOptionPane.showMessageDialog(parent, getLangString("NO_HRF_ERROR"),
						getLangString("NO_HRF_ERROR"), JOptionPane.INFORMATION_MESSAGE);
			}
			case UNPARSEABLE -> {
				setInfoMsg(getLangString("Importfehler"), InfoPanel.FEHLERFARBE);
				Helper.showMessage(parent, getLangString("Importfehler"), getLangString("Fehler"),
						JOptionPane.ERROR_MESSAGE);
			}
		}
		return true;
	}
```

- [ ] **Step 4: Check the mapping against the old code, line by line**

This is the whole of this task's verification, so do it properly. Run:

```bash
git diff src/main/java/core/net/OnlineWorker.java
```

and confirm each row of this table against the removed code in the diff:

| Reason | Old code showed | Old code returned | New `report` |
|---|---|---|---|
| `NO_TEAM` | nothing — silent early `return false` | `false` | `return false`, no dialog |
| `CHPP_ERROR` | `setInfoMsg(msg, FEHLERFARBE)` + `Helper.showMessage(parent, msg + "\n" + e + "\n", "Fehler", ERROR)` | `false` | same, `msg` is `failure.getMessage()`, `e` is `failure.getCause()` |
| `PLAYING_MATCH` | `resetInformation()` + `JOptionPane` `NO_HRF_Spiel` / `NO_HRF_ERROR`, INFORMATION | `true` | same, falls through to `return true` |
| `NO_DATA` | `resetInformation()` + `JOptionPane` `NO_HRF_ERROR` / `NO_HRF_ERROR`, INFORMATION | `true` | same, falls through to `return true` |
| `UNPARSEABLE` | `setInfoMsg(Importfehler, FEHLERFARBE)` + `Helper.showMessage(parent, Importfehler, "Fehler", ERROR)` | `true` | same, falls through to `return true` |

Also confirm the `finally` block still calls `setInformation(getLangString("HRFErfolg"), 0)` on every path including `NO_TEAM`, and that `saveHRFToFile` is reached only after a successful download.

- [ ] **Step 5: Compile**

Run: `./gradlew compileJava`
Expected: `BUILD SUCCESSFUL`. If `Objects`, `IOException`, `HOModel`, `DBManager` or `HOVerwaltung` are reported as unresolved, they were already imported by the old `getHrf` — check that you did not delete an import.

- [ ] **Step 6: Run the suite to prove nothing else broke**

Run: `./gradlew test`
Expected: `BUILD SUCCESSFUL`. Nothing covers `OnlineWorker` today, so this only proves no compilation or wiring regression elsewhere.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/core/net/OnlineWorker.java
git commit -m "refactor(net): split the silent downloadTeamData out of getHrf"
```

---

### Task 3: The failure-to-advice mapping

**Files:**
- Modify: `src/main/java/core/mcp/TeamTools.java` (new package-private record and method at the bottom of the class)
- Test: `src/test/java/core/mcp/TeamToolsTest.java`

**Interfaces:**
- Consumes: `OnlineWorker.Failure` from Task 2.
- Produces, used by Task 4:
  - `record TeamTools.DownloadAdvice(String message, boolean retryable)` (package-private)
  - `static DownloadAdvice TeamTools.advice(OnlineWorker.Failure reason, String detail)` (package-private)

This is the one part of the feature that is pure and testable, and the part most likely to rot. It is an exhaustive `switch` expression with no `default` on purpose: adding a constant to `Failure` later must fail the build rather than hand an agent a blank error.

- [ ] **Step 1: Write the failing tests**

In `src/test/java/core/mcp/TeamToolsTest.java`, add this import to the existing import block:

```java
import core.net.OnlineWorker;
```

Then add these two tests to the class:

```java
    @Test
    void downloadAdvice_coversEveryFailureReasonAndSaysWhatIsWorthRetrying() {
        // exhaustive by construction - this loop fails the moment a reason maps to nothing
        for (final var reason : OnlineWorker.Failure.values()) {
            assertThat(TeamTools.advice(reason, "detail").message())
                    .as("advice for %s", reason).isNotBlank();
        }
        assertThat(TeamTools.advice(OnlineWorker.Failure.PLAYING_MATCH, null).retryable()).isTrue();
        assertThat(TeamTools.advice(OnlineWorker.Failure.NO_DATA, null).retryable()).isTrue();
        assertThat(TeamTools.advice(OnlineWorker.Failure.CHPP_ERROR, "boom").retryable()).isFalse();
        assertThat(TeamTools.advice(OnlineWorker.Failure.UNPARSEABLE, null).retryable()).isFalse();
        assertThat(TeamTools.advice(OnlineWorker.Failure.NO_TEAM, null).retryable()).isFalse();
    }

    @Test
    void downloadAdvice_passesHattricksOwnWordingThroughForAChppError() {
        // the underlying message verbatim - it is the only thing that says what actually broke
        assertThat(TeamTools.advice(OnlineWorker.Failure.CHPP_ERROR, "Corrupt/Missing Data").message())
                .contains("Corrupt/Missing Data");
    }
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew test --tests "core.mcp.TeamToolsTest"`
Expected: FAIL — compilation error, `cannot find symbol: method advice(...)`.

- [ ] **Step 3: Write the mapping**

In `src/main/java/core/mcp/TeamTools.java`, add this import:

```java
import core.net.OnlineWorker;
```

and add at the bottom of the class, before the closing brace:

```java
    /** What the agent is told about a failed download, and whether trying again could help. */
    record DownloadAdvice(String message, boolean retryable) {
    }

    /**
     * The agent-facing rendering of an {@link OnlineWorker.Failure}. Unlike HO's Download button,
     * all five reasons are errors here, because in all five nothing was stored.
     * <p>
     * A switch expression with no {@code default} on purpose: a constant added to
     * {@link OnlineWorker.Failure} without a case here must fail the build, not return a blank
     * error to an agent.
     *
     * @param detail the exception's own message, used where Hattrick's wording is the only thing
     *               that says what actually went wrong
     */
    static DownloadAdvice advice(OnlineWorker.Failure reason, String detail) {
        return switch (reason) {
            case PLAYING_MATCH -> new DownloadAdvice(
                    "Hattrick is playing your match right now and will not export team data. "
                            + "Try again after it finishes.", true);
            case NO_DATA -> new DownloadAdvice(
                    "Hattrick returned no team data.", true);
            case CHPP_ERROR -> new DownloadAdvice(
                    "Hattrick refused the download: " + detail, false);
            case UNPARSEABLE -> new DownloadAdvice(
                    "HO could not read what Hattrick sent back. Retrying will not help - this is "
                            + "worth reporting to the user, who can check HO's log.", false);
            case NO_TEAM -> new DownloadAdvice(
                    "No team is selected in HO, so there is nothing to download. Only the user can "
                            + "fix this, in HO.", false);
        };
    }
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew test --tests "core.mcp.TeamToolsTest"`
Expected: PASS, all cases including the pre-existing ones.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/core/mcp/TeamTools.java src/test/java/core/mcp/TeamToolsTest.java
git commit -m "feat(mcp): map download failures to agent-readable advice"
```

---

### Task 4: The `download_team` tool

**Files:**
- Modify: `src/main/java/core/mcp/LineupTools.java:393` (`describe` opened up, ratings made optional) and `:439` (`onEdt` error text)
- Modify: `src/main/java/core/mcp/TeamTools.java` (imports, `tools()`, new handler and helpers)
- Test: `src/test/java/core/mcp/TeamToolsTest.java:23` (rename and extend the tool-list case, add a schema case)

**Interfaces:**
- Consumes: `OnlineWorker.downloadTeamData()`, `OnlineWorker.TeamDownload`, `OnlineWorker.DownloadFailed`, `OnlineWorker.Failure` (Task 2); `TeamTools.advice(Failure, String)` and `TeamTools.DownloadAdvice` (Task 3); `UserParameter.mcpDownloadsPerSession` (Task 1).
- Consumes from existing code: `LineupTools.onEdt(EdtTask)`, `LineupTools.describe(Lineup, RatingPredictionModel)`, `McpJson.optBool(JsonObject, String, boolean)`, `McpTool.schema/properties/prop`, `Connector.instance().setSilentDownload/lastError`, `HOModel.getCurrentLineup()`, `RefreshManager.doReInit()`, `HOMainFrame.setInformationCompleted()`.
- Produces: the `download_team` tool in `TeamTools.tools()`, and `static int TeamTools.downloadsThisSession` (package-private, so a test can reset it).

- [ ] **Step 1: Write the failing tests**

In `src/test/java/core/mcp/TeamToolsTest.java`, replace the existing `tools_areTheEightReadTools` test:

```java
    @Test
    void tools_areTheEightReadTools() {
        assertThat(TeamTools.tools()).extracting(McpTool::name)
                .containsExactly("get_team", "get_squad", "get_player", "get_opponent_squad",
                        "get_upcoming_matches", "get_league_table", "get_finances", "get_stadium");
    }
```

with:

```java
    @Test
    void tools_areTheEightReadToolsPlusTheDownload() {
        assertThat(TeamTools.tools()).extracting(McpTool::name)
                .containsExactly("get_team", "get_squad", "get_player", "get_opponent_squad",
                        "get_upcoming_matches", "get_league_table", "get_finances", "get_stadium",
                        "download_team");
    }

    @Test
    void downloadTeam_takesNoRequiredArgumentsAndOffersThePriceResolve() {
        final var download = TeamTools.tools().stream()
                .filter(t -> t.name().equals("download_team"))
                .findFirst().orElseThrow();

        assertThat(download.inputSchema().getAsJsonArray("required")).isEmpty();
        assertThat(download.inputSchema().getAsJsonObject("properties").has("resolvePrices"))
                .isTrue();
        // the lineup clobber is the one surprise this tool has, so it has to be in the description
        assertThat(download.description()).contains("lineupReplaced");
    }
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew test --tests "core.mcp.TeamToolsTest"`
Expected: FAIL — `tools_areTheEightReadToolsPlusTheDownload` reports the actual list has 8 elements, and `downloadTeam_takesNoRequiredArgumentsAndOffersThePriceResolve` throws `NoSuchElementException`.

- [ ] **Step 3: Open up `LineupTools.describe` and make its ratings optional**

`download_team` snapshots the lineup either side of the download and compares the two. Ratings must not be in that comparison — they move on nearly every download, so the flag would always be true — and computing them is not free: `McpJson.ratings` runs a full rating pass, and this would run two of them on the EDT inside a call already advertised as ten to twenty seconds. So rather than computing ratings and stripping them again afterwards, `describe` learns to leave them out.

In `src/main/java/core/mcp/LineupTools.java` (around line 393), change:

```java
    private static JsonObject describe(Lineup lineup, core.rating.RatingPredictionModel ratingModel) {
```

to:

```java
    /**
     * Package-private so {@code TeamTools.download_team} can snapshot the lineup a download
     * replaces.
     *
     * @param ratingModel null to leave the {@code ratings} block out entirely - what the download
     *                    snapshot wants, because it compares two snapshots and ratings move on
     *                    nearly every download
     */
    static JsonObject describe(Lineup lineup, @Nullable core.rating.RatingPredictionModel ratingModel) {
```

and further down the same method, replace:

```java
        json.add("ratings", McpJson.ratings(ratingModel, lineup));
```

with:

```java
        if (ratingModel != null) {
            json.add("ratings", McpJson.ratings(ratingModel, lineup));
        }
```

Add `import org.jetbrains.annotations.Nullable;` if the file does not already have it. Change nothing else, and leave the three existing callers (`optimize:237`, `currentLineup:261`, `apply:288`) alone — all three pass a real model, so the `ratings` block they produce is unchanged. `describe` was `private`, so there is no caller outside this file.

- [ ] **Step 4: Make `onEdt` keep the message of a runtime failure**

In the same file (around line 439), `onEdt` ends a failed task with:

```java
        if (failure.get() != null) {
            throw new McpToolException(failure.get().getMessage());
        }
```

`download_team` reaches far more code than `apply_lineup` does — `HRFStringParser.parse`, `storeModel`, `calcSubskills`, `doReInit` — and `getMessage()` is null for a bare `NullPointerException`, which would reach the agent as an empty error. Replace with:

```java
        if (failure.get() != null) {
            // getMessage() is null for a bare NullPointerException; toString() at least names the
            // class. Every McpToolException already carries a message, so this changes nothing for
            // the deliberate failures.
            final var e = failure.get();
            throw new McpToolException(e.getMessage() != null ? e.getMessage() : e.toString());
        }
```

- [ ] **Step 5: Add the imports `TeamTools` needs**

In `src/main/java/core/mcp/TeamTools.java`, the import block currently starts:

```java
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import core.constants.player.PlayerSkill;
import core.db.DBManager;
```

Task 3 already added `import core.net.OnlineWorker;`. Add these seven as well, keeping the block alphabetical (`core.net.Connector` sorts before `core.net.OnlineWorker`):

```java
import com.google.gson.JsonNull;
import core.gui.HOMainFrame;
import core.gui.RefreshManager;
import core.model.HOVerwaltung;
import core.model.UserParameter;
import core.net.Connector;
import core.util.HODateTime;
```

- [ ] **Step 6: Register the tool**

In `TeamTools.tools()`, the list currently ends:

```java
                        TeamTools::arena)
        );
    }
```

Replace that with:

```java
                        TeamTools::arena),
                new McpTool("download_team",
                        "Re-downloads the team from Hattrick and rewrites HO's local database - "
                                + "squad, finances, staff, stadium, team spirit - then "
                                + "recalculates sub-skills. Every other tool answers from that "
                                + "database, so call this when the user has done something in "
                                + "Hattrick that HO cannot see yet: a transfer, a sacking, a "
                                + "training change, a new week. Roughly a dozen CHPP requests and "
                                + "ten to twenty seconds, during which HO's window is frozen, so "
                                + "call it because something changed, not as a routine refresh. "
                                + "It does not refresh matches, fixtures, the league table's "
                                + "played results, hall of fame or youth data - those still need "
                                + "a manual download in HO. It also replaces HO's stored lineup "
                                + "with the match order Hattrick currently holds, so a lineup "
                                + "built in HO but never uploaded is lost: check lineupReplaced "
                                + "and previousLineup in the result before reporting success - a "
                                + "null lineupReplaced means HO could not tell either way.",
                        McpTool.schema(McpTool.properties(
                                "resolvePrices", McpTool.prop("boolean",
                                        "also look up what watched market players sold for - a "
                                                + "burst of extra per-player CHPP lookups, "
                                                + "default false"))),
                        TeamTools::downloadTeam)
        );
    }
```

- [ ] **Step 7: Write the cap, the handler and its three helpers**

In `src/main/java/core/mcp/TeamTools.java`, add above the `DownloadAdvice` record from Task 3:

```java
    /** Team downloads issued over MCP in this HO session. Manual downloads do not count. */
    static int downloadsThisSession;

    /**
     * Refuse before spending anything when the session's downloads are used up.
     * <p>
     * Not synchronized, and it does not need to be: the only caller is inside
     * {@link LineupTools#onEdt}, so the check and the increment both happen on the event dispatch
     * thread. The counter moves only after a download actually succeeded - a Hattrick outage must
     * not eat the session's whole allowance.
     */
    private static void checkDownloadCap() throws McpToolException {
        final var cap = UserParameter.instance().mcpDownloadsPerSession;
        if (downloadsThisSession >= cap) {
            throw new McpToolException("The team-download cap of " + cap + " downloads per HO "
                    + "session is used up. Raise it in HO's options under MCP server, or restart "
                    + "HO, before downloading again.");
        }
    }

    /**
     * Refuse when HO holds no team id.
     * <p>
     * {@code ConvertXml2Hrf.createHrf():79} opens a modal {@code TeamSelectionDialog} in exactly
     * that case when the CHPP account has more than one team, and unlike {@code Connector}'s
     * dialogs this one is not gated on {@code silentDownload}. The only way to keep it off this
     * path is not to reach it.
     */
    private static void requireTeamSelected() throws McpToolException {
        final var model = HOVerwaltung.instance().getModel();
        if (model == null || model.getBasics() == null || model.getBasics().getTeamId() <= 0) {
            throw new McpToolException(advice(OnlineWorker.Failure.NO_TEAM, null).message());
        }
    }

    /**
     * Refresh HO's local database from Hattrick.
     * <p>
     * All of it runs inside one {@link LineupTools#onEdt} hop, because
     * {@link OnlineWorker#downloadTeamData()} touches HO's info line and the lineup panel and
     * because the model swap must not race the UI. HO's window is frozen for the duration - the
     * manual Download button already works that way.
     * <p>
     * The body runs with {@link Connector#setSilentDownload}, restored in a {@code finally}.
     * {@code Connector} opens {@code OAuthDialog} on an HTTP 401 and a {@code JOptionPane} on
     * every other CHPP failure, both gated on that flag alone, and a modal dialog on the EDT would
     * block the EDT and the MCP server's single handler thread together, with nothing on the
     * agent's side able to dismiss it. The wrap spans {@code resolveTransferListings()} too, which
     * is CHPP traffic of its own. It sits here rather than inside {@code downloadTeamData()}
     * because HO's Download button must keep those dialogs.
     */
    private static JsonObject downloadTeam(JsonObject args) throws McpToolException {
        final var resolvePrices = McpJson.optBool(args, "resolvePrices", false);
        return LineupTools.onEdt(() -> {
            // Both before any CHPP traffic, so a refusal costs nothing.
            checkDownloadCap();
            requireTeamSelected();

            final var before = lineupSnapshot();
            final var wasSilent = Connector.instance().setSilentDownload(true);
            try {
                final OnlineWorker.TeamDownload download;
                try {
                    download = OnlineWorker.downloadTeamData();
                } catch (OnlineWorker.DownloadFailed e) {
                    throw new McpToolException(explain(e));
                }
                downloadsThisSession++;

                // Without this the training tools read sub-skills computed against the previous
                // HRF. DownloadDialog:400 does the same after every download - and on the live
                // model, which is the downloaded one only when the swap actually happened.
                HOVerwaltung.instance().getModel().calcSubskills();

                if (resolvePrices) {
                    OnlineWorker.resolveTransferListings();
                }

                // The download only wrote the database. Without this every HO tab keeps showing
                // the old squad while the agent talks about the new one - worse than not
                // refreshing at all, because the user's screen now disagrees with the agent.
                RefreshManager.instance().doReInit();
                // DownloadDialog:413 ends this way. Without it HO's progress bar stays mid-flight
                // after the call returns.
                HOMainFrame.instance().setInformationCompleted();

                // Only comparable when the swap happened. When it did not, HOVerwaltung still
                // holds the previous model with the user's in-memory lineup, so before and after
                // are snapshots of the same object and always compare equal - even though
                // storeModel has already overwritten the stored lineup. Null rather than false:
                // "HO cannot tell" is not "nothing was lost".
                Boolean lineupReplaced = null;
                if (download.becameCurrent() && before != null) {
                    final var after = lineupSnapshot();
                    lineupReplaced = after != null && !before.equals(after);
                }

                final var json = new JsonObject();
                json.addProperty("hrfId", download.model().getHrfId());
                json.addProperty("downloadedAt", HODateTime.now().toHT());
                // false means the download stored an HRF that is not newer than the one HO already
                // held, so nothing became live - do not report that as a refresh.
                json.addProperty("becameCurrent", download.becameCurrent());
                json.addProperty("players", download.model().getCurrentPlayers().size());
                json.addProperty("subskillsRecalculated", true);
                json.addProperty("pricesResolved", resolvePrices);
                json.addProperty("lineupReplaced", lineupReplaced);
                json.add("previousLineup",
                        Boolean.TRUE.equals(lineupReplaced) ? before : JsonNull.INSTANCE);
                return json;
            } finally {
                Connector.instance().setSilentDownload(wasSilent);
            }
        });
    }

    /**
     * The message an agent gets for a failed download.
     * <p>
     * With {@code silentDownload} on, {@code Connector} swallowed the dialog it would otherwise
     * have shown and handed back an empty string; {@code createHrf} then returned null exactly the
     * way it does when no team is selected, so {@code downloadTeamData} reported
     * {@link OnlineWorker.Failure#NO_TEAM}. {@link Connector#lastError()} is what tells the two
     * apart - it is cleared at the start of every CHPP request and left set only by a failed one,
     * so a non-null value here means Hattrick, not the team selection, is the reason. Without this
     * an expired OAuth token - the likeliest real failure - would be reported to the agent as "no
     * team is selected in HO", which the user cannot act on.
     */
    private static String explain(OnlineWorker.DownloadFailed failure) {
        final var chppError = Connector.instance().lastError();
        final var advice = failure.reason == OnlineWorker.Failure.NO_TEAM && chppError != null
                ? advice(OnlineWorker.Failure.CHPP_ERROR, chppError)
                : advice(failure.reason, failure.getMessage());
        return advice.message() + (advice.retryable()
                ? " Nothing was stored; the same call may work in a few minutes."
                : " Nothing was stored.");
    }

    /**
     * The live lineup's positions, or null when HO holds no model yet - on a fresh install the
     * first download has nothing to compare against.
     * <p>
     * No rating model, so no {@code ratings} block: this exists to be compared, and two full
     * rating passes on the EDT to produce a field that would then have to be ignored is a poor
     * trade. The positions are also what an agent needs to rebuild a lineup a download replaced.
     */
    private static JsonObject lineupSnapshot() {
        final var model = HOVerwaltung.instance().getModel();
        if (model == null || model.getBasics() == null) {
            return null;
        }
        return LineupTools.describe(model.getCurrentLineup(), null);
    }
```

- [ ] **Step 8: Run the tests to verify they pass**

Run: `./gradlew test --tests "core.mcp.TeamToolsTest"`
Expected: PASS, including `everyToolHasANonEmptyDescription`, which now covers `download_team` for free.

- [ ] **Step 9: Run the whole suite**

Run: `./gradlew test`
Expected: `BUILD SUCCESSFUL`. Two things to watch: if another test asserts the total number of MCP tools the server advertises, update its expected count — the surface grew by one; and if a `LineupTools` test asserts a `ratings` key on a `describe` result, it still passes, because both existing callers pass a real model.

- [ ] **Step 10: Commit**

```bash
git add src/main/java/core/mcp/TeamTools.java \
        src/main/java/core/mcp/LineupTools.java \
        src/test/java/core/mcp/TeamToolsTest.java
git commit -m "feat(mcp): add download_team"
```

---

### Task 5: Manual verification and the developer note

**Files:**
- Modify: `docs/current.md` (append a new `##` section at the end, matching the existing dated-section style)

**Interfaces:**
- Consumes: everything from Tasks 1–4. Produces nothing code reads.

The important behaviour of this feature is exactly what unit tests cannot reach — a live CHPP session and a Swing frame. This task is where it actually gets checked.

- [ ] **Step 1: Build a runnable distribution**

Run:

```bash
./gradlew createVersion createDistribution
```

Expected: `BUILD SUCCESSFUL`. `createVersion` is not optional — a bare `jar` stamps a two-part version and HO dies at startup with `ArrayIndexOutOfBoundsException`.

- [ ] **Step 2: Start HO with the MCP server on**

Run:

```bash
cd build/install/XXX && java -jar HO.jar
```

In HO: Options → MCP server. Confirm the new **Team downloads per session** row is there, shows `10`, and has the tooltip. Confirm the MCP server checkbox is ticked and note the port.

- [ ] **Step 3: Two downloads in one day — the case that used to open a file chooser**

Call `download_team` from an MCP client twice in a row. Expected: **both** calls return a JSON result. The second is the case spec §1.3 describes, where `saveHRFToFile` would open the chooser because `<teamId>-YYYY-MM-DD.hrf` already exists. If a file chooser appears, the tool is reaching `saveHRFToFile` and Task 2 is wrong.

- [ ] **Step 4: No dialog, and the progress line settles**

While a call is running, watch HO's window. Expected: the info line moves through the download steps, and **no** dialog of any kind opens. When it finishes, the progress bar returns to idle — that is `setInformationCompleted()`. If HO freezes with a dialog, the MCP call will never return — kill HO and fix the path that opened it.

Then do the same with the network unplugged (turn Wi-Fi off, call `download_team`, turn it back on). This is the check that actually exercises the silent flag: `Connector.getCHPPWebFile` would normally show a `JOptionPane` here. Expected: **no** dialog, and the call returns an error naming Hattrick's own wording rather than "No team is selected in HO" — if it says the latter, `explain()` is not consulting `Connector.lastError()`.

- [ ] **Step 5: HO's tabs refresh themselves**

After a successful call, look at HO's squad tab without clicking anything. Expected: it shows the downloaded squad. If it still shows the old one, `RefreshManager.instance().doReInit()` is not being reached.

- [ ] **Step 6: The lineup clobber is reported**

In HO's Lineup tab, build a lineup and do **not** upload it. Call `download_team`. Expected: the result has `"lineupReplaced": true` and `previousLineup` holds the positions you had just set, with **no** `ratings` block — `lineupSnapshot()` passes a null rating model on purpose. Then call `download_team` again immediately without touching the lineup. Expected: either `"lineupReplaced": false` with `"previousLineup": null`, or `"lineupReplaced": null` if that second download was not newer and `becameCurrent` came back `false`. What must **not** happen is `true` twice in a row — that would mean ratings are back in the snapshot, so every comparison differs.

- [ ] **Step 7: The Download button is unchanged**

Click Download in HO's UI with team data ticked. Expected: it behaves exactly as before, **including** the HRF save dialog appearing when today's file already exists.

- [ ] **Step 8: The cap refuses without spending a request**

In Options, set Team downloads per session to `1`. Call `download_team` twice. Expected: the first succeeds, the second returns an error naming the cap and the Options → MCP server panel, and no CHPP traffic happens on the second. Set the cap back to `10`.

Then check that a *failed* download does not spend a slot: set the cap to `1`, unplug the network, call `download_team` (it fails), plug the network back in and call again. Expected: the second call downloads. If it reports the cap instead, the increment is still happening before the download rather than after it.

- [ ] **Step 9: Write the developer note**

Append to `docs/current.md`, at the end of the file:

```markdown
## `download_team` — refreshing HO from the agent (2026-09-05)

Every read tool answers from `DBManager`/`HOVerwaltung`, so after the user fired a player in
Hattrick `get_squad` kept returning the old squad until someone clicked Download in HO. `download_team`
is that click, over MCP.

`OnlineWorker.getHrf` was split. The download — `ConvertXml2Hrf.createHrf` → `HRFStringParser.parse`
→ `storeModel` — is now `OnlineWorker.downloadTeamData()`, which shows nothing itself and throws a
typed `DownloadFailed(Failure reason, …)`. `getHrf` keeps its signature and is now only the five
dialogs (`report()`) plus `saveHRFToFile`. Both had exactly one caller each, so the blast radius was
two files.

Five things worth knowing next time:

- **"Silent" is three separate problems, not one.** `downloadTeamData()` showing nothing is the easy
  part. Underneath it, `Connector.getCHPPWebFile` opens `OAuthDialog` on an HTTP 401 (`:740`) and a
  `JOptionPane` on every other failure (`:765`), both gated only on `Connector.silentDownload`, and
  one download makes roughly a dozen requests through that method. `ConvertXml2Hrf.createHrf():79`
  opens a modal `TeamSelectionDialog` when HO holds no team id and the account has several teams,
  and `silentDownload` does *not* gate that one. `DBManager:150` shows a "Fatal DB Error" box.
  `download_team` wraps its body in `Connector.setSilentDownload(true)` (save/restore, like
  `ChppWriteService` and `TransferSearchService`), refuses up front when `getTeamId() <= 0`, and
  leaves the DB box alone on purpose — HO is dead at that point anyway and hiding a corrupt database
  from the user would be worse. The flag is set by the *tool*, not by `downloadTeamData()`: HO's
  Download button still needs those dialogs.
- **Suppressing the dialog costs you the error.** With the flag on, a CHPP failure makes
  `getCHPPWebFile` return `""`, `createHrf` bails at `teamDetails.isEmpty()`, and the result is the
  same null it returns when no team is selected — so an expired OAuth token reported as `NO_TEAM`,
  "no team is selected in HO", which the user cannot act on. `Connector.lastError()` is the way out:
  cleared at the start of every request, left set only by a failed one, so a non-null value at the
  null-hrf branch means Hattrick was the reason. That disambiguation lives in `TeamTools.explain()`
  and deliberately not in `downloadTeamData()`, where it would make the Download button pop an error
  dialog it currently does not.
- **The save chooser cannot be switched off.** `saveHRFToFile` opens it when the target file already
  exists, and the name is `<teamId>-YYYY-MM-DD.hrf` — one per calendar day. So the second download on
  any day opens it regardless of `showHRFSaveDialog`. Neutralising the flags is not a fix; the tool
  must not enter `saveHRFToFile` at all, and it doesn't.
- **`getHrf` returns `true` on three failures where nothing was stored** (`PLAYING_MATCH`, `NO_DATA`,
  `UNPARSEABLE`), and `DownloadDialog` then carries on downloading matches and prices. That looks
  wrong and was preserved deliberately — the refactor's contract was "the button behaves
  identically". The MCP tool does not inherit it: all five reasons are errors there.
- **A download overwrites HO's stored lineup** with the match order Hattrick currently holds
  (`HRFStringParser:177` → `HOModel.storeLineup`). Three call sites write that lineup and none records
  provenance, so there is no dirty flag and "refuse when unsaved" is not implementable. The tool
  snapshots the lineup either side of the download and reports `lineupReplaced` / `previousLineup`
  instead. Two traps there: the snapshot is taken with a null rating model, because ratings move on
  nearly every download and would make the flag always true (and computing them twice on the EDT is
  not free); and the comparison is only meaningful when `becameCurrent` is true, because otherwise
  `HOVerwaltung` still holds the previous model with the user's in-memory lineup and both snapshots
  come off the same object. In that case `lineupReplaced` is `null`, not `false` — the stored lineup
  *was* overwritten, HO just cannot see it.

Capped by the new `UserParameter.mcpDownloadsPerSession` (default 10, half the other two caps because
one download is roughly a dozen CHPP requests). The counter increments only after a download
succeeded, so a Hattrick outage cannot eat the whole session's allowance. No `DBVersion` bump —
`UserParameter` persists through a plain string map.

Not refreshed, on purpose: matches, fixtures, played league results, hall of fame, youth data. Those
are the other ~150 lines of `DownloadDialog.startDownload`, each with its own filter settings. A later
`download_matches` can follow the same split.
```

- [ ] **Step 10: Commit**

```bash
git add docs/current.md
git commit -m "docs: record the download_team split and its three gotchas"
```

- [ ] **Step 11: Tell the downstream consumer**

`~/hattrick` keeps `docs/mcp-tools.md`, which documents this tool surface for its own sessions. It needs three additions: the `download_team` tool, the §1.6 lineup hazard beside the existing `apply_lineup` note, and the tri-state `lineupReplaced` (`true` / `false` / `null` when HO cannot tell). That file is outside this repository — report to the user that it needs updating rather than editing it from here.
