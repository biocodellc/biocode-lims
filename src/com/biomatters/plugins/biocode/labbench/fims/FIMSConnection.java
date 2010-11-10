package com.biomatters.plugins.biocode.labbench.fims;

import com.biomatters.geneious.publicapi.databaseservice.Query;
import com.biomatters.geneious.publicapi.documents.AnnotatedPluginDocument;
import com.biomatters.geneious.publicapi.documents.DocumentField;
import com.biomatters.geneious.publicapi.documents.Condition;
import com.biomatters.geneious.publicapi.plugin.Options;
import com.biomatters.plugins.biocode.BiocodeUtilities;
import com.biomatters.plugins.biocode.labbench.ConnectionException;
import com.biomatters.plugins.biocode.labbench.FimsSample;

import java.util.*;
import java.lang.ref.SoftReference;

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

    public abstract void disconnect();

    public abstract DocumentField getTissueSampleDocumentField();

    public abstract List<DocumentField> getCollectionAttributes();

    /**
     *
     * @return list of taxonomy fields in order of highest level (eg kingdom) to lowest (eg. species).
     */
    public abstract List<DocumentField> getTaxonomyAttributes();

    public abstract List<DocumentField> getSearchAttributes();

    public abstract BiocodeUtilities.LatLong getLatLong(AnnotatedPluginDocument annotatedDocument);

    public final List<FimsSample> getMatchingSamples(Query query) throws ConnectionException {
        List<FimsSample> samples = _getMatchingSamples(query);
        for(FimsSample sample : samples) {
            sampleCache.put(sample.getId(), new SoftReference<FimsSample>(sample));
        }
        return samples;
    }

    public abstract List<FimsSample> _getMatchingSamples(Query query) throws ConnectionException;

    private Map<String, SoftReference<FimsSample>> sampleCache = new HashMap<String, SoftReference<FimsSample>>();

    public FimsSample getFimsSampleFromCache(String tissueId) {
        SoftReference<FimsSample> fimsSampleSoftReference = sampleCache.get(tissueId);
        return fimsSampleSoftReference != null ? fimsSampleSoftReference.get() : null;
    }

    public List<FimsSample> getMatchingSamples(Collection<String> tissueIds) throws ConnectionException{
        List<String> samplesToSearch = new ArrayList<String>();
        List<FimsSample> samplesToReturn = new ArrayList<FimsSample>();

        for(String s : tissueIds) {
            SoftReference<FimsSample> tissueSampleWeakReference = sampleCache.get(s);
            if(tissueSampleWeakReference != null && tissueSampleWeakReference.get() != null) {
                FimsSample sample = tissueSampleWeakReference.get();
                    samplesToReturn.add(sample);
            }
            else {
                samplesToSearch.add(s);
            }
        }

        if(samplesToSearch.size() > 0) {
            List<Query> queries = new ArrayList<Query>();
            for(String tissue : samplesToSearch) {
                if(tissue != null) {
                    Query fieldQuery = Query.Factory.createFieldQuery(getTissueSampleDocumentField(), Condition.EQUAL, tissue);
                    if(!queries.contains(fieldQuery)) {
                         queries.add(fieldQuery);
                    }
                }
            }

            Query orQuery = Query.Factory.createOrQuery(queries.toArray(new Query[queries.size()]), Collections.<String, Object>emptyMap());
            List<FimsSample> searchedSamples = getMatchingSamples(orQuery);
            for(FimsSample sample : searchedSamples) {
                sampleCache.put(sample.getId(), new SoftReference<FimsSample>(sample));
            }
            samplesToReturn.addAll(searchedSamples);
        }

        return samplesToReturn;
    }

    public abstract Map<String, String> getTissueIdsFromExtractionBarcodes(List<String> extractionIds) throws ConnectionException;

    public abstract Map<String, String> getTissueIdsFromFimsExtractionPlate(String plateId) throws ConnectionException;

    public abstract DocumentField getPlateDocumentField();

    public abstract DocumentField getWellDocumentField();

    public abstract boolean canGetTissueIdsFromFimsTissuePlate();

    public Map<String, String> getTissueIdsFromFimsTissuePlate(String plateId) throws ConnectionException{
        if(canGetTissueIdsFromFimsTissuePlate()) {
            DocumentField plateField = getPlateDocumentField();
            DocumentField wellField = getWellDocumentField();

            Query query = Query.Factory.createFieldQuery(plateField, Condition.EQUAL, plateId);
            List<FimsSample> samples = getMatchingSamples(query);

            Map<String, String> results = new HashMap<String, String>();

            for(FimsSample sample : samples) {
                Object wellValue = sample.getFimsAttributeValue(wellField.getCode());
                if(wellValue != null && wellField.toString().length() > 0) {
                    results.put(wellValue.toString(), sample.getId());
                }
            }

            return results;
        }
        return Collections.emptyMap();
    }

}
