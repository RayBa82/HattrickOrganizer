package core.model.match;

/**
 * Where a team's chances in a match came from: matchdetails 3.1 sends one
 * {@code NrOfChances<xmlName>} element per side and type.
 */
public enum ChanceType {
    LEFT("Left"),
    CENTER("Center"),
    RIGHT("Right"),
    SPECIAL_EVENTS("SpecialEvents"),
    OTHER("Other");

    private final String xmlName;

    ChanceType(String xmlName) {
        this.xmlName = xmlName;
    }

    /** The CHPP element, {@code NrOfChancesLeft} etc. */
    public String getXmlElement() {
        return "NrOfChances" + xmlName;
    }

    /** The column suffix in MATCHDETAILS, e.g. {@code HomeChancesLeft}. */
    public String getColumnSuffix() {
        return xmlName;
    }
}
