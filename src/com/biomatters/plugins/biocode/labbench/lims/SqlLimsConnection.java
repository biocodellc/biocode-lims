package com.biomatters.plugins.biocode.labbench.lims;

import com.biomatters.geneious.publicapi.components.Dialogs;
import com.biomatters.geneious.publicapi.databaseservice.*;
import com.biomatters.geneious.publicapi.documents.*;
import com.biomatters.geneious.publicapi.documents.sequence.NucleotideSequenceDocument;
import com.biomatters.geneious.publicapi.plugin.DocumentSelectionOption;
import com.biomatters.geneious.publicapi.plugin.Geneious;
import com.biomatters.geneious.publicapi.plugin.Options;
import com.biomatters.geneious.publicapi.utilities.StringUtilities;
import com.biomatters.geneious.publicapi.utilities.SystemUtilities;
import com.biomatters.geneious.publicapi.utilities.ThreadUtilities;
import com.biomatters.plugins.biocode.BiocodeUtilities;
import com.biomatters.plugins.biocode.labbench.*;
import com.biomatters.plugins.biocode.labbench.plates.GelImage;
import com.biomatters.plugins.biocode.labbench.plates.Plate;
import com.biomatters.plugins.biocode.labbench.reaction.*;
import com.biomatters.plugins.biocode.utilities.SqlUtilities;
import jebl.util.Cancelable;
import jebl.util.ProgressListener;

import javax.sql.DataSource;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.net.URLClassLoader;
import java.sql.*;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.Date;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * An SQL based {@link LIMSConnection}
 *
 * @author Matthew Cheung
 * @version $Id$
 *          <p/>
 *          Created on 1/04/14 4:45 PM
 */
public abstract class SqlLimsConnection extends LIMSConnection {

    @Override
    public abstract PasswordOptions getConnectionOptions();

    @Override
    public abstract boolean isLocal();

    @Override
    public abstract String getUsername();

    public abstract String getSchema();

    abstract javax.sql.DataSource connectToDb(Options connectionOptions) throws ConnectionException;

    private DataSource dataSource;

    public DataSource getDataSource() {
        return dataSource;
    }

    @Override
    protected void _connect(PasswordOptions options) throws ConnectionException {
        LimsConnectionOptions allLimsOptions = (LimsConnectionOptions) options;
        PasswordOptions selectedLimsOptions = allLimsOptions.getSelectedLIMSOptions();
        dataSource = connectToDb(selectedLimsOptions);
        ConnectionWrapper connection = null;
        try {
            connection = getConnection();
            String errorMessage = verifyDatabaseVersionAndUpgradeIfNecessary(connection);
            if (errorMessage != null) {
                throw new ConnectionException(errorMessage);
            }
        } catch (SQLException e) {
            throw new ConnectionException(e.getMessage(), e);
        } finally {
            returnConnection(connection);
        }
    }

    private Connection legacyConnection;
    @Override
    protected synchronized Connection getConnectionInternal() throws SQLException {
        if(legacyConnection == null) {
            // By pass the new way of getting connections.  Get one directly from the pool.
            legacyConnection = dataSource.getConnection();
        }
        return legacyConnection;
    }


    ThreadLocal<ConnectionWrapper> connectionForThread = new ThreadLocal<ConnectionWrapper>();

    /**
     * @return A {@link com.biomatters.plugins.biocode.labbench.lims.SqlLimsConnection.ConnectionWrapper} from the
     * connection pool.  Should be returned after use with {@link #returnConnection(com.biomatters.plugins.biocode.labbench.lims.SqlLimsConnection.ConnectionWrapper)}
     * 
     * @throws SQLException if the connection could not be established
     */
    protected ConnectionWrapper getConnection() throws SQLException {
        ConnectionWrapper toReturn = connectionForThread.get();
        if (toReturn == null || toReturn.isClosed()) {
            toReturn = new ConnectionWrapper(dataSource.getConnection());
            connectionForThread.set(toReturn);
        }
        synchronized (connectionCounts) {
            Integer current = connectionCounts.get(toReturn);
            if(current == null) {
                current = 1;
            } else {
                current = current + 1;
            }
            connectionCounts.put(toReturn, current);
        }
        return toReturn;
    }
    
    private final Map<ConnectionWrapper, Integer> connectionCounts = new HashMap<ConnectionWrapper, Integer>();
    protected void returnConnection(ConnectionWrapper connection) {
        synchronized (connectionCounts) {
            Integer current = connectionCounts.get(connection);
            if(current == null) {
                ConnectionWrapper.closeConnection(connection);
            } else {
                current = current - 1;
                connectionCounts.put(connection, current);
                if (current <= 0) {
                    ConnectionWrapper.closeConnection(connection);
                }
            }
        }
    }

    public void disconnect() {
        try {
            Class dataSourceClass = dataSource.getClass();
            dataSourceClass.getDeclaredMethod("close").invoke(dataSource);
        } catch (Exception e) {
            e.printStackTrace();
        }

        //we used to explicitly close the SQL connection, but this was causing crashes if the user logged out while a query was in progress.
        //now we remove all references to it and let the garbage collector close it when the queries have finished.
        if(legacyConnection != null) {
            legacyConnection = null;
        }
        dataSource = null;
        serverUrn = null;
    }

    private List<FailureReason> failureReasons = Collections.emptyList();

    public List<FailureReason> getPossibleFailureReasons() {
        return failureReasons;
    }

    public void doAnyExtraInitialization() throws DatabaseServiceException {
        ConnectionWrapper connection = null;
        try {
            connection = getConnection();
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
            throw new DatabaseServiceException(e, "Failed to initialize database: " + e.getMessage(), false);
        } finally {
            returnConnection(connection);
        }
    }

    /**
     * Creates a {@link org.apache.commons.dbcp.BasicDataSource} using a custom ClassLoader.
     * <p/>
     * <b>Note</b>:This method is required because there is an older version of commons-dbcp in Geneious core class loader.  This
     * means the class uses that class loader when looking for the JDBC driver class and cannot access the MySQL driver
     * bundled in the Biocode plugin.
     * <p/>
     * We are unable to make use of {@link org.apache.commons.dbcp.BasicDataSource#setDriverClassLoader(ClassLoader)}
     * because the version that is part of Geneious core is version 1.1 and does not have that method.
     * </p>
     * So we are forced to create a custom class loader that only has access to the plugin classes and libraries.
     *
     * @param connectionUrl The URL to connect to
     * @param username
     *@param password @return A {@link javax.sql.DataSource} for the specified parameters.
     * @throws com.biomatters.plugins.biocode.labbench.ConnectionException
     */
    public static DataSource createBasicDataSource(String connectionUrl, Driver driver, String username, String password) throws ConnectionException {
        ClassLoader pluginClassLoader = SqlLimsConnection.class.getClassLoader();
        if(pluginClassLoader instanceof URLClassLoader) {
            // We need DBCP including dependencies as well as the two JDBC drivers.
            // See http://commons.apache.org/proper/commons-dbcp/dependencies.html for dependencies.
            List<String> required = Arrays.asList(
                    "commons-dbcp-1.4",
                    "commons-logging-1.1.1",
                    "commons-pool-1.6",
                    "geronimo-jta_1.1_spec",
                    "mysql-connector-java-5.1.6",
                    "hsqldb-2.3.0"
            );
            List<URL> urlsOfJar = new ArrayList<URL>();
            for (URL url : ((URLClassLoader) pluginClassLoader).getURLs()) {
                for (String toCheckAgainst : required) {
                    if(url.toString().contains(toCheckAgainst)) {
                        urlsOfJar.add(url);
                    }
                }
            }
            ClassLoader bootstrapClassLoader = ClassLoader.getSystemClassLoader().getParent();
            if(bootstrapClassLoader == null) {
                throw new IllegalStateException("Expected system class loader to have a parent");
            }
            // We specify the parent class loader of our new one as the bootstrap classloader so it wont' be able to load
            // Geneious core classes.
            URLClassLoader classLoaderForPluginLibsOnly = new URLClassLoader(urlsOfJar.toArray(new URL[1]), bootstrapClassLoader);
            try {
                Class<?> dataSourceClass = classLoaderForPluginLibsOnly.loadClass("org.apache.commons.dbcp.BasicDataSource");
                DataSource dataSource = (DataSource)dataSourceClass.newInstance();
                // We have to use reflection here because we can't cast the class we created to the one loaded by Geneious core
                dataSourceClass.getDeclaredMethod("setDriverClassName", String.class).invoke(dataSource, driver.getClass().getName());
                dataSourceClass.getDeclaredMethod("setUrl", String.class).invoke(dataSource, connectionUrl);
                if(username != null) {
                    dataSourceClass.getDeclaredMethod("setUsername", String.class).invoke(dataSource, username);
                }
                if(password != null) {
                    dataSourceClass.getDeclaredMethod("setPassword", String.class).invoke(dataSource, password);
                }

                if(Geneious.isHeadless()) {
                    dataSourceClass.getDeclaredMethod("setMaxActive", int.class).invoke(dataSource, 25);
                }
                return dataSource;
            } catch (ClassNotFoundException e) {
                throw new ConnectionException("Failed to load data source. Missing library.", e);
            } catch (InstantiationException e) {
                throw new ConnectionException("Cannot construct BasicDataSource", e);
            } catch (IllegalAccessException e) {
                throw new ConnectionException("Failed to load data source.", e);
            } catch (NoSuchMethodException e) {
                throw new ConnectionException("Failed to load data source.", e);
            } catch (InvocationTargetException e) {
                throw new ConnectionException("Failed to load data source.", e);
            }
        } else {
            throw new IllegalStateException("Expected plugin class loader to be a URLClassLoader, was " + pluginClassLoader.getClass().getSimpleName());
        }
    }

    /**
     * @return true if this implementation supports automatically upgrading the database
     */
    protected abstract boolean canUpgradeDatabase();

    /**
     * Upgrade the database schema so it is the current version.  Must be overridden if implementations return true from
     * {@link #canUpgradeDatabase()}
     *
     * @param currentVersion
     * @throws SQLException if a database problem occurs during the upgrade
     */
    protected void upgradeDatabase(String currentVersion) throws ConnectionException {
        if (canUpgradeDatabase()) {
            throw new UnsupportedOperationException("LIMSConnection implementations should override upgradeDatabase() when returning true from canUpgradeDatabase()");
        } else {
            throw new UnsupportedOperationException(getClass().getSimpleName() + " does not implement upgradeDatabase()");
        }
    }


    /**
     * @return an error message or null if everything is OK
     * @throws ConnectionException
     */
    private String verifyDatabaseVersionAndUpgradeIfNecessary(ConnectionWrapper connection) throws ConnectionException {
        try {
            ResultSet resultSet = connection.executeQuery("SELECT * FROM databaseversion LIMIT 1");
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

    protected String getFullVersionStringFromDatabase() throws SQLException, ConnectionException {
        ConnectionWrapper connection = null;
        try {
            connection = getConnection();
            ResultSet tableSet = connection.connection.getMetaData().getTables(null, null, "%", null);
            boolean hasPropertiesTable = false;
            while (tableSet.next()) {
                String tableName = tableSet.getString("TABLE_NAME");
                if (tableName.equalsIgnoreCase("properties")) {
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
        } finally {
            returnConnection(connection);
        }
    }

    public LimsSearchResult getMatchingDocumentsFromLims(Query query, Collection<String> tissueIdsToMatch, RetrieveCallback callback) throws DatabaseServiceException {
        LimsSearchResult result = new LimsSearchResult();

        Boolean downloadTissues = BiocodeService.isDownloadTissues(query);
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

        QueryPart extractionPart = getQueryForTable("extraction", tableToTerms, operator);
        if (extractionPart != null) {
            sqlValues.addAll(extractionPart.parameters);
        }

        QueryPart workflowPart = getQueryForTable("workflow", tableToTerms, operator);
        if (workflowPart != null) {
            sqlValues.addAll(workflowPart.parameters);
        }


        QueryPart platePart = getQueryForTable("plate", tableToTerms, operator);
        if (platePart != null) {
            sqlValues.addAll(platePart.parameters);
        }

        QueryPart assemblyPart = getQueryForTable("assembly", tableToTerms, operator);
        if (assemblyPart != null) {
            sqlValues.addAll(assemblyPart.parameters);
        }

        boolean onlySearchingOnFIMSFields = workflowPart == null && extractionPart == null && platePart == null && assemblyPart == null;
        if (!downloadWorkflows && !downloadPlates && !downloadSequences) {
            if (!downloadTissues || onlySearchingOnFIMSFields) {
                return result;
            }
        }
        boolean searchedForSamplesButFoundNone = tissueIdsToMatch != null && tissueIdsToMatch.isEmpty();  // samples == null when doing a browse query
        if (searchedForSamplesButFoundNone && (operator == CompoundSearchQuery.Operator.AND ||
                (operator == CompoundSearchQuery.Operator.OR && onlySearchingOnFIMSFields))
        ) {
            return result;
        }

        StringBuilder workflowQuery = constructLimsQueryString(
                tissueIdsToMatch, operator,
                workflowPart, extractionPart, platePart, assemblyPart);

        ConnectionWrapper connection = null;
        WorkflowsAndPlatesQueryResult queryResult;
        PreparedStatement preparedStatement = null;
        try {
            connection = getConnection();
            System.out.println("Running LIMS query:");
            System.out.print("\t");
            SqlUtilities.printSql(workflowQuery.toString(), sqlValues);
            preparedStatement = connection.prepareStatement(workflowQuery.toString());
            SqlUtilities.fillStatement(sqlValues, preparedStatement);

            long start = System.currentTimeMillis();
            ResultSet resultSet = preparedStatement.executeQuery();
            System.out.println("\tTook " + (System.currentTimeMillis() - start) + "ms to do LIMS query");

            boolean needTissues = downloadTissues || downloadSequences;
            boolean needWorkflows = downloadWorkflows || downloadSequences;
            queryResult = createPlateAndWorkflowsFromResultSet(connection,
                    callback != null ? callback : ProgressListener.EMPTY, resultSet,
                    needTissues, needWorkflows, downloadPlates);
        } catch (SQLException e) {
            throw new DatabaseServiceException(e, e.getMessage(), false);
        } finally {
            SqlUtilities.cleanUpStatements(preparedStatement);
            returnConnection(connection);
        }
        result.tissueSamples.addAll(queryResult.tissueSamples);

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
                callback.add(plateDocument, Collections.<String, Object>emptyMap());
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
        appendSetOfQuestionMarks(countingQuery, plateMap.size());
        countingQuery.append(" GROUP BY cyclesequencing.id");
        PreparedStatement getCount = null;
        ConnectionWrapper connection = null;
        try {
            connection = getConnection();
            getCount = connection.prepareStatement(countingQuery.toString());
            SqlUtilities.fillStatement(plateIds, getCount);
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
            returnConnection(connection);
        }
    }

    /**
     * Builds a LIMS SQL query from {@link Query}.  Can be used to create workflows and a list of plates that match
     * the query.
     *  @param tissueIdsToMatch          The samples to match
     * @param operator                  The {@link com.biomatters.geneious.publicapi.databaseservice.CompoundSearchQuery.Operator} to use for the query
     * @param workflowQueryConditions   Conditions to search workflow on
     * @param extractionQueryConditions Conditions to search extraction on
     * @param plateQueryConditions      Conditions to search plate on
     * @param assemblyQueryConditions   Conditions to search assembly on       @return A SQL string that can be used to query the MySQL LIMS
     */
    private StringBuilder constructLimsQueryString(Collection<String> tissueIdsToMatch, CompoundSearchQuery.Operator operator,
                                                   QueryPart workflowQueryConditions, QueryPart extractionQueryConditions,
                                                   QueryPart plateQueryConditions, QueryPart assemblyQueryConditions) {
        String operatorString = operator == CompoundSearchQuery.Operator.AND ? " AND " : " OR ";
        StringBuilder whereConditionForOrQuery = new StringBuilder();

        boolean filterOnTissues = tissueIdsToMatch != null && !tissueIdsToMatch.isEmpty();

        StringBuilder queryBuilder = new StringBuilder(
                "SELECT extraction.sampleId, workflow.id, plate.id, assembly.id, assembly.date FROM ");
        StringBuilder conditionBuilder = operator == CompoundSearchQuery.Operator.AND ? queryBuilder : whereConditionForOrQuery;

        boolean searchExtractionTable = filterOnTissues || extractionQueryConditions != null;
        if (searchExtractionTable) {
            if (operator == CompoundSearchQuery.Operator.AND) {
                queryBuilder.append("(SELECT * FROM extraction WHERE ");
            }

            if(filterOnTissues) {
                String sampleColumn = isLocal() ? "LOWER(sampleId)" : "sampleId";  // MySQL is case insensitive by default
                conditionBuilder.append(sampleColumn).append(" IN ");
                SqlUtilities.appendSetOfQuestionMarks(conditionBuilder, tissueIdsToMatch.size());
            }
            if(filterOnTissues && extractionQueryConditions != null) {
                conditionBuilder.append(operatorString);
            }
            if(extractionQueryConditions != null) {
                conditionBuilder.append("(").append(extractionQueryConditions).append(")");
            }

            if(operator == CompoundSearchQuery.Operator.AND) {
                queryBuilder.append(")");
            }
            queryBuilder.append(" extraction ");
        } else {
            queryBuilder.append(" extraction ");
        }
        queryBuilder.append(getJoinStringIncludingSpaces(operator, workflowQueryConditions));
        queryBuilder.append("workflow ON extraction.id = workflow.extractionId");
        if (workflowQueryConditions != null) {
            if (searchExtractionTable) {
                conditionBuilder.append(operatorString);
            } else if (operator == CompoundSearchQuery.Operator.AND) {
                conditionBuilder.append(" AND ");
            }
            conditionBuilder.append("(").append(workflowQueryConditions).append(")");
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
        queryBuilder.append(getJoinStringIncludingSpaces(operator, assemblyQueryConditions)).
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

    private String getJoinStringIncludingSpaces(CompoundSearchQuery.Operator operator, QueryPart conditions) {
        return operator == CompoundSearchQuery.Operator.AND && conditions != null ? " INNER JOIN " : " LEFT OUTER JOIN ";
    }

    private String constructPlateQuery(Collection<Integer> plateIds) {
        StringBuilder queryBuilder = new StringBuilder("SELECT E.id, E.extractionId, " +
                "plate.*, extraction.*, workflow.*, pcr.*, cyclesequencing.*, " +
                "assembly.id, assembly.progress, assembly.date, assembly.notes, assembly.failure_reason, assembly.failure_notes FROM ");
        // We join plate twice because HSQL doesn't let us use aliases.  The way the query is written means the select would produce a derived table.
        queryBuilder.append("(SELECT * FROM plate WHERE id IN ");
        appendSetOfQuestionMarks(queryBuilder, plateIds.size());
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
        queryBuilder.append("CASE WHEN pcr.workflow IS NOT NULL THEN pcr.workflow ELSE ");
        queryBuilder.append("CASE WHEN W.id IS NOT NULL THEN W.id ELSE ");
        queryBuilder.append("cyclesequencing.workflow END ");
        queryBuilder.append("END ");

        queryBuilder.append("LEFT OUTER JOIN extraction E ON E.id = " +
                "CASE WHEN extraction.id IS NULL THEN workflow.extractionId ELSE extraction.id END ");  // So we get extraction ID for pcr and cyclesequencing reactions

        queryBuilder.append(" ORDER by plate.id, assembly.date desc");
        return queryBuilder.toString();
    }

    private void appendSetOfQuestionMarks(StringBuilder builder, int count) {
        String[] qMarks = new String[count];
        Arrays.fill(qMarks, "?");
        builder.append("(").append(StringUtilities.join(",", Arrays.asList(qMarks))).append(")");
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

    public Map<Integer, List<GelImage>> getGelImages(Collection<Integer> plateIds) throws DatabaseServiceException {
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
        try {
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
        } catch (SQLException e) {
            throw new DatabaseServiceException(e, e.getMessage(), false);
        }
    }

    public Set<String> getAllExtractionIdsForTissueIds(List<String> tissueIds) throws DatabaseServiceException {
        if(tissueIds.isEmpty()) {
            return Collections.emptySet();
        }
        List<String> queries = new ArrayList<String>();
        //noinspection UnusedDeclaration
        for (String s : tissueIds) {
            queries.add("sampleId LIKE ?");
        }

        String sql = "SELECT extractionId FROM extraction WHERE " + StringUtilities.join(" OR ", queries);

        Set<String> result = null;
        try {
            PreparedStatement statement = createStatement(sql);
            for (int i = 0; i < tissueIds.size(); i++) {
                statement.setString(i + 1, tissueIds.get(i) + "%");
            }

            ResultSet set = statement.executeQuery();
            result = new HashSet<String>();

            while (set.next()) {
                result.add(set.getString("extractionId"));
            }
        } catch (SQLException e) {
            throw new DatabaseServiceException(e, e.getMessage(), false);
        }


        return result;
    }

    public List<ExtractionReaction> getExtractionsFromBarcodes(List<String> barcodes) throws DatabaseServiceException {
        if (barcodes.size() == 0) {
            System.out.println("empty!");
            return Collections.emptyList();
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

        try {
            PreparedStatement statement = createStatement(sql.toString());

            for (int i = 0; i < barcodes.size(); i++) {
                String barcode = barcodes.get(i);
                statement.setString(i + 1, barcode);
            }

            ResultSet r = statement.executeQuery();

            List<ExtractionReaction> results = new ArrayList<ExtractionReaction>();
            while (r.next()) {
                ExtractionReaction reaction = new ExtractionReaction(r);
                results.add(reaction);
            }

            return results;
        } catch (SQLException e) {
            throw new DatabaseServiceException(e, e.getMessage(), false);
        }
    }

    public Collection<String> getPlatesUsingCocktail(Reaction.Type type, int cocktailId) throws DatabaseServiceException {
        if(cocktailId < 0) {
            return Collections.emptyList();
        }
        String tableName;
        switch(type) {
            case PCR:
                tableName = "pcr";
                break;
            case CycleSequencing:
                tableName = "cyclesequencing";
                break;
            default:
                throw new RuntimeException(type+" reactions cannot have a cocktail");
        }
        String sql = "SELECT plate.name FROM plate, "+tableName+" WHERE "+tableName+".plate = plate.id AND "+tableName+".cocktail = "+cocktailId;
        ConnectionWrapper connection = null;
        try {
            connection = getConnection();
            ResultSet resultSet = connection.executeQuery(sql);

            Set<String> plateNames = new LinkedHashSet<String>();
            while(resultSet.next()) {
                plateNames.add(resultSet.getString(1));
            }
            return plateNames;
        } catch (SQLException e) {
            throw new DatabaseServiceException(e, e.getMessage(), false);
        } finally {
            returnConnection(connection);
        }
    }

    public List<String> getPlatesUsingThermocycle(int thermocycleId) throws DatabaseServiceException {
        if(thermocycleId < 0) {
            return Collections.emptyList();
        }
        String sql = "SELECT name FROM plate WHERE thermocycle = "+thermocycleId;
        ConnectionWrapper connection = null;
        try {
            connection = getConnection();
            ResultSet resultSet = connection.executeQuery(sql);

            List<String> plateNames = new ArrayList<String>();
            while(resultSet.next()) {
                plateNames.add(resultSet.getString(1));
            }
            return plateNames;
        } catch (SQLException e) {
            throw new DatabaseServiceException(e, e.getMessage(), false);
        } finally {
            returnConnection(connection);
        }
    }

    public void renameWorkflow(int id, String newName) throws DatabaseServiceException {
        ConnectionWrapper connection = null;
        try {
            connection = getConnection();
            String sql = "UPDATE workflow SET name=? WHERE id=?";
            PreparedStatement statement = connection.prepareStatement(sql);

            statement.setString(1, newName);
            statement.setInt(2, id);

            statement.executeUpdate();
            statement.close();
        } catch (SQLException e) {
            throw new DatabaseServiceException(e, e.getMessage(), false);
        } finally {
            returnConnection(connection);
        }
    }

    @Override
    public Set<Integer> deletePlates(List<Plate> plates, ProgressListener progress) throws DatabaseServiceException {
        Set<Integer> plateIds = new HashSet<Integer>();
        for (Plate plate : plates) {
            //delete the reactions...
            if (plate.getReactionType() == Reaction.Type.Extraction) {
                plateIds.addAll(deleteWorkflows(progress, plate));
            } else {
                deleteReactions(progress, plate);
            }


            //delete the images...
            progress.setMessage("Deleting GEL images");
            deleteRecords("gelimages", "plate", Arrays.asList(plate.getId()));

            //delete the plate...
            progress.setMessage("deleting the plate");

            deleteRecords("plate", "id", Arrays.asList(plate.getId()));
        }
        return plateIds;
    }

private void deleteReactions(ProgressListener progress, Plate plate) throws DatabaseServiceException {
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

        if(tableName.equals("cyclesequencing")) {
            deleteRecords("traces", "reaction", terms);
        }

        deleteRecords(tableName, "id", terms);
    }

    public void renamePlate(int id, String newName) throws DatabaseServiceException{
        ConnectionWrapper connection = null;
        try {
            connection = getConnection();

            String sql = "UPDATE plate SET name=? WHERE id=?";
            PreparedStatement statement = connection.prepareStatement(sql);

            statement.setString(1, newName);
            statement.setInt(2, id);

            statement.executeUpdate();
            statement.close();
        } catch (SQLException e) {
            throw new DatabaseServiceException(e, e.getMessage(), false);
        } finally {
            returnConnection(connection);
        }
    }

    private int getLastInsertId() throws SQLException, DatabaseServiceException {
        ConnectionWrapper connection = null;
        try {
            connection = getConnection();
            int reactionId;
            ResultSet reactionIdResultSet = BiocodeService.getInstance().getActiveLIMSConnection().isLocal() ? connection.executeQuery("CALL IDENTITY();") : connection.executeQuery("SELECT last_insert_id()");
            reactionIdResultSet.next();
            reactionId = reactionIdResultSet.getInt(1);
            return reactionId;
        } finally {
            returnConnection(connection);
        }

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

    private class WorkflowsAndPlatesQueryResult {
        private List<FimsSample> tissueSamples;
        private Map<Integer, Plate> plates;
        private Map<Integer, WorkflowDocument> workflows;
        private Set<Integer> sequenceIds;

        private WorkflowsAndPlatesQueryResult() {
            tissueSamples = new ArrayList<FimsSample>();
            plates = new HashMap<Integer, Plate>();
            workflows = new HashMap<Integer, WorkflowDocument>();
            sequenceIds = new HashSet<Integer>();
        }
    }


    private WorkflowsAndPlatesQueryResult createPlateAndWorkflowsFromResultSet(
            ConnectionWrapper connection, ProgressListener cancelable, ResultSet resultSet,
            boolean getTissues, boolean getWorkflows, boolean getPlates) throws SQLException, DatabaseServiceException {
        WorkflowsAndPlatesQueryResult result = new WorkflowsAndPlatesQueryResult();
        final StringBuilder totalErrors = new StringBuilder("");

        Set<String> tissueIds = new HashSet<String>();
        Set<Integer> workflowIds = new HashSet<Integer>();
        Set<Integer> plateIds = new HashSet<Integer>();

        System.out.println("Creating Reactions...");
        while (resultSet.next()) {
            if (SystemUtilities.isAvailableMemoryLessThan(50)) {
                resultSet.close();
                throw new SQLException("Search cancelled due to lack of free memory");
            }
            if (cancelable.isCanceled()) {
                return new WorkflowsAndPlatesQueryResult();
            }

            String tissue = resultSet.getString("sampleId");
            if(tissue != null) {
                tissueIds.add(tissue);
            }

            int plateId = resultSet.getInt("plate.id");
            if(!resultSet.wasNull()) {
                plateIds.add(plateId);
            }

            int workflowId = resultSet.getInt("workflow.id");
            if(!resultSet.wasNull()) {
                workflowIds.add(workflowId);
            }

            int sequenceId = resultSet.getInt("assembly.id");
            if (!resultSet.wasNull()) {
                result.sequenceIds.add(sequenceId);
            }
        }
        resultSet.close();

        Map<Integer, String> workflowToSampleId = new HashMap<Integer, String>();
        if(!workflowIds.isEmpty() && getWorkflows) {
            cancelable.setMessage("Downloading " + getCountString("matching workflow document", workflowIds.size()) + "...");
            // Query for full contents of plates that matched our query
            String workflowQueryString = constructWorkflowQuery(workflowIds);
            PreparedStatement selectWorkflow = null;
            try {
                System.out.println("Running LIMS (workflows) query:");
                System.out.print("\t");
                SqlUtilities.printSql(workflowQueryString, workflowIds);

                selectWorkflow = connection.prepareStatement(workflowQueryString);
                SqlUtilities.fillStatement(new ArrayList<Object>(workflowIds), selectWorkflow);

                long start = System.currentTimeMillis();
                ResultSet workflowsSet = selectWorkflow.executeQuery();
                System.out.println("\tTook " + (System.currentTimeMillis() - start) + "ms to do LIMS (workflows) query");

                while(workflowsSet.next()) {
                    int workflowId = workflowsSet.getInt("workflow.id");
                    WorkflowDocument existingWorkflow = result.workflows.get(workflowId);
                    if (existingWorkflow != null) {
                        existingWorkflow.addRow(workflowsSet);
                    } else {
                        WorkflowDocument newWorkflow = new WorkflowDocument(workflowsSet);
                        result.workflows.put(workflowId, newWorkflow);
                    }

                    String sampleId = workflowsSet.getString("extraction.sampleId");
                    if (sampleId != null) {
                        workflowToSampleId.put(workflowId, sampleId);
                    }
                }
                workflowsSet.close();
            } finally {
                SqlUtilities.cleanUpStatements(selectWorkflow);
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

        if(!plateIds.isEmpty() && getPlates) {
            cancelable.setMessage("Downloading " + getCountString("matching plate document", plateIds.size()) + "...");
            // Query for full contents of plates that matched our query
            String plateQueryString = constructPlateQuery(plateIds);
            PreparedStatement selectPlate = null;
            try {
                System.out.println("Running LIMS (plates) query:");
                System.out.print("\t");
                SqlUtilities.printSql(plateQueryString, plateIds);

                selectPlate = connection.prepareStatement(plateQueryString);
                SqlUtilities.fillStatement(new ArrayList<Object>(plateIds), selectPlate);

                long start = System.currentTimeMillis();
                ResultSet plateSet = selectPlate.executeQuery();
                System.out.println("\tTook " + (System.currentTimeMillis() - start) + "ms to do LIMS (plates) query");

                result.plates.putAll(getPlatesFromResultSet(plateSet));
                plateSet.close();
            } finally {
                SqlUtilities.cleanUpStatements(selectPlate);
            }
        }

        if(!tissueIds.isEmpty() && getTissues) {
            cancelable.setMessage("Checking samples that match result from LIMS...");
            try {
                result.tissueSamples.addAll(BiocodeService.getInstance().getActiveFIMSConnection().retrieveSamplesForTissueIds(tissueIds));
            } catch (ConnectionException e) {
                throw new DatabaseServiceException(e, "Unable to retrieve FIMS samples", false);
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

    private static String getCountString(String words, int count) {
        String base = count + " " + words;
        if(count > 1) {
            return base + "s";
        } else {
            return base;
        }
    }

    private String constructWorkflowQuery(Set<Integer> workflowIds) {
        StringBuilder queryBuilder = new StringBuilder(
                "SELECT extraction.*, workflow.*, pcr.*, cyclesequencing.*, plate.*, assembly.id, assembly.progress, " +
                        "assembly.date, assembly.notes, assembly.failure_reason, assembly.failure_notes ");
        queryBuilder.append("FROM workflow ");
        queryBuilder.append("INNER JOIN extraction ON extraction.id = workflow.extractionId AND workflow.id IN ");
        SqlUtilities.appendSetOfQuestionMarks(queryBuilder, workflowIds.size());
        queryBuilder.append(" ");
        queryBuilder.append("LEFT OUTER JOIN pcr ON pcr.workflow = workflow.id ");
        queryBuilder.append("LEFT OUTER JOIN cyclesequencing ON cyclesequencing.workflow = workflow.id ");
        queryBuilder.append("INNER JOIN plate ON (extraction.plate = plate.id OR pcr.plate = plate.id OR cyclesequencing.plate = plate.id) ");
        queryBuilder.append("LEFT OUTER JOIN sequencing_result ON cyclesequencing.id = sequencing_result.reaction ");
        queryBuilder.append("LEFT OUTER JOIN assembly ON assembly.id = sequencing_result.assembly ");
        queryBuilder.append("ORDER BY workflow.id, plate.id asc, assembly.date asc");
        return queryBuilder.toString();
    }

    private Map<Integer, Plate> getPlatesFromResultSet(ResultSet resultSet) throws SQLException {
        Map<Integer, Plate> plates = new HashMap<Integer, Plate>();
        final StringBuilder totalErrors = new StringBuilder("");

        int previousId = -1;
        while (resultSet.next()) {
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

    /**
     * Sets a database wide property.  Can be retrieved by calling {@link LIMSConnection#getProperty(String)}
     *
     * @param key   The name of the property
     * @param value The value to set for the property
     * @throws SQLException if something goes wrong communicating with the database.
     */
    public void setProperty(String key, String value) throws DatabaseServiceException {
        PreparedStatement update = null;
        PreparedStatement insert = null;

        ConnectionWrapper connection = null;
        try {
            connection = getConnection();
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
        } catch (SQLException e) {
            throw new DatabaseServiceException(e, e.getMessage(), false);
        } finally {
            SqlUtilities.cleanUpStatements(update, insert);
            returnConnection(connection);
        }
    }

    /**
     * Retrieves a property from the database previously set by calling {@link LIMSConnection#setProperty(String, String)}
     *
     * @param key The name of the property to retrieve
     * @return value of the property or null if it does not exist
     * @throws SQLException if something goes wrong communicating with the database.
     */
    public String getProperty(String key) throws DatabaseServiceException {
        ConnectionWrapper connection = null;
        PreparedStatement get = null;
        try {
            connection = getConnection();
            get = connection.prepareStatement("SELECT value FROM properties WHERE name = ?");
            get.setObject(1, key);
            ResultSet resultSet = get.executeQuery();
            if (resultSet.next()) {
                return resultSet.getString("value");
            } else {
                return null;
            }
        } catch (SQLException e) {
            throw new DatabaseServiceException(e, e.getMessage(), false);
        } finally {
            SqlUtilities.cleanUpStatements(get);
            returnConnection(connection);
        }
    }

    private List<Workflow> addWorkflows(List<Reaction> reactions, ProgressListener progress) throws DatabaseServiceException {
        List<Workflow> workflows = new ArrayList<Workflow>();
        ConnectionWrapper connection = null;

        try {
            connection = getConnection();
            connection.beginTransaction();
            PreparedStatement statement = connection.prepareStatement("INSERT INTO workflow(locus, extractionId, date) VALUES (?, (SELECT extraction.id from extraction where extraction.extractionId = ?), ?)");
            PreparedStatement statement2 = isLocal() ? connection.prepareStatement("CALL IDENTITY();") : connection.prepareStatement("SELECT last_insert_id()");
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

            statement.close();
            statement2.close();
            statement3.close();
            connection.endTransaction();
            return workflows;
        } catch(SQLException e) {
            throw new DatabaseServiceException(e, e.getMessage(), false);
        } finally {
            returnConnection(connection);
        }
    }

    private Set<Integer> deleteWorkflows(ProgressListener progress, Plate plate) throws DatabaseServiceException {
        progress.setMessage("deleting workflows");
        if (plate.getReactionType() != Reaction.Type.Extraction) {
            throw new IllegalArgumentException("You may only delete workflows from an extraction plate!");
        }

        ArrayList<Integer> workflows = new ArrayList<Integer>();
        ArrayList<Integer> ids = new ArrayList<Integer>();


        boolean first = true;
        StringBuilder builder = new StringBuilder();
        int reactionCount = 0;
        Reaction[] reactions = plate.getReactions();
        for (Reaction r : reactions) { //get the extraction id's and set up the query to get the workflow id's
            if (r.getId() >= 0) {
                ids.add(r.getId());
                if (!first) {
                    builder.append(" OR ");
                }
                //noinspection StringConcatenationInsideStringBufferAppend
                builder.append("extractionId=" + r.getId());
                first = false;
                reactionCount++;
            }
        }
        if (reactionCount == 0) { //the plate is empty
            return Collections.emptySet();
        }

        String getWorkflowSQL = "SELECT id FROM workflow WHERE " + builder.toString();
        System.out.println(getWorkflowSQL);

        ConnectionWrapper connection = null;

        try {
            connection = getConnection();

            ResultSet resultSet = connection.executeQuery(getWorkflowSQL);
            while (resultSet.next()) {
                workflows.add(resultSet.getInt("workflow.id"));
            }

            Set<Integer> plates = new HashSet<Integer>();

            plates.addAll(deleteRecords("pcr", "workflow", workflows));
            //plates.addAll(limsConnection.deleteRecords("pcr", "extractionId", extractionNames));
            plates.addAll(deleteRecords("cyclesequencing", "workflow", workflows));
            // plates.addAll(limsConnection.deleteRecords("cyclesequencing", "extractionId", extractionNames));
            deleteRecords("assembly", "workflow", workflows);
            deleteRecords("workflow", "id", workflows);
            plates.addAll(deleteRecords("extraction", "id", ids));

            return plates;
        } catch (SQLException e) {
            throw new DatabaseServiceException(e, e.getMessage(), false);
        } finally {
            returnConnection(connection);
        }
    }

    @Override
    public List<Workflow> getWorkflows(Collection<String> workflowIds) throws DatabaseServiceException {
        if(workflowIds.isEmpty()) {
            return Collections.emptyList();
        }
        ConnectionWrapper connection = null;

        try {
            connection = getConnection();

            StringBuilder sqlBuilder = new StringBuilder();
            sqlBuilder.append("SELECT workflow.name AS workflow, workflow.id AS workflowId, workflow.date AS date, workflow.locus AS locus, extraction.extractionId FROM workflow, extraction WHERE extraction.id = workflow.extractionId AND (");
            for (int i = 0; i < workflowIds.size(); i++) {
                sqlBuilder.append("workflow.name = ? ");
                if (i < workflowIds.size() - 1) {
                    sqlBuilder.append("OR ");
                }
            }
            sqlBuilder.append(")");
            PreparedStatement statement = connection.prepareStatement(sqlBuilder.toString());
            int i = 0;
            for (String s : workflowIds) {
                statement.setString(i + 1, s);
                i++;
            }
            ResultSet results = statement.executeQuery();
            List<Workflow> result = new ArrayList<Workflow>();

            while (results.next()) {
                result.add(new Workflow(results.getInt("workflowId"), results.getString("workflow"), results.getString("extractionId"), results.getString("locus"), results.getDate("workflow.date")));
            }
            statement.close();
            return result;
        } catch(SQLException e) {
            throw new DatabaseServiceException(e, e.getMessage(), false);
        } finally {
            returnConnection(connection);
        }
    }

    @Override
    public Map<String, String> getWorkflowIds(List<String> idsToCheck, List<String> loci, Reaction.Type reactionType) throws DatabaseServiceException {
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

        ConnectionWrapper connection = null;

        try {
            connection = getConnection();

            System.out.println(sqlBuilder.toString());
            PreparedStatement statement = connection.prepareStatement(sqlBuilder.toString());
            for (int i = 0; i < values.size(); i++) {
                statement.setString(i + 1, values.get(i));
            }
            ResultSet results = statement.executeQuery();
            Map<String, String> result = new HashMap<String, String>();

            while (results.next()) {
                result.put(results.getString("id"), results.getString("workflow")/*new Workflow(results.getInt("workflowId"), results.getString("workflow"), results.getString("id"))*/);
            }
            statement.close();
            return result;
        } catch (SQLException e) {
            throw new DatabaseServiceException(e, e.getMessage(), false);
        } finally {
            returnConnection(connection);
        }
    }

    public void testConnection() throws DatabaseServiceException {
        ConnectionWrapper connection = null;
        try {
            connection = getConnection();
            connection.executeQuery(isLocal() ? "SELECT * FROM databaseversion" : "SELECT 1"); //because JDBC doesn't have a better way of checking whether a connection is enabled
        } catch (SQLException e) {
            throw new DatabaseServiceException(e, e.getMessage(), false);
        } finally {
                    returnConnection(connection);
                }
    }

    @Override
    public List<AssembledSequence> getAssemblyDocuments(List<Integer> sequenceIds, RetrieveCallback callback, boolean includeFailed) throws DatabaseServiceException {
        List<AssembledSequence> sequences = new ArrayList<AssembledSequence>();
        ConnectionWrapper connection = null;
        PreparedStatement statement = null;
        try {
            connection = getConnection();
            List<Object> sqlValues = new ArrayList<Object>(sequenceIds);

            StringBuilder sql = new StringBuilder("SELECT workflow.locus, assembly.*, extraction.sampleId, extraction.extractionId, extraction.extractionBarcode ");
            sql.append("FROM workflow INNER JOIN assembly ON assembly.id IN ");
            appendSetOfQuestionMarks(sql, sequenceIds.size());
            if (!includeFailed) {
                sql.append(" AND assembly.progress = ?");
                sqlValues.add("passed");
            }
            sql.append(" AND workflow.id = assembly.workflow INNER JOIN extraction ON workflow.extractionId = extraction.id");

            statement = connection.prepareStatement(sql.toString());
            SqlUtilities.fillStatement(sqlValues, statement);
            SqlUtilities.printSql(sql.toString(), sqlValues);
            if (!isLocal()) {
                statement.setFetchSize(Integer.MIN_VALUE);
            }

            final ResultSet resultSet = statement.executeQuery();
            while (resultSet.next()) {
                if (SystemUtilities.isAvailableMemoryLessThan(50)) {
                    statement.cancel();
                    throw new SQLException("Search cancelled due to lack of free memory");
                }
                if (callback != null && callback.isCanceled()) {
                    return Collections.emptyList();
                }
                AssembledSequence seq = getAssembledSequence(resultSet);
                if (seq != null) {
                    sequences.add(seq);
                }
            }
            return sequences;
        } catch (SQLException e) {
            throw new DatabaseServiceException(e, e.getMessage(), false);
        } finally {
            SqlUtilities.cleanUpStatements(statement);
            returnConnection(connection);
        }
    }

    private AssembledSequence getAssembledSequence(ResultSet resultSet) throws SQLException {
        AssembledSequence seq = new AssembledSequence();
        seq.confidenceScore = resultSet.getString("assembly.confidence_scores");
        seq.id = resultSet.getInt("id");
        seq.workflowLocus = resultSet.getString("workflow.locus");
        seq.extractionId = resultSet.getString("assembly.extraction_id");
        seq.progress = resultSet.getString("progress");
        seq.consensus = resultSet.getString("consensus");
        seq.workflowId = resultSet.getInt("workflow");
        seq.assemblyNotes = resultSet.getString("assembly.notes");
        seq.sampleId = resultSet.getString("sampleId");
        seq.coverage = resultSet.getDouble("assembly.coverage");
        seq.numberOfDisagreements = resultSet.getInt("assembly.disagreements");
        seq.numOfEdits = resultSet.getInt("assembly.edits");
        seq.forwardTrimParameters = resultSet.getString("assembly.trim_params_fwd");
        seq.reverseTrimParameters = resultSet.getString("assembly.trim_params_rev");
        seq.limsId = resultSet.getInt("assembly.id");
        seq.technician = resultSet.getString("assembly.technician");
        seq.bin = resultSet.getString("assembly.bin");
        seq.numberOfAmbiguities = resultSet.getInt("assembly.ambiguities");
        seq.assemblyParameters = resultSet.getString("assembly.params");
        seq.submitted = resultSet.getBoolean("assembly.submitted");
        seq.editRecord = resultSet.getString("assembly.editrecord");
        seq.extractionId = resultSet.getString("extraction.extractionId");
        seq.extractionBarcode = resultSet.getString("extraction.extractionBarcode");
        java.sql.Date created = resultSet.getDate("date");
        if(created != null) {
            seq.date = created.getTime();
        }
        return seq;
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
                SqlUtilities.QueryTermSurrounder termSurrounder = SqlUtilities.getQueryTermSurrounder(q);
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

    private void appendValue(List<Object> inserts, StringBuilder sql, boolean appendAnd, SqlUtilities.QueryTermSurrounder termSurrounder, Object value, Condition condition) {
        String valueString = valueToString(value);
        //valueString = termSurrounder.getPrepend()+valueString+termSurrounder.getAppend();
        /* The time of day of date values used as search constraints should not be considered (the day of the month is
         * the most granular unit for date values used as search constraints). Date values are modified accordingly to
         * help create this behaviour.
         */
        if (Date.class.isAssignableFrom(value.getClass())) {
            GregorianCalendar date = new GregorianCalendar();
            date.setTime((Date)value);
            switch (condition) {
                case DATE_BEFORE:
                case DATE_AFTER_OR_ON:
                    date.set(GregorianCalendar.HOUR_OF_DAY, 0);
                    date.set(GregorianCalendar.MINUTE, 0);
                    date.set(GregorianCalendar.SECOND, 0);
                    date.set(GregorianCalendar.MILLISECOND, 0);
                    break;
                case DATE_BEFORE_OR_ON:
                case DATE_AFTER:
                    date.set(GregorianCalendar.HOUR_OF_DAY, 23);
                    date.set(GregorianCalendar.MINUTE, 59);
                    date.set(GregorianCalendar.SECOND, 59);
                    date.set(GregorianCalendar.MILLISECOND, 999);
                    break;
                default:
                    break;
            }
            value = (Object)date.getTime();
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


    protected Query generateAdvancedQueryFromBasicQuery(Query query) {
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

    public void createOrUpdatePlate(Plate plate, ProgressListener progress) throws DatabaseServiceException {
        ConnectionWrapper connection = null;
        try {
            //check the vaidity of the plate.
            isPlateValid(plate);

            connection = getConnection();
            connection.beginTransaction();

            //update the plate
            PreparedStatement statement = plateToSQL(connection, plate);
            statement.execute();
            statement.close();
            if(plate.getId() < 0) {
                PreparedStatement statement1 = isLocal() ? connection.prepareStatement("CALL IDENTITY();") : connection.prepareStatement("SELECT last_insert_id()");
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
                PreparedStatement deleteImagesStatement = connection.prepareStatement("DELETE FROM gelimages WHERE plate=" + plate.getId());
                deleteImagesStatement.execute();
                for(GelImage image : plate.getImages()) {
                    PreparedStatement statement1 = gelToSql(connection, image);
                    statement1.execute();
                    statement1.close();
                }
                deleteImagesStatement.close();
            }

            saveReactions(plate.getReactions(), plate.getReactionType(), progress);
            Map<String, Set<Integer>> workflowLoci = new HashMap<String, Set<Integer>>();
            for (Reaction reaction : plate.getReactions()) {
                Set<Integer> ids = workflowLoci.get(reaction.getLocus());
                if (ids == null) {
                    ids = new HashSet<Integer>();
                    workflowLoci.put(reaction.getLocus(), ids);
                }
                Workflow workflow = reaction.getWorkflow();
                if (workflow != null) {
                    ids.add(workflow.getId());
                }
            }

            for (Map.Entry<String, Set<Integer>> stringSetEntry : workflowLoci.entrySet()) {
                if (stringSetEntry.getValue().isEmpty()) {
                    continue;
                }
                StringBuilder updateLociSql = new StringBuilder();
                updateLociSql.append("UPDATE workflow SET workflow.locus = ? WHERE id IN ");
                SqlUtilities.appendSetOfQuestionMarks(updateLociSql, stringSetEntry.getValue().size());
                PreparedStatement updateLociStatement = connection.prepareStatement(updateLociSql.toString());
                int i = 2;
                updateLociStatement.setObject(1, stringSetEntry.getKey());
                for (Integer id : stringSetEntry.getValue()) {
                    updateLociStatement.setObject(i++, id);
                }

                int numberOfUpdatedWorkflows = updateLociStatement.executeUpdate();

                if (numberOfUpdatedWorkflows != stringSetEntry.getValue().size()) {
                    throw new DatabaseServiceException("Incorrect number of workflows updated, expected: " +
                            stringSetEntry.getValue().size() + ", actual: " + numberOfUpdatedWorkflows, false);
                }
            }

            //update the last-modified on the workflows associated with this plate...
            String sql;
            if (plate.getReactionType() == Reaction.Type.Extraction) {
                sql = "UPDATE workflow SET workflow.date = (SELECT date from plate WHERE plate.id="+plate.getId()+") WHERE extractionId IN (SELECT id FROM extraction WHERE extraction.plate="+plate.getId()+")";
            }
            else if(plate.getReactionType() == Reaction.Type.PCR){
                sql = "UPDATE workflow SET workflow.date = (SELECT date from plate WHERE plate.id="+plate.getId()+") WHERE id IN (SELECT workflow FROM pcr WHERE pcr.plate="+plate.getId()+")";
            }
            else if(plate.getReactionType() == Reaction.Type.CycleSequencing){
                sql = "UPDATE workflow SET workflow.date = (SELECT date from plate WHERE plate.id="+plate.getId()+") WHERE id IN (SELECT workflow FROM cyclesequencing WHERE cyclesequencing.plate="+plate.getId()+")";

            }
            else {
                throw new SQLException("There is no reaction type "+plate.getReactionType());
            }
            connection.executeUpdate(sql);

            connection.endTransaction();
        } catch (SQLException e) {
            throw new DatabaseServiceException(e, e.getMessage(), false);
        } finally {
            returnConnection(connection);
        }
    }

    private static PreparedStatement gelToSql(ConnectionWrapper connection, GelImage gel) throws SQLException {
        PreparedStatement statement;
        statement = connection.prepareStatement("INSERT INTO gelimages (plate, imageData, notes, name) VALUES (?, ?, ?, ?)");
        statement.setInt(1, gel.getPlate());
        statement.setObject(2, gel.getImageBytes());
        statement.setString(3, gel.getNotes());
        statement.setString(4, gel.getFilename());
        return statement;
    }

    private static PreparedStatement plateToSQL(ConnectionWrapper connection, Plate plate) throws SQLException{
        String name = plate.getName();
        if(name == null || name.trim().length() == 0) {
            throw new SQLException("Plates cannot have empty names");
        }
        PreparedStatement statement;
        int id = plate.getId();
        if(id < 0) {
            statement = connection.prepareStatement("INSERT INTO plate (name, size, type, thermocycle, date) VALUES (?, ?, ?, ?, ?)");
        }
        else {
            statement = connection.prepareStatement("UPDATE plate SET name=?, size=?, type=?, thermocycle=?, date=? WHERE id=?");
            statement.setInt(6, id);
        }
        statement.setString(1, name);
        statement.setInt(2, plate.getReactions().length);
        statement.setString(3, plate.getReactionType().toString());
        Thermocycle tc = plate.getThermocycle();
        if(tc != null) {
            statement.setInt(4, tc.getId());
        }
        else {
            statement.setInt(4, plate.getThermocycleId());
        }
        statement.setDate(5, new java.sql.Date(new Date().getTime()));
        return statement;
    }

    public void isPlateValid(Plate plate) throws DatabaseServiceException {
        try {
            if(plate.getName() == null || plate.getName().length() == 0) {
                throw new BadDataException("Plates cannot have empty names");
            }
            if(plate.getId() < 0) {
                PreparedStatement plateCheckStatement = createStatement("SELECT name FROM plate WHERE name=?");
                plateCheckStatement.setString(1, plate.getName());
                if(plateCheckStatement.executeQuery().next()) {
                    throw new BadDataException("A plate with the name '"+plate.getName()+"' already exists");
                }
                plateCheckStatement.close();
            }
            if(plate.getThermocycle() == null && plate.getReactionType() != Reaction.Type.Extraction) {
                throw new BadDataException("The plate has no thermocycle set");
            }
        } catch (BadDataException e) {
            throw new DatabaseServiceException(e, e.getMessage(), false);
        } catch (SQLException e) {
            throw new DatabaseServiceException(e, e.getMessage(), false);
        }
    }

    public List<Plate> getEmptyPlates(Collection<Integer> plateIds) throws DatabaseServiceException {
        if(plateIds == null || plateIds.size() == 0) {
            return Collections.emptyList();
        }

        String sql = "SELECT * FROM plate WHERE (plate.id NOT IN (select plate from extraction)) AND (plate.id NOT IN " +
                "(select plate from pcr)) AND (plate.id NOT IN (select plate from cyclesequencing))";


        List<String> idMatches = new ArrayList<String>();
        for(Integer num : plateIds) {
            idMatches.add("id="+num);
        }

        String termString = StringUtilities.join(" OR ", idMatches);
        if(termString.length() > 0) {
            sql += " AND ("+termString+")";
        }

        ConnectionWrapper connection = null;
        try {
            connection = getConnection();
            ResultSet resultSet = connection.executeQuery(sql);
            List<Plate> result = new ArrayList<Plate>();
            while(resultSet.next()) {
                Plate plate = new Plate(resultSet);
                plate.initialiseReactions();
                result.add(plate);
            }
            return result;
        } catch (SQLException e) {
            throw new DatabaseServiceException(e, e.getMessage(), false);
        } finally {
            returnConnection(connection);
        }
    }

    public void savePlates(List<Plate> plates, ProgressListener progress) throws BadDataException, DatabaseServiceException {
        for (Plate plate : plates) {
            if (plate.getReactionType() == Reaction.Type.Extraction) {
                saveExtractions(plate, progress);
            } else {
                saveReactions(plate, progress);
            }
        }
    }

    private void saveReactions(Plate plate, ProgressListener progress) throws BadDataException, DatabaseServiceException {
        ConnectionWrapper connection = null;
        try {
            connection = getConnection();
            connection.beginTransaction();

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
            if (reactionsWithoutWorkflows.size() > 0) {
                List<Workflow> workflowList = addWorkflows(reactionsWithoutWorkflows, progress);
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
            connection.endTransaction();
        } catch (SQLException e) {
            throw new DatabaseServiceException(e, e.getMessage(), false);
        } finally {
            returnConnection(connection);
        }
    }

    private void saveExtractions(Plate plate, ProgressListener progress) throws DatabaseServiceException, BadDataException {
        isPlateValid(plate);
        createOrUpdatePlate(plate, progress);
    }

    public void saveReactions(Reaction[] reactions, Reaction.Type type, ProgressListener progress) throws DatabaseServiceException {
        ConnectionWrapper connection = null;
        try {
            connection = getConnection();
            PreparedStatement getLastId = BiocodeService.getInstance().getActiveLIMSConnection().isLocal() ?
                    connection.prepareStatement("CALL IDENTITY();") : connection.prepareStatement("SELECT last_insert_id()");
            switch(type) {
                case Extraction:
                    String insertSQL;
                    String updateSQL;
                    insertSQL  = "INSERT INTO extraction (method, volume, dilution, parent, sampleId, extractionId, extractionBarcode, plate, location, notes, previousPlate, previousWell, date, technician, concentrationStored, concentration, gelimage, control) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
                    updateSQL  = "UPDATE extraction SET method=?, volume=?, dilution=?, parent=?, sampleId=?, extractionId=?, extractionBarcode=?, plate=?, location=?, notes=?, previousPlate=?, previousWell=?, date=?, technician=?, concentrationStored=?, concentration=?, gelImage=?, control=? WHERE id=?";

                    PreparedStatement insertStatement = connection.prepareStatement(insertSQL);
                    PreparedStatement updateStatement = connection.prepareStatement(updateSQL);
                    for (int i = 0; i < reactions.length; i++) {
                        Reaction reaction = reactions[i];
                        if(progress != null) {
                            progress.setMessage("Saving reaction "+(i+1)+" of "+reactions.length);
                        }
                        if (!reaction.isEmpty() && reaction.getPlateId() >= 0) {
                            PreparedStatement statement;
                            boolean isUpdateNotInsert = reaction.getId() >= 0;
                            if(isUpdateNotInsert) { //the reaction is already in the database
                                statement = updateStatement;
                                statement.setInt(19, reaction.getId());
                            }
                            else {
                                statement = insertStatement;
                            }
                            ReactionOptions options = reaction.getOptions();
                            statement.setString(1, options.getValueAsString("extractionMethod"));
                            statement.setInt(2, (Integer) options.getValue("volume"));
                            statement.setInt(3, (Integer) options.getValue("dilution"));
                            statement.setString(4, options.getValueAsString("parentExtraction"));
                            statement.setString(5, options.getValueAsString("sampleId"));
                            statement.setString(6, options.getValueAsString("extractionId"));
                            statement.setString(7, options.getValueAsString("extractionBarcode"));
                            statement.setInt(8, reaction.getPlateId());
                            statement.setInt(9, reaction.getPosition());
                            statement.setString(10, options.getValueAsString("notes"));
                            statement.setString(11, options.getValueAsString("previousPlate"));
                            statement.setString(12, options.getValueAsString("previousWell"));
                            statement.setDate(13, new java.sql.Date(((Date)options.getValue("date")).getTime()));
                            statement.setString(14, options.getValueAsString("technician"));
                            statement.setInt(15, "yes".equals(options.getValueAsString("concentrationStored")) ? 1 : 0);
                            statement.setDouble(16, (Double)options.getValue("concentration"));
                            GelImage image = reaction.getGelImage();
                            statement.setBytes(17, image != null ? image.getImageBytes() : null);
                            statement.setString(18, options.getValueAsString("control"));

                            if(isUpdateNotInsert) {
                                updateStatement.executeUpdate();
                            } else {
                                insertStatement.executeUpdate();
                                ResultSet resultSet = getLastId.executeQuery();
                                if(resultSet.next()) {
                                    reaction.setId(resultSet.getInt(1));
                                }
                                resultSet.close();
                            }
                        }
                    }
                    insertStatement.close();
                    updateStatement.close();
                    break;
                case PCR:
                    insertSQL = "INSERT INTO pcr (prName, prSequence, workflow, plate, location, cocktail, progress, thermocycle, cleanupPerformed, cleanupMethod, extractionId, notes, revPrName, revPrSequence, date, technician, gelimage) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
                    updateSQL = "UPDATE pcr SET prName=?, prSequence=?, workflow=?, plate=?, location=?, cocktail=?, progress=?, thermocycle=?, cleanupPerformed=?, cleanupMethod=?, extractionId=?, notes=?, revPrName=?, revPrSequence=?, date=?, technician=?, gelimage=? WHERE id=?";
                    insertStatement = connection.prepareStatement(insertSQL);
                    updateStatement = connection.prepareStatement(updateSQL);
                    int saveCount = 0;
                    for (int i = 0; i < reactions.length; i++) {
                        Reaction reaction = reactions[i];
                        if(progress != null) {
                            progress.setMessage("Saving reaction "+(i+1)+" of "+reactions.length);
                        }
                        if (!reaction.isEmpty() && reaction.getPlateId() >= 0) {
                            PreparedStatement statement;
                            if(reaction.getId() >= 0) { //the reaction is already in the database
                                statement = updateStatement;
                                statement.setInt(18, reaction.getId());
                            }
                            else {
                                statement = insertStatement;
                            }

                            ReactionOptions options = reaction.getOptions();
                            Options.Option option = options.getOption(PCROptions.PRIMER_OPTION_ID);
                            if(!(option instanceof DocumentSelectionOption)) {
                                throw new SQLException("Could not save reactions - expected primer type "+DocumentSelectionOption.class.getCanonicalName()+" but found a "+option.getClass().getCanonicalName());
                            }
                            List<AnnotatedPluginDocument> primerOptionValue = ((DocumentSelectionOption)option).getDocuments();
                            if(primerOptionValue.size() == 0) {
                                statement.setString(1, "None");
                                statement.setString(2, "");
                            }
                            else {
                                AnnotatedPluginDocument selectedDoc = primerOptionValue.get(0);
                                NucleotideSequenceDocument sequence = (NucleotideSequenceDocument)selectedDoc.getDocumentOrThrow(SQLException.class);
                                statement.setString(1, selectedDoc.getName());
                                statement.setString(2, sequence.getSequenceString());
                            }
                            //statement.setInt(3, (Integer)options.getValue("prAmount"));

                            Options.Option option2 = options.getOption(PCROptions.PRIMER_REVERSE_OPTION_ID);
                            if(!(option2 instanceof DocumentSelectionOption)) {
                                throw new SQLException("Could not save reactions - expected primer type "+DocumentSelectionOption.class.getCanonicalName()+" but found a "+option2.getClass().getCanonicalName());
                            }
                            List<AnnotatedPluginDocument> primerOptionValue2 = ((DocumentSelectionOption)option2).getDocuments();
                            if(primerOptionValue2.size() == 0) {
                                statement.setString(13, "None");
                                statement.setString(14, "");
                            }
                            else {
                                AnnotatedPluginDocument selectedDoc = primerOptionValue2.get(0);
                                NucleotideSequenceDocument sequence = (NucleotideSequenceDocument)selectedDoc.getDocumentOrThrow(SQLException.class);
                                statement.setString(13, selectedDoc.getName());
                                statement.setString(14, sequence.getSequenceString());
                            }
                            statement.setDate(15, new java.sql.Date(((Date)options.getValue("date")).getTime()));
                            statement.setString(16, options.getValueAsString("technician"));
    //                        statement.setInt(14, (Integer)options.getValue("revPrAmount"));
    //                        if (reaction.getWorkflow() == null || reaction.getWorkflow().getId() < 0) {
    //                            throw new SQLException("The reaction " + reaction.getId() + " does not have a workflow set.");
    //                        }
                            //statement.setInt(4, reaction.getWorkflow() != null ? reaction.getWorkflow().getId() : 0);
                            if(reaction.getWorkflow() != null) {
                                statement.setInt(3, reaction.getWorkflow().getId());
                            }
                            else {
                                statement.setObject(3, null);
                            }
                            statement.setInt(4, reaction.getPlateId());
                            statement.setInt(5, reaction.getPosition());
                            int cocktailId;
                            Options.OptionValue cocktailValue = (Options.OptionValue) options.getValue("cocktail");
                            try {
                                cocktailId = Integer.parseInt(cocktailValue.getName());
                            }
                            catch(NumberFormatException ex) {
                                throw new SQLException("The reaction " + reaction.getId() + " does not have a valid cocktail ("+ cocktailValue.getLabel()+", "+cocktailValue.getName()+").");
                            }
                            if(cocktailId < 0) {
                                throw new SQLException("The reaction " + reaction.getPosition() + " does not have a valid cocktail ("+cocktailValue.getName()+").");
                            }

                            statement.setInt(6, cocktailId);
                            statement.setString(7, ((Options.OptionValue)options.getValue(ReactionOptions.RUN_STATUS)).getLabel());
                            if(reaction.getThermocycle() != null) {
                                statement.setInt(8, reaction.getThermocycle().getId());
                            }
                            else {
                                statement.setInt(8, -1);
                            }
                            statement.setInt(9, ((Options.OptionValue)options.getValue("cleanupPerformed")).getName().equals("true") ? 1 : 0);
                            statement.setString(10, options.getValueAsString("cleanupMethod"));
                            statement.setString(11, reaction.getExtractionId());
                            System.out.println(reaction.getExtractionId());
                            statement.setString(12, options.getValueAsString("notes"));
                            GelImage image = reaction.getGelImage();
                            statement.setBytes(17, image != null ? image.getImageBytes() : null);
                            statement.execute();

                            if(reaction.getId() < 0) {
                                ResultSet resultSet = getLastId.executeQuery();
                                if(resultSet.next()) {
                                    reaction.setId(resultSet.getInt(1));
                                }
                                resultSet.close();
                            }
                            saveCount++;
                        }
                    }
                    insertStatement.close();
                    updateStatement.close();
                    System.out.println(saveCount+" reactions saved...");
                    break;
                case CycleSequencing:
                    insertSQL = "INSERT INTO cyclesequencing (primerName, primerSequence, direction, workflow, plate, location, cocktail, progress, thermocycle, cleanupPerformed, cleanupMethod, extractionId, notes, date, technician, gelimage) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
                    updateSQL = "UPDATE cyclesequencing SET primerName=?, primerSequence=?, direction=?, workflow=?, plate=?, location=?, cocktail=?, progress=?, thermocycle=?, cleanupPerformed=?, cleanupMethod=?, extractionId=?, notes=?, date=?, technician=?, gelimage=? WHERE id=?";
                    String clearTracesSQL = "DELETE FROM traces WHERE id=?";
                    String insertTracesSQL = "INSERT INTO traces(reaction, name, data) values(?, ?, ?)";

                    insertStatement = connection.prepareStatement(insertSQL);
                    updateStatement = connection.prepareStatement(updateSQL);
                    PreparedStatement clearTracesStatement = connection.prepareStatement(clearTracesSQL);
                    PreparedStatement insertTracesStatement = connection.prepareStatement(insertTracesSQL);
                    for (int i = 0; i < reactions.length; i++) {
                        Reaction reaction = reactions[i];
                        if(progress != null) {
                            progress.setMessage("Saving reaction "+(i+1)+" of "+reactions.length);
                        }
                        if (!reaction.isEmpty() && reaction.getPlateId() >= 0) {

                            PreparedStatement statement;
                            if(reaction.getId() >= 0) { //the reaction is already in the database
                                statement = updateStatement;
                                statement.setInt(17, reaction.getId());
                            }
                            else {
                                statement = insertStatement;
                            }

                            ReactionOptions options = reaction.getOptions();
                            Options.Option option = options.getOption(PCROptions.PRIMER_OPTION_ID);
                            if(!(option instanceof DocumentSelectionOption)) {
                                throw new SQLException("Could not save reactions - expected primer type "+DocumentSelectionOption.class.getCanonicalName()+" but found a "+option.getClass().getCanonicalName());
                            }
                            List<AnnotatedPluginDocument> primerOptionValue = ((DocumentSelectionOption)option).getDocuments();
                            if(primerOptionValue.size() == 0) {
                                statement.setString(1, "None");
                                statement.setString(2, "");
                            }
                            else {
                                AnnotatedPluginDocument selectedDoc = primerOptionValue.get(0);
                                NucleotideSequenceDocument sequence = (NucleotideSequenceDocument)selectedDoc.getDocumentOrThrow(SQLException.class);
                                statement.setString(1, selectedDoc.getName());
                                statement.setString(2, sequence.getSequenceString());
                            }
                            statement.setString(3, options.getValueAsString("direction"));
                            //statement.setInt(3, (Integer)options.getValue("prAmount"));
                            if(reaction.getWorkflow() != null) {
                                statement.setInt(4, reaction.getWorkflow().getId());
                            }
                            else {
                                statement.setObject(4, null);
                            }
                            statement.setInt(5, reaction.getPlateId());
                            statement.setInt(6, reaction.getPosition());
                            int cocktailId;
                            Options.OptionValue cocktailValue = (Options.OptionValue) options.getValue("cocktail");
                            try {
                                cocktailId = Integer.parseInt(cocktailValue.getName());
                            }
                            catch(NumberFormatException ex) {
                                throw new SQLException("The reaction " + reaction.getLocationString() + " does not have a valid cocktail ("+ cocktailValue.getLabel()+", "+cocktailValue.getName()+").");
                            }
                            if(cocktailId < 0) {
                                throw new SQLException("The reaction " + reaction.getLocationString() + " does not have a valid cocktail ("+cocktailValue.getName()+").");
                            }
                            statement.setInt(7, cocktailId);
                            statement.setString(8, ((Options.OptionValue)options.getValue(ReactionOptions.RUN_STATUS)).getLabel());
                            if(reaction.getThermocycle() != null) {
                                statement.setInt(9, reaction.getThermocycle().getId());
                            }
                            else {
                                statement.setInt(9, -1);
                            }
                            statement.setInt(10, ((Options.OptionValue)options.getValue("cleanupPerformed")).getName().equals("true") ? 1 : 0);
                            statement.setString(11, options.getValueAsString("cleanupMethod"));
                            statement.setString(12, reaction.getExtractionId());
                            statement.setString(13, options.getValueAsString("notes"));
                            statement.setDate(14, new java.sql.Date(((Date)options.getValue("date")).getTime()));
                            statement.setString(15, options.getValueAsString("technician"));
                            GelImage image = reaction.getGelImage();
                            statement.setBytes(16, image != null ? image.getImageBytes() : null);

    //                        List<NucleotideSequenceDocument> sequences = ((CycleSequencingOptions)options).getTraces();
    //                        String sequenceString = "";
    //                        if(sequences != null && sequences.size() > 0) {
    //                            DefaultSequenceListDocument sequenceList = DefaultSequenceListDocument.forNucleotideSequences(sequences);
    //                            Element element = XMLSerializer.classToXML("sequences", sequenceList);
    //                            XMLOutputter out = new XMLOutputter(Format.getCompactFormat());
    //                            StringWriter writer = new StringWriter();
    //                            try {
    //                                out.output(element, writer);
    //                                sequenceString = writer.toString();
    //                            } catch (IOException e) {
    //                                throw new SQLException("Could not write the sequences to the database: "+e.getMessage());
    //                            }
    //                        }
    //
    //                        statement.setString(14, sequenceString);
                            statement.execute();
                            if(reaction.getId() < 0) {
                                ResultSet resultSet = getLastId.executeQuery();
                                if(resultSet.next()) {
                                    reaction.setId(resultSet.getInt(1));
                                }
                                resultSet.close();
                            }

                            if(((CycleSequencingReaction)reaction).getTraces() != null) {
                                int reactionId = reaction.getId();
                                for(Integer traceId : ((CycleSequencingReaction)reaction).getTracesToRemoveOnSave()) {
                                    if(!BiocodeService.getInstance().deleteAllowed("traces")) {
                                        throw new SQLException("It appears that you do not have permission to delete traces.  Please contact your System Administrator for assistance");
                                    }
                                    clearTracesStatement.setInt(1, traceId);
                                    clearTracesStatement.execute();
                                }
                                ((CycleSequencingReaction)reaction).clearTracesToRemoveOnSave();
                                if(reactionId < 0) {
                                    reactionId = getLastInsertId();
                                }

                                List<Trace> traces = ((CycleSequencingReaction)reaction).getTraces();
                                if(traces != null) {
                                    for(Trace trace : traces) {
                                        if(trace.getId() >= 0) {
                                            continue; //already added these...
                                        }
                                        MemoryFile file = trace.getFile();
                                        if(file != null) {
                                            insertTracesStatement.setInt(1, reactionId);
                                            insertTracesStatement.setString(2, file.getName());
                                            insertTracesStatement.setBytes(3, file.getData());
                                            insertTracesStatement.execute();
                                            trace.setId(getLastInsertId());
                                        }
                                    }
                                }
                            }
                            FailureReason reason = FailureReason.getReasonFromOptions(options);
                            if(reason != null) {
                                // Requires schema 10.  This won't work for reactions that don't have an assembly.
                                PreparedStatement update = connection.prepareStatement(
                                        "UPDATE assembly SET failure_reason = ? WHERE id IN (" +
                                                "SELECT assembly FROM sequencing_result WHERE reaction = ?" +
                                                ")"
                                );
                                update.setInt(1, reason.getId());
                                update.setInt(2, reaction.getId());
                                update.executeUpdate();
                                update.close();
                            }
                        }
                    }
                    insertStatement.close();
                    updateStatement.close();
                    insertTracesStatement.close();
                    break;
            }
        } catch (SQLException e) {
            throw new DatabaseServiceException(e, e.getMessage(), false);
        } finally {
            returnConnection(connection);
        }
    }

    public int addAssembly(boolean isPass, String notes, String technician, FailureReason failureReason,
                           String failureNotes, boolean addChromatograms, AssembledSequence seq,
                           List<Integer> reactionIds, Cancelable cancelable) throws DatabaseServiceException {
        PreparedStatement statement = null;
        PreparedStatement statement2 = null;
        PreparedStatement updateReaction;
        //noinspection ConstantConditions
        ConnectionWrapper connection = null;
        try {
            connection = getConnection();
            statement = connection.prepareStatement("INSERT INTO assembly (extraction_id, workflow, progress, consensus, " +
                "coverage, disagreements, trim_params_fwd, trim_params_rev, edits, params, reference_seq_id, confidence_scores, other_processing_fwd, other_processing_rev, notes, technician, bin, ambiguities, editrecord, failure_reason, failure_notes) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,?,?)");
            updateReaction = connection.prepareStatement("INSERT INTO sequencing_result(assembly, reaction) VALUES(?,?)");

            statement2 = isLocal() ? connection.prepareStatement("CALL IDENTITY();") : connection.prepareStatement("SELECT last_insert_id()");

            if (cancelable.isCanceled()) {
                return -1;
            }
            statement.setString(1, seq.extractionId);
            statement.setInt(2, seq.workflowId);
            statement.setString(3, isPass ? "passed" : "failed");
            if (seq.consensus == null) {
                statement.setNull(4, Types.LONGVARCHAR);
            } else {
                statement.setString(4, seq.consensus);
            }
            if (seq.coverage == null) {
                statement.setNull(5, Types.FLOAT);
            } else {
                statement.setDouble(5, seq.coverage);
            }
            if (seq.numberOfDisagreements == null) {
                statement.setNull(6, Types.INTEGER);
            } else {
                statement.setInt(6, seq.numberOfDisagreements);
            }
            if (seq.forwardTrimParameters == null) {
                statement.setNull(7, Types.LONGVARCHAR);
            } else {
                statement.setString(7, seq.forwardTrimParameters);
            }
            if (seq.reverseTrimParameters == null) {
                statement.setNull(8, Types.LONGVARCHAR);
            } else {
                statement.setString(8, seq.reverseTrimParameters);
            }
            statement.setInt(9, seq.numOfEdits);
            if(seq.assemblyParameters != null) {
                statement.setString(10, seq.assemblyParameters); //params
            }
            else {
                statement.setNull(10, Types.LONGVARCHAR); //params
            }
            statement.setNull(11, Types.INTEGER); //reference_seq_id
            if(seq.confidenceScore != null) {
                statement.setString(12, seq.confidenceScore);
            }
            else {
                statement.setNull(12, Types.LONGVARCHAR); //confidence_scores
            }
            statement.setNull(13, Types.LONGVARCHAR); //other_processing_fwd
            statement.setNull(14, Types.LONGVARCHAR); //other_processing_rev

            statement.setString(15, notes); //notes


            //technician, date, bin, ambiguities
            statement.setString(16, technician);

            if(seq.bin != null) {
                statement.setString(17, seq.bin);
            }
            else {
                statement.setNull(17, Types.LONGVARCHAR);
            }
            if(seq.numberOfAmbiguities != null) {
                statement.setInt(18, seq.numberOfAmbiguities);
            }
            else {
                statement.setNull(18, Types.INTEGER);
            }
            statement.setString(19, seq.editRecord);
            if(failureReason == null) {
                statement.setObject(20, null);
            } else {
                statement.setInt(20, failureReason.getId());
            }
            if(failureNotes == null) {
                statement.setObject(21, null);
            } else {
                statement.setString(21, failureNotes);
            }

            statement.execute();

            ResultSet resultSet = statement2.executeQuery();
            resultSet.next();
            int sequenceId = resultSet.getInt(1);
            updateReaction.setObject(1,sequenceId);
            for(int reactionId: reactionIds) {
                updateReaction.setObject(2, reactionId);
                updateReaction.executeUpdate();
            }

            return sequenceId;

        } catch (SQLException e) {
            throw new DatabaseServiceException(e, "Failed to park as pass/fail in LIMS: " + e.getMessage(), false);
        } finally {
            try {
                if(statement != null)
                    statement.close();
                if(statement2 != null)
                    statement2.close();
            } catch (SQLException e) {
                // If we failed to close statements, we'll have to let the garbage collector handle it
            }
            returnConnection(connection);
        }
    }

    @Override
    public boolean deleteAllowed(String tableName) {
        if(isLocal() || getUsername().toLowerCase().equals("root")) {
            return true;
        }

        try {
            //check schema privileges
            String schemaSql = "select * from information_schema.SCHEMA_PRIVILEGES WHERE " +
                    "GRANTEE LIKE ? AND " +
                    "PRIVILEGE_TYPE='DELETE' AND " +
                    "(TABLE_SCHEMA=? OR TABLE_SCHEMA='%');";
            PreparedStatement statement = createStatement(schemaSql);
            statement.setString(1, "'"+getUsername()+"'@%");
            statement.setString(2, getSchema());
            ResultSet resultSet = statement.executeQuery();
            if(resultSet.next()) {
                return true;
            }
            resultSet.close();

            //check table privileges
            String tableSql = "select * from information_schema.TABLE_PRIVILEGES WHERE " +
                    "GRANTEE LIKE ? AND " +
                    "PRIVILEGE_TYPE='DELETE' AND " +
                    "(TABLE_SCHEMA=? OR TABLE_SCHEMA='%') AND " +
                    "(TABLE_NAME=? OR TABLE_NAME='%');";
            statement = createStatement(tableSql);
            statement.setString(1, "'"+getUsername()+"'@%");
            statement.setString(2, getSchema());
            statement.setString(3, tableName);
            resultSet = statement.executeQuery();
            if(resultSet.next()) {
                return true;
            }
            resultSet.close();
        }
        catch(SQLException ex) {
            // todo
            ex.printStackTrace();
            assert false : ex.getMessage();
            // there could be a number of errors here due to the user not having privileges to access the schema information,
            // so I don't want to halt on this error as it could stop the user from deleting when they are actually allowed...
        }

        //Can't find privileges...
        return false;
    }

    public Set<Integer> deleteRecords(String tableName, String term, Iterable ids) throws DatabaseServiceException {
        if (!BiocodeService.getInstance().deleteAllowed(tableName)) {
            throw new DatabaseServiceException("It appears that you do not have permission to delete from " + tableName + ".  Please contact your System Administrator for assistance", false);
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

        PreparedStatement getPlatesStatement = null;
        PreparedStatement deleteStatement = null;
        try {
            Set<Integer> plateIds = new HashSet<Integer>();
            if (tableName.equals("extraction") || tableName.equals("pcr") || tableName.equals("cyclesequencing")) {
                getPlatesStatement = createStatement("SELECT plate FROM " + tableName + " WHERE " + termString);
                ResultSet resultSet = getPlatesStatement.executeQuery();
                while (resultSet.next()) {
                    plateIds.add(resultSet.getInt("plate"));
                }
            }

            deleteStatement = createStatement("DELETE FROM " + tableName + " WHERE " + termString);
            deleteStatement.executeUpdate();

            return plateIds;
        } catch (SQLException e) {
            throw new DatabaseServiceException(e, e.getMessage(), false);
        } finally {
            SqlUtilities.cleanUpStatements(getPlatesStatement, deleteStatement);
        }
    }

    @Override
    public void addCocktails(List<? extends Cocktail> cocktails) throws DatabaseServiceException {
        ConnectionWrapper connection = null;
        try {
            connection = getConnection();
            for(Cocktail cocktail : cocktails) {
                connection.executeUpdate(cocktail.getSQLString());
            }
            connection.close();
        } catch (SQLException e) {
            throw new DatabaseServiceException(e, e.getMessage(), false);
        } finally {
            returnConnection(connection);
        }
    }

    @Override
    public void deleteCocktails(List<? extends Cocktail> deletedCocktails) throws DatabaseServiceException {
        ConnectionWrapper connection = null;
        try {
            connection = getConnection();
            if(!BiocodeService.getInstance().deleteAllowed("cocktail")) {
                throw new DatabaseServiceException("It appears that you do not have permission to delete cocktails.  Please contact your System Administrator for assistance", false);
            }
            for(Cocktail cocktail : deletedCocktails) {
                String sql = "DELETE FROM "+cocktail.getTableName()+" WHERE id = ?";
                PreparedStatement statement = connection.prepareStatement(sql);
                if(cocktail.getId() >= 0) {
                    if(getPlatesUsingCocktail(cocktail.getReactionType(), cocktail.getId()).size() > 0) {
                        throw new DatabaseServiceException("The cocktail "+cocktail.getName()+" is in use by reactions in your database.  Only unused cocktails can be removed.", false);
                    }
                    statement.setInt(1, cocktail.getId());
                    statement.executeUpdate();
                }
            }
        } catch (SQLException e) {
            throw new DatabaseServiceException(e, "Could not delete cocktails: "+e.getMessage(), false);
        } finally {
            returnConnection(connection);
        }
    }

    public List<PCRCocktail> getPCRCocktailsFromDatabase() throws DatabaseServiceException {
        List<PCRCocktail> cocktails = new ArrayList<PCRCocktail>();
        ConnectionWrapper connection = null;
        try {
            connection = getConnection();
            ResultSet resultSet = connection.executeQuery("SELECT * FROM pcr_cocktail");
            while(resultSet.next()) {
                cocktails.add(new PCRCocktail(resultSet));
            }
            resultSet.getStatement().close();
        }
        catch(SQLException ex) {
            ex.printStackTrace();
            throw new DatabaseServiceException(ex, "Could not query PCR Cocktails from the database", false);
        } finally {
            returnConnection(connection);
        }
        return cocktails;
    }

    public List<CycleSequencingCocktail> getCycleSequencingCocktailsFromDatabase() throws DatabaseServiceException {
        ConnectionWrapper connection = null;

        List<CycleSequencingCocktail> cocktails = new ArrayList<CycleSequencingCocktail>();
        try {
            connection = getConnection();
            ResultSet resultSet = connection.executeQuery("SELECT * FROM cyclesequencing_cocktail");
            while(resultSet.next()) {
                cocktails.add(new CycleSequencingCocktail(resultSet));
            }
            resultSet.getStatement().close();
        }
        catch(SQLException ex) {
            throw new DatabaseServiceException(ex, "Could not query CycleSequencing Cocktails from the database", false);
        } finally {
            returnConnection(connection);
        }
        return cocktails;
    }

    public List<Thermocycle> getThermocyclesFromDatabase(Thermocycle.Type type) throws DatabaseServiceException {
        String sql = "SELECT * FROM "+type.databaseTable+" LEFT JOIN thermocycle ON (thermocycle.id = "+type.databaseTable+".cycle) LEFT JOIN cycle ON (thermocycle.id = cycle.thermocycleId) LEFT JOIN state ON (cycle.id = state.cycleId);";
        System.out.println(sql);

        List<Thermocycle> tCycles = new ArrayList<Thermocycle>();

        ConnectionWrapper connection = null;
        try {
            connection = getConnection();
            ResultSet resultSet = connection.executeQuery(sql);
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
        } catch(SQLException ex) {
            throw new DatabaseServiceException(ex, "could not read thermocycles from the database", false);
        } finally {
            returnConnection(connection);
        }

        return tCycles;
    }

    @Override
    public void addThermoCycles(Thermocycle.Type type, List<Thermocycle> cycles) throws DatabaseServiceException {
        ConnectionWrapper connection = null;
        try {
            connection = getConnection();
            connection.beginTransaction();
            for(Thermocycle tCycle : cycles) {
                int id = addThermoCycle(connection, tCycle);
                PreparedStatement statement = connection.prepareStatement("INSERT INTO " + type.databaseTable + " (cycle) VALUES (" + id + ");\n");
                statement.execute();
                statement.close();
            }
            connection.endTransaction();
        } catch (SQLException e) {
            throw new DatabaseServiceException(e, "Could not add thermocycle(s): "+e.getMessage(), false);
        } finally {
            returnConnection(connection);
        }
    }

    public static int addThermoCycle(ConnectionWrapper connection, Thermocycle thermocycle) throws SQLException, DatabaseServiceException {
        //create the thermocycle record
        PreparedStatement statement1 = connection.prepareStatement("INSERT INTO thermocycle (name, notes) VALUES (?, ?);\n");
        statement1.setString(1, thermocycle.getName());
        statement1.setString(2, thermocycle.getNotes());
        statement1.execute();
        statement1.close();

        //get the id of the thermocycle record
        PreparedStatement statement = BiocodeService.getInstance().getActiveLIMSConnection().isLocal() ?
                connection.prepareStatement("CALL IDENTITY();") :
                connection.prepareStatement("SELECT last_insert_id()");
        ResultSet resultSet = statement.executeQuery();
        resultSet.next();
        int thermoId = resultSet.getInt(1);
        statement.close();

        for(Thermocycle.Cycle cycle : thermocycle.getCycles()) {
            //create a cycle record
            PreparedStatement statement2 = connection.prepareStatement("INSERT INTO cycle (thermocycleid, repeats) VALUES (" + thermoId + ", " + cycle.getRepeats() + ");\n");
            statement2.execute();
            statement2.close();

            //get the id of the cycle record
            statement = BiocodeService.getInstance().getActiveLIMSConnection().isLocal() ?
                connection.prepareStatement("CALL IDENTITY();") :
                connection.prepareStatement("SELECT last_insert_id()");
            resultSet = statement.executeQuery();
            resultSet.next();
            int cycleId = resultSet.getInt(1);
            statement.close();

            for(Thermocycle.State state : cycle.getStates()) {
                //create the state record
                PreparedStatement statement3 = connection.prepareStatement("INSERT INTO state (cycleid, temp, length) VALUES (" + cycleId + ", " + state.getTemp() + ", " + state.getTime() + ");\n");
                statement3.execute();
                statement3.close();
            }
        }
        return thermoId;
    }

    @Override
    public void deleteThermoCycles(Thermocycle.Type type, List<Thermocycle> cycles) throws DatabaseServiceException {
        // todo Consider using cascading deletes to get rid of cycles so we can just take the ids
        String sql = "DELETE  FROM state WHERE state.id=?";
        String sql2 = "DELETE  FROM cycle WHERE cycle.id=?";
        String sql3 = "DELETE  FROM thermocycle WHERE thermocycle.id=?";
        String sql4 = "DELETE FROM "+type.databaseTable+" WHERE cycle =?";
        ConnectionWrapper connection = null;
        try {
            if(!BiocodeService.getInstance().deleteAllowed("state") ||
                !BiocodeService.getInstance().deleteAllowed("cycle") ||
                !BiocodeService.getInstance().deleteAllowed("thermocycle") ||
                !BiocodeService.getInstance().deleteAllowed(type.databaseTable)) {
                throw new DatabaseServiceException("It appears that you do not have permission to delete thermocycles.  Please contact your System Administrator for assistance", false);
            }

            connection = getConnection();

            final PreparedStatement statement = connection.prepareStatement(sql);
            final PreparedStatement statement2 = connection.prepareStatement(sql2);
            final PreparedStatement statement3 = connection.prepareStatement(sql3);
            final PreparedStatement statement4 = connection.prepareStatement(sql4);
            for(Thermocycle thermocycle : cycles) {
                if(thermocycle.getId() >= 0) {
                    if(getPlatesUsingThermocycle(thermocycle.getId()).size() > 0) {
                        throw new DatabaseServiceException("The thermocycle "+thermocycle.getName()+" is being used by plates in your database.  Only unused thermocycles can be removed", false);
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
            throw new DatabaseServiceException(e, "Could not delete thermocycles: "+e.getMessage(), false);
        } finally {
            returnConnection(connection);
        }
    }

    protected static class ConnectionWrapper {
        private Connection connection;
        private int transactionLevel = 0;
        private List<Statement> statements = new ArrayList<Statement>();
        private AtomicBoolean closed = new AtomicBoolean(false);

        protected ConnectionWrapper(Connection connection) {
            this.connection = connection;
        }

        Connection getInternalConnection() {
            return connection;
        }

        protected void beginTransaction() throws SQLException {
            if (transactionLevel == 0) {
                connection.setAutoCommit(false);
            }
            transactionLevel++;
        }

        protected void rollback() {
            try {
                connection.rollback();
                connection.setAutoCommit(true);
            } catch (SQLException ex) {/*if we can't rollback, let's ignore*/}
            transactionLevel = 0;
        }

        protected void endTransaction() throws SQLException {
            if (transactionLevel == 0) {
                return;  //we've rolled back our changes by calling rollback() so no commits are necessary
            }
            transactionLevel--;
            if (transactionLevel == 0) {
                connection.commit();
                connection.setAutoCommit(true);
            }
        }

        protected void close() throws SQLException {
            rollback();
            SqlUtilities.cleanUpStatements(statements.toArray(new Statement[statements.size()]));
            connection.close();
            closed.set(true);
        }

        private static void closeConnection(ConnectionWrapper connection) {
            if(connection != null) {
                try {
                    connection.close();
                } catch (SQLException e) {
                    // Let garbage collector clean up
                }
            }
        }

        protected PreparedStatement prepareStatement(String query) throws SQLException {
            PreparedStatement statement = connection.prepareStatement(query);
            statement.setQueryTimeout(BiocodeService.STATEMENT_QUERY_TIMEOUT);
            return statement;
        }

        protected void executeUpdate(String sql) throws SQLException {
            Statement statement = connection.createStatement();
            try {
                statement.executeUpdate(sql);
            } finally {
                statement.close();
            }
        }

        protected ResultSet executeQuery(String sql) throws SQLException {
            Statement statement = connection.createStatement();
            statements.add(statement);
            return statement.executeQuery(sql);
        }

        public boolean isClosed() {
            return closed.get();
        }
    }

    @Override
    public void deleteSequences(List<Integer> sequencesToDelete) throws DatabaseServiceException {
        if (!sequencesToDelete.isEmpty()) {
            StringBuilder sql = new StringBuilder("DELETE FROM assembly WHERE (");
            for (int i1 = 0; i1 < sequencesToDelete.size(); i1++) {
                sql.append("id=?");
                if(i1 < sequencesToDelete.size()-1) {
                    sql.append(" OR ");
                }
            }
            sql.append(")");
            ConnectionWrapper connection = null;
            try {
                connection = getConnection();
                PreparedStatement statement = connection.prepareStatement(sql.toString());

                for (int i1 = 0; i1 < sequencesToDelete.size(); i1++) {
                    Integer i = sequencesToDelete.get(i1);
                    statement.setInt(i1+1, i);
                }

                int notDeletedCount = sequencesToDelete.size() - statement.executeUpdate();
                if(notDeletedCount > 0) {
                    throw new DatabaseServiceException(notDeletedCount + " sequences were not deleted.", false);
                }
            } catch(SQLException e) {
                throw new DatabaseServiceException(e, "Could not delete sequences: "+e.getMessage(), true);
            } finally {
                returnConnection(connection);
            }
        }
    }

    @Override
    public void deleteSequencesForWorkflowId(Integer workflowId, String extractionId) throws DatabaseServiceException {
        ConnectionWrapper connection = null;
        try {
            connection = getConnection();
            String sql = "DELETE FROM assembly WHERE workflow=? AND extraction_id = ?";
            PreparedStatement statement = connection.prepareStatement(sql);
            statement.setInt(1, workflowId);
            statement.setString(2, extractionId);
            statement.executeUpdate();
        } catch(SQLException e) {
            throw new DatabaseServiceException(e, e.getMessage(), false);
        } finally {
            returnConnection(connection);
        }
    }

    @Override
    public void setSequenceStatus(boolean submitted, List<Integer> ids) throws DatabaseServiceException {
        ConnectionWrapper connection = null;
        try {
            connection = getConnection();
            StringBuilder query = new StringBuilder("SELECT COUNT(id) FROM assembly WHERE progress=? AND id IN ");
            SqlUtilities.appendSetOfQuestionMarks(query, ids.size());
            PreparedStatement statement1 = connection.prepareStatement(query.toString());
            for(int i=0; i < ids.size(); i++) {
                statement1.setInt(i+1, ids.get(i));
            }
            statement1.setString(ids.size()+1, "passed");
            ResultSet set = statement1.executeQuery();
            set.next();
            int count = set.getInt(1);

            if(count < ids.size()) {
                throw new DatabaseServiceException("Some of the sequences you are marking are either not present in the database, or are marked as failed.  Please make sure that the sequences are present, and are passed before marking as submitted.", false);
            }

            StringBuilder updateString = new StringBuilder("UPDATE assembly SET submitted = ? WHERE id IN ");
            SqlUtilities.appendSetOfQuestionMarks(updateString, ids.size());

            PreparedStatement statement2 = connection.prepareStatement(updateString.toString());
            statement2.setInt(1, submitted ? 1 : 0);
            for(int i=0; i < ids.size(); i++) {
                statement2.setInt(i+2, ids.get(i));
            }
            statement2.executeUpdate();

        } catch (SQLException e) {
            throw new DatabaseServiceException(e, "There was a problem marking as submitted in LIMS: "+e.getMessage(), false);
        } finally {
            returnConnection(connection);
        }
    }

    @Override
    public Map<String, String> getTissueIdsForExtractionIds(String tableName, List<String> extractionIds) throws DatabaseServiceException {
        String tableDefinition = tableName.equals("extraction") ? tableName : tableName+", extraction, workflow";
        String notExtractionBit = tableName.equals("extraction") ? "" : " workflow.extractionId = extraction.id AND " + tableName + ".workflow = workflow.id AND";
        StringBuilder sql = new StringBuilder("SELECT extraction.extractionId AS extractionId, extraction.sampleId AS tissue FROM " + tableDefinition + " WHERE" + notExtractionBit + " (");

        int count = 0;
        for (String extractionId : extractionIds) {
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

        ConnectionWrapper connection = null;
        try {
            connection = getConnection();
            PreparedStatement statement = connection.prepareStatement(sql.toString());
            int reactionCount = 1;
            for (String extractionId : extractionIds) {
                statement.setString(reactionCount, extractionId);
                reactionCount++;
            }

            ResultSet resultSet = statement.executeQuery();

            Map<String, String> results = new HashMap<String, String>();
            while(resultSet.next()) {
                results.put(resultSet.getString("extractionId"), resultSet.getString("tissue"));
            }

            statement.close();
            return results;
        } catch (SQLException e) {
            throw new DatabaseServiceException(e, e.getMessage(), false);
        } finally {
            returnConnection(connection);
        }
    }

    @Override
    public Map<Integer, List<MemoryFile>> downloadTraces(List<Integer> reactionIds, ProgressListener progressListener) throws DatabaseServiceException {
        ConnectionWrapper connection = null;
        try {
            connection = getConnection();

            StringBuilder getCountQuery = new StringBuilder("SELECT COUNT(id) FROM traces WHERE reaction IN ");
            SqlUtilities.appendSetOfQuestionMarks(getCountQuery, reactionIds.size());
            PreparedStatement getCount = connection.prepareStatement(getCountQuery.toString());

            StringBuilder getTracesQuery = new StringBuilder("SELECT * FROM traces WHERE reaction IN ");
            SqlUtilities.appendSetOfQuestionMarks(getTracesQuery, reactionIds.size());
            PreparedStatement getTraces = connection.prepareStatement(getTracesQuery.toString());
            int index = 1;
            for (int reactionId : reactionIds) {
                getCount.setObject(index, reactionId);
                getTraces.setObject(index++, reactionId);
            }

            if(!BiocodeService.getInstance().getActiveLIMSConnection().isLocal()) {
                getCount.setFetchSize(Integer.MIN_VALUE);
            }
            SqlUtilities.printSql(getCountQuery.toString(), reactionIds);
            ResultSet countResultSet = getCount.executeQuery();
            countResultSet.next();
            int count = countResultSet.getInt(1);
            countResultSet.close();

            SqlUtilities.printSql(getTracesQuery.toString(), reactionIds);
            ResultSet resultSet = getTraces.executeQuery();
            Map<Integer, List<MemoryFile>> results = new HashMap<Integer, List<MemoryFile>>();
            double pos = 0;
            long bytes = 0;
            while(resultSet.next()) {
                if (progressListener.isCanceled()) break;
                progressListener.setMessage("downloaded "+BiocodeUtilities.formatSize(bytes, 2));
                progressListener.setProgress(pos/count);
                pos++;
                MemoryFile memoryFile = new MemoryFile(resultSet.getString("name"), resultSet.getBytes("data"));
                bytes += memoryFile.getData().length;
                int id = resultSet.getInt("reaction");
                List<MemoryFile> files = results.get(id);
                if(files == null) {
                    files = new ArrayList<MemoryFile>();
                    results.put(id, files);
                }
                files.add(memoryFile);
            }
            resultSet.close();
            return results;
        } catch (SQLException e) {
            throw new DatabaseServiceException(e, e.getMessage(), false);
        } finally {
            returnConnection(connection);
        }

    }

    @Override
    public List<ExtractionReaction> getExtractionsForIds(List<String> extractionIds) throws DatabaseServiceException {
        List<ExtractionReaction> extractionsThatExist = new ArrayList<ExtractionReaction>();
        if(extractionIds.isEmpty()) {
            return extractionsThatExist;
        }

        StringBuilder sql = new StringBuilder("SELECT * FROM extraction, plate WHERE plate.id = extraction.plate AND extraction.extractionId IN ");
        SqlUtilities.appendSetOfQuestionMarks(sql, extractionIds.size());

        ConnectionWrapper connection = null;

        try {
            connection = getConnection();
            PreparedStatement statement = connection.prepareStatement(sql.toString());
            int count=1;
            for(String extractionId : extractionIds) {
                statement.setString(count++, extractionId);
            }
            ResultSet results = statement.executeQuery();
            while(results.next()) {
                extractionsThatExist.add(new ExtractionReaction(results));
            }
            statement.close();
            return extractionsThatExist;
        } catch (SQLException e) {
            throw new DatabaseServiceException(e, e.getMessage(), false);
        } finally {
            returnConnection(connection);
        }
    }

    @Override
    public boolean supportReporting() {
        return true;
    }
}