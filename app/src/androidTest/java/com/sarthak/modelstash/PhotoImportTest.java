package com.sarthak.modelstash.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.sarthak.modelstash.util.PhotoStorage;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The gallery import on a real phone: good pictures arrive, bad ones (not a picture,
 * a photo the app may not read) are skipped and counted instead of crashing, and
 * nothing is left behind when the screen is closed. Uses its own test files, never
 * your photos.
 */
@RunWith(AndroidJUnit4.class)
public class PhotoImportTest {

    private Context context;
    private File work;

    @Before
    public void setUp() throws Exception {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        work = new File(context.getCacheDir(), "import-test");
        //noinspection ResultOfMethodCallIgnored
        work.mkdirs();
    }

    private Uri picture(String name) throws Exception {
        File file = new File(work, name);
        Bitmap bitmap = Bitmap.createBitmap(64, 48, Bitmap.Config.ARGB_8888);
        bitmap.eraseColor(Color.BLUE);
        try (FileOutputStream out = new FileOutputStream(file)) {
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out);
        }
        return Uri.fromFile(file);
    }

    private Uri notAPicture() throws Exception {
        File file = new File(work, "notes.txt");
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write("this is not a photo".getBytes());
        }
        return Uri.fromFile(file);
    }

    /** A gallery photo this app may not read (or that is gone): the provider refuses with an exception. */
    private static Uri forbidden() {
        return Uri.parse("content://media/external/images/media/2147483000");
    }

    private int photoFileCount() {
        File[] files = new File(context.getFilesDir(), "photos").listFiles();
        return files == null ? 0 : files.length;
    }

    private void onMain(Runnable r) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(r);
    }

    /** Waits until no copy is running (at most 20 s). */
    private void awaitIdle(PhotoImportViewModel vm) throws InterruptedException {
        long end = System.currentTimeMillis() + 20_000;
        AtomicReference<Integer> pending = new AtomicReference<>(1);
        while (System.currentTimeMillis() < end) {
            onMain(() -> pending.set(vm.pendingCount()));
            if (pending.get() == 0) {
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("Import didn't finish in 20 s");
    }

    @Test
    public void goodPhotosArrive_badOnesAreSkippedNotCrashing() throws Exception {
        PhotoImportViewModel vm = new PhotoImportViewModel((Application) context.getApplicationContext());
        List<Uri> picked = Arrays.asList(picture("a.jpg"), notAPicture(), forbidden(), picture("b.jpg"));

        onMain(() -> vm.importPhotos(picked));
        awaitIdle(vm);

        List<String> ready = new ArrayList<>();
        int[] failed = new int[1];
        onMain(() -> {
            ready.addAll(vm.takeReady());
            failed[0] = vm.takeFailed();
        });
        assertEquals("two real pictures arrive", 2, ready.size());
        assertEquals("the text file and the forbidden photo are counted as failed", 2, failed[0]);
        for (String path : ready) {
            assertTrue(path, PhotoStorage.isImage(new File(path)));
        }
        // Handed over once only.
        onMain(() -> assertTrue(vm.takeReady().isEmpty()));

        for (String path : ready) {
            PhotoStorage.delete(path);
        }
        onMain(vm::onCleared);
    }

    @Test
    public void closingTheScreenDeletesPhotosItNeverTook() throws Exception {
        int before = photoFileCount();
        PhotoImportViewModel vm = new PhotoImportViewModel((Application) context.getApplicationContext());
        List<Uri> picked = Arrays.asList(picture("c.jpg"), picture("d.jpg"));

        onMain(() -> vm.importPhotos(picked));
        awaitIdle(vm);
        assertEquals(before + 2, photoFileCount());

        onMain(vm::onCleared); // the screen was closed without taking them
        assertEquals("nothing left behind", before, photoFileCount());
    }

    @Test
    public void failedCopyLeavesNoFile() {
        int before = photoFileCount();
        try {
            PhotoStorage.copyFromUri(context, forbidden());
            throw new AssertionError("should have failed");
        } catch (java.io.IOException expected) {
            // SecurityException turned into an IOException: catchable, no crash
        }
        assertEquals(before, photoFileCount());
        assertFalse(PhotoStorage.isImage(new File(work, "missing.jpg")));
    }
}
