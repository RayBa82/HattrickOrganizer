# Scout Re-listing Watch Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Re-check an unsold scout entry on every team download, and when Hattrick lists the player again record his new asking price, deadline and skills — without a new CHPP file, a poller, or a database migration.

**Architecture:** `XMLPlayersParser` grows a second parse path that reads a `playerdetails` response as *market* data (`MarketPlayer`) instead of as an own-team player, gated on `<TransferListed>`. `TransferListing.fromPlayerDetails` becomes a full market snapshot rather than a skills-only one. `TransferListingResolver.fillMissingSkills` collapses into a single `observe` call shared by the scout pass and the corpus pass; the scout pass additionally refreshes the `ScoutEintrag` in place through a new `ScoutList.recordRelisting`.

**Tech Stack:** Java 17, Gradle 8.13 wrapper, JUnit 5 + AssertJ, Lombok (`io.freefair.lombok`), HSQLDB.

**Spec:** `docs/superpowers/specs/2026-09-05-scout-relist-watch-design.md`

## Global Constraints

- **Java 17 source/target.** Do not raise the language level.
- **Every `./gradlew` invocation must be prefixed** with `JAVA_HOME=$HOME/.sdkman/candidates/java/17.0.15-sapmchn`. The machine default is JDK 25, on which Gradle 8.13 dies during configuration with a misleading `Could not create task ':test'. > Type T not present`. Do not "fix" `build.gradle` in response to that error.
- **Expect the Java LSP in this repo to report hundreds of bogus unresolved-symbol errors** (`ConnectionManager`, Lombok-generated accessors). Annotation processing is not wired up in the IDE index; **Gradle is the only source of truth for whether the code compiles.** Ignore IDE diagnostics on Lombok accessors.
- **`compileJava.dependsOn(clean)`** — every build is a full clean rebuild, ~10s minimum, plus a POEditor call that prints `{"response":{"status":"fail","code":"4011",...}}` locally. That failure line is expected and is not a build failure.
- **No database migration.** `DBManager.DBVersion` stays at `1003`. Every column this plan writes (`SCOUT.Deadline`, `SCOUT.Price`, `SCOUT.Wecker`, all of `TRANSFERLISTING`) already exists.
- **The invariant (spec §2):** *a TRANSFERLISTING row is only ever written from a response in which `<TransferListed>` is true.* Every task below either upholds it or is explicitly about enforcing it. An unlisted player's `playerdetails` response has seven pruned skills that `xmlIntValue` would silently read as `0`; a zero-skill row poisons `loadComparables`. Never relax this.
- **Line numbers below are as of this plan's writing.** Earlier tasks insert into the same files, so a reference in a later task has already shifted by the time you reach it — Task 4 alone adds ~10 lines to the top of `TransferListingResolver`. Resolve by the named method or symbol; the line number is a hint, not an address.
- **All UI strings go through `TranslationFacility.tr(key)`.** This plan adds none.
- **Legacy German domain vocabulary stays** (`ScoutEintrag`, `m_bWecker`, `setbaseWage`, `getSpielaufbau`). Do not rename.

---

## File Structure

| File | Responsibility after this plan |
|---|---|
| `src/main/java/core/file/xml/XMLPlayersParser.java` | Adds the nested `MarketPlayer` record and `parseMarketPlayerFromString`; `parsePlayerDetailsFromString` shrinks to an own-team wrapper over it. |
| `src/main/java/core/net/OnlineWorker.java` | Adds `downloadMarketPlayer(String)` beside `downloadPlayerDetails(String)`. Three lines. |
| `src/main/java/core/db/TransferListing.java` | `fromPlayerDetails` takes a `MarketPlayer`, fills the market fields, and converts an abroad player's salary to the base wage. |
| `src/main/java/module/transfer/scout/ScoutList.java` | Adds `recordRelisting` (finds the entry, saves) and the pure `applyRelisting` (mutates one entry). |
| `src/main/java/core/net/TransferListingResolver.java` | `observe` replaces `fillMissingSkills`; `SCOUT_WATCH_GRACE`; the pure `scoutAction` decision; scout loop gets its own sub-cap. |
| `src/test/java/core/file/xml/XMLPlayersParserTest.java` | **New.** The two live `playerdetails` shapes as inline XML fixtures. |
| `src/test/java/core/db/TransferListingTableTest.java` | Fixes the `fromPlayerDetails` compile break and adds the base-wage cases. |
| `src/test/java/core/net/TransferListingResolverTest.java` | Adds the §5.1 branch table over `scoutAction`. |
| `src/test/java/module/transfer/scout/ScoutListTest.java` | **New.** The `applyRelisting` field table, including the `Wecker` reset. |
| `docs/current.md`, `docs/chpp_files.md` | Corrections (spec §11). |

## Two deliberate deviations from the spec, and why

Both are about testability, and both are recorded here rather than discovered mid-task.

1. **Spec §9 asks for tests "over `observe`'s result, with the CHPP call stubbed".** `observe` calls the static `OnlineWorker.downloadMarketPlayer` and `DBManager.instance()`; stubbing that needs a new injection seam in `OnlineWorker` that nothing else wants. Instead, Task 5 extracts the *decision* — what the scout pass does given `transferListed` and the old deadline — into a pure package-private `scoutAction`, and the test drives that. Same branch table, no seam, no stub.

2. **Spec §9 case 4 asks that `parsePlayerDetailsFromString` "on the same XML still does" return null.** That assertion needs `UserManager.instance()` and `HOVerwaltung.instance().getModel().getBasics()`, neither of which exists in a unit test. Task 1 asserts only the half that is reachable: `parseMarketPlayerFromString` returns a non-null record for a foreign player. The wrapper's guard is three lines copied verbatim from the method it replaces and is covered by the existing behaviour of every caller.

---

### Task 1: Parse a `playerdetails` response as market data

**Files:**
- Modify: `src/main/java/core/file/xml/XMLPlayersParser.java:402-515`
- Modify: `src/main/java/core/net/OnlineWorker.java:1503-1506`
- Test: `src/test/java/core/file/xml/XMLPlayersParserTest.java` (create)

**Interfaces:**
- Consumes: nothing from earlier tasks.
- Produces:
  - `public record XMLPlayersParser.MarketPlayer(Player player, boolean transferListed, int owningTeamId, boolean isAbroad, long askingPrice, HODateTime deadline, long highestBid)`
  - `public MarketPlayer XMLPlayersParser.parseMarketPlayerFromString(String xml)` — instance method, never returns null for a well-formed response.
  - `public Player XMLPlayersParser.parsePlayerDetailsFromString(String xml)` — unchanged signature and unchanged behaviour.
  - `public static MarketPlayer OnlineWorker.downloadMarketPlayer(String playerID)`

- [ ] **Step 1: Write the failing test**

Create `src/test/java/core/file/xml/XMLPlayersParserTest.java`:

```java
package core.file.xml;

import core.util.HODateTime;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The two shapes a {@code playerdetails} response takes for a player who is not ours. Both were
 * fetched live against the same non-supporter account; the differences between them are the whole
 * reason the market parse path exists (design spec §1.2, §1.3).
 */
class XMLPlayersParserTest {

    /** A foreign player who is on the market: full skill block, populated TransferDetails. */
    private static final String LISTED = """
            <?xml version="1.0" encoding="utf-8"?>
            <HattrickData>
              <FileName>playerdetails.xml</FileName>
              <Version>3.0</Version>
              <UserID>1234567</UserID>
              <FetchedDate>2026-09-05 12:00:00</FetchedDate>
              <UserSupporterTier>none</UserSupporterTier>
              <Player>
                <PlayerID>476487492</PlayerID>
                <FirstName>Bruno</FirstName>
                <NickName></NickName>
                <LastName>Gaspar</LastName>
                <PlayerNumber>100</PlayerNumber>
                <PlayerCategoryID>0</PlayerCategoryID>
                <OwnerNotes></OwnerNotes>
                <Age>24</Age>
                <AgeDays>77</AgeDays>
                <ArrivalDate>2025-04-01 10:00:00</ArrivalDate>
                <PlayerForm>6</PlayerForm>
                <Cards>1</Cards>
                <InjuryLevel>-1</InjuryLevel>
                <Statement></Statement>
                <Agreeability>3</Agreeability>
                <Aggressiveness>2</Aggressiveness>
                <Honesty>4</Honesty>
                <Experience>5</Experience>
                <Loyalty>12</Loyalty>
                <MotherClubBonus>False</MotherClubBonus>
                <Leadership>3</Leadership>
                <Specialty>2</Specialty>
                <NativeCountryID>3</NativeCountryID>
                <TSI>29000</TSI>
                <OwningTeam>
                  <TeamID>555444</TeamID>
                  <TeamName>Selling Sam</TeamName>
                  <LeagueID>2</LeagueID>
                </OwningTeam>
                <Salary>57840</Salary>
                <IsAbroad>True</IsAbroad>
                <Caps>0</Caps>
                <CapsU20>0</CapsU20>
                <CareerGoals>12</CareerGoals>
                <CareerHattricks>1</CareerHattricks>
                <MatchesCurrentTeam>30</MatchesCurrentTeam>
                <GoalsCurrentTeam>7</GoalsCurrentTeam>
                <CareerAssists>4</CareerAssists>
                <AssistsCurrentTeam>2</AssistsCurrentTeam>
                <TransferListed>True</TransferListed>
                <TransferDetails>
                  <AskingPrice>2850000</AskingPrice>
                  <Deadline>2026-09-05 20:08:31</Deadline>
                  <HighestBid>0</HighestBid>
                  <BidderTeam />
                </TransferDetails>
                <PlayerSkills>
                  <StaminaSkill>7</StaminaSkill>
                  <KeeperSkill>1</KeeperSkill>
                  <PlaymakerSkill>12</PlaymakerSkill>
                  <ScorerSkill>5</ScorerSkill>
                  <PassingSkill>9</PassingSkill>
                  <WingerSkill>7</WingerSkill>
                  <DefenderSkill>6</DefenderSkill>
                  <SetPiecesSkill>10</SetPiecesSkill>
                </PlayerSkills>
              </Player>
            </HattrickData>
            """;

    /**
     * The same player off the market. PlayerSkills is present but pruned to stamina, and
     * TransferDetails is present but <em>empty</em> - the trap that would break a presence check.
     */
    private static final String UNLISTED = """
            <?xml version="1.0" encoding="utf-8"?>
            <HattrickData>
              <FileName>playerdetails.xml</FileName>
              <Version>3.0</Version>
              <UserID>1234567</UserID>
              <FetchedDate>2026-09-06 12:00:00</FetchedDate>
              <UserSupporterTier>none</UserSupporterTier>
              <Player>
                <PlayerID>476487492</PlayerID>
                <FirstName>Bruno</FirstName>
                <NickName></NickName>
                <LastName>Gaspar</LastName>
                <PlayerNumber>100</PlayerNumber>
                <PlayerCategoryID>0</PlayerCategoryID>
                <OwnerNotes></OwnerNotes>
                <Age>24</Age>
                <AgeDays>78</AgeDays>
                <ArrivalDate>2025-04-01 10:00:00</ArrivalDate>
                <PlayerForm>6</PlayerForm>
                <Cards>1</Cards>
                <InjuryLevel>-1</InjuryLevel>
                <Statement></Statement>
                <Agreeability>3</Agreeability>
                <Aggressiveness>2</Aggressiveness>
                <Honesty>4</Honesty>
                <Experience>5</Experience>
                <Loyalty>12</Loyalty>
                <MotherClubBonus>False</MotherClubBonus>
                <Leadership>3</Leadership>
                <Specialty>2</Specialty>
                <NativeCountryID>3</NativeCountryID>
                <TSI>29000</TSI>
                <OwningTeam>
                  <TeamID>555444</TeamID>
                  <TeamName>Selling Sam</TeamName>
                  <LeagueID>2</LeagueID>
                </OwningTeam>
                <Salary>57840</Salary>
                <IsAbroad>True</IsAbroad>
                <Caps>0</Caps>
                <CapsU20>0</CapsU20>
                <CareerGoals>12</CareerGoals>
                <CareerHattricks>1</CareerHattricks>
                <MatchesCurrentTeam>30</MatchesCurrentTeam>
                <GoalsCurrentTeam>7</GoalsCurrentTeam>
                <CareerAssists>4</CareerAssists>
                <AssistsCurrentTeam>2</AssistsCurrentTeam>
                <TransferListed>False</TransferListed>
                <TransferDetails />
                <PlayerSkills>
                  <StaminaSkill>7</StaminaSkill>
                </PlayerSkills>
              </Player>
            </HattrickData>
            """;

    @Test
    void listedPlayerCarriesTheFullSkillBlockAndTheMarketData() {
        final var m = new XMLPlayersParser().parseMarketPlayerFromString(LISTED);

        assertThat(m.transferListed()).isTrue();
        assertThat(m.owningTeamId()).isEqualTo(555444);
        assertThat(m.isAbroad()).isTrue();
        assertThat(m.askingPrice()).isEqualTo(2_850_000L);
        assertThat(m.deadline()).isEqualTo(HODateTime.fromHT("2026-09-05 20:08:31"));
        assertThat(m.highestBid()).isZero();

        final var p = m.player();
        assertThat(p.getPlayerId()).isEqualTo(476487492);
        assertThat(p.getStamina()).isEqualTo(7);
        assertThat(p.getGoalkeeperSkill()).isEqualTo(1);
        assertThat(p.getPlaymakingSkill()).isEqualTo(12);
        assertThat(p.getScoringSkill()).isEqualTo(5);
        assertThat(p.getPassingSkill()).isEqualTo(9);
        assertThat(p.getWingerSkill()).isEqualTo(7);
        assertThat(p.getDefendingSkill()).isEqualTo(6);
        assertThat(p.getSetPiecesSkill()).isEqualTo(10);
    }

    /**
     * The single most valuable case in this file. Hattrick prunes an unlisted player's skills to
     * stamina, and {@code xmlIntValue} defaults a missing child to <b>0</b> - so parsing the block
     * unconditionally would report seven genuine skills as zero and drag every valuation built on
     * them downward. Leaving them unparsed keeps Player's own default of 1, which is not "unknown"
     * either, but is at least distinguishable; the §2 invariant is what keeps the row out of the
     * corpus.
     */
    @Test
    void unlistedPlayerKeepsStaminaAndLeavesTheSevenHiddenSkillsUnparsed() {
        final var m = new XMLPlayersParser().parseMarketPlayerFromString(UNLISTED);

        assertThat(m.transferListed()).isFalse();

        final var p = m.player();
        assertThat(p.getStamina()).isEqualTo(7);
        assertThat(p.getGoalkeeperSkill()).isNotZero();
        assertThat(p.getPlaymakingSkill()).isNotZero();
        assertThat(p.getScoringSkill()).isNotZero();
        assertThat(p.getPassingSkill()).isNotZero();
        assertThat(p.getWingerSkill()).isNotZero();
        assertThat(p.getDefendingSkill()).isNotZero();
        assertThat(p.getSetPiecesSkill()).isNotZero();
    }

    /**
     * An unlisted response carries no market data at all: no price, no bid, no deadline.
     * <p>
     * Note what this does <em>not</em> reach. {@code <TransferDetails />} is a present, non-null
     * Element for an unlisted player, so a presence check would report everybody as listed - but
     * {@code parseMarketPlayerFromString} short-circuits on {@code <TransferListed>} before it ever
     * looks the container up, so no unit test can exercise that trap through this method. This is a
     * regression guard on the record's null/zero defaults, and a reminder in prose: if the gate is
     * ever refactored into a container-presence check, this test stays green and the corpus rots.
     */
    @Test
    void anUnlistedPlayerCarriesNoMarketData() {
        final var m = new XMLPlayersParser().parseMarketPlayerFromString(UNLISTED);

        assertThat(m.askingPrice()).isZero();
        assertThat(m.highestBid()).isZero();
        assertThat(m.deadline()).isNull();
    }

    /**
     * The own-team guard that makes {@code parsePlayerDetailsFromString} return null for every
     * market player is not on this path - that guard is why the skills-fill pass has never stored
     * anything (design spec §1.4).
     */
    @Test
    void aForeignPlayerParsesAtAll() {
        assertThat(new XMLPlayersParser().parseMarketPlayerFromString(LISTED)).isNotNull();
        assertThat(new XMLPlayersParser().parseMarketPlayerFromString(LISTED).player()
                .getPlayerId()).isEqualTo(476487492);
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `JAVA_HOME=$HOME/.sdkman/candidates/java/17.0.15-sapmchn ./gradlew test --tests "core.file.xml.XMLPlayersParserTest"`

Expected: compile failure — `cannot find symbol: method parseMarketPlayerFromString(String)`.

- [ ] **Step 3: Add the `MarketPlayer` record**

In `XMLPlayersParser.java`, directly above the existing `// TODO refactor, use createPlayerDetails` comment at line 402, insert:

```java
    /**
     * A {@code playerdetails} response read as market data. {@code transferListed} decides what the
     * rest of it is worth: Hattrick publishes the full skill block and the TransferDetails block
     * only while the player is on the market, and prunes PlayerSkills down to stamina otherwise
     * (see docs/superpowers/specs/2026-09-05-scout-relist-watch-design.md §1.2, §1.3). When it is
     * false the market fields are 0 / null, and the seven hidden skills on {@code player} are a
     * meaningless 1 - Player's own field default, left unparsed rather than read as 0. Only stamina
     * is real. Build nothing out of this record's skills unless the flag is true.
     */
    public record MarketPlayer(Player player, boolean transferListed, int owningTeamId,
                               boolean isAbroad, long askingPrice, HODateTime deadline,
                               long highestBid) {}
```

- [ ] **Step 4: Turn `parsePlayerDetailsFromString` into `parseMarketPlayerFromString`**

Four edits inside the existing method body (`XMLPlayersParser.java:402-515`).

**4a. Rename it, change the return type, and drop the own-team guard.** Replace:

```java
    // TODO refactor, use createPlayerDetails
    public Player parsePlayerDetailsFromString(String xml) {
        Document doc = XMLManager.parseString(xml);

        var root = doc.getDocumentElement();
        root = (Element) root.getElementsByTagName("Player").item(0);

        var owningTeam = (Element)root.getElementsByTagName("OwningTeam").item(0);
        var teamID = xmlIntValue(owningTeam, "TeamID");
        if (!UserManager.instance().getCurrentUser().isNtTeam() &&
             teamID != HOVerwaltung.instance().getModel().getBasics().getTeamId()) return null; // foreign player

        var player = new Player();
```

with:

```java
    // TODO refactor, use createPlayerDetails
    public MarketPlayer parseMarketPlayerFromString(String xml) {
        Document doc = XMLManager.parseString(xml);

        var root = doc.getDocumentElement();
        root = (Element) root.getElementsByTagName("Player").item(0);

        var owningTeam = (Element)root.getElementsByTagName("OwningTeam").item(0);
        var teamID = xmlIntValue(owningTeam, "TeamID");

        var player = new Player();
```

**4b. Parse `IsAbroad` instead of leaving it commented out.**

Mind the polarity. The comment being deleted here claims *"true for beeing in home country, false for
beeing in a team abroad"* — that is backwards. CHPP's `IsAbroad` is `True` when the player plays
**outside** his home country, which is what the live fetch showed: the `<IsAbroad>True</IsAbroad>`
player's `Salary` was exactly 1.2× the base wage `transfersearch` reported for the same player. Task
2 divides by 1.2 on `true`. Inverted, this corrupts the wage column silently and in a direction
nothing downstream would catch.

Replace:

```java
        player.setWage(new AmountOfMoney(xmlIntValue(root, "Salary")));

//        IsAbroad : Boolean*
//                The abroad status of the player, given as an integer. true for beeing in home country, false for beeing in a team abroad. The container is empty If the player has no owner.

```

with:

```java
        player.setWage(new AmountOfMoney(xmlIntValue(root, "Salary")));

        // Not a Player field: the abroad status only matters for correcting the wage back to its
        // base, which TransferListing does. Empty when the player has no owner, so false by default.
        final var isAbroad = xmlBoolValue(root, "IsAbroad");

```

**4c. Gate the skill block and return the record.** Replace the whole tail of the method:

```java
        ele = (Element) root.getElementsByTagName("PlayerSkills").item(0);
        if ( ele != null) {
            player.setStamina(xmlIntValue(ele, "StaminaSkill"));
            player.setGoalkeeperSkill(xmlIntValue(ele, "KeeperSkill"));
            player.setPlaymakingSkill(xmlIntValue(ele, "PlaymakerSkill"));
            player.setScoringSkill(xmlIntValue(ele, "ScorerSkill"));
            player.setPassingSkill(xmlIntValue(ele, "PassingSkill"));
            player.setWingerSkill(xmlIntValue(ele, "WingerSkill"));
            player.setDefendingSkill(xmlIntValue(ele, "DefenderSkill"));
            player.setSetPiecesSkill(xmlIntValue(ele, "SetPiecesSkill"));
        }

        return player;
    }
```

with:

```java
        final var transferListed = xmlBoolValue(root, "TransferListed");

        ele = (Element) root.getElementsByTagName("PlayerSkills").item(0);
        if ( ele != null) {
            // Stamina is published whether or not he is listed. The other seven are pruned out of
            // the block when he is not, and xmlIntValue would read every one of them as 0 - a
            // number indistinguishable from a genuinely terrible footballer. Leave them at Player's
            // own default of 1 instead, and let the caller refuse to build anything out of them.
            player.setStamina(xmlIntValue(ele, "StaminaSkill"));
            if (transferListed) {
                player.setGoalkeeperSkill(xmlIntValue(ele, "KeeperSkill"));
                player.setPlaymakingSkill(xmlIntValue(ele, "PlaymakerSkill"));
                player.setScoringSkill(xmlIntValue(ele, "ScorerSkill"));
                player.setPassingSkill(xmlIntValue(ele, "PassingSkill"));
                player.setWingerSkill(xmlIntValue(ele, "WingerSkill"));
                player.setDefendingSkill(xmlIntValue(ele, "DefenderSkill"));
                player.setSetPiecesSkill(xmlIntValue(ele, "SetPiecesSkill"));
            }
        }

        // Read by child, never by container presence: <TransferDetails /> is a non-null Element for
        // an unlisted player, so only <TransferListed> can tell the two apart.
        long askingPrice = 0L;
        long highestBid = 0L;
        HODateTime deadline = null;
        if (transferListed) {
            final var details = (Element) root.getElementsByTagName("TransferDetails").item(0);
            if (details != null) {
                final var ask = xmlLongValue(details, "AskingPrice");
                final var bid = xmlLongValue(details, "HighestBid");
                askingPrice = ask == null ? 0L : ask;
                highestBid = bid == null ? 0L : bid;
                deadline = xmlHODateTimeValue(details, "Deadline");
            }
        }

        return new MarketPlayer(player, transferListed, teamID, isAbroad,
                askingPrice, deadline, highestBid);
    }
```

**4d.** Delete the now-orphaned line that used to set `transferListed` further up the method — find and remove:

```java
        player.setTransferListed(xmlBoolValue(root, "TransferListed")?1:0);
```

and put it back immediately after the `final var transferListed = ...` line added in 4c, as:

```java
        player.setTransferListed(transferListed ? 1 : 0);
```

- [ ] **Step 5: Add the own-team wrapper back**

Immediately after the closing brace of `parseMarketPlayerFromString`, add:

```java
    /**
     * The own-team view of a {@code playerdetails} response: null for anybody else's player, which
     * is what every existing caller expects.
     *
     * @see #parseMarketPlayerFromString for the market view, which has no such guard
     */
    public Player parsePlayerDetailsFromString(String xml) {
        final var m = parseMarketPlayerFromString(xml);
        if (!UserManager.instance().getCurrentUser().isNtTeam() &&
             m.owningTeamId() != HOVerwaltung.instance().getModel().getBasics().getTeamId()) {
            return null;   // foreign player
        }
        return m.player();
    }
```

- [ ] **Step 6: Add `OnlineWorker.downloadMarketPlayer`**

In `src/main/java/core/net/OnlineWorker.java`, immediately after `downloadPlayerDetails` (line 1506), add:

```java
	/**
	 * A player-details download read as market data, so it works for somebody else's player.
	 * {@link #downloadPlayerDetails} returns null for those.
	 */
	public static XMLPlayersParser.MarketPlayer downloadMarketPlayer(String playerID) {
		var xml = Connector.instance().downloadPlayerDetails(playerID);
		return new XMLPlayersParser().parseMarketPlayerFromString(xml);
	}
```

`XMLPlayersParser` is already imported in `OnlineWorker`; if the build says otherwise, add `import core.file.xml.XMLPlayersParser;`.

- [ ] **Step 7: Run the test to verify it passes**

Run: `JAVA_HOME=$HOME/.sdkman/candidates/java/17.0.15-sapmchn ./gradlew test --tests "core.file.xml.XMLPlayersParserTest"`

Expected: PASS, 4 tests.

- [ ] **Step 8: Commit**

```bash
git add src/main/java/core/file/xml/XMLPlayersParser.java \
        src/main/java/core/net/OnlineWorker.java \
        src/test/java/core/file/xml/XMLPlayersParserTest.java
git commit -m "feat(chpp): read playerdetails as market data, gated on TransferListed"
```

---

### Task 2: `TransferListing.fromPlayerDetails` becomes a full market snapshot

**Files:**
- Modify: `src/main/java/core/db/TransferListing.java:104-135`
- Test: `src/test/java/core/db/TransferListingTableTest.java:103-121`

**Interfaces:**
- Consumes: `XMLPlayersParser.MarketPlayer(Player player, boolean transferListed, int owningTeamId, boolean isAbroad, long askingPrice, HODateTime deadline, long highestBid)` from Task 1.
- Produces: `public static TransferListing TransferListing.fromPlayerDetails(XMLPlayersParser.MarketPlayer m, HODateTime observedAt)`. Throws `IllegalArgumentException` when `m.transferListed()` is false. The old `fromPlayerDetails(Player, HODateTime)` overload is **removed**, not kept — keeping it would leave the invariant bypassable.

- [ ] **Step 1: Write the failing test**

In `src/test/java/core/db/TransferListingTableTest.java`, replace the whole `snapshotsPlayerDetailsAsASkillBearingListing` test (lines 103-121) with:

```java
    private static core.file.xml.XMLPlayersParser.MarketPlayer market(
            int playerId, boolean isAbroad, long salarySek) {
        final var p = new core.model.player.Player();
        p.setPlayerId(playerId);
        p.setAge(27);
        p.setWage(new core.util.AmountOfMoney(salarySek));
        p.setValue4Skill(core.constants.player.PlayerSkill.PLAYMAKING, 13);
        p.setValue4Skill(core.constants.player.PlayerSkill.SCORING, 9);
        return new core.file.xml.XMLPlayersParser.MarketPlayer(
                p, true, 555444, isAbroad, 2_850_000L,
                HODateTime.fromHT("2026-09-05 20:08:31"), 100_000L);
    }

    /** A listed player's details are a complete market snapshot, not a skills-only one. */
    @Test
    void snapshotsPlayerDetailsAsASkillBearingListing() {
        final var observedAt = HODateTime.fromHT("2026-08-30 16:00:00");

        table.storeListing(TransferListing.fromPlayerDetails(market(88991122, false, 44_000L),
                observedAt));

        final var loaded = table.loadUnresolved(50).stream()
                .filter(l -> l.getPlayerId() == 88991122).findFirst().orElseThrow();
        assertThat(loaded.getAge()).isEqualTo(27);
        assertThat(loaded.getPlaymaker()).isEqualTo(13);
        assertThat(loaded.getScorer()).isEqualTo(9);
        assertThat(loaded.getSalePrice()).isNull();
        assertThat(loaded.getAskingPrice()).isEqualTo(2_850_000L);
        assertThat(loaded.getHighestBid()).isEqualTo(100_000L);
        assertThat(loaded.getDeadline()).isEqualTo(HODateTime.fromHT("2026-09-05 20:08:31"));
    }

    /**
     * CHPP's Salary carries the 20% surcharge for a player abroad; the search path's Salary does
     * not. Mixing the two in one column makes wage an unusable comparable - and it reaches the
     * scout table's baseWage column, which the user reads.
     */
    @Test
    void anAbroadPlayersSalaryIsStoredAsTheBaseWage() {
        final var observedAt = HODateTime.fromHT("2026-08-30 16:00:00");

        table.storeListing(TransferListing.fromPlayerDetails(market(88991123, true, 57_840L),
                observedAt));
        table.storeListing(TransferListing.fromPlayerDetails(market(88991124, false, 57_840L),
                observedAt));

        assertThat(table.loadUnresolved(50).stream()
                .filter(l -> l.getPlayerId() == 88991123).findFirst().orElseThrow().getSalary())
                .isEqualTo(48_200L);
        assertThat(table.loadUnresolved(50).stream()
                .filter(l -> l.getPlayerId() == 88991124).findFirst().orElseThrow().getSalary())
                .isEqualTo(57_840L);
    }

    /** The §2 invariant: an unlisted response has nothing the corpus wants, so it cannot build one. */
    @Test
    void refusesToSnapshotAnUnlistedPlayer() {
        final var p = new core.model.player.Player();
        p.setPlayerId(88991125);
        final var unlisted = new core.file.xml.XMLPlayersParser.MarketPlayer(
                p, false, 555444, false, 0L, null, 0L);

        org.assertj.core.api.Assertions.assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> TransferListing.fromPlayerDetails(
                        unlisted, HODateTime.fromHT("2026-08-30 16:00:00")));
    }
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `JAVA_HOME=$HOME/.sdkman/candidates/java/17.0.15-sapmchn ./gradlew test --tests "core.db.TransferListingTableTest"`

Expected: compile failure — `incompatible types: MarketPlayer cannot be converted to Player`.

- [ ] **Step 3: Rewrite `fromPlayerDetails`**

In `src/main/java/core/db/TransferListing.java`, replace lines 104-135 in full with:

```java
    /**
     * The full market snapshot of a {@code playerdetails} download of a listed player: skills,
     * details <em>and</em> the market data, so a row from this path carries everything one built by
     * {@link #fromHit} does bar {@code bidderName} - {@code playerdetails} names the bidding team in
     * a {@code <BidderTeam>} element this path does not read, and that column stays null.
     * <p>
     * Only for a listed player. Hattrick prunes an unlisted player's skills down to stamina and
     * empties his TransferDetails block, so a row built from one of those would carry six or seven
     * fabricated skills into {@code loadComparables} and drag every valuation using them downward.
     * The correct number of rows to write for an unlisted response is zero.
     *
     * @throws IllegalArgumentException if {@code m} is not transfer-listed
     */
    public static TransferListing fromPlayerDetails(XMLPlayersParser.MarketPlayer m,
                                                    HODateTime observedAt) {
        if (!m.transferListed()) {
            throw new IllegalArgumentException(
                    "playerdetails of an unlisted player carries no corpus-worthy data: "
                            + m.player().getPlayerId());
        }
        final var p = m.player();
        final var listing = new TransferListing();
        listing.playerId = p.getPlayerId();
        listing.observedAt = observedAt;
        listing.playerName = p.getFullName();
        listing.deadline = m.deadline();
        listing.askingPrice = m.askingPrice();
        listing.highestBid = m.highestBid();
        listing.age = p.getAge();
        listing.ageDays = p.getAgeDays();
        listing.tsi = p.getTsi();
        listing.specialty = p.getSpecialty();
        listing.form = p.getValue4Skill(PlayerSkill.FORM);
        listing.experience = p.getValue4Skill(PlayerSkill.EXPERIENCE);
        listing.leadership = p.getValue4Skill(PlayerSkill.LEADERSHIP);
        listing.cards = p.getTotalCards();
        listing.injuryLevel = p.getInjuryWeeks();
        listing.stamina = p.getValue4Skill(PlayerSkill.STAMINA);
        listing.keeper = p.getValue4Skill(PlayerSkill.KEEPER);
        listing.playmaker = p.getValue4Skill(PlayerSkill.PLAYMAKING);
        listing.scorer = p.getValue4Skill(PlayerSkill.SCORING);
        listing.passing = p.getValue4Skill(PlayerSkill.PASSING);
        listing.winger = p.getValue4Skill(PlayerSkill.WINGER);
        listing.defender = p.getValue4Skill(PlayerSkill.DEFENDING);
        listing.setPieces = p.getValue4Skill(PlayerSkill.SETPIECES);
        listing.salary = baseWage(p, m.isAbroad());
        return listing;
    }

    /**
     * CHPP's {@code Salary} includes the 20% surcharge a club pays for a player abroad;
     * {@code transfersearch}'s does not. {@link #fromHit} stores the base wage, so this one has to
     * as well or the column mixes two quantities and stops being a comparable.
     */
    private static Long baseWage(Player p, boolean isAbroad) {
        final var wage = p.getWage();
        if (wage == null || wage.getSwedishKrona() == null) {
            return null;
        }
        final var sek = wage.getSwedishKrona().longValue();
        return isAbroad ? Math.round(sek / 1.2) : sek;
    }
```

Add the import at the top of the file, beside the existing `core.file.xml.*` imports:

```java
import core.file.xml.XMLPlayersParser;
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `JAVA_HOME=$HOME/.sdkman/candidates/java/17.0.15-sapmchn ./gradlew test --tests "core.db.TransferListingTableTest"`

Expected: PASS — **after** Step 5 below. Until then the module does not compile, because `TransferListingResolver.fillMissingSkills` still passes a `Player`.

- [ ] **Step 5: Keep the resolver compiling**

A three-line holding patch that Task 5 replaces wholesale. Without it the tree is red between tasks, and a red tree makes every later task's test run unreadable.

In `src/main/java/core/net/TransferListingResolver.java:175-182`, replace:

```java
        try {
            final var player = OnlineWorker.downloadPlayerDetails(
                    String.valueOf(listing.getPlayerId()));
            if (player != null && player.getPlayerId() != 0) {
                DBManager.instance().storeTransferListing(
                        TransferListing.fromPlayerDetails(player, now));
            }
```

with:

```java
        try {
            final var m = OnlineWorker.downloadMarketPlayer(
                    String.valueOf(listing.getPlayerId()));
            if (m != null && m.player().getPlayerId() != 0 && m.transferListed()) {
                DBManager.instance().storeTransferListing(
                        TransferListing.fromPlayerDetails(m, now));
            }
```

Re-run `JAVA_HOME=$HOME/.sdkman/candidates/java/17.0.15-sapmchn ./gradlew test --tests "core.db.TransferListingTableTest"`. Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/core/db/TransferListing.java \
        src/main/java/core/net/TransferListingResolver.java \
        src/test/java/core/db/TransferListingTableTest.java
git commit -m "feat(db): fromPlayerDetails stores market data and the base wage"
```

---

### Task 3: Refresh a re-listed scout entry

**Files:**
- Modify: `src/main/java/module/transfer/scout/ScoutList.java` (add after `recordSale`, line 93)
- Test: `src/test/java/module/transfer/scout/ScoutListTest.java` (create)

**Interfaces:**
- Consumes: nothing from earlier tasks.
- Produces:
  - `public static boolean ScoutList.recordRelisting(int playerId, long askingPrice, long highestBid, HODateTime deadline)`
  - `static void ScoutList.applyRelisting(ScoutEintrag entry, long askingPrice, long highestBid, HODateTime deadline)` — package-private, pure, no I/O.

- [ ] **Step 1: Write the failing test**

Create `src/test/java/module/transfer/scout/ScoutListTest.java`:

```java
package module.transfer.scout;

import core.util.AmountOfMoney;
import core.util.HODateTime;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What a re-listing does to a scout entry. The persistence half of {@link ScoutList} reaches for
 * the Transfers tab when it is open, so only the field mutation is unit-testable - which is the
 * half with the decisions in it.
 */
class ScoutListTest {

    private static final HODateTime OLD_DEADLINE = HODateTime.fromHT("2026-08-20 20:15:00");
    private static final HODateTime NEW_DEADLINE = HODateTime.fromHT("2026-09-12 20:08:31");

    private static ScoutEintrag entry() {
        final var e = new ScoutEintrag();
        e.setPlayerID(476487492);
        e.setName("Bruno Gaspar");
        e.setPrice(new AmountOfMoney(1_500_000L));
        e.setDeadline(OLD_DEADLINE.toDbTimestamp());
        return e;
    }

    @Test
    void aRelistingMovesTheDeadlineAndThePriceForward() {
        final var e = entry();

        ScoutList.applyRelisting(e, 2_850_000L, 0L, NEW_DEADLINE);

        assertThat(e.getDeadline()).isEqualTo(NEW_DEADLINE.toDbTimestamp());
        assertThat(e.getPrice().getSwedishKrona().longValue()).isEqualTo(2_850_000L);
    }

    /** Same rule as a fresh search hit: the column means "what it would cost to take him now". */
    @Test
    void aStandingBidAboveTheAskIsWhatThePriceBecomes() {
        final var e = entry();

        ScoutList.applyRelisting(e, 2_850_000L, 3_100_000L, NEW_DEADLINE);

        assertThat(e.getPrice().getSwedishKrona().longValue()).isEqualTo(3_100_000L);
    }

    /**
     * {@code m_bWecker} is a has-already-rung latch, not a user preference - {@code ScoutThread}
     * fires only when it is false and sets it true, and nothing in src/main/java ever sets it back.
     * Left true, the alarm never fires again for the new deadline, and the watch is pointless.
     */
    @Test
    void aRelistingRearmsTheAlarm() {
        final var e = entry();
        e.setWecker(true);

        ScoutList.applyRelisting(e, 2_850_000L, 0L, NEW_DEADLINE);

        assertThat(e.isWecker()).isFalse();
    }

    /** He has not sold. recordSale owns those two columns. */
    @Test
    void aRelistingLeavesTheSaleColumnsAlone() {
        final var e = entry();

        ScoutList.applyRelisting(e, 2_850_000L, 0L, NEW_DEADLINE);

        assertThat(e.getSalePrice()).isNull();
        assertThat(e.getSoldAt()).isNull();
        assertThat(e.isSold()).isFalse();
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `JAVA_HOME=$HOME/.sdkman/candidates/java/17.0.15-sapmchn ./gradlew test --tests "module.transfer.scout.ScoutListTest"`

Expected: compile failure — `cannot find symbol: method applyRelisting`.

- [ ] **Step 3: Add both methods**

In `src/main/java/module/transfer/scout/ScoutList.java`, immediately after `recordSale` (after line 93) and before `isTheWatchedTransfer`, add:

```java
    /**
     * Move a scout entry onto the transfer the seller has just re-listed him on, so the watch list
     * stops presenting a deadline that has already passed as if it were live.
     * <p>
     * Refuses a sold entry: once {@code recordSale} has answered "he went for X", that is the end
     * of what this list is watching, and a later listing by the buyer is somebody else's transfer.
     *
     * @param askingPrice the new asking price in swedish krona
     * @param highestBid  the standing bid, if any, in swedish krona
     * @param deadline    the new deadline
     * @return whether a scout entry for that player was found and updated
     */
    public static boolean recordRelisting(int playerId, long askingPrice, long highestBid,
                                          HODateTime deadline) {
        if (deadline == null) {
            return false;
        }
        final var entries = entries();
        for (final var entry : entries) {
            if (entry.getPlayerID() != playerId) {
                continue;
            }
            if (entry.isSold()) {
                return false;
            }
            applyRelisting(entry, askingPrice, highestBid, deadline);
            save(entries);
            return true;
        }
        return false;
    }

    /**
     * The fields a re-listing changes, and only those. Skills and TSI are deliberately left alone:
     * they ride on the fresh TRANSFERLISTING row, which {@code TransferTools.listScout} joins in.
     * <p>
     * {@code Price} may be a number the user typed themselves - a hand-added entry carries whatever
     * they entered - and this overwrites it. That is intended: the column means "what it would cost
     * to take him now" everywhere else, including {@code TransferSearchConverter}.
     */
    static void applyRelisting(ScoutEintrag entry, long askingPrice, long highestBid,
                               HODateTime deadline) {
        entry.setPrice(new AmountOfMoney(Math.max(askingPrice, highestBid)));
        entry.setDeadline(deadline.toDbTimestamp());
        entry.setWecker(false);   // a has-already-rung latch; left true the alarm never fires again
    }
```

`AmountOfMoney` and `HODateTime` are already imported in this file (`recordSale` uses both).

- [ ] **Step 4: Run the test to verify it passes**

Run: `JAVA_HOME=$HOME/.sdkman/candidates/java/17.0.15-sapmchn ./gradlew test --tests "module.transfer.scout.ScoutListTest"`

Expected: PASS, 4 tests.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/module/transfer/scout/ScoutList.java \
        src/test/java/module/transfer/scout/ScoutListTest.java
git commit -m "feat(scout): record a re-listing on the watched entry"
```

---

### Task 4: The scout pass's decision, as a pure function

**Files:**
- Modify: `src/main/java/core/net/TransferListingResolver.java` (add after `scoutCandidates`, line 144)
- Test: `src/test/java/core/net/TransferListingResolverTest.java`

**Interfaces:**
- Consumes: nothing from earlier tasks.
- Produces:
  - `record TransferListingResolver.ScoutAction(boolean askAboutSale, boolean refreshEntry)` — package-private
  - `static ScoutAction TransferListingResolver.scoutAction(boolean transferListed, HODateTime oldDeadline, HODateTime now)` — package-private, pure

This task is split out from Task 5 on purpose: it is the whole branch table of spec §5.1, it is the part a reviewer can reject on its own, and separating it is what makes the branch table testable without a CHPP stub.

- [ ] **Step 1: Write the failing test**

Append to `src/test/java/core/net/TransferListingResolverTest.java`, inside the class:

```java
    private static final HODateTime NOW = HODateTime.fromHT("2026-09-05 12:00:00");

    /**
     * The sale question is asked whenever the entry's <em>old</em> deadline is still inside
     * SCOUT_RETRY_GRACE, whether or not he is listed again. "He is listed, so he did not sell" is
     * false - a buyer re-listing a player he has just bought is ordinary Hattrick behaviour - and
     * this is the last moment the sale can be recorded: the refresh moves the deadline forward and
     * isTheWatchedTransfer will never line up with that sale again.
     */
    @Test
    void aRelistingInsideTheRetryGraceStillAsksAboutTheSale() {
        final var recent = HODateTime.fromHT("2026-09-01 20:15:00");   // 4 days ago

        final var action = TransferListingResolver.scoutAction(true, recent, NOW);

        assertThat(action.askAboutSale()).isTrue();
        assertThat(action.refreshEntry()).isTrue();
    }

    @Test
    void pastTheRetryGraceTheWatchContinuesAlone() {
        final var old = HODateTime.fromHT("2026-08-01 20:15:00");      // 35 days ago

        final var action = TransferListingResolver.scoutAction(true, old, NOW);

        assertThat(action.askAboutSale()).isFalse();
        assertThat(action.refreshEntry()).isTrue();
    }

    @Test
    void anUnlistedPlayerIsAskedAboutButHisEntryIsLeftAlone() {
        final var recent = HODateTime.fromHT("2026-09-01 20:15:00");

        final var action = TransferListingResolver.scoutAction(false, recent, NOW);

        assertThat(action.askAboutSale()).isTrue();
        assertThat(action.refreshEntry()).isFalse();
    }

    /** Off the market and long past the retry grace: nothing left to do but keep watching. */
    @Test
    void anUnlistedPlayerPastTheRetryGraceCostsNothingBeyondTheLook() {
        final var old = HODateTime.fromHT("2026-08-01 20:15:00");

        final var action = TransferListingResolver.scoutAction(false, old, NOW);

        assertThat(action.askAboutSale()).isFalse();
        assertThat(action.refreshEntry()).isFalse();
    }

    /**
     * {@code scoutCandidates} filters these out, so this is a guard rather than a live path - but
     * the function is package-private and pure, and a null deadline must never become "ask about a
     * sale at no particular date", which is how a player's decade-old transfer ends up recorded as
     * this entry's.
     */
    @Test
    void aDeadlinelessCandidateIsNeverAskedAboutASale() {
        assertThat(TransferListingResolver.scoutAction(true, null, NOW).askAboutSale()).isFalse();
        assertThat(TransferListingResolver.scoutAction(true, null, NOW).refreshEntry()).isTrue();
    }
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `JAVA_HOME=$HOME/.sdkman/candidates/java/17.0.15-sapmchn ./gradlew test --tests "core.net.TransferListingResolverTest"`

Expected: compile failure — `cannot find symbol: method scoutAction`.

- [ ] **Step 3: Add the record and the function**

In `src/main/java/core/net/TransferListingResolver.java`, after `scoutCandidates` and its `Candidate` record (line 147), add:

```java
    /** What the scout pass does about one candidate, once the observation has come back. */
    record ScoutAction(boolean askAboutSale, boolean refreshEntry) {}

    /**
     * The two independent questions a scout candidate raises, decided together so the branch table
     * is one testable thing rather than a shape spread across the loop.
     * <p>
     * They are independent because "he is listed again" does not answer "did he sell": a buyer
     * re-listing a player he has just bought is ordinary Hattrick behaviour, and once the refresh
     * has moved the deadline forward the sale can never be matched to this entry again.
     *
     * @param oldDeadline the deadline the entry was watching, before any refresh
     */
    static ScoutAction scoutAction(boolean transferListed, HODateTime oldDeadline, HODateTime now) {
        final var stillWorthAsking = oldDeadline != null
                && oldDeadline.getInstant().plus(SCOUT_RETRY_GRACE).isAfter(now.getInstant());
        return new ScoutAction(stillWorthAsking, transferListed);
    }
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `JAVA_HOME=$HOME/.sdkman/candidates/java/17.0.15-sapmchn ./gradlew test --tests "core.net.TransferListingResolverTest"`

Expected: PASS, 10 tests (5 pre-existing plus 5 new).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/core/net/TransferListingResolver.java \
        src/test/java/core/net/TransferListingResolverTest.java
git commit -m "feat(resolver): the scout pass branch table as a pure function"
```

---

### Task 5: One observation pass, wired into both loops

**Files:**
- Modify: `src/main/java/core/net/TransferListingResolver.java` — `resolve`, `scoutCandidates`, `fillMissingSkills` (→ `observe` + `observeForCorpus`), and the end of the file (add `recordRelistingOnScoutList`). Anchor on the method names: Task 4 has already shifted every line number in this file.

**Interfaces:**
- Consumes:
  - `XMLPlayersParser.MarketPlayer` and `OnlineWorker.downloadMarketPlayer(String)` — Task 1
  - `TransferListing.fromPlayerDetails(MarketPlayer, HODateTime)` — Task 2
  - `ScoutList.recordRelisting(int, long, long, HODateTime)` — Task 3
  - `ScoutAction`, `scoutAction(boolean, HODateTime, HODateTime)` — Task 4
- Produces: nothing new that later tasks consume. `TransferListingResolver.resolve(int)` keeps its signature.

- [ ] **Step 1: Replace `fillMissingSkills` with `observe` plus a corpus-only wrapper**

In `src/main/java/core/net/TransferListingResolver.java`, replace the whole `fillMissingSkills` method with:

```java
    /**
     * Look at one player on the market and, if he is actually listed, snapshot him into the corpus.
     * <p>
     * The skills-fill pass and the re-listing watch are the same operation - "call playerdetails,
     * and if he is listed store a full row" - so they share one call rather than spending two.
     * <p>
     * Deliberately unguarded. The corpus pass's two short-circuits live at its own call site (see
     * {@link #observeForCorpus}) and must not move in here: a scout entry normally <em>does</em>
     * already have skill data, from the search hit it was created from, and it is exactly the entry
     * worth re-checking.
     *
     * @return what Hattrick said, or null if the call failed
     */
    private static XMLPlayersParser.MarketPlayer observe(int playerId, HODateTime now) {
        try {
            final var m = OnlineWorker.downloadMarketPlayer(String.valueOf(playerId));
            if (m == null || m.player().getPlayerId() == 0) {
                return null;
            }
            if (m.transferListed()) {
                DBManager.instance().storeTransferListing(
                        TransferListing.fromPlayerDetails(m, now));
            }
            return m;
        } catch (Exception e) {
            HOLogger.instance().error(TransferListingResolver.class, e);
            return null;
        }
    }

    /**
     * The corpus pass's use of {@link #observe}, with the two guards that belong to it and not to
     * the watch.
     * <p>
     * A row already backed by a skill-bearing snapshot needs nothing, and a row whose deadline is
     * more than {@link #SKILL_FILL_GRACE} old describes a player who has since been at his new club
     * long enough to train - filing today's skills against that listing would let him vote in a
     * valuation under skills he did not have when he sold.
     * <p>
     * Note what this pass can and cannot do, which the old code obscured: it stores something only
     * when the player happens to be on the market again right now. The rows it was written for are
     * unlisted players by definition, and Hattrick prunes an unlisted player's skills to stamina.
     * That slice of the corpus is unfillable by any means.
     *
     * @return how many CHPP calls this made
     */
    private static int observeForCorpus(TransferListing listing, HODateTime now) {
        if (DBManager.instance().transferListingHasSkillData(listing.getPlayerId())) {
            return 0;
        }
        if (listing.getDeadline() != null && listing.getDeadline().getInstant()
                .plus(SKILL_FILL_GRACE).isBefore(now.getInstant())) {
            return 0;                          // long sold; today's skills are not the sale's
        }
        observe(listing.getPlayerId(), now);
        return 1;
    }
```

- [ ] **Step 2: Add the best-effort scout-list writer**

At the end of the class, immediately after `recordOnScoutList` (the last method in it), add:

```java
    /**
     * If the re-listed player is on the scout list, move his entry onto the new transfer.
     * <p>
     * Best effort, for the same reason {@link #recordOnScoutList} is: a failure here must not undo
     * the TRANSFERLISTING row that {@link #observe} has already stored.
     */
    private static void recordRelistingOnScoutList(int playerId,
                                                   XMLPlayersParser.MarketPlayer m) {
        try {
            ScoutList.recordRelisting(playerId, m.askingPrice(), m.highestBid(), m.deadline());
        } catch (Exception e) {
            HOLogger.instance().error(TransferListingResolver.class, e);
        }
    }
```

- [ ] **Step 3: Add `SCOUT_WATCH_GRACE` and widen the candidate window**

The constant lives here rather than in Task 4 because `scoutCandidates` is its only reader — declaring it a task earlier would leave a dead field in the tree for one commit.

In `src/main/java/core/net/TransferListingResolver.java`, after `SCOUT_RETRY_GRACE` (line 48), add:

```java
    /**
     * How long after its deadline an unsold scout entry is still watched for a re-listing.
     * <p>
     * Longer than {@link #SCOUT_RETRY_GRACE} on purpose: that one bounds "what did he sell for", a
     * question a completed transfer answers immediately, so a fortnight is generous. This bounds
     * "is he back", which a seller may take months to answer - a young prospect withdrawn from the
     * market is exactly the case where the wait is long and the payoff is highest.
     */
    private static final java.time.Duration SCOUT_WATCH_GRACE = java.time.Duration.ofDays(90);
```

Then replace `scoutCandidates` in full with:

```java
    /**
     * Scouted players whose watched transfer has ended: either his sale price is still unknown, or
     * he is worth re-checking in case the seller has put him back on the market.
     * <p>
     * Bounded, because a scout entry has nowhere to record "looked, no sale" the way a listing row
     * does - without a cut-off an entry that never sold would cost a CHPP call on every download
     * for as long as it stayed on the list. {@link #SCOUT_WATCH_GRACE} is the wider of the two
     * windows and so is the one that bounds the candidate set; {@link #scoutAction} decides which
     * of the two questions each candidate is still inside.
     */
    private static List<Candidate> scoutCandidates(HODateTime now) {
        final var giveUpBefore = new HODateTime(now.getInstant().minus(SCOUT_WATCH_GRACE));
        final var candidates = new ArrayList<Candidate>();
        for (final var entry : ScoutList.entries()) {
            if (entry.isSold() || entry.getDeadline() == null) {
                continue;
            }
            final var deadline = HODateTime.fromDbTimestamp(entry.getDeadline());
            if (deadline.compareTo(now) < 0 && deadline.compareTo(giveUpBefore) > 0) {
                candidates.add(new Candidate(entry.getPlayerID(), deadline));
            }
        }
        return candidates;
    }
```

- [ ] **Step 4: Rewire `resolve`'s two loops**

In `resolve`, replace the scout loop:

```java
        for (final var candidate : scoutCandidates(now)) {
            if (spent(calls, cap, deadline)) {
                break;
            }
            if (!seen.add(candidate.playerId())) {
                continue;
            }
            final var outcome = resolvePlayer(candidate.playerId(), candidate.deadline(), now);
            calls += outcome.calls();
            if (outcome.resolved()) {
                resolved++;
            }
        }
```

with:

```java
        // Half the cap, not all of it. SCOUT_WATCH_GRACE is six and a half times SCOUT_RETRY_GRACE,
        // and a fresh ScoutEintrag defaults its deadline to the moment it was created
        // (ScoutEintrag.java:22), so a hand-typed entry is a candidate from day one and stays one
        // for 90 days. Unbounded, a long scout list would eat the whole run and the corpus pass
        // below would never start.
        //
        // The per-candidate cost also doubled with this pass - one observe, plus one resolvePlayer
        // while the entry is still inside SCOUT_RETRY_GRACE - so against the halved cap the scout
        // pass gets through roughly a quarter as many entries per run as it used to. Ample at the
        // default mcpResolveCap of 200; worth knowing if a user turns that right down.
        final var scoutCap = Math.max(1, cap / 2);
        final var looked = new HashSet<Integer>();   // playerdetails fetched for him this run
        for (final var candidate : scoutCandidates(now)) {
            if (spent(calls, scoutCap, deadline)) {
                break;
            }
            if (!looked.add(candidate.playerId())) {
                continue;
            }
            final var observation = observe(candidate.playerId(), now);
            calls++;
            final var action = scoutAction(observation != null && observation.transferListed(),
                    candidate.deadline(), now);
            // The sale question first, and before the refresh: step 3 moves the deadline forward,
            // after which isTheWatchedTransfer can never line this sale up with this entry again.
            //
            // ponytail: no SELLERTEAMID column on TRANSFERLISTING, so this still costs a
            // transfersPlayer call - add the column and compare observation.owningTeamId() against
            // it if the scout list ever grows enough for one call per watched entry to matter.
            if (action.askAboutSale()) {
                // Only here does he become "seen", because seen means "his sale has been asked
                // about this run" and nothing else. Marking him on the look alone would be a
                // regression: SCOUT_WATCH_GRACE is far wider than the window scoutAction asks in,
                // so most candidates fall past this branch, and seen would then make the corpus
                // loop below skip their TRANSFERLISTING rows for up to 90 days - rows the old,
                // narrower candidate window left it free to resolve.
                seen.add(candidate.playerId());
                final var outcome = resolvePlayer(candidate.playerId(), candidate.deadline(), now);
                calls += outcome.calls();
                if (outcome.resolved()) {
                    resolved++;
                }
            }
            if (action.refreshEntry()) {
                recordRelistingOnScoutList(candidate.playerId(), observation);
            }
        }
```

Then, in the corpus loop, replace:

```java
            if (!seen.add(listing.getPlayerId())) {
                continue;                      // already handled this player in this run
            }
            calls += fillMissingSkills(listing, now);
```

with:

```java
            if (!seen.add(listing.getPlayerId())) {
                continue;                      // his sale was already asked about this run
            }
            if (looked.add(listing.getPlayerId())) {
                calls += observeForCorpus(listing, now);
            }
```

The `looked` guard is what stops a second `playerdetails` call on a player the scout pass has just
looked at but did not ask about. `observeForCorpus`'s own `SKILL_FILL_GRACE` check usually catches
that case anyway; this makes it certain, and costs one set lookup.

- [ ] **Step 5: Add the import**

At the top of `TransferListingResolver.java`, beside the existing imports, add:

```java
import core.file.xml.XMLPlayersParser;
```

- [ ] **Step 6: Run the whole suite**

Run: `JAVA_HOME=$HOME/.sdkman/candidates/java/17.0.15-sapmchn ./gradlew test`

Expected: BUILD SUCCESSFUL. The POEditor `"code":"4011"` line in the output is expected locally and is not a failure. If anything fails, fix it before committing — do not proceed with a red suite.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/core/net/TransferListingResolver.java
git commit -m "feat(resolver): watch an unsold scout entry for a re-listing"
```

---

### Task 6: Correct the documentation the old behaviour left wrong

**Files:**
- Modify: `docs/current.md:1199-1204`
- Modify: `docs/chpp_files.md` (new section, before `## CHPP write actionTypes used by HO` at line 114)

**Interfaces:** none.

This is a task rather than a footnote because `docs/current.md` currently records a pass as working that has never stored a single row, and `docs/chpp_files.md` is the only place four live-fetched CHPP facts would otherwise be written down.

- [ ] **Step 1: Correct `docs/current.md`**

Replace the paragraph at `docs/current.md:1199-1204`:

```markdown
The rows that arrive without skills come from `currentbids` (bookmarks, mother-club, finished
transfers, your own listings), which carries market data only, plus the occasional search hit whose
deadline passed before it was fetched — Hattrick strips those to market data too.
`fillMissingSkills` covers them with one `playerdetails` call. Its old guard also required a
`TRANSFERBID` row, which happened to be true for everything from `currentbids` but excluded that
second case; dropping the condition is both simpler and more complete.
```

with:

```markdown
The rows that arrive without skills come from `currentbids` (bookmarks, mother-club, finished
transfers, your own listings), which carries market data only, plus the occasional search hit whose
deadline passed before it was fetched — Hattrick strips those to market data too. An earlier note
here said `fillMissingSkills` covers them with one `playerdetails` call. That was wrong twice over:

- the call never worked for a market player at all. `parsePlayerDetailsFromString` returns `null`
  for anyone outside your own team, so the pass spent one CHPP call per corpus row and stored
  nothing, silently, on every run;
- and it could not have worked even with that guard lifted, because those rows are unlisted players
  by definition and Hattrick prunes an unlisted player's `PlayerSkills` down to `StaminaSkill`.
  Worse, `xmlIntValue` defaults a missing child to `0`, so an unguarded parse would have filed seven
  fabricated zero-skills into the corpus.

That slice of the corpus is unfillable by any means. `observeForCorpus` (formerly
`fillMissingSkills`) is now honest about it: it stores a row only when the player happens to be back
on the market at the moment it runs, and stores nothing otherwise. The same call is what the scout
re-listing watch uses — see `docs/superpowers/specs/2026-09-05-scout-relist-watch-design.md`.
```

- [ ] **Step 2: Add a `playerdetails` section to `docs/chpp_files.md`**

Insert immediately before `## CHPP write actionTypes used by HO` (line 114):

```markdown
## playerdetails (version 3.0)

Requested by `Connector.downloadPlayerDetails`. Answers for **any** player, including a foreign one
on a non-supporter account (`UserSupporterTier: none`), and carries `OwningTeam/TeamID`. This is the
only CHPP file that can be asked about one named player's market status: `transfersearch` has no
`playerId` parameter, and `currentbids` is read-and-delete only (`view` / `ignoreTransfer`, no
add-to-hotlist action).

Four things about it that cost a live fetch to learn:

- **`<TransferDetails />` is present-but-empty when the player is not listed.**
  `getElementsByTagName("TransferDetails").item(0)` returns a non-null `Element` for an unlisted
  player, so a presence check reports everybody as listed. The detection signal is
  `<TransferListed>`, a clean `True`/`False`. `<BidderTeam />` is the same trap one level down when
  there are no bids.
- **`<PlayerSkills>` is pruned, not withheld.** It is present in both responses. An unlisted player's
  contains exactly one child, `<StaminaSkill>` — so stamina is public always and the other seven are
  published only while he is on the market. Since `XMLManager.xmlIntValue` defaults a missing child
  to `0`, parsing the block unconditionally reports seven real skills as zero.
- **`Salary` carries the abroad surcharge.** A player with `<IsAbroad>True</IsAbroad>` reports a
  salary exactly 1.2× the base wage that `transfersearch` reports for the same player. Both files
  report SEK; the ×10 between SEK and a EUR display is a separate currency-rate concern.
- The listed shape is:

  ```xml
  <TransferListed>True</TransferListed>
  <TransferDetails>
    <AskingPrice>2850000</AskingPrice>
    <Deadline>2026-09-05 20:08:31</Deadline>
    <HighestBid>0</HighestBid>
    <BidderTeam />
  </TransferDetails>
  ```

`XMLPlayersParser.parseMarketPlayerFromString` handles all of the above.
`parsePlayerDetailsFromString` is a wrapper over it that returns `null` for a foreign player.
`XMLPlayersParser.createPlayerDetails` reads the same `TransferDetails` block a third time, into the
HRF hash; it is safe only by accident, because `xmlValue` returns `""` for a missing child rather
than throwing.
```

- [ ] **Step 3: Verify the markdown renders**

Run: `JAVA_HOME=$HOME/.sdkman/candidates/java/17.0.15-sapmchn ./gradlew markdownToHtml`

Expected: BUILD SUCCESSFUL. This is the task the build runs over `docs/`, so a malformed fence shows up here.

- [ ] **Step 4: Commit**

```bash
git add docs/current.md docs/chpp_files.md
git commit -m "docs: correct the skills-fill claim, document playerdetails"
```

---

## Known ceilings left in place

None of these is a defect to fix in this plan:

- `ponytail: no SELLERTEAMID column on TRANSFERLISTING, so answering "did he sell?" still costs a transfersPlayer call — add the column and compare observe()'s owningTeamId if the scout list grows.` (spec §7.1; costs a `DBVersion` bump, and the comparison is worthless for every row already in the corpus.)
- `ScoutList.save` touches Swing (`panel.getTransferTable().refresh()`) on whatever thread the resolver runs on. `recordSale` already does this, so the hazard is not new — but a sale happens once, while the watch runs on every download for as long as an entry is watched, so it exercises the path far harder. Worth watching for an EDT complaint in the logs during manual verification.
- The `<TrainerData />` present-but-empty trap is the same shape as §1.2 and is still there in `parseMarketPlayerFromString`. Pre-existing, unrelated to the market path, deliberately left alone.
- **A stale hand-added scout entry now costs one `playerdetails` call per download for 90 days, not 14.** `ScoutEintrag` defaults its deadline to the moment it was created (`ScoutEintrag.java:22`), so an entry typed in by hand is a candidate from day one, and `SCOUT_WATCH_GRACE` keeps it one six and a half times longer than the old window did. Nothing in the UI says why, and the only way out is deleting the entry — the watch has nowhere to record "looked, still not listed". `scoutCap` bounds a single run, not the recurring cost. Fix by giving `SCOUT` a looked-at column if it ever bites, which costs a `DBVersion` bump.

## Manual verification

Unit tests cannot reach a market event. After the suite is green, build and run:

```bash
JAVA_HOME=$HOME/.sdkman/candidates/java/17.0.15-sapmchn ./gradlew dmg
```

1. Put a listed player on the scout list, let his deadline pass unsold, download the team.
   `list_scout` shows a past deadline and no `salePrice`.
2. When he is listed again, download. `list_scout` shows a **future** deadline, the new asking
   price, and an `observedAt` later than the old deadline.
3. Confirm a player who sold still gets his `salePrice` filled. In the awkward case of one who sold
   *and* was re-listed by his buyer before the next download, expect the **sale price only**: the
   pass asks about the sale first, `recordSale` marks the entry sold, and `recordRelisting` then
   refuses it by design (Task 3) — a listing by the buyer is somebody else's transfer, and the watch
   on this entry is over. The fresh `TRANSFERLISTING` row is stored either way, so the corpus loses
   nothing. Wanting that entry to follow the new transfer instead is a change to Task 3's refusal,
   not a bug in this loop.
4. Watch the log for an EDT complaint from `ScoutList.save` while the Transfers tab is open.
5. **Check the `IsAbroad` polarity end to end**, because no unit test can. Task 1's test asserts only
   that `<IsAbroad>True</IsAbroad>` parses to `true`, and Task 2's ÷1.2 is exercised against a
   hand-built `MarketPlayer` — nothing connects "CHPP said True" to "divide". Take one re-listed
   player whose `OwningTeam/LeagueID` differs from his `NativeCountryID`'s league and compare the
   `baseWage` in `list_scout` against what `transfersearch` reports for the same player. They must
   match. If the abroad player's is 1.2× the search figure, the polarity is inverted and the wage
   column is being corrupted silently in a direction nothing downstream would catch.
