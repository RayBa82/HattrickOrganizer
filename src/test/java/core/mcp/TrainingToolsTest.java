package core.mcp;

import com.google.gson.JsonObject;
import core.constants.player.PlayerSkill;
import core.model.player.Player;
import core.net.ChppWriteService;
import core.util.AmountOfMoney;
import core.util.HODateTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * Like the other tool tests: the bodies need a loaded HOModel and a database, so what is asserted
 * here is the surface the client sees. The set_training body is exercised through a stubbed
 * {@link ChppWriteService}.
 */
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

    private static McpTool tool(String name) {
        return TrainingTools.tools().stream()
                .filter(x -> x.name().equals(name)).findFirst().orElseThrow();
    }

    @Test
    void tools_areTheTrainingTools() {
        assertThat(TrainingTools.tools()).extracting(McpTool::name)
                .containsExactly("get_training", "get_player_history", "set_training");
    }

    private static final core.file.xml.XMLTrainingEventsParser.TrainingEvents TWO_EVENTS =
            new core.file.xml.XMLTrainingEventsParser.TrainingEvents(true, List.of(
                    new core.file.xml.XMLTrainingEventsParser.TrainingEvent(2, 5, 6, 91, 14, 5),
                    new core.file.xml.XMLTrainingEventsParser.TrainingEvent(8, 6, 7, 92, 5, 5)));

    @Test
    void trainingEventsJson_newestFirstWithChppSkillNames() {
        final var events = TrainingTools.trainingEventsJson(4711, TWO_EVENTS, 0)
                .getAsJsonArray("events");
        final var newest = events.get(0).getAsJsonObject();
        assertThat(newest.get("skill").getAsString()).isEqualTo("playmaking");   // CHPP 8, HO 3
        assertThat(newest.get("oldLevel").getAsInt()).isEqualTo(6);
        assertThat(newest.get("newLevel").getAsInt()).isEqualTo(7);
        assertThat(events.get(1).getAsJsonObject().get("skill").getAsString()).isEqualTo("stamina");
    }

    /** Hattrick reports the local season; one season of offset is sixteen weeks of calendar. */
    @Test
    void trainingEventsJson_weekStartHonoursTheSeasonOffset() {
        final var local = core.util.HODateTime.fromHT(TrainingTools.trainingEventsJson(1, TWO_EVENTS, 0)
                .getAsJsonArray("events").get(0).getAsJsonObject().get("weekStart").getAsString());
        final var shifted = core.util.HODateTime.fromHT(TrainingTools.trainingEventsJson(1, TWO_EVENTS, 1)
                .getAsJsonArray("events").get(0).getAsJsonObject().get("weekStart").getAsString());
        assertThat(java.time.Duration.between(shifted.getInstant(), local.getInstant()).toDays())
                .isEqualTo(16 * 7);
        assertThat(TrainingTools.trainingEventsJson(1, TWO_EVENTS, null)
                .getAsJsonArray("events").get(0).getAsJsonObject().has("weekStart")).isFalse();
    }

    @Test
    void getTraining_takesNoRequiredArguments() {
        final var getTraining = tool("get_training");

        assertThat(getTraining.inputSchema().getAsJsonArray("required")).isEmpty();
        final var properties = getTraining.inputSchema().getAsJsonObject("properties");
        assertThat(properties.has("weeks")).isTrue();
        assertThat(properties.has("playerIds")).isTrue();
        assertThat(getTraining.description()).isNotBlank();
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
                .isThrownBy(() -> tool("set_training").handler().call(args(3, 150, 10)))
                .withMessageContaining("0..100");
    }

    @Test
    void setTraining_echoesTheCommittedValues() throws Exception {
        installService(new ChppWriteService() {
            @Override
            public TrainingCommitted setTrainingForAgent(int t, int i, int s) {
                return new TrainingCommitted(t, i, s, false);
            }
        });
        final var result = tool("set_training").handler().call(args(3, 40, 10));
        assertThat(result.get("status").getAsString()).isEqualTo("ok");
        assertThat(result.getAsJsonObject("committed").get("intensity").getAsInt()).isEqualTo(40);
        assertThat(result.get("note").getAsString()).contains("not refreshed");
    }

    // ---- get_player_history ------------------------------------------------------------------
    //
    // The database read is left to the handler; what is worth pinning down is the diffing and the
    // coverage arithmetic, since a growth rate quoted off a series with holes in it is how a
    // caller ends up selling a player who is still improving.

    /** A euro league, so the wage drift is visible in the user's currency. */
    @BeforeEach
    void euroLeague() {
        AmountOfMoney.Companion.setExchangeRate(BigDecimal.TEN);
    }

    @Test
    void getPlayerHistory_takesNoRequiredArgumentsAndDefaultsToDerivedOnly() {
        final var tool = tool("get_player_history");

        assertThat(tool.inputSchema().getAsJsonArray("required")).isEmpty();
        final var properties = tool.inputSchema().getAsJsonObject("properties");
        assertThat(properties.has("playerIds")).isTrue();
        assertThat(properties.has("weeks")).isTrue();
        assertThat(properties.has("includeSnapshots")).isTrue();
        assertThat(properties.getAsJsonObject("includeSnapshots").get("description").getAsString())
                .contains("default false");
    }

    /**
     * One snapshot: passing at {@code level + sub}, taken {@code daysAgo} before now so the window
     * arithmetic is relative to something stable.
     */
    private static Player snapshot(String date, int age, int passing, double sub, int tsi, long wage) {
        return snapshot(HODateTime.fromHT(date), age, passing, sub, tsi, wage);
    }

    private static Player snapshot(HODateTime date, int age, int passing, double sub, int tsi,
                                   long wage) {
        final var player = new Player();
        player.setPlayerId(475805295);
        player.setFirstName("Adrián");
        player.setLastName("Walter");
        player.setHrfDate(date);
        player.setAge(age);
        player.setAgeDays(10);
        player.setValue4Skill(PlayerSkill.PASSING, passing);
        player.setSubskill4PlayerSkill(PlayerSkill.PASSING, sub);
        player.setTsi(tsi);
        player.setWage(new AmountOfMoney(wage));
        return player;
    }

    /** Seventeen weekly snapshots - exactly one Hattrick year - with passing climbing 5.0 to 7.0. */
    private static List<Player> oneHattrickYear() {
        final var start = HODateTime.fromHT("2026-01-01 12:00:00");
        final var series = new ArrayList<Player>();
        for (int week = 0; week <= 16; week++) {
            final var precise = 5.0 + week * (2.0 / 16);
            series.add(snapshot(start.plus(week * 7, ChronoUnit.DAYS), 19,
                    (int) Math.floor(precise), precise - Math.floor(precise),
                    4200 + week * 300, 9000 + week * 20L));
        }
        return series;
    }

    @Test
    void playerHistory_ratesTwoLevelsInSixteenWeeksAsTwoLevelsPerHattrickYear() {
        final var json = TrainingTools
                .historyOf(475805295, oneHattrickYear(), PlayerSkill.PASSING, false);
        final var derived = json.getAsJsonObject("derived");

        assertThat(json.get("status").getAsString()).isEqualTo("ok");
        assertThat(json.get("name").getAsString()).isEqualTo("Adrián Walter");
        assertThat(derived.get("trainedSkill").getAsString()).isEqualTo("PASSING");
        assertThat(derived.get("trainedSkillStart").getAsDouble()).isEqualTo(5.0);
        assertThat(derived.get("trainedSkillEnd").getAsDouble()).isEqualTo(7.0);
        assertThat(derived.get("trainedSkillLevelUps").getAsInt()).isEqualTo(2);

        // sixteen observed weeks is one Hattrick year, so the rate is the raw gain
        assertThat(json.getAsJsonObject("coverage").get("observedWeeks").getAsDouble())
                .isEqualTo(16.0);
        assertThat(derived.get("levelsPerHtYear").getAsDouble()).isEqualTo(2.0);
        assertThat(derived.get("rateBasis").getAsString()).isEqualTo("subskill");
        // weekly downloads leave no hole worth reporting
        assertThat(json.getAsJsonObject("coverage").getAsJsonArray("gaps")).isEmpty();
    }

    /**
     * A player HO never replayed training for carries zero in every sub-skill, and the rate then
     * quietly becomes a count of whole levels while still looking like a measured float. Nothing in
     * the numbers gives that away - a genuine 5.00 and an estimate-less 5 are the same value - so
     * rateBasis has to say it outright.
     */
    @Test
    void playerHistory_saysWhenTheRateIsWholeLevelsBecauseHoHasNoSubSkills() {
        final var start = HODateTime.fromHT("2026-01-01 12:00:00");
        final var noSubs = new ArrayList<Player>();
        for (int week = 0; week <= 16; week++) {
            noSubs.add(snapshot(start.plus(week * 7, ChronoUnit.DAYS), 19,
                    5 + week / 8, 0.0, 4200, 9000));
        }

        final var derived = TrainingTools.historyOf(1, noSubs, PlayerSkill.PASSING, false)
                .getAsJsonObject("derived");

        assertThat(derived.get("rateBasis").getAsString()).isEqualTo("integer");
        // the number still arrives, and still looks exactly like a measured one
        assertThat(derived.get("levelsPerHtYear").getAsDouble()).isEqualTo(2.0);
    }

    /** One populated snapshot is enough: the replay fills a player's rows all at once. */
    @Test
    void playerHistory_callsTheBasisSubskillWhenTheEndpointsHappenToBeWholeLevels() {
        final var series = List.of(
                snapshot("2026-01-01 12:00:00", 19, 5, 0.0, 4200, 9000),
                snapshot("2026-02-01 12:00:00", 19, 5, 0.4, 4500, 9000),
                snapshot("2026-03-01 12:00:00", 19, 6, 0.0, 4900, 9200));

        assertThat(TrainingTools.historyOf(1, series, PlayerSkill.PASSING, false)
                .getAsJsonObject("derived").get("rateBasis").getAsString()).isEqualTo("subskill");
    }

    @Test
    void playerHistory_reportsTsiAndWageDriftInTheUsersCurrency() {
        final var derived = TrainingTools
                .historyOf(475805295, oneHattrickYear(), PlayerSkill.PASSING, false)
                .getAsJsonObject("derived");

        assertThat(derived.getAsJsonObject("tsi").get("start").getAsInt()).isEqualTo(4200);
        assertThat(derived.getAsJsonObject("tsi").get("end").getAsInt()).isEqualTo(9000);
        assertThat(derived.getAsJsonObject("tsi").get("change").getAsInt()).isEqualTo(4800);
        // 9000 krona in, 9320 out, a tenth of each in a euro league
        assertThat(derived.getAsJsonObject("wage").get("start").getAsLong()).isEqualTo(900L);
        assertThat(derived.getAsJsonObject("wage").get("end").getAsLong()).isEqualTo(932L);
    }

    @Test
    void playerHistory_datesEveryLevelChangeIncludingADrop() {
        final var series = List.of(
                snapshot("2026-01-01 12:00:00", 30, 9, 0.9, 5000, 9000),
                snapshot("2026-02-01 12:00:00", 30, 10, 0.1, 5200, 9400),
                snapshot("2026-03-01 12:00:00", 31, 9, 0.8, 4800, 9400));

        final var levelUps = TrainingTools
                .historyOf(1, series, PlayerSkill.PASSING, false).getAsJsonArray("levelUps");

        assertThat(levelUps).hasSize(2);
        final var up = levelUps.get(0).getAsJsonObject();
        assertThat(up.get("skill").getAsString()).isEqualTo("PASSING");
        assertThat(up.get("from").getAsInt()).isEqualTo(9);
        assertThat(up.get("to").getAsInt()).isEqualTo(10);
        assertThat(up.get("date").getAsString()).startsWith("2026-02-01");
        assertThat(up.get("ageAtChange").getAsInt()).isEqualTo(30);
        // a skill that fell back is reported the same way, from above to below
        final var down = levelUps.get(1).getAsJsonObject();
        assertThat(down.get("from").getAsInt()).isEqualTo(10);
        assertThat(down.get("to").getAsInt()).isEqualTo(9);
        // only the rise counts as a level-up
        assertThat(TrainingTools.historyOf(1, series, PlayerSkill.PASSING, false)
                .getAsJsonObject("derived").get("trainedSkillLevelUps").getAsInt()).isEqualTo(1);
    }

    /** The newest snapshot before the training against the newest one; older changes do not count. */
    @Test
    void trainingDiff_comparesAcrossTheLastTrainingOnly() {
        final var lastTraining = HODateTime.fromHT("2026-03-05 08:00:00");
        final var series = List.of(
                snapshot("2026-02-20 12:00:00", 30, 8, 0.9, 5000, 9000),
                snapshot("2026-03-01 12:00:00", 30, 9, 0.9, 5000, 9000),
                snapshot("2026-03-04 12:00:00", 30, 9, 0.95, 5000, 9000),
                snapshot("2026-03-06 12:00:00", 30, 10, 0.1, 5200, 9400));

        final var row = TrainingTools.trainingDiff(1, series, lastTraining);

        assertThat(row.has("reason")).isFalse();
        assertThat(row.get("baselineDate").getAsString()).startsWith("2026-03-04");
        final var levelUps = row.getAsJsonArray("levelUps");
        assertThat(levelUps).hasSize(1);
        assertThat(levelUps.get(0).getAsJsonObject().get("from").getAsInt()).isEqualTo(9);
        assertThat(levelUps.get(0).getAsJsonObject().get("to").getAsInt()).isEqualTo(10);
    }

    @Test
    void eventsSince_keepsTheWindowAndEventsWithoutAWeek() {
        final var events = com.google.gson.JsonParser.parseString("""
                {"playerId":1,"available":true,"events":[
                  {"skill":"PASSING","weekStart":"2026-09-01 00:00:00"},
                  {"skill":"SCORING","weekStart":"2025-01-01 00:00:00"},
                  {"skill":"WINGER"}]}""").getAsJsonObject();

        final var window = new TrainingTools.Window(HODateTime.fromHT("2026-06-01 00:00:00"),
                HODateTime.fromHT("2026-10-01 00:00:00"));
        final var kept = TrainingTools.eventsIn(events, window).getAsJsonArray("events");

        assertThat(kept).extracting(e -> e.getAsJsonObject().get("skill").getAsString())
                .containsExactly("PASSING", "WINGER");

        // A window ending before an event leaves it out: weeksAgo pages back past it.
        final var older = new TrainingTools.Window(HODateTime.fromHT("2025-06-01 00:00:00"),
                HODateTime.fromHT("2026-06-01 00:00:00"));
        final var events2 = com.google.gson.JsonParser.parseString("""
                {"events":[{"skill":"PASSING","weekStart":"2026-09-01 00:00:00"},
                           {"skill":"SCORING","weekStart":"2026-01-01 00:00:00"}]}""").getAsJsonObject();
        assertThat(TrainingTools.eventsIn(events2, older).getAsJsonArray("events"))
                .extracting(e -> e.getAsJsonObject().get("skill").getAsString())
                .containsExactly("SCORING");
    }

    /** The screen is derived and coverage cut to what a many-trainee screen reads, and no more. */
    @Test
    void addScreen_copiesTheGrowthAndCoverageSignals() {
        final var row = TrainingTools.historyOf(1, oneHattrickYear(), PlayerSkill.PASSING, false);

        TrainingTools.addScreen(row);

        final var screen = row.getAsJsonObject("screen");
        assertThat(screen.keySet()).containsExactlyInAnyOrder("levelsPerHtYear", "rateBasis",
                "weeksSinceLastLevelUp", "daysSinceLastSnapshot", "gapCount");
        assertThat(screen.get("levelsPerHtYear"))
                .isEqualTo(row.getAsJsonObject("derived").get("levelsPerHtYear"));
        assertThat(screen.get("gapCount").getAsInt())
                .isEqualTo(row.getAsJsonObject("coverage").getAsJsonArray("gaps").size());
    }

    @Test
    void trainingDiff_namesTheMissingSide() {
        final var lastTraining = HODateTime.fromHT("2026-03-05 08:00:00");
        final var before = snapshot("2026-03-01 12:00:00", 30, 9, 0.9, 5000, 9000);
        final var after = snapshot("2026-03-06 12:00:00", 30, 9, 0.9, 5000, 9000);

        assertThat(TrainingTools.trainingDiff(1, List.of(before), lastTraining)
                .get("reason").getAsString()).isEqualTo("no snapshot since the last training");
        assertThat(TrainingTools.trainingDiff(1, List.of(after), lastTraining)
                .get("reason").getAsString()).isEqualTo("no snapshot from before the last training");
        assertThat(TrainingTools.trainingDiff(1, List.of(), lastTraining)
                .get("reason").getAsString()).isEqualTo("no snapshot");
    }

    /**
     * The load-bearing case. A fortnight with no download hides a training update, so the caller
     * has to be able to see the hole rather than read a confident rate off it.
     */
    @Test
    void playerHistory_reportsHolesInTheSeries() {
        final var series = List.of(
                snapshot("2026-01-01 12:00:00", 19, 5, 0.0, 4200, 9000),
                snapshot("2026-01-08 12:00:00", 19, 5, 0.2, 4300, 9000),
                // three weeks with no download
                snapshot("2026-01-29 12:00:00", 19, 6, 0.1, 4900, 9200),
                snapshot("2026-02-05 12:00:00", 19, 6, 0.3, 5000, 9200));

        final var coverage = TrainingTools
                .historyOf(1, series, PlayerSkill.PASSING, false).getAsJsonObject("coverage");

        assertThat(coverage.get("snapshotCount").getAsInt()).isEqualTo(4);
        assertThat(coverage.get("firstDate").getAsString()).startsWith("2026-01-01");
        assertThat(coverage.get("lastDate").getAsString()).startsWith("2026-02-05");
        assertThat(coverage.get("observedWeeks").getAsDouble()).isEqualTo(5.0);

        final var gaps = coverage.getAsJsonArray("gaps");
        assertThat(gaps).hasSize(1);
        assertThat(gaps.get(0).getAsJsonObject().get("from").getAsString()).startsWith("2026-01-08");
        assertThat(gaps.get(0).getAsJsonObject().get("to").getAsString()).startsWith("2026-01-29");
        assertThat(gaps.get(0).getAsJsonObject().get("days").getAsDouble()).isEqualTo(21.0);
    }

    @Test
    void playerHistory_refusesToQuoteARateOffTooShortASpan() {
        final var series = List.of(
                snapshot("2026-01-01 12:00:00", 19, 5, 0.0, 4200, 9000),
                snapshot("2026-01-03 12:00:00", 19, 6, 0.0, 4900, 9200));

        final var json = TrainingTools.historyOf(1, series, PlayerSkill.PASSING, false);

        // two days apart: one level over that span would extrapolate to nearly sixty a year
        assertThat(json.getAsJsonObject("derived").get("levelsPerHtYear").isJsonNull()).isTrue();
        assertThat(json.getAsJsonObject("derived").get("trainedSkillLevelUps").getAsInt())
                .isEqualTo(1);
    }

    @Test
    void playerHistory_saysSoWhenThereIsNothingToReport() {
        final var empty = TrainingTools.historyOf(999, List.of(), PlayerSkill.PASSING, false);
        assertThat(empty.get("status").getAsString()).isEqualTo("no_history");
        assertThat(empty.has("derived")).isFalse();

        final var one = TrainingTools.historyOf(1,
                List.of(snapshot("2026-01-01 12:00:00", 19, 5, 0.0, 4200, 9000)),
                PlayerSkill.PASSING, false);
        assertThat(one.get("status").getAsString()).isEqualTo("single_snapshot");
        assertThat(one.getAsJsonArray("levelUps")).isEmpty();
        assertThat(one.getAsJsonObject("derived").get("levelsPerHtYear").isJsonNull()).isTrue();
        assertThat(one.getAsJsonObject("derived").get("weeksSinceLastLevelUp").isJsonNull()).isTrue();
    }

    @Test
    void playerHistory_keepsTheRawSeriesBehindTheFlag() {
        final var series = oneHattrickYear();

        assertThat(TrainingTools.historyOf(1, series, PlayerSkill.PASSING, false)
                .has("snapshots")).isFalse();

        final var snapshots = TrainingTools.historyOf(1, series, PlayerSkill.PASSING, true)
                .getAsJsonArray("snapshots");
        assertThat(snapshots).hasSize(series.size());
        final var first = snapshots.get(0).getAsJsonObject();
        assertThat(first.get("date").getAsString()).startsWith("2026-01-01");
        // the sub-skill rides along, so the curve does not have to wait for a whole level
        assertThat(first.getAsJsonObject("skills").get("PASSING").getAsDouble()).isEqualTo(5.0);
        assertThat(snapshots.get(8).getAsJsonObject().getAsJsonObject("skills")
                .get("PASSING").getAsDouble()).isEqualTo(6.0);
    }

    @Test
    void playerHistory_leavesTheTrainedSkillBlockOutWhenNothingIsTrained() {
        final var derived = TrainingTools.historyOf(1, oneHattrickYear(), null, false)
                .getAsJsonObject("derived");

        assertThat(derived.get("trainedSkill").isJsonNull()).isTrue();
        assertThat(derived.has("levelsPerHtYear")).isFalse();
        // the money drift does not depend on what is being trained
        assertThat(derived.getAsJsonObject("tsi").get("change").getAsInt()).isEqualTo(4800);
    }

    /**
     * The one thing in currentWeek that is arithmetic rather than a database read, and the one
     * that decides whether a lineup builder excludes a player: two fixtures in the same training
     * week must not bank more than a week's worth of trained minutes.
     */
    @Test
    void weekMinutes_capTrainedMinutesAtNinetyAcrossTheWeeksFixtures() {
        final var minutes = new TrainingTools.WeekMinutes("Di Tella");
        minutes.add(90, 0, 90, 768523823);
        minutes.add(90, 30, 90, 771424338);

        final var json = minutes.toJson(510470571);
        assertThat(json.get("fullTrainingMinutes").getAsInt()).isEqualTo(90);
        assertThat(json.get("partlyTrainingMinutes").getAsInt()).isEqualTo(30);
        assertThat(json.get("maxedForWeek").getAsBoolean()).isTrue();
        // played minutes are a match count, not a training budget, so they are not capped
        assertThat(json.get("playedMinutes").getAsInt()).isEqualTo(180);
        assertThat(json.getAsJsonArray("matchIds")).hasSize(2);
    }

    /** Partial minutes have to survive to the client: 60 is still worth starting, 90 is not. */
    @Test
    void weekMinutes_reportPartialTrainedMinutesRatherThanCollapsingToTheFlag() {
        final var minutes = new TrainingTools.WeekMinutes("Bench Warmer");
        minutes.add(60, 0, 60, 768523823);

        final var json = minutes.toJson(1);
        assertThat(json.get("fullTrainingMinutes").getAsInt()).isEqualTo(60);
        assertThat(json.get("maxedForWeek").getAsBoolean()).isFalse();
    }
}
