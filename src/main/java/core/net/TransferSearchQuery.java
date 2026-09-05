package core.net;

import core.constants.player.PlayerSkill;
import lombok.Builder;
import lombok.NonNull;

/**
 * Search parameters of the CHPP <code>transfersearch</code> file, version 1.1.
 * <p>
 * Age and one skill are mandatory, everything else narrows the search down. Hattrick enforces
 * a few rules server side, so an invalid combination comes back as an error file, not as an
 * empty result: a skill window may span at most 4 levels <em>counted inclusively</em>, so min and
 * max may differ by at most 3 - a difference of 4 is answered with "No primary skill with min and
 * max range has been specified", whatever the docs say. Set pieces / leadership / experience need
 * a minimum level of 5 and keeper a minimum of 3. An age range that reaches down to 17 or 18 may
 * span at most one year (17-18 and 18-19 are fine, 17-19 is not, and is answered with "Invalid
 * parameter specified: ageMin"). From 19 up a two-year range works (19-21, 20-22, and Hattrick's
 * own 20-22 example); whether there is a wider ceiling there is not yet known, so an over-wide
 * 19+ range is not caught here and still falls through to the raw CHPP error.
 *
 * @see Connector#getTransferSearch(TransferSearchQuery)
 */
@Builder
public record TransferSearchQuery(
        @NonNull Integer ageMin,
        @NonNull Integer ageMax,
        @NonNull Skill skillType1,
        @NonNull Integer minSkillValue1,
        @NonNull Integer maxSkillValue1,

        Integer ageDaysMin,
        Integer ageDaysMax,
        Skill skillType2,
        Integer minSkillValue2,
        Integer maxSkillValue2,
        Skill skillType3,
        Integer minSkillValue3,
        Integer maxSkillValue3,
        Skill skillType4,
        Integer minSkillValue4,
        Integer maxSkillValue4,
        /** One of the {@link core.constants.player.PlayerSpeciality} constants, they share the CHPP numbering. */
        Integer specialty,
        Integer nativeCountryId,
        Integer tsiMin,
        Integer tsiMax,
        Integer priceMin,
        Integer priceMax,
        Integer pageSize,
        Integer pageIndex
) {

    /**
     * Sent when the caller sets no page size. Hattrick's own documented default is 25, the maximum
     * is 100 - and a search that silently stops at the first page hides candidates, so HO always
     * asks for the maximum.
     */
    private static final int DEFAULT_PAGE_SIZE = 100;

    /**
     * The skills the transfer market can be searched by, with the CHPP skillID numbering, which
     * is not the {@link PlayerSkill} one. Form and loyalty cannot be searched for.
     */
    public enum Skill {
        KEEPER(1, PlayerSkill.KEEPER),
        STAMINA(2, PlayerSkill.STAMINA),
        SETPIECES(3, PlayerSkill.SETPIECES),
        DEFENDING(4, PlayerSkill.DEFENDING),
        SCORING(5, PlayerSkill.SCORING),
        WINGER(6, PlayerSkill.WINGER),
        PASSING(7, PlayerSkill.PASSING),
        PLAYMAKING(8, PlayerSkill.PLAYMAKING),
        LEADERSHIP(10, PlayerSkill.LEADERSHIP),
        EXPERIENCE(11, PlayerSkill.EXPERIENCE);

        private final int chppId;
        private final PlayerSkill playerSkill;

        Skill(int chppId, PlayerSkill playerSkill) {
            this.chppId = chppId;
            this.playerSkill = playerSkill;
        }

        /** The matching HO skill, for the translated name and for reading the value off a player. */
        public PlayerSkill toPlayerSkill() {
            return playerSkill;
        }
    }

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
        // Hattrick clamps how wide the youth end can be searched: a range that includes 17 or 18
        // may span at most one year, or it comes back as "Invalid parameter specified: ageMin".
        if (ageMin <= 18 && ageMax - ageMin > 1) {
            return "ls.transfersearch.error.youngagespan";
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
        if (min > max || max - min > 3) {
            return "ls.transfersearch.error.skillrange";
        }
        if (min < minimumLevel(skill)) {
            return "ls.transfersearch.error.minlevel";
        }
        return null;
    }

    /**
     * The query parameters of this search, unset ones omitted so that Hattrick applies its own
     * defaults. Starts with an ampersand, to be appended to the file and version parameters.
     */
    public String toQueryString() {
        final var query = new StringBuilder();
        append(query, "ageMin", ageMin);
        append(query, "ageMax", ageMax);
        append(query, "skillType1", skillType1.chppId);
        append(query, "minSkillValue1", minSkillValue1);
        append(query, "maxSkillValue1", maxSkillValue1);
        append(query, "ageDaysMin", ageDaysMin);
        append(query, "ageDaysMax", ageDaysMax);
        append(query, "skillType2", skillType2);
        append(query, "minSkillValue2", minSkillValue2);
        append(query, "maxSkillValue2", maxSkillValue2);
        append(query, "skillType3", skillType3);
        append(query, "minSkillValue3", minSkillValue3);
        append(query, "maxSkillValue3", maxSkillValue3);
        append(query, "skillType4", skillType4);
        append(query, "minSkillValue4", minSkillValue4);
        append(query, "maxSkillValue4", maxSkillValue4);
        append(query, "specialty", specialty);
        append(query, "nativeCountryId", nativeCountryId);
        append(query, "tsiMin", tsiMin);
        append(query, "tsiMax", tsiMax);
        append(query, "priceMin", priceMin);
        append(query, "priceMax", priceMax);
        // Always sent, never omitted: Hattrick does not reliably apply its own documented
        // defaults for these two, so a query without them is not the query it looks like.
        query.append("&pageSize=").append(pageSize == null ? DEFAULT_PAGE_SIZE : pageSize);
        query.append("&pageIndex=").append(pageIndex == null ? 0 : pageIndex);
        return query.toString();
    }

    private static void append(StringBuilder query, String name, Skill skill) {
        if (skill != null) {
            append(query, name, skill.chppId);
        }
    }

    private static void append(StringBuilder query, String name, Integer value) {
        if (value != null) {
            query.append('&').append(name).append('=').append(value);
        }
    }
}
