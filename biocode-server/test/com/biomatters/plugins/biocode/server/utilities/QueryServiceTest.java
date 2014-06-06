package com.biomatters.plugins.biocode.server.utilities;

import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * @author Gen Li
 *         Created on 6/06/14 3:15 PM
 */
public class QueryServiceTest extends Assert {

    @Test
    public void testAndAllOverlap() {
        testCompoundQuery(Arrays.asList("one", "two", "three"), Arrays.asList("one", "two", "three"), Arrays.asList("one", "two", "three"), new AndQuery(null, null));
    }

    @Test
    public void testANDPartialOverlap() {
        testCompoundQuery(Arrays.asList("one", "two", "three"), Arrays.asList("three", "four", "five"), Arrays.asList("three"), new AndQuery(null, null));
    }

    @Test
    public void testANDNoOverlap() {
        testCompoundQuery(Arrays.asList("one", "two", "three"), Arrays.asList("four", "five", "six"), new ArrayList<String>(), new AndQuery(null, null));
    }

    @Test
    public void testXORAllOverlap() {
        testCompoundQuery(Arrays.asList("one", "two", "three"), Arrays.asList("one", "two", "three"), new ArrayList<String>(), new XorQuery(null, null));
    }

    @Test
    public void testXORPartialOverlap() {
        testCompoundQuery(Arrays.asList("one", "two", "three"), Arrays.asList("three", "four", "five"), Arrays.asList("one", "two", "four", "five"), new XorQuery(null, null));
    }

    @Test
    public void testXORNoOverlap() {
        testCompoundQuery(Arrays.asList("one", "two", "three"), Arrays.asList("four", "five", "six"), Arrays.asList("one", "two", "three", "four", "five", "six"), new XorQuery(null, null));
    }

    @Test
    public void testORAllOverlap() {
        testCompoundQuery(Arrays.asList("one", "two", "three"), Arrays.asList("one", "two", "three"), Arrays.asList("one", "two", "three"), new OrQuery(null, null));
    }

    @Test
    public void testORPartialOverlap() {
        testCompoundQuery(Arrays.asList("one", "two", "three"), Arrays.asList("three", "four", "five"), Arrays.asList("one", "two", "three", "four", "five"), new OrQuery(null, null));
    }

    @Test
    public void testORNoneOverlap() {
        testCompoundQuery(Arrays.asList("one", "two", "three"), Arrays.asList("four", "five", "six"), Arrays.asList("one", "two", "three", "four", "five", "six"), new OrQuery(null, null));
    }

    private <T extends Comparable> void testSameContentsUnordered(List<T> oneOrTwoAsList, List<T> result) {
        Collections.sort(oneOrTwoAsList);
        Collections.sort(result);

        assertEquals(oneOrTwoAsList, result);
    }

    private <T extends Comparable> void testCompoundQuery(List<T> one, List<T> two, List<T> expected, CompoundQuery query) {
        List<T> result = query.combineLists(one, two);
        testSameContentsUnordered(expected, result);
    }
}