package com.biomatters.plugins.biocode.server.utilities;

import org.junit.Test;
import org.junit.Assert;

import java.util.Arrays;
import java.util.HashMap;

/**
 * @author Gen Li
 *         Created on 21/10/14 7:34 AM
 */
public class StringVerificationUtilitiesTest extends Assert {
    private static final String REGULAR_STRING = "regular";
    private static final String EMPTY_STRING   = "";
    private static final String NULL_STRING    = null;

    @Test
    public void testIsStringNULL() {
        assertTrue(StringVerificationUtilities.isStringNULL(NULL_STRING));
        assertFalse(StringVerificationUtilities.isStringNULL(REGULAR_STRING));
    }

    @Test
    public void testIsStringNULLOrEmpty() {
        assertTrue(StringVerificationUtilities.isStringNULLOrEmpty(NULL_STRING));
        assertTrue(StringVerificationUtilities.isStringNULLOrEmpty(EMPTY_STRING));
        assertFalse(StringVerificationUtilities.isStringNULLOrEmpty(REGULAR_STRING));
    }

    @Test
    public void testCheckExistsNULLStringsBoolean() {
        assertTrue(StringVerificationUtilities.existsNULLStrings(Arrays.asList(NULL_STRING)));
        assertFalse(StringVerificationUtilities.existsNULLStrings(Arrays.asList(REGULAR_STRING)));
        assertTrue(StringVerificationUtilities.existsNULLStrings(Arrays.asList(NULL_STRING, REGULAR_STRING)));
    }

    @Test
    public void testCheckExistsEmptyStringsBoolean() {
        assertTrue(StringVerificationUtilities.existsEmptyStrings(Arrays.asList(EMPTY_STRING)));
        assertFalse(StringVerificationUtilities.existsEmptyStrings(Arrays.asList(REGULAR_STRING)));
        assertTrue(StringVerificationUtilities.existsEmptyStrings(Arrays.asList(EMPTY_STRING, REGULAR_STRING)));
    }

    @Test
    public void testCheckExistsNULLOrEmptyStringsBoolean() {
        assertTrue(StringVerificationUtilities.existsNULLOrEmptyStrings(Arrays.asList(NULL_STRING)));
        assertTrue(StringVerificationUtilities.existsNULLOrEmptyStrings(Arrays.asList(EMPTY_STRING)));
        assertFalse(StringVerificationUtilities.existsNULLOrEmptyStrings(Arrays.asList(REGULAR_STRING)));
        assertTrue(StringVerificationUtilities.existsNULLOrEmptyStrings(Arrays.asList(NULL_STRING, REGULAR_STRING)));
        assertTrue(StringVerificationUtilities.existsNULLOrEmptyStrings(Arrays.asList(EMPTY_STRING, REGULAR_STRING)));
    }

    @Test
    public void testCheckAllAreNULLOrEmptyStringsBoolean() {
        assertTrue(StringVerificationUtilities.allNULLOrEmptyStrings(Arrays.asList(NULL_STRING)));
        assertTrue(StringVerificationUtilities.allNULLOrEmptyStrings(Arrays.asList(EMPTY_STRING)));
        assertFalse(StringVerificationUtilities.allNULLOrEmptyStrings(Arrays.asList(REGULAR_STRING)));
        assertTrue(StringVerificationUtilities.allNULLOrEmptyStrings(Arrays.asList(NULL_STRING, EMPTY_STRING)));
        assertFalse(StringVerificationUtilities.allNULLOrEmptyStrings(Arrays.asList(NULL_STRING, REGULAR_STRING)));
        assertFalse(StringVerificationUtilities.allNULLOrEmptyStrings(Arrays.asList(EMPTY_STRING, REGULAR_STRING)));
    }

    @Test(expected=IllegalArgumentException.class)
    public void testThrowExceptionIfExistsNULLStringsOnNULLString() {
        StringVerificationUtilities.throwExceptionIfExistsNULLStrings(new HashMap<String, String>() {{
            put("NULL_STRING", NULL_STRING);
        }});
    }
    @Test
    public void testThrowExceptionIfExistsNULLStringsOnNonNULLString() {
        StringVerificationUtilities.throwExceptionIfExistsNULLStrings(new HashMap<String, String>() {{
            put("REGULAR_STRING", REGULAR_STRING);
        }});
    }

    @Test(expected=IllegalArgumentException.class)
    public void testThrowExceptionIfExistsEmptyStringsOnEmptyString() {
        StringVerificationUtilities.throwExceptionIfExistsEmptyStrings(new HashMap<String, String>() {{
            put("EMPTY_STRING", EMPTY_STRING);
        }});
    }
    @Test
    public void testThrowExceptionIfExistsEmptyStringsOnNonEmptyString() {
        StringVerificationUtilities.throwExceptionIfExistsEmptyStrings(new HashMap<String, String>() {{
            put("REGULAR_STRING", REGULAR_STRING);
        }});
    }

    @Test(expected=IllegalArgumentException.class)
    public void testThrowExceptionIfExistsNULLOrEmptyStringsOnNULLString() {
        StringVerificationUtilities.throwExceptionIfExistsNULLOrEmptyStrings(new HashMap<String, String>() {{
            put("NULL_STRING", NULL_STRING);
        }});
    }
    @Test(expected=IllegalArgumentException.class)
    public void testThrowExceptionIfExistsNULLOrEmptyStringsOnEmptyString() {
        StringVerificationUtilities.throwExceptionIfExistsNULLOrEmptyStrings(new HashMap<String, String>() {{
            put("EMPTY_STRING", EMPTY_STRING);
        }});
    }
    @Test
    public void testThrowExceptionIfExistsNULLOrEmptyStringsOnNonNULLNorEmptyString() {
        StringVerificationUtilities.throwExceptionIfExistsNULLOrEmptyStrings(new HashMap<String, String>() {{
            put("REGULAR_STRING", REGULAR_STRING);
        }});
    }
    @Test(expected=IllegalArgumentException.class)
    public void testThrowExceptionIfExistsNULLOrEmptyStringsOnNULLStringAndNonNULLNorEmptyString() {
        StringVerificationUtilities.throwExceptionIfExistsNULLOrEmptyStrings(new HashMap<String, String>() {{
            put("NULL_STRING", NULL_STRING);
            put("REGULAR_STRING", REGULAR_STRING);
        }});
    }
    @Test(expected=IllegalArgumentException.class)
    public void testThrowExceptionIfExistsNULLOrEmptyStringsOnEmptyStringAndNonNULLNorEmptyString() {
        StringVerificationUtilities.throwExceptionIfExistsNULLOrEmptyStrings(new HashMap<String, String>() {{
            put("EMPTY_STRING", EMPTY_STRING);
            put("REGULAR_STRING", REGULAR_STRING);
        }});
    }

    @Test(expected=IllegalArgumentException.class)
    public void testThrowExceptionIfAllAreNULLOrEmptyStringsOnNULLString() {
        StringVerificationUtilities.throwExceptionIfAllNULLOrEmptyStrings(new HashMap<String, String>() {{
            put("NULL_STRING", NULL_STRING);
        }});
    }
    @Test(expected=IllegalArgumentException.class)
    public void testThrowExceptionIfAllAreNULLOrEmptyStringsOnEmptyString() {
        StringVerificationUtilities.throwExceptionIfAllNULLOrEmptyStrings(new HashMap<String, String>() {{
            put("EMPTY_STRING", EMPTY_STRING);
        }});
    }
    @Test
    public void testThrowExceptionIfAllAreNULLOrEmptyStringsOnNonNULLNorEmptyString() {
        StringVerificationUtilities.throwExceptionIfAllNULLOrEmptyStrings(new HashMap<String, String>() {{
            put("REGULAR_STRING", REGULAR_STRING);
        }});
    }
    @Test(expected=IllegalArgumentException.class)
    public void testThrowExceptionIfAllAreNULLOrEmptyStringsOnNULLStringAndEmptyString() {
        StringVerificationUtilities.throwExceptionIfAllNULLOrEmptyStrings(new HashMap<String, String>() {{
            put("NULL_STRING", NULL_STRING);
            put("EMPTY_STRING", EMPTY_STRING);
        }});
    }
    @Test
    public void testThrowExceptionIfAllAreNULLOrEmptyStringsOnNULLStringAndNonNULLNorEmptyString() {
        StringVerificationUtilities.throwExceptionIfAllNULLOrEmptyStrings(new HashMap<String, String>() {{
            put("NULL_STRING", NULL_STRING);
            put("REGULAR_STRING", REGULAR_STRING);
        }});
    }
    @Test
    public void testThrowExceptionIfAllAreNULLOrEmptyStringsOnEmptyStringAndNonNULLNorEmptyString() {
        StringVerificationUtilities.throwExceptionIfAllNULLOrEmptyStrings(new HashMap<String, String>() {{
            put("EMPTY_STRING", EMPTY_STRING);
            put("REGULAR_STRING", REGULAR_STRING);
        }});
    }
}