package com.sarthak.modelstash;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import com.sarthak.modelstash.scalemates.KitInfo;
import com.sarthak.modelstash.scalemates.ScalematesParser;

import org.junit.Test;

public class ScalematesParserTest {

    /** Shaped like the head of a real kit page (Tamiya 61119, checked Sept 2026). */
    private static final String SPITFIRE_PAGE = "<html><head>"
            + "<title>Supermarine Spitfire Mk.I, Tamiya 61119 (2018)</title>"
            + "<meta name=\"description\" content=\"Tamiya model kit in scale 1:48, 61119 is a NEW tool"
            + " released in 2018 | Contents, Previews, Reviews, History + Marketplace | Supermarine"
            + " Spitfire | EAN: 4950344611195\">"
            + "<meta property=\"og:title\" content=\"Supermarine Spitfire Mk.I, Tamiya 61119 (2018)\">"
            + "</head><body>...</body></html>";

    @Test
    public void readsNameBrandNumberScaleAndYear() {
        KitInfo info = ScalematesParser.parse(SPITFIRE_PAGE);
        assertNotNull(info);
        assertEquals("Supermarine Spitfire Mk.I", info.name);
        assertEquals("Tamiya", info.brand);
        assertEquals("61119", info.kitNumber);
        assertEquals("1:48", info.scale);
        assertEquals("2018", info.year);
        assertEquals("Tamiya 61119 · released 2018", info.summaryLine());
    }

    @Test
    public void handlesCommasInNamesMultiWordBrandsAndNoMetaDescription() {
        String html = "<title>Supermarine Spitfire Mk.Vb, Trop., Hobby Boss 80258 (199x) | Scalemates</title>";
        KitInfo info = ScalematesParser.parse(html);
        assertNotNull(info);
        assertEquals("Supermarine Spitfire Mk.Vb, Trop.", info.name);
        assertEquals("Hobby Boss", info.brand);
        assertEquals("80258", info.kitNumber);
        assertEquals("199x", info.year);
        assertNull(info.scale);
    }

    @Test
    public void numberWithSpacesComesFromTheDescription() {
        String html = "<meta content='Quickboost detail set in scale 1:48, QB 48 866 is a new tool'"
                + " name='description'>"
                + "<meta property='og:title' content='Spitfire Mk.I - Exhaust, Quickboost QB 48 866'>";
        KitInfo info = ScalematesParser.parse(html);
        assertNotNull(info);
        assertEquals("Spitfire Mk.I - Exhaust", info.name);
        assertEquals("Quickboost", info.brand);
        assertEquals("QB 48 866", info.kitNumber);
        assertEquals("1:48", info.scale);
        assertNull(info.year);
    }

    @Test
    public void decodesHtmlEntities() {
        KitInfo info = ScalematesParser.parse(
                "<title>Sd.Kfz. 251 &quot;Hanomag&quot; &amp; crew, Dragon 6245 (2006)</title>");
        assertNotNull(info);
        assertEquals("Sd.Kfz. 251 \"Hanomag\" & crew", info.name);
        assertEquals("Dragon", info.brand);
    }

    @Test
    public void returnsNullForPagesWithoutATitle() {
        assertNull(ScalematesParser.parse("<html><body>Access denied</body></html>"));
        assertNull(ScalematesParser.parse(null));
    }

    @Test
    public void findsKitLinksInSharedText() {
        assertEquals("https://www.scalemates.com/kits/tamiya-61119-supermarine-spitfire-mki--1162877",
                ScalematesParser.extractKitUrl("Supermarine Spitfire Mk.I, Tamiya 61119 (2018)\n"
                        + "https://www.scalemates.com/kits/tamiya-61119-supermarine-spitfire-mki--1162877"));
        assertEquals("https://scalemates.com/kits/abc--1",
                ScalematesParser.extractKitUrl("look: http://scalemates.com/kits/abc--1#reviews)."));
        assertNull(ScalematesParser.extractKitUrl("https://www.scalemates.com/search.php?q=spitfire"));
        assertNull(ScalematesParser.extractKitUrl("https://example.com/kits/abc"));
        assertNull(ScalematesParser.extractKitUrl(null));
    }
}