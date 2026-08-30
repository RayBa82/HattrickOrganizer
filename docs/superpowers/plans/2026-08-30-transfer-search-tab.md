# Transfer Search Tab Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a "Transfer search" tab to the Transfers module that queries the CHPP `transfersearch`
file and shows the hits in a transfer-scout table, with a button that copies a hit into the scout list.

**Architecture:** The tab is a plain Swing panel: a small query form builds a `TransferSearchQuery`,
`OnlineWorker.searchTransfers` fetches and parses it (wait cursor + error dialog, like every other
download), `TransferSearchConverter` maps each `XMLTransferSearchParser.Hit` to a `ScoutEintrag`, and
those entries are handed to a *second instance* of `TransferScoutingTableModel` shown in a
`FixedColumnsTable`. Search results are therefore never written to the persisted scout list — the user
copies the ones they want with an explicit button.

**Tech Stack:** Java 17, Swing, JUnit 5 + AssertJ, Gradle 8.13 wrapper. No new dependencies.

**Spec:** this document (design decisions below) + `docs/chpp_files.md` (`transfersearch`, version 1.1)
+ the javadoc already written on `core.net.TransferSearchQuery` and `core.file.xml.XMLTransferSearchParser`.

## Design decisions

1. **`Hit` is mapped straight to `ScoutEintrag`, not to `module.transfer.scout.Player`.** The scout
   table renders `ScoutEintrag`; `scout.Player` is a dead-end DTO that only `PlayerConverter` produces
   and `TransferEingabePanel.copyPaste()` consumes into form fields. Routing through it would need a
   second `Player` → `ScoutEintrag` hop that does not exist. `ScoutEintrag.getPlayer()` already derives
   the `core.model.player.Player` that the table's position-rating columns need.
2. **The results table gets its own `ColumnModelId` (`TRANSFERSEARCH`).** `TransferScoutingTableModel`
   is a singleton in `UserColumnController`; a second `FixedColumnsTable` over that same instance would
   mirror the scout tab's rows. A second instance with the *same* id would share the divider-location
   config key (`TableDividerLocation_14`) and, if `storeUserSettings()` were ever called on it, would
   overwrite the scout tab's saved column layout via `DBManager.saveHOColumnModel(this)`.
   `YouthPlayerOverviewTableModel(ColumnModelId)` is the existing precedent for passing an id in.
3. **Search runs only on an explicit button click.** CHPP forbids scanning the transfer market
   automatically — no timer, no refresh hook, no `RefreshManager` registration for this panel.
4. **One page of 50 results, no paging UI.** `ItemCount` is `-1` whenever more than 100 players match,
   so exact counts are not available anyway. Paging is a later addition if users ask.
5. **Query validation happens before the call.** Hattrick answers an invalid combination with an error
   file, not an empty result, so the documented server rules (skill span ≤ 4 levels, set pieces /
   leadership / experience from level 5, keeper from level 3) are checked client-side first.
6. **Form scope is age range, one skill with a range, speciality and max price.** Skills 2–4, TSI and
   country filters are supported by `TransferSearchQuery` already and can be added to the form later.
7. **The max price field is read with `Helper.getNumberFormat`, not with `AmountOfMoney.parse`.**
   `parse` takes the typed number for swedish krona without applying the exchange rate, and it
   returns an amount of 0 rather than null for an empty field as long as its currency formatter has
   not been initialised — either one silently limits the search. The field therefore holds the
   user's currency and is converted with `AmountOfMoney.getExchangeRate()`.

## Global Constraints

- Java 17 source/target. Do not raise the language level. No new dependencies.
- All UI strings go through `TranslationFacility.tr(key)`.
- New translation keys are added to `src/main/resources/language/English.properties` **only**.
  Translations are owned by POEditor and `poeditorPull` overwrites the language files on every build
  that has `POEDITOR_APIKEY` set; a maintainer has to add the new keys to the POEditor project before
  release, otherwise the UI shows `!key!`. Never hand-edit the other language files.
- Singleton style (`X.instance()`), no dependency injection, no new abstractions.
- No DB schema change, so **no** `DBManager.DBVersion` bump and no `DBUpdater` case.
- Tests are JUnit 5 + AssertJ unit tests over pure logic. There is no harness for Swing or for live
  CHPP calls — panels and `OnlineWorker` are verified by compiling and by the manual run in Task 6.
- `compileJava.dependsOn(clean)`: every Gradle invocation is a full clean rebuild, expect ~10s+ per run.
  `poeditorPull` printing `{"response":{"status":"fail","code":"4011",...}}` locally is expected, not a failure.
- Keep the legacy German field names in `ScoutEintrag` (`setSpielaufbau`, `setTorschuss`, …); renaming
  ripples into DB column names.

---

### Task 1: Client-side validation of the search query

**Files:**
- Modify: `src/main/java/core/net/TransferSearchQuery.java`
- Test: `src/test/java/core/net/TransferSearchQueryTest.java`

**Interfaces:**
- Consumes: nothing.
- Produces: `public String validationErrorKey()` on `TransferSearchQuery` — returns `null` when the
  query is acceptable, otherwise the translation key of the message to show. Keys used:
  `"ls.transfersearch.error.agerange"`, `"ls.transfersearch.error.skillrange"`,
  `"ls.transfersearch.error.minlevel"`.

- [ ] **Step 1: Write the failing tests**

Append to `src/test/java/core/net/TransferSearchQueryTest.java`:

```java
    private static TransferSearchQuery.TransferSearchQueryBuilder validQuery() {
        return TransferSearchQuery.builder()
                .ageMin(20)
                .ageMax(22)
                .skillType1(TransferSearchQuery.Skill.PLAYMAKING)
                .minSkillValue1(7)
                .maxSkillValue1(9);
    }

    @Test
    void validationErrorKey_acceptsAValidQuery() {
        assertThat(validQuery().build().validationErrorKey()).isNull();
    }

    @Test
    void validationErrorKey_rejectsAnInvertedAgeRange() {
        assertThat(validQuery().ageMin(25).ageMax(20).build().validationErrorKey())
                .isEqualTo("ls.transfersearch.error.agerange");
    }

    @Test
    void validationErrorKey_rejectsASkillRangeOfMoreThanFourLevels() {
        assertThat(validQuery().minSkillValue1(5).maxSkillValue1(10).build().validationErrorKey())
                .isEqualTo("ls.transfersearch.error.skillrange");
    }

    @Test
    void validationErrorKey_rejectsAnInvertedSkillRange() {
        assertThat(validQuery().minSkillValue1(9).maxSkillValue1(7).build().validationErrorKey())
                .isEqualTo("ls.transfersearch.error.skillrange");
    }

    @Test
    void validationErrorKey_rejectsSetPiecesBelowLevelFive() {
        assertThat(validQuery()
                .skillType1(TransferSearchQuery.Skill.SETPIECES)
                .minSkillValue1(4)
                .maxSkillValue1(8)
                .build()
                .validationErrorKey())
                .isEqualTo("ls.transfersearch.error.minlevel");
    }

    @Test
    void validationErrorKey_rejectsKeeperBelowLevelThree() {
        assertThat(validQuery()
                .skillType1(TransferSearchQuery.Skill.KEEPER)
                .minSkillValue1(2)
                .maxSkillValue1(6)
                .build()
                .validationErrorKey())
                .isEqualTo("ls.transfersearch.error.minlevel");
    }

    @Test
    void validationErrorKey_checksTheSecondarySkillsToo() {
        assertThat(validQuery()
                .skillType2(TransferSearchQuery.Skill.WINGER)
                .minSkillValue2(4)
                .maxSkillValue2(12)
                .build()
                .validationErrorKey())
                .isEqualTo("ls.transfersearch.error.skillrange");
    }
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew test --tests "core.net.TransferSearchQueryTest"`
Expected: compilation error — `cannot find symbol: method validationErrorKey()`.

- [ ] **Step 3: Implement the validation**

Add to `src/main/java/core/net/TransferSearchQuery.java`, inside the record body, above `toQueryString()`:

```java
    /**
     * Checks the rules Hattrick enforces server side, which answers a violation with an error file
     * rather than with an empty result.
     *
     * @return the translation key of the message to show, or null if the query is acceptable
     */
    public String validationErrorKey() {
        if (ageMin > ageMax) {
            return "ls.transfersearch.error.agerange";
        }
        final String[] checks = {
                check(skillType1, minSkillValue1, maxSkillValue1),
                check(skillType2, minSkillValue2, maxSkillValue2),
                check(skillType3, minSkillValue3, maxSkillValue3),
                check(skillType4, minSkillValue4, maxSkillValue4)
        };
        for (final var error : checks) {
            if (error != null) {
                return error;
            }
        }
        return null;
    }

    /**
     * The minimum level Hattrick accepts for a skill, 0 for the skills without a lower limit.
     */
    private static int minimumLevel(Skill skill) {
        return switch (skill) {
            case SETPIECES, LEADERSHIP, EXPERIENCE -> 5;
            case KEEPER -> 3;
            default -> 0;
        };
    }

    private static String check(Skill skill, Integer min, Integer max) {
        if (skill == null || min == null || max == null) {
            return null;
        }
        if (min > max || max - min > 4) {
            return "ls.transfersearch.error.skillrange";
        }
        if (min < minimumLevel(skill)) {
            return "ls.transfersearch.error.minlevel";
        }
        return null;
    }
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew test --tests "core.net.TransferSearchQueryTest"`
Expected: PASS, all tests of the class green.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/core/net/TransferSearchQuery.java src/test/java/core/net/TransferSearchQueryTest.java
git commit -m "Validate transfer search queries before calling CHPP"
```

---

### Task 2: Map a search hit to a scout entry

**Files:**
- Create: `src/main/java/module/transfer/search/TransferSearchConverter.java`
- Test: `src/test/java/module/transfer/search/TransferSearchConverterTest.java`

**Interfaces:**
- Consumes: `core.file.xml.XMLTransferSearchParser.Hit` and `.Details` (records, already implemented),
  `module.transfer.scout.ScoutEintrag`.
- Produces: `public static ScoutEintrag TransferSearchConverter.toScoutEintrag(Hit hit)`.

Notes for the implementer:
- `Hit.details()` is `null` once the deadline has passed — Hattrick then sends the market data only.
  The converter must still return a usable entry in that case.
- `Hit.askingPrice()` / `highestBid()` / `Details.salary()` are plain numbers; `ScoutEintrag` wants
  `core.util.AmountOfMoney`, which converts to the user's currency for display.
- `Details.injuryLevel()` is `-1` for a healthy player (and defaults to `-1` when the element is missing
  because a match is running).
- The transfer search does not report loyalty, motherclub or agreeability; those keep the
  `ScoutEintrag` defaults.

- [ ] **Step 1: Write the failing test**

Create `src/test/java/module/transfer/search/TransferSearchConverterTest.java`:

```java
package module.transfer.search;

import core.file.xml.XMLTransferSearchParser.Details;
import core.file.xml.XMLTransferSearchParser.Hit;
import core.model.TranslationFacility;
import core.model.Translator;
import core.util.HODateTime;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TransferSearchConverterTest {

    private static final HODateTime DEADLINE = HODateTime.fromHT("2026-09-02 20:15:00");

    /**
     * The notes are built from translated strings, and tests run against the NoTranslation bundle,
     * which is empty: every key would come back as <code>!key!</code> and the <code>%weeks%</code>
     * placeholder of the injury message would never be replaced.
     */
    @BeforeAll
    static void useEnglish() {
        TranslationFacility.setLanguage(Translator.LANGUAGE_DEFAULT);
    }

    private static Details details() {
        return new Details(24, 77, 44000L, 29000, 6, 4, 3, 2, 0, -1,
                8, 1, 12, 5, 9, 7, 6, 10);
    }

    private static Hit hit(Details details) {
        return new Hit(111222333, "Sven Persson", 3, 1500000L, DEADLINE, 1650000L,
                "Bidding Boys", 555444, "Selling Sam", 2, details);
    }

    @Test
    void toScoutEintrag_copiesTheMarketData() {
        final var entry = TransferSearchConverter.toScoutEintrag(hit(details()));

        assertThat(entry.getPlayerID()).isEqualTo(111222333);
        assertThat(entry.getName()).isEqualTo("Sven Persson");
        assertThat(entry.getNationality()).isEqualTo(3);
        assertThat(entry.getDeadline()).isEqualTo(DEADLINE.toDbTimestamp());
    }

    @Test
    void toScoutEintrag_copiesTheSkills() {
        final var entry = TransferSearchConverter.toScoutEintrag(hit(details()));

        assertThat(entry.getAlter()).isEqualTo(24);
        assertThat(entry.getAgeDays()).isEqualTo(77);
        assertThat(entry.getTSI()).isEqualTo(29000);
        assertThat(entry.getForm()).isEqualTo(6);
        assertThat(entry.getErfahrung()).isEqualTo(4);
        assertThat(entry.getLeadership()).isEqualTo(3);
        assertThat(entry.getSpeciality()).isEqualTo(2);
        assertThat(entry.getKondition()).isEqualTo(8);
        assertThat(entry.getTorwart()).isEqualTo(1);
        assertThat(entry.getSpielaufbau()).isEqualTo(12);
        assertThat(entry.getTorschuss()).isEqualTo(5);
        assertThat(entry.getPasspiel()).isEqualTo(9);
        assertThat(entry.getFluegelspiel()).isEqualTo(7);
        assertThat(entry.getVerteidigung()).isEqualTo(6);
        assertThat(entry.getStandards()).isEqualTo(10);
    }

    @Test
    void toScoutEintrag_leavesTheNotesEmptyForAHealthyPlayerWithoutCards() {
        assertThat(TransferSearchConverter.toScoutEintrag(hit(details())).getInfo()).isEmpty();
    }

    @Test
    void toScoutEintrag_notesInjuryAndCards() {
        final var injured = new Details(24, 77, 44000L, 29000, 6, 4, 3, 2, 2, 3,
                8, 1, 12, 5, 9, 7, 6, 10);

        final var info = TransferSearchConverter.toScoutEintrag(hit(injured)).getInfo();

        assertThat(info).contains("3").contains("2");
    }

    @Test
    void toScoutEintrag_survivesAnExpiredTransferWithoutDetails() {
        final var entry = TransferSearchConverter.toScoutEintrag(hit(null));

        assertThat(entry.getPlayerID()).isEqualTo(111222333);
        assertThat(entry.getAlter()).isEqualTo(17);
    }
}
```

Note on the last assertion: `ScoutEintrag` initialises `m_iAlter` to 17, so an entry without details
keeps that default — the test pins that nothing blows up rather than that 17 is meaningful.

`HODateTime.fromHT(String)` is the factory for Hattrick's `yyyy-MM-dd HH:mm:ss` format, the same one
the parser uses.

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew test --tests "module.transfer.search.TransferSearchConverterTest"`
Expected: compilation error — package `module.transfer.search` does not exist.

- [ ] **Step 3: Write the converter**

Create `src/main/java/module/transfer/search/TransferSearchConverter.java`:

```java
package module.transfer.search;

import core.file.xml.XMLTransferSearchParser.Details;
import core.file.xml.XMLTransferSearchParser.Hit;
import core.model.TranslationFacility;
import core.util.AmountOfMoney;
import module.transfer.scout.ScoutEintrag;

/**
 * Maps one hit of the CHPP <code>transfersearch</code> file to the scout entry that the transfer
 * scout table displays.
 */
public final class TransferSearchConverter {

    private TransferSearchConverter() {
    }

    /**
     * The scout entry of one search hit. Everything but the market data is missing once the deadline
     * has passed, the entry then keeps the defaults of a fresh {@link ScoutEintrag}.
     */
    public static ScoutEintrag toScoutEintrag(Hit hit) {
        final var entry = new ScoutEintrag();
        entry.setPlayerID(hit.playerId());
        entry.setName(hit.playerName());
        entry.setNationality(hit.nativeCountryId());
        entry.setPrice(new AmountOfMoney(Math.max(hit.askingPrice(), hit.highestBid())));
        if (hit.deadline() != null) {
            entry.setDeadline(hit.deadline().toDbTimestamp());
        }

        final var details = hit.details();
        if (details == null) {
            return entry;
        }
        entry.setAlter(details.age());
        entry.setAgeDays(details.ageDays());
        entry.setbaseWage(new AmountOfMoney(details.salary()));
        entry.setTSI(details.tsi());
        entry.setForm(details.form());
        entry.setErfahrung(details.experience());
        entry.setLeadership(details.leadership());
        entry.setSpeciality(details.specialty());
        entry.setKondition(details.stamina());
        entry.setTorwart(details.keeper());
        entry.setSpielaufbau(details.playmaker());
        entry.setTorschuss(details.scorer());
        entry.setPasspiel(details.passing());
        entry.setFluegelspiel(details.winger());
        entry.setVerteidigung(details.defender());
        entry.setStandards(details.setPieces());
        entry.setInfo(notes(details));
        return entry;
    }

    /**
     * Injury and cards, in the notes column of the scout entry. A healthy player has an injury level
     * of -1.
     */
    private static String notes(Details details) {
        final var notes = new StringBuilder();
        if (details.injuryLevel() > 0) {
            notes.append(TranslationFacility.tr("scout_injury")
                            .replaceAll("%weeks%", String.valueOf(details.injuryLevel())))
                    .append("\n");
        }
        if (details.cards() > 0) {
            notes.append(details.cards())
                    .append(" ")
                    .append(TranslationFacility.tr("GelbeKarten"))
                    .append("\n");
        }
        return notes.toString();
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew test --tests "module.transfer.search.TransferSearchConverterTest"`
Expected: PASS.

`AmountOfMoney` is safe to construct in a unit test — `core.util.HOCurrencyTests` does it today.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/module/transfer/search src/test/java/module/transfer/search
git commit -m "Map transfer search hits to scout entries"
```

---

### Task 3: A separate column model for the results table

**Files:**
- Modify: `src/main/java/core/gui/model/UserColumnController.java` (enum `ColumnModelId`, new field,
  new getter, `load()`)
- Modify: `src/main/java/module/transfer/scout/TransferScoutingTableModel.java` (constructor)
- Test: `src/test/java/module/transfer/scout/TransferScoutingTableModelTest.java`

**Interfaces:**
- Consumes: nothing from earlier tasks.
- Produces:
  - `UserColumnController.ColumnModelId.TRANSFERSEARCH` (value 23)
  - `public TransferScoutingTableModel(ColumnModelId id)` — the existing no-arg constructor delegates
    to it with `ColumnModelId.TRANSFERSCOUT`
  - `public TransferScoutingTableModel UserColumnController.getTransferSearchTableModel()`

- [ ] **Step 1: Write the failing test**

Create `src/test/java/module/transfer/scout/TransferScoutingTableModelTest.java`:

```java
package module.transfer.scout;

import core.gui.model.UserColumnController.ColumnModelId;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TransferScoutingTableModelTest {

    /**
     * The transfer search shows its hits in a second table of the same kind. Both tables must not
     * share the column model id: the settings are saved per id, so the search table would overwrite
     * the column layout of the scout table.
     */
    @Test
    void theSearchModelUsesItsOwnColumnModelId() {
        final var scout = new TransferScoutingTableModel();
        final var search = new TransferScoutingTableModel(ColumnModelId.TRANSFERSEARCH);

        assertThat(search.id).isNotEqualTo(scout.id);
        assertThat(search.getColumns()).hasSameSizeAs(scout.getColumns());
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew test --tests "module.transfer.scout.TransferScoutingTableModelTest"`
Expected: compilation error — `TRANSFERSEARCH` is not a member of `ColumnModelId`.

- [ ] **Step 3: Add the id, the constructor and the getter**

In `src/main/java/core/gui/model/UserColumnController.java`, extend the enum (note the semicolon moves):

```java
        HALL_OF_FAME(22),
		TRANSFERSEARCH(23);
```

Add the field next to `transferScoutingTableModel`:

```java
	private TransferScoutingTableModel transferSearchTableModel;
```

Add the getter next to `getTransferScoutingTableModel()`:

```java
	/**
	 * Column model of the transfer search results.
	 * The results are shown in a table of the same kind as the scout list, but with its own column
	 * settings, so that both tables can be configured independently.
	 */
	public TransferScoutingTableModel getTransferSearchTableModel(){
		if (transferSearchTableModel==null){
			transferSearchTableModel = new TransferScoutingTableModel(ColumnModelId.TRANSFERSEARCH);
		}
		return transferSearchTableModel;
	}
```

Add the load line in `load()`, directly after the scouting one:

```java
		dbManager.loadHOColumModel(getTransferSearchTableModel());
```

In `src/main/java/module/transfer/scout/TransferScoutingTableModel.java`, replace the constructor
declaration line

```java
    public TransferScoutingTableModel() {
        super(UserColumnController.ColumnModelId.TRANSFERSCOUT, "TransferScout");
```

with

```java
    public TransferScoutingTableModel() {
        this(UserColumnController.ColumnModelId.TRANSFERSCOUT);
    }

    public TransferScoutingTableModel(UserColumnController.ColumnModelId columnModelId) {
        super(columnModelId, "TransferScout");
```

The parameter has to be called `columnModelId`, not `id`: the constructor body continues with the
local `int id = 0;` that numbers the columns, and Java rejects a local with the same name as a
parameter. The rest of the body stays untouched.

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew test --tests "module.transfer.scout.TransferScoutingTableModelTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/core/gui/model/UserColumnController.java src/main/java/module/transfer/scout/TransferScoutingTableModel.java src/test/java/module/transfer/scout/TransferScoutingTableModelTest.java
git commit -m "Give the transfer search results their own column model"
```

---

### Task 4: Download entry point and the search panel

**Files:**
- Modify: `src/main/java/core/net/OnlineWorker.java`
- Create: `src/main/java/module/transfer/search/TransferSearchPanel.java`
- Test: `src/test/java/module/transfer/search/TransferSearchPanelTest.java`

**Interfaces:**
- Consumes: `TransferSearchQuery.validationErrorKey()` (Task 1),
  `TransferSearchConverter.toScoutEintrag(Hit)` (Task 2),
  `UserColumnController.getTransferSearchTableModel()` (Task 3),
  `Connector.getTransferSearch(TransferSearchQuery)` and `XMLTransferSearchParser.parse(String)`
  (already implemented), `TransferScoutPanel.addScoutEintrag(ScoutEintrag)`.
- Produces:
  - `public static XMLTransferSearchParser.Page OnlineWorker.searchTransfers(TransferSearchQuery query)`
    — returns `null` when the download failed.
  - `public class TransferSearchPanel extends ImagePanel` with
    `public TransferSearchPanel(TransferScoutPanel scoutPanel)`, `public void storeUserSettings()`
    and the package private `static Integer maxPriceOrNull(String text)`.

- [ ] **Step 1: Add the download method to OnlineWorker**

Insert into `src/main/java/core/net/OnlineWorker.java`, next to `downloadLeagueFixtures`:

```java
	/**
	 * Search the transfer market.
	 * <p>
	 * CHPP does not allow the transfer market to be scanned automatically, so this may only be
	 * called on an explicit user request.
	 *
	 * @param query the search parameters
	 * @return one page of search results, or null if the download failed
	 */
	public static XMLTransferSearchParser.Page searchTransfers(TransferSearchQuery query) {
		try {
			HOMainFrame.instance().setWaitInformation();
			final String xml = Connector.instance().getTransferSearch(query);
			HOMainFrame.instance().setWaitInformation();
			return XMLTransferSearchParser.parse(xml);
		} catch (Exception e) {
			HOLogger.instance().log(OnlineWorker.class, e);
			String msg = getLangString("Downloadfehler") + " : Error searching transfers: "
					+ e.getMessage();
			setInfoMsg(msg, InfoPanel.FEHLERFARBE);
			Helper.showMessage(HOMainFrame.instance(), msg, getLangString("Fehler"),
					JOptionPane.ERROR_MESSAGE);
		}
		return null;
	}
```

`core.file.xml.*` is already imported in that file, and `TransferSearchQuery` lives in `core.net` as
well, so no new import is needed.

- [ ] **Step 2: Write the panel**

Create `src/main/java/module/transfer/search/TransferSearchPanel.java`:

```java
package module.transfer.search;

import core.constants.player.PlayerAbility;
import core.constants.player.PlayerSpeciality;
import core.datatype.CBItem;
import core.gui.HOMainFrame;
import core.gui.comp.panel.ImagePanel;
import core.gui.comp.table.FixedColumnsTable;
import core.gui.model.UserColumnController;
import core.model.TranslationFacility;
import core.net.OnlineWorker;
import core.net.TransferSearchQuery;
import core.util.AmountOfMoney;
import core.util.Helper;
import module.transfer.scout.ScoutEintrag;
import module.transfer.scout.TransferScoutPanel;
import module.transfer.scout.TransferScoutingTableModel;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.math.BigDecimal;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

/**
 * Searches the transfer market through the CHPP transfersearch file and shows the hits in a scout
 * table. Hits are not part of the scout list until the user copies one over.
 */
public class TransferSearchPanel extends ImagePanel {

    /** The scout list a hit is copied into. */
    private final TransferScoutPanel scoutPanel;

    private final TransferScoutingTableModel resultsModel = emptyResultsModel();
    private final FixedColumnsTable resultsTable = new FixedColumnsTable(resultsModel, 2);
    private final List<ScoutEintrag> results = new ArrayList<>();

    private final JSpinner ageMin = new JSpinner(new SpinnerNumberModel(17, 17, 40, 1));
    private final JSpinner ageMax = new JSpinner(new SpinnerNumberModel(20, 17, 40, 1));
    private final JComboBox<TransferSearchQuery.Skill> skill = new JComboBox<>(TransferSearchQuery.Skill.values());
    private final JComboBox<CBItem> skillMin = new JComboBox<>(PlayerAbility.ITEMS);
    private final JComboBox<CBItem> skillMax = new JComboBox<>(PlayerAbility.ITEMS);
    private final JComboBox<CBItem> speciality = new JComboBox<>(PlayerSpeciality.ITEMS);
    private final JTextField maxPrice = new JTextField(10);
    private final JButton search = new JButton(TranslationFacility.tr("ls.transfersearch.search"));
    private final JButton addToScout = new JButton(TranslationFacility.tr("ls.transfersearch.addtoscout"));
    private final JLabel status = new JLabel(" ");

    public TransferSearchPanel(TransferScoutPanel scoutPanel) {
        this.scoutPanel = scoutPanel;
        initComponents();
    }

    /**
     * Store the column settings of the results table.
     */
    public void storeUserSettings() {
        resultsModel.storeUserSettings();
    }

    /**
     * The results model, emptied before the table is built over it. The model is a singleton of
     * {@link UserColumnController} and starts out with no data at all.
     */
    private static TransferScoutingTableModel emptyResultsModel() {
        final var model = UserColumnController.instance().getTransferSearchTableModel();
        model.setValues(new Vector<>());
        return model;
    }

    private void initComponents() {
        setLayout(new BorderLayout());
        add(initForm(), BorderLayout.NORTH);
        add(resultsTable.getContainerComponent(), BorderLayout.CENTER);
        add(status, BorderLayout.SOUTH);

        // The skill combo shows the translated HO skill name
        skill.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                          boolean isSelected, boolean cellHasFocus) {
                final var text = value == null ? ""
                        : ((TransferSearchQuery.Skill) value).toPlayerSkill().getLanguageString();
                return super.getListCellRendererComponent(list, text, index, isSelected, cellHasFocus);
            }
        });
        skill.setSelectedItem(TransferSearchQuery.Skill.PLAYMAKING);
        Helper.setComboBoxFromID(skillMin, 7);
        Helper.setComboBoxFromID(skillMax, 9);

        search.addActionListener(this::doSearch);
        addToScout.addActionListener(this::doAddToScout);
        addToScout.setEnabled(false);
        resultsTable.addListSelectionListener(
                e -> addToScout.setEnabled(resultsTable.getSelectedRow() > -1));
    }

    private JPanel initForm() {
        final var form = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 4));
        form.setOpaque(false);
        form.add(new JLabel(TranslationFacility.tr("ls.player.age")));
        form.add(ageMin);
        form.add(ageMax);
        form.add(new JLabel(TranslationFacility.tr("ls.transfersearch.skill")));
        form.add(skill);
        form.add(skillMin);
        form.add(skillMax);
        form.add(new JLabel(TranslationFacility.tr("ls.player.speciality")));
        form.add(speciality);
        form.add(new JLabel(TranslationFacility.tr("ls.transfersearch.maxprice")
                + " (" + AmountOfMoney.Companion.getCurrencyName() + ")"));
        form.add(maxPrice);
        form.add(search);
        form.add(addToScout);
        return form;
    }

    /**
     * Runs the search. Only ever called from the button, the transfer market may not be scanned
     * automatically.
     */
    private void doSearch(ActionEvent event) {
        final var query = buildQuery();
        final var error = query.validationErrorKey();
        if (error != null) {
            status.setText(TranslationFacility.tr(error));
            return;
        }

        final var page = OnlineWorker.searchTransfers(query);
        if (page == null) {
            return;     // OnlineWorker has shown the error
        }

        results.clear();
        for (final var hit : page.hits()) {
            results.add(TransferSearchConverter.toScoutEintrag(hit));
        }
        resultsModel.setValues(new Vector<>(results));
        resultsTable.repaint();
        status.setText(resultText(page.itemCount()));
    }

    /**
     * The hit count. Hattrick answers a query it does not accept with an error file rather than with
     * an HTTP error, and the parser turns that into an empty page - so "no hit" is reported as a
     * possible rejection, not as a plain zero.
     */
    private String resultText(int itemCount) {
        if (results.isEmpty()) {
            return TranslationFacility.tr("ls.transfersearch.nohits");
        }
        final var total = itemCount < 0 ? results.size() + "+" : String.valueOf(itemCount);
        return TranslationFacility.tr("ls.transfersearch.results") + ": " + results.size()
                + " / " + total;
    }

    private TransferSearchQuery buildQuery() {
        final var specialityId = ((CBItem) speciality.getSelectedItem()).getId();
        return TransferSearchQuery.builder()
                .ageMin((Integer) ageMin.getValue())
                .ageMax((Integer) ageMax.getValue())
                .skillType1((TransferSearchQuery.Skill) skill.getSelectedItem())
                .minSkillValue1(((CBItem) skillMin.getSelectedItem()).getId())
                .maxSkillValue1(((CBItem) skillMax.getSelectedItem()).getId())
                .specialty(specialityId == PlayerSpeciality.NO_SPECIALITY ? null : specialityId)
                .priceMax(maxPriceOrNull(maxPrice.getText()))
                .pageSize(50)
                .pageIndex(0)
                .build();
    }

    /**
     * The price limit of the form, converted from the user's currency into the swedish krona the
     * CHPP query expects, or null if the field is empty or unreadable.
     * <p>
     * {@code AmountOfMoney.parse} is deliberately not used: it takes the typed number for swedish
     * krona without applying the exchange rate, and as long as its currency formatter has not been
     * initialised it returns 0 instead of null for an empty field - which would silently limit
     * every search to a price of 0.
     */
    static Integer maxPriceOrNull(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            final var local = Helper.getNumberFormat(0).parse(text.trim()).doubleValue();
            return BigDecimal.valueOf(local)
                    .multiply(AmountOfMoney.Companion.getExchangeRate())
                    .intValue();
        } catch (ParseException e) {
            return null;
        }
    }

    private void doAddToScout(ActionEvent event) {
        final int row = resultsTable.getSelectedRow();
        if (row < 0) {
            return;
        }
        scoutPanel.addScoutEintrag(results.get(resultsTable.convertRowIndexToModel(row)));
        HOMainFrame.instance().setInformation(TranslationFacility.tr("ls.button.add"));
    }
}
```

Implementer notes:
- `maxPriceOrNull` is package private on purpose, Step 3 tests it. It is the only piece of the panel
  that is not pure Swing wiring.
- `getExchangeRate()` and `getCurrencyName()` are companion functions of `core/util/AmountOfMoney.kt`,
  seen from Java as `AmountOfMoney.Companion.*`. `toLocale()` there divides krona by the rate, so the
  way back into krona is a multiplication.
- `HOMainFrame.instance().setInformation(String)` exists (`HOMainFrame.java:206`) and writes to the
  status bar.
- Do **not** register this panel with `RefreshManager`: a refresh must never trigger a market search.

- [ ] **Step 3: Test the price conversion**

Create `src/test/java/module/transfer/search/TransferSearchPanelTest.java`:

```java
package module.transfer.search;

import core.util.AmountOfMoney;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class TransferSearchPanelTest {

    @BeforeAll
    static void fixTheExchangeRate() {
        AmountOfMoney.Companion.setExchangeRate(BigDecimal.TEN);
    }

    @Test
    void maxPriceOrNull_isNullForAnEmptyField() {
        assertThat(TransferSearchPanel.maxPriceOrNull("   ")).isNull();
        assertThat(TransferSearchPanel.maxPriceOrNull(null)).isNull();
    }

    @Test
    void maxPriceOrNull_isNullForAnUnreadableField() {
        assertThat(TransferSearchPanel.maxPriceOrNull("no money")).isNull();
    }

    @Test
    void maxPriceOrNull_convertsTheLocalCurrencyIntoSwedishKrona() {
        assertThat(TransferSearchPanel.maxPriceOrNull("100000")).isEqualTo(1000000);
    }
}
```

No panel is constructed, so the test stays headless: `maxPriceOrNull` is static, and `ImagePanel`
only reaches for `ThemeManager` from its constructor.

Run: `./gradlew test --tests "module.transfer.search.TransferSearchPanelTest"`
Expected: PASS.

- [ ] **Step 4: Compile and run the suite**

Run: `./gradlew test`
Expected: BUILD SUCCESSFUL, the whole suite green.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/core/net/OnlineWorker.java src/main/java/module/transfer/search src/test/java/module/transfer/search/TransferSearchPanelTest.java
git commit -m "Add the transfer search panel"
```

---

### Task 5: Wire the tab in and add the translation keys

**Files:**
- Modify: `src/main/java/module/transfer/TransfersPanel.java`
- Modify: `src/main/resources/language/English.properties`

**Interfaces:**
- Consumes: `TransferSearchPanel(TransferScoutPanel)` and `TransferSearchPanel.storeUserSettings()`
  (Task 4).
- Produces: nothing for later tasks.

- [ ] **Step 1: Add the translation keys**

Append to `src/main/resources/language/English.properties`:

```properties
ls.transfersearch=Transfer search
ls.transfersearch.skill=Skill
ls.transfersearch.maxprice=Max. price
ls.transfersearch.search=Search
ls.transfersearch.addtoscout=Add to scout list
ls.transfersearch.results=Hits
ls.transfersearch.nohits=No player found. Hattrick answers a query it does not accept with an empty result too.
ls.transfersearch.error.agerange=The minimum age must not be greater than the maximum age.
ls.transfersearch.error.skillrange=The skill range must not span more than 4 levels.
ls.transfersearch.error.minlevel=Hattrick searches set pieces, leadership and experience from level 5 and keeper from level 3 only.
```

Do not touch the other language files — POEditor owns them.

- [ ] **Step 2: Add the tab**

In `src/main/java/module/transfer/TransfersPanel.java`, add the import

```java
import module.transfer.search.TransferSearchPanel;
```

the field next to `scoutPanel`

```java
	private final TransferSearchPanel searchPanel;
```

the tab, directly after the scout tab is added in the constructor

```java
		searchPanel = new TransferSearchPanel(scoutPanel);
		tabPane.add(TranslationFacility.tr("ls.transfersearch"), searchPanel);
```

and the settings call in `storeUserSettings()`

```java
		searchPanel.storeUserSettings();
```

- [ ] **Step 3: Compile and run the suite**

Run: `./gradlew test`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/module/transfer/TransfersPanel.java src/main/resources/language/English.properties
git commit -m "Add the transfer search tab to the transfers module"
```

---

### Task 6: Manual verification against the live CHPP API

**Files:** none — this task only runs the application.

- [ ] **Step 1: Start HO from the dev tree**

Run: `./gradlew run`
Expected: HO starts, the user picker and main window appear.

- [ ] **Step 2: Check the tab**

Open the Transfers module. Expected: a fourth tab "Transfer search" next to History, TransferTypes and
TransferScout, with the form on top and an empty scout-style table below.

- [ ] **Step 3: Run an invalid search**

Set the skill range to a span of more than 4 levels (e.g. 5 to 12) and press Search.
Expected: the status line shows the skill-range message, **no** network call happens.

- [ ] **Step 4: Run a valid search**

Set age 20–22, playmaking 7 to 9, press Search.
Expected: the wait cursor appears, hits fill the table with names, prices, deadlines, ages, skills and
the derived best position, and the status line shows `Hits: <shown> / <total>`, with the total in the
`50+` form when Hattrick reports an item count of -1.

- [ ] **Step 5: Check the price filter**

Enter a max price far below the market (e.g. 1) and search, then clear the field and search again.
Expected: the first run reports the no-hits message, the second one returns hits — an empty price
field must never be read as a limit of 0. The label beside the field names the currency of the user's
settings, and a limit typed in that currency filters as it reads.

- [ ] **Step 6: Copy a hit into the scout list**

Select a row, press "Add to scout list", switch to the TransferScout tab.
Expected: the player is in the scout list with the same values; the search table is unchanged; the
column layout of the scout table is untouched after restarting HO.

- [ ] **Step 7: Commit nothing, report the result**

If anything is off, fix it in the task that owns the code and re-run this checklist.

---

## Self-Review

**Spec coverage:** decision 1 → Task 2; decision 2 → Task 3; decision 3 → Task 4 (button-only trigger,
no RefreshManager) and Task 6 step 3; decision 4 → Task 4 (`pageSize(50)`, `itemCount < 0` display);
decision 5 → Task 1; decision 6 → Task 4 form; decision 7 → Task 4 `maxPriceOrNull` and Task 6
step 5. Tab wiring and strings → Task 5.

**Placeholders:** none — every step carries the code it needs.

**Type consistency:** `validationErrorKey()` (Task 1) is called in Task 4; `toScoutEintrag(Hit)`
(Task 2) is called in Task 4; `getTransferSearchTableModel()` and the `ColumnModelId` constructor
(Task 3) are used in Task 4; `TransferSearchPanel(TransferScoutPanel)` and `storeUserSettings()`
(Task 4) are used in Task 5. The translation keys used in Tasks 1 and 4 are exactly those added in
Task 5.
