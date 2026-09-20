package com.sarthak.modelstash.util;

import android.os.Build;
import android.view.HapticFeedbackConstants;
import android.view.View;

/** Small vibrations that make taps feel physical. They respect the phone's touch-feedback setting. */
public final class Haptics {

    private Haptics() {
    }

    /** A satisfying "done" buzz: saving, deleting. */
    public static void confirm(View view) {
        view.performHapticFeedback(Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
                ? HapticFeedbackConstants.CONFIRM
                : HapticFeedbackConstants.VIRTUAL_KEY);
    }

    /** A light tick: choosing a chip, switching a tab. */
    public static void tick(View view) {
        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
    }
}