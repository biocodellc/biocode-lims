package com.biomatters.plugins.biocode.labbench.fims.geome;

import com.biomatters.geneious.publicapi.databaseservice.*;
import com.biomatters.geneious.publicapi.documents.Condition;
import com.biomatters.geneious.publicapi.documents.DocumentField;
import com.biomatters.geneious.publicapi.plugin.Options;
import com.biomatters.geneious.publicapi.utilities.StringUtilities;
import com.biomatters.plugins.biocode.labbench.ConnectionException;
import com.biomatters.plugins.biocode.labbench.FimsSample;
import com.biomatters.plugins.biocode.labbench.PasswordOptions;
import com.biomatters.plugins.biocode.labbench.TissueDocument;
import com.biomatters.plugins.biocode.labbench.connection.Connection;
import com.biomatters.plugins.biocode.labbench.fims.FIMSConnection;
import com.biomatters.plugins.biocode.labbench.fims.FimsProject;
import com.biomatters.plugins.biocode.labbench.fims.TableFimsSample;


import javax.ws.rs.client.Entity;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.core.Form;
import javax.ws.rs.core.MediaType;
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
            if (projects.isEmpty()) {
                throw new ConnectionException("You don't have access to any projects");
            }

            for (Project project : projects) {
                Invocation.Builder configRequest = client.getQueryTarget().path("projects").path(String.valueOf(project.id)).path("config").request();

                Response response = configRequest.get();
                ProjectConfig config = geomeFIMSClient.getRestServiceResult(ProjectConfig.class, response);

                List<String> taxonomyFieldNames = Arrays.asList("urn:kingdom", "urn:phylum", "urn:subphylum", "urn:superClass", "urn:class", "urn:infraClass", "urn:subclass", "urn:superOrder", "urn:order", "urn:infraOrder", "urn:suborder", "urn:superFamily", "urn:family", "urn:subfamily", "urn:genus", "urn:subGenus", "urn:tribe", "urn:subTribe", "urn:species", "urn:subSpecies");

                for (ProjectConfig.Entity entity : config.entities) {
                    if (!Arrays.asList("Tissue", "Event", "Sample").contains(entity.conceptAlias)) {
                        continue;
                    }

                    for (Project.Field attribute : entity.attributes) {
                        allAttributes.put(attribute.uri, attribute.asDocumentField());
                        if (taxonomyFieldNames.contains(attribute.uri)) {
                            taxonomyAttributes.put(attribute.uri, attribute.asDocumentField());
                        } else {
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

    private static final DocumentField PROJECT_FIELD = new DocumentField("Project", "", "geomeProject", String.class, false, false);

    @Override
    protected List<DocumentField> _getCollectionAttributes() {
        List<DocumentField> result = new ArrayList<>(collectionAttributes.values());
        result.removeAll(_getTaxonomyAttributes());
        result.add(PROJECT_FIELD);
        return result;
    }

    @Override
    protected List<DocumentField> _getTaxonomyAttributes() {
        return new ArrayList<>(taxonomyAttributes.values());
    }

    @Override
    protected List<DocumentField> _getSearchAttributes() {
        List<DocumentField> result = new ArrayList<>();
        result.addAll(_getTaxonomyAttributes());
        result.addAll(_getCollectionAttributes());
        return result;
    }

    @Override
    public List<String> getTissueIdsMatchingQuery(Query query, List<FimsProject> projectsToMatch) throws ConnectionException {
        return getTissueIdsMatchingQuery(query, projectsToMatch, true);
    }

    @Override
    public List<String> getTissueIdsMatchingQuery(Query query, List<FimsProject> projectsToMatch, boolean allowEmptyQuery) throws ConnectionException {
        String queryString = buildQuery(query);

        Project project = getProjectFromQuery(query);
        List<Project> projectsToSearch = new ArrayList<>();
        if (project == null) {
            projectsToSearch.addAll(projects);
        } else {
            projectsToSearch.add(project);
        }

        List<String> ids = new ArrayList<>();
        for (Project currentProject : projectsToSearch) {
            Invocation.Builder searchRequest = client.getQueryTarget().path("records/Tissue/json")
                    .queryParam("projectId", currentProject.id)
                    .queryParam("entity", "Tissue")
                    .queryParam("limit", 10000)
                    .queryParam("q", queryString)
                    .request();
            Response response = searchRequest.get();
            try {
                SearchResult result = geomeFIMSClient.getRestServiceResult(SearchResult.class, response);
                for (Map<String, Object> tissue : result.content.Tissue) {
                    ids.add(tissue.get(getTissueSampleDocumentField().getName()).toString());
                }


            } catch (DatabaseServiceException e) {
                throw new ConnectionException(e);
            }
        }

        return ids;
    }

    private Project getProjectFromQuery(Query query) {
        // todo
        if (query instanceof AdvancedSearchQueryTerm) {
            Project project = getProjectFromSearchTerm((AdvancedSearchQueryTerm) query);
            if (project != null) return project;
        }

        if (query instanceof CompoundSearchQuery) {

        }


        return null;
    }

    private Project getProjectFromSearchTerm(AdvancedSearchQueryTerm query) {
        AdvancedSearchQueryTerm term = query;
        if (PROJECT_FIELD.getCode().equals(term.getField().getCode())) {
            for (Project project : projects) {
                if (project.title.equals(term.getValues()[0])) {
                    return project;
                }
            }
        }
        return null;
    }

    private String buildQuery(Query query) {
        if (query instanceof BasicSearchQuery) {
            return ((BasicSearchQuery) query).getSearchText();
        } else if (query instanceof CompoundSearchQuery) {
            CompoundSearchQuery cquery = (CompoundSearchQuery) query;
            CompoundSearchQuery.Operator operator = cquery.getOperator();
            String join = "";
            switch (operator) {
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
        } else if (query instanceof AdvancedSearchQueryTerm) {
            return getQueryExpression((AdvancedSearchQueryTerm) query);
        } else {
            throw new RuntimeException("Unrecognised query type: " + query.getClass());
        }

    }


    public static String getQueryExpression(AdvancedSearchQueryTerm query) {
        String join = "";
        String append = "\"";
        String prepend = "\"";
        String beforeQuery = "";
        switch (query.getCondition()) {
            case EQUAL:
                join = "::";
                break;
            case APPROXIMATELY_EQUAL:
                join = ":";
                prepend = "\"%";
                append = "%\"";
                break;
            case BEGINS_WITH:
                join = "::";
                append = "%\"";
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

    private Map<String, Map<String, Object>> mapResults(String idField, List<Map<String, Object>> listToMap) {
        Map<String, Map<String, Object>> result = new HashMap<>();
        for (Map<String, Object> tissueMap : listToMap) {
            String id = tissueMap.get(idField).toString();
            result.put(id, tissueMap);
        }
        return result;
    }

    @Override
    protected List<FimsSample> _retrieveSamplesForTissueIds(List<String> tissueIds, RetrieveCallback callback) throws ConnectionException {
        try {
            List<FimsSample> samples = new ArrayList<>();
            for (Project currentProject : projects) {

                Map<String, DocumentField> attributesByName = new HashMap<>();
                allAttributes.values().forEach(f -> attributesByName.put(f.getName(), f));

                Query[] tissueQueries = new Query[tissueIds.size()];
                for (int i = 0; i < tissueIds.size(); i++) {
                    tissueQueries[i] = Query.Factory.createFieldQuery(getTissueSampleDocumentField(), Condition.EQUAL, tissueIds.get(i));
                }
                Query tissueQuery = Query.Factory.createOrQuery(tissueQueries, Collections.emptyMap());

                String queryString = buildQuery(tissueQuery);

                Invocation.Builder searchRequest = client.getQueryTarget().path("records/Tissue/json")
                        .queryParam("projectId", currentProject.id)
                        .queryParam("entity", "Tissue")
                        .queryParam("limit", 10000)
                        //                .queryParam("q", queryString + "_select_:[Tissue,Sample,Event]")
                        .request();


                Form formToPost = new Form()
                        .param("query", queryString + "_select_:[Tissue,Sample,Event]");

                Response response = searchRequest.post(
                        Entity.entity(formToPost, MediaType.APPLICATION_FORM_URLENCODED_TYPE));

                SearchResult result = geomeFIMSClient.getRestServiceResult(SearchResult.class, response);



                Map<String, Map<String, Object>> mappedTissues = mapResults(getTissueSampleDocumentField().getName(), result.content.Tissue);
                Map<String, Map<String, Object>> mappedSamples = mapResults("materialSampleID", result.content.Sample);
                Map<String, Map<String, Object>> mappedEvents = mapResults(EVENT_ID, result.content.Event);


                for (String tissueId : tissueIds) {
                    Map<String, Object> valuesForTissue = mappedTissues.get(tissueId);

                    if (valuesForTissue != null) {
                        // Need to convert map from name -> value to uri -> value because that's what Geneious expects
                        Map<String, Object> valuesByCode = new HashMap<>();

                        BiConsumer<String, Object> storeByCode = (key, value) -> {
                            if (value == null || value instanceof String && value.toString().trim().length() == 0) {
                                return;
                            }

                            DocumentField documentField = attributesByName.get(key);
                            if (documentField != null) {

                                Object valueToStore;
                                try {
                                    if (Boolean.class == documentField.getValueType()) {
                                        valueToStore = Boolean.valueOf(value.toString());
                                    } else if (Double.class == documentField.getValueType()) {
                                        valueToStore = Double.valueOf(value.toString());
                                    } else if (Integer.class == documentField.getValueType()) {
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
                        if (sampleId != null) {
                            Map<String, Object> sampleValues = mappedSamples.get(sampleId.toString());

                            if (sampleValues != null) {
                                sampleValues.forEach(storeByCode);
                                eventId = sampleValues.get(EVENT_ID).toString();
                            } else {
                                throw new ConnectionException("Expected to find sample " + sampleId + " but it was not returned by the server");
                            }

                        }

                        Map<String, Object> eventValues = mappedEvents.get(eventId);
                        if (eventValues != null) {
                            eventValues.forEach(storeByCode);
                        } else {
                            throw new ConnectionException("Expected to find event " + eventId + " but it was not returned by the server");
                        }


                        TissueDocument sample = new TissueDocument(
                                new TableFimsSample(
                                        getCollectionAttributes(),
                                        getTaxonomyAttributes(), valuesByCode,
                                        TISSUE_URN,
                                        SAMPLE_URN)
                        );
                        samples.add(sample);
                        if (callback != null) {
                            callback.add(sample, Collections.emptyMap());
                        }
                    } else {
                        throw new ConnectionException("Expected to find tissue " + tissueId + " but it was not returned by the server");
                    }
                }
            }

            return samples;
        } catch (DatabaseServiceException e) {
            throw new ConnectionException(e);
        }
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
