package com.biomatters.plugins.biocode;

import org.junit.Assert;
import org.junit.Test;

public class VersionsTest extends Assert {

    @Test
    public void basicEquality() {
        assertEquals(0, BiocodePlugin.compareVersions("1", "1"));
        assertEquals(0, BiocodePlugin.compareVersions("1.1", "1.1"));
        assertEquals(0, BiocodePlugin.compareVersions("1.2.3", "1.2.3"));
    }

    @Test
    public void ignoresV() {
        assertEquals(0, BiocodePlugin.compareVersions("v1", "v1"));
    }

    @Test
    public void basicComparison() {
        assertEquals(-1, BiocodePlugin.compareVersions("1", "2"));
        assertEquals(-1, BiocodePlugin.compareVersions("1.1", "2"));
        assertEquals(-1, BiocodePlugin.compareVersions("1.1", "1.2"));
        assertEquals(-1, BiocodePlugin.compareVersions("1.1.1", "1.2.3"));

        assertEquals(1, BiocodePlugin.compareVersions("2", "1"));
        assertEquals(1, BiocodePlugin.compareVersions("2", "1.1"));
        assertEquals(1, BiocodePlugin.compareVersions("1.2.3", "1.1.1"));
    }

    @Test
    public void multiLevelComparison() {
        assertEquals(-1, BiocodePlugin.compareVersions("1", "1.2"));
        assertEquals(-1, BiocodePlugin.compareVersions("1.1", "2"));

        assertEquals(1, BiocodePlugin.compareVersions("1.2", "1"));
        assertEquals(1, BiocodePlugin.compareVersions("2", "1.1"));
    }
}
