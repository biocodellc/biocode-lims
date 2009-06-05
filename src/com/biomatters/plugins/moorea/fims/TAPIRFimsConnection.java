package com.biomatters.plugins.moorea.fims;

import com.biomatters.geneious.publicapi.databaseservice.Query;
import com.biomatters.geneious.publicapi.documents.DocumentField;
import com.biomatters.geneious.publicapi.plugin.Options;
import com.biomatters.plugins.moorea.ConnectionException;
import com.biomatters.plugins.moorea.FimsSample;

import java.util.Collections;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: steve
 * Date: 27/05/2009
 * Time: 6:15:23 AM
 * To change this template use File | Settings | File Templates.
 */
public class TAPIRFimsConnection extends FIMSConnection{
    public String getName() {
        return "tapir";
    }

    public String getDescription() {
        return  "Connect to a TAPIR service";
    }

    public String getLabel() {
        return "TAPIR";
    }

    public Options getConnectionOptions() {
        return null;
    }

    public void connect(Options options) throws ConnectionException {
    }

    public void disconnect() throws ConnectionException {
        
    }

    public List<DocumentField> getSearchAttributes() {
        return Collections.emptyList();
    }

    public List<DocumentField> getCollectionAttributes() {
        return Collections.emptyList();
    }

    public List<DocumentField> getTaxonomyAttributes() {
        return Collections.emptyList();
    }

    public List<FimsSample> getMatchingSamples(Query query) {
        return null;//todo: always return all sample records from the LIMS
    }
}
