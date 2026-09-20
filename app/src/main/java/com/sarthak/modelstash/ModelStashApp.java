package com.sarthak.modelstash;

import android.app.Application;

import com.sarthak.modelstash.util.ThemePrefs;

/** Runs once when the app process starts: applies the saved appearance (dark by default). */
public class ModelStashApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        ThemePrefs.apply(ThemePrefs.get(this));
    }
}