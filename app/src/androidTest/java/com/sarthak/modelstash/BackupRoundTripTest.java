package com.sarthak.modelstash.backup;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;

import androidx.room.Room;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.sarthak.modelstash.data.AppDatabase;
import com.sarthak.modelstash.data.ModelDao;
import com.sarthak.modelstash.data.ModelKit;
import com.sarthak.modelstash.data.ModelPhoto;
import com.sarthak.modelstash.util.PhotoCompressor;
import com.sarthak.modelstash.util.PhotoStorage;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.ZipFile;

/**
 * Backup and restore on a real phone, against a throw-away database: your stash
 * is never touched. Backs up two kits (one with photos), changes everything,
 * restores, and checks it all came back. Also checks photo shrinking.
 */
@RunWith(AndroidJUnit4.class)
public class BackupRoundTripTest {

    private Context context;
    private AppDatabase db;
    private ModelDao dao;
    private final List<File> cleanup = new ArrayList<>();

    @Before
    public void setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase.class).allowMainThreadQueries().build();
        dao = db.modelDao();
    }

    @After
    public void tearDown() {
        for (ModelPhoto p : dao.getAllPhotosNow()) {
            PhotoStorage.delete(p.path);
        }
        for (File f : cleanup) {
            //noinspection ResultOfMethodCallIgnored
            f.delete();
        }
        db.close();
    }

    private File jpeg(int w, int h) throws Exception {
        File file = PhotoStorage.newPhotoFile(context);
        Bitmap bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        bitmap.eraseColor(Color.rgb(34, 57, 111));
        try (FileOutputStream out = new FileOutputStream(file)) {
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out);
        }
        bitmap.recycle();
        cleanup.add(file);
        return file;
    }

    private static ModelKit kit(String name, boolean wishlist) {
        ModelKit k = new ModelKit();
        k.name = name;
        k.wishlist = wishlist;
        k.createdAt = 1000;
        return k;
    }

    @Test
    public void backupThenRestore_bringsEverythingBack() throws Exception {
        // 1. A small stash: a kit with two photos, a wishlist kit without.
        ModelKit bloch = kit("Bloch MB.152C.1 \"Early Version\"", false);
        bloch.scale = "1:32";
        bloch.brand = "Special Hobby";
        bloch.description = "Decals, resin wheels,\nmasks";
        bloch.buildStatus = 1;
        long blochId = dao.insert(bloch);
        long wishId = dao.insert(kit("Spitfire Mk.IX", true));
        File a = jpeg(400, 300);
        File b = jpeg(300, 400);
        ModelPhoto pa = new ModelPhoto();
        pa.modelId = blochId;
        pa.path = a.getAbsolutePath();
        pa.label = 0;
        pa.position = 0;
        ModelPhoto pb = new ModelPhoto();
        pb.modelId = blochId;
        pb.path = b.getAbsolutePath();
        pb.label = 1;
        pb.position = 1;
        dao.insertPhotos(Arrays.asList(pa, pb));

        // 2. Back up.
        File zipFile = new File(context.getCacheDir(), "backup-test.zip");
        cleanup.add(zipFile);
        BackupManager.Summary saved = BackupManager.exportBackup(context, db, Uri.fromFile(zipFile), (d, t) -> { });
        assertEquals(2, saved.kits);
        assertEquals(2, saved.photos);
        try (ZipFile zip = new ZipFile(zipFile)) {
            assertNotNull(zip.getEntry(BackupFormat.MANIFEST));
            assertNotNull(zip.getEntry(BackupFormat.KITS));
            assertNotNull(zip.getEntry(BackupFormat.PHOTOS));
            assertNotNull(zip.getEntry(BackupFormat.PHOTO_DIR + a.getName()));
        }
        BackupManager.Summary peek = BackupManager.inspect(context, Uri.fromFile(zipFile));
        assertEquals(2, peek.kits);

        // 3. Change everything: delete it all, add something else.
        dao.deleteAllPhotos();
        dao.deleteAllModels();
        dao.insert(kit("Something else", false));

        // 4. Restore.
        BackupManager.Summary restored = BackupManager.restoreBackup(context, db, Uri.fromFile(zipFile), (d, t) -> { });
        assertEquals(2, restored.kits);
        assertEquals(2, restored.photos);

        List<ModelKit> kits = dao.getAllNow();
        assertEquals("exactly the backed-up kits, nothing else", 2, kits.size());
        ModelKit back = dao.getById(blochId);
        assertNotNull("same id as before", back);
        assertEquals("Bloch MB.152C.1 \"Early Version\"", back.name);
        assertEquals("Special Hobby", back.brand);
        assertEquals("1:32", back.scale);
        assertEquals("Decals, resin wheels,\nmasks", back.description);
        assertEquals(1, back.buildStatus);
        assertTrue(dao.getById(wishId).wishlist);

        List<ModelPhoto> photos = dao.getPhotos(blochId);
        assertEquals(2, photos.size());
        assertEquals("labels kept", 0, photos.get(0).label);
        assertEquals(1, photos.get(1).label);
        assertEquals("cover = first photo", photos.get(0).path, back.photoPath);
        for (ModelPhoto p : photos) {
            assertTrue("photo file exists", new File(p.path).isFile());
            assertTrue(PhotoStorage.isImage(new File(p.path)));
        }
        assertNotEquals("restored under a new file name", a.getAbsolutePath(), photos.get(0).path);
    }

    @Test
    public void notABackup_isRefused_andNothingChanges() throws Exception {
        long id = dao.insert(kit("Keep me", false));
        File junk = new File(context.getCacheDir(), "not-a-backup.zip");
        cleanup.add(junk);
        try (FileOutputStream out = new FileOutputStream(junk)) {
            out.write("hello".getBytes());
        }
        try {
            BackupManager.restoreBackup(context, db, Uri.fromFile(junk), (d, t) -> { });
            fail("should refuse");
        } catch (BackupManager.BackupException expected) {
            // a message for the user, no crash
        }
        assertEquals(1, dao.getAllNow().size());
        assertEquals("Keep me", dao.getById(id).name);
    }

    @Test
    public void photoShrinking() throws Exception {
        File big = jpeg(4000, 3000);
        long before = big.length();
        assertTrue(PhotoCompressor.shrinkInPlace(big));
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(big.getAbsolutePath(), o);
        assertEquals(2560, o.outWidth);
        assertEquals(1920, o.outHeight);
        assertTrue("smaller file", big.length() < before);

        File small = jpeg(800, 600);
        assertFalse("already small: left alone", PhotoCompressor.shrinkInPlace(small));
    }
}
