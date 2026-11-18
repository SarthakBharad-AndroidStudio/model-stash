package com.sarthak.modelstash.ui;

import android.app.Application;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.sarthak.modelstash.util.PhotoCompressor;
import com.sarthak.modelstash.util.PhotoStorage;

import java.io.File;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

/**
 * Copies photos picked from the gallery into the app's photo folder, in the
 * background, for the add/edit screen.
 *
 * Why a ViewModel: it survives rotation, a theme switch and the other reasons the
 * screen gets rebuilt, so a copy that is halfway through when that happens still
 * finishes and the photo still arrives (before, it was thrown away). It is cleared
 * only when the screen is closed for good, and then deletes anything the screen
 * never took.
 *
 * How the screen uses it (all on the main thread):
 * - importPhotos(uris) to start; pending() counts the photos still being copied;
 * - updates() fires when photos are ready; takeReady() hands them over (each once);
 * - takeFailed() says how many could not be copied, so the screen can say so.
 *
 * Each photo is also shrunk on the way in (PhotoCompressor: at most 2560 px, JPEG),
 * camera photos included (addCameraPhoto), so a kit's six photos take ~3 MB, not 40.
 *
 * Every copy runs on one background thread, one photo at a time, so a batch of
 * large photos never competes for memory or storage with itself or the database.
 * Any failure (a cloud photo not downloaded yet, a file that isn't a picture,
 * permission revoked, storage full) skips that one photo instead of crashing.
 */
public class PhotoImportViewModel extends AndroidViewModel {

    private static final String TAG = "PhotoImport";

    private final ExecutorService copier = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    private final MutableLiveData<Integer> pending = new MutableLiveData<>(0);
    private final MutableLiveData<Integer> updates = new MutableLiveData<>(0);

    // Main thread only.
    private final List<String> ready = new ArrayList<>();
    private int failed;
    private boolean cleared;

    public PhotoImportViewModel(@NonNull Application application) {
        super(application);
    }

    /** How many picked photos are still being copied. */
    public LiveData<Integer> pending() {
        return pending;
    }

    public int pendingCount() {
        Integer value = pending.getValue();
        return value == null ? 0 : value;
    }

    /** Changes whenever photos are ready or failed: call takeReady() and takeFailed(). */
    public LiveData<Integer> updates() {
        return updates;
    }

    /** Starts copying; call on the main thread. */
    public void importPhotos(List<Uri> uris) {
        if (cleared || uris.isEmpty()) {
            return;
        }
        pending.setValue(pendingCount() + uris.size());
        Application app = getApplication();
        for (Uri uri : uris) {
            try {
                copier.execute(() -> {
                    String path = null;
                    try {
                        File copy = PhotoStorage.copyFromUri(app, uri);
                        PhotoCompressor.shrinkInPlace(copy); // keeps the original if shrinking fails
                        path = copy.getAbsolutePath();
                    } catch (Exception e) {
                        // Never let one bad photo take the app down.
                        Log.w(TAG, "Couldn't import " + uri, e);
                    }
                    String result = path;
                    main.post(() -> finished(result));
                });
            } catch (RejectedExecutionException e) {
                finished(null); // only after onCleared, when nobody is waiting any more
            }
        }
    }

    /**
     * A photo the camera app just wrote to `path`: shrink it in the background, then
     * deliver it like a gallery photo (takeReady). Counts as pending meanwhile.
     */
    public void addCameraPhoto(String path) {
        if (cleared) {
            PhotoStorage.delete(path);
            return;
        }
        pending.setValue(pendingCount() + 1);
        try {
            copier.execute(() -> {
                String result = path;
                try {
                    File file = new File(path);
                    if (!PhotoStorage.isImage(file)) {
                        PhotoStorage.delete(path);
                        result = null; // the camera app saved nothing usable
                    } else {
                        PhotoCompressor.shrinkInPlace(file);
                    }
                } catch (Exception e) {
                    Log.w(TAG, "Couldn't process camera photo", e); // keep it as it is
                }
                String done = result;
                main.post(() -> finished(done));
            });
        } catch (RejectedExecutionException e) {
            finished(path);
        }
    }

    private void finished(String path) {
        pending.setValue(Math.max(0, pendingCount() - 1));
        if (cleared) {
            PhotoStorage.delete(path); // the screen is gone: nobody will use it
            return;
        }
        if (path != null) {
            ready.add(path);
        } else {
            failed++;
        }
        Integer n = updates.getValue();
        updates.setValue(n == null ? 1 : n + 1);
    }

    /** The photos copied since the last call, in the order picked. Each path is handed over once. */
    public List<String> takeReady() {
        List<String> out = new ArrayList<>(ready);
        ready.clear();
        return out;
    }

    /** How many photos failed since the last call. */
    public int takeFailed() {
        int out = failed;
        failed = 0;
        return out;
    }

    @Override
    protected void onCleared() {
        // The screen was closed (not just rotated). Photos it never took are deleted;
        // copies still running delete their file when they finish (see finished()).
        cleared = true;
        copier.shutdownNow();
        for (String path : ready) {
            PhotoStorage.delete(path);
        }
        ready.clear();
    }
}
