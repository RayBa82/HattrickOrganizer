# CHPP Write Operations over MCP — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let an MCP client drive the five CHPP write families HO can perform for a team — set senior training, send a match order, list/arrange/respond-to friendly challenges, and place a transfer bid — routed through one testable `ChppWriteService`.

**Architecture:** A new `core.net.ChppWriteService` singleton shaped exactly like the existing `TransferSearchService`: a `protected fetch<Family>(...)` seam that tests subclass and override, a `synchronized` per-session write counter checked against a new `UserParameter`, and a `ChppWriteException` mirroring `TransferSearchService.SearchFailed`. Raw CHPP calls and their URL builders go in `core.net.Connector`; response parsing reuses `XMLTrainingParser` / `XMLCHPPPreParser` and adds one new `XMLChallengesParser`. Thin tools in the existing `core.mcp.*Tools` classes validate arguments, call the service, and shape a JSON result. Every non-bid write fires directly (matching `apply_lineup` / `set_player_group`); the transfer bid sits behind a two-step confirm-token gate held in a static map on `TransferTools`.

**Tech Stack:** Java 17, Swing, Gson, embedded HSQLDB, scribejava OAuth 1.0a, JUnit 5 + AssertJ. Build with `./gradlew` (every build is a full clean rebuild, ~10s minimum; POEditor pull failure in the log is expected locally).

**Spec:** `docs/superpowers/specs/2026-09-02-chpp-write-operations-over-mcp-design.md` (approved for planning 2026-09-02, reviewed against the code twice). Read it alongside this plan. This plan also builds on `docs/superpowers/specs/2026-08-30-mcp-server-design.md`.

## Global Constraints

- **Java 17 language level.** Do not raise it. Records, `var`, switch expressions, text blocks are all available and used freely in this codebase.
- **Singletons, no DI.** New service follows `X.instance()` with a package-private constructor, exactly like `TransferSearchService`.
- **No new DB tables, no schema migration.** One new *read* query method on an existing table is in scope (Task 9); nothing new persists.
- **CHPP calls run silent.** Every `fetch<Family>` wraps its `Connector` call in `Connector.instance().setSilentDownload(true)` / restore-in-`finally`. Off the EDT (MCP handler threads), a non-silent CHPP error pops a modal `JOptionPane` / `OAuthDialog` that hangs the request until it times out. This is not optional — see spec §1.4.
- **Reads of `HOModel` / `HOVerwaltung` that a tool needs happen on the EDT** via the existing `LineupTools.onEdt` helper (spec §1.4).
- **All user-facing strings that already have a key** go through `TranslationFacility.tr(key)`. New free-text error messages in `ChppWriteException` are English literals, same as `TransferSearchService.SearchFailed` messages — those are read only by the model, not shown in HO's UI.
- **Money is `long`** throughout the bid path (`Connector`, `ChppWriteService`, `TransferTools`), matching `TransferListing`.
- **Legacy German names stay.** Do not rename anything you touch.
- **This design assumes the running OAuth token already carries the new scopes.** A stale token is out of scope — a missing scope surfaces as an ordinary CHPP `<error>` through the normal failure path (spec §1.3, §6). Do not add token-refresh or scope-detection logic.
- **CHPP request formats** (spec §1.6), all `GET` with query parameters except match-order upload which is a `POST`:

  | Family | file | version | actionType | Parameters |
  |---|---|---|---|---|
  | set training | `training` | `2.1` | `setTraining` | `teamId`, `trainingType` (int), `trainingLevel` (0–100), `trainingLevelStamina` (5–100) |
  | list challenges | `challenges` | `1.6` | `view` | `isWeekendFriendly=1` (optional) |
  | arrange challenge | `challenges` | `1.6` | `challenge` | `opponentTeamId`, `matchType` (0 normal / 1 cup rules), `matchPlace` (0 home / 1 away / 2 neutral), `neutralArenaId` (only when `matchPlace=2`), `isWeekendFriendly=1` (optional) |
  | accept / decline / withdraw | `challenges` | `1.6` | `accept` \| `decline` \| `withdraw` | `trainingMatchId` |
  | place bid | `playerdetails` | `3.0` | `placeBid` | `playerID`, `teamId`, `bidAmount` (int), `maxBidAmount` (int, optional) |

---

## Deviations from the spec (deliberate, with reason)

1. **`Connector` URL builders are `static` package-private, not instance methods** (spec §3 sketches them as instance methods). `htUrl` is `private static final`; the builders touch no instance state, and `static` lets the URL-builder test run without constructing `Connector.instance()` (whose constructor builds an OAuth service). The raw-call methods (`setTraining`, `challenges`, `placeBid`) stay instance because they call the private instance method `getCHPPWebFile`.
2. **`sendMatchOrderForAgent` takes the serialized order string, not just `(matchId, matchType)`** (spec §3.2 sketches 2 args). The current lineup must be read on the EDT via `LineupTools.onEdt` per §1.4, and that helper lives in `core.mcp`, not `core.net`. So `LineupTools.send_match_order` reads `model.getCurrentLineup().toJson()` on the EDT and passes the string to `ChppWriteService.sendMatchOrderForAgent(int matchId, MatchType matchType, String orderString)`.
3. **Step-1 bid preview needs a new read method** `DBManager.loadLatestTransferListing(int playerId)` (one line) + `TransferListingTable.loadLatestForPlayer(int playerId)`. The spec says step 1 "reads the player from HOVerwaltung"; a market player is not in the squad, so `HOModel.getCurrentPlayer` returns null for him. The `TRANSFERLISTING` snapshot corpus that `search_transfers` fills is the right source. No migration — it is a `SELECT`.
4. **`get_upcoming_matches` returns no `matchType` field today** (it is `TeamTools.fixtures()` — the series fixture list: `matchId`, `matchDay`, `date`, team ids/names). Series matches are league matches (`MatchType` id 1). The `send_match_order` tool description therefore says "league matches are matchType 1; take the id from `get_matches` for cup / friendly / tournament", and the null-`MatchType` error names `get_matches`. Extending `get_upcoming_matches` is out of scope.

---

## File structure

| File | Responsibility | Task |
|---|---|---|
| `src/main/java/core/net/login/OAuthDialog.java` | authorization-URL scope string — add 3 scopes | 1 |
| `src/main/java/core/model/UserParameter.java` | new `mcpChppWritesPerSession` field (declare + serialize + parse) | 1 |
| `src/main/java/core/option/McpServerPanel.java` | `writeCap` text field, wired into `saveFields()` | 1 |
| `docs/chpp_files.md` | `challenges` used-version + write actionType notes | 1 |
| `src/main/java/core/net/Connector.java` | URL builders + raw calls for training / challenges / bid; `uploadMatchOrder` `showErrorMessage` overload | 2 |
| `src/main/java/core/net/ChppWriteService.java` | **new** — service + `ChppWriteException` + `TrainingCommitted` / `BidResult` records + `fetch` seam + session cap | 3, 5, 6 |
| `src/main/java/core/file/xml/XMLChallengesParser.java` | **new** — `challenges` `view` XML → `Challenge` / `ChallengeLists` records | 4 |
| `src/main/java/core/db/TransferListingTable.java` | `loadLatestForPlayer(int)` query | 9 |
| `src/main/java/core/db/DBManager.java` | `loadLatestTransferListing(int)` delegate | 9 |
| `src/main/java/core/mcp/McpJson.java` | `reqLong`, `optLong` | 7 |
| `src/main/java/core/mcp/TrainingTools.java` | `set_training` tool + handler | 7 |
| `src/main/java/core/mcp/LineupTools.java` | `send_match_order` tool + handler | 8 |
| `src/main/java/core/mcp/MatchTools.java` | `list_challenges`, `arrange_challenge`, `respond_to_challenge` tools + handlers | 7 |
| `src/main/java/core/mcp/TransferTools.java` | `place_bid` two-step tool + pending-bid map | 9 |
| `src/test/java/core/net/ConnectorWriteUrlTest.java` | **new** — URL builder assertions | 2 |
| `src/test/java/core/net/ChppWriteServiceTest.java` | **new** — cap, increment, typed failures, guards | 3, 5, 6 |
| `src/test/java/core/file/xml/XMLChallengesParserTest.java` | **new** — fixture → records | 4 |
| `src/test/java/core/mcp/TrainingToolsTest.java` | **new** — range validation, committed echo | 7 |
| `src/test/java/core/mcp/MatchToolsTest.java` | **new** — location mapping, error passthrough | 7 |
| `src/test/java/core/mcp/LineupToolsTest.java` | **new (or extend)** — unknown matchType, service call | 8 |
| `src/test/java/core/mcp/TransferToolsTest.java` | **new** — two-step token flow | 9 |

`McpHttpServer.registerAllTools()` already calls each `*Tools.tools()`; new tools are added to those existing lists, so **no `McpHttpServer` change**.

---

## Task 1: OAuth scopes and the write-cap config plumbing

**Files:**
- Modify: `src/main/java/core/net/login/OAuthDialog.java:52-54`
- Modify: `src/main/java/core/model/UserParameter.java` (~line 117 declare, ~line 451 serialize, ~line 701 parse)
- Modify: `src/main/java/core/option/McpServerPanel.java` (field ~line 51, `saveFields()` ~line 105)
- Modify: `docs/chpp_files.md`

**Interfaces:**
- Produces: `UserParameter.instance().mcpChppWritesPerSession` (`public int`, default `20`) — read by `ChppWriteService` in Task 3.

This task is config plumbing with no unit-testable logic of its own; its correctness is verified by the build compiling and by Task 3's cap test, which reads `mcpChppWritesPerSession`. That mirrors how `mcpSearchesPerSession` was introduced (`docs/superpowers/plans/2026-08-30-mcp-server.md`).

- [ ] **Step 1: Expand the OAuth scope string**

In `src/main/java/core/net/login/OAuthDialog.java`, the constructor at line 44 has:

```java
		// Hard code set_matchorder due to authorization woes.
		// scope manage_youthplayers is needed to use action type unlockskills
		scopes = "&scope=" + "set_matchorder,manage_youthplayers";
```

Replace with:

```java
		// Hard code the scope set due to authorization woes - the `scope` constructor
		// parameter is never read.
		// manage_youthplayers is needed for action type unlockskills;
		// set_training / manage_challenges / place_bid are the CHPP write families exposed over MCP
		// (docs/superpowers/specs/2026-09-02-chpp-write-operations-over-mcp-design.md).
		scopes = "&scope=" + "set_matchorder,manage_youthplayers,set_training,manage_challenges,place_bid";
```

- [ ] **Step 2: Declare the new UserParameter field**

In `src/main/java/core/model/UserParameter.java`, find the MCP block (around line 113):

```java
    //------MCP server (see docs/superpowers/specs/2026-08-30-mcp-server-design.md)---
    public boolean mcpServerEnabled = false;
    public int mcpServerPort = 8765;
    /** How many transfer searches one HO session may issue in total. */
    public int mcpSearchesPerSession = 20;
```

Add one line after `mcpSearchesPerSession`:

```java
    /** How many CHPP writes (training, match order, challenges, bids) one HO session may issue in total. */
    public int mcpChppWritesPerSession = 20;
```

- [ ] **Step 3: Serialize it**

In the same file, find (around line 451):

```java
        map.put("mcpSearchesPerSession", String.valueOf(mcpSearchesPerSession));
```

Add after it:

```java
        map.put("mcpChppWritesPerSession", String.valueOf(mcpChppWritesPerSession));
```

- [ ] **Step 4: Parse it back**

Find (around line 701):

```java
        mcpSearchesPerSession = orDefault(getIntValue(values, "mcpSearchesPerSession"), 20);
```

Add after it:

```java
        mcpChppWritesPerSession = orDefault(getIntValue(values, "mcpChppWritesPerSession"), 20);
```

- [ ] **Step 5: Add the writeCap field to McpServerPanel**

In `src/main/java/core/option/McpServerPanel.java`, next to the existing `searchCap` / `resolveCap` field declarations (around line 20):

```java
    private final JTextField writeCap = new JTextField(5);
```

In `initComponents()`, after the `searchcap` field is added (around line 51):

```java
        addField(c, ++c.gridy, "ls.options.mcp.writecap", writeCap,
                String.valueOf(UserParameter.instance().mcpChppWritesPerSession));
```

In `saveFields()` (around line 105), the loop currently is:

```java
        final var portValue = parse(port, 8765);
        final var capValue = parse(searchCap, 20);
        for (final var p : new UserParameter[]{UserParameter.instance(), UserParameter.temp()}) {
            p.mcpServerPort = portValue;
            p.mcpSearchesPerSession = capValue;
        }
```

Add a `writeCapValue` and assign it in the loop:

```java
        final var portValue = parse(port, 8765);
        final var capValue = parse(searchCap, 20);
        final var writeCapValue = parse(writeCap, 20);
        for (final var p : new UserParameter[]{UserParameter.instance(), UserParameter.temp()}) {
            p.mcpServerPort = portValue;
            p.mcpSearchesPerSession = capValue;
            p.mcpChppWritesPerSession = writeCapValue;
        }
```

Check whether `saveFields()` is wired to every field's change listener the same way `searchCap` is — follow the identical pattern (look at how `searchCap`'s document listener or focus listener calls `saveFields()` and replicate for `writeCap`).

- [ ] **Step 6: Add the translation key**

`ls.options.mcp.writecap` is a new key. Per AGENTS.md, translations are owned by POEditor; add the key to `src/main/resources/language/English.properties` only (POEditor picks up new keys; it overwrites existing values on the next build). Use:

```properties
ls.options.mcp.writecap=CHPP writes per session
```

- [ ] **Step 7: Update docs/chpp_files.md**

In the file table, change the `challenges` row's `Used Version` from `-` to `1.6`:

```
| challenges              | Challenges                                                                                  | 1.6 (2016-10-27)         | 1.6          |
```

Then add a short note after the transfersearch section (end of file) describing the write actionTypes HO now uses:

```markdown
## CHPP write actionTypes used by HO

HO issues these write actions on behalf of the managed team (exposed over MCP, see
`docs/superpowers/specs/2026-09-02-chpp-write-operations-over-mcp-design.md`):

| file | version | actionType | purpose |
|---|---|---|---|
| `training` | 2.1 | `setTraining` | set senior training type / intensity / stamina share |
| `matchorders` | 3.0 | `setmatchorder` | upload the match order for an upcoming game (pre-existing) |
| `challenges` | 1.6 | `view` / `challenge` / `accept` / `decline` / `withdraw` | list and manage friendly challenges |
| `playerdetails` | 3.0 | `placeBid` | place a transfer bid |
```

- [ ] **Step 8: Build**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL. (POEditor pull line printing `"status":"fail"` is expected and not a failure.)

- [ ] **Step 9: Commit**

```bash
git add src/main/java/core/net/login/OAuthDialog.java src/main/java/core/model/UserParameter.java \
        src/main/java/core/option/McpServerPanel.java src/main/resources/language/English.properties \
        docs/chpp_files.md
git commit -m "feat(mcp): OAuth scopes and per-session cap for CHPP writes"
```

---

## Task 2: Connector URL builders and raw calls

**Files:**
- Modify: `src/main/java/core/net/Connector.java`
- Test: `src/test/java/core/net/ConnectorWriteUrlTest.java` (create)

**Interfaces:**
- Consumes: `Connector`'s `private static final String htUrl` (`"https://chpp.hattrick.org/chppxml.ashx"`), `private String getCHPPWebFile(String)`, `public boolean setSilentDownload(boolean)` returning the previous value, `getMatchOrdersURl()`.
- Produces (all in `core.net.Connector`):
  - `static String setTrainingUrl(int teamId, int trainingType, int trainingLevel, int staminaLevel)` — package-private
  - `String setTraining(int teamId, int trainingType, int trainingLevel, int staminaLevel)` — public, `getCHPPWebFile`
  - `static String challengesUrl(String actionType, java.util.Map<String,String> params)` — package-private
  - `String challenges(String actionType, java.util.Map<String,String> params)` — public, `getCHPPWebFile`
  - `static String placeBidUrl(int playerId, int teamId, long bidAmount, Long maxBidAmount)` — package-private
  - `String placeBid(int playerId, int teamId, long bidAmount, Long maxBidAmount)` — public, `getCHPPWebFile`
  - `String uploadMatchOrder(int matchId, int teamId, MatchType matchType, String orderString, boolean showErrorMessage)` — public, `throws IOException`; the existing 4-arg method delegates with `true`.

- [ ] **Step 1: Write the failing URL-builder test**

Create `src/test/java/core/net/ConnectorWriteUrlTest.java`:

```java
package core.net;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ConnectorWriteUrlTest {

    @Test
    void setTrainingUrl_hasFileVersionActionAndEveryParameter() {
        final var url = Connector.setTrainingUrl(1500, 3, 40, 10);
        assertThat(url).contains("file=training")
                .contains("version=2.1")
                .contains("actionType=setTraining")
                .contains("teamId=1500")
                .contains("trainingType=3")
                .contains("trainingLevel=40")
                .contains("trainingLevelStamina=10");
    }

    @Test
    void challengesUrl_appendsEveryParam() {
        final var params = new LinkedHashMap<String, String>();
        params.put("opponentTeamId", "999");
        params.put("matchType", "0");
        params.put("matchPlace", "2");
        params.put("neutralArenaId", "42");
        final var url = Connector.challengesUrl("challenge", params);
        assertThat(url).contains("file=challenges")
                .contains("version=1.6")
                .contains("actionType=challenge")
                .contains("opponentTeamId=999")
                .contains("matchType=0")
                .contains("matchPlace=2")
                .contains("neutralArenaId=42");
    }

    @Test
    void challengesUrl_viewWithNoParamsIsJustFileVersionAction() {
        final var url = Connector.challengesUrl("view", Map.of());
        assertThat(url).contains("file=challenges").contains("version=1.6").contains("actionType=view");
        assertThat(url).doesNotContain("&opponentTeamId");
    }

    @Test
    void placeBidUrl_includesMaxBidAmountOnlyWhenGiven() {
        assertThat(Connector.placeBidUrl(111, 222, 1_500_000L, null))
                .contains("file=playerdetails")
                .contains("version=3.0")
                .contains("actionType=placeBid")
                .contains("playerID=111")
                .contains("teamId=222")
                .contains("bidAmount=1500000")
                .doesNotContain("maxBidAmount");
        assertThat(Connector.placeBidUrl(111, 222, 1_500_000L, 2_000_000L))
                .contains("bidAmount=1500000")
                .contains("maxBidAmount=2000000");
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew test --tests "core.net.ConnectorWriteUrlTest"`
Expected: FAIL to compile — `Connector.setTrainingUrl` / `challengesUrl` / `placeBidUrl` do not exist.

- [ ] **Step 3: Add the builders and raw calls to Connector**

In `src/main/java/core/net/Connector.java`, add near the other CHPP methods (e.g. after `uploadMatchOrder`). Add `import java.util.Map;` if not already present.

```java
	// ---- training (file=training, version 2.1, actionType=setTraining) ----

	static String setTrainingUrl(int teamId, int trainingType, int trainingLevel, int staminaLevel) {
		return htUrl + "?file=training&version=2.1&actionType=setTraining"
				+ "&teamId=" + teamId
				+ "&trainingType=" + trainingType
				+ "&trainingLevel=" + trainingLevel
				+ "&trainingLevelStamina=" + staminaLevel;
	}

	/** Set the senior training. Echoes the new state under &lt;View&gt; plus &lt;TrainingSet&gt;. */
	public String setTraining(int teamId, int trainingType, int trainingLevel, int staminaLevel) {
		return getCHPPWebFile(setTrainingUrl(teamId, trainingType, trainingLevel, staminaLevel));
	}

	// ---- challenges (file=challenges, version 1.6) ----

	static String challengesUrl(String actionType, Map<String, String> params) {
		StringBuilder url = new StringBuilder(htUrl)
				.append("?file=challenges&version=1.6&actionType=").append(actionType);
		for (Map.Entry<String, String> e : params.entrySet()) {
			url.append('&').append(e.getKey()).append('=').append(e.getValue());
		}
		return url.toString();
	}

	/** view / challenge / accept / decline / withdraw. */
	public String challenges(String actionType, Map<String, String> params) {
		return getCHPPWebFile(challengesUrl(actionType, params));
	}

	// ---- transfer bid (file=playerdetails, version 3.0, actionType=placeBid) ----

	static String placeBidUrl(int playerId, int teamId, long bidAmount, Long maxBidAmount) {
		StringBuilder url = new StringBuilder(htUrl)
				.append("?file=playerdetails&version=3.0&actionType=placeBid")
				.append("&playerID=").append(playerId)
				.append("&teamId=").append(teamId)
				.append("&bidAmount=").append(bidAmount);
		if (maxBidAmount != null) {
			url.append("&maxBidAmount=").append(maxBidAmount);
		}
		return url.toString();
	}

	/** Place a bid. Returns the updated &lt;Player&gt; block from playerdetails. */
	public String placeBid(int playerId, int teamId, long bidAmount, Long maxBidAmount) {
		return getCHPPWebFile(placeBidUrl(playerId, teamId, bidAmount, maxBidAmount));
	}
```

- [ ] **Step 4: Add the uploadMatchOrder showErrorMessage overload**

The current method (line 314) ends its body with:

```java
		String result = readStream(postWebFileWithBodyParameters(urlpara.toString(), paras, true,
				"set_matchorder"));
```

Change the signature to add `boolean showErrorMessage`, use it in the `postWebFileWithBodyParameters` call, and add a 4-arg delegate:

```java
	public String uploadMatchOrder(int matchId, int teamId, MatchType matchType, String orderString)
			throws IOException {
		return uploadMatchOrder(matchId, teamId, matchType, orderString, true);
	}

	public String uploadMatchOrder(int matchId, int teamId, MatchType matchType, String orderString,
			boolean showErrorMessage) throws IOException {
		StringBuilder urlpara = new StringBuilder(getMatchOrdersURl());
		if (teamId > 0 && !HOVerwaltung.instance().getModel().getBasics().isNationalTeam()) {
			urlpara.append("&teamId=").append(teamId);
		}
		if (matchId > 0) {
			urlpara.append("&matchID=").append(matchId);
		}
		urlpara.append("&actionType=setmatchorder");
		urlpara.append("&sourceSystem=").append(matchType.getSourceString());

		Map<String, String> paras = new HashMap<>();
		paras.put("lineup", orderString);
		String result = readStream(postWebFileWithBodyParameters(urlpara.toString(), paras,
				showErrorMessage, "set_matchorder"));
		String sError = XMLCHPPPreParser.getError(result);
		if (!sError.isEmpty()) {
			throw new RuntimeException(sError);
		}
		return result;
	}
```

(`readStream(null)` returns `""`, and `XMLCHPPPreParser.getError("")` returns the "no HRF" message — so a failed silent upload still throws a `RuntimeException` the service can catch and wrap. That is the intended path.)

- [ ] **Step 5: Run the test to verify it passes**

Run: `./gradlew test --tests "core.net.ConnectorWriteUrlTest"`
Expected: PASS (4 tests).

- [ ] **Step 6: Commit**

```bash
git add src/main/java/core/net/Connector.java src/test/java/core/net/ConnectorWriteUrlTest.java
git commit -m "feat(net): Connector URL builders and raw calls for CHPP writes"
```

---

## Task 3: ChppWriteService skeleton, cap, and set-training

**Files:**
- Create: `src/main/java/core/net/ChppWriteService.java`
- Create: `src/test/java/core/net/ChppWriteServiceTest.java`

**Interfaces:**
- Consumes: `Connector.instance().setTraining(...)`, `Connector.instance().setSilentDownload(boolean)`, `Connector.instance().lastError()`, `UserParameter.instance().mcpChppWritesPerSession`, `HOVerwaltung.instance().getModel().getBasics().getTeamId()`, `XMLTrainingParser.parseTrainingFromString(String)` → `Map<String,String>` (keys `TrainingType`, `TrainingLevel`, `StaminaTrainingPart`), `core.file.xml.XMLManager.parseString(String)` / `XMLManager.xmlValue(Element, String)`.
- Produces:
  - `public static synchronized ChppWriteService instance()`
  - `public static class ChppWriteException extends Exception` with `public String errorKey()`
  - `public record TrainingCommitted(int trainingType, int intensity, int staminaShare)`
  - `public TrainingCommitted setTrainingForAgent(int trainingType, int intensity, int staminaShare) throws ChppWriteException`
  - `protected String fetchSetTraining(int teamId, int trainingType, int trainingLevel, int staminaLevel)` — overridden in tests
  - `private synchronized void checkAndCountWrite() throws ChppWriteException` (used by every write family; reads do not call it)
  - package-private field `int writesThisSession` (visible to the same-package test)

- [ ] **Step 1: Write the failing test**

Create `src/test/java/core/net/ChppWriteServiceTest.java`:

```java
package core.net;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class ChppWriteServiceTest {

    /** A training echo with the three values and TrainingSet=true. */
    private static final String TRAINING_OK = """
            <?xml version="1.0" encoding="utf-8"?>
            <HattrickData>
              <FileName>training.xml</FileName>
              <Version>2.1</Version>
              <FetchedDate>2026-09-03 12:00:00</FetchedDate>
              <View>
                <Team>
                  <TeamID>1500</TeamID>
                  <TeamName>HO FC</TeamName>
                  <TrainingLevel>40</TrainingLevel>
                  <StaminaTrainingPart>10</StaminaTrainingPart>
                  <TrainingType>3</TrainingType>
                  <Trainer><TrainerID>1</TrainerID><TrainerName>T</TrainerName><ArrivalDate>2026-01-01 00:00:00</ArrivalDate></Trainer>
                </Team>
              </View>
              <TrainingSet>true</TrainingSet>
            </HattrickData>
            """;

    private static final String TRAINING_NOT_SET = TRAINING_OK.replace(
            "<TrainingSet>true</TrainingSet>", "<TrainingSet>false</TrainingSet>");

    /** A service whose fetch* methods answer from canned strings, never the network. */
    static class StubService extends ChppWriteService {
        String trainingResponse = TRAINING_OK;
        int trainingFetches;

        @Override
        protected String fetchSetTraining(int teamId, int trainingType, int trainingLevel, int staminaLevel) {
            trainingFetches++;
            return trainingResponse;
        }
    }

    private StubService service;

    @BeforeEach
    void setUp() {
        service = new StubService();
        core.model.UserParameter.instance().mcpChppWritesPerSession = 20;
    }

    @Test
    void setTraining_returnsTheCommittedValuesFromTheEcho() throws Exception {
        final var committed = service.setTrainingForAgent(3, 40, 10);
        assertThat(committed.trainingType()).isEqualTo(3);
        assertThat(committed.intensity()).isEqualTo(40);
        assertThat(committed.staminaShare()).isEqualTo(10);
        assertThat(service.writesThisSession).isEqualTo(1);
    }

    @Test
    void setTraining_trainingSetFalseIsAFailure() {
        service.trainingResponse = TRAINING_NOT_SET;
        assertThatExceptionOfType(ChppWriteService.ChppWriteException.class)
                .isThrownBy(() -> service.setTrainingForAgent(3, 40, 10));
    }

    @Test
    void setTraining_blankResponseIsAFailureCarryingLastError() {
        service.trainingResponse = "";
        assertThatExceptionOfType(ChppWriteService.ChppWriteException.class)
                .isThrownBy(() -> service.setTrainingForAgent(3, 40, 10));
    }

    @Test
    void setTraining_rejectsOutOfRangeBeforeAnyFetch() {
        assertThatExceptionOfType(ChppWriteService.ChppWriteException.class)
                .isThrownBy(() -> service.setTrainingForAgent(3, 200, 10));
        assertThatExceptionOfType(ChppWriteService.ChppWriteException.class)
                .isThrownBy(() -> service.setTrainingForAgent(3, 40, 1));
        assertThat(service.trainingFetches).isZero();
    }

    @Test
    void cap_refusesThe21stWriteAndNamesTheOptionsPanel() throws Exception {
        core.model.UserParameter.instance().mcpChppWritesPerSession = 20;
        for (int i = 0; i < 20; i++) {
            service.setTrainingForAgent(3, 40, 10);
        }
        assertThatExceptionOfType(ChppWriteService.ChppWriteException.class)
                .isThrownBy(() -> service.setTrainingForAgent(3, 40, 10))
                .withMessageContaining("20")
                .withMessageContaining("MCP server");
    }
}
```

> The exact nesting of the real `setTraining` echo is not in HO's fixtures. `XMLTrainingParser.parseDetails` reads `TrainingLevel` / `StaminaTrainingPart` / `TrainingType` via `getElementsByTagName` (recursive), so the wrapping element does not matter. If a captured real response later shows `parseTrainingFromString` returning an empty map for the echo, switch the three reads in Step 2 to direct `XMLManager.xmlValue(root, "...")` calls — the test fixture above already works with either approach.

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew test --tests "core.net.ChppWriteServiceTest"`
Expected: FAIL to compile — `ChppWriteService` does not exist.

- [ ] **Step 3: Create ChppWriteService**

Create `src/main/java/core/net/ChppWriteService.java`:

```java
package core.net;

import core.file.xml.XMLManager;
import core.file.xml.XMLTrainingParser;
import core.model.HOVerwaltung;
import core.model.UserParameter;
import core.util.HOLogger;
import org.w3c.dom.Document;

/**
 * The network and parse core of the CHPP write families HO exposes over MCP: set training, match
 * order upload, friendly challenges, and transfer bids.
 * <p>
 * Shaped exactly like {@link TransferSearchService}: a {@code fetch*} seam overridden in tests, a
 * per-session write cap ({@link UserParameter#mcpChppWritesPerSession}), and a typed
 * {@link ChppWriteException}. Every {@code fetch*} runs with {@link Connector#setSilentDownload}
 * on - a modal CHPP error dialog off the EDT would hang the MCP request until it times out
 * (see the spec, section 1.4).
 */
public class ChppWriteService {

    /** A CHPP write that Hattrick rejected or did not confirm, with a readable reason. */
    public static class ChppWriteException extends Exception {
        private final String errorKey;

        ChppWriteException(String errorKey, String message) {
            super(message);
            this.errorKey = errorKey;
        }

        /** The translation key of the message, or null if there is no translated form. */
        public String errorKey() {
            return errorKey;
        }
    }

    private static ChppWriteService instance;

    public static synchronized ChppWriteService instance() {
        if (instance == null) {
            instance = new ChppWriteService();
        }
        return instance;
    }

    /** Package private: production code uses {@link #instance()}, tests subclass. */
    ChppWriteService() {
    }

    /** Network writes issued in this HO session. Reads (challenge list, bid preview) do not count. */
    int writesThisSession;

    private synchronized void checkAndCountWrite() throws ChppWriteException {
        final var cap = UserParameter.instance().mcpChppWritesPerSession;
        if (writesThisSession >= cap) {
            throw new ChppWriteException(null, "The CHPP write cap of " + cap + " writes per HO "
                    + "session is used up. Raise it in HO's options under MCP server, or restart HO, "
                    + "before writing again.");
        }
        writesThisSession++;
    }

    /** {@link Connector#lastError()} if there is one, else a pointer to the log. */
    private static String reason() {
        final var cause = Connector.instance().lastError();
        return cause == null ? "no answer, see HO's log." : cause;
    }

    // ===================== training =====================

    public record TrainingCommitted(int trainingType, int intensity, int staminaShare) {
    }

    public TrainingCommitted setTrainingForAgent(int trainingType, int intensity, int staminaShare)
            throws ChppWriteException {
        if (intensity < 0 || intensity > 100) {
            throw new ChppWriteException(null, "intensity must be 0..100, was " + intensity);
        }
        if (staminaShare < 5 || staminaShare > 100) {
            throw new ChppWriteException(null, "staminaShare must be 5..100, was " + staminaShare);
        }
        final var teamId = HOVerwaltung.instance().getModel().getBasics().getTeamId();
        checkAndCountWrite();

        final String xml;
        try {
            xml = fetchSetTraining(teamId, trainingType, intensity, staminaShare);
        } catch (Exception e) {
            HOLogger.instance().log(ChppWriteService.class, e);
            throw new ChppWriteException(null, "Error setting training: " + e.getMessage());
        }
        if (xml == null || xml.isBlank()) {
            throw new ChppWriteException(null, "Hattrick rejected the training change: " + reason());
        }

        final Document doc = XMLManager.parseString(xml);
        if (doc != null && "false".equalsIgnoreCase(
                XMLManager.xmlValue(doc.getDocumentElement(), "TrainingSet"))) {
            throw new ChppWriteException(null,
                    "Hattrick did not apply the training change (TrainingSet=false).");
        }

        final var echo = XMLTrainingParser.parseTrainingFromString(xml);
        if (!echo.containsKey("TrainingType") || !echo.containsKey("TrainingLevel")
                || !echo.containsKey("StaminaTrainingPart")) {
            throw new ChppWriteException(null,
                    "Hattrick did not echo the new training state: " + reason());
        }
        return new TrainingCommitted(
                Integer.parseInt(echo.get("TrainingType").trim()),
                Integer.parseInt(echo.get("TrainingLevel").trim()),
                Integer.parseInt(echo.get("StaminaTrainingPart").trim()));
    }

    /** The CHPP call. Overridden in tests so that no network access is needed. */
    protected String fetchSetTraining(int teamId, int trainingType, int trainingLevel, int staminaLevel) {
        final var was = Connector.instance().setSilentDownload(true);
        try {
            return Connector.instance().setTraining(teamId, trainingType, trainingLevel, staminaLevel);
        } finally {
            Connector.instance().setSilentDownload(was);
        }
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew test --tests "core.net.ChppWriteServiceTest"`
Expected: PASS (5 tests). If `setTraining_rejectsOutOfRangeBeforeAnyFetch` or `cap_*` fails on `HOVerwaltung.instance().getModel()` being null in a unit test, move the `teamId` read to *after* `checkAndCountWrite()` is not enough — instead guard it: `final var model = HOVerwaltung.instance().getModel();` and if null throw `ChppWriteException(null, "No team data loaded.")`. Re-run.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/core/net/ChppWriteService.java src/test/java/core/net/ChppWriteServiceTest.java
git commit -m "feat(net): ChppWriteService with per-session cap and set-training"
```

---

## Task 4: XMLChallengesParser

**Files:**
- Create: `src/main/java/core/file/xml/XMLChallengesParser.java`
- Create: `src/test/java/core/file/xml/XMLChallengesParserTest.java`

**Interfaces:**
- Consumes: `core.file.xml.XMLManager.parseString(String)` → `org.w3c.dom.Document`, `XMLManager.xmlValue(Element, String)` → `String` ("" when missing).
- Produces:
  - `public record Challenge(long trainingMatchId, String matchTime, int friendlyType, int opponentTeamId, String opponentName, String arenaName, boolean isAgreed)`
  - `public record ChallengeLists(java.util.List<Challenge> outgoing, java.util.List<Challenge> incoming)`
  - `public static ChallengeLists parse(String xml)` — never throws; returns empty lists on garbage.

- [ ] **Step 1: Write the failing test**

Create `src/test/java/core/file/xml/XMLChallengesParserTest.java`:

```java
package core.file.xml;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class XMLChallengesParserTest {

    /**
     * Hand-built from the challenges 1.6 schema (spec section 1.6). VERIFY the element casing
     * (notably MatchTime and IsAgreed) against a real `challenges&actionType=view` response or the
     * reference structs at github.com/lucianoq/hattrick before merging.
     */
    private static final String VIEW = """
            <?xml version="1.0" encoding="utf-8"?>
            <HattrickData>
              <FileName>challenges.xml</FileName>
              <Version>1.6</Version>
              <IsPossibleToChallengeMidWeek>false</IsPossibleToChallengeMidWeek>
              <IsPossibleToChallengeWeekend>true</IsPossibleToChallengeWeekend>
              <ChallengesByMe>
                <Challenge>
                  <TrainingMatchID>733551221</TrainingMatchID>
                  <MatchTime>2026-09-06 15:00:00</MatchTime>
                  <FriendlyType>1</FriendlyType>
                  <Opponent>
                    <TeamID>1877</TeamID>
                    <TeamName>Rivals FC</TeamName>
                    <ArenaName>Rivals Arena</ArenaName>
                    <IsAgreed>false</IsAgreed>
                  </Opponent>
                </Challenge>
              </ChallengesByMe>
              <OffersByOthers>
                <Offer>
                  <TrainingMatchID>733559999</TrainingMatchID>
                  <MatchTime>2026-09-06 18:00:00</MatchTime>
                  <FriendlyType>0</FriendlyType>
                  <Opponent>
                    <TeamID>2999</TeamID>
                    <TeamName>Challengers United</TeamName>
                    <ArenaName>CU Park</ArenaName>
                    <IsAgreed>true</IsAgreed>
                  </Opponent>
                </Offer>
              </OffersByOthers>
            </HattrickData>
            """;

    @Test
    void parse_readsBothLists() {
        final var lists = XMLChallengesParser.parse(VIEW);

        assertThat(lists.outgoing()).hasSize(1);
        final var out = lists.outgoing().get(0);
        assertThat(out.trainingMatchId()).isEqualTo(733551221L);
        assertThat(out.matchTime()).isEqualTo("2026-09-06 15:00:00");
        assertThat(out.friendlyType()).isEqualTo(1);
        assertThat(out.opponentTeamId()).isEqualTo(1877);
        assertThat(out.opponentName()).isEqualTo("Rivals FC");
        assertThat(out.arenaName()).isEqualTo("Rivals Arena");
        assertThat(out.isAgreed()).isFalse();

        assertThat(lists.incoming()).hasSize(1);
        assertThat(lists.incoming().get(0).trainingMatchId()).isEqualTo(733559999L);
        assertThat(lists.incoming().get(0).isAgreed()).isTrue();
    }

    @Test
    void parse_garbageIsTwoEmptyLists() {
        final var lists = XMLChallengesParser.parse("not xml");
        assertThat(lists.outgoing()).isEmpty();
        assertThat(lists.incoming()).isEmpty();
    }

    @Test
    void parse_missingContainersAreEmptyLists() {
        final var lists = XMLChallengesParser.parse(
                "<HattrickData><Version>1.6</Version></HattrickData>");
        assertThat(lists.outgoing()).isEmpty();
        assertThat(lists.incoming()).isEmpty();
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew test --tests "core.file.xml.XMLChallengesParserTest"`
Expected: FAIL to compile — `XMLChallengesParser` does not exist.

- [ ] **Step 3: Create the parser**

Create `src/main/java/core/file/xml/XMLChallengesParser.java`:

```java
package core.file.xml;

import core.util.HOLogger;
import org.w3c.dom.Element;

import java.util.ArrayList;
import java.util.List;

/**
 * Parses a {@code challenges} {@code view} response (version 1.6) into the outgoing challenges
 * this team has sent and the offers other teams have sent it.
 * <p>
 * Built like {@link XMLTransferSearchParser}: a static {@code parse} that never throws and returns
 * empty lists on anything it cannot read.
 */
public class XMLChallengesParser {

    public record Challenge(long trainingMatchId, String matchTime, int friendlyType,
                            int opponentTeamId, String opponentName, String arenaName,
                            boolean isAgreed) {
    }

    public record ChallengeLists(List<Challenge> outgoing, List<Challenge> incoming) {
    }

    private XMLChallengesParser() {
    }

    public static ChallengeLists parse(String xml) {
        final var doc = XMLManager.parseString(xml);
        if (doc == null) {
            return new ChallengeLists(List.of(), List.of());
        }
        final var root = doc.getDocumentElement();
        return new ChallengeLists(
                parseList(root, "ChallengesByMe", "Challenge"),
                parseList(root, "OffersByOthers", "Offer"));
    }

    private static List<Challenge> parseList(Element root, String containerTag, String itemTag) {
        final var result = new ArrayList<Challenge>();
        final var container = (Element) root.getElementsByTagName(containerTag).item(0);
        if (container == null) {
            return result;
        }
        final var items = container.getElementsByTagName(itemTag);
        for (int i = 0; i < items.getLength(); i++) {
            try {
                result.add(parseItem((Element) items.item(i)));
            } catch (RuntimeException e) {
                HOLogger.instance().log(XMLChallengesParser.class, e);
            }
        }
        return result;
    }

    private static Challenge parseItem(Element item) {
        final var opponent = (Element) item.getElementsByTagName("Opponent").item(0);
        return new Challenge(
                parseLong(XMLManager.xmlValue(item, "TrainingMatchID")),
                XMLManager.xmlValue(item, "MatchTime"),
                parseInt(XMLManager.xmlValue(item, "FriendlyType")),
                opponent == null ? 0 : parseInt(XMLManager.xmlValue(opponent, "TeamID")),
                opponent == null ? "" : XMLManager.xmlValue(opponent, "TeamName"),
                opponent == null ? "" : XMLManager.xmlValue(opponent, "ArenaName"),
                opponent != null
                        && "true".equalsIgnoreCase(XMLManager.xmlValue(opponent, "IsAgreed")));
    }

    private static long parseLong(String s) {
        return s == null || s.isBlank() ? 0L : Long.parseLong(s.trim());
    }

    private static int parseInt(String s) {
        return s == null || s.isBlank() ? 0 : Integer.parseInt(s.trim());
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew test --tests "core.file.xml.XMLChallengesParserTest"`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/core/file/xml/XMLChallengesParser.java src/test/java/core/file/xml/XMLChallengesParserTest.java
git commit -m "feat(xml): XMLChallengesParser for the challenges view file"
```

---

## Task 5: ChppWriteService — challenges

**Files:**
- Modify: `src/main/java/core/net/ChppWriteService.java`
- Modify: `src/test/java/core/net/ChppWriteServiceTest.java`

**Interfaces:**
- Consumes: `Connector.instance().challenges(String, Map<String,String>)`, `XMLChallengesParser.parse(String)` → `ChallengeLists`, `XMLCHPPPreParser.getError(String)` → `String` ("" when no error).
- Produces (in `ChppWriteService`):
  - `public XMLChallengesParser.ChallengeLists listChallengesForAgent(boolean weekend) throws ChppWriteException` — a read; does **not** call `checkAndCountWrite()`.
  - `public void arrangeChallengeForAgent(int opponentTeamId, int friendlyType, int matchPlace, Integer neutralArenaId, boolean weekend) throws ChppWriteException` — guards `matchPlace == 2 ⇒ neutralArenaId != null` *before* the cap check and call.
  - `public void respondToChallengeForAgent(String verb, long trainingMatchId) throws ChppWriteException` — `verb` whitelisted to `accept` / `decline` / `withdraw` before the cap check.
  - `protected String fetchChallenges(String actionType, java.util.Map<String,String> params)` — overridden in tests.

- [ ] **Step 1: Add the failing tests**

In `src/test/java/core/net/ChppWriteServiceTest.java`, extend `StubService` and add tests:

```java
    // --- add to StubService ---
        String challengesResponse = """
                <HattrickData><Version>1.6</Version>
                  <ChallengesByMe/><OffersByOthers/>
                </HattrickData>
                """;
        int challengeFetches;
        String lastChallengeAction;

        @Override
        protected String fetchChallenges(String actionType, java.util.Map<String, String> params) {
            challengeFetches++;
            lastChallengeAction = actionType;
            return challengesResponse;
        }
```

```java
    @Test
    void listChallenges_doesNotCountAgainstTheWriteCap() throws Exception {
        service.listChallengesForAgent(false);
        service.listChallengesForAgent(true);
        assertThat(service.writesThisSession).isZero();
        assertThat(service.lastChallengeAction).isEqualTo("view");
    }

    @Test
    void listChallenges_blankResponseIsAFailure() {
        service.challengesResponse = "";
        assertThatExceptionOfType(ChppWriteService.ChppWriteException.class)
                .isThrownBy(() -> service.listChallengesForAgent(false));
    }

    @Test
    void arrangeChallenge_neutralWithoutArenaIsRejectedBeforeAnyFetch() {
        assertThatExceptionOfType(ChppWriteService.ChppWriteException.class)
                .isThrownBy(() -> service.arrangeChallengeForAgent(999, 0, 2, null, false));
        assertThat(service.challengeFetches).isZero();
    }

    @Test
    void arrangeChallenge_countsOneWrite() throws Exception {
        service.arrangeChallengeForAgent(999, 1, 2, 42, true);
        assertThat(service.writesThisSession).isEqualTo(1);
        assertThat(service.lastChallengeAction).isEqualTo("challenge");
    }

    @Test
    void respondToChallenge_rejectsAnUnknownVerbBeforeAnyFetch() {
        assertThatExceptionOfType(ChppWriteService.ChppWriteException.class)
                .isThrownBy(() -> service.respondToChallengeForAgent("cancel", 733551221L));
        assertThat(service.challengeFetches).isZero();
    }

    @Test
    void respondToChallenge_acceptCountsOneWrite() throws Exception {
        service.respondToChallengeForAgent("accept", 733551221L);
        assertThat(service.writesThisSession).isEqualTo(1);
        assertThat(service.lastChallengeAction).isEqualTo("accept");
    }
```

- [ ] **Step 2: Run to verify failure**

Run: `./gradlew test --tests "core.net.ChppWriteServiceTest"`
Expected: FAIL to compile — `fetchChallenges` / `listChallengesForAgent` / … do not exist.

- [ ] **Step 3: Implement the challenges methods**

Append to `ChppWriteService` (add imports `core.file.xml.XMLCHPPPreParser`, `core.file.xml.XMLChallengesParser`, `java.util.LinkedHashMap`, `java.util.Map`, `java.util.Set`):

```java
    // ===================== challenges =====================

    private static final Set<String> RESPONSE_VERBS = Set.of("accept", "decline", "withdraw");

    /** A read. Shares the silent-fetch handling but does not count against the write cap. */
    public XMLChallengesParser.ChallengeLists listChallengesForAgent(boolean weekend)
            throws ChppWriteException {
        final Map<String, String> params = weekend
                ? Map.of("isWeekendFriendly", "1") : Map.of();
        final var xml = fetchChallenges("view", params);
        if (xml == null || xml.isBlank()) {
            throw new ChppWriteException(null, "Hattrick rejected the challenge list: " + reason());
        }
        return XMLChallengesParser.parse(xml);
    }

    public void arrangeChallengeForAgent(int opponentTeamId, int friendlyType, int matchPlace,
            Integer neutralArenaId, boolean weekend) throws ChppWriteException {
        if (matchPlace == 2 && neutralArenaId == null) {
            throw new ChppWriteException(null,
                    "A neutral-ground challenge needs neutralArenaId.");
        }
        checkAndCountWrite();
        final var params = new LinkedHashMap<String, String>();
        params.put("opponentTeamId", String.valueOf(opponentTeamId));
        params.put("matchType", String.valueOf(friendlyType));   // 0 normal rules, 1 cup rules
        params.put("matchPlace", String.valueOf(matchPlace));    // 0 home, 1 away, 2 neutral
        if (matchPlace == 2) {
            params.put("neutralArenaId", String.valueOf(neutralArenaId));
        }
        if (weekend) {
            params.put("isWeekendFriendly", "1");
        }
        requireAccepted(fetchChallenges("challenge", params), "arrange challenge");
    }

    public void respondToChallengeForAgent(String verb, long trainingMatchId)
            throws ChppWriteException {
        if (!RESPONSE_VERBS.contains(verb)) {
            throw new ChppWriteException(null,
                    "verb must be accept, decline or withdraw, was " + verb);
        }
        checkAndCountWrite();
        requireAccepted(
                fetchChallenges(verb, Map.of("trainingMatchId", String.valueOf(trainingMatchId))),
                "challenge " + verb);
    }

    /** No {@code <error>} block and a non-blank body means Hattrick took the write. */
    private static void requireAccepted(String xml, String what) throws ChppWriteException {
        if (xml == null || xml.isBlank()) {
            throw new ChppWriteException(null, "Hattrick rejected the " + what + ": " + reason());
        }
        final var error = XMLCHPPPreParser.getError(xml);
        if (!error.isEmpty()) {
            throw new ChppWriteException(null, "Hattrick rejected the " + what + ": " + error);
        }
    }

    protected String fetchChallenges(String actionType, Map<String, String> params) {
        final var was = Connector.instance().setSilentDownload(true);
        try {
            return Connector.instance().challenges(actionType, params);
        } finally {
            Connector.instance().setSilentDownload(was);
        }
    }
```

- [ ] **Step 4: Run to verify passing**

Run: `./gradlew test --tests "core.net.ChppWriteServiceTest"`
Expected: PASS (11 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/core/net/ChppWriteService.java src/test/java/core/net/ChppWriteServiceTest.java
git commit -m "feat(net): ChppWriteService challenge list / arrange / respond"
```

---

## Task 6: ChppWriteService — match order and bid

**Files:**
- Modify: `src/main/java/core/net/ChppWriteService.java`
- Modify: `src/test/java/core/net/ChppWriteServiceTest.java`

**Interfaces:**
- Consumes: `Connector.instance().uploadMatchOrder(int, int, MatchType, String, boolean)` (`throws IOException`, throws `RuntimeException` on a CHPP error), `Connector.instance().placeBid(int, int, long, Long)`, `core.model.enums.MatchType`, `XMLManager`.
- Produces (in `ChppWriteService`):
  - `public String sendMatchOrderForAgent(int matchId, core.model.enums.MatchType matchType, String orderString) throws ChppWriteException` — counts one write; wraps any exception from `Connector` in `ChppWriteException`.
  - `public record BidResult(String highestBid, String deadline, String bidderTeamName)`
  - `public BidResult placeBidForAgent(int playerId, long bidAmount, Long maxBidAmount) throws ChppWriteException` — `errorKey` `"BidFailed"` on rejection.
  - `protected String fetchMatchOrder(int matchId, core.model.enums.MatchType matchType, String orderString) throws Exception`
  - `protected String fetchPlaceBid(int playerId, int teamId, long bidAmount, Long maxBidAmount)`

- [ ] **Step 1: Add the failing tests**

In `ChppWriteServiceTest`, extend `StubService`:

```java
        String matchOrderResponse = "<HattrickData><MatchData>ok</MatchData></HattrickData>";
        RuntimeException matchOrderFailure;
        int matchOrderFetches;

        @Override
        protected String fetchMatchOrder(int matchId, core.model.enums.MatchType matchType, String orderString) {
            matchOrderFetches++;
            if (matchOrderFailure != null) throw matchOrderFailure;
            return matchOrderResponse;
        }

        String bidResponse = """
                <HattrickData><Player>
                  <PlayerID>111</PlayerID>
                  <HighestBid>1750000</HighestBid>
                  <Deadline>2026-09-05 20:00:00</Deadline>
                  <HighestBidderTeamName>HO FC</HighestBidderTeamName>
                </Player></HattrickData>
                """;
        int bidFetches;

        @Override
        protected String fetchPlaceBid(int playerId, int teamId, long bidAmount, Long maxBidAmount) {
            bidFetches++;
            return bidResponse;
        }
```

```java
    @Test
    void sendMatchOrder_countsOneWriteAndReturnsTheResponse() throws Exception {
        final var response = service.sendMatchOrderForAgent(
                12345, core.model.enums.MatchType.LEAGUE, "{\"positions\":[]}");
        assertThat(response).contains("ok");
        assertThat(service.writesThisSession).isEqualTo(1);
    }

    @Test
    void sendMatchOrder_wrapsAConnectorFailure() {
        service.matchOrderFailure = new RuntimeException("CHPP Fehler - 50 setmatchorder");
        assertThatExceptionOfType(ChppWriteService.ChppWriteException.class)
                .isThrownBy(() -> service.sendMatchOrderForAgent(
                        1, core.model.enums.MatchType.LEAGUE, "{}"))
                .withMessageContaining("50");
    }

    @Test
    void placeBid_parsesTheReturnedPlayerBlock() throws Exception {
        final var result = service.placeBidForAgent(111, 1_500_000L, null);
        assertThat(result.highestBid()).isEqualTo("1750000");
        assertThat(result.deadline()).isEqualTo("2026-09-05 20:00:00");
        assertThat(service.writesThisSession).isEqualTo(1);
    }

    @Test
    void placeBid_blankResponseFailsWithBidFailedKey() {
        service.bidResponse = "";
        assertThatExceptionOfType(ChppWriteService.ChppWriteException.class)
                .isThrownBy(() -> service.placeBidForAgent(111, 1_500_000L, null))
                .satisfies(e -> assertThat(e.errorKey()).isEqualTo("BidFailed"));
    }
```

> Check `MatchType.LEAGUE` is the actual enum constant name (`core/model/enums/MatchType.java`). If it differs, use whatever the league constant is called.

- [ ] **Step 2: Run to verify failure**

Run: `./gradlew test --tests "core.net.ChppWriteServiceTest"`
Expected: FAIL to compile.

- [ ] **Step 3: Implement**

Append to `ChppWriteService` (imports: `core.model.enums.MatchType`, `org.w3c.dom.Element`):

```java
    // ===================== match order =====================

    public String sendMatchOrderForAgent(int matchId, MatchType matchType, String orderString)
            throws ChppWriteException {
        if (matchType == null) {
            throw new ChppWriteException(null, "Unknown matchType.");
        }
        checkAndCountWrite();
        try {
            final var response = fetchMatchOrder(matchId, matchType, orderString);
            if (response == null || response.isBlank()) {
                throw new ChppWriteException(null,
                        "Hattrick rejected the match order: " + reason());
            }
            return response;
        } catch (ChppWriteException e) {
            throw e;
        } catch (Exception e) {
            HOLogger.instance().log(ChppWriteService.class, e);
            throw new ChppWriteException(null,
                    "Hattrick rejected the match order: " + e.getMessage());
        }
    }

    protected String fetchMatchOrder(int matchId, MatchType matchType, String orderString)
            throws Exception {
        final var teamId = HOVerwaltung.instance().getModel().getBasics().getTeamId();
        final var was = Connector.instance().setSilentDownload(true);
        try {
            return Connector.instance().uploadMatchOrder(matchId, teamId, matchType, orderString, false);
        } finally {
            Connector.instance().setSilentDownload(was);
        }
    }

    // ===================== transfer bid =====================

    public record BidResult(String highestBid, String deadline, String bidderTeamName) {
    }

    public BidResult placeBidForAgent(int playerId, long bidAmount, Long maxBidAmount)
            throws ChppWriteException {
        final var teamId = HOVerwaltung.instance().getModel().getBasics().getTeamId();
        checkAndCountWrite();

        final String xml;
        try {
            xml = fetchPlaceBid(playerId, teamId, bidAmount, maxBidAmount);
        } catch (Exception e) {
            HOLogger.instance().log(ChppWriteService.class, e);
            throw new ChppWriteException("BidFailed", "Error placing the bid: " + e.getMessage());
        }
        if (xml == null || xml.isBlank()) {
            throw new ChppWriteException("BidFailed", "Hattrick rejected the bid: " + reason());
        }
        final var doc = XMLManager.parseString(xml);
        final var player = doc == null ? null
                : (Element) doc.getDocumentElement().getElementsByTagName("Player").item(0);
        if (player == null) {
            throw new ChppWriteException("BidFailed",
                    "Hattrick did not return the player after the bid: " + reason());
        }
        return new BidResult(
                XMLManager.xmlValue(player, "HighestBid"),
                XMLManager.xmlValue(player, "Deadline"),
                XMLManager.xmlValue(player, "HighestBidderTeamName"));
    }

    protected String fetchPlaceBid(int playerId, int teamId, long bidAmount, Long maxBidAmount) {
        final var was = Connector.instance().setSilentDownload(true);
        try {
            return Connector.instance().placeBid(playerId, teamId, bidAmount, maxBidAmount);
        } finally {
            Connector.instance().setSilentDownload(was);
        }
    }
```

> `HighestBidderTeamName` is a guess at the field name inside the returned `<Player>` block for `placeBid`. Verify against a real `placeBid` response or the reference implementation; adjust the tag name if needed (the test only asserts `highestBid` / `deadline`).

- [ ] **Step 4: Run to verify passing**

Run: `./gradlew test --tests "core.net.ChppWriteServiceTest"`
Expected: PASS (15 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/core/net/ChppWriteService.java src/test/java/core/net/ChppWriteServiceTest.java
git commit -m "feat(net): ChppWriteService match-order upload and transfer bid"
```

---

## Task 7: McpJson.reqLong/optLong, set_training tool, and the challenge tools

**Files:**
- Modify: `src/main/java/core/mcp/McpJson.java`
- Modify: `src/main/java/core/mcp/TrainingTools.java`
- Modify: `src/main/java/core/mcp/MatchTools.java`
- Test: `src/test/java/core/mcp/TrainingToolsTest.java` (create), `src/test/java/core/mcp/MatchToolsTest.java` (create)

**Interfaces:**
- Consumes: `McpTool` record + `McpTool.schema/properties/prop`, `McpToolException`, `McpJson.reqInt/optInt/optBool/optString/optInteger`, `ChppWriteService.instance()` and its `setTrainingForAgent` / `listChallengesForAgent` / `arrangeChallengeForAgent` / `respondToChallengeForAgent`, `XMLChallengesParser.Challenge`.
- Produces:
  - `McpJson.reqLong(JsonObject, String) → long`, `McpJson.optLong(JsonObject, String) → Long`
  - `TrainingTools` tool `set_training` (added to `tools()` list) + `private static JsonObject setTraining(JsonObject) throws McpToolException`
  - `MatchTools` tools `list_challenges`, `arrange_challenge`, `respond_to_challenge` (added to `tools()` list) + their handlers.

- [ ] **Step 1: Add reqLong / optLong (with a check)**

In `src/main/java/core/mcp/McpJson.java`, next to `reqInt` / `optInteger`:

```java
    public static long reqLong(JsonObject args, String name) {
        if (!args.has(name) || args.get(name).isJsonNull()) {
            throw new IllegalArgumentException("Missing required argument: " + name);
        }
        return args.get(name).getAsLong();
    }

    public static Long optLong(JsonObject args, String name) {
        return args.has(name) && !args.get(name).isJsonNull() ? args.get(name).getAsLong() : null;
    }
```

These two one-liners are exercised by `MatchToolsTest` and `TransferToolsTest`; no dedicated test.

- [ ] **Step 2: Write the failing TrainingToolsTest**

Create `src/test/java/core/mcp/TrainingToolsTest.java`:

```java
package core.mcp;

import com.google.gson.JsonObject;
import core.net.ChppWriteService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class TrainingToolsTest {

    private ChppWriteService original;

    @AfterEach
    void restore() {
        if (original != null) {
            ChppWriteService.setInstanceForTest(original);
        }
    }

    private void installService(ChppWriteService stub) {
        original = ChppWriteService.instance();
        ChppWriteService.setInstanceForTest(stub);
    }

    private static JsonObject args(int type, int intensity, int stamina) {
        final var a = new JsonObject();
        a.addProperty("trainingType", type);
        a.addProperty("intensity", intensity);
        a.addProperty("staminaShare", stamina);
        return a;
    }

    @Test
    void setTraining_rejectsIntensityOutOfRangeWithoutCallingTheService() {
        installService(new ChppWriteService() {
            @Override
            public TrainingCommitted setTrainingForAgent(int t, int i, int s) {
                throw new AssertionError("service must not be called");
            }
        });
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> TrainingTools.tools().stream()
                        .filter(x -> x.name().equals("set_training")).findFirst().orElseThrow()
                        .handler().call(args(3, 150, 10)))
                .withMessageContaining("0..100");
    }

    @Test
    void setTraining_echoesTheCommittedValues() throws Exception {
        installService(new ChppWriteService() {
            @Override
            public TrainingCommitted setTrainingForAgent(int t, int i, int s) {
                return new TrainingCommitted(t, i, s);
            }
        });
        final var result = TrainingTools.tools().stream()
                .filter(x -> x.name().equals("set_training")).findFirst().orElseThrow()
                .handler().call(args(3, 40, 10));
        assertThat(result.get("status").getAsString()).isEqualTo("ok");
        assertThat(result.getAsJsonObject("committed").get("intensity").getAsInt()).isEqualTo(40);
        assertThat(result.get("note").getAsString()).contains("not refreshed");
    }
}
```

This test needs a test seam on the singleton. Add it to `ChppWriteService`:

```java
    /** Test-only: swap the singleton so a tool test can inject a stub. */
    public static synchronized void setInstanceForTest(ChppWriteService replacement) {
        instance = replacement;
    }
```

(This mirrors nothing pre-existing, but `TransferSearchService` tools are tested by subclassing the service, not the tool; the tools here are new and thin, and a settable singleton is the smallest seam. If the codebase gains a cleaner pattern later, revisit.)

- [ ] **Step 3: Run to verify failure**

Run: `./gradlew test --tests "core.mcp.TrainingToolsTest"`
Expected: FAIL to compile — `set_training` tool and `setInstanceForTest` do not exist.

- [ ] **Step 4: Add set_training to TrainingTools**

In `src/main/java/core/mcp/TrainingTools.java`, add to the `tools()` `List.of(...)`:

```java
                new McpTool("set_training",
                        "Set the senior team's training. Call get_training first for the current "
                                + "values and for what each trainingType id trains. This leaves the "
                                + "machine and changes training in Hattrick immediately. HO's local "
                                + "snapshot is NOT refreshed until the next team download.",
                        McpTool.schema(McpTool.properties(
                                        "trainingType", McpTool.prop("integer",
                                                "training type id; see get_training for the meanings"),
                                        "intensity", McpTool.prop("integer",
                                                "training intensity, 0..100"),
                                        "staminaShare", McpTool.prop("integer",
                                                "stamina share, 5..100")),
                                "trainingType", "intensity", "staminaShare"),
                        TrainingTools::setTraining)
```

Add the handler:

```java
    private static JsonObject setTraining(JsonObject args) throws McpToolException {
        final var trainingType = McpJson.reqInt(args, "trainingType");
        final var intensity = McpJson.reqInt(args, "intensity");
        final var staminaShare = McpJson.reqInt(args, "staminaShare");
        if (intensity < 0 || intensity > 100) {
            throw new IllegalArgumentException("intensity must be 0..100, was " + intensity);
        }
        if (staminaShare < 5 || staminaShare > 100) {
            throw new IllegalArgumentException("staminaShare must be 5..100, was " + staminaShare);
        }
        final ChppWriteService.TrainingCommitted committed;
        try {
            committed = ChppWriteService.instance()
                    .setTrainingForAgent(trainingType, intensity, staminaShare);
        } catch (ChppWriteService.ChppWriteException e) {
            throw new McpToolException(e.getMessage());
        }
        final var out = new JsonObject();
        out.addProperty("status", "ok");
        final var c = new JsonObject();
        c.addProperty("trainingType", committed.trainingType());
        c.addProperty("intensity", committed.intensity());
        c.addProperty("staminaShare", committed.staminaShare());
        out.add("committed", c);
        out.addProperty("note", "HO's snapshot is not refreshed until the next team download.");
        return out;
    }
```

Add imports as needed (`core.net.ChppWriteService`).

- [ ] **Step 5: Write the failing MatchToolsTest**

Create `src/test/java/core/mcp/MatchToolsTest.java`:

```java
package core.mcp;

import com.google.gson.JsonObject;
import core.file.xml.XMLChallengesParser;
import core.net.ChppWriteService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class MatchToolsTest {

    private ChppWriteService original;

    @AfterEach
    void restore() {
        if (original != null) ChppWriteService.setInstanceForTest(original);
    }

    private void install(ChppWriteService stub) {
        original = ChppWriteService.instance();
        ChppWriteService.setInstanceForTest(stub);
    }

    private static McpTool tool(String name) {
        return MatchTools.tools().stream().filter(t -> t.name().equals(name)).findFirst().orElseThrow();
    }

    @Test
    void arrangeChallenge_mapsLocationToMatchPlace() throws Exception {
        final int[] seenMatchPlace = {-1};
        install(new ChppWriteService() {
            @Override
            public void arrangeChallengeForAgent(int opp, int type, int place, Integer arena, boolean wk) {
                seenMatchPlace[0] = place;
            }
        });
        final var a = new JsonObject();
        a.addProperty("opponentTeamId", 999);
        a.addProperty("location", "away");
        tool("arrange_challenge").handler().call(a);
        assertThat(seenMatchPlace[0]).isEqualTo(1);
    }

    @Test
    void arrangeChallenge_neutralWithoutArenaIsRejected() {
        install(new ChppWriteService() {
            @Override
            public void arrangeChallengeForAgent(int opp, int type, int place, Integer arena, boolean wk) {
                throw new AssertionError("must not be called");
            }
        });
        final var a = new JsonObject();
        a.addProperty("opponentTeamId", 999);
        a.addProperty("location", "neutral");
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> tool("arrange_challenge").handler().call(a));
    }

    @Test
    void respondToChallenge_passesTheServiceErrorThrough() {
        install(new ChppWriteService() {
            @Override
            public void respondToChallengeForAgent(String verb, long id) throws ChppWriteException {
                throw new ChppWriteException(null, "Hattrick rejected the challenge accept: gone");
            }
        });
        final var a = new JsonObject();
        a.addProperty("trainingMatchId", 733551221L);
        a.addProperty("action", "accept");
        assertThatExceptionOfType(McpToolException.class)
                .isThrownBy(() -> tool("respond_to_challenge").handler().call(a))
                .withMessageContaining("gone");
    }

    @Test
    void listChallenges_shapesBothLists() throws Exception {
        install(new ChppWriteService() {
            @Override
            public XMLChallengesParser.ChallengeLists listChallengesForAgent(boolean weekend) {
                return new XMLChallengesParser.ChallengeLists(
                        List.of(new XMLChallengesParser.Challenge(1L, "2026-09-06 15:00:00", 1, 10, "A", "AA", false)),
                        List.of());
            }
        });
        final var result = tool("list_challenges").handler().call(new JsonObject());
        assertThat(result.getAsJsonArray("outgoing")).hasSize(1);
        assertThat(result.getAsJsonArray("incoming")).isEmpty();
        assertThat(result.getAsJsonArray("outgoing").get(0).getAsJsonObject()
                .get("trainingMatchId").getAsLong()).isEqualTo(1L);
    }
}
```

- [ ] **Step 6: Add the challenge tools to MatchTools**

In `src/main/java/core/mcp/MatchTools.java`, add to `tools()`:

```java
                new McpTool("list_challenges",
                        "Friendly challenges: the ones this team has sent (outgoing) and the ones "
                                + "other teams have sent it (incoming). accept / decline / withdraw "
                                + "need a trainingMatchId that only this list provides. A read - "
                                + "does not spend the write cap.",
                        McpTool.schema(McpTool.properties(
                                "weekend", McpTool.prop("boolean",
                                        "list weekend friendly challenges instead of midweek; default false"))),
                        MatchTools::listChallenges),
                new McpTool("arrange_challenge",
                        "Send a friendly challenge to another team. Leaves the machine. Poll "
                                + "list_challenges afterwards for whether the opponent agreed.",
                        McpTool.schema(McpTool.properties(
                                        "opponentTeamId", McpTool.prop("integer", "the team to challenge"),
                                        "friendlyType", McpTool.prop("integer",
                                                "0 normal rules, 1 cup rules; default 0"),
                                        "location", McpTool.prop("string",
                                                "home, away or neutral; default home"),
                                        "neutralArenaId", McpTool.prop("integer",
                                                "arena id, required when location is neutral"),
                                        "weekend", McpTool.prop("boolean",
                                                "arrange it as a weekend friendly; default false")),
                                "opponentTeamId"),
                        MatchTools::arrangeChallenge),
                new McpTool("respond_to_challenge",
                        "Accept, decline or withdraw a friendly challenge by its trainingMatchId "
                                + "(from list_challenges). Leaves the machine.",
                        McpTool.schema(McpTool.properties(
                                        "trainingMatchId", McpTool.prop("integer",
                                                "the challenge id from list_challenges"),
                                        "action", McpTool.prop("string",
                                                "accept, decline or withdraw")),
                                "trainingMatchId", "action"),
                        MatchTools::respondToChallenge)
```

Add the handlers:

```java
    private static JsonObject listChallenges(JsonObject args) throws McpToolException {
        final var weekend = McpJson.optBool(args, "weekend", false);
        final core.file.xml.XMLChallengesParser.ChallengeLists lists;
        try {
            lists = ChppWriteService.instance().listChallengesForAgent(weekend);
        } catch (ChppWriteService.ChppWriteException e) {
            throw new McpToolException(e.getMessage());
        }
        final var out = new JsonObject();
        out.add("outgoing", challengeArray(lists.outgoing()));
        out.add("incoming", challengeArray(lists.incoming()));
        return out;
    }

    private static com.google.gson.JsonArray challengeArray(
            java.util.List<core.file.xml.XMLChallengesParser.Challenge> challenges) {
        final var arr = new com.google.gson.JsonArray();
        for (final var c : challenges) {
            final var j = new JsonObject();
            j.addProperty("trainingMatchId", c.trainingMatchId());
            j.addProperty("opponentTeamId", c.opponentTeamId());
            j.addProperty("opponentName", c.opponentName());
            j.addProperty("matchTime", c.matchTime());
            j.addProperty("friendlyType", c.friendlyType());
            j.addProperty("arenaName", c.arenaName());
            j.addProperty("isAgreed", c.isAgreed());
            arr.add(j);
        }
        return arr;
    }

    private static JsonObject arrangeChallenge(JsonObject args) throws McpToolException {
        final var opponentTeamId = McpJson.reqInt(args, "opponentTeamId");
        final var friendlyType = McpJson.optInt(args, "friendlyType", 0);
        final var location = McpJson.optString(args, "location", "home");
        final int matchPlace = switch (location) {
            case "home" -> 0;
            case "away" -> 1;
            case "neutral" -> 2;
            default -> throw new IllegalArgumentException(
                    "location must be home, away or neutral, was " + location);
        };
        final Integer neutralArenaId = McpJson.optInteger(args, "neutralArenaId");
        if (matchPlace == 2 && neutralArenaId == null) {
            throw new IllegalArgumentException("location=neutral needs neutralArenaId.");
        }
        final var weekend = McpJson.optBool(args, "weekend", false);
        try {
            ChppWriteService.instance().arrangeChallengeForAgent(
                    opponentTeamId, friendlyType, matchPlace, neutralArenaId, weekend);
        } catch (ChppWriteService.ChppWriteException e) {
            throw new McpToolException(e.getMessage());
        }
        final var out = new JsonObject();
        out.addProperty("status", "ok");
        out.addProperty("note", "challenge sent; poll list_challenges for the opponent's response");
        return out;
    }

    private static JsonObject respondToChallenge(JsonObject args) throws McpToolException {
        final var trainingMatchId = McpJson.reqLong(args, "trainingMatchId");
        final var action = McpJson.reqString(args, "action");
        if (!java.util.Set.of("accept", "decline", "withdraw").contains(action)) {
            throw new IllegalArgumentException(
                    "action must be accept, decline or withdraw, was " + action);
        }
        try {
            ChppWriteService.instance().respondToChallengeForAgent(action, trainingMatchId);
        } catch (ChppWriteService.ChppWriteException e) {
            throw new McpToolException(e.getMessage());
        }
        final var out = new JsonObject();
        out.addProperty("status", "ok");
        out.addProperty("action", action);
        out.addProperty("trainingMatchId", trainingMatchId);
        return out;
    }
```

Add imports (`core.net.ChppWriteService`).

- [ ] **Step 7: Run both tool tests**

Run: `./gradlew test --tests "core.mcp.TrainingToolsTest" --tests "core.mcp.MatchToolsTest"`
Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add src/main/java/core/mcp/McpJson.java src/main/java/core/mcp/TrainingTools.java \
        src/main/java/core/mcp/MatchTools.java src/main/java/core/net/ChppWriteService.java \
        src/test/java/core/mcp/TrainingToolsTest.java src/test/java/core/mcp/MatchToolsTest.java
git commit -m "feat(mcp): set_training and challenge tools"
```

---

## Task 8: send_match_order tool

**Files:**
- Modify: `src/main/java/core/mcp/LineupTools.java`
- Test: `src/test/java/core/mcp/LineupToolsTest.java` (create if absent, else extend)

**Interfaces:**
- Consumes: `LineupTools.onEdt(EdtTask)` (private static in the same class), `McpJson.model()` → `HOModel`, `HOModel.getCurrentLineup()` → `Lineup` with `.toJson()`, `core.model.enums.MatchType.getById(Integer)` → `MatchType` or null, `ChppWriteService.instance().sendMatchOrderForAgent(int, MatchType, String)`.
- Produces: tool `send_match_order` + `private static JsonObject sendMatchOrder(JsonObject) throws McpToolException`.

- [ ] **Step 1: Write the failing test**

Create `src/test/java/core/mcp/LineupToolsTest.java` (or add these methods if the file exists):

```java
package core.mcp;

import com.google.gson.JsonObject;
import core.net.ChppWriteService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class LineupToolsTest {

    private ChppWriteService original;

    @AfterEach
    void restore() {
        if (original != null) ChppWriteService.setInstanceForTest(original);
    }

    private static McpTool sendTool() {
        return LineupTools.tools().stream()
                .filter(t -> t.name().equals("send_match_order")).findFirst().orElseThrow();
    }

    @Test
    void sendMatchOrder_rejectsAnUnknownMatchType() {
        final var a = new JsonObject();
        a.addProperty("matchId", 12345);
        a.addProperty("matchType", 999);   // not a MatchType id
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> sendTool().handler().call(a))
                .withMessageContaining("get_matches");
    }
}
```

> A full happy-path test needs a loaded `HOModel` on the EDT, which the unit harness does not provide. The unknown-matchType branch is the checkable logic; the service call is covered by `ChppWriteServiceTest`. If a later refactor makes the lineup read injectable, add the happy-path assertion then.

- [ ] **Step 2: Run to verify failure**

Run: `./gradlew test --tests "core.mcp.LineupToolsTest"`
Expected: FAIL to compile — `send_match_order` does not exist.

- [ ] **Step 3: Add the tool**

In `src/main/java/core/mcp/LineupTools.java`, add to `tools()`:

```java
                new McpTool("send_match_order",
                        "Send THE LINEUP CURRENTLY SET IN HO for a match to Hattrick as the match "
                                + "order. This leaves the machine and OVERWRITES whatever order "
                                + "Hattrick currently holds for that match. Build the lineup with "
                                + "apply_lineup and review it with get_lineup first. League matches "
                                + "are matchType 1; for cup, friendly or tournament take the "
                                + "matchType id from get_matches.",
                        McpTool.schema(McpTool.properties(
                                        "matchId", McpTool.prop("integer", "the Hattrick match id"),
                                        "matchType", McpTool.prop("integer",
                                                "match type id (league = 1; from get_matches for others)")),
                                "matchId", "matchType"),
                        LineupTools::sendMatchOrder)
```

Add the handler:

```java
    private static JsonObject sendMatchOrder(JsonObject args) throws McpToolException {
        final var matchId = McpJson.reqInt(args, "matchId");
        final var matchTypeId = McpJson.reqInt(args, "matchType");
        final var matchType = core.model.enums.MatchType.getById(matchTypeId);
        if (matchType == null) {
            throw new IllegalArgumentException("Unknown matchType " + matchTypeId
                    + "; take the matchType id from get_matches (league matches are 1).");
        }
        final var model = McpJson.model();
        // apply_lineup mutates the live Lineup on the EDT, so serialise it there too.
        final var orderString = onEdt(() -> {
            final var wrapper = new JsonObject();
            wrapper.addProperty("order", model.getCurrentLineup().toJson());
            return wrapper;
        }).get("order").getAsString();

        final String response;
        try {
            response = ChppWriteService.instance()
                    .sendMatchOrderForAgent(matchId, matchType, orderString);
        } catch (ChppWriteService.ChppWriteException e) {
            throw new McpToolException(e.getMessage());
        }
        final var out = new JsonObject();
        out.addProperty("status", "ok");
        out.addProperty("matchId", matchId);
        out.addProperty("response", response);
        return out;
    }
```

Add imports (`core.net.ChppWriteService`).

- [ ] **Step 4: Run to verify passing**

Run: `./gradlew test --tests "core.mcp.LineupToolsTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/core/mcp/LineupTools.java src/test/java/core/mcp/LineupToolsTest.java
git commit -m "feat(mcp): send_match_order tool"
```

---

## Task 9: place_bid two-step tool

**Files:**
- Modify: `src/main/java/core/db/TransferListingTable.java`
- Modify: `src/main/java/core/db/DBManager.java`
- Modify: `src/main/java/core/mcp/TransferTools.java`
- Test: `src/test/java/core/mcp/TransferToolsTest.java` (create)

**Interfaces:**
- Consumes: `AbstractTable.createSelectStatement(String)` / `load(Class, ResultSet)` pattern (see `TransferListingTable.loadUnresolved`), `TransferListing` fields (`playerId`, `playerName`, `age`, `askingPrice`, `highestBid`, `deadline`), `ChppWriteService.instance().placeBidForAgent(int, long, Long)` → `BidResult`, `McpJson.reqInt/reqLong/optLong/optString`, `McpToolException`.
- Produces:
  - `TransferListingTable.loadLatestForPlayer(int playerId) → TransferListing` (null when none)
  - `DBManager.loadLatestTransferListing(int playerId) → TransferListing`
  - `TransferTools` tool `place_bid` + handler + a static `Map<String, PendingBid>` (`PendingBid` = `record PendingBid(int playerId, long bidAmount, Long maxBidAmount, long createdAt)`), 120 s TTL, single-use, swept each call.

- [ ] **Step 1: Write the failing TransferToolsTest**

Create `src/test/java/core/mcp/TransferToolsTest.java`:

```java
package core.mcp;

import com.google.gson.JsonObject;
import core.net.ChppWriteService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class TransferToolsTest {

    private ChppWriteService original;

    @AfterEach
    void restore() {
        if (original != null) ChppWriteService.setInstanceForTest(original);
        TransferTools.clearPendingBidsForTest();
    }

    private static McpTool bidTool() {
        return TransferTools.tools().stream()
                .filter(t -> t.name().equals("place_bid")).findFirst().orElseThrow();
    }

    private int placeCalls;
    private long placedAmount;
    private Long placedMax;

    private void installRecordingService() {
        original = ChppWriteService.instance();
        ChppWriteService.setInstanceForTest(new ChppWriteService() {
            @Override
            public BidResult placeBidForAgent(int playerId, long bidAmount, Long maxBidAmount) {
                placeCalls++;
                placedAmount = bidAmount;
                placedMax = maxBidAmount;
                return new BidResult("1750000", "2026-09-05 20:00:00", "HO FC");
            }
        });
    }

    private static JsonObject step1(int playerId, long amount) {
        final var a = new JsonObject();
        a.addProperty("playerId", playerId);
        a.addProperty("bidAmount", amount);
        return a;
    }

    @Test
    void step1_issuesATokenAndPlacesNothing() throws Exception {
        installRecordingService();
        final var result = bidTool().handler().call(step1(111, 1_500_000L));
        assertThat(result.get("status").getAsString()).isEqualTo("confirm_required");
        assertThat(result.has("confirmToken")).isTrue();
        assertThat(placeCalls).isZero();
    }

    @Test
    void step2_placesExactlyThePreviewedAmounts() throws Exception {
        installRecordingService();
        final var token = bidTool().handler().call(step1(111, 1_500_000L))
                .get("confirmToken").getAsString();

        final var a = new JsonObject();
        a.addProperty("playerId", 111);
        a.addProperty("bidAmount", 9_999_999L);   // must be ignored in favour of the pending entry
        a.addProperty("confirmToken", token);
        final var result = bidTool().handler().call(a);

        assertThat(result.get("status").getAsString()).isEqualTo("ok");
        assertThat(placeCalls).isEqualTo(1);
        assertThat(placedAmount).isEqualTo(1_500_000L);
    }

    @Test
    void step2_tokenIsSingleUse() throws Exception {
        installRecordingService();
        final var token = bidTool().handler().call(step1(111, 1_500_000L))
                .get("confirmToken").getAsString();
        final var a = new JsonObject();
        a.addProperty("playerId", 111);
        a.addProperty("confirmToken", token);
        bidTool().handler().call(a);
        assertThatExceptionOfType(McpToolException.class)
                .isThrownBy(() -> bidTool().handler().call(a))
                .withMessageContaining("step-1");
    }

    @Test
    void step2_rejectsAWrongPlayerId() throws Exception {
        installRecordingService();
        final var token = bidTool().handler().call(step1(111, 1_500_000L))
                .get("confirmToken").getAsString();
        final var a = new JsonObject();
        a.addProperty("playerId", 222);
        a.addProperty("confirmToken", token);
        assertThatExceptionOfType(McpToolException.class)
                .isThrownBy(() -> bidTool().handler().call(a));
    }

    @Test
    void step2_rejectsAnExpiredToken() throws Exception {
        installRecordingService();
        final var token = bidTool().handler().call(step1(111, 1_500_000L))
                .get("confirmToken").getAsString();
        TransferTools.expirePendingBidsForTest();
        final var a = new JsonObject();
        a.addProperty("playerId", 111);
        a.addProperty("confirmToken", token);
        assertThatExceptionOfType(McpToolException.class)
                .isThrownBy(() -> bidTool().handler().call(a));
    }
}
```

This drives three test-only seams on `TransferTools`: `clearPendingBidsForTest()`, `expirePendingBidsForTest()` (rewrites every pending entry's `createdAt` to the epoch), and — implicitly — that step 1 works without a `TransferListing` present (it should still issue a token; the player preview is best-effort).

- [ ] **Step 2: Run to verify failure**

Run: `./gradlew test --tests "core.mcp.TransferToolsTest"`
Expected: FAIL to compile.

- [ ] **Step 3: Add the DB read method**

In `src/main/java/core/db/TransferListingTable.java`, next to `loadUnresolved`:

```java
    /** The most recent snapshot of this player's market listing, or null if none was ever seen. */
    public TransferListing loadLatestForPlayer(int playerId) {
        final var sql = createSelectStatement(
                "WHERE PLAYERID = " + playerId + " ORDER BY OBSERVEDAT DESC LIMIT 1");
        final var rows = load(TransferListing.class, connectionManager.executeQuery(sql));
        return rows.isEmpty() ? null : rows.get(0);
    }
```

> Confirm the id column is `PLAYERID` (check the `ColumnDescriptor[]` in `TransferListingTable.initColumns()`). Use the actual column name.

In `src/main/java/core/db/DBManager.java`, next to `storeTransferListing` (line ~1517):

```java
    public TransferListing loadLatestTransferListing(int playerId) {
        return ((TransferListingTable) getTable(TransferListingTable.TABLENAME)).loadLatestForPlayer(playerId);
    }
```

- [ ] **Step 4: Add place_bid to TransferTools**

In `src/main/java/core/mcp/TransferTools.java`, add fields (imports: `java.util.Map`, `java.util.concurrent.ConcurrentHashMap`, `java.util.UUID`, `core.net.ChppWriteService`, `core.db.DBManager`, `core.db.TransferListing`):

```java
    private record PendingBid(int playerId, long bidAmount, Long maxBidAmount, long createdAt) {
    }

    private static final Map<String, PendingBid> PENDING_BIDS = new ConcurrentHashMap<>();
    private static final long BID_TTL_MS = 120_000;

    static void clearPendingBidsForTest() {
        PENDING_BIDS.clear();
    }

    static void expirePendingBidsForTest() {
        PENDING_BIDS.replaceAll((k, v) ->
                new PendingBid(v.playerId(), v.bidAmount(), v.maxBidAmount(), 0L));
    }

    private static void sweepExpiredBids() {
        final var now = System.currentTimeMillis();
        PENDING_BIDS.values().removeIf(b -> now - b.createdAt() > BID_TTL_MS);
    }
```

Add to `tools()`:

```java
                new McpTool("place_bid",
                        "Place a transfer bid. TWO STEPS so a stray call cannot spend money: call "
                                + "once with playerId + bidAmount to get a preview and a "
                                + "confirmToken, then call again with the same playerId and that "
                                + "confirmToken to actually place the bid. The second call leaves "
                                + "the machine and commits money in Hattrick. bidAmount is in the "
                                + "user's currency. maxBidAmount is an optional autobid ceiling.",
                        McpTool.schema(McpTool.properties(
                                        "playerId", McpTool.prop("integer", "the player to bid on"),
                                        "bidAmount", McpTool.prop("integer",
                                                "the bid, in the user's currency (required in step 1)"),
                                        "maxBidAmount", McpTool.prop("integer",
                                                "optional autobid ceiling"),
                                        "confirmToken", McpTool.prop("string",
                                                "the token from step 1 (required in step 2, omit in step 1)")),
                                "playerId"),
                        TransferTools::placeBid)
```

Add the handler:

```java
    private static JsonObject placeBid(JsonObject args) throws McpToolException {
        sweepExpiredBids();
        final var playerId = McpJson.reqInt(args, "playerId");
        final var confirmToken = McpJson.optString(args, "confirmToken", null);

        if (confirmToken == null) {
            return previewBid(args, playerId);
        }
        return commitBid(playerId, confirmToken);
    }

    private static JsonObject previewBid(JsonObject args, int playerId) throws McpToolException {
        final var bidAmount = McpJson.reqLong(args, "bidAmount");
        final Long maxBidAmount = McpJson.optLong(args, "maxBidAmount");

        final var token = UUID.randomUUID().toString();
        PENDING_BIDS.put(token,
                new PendingBid(playerId, bidAmount, maxBidAmount, System.currentTimeMillis()));

        final var out = new JsonObject();
        out.addProperty("status", "confirm_required");

        final var listing = DBManager.instance().loadLatestTransferListing(playerId);
        final var player = new JsonObject();
        player.addProperty("id", playerId);
        if (listing != null) {
            player.addProperty("name", listing.playerName);
            player.addProperty("ageYears", listing.age);
            player.addProperty("askingPrice", listing.askingPrice);
            player.addProperty("highestBid", listing.highestBid);
            player.addProperty("deadline", listing.deadline == null ? null : listing.deadline.toHT());
        } else {
            player.addProperty("note", "No market snapshot for this player - run search_transfers "
                    + "for him first if you want a preview.");
        }
        out.add("player", player);

        final var bid = new JsonObject();
        bid.addProperty("bidAmount", bidAmount);
        if (maxBidAmount != null) {
            bid.addProperty("maxBidAmount", maxBidAmount);
        }
        out.add("bid", bid);

        out.addProperty("confirmToken", token);
        out.addProperty("expiresInSeconds", 120);
        out.addProperty("note", "Call place_bid again with this confirmToken and the same playerId "
                + "to place the bid.");
        return out;
    }

    private static JsonObject commitBid(int playerId, String confirmToken) throws McpToolException {
        final var pending = PENDING_BIDS.remove(confirmToken);
        if (pending == null
                || System.currentTimeMillis() - pending.createdAt() > BID_TTL_MS
                || pending.playerId() != playerId) {
            throw new McpToolException("That confirmToken is invalid, expired, or for a different "
                    + "player - start again with a step-1 place_bid call.");
        }
        final ChppWriteService.BidResult result;
        try {
            result = ChppWriteService.instance()
                    .placeBidForAgent(pending.playerId(), pending.bidAmount(), pending.maxBidAmount());
        } catch (ChppWriteService.ChppWriteException e) {
            throw new McpToolException(e.getMessage());
        }
        final var out = new JsonObject();
        out.addProperty("status", "ok");
        final var player = new JsonObject();
        player.addProperty("id", pending.playerId());
        out.add("player", player);
        out.addProperty("highestBid", result.highestBid());
        out.addProperty("deadline", result.deadline());
        out.addProperty("bidderTeamName", result.bidderTeamName());
        out.addProperty("note", "Bid placed. Poll get_player or search_transfers for the updated "
                + "market state.");
        return out;
    }
```

> Check `TransferListing`'s fields are package-visible (`core.db`) — `TransferTools` is in `core.mcp`, so they may need getters. `TransferListing.fromHit` sets them directly within `core.db`; if the fields are not `public`, add simple getters to `TransferListing` or read via a small mapper in `core.db`. Adjust in this step.

- [ ] **Step 5: Run to verify passing**

Run: `./gradlew test --tests "core.mcp.TransferToolsTest"`
Expected: PASS (5 tests).

- [ ] **Step 6: Commit**

```bash
git add src/main/java/core/db/TransferListingTable.java src/main/java/core/db/DBManager.java \
        src/main/java/core/mcp/TransferTools.java src/test/java/core/mcp/TransferToolsTest.java
git commit -m "feat(mcp): place_bid two-step confirm tool"
```

---

## Task 10: Full suite, docs, and manual smoke check

**Files:**
- Modify: `docs/current.md`

- [ ] **Step 1: Run the whole test suite**

Run: `./gradlew test`
Expected: BUILD SUCCESSFUL, no regressions. If a pre-existing unrelated test is flaky, note it and confirm it fails the same way on `master`.

- [ ] **Step 2: Update docs/current.md**

Add a short section under the current-work notes describing what shipped:

```markdown
### CHPP write operations over MCP (2026-09-03)

Shipped: `set_training`, `send_match_order`, `list_challenges` / `arrange_challenge` /
`respond_to_challenge`, and a two-step `place_bid`. All route through `core.net.ChppWriteService`
(same shape as `TransferSearchService`: `fetch*` seam, per-session cap
`UserParameter.mcpChppWritesPerSession`, typed `ChppWriteException`). URL building + raw calls in
`Connector`; `XMLChallengesParser` is new. Non-bid writes fire directly; `place_bid` needs a
confirm token (static map on `TransferTools`, 120 s TTL, single-use).

OAuth scopes `set_training,manage_challenges,place_bid` added to the hard-coded `OAuthDialog`
string — a token authorized before this ships does NOT carry them and there is no recovery path
(by design); re-authorize to pick them up.

Verified against the code but NOT against live CHPP: the `setTraining` echo nesting, the
`challenges` `view` element casing (`MatchTime` / `IsAgreed`), and the `<Player>` field name for
the post-bid highest bidder. Capture real responses and adjust the parsers/fixtures if they differ.
```

- [ ] **Step 3: Manual smoke check (optional, needs a real HO install + re-authorized token)**

Build a distribution, re-authorize so the token carries the new scopes, start the MCP server, and from an MCP client:
1. `get_training` → note the values.
2. `set_training` with the same values → expect `status:"ok"` and `committed` echoing them.
3. `list_challenges` → expect `outgoing` / `incoming` arrays (likely empty).
4. `place_bid` step 1 for a player on the market → expect `confirm_required` + token; step 2 → `status:"ok"`.

Do **not** smoke-test `send_match_order` or a real `place_bid` commit against a live team unless you mean it — both change real state.

- [ ] **Step 4: Commit**

```bash
git add docs/current.md
git commit -m "docs: record CHPP-write-over-MCP work in current.md"
```

---

## Self-review

**Spec coverage:**

| Spec section | Task |
|---|---|
| §Goals — four/five write families over MCP | 3, 5, 6, 7, 8, 9 |
| §Goals — route through `ChppWriteService` shaped like `TransferSearchService` | 3, 5, 6 |
| §Goals — bid behind two-step confirm | 9 |
| §1.2 / §5 — OAuth scope one-liner | 1 |
| §1.4 — every `fetch` runs silent; EDT reads via `onEdt` | 3, 5, 6 (silent), 8 (`onEdt`) |
| §1.5 — no local training refresh; tool says so | 7 (`note`) |
| §1.6 — CHPP request formats | 2 (URL builders), Global Constraints table |
| §2 / §3 — layering, service + `fetch` seam + cap + `ChppWriteException` | 3 |
| §3 — Connector URL builders as the wire-format seam | 2 |
| §3 — `XMLChallengesParser` new, `XMLTrainingParser` reused | 4 (new), 3 (reuse) |
| §3.1 training / §3.2 match order / §3.3 challenges / §3.4 bid | 3 / 6+8 / 5+7 / 6+9 |
| §4.1 `set_training` | 7 |
| §4.2 `send_match_order` | 8 |
| §4.3 `list_challenges` / `arrange_challenge` / `respond_to_challenge` | 7 |
| §4.4 `place_bid` two-step | 9 |
| §4 — `McpJson.reqLong` / `optLong` | 7 |
| §5 — `mcpChppWritesPerSession` (declare/serialize/parse) + `McpServerPanel` | 1 |
| §5 — `docs/chpp_files.md` | 1 |
| §6 — error handling (cap, neutral-without-arena, out-of-range, bad token, null MatchType) | 3, 5, 7, 8, 9 |
| §7 — every test template | 2, 3, 4, 5, 6, 7, 8, 9 |
| §8 — files touched | File structure table |

Not covered by design (spec non-goals): bid-tracking maintenance, standalone challengeability pre-check, youth training / NT orders, confirmation gate on non-bid writes, stale-token handling, new DB tables. The one addition beyond §8 — `DBManager.loadLatestTransferListing` + `TransferListingTable.loadLatestForPlayer` — is a read for the step-1 preview, justified in Deviation 3; it adds no table and no migration.

**Placeholder scan:** No "TBD"/"implement later"/"add appropriate…" steps. Every code step has real code. Three points carry an explicit *verify against a live response* caveat (training echo nesting, challenges casing, post-bid `<Player>` field name) — these are flagged as such in the spec itself (§1.6) and the tests are written to pass with either resolution; they are noted, not deferred.

**Type consistency:** `ChppWriteService.ChppWriteException` (nested, `errorKey()`), `TrainingCommitted(int trainingType, int intensity, int staminaShare)`, `BidResult(String highestBid, String deadline, String bidderTeamName)`, `XMLChallengesParser.Challenge(long trainingMatchId, String matchTime, int friendlyType, int opponentTeamId, String opponentName, String arenaName, boolean isAgreed)` and `ChallengeLists(List<Challenge> outgoing, List<Challenge> incoming)` — all used consistently across Tasks 3–9. `sendMatchOrderForAgent(int, MatchType, String)` (3 args, Deviation 2) is consistent between Task 6 (definition) and Task 8 (call). `ChppWriteService.setInstanceForTest` introduced in Task 7 and used in Tasks 7–9. `Connector` builders are `static` (Deviation 1) — consistent between Task 2 and the Task 2 test.

---

## Execution Handoff

Plan complete and saved to `docs/superpowers/plans/2026-09-03-chpp-write-operations-over-mcp.md`. Two execution options:

**1. Subagent-Driven (recommended)** — I dispatch a fresh subagent per task, review between tasks, fast iteration.

**2. Inline Execution** — Execute tasks in this session using executing-plans, batch execution with checkpoints.

Which approach?
