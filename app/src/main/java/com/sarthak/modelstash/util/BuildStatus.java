package com.sarthak.modelstash.util;

import android.content.Context;

import androidx.annotation.StringRes;

import com.sarthak.modelstash.R;

/**
 * How far along a build is. Stored as a small int so it sorts and counts
 * cheaply in SQL; the words live in strings.xml, so renaming the stages is a
 * one-file change that leaves every saved kit alone.
 *
 * Only kits you own have a meaningful status — a wishlist kit is always UNBUILT.
 */
public final class BuildStatus {

    /** Still sealed, or at least untouched. The state every kit starts in. */
    public static final int UNBUILT = 0;
    /** Started: sprues cut, paint on, somewhere between box and shelf. */
    public static final int IN_BUILD = 1;
    /** Finished. */
    public static final int COMPLETED = 2;

    /** In display order, which is also progress order. */
    public static final int[] ALL = {UNBUILT, IN_BUILD, COMPLETED};

    private BuildStatus() {
    }

    @StringRes
    public static int label(int status) {
        switch (clamp(status)) {
            case COMPLETED:
                return R.string.status_completed;
            case IN_BUILD:
                return R.string.status_in_build;
            default:
                return R.string.status_unbuilt;
        }
    }

    /** The three labels in order, for a picker dialog. */
    public static String[] labels(Context context) {
        String[] out = new String[ALL.length];
        for (int i = 0; i < ALL.length; i++) {
            out[i] = context.getString(label(ALL[i]));
        }
        return out;
    }

    /** Guards against a value a future version of the app might have written. */
    public static int clamp(int status) {
        return status < UNBUILT || status > COMPLETED ? UNBUILT : status;
    }
}
