package com.sarthak.modelstash.backup;

import androidx.annotation.Nullable;

import com.sarthak.modelstash.data.ModelKit;
import com.sarthak.modelstash.data.ModelPhoto;
import com.sarthak.modelstash.util.CsvTable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * What goes inside a backup .zip, and how it is read back. Plain Java (the zip
 * itself is handled by BackupManager), so it can be unit-tested on your PC.
 *
 * A backup is:
 *   backup.properties   format version, app version, date, counts
 *   kits.csv            one row per kit, every field, by column name
 *   photos.csv          kit_id, file, label, position
 *   photos/IMG_....jpg  the photo files
 *
 * Plain CSV on purpose: you can open a backup's kits.csv in any spreadsheet, and
 * because columns are read by name, kits added in a later version with extra
 * columns (price, shop, tags...) still restore here, and old backups restore in
 * later versions: a missing column just gets its default.
 */
public final class BackupFormat {

    /** Bump only for a change old versions can't read at all. Adding columns doesn't need it. */
    public static final int FORMAT = 1;

    public static final String MANIFEST = "backup.properties";
    public static final String KITS = "kits.csv";
    public static final String PHOTOS = "photos.csv";
    public static final String PHOTO_DIR = "photos/";

    static final String[] KIT_COLUMNS = {
            "id", "name", "scale", "brand", "kit_number", "description", "source_url",
            "wishlist", "build_status", "created_at"};
    static final String[] PHOTO_COLUMNS = {"kit_id", "file", "label", "position"};

    private BackupFormat() {
    }

    /** One photo row read from a backup: which kit, which file inside the zip, label and position. */
    public static final class PhotoEntry {
        public final long kitId;
        public final String file;
        public final int label;
        public final int position;

        PhotoEntry(long kitId, String file, int label, int position) {
            this.kitId = kitId;
            this.file = file;
            this.label = label;
            this.position = position;
        }
    }

    // ---- Writing ----

    public static String kitsCsv(List<ModelKit> kits) {
        CsvTable table = new CsvTable(KIT_COLUMNS);
        for (ModelKit k : kits) {
            Map<String, String> row = table.addRow();
            row.put("id", String.valueOf(k.id));
            row.put("name", k.name);
            row.put("scale", orEmpty(k.scale));
            row.put("brand", orEmpty(k.brand));
            row.put("kit_number", orEmpty(k.kitNumber));
            row.put("description", orEmpty(k.description));
            row.put("source_url", orEmpty(k.sourceUrl));
            row.put("wishlist", k.wishlist ? "1" : "0");
            row.put("build_status", String.valueOf(k.buildStatus));
            row.put("created_at", String.valueOf(k.createdAt));
        }
        return table.write();
    }

    /** The name a photo gets inside the zip: just its file name, no folders. */
    public static String zipName(String path) {
        int slash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        return slash >= 0 ? path.substring(slash + 1) : path;
    }

    public static String photosCsv(List<ModelPhoto> photos) {
        CsvTable table = new CsvTable(PHOTO_COLUMNS);
        for (ModelPhoto p : photos) {
            Map<String, String> row = table.addRow();
            row.put("kit_id", String.valueOf(p.modelId));
            row.put("file", zipName(p.path));
            row.put("label", String.valueOf(p.label));
            row.put("position", String.valueOf(p.position));
        }
        return table.write();
    }

    public static Properties manifest(String appVersion, long createdAt, int kits, int photos) {
        Properties p = new Properties();
        p.setProperty("format", String.valueOf(FORMAT));
        p.setProperty("app", "Model Stash");
        p.setProperty("app_version", appVersion);
        p.setProperty("created_at", String.valueOf(createdAt));
        p.setProperty("kits", String.valueOf(kits));
        p.setProperty("photos", String.valueOf(photos));
        return p;
    }

    // ---- Reading ----

    /** @return the format number, or -1 if this isn't a Model Stash backup. */
    public static int formatOf(@Nullable Properties manifest) {
        if (manifest == null || !"Model Stash".equals(manifest.getProperty("app"))) {
            return -1;
        }
        return (int) number(manifest.getProperty("format"), -1);
    }

    /** Kits as stored, with their original ids (photos refer to them). Rows without a name are skipped. */
    public static List<ModelKit> readKits(String csv) {
        List<ModelKit> out = new ArrayList<>();
        for (Map<String, String> row : CsvTable.parse(csv).rows()) {
            String name = CsvTable.get(row, "name").trim();
            if (name.isEmpty()) {
                continue;
            }
            ModelKit k = new ModelKit();
            k.id = number(CsvTable.get(row, "id"), 0);
            k.name = name;
            k.scale = nullIfEmpty(CsvTable.get(row, "scale"));
            k.brand = nullIfEmpty(CsvTable.get(row, "brand"));
            k.kitNumber = nullIfEmpty(CsvTable.get(row, "kit_number"));
            k.description = nullIfEmpty(CsvTable.get(row, "description"));
            k.sourceUrl = nullIfEmpty(CsvTable.get(row, "source_url"));
            k.wishlist = "1".equals(CsvTable.get(row, "wishlist").trim());
            k.buildStatus = (int) number(CsvTable.get(row, "build_status"), 0);
            k.createdAt = number(CsvTable.get(row, "created_at"), 0);
            out.add(k);
        }
        return out;
    }

    /** Photo rows; ones whose file name could escape the photo folder ("../x") are dropped. */
    public static List<PhotoEntry> readPhotos(String csv) {
        List<PhotoEntry> out = new ArrayList<>();
        for (Map<String, String> row : CsvTable.parse(csv).rows()) {
            String file = CsvTable.get(row, "file").trim();
            if (!isSafeFileName(file)) {
                continue;
            }
            out.add(new PhotoEntry(
                    number(CsvTable.get(row, "kit_id"), 0),
                    file,
                    (int) number(CsvTable.get(row, "label"), -1),
                    (int) number(CsvTable.get(row, "position"), 0)));
        }
        return out;
    }

    /** A bare file name: no folders, no "..", nothing that could point outside the photo folder. */
    public static boolean isSafeFileName(String name) {
        return name != null && !name.isEmpty() && name.length() <= 200
                && !name.contains("/") && !name.contains("\\") && !name.contains("..")
                && !name.startsWith(".");
    }

    /**
     * Checks a backup hangs together before anything on the phone is touched:
     * unique kit ids, every photo belonging to a kit in the backup, at most six photos
     * a kit. Photos that break the rules are dropped; returns the ones to restore.
     */
    public static List<PhotoEntry> validPhotos(List<ModelKit> kits, List<PhotoEntry> photos, int maxPerKit) {
        Map<Long, Integer> perKit = new HashMap<>();
        for (ModelKit k : kits) {
            perKit.put(k.id, 0);
        }
        List<PhotoEntry> out = new ArrayList<>();
        for (PhotoEntry p : photos) {
            Integer count = perKit.get(p.kitId);
            if (count == null || count >= maxPerKit) {
                continue;
            }
            perKit.put(p.kitId, count + 1);
            out.add(p);
        }
        return out;
    }

    /** true when two kits in the backup share an id, which would make photos ambiguous. */
    public static boolean hasDuplicateIds(List<ModelKit> kits) {
        java.util.Set<Long> seen = new java.util.HashSet<>();
        for (ModelKit k : kits) {
            if (k.id <= 0 || !seen.add(k.id)) {
                return true;
            }
        }
        return false;
    }

    private static long number(String text, long fallback) {
        try {
            return Long.parseLong(text == null ? "" : text.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static String orEmpty(String s) {
        return s == null ? "" : s;
    }

    @Nullable
    private static String nullIfEmpty(String s) {
        return s == null || s.trim().isEmpty() ? null : s;
    }
}
