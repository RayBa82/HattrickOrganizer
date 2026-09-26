package core.net;

import java.util.regex.Pattern;

/**
 * A CHPP error, parsed out of the string {@link core.file.xml.XMLCHPPPreParser#getError} builds
 * ("CHPP &lt;Fehler&gt; - &lt;code&gt; - &lt;Hattrick's Error text&gt;"), which is also what
 * {@link Connector#lastError()} holds. Hattrick's own text ends in
 * "(&lt;code&gt;) Additional Info: &lt;detail&gt;", the detail often being "null".
 * <p>
 * {@link #readable()} is what the MCP write tools report: the {@link Reason} first, so an agent
 * can branch on it, then what to do, then Hattrick's words. Codes are from the CHPP docs index,
 * see docs/chpp_files.md.
 */
public record ChppError(int code, Reason reason, String text, String additionalInfo) {

    public enum Reason {
        NOT_LOGGED_IN("the CHPP login is missing or expired; re-authorize HO"),
        ACCESS_DENIED("Hattrick denied access to this file or action"),
        SUPPORTER_REQUIRED("this action needs Hattrick Supporter on the account HO is logged in "
                + "with; nothing in HO can work around it"),
        INVALID_PARAMETER("Hattrick refused a parameter value"),
        MISSING_PARAMETERS("a required parameter was missing"),
        UNKNOWN_ID("Hattrick does not know the team, match, player or league id"),
        NOT_YOUR_TEAM("the team is not managed by the logged-in user"),
        OPPONENT_FRIENDLY_BOOKED("the opponent already has a friendly this week; try the next team"),
        OWN_FRIENDLY_BOOKED("our team already has a friendly booked this week; stop, no other "
                + "opponent will work"),
        CHALLENGE_FAILED("the challenge failed"),
        BID_FAILED("the bid failed"),
        BANNED_INTERNATIONAL_FRIENDLY("the team is banned from international friendlies"),
        BANNED_TRANSFERS("the team is banned from transfers"),
        MAINTENANCE("Hattrick (or this part of it) is down for maintenance; retry later"),
        OTHER("unclassified CHPP error");

        private final String hint;

        Reason(String hint) {
            this.hint = hint;
        }
    }

    private static final Pattern SHAPE = Pattern.compile(" - (\\d+) - (.*)", Pattern.DOTALL);
    private static final Pattern INFO = Pattern.compile("\\(\\d+\\) Additional Info:(.*)", Pattern.DOTALL);

    /** The parsed error, or null when {@code raw} does not carry a CHPP error code. */
    public static ChppError parse(String raw) {
        if (raw == null) {
            return null;
        }
        final var m = SHAPE.matcher(raw);
        if (!m.find()) {
            return null;
        }
        final var code = Integer.parseInt(m.group(1));
        var text = m.group(2).trim();
        var info = "";
        final var i = INFO.matcher(text);
        if (i.find()) {
            info = i.group(1).trim();
            text = text.substring(0, i.start()).trim();
        }
        if (info.equals("null")) {
            info = "";
        }
        return new ChppError(code, reasonFor(code, text + " " + info), text, info);
    }

    /** {@code raw} rewritten as {@link #readable()}, or unchanged if it carries no CHPP code. */
    public static String readable(String raw) {
        final var e = parse(raw);
        return e == null ? raw : e.readable();
    }

    public String readable() {
        final var said = (text + (additionalInfo.isEmpty() ? "" : " - " + additionalInfo)).trim();
        return reason + " (CHPP " + code + "): " + reason.hint
                + (said.isEmpty() ? "." : ". Hattrick said: " + said);
    }

    private static Reason reasonFor(int code, String words) {
        final var w = words.toLowerCase();
        return switch (code) {
            case 0 -> Reason.NOT_LOGGED_IN;
            case 1 -> Reason.ACCESS_DENIED;
            case 6 -> Reason.SUPPORTER_REQUIRED;
            case 10 -> Reason.INVALID_PARAMETER;
            case 63 -> Reason.MISSING_PARAMETERS;
            case 50, 51, 54, 55, 56, 57, 58, 60, 61 -> Reason.UNKNOWN_ID;
            case 59 -> Reason.NOT_YOUR_TEAM;
            case 70 -> challengeReason(w);
            case 71 -> Reason.BID_FAILED;
            case 80 -> Reason.BANNED_INTERNATIONAL_FRIENDLY;
            case 81 -> Reason.BANNED_TRANSFERS;
            case 90, 91, 92, 93 -> Reason.MAINTENANCE;
            default -> Reason.OTHER;
        };
    }

    /**
     * Error 70 covers both sides' slot; only Hattrick's text tells them apart. Both texts were seen
     * live (one came back in German, one in English), see docs/current.md.
     */
    private static Reason challengeReason(String words) {
        if (words.contains("your team already")) {
            return Reason.OWN_FRIENDLY_BOOKED;
        }
        if (words.contains("bereits ein freundschaftsspiel")) {
            return Reason.OPPONENT_FRIENDLY_BOOKED;
        }
        return Reason.CHALLENGE_FAILED;
    }
}
