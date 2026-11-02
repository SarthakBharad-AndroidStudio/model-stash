package com.sarthak.modelstash.util;

import com.sarthak.modelstash.data.ModelKit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Groups the kits you own by brand, for the Brands screen.
 *
 * Brands are matched case-insensitively and ignoring surrounding spaces, so
 * "Tamiya", "tamiya " and "TAMIYA" are one group; the spelling shown is the
 * first one seen. Kits with no brand end up in one unnamed group at the bottom.
 *
 * Plain Java (no Android classes), so it can be unit-tested on your PC.
 */
public final class BrandGroups {

    /** One brand and the kits under it, already sorted by name. */
    public static final class Group {
        /** The brand as it should be displayed, or null for the unbranded group. */
        public final String brand;
        public final List<ModelKit> models;

        Group(String brand, List<ModelKit> models) {
            this.brand = brand;
            this.models = models;
        }

        public int size() {
            return models.size();
        }
    }

    private BrandGroups() {
    }

    /** @return the groups, brands A-Z first and the unbranded kits last. */
    public static List<Group> from(List<ModelKit> models) {
        Map<String, String> displayNames = new LinkedHashMap<>();
        Map<String, List<ModelKit>> byBrand = new LinkedHashMap<>();
        List<ModelKit> unbranded = new ArrayList<>();

        for (ModelKit m : models) {
            if (m.brand == null || m.brand.trim().isEmpty()) {
                unbranded.add(m);
                continue;
            }
            String display = m.brand.trim();
            String key = display.toLowerCase(Locale.ROOT);
            if (!byBrand.containsKey(key)) {
                byBrand.put(key, new ArrayList<>());
                displayNames.put(key, display); // first spelling wins
            }
            byBrand.get(key).add(m);
        }

        List<String> keys = new ArrayList<>(byBrand.keySet());
        Collections.sort(keys);

        List<Group> groups = new ArrayList<>();
        for (String key : keys) {
            List<ModelKit> kits = byBrand.get(key);
            Collections.sort(kits, (a, b) -> a.name.compareToIgnoreCase(b.name));
            groups.add(new Group(displayNames.get(key), kits));
        }
        if (!unbranded.isEmpty()) {
            Collections.sort(unbranded, (a, b) -> a.name.compareToIgnoreCase(b.name));
            groups.add(new Group(null, unbranded));
        }
        return groups;
    }
}
