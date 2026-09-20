package com.sarthak.modelstash.util;

import com.sarthak.modelstash.data.ModelKit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** The Catalogue's three sort orders. Plain Java, so it's unit-tested. */
public final class ModelSort {

    public enum Mode { NAME, NEWEST, SCALE }

    private static final Comparator<ModelKit> BY_NAME =
            (a, b) -> a.name.compareToIgnoreCase(b.name);

    private ModelSort() {
    }

    /** Returns a sorted copy; the input list is left untouched. */
    public static List<ModelKit> sorted(List<ModelKit> models, Mode mode) {
        List<ModelKit> copy = new ArrayList<>(models);
        Comparator<ModelKit> order;
        switch (mode == null ? Mode.NAME : mode) {
            case NEWEST:
                order = (a, b) -> Long.compare(b.createdAt, a.createdAt);
                break;
            case SCALE:
                // Largest models first (1:24 before 1:72), then by name; no scale goes last.
                order = Comparator.<ModelKit>comparingDouble(m -> ScaleFormat.denominator(m.scale))
                        .thenComparing(BY_NAME);
                break;
            case NAME:
            default:
                order = BY_NAME;
                break;
        }
        Collections.sort(copy, order);
        return copy;
    }
}