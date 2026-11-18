package com.sarthak.modelstash.util;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Everything you can change on the add/edit screen, frozen at one moment. The
 * screen takes one when it opens (empty form, or the kit as loaded) and compares it
 * with a fresh one when you press back: different = there are unsaved changes, so
 * it asks before throwing them away.
 *
 * Text is compared trimmed, so a stray space doesn't count as a change.
 * Serializable so the starting point survives rotation.
 *
 * Plain Java (no Android classes), so it can be unit-tested on your PC.
 */
public final class FormSnapshot implements Serializable {

    private static final long serialVersionUID = 1L;

    private final List<String> fields;
    private final List<String> photoPaths;
    private final int[] photoLabels;
    private final int status;

    /**
     * @param fields any number of text fields, always passed in the same order
     *               (name, scale, brand, number, description, link...)
     */
    public FormSnapshot(List<String> fields, List<String> photoPaths, int[] photoLabels, int status) {
        this.fields = new ArrayList<>();
        for (String f : fields) {
            this.fields.add(f == null ? "" : f.trim());
        }
        this.photoPaths = new ArrayList<>(photoPaths);
        this.photoLabels = photoLabels == null ? new int[0] : photoLabels.clone();
        this.status = status;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof FormSnapshot)) {
            return false;
        }
        FormSnapshot other = (FormSnapshot) o;
        return status == other.status
                && fields.equals(other.fields)
                && photoPaths.equals(other.photoPaths)
                && Arrays.equals(photoLabels, other.photoLabels);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fields, photoPaths, Arrays.hashCode(photoLabels), status);
    }
}
