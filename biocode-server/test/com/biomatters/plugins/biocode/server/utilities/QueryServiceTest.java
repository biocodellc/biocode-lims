package com.biomatters.plugins.biocode.server.utilities;

import com.biomatters.geneious.publicapi.documents.DocumentField;
import com.biomatters.plugins.biocode.server.utilities.query.*;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import javax.ws.rs.BadRequestException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * @author Gen Li
 *         Created on 6/06/14 3:15 PM
 */
public class QueryServiceTest extends Assert {

    List<DocumentField> searchAttributes;

    @Before
    public void init() {
        searchAttributes = Arrays.asList(
                /* Not yet supported. */
                // DocumentField.createBooleanField("BooleanField", "Boolean field", "booleanField", false, false),
                // DocumentField.createLongField("LongField", "Long field", "longField", false, false),
                // DocumentField.createDoubleField("DoubleField", "Double field", "doubleField"),
                // DocumentField.createURLField("URLField", "URL field", "urlField"),
                DocumentField.createIntegerField("IntegerField", "Integer field", "integerField"),
                DocumentField.createStringField("StringField", "String field", "stringField"),
                DocumentField.createDateField("DateField", "Date field", "dateField")
        );
    }

    @Test
    public void testParseBasicQuery() {
        assertTrue(new QueryParser(searchAttributes).parseQuery("[stringField=value]") instanceof BasicQuery);
    }
    @Test
    public void testParseAndQuery() {
        assertTrue(((CompoundQuery)new QueryParser(searchAttributes).parseQuery("[stringField=valueOne]AND[stringField=valueTwo]OR[stringField=valueThree]")).getLHS() instanceof AndQuery);
    }
    @Test
    public void testParseXorQuery() {
        assertTrue(new QueryParser(searchAttributes).parseQuery("[stringField=valueOne]XOR[stringField=valueTwo]") instanceof XorQuery);
    }
    @Test
    public void testParseOrQuery() {
        assertTrue(new QueryParser(searchAttributes).parseQuery("[stringField=valueOne]OR[stringField=valueTwo]AND[stringField=valueThree]") instanceof OrQuery);
    }
    @Test
    public void testParseMultipleAndQuery() {
        assertTrue(new QueryParser(searchAttributes).parseQuery("[stringField=valueOne]AND[stringField=valueTwo]") instanceof MultipleAndQuery);
    }
    @Test
    public void testParseMultipleOrQuery() {
        assertTrue(new QueryParser(searchAttributes).parseQuery("[stringField=valueOne]OR[stringField=valueTwo]") instanceof MultipleOrQuery);
    }

    @Test
    public void testAndFunctionOnAllOverlappingValues() {
        testCompoundQuery(Arrays.asList("one", "two", "three"), Arrays.asList("one", "two", "three"), Arrays.asList("one", "two", "three"), new AndQuery(null, null));
    }
    @Test
    public void testAndFunctionOnPartialOverlappingValues() {
        testCompoundQuery(Arrays.asList("one", "two", "three"), Arrays.asList("three", "four", "five"), Arrays.asList("three"), new AndQuery(null, null));
    }
    @Test
    public void testAndFunctionOnNoOverlappingValues() {
        testCompoundQuery(Arrays.asList("one", "two", "three"), Arrays.asList("four", "five", "six"), new ArrayList<String>(), new AndQuery(null, null));
    }
    @Test
    public void testXorFunctionOnAllOverlappingValues() {
        testCompoundQuery(Arrays.asList("one", "two", "three"), Arrays.asList("one", "two", "three"), new ArrayList<String>(), new XorQuery(null, null));
    }
    @Test
    public void testXorFunctionOnPartialOverlappingValues() {
        testCompoundQuery(Arrays.asList("one", "two", "three"), Arrays.asList("three", "four", "five"), Arrays.asList("one", "two", "four", "five"), new XorQuery(null, null));
    }
    @Test
    public void testXorFunctionOnNoOverlappingValues() {
        testCompoundQuery(Arrays.asList("one", "two", "three"), Arrays.asList("four", "five", "six"), Arrays.asList("one", "two", "three", "four", "five", "six"), new XorQuery(null, null));
    }
    @Test
    public void testOrFunctionOnAllOverlappingValues() {
        testCompoundQuery(Arrays.asList("one", "two", "three"), Arrays.asList("one", "two", "three"), Arrays.asList("one", "two", "three"), new OrQuery(null, null));
    }
    @Test
    public void testOrFunctionOnPartialOverlappingValues() {
        testCompoundQuery(Arrays.asList("one", "two", "three"), Arrays.asList("three", "four", "five"), Arrays.asList("one", "two", "three", "four", "five"), new OrQuery(null, null));
    }
    @Test
    public void testOrFunctionOnNoOverlappingValues() {
        testCompoundQuery(Arrays.asList("one", "two", "three"), Arrays.asList("four", "five", "six"), Arrays.asList("one", "two", "three", "four", "five", "six"), new OrQuery(null, null));
    }

    @Test(expected=BadRequestException.class)
    public void testParseInvalidQuerySearchAttribute() {
        new QueryParser(searchAttributes).parseQuery("[invalidField=value]");
    }

    @Test
    public void testParseValidIntegerQueryValue() {
        new QueryParser(searchAttributes).parseQuery("[integerField=0]");
    }

    @Test(expected=BadRequestException.class)
    public void testParseInvalidIntegerQueryValue() {
        new QueryParser(searchAttributes).parseQuery("[integerField=nonIntegerValue]");
    }
    @Test
    public void testParseValidDateQueryValueFormat() {
        new QueryParser(searchAttributes).parseQuery("[dateField=2004-02-02]");
    }
    @Test(expected=BadRequestException.class)
    public void testParseInvalidDateQueryValueFormat() {
        new QueryParser(searchAttributes).parseQuery("[dateField=2004/02/02]");
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