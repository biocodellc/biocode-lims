package com.biomatters.plugins.biocode.labbench.fims.biocode;

import com.biomatters.geneious.publicapi.databaseservice.*;
import com.biomatters.geneious.publicapi.documents.Condition;
import com.biomatters.geneious.publicapi.documents.DocumentField;
import com.biomatters.geneious.publicapi.documents.XMLSerializationException;
import com.biomatters.plugins.biocode.labbench.BiocodeService;
import com.biomatters.plugins.biocode.labbench.ConnectionException;
import com.biomatters.plugins.biocode.labbench.FimsSample;
import com.biomatters.plugins.biocode.labbench.TissueDocument;
import com.biomatters.plugins.biocode.labbench.fims.FimsProject;
import com.biomatters.plugins.biocode.labbench.fims.TableFimsConnection;
import com.biomatters.plugins.biocode.labbench.fims.TableFimsConnectionOptions;
import com.biomatters.plugins.biocode.labbench.fims.TableFimsSample;
import org.jdom.Element;

import javax.ws.rs.core.Form;
import javax.ws.rs.core.MultivaluedMap;
import java.io.IOException;
import java.lang.ref.SoftReference;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Connection to the new Biocode FIMS
 * <p/>
 * Created by matthew on 1/02/14.
 */
public class BiocodeFIMSConnection extends TableFimsConnection {

    static final String HOST = "http://biscicol.org";

    @Override
    public String getLabel() {
        return "Biocode FIMS";
    }

    @Override
    public String getName() {
        return "biocode-fims";
    }

    @Override
    public String getDescription() {
        return "Connection to the new Biocode FIMS (https://code.google.com/p/biocode-fims/)";
    }

    private Map<String, SoftReference<FimsSample>> cachedSamples = new HashMap<String, SoftReference<FimsSample>>();

    @Override
    public List<String> getTissueIdsMatchingQuery(Query query, List<FimsProject> projectsToMatch) throws ConnectionException {
        // Ideally we wouldn't want to pull down the full sample straight away.  But the new Biocode FIMS always returns
        // every column for a sample.  So we'll cache the samples so at least we might not need to download it again.

        // We cache the samples by ID.  Unfortunately this means that if their are samples across expeditions with the
        // same ID then they will be treated as one and only the last will be taken.  There is currently no way to
        // tell these samples apart.  However the author of the Biocode FIMS, John Deck is working on an update that
        // will add globally unique IDs to the returned dataset.  Once that is released we should update this code to
        // cache by expedition.
        List<FimsSample> samples = getSamplesForQuery(query);
        List<String> ids = new ArrayList<String>();
        for (FimsSample sample : samples) {
            ids.add(sample.getId());
        }
        return ids;
    }

    private List<FimsSample> getSamplesForQuery(Query query) throws ConnectionException {
        StringBuilder filterText = new StringBuilder();
        Form form = new Form();
        String expeditionToSearch = null;
        if (query instanceof BasicSearchQuery) {
            filterText.append(((BasicSearchQuery) query).getSearchText());
        } else if (query instanceof AdvancedSearchQueryTerm) {
            AdvancedSearchQueryTerm termQuery = (AdvancedSearchQueryTerm) query;
            expeditionToSearch = getExpeditionOrAddToForm(form, null, termQuery);
        } else if (query instanceof CompoundSearchQuery) {
            CompoundSearchQuery compound = (CompoundSearchQuery) query;
            if (compound.getOperator() == CompoundSearchQuery.Operator.OR) {
                throw new ConnectionException("The Biocode FIMS does not support using the \"any\" operator");
            }

            for (Query inner : compound.getChildren()) {
                if (inner instanceof AdvancedSearchQueryTerm) {
                    expeditionToSearch = getExpeditionOrAddToForm(form, expeditionToSearch, (AdvancedSearchQueryTerm) inner);
                } else {
                    throw new ConnectionException("Unexpected type.  Contact support@mooreabiocode.org");
                }
            }
        }

        return getFimsSamplesBySearch(graphs.get(expeditionToSearch), form, filterText);
    }

    private List<FimsSample> getFimsSamplesBySearch(Graph graph, Form form, StringBuilder filterText) throws ConnectionException {
        List<FimsSample> samples = new ArrayList<FimsSample>();
        if (BiocodeService.getInstance().isQueryCancled())
            return samples;

        try {
            BiocodeFimsData data = BiocodeFIMSUtils.getData("" + project.id, graph,
                    form, filterText == null || filterText.length() == 0 ? null : filterText.toString());

            if (data.data.size() == 0) {
                if (form != null && form.asMap() != null && form.asMap().get(getTissueCol()) != null) {
                    MultivaluedMap<String,String> map = form.asMap();
                    for (String id : map.get(getTissueCol())) {
                        cachedSamples.put(id, new SoftReference<FimsSample>(dummySample));
                    }
                }
            } else {
                for (Row row : data.data) {
                    TableFimsSample sample = getFimsSampleForRow(data.header, row);
                    samples.add(sample);
                    cachedSamples.put(sample.getId(), new SoftReference<FimsSample>(sample));
                }
            }
        } catch (DatabaseServiceException e) {
            throw new ConnectionException(e.getMessage(), e);
        }
        return samples;
    }

    private TableFimsSample getFimsSampleForRow(List<String> header, Row row) throws DatabaseServiceException {
        Map<String, DocumentField> possibleColumns = new HashMap<String, DocumentField>();
        for (DocumentField documentField : getSearchAttributes()) {
            possibleColumns.put(documentField.getName(), documentField);
        }
        Map<String, Object> values = new HashMap<String, Object>();
        for (int i = 0; i < header.size(); i++) {
            DocumentField field = possibleColumns.get(header.get(i));
            if (field != null && i < row.rowItems.size()) {  // todo should we error out when items don't match?
                values.put(field.getCode(), row.rowItems.get(i));
            }
        }

        if (getTissueSampleDocumentField() == null) {
            throw new DatabaseServiceException("Tissue Sample Document Field not set.", false);
        }
        if (getSpecimenDocumentField() == null) {
            throw new DatabaseServiceException("Specimen Document Field not set.", false);
        }

        return new TableFimsSample(getSearchAttributes(), getTaxonomyAttributes(), values, getTissueSampleDocumentField().getCode(), getSpecimenDocumentField().getCode());
    }

    @Override
    protected List<FimsSample> _retrieveSamplesForTissueIds(List<String> tissueIds, RetrieveCallback callback) throws ConnectionException {
        Pattern withExpeditionPattern = Pattern.compile("\\[(.+)\\](.*)");

        List<FimsSample> results = new ArrayList<FimsSample>();
        HashSet<String> idsWithoutDupes = new HashSet<String>(tissueIds);
        for (String tissueId : idsWithoutDupes) {
            String expeditionTitle = null;
            Matcher matcher = withExpeditionPattern.matcher(tissueId);
            if (matcher.matches()) {
                expeditionTitle = matcher.group(1);
                tissueId = matcher.group(2);
            }
            if(tissueId == null || tissueId.isEmpty()) {
                continue;
            }

            SoftReference<FimsSample> cachedResult = cachedSamples.get(tissueId);
            FimsSample sample = null;
            if (cachedResult != null) {
                sample = cachedResult.get();
            }
            TissueDocument tissueDoc;
            if (sample == dummySample) {
                tissueDoc = null;
            } else if (sample != null) {
                tissueDoc = new TissueDocument(sample);
            } else  {
                tissueDoc = downloadTissueDocument(expeditionTitle, tissueId);
            }

            if(tissueDoc != null) {
                if (callback != null) {
                    callback.add(tissueDoc, Collections.<String, Object>emptyMap());
                }
                results.add(tissueDoc);
            }
        }
        return results;
    }

    /**
     *
     * @param expeditionTitle or null to search all expeditions
     * @param tissueId The id of the tissue to retrieve
     * @return TissueDocument or null if no tissue was found
     * @throws ConnectionException if there was a problem communicating with the server
     */
    private TissueDocument downloadTissueDocument(String expeditionTitle, String tissueId) throws ConnectionException {
        Form form = new Form();
        form.param(getTissueCol(), tissueId);
        Graph graph = null;
        for (Graph g : graphs.values()) {
            if(g.getExpeditionTitle().equals(expeditionTitle)) {
                graph = g;
                break;
            }
        }

        List<FimsSample> samples = getFimsSamplesBySearch(graph, form, null);
        assert(samples.size() <= 1);
        return samples.isEmpty() ? null : new TissueDocument(samples.get(0));
    }

    private String getExpeditionOrAddToForm(Form form, String projectToSearch, AdvancedSearchQueryTerm termQuery) throws ConnectionException {
        List<Condition> supportedConditions = Arrays.asList(Condition.CONTAINS, Condition.EQUAL);
        if (termQuery.getValues().length == 1 && supportedConditions.contains(termQuery.getCondition())) {
            String columnName = termQuery.getField().getCode().replace(CODE_PREFIX, "");
            if (columnName.equals(BiocodeFIMSUtils.EXPEDITION_NAME)) {
                projectToSearch = termQuery.getValues()[0].toString();
            } else {
                form.param(columnName, termQuery.getValues()[0].toString());
            }
        } else {
            throw new ConnectionException("Unsupported query.  Only \"contains\" and \"is\" searches are supported.");
        }
        return projectToSearch;
    }

    @Override
    public int getTotalNumberOfSamples() throws ConnectionException {
        return getTissueIdsMatchingQuery(Query.Factory.createBrowseQuery(), null).size();
    }


    @Override
    public TableFimsConnectionOptions _getConnectionOptions() {
        return new BiocodeFIMSOptions();
    }

    private Project project;
    private Map<String, Graph> graphs;

    @Override
    public void _connect(TableFimsConnectionOptions options) throws ConnectionException {
        if (!(options instanceof BiocodeFIMSOptions)) {
            throw new IllegalArgumentException("_connect() must be called with Options obtained from calling _getConnectionOptions()");
        }
        BiocodeFIMSOptions fimsOptions = (BiocodeFIMSOptions) options;
        project = fimsOptions.getProject();
        if (project == null) {
            throw new ConnectionException("You must select a project");
        }
        graphs = new HashMap<String, Graph>();
        try {
            List<Graph> graphsForExpedition = BiocodeFIMSUtils.getGraphsForProject("" + project.id);
            if (graphsForExpedition.isEmpty()) {
                throw new ConnectionException("Project has no expeditions");
            }
            for (Graph graph : graphsForExpedition) {
                graphs.put(graph.getExpeditionTitle(), graph);
            }
        } catch (DatabaseServiceException e) {
            throw new ConnectionException(e.getMessage(), e);
        }
    }

    @Override
    public void _disconnect() {
    }

    @Override
    public List<DocumentField> getTableColumns() throws IOException {
        List<DocumentField> fields = new ArrayList<DocumentField>();
        fields.add(new DocumentField(BiocodeFIMSUtils.EXPEDITION_NAME, "", CODE_PREFIX + BiocodeFIMSUtils.EXPEDITION_NAME, String.class, true, false));
        for (Project.Field field : project.getFields()) {
            fields.add(new DocumentField(field.name, field.name + "(" + field.uri + ")", CODE_PREFIX + field.uri, String.class, true, false));
        }
        return fields;
    }

    // Projects are not implemented for the new Biocode FIMS yet because we can't identify which project or expedition a sample comes from.
    // In a future update the author, John Deck, plans to add this information.  At that time we can implement it.

    @Override
    public List<String> getProjectsForSamples(Collection<FimsSample> samples) {
        return Collections.emptyList();  // Currently can't identify projects from samples?  What are we doing about expedition name?
    }

    @Override
    protected List<List<String>> getProjectLists() throws DatabaseServiceException {
        // What should we do here?  Ask John Deck?
        // Project
        //  |- Expedition
        // OR
        // Expedition
        return Collections.emptyList();  // We can probably get this from the service
    }

    private FimsSample dummySample = new FimsSample() {
        @Override
        public String getId() {
            return null;
        }

        @Override
        public String getSpecimenId() {
            return null;
        }

        @Override
        public List<DocumentField> getFimsAttributes() {
            return null;
        }

        @Override
        public List<DocumentField> getTaxonomyAttributes() {
            return null;
        }

        @Override
        public Object getFimsAttributeValue(String attributeName) {
            return null;
        }

        @Override
        public Element toXML() {
            return null;
        }

        @Override
        public void fromXML(Element element) throws XMLSerializationException {

        }
    };
}