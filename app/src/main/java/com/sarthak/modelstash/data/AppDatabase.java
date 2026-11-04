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
@Database(entities = {ModelKit.class, ModelPhoto.class}, version = 5, exportSchema = false)
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

    /** Version 3 added the kit number. Existing rows get NULL, which means "not set". */
    static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE models ADD COLUMN kit_number TEXT");
        }
    };

    /**
     * Version 4 added the build status. Everything already in the stash starts
     * at 0 (Unbuilt), which is the right answer for a kit nobody has told us about.
     */
    static final Migration MIGRATION_3_4 = new Migration(3, 4) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE models ADD COLUMN build_status INTEGER NOT NULL DEFAULT 0");
        }
    };

    /**
     * Version 5 moved photos into their own table, up to six per kit. The photo each
     * kit already had becomes its first (cover) photo, unlabelled (-1): labels are
     * yours to choose. models.photo_path stays and keeps mirroring the cover, so
     * every card in the app works as before.
     */
    static final Migration MIGRATION_4_5 = new Migration(4, 5) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("CREATE TABLE IF NOT EXISTS `model_photos` ("
                    + "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, "
                    + "`model_id` INTEGER NOT NULL, "
                    + "`path` TEXT NOT NULL, "
                    + "`label` INTEGER NOT NULL, "
                    + "`position` INTEGER NOT NULL, "
                    + "FOREIGN KEY(`model_id`) REFERENCES `models`(`id`) "
                    + "ON UPDATE NO ACTION ON DELETE CASCADE)");
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_model_photos_model_id` "
                    + "ON `model_photos` (`model_id`)");
            db.execSQL("INSERT INTO model_photos (model_id, path, label, position) "
                    + "SELECT id, photo_path, -1, 0 FROM models WHERE photo_path IS NOT NULL");
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
                            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                            .build();
                }
            }
        }
        return instance;
    }
}
