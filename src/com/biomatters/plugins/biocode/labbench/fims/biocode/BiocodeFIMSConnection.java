package com.biomatters.plugins.biocode.labbench.fims.biocode;

import com.biomatters.geneious.publicapi.databaseservice.*;
import com.biomatters.geneious.publicapi.documents.Condition;
import com.biomatters.geneious.publicapi.documents.DocumentField;
import com.biomatters.plugins.biocode.labbench.ConnectionException;
import com.biomatters.plugins.biocode.labbench.FimsSample;
import com.biomatters.plugins.biocode.labbench.fims.TableFimsConnection;
import com.biomatters.plugins.biocode.labbench.fims.TableFimsConnectionOptions;
import com.biomatters.plugins.biocode.labbench.fims.TableFimsSample;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    @Override
    public List<FimsSample> _getMatchingSamples(Query query) throws ConnectionException {
        StringBuilder filterText = new StringBuilder();
        String projectToSearch = null;
        if(query instanceof BasicSearchQuery) {
            filterText.append(((BasicSearchQuery) query).getSearchText());
        } else if(query instanceof AdvancedSearchQueryTerm) {
            AdvancedSearchQueryTerm termQuery = (AdvancedSearchQueryTerm) query;
            projectToSearch = getProjectOrAppendToFilterText(filterText, null, termQuery);
        } else if(query instanceof CompoundSearchQuery) {
            CompoundSearchQuery compound = (CompoundSearchQuery) query;
            if(compound.getOperator() == CompoundSearchQuery.Operator.OR) {
                throw new ConnectionException("The Biocode FIMS does not support using the \"any\" operator");
            }

            boolean first = true;
            for (Query inner : compound.getChildren()) {
                if(first) {
                    first = false;
                } else {
                    filterText.append(",");
                }
                if(inner instanceof AdvancedSearchQueryTerm) {
                    projectToSearch = getProjectOrAppendToFilterText(filterText, projectToSearch, (AdvancedSearchQueryTerm)inner);
                } else {
                    throw new ConnectionException("Unexpected type.  Contact support@mooreabiocode.org");
                }
            }
        }

        try {
            BiocodeFimsData data = BiocodeFIMSUtils.getData(""+expedition.id, graphs.get(projectToSearch),
                    filterText.length() > 0 ? filterText.toString() : null);
            List<FimsSample> samples = new ArrayList<FimsSample>();
            for (Row row : data.data) {
                Map<String, Object> values = new HashMap<String, Object>();
                for(int i=0; i<data.header.size(); i++) {
                    if(i < row.rowItems.size()) {  // todo should we error out?
                        values.put(TableFimsConnection.CODE_PREFIX + data.header.get(i), row.rowItems.get(i));
                    }
                }
                samples.add(new TableFimsSample(getSearchAttributes(), getTaxonomyAttributes(), values, getTissueSampleDocumentField(), getSpecimenDocumentField()));
            }
            return samples;
        } catch (DatabaseServiceException e) {
            throw new ConnectionException(e.getMessage(), e);
        }
    }

    private String getProjectOrAppendToFilterText(StringBuilder filterText, String projectToSearch, AdvancedSearchQueryTerm termQuery) throws ConnectionException {
        if(termQuery.getValues().length == 1 && termQuery.getCondition() == Condition.EQUAL) {
            String columnName = termQuery.getField().getCode().replace(CODE_PREFIX, "");
            if(columnName.equals(BiocodeFIMSUtils.EXPEDITION_NAME)) {
                projectToSearch = termQuery.getValues()[0].toString();
            } else {
                filterText.append(columnName).append(":").append(termQuery.getValues()[0].toString());
            }
        } else {
            throw new ConnectionException("Unsupported query");
        }
        return projectToSearch;
    }

    @Override
    public void getAllSamples(RetrieveCallback callback) throws ConnectionException {
        // todo
    }

    @Override
    public int getTotalNumberOfSamples() throws ConnectionException {
        return 0;
    }

    @Override
    public boolean requiresMySql() {
        return false;
    }


    @Override
    public TableFimsConnectionOptions _getConnectionOptions() {
        return new BiocodeFIMSOptions();
    }

    private Project expedition;
    private Map<String, Graph> graphs;
    @Override
    public void _connect(TableFimsConnectionOptions options) throws ConnectionException {
        if(!(options instanceof BiocodeFIMSOptions)) {
            throw new IllegalArgumentException("_connect() must be called with Options obtained from calling _getConnectionOptiions()");
        }
        BiocodeFIMSOptions fimsOptions = (BiocodeFIMSOptions) options;
        expedition = fimsOptions.getExpedition();
        graphs = new HashMap<String, Graph>();
        try {
            List<Graph> graphsForExpedition = BiocodeFIMSUtils.getGraphsForProject("" + expedition.id);
            for (Graph graph : graphsForExpedition) {
                graphs.put(graph.getExpeditionTitle(), graph);
            }
        } catch (DatabaseServiceException e) {
            throw new ConnectionException(e.getMessage(), e);
        }
    }

    @Override
    public void _disconnect() {
        expedition = null;
    }

    @Override
    public List<DocumentField> getTableColumns() throws IOException {
        List<DocumentField> fields = new ArrayList<DocumentField>();
        fields.add(new DocumentField(BiocodeFIMSUtils.EXPEDITION_NAME, "", CODE_PREFIX + BiocodeFIMSUtils.EXPEDITION_NAME, String.class, true, false));
        for (Project.Field field : expedition.getFields()) {
            fields.add(new DocumentField(field.name, field.name + "(" + field.uri + ")", CODE_PREFIX + field.name, String.class, true, false));
        }
        return fields;
    }
}