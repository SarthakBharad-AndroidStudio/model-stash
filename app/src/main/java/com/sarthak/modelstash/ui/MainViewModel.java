package com.sarthak.modelstash.ui;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;

import com.sarthak.modelstash.data.ModelKit;
import com.sarthak.modelstash.data.ModelRepository;
import com.sarthak.modelstash.util.BuildStatus;
import com.sarthak.modelstash.util.ModelSort;
import com.sarthak.modelstash.util.StashStats;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Data for the two tabs of MainActivity. Shared by both fragments and kept
 * across rotation, so the search text and sort order aren't lost.
 */
public class MainViewModel extends AndroidViewModel {

    public static final int RECENT_LIMIT = 10;
    /** Passed to setStatusFilter to mean "every stage". */
    public static final int STATUS_ANY = -1;

    private final MutableLiveData<String> query = new MutableLiveData<>("");
    private final MutableLiveData<ModelSort.Mode> sortMode = new MutableLiveData<>(ModelSort.Mode.NAME);
    private final MutableLiveData<Integer> statusFilter = new MutableLiveData<>(STATUS_ANY);

    public final LiveData<List<ModelKit>> recent;
    /** The kits you have started, newest first. */
    public final LiveData<List<ModelKit>> onTheBench;
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
        onTheBench = repository.getRecentWithStatus(BuildStatus.IN_BUILD, RECENT_LIMIT);
        stats = Transformations.map(repository.getAll(),
                models -> StashStats.from(models, System.currentTimeMillis()));
        wishlist = repository.getWishlist();
        wishlistCount = repository.wishlistCount();

        // What the grid shows = search results, narrowed to one build stage, then sorted.
        // Three inputs, one output: whichever changes, the whole chain is re-run.
        LiveData<List<ModelKit>> results = Transformations.switchMap(query, q -> repository.search(q));
        MediatorLiveData<List<ModelKit>> shown = new MediatorLiveData<>();
        Runnable rebuild = () -> shown.setValue(ModelSort.sorted(
                atStage(results.getValue(), statusFilter.getValue()), sortMode.getValue()));
        shown.addSource(results, list -> rebuild.run());
        shown.addSource(sortMode, mode -> rebuild.run());
        shown.addSource(statusFilter, status -> rebuild.run());
        visibleModels = shown;
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

    /** Keeps only the kits at one build stage; STATUS_ANY keeps everything. */
    private static List<ModelKit> atStage(@Nullable List<ModelKit> models, @Nullable Integer status) {
        if (models == null) {
            return Collections.emptyList();
        }
        if (status == null || status == STATUS_ANY) {
            return models;
        }
        List<ModelKit> out = new ArrayList<>();
        for (ModelKit m : models) {
            if (BuildStatus.clamp(m.buildStatus) == status) {
                out.add(m);
            }
        }
        return out;
    }

    public void setStatusFilter(int status) {
        if (!Objects.equals(status, statusFilter.getValue())) {
            statusFilter.setValue(status);
        }
    }

    public int getStatusFilter() {
        Integer status = statusFilter.getValue();
        return status == null ? STATUS_ANY : status;
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
