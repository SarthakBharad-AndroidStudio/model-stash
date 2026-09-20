package com.sarthak.modelstash.util;

import android.view.View;
import android.view.ViewGroup;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

/**
 * Android 15+ draws every app edge-to-edge (behind the status bar and the
 * navigation bar). This pads a screen's root view so nothing hides behind
 * them, or behind the keyboard.
 */
public final class InsetsHelper {

    private InsetsHelper() {
    }

    /**
     * @param padTop    pad for the status bar
     * @param padBottom pad for the navigation bar and keyboard (false when a
     *                  BottomNavigationView at the bottom already handles it)
     */
    public static void applySystemBars(View root, boolean padTop, boolean padBottom) {
        final int left = root.getPaddingLeft();
        final int top = root.getPaddingTop();
        final int right = root.getPaddingRight();
        final int bottom = root.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, windowInsets) -> {
            Insets bars = windowInsets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            Insets keyboard = windowInsets.getInsets(WindowInsetsCompat.Type.ime());
            view.setPadding(
                    left + bars.left,
                    top + (padTop ? bars.top : 0),
                    right + bars.right,
                    bottom + (padBottom ? Math.max(bars.bottom, keyboard.bottom) : 0));
            return windowInsets; // pass on, so e.g. BottomNavigationView can pad itself
        });
    }

    /** Lifts a floating button above the navigation bar by adding the bar's height to its bottom margin. */
    public static void liftAboveNavigationBar(View view) {
        ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) view.getLayoutParams();
        final int baseMargin = lp.bottomMargin;
        ViewCompat.setOnApplyWindowInsetsListener(view, (v, windowInsets) -> {
            Insets bars = windowInsets.getInsets(WindowInsetsCompat.Type.navigationBars());
            ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) v.getLayoutParams();
            params.bottomMargin = baseMargin + bars.bottom;
            v.setLayoutParams(params);
            return windowInsets;
        });
    }

    /** Adds the navigation bar's height to a scrolling view's bottom padding. */
    public static void padForNavigationBar(View view) {
        final int basePadding = view.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(view, (v, windowInsets) -> {
            Insets bars = windowInsets.getInsets(WindowInsetsCompat.Type.navigationBars());
            v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(), basePadding + bars.bottom);
            return windowInsets;
        });
    }
}