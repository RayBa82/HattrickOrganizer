package core.net;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The raw strings are verbatim from HO's log (error 6, 56) or from what the hattrick session saw
 * Hattrick answer to a challenge (the two error-70 texts). Their shape is XMLCHPPPreParser.getError:
 * "CHPP <tr(Fehler)> - <code> - <Hattrick's Error text>".
 */
class ChppErrorTest {

    @Test
    void notASupporter_carriesCodeTextAndAdditionalInfo() {
        final var e = ChppError.parse("CHPP Fehler - 6 - This action requires the logged in user to be "
                + "a Supporter (6) Additional Info: Action type setmatchorder is only available for "
                + "Hattrick Supporters");
        assertThat(e.code()).isEqualTo(6);
        assertThat(e.reason()).isEqualTo(ChppError.Reason.SUPPORTER_REQUIRED);
        assertThat(e.text()).isEqualTo("This action requires the logged in user to be a Supporter");
        assertThat(e.additionalInfo())
                .isEqualTo("Action type setmatchorder is only available for Hattrick Supporters");
    }

    @Test
    void error70_opponentAlreadyBooked_meansTryTheNextTeam() {
        final var e = ChppError.parse("CHPP Error - 70 - Challenge failed (70) Additional Info: "
                + "Hat bereits ein Freundschaftsspiel vereinbart");
        assertThat(e.reason()).isEqualTo(ChppError.Reason.OPPONENT_FRIENDLY_BOOKED);
        assertThat(e.readable()).startsWith("OPPONENT_FRIENDLY_BOOKED (CHPP 70)")
                .contains("try the next team")
                .contains("Hat bereits ein Freundschaftsspiel vereinbart");
    }

    @Test
    void error70_ourSlotBooked_meansStop() {
        final var e = ChppError.parse("CHPP Error - 70 - Challenge failed (70) Additional Info: "
                + "Your team already has booked a friendly this week");
        assertThat(e.reason()).isEqualTo(ChppError.Reason.OWN_FRIENDLY_BOOKED);
        assertThat(e.readable()).contains("stop");
    }

    @Test
    void error70_withAnUnknownText_staysAGenericChallengeFailure() {
        assertThat(ChppError.parse("CHPP Error - 70 - Challenge failed (70) Additional Info: other")
                .reason()).isEqualTo(ChppError.Reason.CHALLENGE_FAILED);
    }

    @Test
    void additionalInfoNull_isDropped() {
        final var e = ChppError.parse("CHPP Fehler - 56 -  (56) Additional Info: null");
        assertThat(e.reason()).isEqualTo(ChppError.Reason.UNKNOWN_ID);
        assertThat(e.additionalInfo()).isEmpty();
        assertThat(e.readable()).doesNotContain("null");
    }

    @Test
    void codesMapToTheDocumentedReasons() {
        assertThat(ChppError.parse("CHPP Error - 71 - Bid failed").reason())
                .isEqualTo(ChppError.Reason.BID_FAILED);
        assertThat(ChppError.parse("CHPP Error - 80 - x").reason())
                .isEqualTo(ChppError.Reason.BANNED_INTERNATIONAL_FRIENDLY);
        assertThat(ChppError.parse("CHPP Error - 92 - x").reason())
                .isEqualTo(ChppError.Reason.MAINTENANCE);
        assertThat(ChppError.parse("CHPP Error - 59 - x").reason())
                .isEqualTo(ChppError.Reason.NOT_YOUR_TEAM);
        assertThat(ChppError.parse("CHPP Error - 4242 - x").reason())
                .isEqualTo(ChppError.Reason.OTHER);
    }

    @Test
    void aStringWithoutACode_isPassedThroughUnchanged() {
        final var raw = "HTTP Response Code 401: CHPP Connection failed.";
        assertThat(ChppError.readable(raw)).isEqualTo(raw);
        assertThat(ChppError.readable(null)).isNull();
    }
}
