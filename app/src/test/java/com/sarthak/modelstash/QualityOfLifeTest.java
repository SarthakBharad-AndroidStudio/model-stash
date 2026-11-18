package com.sarthak.modelstash;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.sarthak.modelstash.data.ModelKit;
import com.sarthak.modelstash.util.CsvExport;
import com.sarthak.modelstash.util.CsvImport;
import com.sarthak.modelstash.util.CsvTable;
import com.sarthak.modelstash.util.DuplicateFinder;
import com.sarthak.modelstash.util.FormSnapshot;
import com.sarthak.modelstash.util.ImageSizing;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * CSV export and import (the list's backup), duplicate check, unsaved-changes
 * check and photo sizing: the logic behind version 1.6, without a phone.
 * Right-click > Run.
 */
public class QualityOfLifeTest {

    private static ModelKit kit(long id, String name, String brand, String scale, String number) {
        ModelKit k = new ModelKit();
        k.id = id;
        k.name = name;
        k.brand = brand;
        k.scale = scale;
        k.kitNumber = number;
        return k;
    }

    // ---------------- CSV ----------------

    @Test
    public void csv_awkwardTextSurvivesARoundTrip() {
        CsvTable t = new CsvTable("name", "description");
        Map<String, String> row = t.addRow();
        row.put("name", "Bloch MB.152C.1 \"Early Version\"");
        row.put("description", "Line one, with a comma\nline two; and a semicolon");
        CsvTable back = CsvTable.parse(t.write());
        assertEquals(1, back.rows().size());
        assertEquals("Bloch MB.152C.1 \"Early Version\"", CsvTable.get(back.rows().get(0), "name"));
        assertEquals("Line one, with a comma\nline two; and a semicolon",
                CsvTable.get(back.rows().get(0), "description"));
    }

    @Test
    public void csv_columnsAreReadByName_missingOnesAreEmpty() {
        CsvTable t = CsvTable.parse("Brand,NAME,price\r\nTamiya,F-14D,30\r\n");
        Map<String, String> row = t.rows().get(0);
        assertEquals("F-14D", CsvTable.get(row, "name"));
        assertEquals("Tamiya", CsvTable.get(row, "brand"));
        assertEquals("", CsvTable.get(row, "scale"));
    }

    @Test
    public void export_canBeImportedAgain() {
        ModelKit a = kit(1, "Grumman F-14D Tomcat", "Tamiya", "1:48", "61114");
        a.createdAt = 1_700_000_000_000L;
        ModelKit b = kit(2, "Eurofighter; Typhoon", "Revell", "1:72", "03865");
        b.wishlist = true;
        String csv = CsvExport.write(Arrays.asList(a, b),
                new String[]{"Unbuilt", "In build", "Completed"}, "Stash", "Wishlist");
        assertTrue("starts with a byte-order mark for Excel", csv.startsWith("﻿"));

        List<ModelKit> back = CsvImport.parse(csv, false);
        assertEquals(2, back.size());
        assertEquals("Grumman F-14D Tomcat", back.get(0).name);
        assertEquals("61114", back.get(0).kitNumber);
        assertEquals("Tamiya", back.get(0).brand);
        assertEquals("1:48", back.get(0).scale);
        assertEquals("Eurofighter; Typhoon", back.get(1).name);
    }

    // ---------------- CSV as the list's backup ----------------

    @Test
    public void export_thenImport_bringsEveryFieldBack() {
        ModelKit a = kit(1, "Bloch MB.152C.1 \"Early Version\"", "Special Hobby", "1:32", "SH32041");
        a.description = "Resin wheels,\nmasks; decals yellowed";
        a.sourceUrl = "https://www.scalemates.com/kits/x";
        a.buildStatus = 1;
        a.createdAt = CsvImportDates.day(2026, 11, 2);
        ModelKit b = kit(2, "Spitfire Mk.IX", "Eduard", "1:48", "82153");
        b.wishlist = true;
        String[] labels = {"Unbuilt", "On the bench", "Completed"};
        String csv = CsvExport.write(Arrays.asList(a, b), labels, "Stash", "Wishlist");

        // Imported from the Catalogue tab: the "list" column still puts b on the wishlist.
        List<ModelKit> back = CsvImport.parse(csv, false, labels);
        assertEquals(2, back.size());
        ModelKit ra = back.get(0);
        assertEquals("Bloch MB.152C.1 \"Early Version\"", ra.name);
        assertEquals("Special Hobby", ra.brand);
        assertEquals("1:32", ra.scale);
        assertEquals("SH32041", ra.kitNumber);
        assertEquals("Resin wheels,\nmasks; decals yellowed", ra.description);
        assertEquals("https://www.scalemates.com/kits/x", ra.sourceUrl);
        assertEquals(1, ra.buildStatus);
        assertFalse(ra.wishlist);
        assertEquals("added date kept", a.createdAt, ra.createdAt);
        assertTrue(back.get(1).wishlist);
        assertEquals(0, back.get(1).buildStatus);
    }

    @Test
    public void import_understandsCommonSpellings() {
        String csv = "Kit,Manufacturer,Ratio,Stage,Notes,URL,Date added,Price\r\n"
                + "Tiger I,Tamiya,1/35,built,Zimmerit done,https://x,2025-03-01,39.99\r\n"
                + "Panther,Takom,1/35,in progress,,,,\r\n";
        List<ModelKit> kits = CsvImport.parse(csv, false);
        assertEquals(2, kits.size());
        assertEquals("Tamiya", kits.get(0).brand);
        assertEquals("1:35", kits.get(0).scale);
        assertEquals(2, kits.get(0).buildStatus);
        assertEquals("Zimmerit done", kits.get(0).description);
        assertEquals("https://x", kits.get(0).sourceUrl);
        assertTrue(kits.get(0).createdAt > 0);
        assertEquals(1, kits.get(1).buildStatus);
        assertEquals("no date: the app fills it in", 0, kits.get(1).createdAt);
    }

    @Test
    public void import_oldFourColumnFilesStillWork() {
        List<ModelKit> kits = CsvImport.parse("Tiger I,250,Tamiya,1:35\r\nPanther,,Takom,35\r\n", true);
        assertEquals(2, kits.size());
        assertTrue("no list column: the tab decides", kits.get(0).wishlist);
        assertEquals("250", kits.get(0).kitNumber);
        assertEquals("1:35", kits.get(1).scale);
    }

    @Test
    public void import_skipsKitsYouAlreadyHave() {
        List<ModelKit> existing = Arrays.asList(kit(1, "Grumman F-14D Tomcat", "Tamiya", "1:48", "61114"));
        List<ModelKit> imported = Arrays.asList(
                kit(0, "Grumman F-14D Tomcat", "Tamiya", "1:48", "61114"),  // already have
                kit(0, "Spitfire Mk.IX", "Eduard", "1:48", "82153"),        // new
                kit(0, "Spitfire Mk IX", "Eduard", "1:48", null));          // repeated in the file
        List<ModelKit> kept = CsvImport.withoutDuplicates(imported, existing);
        assertEquals(1, kept.size());
        assertEquals("Spitfire Mk.IX", kept.get(0).name);
    }

    // ---------------- Duplicates ----------------

    private static List<ModelKit> stash() {
        return Arrays.asList(
                kit(1, "Grumman F-14D Tomcat", "Tamiya", "1:48", "61114"),
                kit(2, "Grumman F-14A Tomcat", "Tamiya", "1:72", "60782"),
                kit(3, "Spitfire Mk.Vb", "Airfix", null, null));
    }

    @Test
    public void duplicates_sameBrandAndNumber() {
        List<ModelKit> d = DuplicateFinder.find(kit(0, "Tomcat", "TAMIYA", null, "61114"), stash());
        assertEquals(1, d.size());
        assertEquals(1, d.get(0).id);
    }

    @Test
    public void duplicates_sameNameIgnoringCaseAndPunctuation() {
        List<ModelKit> d = DuplicateFinder.find(kit(0, "grumman f14d tomcat", null, "1:48", null), stash());
        assertEquals(1, d.size());
    }

    @Test
    public void duplicates_differentScaleOrBrandIsADifferentKit() {
        assertTrue(DuplicateFinder.find(kit(0, "Grumman F-14D Tomcat", "Tamiya", "1:72", null), stash()).isEmpty());
        assertTrue(DuplicateFinder.find(kit(0, "Spitfire Mk.Vb", "Eduard", "1:48", null), stash()).isEmpty());
    }

    @Test
    public void duplicates_missingScaleDoesntRuleItOut() {
        assertEquals(1, DuplicateFinder.find(kit(0, "Spitfire Mk Vb", null, "1:48", null), stash()).size());
    }

    @Test
    public void duplicates_editingAKitDoesntFindItself() {
        assertTrue(DuplicateFinder.find(kit(1, "Grumman F-14D Tomcat", "Tamiya", "1:48", "61114"), stash()).isEmpty());
    }

    // ---------------- Unsaved changes ----------------

    @Test
    public void formSnapshot_detectsChangesButNotStraySpaces() {
        FormSnapshot a = new FormSnapshot(Arrays.asList("Tomcat", "1:48"),
                Collections.singletonList("/p/1.jpg"), new int[]{0}, 0);
        FormSnapshot same = new FormSnapshot(Arrays.asList("Tomcat ", "1:48"),
                Collections.singletonList("/p/1.jpg"), new int[]{0}, 0);
        assertEquals(a, same);
        assertNotEquals(a, new FormSnapshot(Arrays.asList("Tomcat", "1:72"),
                Collections.singletonList("/p/1.jpg"), new int[]{0}, 0));
        assertNotEquals("relabelled photo", a, new FormSnapshot(Arrays.asList("Tomcat", "1:48"),
                Collections.singletonList("/p/1.jpg"), new int[]{1}, 0));
        assertNotEquals("new status", a, new FormSnapshot(Arrays.asList("Tomcat", "1:48"),
                Collections.singletonList("/p/1.jpg"), new int[]{0}, 2));
        assertNotEquals("photo added", a, new FormSnapshot(Arrays.asList("Tomcat", "1:48"),
                Arrays.asList("/p/1.jpg", "/p/2.jpg"), new int[]{0, -1}, 0));
    }

    // ---------------- Photo sizing ----------------

    @Test
    public void sizing() {
        assertTrue(ImageSizing.fits(2560, 1920, 2560));
        assertFalse(ImageSizing.fits(4000, 3000, 2560));
        assertEquals(1, ImageSizing.sampleSize(4000, 3000, 2560));   // 4000/2 < 2560: decode full
        assertEquals(2, ImageSizing.sampleSize(8160, 6144, 2560));   // 50 MP: decode at half
        assertEquals(4, ImageSizing.sampleSize(12000, 9000, 2560));
        assertArrayEquals(new int[]{2560, 1920}, ImageSizing.targetSize(4000, 3000, 2560));
        assertArrayEquals(new int[]{1440, 2560}, ImageSizing.targetSize(2250, 4000, 2560)); // portrait
        assertArrayEquals(new int[]{800, 600}, ImageSizing.targetSize(800, 600, 2560));     // never enlarged
    }

    /** Midnight of a day in the phone's time zone, as the export writes and the import reads it. */
    private static final class CsvImportDates {
        static long day(int year, int month, int dayOfMonth) {
            java.util.Calendar c = java.util.Calendar.getInstance();
            c.clear();
            c.set(year, month - 1, dayOfMonth);
            return c.getTimeInMillis();
        }
    }
}
