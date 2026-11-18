package com.sarthak.modelstash.ui;

import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.sarthak.modelstash.MainActivity;
import com.sarthak.modelstash.R;
import com.sarthak.modelstash.data.ModelRepository;
import com.sarthak.modelstash.util.BuildStatus;

import java.util.ArrayList;
import java.util.List;

/**
 * Multi-select for the Catalogue and Wishlist tabs. Hold a card to start; the
 * toolbar becomes "3 selected" with actions:
 *   Catalogue: Select all, Set status, Delete
 *   Wishlist:  Select all, Move to stash, Delete
 * Back or ✕ ends it. A rotation keeps the selection.
 *
 * New bulk actions later (tag, shop, price...) are one more menu item in
 * menu_selection.xml plus one more branch in onMenuItem.
 */
final class KitSelection implements MainActivity.SelectionActions, ModelAdapter.SelectionListener {

    private static final String STATE_SELECTED = "selected_ids";

    private final Fragment fragment;
    private final ModelAdapter adapter;
    private final boolean wishlist;
    @Nullable
    private long[] pendingRestore;

    KitSelection(Fragment fragment, ModelAdapter adapter, boolean wishlist, @Nullable Bundle savedState) {
        this.fragment = fragment;
        this.adapter = adapter;
        this.wishlist = wishlist;
        this.pendingRestore = savedState != null ? savedState.getLongArray(STATE_SELECTED) : null;
        adapter.enableSelection(this);
    }

    /** Call when the list has been shown (submitList's callback): restores a selection after rotation. */
    void onListShown() {
        long[] ids = pendingRestore;
        pendingRestore = null;
        if (ids != null && ids.length > 0) {
            List<Long> list = new ArrayList<>();
            for (long id : ids) {
                list.add(id);
            }
            adapter.setSelection(list);
        }
    }

    void save(Bundle outState) {
        if (adapter.isSelecting()) {
            List<Long> ids = adapter.selectedIds();
            long[] array = new long[ids.size()];
            for (int i = 0; i < array.length; i++) {
                array[i] = ids.get(i);
            }
            outState.putLongArray(STATE_SELECTED, array);
        }
    }

    /** The tab's view is going away (tab switch, rotation): put the normal toolbar back. */
    void end() {
        MainActivity host = host();
        if (host != null && host.isSelecting()) {
            host.endSelection();
        }
    }

    @Nullable
    private MainActivity host() {
        return fragment.getActivity() instanceof MainActivity ? (MainActivity) fragment.getActivity() : null;
    }

    // ---- ModelAdapter.SelectionListener ----

    @Override
    public void onSelectionChanged(int count) {
        MainActivity host = host();
        if (host == null) {
            return;
        }
        if (count == 0) {
            if (host.isSelecting()) {
                host.endSelection();
            }
            return;
        }
        host.startSelection(this);
        host.setSelectionTitle(fragment.getResources().getQuantityString(R.plurals.selection_count, count, count));
    }

    // ---- MainActivity.SelectionActions ----

    @Override
    public void onCreateMenu(Menu menu) {
        fragment.requireActivity().getMenuInflater().inflate(R.menu.menu_selection, menu);
        menu.findItem(R.id.action_selection_status).setVisible(!wishlist);
        menu.findItem(R.id.action_selection_to_stash).setVisible(wishlist);
    }

    @Override
    public boolean onMenuItem(MenuItem item) {
        MainActivity host = host();
        if (host == null) {
            return false;
        }
        ModelRepository repository = ModelRepository.get(host);
        int id = item.getItemId();
        if (id == R.id.action_selection_all) {
            adapter.selectAll();
            return true;
        }
        if (id == R.id.action_selection_status) {
            List<Long> ids = adapter.selectedIds();
            new MaterialAlertDialogBuilder(host)
                    .setTitle(R.string.change_status)
                    .setItems(BuildStatus.labels(host), (dialog, which) -> {
                        String stage = host.getString(BuildStatus.label(which));
                        repository.setStatus(ids, which, () -> host.showMessage(host.getResources()
                                .getQuantityString(R.plurals.status_set_many, ids.size(), ids.size(), stage)));
                        host.endSelection();
                    })
                    .setNegativeButton(R.string.cancel, null)
                    .show();
            return true;
        }
        if (id == R.id.action_selection_to_stash) {
            List<Long> ids = adapter.selectedIds();
            repository.moveToStash(ids, () -> host.showMessage(host.getResources()
                    .getQuantityString(R.plurals.moved_many, ids.size(), ids.size())));
            host.endSelection();
            return true;
        }
        if (id == R.id.action_selection_delete) {
            // No dialog: the snackbar's UNDO puts them all back.
            repository.deleteWithUndo(adapter.selectedKits(), null);
            host.endSelection();
            return true;
        }
        return false;
    }

    @Override
    public void onSelectionClosed() {
        adapter.clearSelection();
    }
}
