package com.biomatters.plugins.biocode.labbench.connection;

import com.biomatters.geneious.publicapi.components.Dialogs;
import com.biomatters.geneious.publicapi.components.GPanel;
import com.biomatters.geneious.publicapi.databaseservice.DatabaseServiceException;
import com.biomatters.geneious.publicapi.documents.XMLSerializable;
import com.biomatters.geneious.publicapi.documents.XMLSerializationException;
import com.biomatters.geneious.publicapi.documents.XMLSerializer;
import com.biomatters.geneious.publicapi.plugin.Options;
import com.biomatters.geneious.publicapi.utilities.ThreadUtilities;
import com.biomatters.plugins.biocode.BiocodeUtilities;
import com.biomatters.plugins.biocode.labbench.BiocodeService;
import com.biomatters.plugins.biocode.labbench.ConnectionException;
import com.biomatters.plugins.biocode.labbench.fims.FIMSConnection;
import com.biomatters.plugins.biocode.labbench.lims.LIMSConnection;
import org.jdom.Element;
import org.virion.jam.util.SimpleListener;

import javax.swing.*;
import javax.swing.event.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.*;
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

    private List<Connection> connections;
    private List<ListDataListener> listeners = new ArrayList<ListDataListener>();
    private int selectedConnection = -1;
    private boolean connectOnStartup = false;
    private JPanel centerPanel;
    private JList connectionsList;
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
        if(e == null) {
            throw new XMLSerializationException("You cannot construct a connection manager with a null element");
        }
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

        Options limsOptions = LIMSConnection.createConnectionOptions();

        Options loginOptions = new Options(BiocodeService.class);
        loginOptions.addChildOptions("fims", null, null, fimsOptions);
        loginOptions.addChildOptions("lims", null, null, limsOptions);
        loginOptions.restorePreferences();
        return Connection.forOld("My Default Connection", loginOptions.valuesToXML("root"));
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
                    okButton.set(BiocodeUtilities.getDialogOkButton(connectionsPanel));
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
                updateCenterPanel(okButton.get());
            }
        });
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

        ListSelectionListener selectionListener = new ListSelectionListener() {
            public void valueChanged(ListSelectionEvent e) {
                int newSelectedIndex = connectionsList.getSelectedIndex();
                boolean enabled = connections.size() > 0 && newSelectedIndex >= 0;
                removeButton.setEnabled(enabled);
                if (newSelectedIndex == selectedConnection) {
                    return;
                }
                if(okButton.get() != null) {
                    okButton.get().setEnabled(false);
                }
                selectedConnection = newSelectedIndex;
                updateCenterPanel(okButton.get());
            }
        };
        connectionsList.getSelectionModel().addListSelectionListener(selectionListener);
        selectionListener.valueChanged(null);   

        connectionsPanel.add(leftPanel, BorderLayout.WEST);
        connectionsPanel.add(centerPanel, BorderLayout.CENTER);

        Dialogs.DialogOptions dialogOptions = new Dialogs.DialogOptions(Dialogs.OK_CANCEL, "Biocode Connections", dialogParent);
        dialogOptions.setMaxWidth(Integer.MAX_VALUE);
        dialogOptions.setMaxHeight(Integer.MAX_VALUE);
        if(okButton.get() != null) {
            okButton.get().setEnabled(false);
        }
        updateCenterPanel(okButton.get());
        if(Dialogs.showDialog(dialogOptions, connectionsPanel).equals(Dialogs.OK)) {
            if(checkIfWeCanLogIn()) {
                return selectedConnection >= 0 ? connections.get(selectedConnection) : null;
            }
        }
        return null;
    }


    public Connection getCurrentlySelectedConnection() {
        if(selectedConnection >= 0 && selectedConnection < connections.size()) {
            return connections.get(selectedConnection);
        }
        return null;
    }

    public boolean checkIfWeCanLogIn() {
        if(selectedConnection >= 0 && selectedConnection < connections.size()) {
            final Connection conn = connections.get(selectedConnection);
                final Options passwordOptions = conn.getEnterPasswordOptions();
                if (passwordOptions != null) {
                    final AtomicBoolean dialogResult = new AtomicBoolean();
                    Runnable runnable = new Runnable() {
                        public void run() {
                            dialogResult.set(Dialogs.showOptionsDialog(passwordOptions, "Biocode Plugin", false));
                        }
                    };
                    ThreadUtilities.invokeNowOrWait(runnable);
                    if (!dialogResult.get()) {
                        return false;
                    }
                    conn.setPasswordsFromOptions(passwordOptions);
                    final AtomicReference<Exception> error = new AtomicReference<Exception>();
                    Runnable r = new Runnable() {
                        public void run() {
                            try {
                                conn.updateNowThatWeHaveAPassword();
                            } catch (ConnectionException e) {
                                Dialogs.showMessageDialog("Could not log in: " + e.getMessage());
                                error.set(e);
                            }
                        }
                    };
                    ThreadUtilities.invokeNowOrWait(r);
                    if (error.get() != null) {
                        return false;
                    }
                }
                return true;
            }
        return false;
    }

    public boolean connectOnStartup() {
        return connectOnStartup;
    }

    private void updateCenterPanel(JButton okButton) {
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
        JPanel chosenConnectionPanel = selectedConnection.getConnectionOptionsPanel(okButton);
        centerPanel.add(chosenConnectionPanel, BorderLayout.CENTER);
        ConnectionManager.packAncestor(chosenConnectionPanel);
        centerPanel.revalidate();
        if(selectedConnection.optionsCreated()) {
            packAncestor(centerPanel);
        }
    }

    public Element toXML() {
        Element root = new Element("ConnectionManager");
        for (int i = 0; i < connections.size(); i++) {
            root.addContent(connections.get(i).getXml(i == selectedConnection)); //reserialize just the new connections, and the one that we have selected
        }
        root.addContent(new Element("SelectedConnection").setText(""+selectedConnection));
        if(connectOnStartup) {
            root.setAttribute("connectOnStartup", "true");
        }
        return root;
    }

    public void fromXML(Element element) throws XMLSerializationException {
        List<Element> connectionElements = element.getChildren("Connection");  // todo this won't work now
        connectOnStartup = element.getAttribute("connectOnStartup") != null;
        connections = new ArrayList<Connection>();
        for(Element e : connectionElements) {
            try {
                Connection newConnection;
                if(XMLSerializable.ROOT_ELEMENT_NAME.equals(e.getName())) {
                    newConnection = XMLSerializer.classFromXML(e, Connection.class);
                } else {
                    newConnection = new Connection(e);
                }
                addConnection(newConnection);
            } catch (XMLSerializationException e1) {
                StringWriter stringWriter = new StringWriter();
                PrintWriter writer = new PrintWriter(stringWriter);
                e1.printStackTrace(writer);
                Dialogs.DialogOptions dialogOptions = new Dialogs.DialogOptions(Dialogs.OK_ONLY, "Error restoring Connection");
                dialogOptions.setMoreOptionsButtonText("Show details", "Hide details");
                Dialogs.showMoreOptionsDialog(dialogOptions, "There was an error restoring one or more of your saved connections from disk.  The affected connections will not be loaded.  Please send the detailed report to biocode.lims@gmail.com", stringWriter.toString());
            }
        }
        selectedConnection = Integer.parseInt(element.getChildText("SelectedConnection"));
        if(selectedConnection >= connectionElements.size()) {
            selectedConnection = connectionElements.size()-1;
        }
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

    static void packAncestor(final JComponent panel) {
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
}
