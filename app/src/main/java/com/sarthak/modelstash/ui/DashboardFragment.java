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
import androidx.recyclerview.widget.LinearSnapHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.sarthak.modelstash.MainActivity;
import com.sarthak.modelstash.R;
import com.sarthak.modelstash.util.StashStats;

/** Tab 1: the stash in numbers, and a swipeable row of the latest additions. */
public class DashboardFragment extends Fragment {

    private int shownCount = -1;
    private boolean carouselAnimated;

    public DashboardFragment() {
        super(R.layout.fragment_dashboard);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        MainViewModel viewModel = new ViewModelProvider(requireActivity()).get(MainViewModel.class);
        MainActivity activity = (MainActivity) requireActivity();

        TextView countText = view.findViewById(R.id.text_count);
        TextView countLabel = view.findViewById(R.id.text_count_label);
        TextView brands = view.findViewById(R.id.stat_brands);
        TextView topScale = view.findViewById(R.id.stat_top_scale);
        TextView recentCount = view.findViewById(R.id.stat_recent);
        View recentSection = view.findViewById(R.id.section_recent);
        View emptyState = view.findViewById(R.id.empty_state);
        RecyclerView carousel = view.findViewById(R.id.list_recent);
        NestedScrollView scroll = view.findViewById(R.id.scroll);

        ModelAdapter adapter = new ModelAdapter(R.layout.item_model_carousel,
                (model, photo) -> ModelDetailActivity.start(requireActivity(), model.id, photo));
        carousel.setAdapter(adapter);
        new LinearSnapHelper().attachToRecyclerView(carousel); // cards settle neatly after a swipe

        view.findViewById(R.id.button_see_all).setOnClickListener(v -> activity.openCatalogue());
        view.findViewById(R.id.button_empty_add).setOnClickListener(v -> activity.openAddScreen());
        scroll.setOnScrollChangeListener((NestedScrollView.OnScrollChangeListener)
                (v, x, y, oldX, oldY) -> activity.onContentScrolled(y - oldY));

        viewModel.stats.observe(getViewLifecycleOwner(), stats -> {
            animateCount(countText, stats.total);
            countLabel.setText(getResources().getQuantityString(R.plurals.models_in_stash, stats.total));
            brands.setText(String.valueOf(stats.brands));
            topScale.setText(stats.topScale != null ? stats.topScale : getString(R.string.stat_none));
            recentCount.setText(String.valueOf(stats.addedLast30Days));
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