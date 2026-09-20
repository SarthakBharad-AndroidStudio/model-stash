package com.sarthak.modelstash;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.sarthak.modelstash.data.ModelKit;
import com.sarthak.modelstash.util.ModelSort;
import com.sarthak.modelstash.util.ScaleFormat;
import com.sarthak.modelstash.util.StashStats;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class StashStatsAndSortTest {

    private static final long DAY = 24L * 60 * 60 * 1000;
    private static final long NOW = 1_800_000_000_000L;

    private static ModelKit kit(String name, String scale, String brand, long ageDays) {
        ModelKit m = new ModelKit();
        m.name = name;
        m.scale = scale;
        m.brand = brand;
        m.createdAt = NOW - ageDays * DAY;
        return m;
    }

    private static List<ModelKit> sampleStash() {
        return Arrays.asList(
                kit("Spitfire Mk.I", "1:48", "Tamiya", 2),
                kit("bismarck", "1:350", "Revell", 40),
                kit("Tiger I", "1:35", "tamiya", 10),
                kit("Zero", "1:48", "Hasegawa", 90),
                kit("Garage kit", null, null, 1));
    }

    @Test
    public void statsCountBrandsIgnoringCaseTopScaleAndRecentAdditions() {
        StashStats stats = StashStats.from(sampleStash(), NOW);
        assertEquals(5, stats.total);
        assertEquals(3, stats.brands);          // Tamiya/tamiya count once
        assertEquals("1:48", stats.topScale);  // two kits
        assertEquals(3, stats.addedLast30Days);
    }

    @Test
    public void statsOfAnEmptyStash() {
        StashStats stats = StashStats.from(new ArrayList<>(), NOW);
        assertEquals(0, stats.total);
        assertEquals(0, stats.brands);
        assertNull(stats.topScale);
        assertEquals(0, stats.addedLast30Days);
    }

    @Test
    public void tiedScalesPreferTheBiggerModel() {
        StashStats stats = StashStats.from(Arrays.asList(
                kit("A", "1:72", null, 1), kit("B", "1:24", null, 1)), NOW);
        assertEquals("1:24", stats.topScale);
    }

    @Test
    public void sortsByNameNewestAndScale() {
        List<ModelKit> stash = sampleStash();
        assertEquals(Arrays.asList("bismarck", "Garage kit", "Spitfire Mk.I", "Tiger I", "Zero"),
                names(ModelSort.sorted(stash, ModelSort.Mode.NAME)));
        assertEquals(Arrays.asList("Garage kit", "Spitfire Mk.I", "Tiger I", "bismarck", "Zero"),
                names(ModelSort.sorted(stash, ModelSort.Mode.NEWEST)));
        assertEquals(Arrays.asList("Tiger I", "Spitfire Mk.I", "Zero", "bismarck", "Garage kit"),
                names(ModelSort.sorted(stash, ModelSort.Mode.SCALE)));
        assertEquals("Spitfire Mk.I", stash.get(0).name); // original list untouched
    }

    @Test
    public void scaleDenominatorForSorting() {
        assertEquals(72.0, ScaleFormat.denominator("1:72"), 0.0);
        assertEquals(12.5, ScaleFormat.denominator("1:12.5"), 0.0);
        assertEquals(Double.MAX_VALUE, ScaleFormat.denominator(null), 0.0);
        assertEquals(Double.MAX_VALUE, ScaleFormat.denominator("big"), 0.0);
    }

    private static List<String> names(List<ModelKit> models) {
        List<String> out = new ArrayList<>();
        for (ModelKit m : models) {
            out.add(m.name);
        }
        return out;
    }
}