package com.biomatters.plugins.biocode.labbench.fims.biocode;

import com.biomatters.geneious.publicapi.databaseservice.*;
import com.biomatters.geneious.publicapi.documents.Condition;
import com.biomatters.geneious.publicapi.documents.DocumentField;
import com.biomatters.plugins.biocode.labbench.ConnectionException;
import com.biomatters.plugins.biocode.labbench.FimsSample;
import com.biomatters.plugins.biocode.labbench.TissueDocument;
import com.biomatters.plugins.biocode.labbench.fims.TableFimsConnection;
import com.biomatters.plugins.biocode.labbench.fims.TableFimsConnectionOptions;
import com.biomatters.plugins.biocode.labbench.fims.TableFimsSample;

import javax.ws.rs.core.Form;
import java.io.IOException;
import java.lang.ref.SoftReference;
import java.util.*;

/**
 * Connection to the new Biocode FIMS
 *
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
    public List<String> getTissueIdsMatchingQuery(Query query) throws ConnectionException {
        // Ideally we wouldn't want to pull down the full sample straight away.  But the new Biocode FIMS always returns
        // every column for a sample.  So we'll cache the samples so at least we might not need to download it again.
        List<FimsSample> samples = getSamplesForQuery(query);
        List<String> ids = new ArrayList<String>();
        for (FimsSample sample : samples) {
            String id = sample.getId();
            ids.add(id);
            cachedSamples.put(id, new SoftReference<FimsSample>(sample));
        }
        return ids;
    }

    private List<FimsSample> getSamplesForQuery(Query query) throws ConnectionException {
        StringBuilder filterText = new StringBuilder();
        Form form = new Form();
        String expeditionToSearch = null;
        if(query instanceof BasicSearchQuery) {
            filterText.append(((BasicSearchQuery) query).getSearchText());
        } else if(query instanceof AdvancedSearchQueryTerm) {
            AdvancedSearchQueryTerm termQuery = (AdvancedSearchQueryTerm) query;
            expeditionToSearch = getExpeditionOrAddToForm(form, null, termQuery);
        } else if(query instanceof CompoundSearchQuery) {
            CompoundSearchQuery compound = (CompoundSearchQuery) query;
            if(compound.getOperator() == CompoundSearchQuery.Operator.OR) {
                throw new ConnectionException("The Biocode FIMS does not support using the \"any\" operator");
            }

            for (Query inner : compound.getChildren()) {
                if(inner instanceof AdvancedSearchQueryTerm) {
                    expeditionToSearch = getExpeditionOrAddToForm(form, expeditionToSearch, (AdvancedSearchQueryTerm) inner);
                } else {
                    throw new ConnectionException("Unexpected type.  Contact support@mooreabiocode.org");
                }
            }
        }

        List<FimsSample> samples = new ArrayList<FimsSample>();
        try {
            BiocodeFimsData data = BiocodeFIMSUtils.getData("" + project.id, graphs.get(expeditionToSearch),
                    form, filterText.length() > 0 ? filterText.toString() : null);
            for (Row row : data.data) {
                samples.add(getFimsSampleForRow(data.header, row));
            }
        } catch (DatabaseServiceException e) {
            throw new ConnectionException(e.getMessage(), e);
        }
        return samples;
    }

    private TableFimsSample getFimsSampleForRow(List<String> header, Row row) {
        Map<String, DocumentField> possibleColumns = new HashMap<String, DocumentField>();
        for (DocumentField documentField : getSearchAttributes()) {
            possibleColumns.put(documentField.getName(), documentField);
        }
        Map<String, Object> values = new HashMap<String, Object>();
        for(int i=0; i<header.size(); i++) {
            DocumentField field = possibleColumns.get(header.get(i));
            if(field != null && i < row.rowItems.size()) {  // todo should we error out when items don't match?
                values.put(field.getCode(), row.rowItems.get(i));
            }
        }
        return new TableFimsSample(getSearchAttributes(), getTaxonomyAttributes(), values, getTissueSampleDocumentField(), getSpecimenDocumentField());
    }

    @Override
    protected List<FimsSample> _retrieveSamplesForTissueIds(List<String> tissueIds, RetrieveCallback callback) throws ConnectionException {
        List<FimsSample> results = new ArrayList<FimsSample>();
        List<String> toRetrieve = new ArrayList<String>();
        for (String tissueId : tissueIds) {
            FimsSample sample = null;
            SoftReference<FimsSample> cachedResult = cachedSamples.get(tissueId);
            if(cachedResult != null) {
                sample = cachedResult.get();
            }
            if(sample != null) {
                TissueDocument tissueDoc = new TissueDocument(sample);
                if(callback != null) {
                    callback.add(tissueDoc, Collections.<String, Object>emptyMap());
                }
                results.add(tissueDoc);
            } else {
                toRetrieve.add(tissueId);
            }
        }

        try {
            for (String id : toRetrieve) {
                Form form = new Form();
                form.param(getTissueCol(), id);
                BiocodeFimsData data = BiocodeFIMSUtils.getData(""+ project.id, null, form, null);
                for (Row row : data.data) {
                    TableFimsSample sample = getFimsSampleForRow(data.header, row);
                    TissueDocument tissueDoc = new TissueDocument(sample);
                    if(callback != null) {
                        callback.add(tissueDoc, Collections.<String, Object>emptyMap());
                    }
                    results.add(tissueDoc);
                }
            }
        } catch (DatabaseServiceException e) {
            throw new ConnectionException(e.getMessage(), e);
        }

        return results;
    }

    private String getExpeditionOrAddToForm(Form form, String projectToSearch, AdvancedSearchQueryTerm termQuery) throws ConnectionException {
        List<Condition> supportedConditions = Arrays.asList(Condition.CONTAINS);
        if(termQuery.getValues().length == 1 && supportedConditions.contains(termQuery.getCondition())) {
            String columnName = termQuery.getField().getCode().replace(CODE_PREFIX, "");
            if(columnName.equals(BiocodeFIMSUtils.EXPEDITION_NAME)) {
                projectToSearch = termQuery.getValues()[0].toString();
            } else {
                form.param(columnName, termQuery.getValues()[0].toString());
            }
        } else {
            throw new ConnectionException("Unsupported query.  Only contains searches are supported.");
        }
        return projectToSearch;
    }

    @Override
    public int getTotalNumberOfSamples() throws ConnectionException {
        return getTissueIdsMatchingQuery(Query.Factory.createBrowseQuery()).size();
    }

    @Override
    public boolean requiresMySql() {
        return false;
    }


    @Override
    public TableFimsConnectionOptions _getConnectionOptions() {
        return new BiocodeFIMSOptions();
    }

    private Project project;
    private Map<String, Graph> graphs;
    @Override
    public void _connect(TableFimsConnectionOptions options) throws ConnectionException {
        if(!(options instanceof BiocodeFIMSOptions)) {
            throw new IllegalArgumentException("_connect() must be called with Options obtained from calling _getConnectionOptiions()");
        }
        BiocodeFIMSOptions fimsOptions = (BiocodeFIMSOptions) options;
        project = fimsOptions.getProject();
        if(project == null) {
            throw new ConnectionException("You must select a project");
        }
        graphs = new HashMap<String, Graph>();
        try {
            List<Graph> graphsForExpedition = BiocodeFIMSUtils.getGraphsForProject("" + project.id);
            if(graphsForExpedition.isEmpty()) {
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
        project = null;
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
}
