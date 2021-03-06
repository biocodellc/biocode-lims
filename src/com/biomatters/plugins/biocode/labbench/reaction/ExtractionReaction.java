package com.biomatters.plugins.biocode.labbench.reaction;

import com.biomatters.geneious.publicapi.databaseservice.DatabaseServiceException;
import com.biomatters.geneious.publicapi.plugin.Options;
import com.biomatters.geneious.publicapi.documents.DocumentField;
import com.biomatters.geneious.publicapi.components.Dialogs;
import com.biomatters.geneious.publicapi.utilities.StringUtilities;
import com.biomatters.plugins.biocode.labbench.lims.LIMSConnection;
import com.biomatters.plugins.biocode.labbench.plates.Plate;
import com.biomatters.plugins.biocode.labbench.plates.GelImage;
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

    public String _areReactionsValid(List<ExtractionReaction> reactions, JComponent dialogParent, boolean checkingFromPlate) {
        if (!BiocodeService.getInstance().isLoggedIn()) {
            return "You are not logged in to the database.";
        }

        ReactionUtilities.setReactionErrorStates(reactions, false);

        StringBuilder errorBuilder = new StringBuilder();

        try {
            String existingExtractionReactionsAssociatedWithAttributesOfNewExtractionReactionsCheckResult = checkForExistingExtractionReactionsAssociatedWithAttributesOfNewExtractionReactions(reactions, dialogParent, checkingFromPlate);
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
                        errorBuilder.append("Encountered a tissue record for the specimen ").append(sample.getSpecimenId()).append(" that has no tissue id.<br>");
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

        List<String> emptyLocations = new ArrayList<String>();
        for (Reaction r : reactions) {
            if (!r.isEmpty() && r.getExtractionId().length() == 0) {
                emptyLocations.add(r.getLocationString());
                r.setHasError(true);
            }
        }
        if(!emptyLocations.isEmpty()) {
            errorBuilder.append("Extraction reactions cannot have empty ids");
            errorBuilder.append(checkingFromPlate ? ":" : ".");

            if(checkingFromPlate) {
                if (emptyLocations.size() < 8) {
                    errorBuilder.append(StringUtilities.humanJoin(emptyLocations)).append(".");
                } else {
                    errorBuilder.append("\n").append(StringUtilities.join("\n", emptyLocations));
                }
            }
        }

        if (errorBuilder.length() > 0) {
            return "<html><b>There were some errors in your data:</b><br>" + errorBuilder.toString() + "</html>";
        }

        return "";
    }

    private static String checkForExistingExtractionReactionsAssociatedWithAttributesOfNewExtractionReactions(Collection<ExtractionReaction> extractionReactions, JComponent dialogParent, boolean checkingFromPlate) throws DatabaseServiceException {
        StringBuilder errorBuilder = new StringBuilder();

        String extractionIDCheck = checkForExistingExtractionReactionsAssociatedWithExtractionIDsOfNewExtractionReactions(extractionReactions, dialogParent, checkingFromPlate);
        if (!extractionIDCheck.isEmpty()) {
            errorBuilder.append(extractionIDCheck).append("<br><br>");
        }

        String extractionBarcodeCheck = checkForExistingExtractionReactionAssociatedWithExtractionBarcodesOfNewExtractionReactions(extractionReactions, dialogParent, checkingFromPlate);
        if (!extractionBarcodeCheck.isEmpty()) {
            errorBuilder.append(extractionBarcodeCheck).append("<br><br>");
        }

        setJustMoved(extractionReactions, false);

        return errorBuilder.toString();
    }

    private static String checkForExistingExtractionReactionsAssociatedWithExtractionIDsOfNewExtractionReactions(Collection<ExtractionReaction> extractionReactions, JComponent dialogParent, boolean checkingFromPlate) throws DatabaseServiceException {
        return checkForExistingExtractionReactionsAssociatedWithAttributeOfNewExtractionReactions(extractionReactions, new ExtractionIDGetter(), new ExtractionReactionRetrieverViaID(), dialogParent, checkingFromPlate);
    }

    private static String checkForExistingExtractionReactionAssociatedWithExtractionBarcodesOfNewExtractionReactions(Collection<ExtractionReaction> extractionReactions, JComponent dialogParent, boolean checkingFromPlate) throws DatabaseServiceException {
        return checkForExistingExtractionReactionsAssociatedWithAttributeOfNewExtractionReactions(extractionReactions, new ExtractionBarcodeGetter(), new ExtractionReactionRetrieverViaBarcode(), dialogParent, checkingFromPlate);
    }

    private static String checkForExistingExtractionReactionsAssociatedWithAttributeOfNewExtractionReactions(Collection<ExtractionReaction> extractionReactions,
                                                                                                             ReactionAttributeGetter<String> reactionAttributeGetter,
                                                                                                             ReactionRetriever<ExtractionReaction, LIMSConnection, List<String>> reactionRetriever,
                                                                                                             JComponent dialogParent,
                                                                                                             boolean checkingFromPlate) throws DatabaseServiceException {
        Map<String, List<ExtractionReaction>> attributeToNewExtractionReactions = ReactionUtilities.buildAttributeToReactionsMap(extractionReactions, reactionAttributeGetter);

        Collection<ExtractionReaction> existingExtractionReactions = reactionRetriever.retrieve(BiocodeService.getInstance().getActiveLIMSConnection(), new ArrayList<String>(attributeToNewExtractionReactions.keySet()));

        filterExtractionReactions(existingExtractionReactions, extractionReactions);

        Map<String, List<ExtractionReaction>> attributeToExistingExtractionReactions = ReactionUtilities.buildAttributeToReactionsMap(existingExtractionReactions, reactionAttributeGetter);

        Map<List<ExtractionReaction>, List<ExtractionReaction>> existingExtractionReactionsToNewExtractionReactions = buildExistingExtractionReactionsToNewExtractionReactionsMap(
                attributeToNewExtractionReactions,
                attributeToExistingExtractionReactions
        );

        if (!existingExtractionReactionsToNewExtractionReactions.isEmpty()) {
            String attributeName = reactionAttributeGetter.getAttributeName();
            if (checkingFromPlate) {

                String move_extractions = "Move extractions";
                String create_alliquots = "Create aliquots";

                String firstPartOfMessage = attributeToExistingExtractionReactions.keySet().size() < 4 ? "Extraction reactions that are associated with the following " + attributeName.toLowerCase() + "(s) already exist: " + StringUtilities.join(", ", attributeToExistingExtractionReactions.keySet()) + ".<br><br>"
                        : "Extraction reactions that are associated with "+attributeToExistingExtractionReactions.keySet().size()+" " + attributeName.toLowerCase() + "(s) you entered already exist.  ";

                boolean b = Dialogs.showDialog(new Dialogs.DialogOptions(new String[] {move_extractions, create_alliquots}, "Extractions already exist", dialogParent, Dialogs.DialogIcon.QUESTION), firstPartOfMessage + "Are you trying to:<br><br><b>Move these extractions to this plate?</b> The existing extraction reactions will be removed from their original plate and placed on this one.  " +
                        "Their original locations will be tracked in the <i>previous plate</i> and <i>previous well</i> fields." +
                        "<br><br><b>Create aliquots?</b> The existing extraction reactions will be left untouched, and these ones will be given new extraction id's.  The location and id's of each alliquot's parent extractions will be tracked in the <i>parent extraction id</i>, " +
                        "<i>previous plate</i>, and <i>previous well</i> fields.") == move_extractions;
                if (b) {
                    return overrideExtractionReactionsWithExistingExtractionReactionsWithSameAttribute(existingExtractionReactionsToNewExtractionReactions, reactionAttributeGetter, false);
                } else {
                    overrideExtractionReactionsWithExistingExtractionReactionsWithSameAttribute(existingExtractionReactionsToNewExtractionReactions, reactionAttributeGetter, true);
                }
            } else {
                Dialogs.showMessageDialog(
                        "Extraction reactions that are associated with the following " + attributeName.toLowerCase() + "(s) already exist: " + StringUtilities.join(", ", attributeToExistingExtractionReactions.keySet()) + ".",
                        "Cannot Save Extraction Reactions",
                        dialogParent,
                        Dialogs.DialogIcon.INFORMATION
                );
            }
        }

        return "";
    }

    private static void filterExtractionReactions(Collection<ExtractionReaction> extractionReactions, Collection<ExtractionReaction> extractionReactionsToExclude) {
        Collection<ExtractionReaction> extractionReactionsToFilterOut = new ArrayList<ExtractionReaction>();
        Collection<Integer> databaseIDsOfExtractionsAssociatedWithExtractionReactionsToExclude = ReactionUtilities.getDatabaseIDOfExtractions(extractionReactionsToExclude);
        Collection<Integer> idsOfJustMovedExtractionReactions = ReactionUtilities.getIDs(getJustMovedExtractionReactions(extractionReactionsToExclude));

        for (ExtractionReaction extractionReaction : extractionReactions) {
            if (databaseIDsOfExtractionsAssociatedWithExtractionReactionsToExclude.contains(extractionReaction.getDatabaseIdOfExtraction()) || idsOfJustMovedExtractionReactions.contains(extractionReaction.getId())) {
                extractionReactionsToFilterOut.add(extractionReaction);
            }
        }

        extractionReactions.removeAll(extractionReactionsToFilterOut);
    }

    private static Collection<ExtractionReaction> getJustMovedExtractionReactions(Collection<ExtractionReaction> extractionReactions) {
        Collection<ExtractionReaction> justMovedExtractionReactions = new ArrayList<ExtractionReaction>();

        for (ExtractionReaction extractionReaction : extractionReactions) {
            if (extractionReaction.isJustMoved()) {
                justMovedExtractionReactions.add(extractionReaction);
            }
        }

        return justMovedExtractionReactions;
    }

    private static Map<List<ExtractionReaction>, List<ExtractionReaction>> buildExistingExtractionReactionsToNewExtractionReactionsMap(Map<String, List<ExtractionReaction>> identifierToNewExtractionReactions,
                                                                                                                                       Map<String, List<ExtractionReaction>> identifierToExistingExtractionReactions) {
        Map<List<ExtractionReaction>, List<ExtractionReaction>> existingExtractionsToNewExtractions = new HashMap<List<ExtractionReaction>, List<ExtractionReaction>>();

        for (Map.Entry<String, List<ExtractionReaction>> identifierAndExtractionsThatExist : identifierToExistingExtractionReactions.entrySet()) {
            existingExtractionsToNewExtractions.put(identifierAndExtractionsThatExist.getValue(), identifierToNewExtractionReactions.get(identifierAndExtractionsThatExist.getKey()));
        }

        return existingExtractionsToNewExtractions;
    }

    private static String overrideExtractionReactionsWithExistingExtractionReactionsWithSameAttribute(Map<List<ExtractionReaction>, List<ExtractionReaction>> existingExtractionReactionsToNewExtractionReactions,
                                                                                                      ReactionAttributeGetter<String> reactionAttributeGetter, boolean copyInsteadOfMove) throws DatabaseServiceException {
        List<String> extractionReactionsThatCouldNotBeOverridden = new ArrayList<String>();

        Set<String> existingExtractionIds = new LinkedHashSet<>();

        if(copyInsteadOfMove) {
            LIMSConnection activeLIMSConnection = BiocodeService.getInstance().getActiveLIMSConnection();
            Set<String> tissueIds = new LinkedHashSet<>();
            for (List<ExtractionReaction> existingExtractionReactions : existingExtractionReactionsToNewExtractionReactions.keySet()) {
                for(ExtractionReaction reaction : existingExtractionReactions) {
                    tissueIds.add(reaction.getTissueId());
                }
            }

            existingExtractionIds.addAll(activeLIMSConnection.getAllExtractionIdsForTissueIds(new ArrayList<>(tissueIds)));
        }

        for (Map.Entry<List<ExtractionReaction>, List<ExtractionReaction>> existingExtractionReactionsAndNewExtractionReactions : existingExtractionReactionsToNewExtractionReactions.entrySet()) {
            List<ExtractionReaction> newExtractionReactions = existingExtractionReactionsAndNewExtractionReactions.getValue();

            if(copyInsteadOfMove) {
                for(ExtractionReaction destinationReaction : newExtractionReactions) {
                    ExtractionReaction.copyExtractionReaction(getExistingExtractionReactionToMove(existingExtractionReactionsAndNewExtractionReactions.getKey()), destinationReaction);

                    destinationReaction.setExtractionId(ReactionUtilities.getNewExtractionId(existingExtractionIds, destinationReaction.getTissueId()));

                    existingExtractionIds.add(destinationReaction.getExtractionId());

                    destinationReaction.setJustMoved(true);
                }
            }
            else if (newExtractionReactions.size() > 1) {
                ReactionUtilities.setReactionErrorStates(newExtractionReactions, true);

                extractionReactionsThatCouldNotBeOverridden.add(reactionAttributeGetter.getAttributeName() + ": " + reactionAttributeGetter.get(newExtractionReactions.get(0)) + ".\n" + "Well Numbers: " + StringUtilities.join(", ", ReactionUtilities.getWellNumbers(newExtractionReactions)) + ".");
            } else {
                ExtractionReaction destinationReaction = newExtractionReactions.get(0);

                ExtractionReaction.copyExtractionReaction(getExistingExtractionReactionToMove(existingExtractionReactionsAndNewExtractionReactions.getKey()), destinationReaction);

                destinationReaction.getOptions().setValue("parentExtraction", "");

                destinationReaction.setJustMoved(true);
            }
        }

        return extractionReactionsThatCouldNotBeOverridden.isEmpty() ? "" : "Cannot override multiple new reactions that are associated with the same" + reactionAttributeGetter.getAttributeName() + ":<br><br>" + StringUtilities.join("\n\n", extractionReactionsThatCouldNotBeOverridden);
    }

    private static ExtractionReaction getExistingExtractionReactionToMove(Collection<ExtractionReaction> existingExtractionReactions) {
        return existingExtractionReactions.iterator().next();
    }

    private static void setJustMoved(Collection<ExtractionReaction> extractionReactions, boolean val) {
        for (ExtractionReaction extractionReaction : extractionReactions) {
            extractionReaction.setJustMoved(val);
        }
    }
}