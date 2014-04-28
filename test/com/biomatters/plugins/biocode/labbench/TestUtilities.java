package com.biomatters.plugins.biocode.labbench;

import java.net.URL;

/**
 * Created Gen Li on 28/04/14.
 */
public class TestUtilities {
    public static String getPathToDemoFIMSExcel(Class aClass, String pathname) {
        final URL resource = aClass.getResource(pathname);
        if (resource == null) {
            throw new IllegalArgumentException("Couldn't find spreadsheet");
        }
        return resource.getFile().replace("%20", " ");
    }
}
