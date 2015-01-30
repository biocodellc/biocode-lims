package com.biomatters.plugins.biocode.labbench.reaction;

import com.biomatters.geneious.publicapi.databaseservice.DatabaseServiceException;
import com.biomatters.geneious.publicapi.plugin.Options;
import com.biomatters.geneious.publicapi.documents.DocumentField;
import com.biomatters.geneious.publicapi.components.Dialogs;
import com.biomatters.geneious.publicapi.utilities.StringUtilities;
import com.biomatters.plugins.biocode.BiocodeUtilities;
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
        if(plateName != null) {
            setPlateName(plateName);
            setLocationString(Plate.getWell(getPosition(), Plate.getSizeEnum(r.getInt("plate.size"))).toString());
        }

        byte[] imageBytes = r.getBytes("extraction.gelimage");
        if(imageBytes != null) {
            setGelImage(new GelImage(imageBytes, getLocationString()));
        }
        options.setValue("control", r.getString("extraction.control"));
        
        
        if(workflowName != null) {
            options.setValue("workflowId", workflowName);
            setWorkflow(new Workflow(r.getInt("workflow.id"), r.getString("workflow.name"), r.getString("extraction.extractionId"), r.getString("workflow.locus"), r.getDate("workflow.date")));
            options.setValue("workflowId", getWorkflow().getName());
        }

        FIMSConnection fimsConnection = BiocodeService.getInstance().getActiveFIMSConnection();
        if(fimsConnection != null) {
            setFimsSample(fimsConnection.getFimsSampleFromCache(options.getValueAsString(ExtractionOptions.TISSUE_ID)));
        }
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


    public String areReactionsValid(List<ExtractionReaction> reactions, JComponent dialogParent, boolean showDialogs) {
        if(!BiocodeService.getInstance().isLoggedIn()) {
            return "You are not logged in to the database";
        }
        FIMSConnection fimsConnection = BiocodeService.getInstance().getActiveFIMSConnection();

        Set<String> samplesToGet = new HashSet<String>();

        Map<String, List<Reaction>> extractionBarcodeToReactions = new HashMap<String, List<Reaction>>();

        for(Reaction reaction : reactions) {
            String extractionBarcode = reaction.getOptions().getValueAsString(BiocodeUtilities.EXTRACTION_BARCODE_FIELD.getCode());
            if (extractionBarcode != null && !extractionBarcode.isEmpty()) {
                List<Reaction> reactionsAssociatedWithExtractionBarcode = extractionBarcodeToReactions.get(extractionBarcode);
                if (reactionsAssociatedWithExtractionBarcode == null) {
                    reactionsAssociatedWithExtractionBarcode = new ArrayList<Reaction>();
                    extractionBarcodeToReactions.put(extractionBarcode, reactionsAssociatedWithExtractionBarcode);
                }
                reactionsAssociatedWithExtractionBarcode.add(reaction);
            }

            ReactionOptions option = reaction.getOptions();
            String tissueId = option.getValueAsString(ExtractionOptions.TISSUE_ID);

            if(reaction.isEmpty() || tissueId == null || tissueId.length() == 0) {
                continue;
            }
            samplesToGet.add(tissueId);
        }

        Map<String, List<String>> extractionBarcodeToReactionLocation = new HashMap<String, List<String>>();

        for (Map.Entry<String, List<Reaction>> extractionBarcodeAndReactions : extractionBarcodeToReactions.entrySet()) {
            List<Reaction> reactionsAssociatedWithCurrentExtractionBarcode = extractionBarcodeAndReactions.getValue();

            if (reactionsAssociatedWithCurrentExtractionBarcode.size() > 1) {
                List<String> idsOfReactionsAssociatedWithCurrentBarcode = new ArrayList<String>();

                extractionBarcodeToReactionLocation.put(extractionBarcodeAndReactions.getKey(), idsOfReactionsAssociatedWithCurrentBarcode);

                for (Reaction reaction : reactionsAssociatedWithCurrentExtractionBarcode) {
                    reaction.setHasError(true);
                    idsOfReactionsAssociatedWithCurrentBarcode.add(reaction.getLocationString());
                }
            }
        }

        if (!extractionBarcodeToReactionLocation.isEmpty()) {
            List<String> duplicationEntries = new ArrayList<String>();

            for (Map.Entry<String, List<String>> extractionBarcodeAndReactionIDs : extractionBarcodeToReactionLocation.entrySet()) {
                duplicationEntries.add(
                        "Extraction barcode: " + extractionBarcodeAndReactionIDs.getKey() +
                        "\nWell numbers: " + StringUtilities.join(", ", extractionBarcodeAndReactionIDs.getValue()) + ".\n"
                );
            }

            Dialogs.showMessageDialog(
                    "Extraction barcodes and the well number of reactions that are associated with them:\n\n" + StringUtilities.join("\n", duplicationEntries),
                    "Reactions that are associated with the same extraction barcode were detected",
                    null,
                    Dialogs.DialogIcon.WARNING
            );
        }

        if(samplesToGet.size() == 0) {
            return null;
        }

        String error = "";
        boolean emptyFimsRecord = false;

        try {
            List<FimsSample> docList = fimsConnection.retrieveSamplesForTissueIds(samplesToGet);
            Map<String, FimsSample> docMap = new HashMap<String, FimsSample>();
            for(FimsSample sample : docList) {
                if(sample == null) {  //don't know how this could happen but it is a possible cause of MBP-269
                    assert false;
                    continue;
                }
                if(sample.getId() == null) {
                    emptyFimsRecord = true;
                    error += "Encountered a tissue record for the specimen "+sample.getSpecimenId()+" that has no tissue id\n";
                }
                else {
                    docMap.put(sample.getId(), sample);
                }
            }
            for(Reaction reaction : reactions) {
                ReactionOptions op = reaction.getOptions();
                String tissueId = op.getValueAsString(ExtractionOptions.TISSUE_ID);
                reaction.isError = false;

                if(reaction.isEmpty() || tissueId == null || tissueId.length() == 0) {
                    continue;
                }
                FimsSample currentFimsSample = docMap.get(tissueId);
                if(currentFimsSample == null) {
                    //we used to report an error here, but since we want to allow extractions to be created 'headless' and then linked to tissue samples as they are entered into the FIMS later, we're just setting the FIMS sample and not complaining
//                    error += "The tissue sample "+tissueId+" does not exist in the database.\n";
//                    reaction.isError = true;
                    if(emptyFimsRecord) { //however if we've found an empty FIMS record (as in MBP-269), we should flag reactions such as this as errored so the user knows where to look.
                        reaction.isError = true;
                    }
                }
                else {
                    reaction.setFimsSample(currentFimsSample);
                }
            }

        } catch (ConnectionException e) {
            return "Could not query the FIMS database.  "+e.getMessage();
        }

        try {
            //check that the extraction id's don't already exist in the database...
            List<String> extractionIds = new ArrayList<String>();
            for(Reaction r : reactions) {
                if(r.getId() < 0 && r.getExtractionId().length() > 0) {
                    extractionIds.add(r.getExtractionId());
                }
            }
            if(extractionIds.size() > 0) {
                List<ExtractionReaction> extractionsThatExist = BiocodeService.getInstance().getActiveLIMSConnection().getExtractionsForIds(extractionIds);
                if(extractionsThatExist.size() > 0) {
                    //ask the user if they want to move the extractions that are already attached to a plate.
                    StringBuilder moveMessage = new StringBuilder("The following extractions already exist in the database.\nDo you want to move them to this plate?\n\n");
                    for(ExtractionReaction reaction : extractionsThatExist) {
                        //noinspection StringConcatenationInsideStringBufferAppend
                        moveMessage.append(reaction.getExtractionId()+"\n");
                    }
                    if(!showDialogs || Dialogs.showYesNoDialog(moveMessage.toString(), "Move existing extractions", dialogParent, Dialogs.DialogIcon.QUESTION)) {
                        for (ExtractionReaction reaction : reactions) {
                            for (ExtractionReaction r2 : extractionsThatExist) {
                                if (reaction.getExtractionId().equals(r2.getExtractionId())) {
                                    ReactionUtilities.copyReaction(r2, reaction);
                                    reaction.setPlateId(reaction.getPlateId());
                                    reaction.setPosition(reaction.getPosition());
                                    reaction.setId(r2.getId());
                                    reaction.setExtractionId(r2.getExtractionId());
                                    reaction.setThermocycle(reaction.getThermocycle());
                                    reaction.setLocationString(reaction.getLocationString());
                                }
                            }
                        }
                    }
                    else {
                        for(Reaction r : reactions) {
                            for(Reaction r2 : extractionsThatExist) {
                                if(r.getExtractionId().equals(r2.getExtractionId())) {
                                    r.isError = true;
                                    error += "The extraction "+r.getExtractionId()+" already exists in the database.\n";
                                }
                            }
                        }
                    }
                }

            }
        } catch (DatabaseServiceException e) {
            return "Could not qurey the LIMS database: "+e.getMessage();
        }

        //give the user the option to not save reactions with no extraction id
        List<Reaction> reactionsWithNoIds = new ArrayList<Reaction>();
        for(Reaction r : reactions) {
            if(r.getExtractionId().length() == 0 && !r.isEmpty()) {
                reactionsWithNoIds.add(r);
            }
        }
        if(reactionsWithNoIds.size() > 0 && reactionsWithNoIds.size() < reactions.size() && Dialogs.showYesNoDialog("You have added information to reactions on your plate which have no tissue data.  Would you like to discard this information so that the wells remain empty?<br>(Cases where you might not want to make the reaction blank would be where you are creating a control reaction, or if you have wells filled with cocktail, but no DNA)", "Extractions with no ids", dialogParent, Dialogs.DialogIcon.QUESTION)) {
            for(Reaction r : reactionsWithNoIds) {
                r.getOptions().restoreDefaults();
                r.isEmpty();
            }
        }


        Set<String> namesSet = new HashSet<String>();
        for(Reaction r : reactions) {
            if(!r.isEmpty()) {
                if(r.getExtractionId().length() == 0) {
                    error += "Extraction reactions cannot have empty id's.\n";
                    r.isError = true;
                }
                else if(!namesSet.add(r.getExtractionId())) {
                    error += "You cannot add an extraction with the name '"+r.getExtractionId()+"' more than once.\n";
                    r.isError = true;
                }
            }
        }


        if(error.length() > 0) {
            return "<html><b>There were some errors in your data:</b><br>"+error+"<br>The affected reactions have been highlighted in yellow.";
        }
        return null;
    }
}
