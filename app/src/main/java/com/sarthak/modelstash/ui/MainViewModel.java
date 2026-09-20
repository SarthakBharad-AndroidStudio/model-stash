package com.sarthak.modelstash.ui;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;

import com.sarthak.modelstash.data.ModelKit;
import com.sarthak.modelstash.data.ModelRepository;
import com.sarthak.modelstash.util.ModelSort;
import com.sarthak.modelstash.util.StashStats;

import java.util.List;
import java.util.Objects;

/**
 * Data for the two tabs of MainActivity. Shared by both fragments and kept
 * across rotation, so the search text and sort order aren't lost.
 */
public class MainViewModel extends AndroidViewModel {

    public static final int RECENT_LIMIT = 10;

    private final MutableLiveData<String> query = new MutableLiveData<>("");
    private final MutableLiveData<ModelSort.Mode> sortMode = new MutableLiveData<>(ModelSort.Mode.NAME);

    public final LiveData<List<ModelKit>> recent;
    public final LiveData<StashStats> stats;

    /** Kits you want to buy, newest first. */
    public final LiveData<List<ModelKit>> wishlist;
    public final LiveData<Integer> wishlistCount;
    /** Search results in the chosen sort order; updates when the table, query or sort changes. */
    public final LiveData<List<ModelKit>> visibleModels;

    public MainViewModel(@NonNull Application application) {
        super(application);
        ModelRepository repository = ModelRepository.get(application);
        recent = repository.getRecent(RECENT_LIMIT);
        stats = Transformations.map(repository.getAll(),
                models -> StashStats.from(models, System.currentTimeMillis()));

        wishlist = repository.getWishlist();
        wishlistCount = repository.wishlistCount();

        LiveData<List<ModelKit>> results = Transformations.switchMap(query, q -> repository.search(q));
        MediatorLiveData<List<ModelKit>> sorted = new MediatorLiveData<>();
        sorted.addSource(results, list -> sorted.setValue(ModelSort.sorted(list, sortMode.getValue())));
        sorted.addSource(sortMode, mode -> {
            List<ModelKit> list = results.getValue();
            if (list != null) {
                sorted.setValue(ModelSort.sorted(list, mode));
            }
        });
        visibleModels = sorted;
    }

    public void setQuery(String newQuery) {
        if (!Objects.equals(newQuery, query.getValue())) {
            query.setValue(newQuery);
        }
    }

    @NonNull
    public String getQuery() {
        String q = query.getValue();
        return q == null ? "" : q;
    }

    public void setSortMode(ModelSort.Mode mode) {
        if (mode != sortMode.getValue()) {
            sortMode.setValue(mode);
        }
    }

    @NonNull
    public ModelSort.Mode getSortMode() {
        ModelSort.Mode mode = sortMode.getValue();
        return mode == null ? ModelSort.Mode.NAME : mode;
    }
}