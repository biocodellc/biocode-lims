package com.biomatters.plugins.biocode.labbench.fims;

import com.biomatters.geneious.publicapi.databaseservice.Query;
import com.biomatters.geneious.publicapi.documents.AnnotatedPluginDocument;
import com.biomatters.geneious.publicapi.documents.DocumentField;
import com.biomatters.geneious.publicapi.plugin.Options;
import com.biomatters.plugins.biocode.BiocodeUtilities;
import com.biomatters.plugins.biocode.labbench.ConnectionException;
import com.biomatters.plugins.biocode.labbench.FimsSample;

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
     * @throws com.biomatters.plugins.biocode.labbench.ConnectionException if the client is unable to connect - either because of a connection error, or bad credentials
     */
    public abstract void connect(Options options) throws ConnectionException;

    public abstract void disconnect() throws ConnectionException;

    public abstract DocumentField getTissueSampleDocumentField();

    public abstract List<DocumentField> getCollectionAttributes();

    /**
     *
     * @return list of taxonomy fields in order of highest level (eg kingdom) to lowest (eg. species).
     */
    public abstract List<DocumentField> getTaxonomyAttributes();

    public abstract List<DocumentField> getSearchAttributes();

    public abstract BiocodeUtilities.LatLong getLatLong(AnnotatedPluginDocument annotatedDocument);

    public abstract List<FimsSample> getMatchingSamples(Query query) throws ConnectionException;

    public abstract Map<String, String> getTissueIdsFromExtractionBarcodes(List<String> extractionIds) throws ConnectionException;

    public abstract Map<String, String> getTissueIdsFromFimsExtractionPlate(String plateId) throws ConnectionException;

    public abstract Map<String, String> getTissueIdsFromFimsTissuePlate(String plateId) throws ConnectionException;

}
