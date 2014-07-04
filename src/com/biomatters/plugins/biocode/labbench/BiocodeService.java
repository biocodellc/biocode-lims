package com.biomatters.plugins.biocode.labbench;

import com.biomatters.geneious.publicapi.components.Dialogs;
import com.biomatters.geneious.publicapi.components.ProgressFrame;
import com.biomatters.geneious.publicapi.databaseservice.*;
import com.biomatters.geneious.publicapi.documents.*;
import com.biomatters.geneious.publicapi.documents.sequence.DefaultNucleotideGraph;
import com.biomatters.geneious.publicapi.documents.sequence.NucleotideGraph;
import com.biomatters.geneious.publicapi.documents.sequence.SequenceDocument;
import com.biomatters.geneious.publicapi.implementations.sequence.DefaultNucleotideGraphSequence;
import com.biomatters.geneious.publicapi.implementations.sequence.DefaultNucleotideSequence;
import com.biomatters.geneious.publicapi.plugin.*;
import com.biomatters.geneious.publicapi.utilities.GuiUtilities;
import com.biomatters.geneious.publicapi.utilities.ThreadUtilities;
import com.biomatters.plugins.biocode.BiocodePlugin;
import com.biomatters.plugins.biocode.BiocodeUtilities;
import com.biomatters.plugins.biocode.assembler.annotate.AnnotateUtilities;
import com.biomatters.plugins.biocode.assembler.annotate.FimsData;
import com.biomatters.plugins.biocode.assembler.annotate.FimsDataGetter;
import com.biomatters.plugins.biocode.labbench.connection.Connection;
import com.biomatters.plugins.biocode.labbench.connection.ConnectionManager;
import com.biomatters.plugins.biocode.labbench.fims.*;
import com.biomatters.plugins.biocode.labbench.fims.biocode.BiocodeFIMSConnection;
import com.biomatters.plugins.biocode.labbench.lims.LIMSConnection;
import com.biomatters.plugins.biocode.labbench.lims.LimsSearchResult;
import com.biomatters.plugins.biocode.labbench.plates.Plate;
import com.biomatters.plugins.biocode.labbench.reaction.*;
import com.biomatters.plugins.biocode.labbench.reporting.ReportingService;
import jebl.util.ProgressListener;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.input.SAXBuilder;
import org.jdom.output.Format;
import org.jdom.output.XMLOutputter;
import org.virion.jam.framework.AbstractFrame;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.sql.*;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

/**
 * @author Steven Stones-Havas
 * @version $Id$
 *          <p/>
 *          Created on 23/02/2009 4:41:26 PM
 */
@SuppressWarnings({"ConstantConditions"})
public class BiocodeService extends PartiallyWritableDatabaseService {

    private static final String DOWNLOAD_TISSUES = "tissueDocuments";
    private static final String DOWNLOAD_WORKFLOWS = "workflowDocuments";
    private static final String DOWNLOAD_PLATES = "plateDocuments";
    private static final String DOWNLOAD_SEQS = "sequenceDocuments";
    private boolean isLoggedIn = false;
    private FIMSConnection activeFIMSConnection;
    private LIMSConnection limsConnection;
    private final String loggedOutMessage = "Right click on the " + getName() + " service in the service tree to log in.";
    private Driver driver;
    private Driver localDriver;
    private static BiocodeService instance = new BiocodeService();;
    public final Map<String, Image[]> imageCache = new HashMap<String, Image[]>();
    private File dataDirectory;
    private static final long FIMS_CONNECTION_TIMEOUT_THRESHOLD_MILLISECONDS = 60000;

    private static Preferences getPreferencesForService() {
        return Preferences.userNodeForPackage(BiocodeService.class);
    }
    private com.biomatters.plugins.biocode.labbench.connection.Connection activeConnection;

    public static final DateFormat dateFormat = SimpleDateFormat.getDateInstance(SimpleDateFormat.MEDIUM);//synchronize access on this (it's not threadsafe!)
    public static final DateFormat XMLDateFormat = new SimpleDateFormat("yyyy MMM dd hh:mm:ss");

    private ConnectionManager connectionManager;
    private boolean loggingIn;
    ReportingService reportingService;
    private Thread disconnectCheckingThread;
    private boolean driverLoaded;
    public static final int STATEMENT_QUERY_TIMEOUT = 300;

    private BiocodeService() {
    }

    public void setDataDirectory(File dataDirectory) {
        this.dataDirectory = dataDirectory;

        if(!dataDirectory.exists()) {
            if(!dataDirectory.mkdirs()) {
                throw new UnsupportedOperationException("Unable to create directory: " + dataDirectory.getAbsolutePath());
            }
        }

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
            throw new DatabaseServiceException("Deleting Biocode documents requires an active license", false);
        }
        if(!BiocodeService.getInstance().isLoggedIn()) {
            throw new DatabaseServiceException("You are not logged into the Biocode Service - you cannot delete biocode documents.", false);
        }
        deletePlates(documents);
        deleteSequences(documents);
    }

    public void deleteSequences(List<AnnotatedPluginDocument> documents) throws DatabaseServiceException {
        if(!deleteAllowed("assembly")) {
            throw new DatabaseServiceException("It appears that you do not have permission to delete sequence records.  Please contact your System Administrator for assistance", false);
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

        limsConnection.deleteSequences(sequencesToDelete);

        if(documentsWithNoIdField.size() > 0) {
            throw new DatabaseServiceException("Some of your selected documents were not correctly annotated with LIMS data, and could not be deleted.  Please contact Biomatters for assistance.", false);
        }
    }

    public void deletePlates(List<AnnotatedPluginDocument> documents) throws DatabaseServiceException {
        if(!deleteAllowed("plate")) {
            throw new DatabaseServiceException("It appears that you do not have permission to delete plate records.  Please contact your System Administrator for assistance", false);
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
        ProgressFrame progressFrame = new ProgressFrame("Deleting Documents", "", GuiUtilities.getMainFrame());
        progressFrame.setCancelable(false);
        progressFrame.setIndeterminateProgress();
        try {
            for(Plate plate : platesToDelete) {
                deletePlate(progressFrame, plate);
            }
        } finally {
            progressFrame.setComplete();
        }
    }

    @Override
    public boolean canEditDocumentField(AnnotatedPluginDocument document, DocumentField field) {
        return (WorkflowDocument.class.isAssignableFrom(document.getDocumentClass()) ||
                PlateDocument.class.isAssignableFrom(document.getDocumentClass())) &&
                field.getCode().equals(DocumentField.NAME_FIELD.getCode());
    }

    @Override
    public void editDocumentField(AnnotatedPluginDocument document, DocumentFieldAndValue newValue) throws DatabaseServiceException {
        System.out.println("tried to rename a document to "+newValue);
        if(newValue == null || newValue.getValue() == null) {
            return;
        }
        if(WorkflowDocument.class.isAssignableFrom(document.getDocumentClass())) {
            WorkflowDocument doc = (WorkflowDocument)document.getDocumentOrThrow(DatabaseServiceException.class);
            limsConnection.renameWorkflow(doc.getWorkflow().getId(), newValue.getValue().toString());
        }

        if(PlateDocument.class.isAssignableFrom(document.getDocumentClass())) {
            PlateDocument doc = (PlateDocument)document.getDocumentOrThrow(DatabaseServiceException.class);
            limsConnection.renamePlate(doc.getPlate().getId(), newValue.getValue().toString());
        }
    }

    private void loadEmptyCaches() {
        cyclesequencingCocktails = Collections.emptyList();
        PCRCocktails = Collections.emptyList();
        PCRThermocycles = Collections.emptyList();
        cyclesequencingThermocycles = Collections.emptyList();
    }

    public static BiocodeService getInstance() {
        return instance;
    }

    public FIMSConnection getActiveFIMSConnection() {
        return activeFIMSConnection;
    }

    public LIMSConnection getActiveLIMSConnection() throws DatabaseServiceException {
        if (limsConnection == null) {
            throw new DatabaseServiceException("No active Lims connection", false);
        }
        return limsConnection;
    }

    public synchronized boolean isLoggedIn() {
        return isLoggedIn;
    }

    @Override
    public ExtendedSearchOption[] getExtendedSearchOptions(boolean isAdvancedSearch) {
        return new ExtendedSearchOption[] {
                new CheckboxSearchOption(DOWNLOAD_TISSUES, "Tissues", true),
                new CheckboxSearchOption(DOWNLOAD_WORKFLOWS, "Workflows", true),
                new CheckboxSearchOption(DOWNLOAD_PLATES, "Plates", true),
                new CheckboxSearchOption(DOWNLOAD_SEQS, "Sequences", false)
        };
    }

    public static boolean isDownloadTissues(Query query) {
        return isDownloadTypeQuery(query, DOWNLOAD_TISSUES);
    }
    public static boolean isDownloadWorkflows(Query query) {
        return isDownloadTypeQuery(query, DOWNLOAD_WORKFLOWS);
    }
    public static boolean isDownloadPlates(Query query) {
        return isDownloadTypeQuery(query, DOWNLOAD_PLATES);
    }
    public static boolean isDownloadSequences(Query query) {
        return isDownloadTypeQuery(query, DOWNLOAD_SEQS);
    }

    private static boolean isDownloadTypeQuery(Query query, String type) {
        return !Boolean.FALSE.equals(query.getExtendedOptionValue(type));
    }

    public static Map<String, Object> getSearchDownloadOptions(boolean tissues, boolean workflows, boolean plates, boolean sequences) {
        Map<String, Object> searchOptions = new HashMap<String, Object>();
        searchOptions.put(DOWNLOAD_TISSUES, tissues);
        searchOptions.put(DOWNLOAD_WORKFLOWS, workflows);
        searchOptions.put(DOWNLOAD_PLATES, plates);
        searchOptions.put(DOWNLOAD_SEQS, sequences);
        return searchOptions;
    }

    public static FIMSConnection[] getFimsConnections() {
        return new FIMSConnection[] {
                new ExcelFimsConnection(),
                new FusionTablesFimsConnection(),
                new MySQLFimsConnection(),
                new MooreaFimsConnection(),
                new TAPIRFimsConnection(),
                new BiocodeFIMSConnection()
        };
    }

    public Driver getDriver() throws ConnectionException {
        if(!driverLoaded) {
            String error = loadMySqlDriver();
            if(error != null) {
                throw new ConnectionException(error);
            }
        }

        if(driver == null && driverLoaded) {
            throw new IllegalStateException("A driver load was attempted, but the driver has not been loaded");
        }

        return driver;
    }

    public synchronized Driver getLocalDriver() throws ConnectionException {
        if(localDriver == null) {
            try {
                Class driverClass = getClass().getClassLoader().loadClass("org.hsqldb.jdbc.JDBCDriver");
                localDriver = (Driver) driverClass.newInstance();
            } catch (ClassNotFoundException e) {
                throw new ConnectionException("Could not find HSQL driver class", e);
            } catch (IllegalAccessException e1) {
                throw new ConnectionException("Could not access HSQL driver class");
            } catch (InstantiationException e) {
                throw new ConnectionException("Could not instantiate HSQL driver class");
            } catch (ClassCastException e) {
                throw new ConnectionException("HSQL Driver class exists, but is not an SQL driver");
            }
        }
        return localDriver;
    }


    public QueryField[] getSearchFields() {
        List<QueryField> fieldList = new ArrayList<QueryField>();

        List<DocumentField> limsFields = LIMSConnection.getSearchAttributes();
        for(DocumentField field : limsFields) {
            Condition[] conditions;
            if(field.isEnumeratedField()) {
                conditions = new Condition[] {
                    Condition.EQUAL,
                    Condition.NOT_EQUAL
                };
            }
            else {
                conditions = LIMSConnection.getFieldConditions(field.getValueType());
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

    public void logOut() {
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
        if(limsConnection != null) {
            limsConnection.disconnect();
        }
        limsConnection = null;
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
        final Connection connection = connectionManager.getConnectionFromUser(null);

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

    public void connect(Connection connection, boolean block) {
        synchronized (this) {
            loggingIn = true;
        }
        ProgressListener progressListener;
        if (block) {
            progressListener = new ProgressFrame("Connecting", "", GuiUtilities.getMainFrame());
            ((ProgressFrame) progressListener).setCancelable(false);
            progressListener.setIndeterminateProgress();
        } else {
            progressListener = ProgressListener.EMPTY;
        }
        activeConnection = connection;
        //load the connection driver -------------------------------------------------------------------
        try {
            new XMLOutputter().output(connection.getXml(true), System.out);
            System.out.println();
        } catch (IOException e) {
            e.printStackTrace();
        }

        String error = null;

        try {
            limsConnection = connection.getLIMSConnection();
        } catch (ConnectionException e) {
            error = "There was an error connecting to your LIMS: cannot find your LIMS connection class: " + e.getMessage();
        }

        if(error == null) {
            try {
                localDriver = getLocalDriver();
            } catch (ConnectionException e) {
                error = e.getMessage();
            }
        }

        if (error != null) {
            if(block) {
                Dialogs.showMessageDialog(error);
            }
            logOut();
            progressListener.setProgress(1.0);
            return;
        }

        //get the selected fims service.
        activeFIMSConnection = connection.getFimsConnection();
        final FIMSConnection finalReferenceToActiveFimsConnection = activeFIMSConnection;
        final PasswordOptions fimsOptions = connection.getFimsOptions();
        final ProgressListener finalReferenceToProgressListener = progressListener;
        final AtomicBoolean fimsSuccessfullyConnected = new AtomicBoolean(false);
        final AtomicBoolean timedOutOrExceptionMet = new AtomicBoolean(false);


        Thread connectToFimsThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    finalReferenceToActiveFimsConnection.connect(fimsOptions);
                    fimsSuccessfullyConnected.set(true);
                } catch (ConnectionException e) {
                    if (!timedOutOrExceptionMet.getAndSet(true)) {
                        if (e != ConnectionException.NO_DIALOG) {
                            String message = e.getMainMessage() == null ? "There was an error connecting to " + activeFIMSConnection.getLabel() : e.getMainMessage();
                            if (e.getMessage() != null) {
                                Dialogs.showMoreOptionsDialog(new Dialogs.DialogOptions(new String[]{"OK"}, "Error connecting to FIMS"), message, e.getMessage());
                            } else {
                                Dialogs.showMessageDialog(message, "Error connecting to FIMS");
                            }
                        }
                        logOut();
                        finalReferenceToProgressListener.setProgress(1.0);
                    }
                }
            }
        });

        long timeoutPoint = System.currentTimeMillis() + FIMS_CONNECTION_TIMEOUT_THRESHOLD_MILLISECONDS;

        progressListener.setMessage("Connecting to the FIMS");
        connectToFimsThread.start();
        while (connectToFimsThread.isAlive()) {
            if (System.currentTimeMillis() > timeoutPoint) {
                if (!timedOutOrExceptionMet.getAndSet(true)) {
                    logOut();
                    Dialogs.showMessageDialog("Connection attempt timed out", "Error connecting to FIMS");
                }
                return;
            }
            ThreadUtilities.sleep(1000);
        }

        if (!fimsSuccessfullyConnected.get()) {
            return;
        }

        try {
            if(!(activeFIMSConnection instanceof MooreaFimsConnection) && connection.getLimsOptions().getValueAsString("server").equalsIgnoreCase("darwin.berkeley.edu")) {
                Dialogs.showMessageDialog("You cannot connect to the Moorea Lab Bench database using a field database other than the Moorea FIMS");
                logOut();
                progressListener.setProgress(1.0);
                return;
            }

            progressListener.setMessage("Connecting to the LIMS");

            if(disconnectCheckingThread != null) {
                disconnectCheckingThread.interrupt();
            }

            limsConnection.connect(connection.getLimsOptions());
            progressListener.setMessage("Building Caches");
            buildCaches();

            progressListener.setMessage("Performing Further Initialization");
            limsConnection.doAnyExtraInitialization();

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
            // todo Surface exception in server.  The current error handling swallows the exception when there is no GUI.  ie Server mode
            progressListener.setProgress(1.0);
            logOut();
            if(e1 == ConnectionException.NO_DIALOG) {
                return;
            }
            String title = "Connection Failure";
            String message = "Geneious could not connect to the LIMS database";
            showErrorDialog(e1, title, message);
            return;
        } catch (DatabaseServiceException e3) {
            logOut();
            progressListener.setProgress(1.0);
            String title = "Connection Failure";
            String message = "Geneious could not connect to the LIMS database";
            showErrorDialog(e3, title, message);
        } finally {
            progressListener.setProgress(1.0);
        }
        updateStatus();
    }

    public String loadMySqlDriver() {
        driverLoaded = true;

        String error = null;
        try {
            Class driverClass = Class.forName("com.mysql.jdbc.Driver");
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
                            limsConnection.testConnection();
                        } catch (DatabaseServiceException e) {
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
            List<String> tissueIdsMatchingFimsQuery;

            callback.setIndeterminateProgress();


            List<Query> fimsQueries = new ArrayList<Query>();
            if(query instanceof CompoundSearchQuery) {
                CompoundSearchQuery masterQuery = (CompoundSearchQuery) query;
                for(Query childQuery : masterQuery.getChildren()) {
                    if(isFimsTermQuery(childQuery)) {
                        fimsQueries.add(childQuery);
                    }
                }


            } else if(isFimsTermQuery(query) || query instanceof BasicSearchQuery) {
                fimsQueries.add(query);
            }

            Query toSearchFimsWith = null;
            if(query instanceof CompoundSearchQuery && fimsQueries.size() > 0) {
                if(((CompoundSearchQuery)query).getOperator() == CompoundSearchQuery.Operator.AND) {
                    toSearchFimsWith = Query.Factory.createAndQuery(fimsQueries.toArray(new Query[fimsQueries.size()]), Collections.<String, Object>emptyMap());
                } else {
                    toSearchFimsWith = Query.Factory.createOrQuery(fimsQueries.toArray(new Query[fimsQueries.size()]), Collections.<String, Object>emptyMap());
                }
            } else if(fimsQueries.size() == 1) {
                toSearchFimsWith = fimsQueries.get(0);
            }

            if(toSearchFimsWith != null) {
                try {
                    callback.setMessage("Searching FIMS");
                    if((callback != null && callback.isCanceled()) || activeFIMSConnection == null) {
                        return;
                    }

                    tissueIdsMatchingFimsQuery = activeFIMSConnection.getTissueIdsMatchingQuery(toSearchFimsWith, null);
                } catch (ConnectionException e) {
                    throw new DatabaseServiceException(e, e.getMessage(), false);
                }
            } else {
                tissueIdsMatchingFimsQuery = null;
            }

            if(callback.isCanceled()) {
                return;
            }

            try {
                callback.setMessage("Searching LIMS...");
                LimsSearchResult limsResult = limsConnection.getMatchingDocumentsFromLims(query,
                        areBrowseQueries(fimsQueries) ? null : tissueIdsMatchingFimsQuery, callback);
                List<WorkflowDocument> workflowList = limsResult.getWorkflows();
                if(callback.isCanceled()) {
                    return;
                }

                callback.setMessage("Creating results...");
                // Now add tissues that match the LIMS query
                // FimsSamples would have been downloaded as part of plate creation.  Collect them now.
                List<FimsSample> tissueSamples = new ArrayList<FimsSample>();
                tissueSamples.addAll(limsResult.getTissueSamples());

                if(isDownloadTissues(query)) {
                    boolean downloadAllSamplesFromFimsQuery = false;
                    if(query instanceof BasicSearchQuery || areBrowseQueries(Collections.singletonList(query))) {
                        downloadAllSamplesFromFimsQuery = true;
                    } else if(query instanceof AdvancedSearchQueryTerm) {
                        downloadAllSamplesFromFimsQuery = !fimsQueries.isEmpty();
                    } else if(query instanceof CompoundSearchQuery) {
                        CompoundSearchQuery compoundQuery = (CompoundSearchQuery) query;
                        downloadAllSamplesFromFimsQuery = !fimsQueries.isEmpty() && (
                                fimsQueries.size() == compoundQuery.getChildren().size() ||
                                compoundQuery.getOperator() == CompoundSearchQuery.Operator.OR
                        );
                    }
                    if(downloadAllSamplesFromFimsQuery && tissueIdsMatchingFimsQuery != null) {
                        callback.setMessage("Downloading Tissues");
                        try {
                            List<String> toRetrieveFromFims = new ArrayList<String>(tissueIdsMatchingFimsQuery);
                            for (FimsSample tissueSample : tissueSamples) {
                                toRetrieveFromFims.remove(tissueSample.getId());
                            }
                            if(!toRetrieveFromFims.isEmpty()) {
                                tissueSamples.addAll(activeFIMSConnection.retrieveSamplesForTissueIds(toRetrieveFromFims));
                            }
                        } catch (ConnectionException e) {
                            throw new DatabaseServiceException(e, e.getMessage(), false);
                        }
                    }

                    for (FimsSample tissueSample : tissueSamples) {
                        callback.add(new TissueDocument(tissueSample), Collections.<String, Object>emptyMap());
                    }
                }
                if(callback.isCanceled()) {
                    return;
                }
                if(isDownloadSequences(query)) {
                    callback.setMessage("Downloading Sequences");
                    getMatchingAssemblyDocumentsForIds(workflowList, tissueSamples, limsResult.getSequenceIds(), callback, true);
                }

            } catch (DatabaseServiceException e) {
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

    /**
     * @param workflows     Used to retrieve FIMS data if not null
     * @param samples       Used to retrieve FIMS data if not null
     * @param sequenceIds   The sequences to retrieve
     * @param callback      To add documents to
     * @param includeFailed true to included empty sequences for failed results
     * @return A list of the documents found/added
     * @throws SQLException if anything goes wrong
     */
    public List<AnnotatedPluginDocument> getMatchingAssemblyDocumentsForIds(
            final Collection<WorkflowDocument> workflows, final List<FimsSample> samples,
            List<Integer> sequenceIds, RetrieveCallback callback, boolean includeFailed) throws DatabaseServiceException {
        if (sequenceIds.isEmpty()) {
            return Collections.emptyList();
        }

        if (!BiocodeService.getInstance().isLoggedIn()) {
            return Collections.emptyList();
        }
        List<AnnotatedPluginDocument> resultDocuments = new ArrayList<AnnotatedPluginDocument>();
        final List<String> missingTissueIds = new ArrayList<String>();
        ArrayList<AnnotatedPluginDocument> documentsWithoutFimsData = new ArrayList<AnnotatedPluginDocument>();

        List<AssembledSequence> sequences = limsConnection.getAssemblyDocuments(sequenceIds, callback, includeFailed);

        try {
            for (final AssembledSequence seq : sequences) {
                AnnotatedPluginDocument doc = createAssemblyDocument(seq);
                FimsDataGetter getter = new FimsDataGetter() {
                    public FimsData getFimsData(AnnotatedPluginDocument document) throws DocumentOperationException {
                        if (workflows != null) {
                            for (WorkflowDocument workflow : workflows) {
                                if (workflow.getId() == seq.workflowId) {
                                    return new FimsData(workflow, null, null);
                                }
                            }
                        }

                        String tissueId = seq.sampleId;
                        if (samples != null) {
                            for (FimsSample sample : samples) {
                                if (sample.getId().equals(tissueId)) {
                                    return new FimsData(sample, null, null);
                                }
                            }
                        }
                        if (!BiocodeService.getInstance().isLoggedIn()) {
                            return null;
                        }
                        FimsSample fimsSample = BiocodeService.getInstance().getActiveFIMSConnection().getFimsSampleFromCache(tissueId);
                        if (fimsSample != null) {
                            return new FimsData(fimsSample, null, null);
                        } else {
                            document.setFieldValue(BiocodeService.getInstance().getActiveFIMSConnection().getTissueSampleDocumentField(), tissueId);
                            missingTissueIds.add(tissueId);
                        }
                        return null;
                    }
                };

                ArrayList<String> failBlog = new ArrayList<String>();
                AnnotateUtilities.annotateDocument(getter, failBlog, doc, false);
                if (failBlog.size() == 0) {
                    resultDocuments.add(doc);
                    if (callback != null) {
                        callback.add(doc, Collections.<String, Object>emptyMap());
                    }
                } else {
                    // Will be added to callback later
                    documentsWithoutFimsData.add(doc);
                }
            }

            //annotate with FIMS data if we couldn't before...
            final List<FimsSample> newFimsSamples = BiocodeService.getInstance().getActiveFIMSConnection().retrieveSamplesForTissueIds(missingTissueIds);
            FimsDataGetter fimsDataGetter = new FimsDataGetter() {
                public FimsData getFimsData(AnnotatedPluginDocument document) throws DocumentOperationException {
                    String tissueId = (String) document.getFieldValue(BiocodeService.getInstance().getActiveFIMSConnection().getTissueSampleDocumentField());
                    if (tissueId != null) {
                        for (FimsSample sample : newFimsSamples) {
                            if (sample.getId().equals(tissueId)) {
                                return new FimsData(sample, null, null);
                            }
                        }
                    }
                    return null;
                }
            };
            for (AnnotatedPluginDocument doc : documentsWithoutFimsData) {
                AnnotateUtilities.annotateDocument(fimsDataGetter, new ArrayList<String>(), doc, false);
                resultDocuments.add(doc);
                if (callback != null) {
                    callback.add(doc, Collections.<String, Object>emptyMap());
                }
            }
            return resultDocuments;
        } catch (DocumentOperationException e) {
            throw new DatabaseServiceException(e, e.getMessage(), false);
        } catch (ConnectionException e) {
            throw new DatabaseServiceException(e, e.getMessage(), false);
        }
    }

    private AnnotatedPluginDocument createAssemblyDocument(AssembledSequence seq) {
        String qualities = seq.confidenceScore;
        DefaultNucleotideSequence sequence;
        URN urn = new URN("Biocode", limsConnection.getUrn(), "" + seq.id);
        String name = seq.extractionId + " " + seq.workflowLocus;

        if (qualities == null || seq.progress == null || seq.progress.toLowerCase().contains("failed")) {
            String consensus = seq.consensus;
            String description = "Assembly consensus sequence for " + name;
            if (consensus == null || seq.date == null) {
                consensus = "";
            } else if (seq.progress == null || seq.progress.toLowerCase().contains("failed")) {
                consensus = "";
                description = "Sequencing failed for this well";
            }
            consensus = consensus.replace("-", "");
            sequence = new DefaultNucleotideSequence(name, description, consensus, new Date(seq.date), urn);
        } else {
            String sequenceString = seq.consensus;
            sequenceString = sequenceString.replace("-", "");
            NucleotideGraph graph = DefaultNucleotideGraph.createNucleotideGraph(null, null, qualitiesFromString(qualities), sequenceString.length(), 0);
            Date dateMarked = new Date(seq.date);
            sequence = new DefaultNucleotideGraphSequence(name, "Assembly consensus sequence for " + name, sequenceString, dateMarked, graph, urn);
            sequence.setFieldValue(PluginDocument.MODIFIED_DATE_FIELD, dateMarked);
        }
        AnnotatedPluginDocument doc = DocumentUtilities.createAnnotatedPluginDocument(sequence);

        //todo: add data as fields and notes...
        String notes = seq.assemblyNotes;
        if (notes != null) {
            doc.setFieldValue(AnnotateUtilities.NOTES_FIELD, notes);
        }
        doc.setFieldValue(LIMSConnection.WORKFLOW_LOCUS_FIELD, seq.workflowLocus);
        doc.setFieldValue(AnnotateUtilities.PROGRESS_FIELD, seq.progress);
        doc.setFieldValue(DocumentField.CONTIG_MEAN_COVERAGE, seq.coverage);
        doc.setFieldValue(DocumentField.DISAGREEMENTS, seq.numberOfDisagreements);
        doc.setFieldValue(AnnotateUtilities.EDITS_FIELD, seq.numOfEdits);
        doc.setFieldValue(AnnotateUtilities.TRIM_PARAMS_FWD_FIELD, seq.forwardTrimParameters);
        doc.setFieldValue(AnnotateUtilities.TRIM_PARAMS_REV_FIELD, seq.reverseTrimParameters);
        doc.setHiddenFieldValue(AnnotateUtilities.LIMS_ID, seq.limsId);
        //todo: fields that require a schema change
        //noinspection ConstantConditions
        doc.setFieldValue(AnnotateUtilities.TECHNICIAN_FIELD, seq.technician);
        doc.setFieldValue(DocumentField.CREATED_FIELD, new Date(seq.date));
        doc.setFieldValue(DocumentField.BIN, seq.bin);
        doc.setFieldValue(AnnotateUtilities.AMBIGUITIES_FIELD, seq.numberOfAmbiguities);
        doc.setFieldValue(AnnotateUtilities.ASSEMBLY_PARAMS_FIELD, seq.assemblyParameters);
        doc.setFieldValue(LIMSConnection.SEQUENCE_ID, seq.id);
        doc.setFieldValue(LIMSConnection.SEQUENCE_SUBMISSION_PROGRESS, seq.submitted ? "Yes" : "No");
        doc.setFieldValue(LIMSConnection.EDIT_RECORD, seq.editRecord);
        doc.setFieldValue(LIMSConnection.EXTRACTION_ID_FIELD, seq.extractionId);
        doc.setFieldValue(LIMSConnection.EXTRACTION_BARCODE_FIELD, seq.extractionBarcode);
        return doc;
    }

    private int[] qualitiesFromString(String qualString) {
        String[] values = qualString.split(",");
        int[] result = new int[values.length];
        for (int i = 0; i < values.length; i++) {
            result[i] = Integer.parseInt(values[i]);
        }
        return result;
    }

    private boolean isFimsTermQuery(Query query) {
        return query instanceof AdvancedSearchQueryTerm && activeFIMSConnection.getSearchAttributes().contains(((AdvancedSearchQueryTerm) query).getField());
    }

    private boolean areBrowseQueries(List<? extends Query> queries) {
        for (Query query : queries) {
            if(query instanceof CompoundSearchQuery) {
                if(!areBrowseQueries(((CompoundSearchQuery) query).getChildren())) {
                    return false;
                }
            } else if (query instanceof AdvancedSearchQueryTerm) {
                AdvancedSearchQueryTerm fieldQuery = (AdvancedSearchQueryTerm) query;
                if(fieldQuery.getCondition() != Condition.CONTAINS) {
                    return false;
                }
                for (Object value : fieldQuery.getValues()) {
                    if(!(value instanceof String) || ((String)value).trim().length() > 0) {
                        return false;
                    }
                }
            } else if(query instanceof BasicSearchQuery) {
                if(((BasicSearchQuery) query).getSearchText().trim().length() > 0) {
                    return false;
                }
            } else {
                if(!query.isBrowse()) {
                    return false;
                }
            }
        }
        return true;
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

    public void addNewCocktails(List<? extends Cocktail> newCocktails) throws DatabaseServiceException {
        if(newCocktails.size() > 0) {
            limsConnection.addCocktails(newCocktails);
        }
        buildCaches();
    }

    public boolean deleteAllowed(String tableName) throws DatabaseServiceException {
        if(!isLoggedIn) {
            throw new DatabaseServiceException("You need to be logged in", false);
        }
        return limsConnection.deleteAllowed(tableName);


    }

    public void removeCocktails(List<? extends Cocktail> deletedCocktails) throws DatabaseServiceException {
        if(deletedCocktails == null || deletedCocktails.size() == 0) {
            return;
        }
        limsConnection.deleteCocktails(deletedCocktails);
        buildCaches();
    }

    private List<Thermocycle> PCRThermocycles = null;
    private List<Thermocycle> cyclesequencingThermocycles = null;
    private List<PCRCocktail> PCRCocktails = null;
    private List<CycleSequencingCocktail> cyclesequencingCocktails = null;
    private List<DisplayFieldsTemplate> extractionDisplayedFields = null;
    private List<DisplayFieldsTemplate> pcrDisplayedFields = null;
    private List<DisplayFieldsTemplate> cycleSequencingDisplayedFields = null;

    public void buildCaches() throws DatabaseServiceException {
        try {
            buildCachesFromDisk();
        } catch (IOException e) {
            throw new DatabaseServiceException(e, "Could not read the caches from disk", false);
        } catch (JDOMException e) {
            throw new DatabaseServiceException(e, "Could not read the caches from disk", false);
        } catch (XMLSerializationException e) {
            throw new DatabaseServiceException(e, "Could not read the caches from disk", false);
        }
        PCRThermocycles = limsConnection.getThermocyclesFromDatabase(Thermocycle.Type.pcr);
        cyclesequencingThermocycles = limsConnection.getThermocyclesFromDatabase(Thermocycle.Type.cyclesequencing);
        PCRCocktails = limsConnection.getPCRCocktailsFromDatabase();
        cyclesequencingCocktails = limsConnection.getCycleSequencingCocktailsFromDatabase();
        try {
            saveCachesToDisk();
        } catch (IOException e) {
            throw new DatabaseServiceException(e, "Could not write the caches to disk", false);
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
        saveCocktailsToDisk(Cocktail.Type.pcr, PCRCocktails);
        saveCocktailsToDisk(Cocktail.Type.cyclesequencing, cyclesequencingCocktails);
        saveDisplayedFieldsToDisk(Reaction.Type.Extraction, extractionDisplayedFields);
        saveDisplayedFieldsToDisk(Reaction.Type.PCR, pcrDisplayedFields);
        saveDisplayedFieldsToDisk(Reaction.Type.CycleSequencing, cycleSequencingDisplayedFields);
    }

    private List<CycleSequencingCocktail> getCycleSequencingCocktailsFromDisk() throws JDOMException, IOException, XMLSerializationException{
        File file = new File(dataDirectory, Cocktail.Type.cyclesequencing.cacheFilename);
        return (List<CycleSequencingCocktail>)getCocktails(file);
    }

    private List<PCRCocktail> getPCRCocktailsFromDisk() throws IOException, JDOMException, XMLSerializationException {
        File file = new File(dataDirectory, Cocktail.Type.pcr.cacheFilename);
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

    private void saveCocktailsToDisk(Cocktail.Type type, List<? extends Cocktail> cocktails) throws IOException {
        File file = new File(dataDirectory, type.cacheFilename);
        if(!file.exists()) {
            createNewFile(file);
        }
        saveCocktails(file, cocktails);
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
        String name = getPreferencesForService().get(type+"_defaultFieldsTemplate", null);
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
        Preferences prefs = getPreferencesForService();
        prefs.put(template.getReactionType() + "_defaultFieldsTemplate", template.getName());
        try {
            prefs.flush();
        } catch (BackingStoreException e) {
            // Ignore.  Not much we can do if the backing store is failing.
        }
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

    public void removeThermoCycles(List<Thermocycle> cycles,  Thermocycle.Type type) throws DatabaseServiceException {
        limsConnection.deleteThermoCycles(type, cycles);
        buildCaches();
    }

    public void insertThermocycles(List<Thermocycle> cycles, Thermocycle.Type type) throws DatabaseServiceException {
        if(limsConnection == null) {
            throw new DatabaseServiceException("You are not logged in", false);
        }
        limsConnection.addThermoCycles(type, cycles);
        buildCaches();
    }

    /**
     * Displays a modal progress frame to prevent the user from touching anything while <code>runInBackgroundThread</code>
     * is executed in new thread. DOES NOT WAIT for the task to finish before returning, returns immediately. Can be called safely from the
     * event dispatch thread.
     *
     * @param message user message to display in the progress frame. Not null
     * @param parentComponent component within the window that should be the owner of the modal progress frame. Can be null to use main window.
     * @param runInBackgroundThread task to run in a background thread. Not null
     * @param runInSwingThreadWhenFinished optional task to run in the event dispatch thread when <code>runInBackgroundThread</code> is finished. May be null
     */
    public static void block(final String message, final JComponent parentComponent, final Runnable runInBackgroundThread,
                             final Runnable runInSwingThreadWhenFinished) {
        Window owner;
        if (parentComponent != null && parentComponent.getTopLevelAncestor() instanceof Window) {
            owner = (Window) parentComponent.getTopLevelAncestor();
        } else {
            owner = GuiUtilities.getMainFrame();
        }
        final ProgressFrame progressFrame = new ProgressFrame("", message, owner);
        progressFrame.setCancelable(false);
        progressFrame.setIndeterminateProgress();
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                try {
                    runInBackgroundThread.run();
                } finally {
                    progressFrame.setComplete();
                    if (runInSwingThreadWhenFinished != null) {
                        ThreadUtilities.invokeNowOrLater(runInSwingThreadWhenFinished);
                    }
                }
            }
        };
        new Thread(runnable, "Biocode blocking thread - " +message).start();
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

    public Map<String, String> getReactionToTissueIdMapping(String tableName, List<? extends Reaction> reactions) throws DatabaseServiceException{
        if(reactions.size() == 0 || !BiocodeService.getInstance().isLoggedIn()) {
            return Collections.emptyMap();
        }
        List<String> extractionIds = new ArrayList<String>();
        for (Reaction reaction : reactions) {
            if(reaction.isEmpty()) {
                continue;
            }
            extractionIds.add(reaction.getExtractionId());
        }
        return limsConnection.getTissueIdsForExtractionIds(tableName, extractionIds);
    }

    public void savePlate(Plate plate, ProgressListener progress) throws DatabaseServiceException, BadDataException {
        //set workflows for reactions that have id's
        List<Reaction> reactionsToSave = new ArrayList<Reaction>();
        List<String> workflowIdStrings = new ArrayList<String>();
        for(Reaction reaction : plate.getReactions()) {

            Object workflowId = reaction.getFieldValue("workflowId");
            Object tissueId = reaction.getFieldValue("sampleId");
            String extractionId = reaction.getExtractionId();

            if (!reaction.isEmpty()) {
                if (reaction.getType() != Reaction.Type.Extraction && reaction.getLocus().equals("None")) {
                    throw new BadDataException("Locus is not specified for reaction with extraction id " + reaction.getExtractionId());
                }
                reactionsToSave.add(reaction);
                if(extractionId != null && tissueId != null && tissueId.toString().length() > 0) {
                    if(reaction.getWorkflow() != null && workflowId.toString().length() > 0){
                        if(!reaction.getWorkflow().getExtractionId().equals(extractionId)) {
                            reaction.setHasError(true);
                            throw new BadDataException("The workflow "+workflowId+" does not match the extraction "+extractionId);
                        }
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
        limsConnection.savePlates(Collections.singletonList(plate), progress);
    }

    public void deletePlate(ProgressListener progress, Plate plate) throws DatabaseServiceException {

        Set<Integer> plateIds = limsConnection.deletePlates(Collections.singletonList(plate), progress);

        if(plate.getReactionType() == Reaction.Type.Extraction) {
            List<Plate> emptyPlates = limsConnection.getEmptyPlates(plateIds);
            if(emptyPlates.size() > 0) {
                StringBuilder message = new StringBuilder("Geneious has found the following empty plates in the database.\n  Do you want to delete them as well?\n");
                for(Plate emptyPlate : emptyPlates) {
                    //noinspection StringConcatenationInsideStringBufferAppend
                    message.append(emptyPlate.getName()+"\n");
                }
                if(Dialogs.showYesNoDialog(message.toString(), "Delete empty plates", null, Dialogs.DialogIcon.QUESTION)){
                    for(Plate p : emptyPlates) {
                        deletePlate(progress, p);
                    }
                }
            }
        }

        plate.setDeleted(true);

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
                Connection connection = connectionManager.getCurrentlySelectedConnection();
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

    public Map<BiocodeUtilities.Well, WorkflowDocument> getWorkflowsForCycleSequencingPlate(String plateName) throws DocumentOperationException, DatabaseServiceException {
        List<PlateDocument> plateList = limsConnection.getMatchingDocumentsFromLims(
                Query.Factory.createFieldQuery(LIMSConnection.PLATE_NAME_FIELD, Condition.EQUAL, new Object[]{plateName},
                        BiocodeService.getSearchDownloadOptions(false, false, true, false)), null, null
        ).getPlates();
        if(plateList.size() == 0) {
            throw new DocumentOperationException("The plate '"+plateName+"' does not exist in the database.");
        }
        assert plateList.size() == 1;
        if(plateList.get(0).getPlate().getReactionType() != Reaction.Type.CycleSequencing) {
            throw new DocumentOperationException("The plate '"+plateName+"' is not a cycle sequencing plate.");
        }
        Plate plate = plateList.get(0).getPlate();

        List<String> workflowNames = new ArrayList<String>();
        for(Reaction r : plate.getReactions()) {
            if(r.getWorkflow() != null) {
                workflowNames.add(r.getWorkflow().getName());
            }
        }
        List<WorkflowDocument> docs = BiocodeService.getInstance().getWorkflowDocumentsForNames(workflowNames);

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

    public Map<String, String> getWorkflowIds(List<String> idsToCheck, List<String> loci, Reaction.Type reactionType) throws DatabaseServiceException {
        if(idsToCheck.size() == 0) {
            return Collections.emptyMap();
        }
        return limsConnection.getWorkflowIds(idsToCheck, loci, reactionType);
    }

    public Map<String, Workflow> getWorkflows(Collection<String> workflowIds) throws DatabaseServiceException {
        List<Workflow> list = limsConnection.getWorkflows(workflowIds);
        Map<String, Workflow> result = new HashMap<String, Workflow>();
        for (Workflow workflow : list) {
            result.put(workflow.getName(), workflow);
        }
        return result;
    }

    public void registerCallback(BiocodeCallback callback) {
        activeCallbacks.add(callback);
    }

    public void unregisterCallback(BiocodeCallback callback) {
        activeCallbacks.remove(callback);
    }

    public List<WorkflowDocument> getWorkflowDocumentsForNames(List<String> workflowNames) throws DatabaseServiceException {
        List<WorkflowDocument> workflows;
        workflows = new ArrayList<WorkflowDocument>();

        Query workflowQuery;
        Map<String, Object> options = BiocodeService.getSearchDownloadOptions(false, true, false, false);
        if(workflowNames.size() == 1) {
            workflowQuery = Query.Factory.createFieldQuery(LIMSConnection.WORKFLOW_NAME_FIELD, Condition.EQUAL,
                    new Object[]{workflowNames.get(0)}, options);
        } else {
            List<Query> subQueries = new ArrayList<Query>();
            for (String id : workflowNames) {
                subQueries.add(Query.Factory.createFieldQuery(LIMSConnection.WORKFLOW_NAME_FIELD, Condition.EQUAL, id));
            }
            workflowQuery = Query.Factory.createOrQuery(subQueries.toArray(new Query[subQueries.size()]), options);
        }

        List<AnnotatedPluginDocument> results = BiocodeService.getInstance().retrieve(workflowQuery, ProgressListener.EMPTY);
        for (AnnotatedPluginDocument result : results) {
           if(WorkflowDocument.class.isAssignableFrom(result.getDocumentClass())) {
               PluginDocument doc = result.getDocumentOrNull();
               if(doc instanceof WorkflowDocument) {
                   workflows.add((WorkflowDocument)doc);
               }
           }
        }
        return workflows;
    }

    public List<String> getPlatesUsingThermocycle(Thermocycle thermocycle) throws DatabaseServiceException {
        return limsConnection.getPlatesUsingThermocycle(thermocycle.getId());
    }

    public Collection<String> getPlatesUsingCocktail(Cocktail cocktail) throws DatabaseServiceException {
        return limsConnection.getPlatesUsingCocktail(cocktail.getReactionType(), cocktail.getId());
    }

    public boolean isQueryCancled() {
        if (!isLoggedIn())
            return true;

        for(BiocodeCallback callback : activeCallbacks) {
            if(callback.isCanceled())
                return true;
        }

        return false;
    }
}