package com.biomatters.plugins.biocode.labbench.lims;

import com.biomatters.geneious.publicapi.components.Dialogs;
import com.biomatters.geneious.publicapi.databaseservice.*;
import com.biomatters.geneious.publicapi.documents.*;
import com.biomatters.geneious.publicapi.documents.sequence.DefaultNucleotideGraph;
import com.biomatters.geneious.publicapi.documents.sequence.NucleotideGraph;
import com.biomatters.geneious.publicapi.implementations.sequence.DefaultNucleotideGraphSequence;
import com.biomatters.geneious.publicapi.implementations.sequence.DefaultNucleotideSequence;
import com.biomatters.geneious.publicapi.plugin.DocumentOperationException;
import com.biomatters.geneious.publicapi.plugin.Options;
import com.biomatters.geneious.publicapi.utilities.*;
import com.biomatters.plugins.biocode.BiocodeUtilities;
import com.biomatters.plugins.biocode.assembler.annotate.AnnotateUtilities;
import com.biomatters.plugins.biocode.assembler.annotate.FimsData;
import com.biomatters.plugins.biocode.assembler.annotate.FimsDataGetter;
import com.biomatters.plugins.biocode.labbench.*;
import com.biomatters.plugins.biocode.labbench.fims.SqlUtilities;
import com.biomatters.plugins.biocode.labbench.plates.GelImage;
import com.biomatters.plugins.biocode.labbench.plates.Plate;
import com.biomatters.plugins.biocode.labbench.reaction.CycleSequencingReaction;
import com.biomatters.plugins.biocode.labbench.reaction.ExtractionReaction;
import com.biomatters.plugins.biocode.labbench.reaction.FailureReason;
import com.biomatters.plugins.biocode.labbench.reaction.Reaction;
import jebl.util.Cancelable;

import java.sql.*;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.Date;
import java.util.List;

/**
 * @author steve
 * @version $Id: 27/05/2009 6:28:38 AM steve $
 */
@SuppressWarnings({"ConstantConditions"})
public abstract class LIMSConnection {
    @SuppressWarnings({"ConstantConditions"})
    public static final int EXPECTED_SERVER_MAJOR_VERSION = 9;
    public static final String EXPECTED_SERVER_FULL_VERSION = "9.1";

    /**
     * Was used for a beta version. But since we didn't actually break backwards compatibility we reverted back to the old
     * version and used a point release system with the value stored in the properties table.
     */
    public static final int ROLLBACK_VERSION = 10;

    public static final String VERSION_WITHOUT_PROPS = "9";

    /**
     * Indicates the full version of the database schema in use.  Stored in the properties table.  The databaseversion
     * table contains the major version.  Major versions are incompatible with each other.
     */
    public static final String VERSION_PROPERTY = "fullDatabaseVersion";

    Driver driver;
    Connection connection;
    Connection connection2;
    private PasswordOptions limsOptions;

    public static final DocumentField DATE_FIELD = new DocumentField("Last Modified (LIMS)", "", "date", Date.class, true, false);

    private static final Map<String, List<DocumentField>> TABLE_TO_FIELDS = new HashMap<String, List<DocumentField>>();

    public static final DocumentField WORKFLOW_NAME_FIELD = new DocumentField("Workflow Name", "", "workflow.name", String.class, true, false);
    public static final DocumentField WORKFLOW_DATE_FIELD = new DocumentField("Last Modified (LIMS workflow)", "", "workflow.date", Date.class, false, false);
    public static final DocumentField WORKFLOW_LOCUS_FIELD = new DocumentField("Locus", "The locus of the workflow", "workflow.locus", String.class, true, false);

    static {
        TABLE_TO_FIELDS.put("workflow", Arrays.asList(WORKFLOW_NAME_FIELD, WORKFLOW_DATE_FIELD, WORKFLOW_LOCUS_FIELD));
    }

    public static final DocumentField PLATE_TYPE_FIELD = DocumentField.createEnumeratedField(new String[]{"Extraction", "PCR", "CycleSequencing"}, "Plate type", "", "plate.type", true, false);
    public static final DocumentField PLATE_NAME_FIELD = new DocumentField("Plate Name (LIMS)", "", "plate.name", String.class, true, false);
    public static final DocumentField PLATE_DATE_FIELD = new DocumentField("Last Modified (LIMS plate)", "", "plate.date", Date.class, false, false);

    static {
        TABLE_TO_FIELDS.put("plate", Arrays.asList(PLATE_TYPE_FIELD, PLATE_NAME_FIELD, PLATE_DATE_FIELD));
    }

    public static final DocumentField EXTRACTION_ID_FIELD = new DocumentField("Extraction ID", "The Extraction ID", "extraction.extractionId", String.class, true, false);
    public static final DocumentField EXTRACTION_BARCODE_FIELD = new DocumentField("Extraction Barcode", "The Extraction Barcode", "extraction.extractionBarcode", String.class, true, false);

    static {
        TABLE_TO_FIELDS.put("extraction", Arrays.asList(EXTRACTION_ID_FIELD, EXTRACTION_BARCODE_FIELD, DATE_FIELD));
    }

    public static final DocumentField SEQUENCE_PROGRESS = DocumentField.createEnumeratedField(new String[]{"passed", "failed"}, "Sequence Progress", "Whether the sequence passed or failed sequencing and assembly", "assembly.progress", true, false);
    public static final DocumentField SEQUENCE_SUBMISSION_PROGRESS = DocumentField.createEnumeratedField(new String[]{"Yes", "No"}, "Sequence Submitted", "Indicates whether this sequence has been submitte to a sequence database (e.g. Genbank)", "assembly.submitted", false, false);
    public static final DocumentField EDIT_RECORD = DocumentField.createStringField("Edit Record", "A record of edits made to this sequence", "assembly.editRecord", false, false);
    public static final DocumentField ASSEMBLY_TECHNICIAN = DocumentField.createStringField("Assembly Technician", "", "assembly.technician", false, false);

    static {
        TABLE_TO_FIELDS.put("assembly", Arrays.asList(SEQUENCE_PROGRESS, SEQUENCE_SUBMISSION_PROGRESS, EDIT_RECORD, ASSEMBLY_TECHNICIAN, DATE_FIELD));
    }

    public static final DocumentField SEQUENCE_ID = DocumentField.createIntegerField("LIMS Sequence ID", "The Unique ID of this sequence in LIMS", "LimsSequenceId", false, false);

    private static final String LOCUS = "locus";

    String serverUrn;

    private List<FailureReason> failureReasons = Collections.emptyList();

    public List<FailureReason> getPossibleFailureReasons() {
        return failureReasons;
    }

    public static enum AvailableLimsTypes {
        local(LocalLIMSConnection.class, "Built-in MySQL Database", "Create and connect to LIMS databases on your local computer (stored with your Geneious data)"),
        remote(MysqlLIMSConnection.class, "Remote MySQL Database", "Connect to a LIMS database on a remote MySQL server");
        private final Class limsClass;
        private final String label;
        private final String description;

        AvailableLimsTypes(Class limsClass, String label, String description) {
            this.limsClass = limsClass;
            this.label = label;
            this.description = description;
        }

        public Class getLimsClass() {
            return limsClass;
        }

        public String getLabel() {
            return label;
        }

        public String getDescription() {
            return description;
        }
    }

    public LIMSConnection() {
    }

    public static PasswordOptions createConnectionOptions() {
        return new LimsConnectionOptions(LIMSConnection.class);
    }

    public static LIMSConnection getLIMSConnection(PasswordOptions connectionOptions) throws ConnectionException {
        LimsConnectionOptions limsOptions = (LimsConnectionOptions) connectionOptions;

        try {
            return (LIMSConnection) limsOptions.getSelectedLIMSType().getLimsClass().newInstance();
        } catch (InstantiationException e) {
            throw new ConnectionException(e);
        } catch (IllegalAccessException e) {
            throw new ConnectionException(e);
        }
    }

    public abstract boolean requiresMySql();

    public abstract PasswordOptions getConnectionOptions();

    public abstract boolean isLocal();

    public String getUrn() {
        return serverUrn;
    }

    public static boolean isLocal(PasswordOptions connectionOptions) {
        return !connectionOptions.getValueAsString("connectionType").equals("remote");
    }

    public abstract String getUsername();

    public abstract String getSchema();

    public void connect(PasswordOptions options) throws ConnectionException {
        driver = getDriver();
        LimsConnectionOptions allLimsOptions = (LimsConnectionOptions) options;
        PasswordOptions selectedLimsOptions = allLimsOptions.getSelectedLIMSOptions();
        this.limsOptions = allLimsOptions;
        connectToDb(selectedLimsOptions);
        String errorMessage = verifyDatabaseVersionAndUpgradeIfNecessary();
        if (errorMessage != null) {
            throw new ConnectionException(errorMessage);
        }
    }

    /**
     * @return an error message or null if everything is OK
     * @throws ConnectionException
     */
    private String verifyDatabaseVersionAndUpgradeIfNecessary() throws ConnectionException {
        try {
            ResultSet resultSet = connection.createStatement().executeQuery("SELECT * FROM databaseversion LIMIT 1");
            if (!resultSet.next()) {
                throw new ConnectionException("Your LIMS database appears to be corrupt.  Please contact your systems administrator for assistance.");
            } else {
                int version = resultSet.getInt("version");
                String fullVersionString = getFullVersionStringFromDatabase();

                if (version == ROLLBACK_VERSION && fullVersionString.equals(VERSION_WITHOUT_PROPS)) {
                    // As part of a beta release we incremented the major version early to 10.  Revert to 9 and record full version.
                    PreparedStatement updateMajorVersion = null;
                    PreparedStatement insertVersion = null;

                    try {
                        updateMajorVersion = connection.prepareStatement("UPDATE databaseversion SET version = " + EXPECTED_SERVER_MAJOR_VERSION);
                        updateMajorVersion.executeUpdate();
                        version = EXPECTED_SERVER_MAJOR_VERSION;

                        insertVersion = connection.prepareStatement("INSERT INTO properties(name, value) VALUES (?,?)");
                        insertVersion.setObject(1, VERSION_PROPERTY);
                        insertVersion.setObject(2, EXPECTED_SERVER_FULL_VERSION);
                        insertVersion.executeUpdate();
                        fullVersionString = EXPECTED_SERVER_FULL_VERSION;
                    } finally {
                        SqlUtilities.cleanUpStatements(updateMajorVersion, insertVersion);
                    }
                }

                boolean databaseIsOlder = BiocodeUtilities.compareVersions(fullVersionString, EXPECTED_SERVER_FULL_VERSION) < 0;
                if (version > EXPECTED_SERVER_MAJOR_VERSION) {
                    throw new ConnectionException("This database was written for a newer version of the LIMS plugin, and cannot be accessed");
                } else if (version < EXPECTED_SERVER_MAJOR_VERSION || databaseIsOlder) {
                    if (!canUpgradeDatabase()) {
                        throw new ConnectionException("The server you are connecting to is running " + (databaseIsOlder ? "an older" : "a newer") +
                                " version of the LIMS database than this plugin was designed for.\n\n" +
                                "<strong>Plugin Version</strong>: " + EXPECTED_SERVER_FULL_VERSION +
                                "\n<strong>Server Version</strong>: " + fullVersionString +
                                "\n\nPlease contact your systems administrator for assistance.");
                    }
                    if (Dialogs.showYesNoDialog("The LIMS database you are connecting to is written for an older version of this plugin.  Would you like to upgrade it?", "Old database", null, Dialogs.DialogIcon.QUESTION)) {
                        upgradeDatabase(fullVersionString);
                    } else {
                        throw new ConnectionException("You need to upgrade your database, or choose another one to continue");
                    }
                }
                return null;
            }
        } catch (SQLException ex) {
            throw new ConnectionException(ex.getMessage(), ex);
        }
    }

    String getFullVersionStringFromDatabase() throws SQLException, ConnectionException {
        ResultSet tableSet = connection.getMetaData().getTables(null, null, "%", null);
        boolean hasPropertiesTable = false;
        while(tableSet.next()) {
            String tableName = tableSet.getString("TABLE_NAME");
            if(tableName.equalsIgnoreCase("properties")) {
                hasPropertiesTable = true;
            }
        }
        tableSet.close();

        if (hasPropertiesTable) {
            PreparedStatement getFullVersion = null;
            try {
                getFullVersion = connection.prepareStatement("SELECT value FROM properties WHERE name = ?");
                getFullVersion.setObject(1, VERSION_PROPERTY);
                ResultSet versionSet = getFullVersion.executeQuery();
                if (versionSet.next()) {
                    return versionSet.getString("value");
                }
            } finally {
                SqlUtilities.cleanUpStatements(getFullVersion);
            }
        }
        return VERSION_WITHOUT_PROPS;
    }

    /**
     * @return true if this implementation supports automatically upgrading the database
     */
    protected abstract boolean canUpgradeDatabase();

    /**
     * Upgrade the database schema so it is the current version.  Must be overridden if implementations return true from
     * {@link #canUpgradeDatabase()}
     *
     * @throws SQLException if a database problem occurs during the upgrade
     * @param currentVersion
     */
    protected void upgradeDatabase(String currentVersion) throws ConnectionException {
        if (canUpgradeDatabase()) {
            throw new UnsupportedOperationException("LIMSConnection implementations should override upgradeDatabase() when returning true from canUpgradeDatabase()");
        } else {
            throw new UnsupportedOperationException(getClass().getSimpleName() + " does not implement upgradeDatabase()");
        }
    }

    public void doAnyExtraInitialziation() throws TransactionException {
        try {
            PreparedStatement getFailureReasons = null;
            try {
                getFailureReasons = connection.prepareStatement("SELECT * FROM failure_reason");
                ResultSet resultSet = getFailureReasons.executeQuery();
                failureReasons = new ArrayList<FailureReason>(FailureReason.getPossibleListFromResultSet(resultSet));
            } finally {
                SqlUtilities.cleanUpStatements(getFailureReasons);
            }

            System.out.println("Populating sequencing_result table from results marked in previous versions...");
            PreparedStatement getReactionsAndResults = null;
            PreparedStatement updateReaction = null;
            try {
                getReactionsAndResults = connection.prepareStatement(
                        "SELECT assembly.id AS assembly, cyclesequencing.id AS reaction FROM workflow " +
                                "INNER JOIN cyclesequencing ON workflow.id = cyclesequencing.workflow " +
                                "INNER JOIN assembly ON assembly.workflow = cyclesequencing.workflow AND " +
                                "assembly.id NOT IN (SELECT assembly FROM sequencing_result);"
                );
                updateReaction = connection.prepareStatement("INSERT INTO sequencing_result(assembly, reaction) VALUES(?,?)");
                ResultSet resultSet = getReactionsAndResults.executeQuery();
                int count = 0;
                while (resultSet.next()) {
                    count++;
                    updateReaction.setObject(1, resultSet.getInt("assembly"));
                    updateReaction.setObject(2, resultSet.getInt("reaction"));
                    updateReaction.addBatch();
                }

                if (count > 0) {
                    long start = System.currentTimeMillis();
                    int[] updateResults = updateReaction.executeBatch();
                    int updated = 0;
                    for (int result : updateResults) {
                        if (result >= 0) {
                            updated += result;
                        }
                    }
                    System.out.println("Took " + (System.currentTimeMillis() - start) + "ms to populate " + updated + " reactions with assemblies.");
                }
            } finally {
                SqlUtilities.cleanUpStatements(getReactionsAndResults, updateReaction);
            }
        } catch (SQLException e) {
            throw new TransactionException("Failed to initialize database: " + e.getMessage(), e);
        }
    }

    public abstract Driver getDriver() throws ConnectionException;

    abstract void connectToDb(Options connectionOptions) throws ConnectionException;


    public void disconnect() {
        //we used to explicitly close the SQL connection, but this was causing crashes if the user logged out while a query was in progress.
        //now we remove all references to it and let the garbage collector close it when the queries have finished.
        connection = null;
        connection2 = null;
        limsOptions = null;
        serverUrn = null;
    }

    public void reconnect() throws ConnectionException {
        if (this.limsOptions == null) {
            return;
        }
        PasswordOptions limsOptions = this.limsOptions;
        disconnect();
        connect(limsOptions);
    }

    public Set<Integer> deleteRecords(String tableName, String term, Iterable ids) throws SQLException {
        if (!BiocodeService.getInstance().deleteAllowed(tableName)) {
            throw new SQLException("It appears that you do not have permission to delete from " + tableName + ".  Please contact your System Administrator for assistance");
        }

        List<String> terms = new ArrayList<String>();
        int count = 0;
        for (Object id : ids) {
            count++;
            terms.add(term + "=" + id);
        }

        if (count == 0) {
            return Collections.emptySet();
        }

        String termString = StringUtilities.join(" OR ", terms);

        Set<Integer> plateIds = new HashSet<Integer>();
        if (tableName.equals("extraction") || tableName.equals("pcr") || tableName.equals("cyclesequencing")) {
            PreparedStatement getPlatesStatement = createStatement("SELECT plate FROM " + tableName + " WHERE " + termString);
            ResultSet resultSet = getPlatesStatement.executeQuery();
            while (resultSet.next()) {
                plateIds.add(resultSet.getInt("plate"));
            }
            getPlatesStatement.close();
        }

        PreparedStatement deleteStatement = createStatement("DELETE FROM " + tableName + " WHERE " + termString);
        deleteStatement.executeUpdate();
        deleteStatement.close();

        return plateIds;
    }

    public ResultSet executeQuery(String sql) throws TransactionException {
        try {
            PreparedStatement statement = createStatement(sql);
            return statement.executeQuery();
        } catch (SQLException ex) {
            throw new TransactionException("Could not execute LIMS query", ex);
        }
    }

    public void executeUpdate(String sql) throws TransactionException {
        Connection connection = null;
        try {
            connection = getConnection();
            if (connection == null) {
                return;
            }
            connection.setAutoCommit(false);
            for (String s : sql.split("\n")) {
                PreparedStatement statement = connection.prepareStatement(s);
                statement.execute();
                statement.close();
            }
            connection.commit();
        } catch (SQLException ex) {
            throw new TransactionException("Could not execute LIMS update query", ex);
        } finally {
            try {
                if (connection != null) {
                    connection.setAutoCommit(true);
                }
            } catch (SQLException ignore) {
            }
        }
    }

    private Connection getConnection() throws SQLException {
        if (connection == null) {
            throw new SQLException(BiocodeUtilities.NOT_CONNECTED_ERROR_MESSAGE);
        }
        return connection;
    }

    public DatabaseMetaData getMetaData() throws SQLException {
        Connection connection = getConnection();
        return connection.getMetaData();
    }

    public Statement createStatement() throws SQLException {
        Connection connection = getConnection();
        Statement statement = connection.createStatement();
        statement.setQueryTimeout(BiocodeService.STATEMENT_QUERY_TIMEOUT);
        return statement;
    }

    public PreparedStatement createStatement(String sql) throws SQLException {
        Connection connection = getConnection();
        PreparedStatement statement = connection.prepareStatement(sql);
        statement.setQueryTimeout(BiocodeService.STATEMENT_QUERY_TIMEOUT);
        return statement;
    }

    public Statement createStatement(int resultSetType, int resultSetConcurrency) throws SQLException {
        Connection connection = getConnection();
        Statement statement = connection.createStatement(resultSetType, resultSetConcurrency);
        statement.setQueryTimeout(BiocodeService.STATEMENT_QUERY_TIMEOUT);
        return statement;
    }

    public int getLastInsertId() throws SQLException {
        int reactionId;
        ResultSet reactionIdResultSet = BiocodeService.getInstance().getActiveLIMSConnection().isLocal() ? createStatement().executeQuery("CALL IDENTITY();") : createStatement().executeQuery("SELECT last_insert_id()");
        reactionIdResultSet.next();
        reactionId = reactionIdResultSet.getInt(1);
        return reactionId;
    }

    private int transactionLevel = 0;

    /**
     * Note that this is not thread safe - it is assumed that only one atomic transaction will access the LIMSConnection at any one time
     */
    public void beginTransaction() throws SQLException {
        if (transactionLevel == 0) {
            Connection connection = getConnection();
            connection.setAutoCommit(false);
        }
        transactionLevel++;
    }

    /**
     * Note that this is not thread safe - it is assumed that only one atomic transaction will access the LIMSConnection at any one time
     */
    public void rollback() {
        try {
            Connection connection = getConnection();
            connection.rollback();
            connection.setAutoCommit(true);
        } catch (SQLException ex) {/*if we can't rollback, let's ignore*/}
        transactionLevel = 0;
    }

    /**
     * Note that this is not thread safe - it is assumed that only one atomic transaction will access the LIMSConnection at any one time
     */
    public void endTransaction() throws SQLException {
        Connection connection = getConnection();
        if (transactionLevel == 0) {
            return;  //we've rolled back our changes by calling rollback() so no commits are necessary
        }
        transactionLevel--;
        if (transactionLevel == 0) {
            connection.commit();
            connection.setAutoCommit(true);
        }
    }


    public static List<DocumentField> getSearchAttributes() {
        return Arrays.asList(
                PLATE_NAME_FIELD,
                WORKFLOW_NAME_FIELD,
                PLATE_TYPE_FIELD,
                DATE_FIELD,
                PLATE_DATE_FIELD,
                WORKFLOW_DATE_FIELD,
                WORKFLOW_LOCUS_FIELD,
                EXTRACTION_ID_FIELD,
                EXTRACTION_BARCODE_FIELD,
                SEQUENCE_PROGRESS,
                SEQUENCE_SUBMISSION_PROGRESS,
                ASSEMBLY_TECHNICIAN
        );
    }

    public static Condition[] getFieldConditions(Class fieldClass) {
        if (Integer.class.equals(fieldClass) || Double.class.equals(fieldClass)) {
            return new Condition[]{
                    Condition.EQUAL,
                    Condition.NOT_EQUAL,
                    Condition.GREATER_THAN,
                    Condition.GREATER_THAN_OR_EQUAL_TO,
                    Condition.LESS_THAN,
                    Condition.LESS_THAN_OR_EQUAL_TO
            };
        } else if (String.class.equals(fieldClass)) {
            return new Condition[]{
                    Condition.CONTAINS,
                    Condition.EQUAL,
                    Condition.NOT_EQUAL,
                    Condition.NOT_CONTAINS,
                    Condition.STRING_LENGTH_LESS_THAN,
                    Condition.STRING_LENGTH_GREATER_THAN,
                    Condition.BEGINS_WITH,
                    Condition.ENDS_WITH
            };
        } else if (Date.class.equals(fieldClass)) {
            return new Condition[]{
                    Condition.EQUAL,
                    Condition.NOT_EQUAL,
                    Condition.GREATER_THAN,
                    Condition.GREATER_THAN_OR_EQUAL_TO,
                    Condition.LESS_THAN,
                    Condition.LESS_THAN_OR_EQUAL_TO
            };
        } else {
            return new Condition[]{
                    Condition.EQUAL,
                    Condition.NOT_EQUAL
            };
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
            Collection<WorkflowDocument> workflows, List<FimsSample> samples,
            List<Integer> sequenceIds, RetrieveCallback callback, boolean includeFailed) throws SQLException {
        if (sequenceIds.isEmpty()) {
            return Collections.emptyList();
        }
        List<Object> sqlValues = new ArrayList<Object>(sequenceIds);

        StringBuilder sql = new StringBuilder("SELECT workflow.locus, assembly.*, extraction.sampleId, extraction.extractionId, extraction.extractionBarcode ");
        sql.append("FROM workflow INNER JOIN assembly ON assembly.id IN ");
        SqlUtilities.appendSetOfQuestionMarks(sql, sequenceIds.size());
        if (!includeFailed) {
            sql.append(" AND assembly.progress = ?");
            sqlValues.add("passed");
        }
        sql.append(" AND workflow.id = assembly.workflow INNER JOIN extraction ON workflow.extractionId = extraction.id");

        return getMatchingAssemblyDocumentsForSQL(workflows, samples,
                callback, new URN[0], callback, sql.toString(), sqlValues);
    }

    private List<AnnotatedPluginDocument> getMatchingAssemblyDocumentsForSQL(final Collection<WorkflowDocument> workflows, final List<FimsSample> fimsSamples, RetrieveCallback callback, URN[] urnsToNotRetrieve, Cancelable cancelable, String sql, List<Object> sqlValues) throws SQLException {
        if (!BiocodeService.getInstance().isLoggedIn()) {
            return Collections.emptyList();
        }
        PreparedStatement statement = createStatement(sql);
        fillStatement(sqlValues, statement);
        SqlUtilities.printSql(sql, sqlValues);
        BiocodeUtilities.CancelListeningThread listeningThread = null;
        if (cancelable != null) {
            //todo: listeningThread = new BiocodeUtilities.CancelListeningThread(cancelable, statement);
        }
        if (!isLocal()) {
            statement.setFetchSize(Integer.MIN_VALUE);
        }
        try {
            final ResultSet resultSet = statement.executeQuery();
            List<AnnotatedPluginDocument> resultDocuments = new ArrayList<AnnotatedPluginDocument>();
            final List<String> missingTissueIds = new ArrayList<String>();
            ArrayList<AnnotatedPluginDocument> documentsWithoutFimsData = new ArrayList<AnnotatedPluginDocument>();
            while (resultSet.next()) {
                if (SystemUtilities.isAvailableMemoryLessThan(50)) {
                    statement.cancel();
                    throw new SQLException("Search cancelled due to lack of free memory");
                }
                if (callback != null && callback.isCanceled()) {
                    return Collections.emptyList();
                }
                AnnotatedPluginDocument doc = createAssemblyDocument(resultSet, urnsToNotRetrieve);
                if (doc == null) {
                    continue;
                }
                FimsDataGetter getter = new FimsDataGetter() {
                    public FimsData getFimsData(AnnotatedPluginDocument document) throws DocumentOperationException {
                        try {
                            if (workflows != null) {
                                for (WorkflowDocument workflow : workflows) {
                                    if (workflow.getId() == resultSet.getInt("workflow")) {
                                        return new FimsData(workflow, null, null);
                                    }
                                }
                            }
                            String tissueId = resultSet.getString("sampleId");
                            if (fimsSamples != null) {
                                for (FimsSample sample : fimsSamples) {
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
                        } catch (SQLException ex) {
                            throw new DocumentOperationException("Could not get workflow id from assembly table: " + ex.getMessage());
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

            if (listeningThread != null) {
                listeningThread.finish();
            }
            return resultDocuments;
        } catch (DocumentOperationException e) {
            e.printStackTrace();
            if (e.getCause() != null && e.getCause() instanceof SQLException) {
                throw (SQLException) e.getCause();
            }
            throw new SQLException(e.getMessage());
        } catch (ConnectionException e) {
            e.printStackTrace();
            if (e.getCause() != null && e.getCause() instanceof SQLException) {
                throw (SQLException) e.getCause();
            }
            throw new SQLException(e.getMessage());
        } finally {
            statement.close();
        }
    }

    private AnnotatedPluginDocument createAssemblyDocument(ResultSet resultSet, URN[] urnsToNotRetrieve) throws SQLException {
        String qualities = resultSet.getString("assembly.confidence_scores");
        DefaultNucleotideSequence sequence;
        URN urn = new URN("Biocode", getUrn(), "" + resultSet.getInt("id"));
        if (urnsToNotRetrieve != null) {
            for (URN urnNotToRetrieve : urnsToNotRetrieve) {
                if (urn.equals(urnNotToRetrieve)) {
                    return null;
                }
            }
        }
        String name = resultSet.getString("assembly.extraction_id") + " " + resultSet.getString("workflow.locus");
        if (qualities == null || resultSet.getString("progress") == null || resultSet.getString("progress").toLowerCase().contains("failed")) {
            String consensus = resultSet.getString("consensus");
            String description = "Assembly consensus sequence for " + name;
            java.sql.Date created = resultSet.getDate("date");
            if (consensus == null || created == null) {
                consensus = "";
            } else if (resultSet.getString("assembly.progress") == null || resultSet.getString("progress").toLowerCase().contains("failed")) {
                consensus = "";
                description = "Sequencing failed for this well";
            }
            consensus = consensus.replace("-", "");
            sequence = new DefaultNucleotideSequence(name, description, consensus, new Date(created.getTime()), urn);
        } else {
            String sequenceString = resultSet.getString("assembly.consensus");
            sequenceString = sequenceString.replace("-", "");
            NucleotideGraph graph = DefaultNucleotideGraph.createNucleotideGraph(null, null, qualitiesFromString(qualities), sequenceString.length(), 0);
            Date dateMarked = new Date(resultSet.getDate("date").getTime());
            sequence = new DefaultNucleotideGraphSequence(name, "Assembly consensus sequence for " + name, sequenceString, dateMarked, graph, urn);
            sequence.setFieldValue(PluginDocument.MODIFIED_DATE_FIELD, dateMarked);
        }
        AnnotatedPluginDocument doc = DocumentUtilities.createAnnotatedPluginDocument(sequence);
        //todo: add data as fields and notes...
        String notes = resultSet.getString("assembly.notes");
        if (notes != null) {
            doc.setFieldValue(AnnotateUtilities.NOTES_FIELD, notes);
        }
        doc.setFieldValue(WORKFLOW_LOCUS_FIELD, resultSet.getString("workflow.locus"));
        doc.setFieldValue(AnnotateUtilities.PROGRESS_FIELD, resultSet.getString("assembly.progress"));
        doc.setFieldValue(DocumentField.CONTIG_MEAN_COVERAGE, resultSet.getDouble("assembly.coverage"));
        doc.setFieldValue(DocumentField.DISAGREEMENTS, resultSet.getInt("assembly.disagreements"));
        doc.setFieldValue(AnnotateUtilities.EDITS_FIELD, resultSet.getInt("assembly.edits"));
        doc.setFieldValue(AnnotateUtilities.TRIM_PARAMS_FWD_FIELD, resultSet.getString("assembly.trim_params_fwd"));
        doc.setFieldValue(AnnotateUtilities.TRIM_PARAMS_REV_FIELD, resultSet.getString("assembly.trim_params_rev"));
        doc.setHiddenFieldValue(AnnotateUtilities.LIMS_ID, resultSet.getInt("assembly.id"));
        //todo: fields that require a schema change
        //noinspection ConstantConditions
        doc.setFieldValue(AnnotateUtilities.TECHNICIAN_FIELD, resultSet.getString("assembly.technician"));
        doc.setFieldValue(DocumentField.CREATED_FIELD, new Date(resultSet.getDate("assembly.date").getTime()));
        String bin = resultSet.getString("assembly.bin");
        doc.setFieldValue(DocumentField.BIN, bin);
        doc.setFieldValue(AnnotateUtilities.AMBIGUITIES_FIELD, resultSet.getInt("assembly.ambiguities"));
        doc.setFieldValue(AnnotateUtilities.ASSEMBLY_PARAMS_FIELD, resultSet.getString("assembly.params"));
        doc.setFieldValue(SEQUENCE_ID, resultSet.getInt("id"));
        doc.setFieldValue(LIMSConnection.SEQUENCE_SUBMISSION_PROGRESS, resultSet.getBoolean("assembly.submitted") ? "Yes" : "No");
        doc.setFieldValue(LIMSConnection.EDIT_RECORD, resultSet.getString("assembly.editrecord"));
        doc.setFieldValue(LIMSConnection.EXTRACTION_ID_FIELD, resultSet.getString("extraction.extractionId"));
        doc.setFieldValue(LIMSConnection.EXTRACTION_BARCODE_FIELD, resultSet.getString("extraction.extractionBarcode"));
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

    private void fillStatement(List<Object> sqlValues, PreparedStatement statement) throws SQLException {
        for (int i = 0; i < sqlValues.size(); i++) {
            Object o = sqlValues.get(i);
            if (o == null) {
                statement.setNull(i + 1, Types.JAVA_OBJECT);
            } else if (Integer.class.isAssignableFrom(o.getClass())) {
                statement.setInt(i + 1, (Integer) o);
            } else if (Double.class.isAssignableFrom(o.getClass())) {
                statement.setDouble(i + 1, (Double) o);
            } else if (String.class.isAssignableFrom(o.getClass())) {
                statement.setString(i + 1, o.toString().toLowerCase());
            } else if (Date.class.isAssignableFrom(o.getClass())) {
                statement.setDate(i + 1, new java.sql.Date(((Date) o).getTime()));
            } else if (Boolean.class.isAssignableFrom(o.getClass())) {
                statement.setBoolean(i + 1, (Boolean) o);
            } else {
                throw new SQLException("You have a field parameter with an invalid type: " + o.getClass().getCanonicalName());
            }
        }
    }

    private List<? extends Query> removeFields(List<? extends Query> queries, List<String> codesToIgnore) {
        if (queries == null) {
            return Collections.emptyList();
        }
        List<Query> returnList = new ArrayList<Query>();
        for (Query q : queries) {
            if (q instanceof AdvancedSearchQueryTerm) {
                if (!codesToIgnore.contains(((AdvancedSearchQueryTerm) q).getField().getCode())) {
                    returnList.add(q);
                }
            } else if (q != null) {
                returnList.add(q);
            }
        }

        return returnList;
    }

//    private AdvancedSearchQueryTerm getField(List<? extends Query> queries, String  code) {
//        if(queries == null) {
//            return null;
//        }
//        AdvancedSearchQueryTerm returnValue = null;
//        for(Query q : queries) {
//            if(q instanceof AdvancedSearchQueryTerm) {
//                AdvancedSearchQueryTerm advancedSearchQueryTerm = (AdvancedSearchQueryTerm) q;
//                if(code.equals(advancedSearchQueryTerm.getField().getCode())) {
//                    returnValue = advancedSearchQueryTerm;
//                }
//            }
//        }
//
//        return returnValue;
//    }

    private String queryToSql(List<? extends Query> queries, CompoundSearchQuery.Operator operator, String tableName, List<Object> inserts) {
        if (queries.isEmpty()) {
            return null;
        }
        StringBuilder sql = new StringBuilder();
        String mainJoin;
        switch (operator) {
            case AND:
                mainJoin = "AND";
                break;
            default:
                mainJoin = "OR";
        }
        for (int i = 0; i < queries.size(); i++) {
            if (queries.get(i) instanceof AdvancedSearchQueryTerm) {
                AdvancedSearchQueryTerm q = (AdvancedSearchQueryTerm) queries.get(i);
                QueryTermSurrounder termSurrounder = getQueryTermSurrounder(q);
                String code = q.getField().getCode();
                boolean isBooleanQuery = false;
                if ("date".equals(code)) {
                    code = tableName + ".date"; //hack for last modified date...
                }
                if (String.class.isAssignableFrom(q.getField().getValueType())) {
                    DocumentField field = q.getField();
                    if (field.isEnumeratedField() && field.getEnumerationValues().length == 2 && field.getEnumerationValues()[0].equals("Yes") && field.getEnumerationValues()[1].equals("No")) {
                        isBooleanQuery = true;
                    } else if (isLocal()) {
                        code = "LOWER(" + code + ")";
                    }
                }

                Object[] queryValues = q.getValues();

                //noinspection StringConcatenationInsideStringBufferAppend
                sql.append(" " + code + " " + termSurrounder.getJoin() + " ");

                for (Object queryValue : queryValues) {
                    if (isBooleanQuery) {
                        queryValue = queryValue.equals("Yes") ? 1 : 0;
                    }
                    appendValue(inserts, sql, i < queryValues.length - 1, termSurrounder, queryValue, q.getCondition());
                }
                //}
            }
            if (i < queries.size() - 1) {
                //noinspection StringConcatenationInsideStringBufferAppend
                sql.append(" " + mainJoin);
            }
        }
        return sql.toString();
    }

    private void appendValue(List<Object> inserts, StringBuilder sql, boolean appendAnd, QueryTermSurrounder termSurrounder, Object value, Condition condition) {
        String valueString = valueToString(value);
        //valueString = termSurrounder.getPrepend()+valueString+termSurrounder.getAppend();
        if (Date.class.isAssignableFrom(value.getClass()) && (condition == Condition.LESS_THAN_OR_EQUAL_TO || condition == Condition.GREATER_THAN)) { //hack to make these conditions work...
            value = new Date(((Date) value).getTime() + 86300000);
        }
        if (value instanceof String) {
            inserts.add(termSurrounder.getPrepend() + valueString + termSurrounder.getAppend());
        } else {
            inserts.add(value);
        }
        sql.append("?");
        if (appendAnd) {
            sql.append(" AND ");
        }
    }

    private static String valueToString(Object value) {
        if (value instanceof Date) {
            DateFormat format = new SimpleDateFormat("yyyy-mm-dd kk:mm:ss");
            return format.format((Date) value);
        }
        return value.toString();
    }

    public List<PlateDocument> getMatchingPlateDocuments(Query query, List<WorkflowDocument> workflowDocuments, RetrieveCallback callback) throws SQLException, DatabaseServiceException {
        return getMatchingPlateDocuments(query, workflowDocuments, callback, callback);
    }

    @SuppressWarnings({"MismatchedQueryAndUpdateOfCollection"})
    public List<PlateDocument> getMatchingPlateDocuments(Query query, List<WorkflowDocument> workflowDocuments, RetrieveCallback callback, Cancelable cancelable) throws SQLException, DatabaseServiceException {
        Connection connection = getConnection();
        Connection connection2 = isLocal() ? connection : this.connection2;
        if (connection == null || connection2 == null) {
            throw new SQLException("You are not logged in");
        }

        List<? extends Query> refinedQueries;
        CompoundSearchQuery.Operator operator;
        Set<Integer> plateIds = new HashSet<Integer>();
        long startTime = System.currentTimeMillis();

        if (query instanceof BasicSearchQuery) {
            query = generateAdvancedQueryFromBasicQuery(query);
        }

        List<String> fieldsToRemove = Arrays.asList(WORKFLOW_NAME_FIELD.getCode(), WORKFLOW_DATE_FIELD.getCode(), LOCUS, EXTRACTION_ID_FIELD.getCode(), EXTRACTION_BARCODE_FIELD.getCode(), SEQUENCE_PROGRESS.getCode(), SEQUENCE_SUBMISSION_PROGRESS.getCode(), ASSEMBLY_TECHNICIAN.getCode());
        if (query == null) {
            refinedQueries = Collections.emptyList();
            operator = CompoundSearchQuery.Operator.AND;
        } else if (query instanceof CompoundSearchQuery) {
            refinedQueries = removeFields(((CompoundSearchQuery) query).getChildren(), fieldsToRemove);
            operator = ((CompoundSearchQuery) query).getOperator();
        } else {
            refinedQueries = removeFields(Arrays.asList(query), fieldsToRemove);
            operator = CompoundSearchQuery.Operator.AND;
        }
        if ((workflowDocuments == null || workflowDocuments.size() == 0) && refinedQueries.size() == 0) {
            return Collections.emptyList();
        }
        StringBuilder sql = new StringBuilder("SELECT workflow.*, extraction.*, pcr.*, cyclesequencing.*, " +
                "plate.*, assembly.id, assembly.date, assembly.progress, assembly.date, assembly.notes, " +
                "assembly.failure_reason, assembly.failure_notes FROM plate " +
                "LEFT JOIN cyclesequencing ON cyclesequencing.plate = plate.id " +
                "LEFT JOIN pcr ON pcr.plate = plate.id " +
                "LEFT JOIN workflow ON (workflow.id = pcr.workflow OR workflow.id = cyclesequencing.workflow) " +
                "LEFT JOIN extraction ON (extraction.plate = plate.id OR extraction.id = workflow.extractionId) " +
                "LEFT OUTER JOIN sequencing_result ON cyclesequencing.id = sequencing_result.reaction " +
                "LEFT OUTER JOIN assembly ON sequencing_result.assembly = assembly.id " +
                "WHERE");
        ArrayList<Object> sqlValues = new ArrayList<Object>();

        if (workflowDocuments != null) {
            for (WorkflowDocument doc : workflowDocuments) {
                for (int i = 0; i < doc.getNumberOfParts(); i++) {
                    WorkflowDocument.ReactionPart p = (WorkflowDocument.ReactionPart) doc.getPart(i);
                    Reaction reaction = p.getReaction();
                    plateIds.add(reaction.getPlateId());
                }
            }
        }
        if (plateIds.size() > 0) {
            sql.append(" (");
            for (Iterator<Integer> it = plateIds.iterator(); it.hasNext(); ) {
                Integer intg = it.next();
                //noinspection StringConcatenationInsideStringBufferAppend
                sql.append(" plate.id=" + intg);
                if (it.hasNext()) {
                    sql.append(" OR");
                }
            }
            sql.append(")");
        }
        if (refinedQueries.size() > 0) {
            if (plateIds.size() > 0) {
                sql.append(operator == CompoundSearchQuery.Operator.AND ? " AND (" : " OR (");
            } else {
                sql.append(" (");
            }

            sql.append(queryToSql(refinedQueries, operator, "plate", sqlValues));

            sql.append(")");
        }
        System.out.println(sql.toString());

        List<PlateDocument> docs = new ArrayList<PlateDocument>();
        BiocodeUtilities.CancelListeningThread listeningThread = null;
        if (cancelable != null) {
            //todo: listeningThread = new BiocodeUtilities.CancelListeningThread(cancelable, statement);
        }
        PreparedStatement statement = null;
        try {
            statement = connection2.prepareStatement(sql.toString());
            if (!isLocal()) {
                statement.setFetchSize(Integer.MIN_VALUE);
            }

            fillStatement(sqlValues, statement);
            System.out.println("EXECUTING PLATE QUERY");

            ResultSet resultSet = statement.executeQuery();


            Map<Integer, Plate> plateMap = createPlateDocuments(callback, cancelable, resultSet);

            if (cancelable != null && cancelable.isCanceled()) {
                return Collections.emptyList();
            }

            System.out.println("Creating plate documents");
            //        System.out.println("Getting GEL images");
            //getGelImagesForPlates(plateMap.values());   //we are only downloading these when the user wants to view them now...
            for (Plate plate : plateMap.values()) {
                docs.add(new PlateDocument(plate));
            }
            int time = (int) (System.currentTimeMillis() - startTime) / 1000;
            System.out.println("done in " + time + " seconds!");
        } finally {
            SqlUtilities.cleanUpStatements(statement);
        }
        if (listeningThread != null) {
            listeningThread.finish();
        }
        return docs;

    }

    private class WorkflowsAndPlatesQueryResult {
        Map<Integer, Plate> plates;
        Map<Integer, WorkflowDocument> workflows;
        List<Integer> sequenceIds;

        private WorkflowsAndPlatesQueryResult() {
            plates = new HashMap<Integer, Plate>();
            workflows = new HashMap<Integer, WorkflowDocument>();
            sequenceIds = new ArrayList<Integer>();
        }
    }

    private Map<Integer, Plate> createPlateDocuments(RetrieveCallback callback, Cancelable cancelable, ResultSet resultSet) throws SQLException, DatabaseServiceException {
        Map<Integer, Plate> plates = createPlateAndWorkflowsFromResultSet(cancelable, resultSet).plates;
        if (callback != null) {
            for (Plate plate : plates.values()) {
                System.out.println("Adding " + plate.getName());
                callback.add(new PlateDocument(plate), Collections.<String, Object>emptyMap());
            }
        }
        return plates;
    }

    private WorkflowsAndPlatesQueryResult createPlateAndWorkflowsFromResultSet(Cancelable cancelable, ResultSet resultSet) throws SQLException, DatabaseServiceException {
        WorkflowsAndPlatesQueryResult result = new WorkflowsAndPlatesQueryResult();
        final StringBuilder totalErrors = new StringBuilder("");

        Set<Integer> plateIds = new HashSet<Integer>();
        Map<Integer, String> workflowToSampleId = new HashMap<Integer, String>();
        System.out.println("Creating Reactions...");
        while (resultSet.next()) {
            if (SystemUtilities.isAvailableMemoryLessThan(50)) {
                resultSet.close();
                throw new SQLException("Search cancelled due to lack of free memory");
            }
            if (cancelable != null && cancelable.isCanceled()) {
                return new WorkflowsAndPlatesQueryResult();
            }

            int plateId = resultSet.getInt("plate.id");
            if(!resultSet.wasNull()) {
                plateIds.add(plateId);
            }

            int sequenceId = resultSet.getInt("assembly.id");
            if (!resultSet.wasNull()) {
                result.sequenceIds.add(sequenceId);
            }

            int workflowId = resultSet.getInt("workflow.id");
            String workflowName = resultSet.getString("workflow.name");
            if(workflowName != null) {  // null name means there is no workflow
                WorkflowDocument existingWorkflow = result.workflows.get(workflowId);
                if (existingWorkflow != null) {
                    existingWorkflow.addRow(resultSet);
                } else {
                    WorkflowDocument newWorkflow = new WorkflowDocument(resultSet);
                    result.workflows.put(workflowId, newWorkflow);
                }

                String sampleId = resultSet.getString("extraction.sampleId");
                if (sampleId != null) {
                    workflowToSampleId.put(workflowId, sampleId);
                }
            }
        }

        if (!workflowToSampleId.isEmpty()) {
            try {
                // Instantiation of a ExtractionReaction's FIMS sample relies on it being in the cache.
                // So pre-cache all the samples we need in one query and hold a reference so they don't get garbage collected
                @SuppressWarnings("UnusedDeclaration")
                List<FimsSample> samples = BiocodeService.getInstance().getActiveFIMSConnection().retrieveSamplesForTissueIds(workflowToSampleId.values());
                for (Map.Entry<Integer, String> entry : workflowToSampleId.entrySet()) {
                    WorkflowDocument workflowDocument = result.workflows.get(entry.getKey());
                    Reaction reaction = workflowDocument.getMostRecentReaction(Reaction.Type.Extraction);
                    FimsSample sample = BiocodeService.getInstance().getActiveFIMSConnection().getFimsSampleFromCache(entry.getValue());
                    if (reaction != null && reaction.getFimsSample() == null) {
                        reaction.setFimsSample(sample);
                    }
                }
            } catch (ConnectionException e) {
                throw new DatabaseServiceException(e, "Unable to retrieve FIMS samples", false);
            }
        }

        if(!plateIds.isEmpty()) {
            // Query for full contents of plates that matched our query
            String plateQueryString = constructPlateQuery(plateIds);
            PreparedStatement selectPlate = null;
            try {
                System.out.println("Running LIMS (plates) query:");
                System.out.print("\t");
                SqlUtilities.printSql(plateQueryString, plateIds);

                selectPlate = connection.prepareStatement(plateQueryString);
                fillStatement(new ArrayList<Object>(plateIds), selectPlate);

                long start = System.currentTimeMillis();
                ResultSet plateSet = selectPlate.executeQuery();
                System.out.println("\tTook " + (System.currentTimeMillis() - start) + "ms to do LIMS (plates) query");

                result.plates.putAll(getPlatesFromResultSet(plateSet));
                plateSet.close();
            } finally {
                SqlUtilities.cleanUpStatements(selectPlate);
            }
        }

        if (totalErrors.length() > 0) {
            Runnable runnable = new Runnable() {
                public void run() {
                    if (totalErrors.toString().contains("connection")) {
                        Dialogs.showMoreOptionsDialog(new Dialogs.DialogOptions(new String[]{"OK"}, "Connection Error"), "There was an error connecting to the server.  Try logging out and logging in again.", totalErrors.toString());
                    } else {
                        Dialogs.showMessageDialog("Geneious has detected the following possible errors in your database.  Please contact your system administrator for asistance.\n\n" + totalErrors, "Database errors detected", null, Dialogs.DialogIcon.WARNING);
                    }
                }
            };
            ThreadUtilities.invokeNowOrLater(runnable);
        }
        return result;
    }

    private Map<Integer, Plate> getPlatesFromResultSet(ResultSet resultSet) throws SQLException {
        Map<Integer, Plate> plates = new HashMap<Integer, Plate>();
        final StringBuilder totalErrors = new StringBuilder("");

        int previousId = -1;
        while(resultSet.next()) {
            Plate plate;
            int plateId = resultSet.getInt("plate.id");
            if (plateId == 0 && resultSet.getString("plate.name") == null) {
                continue;  // Plate was deleted
            }

            if (previousId >= 0 && previousId != plateId) {
                final Plate prevPlate = plates.get(previousId);
                if (prevPlate != null) {
                    Runnable runnable = new Runnable() {
                        public void run() {
                            prevPlate.initialiseReactions();
                        }
                    };
                    ThreadUtilities.invokeNowOrWait(runnable);
                    String error = checkReactions(prevPlate);
                    if (error != null) {
                        //noinspection StringConcatenationInsideStringBufferAppend
                        totalErrors.append(error + "\n");
                    }
                    plates.put(previousId, prevPlate);
                }
            }
            previousId = plateId;

            if (plates.get(plateId) == null) {
                plate = new Plate(resultSet);
                plates.put(plate.getId(), plate);
            } else {
                plate = plates.get(plateId);
            }
            Reaction reaction = plate.addReaction(resultSet);
            if (reaction == null) {
                //do nothing
            }
        }

        if (previousId >= 0) {
            Plate prevPlate = plates.get(previousId);
            if (prevPlate != null) {
                prevPlate.initialiseReactions();
                String error = checkReactions(prevPlate);
                if (error != null) {
                    //noinspection StringConcatenationInsideStringBufferAppend
                    totalErrors.append(error + "\n");
                }

                plates.put(previousId, prevPlate);

            }
        }
        setInitialTraceCountsForPlates(plates);

        if (totalErrors.length() > 0) {
            Runnable runnable = new Runnable() {
                public void run() {
                    if (totalErrors.toString().contains("connection")) {
                        Dialogs.showMoreOptionsDialog(new Dialogs.DialogOptions(new String[]{"OK"}, "Connection Error"), "There was an error connecting to the server.  Try logging out and logging in again.", totalErrors.toString());
                    } else {
                        Dialogs.showMessageDialog("Geneious has detected the following possible errors in your database.  Please contact your system administrator for asistance.\n\n" + totalErrors, "Database errors detected", null, Dialogs.DialogIcon.WARNING);
                    }
                }
            };
            ThreadUtilities.invokeNowOrLater(runnable);
        }
        return plates;
    }

    private Query generateAdvancedQueryFromBasicQuery(Query query) {
        String value = ((BasicSearchQuery) query).getSearchText();
        List<DocumentField> searchFields = getSearchAttributes();
        List<Query> queryTerms = new ArrayList<Query>();
        for (DocumentField field : searchFields) {
            if (String.class.isAssignableFrom(field.getValueType())) {
                if (field.isEnumeratedField()) {
                    boolean hasValue = false;
                    for (String s : field.getEnumerationValues()) {
                        if (s.equalsIgnoreCase(value)) {
                            hasValue = true;
                        }
                    }
                    if (!hasValue)
                        continue;
                }
                queryTerms.add(Query.Factory.createFieldQuery(field, Condition.CONTAINS, value));
            }
        }
        query = Query.Factory.createOrQuery(queryTerms.toArray(new Query[queryTerms.size()]), Collections.<String, Object>emptyMap());
        return query;
    }

    private String checkReactions(Plate plate) {
        System.out.println("Checking " + plate.getName());
        List<Reaction> reactions = new ArrayList<Reaction>();
        for (Reaction r : plate.getReactions()) {
            if (r != null) {
                reactions.add(r);
            }
        }
        return reactions.get(0).areReactionsValid(reactions, null, true);
    }

    public void getGelImagesForPlates(Collection<Plate> plates) throws SQLException {
        List<Integer> plateIds = new ArrayList<Integer>();
        for (Plate plate : plates) {
            plateIds.add(plate.getId());
        }

        Map<Integer, List<GelImage>> gelimages = getGelImages(plateIds);
        for (Plate plate : plates) {
            List<GelImage> gelimagesForPlate = gelimages.get(plate.getId());
            if (gelimagesForPlate != null) {
                plate.setImages(gelimagesForPlate);
            }
        }
    }

    public Map<String, Reaction> getExtractionReactions(List<Reaction> sourceReactions) throws SQLException {
        if (sourceReactions == null || sourceReactions.size() == 0) {
            return Collections.emptyMap();
        }
        StringBuilder sql = new StringBuilder("SELECT plate.name, plate.size, extraction.* FROM extraction, plate WHERE plate.id = extraction.plate AND (");
        for (int i = 0; i < sourceReactions.size(); i++) {
            sql.append("extractionId=?");
            if (i < sourceReactions.size() - 1) {
                sql.append(" OR ");
            }
        }
        sql.append(")");
        PreparedStatement statement = createStatement(sql.toString());
        for (int i = 0; i < sourceReactions.size(); i++) {
            statement.setString(i + 1, sourceReactions.get(i).getExtractionId());
        }
        ResultSet resultSet = statement.executeQuery();
        Map<String, Reaction> reactions = new HashMap<String, Reaction>();
        while (resultSet.next()) {
            ExtractionReaction reaction = new ExtractionReaction(resultSet);
            reactions.put(reaction.getExtractionId(), reaction);
        }
        return reactions;
    }

    private Map<Integer, List<GelImage>> getGelImages(Collection<Integer> plateIds) throws SQLException {
        if (plateIds == null || plateIds.size() == 0) {
            return Collections.emptyMap();
        }
        StringBuilder sql = new StringBuilder("SELECT * FROM gelimages WHERE (");
        for (Iterator<Integer> it = plateIds.iterator(); it.hasNext(); ) {
            Integer i = it.next();
            //noinspection StringConcatenationInsideStringBufferAppend
            sql.append("gelimages.plate=" + i);
            if (it.hasNext()) {
                sql.append(" OR ");
            }
        }
        sql.append(")");
        System.out.println(sql);
        PreparedStatement statement = createStatement(sql.toString());
        ResultSet resultSet = statement.executeQuery();
        Map<Integer, List<GelImage>> map = new HashMap<Integer, List<GelImage>>();
        while (resultSet.next()) {
            GelImage image = new GelImage(resultSet);
            List<GelImage> imageList;
            List<GelImage> existingImageList = map.get(image.getPlate());
            if (existingImageList != null) {
                imageList = existingImageList;
            } else {
                imageList = new ArrayList<GelImage>();
                map.put(image.getPlate(), imageList);
            }
            imageList.add(image);
        }
        statement.close();
        return map;
    }

    private static QueryTermSurrounder getQueryTermSurrounder(AdvancedSearchQueryTerm query) {
        String join = "";
        String append = "";
        String prepend = "";
        switch (query.getCondition()) {
            case EQUAL:
                join = "=";
                break;
            case APPROXIMATELY_EQUAL:
                join = "LIKE";
                break;
            case BEGINS_WITH:
                join = "LIKE";
                append = "%";
                break;
            case ENDS_WITH:
                join = "LIKE";
                prepend = "%";
                break;
            case CONTAINS:
                join = "LIKE";
                append = "%";
                prepend = "%";
                break;
            case GREATER_THAN:
                join = ">";
                break;
            case GREATER_THAN_OR_EQUAL_TO:
                join = ">=";
                break;
            case LESS_THAN:
                join = "<";
                break;
            case LESS_THAN_OR_EQUAL_TO:
                join = "<=";
                break;
            case NOT_CONTAINS:
                join = "NOT LIKE";
                append = "%";
                prepend = "%";
                break;
            case NOT_EQUAL:
                join = "!=";
                break;
            case IN_RANGE:
                join = "BETWEEN";
                break;
        }
        return new QueryTermSurrounder(prepend, append, join);
    }

    public Set<String> getAllExtractionIdsStartingWith(List<String> tissueIds) throws SQLException {
        if(tissueIds.isEmpty()) {
            return Collections.emptySet();
        }
        List<String> queries = new ArrayList<String>();
        //noinspection UnusedDeclaration
        for (String s : tissueIds) {
            queries.add("extractionId LIKE ?");
        }

        String sql = "SELECT extractionId FROM extraction WHERE " + StringUtilities.join(" OR ", queries);

        PreparedStatement statement = createStatement(sql);
        for (int i = 0; i < tissueIds.size(); i++) {
            statement.setString(i + 1, tissueIds.get(i) + "%");
        }

        ResultSet set = statement.executeQuery();
        Set<String> result = new HashSet<String>();

        while (set.next()) {
            result.add(set.getString("extractionId"));
        }


        return result;
    }

    public Map<String, ExtractionReaction> getExtractionsFromBarcodes(List<String> barcodes) throws SQLException {
        if (barcodes.size() == 0) {
            System.out.println("empty!");
            return Collections.emptyMap();
        }
        StringBuilder sql = new StringBuilder("SELECT * FROM extraction " +
                "LEFT JOIN plate ON plate.id = extraction.plate " +
                "WHERE (");

        List<String> queryParams = new ArrayList<String>();
        //noinspection UnusedDeclaration
        for (String barcode : barcodes) {
            queryParams.add("extraction.extractionBarcode = ?");
        }

        sql.append(StringUtilities.join(" OR ", queryParams));

        sql.append(")");

        PreparedStatement statement = createStatement(sql.toString());

        for (int i = 0; i < barcodes.size(); i++) {
            String barcode = barcodes.get(i);
            statement.setString(i + 1, barcode);
        }

        ResultSet r = statement.executeQuery();

        Map<String, ExtractionReaction> results = new HashMap<String, ExtractionReaction>();
        while (r.next()) {
            ExtractionReaction reaction = new ExtractionReaction(r);
            results.put("" + reaction.getFieldValue("extractionBarcode"), reaction);
        }

        return results;
    }


    private static class QueryTermSurrounder {
        private final String prepend, append, join;

        private QueryTermSurrounder(String prepend, String append, String join) {
            this.prepend = prepend;
            this.append = append;
            this.join = join;
        }

        public String getPrepend() {
            return prepend;
        }

        public String getAppend() {
            return append;
        }

        public String getJoin() {
            return join;
        }
    }


    public class LimsSearchResult {
        List<WorkflowDocument> workflows = new ArrayList<WorkflowDocument>();
        List<PlateDocument> plates = new ArrayList<PlateDocument>();
        List<AnnotatedPluginDocument> traces = new ArrayList<AnnotatedPluginDocument>();
        List<Integer> sequenceIds = new ArrayList<Integer>();

        public List<WorkflowDocument> getWorkflows() {
            return Collections.unmodifiableList(workflows);
        }

        public List<PlateDocument> getPlates() {
            return Collections.unmodifiableList(plates);
        }

        public List<AnnotatedPluginDocument> getTraces() {
            return Collections.unmodifiableList(traces);
        }

        public List<Integer> getSequenceIds() {
            return Collections.unmodifiableList(sequenceIds);
        }
    }

    /**
     * @param query    The query.  Can include boolean values for "workflowDocuments" and "plateDocuments" to disable downloading
     * @param tissueIdsToMatch  A list of FIMS samples to match.  Or null to return all results.
     * @param callback To add results to as they are found.  Can be null.
     * @return {@link LimsSearchResult} with workflows and plates found.
     * @throws SQLException if there is a problem with the database
     */
    public LimsSearchResult getMatchingDocumentsFromLims(Query query, Collection<String> tissueIdsToMatch, RetrieveCallback callback) throws SQLException, DatabaseServiceException {
        LimsSearchResult result = new LimsSearchResult();

        // We test against false so that the default is to download
        Boolean downloadWorkflows = BiocodeService.isDownloadWorkflows(query);
        Boolean downloadPlates = BiocodeService.isDownloadPlates(query);
        Boolean downloadSequences = BiocodeService.isDownloadSequences(query);

        if (query instanceof BasicSearchQuery) {
            query = generateAdvancedQueryFromBasicQuery(query);
        }

        List<AdvancedSearchQueryTerm> terms = new ArrayList<AdvancedSearchQueryTerm>();
        CompoundSearchQuery.Operator operator;
        if (query instanceof CompoundSearchQuery) {
            CompoundSearchQuery compoundQuery = (CompoundSearchQuery) query;
            operator = compoundQuery.getOperator();
            for (Query innerQuery : compoundQuery.getChildren()) {
                if (isCompatibleSearchQueryTerm(innerQuery)) {
                    terms.add((AdvancedSearchQueryTerm) innerQuery);
                }
            }
        } else {
            if (isCompatibleSearchQueryTerm(query)) {
                AdvancedSearchQueryTerm advancedQuery = (AdvancedSearchQueryTerm) query;
                terms.add(advancedQuery);


            }
            operator = CompoundSearchQuery.Operator.AND;
        }


        Map<String, List<AdvancedSearchQueryTerm>> tableToTerms = mapQueryTermsToTable(terms);
        List<Object> sqlValues = new ArrayList<Object>();
        if (tissueIdsToMatch != null && !tissueIdsToMatch.isEmpty()) {
            sqlValues.addAll(tissueIdsToMatch);
        }
        QueryPart workflowPart = getQueryForTable("workflow", tableToTerms, operator);
        if (workflowPart != null) {
            sqlValues.addAll(workflowPart.parameters);
        }

        QueryPart extractionPart = getQueryForTable("extraction", tableToTerms, operator);
        if (extractionPart != null) {
            sqlValues.addAll(extractionPart.parameters);
        }

        QueryPart platePart = getQueryForTable("plate", tableToTerms, operator);
        if (platePart != null) {
            sqlValues.addAll(platePart.parameters);
        }

        QueryPart assemblyPart = getQueryForTable("assembly", tableToTerms, operator);
        if (assemblyPart != null) {
            sqlValues.addAll(assemblyPart.parameters);
        }

        boolean searchedForSamplesButFoundNone = tissueIdsToMatch != null && tissueIdsToMatch.isEmpty();  // samples == null when doing a browse query
        boolean nothingToSearchForInLims = workflowPart == null && extractionPart == null && platePart == null && assemblyPart == null;
        if (searchedForSamplesButFoundNone && nothingToSearchForInLims) {
            return result;
        }

        StringBuilder workflowQuery = constructWorkflowQueryString(tissueIdsToMatch, operator,
                workflowPart, extractionPart, platePart, assemblyPart);

        WorkflowsAndPlatesQueryResult queryResult;
        PreparedStatement preparedStatement = null;
        try {
            System.out.println("Running LIMS (workflows) query:");
            System.out.print("\t");
            SqlUtilities.printSql(workflowQuery.toString(), sqlValues);

            preparedStatement = connection.prepareStatement(workflowQuery.toString());
            fillStatement(sqlValues, preparedStatement);

            long start = System.currentTimeMillis();
            ResultSet resultSet = preparedStatement.executeQuery();
            System.out.println("\tTook " + (System.currentTimeMillis() - start) + "ms to do LIMS (workflows) query");

            queryResult = createPlateAndWorkflowsFromResultSet(callback, resultSet);
        } finally {
            SqlUtilities.cleanUpStatements(preparedStatement);
        }

        List<WorkflowDocument> workflows = new ArrayList<WorkflowDocument>(queryResult.workflows.values());
        if (downloadWorkflows && callback != null) {
            for (WorkflowDocument document : workflows) {
                callback.add(document, Collections.<String, Object>emptyMap());
            }
        }
        result.workflows.addAll(workflows);
        result.sequenceIds.addAll(queryResult.sequenceIds);

        List<Plate> plates = new ArrayList<Plate>(queryResult.plates.values());
        for (Plate plate : plates) {
            PlateDocument plateDocument = new PlateDocument(plate);
            if (downloadPlates && callback != null) {
                callback.add(DocumentUtilities.createAnnotatedPluginDocument(plateDocument), Collections.<String, Object>emptyMap());
            }
            result.plates.add(plateDocument);
        }
        return result;
    }

    private void setInitialTraceCountsForPlates(Map<Integer, Plate> plateMap) throws SQLException {
        if (plateMap.isEmpty()) {
            return;
        }
        List<Object> plateIds = new ArrayList<Object>(plateMap.keySet());
        Map<Integer, CycleSequencingReaction> mapping = new HashMap<Integer, CycleSequencingReaction>();
        for (Plate plate : plateMap.values()) {
            for (Reaction reaction : plate.getReactions()) {
                if (reaction instanceof CycleSequencingReaction) {
                    mapping.put(reaction.getId(), (CycleSequencingReaction) reaction);
                }
            }
        }

        StringBuilder countingQuery = new StringBuilder("SELECT cyclesequencing.id, COUNT(traces.id) as traceCount FROM " +
                "cyclesequencing LEFT JOIN traces ON cyclesequencing.id = traces.reaction WHERE cyclesequencing.plate IN ");
        SqlUtilities.appendSetOfQuestionMarks(countingQuery, plateMap.size());
        countingQuery.append(" GROUP BY cyclesequencing.id");
        PreparedStatement getCount = null;
        try {
            getCount = connection.prepareStatement(countingQuery.toString());
            fillStatement(plateIds, getCount);
            SqlUtilities.printSql(countingQuery.toString(), plateIds);
            System.out.println("Running trace counting query:");
            System.out.print("\t");
            long start = System.currentTimeMillis();
            ResultSet countSet = getCount.executeQuery();
            System.out.println("\tTook " + (System.currentTimeMillis() - start) + "ms to do trace counting query");
            while (countSet.next()) {
                int reactionId = countSet.getInt("cyclesequencing.id");
                int count = countSet.getInt("traceCount");
                CycleSequencingReaction reaction = mapping.get(reactionId);
                if (reaction != null) {  // Might be null if we haven't downloaded the full plate
                    reaction.setCacheNumTraces(count);
                }
            }
        } finally {
            SqlUtilities.cleanUpStatements(getCount);
        }
    }

    /**
     * Builds a LIMS SQL query from {@link Query}.  Can be used to create workflows and a list of plates that match
     * the query.
     *
     * @param tissueIdsToMatch          The samples to match
     * @param operator                  The {@link com.biomatters.geneious.publicapi.databaseservice.CompoundSearchQuery.Operator} to use for the query
     * @param workflowQueryConditions   Conditions to search workflow on
     * @param extractionQueryConditions Conditions to search extraction on
     * @param plateQueryConditions      Conditions to search plate on
     * @param assemblyQueryConditions   Conditions to search assembly on
     * @return A SQL string that can be used to query the MySQL LIMS
     */
    private StringBuilder constructWorkflowQueryString(Collection<String> tissueIdsToMatch, CompoundSearchQuery.Operator operator,
                                                       QueryPart workflowQueryConditions, QueryPart extractionQueryConditions,
                                                       QueryPart plateQueryConditions, QueryPart assemblyQueryConditions) {
        String operatorString = operator == CompoundSearchQuery.Operator.AND ? " AND " : " OR ";
        StringBuilder whereConditionForOrQuery = new StringBuilder();

        /* Note: We have to use a CASE statement in the column definitions because there is a bug in HSQL which ignores
         * the column label and always uses the column name.  This effectively means the table name is ignored when
         * calling ResultSet.get*() and the first column with the name is returned.  Even when using AS x, the x label is ignored.
         */
        StringBuilder queryBuilder = new StringBuilder(
                "SELECT extraction.*, workflow.*, pcr.*, cyclesequencing.*, plate.* , assembly.id, assembly.progress, " +
                        "assembly.date, assembly.notes, assembly.failure_reason, assembly.failure_notes");
        StringBuilder conditionBuilder = operator == CompoundSearchQuery.Operator.AND ? queryBuilder : whereConditionForOrQuery;

        queryBuilder.append(" FROM extraction LEFT OUTER JOIN ").append("workflow ON extraction.id = workflow.extractionId");
        if (tissueIdsToMatch != null && !tissueIdsToMatch.isEmpty()) {
            if (operator == CompoundSearchQuery.Operator.AND) {
                conditionBuilder.append(" AND ");
            }
            String sampleColumn = isLocal() ? "LOWER(sampleId)" : "sampleId";  // MySQL is case insensitive by default
            conditionBuilder.append(" (").append(sampleColumn).append(" IN ");
            SqlUtilities.appendSetOfQuestionMarks(conditionBuilder, tissueIdsToMatch.size());
        }
        if (workflowQueryConditions != null) {
            if (tissueIdsToMatch != null && !tissueIdsToMatch.isEmpty()) {
                conditionBuilder.append(operatorString);
            } else if (operator == CompoundSearchQuery.Operator.AND) {
                conditionBuilder.append(" AND ");
            }
            conditionBuilder.append("(").append(workflowQueryConditions).append(")");
        }
        if (extractionQueryConditions != null) {
            conditionBuilder.append(operatorString);
            conditionBuilder.append("(").append(extractionQueryConditions).append(")");
        }

        if (tissueIdsToMatch != null && !tissueIdsToMatch.isEmpty()) {
            conditionBuilder.append(")");
        }

        queryBuilder.append(" LEFT OUTER JOIN ").append("pcr ON pcr.workflow = workflow.id ");
        queryBuilder.append(" LEFT OUTER JOIN ").append("cyclesequencing ON cyclesequencing.workflow = workflow.id ");

        // INNER JOIN here because there should always be a plate for a reaction.  We have already joined the 3 reaction tables
        queryBuilder.append(" INNER JOIN ").append("plate ON (extraction.plate = plate.id OR pcr.plate = plate.id OR cyclesequencing.plate = plate.id)");
        if (plateQueryConditions != null) {
            if (operator == CompoundSearchQuery.Operator.AND || extractionQueryConditions != null || workflowQueryConditions != null) {
                conditionBuilder.append(operatorString);
            }
            conditionBuilder.append("(").append(plateQueryConditions).append(")");
        }
        queryBuilder.append(" LEFT OUTER JOIN sequencing_result ON cyclesequencing.id = sequencing_result.reaction ");
        queryBuilder.append(operator == CompoundSearchQuery.Operator.AND && assemblyQueryConditions != null ? " INNER JOIN " : " LEFT OUTER JOIN ").
                append("assembly ON assembly.id = sequencing_result.assembly");

        if (assemblyQueryConditions != null) {
            conditionBuilder.append(operatorString);
            conditionBuilder.append("(").append(assemblyQueryConditions).append(")");
        }

        if (operator == CompoundSearchQuery.Operator.OR && whereConditionForOrQuery.length() > 0) {
            queryBuilder.append(" WHERE ");
            queryBuilder.append(whereConditionForOrQuery);
        }

        queryBuilder.append(" ORDER BY workflow.id, assembly.date desc");
        return queryBuilder;
    }

    private String constructPlateQuery(Collection<Integer> plateIds) {
        StringBuilder queryBuilder = new StringBuilder("SELECT E.id, E.extractionId, " +
                "plate.*, extraction.*, workflow.*, pcr.*, cyclesequencing.*, " +
                "assembly.id, assembly.progress, assembly.date, assembly.notes, assembly.failure_reason, assembly.failure_notes FROM ");
        // We join plate twice because HSQL doesn't let us use aliases.  The way the query is written means the select would produce a derived table.
        queryBuilder.append("(SELECT * FROM plate WHERE id IN ");
        SqlUtilities.appendSetOfQuestionMarks(queryBuilder, plateIds.size());
        queryBuilder.append(") matching ");
        queryBuilder.append("INNER JOIN plate ON plate.id = matching.id ");
        queryBuilder.append("LEFT OUTER JOIN extraction ON extraction.plate = plate.id ");
        queryBuilder.append("LEFT OUTER JOIN workflow W ON extraction.id = W.extractionId ");
        queryBuilder.append("LEFT OUTER JOIN pcr ON pcr.plate = plate.id ");
        queryBuilder.append("LEFT OUTER JOIN cyclesequencing ON cyclesequencing.plate = plate.id ");
        queryBuilder.append("LEFT OUTER JOIN sequencing_result ON cyclesequencing.id = sequencing_result.reaction ");
        queryBuilder.append("LEFT OUTER JOIN assembly ON assembly.id = sequencing_result.assembly ");

        // This bit of if else is required so that MySQL will use the index on workflow ID.  Using multiple columns causes it to do a full table scan.
        queryBuilder.append("LEFT OUTER JOIN workflow ON workflow.id = ");
        queryBuilder.append(    "CASE WHEN pcr.workflow IS NOT NULL THEN pcr.workflow ELSE ");
        queryBuilder.append(        "CASE WHEN W.id IS NOT NULL THEN W.id ELSE ");
        queryBuilder.append(        "cyclesequencing.workflow END ");
        queryBuilder.append(    "END ");

        queryBuilder.append("LEFT OUTER JOIN extraction E ON E.id = " +
                "CASE WHEN extraction.id IS NULL THEN workflow.extractionId ELSE extraction.id END ");  // So we get extraction ID for pcr and cyclesequencing reactions

        queryBuilder.append(" ORDER by plate.id, assembly.date desc");
        return queryBuilder.toString();
    }

    private QueryPart getQueryForTable(String table, Map<String, List<AdvancedSearchQueryTerm>> tableToTerms, CompoundSearchQuery.Operator operator) {
        List<AdvancedSearchQueryTerm> workflowTerms = tableToTerms.get(table);
        if (workflowTerms == null) {
            return null;
        }

        List<Object> objects = new ArrayList<Object>();
        String queryConditions = queryToSql(workflowTerms, operator, table, objects);
        return new QueryPart(queryConditions, objects);
    }

    private static class QueryPart {
        private String queryConditions;
        private List<Object> parameters;

        private QueryPart(String queryConditions, List<Object> parameters) {
            this.queryConditions = queryConditions;
            this.parameters = parameters;
        }

        @Override
        public String toString() {
            return queryConditions;
        }
    }


    private boolean isCompatibleSearchQueryTerm(Query query) {
        assert (query instanceof AdvancedSearchQueryTerm);
        if (query instanceof AdvancedSearchQueryTerm) {
            Object[] queryValues = ((AdvancedSearchQueryTerm) query).getValues();
            if (queryValues.length == 0 || queryValues[0] == null || queryValues[0].equals("")) {
                return false;
            }
            return true;
        }
        return false;
    }

    /**
     * Filters a list of search terms by which table they apply to.
     * <p/>
     * If a term applies to multiple tables it will appear in the list for both.  If a term does not apply to any table
     * then it will not be included in the results.
     *
     * @param terms A list of terms to filter
     * @return A mapping from LIMS table to query term
     */
    Map<String, List<AdvancedSearchQueryTerm>> mapQueryTermsToTable(List<AdvancedSearchQueryTerm> terms) {
        Map<String, List<AdvancedSearchQueryTerm>> result = new HashMap<String, List<AdvancedSearchQueryTerm>>();
        for (Map.Entry<String, List<DocumentField>> entry : TABLE_TO_FIELDS.entrySet()) {
            List<DocumentField> fieldsForTable = entry.getValue();
            List<AdvancedSearchQueryTerm> matchingTerms = new ArrayList<AdvancedSearchQueryTerm>();
            for (AdvancedSearchQueryTerm term : terms) {
                if (fieldsForTable.contains(term.getField())) {
                    matchingTerms.add(term);
                }
            }
            if (!matchingTerms.isEmpty()) {
                result.put(entry.getKey(), matchingTerms);
            }
        }
        return result;
    }

    /**
     * Sets a database wide property.  Can be retrieved by calling {@link #getProperty(String)}
     *
     * @param key   The name of the property
     * @param value The value to set for the property
     * @throws SQLException if something goes wrong communicating with the database.
     */
    void setProperty(String key, String value) throws SQLException {
        PreparedStatement update = null;
        PreparedStatement insert = null;

        try {
            update = connection.prepareStatement("UPDATE properties SET value = ? WHERE name = ?");

            update.setObject(1, value);
            update.setObject(2, key);
            int changed = update.executeUpdate();
            if (changed == 0) {
                insert = connection.prepareStatement("INSERT INTO properties(value, name) VALUES (?,?)");
                insert.setObject(1, value);
                insert.setObject(2, key);
                insert.executeUpdate();
            }
        } finally {
            SqlUtilities.cleanUpStatements(update, insert);
        }
    }

    /**
     * Retrieves a property from the database previously set by calling {@link #setProperty(String, String)}
     *
     * @param key The name of the property to retrieve
     * @return value of the property or null if it does not exist
     * @throws SQLException if something goes wrong communicating with the database.
     */
    String getProperty(String key) throws SQLException {
        PreparedStatement get = null;
        try {
            get = connection.prepareStatement("SELECT value FROM properties WHERE name = ?");
            get.setObject(1, key);
            ResultSet resultSet = get.executeQuery();
            if (resultSet.next()) {
                return resultSet.getString("value");
            } else {
                return null;
            }
        } finally {
            SqlUtilities.cleanUpStatements(get);
        }
    }


}
