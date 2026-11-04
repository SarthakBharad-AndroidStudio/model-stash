package com.sarthak.modelstash.util;

import android.content.Context;

import androidx.annotation.StringRes;

import com.sarthak.modelstash.R;

/**
 * What a photo shows, as chosen by you. Stored as a small int, like BuildStatus,
 * so the words can change in strings.xml without touching a single saved photo.
 * A new photo starts as NONE: the app never picks a label on your behalf.
 */
public final class PhotoLabel {

    /** Not labelled (yet). Every new photo starts here. */
    public static final int NONE = -1;
    /** The packaging: the box lid, its artwork, the sides. */
    public static final int BOX_ART = 0;
    /** The model once it's built. */
    public static final int FINISHED_BUILD = 1;
    /** Anything else: sprues, decals, instructions, work in progress. */
    public static final int OTHER = 2;

    private PhotoLabel() {
    }

    public static boolean isSet(int label) {
        return clamp(label) != NONE;
    }

    @StringRes
    public static int label(int label) {
        switch (clamp(label)) {
            case BOX_ART:
                return R.string.photo_label_box_art;
            case FINISHED_BUILD:
                return R.string.photo_label_finished;
            case OTHER:
                return R.string.photo_label_other;
            default:
                return R.string.photo_label_none;
        }
    }

    public static String text(Context context, int label) {
        return context.getString(label(label));
    }

    /** Anything that isn't one of the three labels (including a future value) counts as unlabelled. */
    public static int clamp(int label) {
        return label < BOX_ART || label > OTHER ? NONE : label;
    }
}
