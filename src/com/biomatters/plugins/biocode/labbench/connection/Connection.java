package com.biomatters.plugins.biocode.labbench.connection;

import com.biomatters.geneious.publicapi.components.Dialogs;
import com.biomatters.geneious.publicapi.components.GLabel;
import com.biomatters.geneious.publicapi.components.GPanel;
import com.biomatters.geneious.publicapi.components.ProgressFrame;
import com.biomatters.geneious.publicapi.documents.XMLSerializable;
import com.biomatters.geneious.publicapi.documents.XMLSerializationException;
import com.biomatters.geneious.publicapi.plugin.Geneious;
import com.biomatters.geneious.publicapi.plugin.Options;
import com.biomatters.geneious.publicapi.utilities.ThreadUtilities;
import com.biomatters.plugins.biocode.labbench.*;
import com.biomatters.plugins.biocode.labbench.fims.ExcelFimsConnectionOptions;
import com.biomatters.plugins.biocode.labbench.fims.FIMSConnection;
import com.biomatters.plugins.biocode.labbench.fims.TableFimsConnectionOptions;
import com.biomatters.plugins.biocode.labbench.lims.LIMSConnection;
import com.biomatters.plugins.biocode.labbench.lims.LimsConnectionOptions;
import jebl.util.ProgressListener;
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
    private LoginOptions connectionOptions;
    private String name;

    // A decision was made a long time ago to handle preferences within the plugin rather than taking advantage of
    // Geneious core.  Unfortunately this means the connection Options are all stored in a giant XML
    // element stored on disk rather than taking advantage of having different nodes in preferences.  So we have to
    // keep track of the original XML elements for all Options when we re-serialize.
    private Element originalConnectionOptionsXml;

    private List<SimpleListener> nameChangedListeners = new ArrayList<SimpleListener>();
    protected String CONNECTION_OPTIONS_ELEMENT_NAME = "connectionOptions";

    public Connection(String name) {
        this.name = name;
    }

    public Connection(Element e) throws XMLSerializationException {
        if(e == null) {
            throw new XMLSerializationException("You cannot create a new connection with a null element");
        }
        originalConnectionOptionsXml = e.getChild(CONNECTION_OPTIONS_ELEMENT_NAME);
        if(originalConnectionOptionsXml == null) {
            throw new XMLSerializationException("The child element "+CONNECTION_OPTIONS_ELEMENT_NAME+" does not exist");
        }
        correctElementForBackwardsCompatibility(originalConnectionOptionsXml);
        name = e.getChildText(CONN_NAME);
    }

    /**
     * Used to restore a connection that has been stored in an older format before there could be multiple connections.
     * @param name Name to give the connection
     * @param oldFormatElement The {@link Element} to restore from
     * @return A Connection
     */
    public static Connection forOld(String name, Element oldFormatElement) {
        if (oldFormatElement == null) {
            throw new IllegalArgumentException("Connection options are null");
        }
        Connection toReturn = new Connection(name);
        correctElementForBackwardsCompatibility(oldFormatElement);
        toReturn.connectionOptions = new LoginOptions(ConnectionManager.class);
        toReturn.connectionOptions.valuesFromXML(oldFormatElement);
        toReturn.originalConnectionOptionsXml = oldFormatElement;

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
        return connectionOptions != null;
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
        if (connectionOptions == null) {
            createLoginOptions();
        }
        int count = 0;

        Options passwordOptions = new Options(this.getClass());
        passwordOptions.addLabel("Please enter your credentials for " + getName());
        PasswordOptions fimsOptions = getFimsOptions();
        Options fimsEnterPasswordOptions = fimsOptions.getEnterPasswordOptions();
        if (fimsEnterPasswordOptions != null) {
            passwordOptions.addChildOptions("fimsOptions", "FIMS account", "The connection options for your FIMS account", fimsEnterPasswordOptions);
            count++;
        }
        PasswordOptions limsOptions = getLimsOptions();
        Options limsEnterPasswordOptions = limsOptions.getEnterPasswordOptions();
        if (limsEnterPasswordOptions != null) {
            passwordOptions.addChildOptions("limsOptions", "LIMS account", "The connection options for your LIMS account", limsEnterPasswordOptions);
            count++;
        }

        if (count > 0) {
            return passwordOptions;
        }
        return null;
    }

    public void setPasswordsFromOptions(final Options enterPasswordOptions) {
        Runnable runnable = new Runnable() {
            public void run() {
                connectionOptions.getPanel(); //we need to create the panel first so that password options keep their save/don't save status
                Options fimsOptions = enterPasswordOptions.getChildOptions().get("fimsOptions");
                Options limsOptions = enterPasswordOptions.getChildOptions().get("limsOptions");
                if (fimsOptions != null) {
                    getFimsOptions().setPasswordsFromOptions(fimsOptions);
                }
                if (limsOptions != null) {
                    getLimsOptions().setPasswordsFromOptions(limsOptions);
                }
            }
        };
        ThreadUtilities.invokeNowOrWait(runnable);
    }

    private static final String METHOD = "method";

    public JPanel getConnectionOptionsPanel(final JButton button) {  //we only set the values when the panel is actually required - constructing the options can take some time for large excel files...
        final JPanel panel = new GPanel(new BorderLayout());
        final Options overallOptions = new Options(ConnectionManager.class);
        final Options.StringOption nameOption = overallOptions.addStringOption("name", "Connection Name: ", "");

        nameOption.setValue(name);
        nameOption.addChangeListener(new SimpleListener(){
            public void objectChanged() {
                setName(nameOption.getValue());
            }
        });

        if(connectionOptions != null) {
            panel.add(overallOptions.getPanel(), BorderLayout.NORTH);
            panel.add(connectionOptions.getPanel(), BorderLayout.CENTER);
            ConnectionManager.packAncestor(connectionOptions.getPanel());
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
                    Runnable runnable = new Runnable() {
                        public void run() {
                            createLoginOptions(panel, overallOptions.getPanel(), button);
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

    public Element toXML() { // todo: handle null returns at point of calls
        return getXml(true);
    }

    private static final String CONN_NAME = "Name";

    public Element getXml(boolean reserializeOptionsIfTheyExist) {
        Element connectionElement = new Element("Connection");

        if(connectionOptions == null) {
            connectionOptions = new LoginOptions(ConnectionManager.class);
            connectionOptions.restoreDefaults();
        }
        saveOptionValuesXml(connectionElement, CONNECTION_OPTIONS_ELEMENT_NAME, connectionOptions, originalConnectionOptionsXml, reserializeOptionsIfTheyExist);

        connectionElement.addContent(new Element(CONN_NAME).setText(name));

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
        if(connectionOptions == null) {
            createLoginOptions();
        }
        Options fimsOptions = connectionOptions.getChildOptions().get("fims");
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
        if(connectionOptions == null) {
            createLoginOptions();
        }
        Options fimsOptions = connectionOptions.getChildOptions().get("fims");
        String selectedFimsServiceName = fimsOptions.getValueAsString("fims");
        return (PasswordOptions)fimsOptions.getChildOptions().get(selectedFimsServiceName);
    }

    public void setFims(String name) {
        if(connectionOptions == null) {
            createLoginOptions();
        }
        connectionOptions.setValue("fims.fims", name);
    }

    private void createLoginOptions(final JPanel panel, final JPanel overallPanel, final JButton button) {
        connectionOptions = new LoginOptions(ConnectionManager.class);

        final ProgressListener progress;
        if(Geneious.isHeadless()) {
            progress = ProgressListener.EMPTY;
        } else {
            ProgressFrame progressFrame = new ProgressFrame("Loading Connection Options...", "", 0, true, Dialogs.getCurrentModalDialog());
            progressFrame.setCancelable(false);
            progress = progressFrame;
        }

        progress.setIndeterminateProgress();

        final Element loginOptionsValuesLocal = originalConnectionOptionsXml;

        if (loginOptionsValuesLocal != null) {
            connectionOptions.valuesFromXML((Element) loginOptionsValuesLocal.clone());
        }

        final Runnable finalPanelUpdate = new Runnable() {
            public void run() {
                if (loginOptionsValuesLocal != null) {
                    connectionOptions.valuesFromXML((Element) loginOptionsValuesLocal.clone());
                }

                if (panel != null && overallPanel != null) {
                    panel.removeAll();
                    panel.add(overallPanel, BorderLayout.NORTH);
                    panel.add(connectionOptions.getPanel(), BorderLayout.CENTER);
                    ConnectionManager.packAncestor(connectionOptions.getPanel());
                    panel.revalidate();
                    panel.invalidate();
                    ConnectionManager.packAncestor(panel);
                    if (button != null && panel.isShowing()) {
                        button.setEnabled(true);
                    }
                }
                progress.setProgress(1.0);
            }
        };


        Runnable initializeOptions = new Runnable() {
            @Override
            public void run() {
                try {
                    connectionOptions.prepare();

                    connectionOptions.preUpdateOptions();

                    if (loginOptionsValuesLocal != null) {
                        connectionOptions.valuesFromXML((Element) loginOptionsValuesLocal.clone());
                    }

                    connectionOptions.updateOptions();
                } catch (ConnectionException e) {
                    //todo: exception handling: exceptions here should also be thrown when you log in so handling this here is low priority
                    e.printStackTrace();
                }
            }
        };


        doSomethingInTheBackgroundThenSomethingElseInSwingThread(initializeOptions, finalPanelUpdate);
    }

    private void doSomethingInTheBackgroundThenSomethingElseInSwingThread(final Runnable backgroundSomething, final Runnable swingSomethingElse) {
        Runnable backgroundRunnableWrapper = new Runnable() {
            @Override
            public void run() {
                backgroundSomething.run();
                ThreadUtilities.invokeNowOrLater(swingSomethingElse);
            }
        };
        new Thread(backgroundRunnableWrapper, "Biocode: Background Task").start();
    }

    private void createLoginOptions() {
        createLoginOptions(null, null, null);
    }

    public LIMSConnection getLIMSConnection() throws ConnectionException {
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
        if(connectionOptions == null) {
            createLoginOptions();
        }
        return (PasswordOptions) connectionOptions.getChildOptions().get("lims");
    }

    public void updateNowThatWeHaveAPassword() throws ConnectionException {
        new Thread() {
            @Override
            public void run() {
                try {
                    connectionOptions.updateOptions();
                } catch (ConnectionException e) {
                    e.printStackTrace();
                }
            }
        }.start();

        if(originalConnectionOptionsXml != null) {
            connectionOptions.valuesFromXML(originalConnectionOptionsXml);
        }
    }
}