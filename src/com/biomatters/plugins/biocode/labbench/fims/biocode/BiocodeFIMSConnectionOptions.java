package com.biomatters.plugins.biocode.labbench.fims.biocode;

import com.biomatters.geneious.publicapi.components.Dialogs;
import com.biomatters.geneious.publicapi.components.ProgressFrame;
import com.biomatters.geneious.publicapi.databaseservice.DatabaseServiceException;
import com.biomatters.geneious.publicapi.documents.XMLSerializationException;
import com.biomatters.geneious.publicapi.utilities.ThreadUtilities;
import com.biomatters.plugins.biocode.BiocodePlugin;
import com.biomatters.plugins.biocode.labbench.PasswordOptions;
import com.biomatters.plugins.biocode.labbench.fims.TableFimsConnection;
import org.jdom.Element;
import org.virion.jam.util.SimpleListener;

import javax.ws.rs.NotFoundException;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by matthew on 1/02/14.
 */
public class BiocodeFIMSConnectionOptions extends PasswordOptions {

    private final String GRAPH_ID = "id";
    private final String GRAPH_LABEL = "label";
    ComboBoxOption<OptionValue> expeditionOption;
    ComboBoxOption<OptionValue> graphOption;

    private static final OptionValue LOADING = new OptionValue("loading", "Loading...");
    private static final OptionValue NO_VALUES = new OptionValue("none", "None Available");


    public BiocodeFIMSConnectionOptions() {
        super(BiocodePlugin.class);

//        addLabel("<html><i>The Biocode-FIMS is still under active development.<br>" +
//                "There is currently a limitation that prevents Geneious from retrieving fields on the fly.  Due to this the whole FIMS is downloaded and cached when" +
//                "the connection is made.</i></html>");

        // These are hard coded because there is currently no way to query for available expeditions
        final List<OptionValue> expeditionOptions = new ArrayList<OptionValue>();
        expeditionOptions.add(new OptionValue("1", "IndoPacific Database"));
        expeditionOptions.add(new OptionValue("2", "Smithsonian LAB"));
        expeditionOptions.add(new OptionValue("3", "Hawaii Dimensions"));
        expeditionOptions.add(new OptionValue("5", "Barcode of Wildlife Training"));
        expeditionOption = addComboBoxOption("expedition", "Expedition:", expeditionOptions, expeditionOptions.get(0));

        List<OptionValue> dataSetOptions = new ArrayList<OptionValue>();
        dataSetOptions.add(LOADING);  // todo need to cache these options otherwise storing doesn't work :(
        graphOption = addComboBoxOption("dataset", "Data Set:", dataSetOptions, dataSetOptions.get(0));

        SimpleListener expeditionChangedListener = getExpeditionChangedListener();
        expeditionOption.addChangeListener(expeditionChangedListener);
        expeditionChangedListener.objectChanged();
    }


    private static final String GRAPHS_ELEMENT = "availableGraphs";
    @Override
    public Element toXML() {
        Element root = super.toXML();
        Element graphsElement = new Element(GRAPHS_ELEMENT);
        for (OptionValue optionValue : graphOption.getPossibleOptionValues()) {
            graphsElement.addContent(new Element("graph").setAttribute(GRAPH_ID, optionValue.getName()).setAttribute(GRAPH_LABEL, optionValue.getLabel()));
        }
        root.addContent(graphsElement);
        return root;
    }

    public BiocodeFIMSConnectionOptions(Element element) throws XMLSerializationException {
        super(element);
        Element graphsElement = element.getChild(GRAPHS_ELEMENT);
        if(graphsElement != null) {
            List<OptionValue> values = new ArrayList<OptionValue>();
            for (Element child : graphsElement.getChildren()) {
                String id = child.getAttributeValue(GRAPH_ID);
                String label = child.getAttributeValue(GRAPH_LABEL);
                if(id != null && label != null) {
                    values.add(new OptionValue(id, label));
                }
            }
            if(!values.isEmpty()) {
                graphOption.setPossibleValues(values);
            }
        }
    }

    private SimpleListener getExpeditionChangedListener() {
        return new SimpleListener() {
            @Override
            public void objectChanged() {
                final ProgressFrame progressFrame = new ProgressFrame("Retrieving Values", "Retrieving values from "
                         + BiocodeFIMSConnection.HOST + "...", 100, true, null); // todo block Options panel
                progressFrame.setCancelable(false);
                progressFrame.setIndeterminateProgress();

                final List<OptionValue> graphOptions = new ArrayList<OptionValue>();
                new Thread() {
                    public void run() {
                        String expeditionId = expeditionOption.getValue().getName();
                        List<Graph> graphs = getGraphsForExpedition(expeditionId);
                        if(!graphs.isEmpty()) {
                            for (Graph graph : graphs) {
                                graphOptions.add(new OptionValue(graph.getGraphId(), graph.getProjectTitle()));
                            }
                        } else {
                            graphOptions.add(NO_VALUES);
                        }
                        progressFrame.setComplete();

                        ThreadUtilities.invokeLater(new Runnable() {
                            public void run() {
                               graphOption.setPossibleValues(graphOptions);
                            }
                        });
                    }
                }.start();
            }
        };
    }

    static List<Graph> getGraphsForExpedition(String id) {
        WebTarget target = ClientBuilder.newClient().target("http://biscicol.org");
        Invocation.Builder request = target.path("id/expeditionService/graphs").path(id).request(MediaType.APPLICATION_JSON_TYPE);
        GraphList graphs = request.get(GraphList.class);
        return graphs.data;
    }

    static BiocodeFimsData getData(WebTarget target, String filter) throws DatabaseServiceException{
        try {
            if(filter != null) {
                target = target.queryParam("filter", filter);
            }
            Invocation.Builder request = target.
                    request(MediaType.APPLICATION_JSON_TYPE);
            return request.get(BiocodeFimsData.class);
        } catch (NotFoundException e) {
            throw new DatabaseServiceException("No data found.", false);
        } catch (WebApplicationException e) {
            throw new DatabaseServiceException(e, "Encountered an error communicating with " + BiocodeFIMSConnection.HOST, false);
        }
    }

    static WebTarget getWebTarget(String expedition, String graph) {
        WebTarget target = ClientBuilder.newClient().target("http://biscicol.org");
        return target.path("biocode-fims/query/json").
                queryParam("graphs", graph).
                queryParam("expedition_id", expedition);
    }

    public List<OptionValue> getFieldsAsOptionValues() throws DatabaseServiceException {
        if(graphOption.getValue() == LOADING) {
            // todo get this to only show up on user input, not other
//            Dialogs.showMessageDialog("Still loading available data sets from " + BiocodeFIMSConnection.HOST + ".  Please wait.");
        } else if(graphOption.getValue() == NO_VALUES) {
            Dialogs.showMessageDialog("No data is available for expedition " + expeditionOption.getValue().getLabel());
        }

        List<OptionValue> fields = new ArrayList<OptionValue>();
        BiocodeFimsData data = getData(getWebTarget(expeditionOption.getValue().getName(), graphOption.getValue().getName()), "dontMatchThisStringReturnJustHeader");
        for (String fieldName : data.header) {
            fields.add(new OptionValue(TableFimsConnection.CODE_PREFIX + fieldName, fieldName));
        }
        return fields;
    }

    public WebTarget getWebTarget() {
        return getWebTarget(expeditionOption.getValue().getName(), graphOption.getValue().getName());
    }
}
