package com.biomatters.plugins.biocode.labbench;

import com.biomatters.geneious.privateApi.PrivateApiUtilities;
import com.biomatters.geneious.publicapi.components.Dialogs;
import com.biomatters.geneious.publicapi.databaseservice.*;
import com.biomatters.geneious.publicapi.documents.*;
import com.biomatters.geneious.publicapi.documents.sequence.SequenceDocument;
import com.biomatters.geneious.publicapi.plugin.*;
import com.biomatters.geneious.publicapi.utilities.GuiUtilities;
import com.biomatters.geneious.publicapi.utilities.StringUtilities;
import com.biomatters.geneious.publicapi.utilities.ThreadUtilities;
import com.biomatters.plugins.biocode.BiocodePlugin;
import com.biomatters.plugins.biocode.BiocodeUtilities;
import com.biomatters.plugins.biocode.assembler.annotate.AnnotateUtilities;
import com.biomatters.plugins.biocode.labbench.fims.*;
import com.biomatters.plugins.biocode.labbench.lims.LIMSConnection;
import com.biomatters.plugins.biocode.labbench.plates.GelImage;
import com.biomatters.plugins.biocode.labbench.plates.Plate;
import com.biomatters.plugins.biocode.labbench.reaction.*;
import com.biomatters.plugins.biocode.labbench.reporting.ReportingService;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.input.SAXBuilder;
import org.jdom.output.Format;
import org.jdom.output.XMLOutputter;
import org.virion.jam.framework.AbstractFrame;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.sql.*;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.prefs.Preferences;

/**
 * @author Steven Stones-Havas
 * @version $Id$
 *          <p/>
 *          Created on 23/02/2009 4:41:26 PM
 */
@SuppressWarnings({"ConstantConditions"})
public class BiocodeService extends PartiallyWritableDatabaseService {
    private boolean isLoggedIn = false;
    private FIMSConnection activeFIMSConnection;
    private LIMSConnection limsConnection = new LIMSConnection();
    private final String loggedOutMessage = "Right click on the " + getName() + " service in the service tree to log in.";
    private static Driver driver;
    private static Driver localDriver;
    private static BiocodeService instance = null;
    public static final Map<String, Image[]> imageCache = new HashMap<String, Image[]>();
    private File dataDirectory;
    private final Preferences preferences = Preferences.userNodeForPackage(BiocodeService.class);
    private ConnectionManager.Connection activeConnection;

    public static final DateFormat dateFormat = SimpleDateFormat.getDateInstance(SimpleDateFormat.MEDIUM);//synchronize access on this (it's not threadsafe!)
    public static final DateFormat XMLDateFormat = new SimpleDateFormat("yyyy MMM dd hh:mm:ss");

    private ConnectionManager connectionManager;
    private boolean loggingIn;
    ReportingService reportingService;
    private Thread disconnectCheckingThread;
    private static boolean driverLoaded;

    private BiocodeService() {
    }

    public void setDataDirectory(File dataDirectory) {
        this.dataDirectory = dataDirectory;
        try {
            buildCachesFromDisk();
        } catch (IOException e) {
            assert false : e.getMessage();
            loadEmptyCaches();
        } catch (JDOMException e) {
            assert false : e.getMessage();
            loadEmptyCaches();
        } catch (XMLSerializationException e) {
            assert false : e.getMessage();
            loadEmptyCaches();
        }
    }

    public File getDataDirectory() {
        return dataDirectory;
    }

    @Override
    public boolean canDeleteDocuments(List<AnnotatedPluginDocument> documents) {
        if(!License.isProVersion() || !BiocodeService.getInstance().isLoggedIn()) {
            return false;
        }
        for(AnnotatedPluginDocument doc : documents) {
            if(!PlateDocument.class.isAssignableFrom(doc.getDocumentClass()) && !SequenceDocument.class.isAssignableFrom(doc.getDocumentClass())) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void deleteDocuments(List<AnnotatedPluginDocument> documents) throws DatabaseServiceException {
        if(!License.isProVersion()) {
            throw new DatabaseServiceException("You need to be a pro user to delete Biocode documents", false);
        }
        if(!BiocodeService.getInstance().isLoggedIn()) {
            throw new DatabaseServiceException("You are not logged into the Biocode Service - you cannot delete biocode documents.", false);
        }
        deletePlates(documents);
        deleteSequences(documents);
    }

    public void deleteSequences(List<AnnotatedPluginDocument> documents) throws DatabaseServiceException {
        try {
            if(!deleteAllowed("assembly")) {
                throw new DatabaseServiceException("It appears that you do not have permission to delete sequence records.  Please contact your System Administrator for assistance", false);
            }
        } catch (SQLException e) {
            //this might not be a real error so I'm going to let it continue...
            e.printStackTrace();
            assert false : e.getMessage();
        }

        List<Integer> sequencesToDelete = new ArrayList<Integer>();
        List<AnnotatedPluginDocument> documentsWithNoIdField = new ArrayList<AnnotatedPluginDocument>();
        for(AnnotatedPluginDocument doc : documents) {
            if(SequenceDocument.class.isAssignableFrom(doc.getDocumentClass())) {
                Integer id = (Integer)doc.getFieldValue(AnnotateUtilities.LIMS_ID);
                if(id != null) {
                    sequencesToDelete.add(id);
                }
                else {
                    documentsWithNoIdField.add(doc);
                }
            }
        }

        if (!sequencesToDelete.isEmpty()) {
            StringBuilder sql = new StringBuilder("DELETE FROM assembly WHERE (");
            for (int i1 = 0; i1 < sequencesToDelete.size(); i1++) {
                sql.append("id=?");
                if(i1 < sequencesToDelete.size()-1) {
                    sql.append(" OR ");
                }
            }
            sql.append(")");
            try {
                PreparedStatement statement = limsConnection.getConnection().prepareStatement(sql.toString());


                for (int i1 = 0; i1 < sequencesToDelete.size(); i1++) {
                    Integer i = sequencesToDelete.get(i1);
                    statement.setInt(i1+1, i);
                }

                int notDeletedCount = sequencesToDelete.size() - statement.executeUpdate();
                if(notDeletedCount > 0) {
                    throw new DatabaseServiceException(notDeletedCount + " sequences were not deleted.", false);
                }
            }
            catch(SQLException e) {
                throw new DatabaseServiceException(e, "Could not delete sequences: "+e.getMessage(), true);
            }
        }

        if(documentsWithNoIdField.size() > 0) {
            throw new DatabaseServiceException("Some of your selected documents were not correctly annotated with LIMS data, and could not be deleted.  Please contact Biomatters for assistance.", false);
        }
    }

    public void deletePlates(List<AnnotatedPluginDocument> documents) throws DatabaseServiceException {
        try {
            if(!deleteAllowed("plate")) {
                throw new DatabaseServiceException("It appears that you do not have permission to delete plate records.  Please contact your System Administrator for assistance", false);
            }
        } catch (SQLException e) {
            //this might not be a real error so I'm going to let it continue...
            e.printStackTrace();
            assert false : e.getMessage();
        }
        List<Plate> platesToDelete = new ArrayList<Plate>();
        for(AnnotatedPluginDocument doc : documents) {
            if(PlateDocument.class.isAssignableFrom(doc.getDocumentClass())) {
                PlateDocument plateDocument = (PlateDocument)doc.getDocumentOrThrow(DatabaseServiceException.class);
                platesToDelete.add(plateDocument.getPlate());
            }
        }
        if(platesToDelete.size() == 0) {
            return;
        }
        final BlockingDialog blockingDialog = BlockingDialog.getDialog("Deleting documents", null);
        ThreadUtilities.invokeNowOrLater(new Runnable() {
            public void run() {
                blockingDialog.setVisible(true);
            }
        });
        try {
            for(Plate plate : platesToDelete) {
                deletePlate(blockingDialog, plate);
            }
        }
        catch (SQLException e) {
            throw new DatabaseServiceException(e.getMessage(), true);
        } finally {
            while (!blockingDialog.isVisible()) {
                ThreadUtilities.sleep(50);
            }
            ThreadUtilities.invokeNowOrLater(new Runnable() {
                public void run() {
                    blockingDialog.dispose();
                }
            });
        }
    }

    @Override
    public boolean canEditDocumentField(AnnotatedPluginDocument document, DocumentField field) {
        return !FimsSample.class.isAssignableFrom(document.getDocumentClass()) && field.getCode().equals(DocumentField.NAME_FIELD.getCode());
    }

    @Override
    public void editDocumentField(AnnotatedPluginDocument document, DocumentFieldAndValue newValue) throws DatabaseServiceException {
        System.out.println("tried to rename a document to "+newValue);
        if(newValue == null || newValue.getValue() == null) {
            return;
        }
        if(WorkflowDocument.class.isAssignableFrom(document.getDocumentClass())) {
            WorkflowDocument doc = (WorkflowDocument)document.getDocumentOrThrow(DatabaseServiceException.class);
            try {
                renameWorkflow(doc.getWorkflow().getId(), newValue.getValue().toString());
            } catch (SQLException e) {
                throw new DatabaseServiceException(e.getMessage(), true);
            }
        }

        if(PlateDocument.class.isAssignableFrom(document.getDocumentClass())) {
            PlateDocument doc = (PlateDocument)document.getDocumentOrThrow(DatabaseServiceException.class);
            try {
                renamePlate(doc.getPlate().getId(), newValue.getValue().toString());
            } catch (SQLException e) {
                throw new DatabaseServiceException(e.getMessage(), true);
            }
        }
    }


    private void renameWorkflow(int id, String newName) throws SQLException {
        String sql = "UPDATE workflow SET name=? WHERE id=?";
        PreparedStatement statement = limsConnection.getConnection().prepareStatement(sql);

        statement.setString(1, newName);
        statement.setInt(2, id);

        statement.executeUpdate();
        statement.close();
    }

    private void renamePlate(int id, String newName) throws SQLException{
        String sql = "UPDATE plate SET name=? WHERE id=?";
        PreparedStatement statement = limsConnection.getConnection().prepareStatement(sql);

        statement.setString(1, newName);
        statement.setInt(2, id);

        statement.executeUpdate();
        statement.close();
    }

    private void loadEmptyCaches() {
        cyclesequencingCocktails = Collections.emptyList();
        PCRCocktails = Collections.emptyList();
        PCRThermocycles = Collections.emptyList();
        cyclesequencingThermocycles = Collections.emptyList();
    }

    public static BiocodeService getInstance() {
        if(instance == null) {
            instance = new BiocodeService();
        }
        return instance;
    }

    public FIMSConnection getActiveFIMSConnection() {
        return activeFIMSConnection;
    }

    public LIMSConnection getActiveLIMSConnection() {
        return limsConnection;
    }

    public synchronized boolean isLoggedIn() {
        return isLoggedIn;
    }

    @Override
    public ExtendedSearchOption[] getExtendedSearchOptions(boolean isAdvancedSearch) {
        return new ExtendedSearchOption[] {
                new CheckboxSearchOption("tissueDocuments", "Tissues", true),
                new CheckboxSearchOption("workflowDocuments", "Workflows", true),
                new CheckboxSearchOption("plateDocuments", "Plates", true),
                new CheckboxSearchOption("sequenceDocuments", "Sequences", false)
        };
    }

    static FIMSConnection[] getFimsConnections() {
        return new FIMSConnection[] {
                new ExcelFimsConnection(),
                new FusionTablesFimsConnection(),
                new MySQLFimsConnection(),
                new MooreaFimsConnection(),
                new TAPIRFimsConnection()
        };
    }

    public static Class getDriverClass() {
        return driver != null ? driver.getClass() : null;
    }

    public static Driver getDriver() {
        if(driver == null && driverLoaded) {
            throw new IllegalStateException("A driver load was attempted, but the driver has not been loaded");
        }
        return driver;
    }

    public static Class getLocalDriverClass() {
        return localDriver.getClass();
    }

    public static Driver getLocalDriver() {
        return localDriver;
    }


    public QueryField[] getSearchFields() {
        List<QueryField> fieldList = new ArrayList<QueryField>();

        List<DocumentField> limsFields = limsConnection.getSearchAttributes();
        for(DocumentField field : limsFields) {
            Condition[] conditions;
            if(field.isEnumeratedField()) {
                conditions = new Condition[] {
                    Condition.EQUAL,
                    Condition.NOT_EQUAL
                };
            }
            else {
                conditions = limsConnection.getFieldConditions(field.getValueType());
            }
            fieldList.add(new QueryField(field, conditions));
        }

        if(activeFIMSConnection != null) {
            List<DocumentField> fimsAttributes = activeFIMSConnection.getSearchAttributes();
            if(fimsAttributes != null) {
                for(DocumentField field : fimsAttributes) {
                    Condition[] conditions = activeFIMSConnection.getFieldConditions(field.getValueType());
                    fieldList.add(new QueryField(field, conditions));
                }
            }
        }


        return fieldList.toArray(new QueryField[fieldList.size()]);
    }


    @Override
    public List<GeneiousAction> getActionsAlwaysEnabled() {
        List<GeneiousAction> actions = new ArrayList<GeneiousAction>();

        GeneiousAction logoutAction = new GeneiousAction("logout", "Log out") {
            public void actionPerformed(ActionEvent e) {
                logOut();
            }
        }.setInPopupMenu(true);

        GeneiousAction loginAction = new GeneiousAction("login", "Log in") {
            public void actionPerformed(ActionEvent e) {
                logIn();
            }
        }.setInPopupMenu(true);
        if(isLoggedIn) {
            actions.add(logoutAction);
        }
        else if(!loggingIn){
            actions.add(loginAction);
        }

        return actions;
    }

    private void logOut() {
        synchronized (this) {
            isLoggedIn = false;
            loggingIn = false;
        }
        for(BiocodeCallback callback : activeCallbacks) {
            callback.cancel();
        }
        activeCallbacks.clear();
        activeConnection = null;
        if(activeFIMSConnection != null) {
            activeFIMSConnection.disconnect();
            activeFIMSConnection = null;
        }
        limsConnection.disconnect();
        if(reportingService != null) {
            reportingService.notifyLoginStatusChanged();
        }
        updateStatus();
    }

    public ReportingService getReportingService() {
        return reportingService;
    }

    private void saveConnectionManager() throws IOException {
        File file = new File(dataDirectory, "connectionManager.xml");
        XMLOutputter out = new XMLOutputter(Format.getPrettyFormat());
        out.output(connectionManager.toXML(), new FileOutputStream(file));
    }

    private void logIn() {
        final ConnectionManager.Connection connection = connectionManager.getConnectionFromUser(null);

        if (connection != null) {
            try {
                saveConnectionManager();
            } catch (IOException e) {
                e.printStackTrace();
                //todo: error handling
            }

            //try to connect to the selected service
            Runnable runnable = new Runnable() {
                public void run() {
                    connect(connection, true);
                }

            };
            new Thread(runnable).start();
        }
        else {
            initializeConnectionManager(); //undo changes...
        }
    }

    private void connect(ConnectionManager.Connection connection, boolean block) {
        synchronized (this) {
            loggingIn = true;
        }
        activeConnection = connection;
        //load the connection driver -------------------------------------------------------------------
        String driverFileName = connectionManager.getSqlLocationOptions();
        try {
            new XMLOutputter().output(connection.getXml(true), System.out);
        } catch (IOException e) {
            e.printStackTrace();
        }

        String error = null;

        if(!limsConnection.isLocal(connection.getLimsOptions()) || connection.getFimsConnection().requiresMySql()) {
            error = loadMySqlDriver(block, driverFileName);
        }

        try {
            Class driverClass = getClass().getClassLoader().loadClass("org.hsqldb.jdbc.JDBCDriver");
            localDriver = (Driver) driverClass.newInstance();
        } catch (ClassNotFoundException e1) {
            error = "Could not find HSQL driver class";
        } catch (IllegalAccessException e1) {
            error = "Could not access HSQL driver class";
        } catch (InstantiationException e1) {
            error = "Could not instantiate HSQL driver class";
        } catch (ClassCastException e1) {
            error = "HSQL Driver class exists, but is not an SQL driver";
        }

        if (error != null) {
            if(block) {
                Dialogs.showMessageDialog(error);
            }
            logOut();
            return;
        }

        //get the selected fims service.
        activeFIMSConnection = connection.getFimsConnection();
        if(block) {
            block("Connecting to the FIMS", null);
        }
        try {
            activeFIMSConnection.connect(connection.getFimsOptions());
        }
        catch (ConnectionException ex) {
            if(block) {
                unBlock();
            }
            if(ex != ConnectionException.NO_DIALOG) {
                String message = ex.getMainMessage() == null ? "There was an error connecting to "+activeFIMSConnection.getLabel() : ex.getMainMessage();
                if(ex.getMessage() != null) {
                    Dialogs.showMoreOptionsDialog(new Dialogs.DialogOptions(new String[] {"OK"},"Error connecting to FIMS"), message, ex.getMessage());
                }
                else {
                    Dialogs.showMessageDialog(message, "Error connecting to FIMS");
                }
            }
            logOut();
            return;
        }

        try {
            if(!(activeFIMSConnection instanceof MooreaFimsConnection) && connection.getLimsOptions().getValueAsString("server").equalsIgnoreCase("darwin.berkeley.edu")) {
                Dialogs.showMessageDialog("You cannot connect to the Moorea Lab Bench database using a field database other than the Moorea FIMS");
                logOut();
                if(block) {
                    unBlock();
                }
                return;
            }

            if(block) {
                block("Connecting to the LIMS", null);
            }

            if(disconnectCheckingThread != null) {
                disconnectCheckingThread.interrupt();
            }

            limsConnection.connect(connection.getLimsOptions());
            if(block) {
                block("Building Caches", null);
            }
            buildCaches();
            synchronized (this) {
                isLoggedIn = true;
                loggingIn = false;
            }
            if(reportingService != null) {
                reportingService.notifyLoginStatusChanged();
            }
            disconnectCheckingThread = getDisconnectCheckingThread();
            disconnectCheckingThread.start();
        } catch (ConnectionException e1) {
            if(block) {
                unBlock();
            }
            logOut();
            if(e1 == ConnectionException.NO_DIALOG) {
                return;
            }
            String title = "Connection Failure";
            String message = "Geneious could not connect to the LIMS database";
            showErrorDialog(e1, title, message);
            return;
        } catch (TransactionException e2) {
            logOut();
            if(block) {
                unBlock();
            }
            String title = "Connection Failure";
            String message = "Geneious could not connect to the LIMS database";
            showErrorDialog(e2, title, message);
            return;
        }
        if(block) {
            unBlock();
        }
        updateStatus();
    }

    private String loadMySqlDriver(boolean block, String driverFileName) {
        driverLoaded = true;
        ClassLoader loader = getClass().getClassLoader();
        String error = null;
        try {
            File driverFile = new File(driverFileName);
            if(!driverFile.exists() || driverFile.isDirectory()) {
               error = "Could not find the file "+driverFileName+".  Please check that this file exists, and is not a directory.";
            }
            else {
                URL driverUrl = driverFile.toURL();
                loader = new URLClassLoader(new URL[]{driverUrl}, loader);
            }
        } catch (MalformedURLException ex) {
            if(block) {
                error = "Could not load the MySql Driver!";
            }
        }


        if(error == null) {
            try {
                Class driverClass = loader.loadClass("com.mysql.jdbc.Driver");
                driver = (Driver) driverClass.newInstance();
            } catch (ClassNotFoundException e1) {
                error = "Could not find MySQL driver class";
            } catch (IllegalAccessException e1) {
                error = "Could not access MySQL driver class";
            } catch (InstantiationException e1) {
                error = "Could not instantiate MySQL driver class";
            } catch (ClassCastException e1) {
                error = "MySQL Driver class exists, but is not an SQL driver";
            }
        }
        return error;
    }

    private void showErrorDialog(Throwable e1, String title, String message) {
        Dialogs.DialogOptions dialogOptions = new Dialogs.DialogOptions(new String[]{"OK"}, title);
        dialogOptions.setMoreOptionsButtonText("Show details", "Hide details");
        if(e1.getMessage() == null) {
            Dialogs.showMessageDialog(message, title);
        }
        else if(e1.getMessage().contains("\n")) {
            Dialogs.showMoreOptionsDialog(dialogOptions, message, e1.getMessage());
        }
        else {
            Dialogs.showMessageDialog(message+": "+e1.getMessage(), title);
        }
    }

    public void updateStatus() {
        Runnable runnable = new Runnable() {
            public void run() {
                for(final DatabaseServiceListener listener : getDatabaseServiceListeners()) {
                    boolean isLoggedIn = isLoggedIn();
                    listener.searchableStatusChanged(isLoggedIn, isLoggedIn ? "Logged in" : loggedOutMessage);
                    listener.extendedSearchOptionsChanged();
                    listener.fieldsChanged();
                    listener.actionsChanged();
                }
                getGeneiousServiceListener().iconsChanged();
            }
        };
        ThreadUtilities.invokeNowOrWait(runnable);
    }

    public Thread getDisconnectCheckingThread() {
        return new Thread("Checking for the LIMS connection being closed") {
            @Override
            public void run() {
                while(isLoggedIn() && limsConnection != null) {
                    if(activeCallbacks.isEmpty()) {
                        try {
                            limsConnection.getConnection().createStatement().execute(limsConnection.isLocal() ? "SELECT * FROM databaseversion" : "SELECT 1"); //because JDBC doesn't have a better way of checking whether a connection is enabled
                        } catch (SQLException e) {
                            if(!e.getMessage().contains("Streaming result set")) {  //last ditch attempt to stop the system logging users out incorrectly - we should have caught all cases of this because the operations creating streaming result sets should have registered their callbacks/progress listeners with the service
                                e.printStackTrace();
                                if(isLoggedIn()) {
                                    logOut();
                                }
                            }
                        }
                    }
                    ThreadUtilities.sleep(10000);
                }
            }
        };
    }

    @Override
    public void addDatabaseServiceListener(DatabaseServiceListener listener) {
        super.addDatabaseServiceListener(listener);
        listener.searchableStatusChanged(isLoggedIn, loggedOutMessage);
    }

    public void retrieve(Query query, RetrieveCallback callback, URN[] urnsToNotRetrieve) throws DatabaseServiceException {
        retrieve(query, callback, urnsToNotRetrieve, false);
    }

    private Set<BiocodeCallback> activeCallbacks = new HashSet<BiocodeCallback>();

    private void retrieve(Query query, RetrieveCallback rc, URN[] urnsToNotRetrieve, boolean hasAlreadyTriedReconnect) throws DatabaseServiceException {
        BiocodeCallback callback = null;
        if(rc != null) {
            callback = new BiocodeCallback(rc);
            activeCallbacks.add(callback);
        }
        try {
            List<FimsSample> tissueSamples = null;
            List<Query> fimsQueries = new ArrayList<Query>();
            List<Query> limsQueries = new ArrayList<Query>();
            callback.setIndeterminateProgress();


            if(query instanceof CompoundSearchQuery) {
                CompoundSearchQuery masterQuery = (CompoundSearchQuery) query;
                for(Query childQuery : masterQuery.getChildren()) {
                    if((callback != null && callback.isCanceled()) || activeFIMSConnection == null) {
                        return;
                    }
                    if(childQuery instanceof AdvancedSearchQueryTerm && activeFIMSConnection.getSearchAttributes().contains(((AdvancedSearchQueryTerm)childQuery).getField())) {
                        fimsQueries.add(childQuery);//todo: distinguish between queries from multiple FIMS connections
                    }
                    else {
                        limsQueries.add(childQuery);
                    }
                }
                if(fimsQueries.size() > 0) {
                    Query compoundQuery;
                    if(masterQuery.getOperator() == CompoundSearchQuery.Operator.AND) {
                        compoundQuery = Query.Factory.createAndQuery(fimsQueries.toArray(new Query[fimsQueries.size()]), Collections.<String, Object>emptyMap());
                    }
                    else {
                        compoundQuery = Query.Factory.createOrQuery(fimsQueries.toArray(new Query[fimsQueries.size()]), Collections.<String, Object>emptyMap());
                    }
                    try {
                        callback.setMessage("Downloading Tissues");
                        if((callback != null && callback.isCanceled()) || activeFIMSConnection == null) {
                            return;
                        }
                        tissueSamples = activeFIMSConnection.getMatchingSamples(compoundQuery);
                    } catch (ConnectionException e) {
                        throw new DatabaseServiceException(e.getMessage(), false);
                    }
                }
            }
            else if(query instanceof BasicSearchQuery){
                try {
                    callback.setMessage("Downloading Tissues");
                    if((callback != null && callback.isCanceled()) || activeFIMSConnection == null) {
                        return;
                    }
                    tissueSamples = activeFIMSConnection.getMatchingSamples(query);
                } catch (ConnectionException e) {
                    throw new DatabaseServiceException(e, e.getMessage(), false);
                }
                fimsQueries.add(query);
                limsQueries.add(query);
            } else if(query instanceof AdvancedSearchQueryTerm){
                if((callback != null && callback.isCanceled()) || activeFIMSConnection == null) {
                    return;
                }
                if(activeFIMSConnection.getSearchAttributes().contains(((AdvancedSearchQueryTerm)query).getField())) {
                    fimsQueries.add(query);
                    try {
                        callback.setMessage("Downloading Tissues");
                        tissueSamples = activeFIMSConnection.getMatchingSamples(query);
                    } catch (ConnectionException e) {
                        throw new DatabaseServiceException(e.getMessage(), false);
                    }
                }
                else {
                    limsQueries.add(query);
                }
            }
            if(callback.isCanceled()) {
                return;
            }

            if(tissueSamples != null && (Boolean)query.getExtendedOptionValue("tissueDocuments")) {
                for(FimsSample sample : tissueSamples) {
                    TissueDocument doc = new TissueDocument(sample);
                    callback.add(doc, Collections.<String, Object>emptyMap());
                }
            }
            if(callback.isCanceled()) {
                return;
            }
            try {
                List<WorkflowDocument> workflowList = Collections.emptyList();
                boolean isAnd = true;
                if(query instanceof CompoundSearchQuery) {
                    isAnd = ((CompoundSearchQuery)query).getOperator() == CompoundSearchQuery.Operator.AND;
                }
                Query limsQuery = isAnd ? Query.Factory.createAndQuery(limsQueries.toArray(new Query[limsQueries.size()]), Collections.<String, Object>emptyMap()) : Query.Factory.createOrQuery(limsQueries.toArray(new Query[limsQueries.size()]), Collections.<String, Object>emptyMap());

                if((Boolean)query.getExtendedOptionValue("workflowDocuments") || (Boolean)query.getExtendedOptionValue("plateDocuments")) {
                    callback.setMessage("Downloading Workflows");
                    workflowList = limsConnection.getMatchingWorkflowDocuments(limsQuery, tissueSamples, (Boolean)query.getExtendedOptionValue("workflowDocuments") ? callback : null, callback);
                }
                if(callback.isCanceled()) {
                    return;
                }

                Set<WorkflowDocument> workflowsToSearch = new LinkedHashSet<WorkflowDocument>();
                //workflowsToSearch.addAll(workflowList);
    //            if((Boolean)query.getExtendedOptionValue("workflowDocuments")) {
    //                for(PluginDocument doc : workflowList) {
    //                    callback.add(doc, Collections.<String, Object>emptyMap());
    //                }
    //            }
                if(callback.isCanceled()) {
                    return;
                }
                if((Boolean)query.getExtendedOptionValue("plateDocuments") || ((Boolean)query.getExtendedOptionValue("sequenceDocuments") && limsQueries.size() > 0)) {
                    callback.setMessage("Downloading Plates");
                    List<PlateDocument> plateList = limsConnection.getMatchingPlateDocuments(limsQuery, workflowList, (Boolean)query.getExtendedOptionValue("plateDocuments") ? callback : null, callback);
                    if(callback.isCanceled()) {
                        return;
                    }
                    for(PlateDocument plate : plateList) {
                        for(Reaction r : plate.getPlate().getReactions()) {
                            if(r.getWorkflow() != null) {
                                workflowsToSearch.add(new WorkflowDocument(r.getWorkflow(), Collections.<Reaction>emptyList()));
                            }
                        }
                    }
                }
                if(query.getExtendedOptionValue("sequenceDocuments") != null && (Boolean)query.getExtendedOptionValue("sequenceDocuments")) {
                    callback.setMessage("Downloading Sequences");
                    if((tissueSamples != null && tissueSamples.size() > 0) && (workflowList == null || workflowList.size() == 0)) {
                        limsConnection.getMatchingAssemblyDocumentsForTissues(limsQuery, tissueSamples, callback, urnsToNotRetrieve, callback);
                    }
                    else {
                        limsConnection.getMatchingAssemblyDocuments(limsQuery, workflowsToSearch, callback, urnsToNotRetrieve, callback);
                    }
                }

            } catch (SQLException e) {
                e.printStackTrace();
                String message = e.getMessage();
                boolean isNetwork = true;
                if(message != null && message.contains("Streaming result") && message.contains("is still active")) {
                    if(!hasAlreadyTriedReconnect) {
                        try {
                            System.out.println("attempting a reconnect...");
                            limsConnection.reconnect();
                        } catch (ConnectionException e1) {
                            throw new DatabaseServiceException(e1, "Your previous search did not cancel properly, and Geneious was unable to correct the problem.  Try logging out, and logging in again.\n\n"+message, false);
                        }
                        retrieve(query, callback, urnsToNotRetrieve, true);
                        return;
                    }
                    else {
                        message = "Your previous search did not cancel properly.  Try logging out, and logging in again.\n\n"+message;
                        isNetwork = false;
                    }
                }
                throw new DatabaseServiceException(e, message, isNetwork);
            }

        }
        finally {
            if(callback != null) {
                activeCallbacks.remove(callback);
            }
        }
    }

    static final String UNIQUE_ID = "BiocodeService";

    public String getUniqueID() {
        return UNIQUE_ID;
    }

    public String getName() {
        return "Biocode";
    }

    public String getDescription() {
        return isLoggedIn() ? "Connected to "+activeConnection.getName() : "Not connected";
    }

    public String getHelp() {
        return null;
    }

    public Icons getIcons() {
        return isLoggedIn() ? BiocodePlugin.getIcons("biocode16_connected.png") : BiocodePlugin.getIcons("biocode16_disconnected.png");
    }

    public void addNewCocktails(List<? extends Cocktail> newCocktails) throws TransactionException{
        if(newCocktails.size() > 0) {
            for(Cocktail cocktail : newCocktails) {
                limsConnection.executeUpdate(cocktail.getSQLString());
            }
        }
        buildCaches();
    }

    public boolean deleteAllowed(String tableName) throws SQLException{
        if(!isLoggedIn) {
            throw new SQLException("You need to be logged in");
        }
        if(limsConnection.isLocal() || limsConnection.getUsername().toLowerCase().equals("root")) {
            return true;
        }

        try {
            //check schema privileges
            String schemaSql = "select * from information_schema.SCHEMA_PRIVILEGES WHERE GRANTEE LIKE ? AND PRIVILEGE_TYPE='DELETE' AND TABLE_SCHEMA=?;";
            PreparedStatement statement = limsConnection.getConnection().prepareStatement(schemaSql);
            statement.setString(1, "'"+limsConnection.getUsername()+"'@%");
            statement.setString(2, limsConnection.getSchema());
            ResultSet resultSet = statement.executeQuery();
            if(resultSet.next()) {
                return true;
            }
            resultSet.close();

            //check table privileges
            String tableSql = "select * from information_schema.TABLE_PRIVILEGES WHERE GRANTEE LIKE ? AND PRIVILEGE_TYPE='DELETE' AND TABLE_SCHEMA=? AND TABLE_NAME=?;";
            statement = limsConnection.getConnection().prepareStatement(tableSql);
            statement.setString(1, "'"+limsConnection.getUsername()+"'@%");
            statement.setString(2, limsConnection.getSchema());
            statement.setString(3, tableName);
            resultSet = statement.executeQuery();
            if(resultSet.next()) {
                return true;
            }
            resultSet.close();
        }
        catch(SQLException ex) {
            ex.printStackTrace();
            assert false : ex.getMessage();
            // there could be a number of errors here due to the user not having privileges to access the schema information,
            // so I don't want to halt on this error as it could stop the user from deleting when they are actually allowed...
        }

        //Can't find privileges...
        return false;
    }

    public void removeCocktails(List<? extends Cocktail> deletedCocktails) throws TransactionException{
        if(deletedCocktails == null || deletedCocktails.size() == 0) {
            return;
        }
        try {
            if(!BiocodeService.getInstance().deleteAllowed("cocktail")) {
                throw new TransactionException("It appears that you do not have permission to delete cocktails.  Please contact your System Administrator for assistance");
            }
            for(Cocktail cocktail : deletedCocktails) {

                String sql = "DELETE FROM "+cocktail.getTableName()+" WHERE id = ?";
                PreparedStatement statement = limsConnection.getConnection().prepareStatement(sql);           
                if(cocktail.getId() >= 0) {
                    if(getPlatesUsingCocktail(cocktail).size() > 0) {
                        throw new TransactionException("The cocktail "+cocktail.getName()+" is in use by reactions in your database.  Only unused cocktails can be removed.");
                    }
                    statement.setInt(1, cocktail.getId());
                    statement.executeUpdate();
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            throw new TransactionException("Could not delete cocktails: "+e.getMessage(), e);
        }
        buildCaches();
    }

    public Collection<String> getPlatesUsingCocktail(Cocktail cocktail) throws SQLException{
        if(limsConnection == null) {
            throw new SQLException("You are not logged in");
        }
        if(cocktail.getId() < 0) {
            return Collections.emptyList();
        }
        String tableName;
        switch(cocktail.getReactionType()) {
            case PCR:
                tableName = "pcr";
                break;
            case CycleSequencing:
                tableName = "cyclesequencing";
                break;
            default:
                throw new RuntimeException(cocktail.getReactionType()+" reactions cannot have a cocktail");
        }
        String sql = "SELECT plate.name FROM plate, "+tableName+" WHERE "+tableName+".plate = plate.id AND "+tableName+".cocktail = "+cocktail.getId();
        ResultSet resultSet = BiocodeService.getInstance().getActiveLIMSConnection().getConnection().createStatement().executeQuery(sql);

        Set<String> plateNames = new LinkedHashSet<String>();
        while(resultSet.next()) {
            plateNames.add(resultSet.getString(1));
        }
        return plateNames;
    }

    private List<Thermocycle> PCRThermocycles = null;
    private List<Thermocycle> cyclesequencingThermocycles = null;
    private List<PCRCocktail> PCRCocktails = null;
    private List<CycleSequencingCocktail> cyclesequencingCocktails = null;
    private List<DisplayFieldsTemplate> extractionDisplayedFields = null;
    private List<DisplayFieldsTemplate> pcrDisplayedFields = null;
    private List<DisplayFieldsTemplate> cycleSequencingDisplayedFields = null;

    private void buildCaches() throws TransactionException {
        try {
            buildCachesFromDisk();
        } catch (IOException e) {
            throw new TransactionException("Could not read the caches from disk", e);
        } catch (JDOMException e) {
            throw new TransactionException("Could not read the caches from disk", e);
        } catch (XMLSerializationException e) {
            throw new TransactionException("Could not read the caches from disk", e);
        }
        PCRThermocycles = getThermocyclesFromDatabase("pcr_thermocycle");
        cyclesequencingThermocycles = getThermocyclesFromDatabase("cyclesequencing_thermocycle");
        PCRCocktails = getPCRCocktailsFromDatabase();
        cyclesequencingCocktails = getCycleSequencingCocktailsFromDatabase();
        try {
            saveCachesToDisk();
        } catch (IOException e) {
            throw new TransactionException("Could not write the caches to disk", e);
        }
    }

    private void buildCachesFromDisk() throws IOException, JDOMException, XMLSerializationException {
        PCRThermocycles = getThermocyclesFromDisk("pcr_thermocycle");
        cyclesequencingThermocycles = getThermocyclesFromDisk("cyclesequencing_thermocycle");
        PCRCocktails = getPCRCocktailsFromDisk();
        cyclesequencingCocktails = getCycleSequencingCocktailsFromDisk();
        extractionDisplayedFields = getDisplayFieldsTemplatesFromDisk(Reaction.Type.Extraction);
        pcrDisplayedFields = getDisplayFieldsTemplatesFromDisk(Reaction.Type.PCR);
        cycleSequencingDisplayedFields = getDisplayFieldsTemplatesFromDisk(Reaction.Type.CycleSequencing);
    }

    private void saveCachesToDisk() throws IOException {
        saveThermocyclesToDisk("pcr_thermocycle", PCRThermocycles);
        saveThermocyclesToDisk("cyclesequencing_thermocycle", cyclesequencingThermocycles);
        savePCRCocktailsToDisk();
        saveCycleSequencingCocktailsToDisk();
        saveDisplayedFieldsToDisk(Reaction.Type.Extraction, extractionDisplayedFields);
        saveDisplayedFieldsToDisk(Reaction.Type.PCR, pcrDisplayedFields);
        saveDisplayedFieldsToDisk(Reaction.Type.CycleSequencing, cycleSequencingDisplayedFields);
    }

    private List<CycleSequencingCocktail> getCycleSequencingCocktailsFromDisk() throws JDOMException, IOException, XMLSerializationException{
        File file = new File(dataDirectory, "cyclesequencingCocktails.xml");
        return (List<CycleSequencingCocktail>)getCocktails(file);
    }

    private List<PCRCocktail> getPCRCocktailsFromDisk() throws IOException, JDOMException, XMLSerializationException {
        File file = new File(dataDirectory, "PCRCocktails.xml");
        return (List<PCRCocktail>)getCocktails(file);
    }

    private List<? extends Cocktail> getCocktails(File file) throws JDOMException, IOException, XMLSerializationException {
        if(!file.exists()) {
            return Collections.emptyList();
        }
        List<Cocktail> cocktails = new ArrayList<Cocktail>();
        SAXBuilder builder = new SAXBuilder();
        Element element = builder.build(file).detachRootElement();
        for(Element e : element.getChildren("cocktail")) {
            cocktails.add(XMLSerializer.classFromXML(e, Cocktail.class));
        }
        return cocktails;
    }

    public void saveDisplayedFieldTemplate(DisplayFieldsTemplate template) {
        Reaction.Type type = template.getReactionType();
        List<DisplayFieldsTemplate> templates = new ArrayList<DisplayFieldsTemplate>(getDisplayedFieldTemplates(type));
        templates.add(template);
        try {
            saveDisplayedFieldsToDisk(type, templates);
        } catch (IOException e) {
            e.printStackTrace();
        }
        //template.toSQL(limsConnection.getConnection());
        updateDisplayFieldsTemplates();
    }

    public void updateDisplayFieldsTemplates() {
        try {
            extractionDisplayedFields = getDisplayFieldsTemplatesFromDisk(Reaction.Type.Extraction);
            pcrDisplayedFields = getDisplayFieldsTemplatesFromDisk(Reaction.Type.PCR);
            cycleSequencingDisplayedFields = getDisplayFieldsTemplatesFromDisk(Reaction.Type.CycleSequencing);
        } catch (JDOMException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (XMLSerializationException e) {
            e.printStackTrace();
        }
        //todo: load from the database, not from disk!
    }

    private List<DisplayFieldsTemplate> getDisplayFieldsTemplatesFromDisk(Reaction.Type type) throws JDOMException, IOException, XMLSerializationException{
        File file = new File(dataDirectory, type+"Fields.xml");
        if(!file.exists()) {
            Map<String, Color> colors = new HashMap<String, Color>();
            colors.put("not run", Color.white);
            colors.put("run", Color.white);
            colors.put("passed", Color.green.darker());
            colors.put("suspect", Color.orange.darker());
            colors.put("failed", Color.red.darker());
            switch(type) {
                case Extraction: return Arrays.asList(new DisplayFieldsTemplate("Default", Reaction.Type.Extraction, ExtractionReaction.getDefaultDisplayedFields(), new Reaction.BackgroundColorer(null, Collections.<String, Color>emptyMap())));
                case PCR: return Arrays.asList(new DisplayFieldsTemplate("Default", Reaction.Type.PCR, PCRReaction.getDefaultDisplayedFields(), new Reaction.BackgroundColorer(new DocumentField("run status", "", ReactionOptions.RUN_STATUS,String.class, false, false), colors)));
                case CycleSequencing: return Arrays.asList(new DisplayFieldsTemplate("Default", Reaction.Type.CycleSequencing, CycleSequencingReaction.getDefaultDisplayedFields(), new Reaction.BackgroundColorer(new DocumentField("run status", "", ReactionOptions.RUN_STATUS,String.class, false, false), colors)));
                default : throw new IllegalArgumentException("You must supply one of the supported reaction types");
            }


        }
        List<DisplayFieldsTemplate> templates = new ArrayList<DisplayFieldsTemplate>();
        SAXBuilder builder = new SAXBuilder();
        Element element = builder.build(file).detachRootElement();
        for(Element e : element.getChildren("template")) {
            templates.add(XMLSerializer.classFromXML(e, DisplayFieldsTemplate.class));
        }
        return templates;
    }

    private List<Thermocycle> getThermocyclesFromDisk(String type)  throws JDOMException, IOException, XMLSerializationException{
        File file = new File(dataDirectory, type+".xml");
        if(!file.exists()) {
            return Collections.emptyList();
        }

        List<Thermocycle> thermocycles = new ArrayList<Thermocycle>();
        SAXBuilder builder = new SAXBuilder();
        Element element = builder.build(file).detachRootElement();
        for(Element e : element.getChildren("thermocycle")) {
            thermocycles.add(XMLSerializer.classFromXML(e, Thermocycle.class));
        }
        return thermocycles;
    }

    //----

    private void saveCycleSequencingCocktailsToDisk() throws IOException {
        File file = new File(dataDirectory, "cyclesequencingCocktails.xml");
        if(!file.exists()) {
            createNewFile(file);
        }
        saveCocktails(file, cyclesequencingCocktails);
    }

    private void savePCRCocktailsToDisk() throws IOException {
        File file = new File(dataDirectory, "PCRCocktails.xml");
        if(!file.exists()) {
            createNewFile(file);
        }
        saveCocktails(file, PCRCocktails);
    }

    private void saveCocktails(File file, List<? extends Cocktail> cocktails) throws IOException {
        if(cocktails == null || cocktails.size() == 0) {
            if(!file.delete()) {
                file.deleteOnExit();
            }
            return;
        }
        Element cocktailsElement = new Element("cocktails");
        for(Cocktail c : cocktails) {
            cocktailsElement.addContent(XMLSerializer.classToXML("cocktail", c));
        }
        XMLOutputter out = new XMLOutputter(Format.getPrettyFormat());
        FileOutputStream outf = new FileOutputStream(file);
        out.output(cocktailsElement, outf);
        outf.close();
    }

    private void saveDisplayedFieldsToDisk(Reaction.Type type, List<DisplayFieldsTemplate> templates)  throws IOException {
        File file = new File(dataDirectory, type+"Fields.xml");
        if(!file.exists()) {
            createNewFile(file);
        }
        if(templates == null || templates.size() == 0) {
            if(!file.delete()) {
                file.deleteOnExit();
            }
            return;
        }

        Element templatesElement = new Element("templates");
        for(DisplayFieldsTemplate tp : templates) {
            templatesElement.addContent(XMLSerializer.classToXML("template", tp));
        }
        XMLOutputter out = new XMLOutputter(Format.getPrettyFormat());
        FileOutputStream outf = new FileOutputStream(file);
        out.output(templatesElement, outf);
        outf.close();
    }


    private void saveThermocyclesToDisk(String type, List<Thermocycle> thermocycles)  throws IOException {
        File file = new File(dataDirectory, type+".xml");
        if(!file.exists()) {
            createNewFile(file);
        }
        if(thermocycles == null || thermocycles.size() == 0) {
            if(!file.delete()) {
                file.deleteOnExit();
            }
            return;
        }

        Element thermocyclesElement = new Element("thermocycles");
        for(Thermocycle tc : thermocycles) {
            thermocyclesElement.addContent(XMLSerializer.classToXML("thermocycle", tc));
        }
        XMLOutputter out = new XMLOutputter(Format.getPrettyFormat());
        FileOutputStream outf = new FileOutputStream(file);
        out.output(thermocyclesElement, outf);
        outf.close();
    }

    private void createNewFile(File f) throws IOException {
        if(f.getParentFile() != null && !f.getParentFile().exists()) {
            if(!f.getParentFile().mkdirs()) {
                throw new IOException("Cannot create parent directories for the requested file");
            }
        }
        if(!f.createNewFile()) {
            throw new IOException("Could not create "+f.getAbsolutePath());
        }
    }

    private List<PCRCocktail> getPCRCocktailsFromDatabase() throws TransactionException{
        ResultSet resultSet = limsConnection.executeQuery("SELECT * FROM pcr_cocktail");
        List<PCRCocktail> cocktails = new ArrayList<PCRCocktail>();
        try {
            while(resultSet.next()) {
                cocktails.add(new PCRCocktail(resultSet));    
            }
            resultSet.getStatement().close();
        }
        catch(SQLException ex) {
            ex.printStackTrace();
            throw new TransactionException("Could not query PCR Cocktails from the database");
        }
        return cocktails;
    }

    private List<CycleSequencingCocktail> getCycleSequencingCocktailsFromDatabase() throws TransactionException{
        ResultSet resultSet = limsConnection.executeQuery("SELECT * FROM cyclesequencing_cocktail");
        List<CycleSequencingCocktail> cocktails = new ArrayList<CycleSequencingCocktail>();
        try {
            while(resultSet.next()) {
                cocktails.add(new CycleSequencingCocktail(resultSet));    
            }
            resultSet.getStatement().close();
        }
        catch(SQLException ex) {
            throw new TransactionException("Could not query CycleSequencing Cocktails from the database");
        }
        return cocktails;
    }

    private List<Thermocycle> getThermocyclesFromDatabase(String thermocycleIdentifierTable) throws TransactionException {
        //String sql = "SELECT * FROM "+thermocycleIdentifierTable+" LEFT JOIN (thermocycle, cycle, state) ON (thermocycleid = "+thermocycleIdentifierTable+".cycle AND thermocycle.id = cycle.thermocycleId AND cycle.id = state.cycleId);";
        String sql = "SELECT * FROM "+thermocycleIdentifierTable+" LEFT JOIN thermocycle ON (thermocycle.id = "+thermocycleIdentifierTable+".cycle) LEFT JOIN cycle ON (thermocycle.id = cycle.thermocycleId) LEFT JOIN state ON (cycle.id = state.cycleId);";
//        String sql = "SELECT * FROM "+thermocycleIdentifierTable+" LEFT JOIN thermocycle ON thermocycle.id = "+thermocycleIdentifierTable+".cycle LEFT JOIN cycle ON thermocycle.id = cycle.thermocycleId LEFT JOIN state ON cycle.id = state.cycleId;";
        System.out.println(sql);


        ResultSet resultSet = limsConnection.executeQuery(sql);

        List<Thermocycle> tCycles = new ArrayList<Thermocycle>();

        try {
            resultSet.next();
            while(true) {
                try {
                    Thermocycle thermocycle = Thermocycle.fromSQL(resultSet);
                    if(thermocycle != null) {
                        tCycles.add(thermocycle);
                    }
                    else {
                        break;
                    }
                }
                catch(SQLException e) {
                    break;
                }
            }
            resultSet.getStatement().close();
        }
        catch(SQLException ex) {
            throw new TransactionException("could not read thermocycles from the database", ex);
        }

        return tCycles;
    }

    public List<DisplayFieldsTemplate> getDisplayedFieldTemplates(Reaction.Type type) {
        switch(type) {
            case Extraction:return new ArrayList<DisplayFieldsTemplate>(extractionDisplayedFields);
            case PCR:return new ArrayList<DisplayFieldsTemplate>(pcrDisplayedFields);
            case CycleSequencing:return new ArrayList<DisplayFieldsTemplate>(cycleSequencingDisplayedFields);
            default:throw new IllegalArgumentException("You must request one of the supported reaction types (Extraction, PCR, or Cycle Sequencing");
        }
    }

    public DisplayFieldsTemplate getDisplayedFieldTemplate(Reaction.Type type, String name) {
        List<DisplayFieldsTemplate> templates = getDisplayedFieldTemplates(type);
        for(DisplayFieldsTemplate template : templates) {
            if(template.getName().equals(name)) {
                return template;
            }
        }
        return null;
    }

    public DisplayFieldsTemplate getDefaultDisplayedFieldsTemplate(Reaction.Type type) {
        String name = preferences.get(type+"_defaultFieldsTemplate", null);
        List<DisplayFieldsTemplate> templates = getDisplayedFieldTemplates(type);
        if(name != null) {
            for(DisplayFieldsTemplate template : templates) {
                if(template.getName().equals(name)) {
                    return template;
                }
            }
        }
        return templates.get(0);
    }

    public void setDefaultDisplayedFieldsTemplate(DisplayFieldsTemplate template) {
        preferences.put(template.getReactionType()+"_defaultFieldsTemplate", template.getName());
    }

    public List<Thermocycle> getPCRThermocycles() {
//        Thermocycle tc = new Thermocycle("Test Cycle", 0);
//        Thermocycle.Cycle cycle1 = new Thermocycle.Cycle(1);
//        cycle1.addState(new Thermocycle.State(25, 90));
//        tc.addCycle(cycle1);
//        Thermocycle.Cycle cycle2 = new Thermocycle.Cycle(25);
//        cycle1.addState(new Thermocycle.State(90, 30));
//        cycle1.addState(new Thermocycle.State(55, 30));
//        cycle1.addState(new Thermocycle.State(75, 30));
//        tc.addCycle(cycle2);
//        Thermocycle.Cycle cycle3 = new Thermocycle.Cycle(1);
//        cycle1.addState(new Thermocycle.State(15, Integer.MAX_VALUE));
//        tc.addCycle(cycle3);
        ArrayList<Thermocycle> cycles = new ArrayList<Thermocycle>();
        cycles.addAll(PCRThermocycles);
//        cycles.add(tc);
        return cycles;
    }

    public List<Thermocycle> getCycleSequencingThermocycles() {
        ArrayList<Thermocycle> cycles = new ArrayList<Thermocycle>();
        cycles.addAll(cyclesequencingThermocycles);
        return cycles;
    }

    public void removeThermoCycles(List<Thermocycle> cycles,  String tableName) throws TransactionException {
        String sql = "DELETE  FROM state WHERE state.id=?";
        String sql2 = "DELETE  FROM cycle WHERE cycle.id=?";
        String sql3 = "DELETE  FROM thermocycle WHERE thermocycle.id=?";
        String sql4 = "DELETE FROM "+tableName+" WHERE cycle =?";
        try {
            if(!BiocodeService.getInstance().deleteAllowed("state") || !BiocodeService.getInstance().deleteAllowed("cycle") || !BiocodeService.getInstance().deleteAllowed("thermocycle") || !BiocodeService.getInstance().deleteAllowed(tableName)) {
                throw new TransactionException("It appears that you do not have permission to delete thermocycles.  Please contact your System Administrator for assistance");
            }

            final PreparedStatement statement = getActiveLIMSConnection().getConnection().prepareStatement(sql);
            final PreparedStatement statement2 = getActiveLIMSConnection().getConnection().prepareStatement(sql2);
            final PreparedStatement statement3 = getActiveLIMSConnection().getConnection().prepareStatement(sql3);
            final PreparedStatement statement4 = getActiveLIMSConnection().getConnection().prepareStatement(sql4);
            for(Thermocycle thermocycle : cycles) {
                if(thermocycle.getId() >= 0) {
                    if(getPlatesUsingThermocycle(thermocycle).size() > 0) {
                        throw new SQLException("The thermocycle "+thermocycle.getName()+" is being used by plates in your database.  Only unused thermocycles can be removed");
                    }
                    for(Thermocycle.Cycle cycle : thermocycle.getCycles()) {
                        for(Thermocycle.State state : cycle.getStates()) {
                            statement.setInt(1, state.getId());
                            statement.executeUpdate();
                        }
                        statement2.setInt(1, cycle.getId());
                        statement2.executeUpdate();
                    }
                    statement3.setInt(1, thermocycle.getId());
                    statement3.executeUpdate();
                    statement4.setInt(1, thermocycle.getId());
                    statement4.executeUpdate();
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            throw new TransactionException("Could not delete thermocycles: "+e.getMessage(), e);
        }
        buildCaches();
    }

    public void insertThermocycles(List<Thermocycle> cycles, String tableName) throws TransactionException {
        if(limsConnection == null) {
            throw new TransactionException("You are not logged in");
        }
        try {
            Connection connection = limsConnection.getConnection();
            connection.setAutoCommit(false);
            boolean autoCommit = connection.getAutoCommit();
            for(Thermocycle tCycle : cycles) {
                connection.setAutoCommit(false);
                Savepoint savepoint = connection.setSavepoint();
                try {
                    int id = tCycle.toSQL(connection);
                    PreparedStatement statement = connection.prepareStatement("INSERT INTO "+tableName+" (cycle) VALUES ("+id+");\n");
                    statement.execute();
                    if(!autoCommit)
                        connection.commit();
                    statement.close();
                }
                catch(SQLException ex) {
                    try {
                        if(!limsConnection.isLocal()) {
                            connection.rollback(savepoint);
                        }
                    } catch (SQLException ignored) {}
                    throw ex;
                }
                finally {
                    connection.setAutoCommit(autoCommit);
                }
            }
        } catch (SQLException e) {
            throw new TransactionException("Could not add thermocycle(s): "+e.getMessage(), e);
        }
        buildCaches();
    }

    public List<String> getPlatesUsingThermocycle(Thermocycle thermocycle) throws SQLException {
        if(thermocycle.getId() < 0) {
            return Collections.emptyList();
        }
        String sql = "SELECT name FROM plate WHERE thermocycle = "+thermocycle.getId();
        ResultSet resultSet = limsConnection.getConnection().createStatement().executeQuery(sql);

        List<String> plateNames = new ArrayList<String>();
        while(resultSet.next()) {
            plateNames.add(resultSet.getString(1));
        }
        return plateNames;
    }

    private static BlockingDialog blockingDialog;

    public static synchronized void block(final String message, final Component parentComponent, final Runnable task){
        Runnable r = new Runnable(){
            public void run() {
                try {
                    task.run();
                }
                catch(Exception e) {
                    throw new RuntimeException("An exception was caught in another thread", e);
                }
                finally {
                    unSynchronizedUnBlock();
                }
            }
        };
        new Thread(r, "Biocode blocking thread").start();
        block(message, parentComponent);
    }

    public static synchronized void block(final String message, final Component parentComponent){
        Runnable runnable = new Runnable() {
            public void run() {
                if(blockingDialog == null) {
                    Component parent = parentComponent;
                    while(parent != null && !(parent instanceof Frame) && parent.getParent() != null) {
                        parent = parent.getParent();
                    }
                    if(parent instanceof Frame) {
                        blockingDialog = new BlockingDialog(message, (Frame)parent);
                        blockingDialog.setLocationRelativeTo(parent);
                    }
                    else {
                        blockingDialog = new BlockingDialog(message, GuiUtilities.getMainFrame());
                        blockingDialog.setLocationRelativeTo(GuiUtilities.getMainFrame());
                    }
                    blockingDialog.setVisible(true);
                }
                else {
                    blockingDialog.setMessage(message);
                }
            }
        };
        ThreadUtilities.invokeNowOrLater(runnable);
    }

    public static synchronized void unBlock() {
        Runnable runnable = new Runnable() {
            public void run() {
                if(blockingDialog != null) {
                    blockingDialog.setVisible(false);
                    blockingDialog = null;
                }
            }
        };
        ThreadUtilities.invokeNowOrLater(runnable);
    }


    public static void unSynchronizedUnBlock() {
        Runnable runnable = new Runnable() {
            public void run() {
                if(blockingDialog != null) {
                    blockingDialog.setVisible(false);
                    blockingDialog = null;
                }
            }
        };
        ThreadUtilities.invokeNowOrLater(runnable);
    }

    public List<PCRCocktail> getPCRCocktails() {
        List<PCRCocktail> cocktailList = new ArrayList<PCRCocktail>();
        //cocktailList.add(new PCRCocktail("No Cocktail"));
        if(PCRCocktails != null) {
            cocktailList.addAll(PCRCocktails);
        }
        return cocktailList;
    }

    public List<CycleSequencingCocktail> getCycleSequencingCocktails() {
        List<CycleSequencingCocktail> cocktailList = new ArrayList<CycleSequencingCocktail>();
        //cocktailList.add(new CycleSequencingCocktail("No Cocktail"));
        if(cyclesequencingCocktails != null) {
            cocktailList.addAll(cyclesequencingCocktails);
        }
        return cocktailList;
    }

    public static Map<String, String> getSpecNumToMbioMapping(List<String> mbioNumbers) throws ConnectionException {
        if(!BiocodeService.getInstance().isLoggedIn()) {
            throw new ConnectionException(BiocodeUtilities.NOT_CONNECTED_ERROR_MESSAGE);
        }
        Query[] queries = new Query[mbioNumbers.size()];
        DocumentField field = new DocumentField("Specimen Num Collector", "", "biocode.Specimen_Num_Collector", String.class, false, true);
        for (int i = 0; i < mbioNumbers.size(); i++) {
            String num = mbioNumbers.get(i);
            Query query = Query.Factory.createFieldQuery(field, Condition.EQUAL, num);
            queries[i] = query;
        }

        Query masterQuery = Query.Factory.createOrQuery(queries, Collections.<String, Object>emptyMap());

        List<FimsSample> list = BiocodeService.getInstance().getActiveFIMSConnection().getMatchingSamples(masterQuery);
        
        Map<String, String> result = new HashMap<String, String>();
        for(FimsSample sample : list) {
            result.put( ""+sample.getFimsAttributeValue("biocode.Specimen_Num_Collector"), sample.getId());
        }
        return result;
    }

    public Map<String, String> getReactionToTissueIdMapping(String tableName, List<? extends Reaction> reactions) throws SQLException{
        if(reactions.size() == 0 || !BiocodeService.getInstance().isLoggedIn()) {
            return Collections.emptyMap();
        }
        Connection connection = limsConnection.getConnection();
        String tableDefinition = tableName.equals("extraction") ? tableName : tableName+", extraction, workflow";
        String notExtractionBit = tableName.equals("extraction") ? "" : " workflow.extractionId = extraction.id AND " + tableName + ".workflow = workflow.id AND";
        StringBuilder sql = new StringBuilder("SELECT extraction.extractionId AS extractionId, extraction.sampleId AS tissue FROM " + tableDefinition + " WHERE" + notExtractionBit + " (");

        int count = 0;
        for (Reaction reaction : reactions) {
            if (reaction.isEmpty()) {
                continue;
            }
            if (count > 0) {
                sql.append(" OR ");
            }
            sql.append("extraction.extractionId=?");
            count++;
        }
        sql.append(")");
        if(count == 0) {
            return Collections.emptyMap();
        }
        PreparedStatement statement = connection.prepareStatement(sql.toString());
        int reactionCount = 1;
        for (Reaction reaction : reactions) {
            if (reaction.isEmpty()) {
                continue;
            }
            statement.setString(reactionCount, reaction.getExtractionId());
            reactionCount++;
        }

        ResultSet resultSet = statement.executeQuery();

        Map<String, String> results = new HashMap<String, String>();
        while(resultSet.next()) {
            results.put(resultSet.getString("extractionId"), resultSet.getString("tissue"));
        }

        statement.close();
        return results;
    }

    public List<Workflow> createWorkflows(List<Reaction> reactions, BlockingProgress progress) throws SQLException{
        List<Workflow> workflows = new ArrayList<Workflow>();
        Connection connection = limsConnection.getConnection();
        boolean autoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        Savepoint savepoint = connection.setSavepoint();
        try {
            PreparedStatement statement = connection.prepareStatement("INSERT INTO workflow(locus, extractionId, date) VALUES (?, (SELECT extraction.id from extraction where extraction.extractionId = ?), ?)");
            PreparedStatement statement2 = limsConnection.isLocal() ? connection.prepareStatement("CALL IDENTITY();") : connection.prepareStatement("SELECT last_insert_id()");
            PreparedStatement statement3 = connection.prepareStatement("UPDATE workflow SET name = ? WHERE id=?");
            for(int i=0; i < reactions.size(); i++) {
                if(progress != null) {
                    progress.setMessage("Creating new workflow "+(i+1)+" of "+reactions.size());
                }
                statement.setString(2, reactions.get(i).getExtractionId());
                statement.setString(1, reactions.get(i).getLocus());
                statement.setDate(3, new java.sql.Date(new Date().getTime()));
                statement.execute();
                ResultSet resultSet = statement2.executeQuery();
                resultSet.next();
                int workflowId = resultSet.getInt(1);
                workflows.add(new Workflow(workflowId, "workflow"+workflowId, reactions.get(i).getExtractionId(), reactions.get(i).getLocus(), new Date()));
                statement3.setString(1, reactions.get(i).getLocus()+"_workflow"+workflowId);
                statement3.setInt(2, workflowId);
                statement3.execute();
            }
            if(!autoCommit)
                connection.commit();
            statement.close();
            statement2.close();
            statement3.close();
            return workflows;
        }
        catch(SQLException ex) {
            try {
                if(!limsConnection.isLocal()) {
                    connection.rollback(savepoint);
                }
            } catch (SQLException ignored) {}
            throw ex;
        } finally {
            connection.setAutoCommit(autoCommit);
        }
    }

    public void saveExtractions(BiocodeService.BlockingProgress progress, Plate plate) throws SQLException, BadDataException{

        Connection connection = limsConnection.getConnection();
        boolean autoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);      
        Savepoint savepoint = connection.setSavepoint();
        try {
            isPlateValid(plate, connection);

            List<Reaction> reactionsToSave = new ArrayList<Reaction>();
            for(Reaction reaction : plate.getReactions()) {
                if(!reaction.isEmpty()) {
                    reactionsToSave.add(reaction);
                }
            }

            if(reactionsToSave.size() == 0) {
                throw new BadDataException("You need to save at least one reaction with your plate");
            }

            String error = reactionsToSave.get(0).areReactionsValid(reactionsToSave, null, true);
            if(error != null && error.length() > 0) {
                throw new BadDataException(error);
            }

            createOrUpdatePlate(plate, progress);

            if(!autoCommit)
                connection.commit();
            if(!limsConnection.isLocal()) {
                connection.releaseSavepoint(savepoint);
            }
        } catch(BadDataException e) {
            try {
                if(!limsConnection.isLocal()) {
                    connection.rollback(savepoint);
                }
            } catch (SQLException ignored) {} //ignore - if this fails, then we are already rolled back.
            throw e;
        } finally {
            connection.setAutoCommit(autoCommit);
        }


    }

    public void deletePlate(BiocodeService.BlockingProgress progress, Plate plate) throws SQLException {

        Set<Integer> plateIds = new HashSet<Integer>();

        //delete the reactions...
        if(plate.getReactionType() == Reaction.Type.Extraction) {
            plateIds.addAll(deleteWorkflows(progress, plate));
        }
        else {
            deleteReactions(progress, plate);
        }


        //delete the images...
        progress.setMessage("Deleting GEL images");
        limsConnection.deleteRecords("gelimages", "plate", Arrays.asList(plate.getId()));

        //delete the plate...
        progress.setMessage("deleting the plate");

        limsConnection.deleteRecords("plate", "id", Arrays.asList(plate.getId()));

        if(plate.getReactionType() == Reaction.Type.Extraction) {
            List<Plate> emptyPlates = getEmptyPlates(plateIds);
            if(emptyPlates.size() > 0) {
                StringBuilder message = new StringBuilder("Geneious has found the following empty plates in the database.\n  Do you want to delete them as well?\n");
                for(Plate emptyPlate : emptyPlates) {
                    //noinspection StringConcatenationInsideStringBufferAppend
                    message.append(emptyPlate.getName()+"\n");
                }
                if(Dialogs.showYesNoDialog(message.toString(), "Delete empty plates", progress.getComponentForOwner(), Dialogs.DialogIcon.QUESTION)){
                    for(Plate p : emptyPlates) {
                        deletePlate(progress, p);
                    }
                }
            }
        }



        plate.setDeleted(true);

    }

    private Set<Integer> deleteWorkflows(BlockingProgress progress, Plate plate) throws SQLException {
        progress.setMessage("deleting workflows");
        if(plate.getReactionType() != Reaction.Type.Extraction) {
            throw new IllegalArgumentException("You may only delete workflows from an extraction plate!");
        }

        ArrayList<Integer> workflows = new ArrayList<Integer>();
        ArrayList<Integer> ids = new ArrayList<Integer>();


        boolean first = true;
        StringBuilder builder = new StringBuilder();
        int reactionCount = 0;
        Reaction[] reactions = plate.getReactions();
        for(Reaction r : reactions) { //get the extraction id's and set up the query to get the workflow id's
            if(r.getId() >= 0) {
                ids.add(r.getId());
                if(!first) {
                    builder.append(" OR ");
                }
                //noinspection StringConcatenationInsideStringBufferAppend
                builder.append("extractionId="+r.getId());
                first = false;
                reactionCount++;
            }
        }
        if(reactionCount == 0) { //the plate is empty
            return Collections.emptySet();
        }

        String getWorkflowSQL = "SELECT id FROM workflow WHERE "+builder.toString();
        System.out.println(getWorkflowSQL);

        Statement statement = limsConnection.getConnection().createStatement();


        ResultSet resultSet = statement.executeQuery(getWorkflowSQL);
        while(resultSet.next()) {
            workflows.add(resultSet.getInt("workflow.id"));
        }

        Set<Integer> plates = new HashSet<Integer>();

        plates.addAll(limsConnection.deleteRecords("pcr", "workflow", workflows));
        //plates.addAll(limsConnection.deleteRecords("pcr", "extractionId", extractionNames));
        plates.addAll(limsConnection.deleteRecords("cyclesequencing", "workflow", workflows));
       // plates.addAll(limsConnection.deleteRecords("cyclesequencing", "extractionId", extractionNames));
        limsConnection.deleteRecords("assembly", "workflow", workflows);
        limsConnection.deleteRecords("workflow", "id", workflows);
        plates.addAll(limsConnection.deleteRecords("extraction", "id", ids));

        return plates;
    }

    private void deleteReactions(BlockingProgress progress, Plate plate) throws SQLException {
        progress.setMessage("deleting reactions");

        String tableName;
        switch(plate.getReactionType()) {
            case Extraction:
                tableName = "extraction";
                break;
            case PCR:
                tableName = "pcr";
                break;
            case CycleSequencing:
            default:
                tableName = "cyclesequencing";
                break;
        }

        ArrayList<Integer> terms = new ArrayList<Integer>();
        for(Reaction r : plate.getReactions()) {
            if(r.getId() >= 0) {
                terms.add(r.getId());
            }
        }

        limsConnection.deleteRecords(tableName, "id", terms);
    }

    @Override
    protected void initialize(GeneiousServiceListener listener) {
        initializeConnectionManager();
        reportingService = new ReportingService();
        listener.childServiceAdded(reportingService);
        reportingService.updateReportGenerator();

        logOut();

        if(connectionManager.connectOnStartup()) {
            //make sure the main frame is showing
            AbstractFrame mainFrame = GuiUtilities.getMainFrame();
            while(mainFrame == null || !mainFrame.isShowing()) {
                ThreadUtilities.sleep(100);
                mainFrame = GuiUtilities.getMainFrame();
            }
            if(connectionManager.checkIfWeCanLogIn()) {
                ConnectionManager.Connection connection = connectionManager.getCurrentlySelectedConnection();
                connect(connection, false);
            }
        }
    }

    private void initializeConnectionManager() {
        File file = new File(dataDirectory, "connectionManager.xml");
        if(!file.exists()) {
            connectionManager = new ConnectionManager();
        }
        else {
            SAXBuilder builder = new SAXBuilder();
            try {
                connectionManager = new ConnectionManager(builder.build(file).detachRootElement());
            } catch (XMLSerializationException e) {
                e.printStackTrace();
                connectionManager = new ConnectionManager();
            } catch (JDOMException e) {
                e.printStackTrace();
                connectionManager = new ConnectionManager();
            } catch (IOException e) {
                e.printStackTrace();
                connectionManager = new ConnectionManager();
            }
        }
    }

    /**
     * @param plateIds the ids of the plates to check
     * returns all the empty plates in the database...
     * @return all the empty plates in the database...
     * @throws SQLException if the database cannot be queried for some reason
     */
    private List<Plate> getEmptyPlates(Collection<Integer> plateIds) throws SQLException{
        if(plateIds == null || plateIds.size() == 0) {
            return Collections.emptyList();
        }

        String sql = "SELECT * FROM plate WHERE (plate.id NOT IN (select plate from extraction)) AND (plate.id NOT IN (select plate from pcr)) AND (plate.id NOT IN (select plate from cyclesequencing))";


        List<String> idMatches = new ArrayList<String>();
        for(Integer num : plateIds) {
            idMatches.add("id="+num);
        }

        String termString = StringUtilities.join(" OR ", idMatches);
        if(termString.length() > 0) {
            sql += " AND ("+termString+")";
        }

        ResultSet resultSet = limsConnection.getConnection().createStatement().executeQuery(sql);
        List<Plate> result = new ArrayList<Plate>();
        while(resultSet.next()) {
            Plate plate = new Plate(resultSet);
            plate.initialiseReactions();
            result.add(plate);
        }
        return result;
    }

    public void saveReactions(BiocodeService.BlockingProgress progress, Plate plate) throws SQLException, BadDataException {
        if(progress != null) {
            progress.setMessage("Retrieving existing workflows");
        }
        Connection connection = limsConnection.getConnection();
        boolean autoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        Savepoint savepoint = connection.setSavepoint();
        int originalPlateId = plate.getId();
        try {


            //set workflows for reactions that have id's
            List<Reaction> reactionsToSave = new ArrayList<Reaction>();
            List<String> workflowIdStrings = new ArrayList<String>();
            for(Reaction reaction : plate.getReactions()) {

                Object workflowId = reaction.getFieldValue("workflowId");
                Object tissueId = reaction.getFieldValue("sampleId");
                String extractionId = reaction.getExtractionId();

                if(!reaction.isEmpty() && reaction.getType() != Reaction.Type.Extraction) {
                    reactionsToSave.add(reaction);
                    if(extractionId != null && tissueId != null && tissueId.toString().length() > 0) {
                        if(reaction.getWorkflow() != null && workflowId.toString().length() > 0){
                            if(!reaction.getWorkflow().getExtractionId().equals(extractionId)) {
                                reaction.setHasError(true);
                                throw new BadDataException("The workflow "+workflowId+" does not match the extraction "+extractionId);
                            }
//                          ssh: commenting this out because it appears to have no effect
//                            if(reaction.getWorkflow().getName().equals(workflowId)) {
//                                continue;
//                            }
                        }
                        else {
                            reaction.setWorkflow(null);
                            workflowIdStrings.add(workflowId.toString());
                        }
                    }
                }
            }

            if(reactionsToSave.size() == 0) {
                throw new BadDataException("You need to save at least one reaction with your plate");
            }

            String error = reactionsToSave.get(0).areReactionsValid(reactionsToSave, null, true);
            if(error != null && error.length() > 0) {
                throw new BadDataException(error);
            }

            if(workflowIdStrings.size() > 0) {
                Map<String,Workflow> map = BiocodeService.getInstance().getWorkflows(workflowIdStrings);
                for(Reaction reaction : plate.getReactions()) {

                    Object workflowId = reaction.getFieldValue("workflowId");
                    Object tissueId = reaction.getFieldValue("sampleId");
                    String extractionId = reaction.getExtractionId();

                    if(workflowId != null && reaction.getWorkflow() == null && tissueId != null && tissueId.toString().length() > 0){
                        Workflow workflow = map.get(workflowId.toString());
                        if(workflow != null) {
                            if(!reaction.getWorkflow().getExtractionId().equals(extractionId)) {
                                reaction.setHasError(true);
                                throw new BadDataException("The workflow "+workflowId+" does not match the extraction "+extractionId);
                            }
                        }
                        reaction.setWorkflow(workflow);
                    }
                }
            }
            if(progress != null) {
                progress.setMessage("Creating new workflows");
            }

            //create workflows if necessary
            //int workflowCount = 0;
            List<Reaction> reactionsWithoutWorkflows = new ArrayList<Reaction>();
            for(Reaction reaction : plate.getReactions()) {
                if(reaction.getType() == Reaction.Type.Extraction) {
                    continue;
                }
                Object extractionId = reaction.getFieldValue("extractionId");
                if(!reaction.isEmpty() && extractionId != null && extractionId.toString().length() > 0 && (reaction.getWorkflow() == null || reaction.getWorkflow().getId() < 0)) {
                    reactionsWithoutWorkflows.add(reaction);
                }
            }
            if(reactionsWithoutWorkflows.size() > 0) {
                List<Workflow> workflowList = createWorkflows(reactionsWithoutWorkflows, progress);
                for (int i = 0; i < reactionsWithoutWorkflows.size(); i++) {
                    Reaction reaction = reactionsWithoutWorkflows.get(i);
                    reaction.setWorkflow(workflowList.get(i));
                }
            }
            if(progress != null) {
                progress.setMessage("Creating the plate");
            }
            //we need to create the plate
            createOrUpdatePlate(plate, progress);
            if(!autoCommit)
                connection.commit();
            if(!limsConnection.isLocal()) {
                connection.releaseSavepoint(savepoint);
            }
        } catch(BadDataException e) {
            plate.setId(originalPlateId);
            try {
                if(!limsConnection.isLocal()) {
                    connection.rollback(savepoint);
                }
            } catch (SQLException ignored) {} //ignore - if this fails, then we are already rolled back.
            catch(NullPointerException ex) {
                if(!PrivateApiUtilities.isRunningFromADistribution()) {
                    throw ex;
                }
            }
            throw e;
        } finally {
            connection.setAutoCommit(autoCommit);
        }
    }

    private void createOrUpdatePlate(Plate plate, BlockingProgress progress) throws SQLException, BadDataException{
        Connection connection = limsConnection.getConnection();

        //check the vaidity of the plate.
        isPlateValid(plate, connection);

        //update the plate
        PreparedStatement statement = plate.toSQL(connection);
        statement.execute();
        statement.close();
        if(plate.getId() < 0) {
            PreparedStatement statement1 = limsConnection.isLocal() ? connection.prepareStatement("CALL IDENTITY();") : connection.prepareStatement("SELECT last_insert_id()");
            ResultSet resultSet = statement1.executeQuery();
            resultSet.next();
            int plateId = resultSet.getInt(1);
            plate.setId(plateId);
            statement1.close();
        }

        //replace the images
        if(plate.gelImagesHaveBeenDownloaded()) { //don't modify the gel images if we haven't downloaded them from the server or looked at them...
            if(!BiocodeService.getInstance().deleteAllowed("gelimages")) {
                throw new SQLException("It appears that you do not have permission to delete GEL Images.  Please contact your System Administrator for assistance");
            }
            PreparedStatement deleteImagesStatement = connection.prepareStatement("DELETE FROM gelimages WHERE plate="+plate.getId());
            deleteImagesStatement.execute();
            for(GelImage image : plate.getImages()) {
                PreparedStatement statement1 = image.toSql(connection);
                statement1.execute();
                statement1.close();
            }
            deleteImagesStatement.close();
        }

        Reaction.saveReactions(plate.getReactions(), plate.getReactionType(), connection, progress);

        //update the last-modified on the workflows associated with this plate...
        String sql;
        if(plate.getReactionType() == Reaction.Type.Extraction) {
            sql = "UPDATE workflow SET workflow.date = (SELECT date from plate WHERE plate.id="+plate.getId()+") WHERE extractionId IN (SELECT id FROM extraction WHERE extraction.plate="+plate.getId()+")";
        }
        else if(plate.getReactionType() == Reaction.Type.PCR){
            sql="UPDATE workflow SET workflow.date = (SELECT date from plate WHERE plate.id="+plate.getId()+") WHERE id IN (SELECT workflow FROM pcr WHERE pcr.plate="+plate.getId()+")";
        }
        else if(plate.getReactionType() == Reaction.Type.CycleSequencing){
            sql="UPDATE workflow SET workflow.date = (SELECT date from plate WHERE plate.id="+plate.getId()+") WHERE id IN (SELECT workflow FROM cyclesequencing WHERE cyclesequencing.plate="+plate.getId()+")";
        }
        else {
            throw new SQLException("There is no reaction type "+plate.getReactionType());
        }
        Statement workflowUpdateStatement = connection.createStatement();
        workflowUpdateStatement.executeUpdate(sql);
        workflowUpdateStatement.close();
    }

    private void isPlateValid(Plate plate, Connection connection) throws BadDataException, SQLException {
        if(plate.getName() == null || plate.getName().length() == 0) {
            throw new BadDataException("Plates cannot have empty names");
        }
        if(plate.getId() < 0) {
            PreparedStatement plateCheckStatement = connection.prepareStatement("SELECT name FROM plate WHERE name=?");
            plateCheckStatement.setString(1, plate.getName());
            if(plateCheckStatement.executeQuery().next()) {
                throw new BadDataException("A plate with the name '"+plate.getName()+"' already exists");
            }
            plateCheckStatement.close();
        }
        if(plate.getThermocycle() == null && plate.getReactionType() != Reaction.Type.Extraction) {
            throw new BadDataException("The plate has no thermocycle set");
        }
    }

    public Map<BiocodeUtilities.Well, WorkflowDocument> getWorkflowsForCycleSequencingPlate(String plateName) throws SQLException, DocumentOperationException {
        List<PlateDocument> plateList = limsConnection.getMatchingPlateDocuments(Query.Factory.createFieldQuery(LIMSConnection.PLATE_NAME_FIELD, Condition.EQUAL, plateName), null, null);
        if(plateList.size() == 0) {
            throw new DocumentOperationException("The plate '"+plateName+"' does not exist in the database.");
        }
        assert plateList.size() == 1;
        if(plateList.get(0).getPlate().getReactionType() != Reaction.Type.CycleSequencing) {
            throw new DocumentOperationException("The plate '"+plateName+"' is not a cycle sequencing plate.");
        }
        Plate plate = plateList.get(0).getPlate();

        List<Query> workflowNameQueries = new ArrayList<Query>();

        for(Reaction r : plate.getReactions()) {
            if(r.getWorkflow() != null) {
                workflowNameQueries.add(Query.Factory.createFieldQuery(LIMSConnection.WORKFLOW_NAME_FIELD, Condition.EQUAL, r.getWorkflow().getName()));
            }
        }
        List<WorkflowDocument> docs = limsConnection.getMatchingWorkflowDocuments(Query.Factory.createOrQuery(workflowNameQueries.toArray(new Query[workflowNameQueries.size()]), Collections.<String, Object>emptyMap()), null, null);

        Map<BiocodeUtilities.Well, WorkflowDocument> workflows = new HashMap<BiocodeUtilities.Well, WorkflowDocument>();
        for(Reaction r : plate.getReactions()) {
            for(WorkflowDocument doc : docs) {
                Reaction mostRecentExtraction = doc.getMostRecentReaction(Reaction.Type.Extraction);
                assert mostRecentExtraction != null; //workflows should always have an extraction
                //noinspection ConstantConditions
                if(mostRecentExtraction != null && mostRecentExtraction.getFimsSample() != null && r.getFimsSample() != null && mostRecentExtraction.getFimsSample().getId().equals(r.getFimsSample().getId())) {
                    workflows.put(new BiocodeUtilities.Well(r.getLocationString()), doc);
                }
            }
        }
        return workflows;
    }

    public Map<BiocodeUtilities.Well, FimsSample> getFimsSamplesForCycleSequencingPlate(String plateName) throws SQLException{
        //step 1, query the plate record
        String query1 = "SELECT plate.size, plate.id FROM plate WHERE plate.name = ?";
        PreparedStatement statement1 = limsConnection.getConnection().prepareStatement(query1);
        statement1.setString(1, plateName);
        ResultSet resultSet1 = statement1.executeQuery();
        if(!resultSet1.next()) {
            return null;
        }
        int plateId = resultSet1.getInt("plate.id");


        int size = resultSet1.getInt("plate.size");
        Plate.Size sizeEnum = Plate.getSizeEnum(size);

        //step 2, get the relevant reaction record
        String query2 = "SELECT extraction.sampleId, cyclesequencing.location FROM cyclesequencing, plate, extraction WHERE cyclesequencing.extractionId = extraction.extractionId AND cyclesequencing.plate = ?";
        PreparedStatement statement2 = limsConnection.getConnection().prepareStatement(query2);
        statement2.setInt(1, plateId);
        ResultSet resultSet2 = statement2.executeQuery();
        Set<String> samplesToGet = new HashSet<String>();
        Map<String, Integer> tissueToLocationMap = new HashMap<String, Integer>();
        while(resultSet2.next()) {
            tissueToLocationMap.put(resultSet2.getString("extraction.sampleId"), resultSet2.getInt("cycleSequencing.location"));
            String sampleId = resultSet2.getString("extraction.sampleId");
            if(sampleId != null && sampleId.length() > 0) {
                samplesToGet.add(sampleId);
            }
        }
        statement1.close();
        statement2.close();

        //step 3 - get the fims samples from the fims database
        try {
            if(activeFIMSConnection == null) {
                return Collections.emptyMap();
            }
            List<FimsSample> list = activeFIMSConnection.getMatchingSamples(samplesToGet);
            Map<BiocodeUtilities.Well, FimsSample> result = new HashMap<BiocodeUtilities.Well, FimsSample>();
            for(FimsSample sample : list) {
                Integer location = tissueToLocationMap.get(sample.getId());
                if(location != null) {
                    BiocodeUtilities.Well well = Plate.getWell(location, sizeEnum);
                    result.put(well, sample);
                }
            }
            return result;
        } catch (ConnectionException e) {
            if(e == ConnectionException.NO_DIALOG) {
                return null;
            }
            if(e.getCause() instanceof SQLException){
                throw (SQLException)e.getCause();
            }
            e.printStackTrace();
            assert false;
        }
        return null;
    }

    public Map<String, String> getWorkflowIds(List<String> idsToCheck, List<String> loci, Reaction.Type reactionType) throws SQLException{
        if(idsToCheck.size() == 0) {
            return Collections.emptyMap();
        }
        StringBuilder sqlBuilder = new StringBuilder();
        List<String> values = new ArrayList<String>();
        switch(reactionType) {
            case Extraction:
                throw new RuntimeException("You should not be adding extractions to existing workflows!");
            case PCR:
            case CycleSequencing:
                sqlBuilder.append("SELECT extraction.extractionId AS id, workflow.name AS workflow, workflow.date AS date, workflow.id AS workflowId, extraction.date FROM extraction, workflow WHERE workflow.extractionId = extraction.id AND (");
                for (int i = 0; i < idsToCheck.size(); i++) {
                    if(loci.get(i) != null && loci.get(i).length() > 0) {
                        sqlBuilder.append("(extraction.extractionId = ? AND locus = ?)");
                        values.add(idsToCheck.get(i));
                        values.add(loci.get(i));
                    }
                    else {
                        sqlBuilder.append("extraction.extractionId = ?");
                        values.add(idsToCheck.get(i));
                    }

                    if(i < idsToCheck.size()-1) {
                        sqlBuilder.append(" OR ");
                    }
                }
                sqlBuilder.append(") ORDER BY extraction.date"); //make sure the most recent workflow is stored in the map
            default:
                break;
        }
        System.out.println(sqlBuilder.toString());
        PreparedStatement statement = limsConnection.getConnection().prepareStatement(sqlBuilder.toString());
        for (int i = 0; i < values.size(); i++) {
            statement.setString(i+1, values.get(i));
        }
        ResultSet results = statement.executeQuery();
        Map<String, String> result = new HashMap<String, String>();

        while(results.next()) {
            result.put(results.getString("id"), results.getString("workflow")/*new Workflow(results.getInt("workflowId"), results.getString("workflow"), results.getString("id"))*/);
        }
        statement.close();
        return result;
    }

    public Map<String, Workflow> getWorkflows(Collection<String> workflowIds) throws SQLException{
        if(workflowIds.size() == 0) {
            return Collections.emptyMap();
        }
        StringBuilder sqlBuilder = new StringBuilder();
        sqlBuilder.append("SELECT workflow.name AS workflow, workflow.id AS workflowId, workflow.date AS date, workflow.locus AS locus, extraction.extractionId FROM workflow, extraction WHERE extraction.id = workflow.extractionId AND (");
        for(int i=0; i < workflowIds.size(); i++) {
            sqlBuilder.append("workflow.name = ? ");
            if(i < workflowIds.size()-1) {
                sqlBuilder.append("OR ");
            }
        }
        sqlBuilder.append(")");
        PreparedStatement statement = limsConnection.getConnection().prepareStatement(sqlBuilder.toString());
        int i=0;
        for (String s : workflowIds) {
            statement.setString(i+1, s);
            i++;
        }
        ResultSet results = statement.executeQuery();
        Map<String, Workflow> result = new HashMap<String, Workflow>();

        while(results.next()) {
            result.put(results.getString("workflow"), new Workflow(results.getInt("workflowId"), results.getString("workflow"), results.getString("extractionId"), results.getString("locus"), results.getDate("workflow.date")));
        }
        statement.close();
        return result;
    }

    public void registerCallback(BiocodeCallback callback) {
        activeCallbacks.add(callback);
    }

    public void unregisterCallback(BiocodeCallback callback) {
        activeCallbacks.remove(callback);
    }

//    public Map<String, String> getTissueIdsFromBarcodes(List<String> barcodeIds) throws ConnectionException {
//        if(activeFIMSConnection == null) {
//            return Collections.emptyMap();
//        }
//
//        DocumentField barcodeField = activeFIMSConnection.getTissueBarcodeDocumentField();
//        DocumentField tissueField = activeFIMSConnection.getTissueSampleDocumentField();
//
//
//        Query[] queries = new Query[barcodeIds.size()];
//        for(int i=0; i < barcodeIds.size(); i++) {
//            queries[i] = Query.Factory.createFieldQuery(barcodeField, Condition.EQUAL, barcodeIds.get(i));
//        }
//
//        Query orQuery = Query.Factory.createOrQuery(queries, Collections.<String, Object>emptyMap());
//
//        List<FimsSample> samples = activeFIMSConnection.getMatchingSamples(orQuery);
//
//        Map<String, String> result = new HashMap<String, String>();
//        for(FimsSample sample : samples) {
//            result.put(""+sample.getFimsAttributeValue(barcodeField.getCode()), ""+sample.getFimsAttributeValue(tissueField.getCode()));
//        }
//
//        return result;
//    }

    public static interface BlockingProgress {
        public void setMessage(String s);
        public void dispose();
        public Component getComponentForOwner();

        public static final BlockingProgress EMPTY = new BlockingProgress() {
            public void setMessage(String s) {}

            public void dispose() {}

            public Component getComponentForOwner() {
                return null;
            }
        };
    }

    public static class BlockingDialog extends JDialog implements BlockingProgress {
        private String message;
        private JLabel label;

        public static BlockingDialog getDialog(final String message, final Component owner) {
            final AtomicReference<BlockingDialog> dialog = new AtomicReference<BlockingDialog>();
            ThreadUtilities.invokeNowOrWait(new Runnable() {
                public void run() {
                    Window w = getParentFrame(owner);
                    if(w instanceof JFrame) {
                        dialog.set(new BlockingDialog(message, (JFrame)w));
                    }
                    else if(w instanceof JDialog) {
                        dialog.set(new BlockingDialog(message, (JDialog)w));
                    }
                    else {
                        dialog.set(new BlockingDialog(message, GuiUtilities.getMainFrame()));
                    }
                }
            });
            return dialog.get();
        }

        private static Window getParentFrame(Component component) {
            if(component == null) {
                return null;
            }
            if(component instanceof Window) {
                return (Window)component;
            }
            if(component.getParent() != null) {
                return getParentFrame(component.getParent());
            }
            return null;
        }

        private BlockingDialog(String message, Frame owner){
            super(owner);
            if(owner != null) {
                setLocationRelativeTo(owner);
            }
            this.message = message;
            init();
        }

        private BlockingDialog(String message, Dialog owner){
            super(owner);
            if(owner != null) {
                setLocationRelativeTo(owner);
            }
            this.message = message;
            init();
        }

        private void init() {
            setUndecorated(true);
            setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
            setAlwaysOnTop(true);
            setModal(true);
            setTitle("Please Wait...");
            Container cp = getContentPane();
            cp.setLayout(new BoxLayout(cp, BoxLayout.Y_AXIS));
            AnimatedIcon activityIcon = AnimatedIcon.getActivityIcon();
            activityIcon.startAnimation();
            label = new JLabel(message, activityIcon, JLabel.LEFT);
            label.setBorder(new EmptyBorder(25,50,25,50));
            cp.add(label);
            if(cp instanceof JComponent) {
                ((JComponent)cp).setBorder(new LineBorder(Color.black));
            }
            pack();
        }

        public void setMessage(String s) {
            message = s;
            Runnable runnable = new Runnable() {
                public void run() {
                    label.setText(message);
                    pack();
                }
            };
            ThreadUtilities.invokeNowOrLater(runnable);
        }

        public Component getComponentForOwner() {
            return this;
        }

    }
}
