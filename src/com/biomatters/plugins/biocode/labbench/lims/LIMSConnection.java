package com.biomatters.plugins.biocode.labbench.lims;

import com.biomatters.geneious.publicapi.databaseservice.DatabaseServiceException;
import com.biomatters.geneious.publicapi.databaseservice.Query;
import com.biomatters.geneious.publicapi.documents.Condition;
import com.biomatters.geneious.publicapi.documents.DocumentField;
import com.biomatters.geneious.publicapi.plugin.Options;
import com.biomatters.plugins.biocode.labbench.*;
import com.biomatters.plugins.biocode.labbench.plates.GelImage;
import com.biomatters.plugins.biocode.labbench.plates.Plate;
import com.biomatters.plugins.biocode.labbench.reaction.*;
import com.biomatters.plugins.biocode.labbench.rest.client.ServerLimsConnection;
import jebl.util.Cancelable;
import jebl.util.ProgressListener;

import java.sql.*;
import java.util.*;
import java.util.Date;

/**
 * @author steve
 */
@SuppressWarnings({"ConstantConditions"})
public abstract class LIMSConnection {
    @SuppressWarnings({"ConstantConditions"})
    public static final int EXPECTED_SERVER_MAJOR_VERSION = 11;
    public static final String EXPECTED_SERVER_FULL_VERSION = "11.0";
    public static final int BATCH_SIZE = 192;  // 2 x 96-well plates

    public int requestTimeout = LoginOptions.DEFAULT_TIMEOUT;

    public static final String VERSION_WITHOUT_PROPS = "9";

    /**
     * Indicates the full version of the database schema in use.  Stored in the properties table.  The databaseversion
     * table contains the major version.  Major versions are incompatible with each other.
     */
    public static final String VERSION_PROPERTY = "fullDatabaseVersion";


    private PasswordOptions limsOptions;

    public static final Map<String, List<DocumentField>> TABLE_TO_FIELDS = new HashMap<String, List<DocumentField>>();

    public static final DocumentField WORKFLOW_ID_FIELD = DocumentField.createStringField("Workflow ID", "", "workflow.id", false, false);
    public static final DocumentField WORKFLOW_NAME_FIELD = DocumentField.createStringField("Workflow Name", "", "workflow.name", true, false);
    public static final DocumentField WORKFLOW_DATE_FIELD = DocumentField.createDateField("Last Modified (LIMS workflow)", "", "workflow.date", false, false);
    public static final DocumentField WORKFLOW_LOCUS_FIELD = DocumentField.createStringField("Locus", "The locus of the workflow", "workflow.locus", true, false);
    public static final DocumentField WORKFLOW_BCID_FIELD = DocumentField.createStringField("Workflow BCID", "The BCID of the workflow", "workflow.bcid", true, false);
    static {
        TABLE_TO_FIELDS.put("workflow", Arrays.asList(WORKFLOW_ID_FIELD, WORKFLOW_NAME_FIELD, WORKFLOW_DATE_FIELD, WORKFLOW_LOCUS_FIELD));
    }

    public static final DocumentField PLATE_ID_FIELD = DocumentField.createStringField("Plate ID", "", "plate.id", false, false);
    public static final DocumentField PLATE_TYPE_FIELD = DocumentField.createEnumeratedField(new String[]{"Extraction", "PCR", "CycleSequencing", "GelQuantification"}, "Plate type", "", "plate.type", true, false);
    public static final DocumentField PLATE_NAME_FIELD = DocumentField.createStringField("Plate Name (LIMS)", "", "plate.name", true, false);
    public static final DocumentField PLATE_DATE_FIELD = DocumentField.createDateField("Last Modified (LIMS plate)", "", "plate.date", false, false);
    static {
        TABLE_TO_FIELDS.put("plate", Arrays.asList(PLATE_TYPE_FIELD, PLATE_ID_FIELD, PLATE_NAME_FIELD, PLATE_DATE_FIELD));
    }

    public static final DocumentField EXTRACTION_ID_FIELD = DocumentField.createStringField("Extraction ID", "The Extraction ID", "extraction.extractionId", true, false);
    public static final DocumentField EXTRACTION_BARCODE_FIELD = DocumentField.createStringField("Extraction Barcode", "The Extraction Barcode", "extraction.extractionBarcode", true, false);
    public static final DocumentField EXTRACTION_DATE_FIELD = DocumentField.createDateField("Extraction Date", "The Date of Extraction", "extraction.date", true, false);
    public static final DocumentField EXTRACTION_BCID_FIELD = DocumentField.createStringField("Extraction BCID", "The BCID of the extraction", "extractionBCID", true, false);
    static {
        TABLE_TO_FIELDS.put("extraction", Arrays.asList(EXTRACTION_ID_FIELD, EXTRACTION_BARCODE_FIELD, EXTRACTION_DATE_FIELD));
    }

    public static final DocumentField SEQUENCE_PROGRESS = DocumentField.createEnumeratedField(new String[]{"passed", "failed"}, "Sequence Progress", "Whether the sequence passed or failed sequencing and assembly", "assembly.progress", true, false);

    public static final DocumentField SEQUENCE_SUBMISSION_PROGRESS = DocumentField.createEnumeratedField(new String[]{"Yes", "No"}, "Sequence Submitted", "Indicates whether this sequence has been submitted to a sequence database (e.g. Genbank)", "assembly.submitted", false, false);
    public static final DocumentField EDIT_RECORD = DocumentField.createStringField("Edit Record", "A record of edits made to this sequence", "assembly.editRecord", false, false);
    public static final DocumentField ASSEMBLY_TECHNICIAN = DocumentField.createStringField("Assembly Technician", "", "assembly.technician", false, false);
    public static final DocumentField ASSEMBLY_DATE_FIELD = DocumentField.createDateField("Sequence Pass/Fail Date", "The Date at which a Sequence Passed/Failed", "assembly.date", true, false);
    static {
        TABLE_TO_FIELDS.put("assembly", Arrays.asList(SEQUENCE_PROGRESS, SEQUENCE_SUBMISSION_PROGRESS, EDIT_RECORD, ASSEMBLY_TECHNICIAN, ASSEMBLY_DATE_FIELD));
    }

    public static final DocumentField SEQUENCE_ID = DocumentField.createIntegerField("LIMS Sequence ID", "The Unique ID of this sequence in LIMS", "LimsSequenceId", false, false);

    String serverUrn;

    public abstract int addAssembly(boolean isPass, String notes, String technician, FailureReason failureReason, String failureNotes, boolean addChromatograms, AssembledSequence seq, List<Integer> reactionIds, Cancelable cancelable) throws DatabaseServiceException;

    public abstract void savePlates(List<Plate> plates, ProgressListener progress) throws BadDataException, DatabaseServiceException;
    public abstract void saveReactions(Reaction[] reactions, Reaction.Type type, ProgressListener progress) throws DatabaseServiceException;
    public abstract Set<Integer> deletePlates(List<Plate> plates, ProgressListener progress) throws DatabaseServiceException;
    public abstract void renamePlate(int id, String newName) throws DatabaseServiceException;

    public abstract List<FailureReason> getPossibleFailureReasons();

    public abstract boolean deleteAllowed(String tableName);

    public abstract void deleteSequences(List<Integer> sequencesToDelete) throws DatabaseServiceException;
    public abstract void deleteSequencesForWorkflowId(Integer workflowId, String extractionId) throws DatabaseServiceException;

    public final Map<String,String> getTissueIdsForExtractionIds(final String tableName, List<String> extractionIds) throws DatabaseServiceException {
        final Map<String, String> ret = new HashMap<String, String>();
        new BatchRequestExecutor<String>(extractionIds, null) {

            @Override
            protected void iterateBatch(List<String> batch) throws DatabaseServiceException {
                ret.putAll(getTissueIdsForExtractionIds_(tableName, batch));
            }
        }.executeBatch();

        return ret;
    }

    protected abstract Map<String,String> getTissueIdsForExtractionIds_(String tableName, List<String> extractionIds) throws DatabaseServiceException;

    public abstract Map<Integer,List<MemoryFile>> downloadTraces(List<Integer> reactionIds, ProgressListener progressListener) throws DatabaseServiceException;

    public final List<ExtractionReaction> getExtractionsForIds(List<String> extractionIds) throws DatabaseServiceException {
        final List<ExtractionReaction> ret = new ArrayList<ExtractionReaction>();
        new BatchRequestExecutor<String>(extractionIds, null) {

            @Override
            protected void iterateBatch(List<String> batch) throws DatabaseServiceException {
                ret.addAll(getExtractionsForIds_(batch));
            }
        }.executeBatch();

        return ret;
    }

    protected abstract List<ExtractionReaction> getExtractionsForIds_(List<String> extractionIds) throws DatabaseServiceException;

    public abstract void setSequenceStatus(boolean submitted, List<Integer> ids) throws DatabaseServiceException;

    public enum AvailableLimsTypes {
        local(LocalLIMSConnection.class, "Built-in MySQL Database", "Create and connect to LIMS databases on your local computer (stored with your Geneious data)"),
        remote(MysqlLIMSConnection.class, "Remote MySQL Database", "Connect to a LIMS database on a remote MySQL server"),
        server(ServerLimsConnection.class, "Biocode Server", "Connect to an instance of the Biocode Server.");
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

    public static PasswordOptions createConnectionOptions() {
        return new LimsConnectionOptions(LIMSConnection.class);
    }

    public abstract PasswordOptions getConnectionOptions();

    public abstract boolean isLocal();

    public String getUrn() {
        return serverUrn;
    }

    public static boolean isLocal(PasswordOptions connectionOptions) {
        return !connectionOptions.getValueAsString("connectionType").equals("remote");
    }

    public abstract String getUsername();

    public final void connect(PasswordOptions options) throws ConnectionException {
        this.limsOptions = options;
        _connect(options);
        requestTimeout = ((Options.IntegerOption)options.getOption(LoginOptions.LIMS_REQUEST_TIMEOUT_OPTION_NAME)).getValue();
    }

    protected abstract void _connect(PasswordOptions options) throws ConnectionException;

    public abstract void disconnect();

    public void reconnect() throws ConnectionException {
        if (this.limsOptions == null) {
            return;
        }
        PasswordOptions limsOptions = this.limsOptions;
        disconnect();
        connect(limsOptions);
    }

    public abstract void doAnyExtraInitialization(ProgressListener progressListener) throws DatabaseServiceException;

    // todo We want to get rid of all of these so we can just call a web method from the GUI in addition to the SQL method

    /**
     *
     * @return The single connection that the Biocode service keeps open.  Ideally we want to remove this in the future
     * and have the service make use of a connection pool.  The current method assumes everything is single threaded
     * in respect to transactions.
     *
     * @throws SQLException if something goes wrong
     */
    protected abstract Connection getConnectionInternal() throws SQLException;

    public abstract boolean supportReporting();

    public DatabaseMetaData getMetaData() throws SQLException {
        Connection connection = getConnectionInternal();
        return connection.getMetaData();
    }

    public Statement createStatement() throws SQLException {
        Connection connection = getConnectionInternal();
        Statement statement = connection.createStatement();
        statement.setQueryTimeout(requestTimeout);
        return statement;
    }

    public PreparedStatement createStatement(String sql) throws SQLException {
        Connection connection = getConnectionInternal();
        PreparedStatement statement = connection.prepareStatement(sql);
        statement.setQueryTimeout(requestTimeout);
        return statement;
    }

    public static List<DocumentField> getSearchAttributes() {
        return Arrays.asList(
                PLATE_NAME_FIELD,
                WORKFLOW_NAME_FIELD,
                PLATE_TYPE_FIELD,
                PLATE_DATE_FIELD,
                WORKFLOW_DATE_FIELD,
                WORKFLOW_LOCUS_FIELD,
                EXTRACTION_ID_FIELD,
                EXTRACTION_BARCODE_FIELD,
                EXTRACTION_DATE_FIELD,
                SEQUENCE_PROGRESS,
                SEQUENCE_SUBMISSION_PROGRESS,
                ASSEMBLY_DATE_FIELD,
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
                    Condition.DATE_AFTER,
                    Condition.DATE_AFTER_OR_ON,
                    Condition.DATE_BEFORE,
                    Condition.DATE_BEFORE_OR_ON
            };
        } else {
            return new Condition[]{
                    Condition.EQUAL,
                    Condition.NOT_EQUAL
            };
        }
    }


    public void getGelImagesForPlates(Collection<Plate> plates) throws DatabaseServiceException {
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

    public abstract Map<Integer, List<GelImage>> getGelImages(Collection<Integer> plateIds) throws DatabaseServiceException;


    public final Set<String> getAllExtractionIdsForTissueIds(final List<String> tissueIds) throws DatabaseServiceException {
        final Set<String> ret = new HashSet<String>();
        new BatchRequestExecutor<String>(tissueIds, null) {

            @Override
            protected void iterateBatch(List<String> batch) throws DatabaseServiceException{
                ret.addAll(getAllExtractionIdsForTissueIds_(batch));
            }
        }.executeBatch();

        return ret;
    }

    protected abstract Set<String> getAllExtractionIdsForTissueIds_(List<String> tissueIds) throws DatabaseServiceException;

    public final List<ExtractionReaction> getExtractionsFromBarcodes(List<String> barcodes) throws DatabaseServiceException {
        final List<ExtractionReaction> ret = new ArrayList<ExtractionReaction>();
        new BatchRequestExecutor<String>(barcodes, null) {

            @Override
            protected void iterateBatch(List<String> batch) throws DatabaseServiceException{
                ret.addAll(getExtractionsFromBarcodes_(batch));
            }
        }.executeBatch();

        return ret;
    }

    protected abstract List<ExtractionReaction> getExtractionsFromBarcodes_(List<String> barcodes) throws DatabaseServiceException;

    /**
     *
     * @param query    The query.  Can include boolean values for "workflowDocuments" and "plateDocuments" to disable downloading
     * @param tissueIdsToMatch  A list of FIMS samples to match.  Or null to return all results.
     * @param cancelable A cancelable to cancel the search task.  Cannot be null.  Can be a {@link jebl.util.ProgressListener#EMPTY}
     * @return {@link LimsSearchResult} with workflows and plates found.
     * @throws SQLException if there is a problem with the database
     */
    public abstract LimsSearchResult getMatchingDocumentsFromLims(Query query, Collection<String> tissueIdsToMatch, Cancelable cancelable) throws DatabaseServiceException;

    /**
     * Retrieves a list of {@link Plate}s from the LIMS by ID.
     *
     * @param plateIds The collection of IDs of the plates to retrieve
     * @param callback A cancelable to cancel the search task.  Cannot be null.  Can be a {@link jebl.util.ProgressListener#EMPTY}
     * @return a list of {@link Plate}s matching the specified IDs.
     * @throws DatabaseServiceException if a problem happens while communicating with the LIMS
     */
    public final void retrievePlates(Collection<Integer> plateIds, final LimsSearchCallback<Plate> callback) throws DatabaseServiceException {
        performRetrieval(plateIds, new Operation<Integer, Plate>() {
            @Override
            List<Plate> doIt(Collection<Integer> inputs, Cancelable cancelable) throws DatabaseServiceException {
                return getPlates_(inputs, callback);
            }
        }, callback);
    }

    /**
     * A convenience method for {@link #retrievePlates(java.util.Collection, LimsSearchCallback)} that stores results
     * during retrieval and returns them.
     *
     * @see #retrievePlates(java.util.Collection, LimsSearchCallback)
     */
    public final List<Plate> getPlates(final List<Integer> plateIds, Cancelable cancelable) throws DatabaseServiceException {
        LimsSearchCallback.LimsSearchRetrieveListCallback<Plate> callback =
                                new LimsSearchCallback.LimsSearchRetrieveListCallback<Plate>(cancelable);
        retrievePlates(plateIds, callback);
        return callback.getResults();
    }

    protected abstract List<Plate> getPlates_(Collection<Integer> plateIds, Cancelable cancelable) throws DatabaseServiceException;

    /**
     * Sets a database wide property.  Can be retrieved by calling {@link #getProperty(String)}
     *
     * @param key   The name of the property
     * @param value The value to set for the property
     * @throws SQLException if something goes wrong communicating with the database.
     */
    public abstract void setProperty(String key, String value) throws DatabaseServiceException;

    /**
     * Retrieves a property from the database previously set by calling {@link #setProperty(String, String)}
     *
     * @param key The name of the property to retrieve
     * @return value of the property or null if it does not exist
     * @throws SQLException if something goes wrong communicating with the database.
     */
    public abstract String getProperty(String key) throws DatabaseServiceException;

    /**
     * Retrieves a list of {@link com.biomatters.plugins.biocode.labbench.WorkflowDocument}s from the LIMS by ID.
     *
     * @param workflowIds The collection of IDs of the workflow documents to retrieve
     * @param callback To add workflow documents to
     * @return a list of {@link com.biomatters.plugins.biocode.labbench.WorkflowDocument}s matching the specified IDs.
     * @throws DatabaseServiceException if a problem happens while communicating with the LIMS
     */
    public final void retrieveWorkflowsById(Collection<Integer> workflowIds, final LimsSearchCallback<WorkflowDocument> callback) throws DatabaseServiceException {
        performRetrieval(workflowIds, new Operation<Integer, WorkflowDocument>() {
            @Override
            List<WorkflowDocument> doIt(Collection<Integer> inputs, Cancelable cancelable) throws DatabaseServiceException {
                return getWorkflowsById_(inputs, callback);
            }
        },
        callback);
    }

    /**
     * A convenience method for {@link #retrieveWorkflowsById(java.util.Collection, LimsSearchCallback)} that stores
     * results during retrieval and returns them.
     *
     * @see #retrieveWorkflowsById(java.util.Collection, LimsSearchCallback)
     */
    public final List<WorkflowDocument> getWorkflowsById(Collection<Integer> workflowIds, Cancelable cancelable) throws DatabaseServiceException {
        LimsSearchCallback.LimsSearchRetrieveListCallback<WorkflowDocument> callback =
                                        new LimsSearchCallback.LimsSearchRetrieveListCallback<WorkflowDocument>(cancelable);
        retrieveWorkflowsById(workflowIds, callback);
        return callback.getResults();
    }

    protected abstract List<WorkflowDocument> getWorkflowsById_(Collection<Integer> workflowIds, Cancelable cancelable) throws DatabaseServiceException;
    public abstract List<Workflow> getWorkflowsByName(Collection<String> workflowNames) throws DatabaseServiceException;
    public abstract Map<String,String> getWorkflowIds(List<String> idsToCheck, List<String> loci, Reaction.Type reactionType) throws DatabaseServiceException;
    public abstract void renameWorkflow(int id, String newName) throws DatabaseServiceException;

    public abstract void testConnection() throws DatabaseServiceException;

    public final void retrieveAssembledSequences(List<Integer> sequenceIds, LimsSearchCallback<AssembledSequence> callback, final boolean includeFailed) throws DatabaseServiceException {
        performRetrieval(sequenceIds, new Operation<Integer, AssembledSequence>() {
            @Override
            List<AssembledSequence> doIt(Collection<Integer> inputs, Cancelable cancelable) throws DatabaseServiceException {
                return getAssemblySequences_(inputs, cancelable, includeFailed);
            }
        }, callback);
    }

    public final List<AssembledSequence> getAssemblySequences(List<Integer> sequenceIds, Cancelable cancelable, boolean includeFailed) throws DatabaseServiceException {
        LimsSearchCallback.LimsSearchRetrieveListCallback<AssembledSequence> callback =
                                                new LimsSearchCallback.LimsSearchRetrieveListCallback<AssembledSequence>(cancelable);
        retrieveAssembledSequences(sequenceIds, callback, includeFailed);
        return callback.getResults();
    }

    protected abstract List<AssembledSequence> getAssemblySequences_(Collection<Integer> sequenceIds, Cancelable cancelable, boolean includeFailed) throws DatabaseServiceException;

    public abstract void setAssemblySequences(Map<Integer, String> assemblyIDToAssemblySequenceToSet, ProgressListener progressListener) throws DatabaseServiceException;

    /**
     * @param plateIds the ids of the plates to check
     * returns all the empty plates in the database...
     * @return all the empty plates in the database...
     * @throws SQLException if the database cannot be queried for some reason
     */
    public abstract List<Plate> getEmptyPlates(Collection<Integer> plateIds) throws DatabaseServiceException;

    public abstract Collection<String> getPlatesUsingCocktail(Reaction.Type type, int cocktailId) throws DatabaseServiceException;
    public abstract List<String> getPlatesUsingThermocycle(int thermocycleId) throws DatabaseServiceException;

    public abstract void addCocktails(List<? extends Cocktail> cocktails) throws DatabaseServiceException;
    public abstract void deleteCocktails(List<? extends Cocktail> deletedCocktails) throws DatabaseServiceException;
    public abstract List<PCRCocktail> getPCRCocktailsFromDatabase() throws DatabaseServiceException;
    public abstract List<CycleSequencingCocktail> getCycleSequencingCocktailsFromDatabase() throws DatabaseServiceException;

    public abstract List<Thermocycle> getThermocyclesFromDatabase(Thermocycle.Type type) throws DatabaseServiceException;
    public abstract void addThermoCycles(Thermocycle.Type type, List<Thermocycle> cycles) throws DatabaseServiceException;
    public abstract void deleteThermoCycles(Thermocycle.Type type, List<Thermocycle> cycles) throws DatabaseServiceException;

    public abstract static class BatchRequestExecutor<T> {
        private Collection<T> params;
        private Cancelable cancelable;

        public BatchRequestExecutor(Collection<T> params, Cancelable cancelable) {
            this.params = params;
            this.cancelable = cancelable;
        }

        public void executeBatch() throws DatabaseServiceException {
            executeBatch(BATCH_SIZE);
        }

        public void executeBatch(int batchSize) throws DatabaseServiceException {
            if(params.isEmpty()) {
                return;
            }

            if (batchSize <= 0)
                batchSize = BATCH_SIZE;

            Iterator<T> it = params.iterator();
            while (it.hasNext() && !isCancled()) {
                int count = 0;
                List<T> batch = new ArrayList<T>();
                batch.add(it.next());

                while (++count < batchSize && it.hasNext()) {
                    batch.add(it.next());
                }

                if (isCancled()) return;

                iterateBatch(batch);
            }
        }

        private boolean isCancled() {
            return (cancelable != null && cancelable.isCanceled());
        }

        protected abstract void iterateBatch(List<T> batch) throws DatabaseServiceException;
    }

    private static abstract class Operation<InputType, OutputType> {
        abstract List<OutputType> doIt(Collection<InputType> inputs, Cancelable cancelable) throws DatabaseServiceException;
    }

    private static <IdType, ResultType> void performRetrieval(
            Collection<IdType> ids, final Operation<IdType, ResultType> operation, final LimsSearchCallback<ResultType> callback)
            throws DatabaseServiceException {

        new BatchRequestExecutor<IdType>(ids, callback) {
            @Override
            protected void iterateBatch(List<IdType> batch) throws DatabaseServiceException {
                List<ResultType> results = operation.doIt(batch, ProgressListener.EMPTY);
                for (ResultType result : results) {
                    callback.addResult(result);
                }
            }
        }.executeBatch();
    }
}