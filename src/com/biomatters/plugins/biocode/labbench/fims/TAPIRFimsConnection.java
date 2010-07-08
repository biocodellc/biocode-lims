package com.biomatters.plugins.biocode.labbench.fims;

import com.biomatters.geneious.publicapi.databaseservice.AdvancedSearchQueryTerm;
import com.biomatters.geneious.publicapi.databaseservice.BasicSearchQuery;
import com.biomatters.geneious.publicapi.databaseservice.CompoundSearchQuery;
import com.biomatters.geneious.publicapi.databaseservice.Query;
import com.biomatters.geneious.publicapi.documents.AnnotatedPluginDocument;
import com.biomatters.geneious.publicapi.documents.Condition;
import com.biomatters.geneious.publicapi.documents.DocumentField;
import com.biomatters.geneious.publicapi.plugin.Options;
import com.biomatters.plugins.biocode.BiocodeUtilities;
import com.biomatters.plugins.biocode.labbench.ConnectionException;
import com.biomatters.plugins.biocode.labbench.FimsSample;
import com.biomatters.plugins.biocode.labbench.fims.tapir.TAPIRClient;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.Namespace;
import org.jdom.output.Format;
import org.jdom.output.XMLOutputter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Created by IntelliJ IDEA.
 * User: steve
 * Date: 27/05/2009
 * Time: 6:15:23 AM
 * To change this template use File | Settings | File Templates.
 */
public class TAPIRFimsConnection extends FIMSConnection{
    private List<DocumentField> searchAttributes;
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

    public Options getConnectionOptions() {
        Options connectionOptions = new Options(this.getClass());
        connectionOptions.addStringOption("accessPoint", "Access Point:", "http://tapirlink.berkeley.edu/tapir.php/biocode");
        return connectionOptions;
    }

    public void connect(Options options) throws ConnectionException {
        client = new TAPIRClient(options.getValueAsString("accessPoint"));
        try {
            searchAttributes = client.getSearchAttributes();
        } catch (JDOMException e) {
            e.printStackTrace();
            throw new ConnectionException(e.getMessage(), e);
        } catch (IOException e) {
            e.printStackTrace();
            throw new ConnectionException(e.getMessage(), e);
        }
    }

    public void disconnect() throws ConnectionException {
        
    }

    public DocumentField getTissueSampleDocumentField() {
        return new DocumentField("Tissue ID", "", "tissueId", String.class, true, false);
    }

    public List<DocumentField> getSearchAttributes() {
        return searchAttributes;
    }

    public BiocodeUtilities.LatLong getLatLong(AnnotatedPluginDocument annotatedDocument) {
        //todo
        return null;
    }

    public List<DocumentField> getCollectionAttributes() {
        return searchAttributes;
    }

    public List<DocumentField> getTaxonomyAttributes() {
        return Collections.emptyList();
    }

    public List<FimsSample> _getMatchingSamples(Query query) throws ConnectionException{
        Element searchXML = null;
        if(query instanceof CompoundSearchQuery) {
            try {
                searchXML = client.searchTapirServer((CompoundSearchQuery)query, searchAttributes);
            } catch (JDOMException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        else if(query instanceof BasicSearchQuery) {
            List<Query> queries = new ArrayList<Query>();
            try {
                for(DocumentField field : searchAttributes) {
                    if(String.class.isAssignableFrom(field.getValueType())) {
                        queries.add(Query.Factory.createFieldQuery(field, Condition.CONTAINS, ((BasicSearchQuery)query).getSearchText()));
                    }
                }

                searchXML = client.searchTapirServer((CompoundSearchQuery)Query.Factory.createOrQuery(queries.toArray(new Query[queries.size()]), Collections.EMPTY_MAP), searchAttributes);
            } catch (JDOMException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        else if(query instanceof AdvancedSearchQueryTerm) {
            try {
                searchXML = client.searchTapirServer((CompoundSearchQuery)Query.Factory.createOrQuery(new Query[] {query}, Collections.EMPTY_MAP), searchAttributes);
            } catch (JDOMException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        if(searchXML != null) {
            XMLOutputter out = new XMLOutputter(Format.getPrettyFormat());
            try {
                out.output(searchXML, System.out);
            } catch (IOException e) {
                e.printStackTrace();
            }
            List<FimsSample> samples = new ArrayList<FimsSample>();
            Element searchElement = searchXML.getChild("search", searchXML.getNamespace());
            if(searchElement != null) {
                Namespace namespace = Namespace.getNamespace("http://example.net/simple_specimen");
                Element recordsElement = searchElement.getChild("records", namespace);
                if(recordsElement != null) {
                    List<Element> recordList = recordsElement.getChildren("record", namespace);
                    for (int i = 0; i < recordList.size(); i++) {
                        Element e = recordList.get(i);
                        e.detach();
                        samples.add(new TapirFimsSample(e, searchAttributes));
                    }
                    return samples;
                }
            }
            else {
                Element errorElement = searchXML.getChild("error", searchXML.getNamespace());
                if(errorElement != null) {
                    throw new ConnectionException("TAPIR Server reported an error: "+errorElement.getText());
                }
            }

        }
        return Collections.EMPTY_LIST;
    }

    public Map<String, String> getTissueIdsFromExtractionBarcodes(List<String> extractionIds) throws ConnectionException{
        return Collections.emptyMap();
    }

    public Map<String, String> getTissueIdsFromFimsExtractionPlate(String plateId) throws ConnectionException{
        return Collections.emptyMap();
    }

    public Map<String, String> getTissueIdsFromFimsTissuePlate(String plateId) throws ConnectionException{
        return Collections.emptyMap();
    }

    public boolean canGetTissueIdsFromFimsTissuePlate() {
        return false;
    }
}
