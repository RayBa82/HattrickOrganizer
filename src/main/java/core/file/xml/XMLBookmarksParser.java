package core.file.xml;

import org.w3c.dom.Element;

import java.util.ArrayList;
import java.util.List;

/**
 * Parses a {@code bookmarks} response (version 1.0). Never throws, empty on anything unreadable.
 * <p>
 * What {@code text} / {@code objectId} hold depends on {@code typeId} - see the
 * BookmarkTypeReturnValues table in {@code docs/raw/chpp/bookmarks.md}.
 */
public final class XMLBookmarksParser {

    public record Bookmark(String bookmarkId, int typeId, String text, String text2, String objectId,
                           String objectId2, String objectId3, String comment) {
    }

    private XMLBookmarksParser() {
    }

    public static List<Bookmark> parse(String xml) {
        final var doc = XMLManager.parseString(xml);
        final var out = new ArrayList<Bookmark>();
        if (doc == null) {
            return out;
        }
        final var items = doc.getDocumentElement().getElementsByTagName("Bookmark");
        for (int i = 0; i < items.getLength(); i++) {
            final var e = (Element) items.item(i);
            out.add(new Bookmark(
                    XMLManager.xmlValue(e, "BookmarkID"),
                    XMLManager.xmlIntValue(e, "BookmarkTypeID"),
                    XMLManager.xmlValue(e, "Text"),
                    XMLManager.xmlValue(e, "Text2"),
                    XMLManager.xmlValue(e, "ObjectID"),
                    XMLManager.xmlValue(e, "ObjectID2"),
                    XMLManager.xmlValue(e, "ObjectID3"),
                    XMLManager.xmlValue(e, "Comment")));
        }
        return out;
    }
}
