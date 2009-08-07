package com.biomatters.plugins.moorea.labbench.fims;

import com.biomatters.geneious.publicapi.databaseservice.Query;
import com.biomatters.geneious.publicapi.documents.DocumentField;
import com.biomatters.geneious.publicapi.plugin.Options;
import com.biomatters.plugins.moorea.labbench.ConnectionException;
import com.biomatters.plugins.moorea.labbench.FimsSample;

import java.util.List;
import java.util.Map;

/**
 *
 * Represents a connection to a field management database.  The database structure is flattened, and the user is only
 * able to query tissue sample records, which have attributes taken from the rest of the field management database. <br>
 * User: steve
 * Date: 11/05/2009
 * Time: 6:16:57 PM
 */
public abstract class FIMSConnection {

    /**
     *
     * @return a user-friendly name for this connection
     */
    public abstract String getLabel();

    /**
     *
     * @return a unique identifier for this connection
     */
    public abstract String getName();

    /**
     *
     * @return a user friendly description of this connection
     */
    public abstract String getDescription();

    /**
     * @return some options allowing the user to enter all necessary information to connect to the database
     * (eg username/password, server address etc)
     */
    public abstract Options getConnectionOptions();

    /**
     *  connects to the field management database
     * @param options the options taken from {@link #getConnectionOptions()} and passed to the user.
     * @throws com.biomatters.plugins.moorea.labbench.ConnectionException if the client is unable to connect - either because of a connection error, or bad credentials
     */
    public abstract void connect(Options options) throws ConnectionException;

    public abstract void disconnect() throws ConnectionException;

    public abstract DocumentField getTissueSampleDocumentField();

    public abstract DocumentField getTissueBarcodeDocumentField();

    public DocumentField getPlateDocumentField() {
        return new DocumentField("Plate Name (FIMS)", "", "format_name96", String.class, true, false);
    }

    public DocumentField getWellDocumentField() {
        return new DocumentField("Well Number (FIMS)", "", "well_number96", String.class, true, false);
    }

    public abstract List<DocumentField> getCollectionAttributes();

    public abstract List<DocumentField> getTaxonomyAttributes();

    public abstract List<DocumentField> getSearchAttributes();

    public abstract List<FimsSample> getMatchingSamples(Query query) throws ConnectionException;

    public abstract Map<String, String> getTissueIdsFromExtractionBarcodes(List<String> extractionIds) throws ConnectionException;

}
