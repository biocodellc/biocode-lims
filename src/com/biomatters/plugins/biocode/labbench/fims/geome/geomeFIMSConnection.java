package com.biomatters.plugins.biocode.labbench.fims.geome;

import com.biomatters.geneious.publicapi.databaseservice.*;
import com.biomatters.geneious.publicapi.documents.DocumentField;
import com.biomatters.geneious.publicapi.plugin.Options;
import com.biomatters.geneious.publicapi.utilities.StringUtilities;
import com.biomatters.plugins.biocode.labbench.ConnectionException;
import com.biomatters.plugins.biocode.labbench.FimsSample;
import com.biomatters.plugins.biocode.labbench.PasswordOptions;
import com.biomatters.plugins.biocode.labbench.TissueDocument;
import com.biomatters.plugins.biocode.labbench.fims.FIMSConnection;
import com.biomatters.plugins.biocode.labbench.fims.FimsProject;
import com.biomatters.plugins.biocode.labbench.fims.TableFimsSample;


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
        return new geomeFIMSConnectionOptions();
    }

    private List<Project> projects;

    @Override
    public void _connect(Options options) throws ConnectionException {
        if (!(options instanceof geomeFIMSConnectionOptions)) {
            throw new IllegalArgumentException("_connect() must be called with Options obtained from calling _getConnectionOptions()");
        }
        geomeFIMSConnectionOptions fimsOptions = (geomeFIMSConnectionOptions) options;
        client = new geomeFIMSClient(fimsOptions.getHost(), requestTimeoutInSeconds);
        try {
            client.login(fimsOptions.getUserName(), fimsOptions.getPassword());
            projects = client.getProjects(fimsOptions.includePublicProjects());
            if(projects.isEmpty()) {
                throw new ConnectionException("You don't have access to any projects");
            }

            for (Project project : projects) {
                Invocation.Builder configRequest = client.getQueryTarget().path("projects").path(String.valueOf(project.id)).path("config").request();

                Response response = configRequest.get();
                ProjectConfig config = geomeFIMSClient.getRestServiceResult(ProjectConfig.class, response);

                List<String> taxonomyFieldNames = Arrays.asList("urn:kingdom", "urn:phylum", "urn:subphylum", "urn:superClass", "urn:class", "urn:infraClass", "urn:subclass", "urn:superOrder", "urn:order", "urn:infraOrder", "urn:suborder", "urn:superFamily", "urn:family", "urn:subfamily", "urn:genus", "urn:subGenus", "urn:tribe", "urn:subTribe", "urn:species", "urn:subSpecies");

                for (ProjectConfig.Entity entity : config.entities) {
                    if(!Arrays.asList("Tissue", "Event", "Sample").contains(entity.conceptAlias)) {
                        continue;
                    }

                    for (Project.Field attribute : entity.attributes) {
                        allAttributes.put(attribute.uri, attribute.asDocumentField());
                        if(taxonomyFieldNames.contains(attribute.uri)) {
                            taxonomyAttributes.put(attribute.uri, attribute.asDocumentField());
                        }
                        else {
                            collectionAttributes.put(attribute.uri, attribute.asDocumentField());
                        }

                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw new ConnectionException("Unable to connect to GeOMe: " + e.getMessage());
        }
    }

    private Map<String, DocumentField> allAttributes = new LinkedHashMap<>();
    private Map<String, DocumentField> taxonomyAttributes = new LinkedHashMap<>();
    private Map<String, DocumentField> collectionAttributes = new LinkedHashMap<>();

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
        return new ArrayList<>(collectionAttributes.values());
    }

    @Override
    protected List<DocumentField> _getTaxonomyAttributes() {
        return new ArrayList<>(taxonomyAttributes.values());
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
        String queryString = buildQuery(query) + " _select_:[Tissue,Sample,Event]";
        System.out.println(queryString);
        Invocation.Builder searchRequest = client.getQueryTarget().path("records/Tissue/json")
                .queryParam("projectId", 3)
                .queryParam("entity", "Tissue")
                .queryParam("q", queryString)
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

    private String buildQuery(Query query) {
        if(query instanceof BasicSearchQuery) {
            return ((BasicSearchQuery) query).getSearchText();
        }
        else if(query instanceof CompoundSearchQuery) {
            CompoundSearchQuery cquery = (CompoundSearchQuery)query;
            CompoundSearchQuery.Operator operator = cquery.getOperator();
            String join = "";
            switch(operator) {
                case OR:
                    join = " OR ";
                    break;
                case AND:
                    join = " AND ";
                    break;
            }
            List<String> childQueries = new ArrayList<>();
            for (Query childQuery : cquery.getChildren()) {
                childQueries.add(buildQuery(childQuery));
            }
            return StringUtilities.join(join, childQueries);
        }
        else if(query instanceof AdvancedSearchQueryTerm) {
            return getQueryExpression((AdvancedSearchQueryTerm)query);
        }
        else {
            throw new RuntimeException("Unrecognised query type: "+query.getClass());
        }

    }



    public static String getQueryExpression(AdvancedSearchQueryTerm query) {
        String join = "";
        String append = "\"";
        String prepend = "\"";
        String beforeQuery = "";
        switch(query.getCondition()) {
            case EQUAL:
                join = "::";
                break;
            case APPROXIMATELY_EQUAL:
                join = ":";
                prepend="\"%";
                append = "%\"";
                break;
            case BEGINS_WITH:
                join = "::";
                append="%\"";
                break;
            case ENDS_WITH:
                join = "::";
                prepend = "\"%";
                break;
            case CONTAINS:
                join = "::";
                append = "%\"";
                prepend = "\"%";
                break;
            case GREATER_THAN:
            case DATE_AFTER:
                join = ">";
                prepend = "";
                append = "";
                break;
            case GREATER_THAN_OR_EQUAL_TO:
            case DATE_AFTER_OR_ON:
                join = ">=";
                prepend = "";
                append = "";
                break;
            case LESS_THAN:
            case DATE_BEFORE:
                join = "<";
                prepend = "";
                append = "";
                break;
            case LESS_THAN_OR_EQUAL_TO:
            case DATE_BEFORE_OR_ON:
                join = "<=";
                prepend = "";
                append = "";
                break;
            case NOT_CONTAINS:
                join = "::";
                append = "%\"";
                prepend = "\"%";
                beforeQuery = "NOT ";
                break;
            case NOT_EQUAL:
                join = "::";
                beforeQuery = "NOT ";
                break;
            case IN_RANGE:
                //todo: this is a special case
                return query.getField().getName() + ":[" + query.getValues()[0] + " TO " + query.getValues()[1] + "]";
         }
        return beforeQuery + query.getField().getName() + join + prepend + query.getValues()[0] + append;
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

        List<String> uncachedTissueIds = new ArrayList<>();

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
                    if(value == null || value instanceof String && value.toString().trim().length() == 0) {
                        return;
                    }

                    DocumentField documentField = attributesByName.get(key);
                    if (documentField != null) {

                        Object valueToStore;
                        try {
                            if(Boolean.class == documentField.getValueType()) {
                                valueToStore = Boolean.valueOf(value.toString());
                            } else if(Double.class == documentField.getValueType()) {
                                valueToStore = Double.valueOf(value.toString());
                            } else if(Integer.class == documentField.getValueType()) {
                                valueToStore = Integer.valueOf(value.toString());
                            } else {
                                valueToStore = value.toString();
                            }
                            valuesByCode.put(documentField.getCode(), valueToStore);
                        } catch (NumberFormatException e) {
                            System.out.println("Invalid value for " + documentField.getValueType() + " was " + value);
                        }
                    } else {
                        // todo bring in projectId, bcid, expeditionCode from other entities
//                        System.out.println("missing DocumentField for " + key);
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
                    else {
                        uncachedTissueIds.add(tissueId);
                    }
                }
                else {
                    uncachedTissueIds.add(tissueId);
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
