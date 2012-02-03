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
import com.biomatters.geneious.publicapi.utilities.StringUtilities;
import com.biomatters.geneious.publicapi.utilities.ThreadUtilities;
import com.biomatters.geneious.publicapi.utilities.SystemUtilities;
import com.biomatters.geneious.publicapi.utilities.GeneralUtilities;
import com.biomatters.options.PasswordOption;
import com.biomatters.plugins.biocode.BiocodeUtilities;
import com.biomatters.plugins.biocode.assembler.annotate.AnnotateUtilities;
import com.biomatters.plugins.biocode.assembler.annotate.FimsData;
import com.biomatters.plugins.biocode.assembler.annotate.FimsDataGetter;
import com.biomatters.plugins.biocode.labbench.*;
import com.biomatters.plugins.biocode.labbench.plates.GelImage;
import com.biomatters.plugins.biocode.labbench.plates.Plate;
import com.biomatters.plugins.biocode.labbench.reaction.CycleSequencingReaction;
import com.biomatters.plugins.biocode.labbench.reaction.ExtractionReaction;
import com.biomatters.plugins.biocode.labbench.reaction.PCRReaction;
import com.biomatters.plugins.biocode.labbench.reaction.Reaction;
import jebl.util.Cancelable;

import java.sql.*;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.Date;

/**
 * @author steve
 * @version $Id: 27/05/2009 6:28:38 AM steve $
 */
@SuppressWarnings({"ConstantConditions"})
public class LIMSConnection {
    @SuppressWarnings({"ConstantConditions"})
    public static final int EXPECTED_SERVER_VERSION = 9;
    Driver driver;
    Connection connection;
    Connection connection2;
    private LocalLIMS localLIMS;
    private Options limsOptions;
    private String username;
    private String schema;
    public static final DocumentField WORKFLOW_NAME_FIELD = new DocumentField("Workflow Name", "", "workflow.name", String.class, true, false);
    public static final DocumentField PLATE_TYPE_FIELD = DocumentField.createEnumeratedField(new String[] {"Extraction", "PCR", "CycleSequencing"}, "Plate type", "", "plate.type", true, false);
    public static final DocumentField PLATE_NAME_FIELD = new DocumentField("Plate Name (LIMS)", "", "plate.name", String.class, true, false);
    public static final DocumentField DATE_FIELD = new DocumentField("Last Modified (LIMS)", "", "date", Date.class, true, false);
    public static final DocumentField PLATE_DATE_FIELD = new DocumentField("Last Modified (LIMS plate)", "", "plate.date", Date.class, false, false);
    public static final DocumentField WORKFLOW_DATE_FIELD = new DocumentField("Last Modified (LIMS workflow)", "", "workflow.date", Date.class, false, false);
    public static final DocumentField WORKFLOW_LOCUS_FIELD = new DocumentField("Locus", "The locus of the workflow", "workflow.locus", String.class, true, false);
    public static final DocumentField EXTRACTION_NAME_FIELD = new DocumentField("Extraction ID", "The Extraction ID", "extraction.extractionId", String.class, true, false);
    public static final DocumentField EXTRACTION_BARCODE_FIELD = new DocumentField("Extraction Barcode", "The Extraction Barcode", "extraction.extractionBarcode", String.class, true, false);
    public static final DocumentField SEQUENCE_PROGRESS = DocumentField.createEnumeratedField(new String[] {"passed", "failed"}, "Sequence Progress", "Whether the sequence passed or failed sequencing and assembly", "assembly.progress", true, false);
    public static final DocumentField SEQUENCE_SUBMISSION_PROGRESS = DocumentField.createEnumeratedField(new String[]{"Yes", "No"}, "Sequence Submitted", "Indicates whether this sequence has been submitte to a sequence database (e.g. Genbank)", "assembly.submitted", false, false);
    public static final DocumentField SEQUENCE_ID = DocumentField.createIntegerField("LIMS Sequence ID", "The Unique ID of this sequence in LIMS", "LimsSequenceId", false, false);
    public static final DocumentField EDIT_RECORD = DocumentField.createStringField("Edit Record", "A record of edits made to this sequence", "assembly.editRecord", false, false);
    public static final DocumentField ASSEMBLY_TECHNICIAN = DocumentField.createStringField("Assembly Technician", "", "assembly.technician", false, false);
    private boolean isLocal;
    private String PLATE_NAME = "plate.name";
    private final String PLATE_TYPE = "plate.type";
    private final String PLATE_DATE = "plate.date";
    private final String WORKFLOW_NAME = "workflow.name";
    private final String WORKFLOW_DATE = "workflow.date";
    private final String LOCUS = "locus";
    private final String EXTRACTION_ID = "extraction.extractionId";
    private final String EXTRACTION_BARCODE = "extraction.extractionBarcode";
    String serverUrn;

    public LIMSConnection() {}

    /**
     * creates a new LIMSConnection connected to the given local LIMS database
     * @param localDatabaseName
     * @throws ConnectionException
     */
    public LIMSConnection(String localDatabaseName) throws ConnectionException{
        connectLocal(localDatabaseName, false);
    }

    public static PasswordOptions createConnectionOptions() {
        return new LimsConnectionOptions(LIMSConnection.class);
    }

    static Options getLocalOptions() {
        return LocalLIMS.getConnectionOptions();
    }

    public boolean isLocal() {
        return isLocal;
    }

    public String getUrn() {
        return serverUrn;
    }

    public static boolean isLocal(PasswordOptions connectionOptions) {
        return !connectionOptions.getValueAsString("connectionType").equals("remote");    
    }

    public String getUsername() {
        if(isLocal()) {
            throw new IllegalStateException("Username does not apply to local connections");
        }
        return username;
    }

    public String getSchema() {
        if(isLocal()) {
            throw new IllegalStateException("Schema does not apply to local connections");
        }
        return schema;
    }

    public void connect(PasswordOptions LIMSOptions) throws ConnectionException {
        if(isLocal(LIMSOptions)) {
            driver = BiocodeService.getLocalDriver();
            this.limsOptions = LIMSOptions;
            connectLocal(LocalLIMS.getDbNameFromConnectionOptions(LIMSOptions.getChildOptions().get("local")), false);
        }
        else {
            driver = BiocodeService.getDriver();
            connectRemote(LIMSOptions.getChildOptions().get("remote"));
        }
    }

    private void connectLocal(String dbName, boolean alreadyAskedAboutUpgrade) throws ConnectionException {
        isLocal = true;
        if(localLIMS == null) {
            localLIMS = new LocalLIMS();
            localLIMS.initialize(BiocodeService.getInstance().getDataDirectory());
        }
        connection = localLIMS.connect(dbName);
        serverUrn = "local/"+dbName;
        try {
            ResultSet resultSet = connection.createStatement().executeQuery("SELECT * FROM databaseversion LIMIT 1");
            if(!resultSet.next()) {
                throw new ConnectionException("Your LIMS database appears to be corrupt.  Please contact your systems administrator for assistance.");
            }
            else {
                int version = resultSet.getInt("version");

                if(version < EXPECTED_SERVER_VERSION) {
                    if(alreadyAskedAboutUpgrade || Dialogs.showYesNoDialog("The LIMS database you are connecting to is written for an older version of this plugin.  Would you like to upgrade it?", "Old database", null, Dialogs.DialogIcon.QUESTION)) {
                        localLIMS.upgradeDatabase(dbName);
                        connectLocal(dbName, true);
                    }
                    else {
                        throw new ConnectionException("You need to upgrade your database, or choose another one to continue");
                    }
                }
                else if(version > EXPECTED_SERVER_VERSION) {
                    throw new ConnectionException("This database was written for a newer version of the LIMS plugin, and cannot be accessed");
                }
            }
        }
        catch(SQLException ex) {
            throw new ConnectionException(ex.getMessage(), ex);
        }
    }

    private void connectRemote(Options LIMSOptions) throws ConnectionException {
        isLocal = false;
        //connect to the LIMS
        this.limsOptions = LIMSOptions;
        Properties properties = new Properties();
        username = LIMSOptions.getValueAsString("username");
        properties.put("user", username);
        properties.put("password", ((PasswordOption)LIMSOptions.getOption("password")).getPassword());
        try {
            DriverManager.setLoginTimeout(20);
            serverUrn = LIMSOptions.getValueAsString("server") + ":" + LIMSOptions.getValueAsString("port");
            connection = driver.connect("jdbc:mysql://" + serverUrn, properties);
            connection2 = driver.connect("jdbc:mysql://"+ serverUrn, properties);
            Statement statement = connection.createStatement();
            schema = LIMSOptions.getValueAsString("database");
            connection.createStatement().execute("USE "+ schema);
            connection2.createStatement().execute("USE "+ schema);
            ResultSet resultSet = statement.executeQuery("SELECT * FROM databaseversion LIMIT 1");
            serverUrn += "/"+ schema;
            if(!resultSet.next()) {
                throw new ConnectionException("Your LIMS database appears to be corrupt.  Please contact your systems administrator for assistance.");
            }
            else {
                int version = resultSet.getInt("version");
                if(version != EXPECTED_SERVER_VERSION) {
                    throw new ConnectionException("The server you are connecting to is running an "+(version > EXPECTED_SERVER_VERSION ? "newer" : "older")+" version of the LIMS database ("+version+") than this plugin was designed for ("+EXPECTED_SERVER_VERSION+").  Please contact your systems administrator for assistance.");
                }
            }
            resultSet.close();
        } catch (SQLException e1) {
            throw new ConnectionException(e1);
        }
    }

    public void disconnect() {
//        if(connection != null) {
//            Thread t = new Thread() {
//                public void run() {
//                    try {
//                        connection.close();
//                        requiresMySql = false;
//                    } catch (SQLException e) {
//                        System.out.println(e);
//                        e.printStackTrace();
//                    }
//                }
//            };
//            t.start();
//        }
        //we used to explicitly close the SQL connection, but this was causing crashes if the user logged out while a query was in progress.
        //now we remove all references to it and let the garbage collector close it when the queries have finished.
        connection = null;
        connection2 = null;
        limsOptions = null;
        isLocal = false;
        serverUrn = null;
    }

    public void reconnect() throws ConnectionException{
        if(this.limsOptions == null) {
            return;
        }
        Options limsOptions = this.limsOptions;
        boolean isLocal = this.isLocal;
        disconnect();
        if(isLocal) {
            connectLocal(LocalLIMS.getDbNameFromConnectionOptions(limsOptions), true);
        }
        else {
            connectRemote(limsOptions);
        }
    }

    public Set<Integer> deleteRecords(String tableName, String term, Iterable ids) throws SQLException{
        if(!BiocodeService.getInstance().deleteAllowed(tableName)) {
            throw new SQLException("It appears that you do not have permission to delete from "+tableName+".  Please contact your System Administrator for assistance");
        }

        List<String> terms = new ArrayList<String>();
        int count = 0;
        for(Object id : ids) {
            count++;
            terms.add(term+"="+id);
        }

        if(count == 0) {
            return Collections.emptySet();
        }

        String termString = StringUtilities.join(" OR ", terms);

        Set<Integer> plateIds = new HashSet<Integer>();
        if(tableName.equals("extraction") || tableName.equals("pcr") || tableName.equals("cyclesequencing")) {
            PreparedStatement getPlatesStatement = createStatement("SELECT plate FROM "+tableName+" WHERE "+termString);
            ResultSet resultSet = getPlatesStatement.executeQuery();
            while(resultSet.next()) {
                plateIds.add(resultSet.getInt("plate"));
            }
            getPlatesStatement.close();
        }

        PreparedStatement deleteStatement = createStatement("DELETE FROM "+tableName+" WHERE "+termString);
        deleteStatement.executeUpdate();
        deleteStatement.close();

        return plateIds;
    }

    public ResultSet executeQuery(String sql) throws TransactionException{
        try {
            PreparedStatement statement = createStatement(sql);
            return statement.executeQuery();
        }
        catch(SQLException ex) {
            throw new TransactionException("Could not execute LIMS query", ex);
        }
    }

    public void executeUpdate(String sql) throws TransactionException {
        Connection connection = null;
        try {
            connection = getConnection();
            if(connection == null) {
                return;
            }
            connection.setAutoCommit(false);
            for(String s : sql.split("\n")) {
                PreparedStatement statement = connection.prepareStatement(s);
                statement.execute();
                statement.close();
            }
            connection.commit();
        }
        catch(SQLException ex) {
            throw new TransactionException("Could not execute LIMS update query", ex);
        }
        finally {
            try {
                if(connection != null) {
                    connection.setAutoCommit(true);
                }
            } catch (SQLException ignore) {}
        }
    }

    private Connection getConnection() throws SQLException{
        if(connection == null) {
            throw new SQLException(BiocodeUtilities.NOT_CONNECTED_ERROR_MESSAGE);
        }
        return connection;
    }

    public DatabaseMetaData getMetaData() throws SQLException {
        Connection connection = getConnection();
        return connection.getMetaData();
    }

    public Statement createStatement() throws SQLException{
        Connection connection = getConnection();
        Statement statement = connection.createStatement();
        statement.setQueryTimeout(BiocodeService.STATEMENT_QUERY_TIMEOUT);
        return statement;
    }

    public PreparedStatement createStatement(String sql) throws SQLException{
        Connection connection = getConnection();
        PreparedStatement statement = connection.prepareStatement(sql);
        statement.setQueryTimeout(BiocodeService.STATEMENT_QUERY_TIMEOUT);
        return statement;
    }

    public Statement createStatement(int resultSetType, int resultSetConcurrency) throws SQLException{
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
        if(transactionLevel == 0) {
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
        }
        catch(SQLException ex){/*if we can't rollback, let's ignore*/}
        transactionLevel = 0;
    }

    /**
     * Note that this is not thread safe - it is assumed that only one atomic transaction will access the LIMSConnection at any one time
     */
    public void endTransaction() throws SQLException {
        Connection connection = getConnection();
        if(transactionLevel == 0) {
            return;  //we've rolled back our changes by calling rollback() so no commits are necessary
        }
        transactionLevel --;
        if(transactionLevel == 0) {
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
                EXTRACTION_NAME_FIELD,
                EXTRACTION_BARCODE_FIELD,
                SEQUENCE_PROGRESS,
                SEQUENCE_SUBMISSION_PROGRESS,
                ASSEMBLY_TECHNICIAN
        );
    }

    public static Condition[] getFieldConditions(Class fieldClass) {
        if(Integer.class.equals(fieldClass) || Double.class.equals(fieldClass)) {
            return new Condition[] {
                    Condition.EQUAL,
                    Condition.NOT_EQUAL,
                    Condition.GREATER_THAN,
                    Condition.GREATER_THAN_OR_EQUAL_TO,
                    Condition.LESS_THAN,
                    Condition.LESS_THAN_OR_EQUAL_TO
            };
        }
        else if(String.class.equals(fieldClass)) {
            return new Condition[] {
                    Condition.CONTAINS,
                    Condition.EQUAL,
                    Condition.NOT_EQUAL,
                    Condition.NOT_CONTAINS,
                    Condition.STRING_LENGTH_GREATER_THAN,
                    Condition.STRING_LENGTH_GREATER_THAN,
                    Condition.BEGINS_WITH,
                    Condition.ENDS_WITH
            };
        }
        else if(Date.class.equals(fieldClass)) {
            return new Condition[] {
                    Condition.EQUAL,
                    Condition.NOT_EQUAL,
                    Condition.GREATER_THAN,
                    Condition.GREATER_THAN_OR_EQUAL_TO,
                    Condition.LESS_THAN,
                    Condition.LESS_THAN_OR_EQUAL_TO
            };
        }
        else {
            return new Condition[] {
                    Condition.EQUAL,
                    Condition.NOT_EQUAL
            };
        }
    }

    /**
     * This splits up the query because running large queries with lots of OR statements was freezing up the server - better to have the search take a bit longer than crash out and not run at all
     * @param tissueSamples
     * @param callback
     * @param urnsToNotRetrieve
     * @param cancelable
     * @return
     * @throws SQLException
     */
    public List<AnnotatedPluginDocument> getMatchingAssemblyDocumentsForTissues(Query query, List<FimsSample> tissueSamples, RetrieveCallback callback, URN[] urnsToNotRetrieve, Cancelable cancelable) throws SQLException{
        if(tissueSamples == null || tissueSamples.size() == 0) {
            return Collections.emptyList();
        }

        List<AnnotatedPluginDocument> assemblyDocuments = new ArrayList<AnnotatedPluginDocument>();
        for(FimsSample sample : tissueSamples) {
            if(cancelable != null && cancelable.isCanceled()) {
                return Collections.emptyList();
            }
            GeneralUtilities.println("Searching "+sample.getId());
            long currentTime = System.currentTimeMillis();
            assemblyDocuments.addAll(getMatchingAssemblyDocumentsForTissue(query, sample, callback,  urnsToNotRetrieve, cancelable));
            GeneralUtilities.println("Searching "+sample.getId()+" took "+(System.currentTimeMillis()-currentTime)/1000+" seconds");
        }
        return assemblyDocuments;
    }


    public List<AnnotatedPluginDocument> getMatchingAssemblyDocumentsForTissue(Query query, FimsSample tissue, RetrieveCallback callback, URN[] urnsToNotRetrieve, Cancelable cancelable) throws SQLException{
        List<? extends Query> refinedQueries;
        CompoundSearchQuery.Operator operator;

        if(query instanceof BasicSearchQuery) {
            refinedQueries = Collections.emptyList();
            operator = CompoundSearchQuery.Operator.OR;
        }
        else if(query instanceof CompoundSearchQuery) {
            refinedQueries = removeFields(((CompoundSearchQuery)query).getChildren(), Arrays.asList(PLATE_NAME, PLATE_TYPE, PLATE_DATE));
            operator = ((CompoundSearchQuery)query).getOperator();
        }
        else {
            refinedQueries = removeFields(Arrays.asList(query), Arrays.asList(PLATE_NAME, PLATE_TYPE, PLATE_DATE));
            operator = CompoundSearchQuery.Operator.AND;
        }

        if(refinedQueries.size() == 0 && tissue == null) {
            return Collections.emptyList();
        }

        String join;
        switch(operator) {
            case AND:
                join = " AND ";
                break;
            default:
                join = " OR ";
        }

        String sql = "SELECT workflow.locus, assembly.*, extraction.sampleId, extraction.extractionId, extraction.extractionBarcode FROM workflow, assembly, extraction WHERE workflow.id = assembly.workflow AND workflow.extractionId = extraction.id AND ";
        List<String> terms = new ArrayList<String>();
        List<Object> sqlValues = new ArrayList<Object>();
        if(tissue != null) {
            terms.add("extraction.sampleId=?");
            sqlValues.add(tissue.getId());
            sql = sql+"("+StringUtilities.join(" OR ", terms)+")";
            if(refinedQueries.size() > 0) {
                sql = sql + join;
            }
        }

        if(refinedQueries.size() > 0)  {
            sql = sql+"("+queryToSql(refinedQueries, operator, "assembly", sqlValues)+")";
        }

        return getMatchingAssemblyDocuments(null, Arrays.asList(tissue), callback, urnsToNotRetrieve, cancelable, sql, sqlValues);
    }

    public List<AnnotatedPluginDocument> getMatchingAssemblyDocuments(Query query, final Collection<WorkflowDocument> workflows, RetrieveCallback callback, URN[] urnsToNotRetrieve, Cancelable cancelable) throws SQLException{
        List<? extends Query> refinedQueries;
        CompoundSearchQuery.Operator operator;

        if(query instanceof BasicSearchQuery) {
            query = generateAdvancedQueryFromBasicQuery(query);
        }

        if(query instanceof CompoundSearchQuery) {
            refinedQueries = removeFields(((CompoundSearchQuery)query).getChildren(), Arrays.asList(PLATE_NAME, PLATE_TYPE, PLATE_DATE));
            operator = ((CompoundSearchQuery)query).getOperator();
        }
        else {
            refinedQueries = removeFields(Arrays.asList(query), Arrays.asList(PLATE_NAME, PLATE_TYPE, PLATE_DATE));
            operator = CompoundSearchQuery.Operator.AND;
        }

        if(refinedQueries.size() == 0 && (workflows == null || workflows.size() == 0)) {
            return Collections.emptyList();
        }

        List<Query> ultraRefinedQueries = new ArrayList<Query>();
        for(Query q : refinedQueries) {
            if(query instanceof AdvancedSearchQueryTerm) {
                Object[] queryValues = ((AdvancedSearchQueryTerm) q).getValues();
                if(queryValues.length == 0 || queryValues[0].equals("")) {
                    continue;
                }
                ultraRefinedQueries.add(q);
            }
        }

        String sql = "SELECT workflow.locus, assembly.*, extraction.sampleId, extraction.extractionId, extraction.extractionBarcode FROM workflow, assembly, extraction WHERE workflow.id = assembly.workflow AND workflow.extractionId = extraction.id AND ";
        List<String> terms = new ArrayList<String>();
        List<Object> sqlValues = new ArrayList<Object>();
        if(workflows != null && workflows.size() > 0) {
            for(WorkflowDocument workflow : workflows) {
                terms.add("workflow=?");
                sqlValues.add(workflow.getId());
            }
            sql = sql+"("+StringUtilities.join(" OR ", terms)+")";
        }

        if(ultraRefinedQueries.size() > 0)  {
            if(workflows != null && workflows.size() > 0) {
                String join;
                switch(operator) {
                    case AND:
                        join = " AND ";
                        break;
                    default:
                        join = " OR ";
                }
                sql = sql + join;
            }
            sql = sql+"("+queryToSql(ultraRefinedQueries, operator, "assembly", sqlValues)+")";
        }
        printSql(sql, sqlValues);
        return getMatchingAssemblyDocuments(workflows, null, callback, urnsToNotRetrieve, cancelable, sql, sqlValues);
    }

    private static void printSql(String sql, List sqlValues){
        for(Object o : sqlValues) {
            sql = sql.replaceFirst("\\?", ""+o);
        }

        System.out.println(sql);
    }

    private List<AnnotatedPluginDocument> getMatchingAssemblyDocuments(final Collection<WorkflowDocument> workflows, final List<FimsSample> fimsSamples, RetrieveCallback callback, URN[] urnsToNotRetrieve, Cancelable cancelable, String sql, List<Object> sqlValues) throws SQLException {
        if(!BiocodeService.getInstance().isLoggedIn()) {
            return Collections.emptyList();
        }
        PreparedStatement statement = createStatement(sql);
        fillStatement(sqlValues, statement);
        printSql(sql, sqlValues);
        BiocodeUtilities.CancelListeningThread listeningThread = null;
        if(cancelable != null) {
            //todo: listeningThread = new BiocodeUtilities.CancelListeningThread(cancelable, statement);
        }
        if(!isLocal()) {
            statement.setFetchSize(Integer.MIN_VALUE);
        }
        try {
            final ResultSet resultSet = statement.executeQuery();
            List<AnnotatedPluginDocument> resultDocuments = new ArrayList<AnnotatedPluginDocument>();
            final List<String> missingTissueIds = new ArrayList<String>();
            ArrayList<AnnotatedPluginDocument> documentsWithoutFimsData = new ArrayList<AnnotatedPluginDocument>();
            while(resultSet.next()) {
                if(SystemUtilities.isAvailableMemoryLessThan(50)) {
                    statement.cancel();
                    throw new SQLException("Search cancelled due to lack of free memory");
                }
                if(callback != null && callback.isCanceled()) {
                    return Collections.emptyList();
                }
                AnnotatedPluginDocument doc = createAssemblyDocument(resultSet, urnsToNotRetrieve);
                if(doc == null) {
                    continue;
                }
                FimsDataGetter getter = new FimsDataGetter(){
                    public FimsData getFimsData(AnnotatedPluginDocument document) throws DocumentOperationException {
                        try {
                            if(workflows != null) {
                                for(WorkflowDocument workflow : workflows) {
                                    if(workflow.getId() == resultSet.getInt("workflow")) {
                                        return new FimsData(workflow, null, null);
                                    }
                                }
                            }
                            String tissueId = resultSet.getString("sampleId");
                            if(fimsSamples != null) {
                                for(FimsSample sample : fimsSamples) {
                                    if(sample.getId().equals(tissueId)) {
                                        return new FimsData(sample, null, null);
                                    }
                                }
                            }
                            if(!BiocodeService.getInstance().isLoggedIn()) {
                                return null;
                            }
                            FimsSample fimsSample = BiocodeService.getInstance().getActiveFIMSConnection().getFimsSampleFromCache(tissueId);
                            if(fimsSample != null) {
                                return new FimsData(fimsSample, null, null);
                            }
                            else {
                                document.setFieldValue(BiocodeService.getInstance().getActiveFIMSConnection().getTissueSampleDocumentField(), tissueId);
                                missingTissueIds.add(tissueId);
                            }
                        }
                        catch(SQLException ex) {
                            throw new DocumentOperationException("Could not get workflow id from assembly table: "+ex.getMessage());
                        }
                        return null;
                    }
                };
                ArrayList<String> failBlog = new ArrayList<String>();
                AnnotateUtilities.annotateDocument(getter, failBlog, doc);
                if(failBlog.size() == 0) {
                    resultDocuments.add(doc);
                }
                else {
                    documentsWithoutFimsData.add(doc);
                }
                if(callback != null) {
                    callback.add(doc, Collections.<String, Object>emptyMap());
                }
            }

            //annotate with FIMS data if we couldn't before...
            final List<FimsSample> newFimsSamples = BiocodeService.getInstance().getActiveFIMSConnection().getMatchingSamples(missingTissueIds);
            FimsDataGetter fimsDataGetter = new FimsDataGetter() {
                public FimsData getFimsData(AnnotatedPluginDocument document) throws DocumentOperationException {
                    String tissueId = (String)document.getFieldValue(BiocodeService.getInstance().getActiveFIMSConnection().getTissueSampleDocumentField());
                    if(tissueId != null) {
                        for(FimsSample sample : newFimsSamples) {
                            if(sample.getId().equals(tissueId)) {
                                return new FimsData(sample, null, null);
                            }
                        }
                    }
                    return null;
                }
            };
            for(AnnotatedPluginDocument doc : documentsWithoutFimsData) {
                AnnotateUtilities.annotateDocument(fimsDataGetter, new ArrayList<String>(), doc);
                callback.add(doc, Collections.<String, Object>emptyMap());
            }

            if(listeningThread != null) {
                listeningThread.finish();
            }
            return resultDocuments;
        }
        catch (DocumentOperationException e) {
            e.printStackTrace();
            if(e.getCause() != null && e.getCause() instanceof SQLException) {
                throw (SQLException)e.getCause();
            }
            throw new SQLException(e.getMessage());
        } catch (ConnectionException e) {
            e.printStackTrace();
            if(e.getCause() != null && e.getCause() instanceof SQLException) {
                throw (SQLException)e.getCause();
            }
            throw new SQLException(e.getMessage());
        } finally {
            statement.close();
        }
    }

    private AnnotatedPluginDocument createAssemblyDocument(ResultSet resultSet, URN[] urnsToNotRetrieve) throws SQLException{
        String qualities = resultSet.getString("assembly.confidence_scores");
        DefaultNucleotideSequence sequence;
        URN urn = new URN("Biocode", getUrn(), "" + resultSet.getInt("id"));
        if(urnsToNotRetrieve != null) {
            for(URN urnNotToRetrieve : urnsToNotRetrieve) {
                if(urn.equals(urnNotToRetrieve)) {
                    return null;
                }
            }
        }
        String name = resultSet.getString("assembly.extraction_id")+" "+resultSet.getString("workflow.locus");
        if(qualities == null || resultSet.getString("progress") == null || resultSet.getString("progress").toLowerCase().contains("failed")) {
            String consensus = resultSet.getString("consensus");
            String description = "Assembly consensus sequence for "+name;
            java.sql.Date created = resultSet.getDate("date");
            if(consensus == null || created == null) {
                consensus="";
            }
            else if(resultSet.getString("assembly.progress") == null || resultSet.getString("progress").toLowerCase().contains("failed")) {
                consensus = "";
                description = "Sequencing failed for this well";
            }
            consensus = consensus.replace("-","");
            sequence = new DefaultNucleotideSequence(name, description, consensus, new Date(created.getTime()), urn);
        }
        else {
            String sequenceString = resultSet.getString("assembly.consensus");
            sequenceString = sequenceString.replace("-", "");
            NucleotideGraph graph = DefaultNucleotideGraph.createNucleotideGraph(null, null, qualitiesFromString(qualities), sequenceString.length(), 0);
            sequence = new DefaultNucleotideGraphSequence(name, "Assembly consensus sequence for "+name, sequenceString, new Date(resultSet.getDate("date").getTime()), graph, urn);
        }
        AnnotatedPluginDocument doc = DocumentUtilities.createAnnotatedPluginDocument(sequence);
        //todo: add data as fields and notes...
        String notes = resultSet.getString("assembly.notes");
        if(notes != null) {
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
        doc.setFieldValue(LIMSConnection.EXTRACTION_NAME_FIELD, resultSet.getString("extraction.extractionId"));
        doc.setFieldValue(LIMSConnection.EXTRACTION_BARCODE_FIELD, resultSet.getString("extraction.extractionBarcode"));
        return doc;
    }

    private int[] qualitiesFromString(String qualString) {
        String[] values = qualString.split(",");
        int[] result = new int[values.length];
        for(int i=0; i < values.length; i++) {
            result[i] = Integer.parseInt(values[i]);
        }
        return result;
    }
    public List<WorkflowDocument> getMatchingWorkflowDocuments(Query query, Collection<FimsSample> samples, RetrieveCallback callback) throws SQLException{
        return getMatchingWorkflowDocuments(query, samples, callback, callback);
    }

    public List<WorkflowDocument> getMatchingWorkflowDocuments(Query query, Collection<FimsSample> samples, RetrieveCallback callback, Cancelable cancelable) throws SQLException{

        List<? extends Query> refinedQueries;
        CompoundSearchQuery.Operator operator;

        if(query instanceof BasicSearchQuery) {
            query = generateAdvancedQueryFromBasicQuery(query);
        }

        if(query instanceof CompoundSearchQuery) {
            refinedQueries = removeFields(((CompoundSearchQuery)query).getChildren(), Arrays.asList(PLATE_NAME, PLATE_TYPE, PLATE_DATE, SEQUENCE_PROGRESS.getCode(), SEQUENCE_SUBMISSION_PROGRESS.getCode(), ASSEMBLY_TECHNICIAN.getCode()));
            operator = ((CompoundSearchQuery)query).getOperator();
        }
        else {
            refinedQueries = removeFields(Arrays.asList(query), Arrays.asList(PLATE_NAME, PLATE_TYPE, PLATE_DATE, SEQUENCE_PROGRESS.getCode() , SEQUENCE_SUBMISSION_PROGRESS.getCode(), ASSEMBLY_TECHNICIAN.getCode()));
            operator = CompoundSearchQuery.Operator.AND;
        }
        if((samples == null || samples.size() == 0) && refinedQueries.size() == 0) {
            return Collections.emptyList();
        }

        StringBuilder sql = new StringBuilder("SELECT * FROM workflow LEFT JOIN cyclesequencing ON cyclesequencing.workflow = workflow.id " +
                "LEFT JOIN pcr ON pcr.workflow = workflow.id " +
                "LEFT JOIN extraction ON workflow.extractionId = extraction.id " +
                "LEFT JOIN plate ON (plate.id = extraction.plate OR plate.id = pcr.plate OR plate.id = cyclesequencing.plate) "+
                "WHERE ");

        boolean somethingToSearch = false;
        ArrayList<Object> sqlValues = new ArrayList<Object>();
                        
        if(samples != null && samples.size() > 0) {
            somethingToSearch = true;
            sql.append("(");
            FimsSample[] samplesArray = samples.toArray(new FimsSample[samples.size()]);
            for(int i=0; i < samplesArray.length; i++) {
                sql.append(" LOWER(extraction.sampleId)=?");
                sqlValues.add(samplesArray[i].getId());
                if(i != samples.size()-1) {
                    sql.append(" OR");
                }
            }
            sql.append(")");
            if(refinedQueries.size() > 0) {
                sql.append(operator == CompoundSearchQuery.Operator.OR ? " OR " : " AND ");
            }
        }
        if(refinedQueries.size() > 0) {
            somethingToSearch = true;
            if(refinedQueries.size() > 0) {
                somethingToSearch = true;
                sql.append("(");
                sql.append(queryToSql(refinedQueries, operator, "workflow", sqlValues));
                sql.append(")");
            }
        }
        if(!somethingToSearch) {
            return Collections.emptyList();
        }

        //attach the values to the query
        System.out.println(sql.toString());
        PreparedStatement statement = createStatement(sql.toString());
        BiocodeUtilities.CancelListeningThread listeningThread1 = null;
        if(cancelable != null) {
            //todo: listeningThread1 = new BiocodeUtilities.CancelListeningThread(cancelable, statement);
        }
        if(!isLocal()) {
            statement.setFetchSize(Integer.MIN_VALUE);
        }
        int position = 1;
        if(samples != null && samples.size() > 0) {
            for(FimsSample sample : samples) {
                statement.setString(position, sample.getId());
                position++;
            }
        }
        fillStatement(sqlValues, statement);
        ResultSet resultSet = statement.executeQuery();

        Map<Integer, WorkflowDocument> workflowDocs = new HashMap<Integer, WorkflowDocument>();
        int prevWorkflowId = -1;
        while(resultSet.next()) {
            if(SystemUtilities.isAvailableMemoryLessThan(50)) {
                resultSet.close();
                throw new SQLException("Search cancelled due to lack of free memory");
            }
            if(cancelable != null && cancelable.isCanceled()) {
                resultSet.close();
                return Collections.emptyList();
            }
            int workflowId = resultSet.getInt("workflow.id");
            if(callback != null && prevWorkflowId >= 0 && prevWorkflowId != workflowId) {
                WorkflowDocument prevWorkflow = workflowDocs.get(prevWorkflowId);
                if(prevWorkflow != null) {
                    prevWorkflow.sortReactions();
                    callback.add(prevWorkflow, Collections.<String, Object>emptyMap());
                }
            }
            prevWorkflowId = workflowId;
            if(workflowDocs.get(workflowId) != null) {
                workflowDocs.get(workflowId).addRow(resultSet);
            }
            else {
                WorkflowDocument doc = new WorkflowDocument(resultSet);
                workflowDocs.put(workflowId, doc);
            }
        }
        if(prevWorkflowId >= 0) {
            WorkflowDocument prevWorkflow = workflowDocs.get(prevWorkflowId);
            if(prevWorkflow != null && callback != null) {
                prevWorkflow.sortReactions();
                callback.add(prevWorkflow, Collections.<String, Object>emptyMap());
            }
        }
        if(listeningThread1 != null) {
            listeningThread1.finish();
        }
        statement.close();
        return new ArrayList<WorkflowDocument>(workflowDocs.values());
    }

    private void fillStatement(List<Object> sqlValues, PreparedStatement statement) throws SQLException {
        for (int i = 0; i < sqlValues.size(); i++) {
            Object o = sqlValues.get(i);
            if(Integer.class.isAssignableFrom(o.getClass())) {
                statement.setInt(i+1, (Integer)o);
            }
            else if(Double.class.isAssignableFrom(o.getClass())) {
                statement.setDouble(i+1, (Double)o);
            }
            else if(String.class.isAssignableFrom(o.getClass())) {
                statement.setString(i+1, o.toString().toLowerCase());
            }
            else if(Date.class.isAssignableFrom(o.getClass())) {
                statement.setDate(i+1, new java.sql.Date(((Date)o).getTime()));
            }
            else if(Boolean.class.isAssignableFrom(o.getClass())) {
                statement.setBoolean(i+1,(Boolean)o);
            }
            else {
                throw new SQLException("You have a field parameter with an invalid type: "+o.getClass().getCanonicalName());
            }
        }
    }

    private List<? extends Query> removeFields(List<? extends Query> queries, List<String>  codesToIgnore) {
        if(queries == null) {
            return Collections.emptyList();
        }
        List<Query> returnList = new ArrayList<Query>();
        for(Query q : queries) {
            if(q instanceof AdvancedSearchQueryTerm) {
                if(!codesToIgnore.contains(((AdvancedSearchQueryTerm)q).getField().getCode())) {
                    returnList.add(q);
                }
            }
            else if (q != null) {
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
        StringBuilder sql = new StringBuilder();
        String mainJoin;
        switch(operator) {
            case AND:
                mainJoin = "AND";
                break;
            default:
                mainJoin = "OR";
        }
        for (int i = 0; i < queries.size(); i++) {
            if(queries.get(i) instanceof AdvancedSearchQueryTerm) {
                AdvancedSearchQueryTerm q = (AdvancedSearchQueryTerm)queries.get(i);
                QueryTermSurrounder termSurrounder = getQueryTermSurrounder(q);
                String code = q.getField().getCode();
                boolean isBooleanQuery = false;
                if("date".equals(code)) {
                    code = tableName+".date"; //hack for last modified date...
                }
                if(String.class.isAssignableFrom(q.getField().getValueType())) {
                    DocumentField field = q.getField();
                    if(field.isEnumeratedField() && field.getEnumerationValues().length == 2 && field.getEnumerationValues()[0].equals("Yes") && field.getEnumerationValues()[1].equals("No")) {
                        isBooleanQuery = true;
                    }
                    else {
                        code = "LOWER("+code+")";
                    }
                }

                Object[] queryValues = q.getValues();

                //noinspection StringConcatenationInsideStringBufferAppend
                sql.append(" "+ code +" "+ termSurrounder.getJoin() +" ");

                for (Object queryValue : queryValues) {
                    if(isBooleanQuery) {
                        queryValue = queryValue.equals("Yes") ? 1 : 0;
                    }
                    appendValue(inserts, sql, i < queryValues.length - 1, termSurrounder, queryValue, q.getCondition());
                }
                //}
            }
            if(i < queries.size()-1) {
                //noinspection StringConcatenationInsideStringBufferAppend
                sql.append(" "+mainJoin);
            }
        }
        return sql.toString();
    }

    private void appendValue(List<Object> inserts, StringBuilder sql, boolean appendAnd, QueryTermSurrounder termSurrounder, Object value, Condition condition) {
        String valueString = valueToString(value);
        //valueString = termSurrounder.getPrepend()+valueString+termSurrounder.getAppend();
        if(Date.class.isAssignableFrom(value.getClass()) && (condition == Condition.LESS_THAN_OR_EQUAL_TO || condition == Condition.GREATER_THAN)) { //hack to make these conditions work...
            value = new Date(((Date)value).getTime()+86300000);
        }
        if(value instanceof String) {
            inserts.add(termSurrounder.getPrepend()+valueString+termSurrounder.getAppend());
        }
        else {
            inserts.add(value);
        }
        sql.append("?");
        if(appendAnd) {
            sql.append(" AND ");
        }
    }

    private static String valueToString(Object value) {
        if(value instanceof Date) {
            DateFormat format = new SimpleDateFormat("yyyy-mm-dd kk:mm:ss");
            return format.format((Date)value);
        }
        return value.toString();
    }

    public List<PlateDocument> getMatchingPlateDocuments(Query query, List<WorkflowDocument> workflowDocuments, RetrieveCallback callback) throws SQLException{
        return getMatchingPlateDocuments(query, workflowDocuments, callback, callback);
    }

    @SuppressWarnings({"MismatchedQueryAndUpdateOfCollection"})
    public List<PlateDocument> getMatchingPlateDocuments(Query query, List<WorkflowDocument> workflowDocuments, RetrieveCallback callback, Cancelable cancelable) throws SQLException{
        Connection connection = getConnection();
        Connection connection2 = isLocal ? connection : this.connection2;
        if(connection == null || connection2 == null) {
            throw new SQLException("You are not logged in");
        }

        List<? extends Query> refinedQueries;
        CompoundSearchQuery.Operator operator;
        Set<Integer> plateIds = new HashSet<Integer>();
        long startTime = System.currentTimeMillis();

        if(query instanceof BasicSearchQuery) {
            query = generateAdvancedQueryFromBasicQuery(query);
        }

        List<String> fieldsToRemove = Arrays.asList(WORKFLOW_NAME, WORKFLOW_DATE, LOCUS, EXTRACTION_ID, EXTRACTION_BARCODE, SEQUENCE_PROGRESS.getCode(), SEQUENCE_SUBMISSION_PROGRESS.getCode(), ASSEMBLY_TECHNICIAN.getCode());
        if(query == null) {
            refinedQueries = Collections.emptyList();
            operator = CompoundSearchQuery.Operator.AND;
        }
        else if(query instanceof CompoundSearchQuery) {
            refinedQueries = removeFields(((CompoundSearchQuery)query).getChildren(), fieldsToRemove);
            operator = ((CompoundSearchQuery)query).getOperator();
        }
        else {
            refinedQueries = removeFields(Arrays.asList(query), fieldsToRemove);
            operator = CompoundSearchQuery.Operator.AND;
        }
        if((workflowDocuments == null || workflowDocuments.size() == 0) && refinedQueries.size() == 0) {
            return Collections.emptyList();
        }
        StringBuilder sql = new StringBuilder("SELECT * FROM plate LEFT JOIN cyclesequencing ON cyclesequencing.plate = plate.id " +
                "LEFT JOIN pcr ON pcr.plate = plate.id " +
                "LEFT JOIN workflow ON (workflow.id = pcr.workflow OR workflow.id = cyclesequencing.workflow) " +
                "LEFT JOIN extraction ON (extraction.plate = plate.id OR extraction.id = workflow.extractionId) " +            
                "WHERE");
        ArrayList<Object> sqlValues = new ArrayList<Object>();

        if (workflowDocuments != null) {
            for(WorkflowDocument doc : workflowDocuments) {
                for(int i=0; i < doc.getNumberOfParts(); i++) {
                    WorkflowDocument.ReactionPart p = (WorkflowDocument.ReactionPart)doc.getPart(i);
                    Reaction reaction = p.getReaction();
                    plateIds.add(reaction.getPlateId());
                }
            }
        }
        if(plateIds.size() > 0) {
            sql.append(" (");
            for (Iterator<Integer> it = plateIds.iterator(); it.hasNext();) {
                Integer intg = it.next();
                //noinspection StringConcatenationInsideStringBufferAppend
                sql.append(" plate.id=" + intg);
                if(it.hasNext()) {
                    sql.append(" OR");
                }
            }
            sql.append(")");
        }
        if(refinedQueries.size() > 0) {
            if(plateIds.size() > 0) {
                sql.append(operator == CompoundSearchQuery.Operator.AND ? " AND (" : " OR (");
            }
            else{
                sql.append(" (");
            }

            sql.append(queryToSql(refinedQueries, operator, "plate", sqlValues));

            sql.append(")");
        }
        System.out.println(sql.toString());
        PreparedStatement statement = connection2.prepareStatement(sql.toString());
        BiocodeUtilities.CancelListeningThread listeningThread = null;
        if(cancelable != null) {
            //todo: listeningThread = new BiocodeUtilities.CancelListeningThread(cancelable, statement);
        }
        if(!isLocal()) {
            statement.setFetchSize(Integer.MIN_VALUE);
        }

        fillStatement(sqlValues, statement);
        System.out.println("EXECUTING PLATE QUERY");

        ResultSet resultSet = statement.executeQuery();
        final StringBuilder totalErrors = new StringBuilder("");
        Map<Integer, Plate> plateMap = new HashMap<Integer, Plate>();
        List<ExtractionReaction> extractionReactions = new ArrayList<ExtractionReaction>();
        List<PCRReaction> pcrReactions = new ArrayList<PCRReaction>();
        List<CycleSequencingReaction> cyclesequencingReactions = new ArrayList<CycleSequencingReaction>();
        List<Integer> returnedPlateIds = new ArrayList<Integer>();
        System.out.println("Creating Reactions...");
        int previousId = -1;
        while(resultSet.next()) {
            if(SystemUtilities.isAvailableMemoryLessThan(50)) {
                resultSet.close();
                throw new SQLException("Search cancelled due to lack of free memory");
            }
            if(cancelable != null && cancelable.isCanceled()) {
                resultSet.close();
                return Collections.emptyList();
            }

            Plate plate;
            int plateId = resultSet.getInt("plate.id");

            if(previousId >= 0 && previousId != plateId) {
                final Plate prevPlate = plateMap.get(previousId);
                if(prevPlate != null) {
                    Runnable runnable = new Runnable() {
                        public void run() {
                            prevPlate.initialiseReactions();
                        }
                    };
                    ThreadUtilities.invokeNowOrWait(runnable);
                    String error = checkReactions(prevPlate);
                    if(error != null) {
                        //noinspection StringConcatenationInsideStringBufferAppend
                        totalErrors.append(error+"\n");
                    }
                    System.out.println("Adding "+prevPlate.getName());
                    if(callback != null) {
                        callback.add(new PlateDocument(prevPlate), Collections.<String, Object>emptyMap());
                    }
                    plateMap.put(previousId, prevPlate);
                }
            }
            previousId = plateId;

            if(plateMap.get(plateId) == null) {
                returnedPlateIds.add(plateId);
                plate = new Plate(resultSet);
                plateMap.put(plate.getId(), plate);
            }
            else {
                plate = plateMap.get(plateId);
            }
            Reaction reaction = plate.addReaction(resultSet);
            if(reaction == null) {
                //do nothing
            }
            else if(reaction instanceof ExtractionReaction) {
                extractionReactions.add((ExtractionReaction)reaction);
            }
            else if(reaction instanceof PCRReaction) {
                pcrReactions.add((PCRReaction)reaction);
            }
            else if(reaction instanceof CycleSequencingReaction) {
                cyclesequencingReactions.add((CycleSequencingReaction)reaction);
            }
        }
        statement.close();
        if(previousId >= 0) {
            Plate prevPlate = plateMap.get(previousId);
            if(prevPlate != null) {
                prevPlate.initialiseReactions();
                String error = checkReactions(prevPlate);
                if(error != null) {
                    //noinspection StringConcatenationInsideStringBufferAppend
                    totalErrors.append(error+"\n");
                }
                System.out.println("Adding "+prevPlate.getName());
                if (callback != null) {
                    callback.add(new PlateDocument(prevPlate), Collections.<String, Object>emptyMap());
                }

                plateMap.put(previousId, prevPlate);
                
            }
        }

//        if(extractionReactions.size() > 0) {
//            System.out.println("Checking extractions");
//            String extractionErrors = extractionReactions.get(0).areReactionsValid(extractionReactions, null, true);
//            if(extractionErrors != null) {
//                totalErrors.append(extractionErrors+"\n");
//            }
//        }
//        if(pcrReactions.size() > 0) {
//            System.out.println("Checking PCR's");
//            String pcrErrors = pcrReactions.get(0).areReactionsValid(pcrReactions, null, true);
//            if(pcrErrors != null) {
//                totalErrors.append(pcrErrors+"\n");
//            }
//        }
//        if(cyclesequencingReactions.size() > 0) {
//            System.out.println("Checking Cycle Sequencing's...");
//            String cyclesequencingErrors = cyclesequencingReactions.get(0).areReactionsValid(cyclesequencingReactions, null, true);
//            if(cyclesequencingErrors != null) {
//                totalErrors.append(cyclesequencingErrors+"\n");
//            }
//        }
        if(totalErrors.length() > 0) {
            Runnable runnable = new Runnable() {
                public void run() {
                    if(totalErrors.toString().contains("connection")) {
                        Dialogs.showMoreOptionsDialog(new Dialogs.DialogOptions(new String[] {"OK"}, "Connection Error"), "There was an error connecting to the server.  Try logging out and logging in again.", totalErrors.toString());
                    }
                    else {
                        Dialogs.showMessageDialog("Geneious has detected the following possible errors in your database.  Please contact your system administrator for asistance.\n\n"+totalErrors, "Database errors detected", null, Dialogs.DialogIcon.WARNING);
                    }
                }
            };
            ThreadUtilities.invokeNowOrLater(runnable);
        }

        System.out.println("Creating plate documents");
        List<PlateDocument> docs = new ArrayList<PlateDocument>();
        System.out.println("Getting GEL images");
        //getGelImagesForPlates(plateMap.values());   //we are only downloading these when the user wants to view them now...
        for(Plate plate : plateMap.values()) {
            docs.add(new PlateDocument(plate));
        }
        int time = (int)(System.currentTimeMillis()-startTime)/1000;
        System.out.println("done in "+time+" seconds!");

        if(listeningThread != null) {
            listeningThread.finish();
        }
        return docs;

    }

    private Query generateAdvancedQueryFromBasicQuery(Query query) {
        String value = ((BasicSearchQuery)query).getSearchText();
        List<DocumentField> searchFields = getSearchAttributes();
        List<Query> queryTerms = new ArrayList<Query>();
        for(DocumentField field : searchFields) {
            if(String.class.isAssignableFrom(field.getValueType())) {
                if(field.isEnumeratedField()) {
                    boolean hasValue = false;
                    for(String s : field.getEnumerationValues()) {
                        if(s.equalsIgnoreCase(value)) {
                            hasValue = true;
                        }
                    }
                    if(!hasValue)
                        continue;
                }
                queryTerms.add(Query.Factory.createFieldQuery(field, Condition.CONTAINS, value));
            }
        }
        query = Query.Factory.createOrQuery(queryTerms.toArray(new Query[queryTerms.size()]), Collections.<String, Object>emptyMap());
        return query;
    }

    private String checkReactions(Plate plate) {
        System.out.println("Checking "+plate.getName());
        List<Reaction> reactions = new ArrayList<Reaction>();
        for(Reaction r : plate.getReactions()) {
            if(r != null) {
                reactions.add(r);
            }
        }
        return reactions.get(0).areReactionsValid(reactions, null, true);
    }

    public void getGelImagesForPlates(Collection<Plate> plates) throws SQLException {
        List<Integer> plateIds = new ArrayList<Integer>();
        for(Plate plate : plates) {
            plateIds.add(plate.getId());
        }

        Map<Integer, List<GelImage>> gelimages = getGelImages(plateIds);
        for(Plate plate : plates) {
            List<GelImage> gelimagesForPlate = gelimages.get(plate.getId());
            if(gelimagesForPlate != null) {
                plate.setImages(gelimagesForPlate);
            }
        }
    }

    public Map<String, Reaction> getExtractionReactions(List<Reaction> sourceReactions) throws SQLException{
        if(sourceReactions == null || sourceReactions.size() == 0) {
            return Collections.emptyMap();
        }
        StringBuilder sql = new StringBuilder("SELECT plate.name, plate.size, extraction.* FROM extraction, plate WHERE plate.id = extraction.plate AND (");
        for (int i=0; i < sourceReactions.size(); i++) {
            sql.append("extractionId=?");
            if (i < sourceReactions.size() - 1) {
                sql.append(" OR ");
            }
        }
        sql.append(")");
        PreparedStatement statement = createStatement(sql.toString());
        for (int i=0; i < sourceReactions.size(); i++) {
            statement.setString(i+1, sourceReactions.get(i).getExtractionId());
        }
        ResultSet resultSet = statement.executeQuery();
        Map<String, Reaction> reactions = new HashMap<String, Reaction>();
        while(resultSet.next()) {
            ExtractionReaction reaction = new ExtractionReaction(resultSet);
            reactions.put(reaction.getExtractionId(), reaction);
        }
        return reactions;
    }

    private Map<Integer, List<GelImage>> getGelImages(Collection<Integer> plateIds) throws SQLException{
        if(plateIds == null || plateIds.size() == 0) {
            return Collections.emptyMap();
        }
        StringBuilder sql = new StringBuilder("SELECT * FROM gelimages WHERE (");
        for (Iterator<Integer> it = plateIds.iterator(); it.hasNext();) {
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
        while(resultSet.next()) {
            GelImage image = new GelImage(resultSet);
            List<GelImage> imageList;
            List<GelImage> existingImageList = map.get(image.getPlate());
            if(existingImageList != null) {
                imageList = existingImageList;
            }
            else {
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
        switch(query.getCondition()) {
                case EQUAL:
                    join = "=";
                    break;
                case APPROXIMATELY_EQUAL:
                    join = "LIKE";
                    break;
                case BEGINS_WITH:
                    join = "LIKE";
                    append="%";
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

    public Set<String> getAllExtractionIdsStartingWith(List<String> tissueIds) throws SQLException{
        List<String> queries = new ArrayList<String>();
        //noinspection UnusedDeclaration
        for(String s : tissueIds) {
            queries.add("extractionId LIKE ?");
        }

        String sql = "SELECT extractionId FROM extraction WHERE "+StringUtilities.join(" OR ", queries);

        PreparedStatement statement = createStatement(sql);
        for (int i = 0; i < tissueIds.size(); i++) {
            statement.setString(i+1, tissueIds.get(i)+"%");
        }

        ResultSet set = statement.executeQuery();
        Set<String> result = new HashSet<String>();

        while(set.next()) {
            result.add(set.getString("extractionId"));
        }


        return result;
    }

    public Map<String, ExtractionReaction> getExtractionsFromBarcodes(List<String> barcodes) throws SQLException{
        if(barcodes.size() == 0) {
            System.out.println("empty!");
            return Collections.emptyMap();
        }
         StringBuilder sql = new StringBuilder("SELECT * FROM extraction "+
                "LEFT JOIN plate ON plate.id = extraction.plate "+
                "WHERE (");

        List<String> queryParams = new ArrayList<String>();
        //noinspection UnusedDeclaration
        for(String barcode : barcodes) {
            queryParams.add("extraction.extractionBarcode = ?");
        }

        sql.append(StringUtilities.join(" OR ", queryParams));

        sql.append(")");

        PreparedStatement statement = createStatement(sql.toString());

        for (int i = 0; i < barcodes.size(); i++) {
            String barcode = barcodes.get(i);
            statement.setString(i+1, barcode);
        }

        ResultSet r = statement.executeQuery();

        Map<String, ExtractionReaction> results = new HashMap<String, ExtractionReaction>();
        while(r.next()) {
            ExtractionReaction reaction = new ExtractionReaction(r);
            results.put(""+reaction.getFieldValue("extractionBarcode"), reaction);
        }

        return results;
    }


    private static class QueryTermSurrounder{
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

}
