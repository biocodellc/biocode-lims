package com.biomatters.plugins.biocode.labbench.fims.geome;

import com.biomatters.geneious.publicapi.databaseservice.DatabaseServiceException;
import com.biomatters.geneious.publicapi.databaseservice.Query;
import com.biomatters.geneious.publicapi.databaseservice.RetrieveCallback;
import com.biomatters.geneious.publicapi.documents.DocumentField;
import com.biomatters.geneious.publicapi.plugin.Options;
import com.biomatters.plugins.biocode.labbench.ConnectionException;
import com.biomatters.plugins.biocode.labbench.FimsSample;
import com.biomatters.plugins.biocode.labbench.PasswordOptions;
import com.biomatters.plugins.biocode.labbench.TissueDocument;
import com.biomatters.plugins.biocode.labbench.fims.FIMSConnection;
import com.biomatters.plugins.biocode.labbench.fims.FimsProject;
import com.biomatters.plugins.biocode.labbench.fims.TableFimsSample;
import org.apache.commons.collections.map.LinkedMap;


import javax.ws.rs.client.Invocation;
import javax.ws.rs.core.Response;
import java.lang.ref.SoftReference;
import java.util.*;
import java.util.function.BiConsumer;

public class geomeFIMSConnection extends FIMSConnection {
    private static final String HOST = "api.develop.geome-db.org";
    static final String GEOME_URL = "https://" + HOST;
    private geomeFIMSClient client;

    @Override
    public String getLabel() {
        return "GeOMe FIMS";
    }

    @Override
    public String getName() {
        return "GeOMe FIMS";
    }

    @Override
    public String getDescription() {
        return "Connection to GeOMe (https://geome-db.org/)";
    }

    @Override
    public PasswordOptions getConnectionOptions() {
        return new geomeFIMSOptions();
    }



    @Override
    public void _connect(Options options) throws ConnectionException {
        if (!(options instanceof geomeFIMSOptions)) {
            throw new IllegalArgumentException("_connect() must be called with Options obtained from calling _getConnectionOptions()");
        }
        geomeFIMSOptions fimsOptions = (geomeFIMSOptions) options;
        client = new geomeFIMSClient(fimsOptions.getHost(), requestTimeoutInSeconds);
        try {
            client.login(fimsOptions.getUserName(), fimsOptions.getPassword());
            List<Project> projects = client.getProjects();
            for (Project project : projects) {
                Invocation.Builder configRequest = client.getQueryTarget().path("projects").path(String.valueOf(project.id)).path("config").request();

                Response response = configRequest.get();
                ProjectConfig config = geomeFIMSClient.getRestServiceResult(ProjectConfig.class, response);
                for (ProjectConfig.Entity entity : config.entities) {
                    if(!Arrays.asList("Tissue", "Event", "Sample").contains(entity.conceptAlias)) {
                        continue;
                    }

                    for (Project.Field attribute : entity.attributes) {
                        allAttributes.put(attribute.uri, attribute.asDocumentField());
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw new ConnectionException("Unable to connect to GeOMe: " + e.getMessage());
        }
    }

    private Map<String, DocumentField> allAttributes = new LinkedMap();

    @Override
    public void disconnect() {

    }

    private static final String TISSUE_URN = "urn:tissueID";
    private static final String SAMPLE_URN = "urn:materialSampleID";
    private static final String EVENT_ID = "eventID";
    @Override
    public DocumentField getTissueSampleDocumentField() {
        return allAttributes.get(TISSUE_URN);
    }

    @Override
    public Map<String, Collection<FimsSample>> getProjectsForSamples(Collection<FimsSample> samples) {
        return null;
    }

    @Override
    public List<FimsProject> getProjects() throws DatabaseServiceException {
        return null;
    }

    @Override
    protected List<DocumentField> _getCollectionAttributes() {
        return new ArrayList<>(allAttributes.values());
    }

    @Override
    protected List<DocumentField> _getTaxonomyAttributes() {
        return Arrays.asList(
                allAttributes.get("urn:family"),
                allAttributes.get("urn:scientificName")
        );
    }

    @Override
    protected List<DocumentField> _getSearchAttributes() {
        return new ArrayList<>(allAttributes.values());
    }

    @Override
    public List<String> getTissueIdsMatchingQuery(Query query, List<FimsProject> projectsToMatch) throws ConnectionException {
        return getTissueIdsMatchingQuery(query, projectsToMatch, true);
    }

    private Map<String, SoftReference<Map<String, Object>>> cachedTissues = new HashMap<>();
    private Map<String, SoftReference<Map<String, Object>>> cachedSamples = new HashMap<>();
    private Map<String, SoftReference<Map<String, Object>>> cachedEvents = new HashMap<>();

    @Override
    public List<String> getTissueIdsMatchingQuery(Query query, List<FimsProject> projectsToMatch, boolean allowEmptyQuery) throws ConnectionException {
        Invocation.Builder searchRequest = client.getQueryTarget().path("records/Tissue/json")
                .queryParam("projectId", 3)
                .queryParam("entity", "Tissue")
                .queryParam("q", "_select_:[Tissue,Sample,Event]")
                .request();
        Response response = searchRequest.get();
        try {
            List<String> ids = new ArrayList<>();
            SearchResult result = geomeFIMSClient.getRestServiceResult(SearchResult.class, response);

            cacheMapById(getTissueSampleDocumentField().getName(), result.content.Tissue, cachedTissues, ids);
            cacheMapById("materialSampleID", result.content.Sample, cachedSamples, ids);
            cacheMapById(EVENT_ID, result.content.Event, cachedEvents, ids);

            return ids;
        } catch (DatabaseServiceException e) {
            throw new ConnectionException(e);
        }
    }

    private void cacheMapById(String idField, List<Map<String, Object>> listToCache, Map<String, SoftReference<Map<String, Object>>> cache, List<String> idList) {
        for (Map<String, Object> tissueMap : listToCache) {

            String id = tissueMap.get(idField).toString();
            cache.put(id, new SoftReference<>(tissueMap));
            if(idList != null) {
                idList.add(id);
            }
        }
    }

    @Override
    protected List<FimsSample> _retrieveSamplesForTissueIds(List<String> tissueIds, RetrieveCallback callback) throws ConnectionException {
        Map<String, DocumentField> attributesByName = new HashMap<>();
        allAttributes.values().forEach(f -> attributesByName.put(f.getName(), f));

        List<FimsSample> samples = new ArrayList<>();

        for (String tissueId : tissueIds) {
            Map<String, Object> valuesForTissue = null;
            SoftReference<Map<String, Object>> ref = cachedTissues.get(tissueId);
            if(ref != null) {
                valuesForTissue = ref.get();
            }

            if(valuesForTissue != null) {
                // Need to convert map from name -> value to uri -> value because that's what Geneious expects
                Map<String, Object> valuesByCode = new HashMap<>();

                BiConsumer<String, Object> storeByCode = (key, value) -> {
                    DocumentField documentField = attributesByName.get(key);
                    if (documentField != null) {
                        valuesByCode.put(documentField.getCode(), value);
                    } else {
                        System.out.println("missing DocumentField for " + key);
                    }
                };
                valuesForTissue.forEach(storeByCode);
                Object sampleId = valuesForTissue.get(allAttributes.get(SAMPLE_URN).getName());
                String eventId = null;
                if(sampleId != null) {
                    SoftReference<Map<String, Object>> sampleInfo = cachedSamples.get(sampleId.toString());
                    if (sampleInfo != null) {
                        Map<String, Object> sampleValues = sampleInfo.get();
                        if (sampleValues != null) {
                            sampleValues.forEach(storeByCode);
                            eventId = sampleValues.get(EVENT_ID).toString();
                        }
                    }
                }

                SoftReference<Map<String, Object>> eventInfo = cachedEvents.get(eventId);
                if(eventInfo != null) {
                    Map<String, Object> eventValues = eventInfo.get();
                    if(eventValues != null) {
                        eventValues.forEach(storeByCode);
                    }
                }


                TissueDocument sample = new TissueDocument(
                        new TableFimsSample(
                                getCollectionAttributes(),
                                getTaxonomyAttributes(), valuesByCode,
                                TISSUE_URN,
                                SAMPLE_URN)
                );
                samples.add(sample);
                if(callback != null) {
                    callback.add(sample, Collections.emptyMap());
                }
            }
        }

        return samples;
    }

    @Override
    public int getTotalNumberOfSamples() throws ConnectionException {
        return 0;
    }

    @Override
    public DocumentField getPlateDocumentField() {
        return allAttributes.get("urn:plateID");
    }

    @Override
    public DocumentField getWellDocumentField() {
        return allAttributes.get("urn:wellID");
    }

    @Override
    public boolean storesPlateAndWellInformation() {
        return true;
    }

    @Override
    public boolean hasPhotos() {
        return false;
    }
}
