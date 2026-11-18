package com.sarthak.modelstash.ui;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.sarthak.modelstash.R;
import com.sarthak.modelstash.data.AppDatabase;
import com.sarthak.modelstash.data.ModelDao;
import com.sarthak.modelstash.data.ModelKit;
import com.sarthak.modelstash.util.BuildStatus;
import com.sarthak.modelstash.util.CsvExport;
import com.sarthak.modelstash.util.CsvImport;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Runs the "Import & export" jobs in the background, one at a time:
 * - export: every kit (stash and wishlist, every field) to a CSV file you choose;
 * - import: a CSV back in, skipping kits you already have.
 *
 * Export + import together are the app's backup of your list (photos aren't in
 * a CSV). A ViewModel, so a job keeps going, and its result still arrives, when
 * the screen rotates in the middle of it.
 */
public class DataTaskViewModel extends AndroidViewModel {

    private static final String TAG = "DataTask";
    private static final String PREFS = "data";
    private static final String KEY_LAST_EXPORT = "last_export";
    /** A CSV of tens of thousands of kits; anything bigger isn't one of ours. */
    private static final int MAX_CSV_BYTES = 20 * 1024 * 1024;

    /** What is running right now (null = nothing). */
    public static final class Progress {
        @StringRes
        public final int title;
        public final int done;
        public final int total;

        Progress(@StringRes int title, int done, int total) {
            this.title = title;
            this.done = done;
            this.total = total;
        }
    }

    /** A message to show once a job is over. Shown once, not again after a rotation. */
    public static final class Event {
        public final String message;
        private boolean handled;

        Event(String message) {
            this.message = message;
        }

        public boolean take() {
            if (handled) {
                return false;
            }
            handled = true;
            return true;
        }
    }

    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final MutableLiveData<Progress> progress = new MutableLiveData<>(null);
    private final MutableLiveData<Event> events = new MutableLiveData<>(null);

    public DataTaskViewModel(@NonNull Application application) {
        super(application);
    }

    public LiveData<Progress> progress() {
        return progress;
    }

    public LiveData<Event> events() {
        return events;
    }

    public boolean isBusy() {
        return progress.getValue() != null;
    }

    /** When the list was last exported on this phone, 0 = never. */
    public long lastExportAt() {
        return prefs().getLong(KEY_LAST_EXPORT, 0);
    }

    // ---- Jobs ----

    /** Writes every kit to the CSV file at `target` (from the system "save as" screen). */
    public void exportCsv(Uri target) {
        run(R.string.task_exporting, () -> {
            Context app = getApplication();
            List<ModelKit> kits = AppDatabase.get(app).modelDao().getAllNow();
            Collections.sort(kits, (a, b) -> { // stash first, then wishlist, A–Z
                if (a.wishlist != b.wishlist) {
                    return a.wishlist ? 1 : -1;
                }
                return a.name.compareToIgnoreCase(b.name);
            });
            String csv = CsvExport.write(kits, BuildStatus.labels(app),
                    app.getString(R.string.csv_list_stash), app.getString(R.string.csv_list_wishlist));
            try (OutputStream out = app.getContentResolver().openOutputStream(target)) {
                if (out == null) {
                    return app.getString(R.string.export_failed);
                }
                out.write(csv.getBytes(StandardCharsets.UTF_8));
            } catch (IOException | RuntimeException e) {
                Log.w(TAG, "Export failed", e);
                return app.getString(R.string.export_failed);
            }
            prefs().edit().putLong(KEY_LAST_EXPORT, System.currentTimeMillis()).apply();
            return app.getResources().getQuantityString(R.plurals.export_done, kits.size(), kits.size());
        });
    }

    /**
     * Reads a CSV and adds its kits. Rows that say which list they belong to
     * (a "list" column, as in an export) go there; the others go to the Wishlist
     * if you imported from the Wishlist tab, otherwise to your stash. Kits you
     * already have are skipped, so importing the same file twice adds nothing.
     */
    public void importCsv(Uri source, boolean toWishlist) {
        run(R.string.task_importing, () -> {
            Context app = getApplication();
            String csv;
            try {
                csv = readText(app, source);
            } catch (IOException | RuntimeException e) {
                Log.w(TAG, "Import failed", e);
                return app.getString(R.string.import_failed);
            }
            List<ModelKit> parsed = CsvImport.parse(csv, toWishlist, BuildStatus.labels(app));
            if (parsed.isEmpty()) {
                return app.getString(R.string.import_empty);
            }
            ModelDao dao = AppDatabase.get(app).modelDao();
            List<ModelKit> fresh = CsvImport.withoutDuplicates(parsed, dao.getAllNow());
            int skipped = parsed.size() - fresh.size();
            if (!fresh.isEmpty()) {
                long now = System.currentTimeMillis();
                for (int i = 0; i < fresh.size(); i++) {
                    if (fresh.get(i).createdAt <= 0) {
                        fresh.get(i).createdAt = now - (fresh.size() - 1 - i) * 1000L; // file order
                    }
                }
                dao.insertAll(fresh); // the lists on screen update themselves
            }
            String added = app.getResources().getQuantityString(R.plurals.import_done, fresh.size(), fresh.size());
            return skipped == 0 ? added : added + " · "
                    + app.getResources().getQuantityString(R.plurals.import_skipped, skipped, skipped);
        });
    }

    // ---- Plumbing ----

    private interface Job {
        /** @return the message to show when done */
        String run() throws Exception;
    }

    private void run(@StringRes int title, Job job) {
        if (isBusy()) {
            return; // one job at a time
        }
        progress.setValue(new Progress(title, 0, 0));
        worker.execute(() -> {
            String message;
            try {
                message = job.run();
            } catch (Exception | OutOfMemoryError e) {
                Log.w(TAG, "Job failed", e);
                message = getApplication().getString(R.string.task_failed);
            }
            if (message != null) {
                events.postValue(new Event(message));
            }
            progress.postValue(null);
        });
    }

    private static String readText(Context context, Uri uri) throws IOException {
        try (InputStream in = context.getContentResolver().openInputStream(uri)) {
            if (in == null) {
                throw new IOException("Could not open the file");
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[16 * 1024];
            int read;
            int total = 0;
            while ((read = in.read(buffer)) != -1) {
                total += read;
                if (total > MAX_CSV_BYTES) {
                    throw new IOException("File too big for a kit list");
                }
                out.write(buffer, 0, read);
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private SharedPreferences prefs() {
        return getApplication().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    @Override
    protected void onCleared() {
        worker.shutdown(); // a running job still finishes; nobody is listening any more
    }
}
