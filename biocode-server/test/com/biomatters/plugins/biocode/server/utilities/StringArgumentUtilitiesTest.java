package com.biomatters.plugins.biocode.server.utilities;

import org.junit.Test;
import org.junit.Assert;

import java.util.HashMap;

/**
 * @author Gen Li
 *         Created on 21/10/14 7:34 AM
 */
public class StringArgumentUtilitiesTest extends Assert {
    @Test
    public void testIsStringNULLOnNULLString() {
        String NULLString = null;

        assertTrue(StringArgumentUtilities.isStringNULL(NULLString));
    }

    @Test
    public void testIsStringNULLOnNonNULLString() {
        String nonNULLString = "value";

        assertFalse(StringArgumentUtilities.isStringNULL(nonNULLString));
    }

    @Test
    public void testIsStringNULLOrEmptyOnNULLString() {
        String NULLString = null;

        assertTrue(StringArgumentUtilities.isStringNULLOrEmpty(NULLString));
    }

    @Test
    public void testIsStringNULLOrEmptyOnEmptyString() {
        String emptyString = "";

        assertTrue(StringArgumentUtilities.isStringNULLOrEmpty(emptyString));
    }

    @Test
    public void testIsStringNULLOrEmptyOnNonNULLNorEmptyString() {
        String nonNULLNorEmptyString = "value";

        assertFalse(StringArgumentUtilities.isStringNULLOrEmpty(nonNULLNorEmptyString));
    }

    @Test(expected=IllegalArgumentException.class)
    public void testCheckForNULLStringArgumentsOnArgumentsContainingNULLString() {
        final String nonNULLString = "value";
        final String nonNULLString2 = "value";
        final String NULLString = null;

        StringArgumentUtilities.checkForNULLStringArguments(new HashMap<String, String>() {
            {
                put("nonNULLString", nonNULLString);
                put("nonNULLString2", nonNULLString2);
                put("NULLString", NULLString);
            }
        });
    }

    @Test
    public void testCheckForNULLStringArgumentsOnArgumentsNotContainingNULLString() {
        final String nonNULLString = "value";
        final String nonNULLString2 = "value";
        final String nonNULLString3 = "value";

        StringArgumentUtilities.checkForNULLStringArguments(new HashMap<String, String>() {
            {
                put("nonNULLString", nonNULLString);
                put("nonNULLString2", nonNULLString2);
                put("nonNULLString3", nonNULLString3);
            }
        });
    }

    @Test(expected=IllegalArgumentException.class)
    public void testCheckForNULLOrEmptyStringArgumentsOnArgumentsContainingNULLString() {
        final String nonNULLNorEmptyString = "value";
        final String nonNULLNorEmptyString2 = "value";
        final String NULLString = null;

        StringArgumentUtilities.checkForNULLOrEmptyStringArguments(new HashMap<String, String>() {
            {
                put("nonNULLNorEmptyString", nonNULLNorEmptyString);
                put("nonNULLNorEmptyString2", nonNULLNorEmptyString2);
                put("NULLString", NULLString);
            }
        });
    }

    @Test(expected=IllegalArgumentException.class)
    public void testCheckForNULLOrEmptyStringArgumentsOnArgumentsContainingEmptyString() {
        final String nonNULLNorEmptyString = "value";
        final String nonNULLNorEmptyString2 = "value";
        final String emptyString = "";

        StringArgumentUtilities.checkForNULLOrEmptyStringArguments(new HashMap<String, String>() {
            {
                put("nonNULLNorEmptyString", nonNULLNorEmptyString);
                put("nonNULLNorEmptyString2", nonNULLNorEmptyString2);
                put("emptyString", emptyString);
            }
        });
    }

    @Test
    public void testCheckForNULLOrEmptyStringArgumentsOnArgumentsNotContainingNULLStringNorEmptyString() {
        final String nonNULLNorEmptyString = "value";
        final String nonNULLNorEmptyString2 = "value";
        final String nonNULLNorEmptyString3 = "value";

        StringArgumentUtilities.checkForNULLOrEmptyStringArguments(new HashMap<String, String>() {
            {
                put("nonNULLNorEmptyString", nonNULLNorEmptyString);
                put("nonNULLNorEmptyString2", nonNULLNorEmptyString2);
                put("nonNULLNorEmptyString3", nonNULLNorEmptyString3);
            }
        });
    }
}