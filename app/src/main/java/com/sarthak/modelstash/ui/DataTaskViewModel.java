package com.sarthak.modelstash.ui;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.text.format.Formatter;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.sarthak.modelstash.R;
import com.sarthak.modelstash.backup.BackupManager;
import com.sarthak.modelstash.data.ModelRepository;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Runs the "Backup & data" jobs (back up, restore, export CSV, shrink photos) in
 * the background, one at a time, and tells the home screen how far along it is.
 *
 * A ViewModel, so a job keeps going and its result still arrives when the screen
 * rotates or is rebuilt in the middle of it.
 */
public class DataTaskViewModel extends AndroidViewModel {

    private static final String TAG = "DataTask";
    private static final String PREFS = "data";
    private static final String KEY_LAST_BACKUP = "last_backup";

    /** What is running right now: null title = nothing. */
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

    /** Something to tell you once a job is over (or a question to ask). Handled once. */
    public static final class Event {
        public static final int MESSAGE = 0;
        /** A backup was opened: ask before replacing everything. */
        public static final int CONFIRM_RESTORE = 1;

        public final int kind;
        public final String message;
        @Nullable
        public final Uri uri;
        @Nullable
        public final BackupManager.Summary summary;
        private boolean handled;

        Event(int kind, String message, @Nullable Uri uri, @Nullable BackupManager.Summary summary) {
            this.kind = kind;
            this.message = message;
            this.uri = uri;
            this.summary = summary;
        }

        /** true the first time only, so a rotation doesn't show the same message again. */
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

    /** When the last backup was made on this phone, 0 = never. */
    public long lastBackupAt() {
        return prefs().getLong(KEY_LAST_BACKUP, 0);
    }

    // ---- Jobs ----

    public void backup(Uri target) {
        run(R.string.task_backing_up, () -> {
            BackupManager.Summary s = BackupManager.exportBackup(getApplication(), target, this::report);
            prefs().edit().putLong(KEY_LAST_BACKUP, System.currentTimeMillis()).apply();
            return text(R.plurals.backup_done, s.kits, s.kits, s.photos);
        });
    }

    /** Step 1 of restoring: read what's in the file, then ask (Event.CONFIRM_RESTORE). */
    public void inspect(Uri source) {
        run(R.string.task_reading_backup, () -> {
            BackupManager.Summary s = BackupManager.inspect(getApplication(), source);
            events.postValue(new Event(Event.CONFIRM_RESTORE, "", source, s));
            return null;
        });
    }

    /** Step 2: replace everything. Call on the main thread. */
    public void restore(Uri source) {
        // A delete still waiting for its UNDO would put back kits whose ids the backup may use.
        ModelRepository.get(getApplication()).finalizePendingDelete();
        run(R.string.task_restoring, () -> {
            BackupManager.Summary s = BackupManager.restoreBackup(getApplication(), source, this::report);
            return text(R.plurals.restore_done, s.kits, s.kits, s.photos);
        });
    }

    public void exportCsv(Uri target) {
        run(R.string.task_exporting, () -> {
            int n = BackupManager.exportCsv(getApplication(), target);
            return text(R.plurals.export_done, n, n);
        });
    }

    public void shrinkPhotos() {
        run(R.string.task_shrinking, () -> {
            long saved = BackupManager.shrinkAllPhotos(getApplication(), this::report);
            return saved > 0
                    ? getApplication().getString(R.string.shrink_done,
                    Formatter.formatShortFileSize(getApplication(), saved))
                    : getApplication().getString(R.string.shrink_nothing);
        });
    }

    // ---- Plumbing ----

    private interface Job {
        /** @return the message to show when done, or null for none */
        @Nullable
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
            } catch (BackupManager.BackupException e) {
                Log.w(TAG, "Job failed", e);
                message = getApplication().getString(e.message);
            } catch (Exception | OutOfMemoryError e) {
                Log.w(TAG, "Job failed", e);
                message = getApplication().getString(R.string.task_failed);
            }
            if (message != null) {
                events.postValue(new Event(Event.MESSAGE, message, null, null));
            }
            progress.postValue(null);
        });
    }

    /** Called from the worker thread while a job runs. */
    private void report(int done, int total) {
        Progress now = progress.getValue();
        int title = now != null ? now.title : R.string.task_working;
        progress.postValue(new Progress(title, done, total));
    }

    private String text(int plurals, int count, Object... args) {
        return getApplication().getResources().getQuantityString(plurals, count, args);
    }

    private SharedPreferences prefs() {
        return getApplication().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    @Override
    protected void onCleared() {
        worker.shutdown(); // a running job still finishes; nobody is listening any more
    }
}
