package com.biomatters.plugins.biocode.labbench.fims.geome;

import com.biomatters.geneious.publicapi.databaseservice.*;
import com.biomatters.geneious.publicapi.documents.Condition;
import com.biomatters.geneious.publicapi.documents.DocumentField;
import com.biomatters.geneious.publicapi.documents.PluginDocument;
import com.biomatters.geneious.publicapi.plugin.Options;
import com.biomatters.geneious.publicapi.utilities.StringUtilities;
import com.biomatters.plugins.biocode.BiocodeUtilities;
import com.biomatters.plugins.biocode.labbench.*;
import com.biomatters.plugins.biocode.labbench.fims.FIMSConnection;
import com.biomatters.plugins.biocode.labbench.fims.FimsProject;
import com.biomatters.plugins.biocode.labbench.fims.TableFimsSample;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.client.repackaged.org.apache.commons.codec.binary.StringUtils;
import okhttp3.*;

import java.io.IOException;
import java.lang.ref.SoftReference;
import java.text.Normalizer;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

public class geomeFIMSConnection extends FIMSConnection {
    private static final String HOST = "api.geome-db.org";
    public static final String GEOME_URL = "https://" + HOST;
    private geomeFIMSClient client;
    private String credentials;

    @Override
    public String getLabel() {
        return "GEOME FIMS";
    }

    @Override
    public String getName() {
        return "GEOME FIMS";
    }

    @Override
    public String getDescription() {
        return "Connection to GEOME at https://api.geome-db.org/";
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
            credentials = Credentials.basic(username, password);

            client.login(username, password);
            projects = client.getProjects(fimsOptions.includePublicProjects());
            if (projects == null || projects.isEmpty()) {
                throw new ConnectionException("You don't have access to any projects");
            }

            // Defensive sanitize so later logic never sees null entries
            projects = projects.stream()
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

            // Build /network/config request
            HttpUrl.Builder urlBuilder = client.getQueryTarget()
                    .newBuilder()
                    .addPathSegment("network")
                    .addPathSegment("config");

            if (client.access_token.getAccess_token() != null &&
                !client.access_token.getAccess_token().isEmpty()) {
                urlBuilder.addQueryParameter("access_token", client.access_token.getAccess_token());
            }

            HttpUrl url = urlBuilder.build();
            Request configRequest = new Request.Builder().url(url).get().build();
            Response response = client.client.newCall(configRequest).execute();
            ProjectConfig config = geomeFIMSClient.getRestServiceResult(ProjectConfig.class, response);

            List<String> taxonomyFieldNames = Arrays.asList("urn:kingdom", "urn:phylum", "urn:subphylum", "urn:superClass", "urn:class", "urn:infraClass", "urn:subclass", "urn:superOrder", "urn:order", "urn:infraOrder", "urn:suborder", "urn:superFamily", "urn:family", "urn:subfamily", "urn:genus", "urn:subGenus", "urn:tribe", "urn:subTribe", "urn:species", "urn:subSpecies");

            String[] arrayFields = {
                    "urn:eventID", "urn:principalInvestigator", "urn:samplingProtocol", "urn:sampleCollectionDevicee",
                    "urn:recordedBy", "urn:continentOcean", "urn:country", "urn:county", "urn:dayCollected",
                    "urn:decimalLatitude", "urn:decimalLongitude", "urn:enteredBy", "urn:habitat", "urn:environmentalMedium",
                    "urn:island", "urn:locality", "urn:maximumDepthInMeters", "urn:maximumDistanceAboveSurfaceInMeters",
                    "urn:coordinateUncertaintyInMeters", "urn:microHabitat", "urn:minimumDepthInMeters",
                    "urn:minimumDistanceAboveSurfaceInMeters", "urn:monthCollected", "urn:permitInformation",
                    "urn:eventRemarks", "urn:stateProvince", "urn:taxTeam", "urn:yearCollected",
                    "urn:fixative", "urn:sampleOwnerInstitutionCode", "urn:collectionCode", "urn:preparations",
                    "urn:preservative", "urn:relaxant", "urn:license", "urn:EnteredBy", "urn:catalogNumber",
                    "urn:boldBIN", "urn:fieldNumber", "urn:genbankSpecimenVoucher", "urn:otherCatalogNumbers",
                    "urn:materialSampleID", "urn:occurrenceID", "urn:subProject", "urn:subSubProject",
                    "urn:boldProcessID", "urn:voucherURI", "urn:establishmentMeans", "urn:vernacularName",
                    "urn:dayIdentified", "urn:basisOfRecord", "urn:family", "urn:genus", "urn:identifiedBy",
                    "urn:class", "urn:infraClass", "urn:infraOrder", "urn:kingdom", "urn:lifeStage", "urn:taxonRank",
                    "urn:order", "urn:phylum", "urn:scientificName", "urn:sex", "urn:species", "urn:subclass",
                    "urn:subfamily", "urn:subGenus", "urn:suborder", "urn:subphylum", "urn:subSpecies",
                    "urn:subTribe", "urn:superClass", "urn:superFamily", "urn:superOrder", "urn:tribe",
                    "urn:wormsID", "urn:yearIdentified", "urn:tissueID", "urn:geneticTissueType",
                    "urn:plateID", "urn:wellID", "urn:tissueInstitution", "urn:tissueOtherCatalogNumbers",
                    "urn:tissuePreservative", "urn:associatedSequences", "urn:biosampleAccession", "urn:voucherCatalogNumber", "urn:tissueStorageID"};
            List<String> allFieldNames = Arrays.asList(arrayFields);

            for (ProjectConfig.Entity entity : config.entities) {
                if (!Arrays.asList("Tissue", "Event", "Sample").contains(entity.conceptAlias)) {
                    continue;
                }
                for (Project.Field attribute : entity.attributes) {
                    boolean include = allFieldNames.stream().anyMatch(x -> x.contains(attribute.uri));
                    if (include) {
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
            throw new ConnectionException("Unable to retrieve projects from GEOME.  This may be due either to " +
                    "an invalid username/password combination or the user has not opted to retrieve public projects " +
                    "and does not have access to any private projects  " + e.getMessage());
        }
    }

    private Map<String, DocumentField> allAttributes = new LinkedHashMap<>();
    private Map<String, DocumentField> taxonomyAttributes = new LinkedHashMap<>();
    private Map<String, DocumentField> collectionAttributes = new LinkedHashMap<>();

    @Override
    public void disconnect() {
        // no-op
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
    private static final String BLANK_ATTRIBUTE = "Unknown";
    private static final DocumentField EXPEDITION_CODE_FIELD =
            new DocumentField("expeditionCode", "", "urn:expeditionCode", String.class, false, false);

    private static final DocumentField PROJECT_ID_FIELD =
            new DocumentField("projectId", "", "urn:projectId", String.class, false, false);

    @Override
    protected List<DocumentField> _getCollectionAttributes() {
        List<DocumentField> result = new ArrayList<>(collectionAttributes.values());
        result.removeAll(_getTaxonomyAttributes());
        result.add(PROJECT_FIELD);
        result.add(GENBANK_COUNTRY_FIELD);
        result.add(GENBANK_DATE_FIELD);
        result.add(GENBANK_LATLNG_FIELD);
        result.add(EXPEDITION_CODE_FIELD);
        result.add(PROJECT_ID_FIELD);
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

    // ===== NEW: robust normalizer (fold diacritics, keep alnum only) =====
    private static String normalize(String s) {
        if (s == null) return "";
        String folded = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return folded.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    // ===== NEW: safe view of projects list (filters nulls only) =====
    private List<Project> projectsSafe() {
        if (projects == null) return Collections.emptyList();
        List<Project> safe = new ArrayList<>(projects.size());
        for (Project p : projects) {
            if (p != null) safe.add(p);
        }
        return safe;
    }

    // ===== NEW: collect ALL matching projects for EQUAL/CONTAINS =====
    private List<Project> getProjectsFromQuery(Query query) throws ConnectionException {
        LinkedHashSet<Integer> equalIds = new LinkedHashSet<>();
        LinkedHashSet<Integer> containsIds = new LinkedHashSet<>();
        collectProjectTerms(query, equalIds, containsIds);

        if (!equalIds.isEmpty()) {
            List<Project> out = new ArrayList<>();
            for (Project p : projectsSafe()) if (equalIds.contains(p.id)) out.add(p);
            return out;
        }
        if (!containsIds.isEmpty()) {
            List<Project> out = new ArrayList<>();
            for (Project p : projectsSafe()) if (containsIds.contains(p.id)) out.add(p);
            return out;
        }
        return null; // no Project term → search all
    }

    private void collectProjectTerms(Query q,
                                     Set<Integer> equalIds,
                                     Set<Integer> containsIds) throws ConnectionException {
        if (q instanceof AdvancedSearchQueryTerm) {
            AdvancedSearchQueryTerm t = (AdvancedSearchQueryTerm) q;
            if (!PROJECT_FIELD.getCode().equals(t.getField().getCode())) return;

            String raw = (String) t.getValues()[0];
            String needle = normalize(raw);

            if (t.getCondition() == Condition.EQUAL) {
                boolean any = false;
                for (Project p : projectsSafe()) {
                    if (normalize(p.title).equals(needle)) {
                        equalIds.add(p.id);
                        any = true;
                    }
                }
                if (!any) throw new ConnectionException("Project '" + raw + "' not found.");
            } else if (t.getCondition() == Condition.CONTAINS) {
                boolean any = false;
                for (Project p : projectsSafe()) {
                    if (normalize(p.title).contains(needle)) {
                        containsIds.add(p.id);
                        any = true;
                    }
                }
                if (!any) throw new ConnectionException("No projects containing '" + raw + "'.");
            } else {
                throw new ConnectionException("Only Project queries with Contains or Equal are supported");
            }

        } else if (q instanceof CompoundSearchQuery) {
            for (Query child : ((CompoundSearchQuery) q).getChildren()) {
                collectProjectTerms(child, equalIds, containsIds);
            }
        }
    }

    @Override
    public List<String> getTissueIdsMatchingQuery(Query query, List<FimsProject> projectsToMatch) throws ConnectionException {
        return getTissueIdsMatchingQuery(query, projectsToMatch, true);
    }

    @Override
    public List<String> getTissueIdsMatchingQuery(Query query, List<FimsProject> projectsToMatch, boolean allowEmptyQuery) throws ConnectionException {
        String queryString = buildQuery(query);

        // Use ALL matching projects for Project terms (EQUAL/CONTAINS)
        List<Project> projectsToSearch = getProjectsFromQuery(query);
        if (projectsToSearch == null || projectsToSearch.isEmpty()) {
            projectsToSearch = projectsSafe();
        }

        List<String> tissueIds = new ArrayList<>();
        List<Integer> projectIds = new ArrayList<>();
        for (Project currentProject : projectsToSearch) {
            projectIds.add(currentProject.id);
        }

        if (!queryString.trim().equals("")) {
            queryString += " and";
        }
        queryString += " _projects_:" + projectIds;

        HttpUrl.Builder urlBuilder = client.getQueryTarget()
                .newBuilder()
                .addPathSegment("records")
                .addPathSegment("Tissue")
                .addPathSegment("json")
                .addQueryParameter("entity", "Tissue")
                .addQueryParameter("limit", "100000")
                .addQueryParameter("includeEmptyProperties", "false")
                .addQueryParameter("q", "_select_:[Event,Sample,Tissue] " + queryString);

        if (client.access_token.getAccess_token() != null &&
            !client.access_token.getAccess_token().isEmpty()) {
            urlBuilder.addQueryParameter("access_token", client.access_token.getAccess_token());
        }

        HttpUrl url = urlBuilder.build();
        Request searchRequest = new Request.Builder().url(url).get().build();

        Response response;
        try {
            response = client.client.newCall(searchRequest).execute();
        } catch (IOException e) {
            throw new ConnectionException(e);
        }

        try {
            SearchResult result = geomeFIMSClient.getRestServiceResult(SearchResult.class, response);

            for (Map<String, Object> tissue : result.content.Tissue) {
                Object tid = tissue.get(getTissueSampleDocumentField().getName());
                if (tid == null) continue;
                String tissueID = tid.toString();
                if (tissueID.trim().isEmpty()) continue;
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

    private String buildQuery(Query query) {
        if (query instanceof BasicSearchQuery) {
            return ((BasicSearchQuery) query).getSearchText();
        } else if (query instanceof CompoundSearchQuery) {
            CompoundSearchQuery cquery = (CompoundSearchQuery) query;
            CompoundSearchQuery.Operator operator = cquery.getOperator();
            String join = "";
            switch (operator) {
                case OR:  join = " OR ";  break;
                case AND: join = " AND "; break;
            }
            List<String> childQueries = new ArrayList<>();
            for (Query childQuery : cquery.getChildren()) {
                if (childQuery instanceof AdvancedSearchQueryTerm &&
                        ((AdvancedSearchQueryTerm) childQuery).getField().getCode().equals(PROJECT_FIELD.getCode())) {
                    continue; // handled via _projects_ filter
                }
                childQueries.add(buildQuery(childQuery));
            }
            return StringUtilities.join(join, childQueries);
        } else if (query instanceof AdvancedSearchQueryTerm) {
            AdvancedSearchQueryTerm aQuery = (AdvancedSearchQueryTerm) query;
            if (aQuery.getField().getCode().equals(PROJECT_FIELD.getCode())) {
                return ""; // project handled elsewhere
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
                // contains-like, but uses ":" per existing behavior
                join = ":";
                prepend = "\"%";
                append = "%\"";
                break;

            case BEGINS_WITH:
                join = "::";
                append = "%\"";      // field::"value%"
                break;

            case ENDS_WITH:
                join = "::";
                prepend = "\"%";     // field::"%value"
                break;

            case CONTAINS:
                join = "::";
                prepend = "\"%";     // field::"%value%"
                append = "%\"";
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
                beforeQuery = "NOT ";
                prepend = "\"%";     // NOT field::"%value%"
                append = "%\"";
                break;

            case NOT_EQUAL:
                join = "::";
                beforeQuery = "NOT ";
                break;

            case IN_RANGE:
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
    protected List<FimsSample> _retrieveSamplesForTissueIds(List<String> tissueIds, RetrieveCallback rc) throws ConnectionException {
        try {
            ArrayList<String> tissueIdsArrayList = new ArrayList<>();
            for (int i = 0; i < tissueIds.size(); i++) {
                String val = tissueIds.get(i);
                if (val != null && !val.trim().equals("")) {
                    tissueIdsArrayList.add(val);
                }
            }
            Object[] trimmedTissueIds = tissueIdsArrayList.toArray();

            List<FimsSample> allSamples = new ArrayList<>();

            int chunk = 1000;
            for (int cnt = 0; cnt < trimmedTissueIds.length; cnt += chunk) {
                Object[] trimmedTissueIdsChunk = Arrays.copyOfRange(trimmedTissueIds, cnt, Math.min(trimmedTissueIds.length, cnt + chunk));

                Query[] tissueQueries = new Query[trimmedTissueIdsChunk.length];
                for (int i = 0; i < trimmedTissueIdsChunk.length; i++) {
                    tissueQueries[i] = Query.Factory.createFieldQuery(getTissueSampleDocumentField(), Condition.EQUAL, trimmedTissueIdsChunk[i].toString());
                }
                Query tissueQuery = Query.Factory.createOrQuery(tissueQueries, Collections.emptyMap());
                String tissueIDsToQuery = buildQuery(tissueQuery);
                String queryString = tissueIDsToQuery + " _select_:[Tissue,Sample,Event]";

                HttpUrl.Builder urlBuilder = client.getQueryTarget()
                        .newBuilder()
                        .addPathSegment("records")
                        .addPathSegment("Tissue")
                        .addPathSegment("json")
                        .addQueryParameter("includeEmptyProperties", "false")
                        .addQueryParameter("limit", String.valueOf(chunk));

                if (client.access_token.getAccess_token() != null &&
                    !client.access_token.getAccess_token().isEmpty()) {
                    urlBuilder.addQueryParameter("access_token", client.access_token.getAccess_token());
                }

                HttpUrl url = urlBuilder.build();

                RequestBody formBody = new FormBody.Builder()
                        .add("query", queryString)
                        .add("entity", "Tissue")
                        .build();

                Request searchRequest = new Request.Builder()
                        .url(url)
                        .post(formBody)
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .build();

                long start = System.currentTimeMillis();

                try (Response response = client.client.newCall(searchRequest).execute()) {
                    System.out.println("Took " + (System.currentTimeMillis() - start) + "ms to get GEOME searchRequest.post");
                    if (!response.isSuccessful()) throw new IOException("Unexpected code " + response);

                    SearchResult result = geomeFIMSClient.getRestServiceResult(SearchResult.class, response);
                    List<FimsSample> samples = transformQueryResults(tissueIds, result);
                    allSamples.addAll(samples);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            return allSamples;

        } catch (DatabaseServiceException e) {
            throw new ConnectionException(e);
        }
    }

    private List<FimsSample> transformQueryResults(List<String> tissueIds, SearchResult result) throws ConnectionException {
        List<FimsSample> samples = new ArrayList<>();

        allAttributes.put("genbankCountry", GENBANK_COUNTRY_FIELD);
        allAttributes.put("genbankDate", GENBANK_DATE_FIELD);
        allAttributes.put("genbankLatLng", GENBANK_LATLNG_FIELD);
        allAttributes.put("urn:expeditionCode", EXPEDITION_CODE_FIELD);
        allAttributes.put("urn:projectId",     PROJECT_ID_FIELD);
        allAttributes.put("Project", PROJECT_FIELD);

        Map<String, DocumentField> attributesByName = new HashMap<>();
        allAttributes.values().forEach(f -> attributesByName.put(f.getName(), f));

        Map<String, Map<String, Object>> mappedTissues = mapResults(getTissueSampleDocumentField().getName(), result.content.Tissue);
        Map<String, Map<String, Object>> mappedSamples = mapResults("materialSampleID", result.content.Sample);
        Map<String, Map<String, Object>> mappedEvents = mapResults(EVENT_ID, result.content.Event);

        for (String tissueId : tissueIds) {
            Map<String, Object> valuesForTissue = mappedTissues.get(tissueId);

            if (valuesForTissue != null) {
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
                            if ("urn:projectId".equals(documentField.getCode())) {
                                String id = valueToStore.toString();
                                if (!id.startsWith("http")) {
                                    valueToStore = "https://geome-db.org/workbench/project-overview?projectId=" + id;
                                }
                            }
                            valuesByCode.put(documentField.getCode(), valueToStore);

                        } catch (NumberFormatException e) {
                            System.out.println("Invalid value for " + documentField.getValueType() + " was " + value);
                        }
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

                Object pidObj = valuesByCode.get(PROJECT_ID_FIELD.getCode());
                if (pidObj != null) {
                    String pid = pidObj.toString();
                    String idOnly = pid.replaceFirst("^https?://geome-db\\.org/workbench/project-overview\\?projectId=", "");

                    Project matched = null;
                    for (Project p : projectsSafe()) {
                        if (String.valueOf(p.id).equals(idOnly)) {
                            matched = p; break;
                        }
                    }
                    if (matched != null) {
                        valuesByCode.put(PROJECT_FIELD.getCode(), matched.title);
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

    private String getGenbankLatLong(String latText, String lngText) {
        StringBuilder latLongSb = new StringBuilder();

        if (latText != null && lngText != null && !latText.equals("") && !lngText.equals("")) {
            try {
                Double lat = Double.parseDouble(latText);
                if (lat < 0) latLongSb.append(Math.abs(lat)).append(" S");
                else latLongSb.append(lat).append(" N");
                latLongSb.append(" ");
                Double lng = Double.parseDouble(lngText);
                if (lng < 0) latLongSb.append(Math.abs(lng)).append(" W");
                else latLongSb.append(lng).append(" E");
            } catch (NumberFormatException e) {
                latLongSb = new StringBuilder().append(latText).append(" ").append(lngText);
            }
        }

        if (latLongSb.toString().equals("")) return BLANK_ATTRIBUTE;
        else return latLongSb.toString();
    }

    private String getGenbankCountryValue(String country, String locality) {
        String genbankCountryValue = "";
        if (country != null) {
            genbankCountryValue = country.trim();
        }
        if (locality != null &&
                country != null &&
                !locality.trim().equalsIgnoreCase(country.trim()) &&
                !locality.trim().equals("")) {

            if (locality.trim().startsWith(country.trim()) &&
                    !locality.trim().equalsIgnoreCase(country.trim())) {
                String tempLocalityField = locality.trim().replaceFirst(country.trim(), "");
                tempLocalityField = tempLocalityField.replace(":", "");
                genbankCountryValue += ":" + tempLocalityField;
            } else {
                genbankCountryValue += ":" + locality.trim();
            }
        }
        if (genbankCountryValue.equals("")) {
            return BLANK_ATTRIBUTE;
        } else {
            return genbankCountryValue;
        }
    }

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

