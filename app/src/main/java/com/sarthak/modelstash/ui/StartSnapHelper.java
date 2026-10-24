package com.sarthak.modelstash.ui;

import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.LinearSnapHelper;
import androidx.recyclerview.widget.OrientationHelper;
import androidx.recyclerview.widget.RecyclerView;

/**
 * Snaps a horizontal carousel so the nearest card lines up with the list's left
 * edge, and stops snapping altogether once the last card is fully on screen.
 *
 * The stock LinearSnapHelper centres the nearest card instead. On a short
 * carousel that leaves only one position it can ever settle in, so every swipe
 * is animated straight back to where it started and the list feels stuck.
 */
public class StartSnapHelper extends LinearSnapHelper {

    @Nullable
    private OrientationHelper horizontal;

    @Override
    public int[] calculateDistanceToFinalSnap(@NonNull RecyclerView.LayoutManager manager,
                                              @NonNull View targetView) {
        OrientationHelper helper = helperFor(manager);
        int[] distance = new int[2];
        distance[0] = helper.getDecoratedStart(targetView) - helper.getStartAfterPadding();
        distance[1] = 0;
        return distance;
    }

    @Nullable
    @Override
    public View findSnapView(RecyclerView.LayoutManager manager) {
        if (!(manager instanceof LinearLayoutManager)) {
            return super.findSnapView(manager);
        }
        LinearLayoutManager layout = (LinearLayoutManager) manager;

        // Nothing left to snap to at the end of the list: returning null lets the
        // fling settle where it lands instead of bouncing back to the same card.
        if (layout.findLastCompletelyVisibleItemPosition() == manager.getItemCount() - 1) {
            return null;
        }

        int first = layout.findFirstVisibleItemPosition();
        if (first == RecyclerView.NO_POSITION) {
            return null;
        }
        View card = layout.findViewByPosition(first);
        if (card == null) {
            return null;
        }

        // More than half of the first card already scrolled past? Settle on the next one.
        OrientationHelper helper = helperFor(manager);
        if (helper.getDecoratedEnd(card) < helper.getDecoratedMeasurement(card) / 2) {
            View next = layout.findViewByPosition(first + 1);
            if (next != null) {
                return next;
            }
        }
        return card;
    }

    private OrientationHelper helperFor(RecyclerView.LayoutManager manager) {
        if (horizontal == null || horizontal.getLayoutManager() != manager) {
            horizontal = OrientationHelper.createHorizontalHelper(manager);
        }
        return horizontal;
    }
}
