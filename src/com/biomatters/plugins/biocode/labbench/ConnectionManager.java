package com.biomatters.plugins.biocode.labbench;

import com.biomatters.geneious.publicapi.components.Dialogs;
import com.biomatters.geneious.publicapi.components.GLabel;
import com.biomatters.geneious.publicapi.components.GPanel;
import com.biomatters.geneious.publicapi.documents.XMLSerializable;
import com.biomatters.geneious.publicapi.documents.XMLSerializationException;
import com.biomatters.geneious.publicapi.plugin.Options;
import com.biomatters.geneious.publicapi.utilities.ThreadUtilities;
import com.biomatters.plugins.biocode.labbench.fims.FIMSConnection;
import com.biomatters.plugins.biocode.labbench.fims.TableFimsConnectionOptions;
import com.biomatters.plugins.biocode.labbench.fims.ExcelFimsConnectionOptions;
import com.biomatters.plugins.biocode.labbench.lims.LIMSConnection;
import org.jdom.Element;
import org.jdom.Attribute;
import org.jdom.output.XMLOutputter;
import org.jdom.output.Format;
import org.virion.jam.util.SimpleListener;

import javax.swing.*;
import javax.swing.event.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

/**
 * @author Steve
 * @version $Id$
 */
public class ConnectionManager implements XMLSerializable{
    private static LIMSConnection limsConnection = new LIMSConnection();

    private List<Connection> connections;
    private List<ListDataListener> listeners = new ArrayList<ListDataListener>();
    private int selectedConnection = -1;
    private boolean connectOnStartup = false;
    private JPanel centerPanel;
    private JList connectionsList;
    private Options sqlConnectorLocationOptions;
    private String sqlDirverLocation;
    private JButton removeButton;

    public ConnectionManager() {
        connections = new ArrayList<Connection>();
        Connection previousConnection = getConnectionFromPreviousVersion();
        if(previousConnection != null) {
            connections.add(previousConnection);
            selectedConnection = -1;
        }
    }

    public ConnectionManager(Element e) throws XMLSerializationException {
        fromXML(e);
    }

    private ListModel connectionsListModel = new ListModel(){
        public int getSize() {
            return connections.size();
        }

        public Object getElementAt(int index) {
            return connections.get(index);
        }

        public void addListDataListener(ListDataListener l) {
            listeners.add(l);
        }

        public void removeListDataListener(ListDataListener l) {
            listeners.remove(l);
        }
    };

    private void fireListListeners() {
        for(ListDataListener listener: listeners) {
            listener.contentsChanged(new ListDataEvent(connectionsListModel, ListDataEvent.CONTENTS_CHANGED, 0, connections.size()));
        }
    }

    private static Connection getConnectionFromPreviousVersion() {
        if(getPreferencesFromPreviousVersion() == null) {
            return null;
        }
        Options fimsOptions = new Options(BiocodeService.class);
        for (FIMSConnection connection : BiocodeService.getFimsConnections()) {
            fimsOptions.addChildOptions(connection.getName(), connection.getLabel(), connection.getDescription(), connection.getConnectionOptions() != null ? connection.getConnectionOptions() : new Options(BiocodeService.class));
        }
        fimsOptions.addChildOptionsPageChooser("fims", "Field Database Connection", Collections.<String>emptyList(), Options.PageChooserType.COMBO_BOX, false);

        Options limsOptions = limsConnection.getConnectionOptions();

        Options loginOptions = new Options(BiocodeService.class);
        loginOptions.addChildOptions("fims", null, null, fimsOptions);
        loginOptions.addChildOptions("lims", null, null, limsOptions);
        loginOptions.restorePreferences();
        return new Connection("My Default Connection", loginOptions.valuesToXML("root"));
    }

    private static Preferences getPreferencesFromPreviousVersion() {
        return getPreferences("/com/biomatters/geneious/publicapi/plugin/Options/com/biomatters/plugins/biocode/labbench/BiocodeService");
    }

    private static Preferences getPreferences(String preferenceNode) {
        Preferences preferences = Preferences.userRoot();
        for (String s : preferenceNode.split("\\.")) {
            try {
                if(!preferences.nodeExists(s)) {
                    return null;
                }
            } catch (BackingStoreException e) {
                return null;
            }
            preferences = preferences.node(s);
        }
        return preferences;
    }


    /**
     *
     * @param dialogParent a component for the connection dialog to be modal over (can be null)
     * @return The selected connection, if the user clicked connect, or null if the user clicked cancel
     */
    public Connection getConnectionFromUser(JComponent dialogParent) {
        final JPanel connectionsPanel = new GPanel(new BorderLayout());

        //the stuff on the LHS of the dialog...
        connectionsList = new JList(connectionsListModel);
        connectionsList.getSelectionModel().setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        if(selectedConnection >= 0) {
            connectionsList.setSelectedIndex(selectedConnection);
        }
        final AtomicReference<JButton> okButton = new AtomicReference<JButton>();

        ListSelectionListener selectionListener = new ListSelectionListener() {
            public void valueChanged(ListSelectionEvent e) {
                int newSelectedIndex = connectionsList.getSelectedIndex();
                boolean enabled = connections.size() > 0 && newSelectedIndex >= 0;
                removeButton.setEnabled(enabled);
                if(okButton.get() != null) {
                    okButton.get().setEnabled(enabled);
                }
                if (newSelectedIndex == selectedConnection) {
                    return;
                }
                selectedConnection = newSelectedIndex;               
                updateCenterPanel();
            }
        };
        connectionsList.getSelectionModel().addListSelectionListener(selectionListener);

        connectionsPanel.addAncestorListener(new AncestorListener(){
            public void ancestorAdded(AncestorEvent event) {
                setOkButtonEnabledness();

            }

            public void ancestorRemoved(AncestorEvent event) {
                setOkButtonEnabledness();
            }

            public void ancestorMoved(AncestorEvent event) {
                setOkButtonEnabledness();
            }

            private void setOkButtonEnabledness() {
                if(okButton.get() != null) {
                    okButton.get().setEnabled(connections.size() > 0 && selectedConnection >= 0);
                }
                else {
                    okButton.set(getPanelOkButton(connectionsPanel));
                }
            }
        });


        JPanel leftPanel = new GPanel(new BorderLayout());
        leftPanel.add(new JLabel("Connections"), BorderLayout.NORTH);
        JScrollPane scroller = new JScrollPane(connectionsList);
        scroller.setPreferredSize(connectionsList.getPreferredSize());
        scroller.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        scroller.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        leftPanel.add(scroller, BorderLayout.CENTER);
        JPanel leftBottomPanel = new GPanel(new BorderLayout());
        JPanel addRemovePanel = new GPanel(new FlowLayout());
        JButton addButton = new JButton("Add");
        addButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                addConnection(new Connection("Untitled"));
                fireListListeners();
                connectionsList.setSelectedIndex(connections.size()-1);
            }
        });
        addRemovePanel.add(addButton);
        removeButton = new JButton("Remove");
        removeButton.addActionListener(new ActionListener(){
            public void actionPerformed(ActionEvent e) {
                if(selectedConnection >= 0) {
                    connections.remove(selectedConnection);
                }
                selectedConnection--;
                if(connections.size() > 0) {
                    selectedConnection = Math.max(0, selectedConnection);
                }
                fireListListeners();
                updateCenterPanel();
            }
        });
        selectionListener.valueChanged(null);
        addRemovePanel.add(removeButton);
        final JCheckBox connectBox = new JCheckBox("Connect on startup", connectOnStartup);
        connectBox.addChangeListener(new ChangeListener(){
            public void stateChanged(ChangeEvent e) {
                connectOnStartup = connectBox.isSelected();
            }
        });
        leftBottomPanel.add(addRemovePanel, BorderLayout.CENTER);
        leftBottomPanel.add(connectBox, BorderLayout.SOUTH);
        leftPanel.add(leftBottomPanel, BorderLayout.SOUTH);

        int imageNumber = (int)(6*Math.random())+1;
        final Image introImage = Toolkit.getDefaultToolkit().createImage(getClass().getResource("biocode_intro"+imageNumber+".jpg"));

        centerPanel = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                if(selectedConnection == -1) {
                    g.drawImage(introImage,10,0,connectionsPanel);
                }
                else {
                    super.paintComponent(g);
                }
            }

            @Override
            public Dimension getPreferredSize() {
                if(selectedConnection == -1) {
                    return new Dimension(522,384);
                }
                return super.getPreferredSize();
            }
        };

        connectionsPanel.add(leftPanel, BorderLayout.WEST);
        connectionsPanel.add(centerPanel, BorderLayout.CENTER);

        createSqlOptions();

        Dialogs.DialogOptions dialogOptions = new Dialogs.DialogOptions(Dialogs.OK_CANCEL, "Biocode Connections", dialogParent);
        dialogOptions.setMaxWidth(Integer.MAX_VALUE);
        dialogOptions.setMaxHeight(Integer.MAX_VALUE);
        updateCenterPanel();
        if(Dialogs.showDialog(dialogOptions, connectionsPanel, sqlConnectorLocationOptions.getPanel()).equals(Dialogs.OK)) {
            sqlDirverLocation = sqlConnectorLocationOptions.getValueAsString("driver");
            if(checkIfWeCanLogIn()) {
                return selectedConnection >= 0 ? connections.get(selectedConnection) : null;
            }
        }
        return null;
    }

    private JButton getPanelOkButton(JPanel panel) {
        JRootPane rootPane = panel.getRootPane();
        if(rootPane != null) {
            return rootPane.getDefaultButton();
        }
        return null;
    }

    private void createSqlOptions() {
        sqlConnectorLocationOptions = new Options(ConnectionManager.class);
        String driverDefault;
        Preferences prefs = getPreferencesFromPreviousVersion();
        if(prefs != null) {
            driverDefault = prefs.get("driver", "");
        }
        else {
            driverDefault = "";
        }
        if(sqlDirverLocation == null) {
            sqlDirverLocation = driverDefault;
        }
        Options.FileSelectionOption driverOption = sqlConnectorLocationOptions.addFileSelectionOption("driver", "MySQL Driver:", driverDefault, new String[0], "Browse...", new FilenameFilter() {
            public boolean accept(File dir, String name) {
                return name.toLowerCase().endsWith(".jar");
            }
        });
        driverOption.setDescription("A file similar to \"mysql-connector-java-5.1.12-bin.jar\", available for download from http://dev.mysql.com/downloads/connector/j/");
        driverOption.setSelectionType(JFileChooser.FILES_ONLY);
        driverOption.setValue(sqlDirverLocation);
        sqlConnectorLocationOptions.restorePreferences();
    }

    public Connection getCurrentlySelectedConnection() {
        if(selectedConnection >= 0) {
            return connections.get(selectedConnection);
        }
        return null;
    }

    public boolean checkIfWeCanLogIn() {
        if(selectedConnection >= 0) {
            Connection conn = connections.get(selectedConnection);
            final Options passwordOptions = conn.getEnterPasswordOptions();
            if(passwordOptions != null) {
                final AtomicBoolean dialogResult = new AtomicBoolean();
                Runnable runnable = new Runnable() {
                    public void run() {
                        dialogResult.set(Dialogs.showOptionsDialog(passwordOptions, "Biocode Plugin", false));
                    }
                };
                ThreadUtilities.invokeNowOrWait(runnable);
                if(!dialogResult.get()) {
                    return false;
                }
                conn.setPasswordsFromOptions(passwordOptions);
            }
            return true;
        }
        return false;
    }

    public boolean connectOnStartup() {
        return connectOnStartup;
    }

    public String getSqlLocationOptions() {
        if(sqlConnectorLocationOptions == null) {
            createSqlOptions();
        }
        return ""+sqlConnectorLocationOptions.getValue("driver");
    }

    private void updateCenterPanel() {
        centerPanel.removeAll();
        if(selectedConnection < 0) {
            connectionsList.clearSelection();
            centerPanel.repaint();
            packAncestor(centerPanel);
            return;    
        }
        else {
            connectionsList.setSelectedIndex(selectedConnection);
        }

        Connection selectedConnection = connections.get(this.selectedConnection);
        centerPanel.add(selectedConnection.getConnectionOptionsPanel(), BorderLayout.CENTER);
        centerPanel.revalidate();
        if(selectedConnection.optionsCreated()) {
            packAncestor(centerPanel);
        }
    }

    public Element toXML() {
        Element root = new Element("ConnectionManager");
        for(int i=0; i < connections.size(); i++) {
            root.addContent(connections.get(i).getXml(i == selectedConnection)); //reserialize just the new connections, and the one that we have selected
        }
        root.addContent(new Element("SelectedConnection").setText(""+selectedConnection));
        if(connectOnStartup) {
            root.setAttribute("connectOnStartup", "true");
        }
        if(sqlDirverLocation != null) {
            root.addContent(new Element("driverLocation").setText(sqlDirverLocation));
        }
        return root;
    }

    public void fromXML(Element element) throws XMLSerializationException {
        List<Element> connectionElements = element.getChildren("Connection");
        connectOnStartup = element.getAttribute("connectOnStartup") != null;
        connections = new ArrayList<Connection>();
        for(Element e : connectionElements) {
            Connection newConnection = new Connection(e);
            addConnection(newConnection);
        }
        selectedConnection = Integer.parseInt(element.getChildText("SelectedConnection"));
        if(selectedConnection >= connectionElements.size()) {
            selectedConnection = connectionElements.size()-1;
        }
        sqlDirverLocation = element.getChildText("driverLocation");
    }

    private SimpleListener connectionNameChangedListener = new SimpleListener(){
        public void objectChanged() {
            fireListListeners();
        }
    };

    private void addConnection(Connection newConnection) {
        connections.add(newConnection);
        newConnection.addNameChangedListener(connectionNameChangedListener);
        connectionNameChangedListener.objectChanged();
    }

    private static void packAncestor(final JComponent panel) {
        Runnable runnable = new Runnable() {
            public void run() {
                JRootPane rootPane = panel.getRootPane();
                if(rootPane != null) {
                    Container frame = rootPane.getParent();
                    if(frame != null && frame instanceof Dialog) {
                        ((Dialog)frame).pack();
                    }
                    if(frame != null && frame instanceof Frame) {
                        ((Frame)frame).pack();
                    }
                }
            }
        };
        ThreadUtilities.invokeNowOrLater(runnable);
    }

    public static class Connection implements XMLSerializable{
        private LoginOptions loginOptions;
        private String name;
        Element loginOptionsValues;
        private List<SimpleListener> nameChangedListeners = new ArrayList<SimpleListener>();


        public Connection(String name) {
            this.name = name;
            //setLocationOptions();
            //loginOptions.restoreDefaults();
        }

        public Connection(Element e) throws XMLSerializationException{
            fromXML(e);
        }

        public Connection(String name, Element connectionOptions) {
            this.name = name;
            correctElementForBackwardsCompatibility(connectionOptions);
            loginOptions = new LoginOptions(ConnectionManager.class);
            loginOptions.valuesFromXML(connectionOptions);
            this.loginOptionsValues = connectionOptions;
        }

        public void correctElementForBackwardsCompatibility(Element e) {   //backwards compatibility - we moved some things to child options...
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

        private void setLocationOptions() {
            createLoginOptions();
        }

        public boolean optionsCreated() {
            return loginOptions != null;
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
            if(loginOptions == null) {
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
                    loginOptions.getPanel(); //we need to create the panel first so that password options keep their save/don't save status
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




        public JPanel getConnectionOptionsPanel(){  //we only set the values when the panel is actually required - constructing the options can take some time for large excel files...
            final JPanel panel = new GPanel(new BorderLayout());
            final Options nameOptions = new Options(ConnectionManager.class);
            final Options.StringOption nameOption = nameOptions.addStringOption("name", "Connection Name: ", "");
            nameOption.setValue(name);
            nameOption.addChangeListener(new SimpleListener(){
                public void objectChanged() {
                    setName(nameOption.getValue());
                }
            });
            if(loginOptions != null) {
                panel.add(loginOptions.getPanel());
                panel.add(nameOptions.getPanel(), BorderLayout.NORTH);
            }
            else {
                JPanel labelPanel = new GPanel(new GridBagLayout());
                AnimatedIcon activityIcon = AnimatedIcon.getActivityIcon();
                JLabel label = new GLabel("Loading connection options...", activityIcon, SwingConstants.CENTER);
                activityIcon.startAnimation();
                labelPanel.add(label, new GridBagConstraints());
                panel.add(labelPanel, BorderLayout.CENTER);

                Runnable runnable = new Runnable() {
                    public void run() {
                        ThreadUtilities.sleep(100); //let's give the UI a chance to update before we use up all the CPU
                        setLocationOptions();
                        Runnable runnable = new Runnable() {
                            public void run() {
                                if(loginOptionsValues != null) {
                                    createLoginOptions();
                                }
                                panel.removeAll();
                                panel.add(nameOptions.getPanel(), BorderLayout.NORTH);
                                panel.add(loginOptions.getPanel(), BorderLayout.CENTER);
                                panel.revalidate();
                                panel.invalidate();
                                packAncestor(panel);
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

        public Element getXml(boolean reserializeOptionsIfTheyExist) {
            Element connectionElement = new Element("Connection");
            if(loginOptions != null && (reserializeOptionsIfTheyExist || loginOptionsValues == null)) {
                connectionElement.addContent(loginOptions.valuesToXML("connectionOptions"));
            }
            else if(loginOptionsValues != null) {
                connectionElement.addContent((Element)loginOptionsValues.clone());
            }
            else {
                throw new RuntimeException("The connection "+getName()+" has no options or options values!");
            }
            connectionElement.addContent(new Element("Name").setText(name));
            return connectionElement;
        }

        public void fromXML(Element element) throws XMLSerializationException {
            loginOptions = null;
            loginOptionsValues = element.getChild("connectionOptions");
            correctElementForBackwardsCompatibility(loginOptionsValues);
            name = element.getChildText("Name");
        }


        public FIMSConnection getFimsConnection() {
            if(loginOptions == null) {
                createLoginOptions();
            }
            Options fimsOptions = loginOptions.getChildOptions().get("fims");
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

        public PasswordOptions getFimsOptions() {
            if(loginOptions == null) {
                createLoginOptions();
            }
            Options fimsOptions = loginOptions.getChildOptions().get("fims");
            String selectedFimsServiceName = fimsOptions.getValueAsString("fims");
            return (PasswordOptions)fimsOptions.getChildOptions().get(selectedFimsServiceName);
        }

        private void createLoginOptions() {
            loginOptions = new LoginOptions(ConnectionManager.class);
            if(loginOptionsValues != null) {
                ThreadUtilities.invokeNowOrWait(new Runnable() {
                    public void run() {
                        loginOptions.valuesFromXML(loginOptionsValues);
                        loginOptions.updateOptions();
                        loginOptions.valuesFromXML(loginOptionsValues);
                    }
                });
            }
        }

        public PasswordOptions getLimsOptions() {
            if(loginOptions == null) {
                createLoginOptions();
            }
            return (PasswordOptions)loginOptions.getChildOptions().get("lims");
        }
    }


}
