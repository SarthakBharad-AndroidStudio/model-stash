package com.sarthak.modelstash.data;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.Nullable;
import androidx.core.util.Consumer;
import androidx.lifecycle.LiveData;

import com.sarthak.modelstash.util.PhotoStorage;

import java.util.List;

/**
 * The only class the screens use to read or change models. It hides Room,
 * runs writes on a background thread, and keeps photo files in sync with rows.
 */
public class ModelRepository {

    private static volatile ModelRepository instance;

    private final ModelDao dao;
    private final Handler mainThread = new Handler(Looper.getMainLooper());

    private ModelRepository(Context context) {
        dao = AppDatabase.get(context).modelDao();
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

    public LiveData<List<ModelKit>> getAll() {
        return dao.getAll();
    }

    /** All models when the query is blank, otherwise models whose name contains it. */
    public LiveData<List<ModelKit>> search(@Nullable String query) {
        if (query == null || query.trim().isEmpty()) {
            return dao.getAll();
        }
        return dao.searchByName(escapeLike(query.trim()));
    }

    public LiveData<List<ModelKit>> getWishlist() {
        return dao.getWishlist();
    }

    public LiveData<Integer> wishlistCount() {
        return dao.wishlistCount();
    }

    public LiveData<ModelKit> observe(long id) {
        return dao.observeById(id);
    }

    /** Loads one model once, in the background; the result arrives on the main thread (null if gone). */
    public void load(long id, Consumer<ModelKit> onLoaded) {
        AppDatabase.IO.execute(() -> {
            ModelKit model = dao.getById(id);
            mainThread.post(() -> onLoaded.accept(model));
        });
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

    /** Bought it: moves a wishlist model into the owned catalogue. */
    public void moveToStash(ModelKit model, @Nullable Runnable onDone) {
        model.wishlist = false;
        save(model, onDone);
    }

    /** Deletes the row and its photo file. onDone runs on the main thread. */
    public void delete(ModelKit model, @Nullable Runnable onDone) {
        AppDatabase.IO.execute(() -> {
            dao.delete(model);
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