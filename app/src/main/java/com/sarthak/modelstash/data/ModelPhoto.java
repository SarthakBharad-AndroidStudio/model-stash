package com.sarthak.modelstash.data;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * One photo of one kit. A kit has up to six (see PhotoDraft.MAX_PHOTOS), kept in
 * the order you arranged them; position 0 is the cover the cards show.
 * Deleting the kit deletes its rows here too (ON DELETE CASCADE).
 */
@Entity(tableName = "model_photos",
        foreignKeys = @ForeignKey(entity = ModelKit.class,
                parentColumns = "id",
                childColumns = "model_id",
                onDelete = ForeignKey.CASCADE),
        indices = @Index("model_id"))
public class ModelPhoto {

    @PrimaryKey(autoGenerate = true)
    public long id;

    @ColumnInfo(name = "model_id")
    public long modelId;

    /** Absolute path of the file inside the app's private storage (see PhotoStorage). */
    @NonNull
    public String path = "";

    /** What the photo shows, as you labelled it: see PhotoLabel. -1 (NONE) = not labelled. */
    public int label = -1;

    /** 0 = cover, then left to right as arranged on the edit screen. */
    public int position;
}
