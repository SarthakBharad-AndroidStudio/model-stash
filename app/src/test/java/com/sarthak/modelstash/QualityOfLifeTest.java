package com.sarthak.modelstash;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.sarthak.modelstash.backup.BackupFormat;
import com.sarthak.modelstash.data.ModelKit;
import com.sarthak.modelstash.data.ModelPhoto;
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
import java.util.Properties;

/**
 * Backup format, CSV export, duplicate check, unsaved-changes check and photo
 * sizing: the logic behind version 1.6, without a phone. Right-click > Run.
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

    // ---------------- Backup format ----------------

    @Test
    public void backup_kitsAndPhotosRoundTrip() {
        ModelKit a = kit(7, "Bloch MB.152C.1", "Special Hobby", "1:32", "SH32041");
        a.description = "Box opened,\n\"decals\" yellowed";
        a.sourceUrl = "https://www.scalemates.com/kits/x";
        a.buildStatus = 1;
        a.createdAt = 123456789L;
        ModelKit b = kit(9, "Wish", null, null, null);
        b.wishlist = true;

        List<ModelKit> kits = BackupFormat.readKits(BackupFormat.kitsCsv(Arrays.asList(a, b)));
        assertEquals(2, kits.size());
        ModelKit ra = kits.get(0);
        assertEquals(7, ra.id);
        assertEquals("Bloch MB.152C.1", ra.name);
        assertEquals("Special Hobby", ra.brand);
        assertEquals("1:32", ra.scale);
        assertEquals("SH32041", ra.kitNumber);
        assertEquals("Box opened,\n\"decals\" yellowed", ra.description);
        assertEquals("https://www.scalemates.com/kits/x", ra.sourceUrl);
        assertEquals(1, ra.buildStatus);
        assertEquals(123456789L, ra.createdAt);
        assertFalse(ra.wishlist);
        assertTrue(kits.get(1).wishlist);
        assertNull(kits.get(1).brand);

        ModelPhoto p = new ModelPhoto();
        p.modelId = 7;
        p.path = "/data/user/0/com.sarthak.modelstash/files/photos/IMG_abc.jpg";
        p.label = 0;
        p.position = 0;
        List<BackupFormat.PhotoEntry> photos =
                BackupFormat.readPhotos(BackupFormat.photosCsv(Collections.singletonList(p)));
        assertEquals(1, photos.size());
        assertEquals(7, photos.get(0).kitId);
        assertEquals("IMG_abc.jpg", photos.get(0).file);
        assertEquals(0, photos.get(0).label);
    }

    @Test
    public void backup_laterVersionWithExtraColumnsStillRestores() {
        // What a backup from a future version with price and shop columns would look like.
        String csv = "id,name,scale,price,shop,wishlist\r\n3,Spitfire Mk.IX,1:48,24.99,Modellbau Kassel,0\r\n";
        List<ModelKit> kits = BackupFormat.readKits(csv);
        assertEquals(1, kits.size());
        assertEquals("Spitfire Mk.IX", kits.get(0).name);
        assertEquals("1:48", kits.get(0).scale);
        assertEquals(0, kits.get(0).buildStatus); // missing column: default
    }

    @Test
    public void backup_rejectsFileNamesThatEscapeThePhotoFolder() {
        String csv = "kit_id,file,label,position\r\n1,../../databases/model_stash.db,0,0\r\n"
                + "1,photos/x.jpg,0,1\r\n1,ok.jpg,0,2\r\n";
        List<BackupFormat.PhotoEntry> photos = BackupFormat.readPhotos(csv);
        assertEquals(1, photos.size());
        assertEquals("ok.jpg", photos.get(0).file);
    }

    @Test
    public void backup_validation() {
        List<ModelKit> kits = Arrays.asList(kit(1, "A", null, null, null), kit(2, "B", null, null, null));
        assertFalse(BackupFormat.hasDuplicateIds(kits));
        assertTrue(BackupFormat.hasDuplicateIds(Arrays.asList(kit(1, "A", null, null, null),
                kit(1, "B", null, null, null))));

        StringBuilder csv = new StringBuilder("kit_id,file,label,position\r\n");
        for (int i = 0; i < 8; i++) {
            csv.append("1,p").append(i).append(".jpg,-1,").append(i).append("\r\n");
        }
        csv.append("99,orphan.jpg,-1,0\r\n");
        List<BackupFormat.PhotoEntry> valid =
                BackupFormat.validPhotos(kits, BackupFormat.readPhotos(csv.toString()), 6);
        assertEquals("six per kit at most, none for unknown kits", 6, valid.size());
    }

    @Test
    public void backup_manifest() {
        Properties p = BackupFormat.manifest("1.6", 1L, 46, 120);
        assertEquals(BackupFormat.FORMAT, BackupFormat.formatOf(p));
        Properties other = new Properties();
        other.setProperty("format", "1");
        assertEquals("not our backup", -1, BackupFormat.formatOf(other));
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
}
