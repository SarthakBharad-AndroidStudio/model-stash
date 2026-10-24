package com.sarthak.modelstash.data;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/** One model kit you own. Room turns this class into the "models" table. */
@Entity(tableName = "models", indices = {@Index("name"), @Index("created_at")})
public class ModelKit {

    @PrimaryKey(autoGenerate = true)
    public long id;

    @NonNull
    public String name = "";

    /** Size compared to the real thing, always stored as "1:N" (see ScaleFormat). */
    @Nullable
    public String scale;

    @Nullable
    public String brand;

    /** The manufacturer's catalogue number, e.g. "32571". Optional. */
    @Nullable
    @ColumnInfo(name = "kit_number")
    public String kitNumber;

    @Nullable
    public String description;

    /** Absolute path of the photo inside the app's private storage (see PhotoStorage). */
    @Nullable
    @ColumnInfo(name = "photo_path")
    public String photoPath;

    /** Scalemates kit page the details came from, if any. */
    @Nullable
    @ColumnInfo(name = "source_url")
    public String sourceUrl;

    /** true = a kit you want to buy (Wishlist tab); false = a kit you own (Catalogue). */
    public boolean wishlist;

    /** How far along the build is: see BuildStatus. Wishlist kits are always UNBUILT. */
    @ColumnInfo(name = "build_status")
    public int buildStatus;

    /** When it was added, in milliseconds since 1970 (System.currentTimeMillis()). */
    @ColumnInfo(name = "created_at")
    public long createdAt;
}
