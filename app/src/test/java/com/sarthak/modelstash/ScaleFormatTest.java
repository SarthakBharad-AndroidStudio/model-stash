package com.sarthak.modelstash;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.sarthak.modelstash.util.ScaleFormat;

import org.junit.Test;

public class ScaleFormatTest {

    @Test
    public void acceptsCommonWaysOfWritingAScale() {
        assertEquals("1:72", ScaleFormat.normalize("1:72"));
        assertEquals("1:72", ScaleFormat.normalize("72"));
        assertEquals("1:144", ScaleFormat.normalize("1/144"));
        assertEquals("1:35", ScaleFormat.normalize("  1 : 35 "));
        assertEquals("1:12.5", ScaleFormat.normalize("1:12,5"));
        assertEquals("1:1", ScaleFormat.normalize("1"));
    }

    @Test
    public void emptyMeansNoScale() {
        assertEquals("", ScaleFormat.normalize(""));
        assertEquals("", ScaleFormat.normalize("   "));
        assertEquals("", ScaleFormat.normalize(null));
    }

    @Test
    public void rejectsThingsThatAreNotScales() {
        assertNull(ScaleFormat.normalize("big"));
        assertNull(ScaleFormat.normalize("2:72"));
        assertNull(ScaleFormat.normalize("1:0"));
        assertNull(ScaleFormat.normalize("1:72:5"));
    }
}