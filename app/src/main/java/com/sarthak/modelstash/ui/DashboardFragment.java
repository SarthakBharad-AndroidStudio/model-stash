package com.sarthak.modelstash.ui;

import android.animation.ValueAnimator;
import android.os.Bundle;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.widget.NestedScrollView;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.RecyclerView;

import com.sarthak.modelstash.MainActivity;
import com.sarthak.modelstash.R;
import com.sarthak.modelstash.util.BuildStatus;
import com.sarthak.modelstash.util.StashStats;

/** Tab 1: the stash in numbers, and a swipeable row of the latest additions. */
public class DashboardFragment extends Fragment {

    private int shownCount = -1;
    private boolean carouselAnimated;
    private boolean benchAnimated;

    public DashboardFragment() {
        super(R.layout.fragment_dashboard);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        MainViewModel viewModel = new ViewModelProvider(requireActivity()).get(MainViewModel.class);
        MainActivity activity = (MainActivity) requireActivity();

        TextView countText = view.findViewById(R.id.text_count);
        TextView countLabel = view.findViewById(R.id.text_count_label);
        TextView progress = view.findViewById(R.id.text_progress);
        TextView brands = view.findViewById(R.id.stat_brands);
        TextView topScale = view.findViewById(R.id.stat_top_scale);
        TextView recentCount = view.findViewById(R.id.stat_recent);
        View recentSection = view.findViewById(R.id.section_recent);
        View benchSection = view.findViewById(R.id.section_bench);
        RecyclerView bench = view.findViewById(R.id.list_bench);
        View emptyState = view.findViewById(R.id.empty_state);
        RecyclerView carousel = view.findViewById(R.id.list_recent);
        NestedScrollView scroll = view.findViewById(R.id.scroll);

        ModelAdapter adapter = new ModelAdapter(R.layout.item_model_carousel,
                (model, photo) -> ModelDetailActivity.start(requireActivity(), model.id, photo));
        carousel.setAdapter(adapter);
        new StartSnapHelper().attachToRecyclerView(carousel); // cards settle against the left edge

        ModelAdapter benchAdapter = new ModelAdapter(R.layout.item_model_carousel,
                (model, photo) -> ModelDetailActivity.start(requireActivity(), model.id, photo));
        bench.setAdapter(benchAdapter);
        new StartSnapHelper().attachToRecyclerView(bench);

        view.findViewById(R.id.button_see_all).setOnClickListener(v -> {
            viewModel.setStatusFilter(MainViewModel.STATUS_ANY);
            activity.openCatalogue();
        });
        // "See all" here hands the Catalogue its filter on the way over.
        view.findViewById(R.id.button_see_bench).setOnClickListener(v -> {
            viewModel.setStatusFilter(BuildStatus.IN_BUILD);
            activity.openCatalogue();
        });
        view.findViewById(R.id.button_empty_add).setOnClickListener(v -> activity.openAddScreen());
        scroll.setOnScrollChangeListener((NestedScrollView.OnScrollChangeListener)
                (v, x, y, oldX, oldY) -> activity.onContentScrolled(y - oldY));

        viewModel.stats.observe(getViewLifecycleOwner(), stats -> {
            animateCount(countText, stats.total);
            countLabel.setText(getResources().getQuantityString(R.plurals.models_in_stash, stats.total));
            boolean anyProgress = stats.completed > 0 || stats.inBuild > 0;
            progress.setText(getString(R.string.progress_line, stats.completed, stats.inBuild));
            progress.setVisibility(anyProgress ? View.VISIBLE : View.GONE);
            brands.setText(String.valueOf(stats.brands));
            topScale.setText(stats.topScale != null ? stats.topScale : getString(R.string.stat_none));
            recentCount.setText(String.valueOf(stats.addedLast30Days));
        });

        viewModel.onTheBench.observe(getViewLifecycleOwner(), models -> {
            benchAdapter.submitList(models);
            // No half-finished kits means no section at all, rather than an empty heading.
            int visibility = models.isEmpty() ? View.GONE : View.VISIBLE;
            benchSection.setVisibility(visibility);
            bench.setVisibility(visibility);
            if (!benchAnimated && !models.isEmpty()) {
                benchAnimated = true;
                bench.scheduleLayoutAnimation();
            }
        });

        viewModel.recent.observe(getViewLifecycleOwner(), models -> {
            adapter.submitList(models);
            boolean empty = models.isEmpty();
            emptyState.setVisibility(empty ? View.VISIBLE : View.GONE);
            recentSection.setVisibility(empty ? View.GONE : View.VISIBLE);
            carousel.setVisibility(empty ? View.GONE : View.VISIBLE);
            if (!carouselAnimated && !empty) {
                carouselAnimated = true;
                carousel.scheduleLayoutAnimation();
            }
        });
    }

    /** Counts up from the previous number to the new one instead of jumping. */
    private void animateCount(TextView target, int to) {
        int from = Math.max(shownCount, 0);
        shownCount = to;
        if (from == to) {
            target.setText(String.valueOf(to));
            return;
        }
        ValueAnimator animator = ValueAnimator.ofInt(from, to);
        animator.setDuration(Math.min(900, 250 + Math.abs(to - from) * 40L));
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(a -> target.setText(String.valueOf(a.getAnimatedValue())));
        animator.start();
    }
}
