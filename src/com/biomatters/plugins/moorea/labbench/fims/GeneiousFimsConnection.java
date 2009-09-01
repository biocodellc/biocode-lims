package com.biomatters.plugins.moorea.labbench.fims;

import com.biomatters.geneious.publicapi.databaseservice.Query;
import com.biomatters.geneious.publicapi.documents.AnnotatedPluginDocument;
import com.biomatters.geneious.publicapi.documents.DocumentField;
import com.biomatters.geneious.publicapi.plugin.Options;
import com.biomatters.plugins.moorea.MooreaUtilities;
import com.biomatters.plugins.moorea.labbench.ConnectionException;
import com.biomatters.plugins.moorea.labbench.FimsSample;

import java.util.Collections;
import java.util.List;
import java.util.Map;

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

    public void disconnect() throws ConnectionException {

    }

    public List<DocumentField> getSearchAttributes() {
        return Collections.emptyList();
    }

    public DocumentField getTissueSampleDocumentField() {
        return new DocumentField("Tissue ID", "", "tissueId", String.class, true, false);
    }

    public DocumentField getTissueBarcodeDocumentField() {
        return new DocumentField("Tissue Barcode", "", "tissue_barcode", String.class, true, false);
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

    public Map<String, String> getTissueIdsFromExtractionBarcodes(List<String> extractionIds) throws ConnectionException{
        return Collections.emptyMap();
    }

    public Map<String, String> getTissueIdsFromFimsPlate(String plateId) throws ConnectionException{
        return Collections.emptyMap();
    }

    public MooreaUtilities.LatLong getLatLong(AnnotatedPluginDocument annotatedDocument) {
        return null;
    }
}
