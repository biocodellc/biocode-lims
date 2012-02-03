package com.biomatters.plugins.biocode.labbench;

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
import com.biomatters.plugins.biocode.labbench.lims.LocalLIMS;
import com.biomatters.plugins.biocode.labbench.lims.LIMSConnection;
import com.biomatters.plugins.biocode.labbench.reaction.*;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.sql.SQLException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

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
                "<li>This operaiton will not write anything to the destination database unless the entire import is successful.  However, please make sure <br>" +
                "that you back up your destination LIMS before performing this operation</li>" +
                "" +
                "</ul></html>", false, true);

        List<Options.OptionValue> databaseValues = LocalLIMS.getDatabaseOptionValues();

        options.addComboBoxOption(DATABASE, "Select your source database",databaseValues, databaseValues.get(0));

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

        final LIMSConnection destinationLims = BiocodeService.getInstance().getActiveLIMSConnection();
        try {
            progressListener.setIndeterminateProgress();
            progressListener.setMessage("Checking for plates, cocktails, and thermocycles with the same name");

            LIMSConnection sourceLims = new LIMSConnection(databaseName);

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

            Map<Integer, Integer> pcrCocktailMap = copyCocktails(BiocodeService.getPCRCocktailsFromDatabase(sourceLims), destinationLims);
            checkForCancelled(progressListener);

            Map<Integer, Integer> sequencingCocktailMap = copyCocktails(BiocodeService.getCycleSequencingCocktailsFromDatabase(sourceLims), destinationLims);
            checkForCancelled(progressListener);

            Map<Integer, Integer> pcrThermocycleMap = copyThermocycles(BiocodeService.getThermocyclesFromDatabase("pcr_thermocycle", sourceLims), "pcr_thermocycle", destinationLims);
            checkForCancelled(progressListener);

            Map<Integer, Integer> sequencingThermocycleMap = copyThermocycles(BiocodeService.getThermocyclesFromDatabase("cyclesequencing_thermocycle", sourceLims), "cyclesequencing_thermocycle", destinationLims);
            checkForCancelled(progressListener);

            BiocodeService.getInstance().buildCaches();
            checkForCancelled(progressListener);

            CompositeProgressListener composite = new CompositeProgressListener(progressListener, 5);
            checkForCancelled(progressListener);

            composite.beginSubtask("Copying Extraction Plates");
            copyExtractionPlates(sourceLims, destinationLims, composite);
            checkForCancelled(progressListener);

            Map<String, String> workflowMap = new HashMap<String, String>();

            composite.beginSubtask("Copying PCR Plates");
            copyReactionPlates(sourceLims, destinationLims, "PCR", pcrCocktailMap, pcrThermocycleMap, workflowMap, composite);
            checkForCancelled(progressListener);

            composite.beginSubtask("Copying Sequencing Plates");
            Map<Integer, Integer> sequencingReactionMap = copyReactionPlates(sourceLims, destinationLims, "CycleSequencing", sequencingCocktailMap, sequencingThermocycleMap, workflowMap, composite);
            checkForCancelled(progressListener);

            composite.beginSubtask("Copying Raw Traces");
            copyTraces(sourceLims, destinationLims, sequencingReactionMap, progressListener);
            checkForCancelled(progressListener);

            composite.beginSubtask("Copying Sequences");
            copyAssemblies(sourceLims, destinationLims, workflowMap, progressListener);
            checkForCancelled(progressListener);


        } catch (ConnectionException e) {
            destinationLims.rollback();
            throw new DocumentOperationException(e.getMessage(), e);
        } catch (SQLException e) {
            destinationLims.rollback();
            e.printStackTrace();
            throw new DocumentOperationException(e.getMessage(), e);
        } catch(DocumentOperationException ex) {
            destinationLims.rollback();
            throw ex;
        } catch(Throwable th) {
            destinationLims.rollback();
            throw new RuntimeException(th);
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
        PreparedStatement getTracesStatement = sourceLims.createStatement("SELECT * from traces");
        PreparedStatement saveTraceStatement = destinationLims.createStatement("INSERT INTO traces (reaction, name, data) values (?, ?, ?)");
        ResultSet getTracesResult = getTracesStatement.executeQuery();

        while(getTracesResult.next()) {
            saveTraceStatement.setInt(1, sequencingReactionMap.get(getTracesResult.getInt("reaction")));
            saveTraceStatement.setString(2, getTracesResult.getString("name"));
            saveTraceStatement.setBlob(3, getTracesResult.getBlob("data"));
            int result = saveTraceStatement.executeUpdate();
            if(result != 1) {
                throw new SQLException("Failed to save the trace "+getTracesResult.getString("name")+" to the destination database.");
            }
        }
    }

    private static void checkForCancelled(ProgressListener progress) throws DocumentOperationException {
        if(progress.isCanceled()) {
            throw new DocumentOperationException.Canceled();
        }
    }


    private static Map<Integer, Integer> copyReactionPlates(LIMSConnection sourceLims, final LIMSConnection destinationLims, String reactionType, final Map<Integer, Integer> cocktailMap, final Map<Integer, Integer> thermocycleMap, final Map<String, String> workflowMap,  final ProgressListener progressListener) throws Throwable {
        final AtomicReference<Throwable> callbackException = new AtomicReference<Throwable>();
        final Map<Integer, Integer> reactionMap = new LinkedHashMap<Integer, Integer>();

        sourceLims.getMatchingPlateDocuments(Query.Factory.createFieldQuery(LIMSConnection.PLATE_TYPE_FIELD, Condition.EQUAL, reactionType), Collections.<WorkflowDocument>emptyList(), new RetrieveCallback(){
            private boolean canceled = false;

            protected void _add(PluginDocument document, Map<String, Object> searchResultProperties) {
                handleDocument((PlateDocument)document);
            }

            protected void _add(AnnotatedPluginDocument document, Map<String, Object> searchResultProperties) {
                try {
                    PluginDocument pluginDocument = document.getDocument();
                    handleDocument((PlateDocument) pluginDocument);
                } catch (DocumentOperationException e) {
                    canceled = true;
                    callbackException.set(e);
                }
            }

            private void handleDocument(PlateDocument plate) {
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
                    r.setWorkflow(null);
                    r.getOptions().setValue(ReactionOptions.WORKFLOW_ID, workflowId);
                    plateReactions[0].areReactionsValid(Arrays.asList(r), null, false); //call this to set the workflow objects...

                    //set cocktails
                    int existingCocktailId = Integer.parseInt(r.getOptions().getValueAsString(ReactionOptions.COCKTAIL_OPTION_ID));
                    Integer newCocktailId = cocktailMap.get(existingCocktailId);
                    if(newCocktailId == null) {
                        newCocktailId = existingCocktailId; //the ignored cocktails (i.e. the no cocktail one)
                    }
                    r.getOptions().setValue(ReactionOptions.COCKTAIL_OPTION_ID, newCocktailId);
                }
                try {
                    BiocodeService.getInstance().saveReactions(null, destinationLims, plate.getPlate());
                } catch (SQLException e) {
                    canceled = true;
                    callbackException.set(e);
                    return;
                } catch (BadDataException e) {
                    canceled = true;
                    callbackException.set(e);
                    return;
                }

                List<PlateDocument> savedPlates = null;
                try {
                    savedPlates = destinationLims.getMatchingPlateDocuments(Query.Factory.createFieldQuery(LIMSConnection.PLATE_NAME_FIELD, Condition.EQUAL, plate.getPlate().getName()), Collections.<WorkflowDocument>emptyList(), null);
                } catch (SQLException e) {
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
                return progressListener.isCanceled() && canceled;
            }
        });

        if(callbackException.get() != null) {
            throw callbackException.get();
        }
        return reactionMap;
    }

    private static void copyExtractionPlates(LIMSConnection sourceLims, final LIMSConnection destinationLims, final ProgressListener progressListener) throws Throwable {
        final AtomicReference<Throwable> callbackException = new AtomicReference<Throwable>();

        sourceLims.getMatchingPlateDocuments(Query.Factory.createFieldQuery(LIMSConnection.PLATE_TYPE_FIELD, Condition.EQUAL, "Extraction"), Collections.<WorkflowDocument>emptyList(), new RetrieveCallback(){
            private boolean canceled = false;

            protected void _add(PluginDocument document, Map<String, Object> searchResultProperties) {
                handleDocument((PlateDocument)document);
            }

            protected void _add(AnnotatedPluginDocument document, Map<String, Object> searchResultProperties) {
                try {
                    PluginDocument pluginDocument = document.getDocument();
                    handleDocument((PlateDocument) pluginDocument);
                } catch (DocumentOperationException e) {
                    canceled = true;
                    callbackException.set(e);
                }
            }



            private void handleDocument(PlateDocument plate) {
                plate.getPlate().setId(-1);
                for(Reaction r : plate.getPlate().getReactions()) {
                    r.setId(-1);
                }
                try {
                    BiocodeService.getInstance().saveExtractions(null, plate.getPlate(), destinationLims);
                } catch (SQLException e) {
                    canceled = true;
                    callbackException.set(e);
                } catch (BadDataException e) {
                    canceled = true;
                    callbackException.set(e);
                }
            }

            @Override
            protected boolean _isCanceled() {
                return progressListener.isCanceled() && canceled;
            }
        });

        if(callbackException.get() != null) {
            throw callbackException.get();
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
