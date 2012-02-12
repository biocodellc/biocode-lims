package com.biomatters.plugins.biocode.labbench;

import com.biomatters.geneious.publicapi.components.Dialogs;
import com.biomatters.geneious.publicapi.documents.AnnotatedPluginDocument;
import com.biomatters.geneious.publicapi.documents.DocumentField;
import com.biomatters.geneious.publicapi.plugin.*;
import com.biomatters.plugins.biocode.BiocodeUtilities;
import com.biomatters.plugins.biocode.labbench.plates.Plate;
import com.biomatters.plugins.biocode.labbench.reaction.PCROptions;
import com.biomatters.plugins.biocode.labbench.reaction.Reaction;
import com.biomatters.plugins.biocode.labbench.reaction.ReactionUtilities;
import com.biomatters.plugins.biocode.labbench.reaction.ExtractionOptions;
import jebl.util.CompositeProgressListener;
import jebl.util.ProgressListener;

import java.sql.SQLException;
import java.util.*;

/**
 * @author Steve
 * @version $Id: 25/08/2010 2:38:08 AM steve $
 */
public class OneOffHackOperation extends DocumentOperation{

    @Override
    public GeneiousActionOptions getActionOptions() {
        return new GeneiousActionOptions("One off Hack").setInPopupMenu(true);
    }

    @Override
    public String getHelp() {
        return null;
    }

    @Override
    public DocumentSelectionSignature[] getSelectionSignatures() {
        return new DocumentSelectionSignature[] {
                new DocumentSelectionSignature(WorkflowDocument.class, 1, Integer.MAX_VALUE)
        };
    }

    @Override
    public boolean isDocumentGenerator() {
        return false;
    }

    @Override
    public Options getOptions(AnnotatedPluginDocument... documents) throws DocumentOperationException {
        Options option = new Options(this.getClass());
        option.addIntegerOption("min", "Minimum Plate number", 1, 1, Integer.MAX_VALUE);
        option.addIntegerOption("max", "Maximum Plate number", 200, 1, Integer.MAX_VALUE);
        return option;
    }

    @Override
    public String getOrderDependentOperationMessage() {
        return "put the workflow containing your archive plate first";
    }

    @Override
    public List<AnnotatedPluginDocument> performOperation(AnnotatedPluginDocument[] annotatedDocuments, ProgressListener progressListener, Options options) throws DocumentOperationException {
        if(!BiocodeService.getInstance().isLoggedIn()) {
            throw new DocumentOperationException(BiocodeUtilities.NOT_CONNECTED_ERROR_MESSAGE);
        }

        List<Reaction> reactions = new ArrayList<Reaction>();
        for(AnnotatedPluginDocument doc : annotatedDocuments) {
            WorkflowDocument workflowDoc = (WorkflowDocument)doc.getDocument();
            reactions.addAll(workflowDoc.getReactions());
        }

        int MIN_PLATE=(Integer)options.getValue("min");
        int MAX_PLATE = (Integer)options.getValue("max");

        CompositeProgressListener composite1 = new CompositeProgressListener(progressListener, MAX_PLATE-MIN_PLATE+1);

        for(int plateNum = MIN_PLATE; plateNum <= MAX_PLATE; plateNum++) {
            Plate extractionPlate = null;
            String plateName = getPlateName(plateNum);
            composite1.beginSubtask("Processing plate "+plateName);
            CompositeProgressListener composite2 = new CompositeProgressListener(composite1, reactions.size());
            for(Reaction r : reactions) {
                composite2.beginSubtask("Doing "+r.getType());
                //create the plate
                Plate plate = new Plate(Plate.Size.w96, r.getType());

                //set the reaction values
                for(Reaction plateReaction : plate.getReactions()) {
                    plateReaction.getOptions().valuesFromXML(r.getOptions().valuesToXML("options"));

                    //primer hacks
                    if(r.getType() == Reaction.Type.PCR) {
                         plateReaction.getOptions().getOption(PCROptions.PRIMER_OPTION_ID).setValue(r.getOptions().getValue(PCROptions.PRIMER_OPTION_ID));
                         plateReaction.getOptions().getOption(PCROptions.PRIMER_REVERSE_OPTION_ID).setValue(r.getOptions().getValue(PCROptions.PRIMER_REVERSE_OPTION_ID));
                    }
                    else if(r.getType() == Reaction.Type.CycleSequencing) {
                        plateReaction.getOptions().getOption(PCROptions.PRIMER_OPTION_ID).setValue(r.getOptions().getValue(PCROptions.PRIMER_OPTION_ID));
                    }
                }
                plate.setName(r.getPlateName().replace("FLMNH_001", plateName));

                //set the workflows and extraction/tissue ID's
                if(r.getType() == Reaction.Type.Extraction) {
                    Map<String,String> tissueIds;
                    try {
                        tissueIds = BiocodeService.getInstance().getActiveFIMSConnection().getTissueIdsFromFimsTissuePlate(plateName);
                    } catch (ConnectionException e) {
                        e.printStackTrace();
                        throw new DocumentOperationException(e.getMessage(), e);
                    }
                    populateWells96(tissueIds, ExtractionOptions.TISSUE_ID, plate);
                    generateExtractionIds(plate);

                    extractionPlate = plate;
                }
                else {
                    if(extractionPlate != null) {
                        NewPlateDocumentOperation.copyPlateOfSameSize(extractionPlate, plate, false);
                    }
                    plate.setThermocycle(r.getThermocycle());
                }

                try {
                    if(plate.getReactionType() == Reaction.Type.Extraction) {
                        extractionPlate = plate;
                        BiocodeService.getInstance().saveExtractions(null, plate);
                    }
                    else {
                        BiocodeService.getInstance().saveReactions(null, plate);
                    }
                } catch (SQLException e) {
                    e.printStackTrace();
                    throw new DocumentOperationException(e.getMessage(), e);
                } catch (BadDataException e) {
                    e.printStackTrace();
                     throw new DocumentOperationException(e.getMessage(), e);
                }

            }
        }

        return Collections.EMPTY_LIST;
    }

    private static String getPlateName(int plateNum) {
        String numString = ""+plateNum;
        while(numString.length() < 3) {
            numString = "0"+numString;
        }
        return "FLMNH_"+numString;
    }

    private static void populateWells96(Map<String, String> ids, String fieldName, Plate p) {
        for(Map.Entry<String, String> entry : ids.entrySet()) {
            BiocodeUtilities.Well well;
            try {
                well = new BiocodeUtilities.Well(entry.getKey());
            } catch (IllegalArgumentException e) {
                Dialogs.showMessageDialog(e.getMessage());
                return;
            }
            try {
                p.getReaction(well).getOptions().setValue(fieldName, entry.getValue());
            } catch (Exception e1) {
                e1.printStackTrace();
            }
        }
    }

    private void generateExtractionIds(Plate plate) {
        DocumentField tissueField = new DocumentField("Tissue Sample Id", "", ExtractionOptions.TISSUE_ID, String.class, false, false);

        DocumentField extractionField = new DocumentField("Extraction Id", "", "extractionId", String.class, false, false);

        DocumentField extractionBarcodeField = new DocumentField("Extraction Barcode", "", "extractionBarcode", String.class, false, false);

        List<String> tissueIds = getIdsToCheck(tissueField, plate);

        List<String> existingExtractionIds = getIdsToCheck(extractionField, plate);

        boolean fillAllWells = false;
        if(existingExtractionIds.size() > 1) {
            fillAllWells = true;
        }

        try {
            Set<String> extractionIds = BiocodeService.getInstance().getActiveLIMSConnection().getAllExtractionIdsStartingWith(tissueIds);
            extractionIds.addAll(existingExtractionIds);
            for(int row=0; row < plate.getRows(); row++) {
                for(int col=0; col < plate.getCols(); col++) {
                    Object existingValue = plate.getReaction(row, col).getFieldValue(extractionField.getCode());
                    Object value = plate.getReaction(row, col).getFieldValue(tissueField.getCode());
                    Object barcodeValue = plate.getReaction(row, col).getFieldValue(extractionBarcodeField.getCode());
                    if(existingValue != null && existingValue.toString().length() > 0) {
                        extractionIds.add(existingValue.toString());
                        if(!fillAllWells) {
                            continue;
                        }
                    }
                    if(value != null && value.toString().trim().length() > 0) {
                        String valueString = ReactionUtilities.getNewExtractionId(extractionIds, value);
                        plate.getReaction(row, col).getOptions().setValue(extractionField.getCode(), valueString);
                        extractionIds.add(valueString);
                    }
                    else if(barcodeValue != null && barcodeValue.toString().length() > 0) {
                        String emptyValue = "noTissue";
                        int i = 1;
                        while(extractionIds.contains(emptyValue+i)) {
                            i++;
                        }
                        String valueString = emptyValue + i;
                        plate.getReaction(row, col).getOptions().setValue(extractionField.getCode(), valueString);
                        extractionIds.add(valueString);
                    }
                }
            }

        } catch (SQLException e1) {
            //todo: handle
            //todo: multithread
        }
    }
    private static List<String> getIdsToCheck(DocumentField fieldToCheck, Plate p) {
        List<String> idsToCheck = new ArrayList<String>();
        for(int row=0; row < p.getRows(); row++) {
            for(int col=0; col < p.getCols(); col++) {
                Object value = p.getReaction(row, col).getOptions().getValue(fieldToCheck.getCode());
                if(value != null && value.toString().trim().length() > 0) {
                    idsToCheck.add(value.toString());
                }
            }
        }
        idsToCheck.add("noTissue");
        return idsToCheck;
    }
}
