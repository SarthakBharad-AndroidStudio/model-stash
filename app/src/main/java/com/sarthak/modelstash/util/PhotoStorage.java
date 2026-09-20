package com.sarthak.modelstash.util;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

/**
 * Model photos live in the app's private folder (files/photos/), so they
 * survive even if you delete the original from your gallery, and they are
 * removed when the app is uninstalled.
 */
public final class PhotoStorage {

    private PhotoStorage() {
    }

    private static File photoDir(Context context) {
        File dir = new File(context.getFilesDir(), "photos");
        if (!dir.exists()) {
            //noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
        }
        return dir;
    }

    /** A new, unique, still-empty file for a photo. */
    public static File newPhotoFile(Context context) {
        return new File(photoDir(context), "IMG_" + UUID.randomUUID() + ".jpg");
    }

    /** A content:// Uri the camera app is allowed to write into (see file_paths.xml). */
    public static Uri uriForCamera(Context context, File file) {
        return FileProvider.getUriForFile(context, context.getPackageName() + ".fileprovider", file);
    }

    /** Copies a picked gallery image into private storage. Call off the main thread. */
    public static File copyFromUri(Context context, Uri source) throws IOException {
        File target = newPhotoFile(context);
        try (InputStream in = context.getContentResolver().openInputStream(source);
             OutputStream out = new FileOutputStream(target)) {
            if (in == null) {
                throw new IOException("Could not open the selected image");
            }
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
        } catch (IOException e) {
            //noinspection ResultOfMethodCallIgnored
            target.delete();
            throw e;
        }
        return target;
    }

    public static void delete(@Nullable String path) {
        if (path != null) {
            //noinspection ResultOfMethodCallIgnored
            new File(path).delete();
        }
    }
}