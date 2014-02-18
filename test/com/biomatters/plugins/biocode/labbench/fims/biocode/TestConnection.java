package com.biomatters.plugins.biocode.labbench.fims.biocode;

import com.biomatters.geneious.publicapi.databaseservice.DatabaseServiceException;
import org.junit.Assert;
import org.junit.Test;

import java.util.List;

/**
 * @author Matthew Cheung
 *         Created on 3/02/14 3:10 PM
 */
public class TestConnection extends Assert {

    @Test
    public void getGraphs() throws DatabaseServiceException {
        List<Graph> graphs = BiocodeFIMSUtils.getGraphsForProject("1");
        assertNotNull(graphs);
        assertFalse(graphs.isEmpty());
    }

    @Test
    public void getFimsData() throws DatabaseServiceException {
        BiocodeFimsData data = BiocodeFIMSUtils.getData(
                "1", null, null);

        System.out.println(data.header);
        assertFalse(data.header.isEmpty());
    }
}
