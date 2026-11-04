package com.sarthak.modelstash.util;

import androidx.annotation.Nullable;

import com.sarthak.modelstash.data.ModelPhoto;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The photos on the add/edit screen while you work on them: up to six, in order,
 * each with a label. Nothing here touches the database or the files; saving turns
 * the draft into ModelPhoto rows (toPhotos) and the repository writes them.
 * Plain Java, so it is covered by unit tests.
 */
public final class PhotoDraft {

    public static final int MAX_PHOTOS = 6;

    public static final class Item {
        public final String path;
        public int label;

        Item(String path, int label) {
            this.path = path;
            this.label = label;
        }
    }

    private final List<Item> items = new ArrayList<>();

    public int size() {
        return items.size();
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }

    public boolean isFull() {
        return items.size() >= MAX_PHOTOS;
    }

    /** How many more photos fit. */
    public int remaining() {
        return MAX_PHOTOS - items.size();
    }

    public Item get(int index) {
        return items.get(index);
    }

    /** Adds an unlabelled photo at the end. Returns false (and adds nothing) when full or already there. */
    public boolean add(String path) {
        return add(path, PhotoLabel.NONE);
    }

    /** Adds a photo with a label (used when loading saved photos). */
    public boolean add(String path, int label) {
        if (path == null || isFull() || contains(path)) {
            return false;
        }
        items.add(new Item(path, PhotoLabel.clamp(label)));
        return true;
    }

    public boolean contains(String path) {
        for (Item item : items) {
            if (item.path.equals(path)) {
                return true;
            }
        }
        return false;
    }

    /** Removes the photo at index and returns its path. */
    public String remove(int index) {
        return items.remove(index).path;
    }

    public void setLabel(int index, int label) {
        items.get(index).label = PhotoLabel.clamp(label);
    }

    /** Moves one photo, shifting the ones in between (drag and drop). */
    public void move(int from, int to) {
        if (from == to) {
            return;
        }
        if (from < to) {
            for (int i = from; i < to; i++) {
                Collections.swap(items, i, i + 1);
            }
        } else {
            for (int i = from; i > to; i--) {
                Collections.swap(items, i, i - 1);
            }
        }
    }

    /** Puts the photo first, which makes it the cover. */
    public void makeCover(int index) {
        move(index, 0);
    }

    /** The cover is simply the first photo; null when there are none. */
    @Nullable
    public String coverPath() {
        return items.isEmpty() ? null : items.get(0).path;
    }

    // ---- From and to the database ----

    /** Replaces the draft with saved photos (already sorted by position). */
    public void setAll(List<ModelPhoto> photos) {
        items.clear();
        for (ModelPhoto photo : photos) {
            add(photo.path, photo.label);
        }
    }

    /** Fresh rows in the current order: position 0 is the cover. modelId is set when saving. */
    public List<ModelPhoto> toPhotos() {
        List<ModelPhoto> out = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            ModelPhoto photo = new ModelPhoto();
            photo.path = items.get(i).path;
            photo.label = items.get(i).label;
            photo.position = i;
            out.add(photo);
        }
        return out;
    }

    // ---- Rotation: the draft goes into the saved-state Bundle as two plain arrays ----

    public ArrayList<String> paths() {
        ArrayList<String> out = new ArrayList<>();
        for (Item item : items) {
            out.add(item.path);
        }
        return out;
    }

    public int[] labels() {
        int[] out = new int[items.size()];
        for (int i = 0; i < items.size(); i++) {
            out[i] = items.get(i).label;
        }
        return out;
    }

    public void restore(@Nullable List<String> paths, @Nullable int[] labels) {
        items.clear();
        if (paths == null) {
            return;
        }
        for (int i = 0; i < paths.size(); i++) {
            int label = labels != null && i < labels.length ? labels[i] : PhotoLabel.NONE;
            add(paths.get(i), label);
        }
    }
}
