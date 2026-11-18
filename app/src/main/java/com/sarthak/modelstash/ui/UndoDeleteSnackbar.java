package com.sarthak.modelstash.ui;

import android.view.View;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.snackbar.BaseTransientBottomBar;
import com.google.android.material.snackbar.Snackbar;
import com.sarthak.modelstash.R;
import com.sarthak.modelstash.data.ModelRepository;

/**
 * "Deleted Grumman F-14D Tomcat · UNDO", shown on whichever screen you land on
 * after deleting (home or Brands). Tapping UNDO puts the kit back with its photos;
 * letting it time out, swiping it away, or another message replacing it makes the
 * delete final and removes the photo files.
 *
 * A rotation or leaving the screen doesn't count as "final": the next screen to
 * appear simply shows the snackbar again.
 */
public final class UndoDeleteSnackbar {

    private static final int DURATION_MS = 7000;

    private final AppCompatActivity activity;
    private final View parent;
    @Nullable
    private final View anchor;
    private final ModelRepository repository;
    @Nullable
    private ModelRepository.DeletedKits showing;
    @Nullable
    private Snackbar bar;

    private UndoDeleteSnackbar(AppCompatActivity activity, View parent, @Nullable View anchor) {
        this.activity = activity;
        this.parent = parent;
        this.anchor = anchor;
        this.repository = ModelRepository.get(activity);
    }

    /** Call once in onCreate. anchor = a view the snackbar should sit above (the + button), or null. */
    public static void attach(AppCompatActivity activity, View parent, @Nullable View anchor) {
        UndoDeleteSnackbar helper = new UndoDeleteSnackbar(activity, parent, anchor);
        helper.repository.recentlyDeleted().observe(activity, helper::show);
    }

    private void show(@Nullable ModelRepository.DeletedKits deleted) {
        if (deleted == null) {
            if (bar != null && bar.isShownOrQueued()) {
                bar.dismiss(); // undone or made final elsewhere
            }
            showing = null;
            return;
        }
        if (deleted == showing && bar != null && bar.isShownOrQueued()) {
            return; // already on screen
        }
        int count = deleted.kits.size();
        String text = count == 1
                ? activity.getString(R.string.deleted_one, deleted.kits.get(0).name)
                : activity.getResources().getQuantityString(R.plurals.deleted_many, count, count);
        showing = deleted;
        bar = Snackbar.make(parent, text, DURATION_MS)
                .setAction(R.string.undo, v -> repository.undoDelete(deleted));
        if (anchor != null && anchor.getVisibility() == View.VISIBLE) {
            bar.setAnchorView(anchor);
        }
        bar.addCallback(new BaseTransientBottomBar.BaseCallback<Snackbar>() {
            @Override
            public void onDismissed(Snackbar snackbar, int event) {
                if (event == DISMISS_EVENT_TIMEOUT || event == DISMISS_EVENT_SWIPE
                        || event == DISMISS_EVENT_CONSECUTIVE) {
                    repository.finalizeDelete(deleted);
                }
                // DISMISS_EVENT_ACTION: undone. DISMISS_EVENT_MANUAL: screen went away; shown again later.
            }
        });
        bar.show();
    }
}
