package com.sarthak.modelstash.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;

/** Photo shrinking on a real phone, with its own test pictures (never your photos). */
@RunWith(AndroidJUnit4.class)
public class PhotoCompressorTest {

    private final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    private final List<File> made = new ArrayList<>();

    @After
    public void tearDown() {
        for (File f : made) {
            //noinspection ResultOfMethodCallIgnored
            f.delete();
        }
    }

    private File jpeg(int w, int h) throws Exception {
        File file = new File(context.getCacheDir(), "shrink-test-" + w + "x" + h + ".jpg");
        Bitmap bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        bitmap.eraseColor(Color.rgb(34, 57, 111));
        try (FileOutputStream out = new FileOutputStream(file)) {
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out);
        }
        bitmap.recycle();
        made.add(file);
        return file;
    }

    @Test
    public void bigPhotoIsShrunkTo2560() throws Exception {
        File big = jpeg(4000, 3000);
        long before = big.length();
        assertTrue(PhotoCompressor.shrinkInPlace(big));
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(big.getAbsolutePath(), o);
        assertEquals(2560, o.outWidth);
        assertEquals(1920, o.outHeight);
        assertTrue("smaller file", big.length() < before);
    }

    @Test
    public void smallPhotoIsLeftAlone() throws Exception {
        File small = jpeg(800, 600);
        long before = small.length();
        assertFalse(PhotoCompressor.shrinkInPlace(small));
        assertEquals(before, small.length());
    }

    @Test
    public void notAPictureIsLeftAlone() throws Exception {
        File text = new File(context.getCacheDir(), "shrink-test.txt");
        made.add(text);
        try (FileOutputStream out = new FileOutputStream(text)) {
            out.write("not a photo".getBytes());
        }
        assertFalse(PhotoCompressor.shrinkInPlace(text));
        assertTrue(text.isFile());
    }
}
