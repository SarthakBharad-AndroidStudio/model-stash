package com.sarthak.modelstash.data;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** The app's single SQLite database file, "model_stash.db". */
@Database(entities = {ModelKit.class}, version = 2, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {

    /** Background threads for database writes and file work (never block the UI thread). */
    public static final ExecutorService IO = Executors.newFixedThreadPool(2);

    /**
     * Version 2 added the wishlist flag. This adds the column to an existing
     * database instead of wiping it, so models already on the phone survive.
     */
    static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE models ADD COLUMN wishlist INTEGER NOT NULL DEFAULT 0");
        }
    };

    private static volatile AppDatabase instance;

    public abstract ModelDao modelDao();

    public static AppDatabase get(Context context) {
        if (instance == null) {
            synchronized (AppDatabase.class) {
                if (instance == null) {
                    instance = Room.databaseBuilder(context.getApplicationContext(),
                                    AppDatabase.class, "model_stash.db")
                            .addMigrations(MIGRATION_1_2)
                            .build();
                }
            }
        }
        return instance;
    }
}