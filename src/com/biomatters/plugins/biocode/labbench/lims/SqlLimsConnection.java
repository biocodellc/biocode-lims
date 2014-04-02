package com.biomatters.plugins.biocode.labbench.lims;

import com.biomatters.geneious.publicapi.components.Dialogs;
import com.biomatters.geneious.publicapi.databaseservice.*;
import com.biomatters.geneious.publicapi.documents.*;
import com.biomatters.geneious.publicapi.documents.sequence.DefaultNucleotideGraph;
import com.biomatters.geneious.publicapi.documents.sequence.NucleotideGraph;
import com.biomatters.geneious.publicapi.documents.sequence.NucleotideSequenceDocument;
import com.biomatters.geneious.publicapi.implementations.sequence.DefaultNucleotideGraphSequence;
import com.biomatters.geneious.publicapi.implementations.sequence.DefaultNucleotideSequence;
import com.biomatters.geneious.publicapi.plugin.DocumentOperationException;
import com.biomatters.geneious.publicapi.plugin.DocumentSelectionOption;
import com.biomatters.geneious.publicapi.plugin.Options;
import com.biomatters.geneious.publicapi.utilities.FileUtilities;
import com.biomatters.geneious.publicapi.utilities.StringUtilities;
import com.biomatters.geneious.publicapi.utilities.SystemUtilities;
import com.biomatters.geneious.publicapi.utilities.ThreadUtilities;
import com.biomatters.plugins.biocode.BiocodeUtilities;
import com.biomatters.plugins.biocode.assembler.BatchChromatogramExportOperation;
import com.biomatters.plugins.biocode.assembler.annotate.AnnotateUtilities;
import com.biomatters.plugins.biocode.assembler.annotate.FimsData;
import com.biomatters.plugins.biocode.assembler.annotate.FimsDataGetter;
import com.biomatters.plugins.biocode.assembler.lims.AddAssemblyResultsToLimsOperation;
import com.biomatters.plugins.biocode.assembler.lims.AddAssemblyResultsToLimsOptions;
import com.biomatters.plugins.biocode.labbench.*;
import com.biomatters.plugins.biocode.labbench.fims.SqlUtilities;
import com.biomatters.plugins.biocode.labbench.plates.GelImage;
import com.biomatters.plugins.biocode.labbench.plates.Plate;
import com.biomatters.plugins.biocode.labbench.reaction.*;
import jebl.util.Cancelable;
import jebl.util.CompositeProgressListener;
import jebl.util.ProgressListener;
import org.apache.commons.dbcp.*;

import java.io.File;
import java.io.IOException;
import java.sql.*;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.Date;

/**
 * An SQL based {@link LIMSConnection}
 *
 * @author Matthew Cheung
 * @version $Id$
 *          <p/>
 *          Created on 1/04/14 4:45 PM
 */
public abstract class SqlLimsConnection extends LIMSConnection {

    private Connection connection;

    @Override
    public abstract PasswordOptions getConnectionOptions();

    @Override
    public abstract boolean isLocal();

    @Override
    public abstract String getUsername();

    @Override
    public abstract String getSchema();

    abstract BasicDataSource connectToDb(Options connectionOptions) throws ConnectionException;

    private BasicDataSource dataSource;
    @Override
    protected void _connect(PasswordOptions options) throws ConnectionException {
        LimsConnectionOptions allLimsOptions = (LimsConnectionOptions) options;
        PasswordOptions selectedLimsOptions = allLimsOptions.getSelectedLIMSOptions();
        dataSource = connectToDb(selectedLimsOptions);
        try {
            connection = getConnection().connection;
        } catch (SQLException e) {
            throw new ConnectionException(e.getMessage(), e);
        }
        String errorMessage = verifyDatabaseVersionAndUpgradeIfNecessary();
        if (errorMessage != null) {
            throw new ConnectionException(errorMessage);
        }
    }

    @Override
    protected Connection getConnectionInternal() throws SQLException {
        return connection;
    }

    /**
     * @return A {@link com.biomatters.plugins.biocode.labbench.lims.SqlLimsConnection.ConnectionWrapper} from the
     * connection pool.  Should be closed after use with {@link com.biomatters.plugins.biocode.labbench.lims.SqlLimsConnection.ConnectionWrapper#closeConnection(com.biomatters.plugins.biocode.labbench.lims.SqlLimsConnection.ConnectionWrapper)}
     * or {@link com.biomatters.plugins.biocode.labbench.lims.SqlLimsConnection.ConnectionWrapper#close()}
     *
     * @throws SQLException if the connection could not be established
     */
    protected ConnectionWrapper getConnection() throws SQLException {
        return new ConnectionWrapper(dataSource.getConnection());
    }

    public void disconnect() {
        //we used to explicitly close the SQL connection, but this was causing crashes if the user logged out while a query was in progress.
        //now we remove all references to it and let the garbage collector close it when the queries have finished.
        connection = null;
        dataSource = null;
        serverUrn = null;
    }

    private List<FailureReason> failureReasons = Collections.emptyList();

    public List<FailureReason> getPossibleFailureReasons() {
        return failureReasons;
    }

    public void doAnyExtraInitialziation() throws DatabaseServiceException {
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
            throw new DatabaseServiceException(e, "Failed to initialize database: " + e.getMessage(), false);
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

    protected String getFullVersionStringFromDatabase() throws SQLException, ConnectionException {
        ResultSet tableSet = connection.getMetaData().getTables(null, null, "%", null);
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
    }

    public LimsSearchResult getMatchingDocumentsFromLims(Query query, Collection<String> tissueIdsToMatch, RetrieveCallback callback, boolean downloadTissues) throws DatabaseServiceException {
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

        boolean onlySearchingOnFIMSFields = workflowPart == null && extractionPart == null && platePart == null && assemblyPart == null;
        if (!downloadWorkflows && !downloadPlates && !downloadSequences) {
            if (!downloadTissues || onlySearchingOnFIMSFields) {
                return result;
            }
        }
        boolean searchedForSamplesButFoundNone = tissueIdsToMatch != null && tissueIdsToMatch.isEmpty();  // samples == null when doing a browse query
        if (searchedForSamplesButFoundNone && onlySearchingOnFIMSFields) {
            return result;
        }

        StringBuilder workflowQuery = constructWorkflowQueryString(downloadTissues || downloadWorkflows,
                downloadSequences, tissueIdsToMatch, operator,
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

            queryResult = createPlateAndWorkflowsFromResultSet(callback, resultSet, downloadTissues || downloadWorkflows,
                    downloadTissues || downloadPlates, downloadSequences);
        } catch (SQLException e) {
            throw new DatabaseServiceException(e, e.getMessage(), false);
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
     * @param downloadWorkflows         True to download all workflow information.
     * @param downloadSequences         True to download matching assembly IDs.
     * @param tissueIdsToMatch          The samples to match
     * @param operator                  The {@link com.biomatters.geneious.publicapi.databaseservice.CompoundSearchQuery.Operator} to use for the query
     * @param workflowQueryConditions   Conditions to search workflow on
     * @param extractionQueryConditions Conditions to search extraction on
     * @param plateQueryConditions      Conditions to search plate on
     * @param assemblyQueryConditions   Conditions to search assembly on       @return A SQL string that can be used to query the MySQL LIMS
     */
    private StringBuilder constructWorkflowQueryString(boolean downloadWorkflows, boolean downloadSequences, Collection<String> tissueIdsToMatch, CompoundSearchQuery.Operator operator,
                                                       QueryPart workflowQueryConditions, QueryPart extractionQueryConditions,
                                                       QueryPart plateQueryConditions, QueryPart assemblyQueryConditions) {
        String operatorString = operator == CompoundSearchQuery.Operator.AND ? " AND " : " OR ";
        StringBuilder whereConditionForOrQuery = new StringBuilder();

        String columnsToRetrieve = "plate.*";
        if (downloadWorkflows) {
            columnsToRetrieve += ",extraction.*, workflow.*, pcr.*, cyclesequencing.*, assembly.progress, assembly.date, assembly.notes, assembly.failure_reason, assembly.failure_notes";
        }
        if (downloadWorkflows || downloadSequences) {
            columnsToRetrieve += ", assembly.id";
        }


        StringBuilder queryBuilder = new StringBuilder(
                "SELECT " + columnsToRetrieve);
        StringBuilder conditionBuilder = operator == CompoundSearchQuery.Operator.AND ? queryBuilder : whereConditionForOrQuery;

        queryBuilder.append("\nFROM ");

        queryBuilder.append("(\n\tSELECT workflow.id, extraction.id as ext FROM extraction");
        queryBuilder.append("\n\tLEFT OUTER JOIN ").append("workflow ON extraction.id = workflow.extractionId");
        if (tissueIdsToMatch != null && !tissueIdsToMatch.isEmpty()) {
            if (operator == CompoundSearchQuery.Operator.AND) {
                conditionBuilder.append(" AND ");
            }
            String sampleColumn = isLocal() ? "LOWER(sampleId)" : "sampleId";  // MySQL is case insensitive by default
            conditionBuilder.append(" (").append(sampleColumn).append(" IN ");
            appendSetOfQuestionMarks(conditionBuilder, tissueIdsToMatch.size());
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

        queryBuilder.append("\n\tLEFT OUTER JOIN ").append("pcr ON pcr.workflow = workflow.id ");
        queryBuilder.append("\n\tLEFT OUTER JOIN ").append("cyclesequencing ON cyclesequencing.workflow = workflow.id ");

        // INNER JOIN here because there should always be a plate for a reaction.  We have already joined the 3 reaction tables
        queryBuilder.append("\n\tINNER JOIN ").append("plate ON (extraction.plate = plate.id OR pcr.plate = plate.id OR cyclesequencing.plate = plate.id)");
        if (plateQueryConditions != null) {
            if (operator == CompoundSearchQuery.Operator.AND || extractionQueryConditions != null || workflowQueryConditions != null) {
                conditionBuilder.append(operatorString);
            }
            conditionBuilder.append("(").append(plateQueryConditions).append(")");
        }
        queryBuilder.append("\n\tLEFT OUTER JOIN sequencing_result ON cyclesequencing.id = sequencing_result.reaction ");
        queryBuilder.append("\n\t").append(operator == CompoundSearchQuery.Operator.AND && assemblyQueryConditions != null ? " INNER JOIN " : " LEFT OUTER JOIN ").
                append("assembly ON assembly.id = sequencing_result.assembly");

        if (assemblyQueryConditions != null) {
            conditionBuilder.append(operatorString);
            conditionBuilder.append("(").append(assemblyQueryConditions).append(")");
        }

        if (operator == CompoundSearchQuery.Operator.OR && whereConditionForOrQuery.length() > 0) {
            queryBuilder.append(" WHERE ");
            queryBuilder.append(whereConditionForOrQuery);
        }

        queryBuilder.append("\n\tGROUP BY workflow.id, ext\n) matching ");
        queryBuilder.append("\nINNER JOIN extraction ON extraction.id = matching.ext ");
        queryBuilder.append("\nLEFT OUTER JOIN workflow ON workflow.id = matching.id ");
        queryBuilder.append("\nLEFT OUTER JOIN pcr ON pcr.workflow = workflow.id ");
        queryBuilder.append("\nLEFT OUTER JOIN cyclesequencing ON cyclesequencing.workflow = workflow.id ");
        queryBuilder.append("\nINNER JOIN plate ON (extraction.plate = plate.id OR pcr.plate = plate.id OR cyclesequencing.plate = plate.id) ");
        queryBuilder.append("\nLEFT OUTER JOIN sequencing_result ON cyclesequencing.id = sequencing_result.reaction ");
        queryBuilder.append("\nLEFT OUTER JOIN assembly ON assembly.id = sequencing_result.assembly ");

        queryBuilder.append("\nORDER BY workflow.id, assembly.date desc");
        return queryBuilder;
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

    private static final Map<String, List<DocumentField>> TABLE_TO_FIELDS = new HashMap<String, List<DocumentField>>();

    static {
        TABLE_TO_FIELDS.put("workflow", Arrays.asList(WORKFLOW_NAME_FIELD, WORKFLOW_DATE_FIELD, WORKFLOW_LOCUS_FIELD));
        TABLE_TO_FIELDS.put("plate", Arrays.asList(PLATE_TYPE_FIELD, PLATE_NAME_FIELD, PLATE_DATE_FIELD));
        TABLE_TO_FIELDS.put("extraction", Arrays.asList(EXTRACTION_ID_FIELD, EXTRACTION_BARCODE_FIELD, DATE_FIELD));
        TABLE_TO_FIELDS.put("assembly", Arrays.asList(SEQUENCE_PROGRESS, SEQUENCE_SUBMISSION_PROGRESS, EDIT_RECORD, ASSEMBLY_TECHNICIAN, DATE_FIELD));
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
        Map<Integer, Plate> plates = createPlateAndWorkflowsFromResultSet(cancelable, resultSet, false, true, false).plates;
        if (callback != null) {
            for (Plate plate : plates.values()) {
                System.out.println("Adding " + plate.getName());
                callback.add(new PlateDocument(plate), Collections.<String, Object>emptyMap());
            }
        }
        return plates;
    }

    private WorkflowsAndPlatesQueryResult createPlateAndWorkflowsFromResultSet(Cancelable cancelable, ResultSet resultSet, boolean createWorkflows, boolean createPlates, boolean collectSequenceIds) throws SQLException, DatabaseServiceException {
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
            if (!resultSet.wasNull()) {
                plateIds.add(plateId);
            }

            if (collectSequenceIds) {
                int sequenceId = resultSet.getInt("assembly.id");
                if (!resultSet.wasNull()) {
                    result.sequenceIds.add(sequenceId);
                }
            }

            if (createWorkflows) {
                int workflowId = resultSet.getInt("workflow.id");
                String workflowName = resultSet.getString("workflow.name");
                if (workflowName != null) {  // null name means there is no workflow
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

        if (createPlates && !plateIds.isEmpty()) {
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
     * Sets a database wide property.  Can be retrieved by calling {@link #getProperty(String)}
     *
     * @param key   The name of the property
     * @param value The value to set for the property
     * @throws SQLException if something goes wrong communicating with the database.
     */
    void setProperty(String key, String value) throws DatabaseServiceException {
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
        } catch (SQLException e) {
            throw new DatabaseServiceException(e, e.getMessage(), false);
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
    String getProperty(String key) throws DatabaseServiceException {
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
        } catch (SQLException e) {
            throw new DatabaseServiceException(e, e.getMessage(), false);
        } finally {
            SqlUtilities.cleanUpStatements(get);
        }
    }

    public Set<Integer> deleteWorkflows(ProgressListener progress, Plate plate) throws DatabaseServiceException {
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

        Statement statement = null;
        try {
            statement = createStatement();


            ResultSet resultSet = statement.executeQuery(getWorkflowSQL);
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
            SqlUtilities.cleanUpStatements(statement);
        }
    }

    public void testConnection() throws DatabaseServiceException {
        try {
            createStatement().execute(isLocal() ? "SELECT * FROM databaseversion" : "SELECT 1"); //because JDBC doesn't have a better way of checking whether a connection is enabled
        } catch (SQLException e) {
            throw new DatabaseServiceException(e, e.getMessage(), false);
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
            List<Integer> sequenceIds, RetrieveCallback callback, boolean includeFailed) throws DatabaseServiceException {
        if (sequenceIds.isEmpty()) {
            return Collections.emptyList();
        }
        List<Object> sqlValues = new ArrayList<Object>(sequenceIds);

        StringBuilder sql = new StringBuilder("SELECT workflow.locus, assembly.*, extraction.sampleId, extraction.extractionId, extraction.extractionBarcode ");
        sql.append("FROM workflow INNER JOIN assembly ON assembly.id IN ");
        appendSetOfQuestionMarks(sql, sequenceIds.size());
        if (!includeFailed) {
            sql.append(" AND assembly.progress = ?");
            sqlValues.add("passed");
        }
        sql.append(" AND workflow.id = assembly.workflow INNER JOIN extraction ON workflow.extractionId = extraction.id");

        return getMatchingAssemblyDocumentsForSQL(workflows, samples,
                callback, new URN[0], callback, sql.toString(), sqlValues);
    }

    private List<AnnotatedPluginDocument> getMatchingAssemblyDocumentsForSQL(final Collection<WorkflowDocument> workflows, final List<FimsSample> fimsSamples, RetrieveCallback callback, URN[] urnsToNotRetrieve, Cancelable cancelable, String sql, List<Object> sqlValues) throws DatabaseServiceException {

        if (!BiocodeService.getInstance().isLoggedIn()) {
            return Collections.emptyList();
        }
        PreparedStatement statement = null;
        try {
            statement = createStatement(sql);
            fillStatement(sqlValues, statement);
            SqlUtilities.printSql(sql, sqlValues);
            BiocodeUtilities.CancelListeningThread listeningThread = null;
            if (cancelable != null) {
                //todo: listeningThread = new BiocodeUtilities.CancelListeningThread(cancelable, statement);
            }
            if (!isLocal()) {
                statement.setFetchSize(Integer.MIN_VALUE);
            }

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
            throw new DatabaseServiceException(e, e.getMessage(), false);
        } catch (ConnectionException e) {
            throw new DatabaseServiceException(e, e.getMessage(), false);
        } catch (SQLException e) {
            throw new DatabaseServiceException(e, e.getMessage(), false);
        } finally {
            SqlUtilities.cleanUpStatements(statement);
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

    public void createOrUpdatePlate(Plate plate, ProgressListener progress) throws DatabaseServiceException {
        try {
            //check the vaidity of the plate.
            isPlateValid(plate);

            beginTransaction();

            //update the plate
            PreparedStatement statement = plate.toSQL(this);
            statement.execute();
            statement.close();
            if(plate.getId() < 0) {
                PreparedStatement statement1 = isLocal() ? createStatement("CALL IDENTITY();") : createStatement("SELECT last_insert_id()");
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
                PreparedStatement deleteImagesStatement = createStatement("DELETE FROM gelimages WHERE plate="+plate.getId());
                deleteImagesStatement.execute();
                for(GelImage image : plate.getImages()) {
                    PreparedStatement statement1 = image.toSql(this);
                    statement1.execute();
                    statement1.close();
                }
                deleteImagesStatement.close();
            }

            saveReactions(plate.getReactions(), plate.getReactionType(), progress);

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
            Statement workflowUpdateStatement = createStatement();
            workflowUpdateStatement.executeUpdate(sql);
            workflowUpdateStatement.close();
        } catch(SQLException e) {
            rollback();
            throw new DatabaseServiceException(e, e.getMessage(), false);
        } finally {
            try {
                endTransaction();
            } catch (SQLException e) {
                throw new DatabaseServiceException(e, e.getMessage(), false);
            }
        }
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

        String sql = "SELECT * FROM plate WHERE (plate.id NOT IN (select plate from extraction)) AND (plate.id NOT IN (select plate from pcr)) AND (plate.id NOT IN (select plate from cyclesequencing))";


        List<String> idMatches = new ArrayList<String>();
        for(Integer num : plateIds) {
            idMatches.add("id="+num);
        }

        String termString = StringUtilities.join(" OR ", idMatches);
        if(termString.length() > 0) {
            sql += " AND ("+termString+")";
        }

        try {
            ResultSet resultSet = createStatement().executeQuery(sql);
            List<Plate> result = new ArrayList<Plate>();
            while(resultSet.next()) {
                Plate plate = new Plate(resultSet);
                plate.initialiseReactions();
                result.add(plate);
            }
            return result;
        } catch (SQLException e) {
            throw new DatabaseServiceException(e, e.getMessage(), false);
        }
    }

    public void saveReactions(Reaction[] reactions, Reaction.Type type, ProgressListener progress) throws DatabaseServiceException {

        try {
            PreparedStatement getLastId = BiocodeService.getInstance().getActiveLIMSConnection().isLocal() ?
                    createStatement("CALL IDENTITY();") : createStatement("SELECT last_insert_id()");
            switch(type) {
                case Extraction:
                    String insertSQL;
                    String updateSQL;
                    insertSQL  = "INSERT INTO extraction (method, volume, dilution, parent, sampleId, extractionId, extractionBarcode, plate, location, notes, previousPlate, previousWell, date, technician, concentrationStored, concentration, gelimage, control) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
                    updateSQL  = "UPDATE extraction SET method=?, volume=?, dilution=?, parent=?, sampleId=?, extractionId=?, extractionBarcode=?, plate=?, location=?, notes=?, previousPlate=?, previousWell=?, date=?, technician=?, concentrationStored=?, concentration=?, gelImage=?, control=? WHERE id=?";

                    PreparedStatement insertStatement = createStatement(insertSQL);
                    PreparedStatement updateStatement = createStatement(updateSQL);
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
                    insertStatement = createStatement(insertSQL);
                    updateStatement = createStatement(updateSQL);
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

                    insertStatement = createStatement(insertSQL);
                    updateStatement = createStatement(updateSQL);
                    PreparedStatement clearTracesStatement = createStatement(clearTracesSQL);
                    PreparedStatement insertTracesStatement = createStatement(insertTracesSQL);
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
                                        ReactionUtilities.MemoryFile file = trace.getFile();
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
                                PreparedStatement update = createStatement(
                                        "UPDATE assembly SET failure_reason = ? WHERE id IN (" +
                                            "SELECT assembly FROM sequencing_result WHERE reaction = ?" +
                                        ")");
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
        }
    }


    public Map<URN, String> addAssembly(AddAssemblyResultsToLimsOptions options, CompositeProgressListener progress, Map<URN, AddAssemblyResultsToLimsOperation.AssemblyResult> assemblyResults, boolean isPass) throws DatabaseServiceException {
        Map<URN, String> toReturn = new HashMap<URN, String>(assemblyResults.size());

        PreparedStatement statement = null;
        PreparedStatement statement2 = null;
        PreparedStatement updateReaction;
        //noinspection ConstantConditions
        try {
            statement = createStatement("INSERT INTO assembly (extraction_id, workflow, progress, consensus, " +
                "coverage, disagreements, trim_params_fwd, trim_params_rev, edits, params, reference_seq_id, confidence_scores, other_processing_fwd, other_processing_rev, notes, technician, bin, ambiguities, editrecord, failure_reason, failure_notes) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,?,?)");
            updateReaction = createStatement("INSERT INTO sequencing_result(assembly, reaction) VALUES(?,?)");

            statement2 = isLocal() ? createStatement("CALL IDENTITY();") : createStatement("SELECT last_insert_id()");
            for (Map.Entry<URN, AddAssemblyResultsToLimsOperation.AssemblyResult> resultEntry : assemblyResults.entrySet()) {
                AddAssemblyResultsToLimsOperation.AssemblyResult result = resultEntry.getValue();
                progress.beginSubtask();
                if (progress.isCanceled()) {
                    return Collections.emptyMap();
                }
                statement.setString(1, result.extractionId);
                statement.setInt(2, result.workflowId);
                statement.setString(3, isPass ? "passed" : "failed");
                if (result.consensus == null) {
                    statement.setNull(4, Types.LONGVARCHAR);
                } else {
                    statement.setString(4, result.consensus);
                }
                if (result.coverage == null) {
                    statement.setNull(5, Types.FLOAT);
                } else {
                    statement.setDouble(5, result.coverage);
                }
                if (result.disagreements == null) {
                    statement.setNull(6, Types.INTEGER);
                } else {
                    statement.setInt(6, result.disagreements);
                }
                if (result.trims[0] == null) {
                    statement.setNull(7, Types.LONGVARCHAR);
                } else {
                    statement.setString(7, result.trims[0]);
                }
                if (result.trims[1] == null) {
                    statement.setNull(8, Types.LONGVARCHAR);
                } else {
                    statement.setString(8, result.trims[1]);
                }
                statement.setInt(9, result.edits);
                String params = result.assemblyOptionValues;
                if(params != null) {
                    statement.setString(10, params); //params
                }
                else {
                    statement.setNull(10, Types.LONGVARCHAR); //params
                }
                statement.setNull(11, Types.INTEGER); //reference_seq_id
                if(result.qualities != null) {
                    List<Integer> qualitiesList = new ArrayList<Integer>();
                    for(int i : result.qualities) {
                        qualitiesList.add(i);
                    }
                    statement.setString(12, StringUtilities.join(",", qualitiesList));
                }
                else {
                    statement.setNull(12, Types.LONGVARCHAR); //confidence_scores
                }
                statement.setNull(13, Types.LONGVARCHAR); //other_processing_fwd
                statement.setNull(14, Types.LONGVARCHAR); //other_processing_rev

                statement.setString(15, options.getNotes()); //notes


                //technician, date, bin, ambiguities
                statement.setString(16, options.getTechnician());

                if(result.bin != null) {
                    statement.setString(17, result.bin);
                }
                else {
                    statement.setNull(17, Types.LONGVARCHAR);
                }
                if(result.ambiguities != null) {
                    statement.setInt(18, result.ambiguities);
                }
                else {
                    statement.setNull(18, Types.INTEGER);
                }
                statement.setString(19, result.editRecord);
                FailureReason reason = options.getFailureReason();
                if(reason == null) {
                    statement.setObject(20, null);
                } else {
                    statement.setInt(20, reason.getId());
                }
                String failNotes = options.getFailureNotes();
                if(failNotes == null) {
                    statement.setObject(21, null);
                } else {
                    statement.setString(21, failNotes);
                }

                statement.execute();

                ResultSet resultSet = statement2.executeQuery();
                resultSet.next();
                int sequenceId = resultSet.getInt(1);
                updateReaction.setObject(1,sequenceId);
                for(Map.Entry<CycleSequencingReaction, List<AnnotatedPluginDocument>> entry : result.getReactions().entrySet()) {
                    updateReaction.setObject(2, entry.getKey().getId());
                    updateReaction.executeUpdate();
                    for(AnnotatedPluginDocument doc : entry.getValue()) {
                        doc.setFieldValue(SEQUENCE_ID, sequenceId);
                        doc.save();
                    }
                }

                BatchChromatogramExportOperation chromatogramExportOperation = new BatchChromatogramExportOperation();
                Options chromatogramExportOptions = null;
                File tempFolder;
                try {
                    tempFolder = FileUtilities.createTempFile("chromat", ".ab1", true).getParentFile();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }

                for (Map.Entry<CycleSequencingReaction, List<AnnotatedPluginDocument>> entry : result.getReactions().entrySet()) {
                    if (options.isAddChromatograms()) {
                        if (chromatogramExportOptions == null) {
                            chromatogramExportOptions = chromatogramExportOperation.getOptions(entry.getValue());
                            chromatogramExportOptions.setValue("exportTo", tempFolder.toString());
                        }
                        List<Trace> traces = new ArrayList<Trace>();
                        for (AnnotatedPluginDocument chromatogramDocument : entry.getValue()) {
                            chromatogramExportOperation.performOperation(new AnnotatedPluginDocument[] {chromatogramDocument}, ProgressListener.EMPTY, chromatogramExportOptions);
                            File exportedFile = new File(tempFolder, chromatogramExportOperation.getFileNameUsedFor(chromatogramDocument));
                            try {
                                traces.add(new Trace(Arrays.asList((NucleotideSequenceDocument) chromatogramDocument.getDocument()), ReactionUtilities.loadFileIntoMemory(exportedFile)));
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                        }
                        entry.getKey().addSequences(traces);
                    }
                    entry.getKey().getOptions().setValue(ReactionOptions.RUN_STATUS, isPass ? ReactionOptions.PASSED_VALUE : ReactionOptions.FAILED_VALUE);
                }

                Set<CycleSequencingReaction> reactionSet = result.getReactions().keySet();
                saveReactions(reactionSet.toArray(new Reaction[reactionSet.size()]), Reaction.Type.CycleSequencing, null);

                toReturn.put(resultEntry.getKey(), ""+sequenceId);
            }
            return toReturn;
        } catch (SQLException e) {
            throw new DatabaseServiceException(e, "Failed to park as pass/fail in LIMS: " + e.getMessage(), false);
        } catch (DocumentOperationException e) {
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
            ConnectionWrapper.closeConnection(connection);
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
                    if(getPlatesUsingCocktail(cocktail).size() > 0) {
                        throw new DatabaseServiceException("The cocktail "+cocktail.getName()+" is in use by reactions in your database.  Only unused cocktails can be removed.", false);
                    }
                    statement.setInt(1, cocktail.getId());
                    statement.executeUpdate();
                }
            }
        } catch (SQLException e) {
            throw new DatabaseServiceException(e, "Could not delete cocktails: "+e.getMessage(), false);
        } finally {
            ConnectionWrapper.closeConnection(connection);
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
            ConnectionWrapper.closeConnection(connection);
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
            ConnectionWrapper.closeConnection(connection);
        }
        return cocktails;
    }

    public List<Thermocycle> getThermocyclesFromDatabase(String thermocycleIdentifierTable) throws DatabaseServiceException {
        String sql = "SELECT * FROM "+thermocycleIdentifierTable+" LEFT JOIN thermocycle ON (thermocycle.id = "+thermocycleIdentifierTable+".cycle) LEFT JOIN cycle ON (thermocycle.id = cycle.thermocycleId) LEFT JOIN state ON (cycle.id = state.cycleId);";
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
            ConnectionWrapper.closeConnection(connection);
        }

        return tCycles;
    }

    @Override
    public void addThermoCycles(String tableName, List<Thermocycle> cycles) throws DatabaseServiceException {
        ConnectionWrapper connection = null;
        try {
            connection = getConnection();
            connection.beginTransaction();
            for(Thermocycle tCycle : cycles) {
                int id = tCycle.toSQL(this);
                PreparedStatement statement = createStatement("INSERT INTO "+tableName+" (cycle) VALUES ("+id+");\n");
                statement.execute();
                statement.close();
            }
            connection.endTransaction();
        } catch (SQLException e) {
            throw new DatabaseServiceException(e, "Could not add thermocycle(s): "+e.getMessage(), false);
        } finally {
            ConnectionWrapper.closeConnection(connection);
        }
    }

    @Override
    public void deleteThermoCycles(String tableName, List<Thermocycle> cycles) throws DatabaseServiceException {
        String sql = "DELETE  FROM state WHERE state.id=?";
        String sql2 = "DELETE  FROM cycle WHERE cycle.id=?";
        String sql3 = "DELETE  FROM thermocycle WHERE thermocycle.id=?";
        String sql4 = "DELETE FROM "+tableName+" WHERE cycle =?";
        ConnectionWrapper connection = null;
        try {
            if(!BiocodeService.getInstance().deleteAllowed("state") || !BiocodeService.getInstance().deleteAllowed("cycle") || !BiocodeService.getInstance().deleteAllowed("thermocycle") || !BiocodeService.getInstance().deleteAllowed(tableName)) {
                throw new DatabaseServiceException("It appears that you do not have permission to delete thermocycles.  Please contact your System Administrator for assistance", false);
            }

            connection = getConnection();

            final PreparedStatement statement = connection.prepareStatement(sql);
            final PreparedStatement statement2 = connection.prepareStatement(sql2);
            final PreparedStatement statement3 = connection.prepareStatement(sql3);
            final PreparedStatement statement4 = connection.prepareStatement(sql4);
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
            throw new DatabaseServiceException(e, "Could not delete thermocycles: "+e.getMessage(), false);
        } finally {
            ConnectionWrapper.closeConnection(connection);
        }
    }

    protected static class ConnectionWrapper {
        private Connection connection;
        private int transactionLevel = 0;
        private List<Statement> statements = new ArrayList<Statement>();

        protected ConnectionWrapper(Connection connection) {
            this.connection = connection;
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

        private void close() throws SQLException {
            rollback();
            SqlUtilities.cleanUpStatements(statements.toArray(new Statement[statements.size()]));
            connection.close();
        }

        protected static void closeConnection(ConnectionWrapper connection) {
            if(connection != null) {
                try {
                    connection.close();
                } catch (SQLException e) {
                    // Let garbage collector clean up
                }
            }
        }

        protected PreparedStatement prepareStatement(String query) throws SQLException {
            return connection.prepareStatement(query);
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
    }
}
