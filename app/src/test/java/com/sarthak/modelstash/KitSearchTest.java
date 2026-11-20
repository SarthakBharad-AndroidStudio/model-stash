package com.sarthak.modelstash;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.sarthak.modelstash.data.ModelKit;
import com.sarthak.modelstash.util.KitSearch;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The Catalogue's search ("do I own it?"), without a phone. Right-click > Run.
 */
public class KitSearchTest {

    private static ModelKit kit(String name, String brand, String scale, String number) {
        ModelKit k = new ModelKit();
        k.name = name;
        k.brand = brand;
        k.scale = scale;
        k.kitNumber = number;
        return k;
    }

    private static final List<ModelKit> STASH = Arrays.asList(
            kit("Grumman F-14D Tomcat", "Tamiya", "1:48", "61114"),
            kit("Grumman F-14A Tomcat", "Hasegawa", "1:72", "01564"),
            kit("Messerschmitt Bf 109 G-6", "Eduard", "1:48", "82113"),
            kit("Focke-Wulf Fw 190 A-8", "Eduard", "1:72", "70118"),
            kit("Spitfire Mk.IX", "Airfix", "1:72", "A02108"),
            kit("Škoda LT vz. 38", "Bronco", "1:35", "CB35036"),
            kit("Bloch MB.152C.1", "Special Hobby", "1:32", "SH32041"),
            kit("Tiger I Early", "Tamiya", "1:35", "35216"));

    private static final KitSearch.Index INDEX = new KitSearch.Index(STASH);

    /** Names found for `query`, in list order. */
    private static List<String> names(String query) {
        List<String> out = new ArrayList<>();
        for (ModelKit k : INDEX.search(query).kits) {
            out.add(k.name);
        }
        return out;
    }

    // ---------------- Spaces, hyphens, dots, accents ----------------

    @Test
    public void hyphensDontMatter() {
        assertEquals(Arrays.asList("Grumman F-14D Tomcat", "Grumman F-14A Tomcat"), names("F14"));
        assertEquals(Arrays.asList("Grumman F-14D Tomcat"), names("f14d"));
        assertEquals(Arrays.asList("Grumman F-14D Tomcat"), names("f 14 d"));
    }

    @Test
    public void spacesDontMatter() {
        assertEquals(Arrays.asList("Messerschmitt Bf 109 G-6"), names("bf109"));
        assertEquals(Arrays.asList("Messerschmitt Bf 109 G-6"), names("BF-109g6"));
        assertEquals(Arrays.asList("Focke-Wulf Fw 190 A-8"), names("fw190"));
        assertEquals(Arrays.asList("Bloch MB.152C.1"), names("mb152"));
    }

    @Test
    public void dotsDontMatter() {
        assertEquals(Arrays.asList("Spitfire Mk.IX"), names("mk ix"));
        assertEquals(Arrays.asList("Spitfire Mk.IX"), names("mkix"));
        assertEquals(Arrays.asList("Bloch MB.152C.1"), names("MB 152 C1"));
    }

    @Test
    public void accentsDontMatter() {
        assertEquals(Arrays.asList("Škoda LT vz. 38"), names("skoda"));
        assertEquals(Arrays.asList("Škoda LT vz. 38"), names("ŠKODA lt"));
        assertEquals("skoda strasse", KitSearch.fold("Škoda Straße"));
    }

    // ---------------- Brand, number, several words, scale ----------------

    @Test
    public void brandAndKitNumberStillWork() {
        assertEquals(Arrays.asList("Grumman F-14D Tomcat", "Tiger I Early"), names("tamiya"));
        assertEquals(Arrays.asList("Grumman F-14D Tomcat"), names("61114"));
        assertEquals(Arrays.asList("Bloch MB.152C.1"), names("sh 32041"));
        assertEquals(Arrays.asList("Bloch MB.152C.1"), names("specialhobby"));
    }

    @Test
    public void severalWordsInAnyOrder() {
        assertEquals(Arrays.asList("Grumman F-14A Tomcat"), names("hasegawa tomcat"));
        assertEquals(Arrays.asList("Grumman F-14A Tomcat"), names("tomcat hasegawa"));
        assertEquals(Arrays.asList("Focke-Wulf Fw 190 A-8"), names("eduard fw 190"));
        assertTrue("every word has to match", names("tamiya spitfire").isEmpty());
    }

    @Test
    public void scaleWords() {
        assertEquals(Arrays.asList("Grumman F-14D Tomcat"), names("tomcat 1/48"));
        assertEquals(Arrays.asList("Grumman F-14A Tomcat"), names("1:72 tomcat"));
        assertEquals(Arrays.asList("Škoda LT vz. 38", "Tiger I Early"), names("1/35"));
        assertTrue(names("tomcat 1/32").isEmpty());
    }

    @Test
    public void blankQueryShowsEverything() {
        assertEquals(STASH.size(), INDEX.search("").kits.size());
        assertEquals(STASH.size(), INDEX.search("   ").kits.size());
        assertEquals(STASH.size(), INDEX.search(null).kits.size());
        assertEquals(STASH.size(), INDEX.search(" - ").kits.size());
        assertFalse(INDEX.search("").close);
    }

    // ---------------- Typos ----------------

    @Test
    public void oneTypoInALongerWordIsACloseMatch() {
        KitSearch.Result r = INDEX.search("hasegwa"); // letter missing
        assertTrue(r.close);
        assertEquals(1, r.kits.size());
        assertEquals("Hasegawa", r.kits.get(0).brand);

        assertEquals(Arrays.asList("Spitfire Mk.IX"), names("spitfre"));           // missing
        assertEquals(Arrays.asList("Spitfire Mk.IX"), names("spitffire"));         // extra
        assertEquals(Arrays.asList("Spitfire Mk.IX"), names("splitfire"));         // extra
        assertEquals(Arrays.asList("Spitfire Mk.IX"), names("spitfine"));          // wrong
        assertEquals(Arrays.asList("Messerschmitt Bf 109 G-6"), names("messerchmitt")); // missing
        assertEquals(Arrays.asList("Grumman F-14D Tomcat", "Grumman F-14A Tomcat"), names("tomcta")); // swapped
    }

    @Test
    public void typoAlsoWorksWhileStillTyping() {
        assertEquals(Arrays.asList("Grumman F-14A Tomcat"), names("hasegw"));
        assertEquals(Arrays.asList("Messerschmitt Bf 109 G-6"), names("mesers"));
    }

    @Test
    public void typoInOneOfSeveralWords() {
        KitSearch.Result r = INDEX.search("tamiya tomcta");
        assertTrue(r.close);
        assertEquals(1, r.kits.size());
        assertEquals("Grumman F-14D Tomcat", r.kits.get(0).name);
    }

    @Test
    public void exactMatchesWinOverTypos() {
        KitSearch.Result r = INDEX.search("tiger");
        assertFalse(r.close);
        assertEquals(Arrays.asList("Tiger I Early"), names("tiger"));
    }

    @Test
    public void shortWordsAndNumbersMustBeTypedRight() {
        assertTrue("4 letters: no typo allowed", names("tigr").isEmpty());
        assertTrue("one digit off is another kit", names("61115").isEmpty());
        assertTrue("two typos is too many", names("hasgwa").isEmpty());
        assertFalse(INDEX.search("tigr").close);
    }

    // ---------------- "You own it" ----------------

    @Test
    public void sameNameIgnoresCaseAccentsAndPunctuation() {
        assertTrue(KitSearch.sameName("Grumman F-14D Tomcat", "grumman f14d tomcat"));
        assertTrue(KitSearch.sameName("Škoda LT vz. 38", "skoda lt vz38"));
        assertFalse(KitSearch.sameName("Grumman F-14D Tomcat", "Grumman F-14A Tomcat"));
        assertFalse(KitSearch.sameName("", ""));
    }
}
