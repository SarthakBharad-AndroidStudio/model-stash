package com.sarthak.modelstash.util;

import com.sarthak.modelstash.data.ModelKit;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * "You already have this kit": finds kits (owned or wished for) that look like the
 * same box as the one being saved.
 *
 * Two kits count as the same when:
 * - brand and kit number match (the manufacturer's catalogue number identifies a box), or
 * - the name matches and the scales don't contradict each other
 *   (a 1/48 and a 1/72 Tomcat are different kits; a missing scale doesn't rule it out).
 * Case, spaces, dots and hyphens are ignored: "F-14D Tomcat" = "f14d tomcat",
 * "03865" = "03 865".
 *
 * Plain Java (no Android classes), so it can be unit-tested on your PC.
 */
public final class DuplicateFinder {

    private DuplicateFinder() {
    }

    /** @return kits in `all` that look like `candidate`, never `candidate` itself (same id). */
    public static List<ModelKit> find(ModelKit candidate, List<ModelKit> all) {
        List<ModelKit> out = new ArrayList<>();
        String name = key(candidate.name);
        String brand = key(candidate.brand);
        String number = key(candidate.kitNumber);
        for (ModelKit other : all) {
            if (candidate.id != 0 && other.id == candidate.id) {
                continue;
            }
            boolean sameNumber = !number.isEmpty() && !brand.isEmpty()
                    && number.equals(key(other.kitNumber)) && brand.equals(key(other.brand));
            boolean sameName = !name.isEmpty() && name.equals(key(other.name))
                    && compatible(candidate.scale, other.scale)
                    && compatibleBrand(brand, key(other.brand));
            if (sameNumber || sameName) {
                out.add(other);
            }
        }
        return out;
    }

    /** Same scale, or one of them has none. */
    private static boolean compatible(String a, String b) {
        String ka = key(a);
        String kb = key(b);
        return ka.isEmpty() || kb.isEmpty() || ka.equals(kb);
    }

    /** Same brand, or one of them has none (a Tamiya and a Revell kit with the same name are different). */
    private static boolean compatibleBrand(String a, String b) {
        return a.isEmpty() || b.isEmpty() || a.equals(b);
    }

    /** Lower-case letters and digits only. */
    static String key(String s) {
        if (s == null) {
            return "";
        }
        return s.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]", "");
    }
}
