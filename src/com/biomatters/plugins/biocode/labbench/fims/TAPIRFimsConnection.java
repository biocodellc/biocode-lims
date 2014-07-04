package com.biomatters.plugins.biocode.labbench.fims;

import com.biomatters.geneious.publicapi.databaseservice.*;
import com.biomatters.geneious.publicapi.documents.Condition;
import com.biomatters.geneious.publicapi.documents.DocumentField;
import com.biomatters.geneious.publicapi.plugin.Options;
import com.biomatters.plugins.biocode.labbench.ConnectionException;
import com.biomatters.plugins.biocode.labbench.FimsSample;
import com.biomatters.plugins.biocode.labbench.PasswordOptions;
import com.biomatters.plugins.biocode.labbench.fims.tapir.TAPIRClient;
import com.biomatters.plugins.biocode.labbench.fims.tapir.TapirSchema;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.Namespace;
import org.jdom.output.Format;
import org.jdom.output.XMLOutputter;

import java.io.IOException;
import java.util.*;

/**
 * @author steve
 * @version $Id: 27/05/2009 6:15:23 AM steve $
 */
public class TAPIRFimsConnection extends FIMSConnection{
    private TapirSchema schema;

    private List<DocumentField> searchAttributes;
    private List<DocumentField> taxonomyAttributes;
    private TAPIRClient client;

    public String getName() {
        return "tapir";
    }

    public String getDescription() {
        return  "Connect to a TAPIR provider";
    }

    public String getLabel() {
        return "TAPIR";
    }

    @SuppressWarnings({"UnusedDeclaration"})
    private enum DataStandard {
        DARWIN_CORE("DarwinCore", TapirSchema.DarwinCore),
        ABCD("ABCD", TapirSchema.ABCD);

        String label;
        TapirSchema schema;

        private DataStandard(String label, TapirSchema schema) {
            this.schema = schema;
            this.label = label;
        }
    }


    private static final String SCHEMA_OP_KEY = "schema";
    public PasswordOptions getConnectionOptions() {
        PasswordOptions connectionOptions = new PasswordOptions(this.getClass());
        connectionOptions.addStringOption("accessPoint", "Access Point:", "http://tapirlink.berkeley.edu/tapir.php/biocode");
        List<Options.OptionValue> optionValues = new ArrayList<Options.OptionValue>();
        for (DataStandard dataStandard : DataStandard.values()) {
            optionValues.add(new Options.OptionValue(dataStandard.name(), dataStandard.label));
        }
        connectionOptions.addComboBoxOption(SCHEMA_OP_KEY, "Data Sharing Standard:", optionValues, optionValues.get(0));
        return connectionOptions;
    }

    public void _connect(Options options) throws ConnectionException {
        String schemaName = options.getValueAsString(SCHEMA_OP_KEY);
        DataStandard dataStandard = DataStandard.valueOf(schemaName);
        schema = dataStandard.schema;

        try {
            client = new TAPIRClient(schema, options.getValueAsString("accessPoint"));
            searchAttributes = getMatchingFields(client.getSearchAttributes(), false);
            taxonomyAttributes = getMatchingFields(client.getSearchAttributes(), true);
            for (DocumentField field : searchAttributes) {
                if(schema.getTissueIdField().equals(field.getCode())) {
                    tissueField = field;
                }
            }
        } catch (JDOMException e) {
            e.printStackTrace();
            throw new ConnectionException(e.getMessage(), e);
        } catch (IOException e) {
            e.printStackTrace();
            throw new ConnectionException(e.getMessage(), e);
        } catch(IllegalArgumentException e) {
            throw new ConnectionException(e.getMessage(), e);
        }
    }

    public void disconnect() {
        
    }

    private DocumentField tissueField;
    public DocumentField getTissueSampleDocumentField() {
        return tissueField;
    }

    public List<DocumentField> _getSearchAttributes() {
        return getMatchingFields(searchAttributes, false);
    }

    private List<DocumentField> getMatchingFields(List<DocumentField> searchAttributes, boolean taxonomy) {
        List<DocumentField> result = new ArrayList<DocumentField>();
        for(DocumentField field : searchAttributes) {
            if(isTaxonomyAttribute(field.getCode()) == taxonomy) {
                result.add(field);
            }
        }
        return result;
    }

    private boolean isTaxonomyAttribute(String code) {
        for (String taxonomyCode : schema.getTaxonomyCodes()) {
            if (taxonomyCode.equals(code) || "http://biocode.berkeley.edu/schema/lowesttaxon".equals(code)) {
                return true;
            }
        }
        return false;
    }

    public List<DocumentField> getCollectionAttributes() {
        return searchAttributes;
    }

    public List<DocumentField> getTaxonomyAttributes() {
        return taxonomyAttributes;
    }

    public int getTotalNumberOfSamples() throws ConnectionException {
        return -1;
    }

    private Element searchTapirServer(Query query, boolean justReturnTissueID) throws ConnectionException {
        List<DocumentField> fieldsToReturn = justReturnTissueID ? Collections.singletonList(getTissueSampleDocumentField()) : null;
        Element searchXML = null;
        if(query instanceof CompoundSearchQuery) {
            try {
                CompoundSearchQuery csq = (CompoundSearchQuery) query;
                List<? extends Query> children = csq.getChildren();
                searchXML = client.searchTapirServer((List<AdvancedSearchQueryTerm>)children, csq.getOperator(), searchAttributes, fieldsToReturn);
            } catch (JDOMException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        else if(query instanceof BasicSearchQuery) {
            List<AdvancedSearchQueryTerm> queries = new ArrayList<AdvancedSearchQueryTerm>();
            try {
                for(DocumentField field : searchAttributes) {
                    if(String.class.isAssignableFrom(field.getValueType())) {
                        queries.add((AdvancedSearchQueryTerm)Query.Factory.createFieldQuery(field, Condition.CONTAINS, ((BasicSearchQuery)query).getSearchText()));
                    }
                }

                searchXML = client.searchTapirServer(queries, CompoundSearchQuery.Operator.OR, searchAttributes, fieldsToReturn);
            } catch (JDOMException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        else if(query instanceof AdvancedSearchQueryTerm) {
            try {
                searchXML = client.searchTapirServer(Arrays.asList((AdvancedSearchQueryTerm)query), CompoundSearchQuery.Operator.AND, searchAttributes, fieldsToReturn);
            } catch (JDOMException e) {
                e.printStackTrace();
                throw new ConnectionException(e.getMessage(), e);
            } catch (IOException e) {
                e.printStackTrace();
                throw new ConnectionException(e.getMessage(), e);
            }
        }
        return searchXML;

    }

    @Override
    public List<String> getTissueIdsMatchingQuery(Query query, List<FimsProject> projectsToMatch) throws ConnectionException {
        Element searchXml = searchTapirServer(query, true);
        List<Element> results = getRecordElementsFromSearchResultXml(searchXml);

        List<String> tissueIds = new ArrayList<String>();
        for (Element resultElement : results) {
            String tissueId = null;
            List<Element> children = resultElement.getChildren();
            assert(children.size() <= 1);
            if(children.size() > 0) {
                tissueId = children.get(0).getText();
            }
            if(tissueId != null) {
                tissueId = tissueId.trim();
                if(tissueId.length() > 0) {
                    tissueIds.add(tissueId);
                }
            }
        }
        return tissueIds;
    }

    @Override
    protected List<FimsSample> _retrieveSamplesForTissueIds(List<String> tissueIds, RetrieveCallback callback) throws ConnectionException {
        Query[] queries = new Query[tissueIds.size()];
        int i = 0;
        for (String tissueId : tissueIds) {
            queries[i++] = Query.Factory.createFieldQuery(getTissueSampleDocumentField(), Condition.EQUAL, tissueId);
        }
        Element searchXML = searchTapirServer(Query.Factory.createOrQuery(queries, Collections.<String, Object>emptyMap()), false);
        if(searchXML != null) {
            List<Element> recordList = getRecordElementsFromSearchResultXml(searchXML);
            if(recordList != null) {
                List<FimsSample> samples = new ArrayList<FimsSample>();
                for (Element e : recordList) {
                    samples.add(new TapirFimsSample(schema.getTissueIdField(), schema.getSpecimenIdField(), (Element)e.clone(), searchAttributes, taxonomyAttributes));
                }
                return samples;
            }
        }
        return Collections.emptyList();
    }

    private List<Element> getRecordElementsFromSearchResultXml(Element searchXML) throws ConnectionException {
        XMLOutputter out = new XMLOutputter(Format.getPrettyFormat());
        try {
            out.output(searchXML, System.out);
        } catch (IOException e) {
            e.printStackTrace();
        }
        Element searchElement = searchXML.getChild("search", searchXML.getNamespace());
        List<Element> recordList = null;
        if(searchElement != null) {
            Namespace namespace = Namespace.getNamespace("http://example.net/simple_specimen");
            Element recordsElement = searchElement.getChild("records", namespace);
            if(recordsElement != null) {
                recordList = new ArrayList<Element>(recordsElement.getChildren("record", namespace));
            }
        }
        else {
            Element errorElement = searchXML.getChild("error", searchXML.getNamespace());
            if(errorElement != null) {
                throw new ConnectionException("TAPIR Server reported an error: "+errorElement.getText());
            }
        }
        return recordList;
    }

    @Override
    public DocumentField getPlateDocumentField() {
        return new DocumentField("Plate", "", "http://biocode.berkeley.edu/schema/plate", String.class, false, false);
    }

    @Override
    public DocumentField getWellDocumentField() {
        return new DocumentField("Well", "", "http://biocode.berkeley.edu/schema/well", String.class, false, false);
    }

    public boolean storesPlateAndWellInformation() {
        return true;
    }

    public boolean hasPhotos() {
        return false;
    }


    // Projects are currently unsupported in Tapir connections.  Have to check with John Deck if the DarwinCore standard
    // has a field for Project.
    @Override
    public List<String> getProjectsForSamples(Collection<FimsSample> samples) {
        return Collections.emptyList();
    }

    @Override
    public List<FimsProject> getProjects() throws DatabaseServiceException {
        return Collections.emptyList();
    }
}
