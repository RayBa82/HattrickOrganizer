# MCP Server for HO — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Expose HO's live team data, transfer search and lineup engine to any MCP client over a
localhost HTTP endpoint served from inside the running HO JVM.

**Architecture:** A `com.sun.net.httpserver.HttpServer` bound to `127.0.0.1`, speaking MCP
Streamable HTTP (single `POST /mcp`, `application/json` responses, no SSE), dispatching JSON-RPC
`tools/call` into a flat map of handlers that reach HO's singletons directly. The network+parse core
of `OnlineWorker.searchTransfers` is extracted into a headless `TransferSearchService` that both the
existing Swing tab and the MCP path call. Every transfer-search result is snapshotted into a new
`TRANSFERLISTING` table to build a price corpus.

**Tech Stack:** Java 17, `com.sun.net.httpserver` (JDK stdlib), gson 2.12.1 (already a compile
dependency), HSQLDB via `core.db.AbstractTable`, Swing for the options panel, JUnit 5 + AssertJ.
**No new dependencies.**

**Spec:** `docs/superpowers/specs/2026-08-30-mcp-server-design.md` — read it alongside this plan.

## Global Constraints

- **Java 17 source/target.** Do not raise the language level. Kotlin/Groovy coexist in
  `src/main/java` but all new code here is Java.
- **No new Gradle dependencies.** `com.sun.net.httpserver` and gson only.
- **Every build is a full clean rebuild** (`compileJava.dependsOn(clean)`); expect ~10s minimum.
  `./gradlew test` prints a POEditor `Invalid API Token` line locally — that is expected, not a failure.
- **Bind `127.0.0.1` explicitly, never `0.0.0.0`.** Reject any request carrying an `Origin` header
  with HTTP 403. These two guards are the entire security perimeter and must not be simplified away.
- **Off by default.** Nothing binds a port unless the user enabled it in the options panel.
- **No write ever leaves the machine.** No `uploadMatchOrder`, no bids. Reads of CHPP are limited to
  `transfersearch` (gated, §Task 2) and `transfersPlayer` (manual button only, Task 9).
- **No scheduler, no timer, no background loop** may call `TransferSearchService`.
- **All UI strings go through `TranslationFacility.tr(key)`.** New `ls.*` keys go into
  `src/main/resources/language/English.properties` **only** — POEditor owns the other language files
  and `poeditorPull` overwrites them on every build.
- **Logging goes through `HOLogger.instance()`**, never `System.out`. No stack trace crosses the wire.
- **Legacy German domain names stay** (`HOVerwaltung`, `Lineup.setSpielerAtPosition`, …). Do not rename.
- **Schema changes require a migration** (Task 3). `DBManager.DBVersion` 1000 → 1001 **and**
  `DBManager.previousDBVersion` 901 → 1000.
- Commit after every task with the message given in that task's final step.
- **Confirm each accessor against the real class before pasting a tool body.** The code below was
  written against HO's API as it stands, but the accessor surface is wide and legacy-named.
  Verified by inspection already: `HOModel.getCurrentLineup` / `getCurrentLineupTeam` /
  `storeLineup` / `getRatingPredictionModel` / `getCurrentPlayers` / `getCurrentPlayer`,
  `Lineup.getPositionById` / `setSpielerAtPosition` / `optimizeLineup` / `getAllPositions` /
  `getCurrentTeamFormationString` / `getCaptain` / `getKicker`, `RatingPredictionModel(Team)` and
  `RatingSector`, `HODateTime.fromHT` / `now` / `toHT` / `toDbTimestamp`,
  `ColumnDescriptor.Builder.setGetter(UnaryOperator<Object>)` /
  `setSetter(BiConsumer<Object, Object>)`, `ConnectionManager.connect(Connection)`,
  `UserParameter.instance()` / `temp()` / `getBooleanValue` / `getIntValue`, `core.HO.VERSION`
  (a `double`), `module.transfer.XMLParser.getAllPlayerTransfers(int)`.
  **Not** yet confirmed — check these when the task reaches them:
  `Player.getSpecialtyName` / `getWage` / `getTotalCards` / `getTransferListed` / `isHomeGrown` /
  `setSpecialty`, `Economy.getExpectedWeeksTotal` / `getCostsPlayers`, `StaffMember.getStaffType`,
  `Liga.getPlatzierung`, `HOModel.getFixtures().getMatches()`, that `RatingSector.values()` really
  has seven entries, and `Lineup.optimizeLineup`'s exact boolean arity.

## Decisions taken on the spec's open items (§12)

1. **Private rating models — reflection route.** Each tool call that needs ratings builds its **own**
   `RatingPredictionModel` via `sharedModel.getClass().getConstructor(Team.class).newInstance(team)`.
   This keeps MCP ratings identical to the Lineup tab (it reuses the user's Groovy model class if one
   is loaded), needs no change to `RatingPredictionManager`, and sidesteps the
   `LineupRatingCache` revision collision entirely rather than patching it. Because the private model
   is never shared, `get_squad`'s `positionRatings` no longer need to marshal through the EDT either —
   only reads of the **live `Lineup` object** do.
2. **The market unlock is a duration**, `mcpMarketUnlockMinutes` (default 30), expiry held in memory
   only. A duration cannot be left forgotten in the on position.
3. **`previousDBVersion` stays hand-bumped.** Out of scope.
4. **`pageSize` defaults to 50, capped at 100. No paging exposed.** As specified.

**Deviation from §7 worth knowing:** the *session cap* applies to both callers (it bounds total CHPP
traffic); the *market unlock gate* applies to the MCP path only. The transfer-search tab's button
click already **is** the human action the unlock exists to require, so gating the tab on it would be
redundant and would break the tab.

## File Structure

New:

| File | Responsibility |
|---|---|
| `src/main/java/core/net/TransferSearchService.java` | Headless CHPP transfer search + the §4 guards (unlock window, session cap). No Swing. |
| `src/main/java/core/db/TransferListing.java` | One observed market listing — the `Storable` row of `TRANSFERLISTING`. |
| `src/main/java/core/db/TransferListingTable.java` | Table definition, snapshot write, unresolved-listing query, sale-price update. |
| `src/main/java/core/mcp/McpTool.java` | Tool record (name, description, schema, handler) + schema helpers. |
| `src/main/java/core/mcp/McpToolException.java` | An expected tool failure, rendered as `isError: true` rather than a protocol error. |
| `src/main/java/core/mcp/McpHttpServer.java` | Transport, `Origin` guard, JSON-RPC framing, dispatch table, lifecycle. |
| `src/main/java/core/mcp/McpJson.java` | Shared serialization (player, hit, ratings), argument readers, private-rating-model factory. |
| `src/main/java/core/mcp/TeamTools.java` | `get_team`, `get_squad`, `get_player`, `get_upcoming_matches`. |
| `src/main/java/core/mcp/LineupTools.java` | `get_lineup`, `evaluate_lineup`, `optimize_lineup`, `apply_lineup`. |
| `src/main/java/core/mcp/TransferTools.java` | `search_transfers`, `find_comparables`, corpus write. |
| `src/main/java/core/option/McpServerPanel.java` | Enable/port/cap fields, unlock button, resolve-listings button. |

Modified: `core/net/OnlineWorker.java`, `core/db/DBManager.java`, `core/db/DBUpdater.java`,
`core/model/UserParameter.java`, `core/option/OptionenDialog.java`, `core/HO.java`,
`core/gui/HOMainFrame.java`, `src/main/resources/language/English.properties`.

---

### Task 1: Headless transfer search service

Extract the network + parse core of `OnlineWorker.searchTransfers` so it can be called from an HTTP
handler thread. Today it calls `HOMainFrame.instance().setWaitInformation()` and pops a
`JOptionPane` — both illegal off the EDT (spec §1.3).

**Files:**
- Create: `src/main/java/core/net/TransferSearchService.java`
- Modify: `src/main/java/core/net/OnlineWorker.java:707-732`
- Test: `src/test/java/core/net/TransferSearchServiceTest.java`

**Interfaces:**
- Consumes: `core.net.TransferSearchQuery`, `core.net.Connector#getTransferSearch(TransferSearchQuery)`,
  `core.file.xml.XMLTransferSearchParser#parse(String)`.
- Produces:
  - `TransferSearchService.instance()` → singleton
  - `Page search(TransferSearchQuery) throws SearchFailed` — human-initiated path (the Swing tab)
  - `protected String fetch(TransferSearchQuery)` — the CHPP call, overridden in tests
  - `class SearchFailed extends Exception` — carries a translation key in `errorKey()` (nullable) and
    a readable message

- [ ] **Step 1: Write the failing test**

Create `src/test/java/core/net/TransferSearchServiceTest.java`:

```java
package core.net;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class TransferSearchServiceTest {

    private static final String ONE_RESULT = """
            <?xml version="1.0" encoding="utf-8"?>
            <HattrickData>
              <FileName>transfersearch.xml</FileName>
              <Version>1.1</Version>
              <TransferSearch>
                <ItemCount>1</ItemCount>
                <PageSize>50</PageSize>
                <PageIndex>0</PageIndex>
                <TransferResults>
                  <TransferResult>
                    <PlayerId>111222333</PlayerId>
                    <FirstName>Sven</FirstName>
                    <NickName></NickName>
                    <LastName>Persson</LastName>
                    <NativeCountryID>3</NativeCountryID>
                    <AskingPrice>1500000</AskingPrice>
                    <Deadline>2026-09-02 20:15:00</Deadline>
                    <HighestBid>1650000</HighestBid>
                    <Details>
                      <Age>24</Age>
                      <AgeDays>77</AgeDays>
                      <Salary>44000</Salary>
                      <TSI>29000</TSI>
                      <PlayerForm>6</PlayerForm>
                      <Experience>4</Experience>
                      <Leadership>3</Leadership>
                      <Specialty>2</Specialty>
                      <Cards>1</Cards>
                      <InjuryLevel>-1</InjuryLevel>
                      <StaminaSkill>8</StaminaSkill>
                      <KeeperSkill>1</KeeperSkill>
                      <PlaymakerSkill>12</PlaymakerSkill>
                      <ScorerSkill>5</ScorerSkill>
                      <PassingSkill>9</PassingSkill>
                      <WingerSkill>7</WingerSkill>
                      <DefenderSkill>6</DefenderSkill>
                      <SetPiecesSkill>10</SetPiecesSkill>
                    </Details>
                  </TransferResult>
                </TransferResults>
              </TransferSearch>
            </HattrickData>
            """;

    /** A service that answers from a canned file instead of calling Hattrick. */
    static class StubService extends TransferSearchService {
        String lastQueryString;

        @Override
        protected String fetch(TransferSearchQuery query) {
            lastQueryString = query.toQueryString();
            return ONE_RESULT;
        }
    }

    private static TransferSearchQuery.TransferSearchQueryBuilder validQuery() {
        return TransferSearchQuery.builder()
                .ageMin(20).ageMax(22)
                .skillType1(TransferSearchQuery.Skill.PLAYMAKING)
                .minSkillValue1(7).maxSkillValue1(9);
    }

    @Test
    void search_parsesTheAnswer() throws Exception {
        final var service = new StubService();

        final var page = service.search(validQuery().build());

        assertThat(page.hits()).hasSize(1);
        assertThat(page.hits().getFirst().playerId()).isEqualTo(111222333);
        assertThat(service.lastQueryString).contains("ageMin=20");
    }

    /** The regression guard for spec section 1.3: no Swing call, no dialog, on the failure path. */
    @Test
    void search_rejectsAnInvalidQueryWithoutTouchingSwing() {
        final var service = new StubService();
        final var invalid = validQuery().maxSkillValue1(20).build();   // span of 13 levels

        assertThatExceptionOfType(TransferSearchService.SearchFailed.class)
                .isThrownBy(() -> service.search(invalid))
                .satisfies(e -> assertThat(e.errorKey()).isEqualTo("ls.transfersearch.error.skillrange"));

        assertThat(service.lastQueryString).isNull();   // never went out
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew test --tests "core.net.TransferSearchServiceTest"`
Expected: FAIL — compilation error, `TransferSearchService` does not exist.

- [ ] **Step 3: Write the service**

Create `src/main/java/core/net/TransferSearchService.java`:

```java
package core.net;

import core.file.xml.XMLTransferSearchParser;
import core.util.HOLogger;

/**
 * Headless transfer market search.
 * <p>
 * The network and parse core of {@link OnlineWorker#searchTransfers(TransferSearchQuery)}, without
 * any Swing call: {@code setWaitInformation} and the error dialog stay in OnlineWorker, so this can
 * be called from a plain worker thread.
 * <p>
 * CHPP does not allow the transfer market to be scanned automatically. This class exposes no entry
 * point reachable from a timer, a {@link Thread} or a scheduled task, and nothing in HO may call it
 * from one.
 */
public class TransferSearchService {

    /** A search that did not produce a result page, with a readable reason for the caller to show. */
    public static class SearchFailed extends Exception {
        private final String errorKey;

        SearchFailed(String errorKey, String message) {
            super(message);
            this.errorKey = errorKey;
        }

        SearchFailed(String message, Throwable cause) {
            super(message, cause);
            this.errorKey = null;
        }

        /** The translation key of the message, or null if there is no translated form. */
        public String errorKey() {
            return errorKey;
        }
    }

    private static TransferSearchService instance;

    public static synchronized TransferSearchService instance() {
        if (instance == null) {
            instance = new TransferSearchService();
        }
        return instance;
    }

    /** Package private: production code uses {@link #instance()}, tests subclass. */
    TransferSearchService() {
    }

    /**
     * Search the transfer market on behalf of the user, who asked for it in HO's own UI.
     *
     * @param query the search parameters
     * @return one page of results
     * @throws SearchFailed if the query is invalid or the download failed
     */
    public XMLTransferSearchParser.Page search(TransferSearchQuery query) throws SearchFailed {
        final var validationError = query.validationErrorKey();
        if (validationError != null) {
            throw new SearchFailed(validationError, "Invalid transfer search: " + validationError);
        }
        try {
            return XMLTransferSearchParser.parse(fetch(query));
        } catch (Exception e) {
            HOLogger.instance().log(TransferSearchService.class, e);
            throw new SearchFailed("Error searching transfers: " + e.getMessage(), e);
        }
    }

    /** The CHPP call. Overridden in tests so that no network access is needed. */
    protected String fetch(TransferSearchQuery query) {
        return Connector.instance().getTransferSearch(query);
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew test --tests "core.net.TransferSearchServiceTest"`
Expected: PASS (2 tests).

- [ ] **Step 5: Reimplement `OnlineWorker.searchTransfers` on the service**

In `src/main/java/core/net/OnlineWorker.java`, replace the body of `searchTransfers` (keep the
javadoc above it unchanged) with:

```java
	public static XMLTransferSearchParser.Page searchTransfers(TransferSearchQuery query) {
		try {
			HOMainFrame.instance().setWaitInformation();
			return TransferSearchService.instance().search(query);
		} catch (TransferSearchService.SearchFailed e) {
			String msg = e.errorKey() != null
					? getLangString(e.errorKey())
					: getLangString("Downloadfehler") + " : " + e.getMessage();
			setInfoMsg(msg, InfoPanel.FEHLERFARBE);
			Helper.showMessage(HOMainFrame.instance(), msg, getLangString("Fehler"),
					JOptionPane.ERROR_MESSAGE);
		} finally {
			HOMainFrame.instance().setWaitInformation();
		}
		return null;
	}
```

The signature, the `null` return on failure and the dialog are unchanged, so
`module.transfer.search.TransferSearchPanel:225` keeps working untouched.

- [ ] **Step 6: Build and run the full suite**

Run: `./gradlew test`
Expected: BUILD SUCCESSFUL, no new failures.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/core/net/TransferSearchService.java \
        src/main/java/core/net/OnlineWorker.java \
        src/test/java/core/net/TransferSearchServiceTest.java
git commit -m "Extract headless TransferSearchService out of OnlineWorker"
```

---

### Task 2: Transfer-search guards and the user parameters behind them

The gate that makes the CHPP restriction (spec §1.2, §4) enforceable rather than aspirational: an
agent-facing entry point that refuses unless the user has unlocked market search in HO's own UI
within the last `mcpMarketUnlockMinutes`, plus a per-session cap shared by both callers.

**Files:**
- Modify: `src/main/java/core/net/TransferSearchService.java`
- Modify: `src/main/java/core/model/UserParameter.java` (three sections: field declarations,
  `getValues()`, `setValues()`)
- Test: `src/test/java/core/net/TransferSearchServiceTest.java`

**Interfaces:**
- Consumes: `TransferSearchService#search`, `UserParameter.instance()`
- Produces:
  - `UserParameter.mcpServerEnabled` (boolean, default `false`)
  - `UserParameter.mcpServerPort` (int, default `8765`)
  - `UserParameter.mcpSearchesPerSession` (int, default `20`)
  - `UserParameter.mcpMarketUnlockMinutes` (int, default `30`)
  - `TransferSearchService#searchForAgent(TransferSearchQuery)` — the only entry point MCP may use
  - `TransferSearchService#unlockMarketSearch()` — called by the options panel button
  - `TransferSearchService#marketUnlockSecondsRemaining()` → `long`, 0 when locked

- [ ] **Step 1: Add the user parameters**

In `src/main/java/core/model/UserParameter.java`, after the `public String ReleaseChannel = null;`
field (around line 111), add:

```java
    //------MCP server (see docs/superpowers/specs/2026-08-30-mcp-server-design.md)---
    public boolean mcpServerEnabled = false;
    public int mcpServerPort = 8765;
    /** How many transfer searches one HO session may issue in total. */
    public int mcpSearchesPerSession = 20;
    /** How long the "unlock market search" button keeps market search open, in minutes. */
    public int mcpMarketUnlockMinutes = 30;
```

In `getValues()`, next to the other `map.put` calls, add:

```java
        map.put("mcpServerEnabled", String.valueOf(mcpServerEnabled));
        map.put("mcpServerPort", String.valueOf(mcpServerPort));
        map.put("mcpSearchesPerSession", String.valueOf(mcpSearchesPerSession));
        map.put("mcpMarketUnlockMinutes", String.valueOf(mcpMarketUnlockMinutes));
```

In `setValues()`, add:

```java
        mcpServerEnabled = getBooleanValue(values, "mcpServerEnabled");
        // Configuration.getIntValue returns 0 for a key an older database does not have yet,
        // so 0 means "not configured" and the default applies.
        mcpServerPort = orDefault(getIntValue(values, "mcpServerPort"), 8765);
        mcpSearchesPerSession = orDefault(getIntValue(values, "mcpSearchesPerSession"), 20);
        mcpMarketUnlockMinutes = orDefault(getIntValue(values, "mcpMarketUnlockMinutes"), 30);
```

and add this private helper to the class:

```java
    private static int orDefault(int value, int defaultValue) {
        return value > 0 ? value : defaultValue;
    }
```

- [ ] **Step 2: Write the failing tests**

Append to `src/test/java/core/net/TransferSearchServiceTest.java` (inside the existing class):

```java
    @Test
    void searchForAgent_refusedWhileMarketSearchIsLocked() {
        final var service = new StubService();

        assertThatExceptionOfType(TransferSearchService.SearchFailed.class)
                .isThrownBy(() -> service.searchForAgent(validQuery().build()))
                .withMessageContaining("locked");

        assertThat(service.lastQueryString).isNull();
    }

    @Test
    void searchForAgent_succeedsWhileUnlocked() throws Exception {
        final var service = new StubService();
        service.unlockMarketSearch();

        assertThat(service.searchForAgent(validQuery().build()).hits()).hasSize(1);
        assertThat(service.marketUnlockSecondsRemaining()).isPositive();
    }

    @Test
    void searchForAgent_refusesThe21stCallAndSaysSo() throws Exception {
        final var service = new StubService();
        service.unlockMarketSearch();
        core.model.UserParameter.instance().mcpSearchesPerSession = 20;

        for (int i = 0; i < 20; i++) {
            service.searchForAgent(validQuery().build());
        }

        assertThatExceptionOfType(TransferSearchService.SearchFailed.class)
                .isThrownBy(() -> service.searchForAgent(validQuery().build()))
                .withMessageContaining("20");
    }
```

- [ ] **Step 3: Run the tests to verify they fail**

Run: `./gradlew test --tests "core.net.TransferSearchServiceTest"`
Expected: FAIL — `searchForAgent`, `unlockMarketSearch`, `marketUnlockSecondsRemaining` do not exist.

- [ ] **Step 4: Implement the guards**

Add to `src/main/java/core/net/TransferSearchService.java`:

```java
    /**
     * When the user's market search unlock expires. Null while locked. Deliberately in memory only:
     * an unlock must not survive a restart.
     */
    private Instant marketUnlockedUntil;

    /** Searches issued in this HO session, by either caller. Bounds total CHPP traffic. */
    private int searchesThisSession;

    /**
     * Open market search for {@link core.model.UserParameter#mcpMarketUnlockMinutes} minutes.
     * Only ever called from the options panel button - a human action in HO's own UI is the
     * precondition CHPP asks for.
     */
    public synchronized void unlockMarketSearch() {
        final var minutes = Math.max(1, UserParameter.instance().mcpMarketUnlockMinutes);
        marketUnlockedUntil = Instant.now().plus(Duration.ofMinutes(minutes));
    }

    /** Seconds left on the unlock window, 0 if market search is locked. */
    public synchronized long marketUnlockSecondsRemaining() {
        if (marketUnlockedUntil == null) {
            return 0;
        }
        final var remaining = Duration.between(Instant.now(), marketUnlockedUntil).toSeconds();
        return Math.max(remaining, 0);
    }

    /**
     * Search the transfer market on behalf of an MCP client.
     * <p>
     * Refuses unless the user has unlocked market search in HO, and unless the per-session cap has
     * room left. The message says why, so the model can act on it rather than retrying.
     */
    public XMLTransferSearchParser.Page searchForAgent(TransferSearchQuery query) throws SearchFailed {
        synchronized (this) {
            if (marketUnlockSecondsRemaining() == 0) {
                throw new SearchFailed(null, "Transfer market search is locked. Hattrick only allows "
                        + "the market to be searched on an explicit user request, so the user has to "
                        + "press \"unlock market search\" in HO's options under MCP server first.");
            }
        }
        return search(query);
    }
```

and enforce the cap inside `search`, right after the validation block:

```java
        synchronized (this) {
            final var cap = UserParameter.instance().mcpSearchesPerSession;
            if (searchesThisSession >= cap) {
                throw new SearchFailed(null, "The transfer search cap of " + cap + " searches per HO "
                        + "session is used up. Raise it in HO's options under MCP server, or restart "
                        + "HO, before searching again.");
            }
            searchesThisSession++;
        }
```

Add the imports `core.model.UserParameter`, `java.time.Duration`, `java.time.Instant`.

- [ ] **Step 5: Run the tests to verify they pass**

Run: `./gradlew test --tests "core.net.TransferSearchServiceTest"`
Expected: PASS (5 tests).

- [ ] **Step 6: Commit**

```bash
git add src/main/java/core/net/TransferSearchService.java \
        src/main/java/core/model/UserParameter.java \
        src/test/java/core/net/TransferSearchServiceTest.java
git commit -m "Gate agent transfer search behind a user unlock window and a session cap"
```

---

### Task 3: `TRANSFERLISTING` table and migration

The price corpus (spec §5). One row per listing per observation, written from data already in hand,
so it costs no extra CHPP traffic.

**Files:**
- Create: `src/main/java/core/db/TransferListing.java`
- Create: `src/main/java/core/db/TransferListingTable.java`
- Modify: `src/main/java/core/db/DBManager.java:62` (DBVersion), `:66` (previousDBVersion),
  `:237-285` (`initAllTables`), plus a new accessor
- Modify: `src/main/java/core/db/DBUpdater.java:91` (the `case 1000:` line) and a new
  `updateDBv1001` method
- Test: `src/test/java/core/db/TransferListingTableTest.java`

**Interfaces:**
- Consumes: `core.file.xml.XMLTransferSearchParser.Hit`, `core.db.AbstractTable`
- Produces:
  - `TransferListing` — `Storable` with `fromHit(Hit, HODateTime observedAt)`, plus getters/setters
    for every column
  - `TransferListingTable.TABLENAME` = `"TRANSFERLISTING"`
  - `TransferListingTable#storeListing(TransferListing)`
  - `TransferListingTable#loadUnresolved(int max)` → `List<TransferListing>`
  - `TransferListingTable#storeSalePrice(int playerId, HODateTime observedAt, long salePrice)`
  - `DBManager#storeTransferListing(TransferListing)`, `DBManager#loadUnresolvedTransferListings(int)`,
    `DBManager#storeTransferListingSalePrice(int, HODateTime, long)`

- [ ] **Step 1: Write the failing test**

Create `src/test/java/core/db/TransferListingTableTest.java`:

```java
package core.db;

import core.file.xml.XMLTransferSearchParser.Details;
import core.file.xml.XMLTransferSearchParser.Hit;
import core.util.HODateTime;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TransferListingTableTest {

    private Connection conn;
    private TransferListingTable table;

    @BeforeAll
    void setUp() throws SQLException {
        conn = DriverManager.getConnection("jdbc:hsqldb:mem:transferlistingtestdb", "SA", "");
        final var connectionManager = new ConnectionManager();
        connectionManager.connect(conn);
        table = new TransferListingTable(connectionManager);
        table.createTable();
    }

    @AfterAll
    void cleanUp() throws SQLException {
        conn.createStatement().execute("DROP TABLE " + TransferListingTable.TABLENAME);
        conn.close();
    }

    private static Hit hit(int playerId, Details details) {
        return new Hit(playerId, "Sven Persson", 3, 1_500_000L,
                HODateTime.fromHT("2026-09-02 20:15:00"), 1_650_000L, "Bidding Boys",
                555444, "Selling Sam", 2, details);
    }

    private static Details details() {
        return new Details(24, 77, 44_000L, 29_000, 6, 4, 3, 2, 1, -1,
                8, 1, 12, 5, 9, 7, 6, 10);
    }

    @Test
    void roundTripsAHitWithDetails() {
        final var observedAt = HODateTime.fromHT("2026-08-30 12:00:00");

        table.storeListing(TransferListing.fromHit(hit(111222333, details()), observedAt));

        final var loaded = table.loadUnresolved(10).stream()
                .filter(l -> l.getPlayerId() == 111222333).findFirst().orElseThrow();
        assertThat(loaded.getAskingPrice()).isEqualTo(1_500_000L);
        assertThat(loaded.getHighestBid()).isEqualTo(1_650_000L);
        assertThat(loaded.getTsi()).isEqualTo(29_000);
        assertThat(loaded.getPlaymaker()).isEqualTo(12);
        assertThat(loaded.getSalePrice()).isNull();
    }

    /** Details are null once a deadline has passed - every consumer has to survive that. */
    @Test
    void roundTripsAHitWithoutDetails() {
        final var observedAt = HODateTime.fromHT("2026-08-30 13:00:00");

        table.storeListing(TransferListing.fromHit(hit(444555666, null), observedAt));

        final var loaded = table.loadUnresolved(10).stream()
                .filter(l -> l.getPlayerId() == 444555666).findFirst().orElseThrow();
        assertThat(loaded.getTsi()).isNull();
        assertThat(loaded.getPlaymaker()).isNull();
        assertThat(loaded.getAskingPrice()).isEqualTo(1_500_000L);
        assertThat(loaded.getSalePrice()).isNull();
    }

    @Test
    void storesASalePriceAndDropsTheRowOutOfTheUnresolvedList() {
        final var observedAt = HODateTime.fromHT("2026-08-30 14:00:00");
        table.storeListing(TransferListing.fromHit(hit(777888999, details()), observedAt));

        table.storeSalePrice(777888999, observedAt, 1_700_000L);

        assertThat(table.loadUnresolved(10)).noneMatch(l -> l.getPlayerId() == 777888999);
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew test --tests "core.db.TransferListingTableTest"`
Expected: FAIL — compilation error, `TransferListing` does not exist.

- [ ] **Step 3: Write the row object**

Create `src/main/java/core/db/TransferListing.java`:

```java
package core.db;

import core.file.xml.XMLTransferSearchParser.Hit;
import core.util.HODateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * One observation of one player on the transfer market.
 * <p>
 * The skill and detail fields are boxed because Hattrick drops the whole Details block once a
 * deadline has passed - an expired listing is stored with the market data only.
 */
@Getter
@Setter
public class TransferListing extends AbstractTable.Storable {

    private int playerId;
    private HODateTime observedAt;
    private String playerName;
    private HODateTime deadline;
    private long askingPrice;
    private long highestBid;
    private String bidderName;
    private Integer tsi;
    private Integer age;
    private Integer ageDays;
    private Long salary;
    private Integer specialty;
    private Integer form;
    private Integer experience;
    private Integer leadership;
    private Integer cards;
    private Integer injuryLevel;
    private Integer stamina;
    private Integer keeper;
    private Integer playmaker;
    private Integer scorer;
    private Integer passing;
    private Integer winger;
    private Integer defender;
    private Integer setPieces;
    /** What the player actually sold for, filled in later by the resolve pass. Null until then. */
    private Long salePrice;
    private HODateTime resolvedAt;

    public TransferListing() {
    }

    /** The snapshot of one search hit, as observed now. */
    public static TransferListing fromHit(Hit hit, HODateTime observedAt) {
        final var listing = new TransferListing();
        listing.playerId = hit.playerId();
        listing.observedAt = observedAt;
        listing.playerName = hit.playerName();
        listing.deadline = hit.deadline();
        listing.askingPrice = hit.askingPrice();
        listing.highestBid = hit.highestBid();
        listing.bidderName = hit.bidderTeamName();

        final var details = hit.details();
        if (details == null) {
            return listing;
        }
        listing.age = details.age();
        listing.ageDays = details.ageDays();
        listing.salary = details.salary();
        listing.tsi = details.tsi();
        listing.form = details.form();
        listing.experience = details.experience();
        listing.leadership = details.leadership();
        listing.specialty = details.specialty();
        listing.cards = details.cards();
        listing.injuryLevel = details.injuryLevel();
        listing.stamina = details.stamina();
        listing.keeper = details.keeper();
        listing.playmaker = details.playmaker();
        listing.scorer = details.scorer();
        listing.passing = details.passing();
        listing.winger = details.winger();
        listing.defender = details.defender();
        listing.setPieces = details.setPieces();
        return listing;
    }
}
```

- [ ] **Step 4: Write the table**

Create `src/main/java/core/db/TransferListingTable.java`:

```java
package core.db;

import core.util.HODateTime;

import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

/**
 * Every transfer market listing HO has seen, one row per observation.
 * <p>
 * The corpus behind player valuation: repeated observation of the same listing yields a highest bid
 * ever closer to the sale price, and the resolve pass fills the sale price in for expired ones.
 */
public class TransferListingTable extends AbstractTable {

    /** Table name **/
    static final String TABLENAME = "TRANSFERLISTING";

    TransferListingTable(ConnectionManager adapter) {
        super(TABLENAME, adapter);
        idColumns = 2;
    }

    @Override
    protected void initColumns() {
        this.columns = new ArrayList<>(List.of(
                intCol("PLAYERID", p -> ((TransferListing) p).getPlayerId(), (p, v) -> ((TransferListing) p).setPlayerId((Integer) v), false),
                timeCol("OBSERVEDAT", p -> HODateTime.toDbTimestamp(((TransferListing) p).getObservedAt()), (p, v) -> ((TransferListing) p).setObservedAt((HODateTime) v), false),
                ColumnDescriptor.Builder.newInstance().setColumnName("PLAYERNAME").setGetter(p -> ((TransferListing) p).getPlayerName()).setSetter((p, v) -> ((TransferListing) p).setPlayerName((String) v)).setType(Types.VARCHAR).setLength(256).isNullable(true).build(),
                timeCol("DEADLINE", p -> HODateTime.toDbTimestamp(((TransferListing) p).getDeadline()), (p, v) -> ((TransferListing) p).setDeadline((HODateTime) v), true),
                longCol("ASKINGPRICE", p -> ((TransferListing) p).getAskingPrice(), (p, v) -> ((TransferListing) p).setAskingPrice(v == null ? 0L : ((Number) v).longValue()), false),
                longCol("HIGHESTBID", p -> ((TransferListing) p).getHighestBid(), (p, v) -> ((TransferListing) p).setHighestBid(v == null ? 0L : ((Number) v).longValue()), false),
                ColumnDescriptor.Builder.newInstance().setColumnName("BIDDERNAME").setGetter(p -> ((TransferListing) p).getBidderName()).setSetter((p, v) -> ((TransferListing) p).setBidderName((String) v)).setType(Types.VARCHAR).setLength(256).isNullable(true).build(),
                intCol("TSI", p -> ((TransferListing) p).getTsi(), (p, v) -> ((TransferListing) p).setTsi((Integer) v), true),
                intCol("AGE", p -> ((TransferListing) p).getAge(), (p, v) -> ((TransferListing) p).setAge((Integer) v), true),
                intCol("AGEDAYS", p -> ((TransferListing) p).getAgeDays(), (p, v) -> ((TransferListing) p).setAgeDays((Integer) v), true),
                longCol("SALARY", p -> ((TransferListing) p).getSalary(), (p, v) -> ((TransferListing) p).setSalary(v == null ? null : ((Number) v).longValue()), true),
                intCol("SPECIALTY", p -> ((TransferListing) p).getSpecialty(), (p, v) -> ((TransferListing) p).setSpecialty((Integer) v), true),
                intCol("FORM", p -> ((TransferListing) p).getForm(), (p, v) -> ((TransferListing) p).setForm((Integer) v), true),
                intCol("EXPERIENCE", p -> ((TransferListing) p).getExperience(), (p, v) -> ((TransferListing) p).setExperience((Integer) v), true),
                intCol("LEADERSHIP", p -> ((TransferListing) p).getLeadership(), (p, v) -> ((TransferListing) p).setLeadership((Integer) v), true),
                intCol("CARDS", p -> ((TransferListing) p).getCards(), (p, v) -> ((TransferListing) p).setCards((Integer) v), true),
                intCol("INJURYLEVEL", p -> ((TransferListing) p).getInjuryLevel(), (p, v) -> ((TransferListing) p).setInjuryLevel((Integer) v), true),
                intCol("STAMINA", p -> ((TransferListing) p).getStamina(), (p, v) -> ((TransferListing) p).setStamina((Integer) v), true),
                intCol("KEEPER", p -> ((TransferListing) p).getKeeper(), (p, v) -> ((TransferListing) p).setKeeper((Integer) v), true),
                intCol("PLAYMAKER", p -> ((TransferListing) p).getPlaymaker(), (p, v) -> ((TransferListing) p).setPlaymaker((Integer) v), true),
                intCol("SCORER", p -> ((TransferListing) p).getScorer(), (p, v) -> ((TransferListing) p).setScorer((Integer) v), true),
                intCol("PASSING", p -> ((TransferListing) p).getPassing(), (p, v) -> ((TransferListing) p).setPassing((Integer) v), true),
                intCol("WINGER", p -> ((TransferListing) p).getWinger(), (p, v) -> ((TransferListing) p).setWinger((Integer) v), true),
                intCol("DEFENDER", p -> ((TransferListing) p).getDefender(), (p, v) -> ((TransferListing) p).setDefender((Integer) v), true),
                intCol("SETPIECES", p -> ((TransferListing) p).getSetPieces(), (p, v) -> ((TransferListing) p).setSetPieces((Integer) v), true),
                longCol("SALEPRICE", p -> ((TransferListing) p).getSalePrice(), (p, v) -> ((TransferListing) p).setSalePrice(v == null ? null : ((Number) v).longValue()), true),
                timeCol("RESOLVEDAT", p -> HODateTime.toDbTimestamp(((TransferListing) p).getResolvedAt()), (p, v) -> ((TransferListing) p).setResolvedAt((HODateTime) v), true)
        )).toArray(new ColumnDescriptor[0]);
    }

    private static ColumnDescriptor intCol(String name, java.util.function.UnaryOperator<Object> getter,
                                           java.util.function.BiConsumer<Object, Object> setter, boolean nullable) {
        return ColumnDescriptor.Builder.newInstance().setColumnName(name).setGetter(getter)
                .setSetter(setter).setType(Types.INTEGER).isNullable(nullable).build();
    }

    private static ColumnDescriptor longCol(String name, java.util.function.UnaryOperator<Object> getter,
                                            java.util.function.BiConsumer<Object, Object> setter, boolean nullable) {
        return ColumnDescriptor.Builder.newInstance().setColumnName(name).setGetter(getter)
                .setSetter(setter).setType(Types.BIGINT).isNullable(nullable).build();
    }

    private static ColumnDescriptor timeCol(String name, java.util.function.UnaryOperator<Object> getter,
                                            java.util.function.BiConsumer<Object, Object> setter, boolean nullable) {
        return ColumnDescriptor.Builder.newInstance().setColumnName(name).setGetter(getter)
                .setSetter(setter).setType(Types.TIMESTAMP).isNullable(nullable).build();
    }

    /** Snapshot one observed listing. */
    public void storeListing(TransferListing listing) {
        store(listing);
    }

    /**
     * Listings whose deadline has passed and whose sale price is still unknown, oldest first.
     *
     * @param max how many rows to return - the resolve pass is capped, one CHPP call per row
     */
    public List<TransferListing> loadUnresolved(int max) {
        final var sql = createSelectStatement(
                "WHERE SALEPRICE IS NULL ORDER BY OBSERVEDAT ASC LIMIT " + Math.max(max, 0));
        return load(TransferListing.class, connectionManager.executeQuery(sql));
    }

    /** Record what an observed listing actually sold for. */
    public void storeSalePrice(int playerId, HODateTime observedAt, long salePrice) {
        connectionManager.executeUpdate("UPDATE " + getTableName()
                + " SET SALEPRICE=" + salePrice
                + ", RESOLVEDAT='" + HODateTime.toDbTimestamp(HODateTime.now()) + "'"
                + " WHERE PLAYERID=" + playerId
                + " AND OBSERVEDAT='" + HODateTime.toDbTimestamp(observedAt) + "'");
    }
}
```

Three things about this table worth knowing before writing it:

- `loadUnresolved` deliberately does not filter on `deadline < now` — the caller (Task 9) does that,
  so the test can use a future deadline.
- **`Types.BIGINT` round-trips through `Integer`.** `AbstractTable.load` maps `BIT, SMALLINT,
  TINYINT, BIGINT, INTEGER` all onto `getInteger` (`AbstractTable.java:227`), so the `Long` fields
  here are effectively int-ranged on the way back out. That is fine for Hattrick prices, salaries
  and TSI, none of which approach 2³¹ — but do not widen a column expecting more than that without
  fixing `AbstractTable` first. The `((Number) v).longValue()` in the setters is what makes the
  `Integer` the loader hands back land in a `Long` field without a `ClassCastException`.
- **There is no primary key.** `idColumns = 2` only tells `AbstractTable` how many leading columns
  form the key for its *generated* WHERE clauses; nothing declares a constraint, and
  `TransferListing.fromHit` always produces a fresh unstored object, so `store()` always INSERTs.
  Two observations of the same listing at the same `HODateTime` would therefore both be kept. That
  is acceptable for a corpus — it is an append-only log of observations, and `storeSalePrice`
  updating two identical rows is harmless — but it is a deliberate choice, not an oversight. No
  index either: the resolve pass is a capped button press, not a hot path.

- [ ] **Step 5: Run the test to verify it passes**

Run: `./gradlew test --tests "core.db.TransferListingTableTest"`
Expected: PASS (3 tests).

- [ ] **Step 6: Register the table and write the migration**

In `src/main/java/core/db/DBManager.java`:

```java
	private static final int DBVersion = 1001; // HO 10.0 version
	...
	private static final int previousDBVersion = 1000;
```

At the end of `initAllTables()`, after the `HallOfFamePlayersTable` line:

```java
		tables.put(TransferListingTable.TABLENAME, new TransferListingTable(connectionManager));
```

and next to the other table accessors (e.g. after `getTransfers`, around line 1500), add:

```java
	public void storeTransferListing(TransferListing listing) {
		((TransferListingTable) getTable(TransferListingTable.TABLENAME)).storeListing(listing);
	}

	public List<TransferListing> loadUnresolvedTransferListings(int max) {
		return ((TransferListingTable) getTable(TransferListingTable.TABLENAME)).loadUnresolved(max);
	}

	public void storeTransferListingSalePrice(int playerId, HODateTime observedAt, long salePrice) {
		((TransferListingTable) getTable(TransferListingTable.TABLENAME))
				.storeSalePrice(playerId, observedAt, salePrice);
	}
```

In `src/main/java/core/db/DBUpdater.java`, change the tail of the switch from

```java
					case 901:
						updateDBv1000(DBVersion);
					case 1000:
```

to

```java
					case 901:
						updateDBv1000(DBVersion);
					case 1000:
						updateDBv1001(DBVersion);
					case 1001:
```

and add the step next to `updateDBv1000`:

```java
	/** HO 10.0.1: the transfer listing corpus behind player valuation. */
	private void updateDBv1001(int dbVersion) throws SQLException {
		dbManager.getTable(TransferListingTable.TABLENAME).createTable();

		updateDBVersion(dbVersion, 1001);
	}
```

`updateDBv1000` and `updateDBv901` both end in exactly that `updateDBVersion(dbVersion, <version>)`
call, so `updateDBv1001` follows the same shape. `AbstractTable.createTable()` is guarded by `tableExists()`, so re-running the step on every DEV
start is harmless.

- [ ] **Step 7: Verify the migration runs**

Run: `./gradlew test`
Expected: BUILD SUCCESSFUL.

Then run HO once and confirm the table exists and no migration error was logged:

Run: `./gradlew run`
Expected: HO starts; `logs/` shows no `DBUpdater` error. Quit HO.

- [ ] **Step 8: Commit**

```bash
git add src/main/java/core/db/TransferListing.java \
        src/main/java/core/db/TransferListingTable.java \
        src/main/java/core/db/DBManager.java \
        src/main/java/core/db/DBUpdater.java \
        src/test/java/core/db/TransferListingTableTest.java
git commit -m "Add TRANSFERLISTING table and the 1001 migration"
```

---

### Task 4: MCP transport — JSON-RPC framing, `Origin` guard, tool dispatch

The protocol shell, with no tools in it yet. Everything here is testable without a socket because
`handle(JsonObject)` is separated from the HTTP plumbing.

**Files:**
- Create: `src/main/java/core/mcp/McpTool.java`
- Create: `src/main/java/core/mcp/McpHttpServer.java`
- Test: `src/test/java/core/mcp/McpHttpServerTest.java`

**Interfaces:**
- Consumes: `com.google.gson.*`, `com.sun.net.httpserver.HttpServer`
- Produces:
  - `record McpTool(String name, String description, JsonObject inputSchema, Handler handler)`
    with `interface Handler { JsonObject call(JsonObject arguments) throws Exception; }`
  - `class McpToolException extends Exception` — an expected tool failure, rendered as
    `isError: true` content rather than a protocol error
  - `McpTool.schema(JsonObject properties, String... required)` and
    `McpTool.prop(String type, String description)` schema builders
  - `McpHttpServer.instance()`, `#start(int port)`, `#stop()`, `#isRunning()`, `#port()`
  - `McpHttpServer#register(McpTool)` and `#registerAll(List<McpTool>)`
  - `McpHttpServer#handle(JsonObject request)` → `JsonObject`, or `null` for a notification
    (package private, unit tested)

- [ ] **Step 1: Write the failing test**

Create `src/test/java/core/mcp/McpHttpServerTest.java`:

```java
package core.mcp;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

class McpHttpServerTest {

    private static JsonObject request(String json) {
        return JsonParser.parseString(json).getAsJsonObject();
    }

    private static McpHttpServer serverWithEchoTool() {
        final var server = new McpHttpServer();
        server.register(new McpTool("echo", "Echoes its argument.",
                McpTool.schema(McpTool.properties("text", McpTool.prop("string", "what to echo")), "text"),
                args -> {
                    final var result = new JsonObject();
                    result.addProperty("text", args.get("text").getAsString());
                    return result;
                }));
        return server;
    }

    @Test
    void initialize_echoesTheClientsProtocolVersion() {
        final var response = serverWithEchoTool().handle(request("""
                {"jsonrpc":"2.0","id":1,"method":"initialize",
                 "params":{"protocolVersion":"2025-06-18","capabilities":{}}}"""));

        assertThat(response.getAsJsonObject("result").get("protocolVersion").getAsString())
                .isEqualTo("2025-06-18");
        assertThat(response.getAsJsonObject("result").getAsJsonObject("capabilities").has("tools"))
                .isTrue();
    }

    @Test
    void toolsList_advertisesNameDescriptionAndSchema() {
        final var response = serverWithEchoTool()
                .handle(request("""
                        {"jsonrpc":"2.0","id":2,"method":"tools/list"}"""));

        final var tools = response.getAsJsonObject("result").getAsJsonArray("tools");
        assertThat(tools).hasSize(1);
        final var tool = tools.get(0).getAsJsonObject();
        assertThat(tool.get("name").getAsString()).isEqualTo("echo");
        assertThat(tool.get("description").getAsString()).isNotEmpty();
        assertThat(tool.getAsJsonObject("inputSchema").get("type").getAsString()).isEqualTo("object");
    }

    @Test
    void toolsCall_returnsTheHandlersJsonAsTextContent() {
        final var response = serverWithEchoTool().handle(request("""
                {"jsonrpc":"2.0","id":3,"method":"tools/call",
                 "params":{"name":"echo","arguments":{"text":"hi"}}}"""));

        final var result = response.getAsJsonObject("result");
        assertThat(result.get("isError").getAsBoolean()).isFalse();
        final var text = result.getAsJsonArray("content").get(0).getAsJsonObject()
                .get("text").getAsString();
        assertThat(JsonParser.parseString(text).getAsJsonObject().get("text").getAsString())
                .isEqualTo("hi");
    }

    @Test
    void toolsCall_reportsAnExpectedFailureAsIsErrorContent() {
        final var server = new McpHttpServer();
        server.register(new McpTool("boom", "Always fails.", McpTool.schema(new JsonObject()),
                args -> { throw new McpToolException("no model loaded"); }));

        final var response = server.handle(request("""
                {"jsonrpc":"2.0","id":4,"method":"tools/call","params":{"name":"boom","arguments":{}}}"""));

        final var result = response.getAsJsonObject("result");
        assertThat(result.get("isError").getAsBoolean()).isTrue();
        assertThat(result.getAsJsonArray("content").get(0).getAsJsonObject().get("text").getAsString())
                .contains("no model loaded");
        assertThat(response.has("error")).isFalse();
    }

    @Test
    void unknownMethod_isMinus32601() {
        final var response = serverWithEchoTool()
                .handle(request("""
                        {"jsonrpc":"2.0","id":5,"method":"nope/nope"}"""));

        assertThat(response.getAsJsonObject("error").get("code").getAsInt()).isEqualTo(-32601);
    }

    @Test
    void unknownToolName_isMinus32602() {
        final var response = serverWithEchoTool().handle(request("""
                {"jsonrpc":"2.0","id":6,"method":"tools/call","params":{"name":"missing"}}"""));

        assertThat(response.getAsJsonObject("error").get("code").getAsInt()).isEqualTo(-32602);
    }

    @Test
    void aNotificationGetsNoResponse() {
        assertThat(serverWithEchoTool()
                .handle(request("""
                        {"jsonrpc":"2.0","method":"notifications/initialized"}""")))
                .isNull();
    }

    @Test
    void malformedJson_isMinus32700() throws Exception {
        final var server = serverWithEchoTool();
        server.start(0);
        try {
            final var response = post(server.port(), "{not json", null);
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(JsonParser.parseString(response.body()).getAsJsonObject()
                    .getAsJsonObject("error").get("code").getAsInt()).isEqualTo(-32700);
        } finally {
            server.stop();
        }
    }

    /** The DNS rebinding guard: a browser always sends Origin, a real MCP client never does. */
    @Test
    void aRequestWithAnOriginHeaderIsRejected() throws Exception {
        final var server = serverWithEchoTool();
        server.start(0);
        try {
            final var response = post(server.port(),
                    """
                    {"jsonrpc":"2.0","id":1,"method":"tools/list"}""", "http://evil.example");
            assertThat(response.statusCode()).isEqualTo(403);
        } finally {
            server.stop();
        }
    }

    private static HttpResponse<String> post(int port, String body, String origin) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/mcp"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body));
        if (origin != null) {
            builder = builder.header("Origin", origin);
        }
        return HttpClient.newHttpClient().send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew test --tests "core.mcp.McpHttpServerTest"`
Expected: FAIL — compilation error, `core.mcp` does not exist.

#### The tool record

- [ ] **Step 3: Write `McpTool`**

Create `src/main/java/core/mcp/McpTool.java`:

```java
package core.mcp;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * One MCP tool: what {@code tools/list} advertises and what {@code tools/call} runs.
 *
 * @param name        the tool name the client calls
 * @param description what the tool does, read by the model to decide whether to call it
 * @param inputSchema JSON schema of the {@code arguments} object
 * @param handler     the implementation
 */
public record McpTool(String name, String description, JsonObject inputSchema, Handler handler) {

    @FunctionalInterface
    public interface Handler {
        /**
         * @param arguments the client's {@code arguments} object, never null (empty if omitted)
         * @return the JSON that becomes the tool result
         * @throws McpToolException for an expected failure the model should read and act on
         */
        JsonObject call(JsonObject arguments) throws Exception;
    }

    /** An object schema over the given properties, with the named ones required. */
    public static JsonObject schema(JsonObject properties, String... required) {
        final var schema = new JsonObject();
        schema.addProperty("type", "object");
        schema.add("properties", properties);
        final var requiredNames = new JsonArray();
        for (final var name : required) {
            requiredNames.add(name);
        }
        schema.add("required", requiredNames);
        return schema;
    }

    /** One schema property. */
    public static JsonObject prop(String type, String description) {
        final var property = new JsonObject();
        property.addProperty("type", type);
        property.addProperty("description", description);
        return property;
    }

    /** A properties object built from alternating name / {@link #prop} pairs. */
    public static JsonObject properties(Object... nameAndProperty) {
        final var properties = new JsonObject();
        for (int i = 0; i + 1 < nameAndProperty.length; i += 2) {
            properties.add((String) nameAndProperty[i], (JsonObject) nameAndProperty[i + 1]);
        }
        return properties;
    }
}
```

Create `src/main/java/core/mcp/McpToolException.java`:

```java
package core.mcp;

/**
 * An expected tool failure - no model loaded, search cap reached, unknown player id.
 * <p>
 * Rendered as an {@code isError: true} tool result with a plain English explanation rather than as a
 * JSON-RPC protocol error, so that the model can read it and act on it instead of treating it as a
 * transport fault.
 */
public class McpToolException extends Exception {
    public McpToolException(String message) {
        super(message);
    }
}
```

#### The transport

- [ ] **Step 4: Write `McpHttpServer`**

Create `src/main/java/core/mcp/McpHttpServer.java`:

```java
package core.mcp;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import core.util.HOLogger;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * MCP server over Streamable HTTP, single endpoint {@code POST /mcp}, answers as
 * {@code application/json}.
 * <p>
 * No SSE: nothing here pushes server initiated messages, so the event stream half of the transport
 * would be dead weight. {@code GET /mcp} answers 405.
 * <p>
 * The whole security perimeter is three rules, and none of them is optional: bind loopback only,
 * reject any request that carries an {@code Origin} header, and reject any request whose
 * {@code Host} is not a loopback name. Real MCP clients send no Origin and browsers always do; a
 * DNS rebound name resolves to 127.0.0.1 but still arrives with the attacker's name in Host.
 * Together they are the rebinding guard the MCP specification asks of a local server. Without them
 * any page open in the user's browser could read the whole squad.
 * <p>
 * There is no authentication beyond that: any process running as this user can call every tool.
 * The switch being off by default is what bounds that.
 */
public final class McpHttpServer {

    private static final String DEFAULT_PROTOCOL_VERSION = "2024-11-05";
    private static final Gson GSON = new Gson();

    private static McpHttpServer instance;

    private final Map<String, McpTool> tools = new LinkedHashMap<>();
    private HttpServer server;
    private ExecutorService executor;

    public static synchronized McpHttpServer instance() {
        if (instance == null) {
            instance = new McpHttpServer();
        }
        return instance;
    }

    /** Package private: production code uses {@link #instance()}, tests build their own. */
    McpHttpServer() {
    }

    public void register(McpTool tool) {
        tools.put(tool.name(), tool);
    }

    public void registerAll(List<McpTool> toolList) {
        toolList.forEach(this::register);
    }

    public synchronized boolean isRunning() {
        return server != null;
    }

    public synchronized int port() {
        return server == null ? 0 : server.getAddress().getPort();
    }

    /**
     * Bind the endpoint.
     *
     * @param port the TCP port, 0 to let the OS pick one (tests)
     * @throws IOException if the port is already in use - the caller reports that, it is not swallowed
     */
    public synchronized void start(int port) throws IOException {
        if (server != null) {
            return;
        }
        // loopback only, never 0.0.0.0
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), port), 0);
        // Single threaded on purpose. Handlers reach straight into DBManager, and HO's
        // ConnectionManager shares one JDBC Statement and one StatementCache across every table, so
        // two tool calls in parallel would interleave on it. A slow CHPP call therefore blocks the
        // next request - the cheaper of the two problems.
        executor = Executors.newSingleThreadExecutor();
        server.setExecutor(executor);
        server.createContext("/mcp", this::serve);
        server.start();
        HOLogger.instance().info(getClass(), "MCP server listening on http://127.0.0.1:" + port() + "/mcp");
    }

    public synchronized void stop() {
        if (server == null) {
            return;
        }
        server.stop(0);
        executor.shutdownNow();
        server = null;
        executor = null;
        HOLogger.instance().info(getClass(), "MCP server stopped");
    }

    private void serve(HttpExchange exchange) throws IOException {
        try {
            if (exchange.getRequestHeaders().getFirst("Origin") != null) {
                respond(exchange, 403, "{\"error\":\"Origin header not accepted\"}");
                return;
            }
            if (!isLoopbackHost(exchange.getRequestHeaders().getFirst("Host"))) {
                respond(exchange, 403, "{\"error\":\"Host not accepted\"}");
                return;
            }
            if (!"POST".equals(exchange.getRequestMethod())) {
                respond(exchange, 405, "{\"error\":\"POST only\"}");
                return;
            }

            final var body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            final JsonObject request;
            try {
                request = JsonParser.parseString(body).getAsJsonObject();
            } catch (Exception e) {
                respond(exchange, 200, GSON.toJson(error(null, -32700, "Parse error")));
                return;
            }

            final var response = handle(request);
            if (response == null) {
                respond(exchange, 202, "");          // a notification gets no body
                return;
            }
            respond(exchange, 200, GSON.toJson(response));
        } catch (Exception e) {
            HOLogger.instance().error(getClass(), e);
            try {
                respond(exchange, 200, GSON.toJson(error(null, -32603, "Internal error")));
            } catch (Exception alreadyAnswered) {
                exchange.close();     // headers were already sent, there is nothing left to say
            }
        }
    }

    /**
     * The second half of the DNS rebinding guard: a rebound name resolves to 127.0.0.1, but the
     * browser still puts the attacker's name in {@code Host}.
     */
    private static boolean isLoopbackHost(String host) {
        if (host == null) {
            return false;
        }
        final var colon = host.lastIndexOf(':');
        final var name = colon > host.lastIndexOf(']') ? host.substring(0, colon) : host;
        return name.equals("127.0.0.1") || name.equals("localhost") || name.equals("[::1]");
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        final var bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        // -1, not 0: com.sun.net.httpserver reads a length of 0 as "chunked, length unknown".
        exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
        try (final var out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    /**
     * Answer one JSON-RPC request.
     *
     * @return the response object, or null for a notification (a request without an id)
     */
    JsonObject handle(JsonObject request) {
        final var id = request.get("id");
        if (id == null) {
            return null;                     // notifications/initialized and friends
        }
        final var method = request.has("method") ? request.get("method").getAsString() : "";
        final var params = request.has("params") && request.get("params").isJsonObject()
                ? request.getAsJsonObject("params")
                : new JsonObject();

        return switch (method) {
            case "initialize" -> result(id, initialize(params));
            case "tools/list" -> result(id, toolsList());
            case "tools/call" -> toolsCall(id, params);
            default -> error(id, -32601, "Unknown method: " + method);
        };
    }

    private JsonObject initialize(JsonObject params) {
        // Echo the client's protocol version so that a client of any generation is satisfied - this
        // server behaves the same under all of them, it speaks plain request/response JSON.
        final var version = params.has("protocolVersion")
                ? params.get("protocolVersion").getAsString()
                : DEFAULT_PROTOCOL_VERSION;

        final var capabilities = new JsonObject();
        capabilities.add("tools", new JsonObject());

        final var serverInfo = new JsonObject();
        serverInfo.addProperty("name", "ho");
        serverInfo.addProperty("version", String.valueOf(core.HO.VERSION));

        final var result = new JsonObject();
        result.addProperty("protocolVersion", version);
        result.add("capabilities", capabilities);
        result.add("serverInfo", serverInfo);
        return result;
    }

    private JsonObject toolsList() {
        final var list = new JsonArray();
        for (final var tool : tools.values()) {
            final var entry = new JsonObject();
            entry.addProperty("name", tool.name());
            entry.addProperty("description", tool.description());
            entry.add("inputSchema", tool.inputSchema());
            list.add(entry);
        }
        final var result = new JsonObject();
        result.add("tools", list);
        return result;
    }

    private JsonObject toolsCall(com.google.gson.JsonElement id, JsonObject params) {
        final var name = params.has("name") ? params.get("name").getAsString() : "";
        final var tool = tools.get(name);
        if (tool == null) {
            return error(id, -32602, "Unknown tool: " + name);
        }
        final var arguments = params.has("arguments") && params.get("arguments").isJsonObject()
                ? params.getAsJsonObject("arguments")
                : new JsonObject();
        try {
            return result(id, toolResult(GSON.toJson(tool.handler().call(arguments)), false));
        } catch (McpToolException e) {
            return result(id, toolResult(e.getMessage(), true));
        } catch (IllegalArgumentException e) {
            return error(id, -32602, e.getMessage());
        } catch (Exception e) {
            HOLogger.instance().error(getClass(), e);      // no stack trace crosses the wire
            return error(id, -32603, "Internal error in tool " + name);
        }
    }

    private static JsonObject toolResult(String text, boolean isError) {
        final var content = new JsonObject();
        content.addProperty("type", "text");
        content.addProperty("text", text);
        final var contents = new JsonArray();
        contents.add(content);
        final var result = new JsonObject();
        result.add("content", contents);
        result.addProperty("isError", isError);
        return result;
    }

    private static JsonObject result(com.google.gson.JsonElement id, JsonObject result) {
        final var response = new JsonObject();
        response.addProperty("jsonrpc", "2.0");
        response.add("id", id);
        response.add("result", result);
        return response;
    }

    private static JsonObject error(com.google.gson.JsonElement id, int code, String message) {
        final var error = new JsonObject();
        error.addProperty("code", code);
        error.addProperty("message", message);
        final var response = new JsonObject();
        response.addProperty("jsonrpc", "2.0");
        response.add("id", id);
        response.add("error", error);
        return response;
    }
}
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `./gradlew test --tests "core.mcp.McpHttpServerTest"`
Expected: PASS (9 tests).

- [ ] **Step 6: Confirm `jdk.httpserver` survives packaging**

The repo's only existing use of `com.sun.net.httpserver` is in test scope on the full local JDK,
which does not prove the module is in a shipped bundle (spec §2.1). Verify against a real one:

```bash
./gradlew createVersion createDistribution
jpackage --type app-image --input "$PWD/build/install/XXX" --dest "$PWD/build/macapp" \
  --name HO --main-jar HO.jar --main-class core.HOLauncher --app-version 10.0 --arguments INSTALLED
build/macapp/HO.app/Contents/runtime/Contents/Home/bin/java --list-modules | grep jdk.httpserver
```

Expected: `jdk.httpserver@17...` (or the local JDK's version) is listed.
If it is **not** listed, stop and report — the design's "no new dependencies" claim does not hold and
the transport choice has to be revisited before continuing.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/core/mcp/ src/test/java/core/mcp/
git commit -m "Add MCP JSON-RPC transport with loopback bind and Origin guard"
```

---

### Task 5: Shared serialization and the private rating model

The helpers every tool group needs, including the fix for the rating cache collision (spec §3).

`Lineup.ratingRevision` is a per-instance counter starting at 0, and `LineupRatingCache` is keyed on
that number alone while holding exactly one revision at a time. A scratch `Lineup` at revision 0 and
the live `Lineup` at revision 0 are indistinguishable to the cache, so a shared
`RatingPredictionModel` would serve the live lineup's ratings for a scratch one and poison the live
cache. Every rating-using tool call therefore builds its own model and throws it away.

**Files:**
- Create: `src/main/java/core/mcp/McpJson.java`
- Test: `src/test/java/core/mcp/McpJsonTest.java`

**Interfaces:**
- Consumes: `core.model.HOVerwaltung`, `core.model.player.Player`,
  `core.rating.RatingPredictionModel`, `core.file.xml.XMLTransferSearchParser.Hit`,
  `module.lineup.Lineup`
- Produces:
  - `McpJson.privateRatingModel()` → `RatingPredictionModel`, a fresh instance of the class the
    manager currently holds
  - `McpJson.model()` → `HOModel`, throwing `McpToolException` when nothing is loaded
  - `McpJson.player(Player)` → `JsonObject` without ratings
  - `McpJson.player(Player, RatingPredictionModel)` → `JsonObject` with `positionRatings`
  - `McpJson.hit(Hit)` → `JsonObject`
  - `McpJson.ratings(RatingPredictionModel, Lineup)` → `JsonObject` (seven sectors + `formation`)
  - `McpJson.FIELD_ROLES` → `int[]` of 100..113
  - argument readers: `reqInt`, `optInt`, `optString`, `reqArray`

- [ ] **Step 1: Write the failing test**

Create `src/test/java/core/mcp/McpJsonTest.java`:

```java
package core.mcp;

import com.google.gson.JsonObject;
import core.constants.player.PlayerSkill;
import core.file.xml.XMLTransferSearchParser.Hit;
import core.model.player.Player;
import core.util.HODateTime;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class McpJsonTest {

    @Test
    void player_carriesIdentityAgeAndTheEightSkills() {
        final var player = new Player();
        player.setPlayerId(4711);
        player.setAge(27);
        player.setAgeDays(42);
        player.setValue4Skill(PlayerSkill.PLAYMAKING, 11);
        player.setValue4Skill(PlayerSkill.DEFENDING, 7);

        final var json = McpJson.player(player);

        assertThat(json.get("playerId").getAsInt()).isEqualTo(4711);
        assertThat(json.get("ageYears").getAsInt()).isEqualTo(27);
        assertThat(json.get("ageDays").getAsInt()).isEqualTo(42);
        assertThat(json.getAsJsonObject("skills").get("playmaking").getAsInt()).isEqualTo(11);
        assertThat(json.getAsJsonObject("skills").get("defending").getAsInt()).isEqualTo(7);
        assertThat(json.has("positionRatings")).isFalse();
    }

    @Test
    void hit_survivesANullDetailsBlock() {
        final var expired = new Hit(999, "Gone Guy", 3, 100_000L,
                HODateTime.fromHT("2026-08-01 20:00:00"), 120_000L, "", 0, "", 0, null);

        final var json = McpJson.hit(expired);

        assertThat(json.get("playerId").getAsInt()).isEqualTo(999);
        assertThat(json.get("highestBid").getAsLong()).isEqualTo(120_000L);
        assertThat(json.get("details").isJsonNull()).isTrue();
    }

    @Test
    void reqInt_rejectsAMissingArgument() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> McpJson.reqInt(new JsonObject(), "playerId"))
                .withMessageContaining("playerId");
    }

    @Test
    void optInt_fallsBackToTheDefault() {
        assertThat(McpJson.optInt(new JsonObject(), "ageTolerance", 1)).isEqualTo(1);
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew test --tests "core.mcp.McpJsonTest"`
Expected: FAIL — `McpJson` does not exist.

- [ ] **Step 3: Write `McpJson`**

Create `src/main/java/core/mcp/McpJson.java`:

```java
package core.mcp;

import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import core.constants.player.PlayerSkill;
import core.file.xml.XMLTransferSearchParser.Hit;
import core.model.HOModel;
import core.model.HOVerwaltung;
import core.model.Team;
import core.model.player.IMatchRoleID;
import core.model.player.Player;
import core.rating.RatingPredictionModel;
import core.util.HOLogger;
import module.lineup.Lineup;

/**
 * Serialization shared by the tool groups, plus the two lookups every tool starts with.
 */
public final class McpJson {

    /** The fourteen field slots Hattrick numbers, keeper (100) through left forward (113). */
    public static final int[] FIELD_ROLES = {
            IMatchRoleID.keeper, 101, 102, 103, 104, 105, 106, 107, 108, 109, 110, 111, 112,
            IMatchRoleID.leftForward
    };

    private McpJson() {
    }

    /** The loaded team snapshot. */
    public static HOModel model() throws McpToolException {
        final var model = HOVerwaltung.instance().getModel();
        if (model == null || model.getBasics() == null) {
            throw new McpToolException("No team data is loaded in HO yet. Download the team first.");
        }
        return model;
    }

    /**
     * A rating model private to one tool call.
     * <p>
     * Never use {@link HOModel#getRatingPredictionModel()} for a scratch lineup: its
     * {@code LineupRatingCache} is keyed on {@link Lineup#getRatingRevision()} alone and holds one
     * revision at a time, so a scratch lineup at revision 0 and the live lineup at revision 0 are
     * the same cache entry - the scratch score would come back as the live one and would overwrite
     * it. A fresh instance per call sidesteps that and costs nothing measurable.
     * <p>
     * Built from the class the manager currently holds, so that a user running the Groovy
     * {@code AkasolaceRatingPredictionModel} gets the same numbers here as in the Lineup tab.
     */
    public static RatingPredictionModel privateRatingModel() throws McpToolException {
        final var team = model().getTeam();
        final var shared = model().getRatingPredictionModel();
        try {
            return shared.getClass().getConstructor(Team.class).newInstance(team);
        } catch (ReflectiveOperationException e) {
            HOLogger.instance().log(McpJson.class, e);
            return new RatingPredictionModel(team);
        }
    }

    public static JsonObject player(Player p) {
        final var json = new JsonObject();
        json.addProperty("playerId", p.getPlayerId());
        json.addProperty("name", p.getFullName());
        json.addProperty("ageYears", p.getAge());
        json.addProperty("ageDays", p.getAgeDays());
        json.addProperty("tsi", p.getTsi());
        json.addProperty("wage", p.getWage() == null ? 0L : p.getWage().getSwedishKrona().longValue());
        json.addProperty("form", p.getForm());
        json.addProperty("stamina", p.getStamina());
        json.addProperty("loyalty", p.getLoyalty());
        json.addProperty("motherclub", p.isHomeGrown());
        json.addProperty("specialty", p.getSpecialty());
        json.addProperty("specialtyName", p.getSpecialtyName());
        json.addProperty("injuryWeeks", p.getInjuryWeeks());
        json.addProperty("cards", p.getTotalCards());
        json.addProperty("transferListed", p.getTransferListed() > 0);

        final var skills = new JsonObject();
        skills.addProperty("keeper", p.getValue4Skill(PlayerSkill.KEEPER));
        skills.addProperty("defending", p.getValue4Skill(PlayerSkill.DEFENDING));
        skills.addProperty("playmaking", p.getValue4Skill(PlayerSkill.PLAYMAKING));
        skills.addProperty("winger", p.getValue4Skill(PlayerSkill.WINGER));
        skills.addProperty("passing", p.getValue4Skill(PlayerSkill.PASSING));
        skills.addProperty("scoring", p.getValue4Skill(PlayerSkill.SCORING));
        skills.addProperty("setPieces", p.getValue4Skill(PlayerSkill.SETPIECES));
        skills.addProperty("experience", p.getValue4Skill(PlayerSkill.EXPERIENCE));
        json.add("skills", skills);
        return json;
    }

    /**
     * The player plus what he is worth in each of the fourteen field slots at normal behaviour -
     * the single most useful thing a model can be handed in one call, since it otherwise has to
     * guess role fit from raw skills.
     */
    public static JsonObject player(Player p, RatingPredictionModel ratingModel) {
        final var json = player(p);
        final var ratings = new JsonObject();
        for (final var roleId : FIELD_ROLES) {
            ratings.addProperty(String.valueOf(roleId),
                    ratingModel.getPlayerRatingMatchBeginning(p, roleId, IMatchRoleID.NORMAL));
        }
        json.add("positionRatings", ratings);
        return json;
    }

    /** One transfer market hit. Details are null once the deadline has passed. */
    public static JsonObject hit(Hit hit) {
        final var json = new JsonObject();
        json.addProperty("playerId", hit.playerId());
        json.addProperty("playerName", hit.playerName());
        json.addProperty("nativeCountryId", hit.nativeCountryId());
        json.addProperty("askingPrice", hit.askingPrice());
        json.addProperty("highestBid", hit.highestBid());
        json.addProperty("bidderTeamName", hit.bidderTeamName());
        json.addProperty("sellerTeamId", hit.sellerTeamId());
        json.addProperty("sellerTeamName", hit.sellerTeamName());
        json.addProperty("sellerLeagueId", hit.sellerLeagueId());
        json.addProperty("deadline", hit.deadline() == null ? null : hit.deadline().toHT());

        final var d = hit.details();
        if (d == null) {
            json.add("details", JsonNull.INSTANCE);
            return json;
        }
        final var details = new JsonObject();
        details.addProperty("age", d.age());
        details.addProperty("ageDays", d.ageDays());
        details.addProperty("salary", d.salary());
        details.addProperty("tsi", d.tsi());
        details.addProperty("form", d.form());
        details.addProperty("experience", d.experience());
        details.addProperty("leadership", d.leadership());
        details.addProperty("specialty", d.specialty());
        details.addProperty("cards", d.cards());
        details.addProperty("injuryLevel", d.injuryLevel());
        details.addProperty("stamina", d.stamina());
        details.addProperty("keeper", d.keeper());
        details.addProperty("playmaker", d.playmaker());
        details.addProperty("scorer", d.scorer());
        details.addProperty("passing", d.passing());
        details.addProperty("winger", d.winger());
        details.addProperty("defender", d.defender());
        details.addProperty("setPieces", d.setPieces());
        json.add("details", details);
        return json;
    }

    /** The seven sector ratings over 90 minutes, plus the derived formation. */
    public static JsonObject ratings(RatingPredictionModel ratingModel, Lineup lineup) {
        final var json = new JsonObject();
        for (final var sector : RatingPredictionModel.RatingSector.values()) {
            json.addProperty(sector.name().toLowerCase(java.util.Locale.ENGLISH),
                    ratingModel.getAverageRating(lineup, sector, 90));
        }
        json.addProperty("formation", lineup.getCurrentTeamFormationString());
        return json;
    }

    // ---- argument readers -------------------------------------------------------------------

    public static int reqInt(JsonObject args, String name) {
        if (!args.has(name) || args.get(name).isJsonNull()) {
            throw new IllegalArgumentException("Missing required argument: " + name);
        }
        return args.get(name).getAsInt();
    }

    public static int optInt(JsonObject args, String name, int defaultValue) {
        return args.has(name) && !args.get(name).isJsonNull() ? args.get(name).getAsInt() : defaultValue;
    }

    public static Integer optInteger(JsonObject args, String name) {
        return args.has(name) && !args.get(name).isJsonNull() ? args.get(name).getAsInt() : null;
    }

    public static String optString(JsonObject args, String name, String defaultValue) {
        return args.has(name) && !args.get(name).isJsonNull() ? args.get(name).getAsString() : defaultValue;
    }

    public static JsonArray reqArray(JsonObject args, String name) {
        if (!args.has(name) || !args.get(name).isJsonArray()) {
            throw new IllegalArgumentException("Missing required array argument: " + name);
        }
        return args.getAsJsonArray(name);
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew test --tests "core.mcp.McpJsonTest"`
Expected: PASS (4 tests).

If `player_carriesIdentityAgeAndTheEightSkills` fails on something other than an assertion, suspect
`Player.getSpecialtyName()`: it may route through `TranslationFacility` and want the language files
initialised, which the unit suite does not do. Drop `specialtyName` from the JSON if so — the
numeric `specialty` is what a model actually needs and the name is only a convenience.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/core/mcp/McpJson.java src/test/java/core/mcp/McpJsonTest.java
git commit -m "Add MCP serialization helpers and per-call private rating models"
```

---

### Task 6: Read tools — team, squad, player, fixtures

**Files:**
- Create: `src/main/java/core/mcp/TeamTools.java`
- Test: `src/test/java/core/mcp/TeamToolsTest.java`

**Interfaces:**
- Consumes: `McpJson`, `McpTool`, `HOModel`, `DBManager#getTransfers(int, boolean)`
- Produces: `TeamTools.tools()` → `List<McpTool>` containing `get_team`, `get_squad`, `get_player`,
  `get_upcoming_matches`

- [ ] **Step 1: Write the failing test**

Create `src/test/java/core/mcp/TeamToolsTest.java`:

```java
package core.mcp;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The tool bodies need a loaded HOModel and a database, which the unit suite deliberately does not
 * have (there is no Swing or CHPP harness in this repo). What is testable here is the surface: the
 * names and schemas the client sees.
 */
class TeamToolsTest {

    @Test
    void tools_areTheFourReadTools() {
        assertThat(TeamTools.tools()).extracting(McpTool::name)
                .containsExactly("get_team", "get_squad", "get_player", "get_upcoming_matches");
    }

    @Test
    void getPlayer_requiresAPlayerId() {
        final var getPlayer = TeamTools.tools().stream()
                .filter(t -> t.name().equals("get_player")).findFirst().orElseThrow();

        assertThat(getPlayer.inputSchema().getAsJsonArray("required")).hasSize(1);
        assertThat(getPlayer.inputSchema().getAsJsonArray("required").get(0).getAsString())
                .isEqualTo("playerId");
        assertThat(getPlayer.inputSchema().getAsJsonObject("properties").has("playerId")).isTrue();
    }

    @Test
    void everyToolHasANonEmptyDescription() {
        assertThat(TeamTools.tools()).allSatisfy(t -> assertThat(t.description()).isNotBlank());
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew test --tests "core.mcp.TeamToolsTest"`
Expected: FAIL — `TeamTools` does not exist.

- [ ] **Step 3: Write `TeamTools`**

Create `src/main/java/core/mcp/TeamTools.java`:

```java
package core.mcp;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import core.db.DBManager;

import java.util.List;

/** The read only view of the team HO already knows about. */
public final class TeamTools {

    private TeamTools() {
    }

    public static List<McpTool> tools() {
        return List.of(
                new McpTool("get_team",
                        "The user's team: name, league, finances, stadium and staff, as of the "
                                + "latest download.",
                        McpTool.schema(new JsonObject()),
                        args -> team()),
                new McpTool("get_squad",
                        "Every player in the squad with skills, form, stamina, loyalty, TSI, wage, "
                                + "specialty, injuries and cards, plus a positionRatings map giving "
                                + "what the player is worth in each of the fourteen field slots "
                                + "(100 keeper through 113 left forward) at normal behaviour.",
                        McpTool.schema(new JsonObject()),
                        args -> squad()),
                new McpTool("get_player",
                        "One player in full, together with his transfer history as HO recorded it.",
                        McpTool.schema(
                                McpTool.properties("playerId", McpTool.prop("integer", "the Hattrick player id")),
                                "playerId"),
                        args -> player(McpJson.reqInt(args, "playerId"))),
                new McpTool("get_upcoming_matches",
                        "The fixture list of the current series.",
                        McpTool.schema(new JsonObject()),
                        args -> fixtures())
        );
    }

    private static JsonObject team() throws McpToolException {
        final var model = McpJson.model();
        final var basics = model.getBasics();
        final var economy = model.getEconomy();
        final var league = model.getLeague();
        final var stadium = model.getStadium();

        final var json = new JsonObject();
        json.addProperty("teamId", basics.getTeamId());
        json.addProperty("teamName", basics.getTeamName());
        json.addProperty("manager", basics.getManager());
        json.addProperty("season", basics.getSeason());
        json.addProperty("week", basics.getSpieltag());

        json.addProperty("leagueId", basics.getLiga());
        json.addProperty("countryId", basics.getLand());

        if (league != null) {
            // core.model.series.Liga: getLiga() is the series name, not an id
            final var leagueJson = new JsonObject();
            leagueJson.addProperty("seriesName", league.getLiga());
            leagueJson.addProperty("position", league.getPlatzierung());
            leagueJson.addProperty("points", league.getPunkte());
            leagueJson.addProperty("goalsFor", league.getToreFuer());
            leagueJson.addProperty("goalsAgainst", league.getToreGegen());
            json.add("league", leagueJson);
        }
        if (economy != null) {
            // Economy returns AmountOfMoney, whose raw value is swedish krona
            final var economyJson = new JsonObject();
            economyJson.addProperty("cash", money(economy.getCash()));
            economyJson.addProperty("expectedWeeksTotal", money(economy.getExpectedWeeksTotal()));
            economyJson.addProperty("playersSalary", money(economy.getCostsPlayers()));
            json.add("economy", economyJson);
        }
        if (stadium != null) {
            final var stadiumJson = new JsonObject();
            stadiumJson.addProperty("name", stadium.getStadiumName());
            stadiumJson.addProperty("capacity", stadium.getTotalSize());
            json.add("stadium", stadiumJson);
        }

        final var staff = new JsonArray();
        if (model.getStaff() != null) {
            for (final var member : model.getStaff()) {          // core.model.StaffMember
                final var staffJson = new JsonObject();
                staffJson.addProperty("name", member.getName());
                staffJson.addProperty("type", String.valueOf(member.getStaffType()));
                staffJson.addProperty("level", member.getLevel());
                staffJson.addProperty("cost", member.getCost());
                staff.add(staffJson);
            }
        }
        json.add("staff", staff);
        return json;
    }

    /** The raw amount behind an AmountOfMoney, which stores swedish krona. */
    private static long money(core.util.AmountOfMoney amount) {
        return amount == null ? 0L : amount.getSwedishKrona().longValue();
    }

    private static JsonObject squad() throws McpToolException {
        final var model = McpJson.model();
        final var ratingModel = McpJson.privateRatingModel();

        final var players = new JsonArray();
        for (final var player : model.getCurrentPlayers()) {
            players.add(McpJson.player(player, ratingModel));
        }
        final var json = new JsonObject();
        json.add("players", players);
        return json;
    }

    private static JsonObject player(int playerId) throws McpToolException {
        final var model = McpJson.model();
        final var player = model.getCurrentPlayer(playerId);
        if (player == null) {
            throw new McpToolException("No player with id " + playerId + " is in the squad. "
                    + "Call get_squad for the ids HO knows about.");
        }
        final var json = McpJson.player(player, McpJson.privateRatingModel());

        final var transfers = new JsonArray();
        for (final var transfer : DBManager.instance().getTransfers(playerId, true)) {
            final var transferJson = new JsonObject();
            transferJson.addProperty("date", transfer.getDate() == null ? null : transfer.getDate().toHT());
            transferJson.addProperty("price", money(transfer.getPrice()));
            transferJson.addProperty("tsi", transfer.getTsi());
            transferJson.addProperty("type", transfer.getType());
            transferJson.addProperty("buyerTeamName", transfer.getBuyerName());
            transferJson.addProperty("sellerTeamName", transfer.getSellerName());
            transfers.add(transferJson);
        }
        json.add("transfers", transfers);
        return json;
    }

    private static JsonObject fixtures() throws McpToolException {
        final var model = McpJson.model();
        final var matches = new JsonArray();
        final var fixtures = model.getFixtures();
        if (fixtures != null) {
            for (final var pairing : fixtures.getMatches()) {
                final var matchJson = new JsonObject();
                matchJson.addProperty("matchId", pairing.getMatchId());
                matchJson.addProperty("matchDay", pairing.getSpieltag());
                matchJson.addProperty("date", pairing.getDatum() == null ? null : pairing.getDatum().toHT());
                matchJson.addProperty("homeTeamId", pairing.getHeimId());
                matchJson.addProperty("homeTeamName", pairing.getHeimName());
                matchJson.addProperty("awayTeamId", pairing.getGastId());
                matchJson.addProperty("awayTeamName", pairing.getGastName());
                matches.add(matchJson);
            }
        }
        final var json = new JsonObject();
        json.add("matches", matches);
        return json;
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew test --tests "core.mcp.TeamToolsTest"`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/core/mcp/TeamTools.java src/test/java/core/mcp/TeamToolsTest.java
git commit -m "Add MCP read tools for team, squad, player and fixtures"
```

---

### Task 7: Lineup tools

`evaluate_lineup` is the load-bearing one: the model can try ten variants in a turn for free because
each is scored on a scratch `Lineup` with a private rating model and never touches the live one.

**`optimize_lineup` is the one exception, and it cannot be fixed from here.** `Lineup.optimizeLineup`
delegates to `LineupAssistant`, which reaches for
`HOVerwaltung.instance().getModel().getRatingPredictionModel()` itself (`LineupAssistant.java:354`) -
the **shared** model, applied to a scratch lineup. That is precisely the collision Task 5 exists to
avoid, and nothing passed in from here changes it; only rewriting `LineupAssistant` would, and that
is out of scope. So the tool says so in its own description, and Task 10 Step 5 re-reads the live
ratings after calling it rather than only after `evaluate_lineup`.

**Files:**
- Create: `src/main/java/core/mcp/LineupTools.java`
- Test: `src/test/java/core/mcp/LineupToolsTest.java`

**Interfaces:**
- Consumes: `McpJson`, `McpTool`, `module.lineup.Lineup`, `core.model.player.IMatchRoleID`,
  `core.model.HOModel#getCurrentLineupTeam()`, `#storeLineup(MatchLineupTeam)`
- Produces: `LineupTools.tools()` → `List<McpTool>` containing `get_lineup`, `evaluate_lineup`,
  `optimize_lineup`, `apply_lineup`; and `LineupTools.scratchLineup(JsonObject spec)` → `Lineup`
  (package private, unit tested)

**Lineup spec** — the shared input shape for `evaluate_lineup` and `apply_lineup`:

```json
{
  "positions": [ { "roleId": 100, "playerId": 12345, "behaviour": 0 } ],
  "bench":     [ { "roleId": 114, "playerId": 67890 } ],
  "tactic": 0, "attitude": 0, "styleOfPlay": 0,
  "captainId": 12345, "setPiecesTakerId": 67890
}
```

- [ ] **Step 1: Write the failing test**

Create `src/test/java/core/mcp/LineupToolsTest.java`:

```java
package core.mcp;

import com.google.gson.JsonParser;
import core.model.player.IMatchRoleID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LineupToolsTest {

    @Test
    void tools_areTheFourLineupTools() {
        assertThat(LineupTools.tools()).extracting(McpTool::name)
                .containsExactly("get_lineup", "evaluate_lineup", "optimize_lineup", "apply_lineup");
    }

    @Test
    void scratchLineup_placesPlayersAtTheGivenRolesAndSetsTheOrders() {
        final var spec = JsonParser.parseString("""
                {"positions":[{"roleId":100,"playerId":11},{"roleId":113,"playerId":22,"behaviour":1}],
                 "tactic":3,"attitude":1,"captainId":11,"setPiecesTakerId":22}""").getAsJsonObject();

        final var lineup = LineupTools.scratchLineup(spec);

        assertThat(lineup.getPositionById(IMatchRoleID.keeper).getPlayerId()).isEqualTo(11);
        assertThat(lineup.getPositionById(IMatchRoleID.leftForward).getPlayerId()).isEqualTo(22);
        assertThat((int) lineup.getPositionById(IMatchRoleID.leftForward).getBehaviour()).isEqualTo(1);
        assertThat(lineup.getTacticType()).isEqualTo(3);
        assertThat(lineup.getAttitude()).isEqualTo(1);
        assertThat(lineup.getCaptain()).isEqualTo(11);
        assertThat(lineup.getKicker()).isEqualTo(22);
    }

    /**
     * The regression guard for spec section 3: a scratch lineup and the live lineup both sit at
     * rating revision 0 in a fresh fixture, which is exactly the case where a shared
     * LineupRatingCache would confuse them. Two scratch lineups must stay distinguishable.
     */
    @Test
    void scratchLineupsAreIndependentInstances() {
        final var a = LineupTools.scratchLineup(JsonParser.parseString("""
                {"positions":[{"roleId":100,"playerId":11}]}""").getAsJsonObject());
        final var b = LineupTools.scratchLineup(JsonParser.parseString("""
                {"positions":[{"roleId":100,"playerId":22}]}""").getAsJsonObject());

        assertThat(a).isNotSameAs(b);
        assertThat(a.getPositionById(IMatchRoleID.keeper).getPlayerId()).isEqualTo(11);
        assertThat(b.getPositionById(IMatchRoleID.keeper).getPlayerId()).isEqualTo(22);
    }
}
```

**Assert through `getPositionById`, never `getPlayerByPositionID`.** `Lineup.getPlayerByPositionID`
(`Lineup.java:700`) resolves the id against `HOVerwaltung.instance().getModel().getCurrentPlayer(...)`;
with no model loaded the resulting NPE is swallowed by its own `try/catch` and the method returns
null, so an assertion chained off it NPEs in the test. `getPositionById(int)` returns the
`MatchLineupPosition` straight out of the lineup's own slot list and needs no model.
`new Lineup()` calls `initPositionen553()`, so every slot 100-113 exists before anything is set, and
`setSpielerAtPosition` → `MatchRoleID.setPlayerIdIfValidForLineup(int, Lineup)` checks only
`lineup.hasFreePosition()` — no model lookup on the write path either.

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew test --tests "core.mcp.LineupToolsTest"`
Expected: FAIL — `LineupTools` does not exist.

- [ ] **Step 3: Write `LineupTools`**

Create `src/main/java/core/mcp/LineupTools.java`:

```java
package core.mcp;

import com.google.gson.JsonObject;
import core.model.player.IMatchRoleID;
import core.model.player.Player;
import module.lineup.Lineup;

import javax.swing.SwingUtilities;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Scoring and setting lineups.
 * <p>
 * {@code evaluate_lineup} and {@code optimize_lineup} work on a scratch {@link Lineup} scored with a
 * private rating model, so a model can try any number of variants without touching, or being
 * polluted by, the live lineup. Only {@code apply_lineup} writes, and it does so on the EDT.
 */
public final class LineupTools {

    private static final String SPEC_DOC =
            "A lineup: positions is a list of {roleId, playerId, behaviour}, roleId 100 keeper "
                    + "through 113 left forward for the field and 114-118 for the bench, behaviour "
                    + "0 normal, 1 offensive, 2 defensive, 3 towards middle, 4 towards wing. "
                    + "tactic, attitude, styleOfPlay, captainId and setPiecesTakerId are optional.";

    private LineupTools() {
    }

    public static List<McpTool> tools() {
        return List.of(
                new McpTool("get_lineup",
                        "The lineup currently set in HO, with its sector ratings and formation.",
                        McpTool.schema(new JsonObject()),
                        args -> currentLineup()),
                new McpTool("evaluate_lineup",
                        "Score a hypothetical lineup without changing anything in HO. Returns the "
                                + "seven sector ratings over 90 minutes and the formation. Call it "
                                + "as often as you like - it has no side effects.",
                        lineupSchema(),
                        LineupTools::evaluate),
                new McpTool("optimize_lineup",
                        "Let HO's lineup assistant build a baseline lineup out of the given players, "
                                + "then refine it yourself with evaluate_lineup. Unlike "
                                + "evaluate_lineup this one runs through HO's shared rating state, "
                                + "so call it once for a starting point rather than in a loop.",
                        McpTool.schema(McpTool.properties(
                                        "availablePlayerIds", McpTool.prop("array", "player ids to choose from; the whole squad if omitted"),
                                        "sectorsStrengthPriority", McpTool.prop("integer", "0 balanced, 1 defence, 2 midfield, 3 attack"),
                                        "withForm", McpTool.prop("boolean", "weigh current form"),
                                        "idealPosFirst", McpTool.prop("boolean", "fill each player's best position first"),
                                        "considerInjured", McpTool.prop("boolean", "allow injured players"),
                                        "considerSuspended", McpTool.prop("boolean", "allow suspended players"),
                                        "useAverageRating", McpTool.prop("boolean", "optimise the match average rather than the kick off rating")),
                                "sectorsStrengthPriority"),
                        LineupTools::optimize),
                new McpTool("apply_lineup",
                        "Write a lineup into HO. This changes what the Lineup tab shows and what HO "
                                + "would upload; it does not send anything to Hattrick.",
                        lineupSchema(),
                        LineupTools::apply)
        );
    }

    private static JsonObject lineupSchema() {
        return McpTool.schema(McpTool.properties(
                        "positions", McpTool.prop("array", SPEC_DOC),
                        "bench", McpTool.prop("array", "bench slots as {roleId, playerId}"),
                        "tactic", McpTool.prop("integer", "tactic type"),
                        "attitude", McpTool.prop("integer", "attitude"),
                        "styleOfPlay", McpTool.prop("integer", "style of play, -10 defensive to 10 offensive"),
                        "captainId", McpTool.prop("integer", "player id of the captain"),
                        "setPiecesTakerId", McpTool.prop("integer", "player id of the set pieces taker")),
                "positions");
    }

    /** A throwaway lineup built from a lineup spec. Never the live one. */
    static Lineup scratchLineup(JsonObject spec) {
        final var lineup = new Lineup();
        applySpec(lineup, spec);
        return lineup;
    }

    private static void applySpec(Lineup lineup, JsonObject spec) {
        for (final var element : McpJson.reqArray(spec, "positions")) {
            final var position = element.getAsJsonObject();
            lineup.setSpielerAtPosition(McpJson.reqInt(position, "roleId"),
                    McpJson.reqInt(position, "playerId"),
                    (byte) McpJson.optInt(position, "behaviour", IMatchRoleID.NORMAL));
        }
        if (spec.has("bench")) {
            for (final var element : spec.getAsJsonArray("bench")) {
                final var slot = element.getAsJsonObject();
                lineup.setSpielerAtPosition(McpJson.reqInt(slot, "roleId"), McpJson.reqInt(slot, "playerId"));
            }
        }
        if (spec.has("tactic")) {
            lineup.setTacticType(spec.get("tactic").getAsInt());
        }
        if (spec.has("attitude")) {
            lineup.setAttitude(spec.get("attitude").getAsInt());
        }
        if (spec.has("styleOfPlay")) {
            lineup.setStyleOfPlay(spec.get("styleOfPlay").getAsInt());
        }
        if (spec.has("captainId")) {
            lineup.setCaptain(spec.get("captainId").getAsInt());
        }
        if (spec.has("setPiecesTakerId")) {
            lineup.setKicker(spec.get("setPiecesTakerId").getAsInt());
        }
    }

    private static JsonObject evaluate(JsonObject spec) throws McpToolException {
        return McpJson.ratings(McpJson.privateRatingModel(), scratchLineup(spec));
    }

    private static JsonObject optimize(JsonObject args) throws McpToolException {
        final var model = McpJson.model();

        List<Player> candidates = new ArrayList<>(model.getCurrentPlayers());
        if (args.has("availablePlayerIds")) {
            final var wanted = new ArrayList<Integer>();
            args.getAsJsonArray("availablePlayerIds").forEach(e -> wanted.add(e.getAsInt()));
            candidates = candidates.stream().filter(p -> wanted.contains(p.getPlayerId())).toList();
        }

        final var lineup = new Lineup();
        lineup.optimizeLineup(candidates,
                (byte) McpJson.reqInt(args, "sectorsStrengthPriority"),
                args.has("withForm") && args.get("withForm").getAsBoolean(),
                args.has("idealPosFirst") && args.get("idealPosFirst").getAsBoolean(),
                args.has("considerInjured") && args.get("considerInjured").getAsBoolean(),
                args.has("considerSuspended") && args.get("considerSuspended").getAsBoolean(),
                args.has("useAverageRating") && args.get("useAverageRating").getAsBoolean());

        // describe() re-scores on a private model, but optimizeLineup itself already went through
        // HOVerwaltung's shared one - see the note at the top of this task.
        return describe(lineup, McpJson.privateRatingModel());
    }

    private static JsonObject currentLineup() throws McpToolException {
        final var model = McpJson.model();
        final var ratingModel = McpJson.privateRatingModel();
        // apply_lineup mutates the live Lineup on the EDT, so read it there too.
        return onEdt(() -> describe(model.getCurrentLineup(), ratingModel));
    }

    private static JsonObject apply(JsonObject spec) throws McpToolException {
        final var model = McpJson.model();
        final var ratingModel = McpJson.privateRatingModel();
        return onEdt(() -> {
            // The live lineup is owned by its MatchLineupTeam - HOModel.setLineup takes a
            // MatchLineupTeam, not a Lineup - so mutate it in place and persist through the same
            // path the Lineup tab uses.
            final var team = model.getCurrentLineupTeam();
            applySpec(team.getLineup(), spec);
            model.storeLineup(team);
            return describe(team.getLineup(), ratingModel);
        });
    }

    private static JsonObject describe(Lineup lineup, core.rating.RatingPredictionModel ratingModel) {
        final var json = new JsonObject();
        final var positions = new com.google.gson.JsonArray();
        for (final var position : lineup.getAllPositions()) {
            final var positionJson = new JsonObject();
            positionJson.addProperty("roleId", position.getRoleId());
            positionJson.addProperty("playerId", position.getPlayerId());
            positionJson.addProperty("behaviour", position.getBehaviour());
            positions.add(positionJson);
        }
        json.add("positions", positions);
        json.addProperty("tactic", lineup.getTacticType());
        json.addProperty("attitude", lineup.getAttitude());
        json.addProperty("captainId", lineup.getCaptain());
        json.addProperty("setPiecesTakerId", lineup.getKicker());
        // styleOfPlay is settable on Lineup but readable only from MatchLineupTeam, so it is an
        // input here and not part of the output.
        json.add("ratings", McpJson.ratings(ratingModel, lineup));
        return json;
    }

    /** Run on the event dispatch thread and propagate any failure back to the caller. */
    private static JsonObject onEdt(EdtTask task) throws McpToolException {
        if (SwingUtilities.isEventDispatchThread()) {
            return task.run();
        }
        final var result = new AtomicReference<JsonObject>();
        final var failure = new AtomicReference<Exception>();
        try {
            SwingUtilities.invokeAndWait(() -> {
                try {
                    result.set(task.run());
                } catch (Exception e) {
                    failure.set(e);
                }
            });
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new McpToolException("Interrupted while waiting for HO's UI thread.");
        } catch (InvocationTargetException e) {
            throw new McpToolException("HO's UI thread rejected the request: " + e.getMessage());
        }
        if (failure.get() != null) {
            throw new McpToolException(failure.get().getMessage());
        }
        return result.get();
    }

    @FunctionalInterface
    private interface EdtTask {
        JsonObject run() throws McpToolException;
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew test --tests "core.mcp.LineupToolsTest"`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/core/mcp/LineupTools.java src/test/java/core/mcp/LineupToolsTest.java
git commit -m "Add MCP lineup tools scoring on scratch lineups"
```

---

### Task 8: Transfer tools and the corpus write

**Files:**
- Create: `src/main/java/core/mcp/TransferTools.java`
- Modify: `src/main/java/core/net/TransferSearchQuery.java` (widen `toQueryString`)
- Test: `src/test/java/core/mcp/TransferToolsTest.java`

**Interfaces:**
- Consumes: `TransferSearchService#searchForAgent`, `TransferSearchQuery`, `McpJson#hit`,
  `DBManager#storeTransferListing`, `TransferListing#fromHit`
- Produces:
  - `TransferTools.tools()` → `List<McpTool>` with `search_transfers`, `find_comparables`
  - `TransferTools.comparableQuery(Player, int ageTolerance, int skillTolerance)` →
    `TransferSearchQuery` (package private, unit tested)
  - `TransferTools.queryFrom(JsonObject)` → `TransferSearchQuery` (package private, unit tested)

`find_comparables` must pick the player's two highest **positional** skills only — `KEEPER`,
`DEFENDING`, `PLAYMAKING`, `PASSING`, `WINGER`, `SCORING`, `SETPIECES`. A naive maximum over the
whole `TransferSearchQuery.Skill` enum would happily pick `STAMINA`, `LEADERSHIP` or `EXPERIENCE`,
which say nothing about comparability, and the last two carry a Hattrick minimum level of 5 that
would then get the query rejected.

- [ ] **Step 1: Write the failing test**

Create `src/test/java/core/mcp/TransferToolsTest.java`:

```java
package core.mcp;

import com.google.gson.JsonParser;
import core.constants.player.PlayerSkill;
import core.constants.player.PlayerSpeciality;
import core.model.player.Player;
import core.net.TransferSearchQuery;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TransferToolsTest {

    private static Player player(int age, int... skillValues) {
        final var p = new Player();
        p.setPlayerId(4711);
        p.setAge(age);
        p.setAgeDays(0);
        final var skills = new PlayerSkill[]{
                PlayerSkill.KEEPER, PlayerSkill.DEFENDING, PlayerSkill.PLAYMAKING,
                PlayerSkill.PASSING, PlayerSkill.WINGER, PlayerSkill.SCORING,
                PlayerSkill.SETPIECES, PlayerSkill.STAMINA, PlayerSkill.EXPERIENCE,
                PlayerSkill.LEADERSHIP};
        for (int i = 0; i < skillValues.length && i < skills.length; i++) {
            p.setValue4Skill(skills[i], skillValues[i]);
        }
        return p;
    }

    @Test
    void tools_areTheTwoTransferTools() {
        assertThat(TransferTools.tools()).extracting(McpTool::name)
                .containsExactly("search_transfers", "find_comparables");
    }

    @Test
    void comparableQuery_usesAgeAndTheTwoHighestPositionalSkills() {
        //                      kp de pm pa wi sc sp st ex ld
        final var p = player(24, 1, 7, 12, 9, 6, 5, 3, 8, 4, 2);
        p.setSpecialty(PlayerSpeciality.QUICK);

        final var query = TransferTools.comparableQuery(p, 1, 1);

        assertThat(query.ageMin()).isEqualTo(23);
        assertThat(query.ageMax()).isEqualTo(25);
        assertThat(query.skillType1()).isEqualTo(TransferSearchQuery.Skill.PLAYMAKING);
        assertThat(query.minSkillValue1()).isEqualTo(11);
        assertThat(query.maxSkillValue1()).isEqualTo(13);
        assertThat(query.skillType2()).isEqualTo(TransferSearchQuery.Skill.PASSING);
        assertThat(query.minSkillValue2()).isEqualTo(8);
        assertThat(query.maxSkillValue2()).isEqualTo(10);
        assertThat(query.specialty()).isEqualTo(PlayerSpeciality.QUICK);
        assertThat(query.validationErrorKey()).isNull();
    }

    /** Stamina, experience and leadership are never comparability criteria, however high they are. */
    @Test
    void comparableQuery_neverPicksStaminaExperienceOrLeadership() {
        //                      kp de pm pa wi sc sp st ex ld
        final var p = player(28, 1, 4, 3, 2, 1, 1, 1, 20, 15, 18);

        final var query = TransferTools.comparableQuery(p, 1, 1);

        assertThat(query.skillType1()).isEqualTo(TransferSearchQuery.Skill.DEFENDING);
        assertThat(query.skillType2()).isEqualTo(TransferSearchQuery.Skill.PLAYMAKING);
        assertThat(query.validationErrorKey()).isNull();
    }

    /** Hattrick will not accept a keeper query below level 3 or a span wider than 4 levels. */
    @Test
    void comparableQuery_clampsToTheKeeperMinimumAndTheFourLevelSpan() {
        //                      kp de pm pa wi sc sp
        final var p = player(19, 2, 1, 1, 1, 1, 1, 1);

        final var query = TransferTools.comparableQuery(p, 1, 5);

        assertThat(query.skillType1()).isEqualTo(TransferSearchQuery.Skill.KEEPER);
        assertThat(query.minSkillValue1()).isEqualTo(3);       // keeper minimum level
        assertThat(query.maxSkillValue1() - query.minSkillValue1()).isLessThanOrEqualTo(4);
        assertThat(query.validationErrorKey()).isNull();
    }

    @Test
    void queryFrom_readsSkillNamesRatherThanChppNumbers() {
        final var args = JsonParser.parseString("""
                {"ageMin":20,"ageMax":22,"skillType1":"PLAYMAKING","minSkillValue1":7,
                 "maxSkillValue1":9,"priceMax":2000000}""").getAsJsonObject();

        final var query = TransferTools.queryFrom(args);

        assertThat(query.skillType1()).isEqualTo(TransferSearchQuery.Skill.PLAYMAKING);
        assertThat(query.priceMax()).isEqualTo(2_000_000);
        assertThat(query.pageSize()).isEqualTo(50);            // default
    }

    @Test
    void queryFrom_capsPageSizeAtTheChppMaximum() {
        final var args = JsonParser.parseString("""
                {"ageMin":20,"ageMax":22,"skillType1":"SCORING","minSkillValue1":7,
                 "maxSkillValue1":9,"pageSize":500}""").getAsJsonObject();

        assertThat(TransferTools.queryFrom(args).pageSize()).isEqualTo(100);
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew test --tests "core.mcp.TransferToolsTest"`
Expected: FAIL — `TransferTools` does not exist.

- [ ] **Step 3: Write `TransferTools`**

First widen one accessor. `TransferSearchQuery.toQueryString()` is declared package private
(`TransferSearchQuery.java:123`, `String toQueryString()`), which is why Task 1's test could call it
from `core.net`. `find_comparables` echoes the query it built from `core.mcp`, so make it `public`:

```java
    public String toQueryString() {
```

Then create `src/main/java/core/mcp/TransferTools.java`:

```java
package core.mcp;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import core.db.DBManager;
import core.db.TransferListing;
import core.file.xml.XMLTransferSearchParser;
import core.model.player.Player;
import core.net.TransferSearchQuery;
import core.net.TransferSearchService;
import core.util.HODateTime;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * Transfer market search and comparables.
 * <p>
 * Every result is snapshotted into the TRANSFERLISTING corpus before being returned - the data is
 * already in hand, so it costs no extra CHPP traffic, and repeated observation of the same listing
 * yields a highest bid ever closer to the sale price.
 */
public final class TransferTools {

    /** The skills that say something about what a player is comparable to. */
    private static final List<TransferSearchQuery.Skill> POSITIONAL_SKILLS = List.of(
            TransferSearchQuery.Skill.KEEPER,
            TransferSearchQuery.Skill.DEFENDING,
            TransferSearchQuery.Skill.PLAYMAKING,
            TransferSearchQuery.Skill.PASSING,
            TransferSearchQuery.Skill.WINGER,
            TransferSearchQuery.Skill.SCORING,
            TransferSearchQuery.Skill.SETPIECES);

    private static final int DEFAULT_PAGE_SIZE = 50;
    private static final int MAX_PAGE_SIZE = 100;     // the CHPP maximum

    private TransferTools() {
    }

    public static List<McpTool> tools() {
        return List.of(
                new McpTool("search_transfers",
                        "Search the Hattrick transfer market. Age and one skill are mandatory. Skill "
                                + "names are KEEPER, STAMINA, SETPIECES, DEFENDING, SCORING, WINGER, "
                                + "PASSING, PLAYMAKING, LEADERSHIP, EXPERIENCE. Hattrick rejects a "
                                + "skill span wider than 4 levels, set pieces / leadership / "
                                + "experience below level 5 and keeper below level 3. Narrow the "
                                + "query rather than paging: Hattrick reports no exact total once "
                                + "more than 100 players match. Searching needs the user to unlock "
                                + "market search in HO first, and is capped per session.",
                        searchSchema(),
                        args -> search(queryFrom(args))),
                new McpTool("find_comparables",
                        "Find players on the market comparable to one of your own, to value him "
                                + "against live asking prices and bids. Derives the query from his "
                                + "age, his two highest positional skills and his specialty, and "
                                + "echoes the query it built so you can refine it.",
                        McpTool.schema(McpTool.properties(
                                        "playerId", McpTool.prop("integer", "the player to value"),
                                        "ageTolerance", McpTool.prop("integer", "age window in years, default 1"),
                                        "skillTolerance", McpTool.prop("integer", "skill window in levels, default 1")),
                                "playerId"),
                        TransferTools::findComparables)
        );
    }

    private static JsonObject searchSchema() {
        final var properties = McpTool.properties(
                "ageMin", McpTool.prop("integer", "minimum age in years"),
                "ageMax", McpTool.prop("integer", "maximum age in years"),
                "skillType1", McpTool.prop("string", "first skill name"),
                "minSkillValue1", McpTool.prop("integer", "minimum level of the first skill"),
                "maxSkillValue1", McpTool.prop("integer", "maximum level of the first skill"),
                "skillType2", McpTool.prop("string", "second skill name"),
                "minSkillValue2", McpTool.prop("integer", "minimum level of the second skill"),
                "maxSkillValue2", McpTool.prop("integer", "maximum level of the second skill"),
                "skillType3", McpTool.prop("string", "third skill name"),
                "minSkillValue3", McpTool.prop("integer", "minimum level of the third skill"),
                "maxSkillValue3", McpTool.prop("integer", "maximum level of the third skill"),
                "skillType4", McpTool.prop("string", "fourth skill name"),
                "minSkillValue4", McpTool.prop("integer", "minimum level of the fourth skill"),
                "maxSkillValue4", McpTool.prop("integer", "maximum level of the fourth skill"),
                "specialty", McpTool.prop("integer", "specialty id, 0 none"),
                "nativeCountryId", McpTool.prop("integer", "native country id"),
                "tsiMin", McpTool.prop("integer", "minimum TSI"),
                "tsiMax", McpTool.prop("integer", "maximum TSI"),
                "priceMin", McpTool.prop("integer", "minimum asking price"),
                "priceMax", McpTool.prop("integer", "maximum asking price"),
                "pageSize", McpTool.prop("integer", "results per page, default 50, maximum 100"));
        return McpTool.schema(properties,
                "ageMin", "ageMax", "skillType1", "minSkillValue1", "maxSkillValue1");
    }

    /** The query the client asked for, with the page size defaulted and capped. */
    static TransferSearchQuery queryFrom(JsonObject args) {
        final var builder = TransferSearchQuery.builder()
                .ageMin(McpJson.reqInt(args, "ageMin"))
                .ageMax(McpJson.reqInt(args, "ageMax"))
                .skillType1(skill(args, "skillType1"))
                .minSkillValue1(McpJson.reqInt(args, "minSkillValue1"))
                .maxSkillValue1(McpJson.reqInt(args, "maxSkillValue1"))
                .skillType2(optionalSkill(args, "skillType2"))
                .minSkillValue2(McpJson.optInteger(args, "minSkillValue2"))
                .maxSkillValue2(McpJson.optInteger(args, "maxSkillValue2"))
                .skillType3(optionalSkill(args, "skillType3"))
                .minSkillValue3(McpJson.optInteger(args, "minSkillValue3"))
                .maxSkillValue3(McpJson.optInteger(args, "maxSkillValue3"))
                .skillType4(optionalSkill(args, "skillType4"))
                .minSkillValue4(McpJson.optInteger(args, "minSkillValue4"))
                .maxSkillValue4(McpJson.optInteger(args, "maxSkillValue4"))
                .specialty(McpJson.optInteger(args, "specialty"))
                .nativeCountryId(McpJson.optInteger(args, "nativeCountryId"))
                .tsiMin(McpJson.optInteger(args, "tsiMin"))
                .tsiMax(McpJson.optInteger(args, "tsiMax"))
                .priceMin(McpJson.optInteger(args, "priceMin"))
                .priceMax(McpJson.optInteger(args, "priceMax"))
                // Paging is not exposed: ItemCount comes back as -1 once more than 100 players
                // match, so a second page buys little and would eat the per session cap.
                .pageSize(Math.min(McpJson.optInt(args, "pageSize", DEFAULT_PAGE_SIZE), MAX_PAGE_SIZE));
        return builder.build();
    }

    private static TransferSearchQuery.Skill skill(JsonObject args, String name) {
        final var value = McpJson.optString(args, name, null);
        if (value == null) {
            throw new IllegalArgumentException("Missing required argument: " + name);
        }
        return parseSkill(value);
    }

    private static TransferSearchQuery.Skill optionalSkill(JsonObject args, String name) {
        final var value = McpJson.optString(args, name, null);
        return value == null ? null : parseSkill(value);
    }

    private static TransferSearchQuery.Skill parseSkill(String value) {
        try {
            return TransferSearchQuery.Skill.valueOf(value.toUpperCase(java.util.Locale.ENGLISH));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown skill '" + value + "'. Use one of "
                    + Arrays.toString(TransferSearchQuery.Skill.values()));
        }
    }

    private static JsonObject search(TransferSearchQuery query) throws McpToolException {
        final XMLTransferSearchParser.Page page;
        try {
            page = TransferSearchService.instance().searchForAgent(query);
        } catch (TransferSearchService.SearchFailed e) {
            if (e.errorKey() != null) {
                // A query Hattrick would reject is an invalid parameter, JSON-RPC -32602, and
                // McpHttpServer maps IllegalArgumentException onto that.
                throw new IllegalArgumentException(e.errorKey() + ": " + e.getMessage());
            }
            // Locked market or an exhausted cap is an expected refusal the model must be able to
            // read and act on, so it comes back as an isError tool result instead.
            throw new McpToolException(e.getMessage());
        }

        final var observedAt = HODateTime.now();
        final var hits = new JsonArray();
        for (final var hit : page.hits()) {
            DBManager.instance().storeTransferListing(TransferListing.fromHit(hit, observedAt));
            hits.add(McpJson.hit(hit));
        }

        final var json = new JsonObject();
        // -1 means "more than 100", Hattrick's analogy to "many"
        json.addProperty("itemCount", page.itemCount());
        json.addProperty("pageSize", page.pageSize());
        json.add("hits", hits);
        return json;
    }

    private static JsonObject findComparables(JsonObject args) throws McpToolException {
        final var model = McpJson.model();
        final var playerId = McpJson.reqInt(args, "playerId");
        final var player = model.getCurrentPlayer(playerId);
        if (player == null) {
            throw new McpToolException("No player with id " + playerId + " is in the squad. "
                    + "Call get_squad for the ids HO knows about.");
        }

        final var query = comparableQuery(player,
                McpJson.optInt(args, "ageTolerance", 1),
                McpJson.optInt(args, "skillTolerance", 1));

        final var result = search(query);
        result.addProperty("echo", query.toQueryString());
        return result;
    }

    /**
     * The market query that describes a player: his age window, his two highest positional skills
     * and his specialty.
     * <p>
     * Clamped so that Hattrick accepts it - a skill window may span at most 4 levels, and keeper /
     * set pieces carry a minimum level.
     */
    static TransferSearchQuery comparableQuery(Player player, int ageTolerance, int skillTolerance) {
        final var ranked = POSITIONAL_SKILLS.stream()
                .sorted(Comparator.comparingInt(
                        (TransferSearchQuery.Skill s) -> player.getValue4Skill(s.toPlayerSkill())).reversed())
                .toList();
        final var first = ranked.get(0);
        final var second = ranked.get(1);

        final var tolerance = Math.min(Math.max(skillTolerance, 0), 2);   // a span of at most 4 levels
        return TransferSearchQuery.builder()
                .ageMin(Math.max(player.getAge() - ageTolerance, 17))
                .ageMax(player.getAge() + ageTolerance)
                .skillType1(first)
                .minSkillValue1(minLevel(first, player, tolerance))
                .maxSkillValue1(maxLevel(first, player, tolerance))
                .skillType2(second)
                .minSkillValue2(minLevel(second, player, tolerance))
                .maxSkillValue2(maxLevel(second, player, tolerance))
                .specialty(player.getSpecialty() > 0 ? player.getSpecialty() : null)
                .pageSize(DEFAULT_PAGE_SIZE)
                .build();
    }

    /** The window's lower bound, lifted to whatever minimum Hattrick enforces for the skill. */
    private static int minLevel(TransferSearchQuery.Skill skill, Player player, int tolerance) {
        final var minimum = switch (skill) {
            case SETPIECES, LEADERSHIP, EXPERIENCE -> 5;
            case KEEPER -> 3;
            default -> 0;
        };
        return Math.max(player.getValue4Skill(skill.toPlayerSkill()) - tolerance, minimum);
    }

    /** The window's upper bound, kept within 4 levels of the lower one. */
    private static int maxLevel(TransferSearchQuery.Skill skill, Player player, int tolerance) {
        final var value = player.getValue4Skill(skill.toPlayerSkill());
        return Math.min(value + tolerance, minLevel(skill, player, tolerance) + 4);
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew test --tests "core.mcp.TransferToolsTest"`
Expected: PASS (6 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/core/mcp/TransferTools.java \
        src/main/java/core/net/TransferSearchQuery.java \
        src/test/java/core/mcp/TransferToolsTest.java
git commit -m "Add MCP transfer search and comparables tools with corpus snapshotting"
```

---

### Task 9: Options panel, lifecycle, and the resolve-expired-listings pass

Wires the ten tools into the server, gives the user the switch, the unlock button and the resolve
button, and starts/stops the server with HO.

**Files:**
- Create: `src/main/java/core/option/McpServerPanel.java`
- Modify: `src/main/java/core/option/OptionenDialog.java` (register the tab next to
  `ReleaseChannelPanel`, around line 100)
- Modify: `src/main/java/core/HO.java:229-236` (start on launch when enabled)
- Modify: `src/main/java/core/gui/HOMainFrame.java:271-300` (stop on shutdown)
- Modify: `src/main/resources/language/English.properties` (append the new `ls.options.mcp.*` keys)
- Test: `src/test/java/core/mcp/McpServerToolsTest.java`

**Interfaces:**
- Consumes: `McpHttpServer`, `TeamTools#tools`, `LineupTools#tools`, `TransferTools#tools`,
  `TransferSearchService#unlockMarketSearch`, `#marketUnlockSecondsRemaining`,
  `DBManager#loadUnresolvedTransferListings`, `#storeTransferListingSalePrice`,
  `core.net.Connector#getTransfersForPlayer(int)`, `module.transfer.XMLParser`
- Produces:
  - `McpHttpServer#registerAllTools()` — registers all ten tools once
  - `McpServerPanel` — the options tab

- [ ] **Step 1: Write the failing test**

Create `src/test/java/core/mcp/McpServerToolsTest.java`:

```java
package core.mcp;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class McpServerToolsTest {

    @Test
    void theServerAdvertisesAllTenTools() {
        final var server = new McpHttpServer();
        server.registerAllTools();

        final var tools = server.handle(JsonParser.parseString("""
                        {"jsonrpc":"2.0","id":1,"method":"tools/list"}""").getAsJsonObject())
                .getAsJsonObject("result").getAsJsonArray("tools");

        assertThat(tools).hasSize(10);
        assertThat(tools).extracting(t -> t.getAsJsonObject().get("name").getAsString())
                .containsExactlyInAnyOrder("get_team", "get_squad", "get_player",
                        "get_upcoming_matches", "search_transfers", "find_comparables",
                        "get_lineup", "evaluate_lineup", "optimize_lineup", "apply_lineup");
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew test --tests "core.mcp.McpServerToolsTest"`
Expected: FAIL — `registerAllTools` does not exist.

- [ ] **Step 3: Add `registerAllTools`**

In `src/main/java/core/mcp/McpHttpServer.java`, add:

```java
    /** Register the whole tool surface. Idempotent - the map is keyed on the tool name. */
    public void registerAllTools() {
        registerAll(TeamTools.tools());
        registerAll(TransferTools.tools());
        registerAll(LineupTools.tools());
    }
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew test --tests "core.mcp.McpServerToolsTest"`
Expected: PASS.

- [ ] **Step 5: Add the language keys**

Append to `src/main/resources/language/English.properties`:

```properties
ls.options.mcp.tabtitle=MCP server
ls.options.mcp.enabled=Enable the MCP server
ls.options.mcp.enabled.tt=Lets an AI assistant on this machine read your team and build lineups. Loopback only, nothing is sent to Hattrick.
ls.options.mcp.port=Port
ls.options.mcp.url=Client configuration URL
ls.options.mcp.searchcap=Transfer searches per session
ls.options.mcp.unlockminutes=Unlock market search for (minutes)
ls.options.mcp.unlock=Unlock market search
ls.options.mcp.unlock.tt=Hattrick only allows the transfer market to be searched on an explicit user request. Press this before asking your assistant about the market.
ls.options.mcp.unlock.locked=Market search is locked
ls.options.mcp.unlock.remaining=Market search unlocked for %minutes% more minutes
ls.options.mcp.resolve=Resolve expired listings
ls.options.mcp.resolve.tt=Look up what expired listings in the price corpus actually sold for. One Hattrick call per listing.
ls.options.mcp.resolve.cap=Listings to resolve per run
ls.options.mcp.resolve.done=Resolved %count% listings
ls.options.mcp.status.stopped=Not running
ls.options.mcp.status.running=Running
ls.options.mcp.status.failed=Could not start: %error%
```

These are English only. A maintainer must add them to POEditor before release, or every other
language shows `!key!`.

- [ ] **Step 6: Write the options panel**

Create `src/main/java/core/option/McpServerPanel.java`:

```java
package core.option;

import core.db.DBManager;
import core.gui.comp.panel.ImagePanel;
import core.mcp.McpHttpServer;
import core.model.TranslationFacility;
import core.model.UserParameter;
import core.net.TransferSearchService;
import core.util.HODateTime;
import core.util.HOLogger;

import javax.swing.*;
import java.awt.*;

/**
 * The MCP server switch, and the two buttons the design deliberately keeps in HO's own UI rather
 * than exposing as tools: unlocking market search, and resolving expired listings. Both start CHPP
 * traffic, so both stay a human action.
 */
public final class McpServerPanel extends ImagePanel {

    private final JCheckBox enabled = new JCheckBox(TranslationFacility.tr("ls.options.mcp.enabled"));
    private final JTextField port = new JTextField(6);
    private final JTextField searchCap = new JTextField(6);
    private final JTextField unlockMinutes = new JTextField(6);
    private final JTextField resolveCap = new JTextField(6);
    private final JLabel status = new JLabel();
    private final JLabel unlockStatus = new JLabel();
    private final JTextField url = new JTextField(34);

    public McpServerPanel() {
        initComponents();
    }

    private void initComponents() {
        setLayout(new GridBagLayout());
        final var c = new GridBagConstraints();
        c.anchor = GridBagConstraints.NORTHWEST;
        c.insets = new Insets(4, 6, 4, 6);
        c.gridx = 0;
        c.gridy = 0;
        c.gridwidth = 2;

        enabled.setOpaque(false);
        enabled.setToolTipText(TranslationFacility.tr("ls.options.mcp.enabled.tt"));
        enabled.setSelected(UserParameter.instance().mcpServerEnabled);
        enabled.addItemListener(e -> toggleServer());
        add(enabled, c);

        c.gridwidth = 1;
        addField(c, ++c.gridy, "ls.options.mcp.port", port,
                String.valueOf(UserParameter.instance().mcpServerPort));
        addField(c, ++c.gridy, "ls.options.mcp.searchcap", searchCap,
                String.valueOf(UserParameter.instance().mcpSearchesPerSession));
        addField(c, ++c.gridy, "ls.options.mcp.unlockminutes", unlockMinutes,
                String.valueOf(UserParameter.instance().mcpMarketUnlockMinutes));

        c.gridx = 0;
        c.gridy++;
        add(status, c);

        c.gridy++;
        final var unlock = new JButton(TranslationFacility.tr("ls.options.mcp.unlock"));
        unlock.setToolTipText(TranslationFacility.tr("ls.options.mcp.unlock.tt"));
        unlock.addActionListener(e -> {
            saveFields();
            TransferSearchService.instance().unlockMarketSearch();
            refreshUnlockStatus();
        });
        add(unlock, c);
        c.gridx = 1;
        add(unlockStatus, c);

        c.gridx = 0;
        c.gridy++;
        final var resolve = new JButton(TranslationFacility.tr("ls.options.mcp.resolve"));
        resolve.setToolTipText(TranslationFacility.tr("ls.options.mcp.resolve.tt"));
        resolve.addActionListener(e -> resolveExpiredListings());
        add(resolve, c);
        c.gridx = 1;
        addField(c, c.gridy, "ls.options.mcp.resolve.cap", resolveCap, "20");

        c.gridx = 0;
        c.gridy++;
        add(new JLabel(TranslationFacility.tr("ls.options.mcp.url")), c);
        c.gridx = 1;
        url.setEditable(false);
        add(url, c);

        c.gridx = 0;
        c.gridy++;
        c.weightx = 1;
        c.weighty = 1;
        add(new JLabel(""), c);

        refreshStatus();
        refreshUnlockStatus();
    }

    private void addField(GridBagConstraints c, int row, String labelKey, JTextField field, String value) {
        c.gridx = 0;
        c.gridy = row;
        add(new JLabel(TranslationFacility.tr(labelKey)), c);
        c.gridx = 1;
        field.setText(value);
        field.addActionListener(e -> saveFields());
        field.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override
            public void focusLost(java.awt.event.FocusEvent e) {
                saveFields();
            }
        });
        add(field, c);
        c.gridx = 0;
    }

    /**
     * Written to both parameter objects on purpose. {@link UserParameter#temp()} returns a separate
     * copy that the options dialog persists on OK; {@link UserParameter#instance()} is what the
     * running server and {@link TransferSearchService} actually read. This panel takes effect the
     * moment a field is edited - the server starts on the checkbox, not on OK - so writing only
     * temp() would leave the cap and the unlock duration inert until the dialog was committed.
     */
    private void saveFields() {
        final var portValue = parse(port, 8765);
        final var capValue = parse(searchCap, 20);
        final var minutesValue = parse(unlockMinutes, 30);
        for (final var p : new UserParameter[]{UserParameter.instance(), UserParameter.temp()}) {
            p.mcpServerPort = portValue;
            p.mcpSearchesPerSession = capValue;
            p.mcpMarketUnlockMinutes = minutesValue;
        }
    }

    /** See {@link #saveFields()} - the enable flag has to reach both objects too. */
    private static void storeEnabled(boolean value) {
        UserParameter.instance().mcpServerEnabled = value;
        UserParameter.temp().mcpServerEnabled = value;
    }

    private static int parse(JTextField field, int defaultValue) {
        try {
            final var value = Integer.parseInt(field.getText().trim());
            return value > 0 ? value : defaultValue;
        } catch (NumberFormatException e) {
            field.setText(String.valueOf(defaultValue));
            return defaultValue;
        }
    }

    private void toggleServer() {
        saveFields();
        storeEnabled(enabled.isSelected());
        if (enabled.isSelected()) {
            try {
                McpHttpServer.instance().registerAllTools();
                McpHttpServer.instance().start(UserParameter.instance().mcpServerPort);
            } catch (Exception e) {
                // A port already in use is reported here, not swallowed.
                HOLogger.instance().error(getClass(), e);
                status.setText(TranslationFacility.tr("ls.options.mcp.status.failed")
                        .replace("%error%", String.valueOf(e.getMessage())));
                enabled.setSelected(false);
                storeEnabled(false);
                return;
            }
        } else {
            McpHttpServer.instance().stop();
        }
        refreshStatus();
    }

    private void refreshStatus() {
        final var running = McpHttpServer.instance().isRunning();
        status.setText(TranslationFacility.tr(running
                ? "ls.options.mcp.status.running" : "ls.options.mcp.status.stopped"));
        url.setText(running ? "http://127.0.0.1:" + McpHttpServer.instance().port() + "/mcp" : "");
    }

    private void refreshUnlockStatus() {
        final var seconds = TransferSearchService.instance().marketUnlockSecondsRemaining();
        unlockStatus.setText(seconds == 0
                ? TranslationFacility.tr("ls.options.mcp.unlock.locked")
                : TranslationFacility.tr("ls.options.mcp.unlock.remaining")
                        .replace("%minutes%", String.valueOf((seconds + 59) / 60)));
    }

    /**
     * Fill in what expired listings actually sold for.
     * <p>
     * One CHPP transfersPlayer call per listing, capped per run, only ever from this button - an
     * agent must not be able to start a batch of CHPP calls. transfersPlayer is not covered by the
     * market scanning restriction, but it is still throttled and still manual.
     */
    private void resolveExpiredListings() {
        final var cap = parse(resolveCap, 20);
        final var now = HODateTime.now();
        var resolved = 0;
        for (final var listing : DBManager.instance().loadUnresolvedTransferListings(cap)) {
            if (listing.getDeadline() == null || listing.getDeadline().compareTo(now) >= 0) {
                continue;                      // not expired yet, nothing to resolve
            }
            try {
                // module.transfer.XMLParser makes the transfersPlayer call itself
                final var transfers = module.transfer.XMLParser.getAllPlayerTransfers(listing.getPlayerId());
                if (transfers.isEmpty()) {
                    continue;
                }
                final var sale = transfers.get(transfers.size() - 1);   // the most recent one
                DBManager.instance().storeTransferListingSalePrice(listing.getPlayerId(),
                        listing.getObservedAt(), sale.getPrice().getSwedishKrona().longValue());
                resolved++;
            } catch (Exception e) {
                HOLogger.instance().error(getClass(), e);
            }
        }
        status.setText(TranslationFacility.tr("ls.options.mcp.resolve.done")
                .replace("%count%", String.valueOf(resolved)));
    }
}
```

- [ ] **Step 7: Register the panel in the options dialog**

In `src/main/java/core/option/OptionenDialog.java`, next to the release channel tab (around line
101), add:

```java
		tabbedPane.addTab(TranslationFacility.tr("ls.options.mcp.tabtitle"),
				new JScrollPane(new McpServerPanel()));
```

- [ ] **Step 8: Start on launch and stop on shutdown**

In `src/main/java/core/HO.java`, inside the `SwingUtilities.invokeLater` block at the end of `main`,
after `HOMainFrame.instance().setVisible(true);`:

```java
			if (UserParameter.instance().mcpServerEnabled) {
				try {
					core.mcp.McpHttpServer.instance().registerAllTools();
					core.mcp.McpHttpServer.instance().start(UserParameter.instance().mcpServerPort);
				} catch (Exception e) {
					HOLogger.instance().error(HO.class, e);
				}
			}
```

In `src/main/java/core/gui/HOMainFrame.java`, in `shutdown()`, before
`DBManager.instance().disconnect();`:

```java
			core.mcp.McpHttpServer.instance().stop();
			HOLogger.instance().debug(getClass(), "MCP server stopped");
```

- [ ] **Step 9: Run the full suite**

Run: `./gradlew test`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 10: Commit**

```bash
git add src/main/java/core/option/McpServerPanel.java \
        src/main/java/core/option/OptionenDialog.java \
        src/main/java/core/mcp/McpHttpServer.java \
        src/main/java/core/HO.java \
        src/main/java/core/gui/HOMainFrame.java \
        src/main/resources/language/English.properties \
        src/test/java/core/mcp/McpServerToolsTest.java
git commit -m "Wire the MCP server into HO's options, startup and shutdown"
```

---

### Task 10: End-to-end check against a real client

The unit suite cannot exercise a loaded `HOModel`. This task is the manual verification that the
whole thing works, plus the documentation update `AGENTS.md` requires.

**Files:**
- Modify: `docs/current.md`

- [ ] **Step 1: Start HO and enable the server**

Run: `./gradlew run`

In HO: Options → MCP server → tick "Enable the MCP server". The status line must read "Running" and
the URL field must show `http://127.0.0.1:8765/mcp`.

- [ ] **Step 2: Verify the handshake and the tool list from the shell**

```bash
curl -s -X POST http://127.0.0.1:8765/mcp -H 'Content-Type: application/json' \
  -d '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18","capabilities":{}}}'
curl -s -X POST http://127.0.0.1:8765/mcp -H 'Content-Type: application/json' \
  -d '{"jsonrpc":"2.0","id":2,"method":"tools/list"}' | head -c 400
```

Expected: a `result` with `serverInfo.name` = `ho`, then ten tool names.

- [ ] **Step 3: Verify the `Origin` and `Host` guards on the live server**

Java's `HttpClient` refuses to set a custom `Host`, so the unit suite cannot cover the second guard.
This is where it gets checked.

```bash
# Origin present -> rejected
curl -s -o /dev/null -w '%{http_code}\n' -X POST http://127.0.0.1:8765/mcp \
  -H 'Content-Type: application/json' -H 'Origin: http://evil.example' \
  -d '{"jsonrpc":"2.0","id":1,"method":"tools/list"}'

# rebound name in Host -> rejected
curl -s -o /dev/null -w '%{http_code}\n' -X POST http://127.0.0.1:8765/mcp \
  -H 'Content-Type: application/json' -H 'Host: evil.example:8765' \
  -d '{"jsonrpc":"2.0","id":1,"method":"tools/list"}'

# plain loopback call -> still works
curl -s -o /dev/null -w '%{http_code}\n' -X POST http://localhost:8765/mcp \
  -H 'Content-Type: application/json' \
  -d '{"jsonrpc":"2.0","id":1,"method":"tools/list"}'
```

Expected: `403`, `403`, `200`.

- [ ] **Step 4: Verify the read and lineup tools against real data**

```bash
call() { curl -s -X POST http://127.0.0.1:8765/mcp -H 'Content-Type: application/json' \
  -d "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\",\"params\":{\"name\":\"$1\",\"arguments\":$2}}"; }

call get_team '{}'
call get_squad '{}' | head -c 600
call get_lineup '{}'
```

Expected: real team data; `get_squad` players carry a `positionRatings` map with fourteen entries,
keyed 100 through 113; `get_lineup` returns seven sector ratings and a formation like `4-4-2`.

- [ ] **Step 5: Verify the rating cache isolation on live data**

Note the `midfield` value from `call get_lineup '{}'`. Then score a deliberately different scratch
lineup and read the live one again:

```bash
call evaluate_lineup '{"positions":[{"roleId":100,"playerId":<any squad id>}]}'
call get_lineup '{}'
```

Expected: the second `get_lineup` reports the **same** `midfield` value as the first. A changed value
means the scratch lineup poisoned the live cache and Task 5's private-model rule was not applied
somewhere.

Then do the same around `optimize_lineup`, which Task 7 flags as running through the shared model:

```bash
call optimize_lineup '{"sectorsStrengthPriority":0}'
call get_lineup '{}'
```

Expected: `get_lineup` still reports the lineup and ratings it did before, unchanged. If it does not,
record the actual behaviour in `docs/current.md` (Step 9) — do **not** try to patch
`LineupAssistant` here. The point of this check is that the limitation is known and written down,
not that it is absent.

- [ ] **Step 6: Verify the transfer-search gate**

```bash
call search_transfers '{"ageMin":20,"ageMax":22,"skillType1":"PLAYMAKING","minSkillValue1":7,"maxSkillValue1":9}'
```

Expected: `isError: true` with the "market search is locked" text. Then press "Unlock market search"
in the options panel and repeat.

Expected: real hits. Confirm the corpus was written:

```sql
-- in HO's DB browser, or via the HSQLDB shell against db/database
SELECT COUNT(*) FROM TRANSFERLISTING;
```

Expected: one row per hit.

- [ ] **Step 7: Verify `apply_lineup` writes through to the UI**

```bash
call apply_lineup '{"positions":[{"roleId":100,"playerId":<a keeper id>}],"tactic":0,"attitude":0}'
```

Expected: HO's Lineup tab shows that keeper. Quit HO and restart: the lineup persisted.

- [ ] **Step 8: Verify the client config works in Claude Code**

Add to the MCP client config:

```json
{ "mcpServers": { "ho": { "type": "http", "url": "http://127.0.0.1:8765/mcp" } } }
```

Expected: the client lists ten `ho` tools and `get_team` answers.

- [ ] **Step 9: Update the developer notes**

Add a section to `docs/current.md` covering:

- The MCP server ships off by default and is enabled in Options → MCP server. It binds `127.0.0.1`
  only and rejects any request carrying an `Origin` header or a non-loopback `Host`. **There is no
  authentication beyond that** — while it is on, any process running as this user can read the squad
  and write the lineup. That is the reason the switch is off by default.
- The transfer market needs the unlock button pressed first, because CHPP forbids automated
  scanning; the per-session cap bounds both callers.
- Scratch lineups must always be scored on a private `RatingPredictionModel`, because
  `LineupRatingCache` is keyed on `Lineup.ratingRevision` alone. **`optimize_lineup` is the known
  exception**: `Lineup.optimizeLineup` → `LineupAssistant` fetches the shared model itself, so that
  one tool does touch HO's own rating state. Fixing it means rewriting `LineupAssistant`.
- The HTTP executor is deliberately single threaded: handlers call `DBManager`, and
  `ConnectionManager` shares one JDBC `Statement` across all tables.
- `McpServerPanel` writes both `UserParameter.instance()` and `UserParameter.temp()`, because it
  takes effect immediately rather than on the options dialog's OK.
- The new `ls.options.mcp.*` keys are English-only until a maintainer adds them to POEditor.

- [ ] **Step 10: Commit**

```bash
git add docs/current.md
git commit -m "Document the MCP server in the developer notes"
```
