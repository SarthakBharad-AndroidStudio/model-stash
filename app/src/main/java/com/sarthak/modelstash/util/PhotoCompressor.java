package com.sarthak.modelstash.util;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Shrinks a stored photo to at most 2560 px on its long side, as a JPEG at 85%.
 * A 12 MP phone photo goes from ~4–8 MB to ~0.5 MB and looks the same on a phone
 * screen, so the stash takes far less space and backups stay small.
 *
 * The photo is also turned upright for good (phones save sideways and only note the
 * turn in the file), so it shows the right way everywhere.
 *
 * Always safe: if anything goes wrong, or the result wouldn't be smaller, the
 * original file is left exactly as it was. Call off the main thread.
 */
public final class PhotoCompressor {

    private PhotoCompressor() {
    }

    /**
     * Replaces the file with a smaller version when that helps.
     *
     * @return true if the file was replaced, false if it was left as it was
     */
    public static boolean shrinkInPlace(File file) {
        File temp = new File(file.getParentFile(), file.getName() + ".shrink.tmp");
        Bitmap decoded = null;
        Bitmap scaled = null;
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
            int w = bounds.outWidth;
            int h = bounds.outHeight;
            if (w <= 0 || h <= 0) {
                return false; // not something we can decode: leave it
            }
            int rotation = rotationOf(file);
            boolean isJpeg = "image/jpeg".equals(bounds.outMimeType);
            if (ImageSizing.fits(w, h, ImageSizing.MAX_SIDE) && rotation == 0 && isJpeg) {
                return false; // already small, upright and a JPEG
            }

            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = ImageSizing.sampleSize(w, h, ImageSizing.MAX_SIDE);
            decoded = BitmapFactory.decodeFile(file.getAbsolutePath(), options);
            if (decoded == null) {
                return false;
            }
            int[] size = ImageSizing.targetSize(decoded.getWidth(), decoded.getHeight(), ImageSizing.MAX_SIDE);
            Matrix matrix = new Matrix();
            matrix.postScale((float) size[0] / decoded.getWidth(), (float) size[1] / decoded.getHeight());
            if (rotation != 0) {
                matrix.postRotate(rotation);
            }
            scaled = Bitmap.createBitmap(decoded, 0, 0, decoded.getWidth(), decoded.getHeight(), matrix, true);

            try (FileOutputStream out = new FileOutputStream(temp)) {
                if (!scaled.compress(Bitmap.CompressFormat.JPEG, ImageSizing.JPEG_QUALITY, out)) {
                    throw new IOException("JPEG encoding failed");
                }
            }
            // Keep the smaller one. A rotated photo is kept even if not smaller, so it stays upright.
            if (temp.length() == 0 || (rotation == 0 && isJpeg && temp.length() >= file.length())) {
                //noinspection ResultOfMethodCallIgnored
                temp.delete();
                return false;
            }
            if (!temp.renameTo(file)) {
                //noinspection ResultOfMethodCallIgnored
                temp.delete();
                return false;
            }
            return true;
        } catch (IOException | RuntimeException | OutOfMemoryError e) {
            //noinspection ResultOfMethodCallIgnored
            temp.delete();
            return false; // the original is untouched
        } finally {
            if (scaled != null && scaled != decoded) {
                scaled.recycle();
            }
            if (decoded != null) {
                decoded.recycle();
            }
        }
    }

    private static int rotationOf(File file) {
        try {
            int o = new ExifInterface(file.getAbsolutePath())
                    .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            switch (o) {
                case ExifInterface.ORIENTATION_ROTATE_90:
                    return 90;
                case ExifInterface.ORIENTATION_ROTATE_180:
                    return 180;
                case ExifInterface.ORIENTATION_ROTATE_270:
                    return 270;
                default:
                    return 0;
            }
        } catch (IOException | RuntimeException e) {
            return 0;
        }
    }
}
