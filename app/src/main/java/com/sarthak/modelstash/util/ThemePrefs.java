package com.sarthak.modelstash.util;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.appcompat.app.AppCompatDelegate;

/** Remembers Midnight (dark, the default), Paper (light) or Follow system. */
public final class ThemePrefs {

    public static final int DARK = 0;
    public static final int LIGHT = 1;
    public static final int SYSTEM = 2;

    private static final String FILE = "settings";
    private static final String KEY = "appearance";

    private ThemePrefs() {
    }

    public static int get(Context context) {
        return prefs(context).getInt(KEY, DARK);
    }

    /** Saves the choice and switches the whole app to it (open screens redraw themselves). */
    public static void set(Context context, int choice) {
        prefs(context).edit().putInt(KEY, choice).apply();
        apply(choice);
    }

    public static void apply(int choice) {
        int mode;
        if (choice == LIGHT) {
            mode = AppCompatDelegate.MODE_NIGHT_NO;
        } else if (choice == SYSTEM) {
            mode = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
        } else {
            mode = AppCompatDelegate.MODE_NIGHT_YES;
        }
        AppCompatDelegate.setDefaultNightMode(mode);
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }
}