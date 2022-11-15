package com.biomatters.plugins.biocode.labbench;

import com.biomatters.geneious.publicapi.components.Dialogs;
import com.biomatters.geneious.publicapi.components.ProgressFrame;
import com.biomatters.geneious.publicapi.databaseservice.*;
import com.biomatters.geneious.publicapi.documents.*;
import com.biomatters.geneious.publicapi.documents.sequence.DefaultNucleotideGraph;
import com.biomatters.geneious.publicapi.documents.sequence.NucleotideGraph;
import com.biomatters.geneious.publicapi.documents.sequence.SequenceAlignmentDocument;
import com.biomatters.geneious.publicapi.documents.sequence.SequenceDocument;
import com.biomatters.geneious.publicapi.implementations.sequence.DefaultNucleotideGraphSequence;
import com.biomatters.geneious.publicapi.implementations.sequence.DefaultNucleotideSequence;
import com.biomatters.geneious.publicapi.plugin.*;
import com.biomatters.geneious.publicapi.utilities.GuiUtilities;
import com.biomatters.geneious.publicapi.utilities.StringUtilities;
import com.biomatters.geneious.publicapi.utilities.ThreadUtilities;
import com.biomatters.plugins.biocode.BiocodePlugin;
import com.biomatters.plugins.biocode.BiocodeUtilities;
import com.biomatters.plugins.biocode.assembler.annotate.AnnotateUtilities;
import com.biomatters.plugins.biocode.assembler.annotate.FimsData;
import com.biomatters.plugins.biocode.assembler.annotate.FimsDataGetter;
import com.biomatters.plugins.biocode.assembler.download.DownloadChromatogramsFromLimsOperation;
import com.biomatters.plugins.biocode.assembler.download.DownloadChromatogramsFromLimsOptions;
import com.biomatters.plugins.biocode.labbench.connection.Connection;
import com.biomatters.plugins.biocode.labbench.connection.ConnectionManager;
import com.biomatters.plugins.biocode.labbench.fims.*;
import com.biomatters.plugins.biocode.labbench.fims.biocode.BiocodeFIMSConnection;
import com.biomatters.plugins.biocode.labbench.fims.geome.geomeFIMSConnection;
import com.biomatters.plugins.biocode.labbench.lims.LIMSConnection;
import com.biomatters.plugins.biocode.labbench.lims.LimsSearchCallback;
import com.biomatters.plugins.biocode.labbench.lims.LimsSearchResult;
import com.biomatters.plugins.biocode.labbench.plates.Plate;
import com.biomatters.plugins.biocode.labbench.reaction.*;
import com.biomatters.plugins.biocode.labbench.reporting.ReportingService;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.google.common.base.Function;
import jebl.util.Cancelable;
import jebl.util.CompositeProgressListener;
import jebl.util.ProgressListener;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.input.SAXBuilder;
import org.jdom.output.Format;
import org.jdom.output.XMLOutputter;
import org.virion.jam.framework.AbstractFrame;

import javax.annotation.Nullable;
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
import java.util.concurrent.atomic.AtomicReference;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

/**
 * @author Steven Stones-Havas
 *          <p/>
 *          Created on 23/02/2009 4:41:26 PM
 */
@SuppressWarnings({"ConstantConditions"})
public class BiocodeService extends PartiallyWritableDatabaseService {

    private static final String DOWNLOAD_TISSUES = "tissueDocuments";
    private static final String DOWNLOAD_WORKFLOWS = "workflowDocuments";
    private static final String DOWNLOAD_PLATES = "plateDocuments";
    private static final String DOWNLOAD_SEQS = "sequenceDocuments";
    private static final String DOWNLOAD_ASSEMBLIES = "re-assemble";
    private boolean isLoggedIn = false;
    private FIMSConnection activeFIMSConnection;
    private LIMSConnection limsConnection;
    private final Object limsConnectionLock = new Object();
    private final String loggedOutMessage = "Right click on the " + getName() + " service in the service tree to log in.";
    private Driver driver;
    private Driver localDriver;
    private static BiocodeService instance = new BiocodeService();
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
    private DisconnectCheckingThread disconnectCheckingThread;
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

        getActiveLIMSConnection().deleteSequences(sequencesToDelete);

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
            getActiveLIMSConnection().renameWorkflow(doc.getWorkflow().getId(), newValue.getValue().toString());
        }

        if(PlateDocument.class.isAssignableFrom(document.getDocumentClass())) {
            PlateDocument doc = (PlateDocument)document.getDocumentOrThrow(DatabaseServiceException.class);
            getActiveLIMSConnection().renamePlate(doc.getPlate().getId(), newValue.getValue().toString());
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
        synchronized (limsConnectionLock) {
            if (limsConnection == null) {
                throw new DatabaseServiceException("No active Lims connection, please try to relogin Biocode service.", false);
            }
            return limsConnection;
        }
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
                new CheckboxSearchOption(DOWNLOAD_SEQS, "Sequences", false),
                new CheckboxSearchOption(DOWNLOAD_ASSEMBLIES, "Ref assemblies", false),
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
        return isDownloadTypeQuery(query, DOWNLOAD_SEQS) || isDownloadTypeQuery(query, DOWNLOAD_ASSEMBLIES);
    }

    private static boolean isDownloadTypeQuery(Query query, String type) {
        return !Boolean.FALSE.equals(query.getExtendedOptionValue(type));
    }

    public static Map<String, Object> getSearchDownloadOptions(boolean tissues, boolean workflows, boolean plates, boolean sequences, boolean assemblies) {
        Map<String, Object> searchOptions = new HashMap<String, Object>();
        searchOptions.put(DOWNLOAD_TISSUES, tissues);
        searchOptions.put(DOWNLOAD_WORKFLOWS, workflows);
        searchOptions.put(DOWNLOAD_PLATES, plates);
        searchOptions.put(DOWNLOAD_SEQS, sequences);
        searchOptions.put(DOWNLOAD_ASSEMBLIES, assemblies);
        return searchOptions;
    }

    public static FIMSConnection[] getFimsConnections() {
        return new FIMSConnection[] {
                new ExcelFimsConnection(),
                new geomeFIMSConnection(),
                //new FusionTablesFimsConnection(),
                new MySQLFimsConnection(),
                new MooreaFimsConnection()
                //new TAPIRFimsConnection(),
                //new BiocodeFIMSConnection(),
        };
    }

    public Driver getDriver() throws ConnectionException {
        String error = loadMySqlDriver();
        if (error != null) {
            throw new ConnectionException(error);
        }

        if(driver == null) {
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

        synchronized (limsConnectionLock) {
            if(limsConnection != null) {
                limsConnection.disconnect();
            }
            limsConnection = null;
        }

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
        connect(connection, block, false);
    }

    public void connect(Connection connection, boolean block, boolean needDisconnectionThread) {
        synchronized (this) {
            loggingIn = true;
        }
        final ProgressListener progressListener;
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
            synchronized (limsConnectionLock) {
                limsConnection = connection.getLIMSConnection();
            }
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
                                Dialogs.DialogOptions dialogOptions = new Dialogs.DialogOptions(new String[]{"OK"}, "Error connecting to FIMS");
                                dialogOptions.setMoreOptionsButtonText("Show Details", "Hide Details");
                                Dialogs.showMoreOptionsDialog(dialogOptions, message, e.getMessage());
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
                    progressListener.setProgress(1.0);
                }
                return;
            }
            ThreadUtilities.sleep(1000);
        }

        if (!fimsSuccessfullyConnected.get()) {
            return;
        }

        try {
            if(!(activeFIMSConnection instanceof MooreaFimsConnection) && connection.getLimsOptions().getValueAsString("server").equalsIgnoreCase("gall.bnhm.berkeley.edu")) {
                Dialogs.showMessageDialog("You cannot connect to the Moorea Lab Bench database using a field database other than the Moorea FIMS");
                logOut();
                progressListener.setProgress(1.0);
                return;
            }

            progressListener.setMessage("Connecting to the LIMS");

            if(disconnectCheckingThread != null) {
                disconnectCheckingThread.interrupt();
            }

            PasswordOptions limsOptions = connection.getLimsOptions();
            getActiveLIMSConnection().connect(limsOptions);

            progressListener.setMessage("Building Caches");
            buildCaches();

            progressListener.setMessage("Performing Further Initialization");
            getActiveLIMSConnection().doAnyExtraInitialization(progressListener);

            synchronized (this) {
                isLoggedIn = true;
                loggingIn = false;
            }
            if(reportingService != null) {
                reportingService.notifyLoginStatusChanged();
            }

            if (needDisconnectionThread) {
                disconnectCheckingThread = new DisconnectCheckingThread();
                disconnectCheckingThread.start();
            }
        } catch (ConnectionException e1) {
            // todo Surface exception in server.  The current error handling swallows the exception when there is no GUI.  ie Server mode
            progressListener.setProgress(1.0);
            logOut();
            if(e1 == ConnectionException.NO_DIALOG) {
                return;
            }
            String title = "Connection Failure";
            String message = "Geneious could not connect to the LIMS database";
            BiocodeUtilities.displayExceptionDialog(title, message + ": " + e1.getMessage(), e1, null);
            return;
        } catch (DatabaseServiceException e3) {
            logOut();
            progressListener.setProgress(1.0);
            String title = "Connection Failure";
            String message = "Geneious could not connect to the LIMS database";
            BiocodeUtilities.displayExceptionDialog(title, message + ": " + e3.getMessage(), e3, null);
        } finally {
            progressListener.setProgress(1.0);
        }
        updateStatus();
    }

    public synchronized String loadMySqlDriver() {
        String error = null;
        if (driver == null) {
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
        }

        return error;
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

    private class DisconnectCheckingThread extends Thread {

        /**
         * A lock to prevent this thread from being interrupted when it is waiting for the ServiceTree status to update
         */
        private final Object interruptionLock = new Object();

        @Override
        public void run() {
            LIMSConnection originalConnection = null;
            while(isLoggedIn()) {
                LIMSConnection activeLIMSConnection;
                try {
                    activeLIMSConnection = getActiveLIMSConnection();

                    //for potential memory leak
                    if (originalConnection == null)
                        originalConnection = activeLIMSConnection;
                    else if (originalConnection != activeLIMSConnection)
                        break;
                } catch (DatabaseServiceException e) {
                    e.printStackTrace();
                    break;
                }

                if(activeCallbacks.isEmpty()) {
                    try {
                        activeLIMSConnection.testConnection();
                    } catch (DatabaseServiceException e) {
                        if(!e.getMessage().contains("Streaming result set")) {  //last ditch attempt to stop the system logging users out incorrectly - we should have caught all cases of this because the operations creating streaming result sets should have registered their callbacks/progress listeners with the service
                            e.printStackTrace();
                            if(isLoggedIn()) {
                                // Prevent the thread from being interrupted while logging out
                                synchronized (interruptionLock) {
                                    logOut();
                                }
                            }
                        }
                    }
                }
                ThreadUtilities.sleep(60*1000);
            }
        }

        @Override
        public void interrupt() {
            synchronized (interruptionLock) {
                super.interrupt();
            }
        }
    }

    @Override
    public void addDatabaseServiceListener(DatabaseServiceListener listener) {
        super.addDatabaseServiceListener(listener);
        listener.searchableStatusChanged(isLoggedIn, loggedOutMessage);
    }
    
    /**
     *
     * @param urns
     * @param callback
     * 11 geneious reports this error having to do with urns: contained
     *      in XML responses and requiring this method.  However, JBD is fairly certain nothing
     *      needs to be done with this information.
     */
    public void retrieve(URN[] urns, RetrieveCallback callback) {
        //do nothing
    }

    public void retrieve(Query query, RetrieveCallback callback, URN[] urnsToNotRetrieve) throws DatabaseServiceException {
        retrieve(query, callback, urnsToNotRetrieve, false);
    }

    public void retrieve(Query query, RetrieveCallback callback, URN[] urnsToNotRetrieve, boolean hasAlreadyTriedReconnect) throws DatabaseServiceException {
        retrieve(query, callback, urnsToNotRetrieve, false, false);
    }

    private Set<BiocodeCallback> activeCallbacks = new HashSet<BiocodeCallback>();

    public void retrieve(Query query, RetrieveCallback rc, URN[] urnsToNotRetrieve, boolean hasAlreadyTriedReconnect, boolean allowEmptyQuery) throws DatabaseServiceException {
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

                    tissueIdsMatchingFimsQuery = activeFIMSConnection.getTissueIdsMatchingQuery(toSearchFimsWith, null, allowEmptyQuery);
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
                LimsSearchResult limsResult = getActiveLIMSConnection().getMatchingDocumentsFromLims(query,
                        areBrowseQueries(fimsQueries) ? null : tissueIdsMatchingFimsQuery, callback);

                if(!limsResult.getPlateIds().isEmpty() && isDownloadPlates(query)) {
                    callback.setMessage("Downloading " + BiocodeUtilities.getCountString("matching plate document", limsResult.getPlateIds().size()) + "...");
                    getActiveLIMSConnection().retrievePlates(
                            limsResult.getPlateIds(),
                            LimsSearchCallback.forRetrievePluginDocumentCallback(callback, new Function<Plate, PlateDocument>() {
                                @Override
                                public PlateDocument apply(Plate plate) {
                                    return new PlateDocument(plate);
                                }
                            })
                    );
                }

                if(callback.isCanceled()) {
                    return;
                }

                if(isDownloadWorkflows(query) && !limsResult.getWorkflowIds().isEmpty()) {
                    callback.setMessage("Downloading " + BiocodeUtilities.getCountString("matching workflow document", limsResult.getWorkflowIds().size()) + "...");
                    getActiveLIMSConnection().retrieveWorkflowsById(limsResult.getWorkflowIds(),
                            LimsSearchCallback.forRetrievePluginDocumentCallback(callback, new Function<WorkflowDocument, PluginDocument>() {
                                @Override
                                public PluginDocument apply(WorkflowDocument workflowDocument) {
                                    return workflowDocument;
                                }
                            }));
                }

                if(callback.isCanceled()) {
                    return;
                }

                Set<String> tissueIdsToDownload = new HashSet<String>();
                if(isDownloadTissues(query) || isDownloadSequences(query)) {
                    // Now add tissues that match the LIMS query
                    // FimsSamples would have been downloaded as part of plate creation.  Collect them now.
                    tissueIdsToDownload.addAll(limsResult.getTissueIds());
                }

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
                        tissueIdsToDownload.addAll(tissueIdsMatchingFimsQuery);
                    }
                }
                List<FimsSample> tissueSamples = new ArrayList<FimsSample>();
                try {
                    if(!tissueIdsToDownload.isEmpty()) {
                        callback.setMessage("Downloading " + BiocodeUtilities.getCountString("matching tissue", tissueIdsToDownload.size()) + "...");
                        tissueSamples.addAll(activeFIMSConnection.retrieveSamplesForTissueIds(tissueIdsToDownload));
                    }
                } catch (ConnectionException e) {
                    throw new DatabaseServiceException(e, e.getMessage(), false);
                }
                if(isDownloadTissues(query)) {
                    for (FimsSample tissueSample : tissueSamples) {
                        callback.add(new TissueDocument(tissueSample), Collections.<String, Object>emptyMap());
                    }
                }
                if(callback.isCanceled()) {
                    return;
                }
                if(isDownloadSequences(query)) {
                    callback.setMessage("Downloading " + BiocodeUtilities.getCountString("matching sequence", limsResult.getSequenceIds().size()) + "...");
                    retrieveMatchingAssemblyDocumentsForIds(tissueSamples, limsResult.getSequenceIds(), callback, true,
                            isDownloadTypeQuery(query, DOWNLOAD_SEQS),
                            isDownloadTypeQuery(query, DOWNLOAD_ASSEMBLIES));
                }

            } catch (DatabaseServiceException e) {
                e.printStackTrace();
                String message = e.getMessage();
                boolean isNetwork = true;
                if(message != null && message.contains("Streaming result") && message.contains("is still active")) {
                    if(!hasAlreadyTriedReconnect) {
                        try {
                            System.out.println("attempting a reconnect...");
                            getActiveLIMSConnection().reconnect();
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
     * @param samples       Used to retrieve FIMS data if not null
     * @param sequenceIds   The sequences to retrieve
     * @param callback      To add documents to.  Must not be null
     * @param includeFailed true to included empty sequences for failed results
     * @param addSequences
     * @param reassemble  @return A list of the documents found/added
     *
     * @throws SQLException if anything goes wrong
     */
    public void retrieveMatchingAssemblyDocumentsForIds(final List<FimsSample> samples,
                                                        List<Integer> sequenceIds,
                                                        final RetrieveCallback callback,
                                                        boolean includeFailed,
                                                        final boolean addSequences, final boolean reassemble)
            throws DatabaseServiceException {
        if (!BiocodeService.getInstance().isLoggedIn() || sequenceIds.isEmpty()) {
            return;
        }

        final String limsUrn = getActiveLIMSConnection().getUrn();
        final List<String> missingTissueIds = new ArrayList<String>();
        final List<AnnotatedPluginDocument> documentsWithoutFimsData = new ArrayList<AnnotatedPluginDocument>();
        final AtomicReference<DocumentOperationException> exceptionDuringAnnotate = new AtomicReference<DocumentOperationException>();

        final List<AnnotatedPluginDocument> toAssemble = new ArrayList<AnnotatedPluginDocument>();

        RetrieveCallback downloadCallbackThatCanAssemble = new RetrieveCallback() {

            @Override
            protected void _add(PluginDocument pluginDocument, Map<String, Object> map) {
                throw new IllegalStateException("Should only be returning APDs");
            }

            @Override
            protected void _add(AnnotatedPluginDocument annotatedPluginDocument, Map<String, Object> map) {
                if (addSequences) {
                    callback.add(annotatedPluginDocument, map);
                }
                if (reassemble) {
                    toAssemble.add(annotatedPluginDocument);
                }
            }
        };

        LimsSearchCallback<AssembledSequence> limsCallback = LimsSearchCallback.forRetrieveAnnotatedPluginDocumentCallback(downloadCallbackThatCanAssemble,
                new Function<AssembledSequence, AnnotatedPluginDocument>() {
                    @Nullable
                    @Override
                    public AnnotatedPluginDocument apply(final @Nullable AssembledSequence seq) {
                        //noinspection ThrowableResultOfMethodCallIgnored
                        if(exceptionDuringAnnotate.get() != null) {
                            return null;  // exception has to be thrown later.  Function doesn't throw exceptions
                        }

                        AnnotatedPluginDocument doc = createAssemblyDocument(seq, limsUrn);
                        FimsDataGetter getter = new FimsDataGetter() {
                            public FimsData getFimsData(AnnotatedPluginDocument document) throws DocumentOperationException {
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
                        try {
                            // todo workflow???
                            AnnotateUtilities.annotateDocument(getter, failBlog, doc, false);
                        } catch (DocumentOperationException e) {
                            exceptionDuringAnnotate.set(e);
                            return null;
                        }
                        if (failBlog.size() == 0) {
                            return doc;
                        } else {
                            // Will be added to callback later
                            documentsWithoutFimsData.add(doc);
                            return null;
                        }
                    }
                }
        );

        getActiveLIMSConnection().retrieveAssembledSequences(sequenceIds, limsCallback, includeFailed);

        try {
            DocumentOperationException documentOperationException = exceptionDuringAnnotate.get();
            if(documentOperationException != null) {
                throw documentOperationException;
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
                downloadCallbackThatCanAssemble.add(doc, Collections.<String, Object>emptyMap());
            }
            if(!toAssemble.isEmpty()) {
                reassembleLimsSequences(toAssemble, callback);
            }
        } catch (DocumentOperationException e) {
            throw new DatabaseServiceException(e, e.getMessage(), false);
        } catch (ConnectionException e) {
            throw new DatabaseServiceException(e, e.getMessage(), false);
        }
    }

    public static DocumentField FWD_PLATE_FIELD = DocumentField.createStringField("Forward Plate", "", "forwardSeqPlate");
    public static DocumentField REV_PLATE_FIELD = DocumentField.createStringField("Reverse Plate", "", "reverseSeqPlate");

    private AnnotatedPluginDocument createAssemblyDocument(AssembledSequence seq, String limsUrn) {
        String qualities = seq.confidenceScore;
        DefaultNucleotideSequence sequence;
        URN urn = new URN("Biocode", limsUrn, "" + seq.id);
        String name = seq.extractionId + " " + seq.workflowLocus;

        if (qualities == null || seq.progress == null || seq.progress.toLowerCase().contains("failed")) {
            String consensus = seq.consensus;
            String description = "Assembly consensus sequence for " + name;
            if (consensus == null || seq.date == null) {
                consensus = "";
            } else if (seq.progress == null || seq.progress.toLowerCase().contains("failed")) {
                //consensus = "";
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
        doc.setFieldValue(BiocodeUtilities.WORKFLOW_NAME_FIELD, seq.workflowName);
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

        AnnotateUtilities.setSequencingPrimerNote(doc, seq.forwardPrimerName, seq.forwardPrimerSequence, seq.reversePrimerName, seq.reversePrimerSequence);

        doc.setFieldValue(FWD_PLATE_FIELD, seq.forwardPlate);
        doc.setFieldValue(REV_PLATE_FIELD, seq.reversePlate);

        return doc;
    }

    private void reassembleLimsSequences(List<AnnotatedPluginDocument> docs, final RetrieveCallback callback) throws DatabaseServiceException {
        DownloadChromatogramsFromLimsOperation downloadAssembly = new DownloadChromatogramsFromLimsOperation(true);
        final List<AnnotatedPluginDocument> results = new ArrayList<AnnotatedPluginDocument>();

        Multimap<String, AnnotatedPluginDocument> batches = ArrayListMultimap.create();
        for (AnnotatedPluginDocument doc : docs) {
            if("failed".equals(doc.getFieldValue(AnnotateUtilities.PROGRESS_FIELD))) {
                continue;  // We only want to download passed sequences
            }
            String key = String.valueOf(doc.getFieldValue(FWD_PLATE_FIELD)) + String.valueOf(doc.getFieldValue(REV_PLATE_FIELD));
            batches.put(key, doc);
        }

        try {
            Options _options = downloadAssembly.getOptions(docs);
            if(!(_options instanceof DownloadChromatogramsFromLimsOptions)) {
                throw new IllegalStateException("");
            }
            DownloadChromatogramsFromLimsOptions options = (DownloadChromatogramsFromLimsOptions)_options;
            options.downloadMethodOption.setValue(options.SELECTED_SEQUENCES);
            options.assembleTracesOption.setValue(true);

            int currentSeqIndex = 1;
            final int numSeqs = batches.size();
            final CompositeProgressListener compositeProgressListener = new CompositeProgressListener(callback, numSeqs);
            DocumentOperation.OperationCallback operationCallback = new DocumentOperation.OperationCallback() {
                int count = 1;
                @Override
                public AnnotatedPluginDocument addDocument(AnnotatedPluginDocument annotatedPluginDocument, boolean b, ProgressListener progressListener) throws DocumentOperationException {
                    if (SequenceAlignmentDocument.class.isAssignableFrom(annotatedPluginDocument.getDocumentClass())) {
                        if (!callback.isCanceled()) {
                            callback.add(annotatedPluginDocument, Collections.<String, Object>emptyMap());
                            compositeProgressListener.beginSubtask("Assembled " + annotatedPluginDocument.getName() + " (" + count++ + " of " + numSeqs + ")");
                        }
                    }
                    return annotatedPluginDocument;
                }
            };


            for (Collection<AnnotatedPluginDocument> batch : batches.asMap().values()) {
                int startIndex = currentSeqIndex;
                currentSeqIndex = currentSeqIndex + batch.size();
                String message = "Downloading original traces";
                if(numSeqs > 1) {
                    message += " for sequences " + startIndex + " to " + (currentSeqIndex - 1) + " (of " + numSeqs + ")";
                }
                callback.setMessage(message + "...");
                downloadAssembly.performOperation(
                        new ArrayList<AnnotatedPluginDocument>(batch).toArray(new AnnotatedPluginDocument[batch.size()]),
                        new ProgressListenerThatOnlyRespectsCancel(callback), options, new SequenceSelection(), operationCallback
                );
            }
        } catch (DocumentOperationException e) {
            throw new DatabaseServiceException(e, "Couldn't download traces: " + e.getMessage(), false);
        }
    }

    private static class ProgressListenerThatOnlyRespectsCancel extends ProgressListener {

        private Cancelable cancelable;

        public ProgressListenerThatOnlyRespectsCancel(Cancelable cancelable) {
            this.cancelable = cancelable;
        }

        @Override
        protected void _setProgress(double v) {

        }

        @Override
        protected void _setIndeterminateProgress() {

        }

        @Override
        protected void _setMessage(String s) {

        }

        @Override
        public boolean isCanceled() {
            return cancelable.isCanceled();
        }
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
            getActiveLIMSConnection().addCocktails(newCocktails);
        }
        buildCaches();
    }

    public boolean deleteAllowed(String tableName) throws DatabaseServiceException {
        if(!isLoggedIn) {
            throw new DatabaseServiceException("You need to be logged in", false);
        }
        return getActiveLIMSConnection().deleteAllowed(tableName);


    }

    public void removeCocktails(List<? extends Cocktail> deletedCocktails) throws DatabaseServiceException {
        if(deletedCocktails == null || deletedCocktails.size() == 0) {
            return;
        }
        getActiveLIMSConnection().deleteCocktails(deletedCocktails);
        buildCaches();
    }

    private List<Thermocycle> PCRThermocycles = null;
    private List<Thermocycle> cyclesequencingThermocycles = null;
    private List<PCRCocktail> PCRCocktails = null;
    private List<CycleSequencingCocktail> cyclesequencingCocktails = null;
    private Map<Reaction.Type, List<DisplayFieldsTemplate>> reactionToDisplayableFields = new HashMap<Reaction.Type, List<DisplayFieldsTemplate>>();

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
        PCRThermocycles = getActiveLIMSConnection().getThermocyclesFromDatabase(Thermocycle.Type.pcr);
        cyclesequencingThermocycles = getActiveLIMSConnection().getThermocyclesFromDatabase(Thermocycle.Type.cyclesequencing);
        PCRCocktails = getActiveLIMSConnection().getPCRCocktailsFromDatabase();
        cyclesequencingCocktails = getActiveLIMSConnection().getCycleSequencingCocktailsFromDatabase();
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
        for (Reaction.Type type : Reaction.Type.values()) {
            reactionToDisplayableFields.put(type, getDisplayFieldsTemplatesFromDisk(type));
        }
    }

    private void saveCachesToDisk() throws IOException {
        saveThermocyclesToDisk("pcr_thermocycle", PCRThermocycles);
        saveThermocyclesToDisk("cyclesequencing_thermocycle", cyclesequencingThermocycles);
        saveCocktailsToDisk(Cocktail.Type.pcr, PCRCocktails);
        saveCocktailsToDisk(Cocktail.Type.cyclesequencing, cyclesequencingCocktails);
        for (Reaction.Type type : Reaction.Type.values()) {
            saveDisplayedFieldsToDisk(type, reactionToDisplayableFields.get(type));
        }
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
            for (Reaction.Type type : Reaction.Type.values()) {
                reactionToDisplayableFields.put(type, getDisplayFieldsTemplatesFromDisk(type));
            }
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
                case GelQuantification: return Arrays.asList(new DisplayFieldsTemplate("Default", Reaction.Type.GelQuantification, GelQuantificationReaction.getDefaultDisplayedFields(), new Reaction.BackgroundColorer(null, Collections.<String, Color>emptyMap())));
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
        return reactionToDisplayableFields.get(type);
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
        getActiveLIMSConnection().deleteThermoCycles(type, cycles);
        buildCaches();
    }

    public void insertThermocycles(List<Thermocycle> cycles, Thermocycle.Type type) throws DatabaseServiceException {
        getActiveLIMSConnection().addThermoCycles(type, cycles);
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
        return getActiveLIMSConnection().getTissueIdsForExtractionIds(tableName, extractionIds);
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
                if (reaction.getType().linksToWorkflows() && reaction.getLocus().equals("None")) {
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

        List<String> locationOfReactionsWithErrors = new ArrayList<String>();
        for (Reaction reaction : plate.getReactions()) {
            if (reaction.hasError()) {
                locationOfReactionsWithErrors.add(reaction.getLocationString());
            }
        }
        if (!locationOfReactionsWithErrors.isEmpty()) {
            throw new BadDataException("The reactions in the following well locations are in an error state: " + StringUtilities.join(", ", locationOfReactionsWithErrors) + ".");
        }

        getActiveLIMSConnection().savePlates(Collections.singletonList(plate), progress);
    }

    public void deletePlate(ProgressListener progress, Plate plate) throws DatabaseServiceException {

        Set<Integer> plateIds = getActiveLIMSConnection().deletePlates(Collections.singletonList(plate), progress);

        if(plate.getReactionType() == Reaction.Type.Extraction) {
            List<Plate> emptyPlates = getActiveLIMSConnection().getEmptyPlates(plateIds);
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
        List<Integer> plateIds = getActiveLIMSConnection().getMatchingDocumentsFromLims(
                Query.Factory.createFieldQuery(LIMSConnection.PLATE_NAME_FIELD, Condition.EQUAL, new Object[]{plateName},
                        BiocodeService.getSearchDownloadOptions(false, false, true, false, false)), null, ProgressListener.EMPTY
        ).getPlateIds();
        List<Plate> plates = getActiveLIMSConnection().getPlates(plateIds, ProgressListener.EMPTY);
        if(plates.size() == 0) {
            throw new DocumentOperationException("The plate '"+plateName+"' does not exist in the database.");
        }
        assert plateIds.size() == 1;
        if(plates.get(0).getReactionType() != Reaction.Type.CycleSequencing) {
            throw new DocumentOperationException("The plate '"+plateName+"' is not a cycle sequencing plate.");
        }
        Plate plate = plates.get(0);

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
        return getActiveLIMSConnection().getWorkflowIds(idsToCheck, loci, reactionType);
    }

    public Map<String, Workflow> getWorkflows(Collection<String> workflowNames) throws DatabaseServiceException {
        List<Workflow> list = getActiveLIMSConnection().getWorkflowsByName(workflowNames);
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
        if (workflowNames.isEmpty()) {
            return Collections.emptyList();
        }

        List<WorkflowDocument> workflows = new ArrayList<WorkflowDocument>();
        Query workflowQuery;
        Map<String, Object> options = BiocodeService.getSearchDownloadOptions(false, true, false, false, false);

        if (workflowNames.size() == 1) {
            workflowQuery = Query.Factory.createFieldQuery(LIMSConnection.WORKFLOW_NAME_FIELD, Condition.EQUAL, new Object[]{workflowNames.get(0)}, options);
        }
        else {
            List<Query> subQueries = new ArrayList<Query>();

            for (String id : workflowNames) {
                subQueries.add(Query.Factory.createFieldQuery(LIMSConnection.WORKFLOW_NAME_FIELD, Condition.EQUAL, id));
            }

            workflowQuery = Query.Factory.createOrQuery(subQueries.toArray(new Query[subQueries.size()]), options);
        }

        List<AnnotatedPluginDocument> results = BiocodeService.getInstance().retrieve(workflowQuery, ProgressListener.EMPTY);

        for (AnnotatedPluginDocument result : results) {
            if (WorkflowDocument.class.isAssignableFrom(result.getDocumentClass())) {
                PluginDocument doc = result.getDocumentOrNull();

                if (doc instanceof WorkflowDocument) {
                    workflows.add((WorkflowDocument) doc);
                }
            }
        }

        return workflows;
    }

    public List<String> getPlatesUsingThermocycle(Thermocycle thermocycle) throws DatabaseServiceException {
        return getActiveLIMSConnection().getPlatesUsingThermocycle(thermocycle.getId());
    }

    public Collection<String> getPlatesUsingCocktail(Cocktail cocktail) throws DatabaseServiceException {
        return getActiveLIMSConnection().getPlatesUsingCocktail(cocktail.getReactionType(), cocktail.getId());
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

    public Plate getPlateForName(String plateName) throws DatabaseServiceException {
        Query q = Query.Factory.createFieldQuery(LIMSConnection.PLATE_NAME_FIELD, Condition.EQUAL, new Object[]{plateName},
                                BiocodeService.getSearchDownloadOptions(false, false, true, false, false));
        try {
            List<Integer> plateIds = getActiveLIMSConnection().getMatchingDocumentsFromLims(q, null, ProgressListener.EMPTY).getPlateIds();
            if(plateIds.isEmpty()) {
                return null;
            }
            List<Plate> plates = getActiveLIMSConnection().getPlates(plateIds, new LimsSearchCallback.LimsSearchRetrieveListCallback<Plate>(ProgressListener.EMPTY));
            assert(plates.size() <= 1);
            if(plates.isEmpty()) {
                return null;
            } else {
                return plates.get(0);
            }
        } catch (DatabaseServiceException e) {
            e.printStackTrace();
            throw new DatabaseServiceException(e, "Failed to download plate " + plateName + ": " + e.getMessage(), false);
        }
    }
}
