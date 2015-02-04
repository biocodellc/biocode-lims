package com.biomatters.plugins.biocode.server.filter;

import org.junit.Assert;
import org.junit.Test;

/**
 * @author Frank
 */
public class FilterTest extends Assert {

    @Test
    public void testVersionCompare() {
        BiocodeVersionSupportFilter filter = new BiocodeVersionSupportFilter();
        assertTrue(filter.compareVersion("1.0.0", "1.0.0") == 0);
        assertTrue(filter.compareVersion("1.0.0", "1.0") > 0);
        assertTrue(filter.compareVersion("1.0", "1.0.0") < 0);
        assertTrue(filter.compareVersion("12.0.0", "2.0.0") > 0);
        assertTrue(filter.compareVersion("2.8.3", "2.8.4") < 0);
    }

}
