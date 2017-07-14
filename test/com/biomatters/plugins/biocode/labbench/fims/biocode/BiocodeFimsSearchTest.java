package com.biomatters.plugins.biocode.labbench.fims.biocode;

import com.biomatters.geneious.publicapi.databaseservice.DatabaseServiceException;
import com.biomatters.geneious.publicapi.databaseservice.Query;
import com.biomatters.geneious.publicapi.documents.Condition;
import com.biomatters.geneious.publicapi.documents.DocumentField;
import com.biomatters.geneious.publicapi.documents.URN;
import com.biomatters.geneious.publicapi.plugin.Options;
import com.biomatters.geneious.publicapi.plugin.TestGeneious;
import com.biomatters.geneious.publicapi.utilities.ThreadUtilities;
import com.biomatters.plugins.biocode.labbench.BiocodeService;
import com.biomatters.plugins.biocode.labbench.ConnectionException;
import com.biomatters.plugins.biocode.labbench.FimsSample;
import com.biomatters.plugins.biocode.labbench.PasswordOptions;
import com.biomatters.plugins.biocode.utilities.SharedCookieHandler;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.net.MalformedURLException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class BiocodeFimsSearchTest extends BiocodeFimsTestCase {

    @Test 
    public void canMakeOrQuery() throws ConnectionException, MalformedURLException, DatabaseServiceException {
        TestGeneious.initialize();
        BiocodeFIMSConnection connection = new BiocodeFIMSConnection();
        BiocodeFIMSOptions options = (BiocodeFIMSOptions)connection.getConnectionOptions();
        BiocodeFIMSConnectionOptions connectionOptions = (BiocodeFIMSConnectionOptions)options.getConnectionOptions();

        connectionOptions.login(BiocodeFIMSConnection.BISCICOL_URL, "demo", "demo");

        connectionOptions.setStringValue("project", "BWPT");
        assertTrue(connectionOptions.getProject().code.equals("BWPT"));

        options.update();
        ThreadUtilities.sleep(1000);  // Wait for update to update UI components
        options.setStringValue("tissueId", "TABLEFIMS:urn:tissueBarcode");
        options.setStringValue("specimenId", "TABLEFIMS:urn:voucherID");
        options.autodetectTaxonFields();

        connection.connect(options);


        Map<String, Object> searchOptions = BiocodeService.getSearchDownloadOptions(true, false, false, false, false);
        List<String> tissues = connection.getTissueIdsMatchingQuery(Query.Factory.createExtendedQuery("",
                searchOptions), null);
        assertFalse("There should have been some tissues", tissues.isEmpty());
        List<FimsSample> fimsSamples = connection.retrieveSamplesForTissueIds(tissues);


        FimsSample sample1 = null;
        FimsSample sample2 = null;

        List<DocumentField> taxonLevels = fimsSamples.get(0).getTaxonomyAttributes();
        DocumentField taxonLevel = taxonLevels.get(taxonLevels.size()-1);
        for (FimsSample fimsSample : fimsSamples) {
            Object value = fimsSample.getFimsAttributeValue(taxonLevel.getCode());
            if(value == null) continue;
            
            if(sample1 == null) {
                sample1 = fimsSample;
            } else if(sample2 == null && !sample1.getFimsAttributeValue(taxonLevel.getCode()).equals(value)) {
                sample2 = fimsSample;
            }
        }

        Query query = Query.Factory.createOrQuery(new Query[]{
                Query.Factory.createFieldQuery(taxonLevel, Condition.EQUAL, sample1.getFimsAttributeValue(taxonLevel.getCode())),
                Query.Factory.createFieldQuery(taxonLevel, Condition.EQUAL, sample2.getFimsAttributeValue(taxonLevel.getCode()))
        }, searchOptions);

        List<String> fromQuery = connection.getTissueIdsMatchingQuery(query, null);
        assertTrue(fromQuery.contains(sample1.getId()));
        assertTrue(fromQuery.contains(sample2.getId()));
    }
}
