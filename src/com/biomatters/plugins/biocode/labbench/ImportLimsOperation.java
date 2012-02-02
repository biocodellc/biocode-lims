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
        Options options = new Options(this.getClass());


        options.addLabelWithIcon("<html>This operation will import all data from the local LIMS (source database) you select into the current LIMS (destination database).  <br>Please make sure that you are connected to the correct LIMS before running this operation, and make sure that you no users <br>modify the destination LIMS while this operation is in progress.</html>", StandardIcons.warning.getIcons());

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

            CompositeProgressListener composite = new CompositeProgressListener(progressListener, 3);
            checkForCancelled(progressListener);

            composite.beginSubtask("Copying Extraction Plates");
            copyExtractionPlates(sourceLims, destinationLims, composite);
            checkForCancelled(progressListener);

            composite.beginSubtask("Copying PCR Plates");
            copyReactionPlates(sourceLims, destinationLims, "PCR", pcrCocktailMap, pcrThermocycleMap, composite);
            checkForCancelled(progressListener);

            composite.beginSubtask("Copying Sequencing Plates");
            copyReactionPlates(sourceLims, destinationLims, "CycleSequencing", sequencingCocktailMap, sequencingThermocycleMap, composite);
            checkForCancelled(progressListener);

            //todo: copy traces and sequences


        } catch (ConnectionException e) {
            destinationLims.rollback();
            throw new DocumentOperationException(e.getMessage(), e);
        } catch (SQLException e) {
            destinationLims.rollback();
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

    private static void checkForCancelled(ProgressListener progress) throws DocumentOperationException {
        if(progress.isCanceled()) {
            throw new DocumentOperationException.Canceled();
        }
    }


    private static void copyReactionPlates(LIMSConnection sourceLims, final LIMSConnection destinationLims, String reactionType, final Map<Integer, Integer> cocktailMap, final Map<Integer, Integer> thermocycleMap, final ProgressListener progressListener) throws Throwable {
        final AtomicReference<Throwable> callbackException = new AtomicReference<Throwable>();
        final Map<String, String> workflowMap = new HashMap<String, String>();

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
                String[] existingWorkflows = new String[plateReactions.length];

                int oldThermocycleId = plate.getPlate().getThermocycleId();
                int newThermocycleId = thermocycleMap.get(oldThermocycleId);
                Thermocycle newThermocycle = new Thermocycle("temp", newThermocycleId);
                plate.getPlate().setThermocycle(newThermocycle);

                for (int i = 0; i < plateReactions.length; i++) {
                    Reaction r = plateReactions[i];
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
                    r.getOptions().setValue(ReactionOptions.COCKTAIL_OPTION_ID, cocktailMap.get(existingCocktailId));
                }
                try {
                    BiocodeService.getInstance().saveReactions(null, destinationLims, plate.getPlate());
                } catch (SQLException e) {
                    canceled = true;
                    callbackException.set(e);
                } catch (BadDataException e) {
                    canceled = true;
                    callbackException.set(e);
                }
                //todo: test that two PCR plates with the same workflows copy across correctly...
                for (int i = 0; i < plateReactions.length; i++) {
                    Reaction r = plateReactions[i];
                    if(r != null && !r.isEmpty()) {
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
