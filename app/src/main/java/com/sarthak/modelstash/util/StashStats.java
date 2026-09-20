package com.sarthak.modelstash.util;

import com.sarthak.modelstash.data.ModelKit;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** The numbers on the dashboard, worked out from the full list. Plain Java, so it's unit-tested. */
public final class StashStats {

    private static final long THIRTY_DAYS_MS = 30L * 24 * 60 * 60 * 1000;

    public final int total;
    /** Distinct brands, ignoring upper/lower case. */
    public final int brands;
    /** The most common scale, e.g. "1:72", or null if no model has one. */
    public final String topScale;
    public final int addedLast30Days;

    private StashStats(int total, int brands, String topScale, int addedLast30Days) {
        this.total = total;
        this.brands = brands;
        this.topScale = topScale;
        this.addedLast30Days = addedLast30Days;
    }

    public static StashStats from(List<ModelKit> models, long nowMillis) {
        Set<String> brandNames = new HashSet<>();
        Map<String, Integer> scaleCounts = new HashMap<>();
        int recent = 0;
        for (ModelKit m : models) {
            if (m.brand != null && !m.brand.trim().isEmpty()) {
                brandNames.add(m.brand.trim().toLowerCase(Locale.ROOT));
            }
            if (m.scale != null) {
                Integer n = scaleCounts.get(m.scale);
                scaleCounts.put(m.scale, n == null ? 1 : n + 1);
            }
            if (nowMillis - m.createdAt <= THIRTY_DAYS_MS) {
                recent++;
            }
        }

        String top = null;
        int topCount = 0;
        for (Map.Entry<String, Integer> e : scaleCounts.entrySet()) {
            int count = e.getValue();
            boolean better = count > topCount
                    || (count == topCount && top != null
                    && ScaleFormat.denominator(e.getKey()) < ScaleFormat.denominator(top));
            if (better) {
                top = e.getKey();
                topCount = count;
            }
        }
        return new StashStats(models.size(), brandNames.size(), top, recent);
    }
}