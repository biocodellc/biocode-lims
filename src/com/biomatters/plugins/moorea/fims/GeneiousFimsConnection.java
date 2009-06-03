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
 * Date: 12/05/2009
 * Time: 8:00:41 AM
 * To change this template use File | Settings | File Templates.
 */
public class GeneiousFimsConnection extends FIMSConnection{
    public String getName() {
        return "geneious";
    }

    public String getDescription() {
        return  "Use the Lab bench database to store field information";
    }

    public String getLabel() {
        return "Geneious";
    }

    public Options getConnectionOptions() {
        return null;
    }

    public void connect(Options options) throws ConnectionException {
    }

    public List<DocumentField> getFimsAttributes() {
        return Collections.emptyList();
    }

    public List<FimsSample> getMatchingSamples(Query query) {
        return null;//todo: always return all sample records from the LIMS
    }
}
