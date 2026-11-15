package core.file.xml;

import core.model.match.Matchdetails;
import core.util.ResourceUtils;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.IOException;
import java.util.Arrays;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The same matches, fetched at the old and at the new CHPP version of matchdetails and
 * matchesarchive (2026-11-15, captured from a real team). Everything HO read before must read
 * the same after the version bump; only the fields a version added may differ.
 */
class XMLMatchVersionRegressionTest {

    /** Filled only from the newer versions, so they are left out of the comparison. */
    private static final String[] ADDED_BY_NEWER_VERSIONS = {
            "m_clFetchDatum", "homeChances", "guestChances", "homeHasReplay", "guestHasReplay",
            "homeNrOfReplays", "guestNrOfReplays", "expectedResult"};

    /** Youth matches got the spectator split only with 3.1. */
    private static final String[] YOUTH_SPECTATORS = {"soldTerraces", "soldBasic", "soldRoof", "soldVIP"};

    @ParameterizedTest
    @CsvSource({
            "league, 3.0, 3.2",
            "league, 3.1, 3.2",
            "friendly, 3.0, 3.2",
            "friendly, 3.1, 3.2",
            "youthleague, 3.0, 3.2",
            "youthleague, 3.1, 3.2"})
    void matchdetailsReadTheSameAtTheNewVersion(String match, String oldVersion, String newVersion) throws IOException {
        final var before = parse(match, oldVersion);
        final var after = parse(match, newVersion);

        var ignored = ADDED_BY_NEWER_VERSIONS;
        if (match.startsWith("youth") && "3.0".equals(oldVersion)) {
            ignored = Stream.concat(Arrays.stream(ignored), Arrays.stream(YOUTH_SPECTATORS)).toArray(String[]::new);
        }
        assertThat(after.getHighlights()).hasSameSizeAs(before.getHighlights()).isNotEmpty();
        assertThat(after).usingRecursiveComparison().ignoringFields(ignored).isEqualTo(before);
    }

    @ParameterizedTest
    @CsvSource({"senior", "youth"})
    void matchesArchiveReadsTheSameAtTheNewVersion(String team) throws IOException {
        final var before = XMLMatchArchiveParser.parseMatchesFromString(
                ResourceUtils.getResourceFileAsString("chpp/matchesarchive/versions/" + team + "_1.4.xml"));
        final var after = XMLMatchArchiveParser.parseMatchesFromString(
                ResourceUtils.getResourceFileAsString("chpp/matchesarchive/versions/" + team + "_1.5.xml"));

        assertThat(before).isNotEmpty();
        assertThat(after).usingRecursiveFieldByFieldElementComparator().containsExactlyElementsOf(before);
    }

    /** Both teams' lineups merged into one, as OnlineWorker.downloadMatchlineup does. */
    private static Matchdetails parse(String match, String version) throws IOException {
        final var lineup = XMLMatchLineupParser.parseMatchLineupFromString(
                ResourceUtils.getResourceFileAsString("chpp/matchlineup/versions/" + match + "_home.xml"));
        final var away = XMLMatchLineupParser.parseMatchLineupFromString(
                ResourceUtils.getResourceFileAsString("chpp/matchlineup/versions/" + match + "_away.xml"));
        assertThat(lineup).isNotNull();
        assertThat(away).isNotNull();
        lineup.setGuestTeam(away.getGuestTeam());
        return XMLMatchdetailsParser.parseMatchdetailsFromString(
                ResourceUtils.getResourceFileAsString("chpp/matchdetails/versions/" + match + "_" + version + ".xml"), lineup);
    }
}
