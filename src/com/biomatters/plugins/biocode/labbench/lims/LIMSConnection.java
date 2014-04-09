package com.biomatters.plugins.biocode.labbench.lims;

import com.biomatters.geneious.publicapi.databaseservice.*;
import com.biomatters.geneious.publicapi.documents.*;
import com.biomatters.plugins.biocode.assembler.lims.AddAssemblyResultsToLimsOperation;
import com.biomatters.plugins.biocode.labbench.*;
import com.biomatters.plugins.biocode.labbench.plates.GelImage;
import com.biomatters.plugins.biocode.labbench.plates.Plate;
import com.biomatters.plugins.biocode.labbench.reaction.*;
import jebl.util.Cancelable;
import jebl.util.CompositeProgressListener;
import jebl.util.ProgressListener;

import java.sql.*;
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


    private PasswordOptions limsOptions;

    public static final DocumentField DATE_FIELD = new DocumentField("Last Modified (LIMS)", "", "date", Date.class, true, false);

    public static final DocumentField WORKFLOW_NAME_FIELD = new DocumentField("Workflow Name", "", "workflow.name", String.class, true, false);

    public static final DocumentField WORKFLOW_DATE_FIELD = new DocumentField("Last Modified (LIMS workflow)", "", "workflow.date", Date.class, false, false);
    public static final DocumentField WORKFLOW_LOCUS_FIELD = new DocumentField("Locus", "The locus of the workflow", "workflow.locus", String.class, true, false);
    public static final DocumentField PLATE_TYPE_FIELD = DocumentField.createEnumeratedField(new String[]{"Extraction", "PCR", "CycleSequencing"}, "Plate type", "", "plate.type", true, false);

    public static final DocumentField PLATE_NAME_FIELD = new DocumentField("Plate Name (LIMS)", "", "plate.name", String.class, true, false);
    public static final DocumentField PLATE_DATE_FIELD = new DocumentField("Last Modified (LIMS plate)", "", "plate.date", Date.class, false, false);

    public static final DocumentField EXTRACTION_ID_FIELD = new DocumentField("Extraction ID", "The Extraction ID", "extraction.extractionId", String.class, true, false);

    public static final DocumentField EXTRACTION_BARCODE_FIELD = new DocumentField("Extraction Barcode", "The Extraction Barcode", "extraction.extractionBarcode", String.class, true, false);
    public static final DocumentField SEQUENCE_PROGRESS = DocumentField.createEnumeratedField(new String[]{"passed", "failed"}, "Sequence Progress", "Whether the sequence passed or failed sequencing and assembly", "assembly.progress", true, false);

    public static final DocumentField SEQUENCE_SUBMISSION_PROGRESS = DocumentField.createEnumeratedField(new String[]{"Yes", "No"}, "Sequence Submitted", "Indicates whether this sequence has been submitte to a sequence database (e.g. Genbank)", "assembly.submitted", false, false);
    public static final DocumentField EDIT_RECORD = DocumentField.createStringField("Edit Record", "A record of edits made to this sequence", "assembly.editRecord", false, false);
    public static final DocumentField ASSEMBLY_TECHNICIAN = DocumentField.createStringField("Assembly Technician", "", "assembly.technician", false, false);

    public static final DocumentField SEQUENCE_ID = DocumentField.createIntegerField("LIMS Sequence ID", "The Unique ID of this sequence in LIMS", "LimsSequenceId", false, false);

    String serverUrn;

    public abstract int addAssembly(boolean isPass, String notes, String technician, FailureReason failureReason, String failureNotes, boolean addChromatograms, AssembledSequence seq, List<Integer> reactionIds, Cancelable cancelable) throws DatabaseServiceException;

    public abstract void savePlate(Plate plate, ProgressListener progress) throws BadDataException, DatabaseServiceException;
    public abstract void saveReactions(Reaction[] reactions, Reaction.Type type, ProgressListener progress) throws DatabaseServiceException;
    public abstract Set<Integer> deletePlate(Plate plate, ProgressListener progress) throws DatabaseServiceException;
    public abstract void renamePlate(int id, String newName) throws DatabaseServiceException;

    public abstract List<FailureReason> getPossibleFailureReasons();

    public abstract boolean deleteAllowed(String tableName);

    public abstract void deleteSequences(List<Integer> sequencesToDelete) throws DatabaseServiceException;
    public abstract void deleteSequencesForWorkflowId(Integer workflowId, String extractionId) throws DatabaseServiceException;

    public abstract Map<String,String> getTissueIdsForExtractionIds(String tableName, List<String> extractionIds) throws DatabaseServiceException;

    public abstract Map<Integer,List<MemoryFile>> downloadTraces(List<Integer> reactionIds, ProgressListener progressListener) throws DatabaseServiceException;

    public abstract List<ExtractionReaction> getExtractionsForIds(List<String> extractionIds) throws DatabaseServiceException;

    public abstract void setSequenceStatus(boolean submitted, List<Integer> ids) throws DatabaseServiceException;

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

    public abstract void doAnyExtraInitialziation() throws DatabaseServiceException;

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
        statement.setQueryTimeout(BiocodeService.STATEMENT_QUERY_TIMEOUT);
        return statement;
    }

    public PreparedStatement createStatement(String sql) throws SQLException {
        Connection connection = getConnectionInternal();
        PreparedStatement statement = connection.prepareStatement(sql);
        statement.setQueryTimeout(BiocodeService.STATEMENT_QUERY_TIMEOUT);
        return statement;
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


    public abstract Set<String> getAllExtractionIdsForTissueIds(List<String> tissueIds) throws DatabaseServiceException;

    public abstract List<ExtractionReaction> getExtractionsFromBarcodes(List<String> barcodes) throws DatabaseServiceException;

    /**
     *
     * @param query    The query.  Can include boolean values for "workflowDocuments" and "plateDocuments" to disable downloading
     * @param tissueIdsToMatch  A list of FIMS samples to match.  Or null to return all results.
     * @param callback To add results to as they are found.  Can be null.
     * @return {@link LimsSearchResult} with workflows and plates found.
     * @throws SQLException if there is a problem with the database
     */
    public abstract LimsSearchResult getMatchingDocumentsFromLims(Query query, Collection<String> tissueIdsToMatch, RetrieveCallback callback) throws DatabaseServiceException;

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

    public abstract List<Workflow> getWorkflows(Collection<String> workflowIds) throws DatabaseServiceException;
    public abstract Map<String,String> getWorkflowIds(List<String> idsToCheck, List<String> loci, Reaction.Type reactionType) throws DatabaseServiceException;
    public abstract void renameWorkflow(int id, String newName) throws DatabaseServiceException;

    public abstract void testConnection() throws DatabaseServiceException;

    public abstract List<AssembledSequence> getAssemblyDocuments(List<Integer> sequenceIds, RetrieveCallback callback, boolean includeFailed) throws DatabaseServiceException;

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
}
