package com.sarthak.modelstash;

import android.app.Activity;
import android.app.Application;
import android.os.Build;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.sarthak.modelstash.util.ThemePrefs;

/**
 * Runs once when the app process starts: applies the saved appearance (dark by
 * default) and gives every screen the same full-screen wipe.
 */
public class ModelStashApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        ThemePrefs.apply(ThemePrefs.get(this));
        registerActivityLifecycleCallbacks(new ScreenWipe());
    }

    /**
     * The whole new screen slides in from the right over the current one, which
     * drifts left and darkens a little; back runs it in reverse.
     *
     * The theme's windowAnimationStyle asks for this too, but on Android 14+ (and on
     * phones whose maker replaces the system animations) that request is often
     * ignored, which is when you see the old screen's content showing through.
     * overrideActivityTransition is the Android 14+ way that phones do respect,
     * and doing it here covers every activity without touching any of them.
     */
    private static final class ScreenWipe implements ActivityLifecycleCallbacks {

        @Override
        public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle savedInstanceState) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                return; // older Android uses the theme's windowAnimationStyle
            }
            if (activity.isTaskRoot()) {
                return; // the home screen: opening or leaving the app keeps the system's launch animation
            }
            activity.overrideActivityTransition(Activity.OVERRIDE_TRANSITION_OPEN,
                    R.anim.screen_open_enter, R.anim.screen_open_exit);
            activity.overrideActivityTransition(Activity.OVERRIDE_TRANSITION_CLOSE,
                    R.anim.screen_close_enter, R.anim.screen_close_exit);
        }

        @Override
        public void onActivityStarted(@NonNull Activity activity) {
        }

        @Override
        public void onActivityResumed(@NonNull Activity activity) {
        }

        @Override
        public void onActivityPaused(@NonNull Activity activity) {
        }

        @Override
        public void onActivityStopped(@NonNull Activity activity) {
        }

        @Override
        public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle outState) {
        }

        @Override
        public void onActivityDestroyed(@NonNull Activity activity) {
        }
    }
}
