package com.biomatters.plugins.biocode.labbench.fims.geome;

import com.biomatters.geneious.publicapi.databaseservice.*;
import com.biomatters.geneious.publicapi.documents.Condition;
import com.biomatters.geneious.publicapi.documents.DocumentField;
import com.biomatters.geneious.publicapi.documents.PluginDocument;
import com.biomatters.geneious.publicapi.plugin.Options;
import com.biomatters.geneious.publicapi.utilities.StringUtilities;
import com.biomatters.plugins.biocode.labbench.ConnectionException;
import com.biomatters.plugins.biocode.labbench.FimsSample;
import com.biomatters.plugins.biocode.labbench.PasswordOptions;
import com.biomatters.plugins.biocode.labbench.TissueDocument;
import com.biomatters.plugins.biocode.labbench.fims.FIMSConnection;
import com.biomatters.plugins.biocode.labbench.fims.FimsProject;
import com.biomatters.plugins.biocode.labbench.fims.TableFimsSample;
import com.google.api.client.repackaged.org.apache.commons.codec.binary.StringUtils;


import javax.ws.rs.client.Entity;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.core.Form;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.lang.ref.SoftReference;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

public class geomeFIMSConnection extends FIMSConnection {
    private static final String HOST = "api.geome-db.org";
    public static final String GEOME_URL = "https://" + HOST;
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
            String username = fimsOptions.getUserName();
            String password = fimsOptions.getPassword();
            client.login(username, password);
            projects = client.getProjects(fimsOptions.includePublicProjects());
            if (projects.isEmpty()) {
                throw new ConnectionException("You don't have access to any projects");
            }

            // for (Project project : projects) {
            // Invocation.Builder configRequest = client.getQueryTarget().path("projects").path(String.valueOf(project.id)).path("config").request();
            Invocation.Builder configRequest = client.getQueryTarget().path("network").path("config").request();

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

    private DocumentField getSampleDocumentField() {
        return allAttributes.get(SAMPLE_URN);
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
    private static final DocumentField GENBANK_COUNTRY_FIELD = new DocumentField("genbankCountry", "", "urn:genbankCountry", String.class, false, false);
    private static final DocumentField GENBANK_DATE_FIELD = new DocumentField("genbankDate", "", "urn:genbankDate", String.class, false, false);
    private static final DocumentField GENBANK_LATLNG_FIELD = new DocumentField("genbankLatLng", "", "urn:genbankLatLng", String.class, false, false);
    // NOTE: the Genbank submission docs indicate empty attributes should be titled "misssing", however, geome commonlyl
    // encodes this as "Unknown".  To maintain consistency with Geome, we set the BLANK_ATTRIBUTE to "Unknown"
    private static final String BLANK_ATTRIBUTE = "Unknown";


    @Override
    protected List<DocumentField> _getCollectionAttributes() {
        List<DocumentField> result = new ArrayList<>(collectionAttributes.values());
        result.removeAll(_getTaxonomyAttributes());
        result.add(PROJECT_FIELD);
        result.add(GENBANK_COUNTRY_FIELD);
        result.add(GENBANK_DATE_FIELD);
        result.add(GENBANK_LATLNG_FIELD);
        return result;
    }

    protected List<DocumentField> _getLimitedCollectionAttributesForSearch() {
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
        result.addAll(_getLimitedCollectionAttributesForSearch());
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

        List<String> tissueIds = new ArrayList<>();
        List<Integer> projectIds = new ArrayList<>();
        // collect project ids
        for (Project currentProject : projectsToSearch) {
            projectIds.add(currentProject.id);
        }
        if (!queryString.trim().equals("")) {
            queryString += " and";
        }
        queryString += " _projects_:" + projectIds;

        //System.out.println(projectIds);
        // _projects_:[1,2,11]
        Invocation.Builder searchRequest = client.getQueryTarget().path("records/Tissue/json")
//                .queryParam("_projects_:", projectIds)
                .queryParam("entity", "Tissue")
                .queryParam("limit", 100000)
                .queryParam("q", "_select_:[Event,Sample,Tissue] " + queryString)
                .request();
        Response response = searchRequest.get();
        try {
            SearchResult result = geomeFIMSClient.getRestServiceResult(SearchResult.class, response);

            for (Map<String, Object> tissue : result.content.Tissue) {
                String tissueID = tissue.get(getTissueSampleDocumentField().getName()).toString();
                //sampleCache.put(sampleId, new SoftReference<FimsSample>(sample));
                if (tissueID == null || tissueID.trim().length() == 0) {
                    continue;
                }
                tissueIds.add(tissueID);

            }

            transformQueryResults(tissueIds, result).forEach(s ->
                    sampleCache.put(s.getId(), new SoftReference<FimsSample>(s))
            );
        } catch (DatabaseServiceException e) {
            throw new ConnectionException(e);
        }


        return tissueIds;
    }

    private Project getProjectFromQuery(Query query) throws ConnectionException {
        if (query instanceof AdvancedSearchQueryTerm) {
            Project project = getProjectFromSearchTerm((AdvancedSearchQueryTerm) query);
            if (project != null) return project;
        }


        if (query instanceof CompoundSearchQuery) {
            // JBD: removing the restriction on OR queries from Geome... these DO work.
            // However, i'm not certain why this restriction was placed here in the first place
            //if (((CompoundSearchQuery) query).getOperator() != CompoundSearchQuery.Operator.AND) {
            //    throw new ConnectionException("OR queries with Project unsupported");
            //}
            for (Query childQuery : ((CompoundSearchQuery) query).getChildren()) {
                if (childQuery instanceof AdvancedSearchQueryTerm) {
                    Project project = getProjectFromSearchTerm((AdvancedSearchQueryTerm) childQuery);
                    if (project != null) return project;
                }
            }
        }

        return null;
    }

    private Project getProjectFromSearchTerm(AdvancedSearchQueryTerm term) throws ConnectionException {
        if (PROJECT_FIELD.getCode().equals(term.getField().getCode())) {
            if (term.getCondition() != Condition.CONTAINS) {
                throw new ConnectionException("Only Project queries with Contains are supported");
            }
            for (Project project : projects) {
                // query the project title
                if (project.title.equals(term.getValues()[0])) {
                    return project;
                }

            }
        }
        return null;
        // if the project cannot be found, then return an error...
        //throw new ConnectionException("Project '" + term.getValues()[0] +"' cannot be found or is not a LIMS-enabled project. Please check the project code or title and try again.");

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
                if (childQuery instanceof AdvancedSearchQueryTerm &&
                        ((AdvancedSearchQueryTerm) childQuery).getField().getCode().equals(PROJECT_FIELD.getCode())) {
                    continue;
                }
                childQueries.add(buildQuery(childQuery));
            }
            return StringUtilities.join(join, childQueries);
        } else if (query instanceof AdvancedSearchQueryTerm) {
            AdvancedSearchQueryTerm aQuery = (AdvancedSearchQueryTerm) query;
            if (aQuery.getField().getCode().equals(PROJECT_FIELD.getCode())) {
                return "";
            }
            return getQueryExpression(aQuery);
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
                prepend = "\"%25";
                append = "%\"";
                break;
            case BEGINS_WITH:
                join = "::";
                append = "%\"";
                break;
            case ENDS_WITH:
                join = "::";
                prepend = "\"%25";
                break;
            case CONTAINS:
                join = "::";
                append = "%\"";
                prepend = "\"%25";
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
                prepend = "\"%25";
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
            /*
            return tissueIds.stream()
                .map(id -> sampleCache.get(id))
                .filter(s -> s != null)
                .map(s -> {
                    if (callback != null) {
                        callback.add((PluginDocument) s, Collections.emptyMap());
                    }
                    return s.get();
                })
                .collect(Collectors.toList());
                */
            List<Integer> projectIds = new ArrayList<>();
            // collect project ids
            for (Project currentProject : projects) {
                projectIds.add(currentProject.id);
            }

            for (Project currentProject : projects) {

                Query[] tissueQueries = new Query[tissueIds.size()];
                for (int i = 0; i < tissueIds.size(); i++) {
                    tissueQueries[i] = Query.Factory.createFieldQuery(getTissueSampleDocumentField(), Condition.EQUAL, tissueIds.get(i));
                }
                Query tissueQuery = Query.Factory.createOrQuery(tissueQueries, Collections.emptyMap());

                String queryString = buildQuery(tissueQuery);


                // POST Connection method... should work for large requests
                Invocation.Builder searchRequest = client.getQueryTarget().path("records/Tissue/json")
                        .queryParam("_projects_:", projectIds)
                        .queryParam("entity", "Tissue")
                        .queryParam("limit", 100000)
                        .request();

                Form formToPost = new Form()
                        .param("query", queryString + "_select_:[Tissue,Sample,Event]");

                Response response = searchRequest.post(
                        Entity.entity(formToPost, MediaType.APPLICATION_FORM_URLENCODED_TYPE));
                 /*
                // GET Connection method... fails on large requests
                Invocation.Builder searchRequest = client.getQueryTarget().path("records/Tissue/json")
                        .queryParam("_projects_:", projectIds)
                        .queryParam("entity", "Tissue")
                        .queryParam("limit", 100000)
                        .queryParam("q", queryString + "_select_:[Tissue,Sample,Event]")
                        .request();

                Response response = searchRequest.get();
                */
                SearchResult result = geomeFIMSClient.getRestServiceResult(SearchResult.class, response);

                List<FimsSample> samples = transformQueryResults(tissueIds, result);
                return samples;
            }

        } catch (DatabaseServiceException e) {
            throw new ConnectionException(e);
        }
        return null;
    }

    private List<FimsSample> transformQueryResults(List<String> tissueIds, SearchResult result) throws ConnectionException {
        List<FimsSample> samples = new ArrayList<>();

        allAttributes.put("genbankCountry", GENBANK_COUNTRY_FIELD);
        allAttributes.put("genbankDate", GENBANK_DATE_FIELD);
        allAttributes.put("genbankLatLng", GENBANK_LATLNG_FIELD);

        Map<String, DocumentField> attributesByName = new HashMap<>();
        allAttributes.values().forEach(f -> attributesByName.put(f.getName(), f));

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

                eventValues.put("genbankCountry", getGenbankCountryValue(
                        (String) eventValues.get(allAttributes.get("urn:country").getName()),
                        (String) eventValues.get(allAttributes.get("urn:locality").getName())
                ));

                eventValues.put("genbankLatLng", getGenbankLatLong(
                        (String) eventValues.get(allAttributes.get("urn:decimalLatitude").getName()),
                        (String) eventValues.get(allAttributes.get("urn:decimalLongitude").getName())
                ));

                eventValues.put("genbankDate", getGenbankCollectionDate(
                        (String) eventValues.get(allAttributes.get("urn:yearCollected").getName()),
                        (String) eventValues.get(allAttributes.get("urn:monthCollected").getName()),
                        (String) eventValues.get(allAttributes.get("urn:dayCollected").getName())
                ));

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

    /**
     * if both lat and long are present, return a string containing the abs(decimalDegree) + Compass Direction
     * <p>
     * ex.
     * <p>
     * lat = -8, long = 140 would return "8 S 140 W"
     */
    private String getGenbankLatLong(String latText, String lngText) {
        StringBuilder latLongSb = new StringBuilder();

        if (!latText.equals("") &&
                !lngText.equals("")) {

            try {
                Double lat = Double.parseDouble(latText);

                if (lat < 0) {
                    latLongSb.append(Math.abs(lat)).append(" S");
                } else {
                    latLongSb.append(lat).append(" N");
                }

                latLongSb.append(" ");

                Double lng = Double.parseDouble(lngText);

                if (lng < 0) {
                    latLongSb.append(Math.abs(lng)).append(" W");
                } else {
                    latLongSb.append(lng).append(" E");
                }
            } catch (NumberFormatException e) {
                latLongSb = new StringBuilder()
                        .append(latText)
                        .append(" ")
                        .append(lngText);
            }
        }

        if (latLongSb.toString().equals("")) {
            return BLANK_ATTRIBUTE;
        } else {
            return latLongSb.toString();
        }
    }

    /**
     * Format the genbank Country field
     *
     * @param country
     * @param locality
     *
     * @return
     */
    private String getGenbankCountryValue(String country, String locality) {
        String genbankCountryValue = "";
        // Assign the country portion of the Genbank country field
        if (country != null) {
            genbankCountryValue = country.trim();
        }
        // Assign the locality portion of the Genbank country field
        if (locality != null &&
                country != null &&
                !locality.trim().equalsIgnoreCase(country.trim()) &&
                !locality.trim().equals("")) {
            // In some cases, the country name has already been mapped into the locality
            // causing an unusual cascade of country names in the genbankCountry Field.
            // This conditional statement attempts to catch and fix this situation.
            if (locality.trim().startsWith(country.trim()) &&
                    !locality.trim().equalsIgnoreCase(country.trim())) {
                // remove the country name
                String tempLocalityField = locality.trim().replaceFirst(country.trim(), "");
                // remove colons since they will be confusing in this context
                tempLocalityField = tempLocalityField.replace(":","");
                genbankCountryValue += ":" + tempLocalityField;
            } else {
                genbankCountryValue += ":" + locality.trim();
            }
        }
        // Return the blank attribute if we do not have any content
        if (genbankCountryValue.equals("")) {
            return BLANK_ATTRIBUTE;
        } else {
            return genbankCountryValue;
        }
    }

    /**
     * Format the genbank collection date field
     *
     * @param yearCollected
     * @param monthCollected
     * @param dayCollected
     *
     * @return
     */
    private String getGenbankCollectionDate(String yearCollected, String monthCollected, String dayCollected) {
        StringBuilder collectionDate = new StringBuilder();

        collectionDate.append(yearCollected);


        if (monthCollected != null && !monthCollected.equals("")) {

            collectionDate.append("-");
            collectionDate.append(monthCollected);

            if (dayCollected != null && !dayCollected.equals("")) {
                collectionDate.append("-");
                collectionDate.append(dayCollected);
            }
        }

        if (collectionDate.toString().equals("")) {
            return BLANK_ATTRIBUTE;
        } else {
            return collectionDate.toString();
        }
    }

}
