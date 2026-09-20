package com.sarthak.modelstash.data;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

/**
 * Every database query the app uses. Room generates the implementation.
 * Methods returning LiveData run in the background automatically and
 * re-emit whenever the table changes; the others must be called off the
 * main thread (ModelRepository takes care of that).
 */
@Dao
public interface ModelDao {

    @Insert
    long insert(ModelKit model);

    @Update
    void update(ModelKit model);

    @Delete
    void delete(ModelKit model);

    // --- Models you own (wishlist = 0) ---

    @Query("SELECT * FROM models WHERE wishlist = 0 ORDER BY created_at DESC LIMIT :limit")
    LiveData<List<ModelKit>> getRecent(int limit);

    @Query("SELECT COUNT(*) FROM models WHERE wishlist = 0")
    LiveData<Integer> count();

    @Query("SELECT * FROM models WHERE wishlist = 0 ORDER BY name COLLATE NOCASE")
    LiveData<List<ModelKit>> getAll();

    /** Name contains the query (case-insensitive). The query must already be LIKE-escaped. */
    @Query("SELECT * FROM models WHERE wishlist = 0 AND name LIKE '%' || :query || '%' ESCAPE '\\' "
            + "ORDER BY name COLLATE NOCASE")
    LiveData<List<ModelKit>> searchByName(String query);

    // --- Models you want (wishlist = 1) ---

    @Query("SELECT * FROM models WHERE wishlist = 1 ORDER BY created_at DESC")
    LiveData<List<ModelKit>> getWishlist();

    @Query("SELECT COUNT(*) FROM models WHERE wishlist = 1")
    LiveData<Integer> wishlistCount();

    @Query("SELECT * FROM models WHERE id = :id")
    LiveData<ModelKit> observeById(long id);

    @Query("SELECT * FROM models WHERE id = :id")
    ModelKit getById(long id);
}