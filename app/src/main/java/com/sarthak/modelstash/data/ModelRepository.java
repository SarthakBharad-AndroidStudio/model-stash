package com.sarthak.modelstash.data;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.Nullable;
import androidx.core.util.Consumer;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.sarthak.modelstash.util.PhotoStorage;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The only class the screens use to read or change models. It hides Room,
 * runs writes on a background thread, and keeps photo files in sync with rows.
 */
public class ModelRepository {

    private static volatile ModelRepository instance;

    /** Result of loadWithPhotos: the model (null if it was deleted) and its photos in order. */
    public interface ModelWithPhotos {
        void onLoaded(@Nullable ModelKit model, List<ModelPhoto> photos);
    }

    /**
     * Kits that were just deleted and can still be brought back (the "UNDO" in the
     * snackbar). Their rows are gone but their photo files are kept until the undo
     * chance has passed (finalizeDelete), or until the next delete.
     */
    public static final class DeletedKits {
        public final List<ModelKit> kits;
        final List<ModelPhoto> photos;

        DeletedKits(List<ModelKit> kits, List<ModelPhoto> photos) {
            this.kits = kits;
            this.photos = photos;
        }
    }

    private final AppDatabase db;
    private final ModelDao dao;
    private final Handler mainThread = new Handler(Looper.getMainLooper());
    private final MutableLiveData<DeletedKits> recentlyDeleted = new MutableLiveData<>(null);

    private ModelRepository(Context context) {
        db = AppDatabase.get(context);
        dao = db.modelDao();
    }

    public static ModelRepository get(Context context) {
        if (instance == null) {
            synchronized (ModelRepository.class) {
                if (instance == null) {
                    instance = new ModelRepository(context.getApplicationContext());
                }
            }
        }
        return instance;
    }

    // ---- Reads (LiveData updates itself when the table changes) ----

    public LiveData<List<ModelKit>> getRecent(int limit) {
        return dao.getRecent(limit);
    }

    public LiveData<Integer> count() {
        return dao.count();
    }

    public LiveData<ModelKit> observe(long id) {
        return dao.observeById(id);
    }

    /** The model's photos in order (cover first); re-emits whenever they change. */
    public LiveData<List<ModelPhoto>> observePhotos(long modelId) {
        return dao.observePhotos(modelId);
    }

    /** Loads a model and its photos together, once; the result arrives on the main thread. */
    public void loadWithPhotos(long id, ModelWithPhotos onLoaded) {
        AppDatabase.IO.execute(() -> {
            ModelKit model = dao.getById(id);
            List<ModelPhoto> photos = model != null ? dao.getPhotos(id) : new ArrayList<>();
            mainThread.post(() -> onLoaded.onLoaded(model, photos));
        });
    }

    /** Loads one model once, in the background; the result arrives on the main thread (null if gone). */
    public void load(long id, Consumer<ModelKit> onLoaded) {
        AppDatabase.IO.execute(() -> {
            ModelKit model = dao.getById(id);
            mainThread.post(() -> onLoaded.accept(model));
        });
    }

    /** Every kit (owned and wishlist), once, in the background; the result arrives on the main thread. */
    public void loadAllKits(Consumer<List<ModelKit>> onLoaded) {
        AppDatabase.IO.execute(() -> {
            List<ModelKit> kits = dao.getAllNow();
            mainThread.post(() -> onLoaded.accept(kits));
        });
    }

    public LiveData<List<ModelKit>> getAll() {
        return dao.getAll();
    }

    public LiveData<List<ModelKit>> getWishlist() {
        return dao.getWishlist();
    }

    public LiveData<Integer> wishlistCount() {
        return dao.wishlistCount();
    }

    public LiveData<List<ModelKit>> getRecentWithStatus(int status, int limit) {
        return dao.getRecentWithStatus(status, limit);
    }

    // ---- Writes ----

    /** Inserts a new model (id == 0) or updates an existing one. onDone runs on the main thread. */
    public void save(ModelKit model, @Nullable Runnable onDone) {
        AppDatabase.IO.execute(() -> {
            if (model.id == 0) {
                model.createdAt = System.currentTimeMillis();
                model.id = dao.insert(model);
            } else {
                dao.update(model);
            }
            if (onDone != null) {
                mainThread.post(onDone);
            }
        });
    }

    /**
     * Saves a model together with its photos, replacing the ones it had. The first
     * photo becomes the cover (models.photo_path), which is what every card shows.
     * Files of photos that were removed are deleted afterwards. onDone runs on the main thread.
     */
    public void save(ModelKit model, List<ModelPhoto> photos, @Nullable Runnable onDone) {
        AppDatabase.IO.execute(() -> {
            List<String> removed = new ArrayList<>();
            db.runInTransaction(() -> {
                model.photoPath = photos.isEmpty() ? null : photos.get(0).path;
                if (model.id == 0) {
                    model.createdAt = System.currentTimeMillis();
                    model.id = dao.insert(model);
                } else {
                    dao.update(model);
                }
                Set<String> kept = new HashSet<>();
                for (ModelPhoto photo : photos) {
                    kept.add(photo.path);
                }
                for (ModelPhoto old : dao.getPhotos(model.id)) {
                    if (!kept.contains(old.path)) {
                        removed.add(old.path);
                    }
                }
                dao.deletePhotos(model.id);
                for (int i = 0; i < photos.size(); i++) {
                    ModelPhoto photo = photos.get(i);
                    photo.id = 0;
                    photo.modelId = model.id;
                    photo.position = i;
                }
                dao.insertPhotos(photos);
            });
            // Only once the database is safely written: a crash before this point loses no photo.
            for (String path : removed) {
                PhotoStorage.delete(path);
            }
            if (onDone != null) {
                mainThread.post(onDone);
            }
        });
    }

    /** Bought it: moves a wishlist model into the owned catalogue. */
    public void moveToStash(ModelKit model, @Nullable Runnable onDone) {
        model.wishlist = false;
        save(model, onDone);
    }

    /**
     * Adds many models at once (CSV import). Rows with an "added" date keep it; the
     * others are timestamped in file order, so the last row is the newest.
     * onDone gets the number added, on the main thread.
     */
    public void importAll(List<ModelKit> models, @Nullable Consumer<Integer> onDone) {
        AppDatabase.IO.execute(() -> {
            long now = System.currentTimeMillis();
            for (int i = 0; i < models.size(); i++) {
                if (models.get(i).createdAt <= 0) {
                    models.get(i).createdAt = now - (models.size() - 1 - i) * 1000L;
                }
            }
            int added = dao.insertAll(models).size();
            if (onDone != null) {
                mainThread.post(() -> onDone.accept(added));
            }
        });
    }

    // ---- Bulk actions (multi-select) ----

    /** Sets the build stage of several owned kits at once. onDone runs on the main thread. */
    public void setStatus(List<Long> ids, int status, @Nullable Runnable onDone) {
        List<Long> copy = new ArrayList<>(ids);
        AppDatabase.IO.execute(() -> {
            dao.setStatus(copy, status);
            if (onDone != null) {
                mainThread.post(onDone);
            }
        });
    }

    /** Moves several wishlist kits into the owned catalogue. onDone runs on the main thread. */
    public void moveToStash(List<Long> ids, @Nullable Runnable onDone) {
        List<Long> copy = new ArrayList<>(ids);
        AppDatabase.IO.execute(() -> {
            dao.moveToStash(copy);
            if (onDone != null) {
                mainThread.post(onDone);
            }
        });
    }

    // ---- Delete with undo ----

    /** The kits waiting for their "UNDO" chance to pass, or null. Observe it to show the snackbar. */
    public LiveData<DeletedKits> recentlyDeleted() {
        return recentlyDeleted;
    }

    /**
     * Deletes kits but keeps their photo files for a moment, so the delete can be
     * undone. Call on the main thread; onDone runs on the main thread. A previous
     * delete still waiting is made final first.
     */
    public void deleteWithUndo(List<ModelKit> kits, @Nullable Runnable onDone) {
        DeletedKits previous = recentlyDeleted.getValue();
        if (previous != null) {
            finalizeDelete(previous);
        }
        List<Long> ids = new ArrayList<>();
        for (ModelKit k : kits) {
            ids.add(k.id);
        }
        AppDatabase.IO.execute(() -> {
            List<ModelKit> rows = new ArrayList<>();
            List<ModelPhoto> photos = new ArrayList<>();
            db.runInTransaction(() -> {
                rows.addAll(dao.getByIds(ids));
                photos.addAll(dao.getPhotosFor(ids));
                dao.deletePhotosFor(ids);
                dao.deleteByIds(ids);
            });
            mainThread.post(() -> {
                if (!rows.isEmpty()) {
                    recentlyDeleted.setValue(new DeletedKits(rows, photos));
                }
                if (onDone != null) {
                    onDone.run();
                }
            });
        });
    }

    /** UNDO: puts the kits and their photos back exactly as they were (same ids). Main thread. */
    public void undoDelete(DeletedKits deleted) {
        if (recentlyDeleted.getValue() != deleted) {
            return; // already final
        }
        recentlyDeleted.setValue(null);
        AppDatabase.IO.execute(() -> db.runInTransaction(() -> {
            dao.insertAll(deleted.kits); // ids are kept, so the photo rows still match
            List<ModelPhoto> photos = new ArrayList<>(deleted.photos);
            for (ModelPhoto p : photos) {
                p.id = 0;
            }
            if (!photos.isEmpty()) {
                dao.insertPhotos(photos);
            }
        }));
    }

    /** The undo chance has passed: delete the photo files for good. Main thread. */
    public void finalizeDelete(DeletedKits deleted) {
        if (recentlyDeleted.getValue() != deleted) {
            return;
        }
        recentlyDeleted.setValue(null);
        AppDatabase.IO.execute(() -> {
            for (ModelPhoto p : deleted.photos) {
                PhotoStorage.delete(p.path);
            }
            for (ModelKit k : deleted.kits) {
                PhotoStorage.delete(k.photoPath);
            }
        });
    }

    /** Makes a delete that is still waiting for its UNDO final right away (before a restore). Main thread. */
    public void finalizePendingDelete() {
        DeletedKits pending = recentlyDeleted.getValue();
        if (pending != null) {
            finalizeDelete(pending);
        }
    }

    /**
     * Housekeeping at app start: deletes photo files nothing in the database points
     * at (left over when the app was closed during an undo, a crash mid-save, ...).
     * Only files older than minAgeMs, so a photo you are adding right now is never touched.
     */
    public void sweepOrphanPhotos(File photoDir, long minAgeMs) {
        AppDatabase.IO.execute(() -> {
            File[] files = photoDir.listFiles();
            if (files == null || files.length == 0) {
                return;
            }
            // Compared by file name, not full path: the folder's path can differ after
            // moving to a new phone, and a file must never be deleted just for that.
            Set<String> used = new HashSet<>();
            try {
                for (String path : dao.allReferencedPhotoPaths()) {
                    if (path != null) {
                        used.add(new File(path).getName());
                    }
                }
            } catch (RuntimeException e) {
                return; // can't tell what's used: touch nothing
            }
            long cutoff = System.currentTimeMillis() - minAgeMs;
            for (File f : files) {
                if (f.isFile() && f.lastModified() < cutoff && !used.contains(f.getName())) {
                    //noinspection ResultOfMethodCallIgnored
                    f.delete();
                }
            }
        });
    }

    /** Deletes the row, its photo rows and every photo file. onDone runs on the main thread. */
    public void delete(ModelKit model, @Nullable Runnable onDone) {
        AppDatabase.IO.execute(() -> {
            List<ModelPhoto> photos = dao.getPhotos(model.id);
            db.runInTransaction(() -> {
                dao.deletePhotos(model.id); // ON DELETE CASCADE would too; this doesn't rely on it
                dao.delete(model);
            });
            for (ModelPhoto photo : photos) {
                PhotoStorage.delete(photo.path);
            }
            PhotoStorage.delete(model.photoPath);
            if (onDone != null) {
                mainThread.post(onDone);
            }
        });
    }
}
