package com.biomatters.plugins.biocode.labbench.connection;

import com.biomatters.geneious.publicapi.components.GLabel;
import com.biomatters.geneious.publicapi.components.GPanel;
import com.biomatters.geneious.publicapi.documents.XMLSerializable;
import com.biomatters.geneious.publicapi.documents.XMLSerializationException;
import com.biomatters.geneious.publicapi.plugin.Options;
import com.biomatters.geneious.publicapi.utilities.ThreadUtilities;
import com.biomatters.plugins.biocode.labbench.*;
import com.biomatters.plugins.biocode.labbench.fims.ExcelFimsConnectionOptions;
import com.biomatters.plugins.biocode.labbench.fims.FIMSConnection;
import com.biomatters.plugins.biocode.labbench.fims.TableFimsConnectionOptions;
import com.biomatters.plugins.biocode.labbench.lims.LIMSConnection;
import com.biomatters.plugins.biocode.labbench.lims.LimsConnectionOptions;
import com.biomatters.plugins.biocode.labbench.rest.client.RESTConnectionOptions;
import com.biomatters.plugins.biocode.labbench.rest.client.ServerFimsConnection;
import com.biomatters.plugins.biocode.labbench.rest.client.ServerLimsConnection;
import org.jdom.Attribute;
import org.jdom.Element;
import org.virion.jam.util.SimpleListener;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
* @author Matthew Cheung
* @version $Id$
*          <p/>
*          Created on 25/03/14 2:55 PM
*/
public class Connection implements XMLSerializable {
    private LoginOptions directOptions;
    private RESTConnectionOptions serverOptions;
    private String name;
    private ConnectionType type = ConnectionType.direct;

    // A decision was made a long time ago to handle preferences within the plugin rather than taking advantage of
    // Geneious core.  Unfortunately this means the connection Options are all stored in a giant XML
    // element stored on disk rather than taking advantage of having different nodes in preferences.  So we have to
    // keep track of the original XML elements for all Options when we re-serialize.
    private Element originalDirectOptionsXml;
    private Element originalServerOptionsXml;


    private List<SimpleListener> nameChangedListeners = new ArrayList<SimpleListener>();
    protected String CONNECTION_OPTIONS_ELEMENT_NAME = "connectionOptions";
    private static final String SERVER_OPTIONS = "serverConnectionOptions";


    public Connection(String name) {
        this.name = name;
        serverOptions = new RESTConnectionOptions();
    }

    public Connection(Element e) throws XMLSerializationException {
        if(e == null) {
            throw new XMLSerializationException("You cannot create a new connection with a null element");
        }
        originalDirectOptionsXml = e.getChild(CONNECTION_OPTIONS_ELEMENT_NAME);
        if(originalDirectOptionsXml == null) {
            throw new XMLSerializationException("The child element "+CONNECTION_OPTIONS_ELEMENT_NAME+" does not exist");
        }
        correctElementForBackwardsCompatibility(originalDirectOptionsXml);
        name = e.getChildText(CONN_NAME);
        String typeText = e.getChildText(CONN_TYPE);
        if(typeText != null) {
            type = ConnectionType.valueOf(typeText);
        }

        serverOptions = new RESTConnectionOptions();
        Element serverOptionsElement = e.getChild(SERVER_OPTIONS);
        //noinspection StatementWithEmptyBody
        if(serverOptionsElement != null) {
            serverOptions.valuesFromXML(serverOptionsElement);
        } else {
            // Probably an old serialized Connection from before server connections were an option.
        }

    }

    /**
     * Used to restore a connection that has been stored in an older format before there could be multiple connections.
     * @param name Name to give the connection
     * @param oldFormatElement The {@link Element} to restore from
     * @return A Connection
     */
    public static Connection forOld(String name, Element oldFormatElement) {
        if(oldFormatElement == null) {
            throw new IllegalArgumentException("Connection options are null");
        }
        Connection toReturn = new Connection(name);
        correctElementForBackwardsCompatibility(oldFormatElement);
        toReturn.directOptions = new LoginOptions(ConnectionManager.class);
        toReturn.directOptions.valuesFromXML(oldFormatElement);
        toReturn.originalDirectOptionsXml = oldFormatElement;

        return toReturn;
    }

    public static void correctElementForBackwardsCompatibility(Element e) {   //backwards compatibility - we moved some things to child options...
        for (Element child : e.getChildren("childOption")) {
            if ("fims".equals(child.getAttributeValue("name"))) {
                boolean excelActive = false;
                for (Element fimsOptionElement : child.getChildren("option")) {
                    if("fims".equals(fimsOptionElement.getAttributeValue("name")) && "excel".equals(fimsOptionElement.getText())) {
                        excelActive = true;
                        break;
                    }
                }
                for (Element child2 : child.getChildren("childOption")) {
                    if ("excel".equals(child2.getAttributeValue("name"))) {
                        if (child2.getChild("childOption") == null) {
                            //now do the work...
                            Element newChildOption = new Element("childOption");
                            newChildOption.setAttribute("name", TableFimsConnectionOptions.CONNECTION_OPTIONS_KEY);
                            for (Element optionElement : child2.getChildren("option")) {
                                if (ExcelFimsConnectionOptions.FILE_LOCATION.equals(optionElement.getAttributeValue("name")) && excelActive) {
                                    Element element = new Element("option").setAttribute("name", ExcelFimsConnectionOptions.FILE_LOCATION).setText(optionElement.getText());
                                    for (Attribute a : (List<Attribute>) optionElement.getAttributes()) {
                                        element.setAttribute(a.getName(), a.getValue());
                                    }
                                    newChildOption.addContent(element);
                                }
                                if ("label_1".equals(optionElement.getAttributeValue("name"))) {
                                    optionElement.setAttribute("name", "label_0");
                                }
                                if ("label_2".equals(optionElement.getAttributeValue("name"))) {
                                    optionElement.setAttribute("name", "label_1");
                                }
                                if ("label_3".equals(optionElement.getAttributeValue("name"))) {
                                    optionElement.setAttribute("name", "label_2");
                                }
                            }
                            child2.addContent(newChildOption);
                        } else if (!excelActive) {
                            for (Element childOption : child2.getChildren("childOption")) {
                                if ("excel".equals(childOption.getAttributeValue("name"))) {
                                    for (Element optionElement : child2.getChildren("option")) {
                                        if (ExcelFimsConnectionOptions.FILE_LOCATION.equals(optionElement.getAttributeValue("name"))) {
                                            optionElement.setText("");
                                        }
                                    }
                                }
                            }
                        }
                    }
                    if ("Google".equals(child2.getAttributeValue("name"))) {
                        if (child2.getChild("childOption") == null) {
                            //now do the work...
                            Element newChildOption = new Element("childOption");
                            newChildOption.setAttribute("name", TableFimsConnectionOptions.CONNECTION_OPTIONS_KEY);
                            for (Element optionElement : child2.getChildren("option")) {

                                if ("label_1".equals(optionElement.getAttributeValue("name"))) {
                                    optionElement.setAttribute("name", "label_0");
                                }
                                if ("label_2".equals(optionElement.getAttributeValue("name"))) {
                                    optionElement.setAttribute("name", "label_1");
                                }
                                if ("label_3".equals(optionElement.getAttributeValue("name"))) {
                                    optionElement.setAttribute("name", "label_2");
                                }
                            }
                            child2.addContent(newChildOption);
                        }
                    }
                }
            }
        }
    }

    public void setName(String name) {
        this.name = name;
        fireNameChangedListeners();
    }

    public boolean optionsCreated() {
        return directOptions != null;
    }

    public void addNameChangedListener(SimpleListener l) {
        nameChangedListeners.add(l);
    }

    public void removeNameChangedListener(SimpleListener l) {
        nameChangedListeners.remove(l);
    }

    private void fireNameChangedListeners() {
        for(SimpleListener listener : nameChangedListeners) {
            listener.objectChanged();
        }
    }

    public Options getEnterPasswordOptions() {
        if(directOptions == null) {
            createLoginOptions();
        }
        int count = 0;

        Options passwordOptions = new Options(this.getClass());
        passwordOptions.addLabel("Please enter your credentials for "+getName());
        PasswordOptions fimsOptions = getFimsOptions();
        Options fimsEnterPasswordOptions = fimsOptions.getEnterPasswordOptions();
        if(fimsEnterPasswordOptions != null) {
            passwordOptions.addChildOptions("fimsOptions", "FIMS account", "The connection options for your FIMS account", fimsEnterPasswordOptions);
            count++;
        }
        PasswordOptions limsOptions = getLimsOptions();
        Options limsEnterPasswordOptions = limsOptions.getEnterPasswordOptions();
        if(limsEnterPasswordOptions != null) {
            passwordOptions.addChildOptions("limsOptions", "LIMS account", "The connection options for your LIMS account", limsEnterPasswordOptions);
            count++;
        }

        if(count > 0) {
            return passwordOptions;
        }
        return null;
    }

    public void setPasswordsFromOptions(final Options enterPasswordOptions) {
        Runnable runnable = new Runnable() {
            public void run() {
                directOptions.getPanel(); //we need to create the panel first so that password options keep their save/don't save status
                Options fimsOptions = enterPasswordOptions.getChildOptions().get("fimsOptions");
                Options limsOptions = enterPasswordOptions.getChildOptions().get("limsOptions");
                if(fimsOptions != null) {
                    getFimsOptions().setPasswordsFromOptions(fimsOptions);
                }
                if(limsOptions != null) {
                    getLimsOptions().setPasswordsFromOptions(limsOptions);
                }
            }
        };
        ThreadUtilities.invokeNowOrWait(runnable);
    }

    private static final String METHOD = "method";
    private static final Options.OptionValue DIRECT = new Options.OptionValue(ConnectionType.direct.name(), "Direct FIMS/LIMS");
    private static final Options.OptionValue SERVER = new Options.OptionValue(ConnectionType.server.name(), "Through Biocode Server");


    public JPanel getConnectionOptionsPanel(final JButton button){  //we only set the values when the panel is actually required - constructing the options can take some time for large excel files...
        final JPanel panel = new GPanel(new BorderLayout());
        final Options overallOptions = new Options(ConnectionManager.class);
        final Options.StringOption nameOption = overallOptions.addStringOption("name", "Connection Name: ", "");
        final Options.ComboBoxOption<Options.OptionValue> typeOption = overallOptions.addComboBoxOption(METHOD, "Connection Method:", new Options.OptionValue[]{DIRECT, SERVER}, DIRECT);

        nameOption.setValue(name);
        nameOption.addChangeListener(new SimpleListener(){
            public void objectChanged() {
                setName(nameOption.getValue());
            }
        });

        Options.OptionValue typeValue = type == ConnectionType.direct ? DIRECT : SERVER;
        typeOption.setValue(typeValue);
        typeOption.addChangeListener(new SimpleListener(){
            public void objectChanged() {
                type = ConnectionType.valueOf(typeOption.getValue().getName());
            }
        });

        final SimpleListener visListener = new SimpleListener() {
            @Override
            public void objectChanged() {
                boolean direct = overallOptions.getValue(METHOD) == DIRECT;
                JPanel toAdd = direct ? directOptions.getPanel() : serverOptions.getPanel();
                JPanel toRemove = direct ? serverOptions.getPanel() : directOptions.getPanel();
                panel.remove(toRemove);
                panel.add(toAdd, BorderLayout.CENTER);
                ConnectionManager.packAncestor(panel);
            }
        };
        typeOption.addChangeListener(visListener);

        if(directOptions != null) {
            panel.add(overallOptions.getPanel(), BorderLayout.NORTH);
            visListener.objectChanged();
            if(button != null) {
                button.setEnabled(true);
            }
        } else {
            // When loading from stored values
            JPanel labelPanel = new GPanel(new GridBagLayout());
            AnimatedIcon activityIcon = AnimatedIcon.getActivityIcon();
            JLabel label = new GLabel("Loading connection options...", activityIcon, SwingConstants.CENTER);
            activityIcon.startAnimation();
            labelPanel.add(label, new GridBagConstraints());
            panel.add(labelPanel, BorderLayout.CENTER);

            Runnable runnable = new Runnable() {
                public void run() {
                    ThreadUtilities.sleep(100); //let's give the UI a chance to update before we use up all the CPU
                    createLoginOptions();
                    Runnable runnable = new Runnable() {
                        public void run() {
                            if(originalDirectOptionsXml != null) {
                                createLoginOptions();
                            }
                            panel.removeAll();
                            panel.add(overallOptions.getPanel(), BorderLayout.NORTH);
                            visListener.objectChanged();
                            panel.revalidate();
                            panel.invalidate();
                            ConnectionManager.packAncestor(panel);
                            if(button != null && panel.isShowing()) {
                                button.setEnabled(true);
                            }
                        }
                    };
                    ThreadUtilities.invokeNowOrLater(runnable);
                }
            };
            new Thread(runnable).start();
        }
        return panel;
    }


    @Override
    public String toString() {
        return getName();
    }

    public String getName() {
        if(name == null || name.length() == 0) {
            return "Untitled";
        }
        return name;
    }

    public Element toXML() {
        return getXml(true);
    }

    private static final String CONN_NAME = "Name";
    private static final String CONN_TYPE = "Type";

    public Element getXml(boolean reserializeOptionsIfTheyExist) {
        Element connectionElement = new Element("Connection");

        if(directOptions == null) {
            directOptions = new LoginOptions(ConnectionManager.class);
            directOptions.restoreDefaults();
        }
        saveOptionValuesXml(connectionElement, CONNECTION_OPTIONS_ELEMENT_NAME, directOptions, originalDirectOptionsXml, reserializeOptionsIfTheyExist);
        saveOptionValuesXml(connectionElement, SERVER_OPTIONS, serverOptions, originalServerOptionsXml, reserializeOptionsIfTheyExist);

        connectionElement.addContent(new Element(CONN_NAME).setText(name));
        connectionElement.addContent(new Element(CONN_TYPE).setText(type.name()));

        return connectionElement;
    }

    private void saveOptionValuesXml(Element elementToAddTo, String elementName, Options options, Element originalElement, boolean reserializeOptionsIfTheyExist) {
        boolean useOptions = reserializeOptionsIfTheyExist || originalElement == null;
        if(useOptions && options == null) {
            throw new NullPointerException("Options null");
        }
        if(useOptions) {
            elementToAddTo.addContent(options.valuesToXML(elementName));
        } else {
            elementToAddTo.addContent(((Element) originalElement.clone()).setName(elementName));
        }
    }

    public void fromXML(Element element) throws XMLSerializationException {
        throw new UnsupportedOperationException("Call the constructor");
    }


    public FIMSConnection getFimsConnection() {
        if(type == ConnectionType.server) {
            return new ServerFimsConnection();
        }
        if(directOptions == null) {
            createLoginOptions();
        }
        Options fimsOptions = directOptions.getChildOptions().get("fims");
        String selectedFimsServiceName = fimsOptions.getValueAsString("fims");
        FIMSConnection activeFIMSConnection = null;
        for (FIMSConnection connection : BiocodeService.getFimsConnections()) {
            if (connection.getName().equals(selectedFimsServiceName)) {
                activeFIMSConnection = connection;
            }
        }
        if (activeFIMSConnection == null) {
            throw new RuntimeException("Could not find a FIMS connection called " + selectedFimsServiceName);
        }
        return activeFIMSConnection;
    }

    /**
     * Return the Options for the currently selected FIMS
     * @return
     */
    public PasswordOptions getFimsOptions() {
        if(type == ConnectionType.server) {
            return serverOptions;
        }

        if(directOptions == null) {
            createLoginOptions();
        }
        Options fimsOptions = directOptions.getChildOptions().get("fims");
        String selectedFimsServiceName = fimsOptions.getValueAsString("fims");
        return (PasswordOptions)fimsOptions.getChildOptions().get(selectedFimsServiceName);
    }

    public void setFims(String name) {
        if(directOptions == null) {
            createLoginOptions();
        }
        directOptions.setValue("fims.fims", name);
    }

    private void createLoginOptions() {
        directOptions = new LoginOptions(ConnectionManager.class);
        if(originalDirectOptionsXml != null) {
            final Element loginOptionsValuesLocal = originalDirectOptionsXml;
            ThreadUtilities.invokeNowOrWait(new Runnable() {
                public void run() {
                    directOptions.valuesFromXML((Element) loginOptionsValuesLocal.clone());
                    try {
                        directOptions.updateOptions();
                    } catch (ConnectionException e) {
                        //todo: exception handling: exceptions here should also be thrown when you log in so handling this here is low priority
                        e.printStackTrace();
                    }
                    directOptions.valuesFromXML((Element) loginOptionsValuesLocal.clone());
                }
            });
        }
    }

    public LIMSConnection getLIMSConnection() throws ConnectionException {
        if(type == ConnectionType.server) {
            return new ServerLimsConnection();
        }

        LimsConnectionOptions limsOptions = (LimsConnectionOptions) getLimsOptions();

        try {
            return (LIMSConnection) limsOptions.getSelectedLIMSType().getLimsClass().newInstance();
        } catch (InstantiationException e) {
            throw new ConnectionException("Could not instantiate LIMS connection: " + e.getMessage(), e);
        } catch (IllegalAccessException e) {
            throw new ConnectionException("Could not instantiate LIMS connection: " + e.getMessage(), e);
        }
    }

    /**
     *
     * @return the parent Options that contains all the child LIMSOptions
     */
    public PasswordOptions getLimsOptions() {
        if(type == ConnectionType.server) {
            return serverOptions;
        }

        if(directOptions == null) {
            createLoginOptions();
        }
        return (PasswordOptions) directOptions.getChildOptions().get("lims");
    }

    public void updateNowThatWeHaveAPassword() throws ConnectionException{
        directOptions.updateOptions();
        if(originalDirectOptionsXml != null) {
            directOptions.valuesFromXML(originalDirectOptionsXml);
        }
    }

    private static enum ConnectionType {
        direct,
        server
    }
}
