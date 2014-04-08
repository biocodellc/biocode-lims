package com.biomatters.plugins.biocode.server;

import com.biomatters.geneious.publicapi.plugin.TestGeneious;
import org.junit.Assert;
import org.junit.Test;

import javax.ws.rs.BadRequestException;
import java.util.Collections;

/**
 * @author Matthew Cheung
 * @version $Id$
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
}
