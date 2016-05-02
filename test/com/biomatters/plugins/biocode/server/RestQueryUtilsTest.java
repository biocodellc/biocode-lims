package com.biomatters.plugins.biocode.server;

import com.biomatters.geneious.publicapi.databaseservice.AdvancedSearchQueryTerm;
import com.biomatters.geneious.publicapi.databaseservice.Query;
import com.biomatters.geneious.publicapi.documents.Condition;
import com.biomatters.geneious.publicapi.documents.DocumentField;
import com.biomatters.geneious.publicapi.plugin.TestGeneious;
import com.biomatters.plugins.biocode.labbench.lims.LIMSConnection;
import org.junit.Assert;
import org.junit.Test;

import javax.ws.rs.BadRequestException;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.GregorianCalendar;

/**
 * @author Matthew Cheung
 *          <p/>
 *          Created on 8/04/14 12:06 PM
 */
public class RestQueryUtilsTest extends Assert {
    @Test
    public void canConvertBasicQueryString() {
        TestGeneious.initialize();
        assertNotNull(RestQueryUtils.createQueryFromQueryString(RestQueryUtils.QueryType.AND,
                "[plate.name]~test", Collections.<String, Object>emptyMap()));
        assertNotNull(RestQueryUtils.createQueryFromQueryString(RestQueryUtils.QueryType.AND,
                        "[plate.name]!~test", Collections.<String, Object>emptyMap()));
        assertNotNull(RestQueryUtils.createQueryFromQueryString(RestQueryUtils.QueryType.AND,
                        "[plate.name]~test+[plate.name]!~test", Collections.<String, Object>emptyMap()));
    }

    @Test(expected = BadRequestException.class)
    public void doesNotAcceptUnknownField() {
        assertNotNull(RestQueryUtils.createQueryFromQueryString(RestQueryUtils.QueryType.AND,
                        "[abc]~123", Collections.<String, Object>emptyMap()));
    }

    @Test(expected = BadRequestException.class)
    public void doesNotAcceptFieldWithoutSquareBrackets() {
        assertNotNull(RestQueryUtils.createQueryFromQueryString(RestQueryUtils.QueryType.AND,
                        "plate.name~test", Collections.<String, Object>emptyMap()));
    }

    @Test
    public void canConvertDates() {
        TestGeneious.initialize();

        Date now = new Date();
        Query query = Query.Factory.createFieldQuery(LIMSConnection.PLATE_DATE_FIELD, Condition.EQUAL, now);

        RestQueryUtils.Query restQuery = RestQueryUtils.createRestQuery(query);
        Query convertedQuery = RestQueryUtils.createQueryFromQueryString(RestQueryUtils.QueryType.AND, restQuery.getQueryString(), Collections.<String, Object>emptyMap());
        assertNotNull(convertedQuery);

        Object[] terms = ((AdvancedSearchQueryTerm) convertedQuery).getValues();
        assertEquals(1, terms.length);
        assertTrue("Expected a Date but got " + terms[0].getClass().getSimpleName() + ".class (" + terms[0].toString() + ")", terms[0] instanceof Date);
        Date convertedDate = (Date) terms[0];
        GregorianCalendar convertedCal = new GregorianCalendar();
        convertedCal.setTime(convertedDate);

        GregorianCalendar cal = new GregorianCalendar();
        cal.setTime(now);
        assertEquals(cal.get(Calendar.YEAR), convertedCal.get(Calendar.YEAR));
        assertEquals(cal.get(Calendar.MONTH), convertedCal.get(Calendar.MONTH));
        assertEquals(cal.get(Calendar.DAY_OF_MONTH), convertedCal.get(Calendar.DAY_OF_MONTH));

    }
}
