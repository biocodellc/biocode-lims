package com.biomatters.plugins.biocode.labbench.reaction;

import com.biomatters.geneious.publicapi.databaseservice.DatabaseServiceException;
import com.biomatters.geneious.publicapi.plugin.Options;
import com.biomatters.geneious.publicapi.documents.DocumentField;
import com.biomatters.geneious.publicapi.components.Dialogs;
import com.biomatters.geneious.publicapi.utilities.StringUtilities;
import com.biomatters.plugins.biocode.labbench.lims.LIMSConnection;
import com.biomatters.plugins.biocode.labbench.plates.Plate;
import com.biomatters.plugins.biocode.labbench.plates.GelImage;
import com.biomatters.plugins.biocode.labbench.fims.FIMSConnection;
import com.biomatters.plugins.biocode.labbench.BiocodeService;
import com.biomatters.plugins.biocode.labbench.Workflow;
import com.biomatters.plugins.biocode.labbench.FimsSample;
import com.biomatters.plugins.biocode.labbench.ConnectionException;

import javax.swing.*;
import java.util.*;
import java.util.List;
import java.awt.*;
import java.sql.*;

/**
 * @author Steven Stones-Havas
 * @version $Id$
 *          <p/>
 *          Created on 12/06/2009 5:27:29 PM
 */
@SuppressWarnings({"ConstantConditions"})
public class ExtractionReaction extends Reaction<ExtractionReaction>{
    private boolean justMoved = false;

    public ExtractionReaction(){}

    public ExtractionReaction(ResultSet r) throws SQLException{
        ReactionOptions options = getOptions();
        init(r, options);
    }

    private void init(ResultSet r, Options options) throws SQLException {
        setId(r.getInt("extraction.id"));
        setCreated(r.getTimestamp("extraction.date"));
        options.setValue(ExtractionOptions.TISSUE_ID, r.getString("extraction.sampleId"));
        options.setValue("extractionId", r.getString("extraction.extractionId"));
        extractionBarcode = r.getString("extraction.extractionBarcode");
        databaseIdOfExtraction = getId();
        options.setValue("extractionBarcode", extractionBarcode);
        options.setValue("extractionMethod", r.getString("extraction.method"));
        options.setValue("previousPlate", r.getString("extraction.previousPlate"));
        options.setValue("previousWell", r.getString("extraction.previousWell"));
        options.setValue("parentExtraction", r.getString("extraction.parent"));
        options.setValue("volume", r.getInt("extraction.volume"));
        options.setValue("concentrationStored", r.getBoolean("concentrationStored") ? "yes" : "no");
        options.setValue("concentration", r.getDouble("concentration"));
        options.setValue("dilution", r.getInt("extraction.dilution"));
        options.setValue("notes", r.getString("extraction.notes"));
        //noinspection unchecked
        options.getOption("date").setValue(r.getDate("extraction.date")); //we use getOption() here because the toString() method of java.sql.Date is different to the toString() method of java.util.Date, so setValueFromString() fails in DateOption
        options.setValue("technician", r.getString("extraction.technician"));
        setPlateId(r.getInt("extraction.plate"));
        setPosition(r.getInt("extraction.location"));
        String workflowName = null;
        try {
            workflowName = r.getString("workflow.name");
        } catch (SQLException e) {
            e.printStackTrace();
            //ignore...
        }

        String plateName = null;
        try {
            plateName = r.getString("plate.name");
        } catch (SQLException e) {
            e.printStackTrace();
            //ignore...
        }
        if (plateName != null) {
            setPlateName(plateName);
            setLocationString(Plate.getWell(getPosition(), Plate.getSizeEnum(r.getInt("plate.size"))).toString());
        }

        byte[] imageBytes = r.getBytes("extraction.gelimage");
        if (imageBytes != null) {
            setGelImage(new GelImage(imageBytes, getLocationString()));
        }
        options.setValue("control", r.getString("extraction.control"));


        if (workflowName != null) {
            options.setValue("workflowId", workflowName);
            setWorkflow(new Workflow(r.getInt("workflow.id"), r.getString("workflow.name"), r.getString("extraction.extractionId"), r.getString("workflow.locus"), r.getDate("workflow.date")));
            options.setValue("workflowId", getWorkflow().getName());
        }

        FIMSConnection fimsConnection = BiocodeService.getInstance().getActiveFIMSConnection();
        if (fimsConnection != null) {
            setFimsSample(fimsConnection.getFimsSampleFromCache(options.getValueAsString(ExtractionOptions.TISSUE_ID)));
        }
    }

    public static void copyExtractionReaction(ExtractionReaction src, ExtractionReaction dest) {
        ReactionUtilities.copyReaction(src, dest);
        dest.setId(src.getId());
        dest.setExtractionId(src.getExtractionId());
    }

    public String getLocus() {
        return null; //extractions don't have a locus
    }

    public String getExtractionId() {
        return getOptions().getValueAsString("extractionId");
    }

    public void setExtractionId(String s) {
        getOptions().setValue("extractionId", s);
    }

    public String getTissueId() {
        return getOptions().getValueAsString(ExtractionOptions.TISSUE_ID);
    }

    public void setTissueId(String s) {
        getOptions().setValue(ExtractionOptions.TISSUE_ID, s);
    }
    

    private ReactionOptions options;

    public ReactionOptions _getOptions() {
        if(options == null) {
            options = new ExtractionOptions();
        }
        return options;
    }

    public void setOptions(ReactionOptions op) {
        this.options = op;
    }

    public void setThermocycle(Thermocycle tc){}
    public Thermocycle getThermocycle() {
        return null;  //Extractions don't have thermocycles
    }

    public Cocktail getCocktail() {
        return null; //extractions don't have cocktails
    }
    
    public Type getType() {
        return Type.Extraction;
    }

    public boolean isJustMoved() {
        return justMoved;
    }

    public void setJustMoved(boolean justMoved) {
        this.justMoved = justMoved;
    }

    public static List<DocumentField> getDefaultDisplayedFields() {
        if(BiocodeService.getInstance().isLoggedIn()) {
            return Arrays.asList(
                    BiocodeService.getInstance().getActiveFIMSConnection().getTissueSampleDocumentField(),
                    new DocumentField("Extraction Id", "", "extractionId", String.class, false, false)
            );
        }
        else {
            return Arrays.asList(
                    new DocumentField("Extraction Id", "", "extractionId", String.class, false, false)
            );
        }
    }


    public Color _getBackgroundColor() {
        return Color.white;
    }

    public String areReactionsValid(List<ExtractionReaction> reactions, JComponent dialogParent) {
        if (!BiocodeService.getInstance().isLoggedIn()) {
            return "You are not logged in to the database.";
        }

        ReactionUtilities.setReactionErrorStates(reactions, false);

        StringBuilder errorBuilder = new StringBuilder();

        try {
            String existingExtractionReactionsAssociatedWithAttributesOfNewExtractionReactionsCheckResult = checkForExistingExtractionReactionsAssociatedWithAttributesOfNewExtractionReactions(reactions);
            if (!existingExtractionReactionsAssociatedWithAttributesOfNewExtractionReactionsCheckResult.isEmpty()) {
                errorBuilder.append(existingExtractionReactionsAssociatedWithAttributesOfNewExtractionReactionsCheckResult).append("<br><br>");
            }
        } catch (DatabaseServiceException e) {
            return e.getMessage();
        }

        Set<String> samplesToGet = new HashSet<String>();
        for (Reaction reaction : reactions) {
            ReactionOptions option = reaction.getOptions();
            String tissueId = option.getValueAsString(ExtractionOptions.TISSUE_ID);

            if (reaction.isEmpty() || tissueId == null || tissueId.length() == 0) {
                continue;
            }
            samplesToGet.add(tissueId);
        }

        if (!samplesToGet.isEmpty()) {
            try {
                boolean emptyFimsRecord = false;
                List<FimsSample> docList = BiocodeService.getInstance().getActiveFIMSConnection().retrieveSamplesForTissueIds(samplesToGet);
                Map<String, FimsSample> docMap = new HashMap<String, FimsSample>();
                for (FimsSample sample : docList) {
                    if (sample == null) {  //don't know how this could happen but it is a possible cause of MBP-269
                        assert false;
                        continue;
                    }
                    if (sample.getId() == null) {
                        emptyFimsRecord = true;
                        errorBuilder.append("Encountered a tissue record for the specimen ").append(sample.getSpecimenId()).append(" that has no tissue id.<br><br>");
                    } else {
                        docMap.put(sample.getId(), sample);
                    }
                }
                for (Reaction reaction : reactions) {
                    ReactionOptions op = reaction.getOptions();
                    String tissueId = op.getValueAsString(ExtractionOptions.TISSUE_ID);

                    if (reaction.isEmpty() || tissueId == null || tissueId.length() == 0) {
                        continue;
                    }
                    FimsSample currentFimsSample = docMap.get(tissueId);
                    if (currentFimsSample == null) {
                        //we used to report an error here, but since we want to allow extractions to be created 'headless' and then linked to tissue samples as they are entered into the FIMS later, we're just setting the FIMS sample and not complaining
//                    error += "The tissue sample "+tissueId+" does not exist in the database.\n";
//                    reaction.isError = true;
                        if (emptyFimsRecord) { //however if we've found an empty FIMS record (as in MBP-269), we should flag reactions such as this as errored so the user knows where to look.
                            reaction.setHasError(true);
                        }
                    } else {
                        reaction.setFimsSample(currentFimsSample);
                    }
                }

            } catch (ConnectionException e) {
                return "Could not query the FIMS database. " + e.getMessage();
            }
        }

        //give the user the option to not save reactions with no extraction id
        List<Reaction> reactionsWithNoIds = new ArrayList<Reaction>();
        for (Reaction r : reactions) {
            if (r.getExtractionId().length() == 0 && !r.isEmpty()) {
                reactionsWithNoIds.add(r);
            }
        }
        if (reactionsWithNoIds.size() > 0 && reactionsWithNoIds.size() < reactions.size() && Dialogs.showYesNoDialog("You have added information to reactions on your plate which have no tissue data.  Would you like to discard this information so that the wells remain empty?<br>(Cases where you might not want to make the reaction blank would be where you are creating a control reaction, or if you have wells filled with cocktail, but no DNA)", "Extractions with no ids", dialogParent, Dialogs.DialogIcon.QUESTION)) {
            for (Reaction r : reactionsWithNoIds) {
                r.getOptions().restoreDefaults();
                r.isEmpty();
            }
        }

        for (Reaction r : reactions) {
            if (!r.isEmpty() && r.getExtractionId().length() == 0) {
                errorBuilder.append("Extraction reactions cannot have empty ids.<br><br>");
                r.setHasError(true);
            }
        }

        if (errorBuilder.length() > 0) {
            return "<html><b>There were some errors in your data:</b><br>" + errorBuilder.toString() + "<br>The affected reactions have been highlighted in yellow.</html>";
        }

        return "";
    }

    private static String checkForExistingExtractionReactionsAssociatedWithAttributesOfNewExtractionReactions(Collection<ExtractionReaction> extractionReactions) throws DatabaseServiceException {
        StringBuilder errorBuilder = new StringBuilder();

        String extractionIDCheck = checkForExistingExtractionReactionsAssociatedWithExtractionIDsOfNewExtractionReactions(extractionReactions);
        if (!extractionIDCheck.isEmpty()) {
            errorBuilder.append(extractionIDCheck).append("<br><br>");
        }

        String extractionBarcodeCheck = checkForExistingExtractionReactionAssociatedWithExtractionBarcodesOfNewExtractionReactions(extractionReactions);
        if (!extractionBarcodeCheck.isEmpty()) {
            errorBuilder.append(extractionBarcodeCheck).append("<br><br>");
        }

        setJustMoved(extractionReactions, false);

        return errorBuilder.toString();
    }

    private static String checkForExistingExtractionReactionsAssociatedWithExtractionIDsOfNewExtractionReactions(Collection<ExtractionReaction> extractionReactions) throws DatabaseServiceException {
        return checkForExistingExtractionReactionsAssociatedWithAttributeOfNewExtractionReactions(extractionReactions, new ExtractionIDGetter(), new ExtractionReactionRetrieverViaID());
    }

    private static String checkForExistingExtractionReactionAssociatedWithExtractionBarcodesOfNewExtractionReactions(Collection<ExtractionReaction> extractionReactions) throws DatabaseServiceException {
        return checkForExistingExtractionReactionsAssociatedWithAttributeOfNewExtractionReactions(extractionReactions, new ExtractionBarcodeGetter(), new ExtractionReactionRetrieverViaBarcode());
    }

    private static String checkForExistingExtractionReactionsAssociatedWithAttributeOfNewExtractionReactions(Collection<ExtractionReaction> extractionReactions,
                                                                                                             ReactionAttributeGetter<String> reactionAttributeGetter,
                                                                                                             ReactionRetriever<ExtractionReaction, LIMSConnection, List<String>> reactionRetriever) throws DatabaseServiceException {
        Map<String, List<ExtractionReaction>> attributeToNewExtractionReactions = ReactionUtilities.buildAttributeToReactionsMap(extractionReactions, reactionAttributeGetter);

        Collection<ExtractionReaction> existingExtractionReactions = reactionRetriever.retrieve(BiocodeService.getInstance().getActiveLIMSConnection(), new ArrayList<String>(attributeToNewExtractionReactions.keySet()));

        filterExtractionReactions(existingExtractionReactions, extractionReactions);

        Map<String, List<ExtractionReaction>> attributeToExistingExtractionReactions = ReactionUtilities.buildAttributeToReactionsMap(existingExtractionReactions, reactionAttributeGetter);

        Map<List<ExtractionReaction>, List<ExtractionReaction>> existingExtractionReactionsToNewExtractionReactions = buildExistingExtractionsReactionsToNewExtractionReactionsMap(
                attributeToNewExtractionReactions,
                attributeToExistingExtractionReactions
        );

        if (!existingExtractionReactionsToNewExtractionReactions.isEmpty()) {
            String attributeName = reactionAttributeGetter.getAttributeName();
            if (Dialogs.showYesNoDialog(
                    "Extraction reactions that are associated with the following " + attributeName.toLowerCase() + "(s) already exist: " + StringUtilities.join(", ", attributeToExistingExtractionReactions.keySet()) + "."
                            + "<br><br> Move the existing extraction reactions to the plate and override the corresponding new extraction reactions?",
                    "Existing Extractions With " + attributeName + " Detected",
                    null,
                    Dialogs.DialogIcon.QUESTION)) {
                return overrideNewExtractionReactionsWithExistingExtractionReactionsWithSameAttribute(existingExtractionReactionsToNewExtractionReactions, reactionAttributeGetter);
            } else {
                List<ExtractionReaction> newExtractionReactionsAssociatedWithExistingAttributeValue = new ArrayList<ExtractionReaction>();

                for (List<ExtractionReaction> groupOfNewExtractionReactionsAssociatedWithSameExistingBarcode : existingExtractionReactionsToNewExtractionReactions.values()) {
                    newExtractionReactionsAssociatedWithExistingAttributeValue.addAll(groupOfNewExtractionReactionsAssociatedWithSameExistingBarcode);
                }

                ReactionUtilities.setReactionErrorStates(newExtractionReactionsAssociatedWithExistingAttributeValue, true);
            }
        }

        return "";
    }

    private static void filterExtractionReactions(Collection<ExtractionReaction> extractionReactions, Collection<ExtractionReaction> extractionReactionsToExclude) {
        Collection<ExtractionReaction> extractionReactionsToFilterOut = new ArrayList<ExtractionReaction>();
        Collection<Integer> databaseIDsOfExtractionsAssociatedWithExtractionReactionsToExclude = ReactionUtilities.getDatabaseIDOfExtractions(extractionReactionsToExclude);

        for (ExtractionReaction extractionReaction : extractionReactions) {
            if (databaseIDsOfExtractionsAssociatedWithExtractionReactionsToExclude.contains(extractionReaction.getDatabaseIdOfExtraction()) || extractionReaction.isJustMoved()) {
                extractionReactionsToFilterOut.add(extractionReaction);
            }
        }

        extractionReactions.removeAll(extractionReactionsToFilterOut);
    }

    private static Map<List<ExtractionReaction>, List<ExtractionReaction>> buildExistingExtractionsReactionsToNewExtractionReactionsMap(Map<String, List<ExtractionReaction>> identifierToNewExtractionReactions,
                                                                                                                                        Map<String, List<ExtractionReaction>> identifierToExistingExtractionReactions) {
        Map<List<ExtractionReaction>, List<ExtractionReaction>> existingExtractionsToNewExtractions = new HashMap<List<ExtractionReaction>, List<ExtractionReaction>>();

        for (Map.Entry<String, List<ExtractionReaction>> identifierAndExtractionsThatExist : identifierToExistingExtractionReactions.entrySet()) {
            existingExtractionsToNewExtractions.put(identifierAndExtractionsThatExist.getValue(), identifierToNewExtractionReactions.get(identifierAndExtractionsThatExist.getKey()));
        }

        return existingExtractionsToNewExtractions;
    }

    private static String overrideNewExtractionReactionsWithExistingExtractionReactionsWithSameAttribute(Map<List<ExtractionReaction>, List<ExtractionReaction>> existingExtractionReactionsToNewExtractionReactions,
                                                                                                         ReactionAttributeGetter<String> reactionAttributeGetter) {
        List<String> extractionReactionsThatCouldNotBeOverridden = new ArrayList<String>();

        for (Map.Entry<List<ExtractionReaction>, List<ExtractionReaction>> existingExtractionReactionsAndNewExtractionReactions : existingExtractionReactionsToNewExtractionReactions.entrySet()) {
            List<ExtractionReaction> newExtractionReactions = existingExtractionReactionsAndNewExtractionReactions.getValue();

            if (newExtractionReactions.size() > 1) {
                ReactionUtilities.setReactionErrorStates(newExtractionReactions, true);

                extractionReactionsThatCouldNotBeOverridden.add(reactionAttributeGetter.getAttributeName() + ": " + reactionAttributeGetter.get(newExtractionReactions.get(0)) + ".\n" + "Well Numbers: " + StringUtilities.join(", ", ReactionUtilities.getWellNumbers(newExtractionReactions)) + ".");
            } else {
                ExtractionReaction destinationReaction = newExtractionReactions.get(0);

                ExtractionReaction.copyExtractionReaction(getExistingExtractionReactionToMove(existingExtractionReactionsAndNewExtractionReactions.getKey()), destinationReaction);

                destinationReaction.setJustMoved(true);
            }
        }

        return extractionReactionsThatCouldNotBeOverridden.isEmpty() ? "" : "Cannot override multiple new reactions that are associated with the same" + reactionAttributeGetter.getAttributeName() + ":<br><br>" + StringUtilities.join("\n\n", extractionReactionsThatCouldNotBeOverridden);
    }

    private static ExtractionReaction getExistingExtractionReactionToMove(Collection<ExtractionReaction> existingExtractionReactions) {
        // todo: determine how to handle when existingExtractionReactions.size() > 1;
        return existingExtractionReactions.iterator().next();
    }

    private static void setJustMoved(Collection<ExtractionReaction> extractionReactions, boolean val) {
        for (ExtractionReaction extractionReaction : extractionReactions) {
            extractionReaction.setJustMoved(val);
        }
    }
}