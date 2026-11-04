package com.sarthak.modelstash.util;

import android.animation.ValueAnimator;
import android.provider.Settings;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.PathInterpolator;

/**
 * Small, shared motion helpers so every screen moves the same way: the same
 * curve, the same distances, the same timing.
 *
 * Everything here is skipped when the phone's animations are switched off
 * (Developer options, or Accessibility → Remove animations).
 */
public final class Motion {

    /** Material 3 "emphasized decelerate": quick start, long gentle landing. */
    public static final PathInterpolator EMPHASIZED_DECELERATE =
            new PathInterpolator(0.05f, 0.7f, 0.1f, 1f);

    private static final long ENTER_DURATION = 420;
    private static final long STAGGER = 45;
    private static final float RISE_DP = 28;

    private Motion() {
    }

    /** False when the user (or a test) has turned animations off. */
    public static boolean enabled(View view) {
        float scale = Settings.Global.getFloat(view.getContext().getContentResolver(),
                Settings.Global.ANIMATOR_DURATION_SCALE, 1f);
        return scale > 0f && ValueAnimator.areAnimatorsEnabled();
    }

    /**
     * Hides a group's visible children so staggerIn can bring them in. Call it in
     * onCreate, before the first frame, so nothing flashes at full opacity first.
     */
    public static void prepareStagger(ViewGroup group) {
        if (!enabled(group)) {
            return;
        }
        float rise = dp(group, RISE_DP);
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            child.setAlpha(0f);
            child.setTranslationY(rise);
        }
    }

    /** Each child fades and rises into place, one shortly after the other. */
    public static void staggerIn(ViewGroup group, long startDelay) {
        int visibleIndex = 0;
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (!enabled(group)) {
                child.setAlpha(1f);
                child.setTranslationY(0f);
                continue;
            }
            long delay = startDelay;
            if (child.getVisibility() == View.VISIBLE) {
                delay += visibleIndex * STAGGER;
                visibleIndex++;
            }
            child.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setStartDelay(delay)
                    .setDuration(ENTER_DURATION)
                    .setInterpolator(EMPHASIZED_DECELERATE)
                    .withLayer()
                    .start();
        }
    }

    /** Shrinks a view out of sight so popIn can bring it in. Call before the first frame. */
    public static void preparePop(View view) {
        if (!enabled(view)) {
            return;
        }
        view.setScaleX(0.4f);
        view.setScaleY(0.4f);
        view.setAlpha(0f);
    }

    /** A view prepared with preparePop grows into place: used for floating buttons. */
    public static void popIn(View view, long startDelay) {
        if (!enabled(view)) {
            view.setScaleX(1f);
            view.setScaleY(1f);
            view.setAlpha(1f);
            return;
        }
        view.animate()
                .scaleX(1f)
                .scaleY(1f)
                .alpha(1f)
                .setStartDelay(startDelay)
                .setDuration(360)
                .setInterpolator(EMPHASIZED_DECELERATE)
                .start();
    }

    private static float dp(View view, float value) {
        return value * view.getResources().getDisplayMetrics().density;
    }
}
