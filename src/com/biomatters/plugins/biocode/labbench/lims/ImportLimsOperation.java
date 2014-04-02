package com.biomatters.plugins.biocode.labbench.lims;

import com.biomatters.geneious.publicapi.databaseservice.DatabaseServiceException;
import com.biomatters.geneious.publicapi.plugin.*;
import com.biomatters.geneious.publicapi.documents.AnnotatedPluginDocument;
import com.biomatters.geneious.publicapi.documents.Condition;
import com.biomatters.geneious.publicapi.documents.PluginDocument;
import com.biomatters.geneious.publicapi.utilities.StandardIcons;
import com.biomatters.geneious.publicapi.components.Dialogs;
import com.biomatters.geneious.publicapi.databaseservice.Query;
import com.biomatters.geneious.publicapi.databaseservice.RetrieveCallback;
import com.biomatters.plugins.biocode.BiocodePlugin;
import com.biomatters.plugins.biocode.BiocodeUtilities;
import com.biomatters.plugins.biocode.CSVUtilities;
import com.biomatters.plugins.biocode.labbench.*;
import com.biomatters.plugins.biocode.labbench.lims.LIMSConnection;
import com.biomatters.plugins.biocode.labbench.lims.LocalLIMSConnection;
import com.biomatters.plugins.biocode.labbench.lims.LocalLIMSConnectionOptions;
import com.biomatters.plugins.biocode.labbench.reaction.*;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.sql.SQLException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;
import java.io.*;

import jebl.util.ProgressListener;
import jebl.util.CompositeProgressListener;

import javax.swing.*;

/**
 * @author Steve
 * @version $Id$
 *          <p/>
 *          Created on 1/02/2012 10:10:51 AM
 */


public class ImportLimsOperation extends DocumentOperation {
    private String DATABASE = "database";
    static final List<String> allowedNames = Arrays.asList("Example Thermocycle", "No Cocktail");

    public GeneiousActionOptions getActionOptions() {
        return GeneiousActionOptions.createSubmenuActionOptions(BiocodePlugin.getSuperBiocodeAction(), new GeneiousActionOptions("Import LIMS"));
    }

    public String getHelp() {
        return "Import a local LIMS into the current LIMS";
    }

    public DocumentSelectionSignature[] getSelectionSignatures() {
        return new DocumentSelectionSignature[0];
    }

    @Override
    public Options getOptions(AnnotatedPluginDocument... documents) throws DocumentOperationException {
        if(!BiocodeService.getInstance().isLoggedIn()) {
            throw new DocumentOperationException(BiocodeUtilities.NOT_CONNECTED_ERROR_MESSAGE);
        }
        Options options = new Options(this.getClass()){
            @Override
            public Dialogs.DialogOptions getDialogOptions() {
                Dialogs.DialogOptions dialogOptions = super.getDialogOptions();
                if(dialogOptions == null) {
                    dialogOptions = new Dialogs.DialogOptions(Dialogs.OK_CANCEL, "Import LIMS", null, Dialogs.DialogIcon.WARNING);    
                }
                else {

                }
                dialogOptions.setCustomIcon(UIManager.getIcon("OptionPane.warningIcon"));
                return dialogOptions;
            }
        };


        options.addLabel("<html>This operation will import all data from the local LIMS you select below (the source database)  into the current LIMS (the destination database).  <br>Please make sure that you observe the following:<ul>" +
                "<li>Make sure that you are connected to the correct LIMS database</li>" +
                "<li>Both LIMS databases must be connected to the same FIMS database, or at the very least all tissue id's recorded in the source database <br>" +
                "must be accessable from the destination database</li>" +
                "<li>The destination database must not contain any cocktails, thermocycles, or plates with the same name as those in the source database</li>" +
                "<li>If extractionin the source database have the same names as those in the destination database, they will be renamed (with a suffix such as '_2')</li>" +
                "<li>This operaiton will not write anything to the destination database unless the entire import is successful.  However, please make sure <br>" +
                "that you back up your destination LIMS before performing this operation</li>" +
                "" +
                "</ul></html>", false, true);

        List<Options.OptionValue> databaseValues = LocalLIMSConnectionOptions.getDatabaseOptionValues();

        options.addComboBoxOption(DATABASE, "Select your source database",databaseValues, databaseValues.get(0));
        options.beginAlignHorizontally(null, false);
        Options.BooleanOption mapTissueIds = options.addBooleanOption("mapTissueIds", "Map Tissue Ids", false);
        Options.FileSelectionOption tissueIdsFile = options.addFileSelectionOption("tissueMapFile", "", "");
        Options.ButtonOption helpButton = options.addButtonOption("helpButton", "", "", StandardIcons.help.getIcons().getIcon16(), JButton.RIGHT);
        mapTissueIds.addDependent(tissueIdsFile, true, false);
        options.endAlignHorizontally();
        helpButton.addActionListener(new ActionListener(){
            public void actionPerformed(ActionEvent e) {
                Dialogs.showMessageDialog("If your destination LIMS uses a new Tissue Id system to your source LIMS, you can specify a file describing the mapping here.  The file should be a CSV (comma separated value) file with the source LIMS ids in the first column, and the destination LIMS ids in the second column.", "Tissue ID Mapping");
            }
        });


        return options;
    }

    @Override
    public List<AnnotatedPluginDocument> performOperation(AnnotatedPluginDocument[] annotatedDocuments, final ProgressListener progressListener, Options options) throws DocumentOperationException {
        if(!BiocodeService.getInstance().isLoggedIn()) {
            throw new DocumentOperationException(BiocodeUtilities.NOT_CONNECTED_ERROR_MESSAGE);
        }
        if(!Dialogs.showYesNoDialog("It is recommended that you make a full backup of your destination LIMS database before running this operation.  To back up a local LIMS, use the <i>Back Up</i> button in the main Geneious toolbar, or for a remote LIMS, contact your system administrator.  <br><br>Do you want to continue?", "Back up", null, Dialogs.DialogIcon.QUESTION)) {
            throw new DocumentOperationException.Canceled();    
        }

        String databaseName = options.getValueAsString(DATABASE);
        Map<String, String> tissueIdMapping = null;
        if((Boolean)options.getValue("mapTissueIds")) {
            tissueIdMapping = mapTissueIds(options.getValueAsString("tissueMapFile"));
        }
        

        final LIMSConnection destinationLims = BiocodeService.getInstance().getActiveLIMSConnection();
        try {
            progressListener.setIndeterminateProgress();
            progressListener.setMessage("Checking for plates, cocktails, and thermocycles with the same name");

            LIMSConnection sourceLims = new LocalLIMSConnection();
            LocalLIMSConnectionOptions connectOptions = new LocalLIMSConnectionOptions();
            connectOptions.setValue(LocalLIMSConnectionOptions.DATABASE, databaseName);
            sourceLims.connect(connectOptions);

            checkForDuplicateNames(sourceLims, destinationLims, "plate", "name", "plate(s)");
            checkForCancelled(progressListener);

            checkForDuplicateNames(sourceLims, destinationLims, "thermocycle", "name", "thermocycle(s)");
            checkForCancelled(progressListener);

            checkForDuplicateNames(sourceLims, destinationLims, "pcr_cocktail", "name", "PCR cocktail(s)");
            checkForCancelled(progressListener);

            checkForDuplicateNames(sourceLims, destinationLims, "cyclesequencing_cocktail", "name", "sequencing cocktail(s)");
            checkForCancelled(progressListener);

            destinationLims.beginTransaction();
            checkForCancelled(progressListener);

            Map<Integer, Integer> pcrCocktailMap = copyCocktails(sourceLims.getPCRCocktailsFromDatabase(), destinationLims);
            checkForCancelled(progressListener);

            Map<Integer, Integer> sequencingCocktailMap = copyCocktails(sourceLims.getCycleSequencingCocktailsFromDatabase(), destinationLims);
            checkForCancelled(progressListener);

            Map<Integer, Integer> pcrThermocycleMap = copyThermocycles(sourceLims.getThermocyclesFromDatabase("pcr_thermocycle"), "pcr_thermocycle", destinationLims);
            checkForCancelled(progressListener);

            Map<Integer, Integer> sequencingThermocycleMap = copyThermocycles(sourceLims.getThermocyclesFromDatabase("cyclesequencing_thermocycle"), "cyclesequencing_thermocycle", destinationLims);
            checkForCancelled(progressListener);

            BiocodeService.getInstance().buildCaches();
            checkForCancelled(progressListener);

            CompositeProgressListener composite = new CompositeProgressListener(progressListener, 5);
            checkForCancelled(progressListener);

            composite.beginSubtask("Copying Extraction Plates");
            Map<String, String> extractionIdMap = copyExtractionPlates(sourceLims, destinationLims, tissueIdMapping, composite);
            checkForCancelled(progressListener);

            Map<String, String> workflowMap = new HashMap<String, String>();

            composite.beginSubtask("Copying PCR Plates");
            copyReactionPlates(sourceLims, destinationLims, "PCR", pcrCocktailMap, pcrThermocycleMap, extractionIdMap, workflowMap, composite);
            checkForCancelled(progressListener);

            composite.beginSubtask("Copying Sequencing Plates");
            Map<Integer, Integer> sequencingReactionMap = copyReactionPlates(sourceLims, destinationLims, "CycleSequencing", sequencingCocktailMap, sequencingThermocycleMap, extractionIdMap, workflowMap, composite);
            checkForCancelled(progressListener);

            composite.beginSubtask("Copying Raw Traces");
            copyTraces(sourceLims, destinationLims, sequencingReactionMap, composite);
            checkForCancelled(progressListener);

            composite.beginSubtask("Copying Sequences");
            copyAssemblies(sourceLims, destinationLims, workflowMap, composite);
            checkForCancelled(progressListener);


        } catch (ConnectionException e) {
            destinationLims.rollback();
            try {
                BiocodeService.getInstance().buildCaches();
            } catch (DatabaseServiceException ignore) {}
            throw new DocumentOperationException(e.getMessage(), e);
        } catch (SQLException e) {
            destinationLims.rollback();
            try {
                BiocodeService.getInstance().buildCaches();
            } catch (DatabaseServiceException ignore) {}
            e.printStackTrace();
            throw new DocumentOperationException(e.getMessage(), e);
        } catch(DocumentOperationException ex) {
            destinationLims.rollback();
            try {
                BiocodeService.getInstance().buildCaches();
            } catch (DatabaseServiceException ignore) {}
            throw ex;
        } catch(Throwable th) {
            destinationLims.rollback();
            try {
                BiocodeService.getInstance().buildCaches();
            } catch (DatabaseServiceException ignore) {}
            throw new DocumentOperationException("There was a problem importing your LIMS: "+th.getMessage(), th);
        }
        finally {
            try {
                destinationLims.endTransaction();
            } catch (SQLException e) {
                //ignore
            }
        }


        return Collections.emptyList();
    }

    private static Map<String, String> mapTissueIds(String mappingFileLocation) throws DocumentOperationException{
        Map<String, String> map = new HashMap<String, String>();
        File mappingFile = new File(mappingFileLocation);
        if(!mappingFile.exists()) {
            throw new DocumentOperationException("The mapping file located at \""+mappingFileLocation+"\" does not appear to exist.");
        }

        if(!mappingFile.isFile()) {
            throw new DocumentOperationException("The mapping file located at \""+mappingFileLocation+"\" is a folder.");
        }

        try {
            BufferedReader reader = new BufferedReader(new FileReader(mappingFile));
            String line;
            while((line = reader.readLine()) != null) {
                String[] tokens = CSVUtilities.tokenizeLine(line);
                if(tokens.length != 2)  {
                    throw new DocumentOperationException("Your tissue mapping file contains an invalid line: "+line);
                }
                if(map.get(tokens[0]) != null) {
                    throw new DocumentOperationException("Your tissue mapping file contains duplicate entries for \""+tokens[0]+"\"");
                }
                map.put(tokens[0], tokens[1]);
            }
        } catch (IOException e) {
            throw new DocumentOperationException(e.getMessage(), e);
        }
        return map;
    }

    private void copyAssemblies(LIMSConnection sourceLims, LIMSConnection destinationLims, Map<String, String> workflowMap, ProgressListener progressListener) throws SQLException{
        PreparedStatement getAssembliesStatement = sourceLims.createStatement("SELECT workflow.name, assembly.* from assembly, workflow WHERE workflow.id = assembly.workflow");
        PreparedStatement saveAssembliesStatement = destinationLims.createStatement("INSERT INTO assembly (extraction_id, workflow, progress, consensus, params, coverage, " +
                "disagreements, edits, reference_seq_id, confidence_scores, trim_params_fwd, trim_params_rev, date, notes, technician, bin, ambiguities, submitted, editrecord) " +
                "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
        ResultSet getAssembliesResultSet = getAssembliesStatement.executeQuery();

        while(getAssembliesResultSet.next()) {
            saveAssembliesStatement.setString(1, getAssembliesResultSet.getString("assembly.extraction_id"));
            saveAssembliesStatement.setInt(2, getWorkflowId(destinationLims, workflowMap.get(getAssembliesResultSet.getString("workflow.name"))));
            saveAssembliesStatement.setString(3, getAssembliesResultSet.getString("assembly.progress"));
            saveAssembliesStatement.setString(4, getAssembliesResultSet.getString("assembly.consensus"));
            saveAssembliesStatement.setString(5, getAssembliesResultSet.getString("assembly.params"));
            saveAssembliesStatement.setFloat(6, getAssembliesResultSet.getFloat("assembly.coverage"));
            saveAssembliesStatement.setString(7, getAssembliesResultSet.getString("assembly.disagreements"));
            saveAssembliesStatement.setString(8, getAssembliesResultSet.getString("assembly.edits"));
            saveAssembliesStatement.setInt(9, getAssembliesResultSet.getInt("assembly.reference_seq_id"));
            saveAssembliesStatement.setString(10, getAssembliesResultSet.getString("assembly.confidence_scores"));
            saveAssembliesStatement.setString(11, getAssembliesResultSet.getString("assembly.trim_params_fwd"));
            saveAssembliesStatement.setString(12, getAssembliesResultSet.getString("assembly.trim_params_rev"));
            saveAssembliesStatement.setDate(13, getAssembliesResultSet.getDate("assembly.date"));
            saveAssembliesStatement.setString(14, getAssembliesResultSet.getString("assembly.notes"));
            saveAssembliesStatement.setString(15, getAssembliesResultSet.getString("assembly.technician"));
            saveAssembliesStatement.setString(16, getAssembliesResultSet.getString("assembly.bin"));
            saveAssembliesStatement.setInt(17, getAssembliesResultSet.getInt("assembly.ambiguities"));
            saveAssembliesStatement.setInt(18, getAssembliesResultSet.getInt("assembly.submitted"));
            saveAssembliesStatement.setString(19, getAssembliesResultSet.getString("assembly.editrecord"));
            int result = saveAssembliesStatement.executeUpdate();
            if(result != 1) {
                throw new SQLException("Failed to save the sequence "+getAssembliesResultSet.getString("extraction_id")+" to the destination database.");
            }
        }
    }

    private int getWorkflowId(LIMSConnection destinationLims, String workflowName) throws SQLException{
        PreparedStatement statement = destinationLims.createStatement("SELECT id from workflow WHERE name=?");
        statement.setString(1, workflowName);
        ResultSet resultSet = statement.executeQuery();
        while(resultSet.next()) {
            return resultSet.getInt("id");
        }
        throw new SQLException("There is no workflow with the name "+workflowName);

    }

    private static void copyTraces(LIMSConnection sourceLims, LIMSConnection destinationLims, Map<Integer, Integer> sequencingReactionMap, ProgressListener progressListener) throws SQLException{
        int totalTraces = 1;
        PreparedStatement countTracesStatement = sourceLims.createStatement("SELECT count(*) from traces");
        ResultSet countTracesResult = countTracesStatement.executeQuery();
        while(countTracesResult.next()) {
            totalTraces = countTracesResult.getInt(1);
        }

        int totalTraceCount = 0;
        while(true) {//break up the retrieve in an attempt to save memory...
            PreparedStatement getTracesStatement = sourceLims.createStatement("SELECT * from traces LIMIT 10 OFFSET "+totalTraceCount);
            PreparedStatement saveTraceStatement = destinationLims.createStatement("INSERT INTO traces (reaction, name, data) values (?, ?, ?)");
            ResultSet getTracesResult = getTracesStatement.executeQuery();

            int traceCount = 0;
            while(getTracesResult.next()) {
                if(totalTraces > 1) {
                    progressListener.setMessage("Copying trace "+(totalTraceCount+traceCount+" of "+totalTraces));
                }
                progressListener.setProgress(Math.min(1, ((double)totalTraceCount+traceCount)/totalTraces));
                saveTraceStatement.setInt(1, sequencingReactionMap.get(getTracesResult.getInt("reaction")));
                saveTraceStatement.setString(2, getTracesResult.getString("name"));
                saveTraceStatement.setBlob(3, getTracesResult.getBlob("data"));
                int result = saveTraceStatement.executeUpdate();
                if(result != 1) {
                    throw new SQLException("Failed to save the trace "+getTracesResult.getString("name")+" to the destination database.");
                }
                traceCount++;
            }
            if(traceCount == 0) {
                break;
            }
            totalTraceCount += traceCount;
        }
    }

    private static void checkForCancelled(ProgressListener progress) throws DocumentOperationException {
        if(progress.isCanceled()) {
            throw new DocumentOperationException.Canceled();
        }
    }


    private static Map<Integer, Integer> copyReactionPlates(final LIMSConnection sourceLims, final LIMSConnection destinationLims, String reactionType, final Map<Integer, Integer> cocktailMap, final Map<Integer, Integer> thermocycleMap, final Map<String, String> extractionIdMap, final Map<String, String> workflowMap,  final ProgressListener progressListener) throws Throwable {
        final AtomicReference<Throwable> callbackException = new AtomicReference<Throwable>();
        final Map<Integer, Integer> reactionMap = new LinkedHashMap<Integer, Integer>();

        final int plateCount = getPlateCount(sourceLims, reactionType);


        sourceLims.getMatchingDocumentsFromLims(
                Query.Factory.createFieldQuery(LIMSConnection.PLATE_TYPE_FIELD, Condition.EQUAL, new Object[]{reactionType},
                        BiocodeService.getSearchDownloadOptions(false, false, true, false)),
                null, new RetrieveCallback(){
            private boolean canceled = false;
            double currentPlate = 0;

            protected void _add(PluginDocument document, Map<String, Object> searchResultProperties) {
                if(canceled) {
                    return;
                }
                handleDocument((PlateDocument)document);
            }

            protected void _add(AnnotatedPluginDocument document, Map<String, Object> searchResultProperties) {
                if(canceled) {
                    return;
                }
                try {
                    PluginDocument pluginDocument = document.getDocument();
                    handleDocument((PlateDocument) pluginDocument);
                } catch (DocumentOperationException e) {
                    canceled = true;
                    callbackException.set(e);
                }
            }

            private void handleDocument(PlateDocument plate) {
                progressListener.setProgress(currentPlate/ plateCount);
                currentPlate++;
                int oldPlateId = plate.getPlate().getId();
                plate.getPlate().setId(-1);
                Reaction[] plateReactions = plate.getPlate().getReactions();
                final int[] reactionIds = new int[plateReactions.length];
                String[] existingWorkflows = new String[plateReactions.length];

                int oldThermocycleId = plate.getPlate().getThermocycleId();
                Integer newThermocycleId = thermocycleMap.get(oldThermocycleId);
                if(newThermocycleId == null) {
                    newThermocycleId = oldThermocycleId; //the ignored one (i.e. the default one)
                }
                Thermocycle newThermocycle = new Thermocycle("temp", newThermocycleId);
                plate.getPlate().setThermocycle(newThermocycle);

                for (int i = 0; i < plateReactions.length; i++) {
                    Reaction r = plateReactions[i];
                    reactionIds[i] = r.getId();
                    r.setId(-1);

                    //set workflows
                    String existingWorkflowId = r.getOptions().getValueAsString(ReactionOptions.WORKFLOW_ID);
                    if (existingWorkflowId.length() > 0) {
                        existingWorkflows[i] = existingWorkflowId;
                    }
                    String workflowId = getDestinationWorkflowId(existingWorkflowId, workflowMap);

                    try {
                        r.setWorkflow(getWorkflow(workflowId, destinationLims));
                        r.getOptions().setValue(ReactionOptions.WORKFLOW_ID, workflowId);
                    } catch (SQLException e) {
                        canceled = true;
                        callbackException.set(e);
                        return;
                    }

                    //set cocktails
                    int existingCocktailId = Integer.parseInt(r.getOptions().getValueAsString(ReactionOptions.COCKTAIL_OPTION_ID));
                    Integer newCocktailId = cocktailMap.get(existingCocktailId);
                    if(newCocktailId == null) {
                        newCocktailId = existingCocktailId; //the ignored cocktails (i.e. the no cocktail one)
                    }
                    r.getOptions().setValue(ReactionOptions.COCKTAIL_OPTION_ID, newCocktailId);

                    if(extractionIdMap.get(r.getExtractionId()) != null) {
                        r.setExtractionId(extractionIdMap.get(r.getExtractionId()));
                    }
                }
                try {
                    BiocodeService.getInstance().saveReactions(null, plate.getPlate());
                    copyGelImages(sourceLims, destinationLims, oldPlateId, plate.getPlate().getId());
                } catch (SQLException e) {
                    canceled = true;
                    callbackException.set(e);
                    return;
                } catch (BadDataException e) {
                    canceled = true;
                    callbackException.set(e);
                    return;
                } catch (DatabaseServiceException e) {
                    canceled = true;
                                        callbackException.set(e);
                                        return;
                }

                List<PlateDocument> savedPlates;
                try {
                    savedPlates = destinationLims.getMatchingDocumentsFromLims(
                            Query.Factory.createFieldQuery(LIMSConnection.PLATE_NAME_FIELD, Condition.EQUAL,
                                    new Object[]{plate.getPlate().getName()}, BiocodeService.getSearchDownloadOptions(false, false, true, false)),
                            null, null, false).getPlates();
                } catch (DatabaseServiceException e) {
                    canceled = true;
                    callbackException.set(e);
                    return;
                }
                if(savedPlates.size() != 1) {
                    callbackException.set(new DocumentOperationException("The plate appears not to have been saved to the database - expected 1 plate but got "+savedPlates.size()));
                    canceled = true;
                    return;
                }
                Reaction[] oldPlateReactions = savedPlates.get(0).getPlate().getReactions();
                //todo: test that two PCR plates with the same workflows copy across correctly...
                for (int i = 0; i < oldPlateReactions.length; i++) {
                    Reaction r = oldPlateReactions[i];
                    if(r != null && !r.isEmpty()) {
                        reactionMap.put(reactionIds[i], r.getId());
                        Workflow workflow = r.getWorkflow();
                        if (workflow != null && existingWorkflows[i] != null) {
                            workflowMap.put(existingWorkflows[i], workflow.getName());
                        }
                    }
                }
            }

            @Override
            protected boolean _isCanceled() {
                return progressListener.isCanceled() || canceled;
            }
        }, false);

        if(callbackException.get() != null) {
            throw callbackException.get();
        }
        return reactionMap;
    }

    private static int getPlateCount(LIMSConnection sourceLims, String reactionType) throws SQLException {
        PreparedStatement plateCountStatement = sourceLims.createStatement("SELECT count(id) from plate where LOWER(type)=?");
        plateCountStatement.setString(1, reactionType.toLowerCase());
        ResultSet plateCountSet = plateCountStatement.executeQuery();
        int plateCount = Integer.MAX_VALUE;
        if(plateCountSet.next()) {
            plateCount = plateCountSet.getInt(1);
        }
        return plateCount;
    }

    private static Workflow getWorkflow(String workflowId, LIMSConnection lims) throws SQLException{
        PreparedStatement statement = lims.createStatement("SELECT workflow.id as id, workflow.name as name, extraction.extractionId as extractionid, workflow.locus as locus, workflow.date as date from workflow, extraction WHERE workflow.extractionid = extraction.id AND name=?");
        statement.setString(1, workflowId);
        ResultSet resultSet = statement.executeQuery();
        if(resultSet.next()) {
            return new Workflow(resultSet);
        }
        return null;
    }

    private static Map<String, String> copyExtractionPlates(final LIMSConnection sourceLims, final LIMSConnection destinationLims, final Map<String, String> tissueIdMapping, final ProgressListener progressListener) throws Throwable {
        final AtomicReference<Throwable> callbackException = new AtomicReference<Throwable>();

        final int plateCount = getPlateCount(sourceLims, "extraction");
        final Map<String, String> extractionIdMapping = new HashMap<String, String>();

        sourceLims.getMatchingDocumentsFromLims(
                Query.Factory.createFieldQuery(LIMSConnection.PLATE_TYPE_FIELD, Condition.EQUAL, new Object[]{"Extraction"},
                        BiocodeService.getSearchDownloadOptions(false, false, true, false)),
                null, new RetrieveCallback() {
                    private boolean canceled = false;
                    double currentPlate = 0;

                    protected void _add(PluginDocument document, Map<String, Object> searchResultProperties) {
                        if (canceled) {
                            return;
                        }
                        handleDocument((PlateDocument) document);
                    }

                    protected void _add(AnnotatedPluginDocument document, Map<String, Object> searchResultProperties) {
                        if (canceled) {
                            return;
                        }
                        try {
                            PluginDocument pluginDocument = document.getDocument();
                            handleDocument((PlateDocument) pluginDocument);
                        } catch (DocumentOperationException e) {
                            canceled = true;
                            callbackException.set(e);
                        }
                    }


                    private void handleDocument(PlateDocument plate) {
                        progressListener.setProgress(currentPlate / plateCount);
                        currentPlate++;
                        int oldPlateId = plate.getPlate().getId();
                        plate.getPlate().setId(-1);
                        List<String> sourceExtractionIds = new ArrayList<String>();
                        for (Reaction r : plate.getPlate().getReactions()) {
                            if (!r.isEmpty() && r.getExtractionId().length() > 0) {
                                sourceExtractionIds.add(r.getExtractionId());
                            }
                        }
                        Set<String> existingExtractionIds;
                        try {
                            existingExtractionIds = destinationLims.getAllExtractionIdsStartingWith(sourceExtractionIds);
                        } catch (SQLException e) {
                            canceled = true;
                            callbackException.set(e);
                            return;
                        }
                        for (Reaction r : plate.getPlate().getReactions()) {
                            ExtractionReaction reaction = (ExtractionReaction) r;
                            reaction.setId(-1);
                            String tissueId = reaction.getTissueId();
                            if (tissueIdMapping != null && tissueId != null && tissueId.length() > 0) {
                                String newTissueId = tissueIdMapping.get(tissueId);
                                if (newTissueId == null) {
                                    callbackException.set(new DocumentOperationException("Could not find a mapped value for the tissue id \"" + tissueId + "\""));
                                    return;
                                }
                                reaction.setTissueId(newTissueId);
                            }
                            String extractionId = reaction.getExtractionId();
                            String originalExtractionId = extractionId;
                            if (extractionId.length() > 0) {
                                int count = 2;
                                while (existingExtractionIds.contains(extractionId)) {
                                    extractionId = originalExtractionId + "_" + count;
                                    count++;
                                }
                                existingExtractionIds.add(extractionId);
                                reaction.setExtractionId(extractionId);
                                if (!extractionId.equals(originalExtractionId)) {
                                    extractionIdMapping.put(originalExtractionId, extractionId);
                                }
                            }
                        }
                        try {
                            BiocodeService.getInstance().saveExtractions(null, plate.getPlate(), destinationLims);
                            copyGelImages(sourceLims, destinationLims, oldPlateId, plate.getPlate().getId());
                        } catch (SQLException e) {
                            canceled = true;
                            callbackException.set(e);
                        } catch (BadDataException e) {
                            canceled = true;
                            callbackException.set(e);
                        } catch (DatabaseServiceException e) {
                            canceled = true;
                            callbackException.set(e);
                        }
                    }

                    @Override
                    protected boolean _isCanceled() {
                        return progressListener.isCanceled() || canceled;
                    }
                }, false
        );

        Throwable exception = callbackException.get();
        if(exception != null) {
            throw exception;
        }
        return extractionIdMapping;
    }

    private static void copyGelImages(LIMSConnection sourceLims, LIMSConnection destinationLims, int sourcePlate, int destPlate) throws SQLException{
        PreparedStatement getImagesStatement = sourceLims.createStatement("SELECT * from gelimages WHERE plate=?");
        PreparedStatement insertImagesStatement = destinationLims.createStatement("INSERT INTO gelimages(plate, imagedata, notes, name) VALUES (?, ?, ?, ?)");
        getImagesStatement.setInt(1, sourcePlate);
        ResultSet resultSet = getImagesStatement.executeQuery();

        while(resultSet.next()) {
            insertImagesStatement.setInt(1, destPlate);
            insertImagesStatement.setBlob(2, resultSet.getBlob("imagedata"));
            insertImagesStatement.setString(3, resultSet.getString("notes"));
            insertImagesStatement.setString(4, resultSet.getString("name"));
            int rowCount = insertImagesStatement.executeUpdate();
            if(rowCount != 1) {
                throw new SQLException("The gel Image "+resultSet.getString("name")+" seems to have not copied properly.  Expected 1 row updated, got "+rowCount);
            }
        }
    }

    private static String getDestinationWorkflowId(String sourceWorkflowId, Map<String, String> workflowMap) {
        if(sourceWorkflowId.trim().length() == 0) {
            return "";
        }
        String destWorkflowId = workflowMap.get(sourceWorkflowId);
        if(destWorkflowId == null) {
            return "";
        }
        return destWorkflowId;
    }

    private static Map<Integer, Integer> copyCocktails(List<? extends Cocktail> cocktails, LIMSConnection destinationLims) throws SQLException {
        Map<Integer, Integer> idMap = new HashMap<Integer, Integer>();
        Statement statement = destinationLims.createStatement();
        for(Cocktail cocktail : cocktails) {
            if(allowedNames.contains(cocktail.getName())) {
                continue;
            }
            int updateCount = statement.executeUpdate(cocktail.getSQLString());
            if(updateCount <= 0) {
                throw new SQLException("Could not insert cocktail");
            }
            int newId = destinationLims.getLastInsertId();
            idMap.put(cocktail.getId(), newId);
        }
        return idMap;
    }

    private static Map<Integer, Integer> copyThermocycles(List<Thermocycle> thermocycles, String tableName, LIMSConnection destinationLims) throws SQLException {
        Map<Integer, Integer> idMap = new HashMap<Integer, Integer>();
        for(Thermocycle tCycle : thermocycles) {
            int oldId = tCycle.getId();
            if(allowedNames.contains(tCycle.getName())) {
                idMap.put(oldId, oldId);
                continue;
            }
            int id = tCycle.toSQL(destinationLims);
            idMap.put(oldId, id);
            PreparedStatement statement = destinationLims.createStatement("INSERT INTO "+tableName+" (cycle) VALUES ("+id+")");
            statement.execute();
            statement.close();
        }
        return idMap;
    }

    private static String getErrorMessageForDuplicateNames(String friendlyTableName, List<String> names) {
        StringBuilder message = new StringBuilder("<html>The following "+friendlyTableName+" exist in both the source and destination LIMS.  Please delete or rename them in one of your LIMS databases:<ul>");

        for(String name : names) {
            message.append("<li>"+name+"</li>");
        }

        message.append("</ul></html>");

        return message.toString();
    }


    private static void checkForDuplicateNames(LIMSConnection sourceLims, LIMSConnection destinationLims, String tableName, String columnName, String friendlyTableName) throws SQLException, DocumentOperationException {
        Set<String> existingPlateNames = new LinkedHashSet<String>();
        List<String> duplicatePlateNames = new ArrayList<String>();

        PreparedStatement sourceLimsPlateNamesStatement = sourceLims.createStatement("SELECT "+columnName+" from "+tableName);
        ResultSet sourceLimsResultSet = sourceLimsPlateNamesStatement.executeQuery();
        while(sourceLimsResultSet.next()) {
            String name = sourceLimsResultSet.getString(columnName);
            if(allowedNames.contains(name)) {
                continue;
            }
            existingPlateNames.add(name);
        }
        sourceLimsPlateNamesStatement.close();

        PreparedStatement destinationLimsPlateNamesStatement = destinationLims.createStatement("SELECT "+columnName+" from "+tableName);
        ResultSet destinationLimsPlateNameResultSet = destinationLimsPlateNamesStatement.executeQuery();
        while(destinationLimsPlateNameResultSet.next()) {
            String name = destinationLimsPlateNameResultSet.getString(columnName);
            if(!existingPlateNames.add(name)) {
                duplicatePlateNames.add(name);
            }
        }
        destinationLimsPlateNamesStatement.close();
        if(duplicatePlateNames.size() > 0) {
            throw new DocumentOperationException(getErrorMessageForDuplicateNames(friendlyTableName, duplicatePlateNames));
        }
    }

    @Override
    public boolean isDocumentGenerator() {
        return false;
    }
}
