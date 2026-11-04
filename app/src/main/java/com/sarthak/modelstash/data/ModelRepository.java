package com.sarthak.modelstash.data;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.Nullable;
import androidx.core.util.Consumer;
import androidx.lifecycle.LiveData;

import com.sarthak.modelstash.util.PhotoStorage;

import java.util.List;
import java.util.ArrayList;
import java.util.HashSet;
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

    private final AppDatabase db;
    private final ModelDao dao;
    private final Handler mainThread = new Handler(Looper.getMainLooper());

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

    /** All models when the query is blank, otherwise models whose name contains it. */
    public LiveData<List<ModelKit>> search(@Nullable String query) {
        if (query == null || query.trim().isEmpty()) {
            return dao.getAll();
        }
        return dao.searchByName(escapeLike(query.trim()));
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
     * Adds many models at once (CSV import). They are timestamped in file order,
     * so the last row is the newest. onDone gets the number added, on the main thread.
     */
    public void importAll(List<ModelKit> models, @Nullable Consumer<Integer> onDone) {
        AppDatabase.IO.execute(() -> {
            long now = System.currentTimeMillis();
            for (int i = 0; i < models.size(); i++) {
                models.get(i).createdAt = now - (models.size() - 1 - i) * 1000L;
            }
            int added = dao.insertAll(models).size();
            if (onDone != null) {
                mainThread.post(() -> onDone.accept(added));
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

    /** Makes %, _ and \ in the user's text match literally inside LIKE '%...%'. */
    static String escapeLike(String text) {
        return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
