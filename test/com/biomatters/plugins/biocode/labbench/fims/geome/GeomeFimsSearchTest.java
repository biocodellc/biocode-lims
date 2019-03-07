package com.biomatters.plugins.biocode.labbench.fims.geome;

import com.biomatters.geneious.publicapi.databaseservice.DatabaseServiceException;
import com.biomatters.geneious.publicapi.databaseservice.Query;
import com.biomatters.geneious.publicapi.plugin.TestGeneious;
import com.biomatters.plugins.biocode.labbench.BiocodeService;
import com.biomatters.plugins.biocode.labbench.ConnectionException;
import com.biomatters.plugins.biocode.labbench.fims.biocode.BiocodeFimsTestCase;
import org.junit.Test;

import java.net.MalformedURLException;
import java.util.List;
import java.util.Map;

public class GeomeFimsSearchTest extends GeomeFimsTestCase {

    @Test 
    public void canMakeOrQuery() throws ConnectionException, MalformedURLException, DatabaseServiceException {
        TestGeneious.initialize();
        geomeFIMSConnection geomeFIMSConnection = new geomeFIMSConnection();
        geomeFIMSConnectionOptions geomeFIMSConnectionOptions = (geomeFIMSConnectionOptions) geomeFIMSConnection.getConnectionOptions();
        geomeFIMSConnectionOptions.setIncludePublicProjectsOption(true);
        geomeFIMSConnectionOptions.setHostOption(geomeFIMSConnection.GEOME_URL);
        geomeFIMSConnectionOptions.setUserName("demo");
        geomeFIMSConnectionOptions.setPassword("demo1234");
        geomeFIMSConnection.connect(geomeFIMSConnectionOptions);

        Map<String, Object> searchOptions = BiocodeService.getSearchDownloadOptions
                (true, false, false, false, false);
        //Query query = Query.Factory.createExtendedQuery("materialSampleID contains 'BIRL_0842'", searchOptions);
        Query query = Query.Factory.createExtendedQuery("materialSampleID::%22%25BIRL_0842%25%22", searchOptions);
        List<String> tissues = geomeFIMSConnection.getTissueIdsMatchingQuery(
                query,
                null);

        assertFalse("There should have been some tissues", tissues.isEmpty());

        // TODO: extend the tests for the geome search to search for particular items.
    }
}
