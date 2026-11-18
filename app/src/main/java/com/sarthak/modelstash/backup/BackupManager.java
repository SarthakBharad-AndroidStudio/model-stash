package com.sarthak.modelstash.backup;

import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;

import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.sarthak.modelstash.R;
import com.sarthak.modelstash.data.AppDatabase;
import com.sarthak.modelstash.data.ModelDao;
import com.sarthak.modelstash.data.ModelKit;
import com.sarthak.modelstash.data.ModelPhoto;
import com.sarthak.modelstash.util.BuildStatus;
import com.sarthak.modelstash.util.CsvExport;
import com.sarthak.modelstash.util.PhotoCompressor;
import com.sarthak.modelstash.util.PhotoDraft;
import com.sarthak.modelstash.util.PhotoStorage;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Backup, restore, CSV export and "shrink stored photos": the slow jobs behind the
 * "Backup & data" menu. Every method runs on the calling thread and can take a
 * while, so call them off the main thread (DataTaskViewModel does).
 *
 * Restore is careful: the whole backup is read and checked first, the photos are
 * put in place, and only then is the database replaced, in one transaction. If
 * anything fails before that point, nothing on the phone has changed.
 */
public final class BackupManager {

    /** Reported while a job runs: done of total (total 0 = unknown). */
    public interface Progress {
        void update(int done, int total);
    }

    /** A failure with a message meant for you, not a stack trace. */
    public static final class BackupException extends IOException {
        @StringRes
        public final int message;

        BackupException(@StringRes int message) {
            super("backup error " + message);
            this.message = message;
        }

        BackupException(@StringRes int message, Throwable cause) {
            super("backup error " + message, cause);
            this.message = message;
        }
    }

    /** What a backup holds, from its backup.properties (read before restoring, for the confirmation). */
    public static final class Summary {
        public final int kits;
        public final int photos;
        public final long createdAt;

        Summary(int kits, int photos, long createdAt) {
            this.kits = kits;
            this.photos = photos;
            this.createdAt = createdAt;
        }
    }

    private static final long MAX_TEXT = 20L * 1024 * 1024;   // kits.csv of tens of thousands of kits
    private static final long MAX_PHOTO = 60L * 1024 * 1024;  // one photo file

    private BackupManager() {
    }

    // ---------------- Backup ----------------

    /** Writes everything (kits, photo list, photo files) into the .zip at `target`. Returns the counts. */
    public static Summary exportBackup(Context context, Uri target, Progress progress) throws IOException {
        return exportBackup(context, AppDatabase.get(context), target, progress);
    }

    /** Same, for a given database (tests use a throw-away one, never your stash). */
    public static Summary exportBackup(Context context, AppDatabase db, Uri target, Progress progress)
            throws IOException {
        ModelDao dao = db.modelDao();
        List<ModelKit> kits = new ArrayList<>();
        List<ModelPhoto> photos = new ArrayList<>();
        db.runInTransaction(() -> { // one consistent moment, even if something is being saved
            kits.addAll(dao.getAllNow());
            photos.addAll(dao.getAllPhotosNow());
        });
        // Only photos whose file is really there; a name may appear only once in the zip.
        List<ModelPhoto> present = new ArrayList<>();
        Set<String> names = new HashSet<>();
        for (ModelPhoto p : photos) {
            if (new File(p.path).isFile() && names.add(BackupFormat.zipName(p.path))) {
                present.add(p);
            }
        }

        ContentResolver resolver = context.getContentResolver();
        try (OutputStream raw = resolver.openOutputStream(target)) {
            if (raw == null) {
                throw new BackupException(R.string.backup_cant_write);
            }
            try (ZipOutputStream zip = new ZipOutputStream(new BufferedOutputStream(raw, 64 * 1024))) {
                long now = System.currentTimeMillis();
                putText(zip, BackupFormat.MANIFEST,
                        propertiesText(BackupFormat.manifest(appVersion(context), now, kits.size(), present.size())));
                putText(zip, BackupFormat.KITS, BackupFormat.kitsCsv(kits));
                putText(zip, BackupFormat.PHOTOS, BackupFormat.photosCsv(present));

                zip.setLevel(Deflater.NO_COMPRESSION); // JPEGs don't compress; don't waste time trying
                byte[] buffer = new byte[64 * 1024];
                for (int i = 0; i < present.size(); i++) {
                    ModelPhoto p = present.get(i);
                    zip.putNextEntry(new ZipEntry(BackupFormat.PHOTO_DIR + BackupFormat.zipName(p.path)));
                    try (InputStream in = new FileInputStream(p.path)) {
                        int read;
                        while ((read = in.read(buffer)) != -1) {
                            zip.write(buffer, 0, read);
                        }
                    }
                    zip.closeEntry();
                    progress.update(i + 1, present.size());
                }
                zip.finish();
                return new Summary(kits.size(), present.size(), now);
            }
        } catch (BackupException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            throw new BackupException(R.string.backup_cant_write, e);
        }
    }

    // ---------------- Restore ----------------

    /** Reads only backup.properties, for "this backup has 46 kits from 18 Nov 2026 – replace?". */
    public static Summary inspect(Context context, Uri source) throws IOException {
        try (InputStream raw = context.getContentResolver().openInputStream(source)) {
            if (raw == null) {
                throw new BackupException(R.string.restore_cant_read);
            }
            try (ZipInputStream zip = new ZipInputStream(new BufferedInputStream(raw))) {
                ZipEntry entry;
                while ((entry = zip.getNextEntry()) != null) {
                    if (BackupFormat.MANIFEST.equals(entry.getName())) {
                        Properties manifest = loadProperties(readText(zip, 64 * 1024));
                        checkFormat(manifest);
                        return new Summary(
                                intOf(manifest.getProperty("kits")),
                                intOf(manifest.getProperty("photos")),
                                longOf(manifest.getProperty("created_at")));
                    }
                }
            }
        } catch (BackupException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            throw new BackupException(R.string.restore_not_a_backup, e);
        }
        throw new BackupException(R.string.restore_not_a_backup);
    }

    /**
     * Replaces every kit and photo on the phone with the backup's. Returns the counts restored.
     * Nothing changes on the phone unless the backup could be read completely.
     */
    public static Summary restoreBackup(Context context, Uri source, Progress progress) throws IOException {
        return restoreBackup(context, AppDatabase.get(context), source, progress);
    }

    /** Same, for a given database (tests use a throw-away one, never your stash). */
    public static Summary restoreBackup(Context context, AppDatabase db, Uri source, Progress progress)
            throws IOException {
        File staging = new File(context.getCacheDir(), "restore");
        deleteRecursively(staging);
        if (!staging.mkdirs() && !staging.isDirectory()) {
            throw new BackupException(R.string.restore_failed);
        }
        List<File> placed = new ArrayList<>(); // photo files moved into the photo folder
        try {
            // 1. Read the whole zip into the staging folder.
            Properties manifest = null;
            String kitsCsv = null;
            String photosCsv = "";
            Map<String, File> staged = new HashMap<>();
            try (InputStream raw = context.getContentResolver().openInputStream(source)) {
                if (raw == null) {
                    throw new BackupException(R.string.restore_cant_read);
                }
                try (ZipInputStream zip = new ZipInputStream(new BufferedInputStream(raw, 64 * 1024))) {
                    ZipEntry entry;
                    int count = 0;
                    while ((entry = zip.getNextEntry()) != null) {
                        String name = entry.getName();
                        if (entry.isDirectory()) {
                            continue;
                        }
                        if (BackupFormat.MANIFEST.equals(name)) {
                            manifest = loadProperties(readText(zip, 64 * 1024));
                        } else if (BackupFormat.KITS.equals(name)) {
                            kitsCsv = readText(zip, MAX_TEXT);
                        } else if (BackupFormat.PHOTOS.equals(name)) {
                            photosCsv = readText(zip, MAX_TEXT);
                        } else if (name.startsWith(BackupFormat.PHOTO_DIR)) {
                            String file = name.substring(BackupFormat.PHOTO_DIR.length());
                            if (BackupFormat.isSafeFileName(file)) {
                                File out = new File(staging, file);
                                copyLimited(zip, out, MAX_PHOTO);
                                staged.put(file, out);
                                progress.update(++count, 0);
                            }
                        }
                    }
                }
            } catch (BackupException e) {
                throw e;
            } catch (IOException | RuntimeException e) {
                throw new BackupException(R.string.restore_cant_read, e);
            }
            checkFormat(manifest);
            if (kitsCsv == null) {
                throw new BackupException(R.string.restore_not_a_backup);
            }

            // 2. Check it hangs together.
            List<ModelKit> kits = BackupFormat.readKits(kitsCsv);
            if (BackupFormat.hasDuplicateIds(kits)) {
                throw new BackupException(R.string.restore_damaged);
            }
            List<BackupFormat.PhotoEntry> entries =
                    BackupFormat.validPhotos(kits, BackupFormat.readPhotos(photosCsv), PhotoDraft.MAX_PHOTOS);

            // 3. Put the photos in place under new names (nothing points at them yet).
            Map<Long, List<BackupFormat.PhotoEntry>> byKit = new HashMap<>();
            for (BackupFormat.PhotoEntry e : entries) {
                File file = staged.get(e.file);
                if (file != null && PhotoStorage.isImage(file)) {
                    List<BackupFormat.PhotoEntry> list = byKit.get(e.kitId);
                    if (list == null) {
                        list = new ArrayList<>();
                        byKit.put(e.kitId, list);
                    }
                    list.add(e);
                }
            }
            List<ModelPhoto> rows = new ArrayList<>();
            Map<Long, String> covers = new HashMap<>();
            for (Map.Entry<Long, List<BackupFormat.PhotoEntry>> group : byKit.entrySet()) {
                List<BackupFormat.PhotoEntry> list = group.getValue();
                Collections.sort(list, (a, b) -> Integer.compare(a.position, b.position));
                for (int i = 0; i < list.size(); i++) {
                    File dest = PhotoStorage.newPhotoFile(context);
                    moveOrCopy(staged.get(list.get(i).file), dest);
                    placed.add(dest);
                    ModelPhoto row = new ModelPhoto();
                    row.modelId = group.getKey();
                    row.path = dest.getAbsolutePath();
                    row.label = list.get(i).label;
                    row.position = i;
                    rows.add(row);
                    if (i == 0) {
                        covers.put(group.getKey(), row.path);
                    }
                }
            }
            for (ModelKit k : kits) {
                k.photoPath = covers.get(k.id); // the cover is always the first photo
            }

            // 4. Swap the database contents in one go.
            ModelDao dao = db.modelDao();
            List<String> oldFiles = new ArrayList<>();
            db.runInTransaction(() -> {
                oldFiles.addAll(dao.allReferencedPhotoPaths());
                dao.deleteAllPhotos();
                dao.deleteAllModels();
                if (!kits.isEmpty()) {
                    dao.insertAll(kits);
                }
                if (!rows.isEmpty()) {
                    dao.insertPhotos(rows);
                }
            });
            placed.clear(); // they belong to the database now

            // 5. Only now remove the old photo files.
            Set<String> keep = new HashSet<>();
            for (ModelPhoto r : rows) {
                keep.add(r.path);
            }
            for (String path : oldFiles) {
                if (!keep.contains(path)) {
                    PhotoStorage.delete(path);
                }
            }
            return new Summary(kits.size(), rows.size(), longOf(manifest.getProperty("created_at")));
        } catch (BackupException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            throw new BackupException(R.string.restore_failed, e);
        } finally {
            for (File f : placed) { // failed before the database swap: undo the copies
                //noinspection ResultOfMethodCallIgnored
                f.delete();
            }
            deleteRecursively(staging);
        }
    }

    // ---------------- CSV export ----------------

    /** Writes every kit (stash first, then wishlist, A–Z) as a spreadsheet. Returns the number of kits. */
    public static int exportCsv(Context context, Uri target) throws IOException {
        List<ModelKit> kits = AppDatabase.get(context).modelDao().getAllNow();
        Collections.sort(kits, (a, b) -> {
            if (a.wishlist != b.wishlist) {
                return a.wishlist ? 1 : -1;
            }
            return a.name.compareToIgnoreCase(b.name);
        });
        String csv = CsvExport.write(kits, BuildStatus.labels(context),
                context.getString(R.string.csv_list_stash), context.getString(R.string.csv_list_wishlist));
        try (OutputStream out = context.getContentResolver().openOutputStream(target)) {
            if (out == null) {
                throw new BackupException(R.string.backup_cant_write);
            }
            out.write(csv.getBytes(StandardCharsets.UTF_8));
        } catch (BackupException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            throw new BackupException(R.string.backup_cant_write, e);
        }
        return kits.size();
    }

    // ---------------- Shrink stored photos ----------------

    /** Shrinks every stored photo that is bigger than needed. Returns the bytes saved. */
    public static long shrinkAllPhotos(Context context, Progress progress) {
        List<ModelPhoto> photos = AppDatabase.get(context).modelDao().getAllPhotosNow();
        long saved = 0;
        for (int i = 0; i < photos.size(); i++) {
            File file = new File(photos.get(i).path);
            long before = file.length();
            if (file.isFile() && PhotoCompressor.shrinkInPlace(file)) {
                saved += Math.max(0, before - file.length());
            }
            progress.update(i + 1, photos.size());
        }
        return saved;
    }

    // ---------------- Helpers ----------------

    private static void checkFormat(@Nullable Properties manifest) throws BackupException {
        int format = BackupFormat.formatOf(manifest);
        if (format < 1) {
            throw new BackupException(R.string.restore_not_a_backup);
        }
        if (format > BackupFormat.FORMAT) {
            throw new BackupException(R.string.restore_too_new);
        }
    }

    private static void putText(ZipOutputStream zip, String name, String text) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(text.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private static String readText(InputStream in, long limit) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[16 * 1024];
        long total = 0;
        int read;
        while ((read = in.read(buffer)) != -1) {
            total += read;
            if (total > limit) {
                throw new BackupException(R.string.restore_damaged);
            }
            out.write(buffer, 0, read);
        }
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }

    private static void copyLimited(InputStream in, File out, long limit) throws IOException {
        byte[] buffer = new byte[64 * 1024];
        long total = 0;
        try (OutputStream o = new FileOutputStream(out)) {
            int read;
            while ((read = in.read(buffer)) != -1) {
                total += read;
                if (total > limit) {
                    throw new BackupException(R.string.restore_damaged);
                }
                o.write(buffer, 0, read);
            }
        }
    }

    private static void moveOrCopy(File from, File to) throws IOException {
        if (from.renameTo(to)) {
            return;
        }
        try (InputStream in = new FileInputStream(from); OutputStream out = new FileOutputStream(to)) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
        }
    }

    private static String propertiesText(Properties p) throws IOException {
        StringWriter w = new StringWriter();
        p.store(w, "Model Stash backup");
        return w.toString();
    }

    private static Properties loadProperties(String text) throws IOException {
        Properties p = new Properties();
        p.load(new StringReader(text));
        return p;
    }

    private static String appVersion(Context context) {
        try {
            PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            return info.versionName == null ? "" : info.versionName;
        } catch (PackageManager.NameNotFoundException e) {
            return "";
        }
    }

    private static int intOf(String s) {
        return (int) longOf(s);
    }

    private static long longOf(String s) {
        try {
            return Long.parseLong(s == null ? "" : s.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static void deleteRecursively(File f) {
        File[] children = f.listFiles();
        if (children != null) {
            for (File c : children) {
                deleteRecursively(c);
            }
        }
        //noinspection ResultOfMethodCallIgnored
        f.delete();
    }
}
