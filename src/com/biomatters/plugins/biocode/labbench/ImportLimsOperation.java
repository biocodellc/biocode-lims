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
import com.biomatters.plugins.biocode.labbench.lims.LimsConnectionOptions;
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

            checkForDuplicateNames(sourceLims, destinationLims, "thermocycle", "name", "thermocycle(s)");

            checkForDuplicateNames(sourceLims, destinationLims, "pcr_cocktail", "name", "PCR cocktail(s)");

            checkForDuplicateNames(sourceLims, destinationLims, "cyclesequencing_cocktail", "name", "sequencing cocktail(s)");

            destinationLims.beginTransaction();

            Map<Integer, Integer> pcrCocktailMap = copyCocktails(BiocodeService.getInstance().getPCRCocktails(), destinationLims);

            Map<Integer, Integer> sequencingCocktailMap = copyCocktails(BiocodeService.getInstance().getCycleSequencingCocktails(), destinationLims);

            Map<Integer, Integer> pcrThermocycleMap = copyThermocycles(BiocodeService.getInstance().getPCRThermocycles(), "pcr_thermocycles", destinationLims);

            Map<Integer, Integer> sequencingThermocycleMap = copyThermocycles(BiocodeService.getInstance().getCycleSequencingThermocycles(), "cyclesequencing_thermocycles", destinationLims);

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
            if(allowedNames.contains(tCycle.getName())) {
                continue;
            }
            int oldId = tCycle.getId();
            int id = tCycle.toSQL(destinationLims);
            PreparedStatement statement = destinationLims.createStatement("INSERT INTO "+tableName+" (cycle) VALUES ("+id+")");
            int newId = destinationLims.getLastInsertId();
            idMap.put(oldId, newId);
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
