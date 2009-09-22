package com.biomatters.plugins.biocode.labbench;

import com.biomatters.geneious.publicapi.documents.AnnotatedPluginDocument;
import com.biomatters.geneious.publicapi.documents.PluginDocument;
import com.biomatters.geneious.publicapi.plugin.*;
import com.biomatters.geneious.publicapi.utilities.GuiUtilities;
import com.biomatters.plugins.biocode.BiocodePlugin;
import com.biomatters.plugins.biocode.BiocodeUtilities;
import com.biomatters.plugins.biocode.labbench.plates.Plate;
import com.biomatters.plugins.biocode.labbench.plates.PlateViewer;
import com.biomatters.plugins.biocode.labbench.reaction.Reaction;
import com.biomatters.plugins.biocode.labbench.reaction.ExtractionReaction;
import jebl.util.ProgressListener;

import java.util.List;
import java.util.Arrays;

import org.virion.jam.util.SimpleListener;

/**
 * @author Steven Stones-Havas
 * @version $Id$
 *          <p/>
 *          Created on 4/06/2009 4:53:15 PM
 */
public class NewPlateDocumentOperation extends DocumentOperation {
    public GeneiousActionOptions getActionOptions() {
        return new GeneiousActionOptions("New Reaction", "Create a new reaction (or plate of reactions)", BiocodePlugin.getIcons("newReaction_24.png")).setInMainToolbar(true, 0.533).setProOnly(true);
    }

    public String getHelp() {
        return null;
    }

    public DocumentSelectionSignature[] getSelectionSignatures() {
        return new DocumentSelectionSignature[]{
                new DocumentSelectionSignature(new DocumentSelectionSignature.DocumentSelectionSignatureAtom[0]),
                new DocumentSelectionSignature(PlateDocument.class,1,1)
        };
    }

    @Override
     public boolean isDocumentGenerator() {
        return false;
    }

    @Override
    public Options getOptions(AnnotatedPluginDocument... documents) throws DocumentOperationException {
        if(!BiocodeService.getInstance().isLoggedIn()) {
            throw new DocumentOperationException(BiocodeUtilities.NOT_CONNECTED_ERROR_MESSAGE);
        }

        Options options = new Options(this.getClass());
        Options.OptionValue[] plateValues = new Options.OptionValue[] {
                new Options.OptionValue("individualReactions", "Individual Reactions"),
                new Options.OptionValue("48Plate", "48 well plate"),
                new Options.OptionValue("96Plate", "96 well plate"),
                new Options.OptionValue("384Plate", "384 well plate")
        };

        Options.OptionValue[] typeValues = new Options.OptionValue[] {
                new Options.OptionValue("extraction", "Extraction"),
                new Options.OptionValue("pcr", "PCR"),
                new Options.OptionValue("cyclesequencing", "Cycle Sequencing")
        };

        Options.BooleanOption fromExistingOption = null;
        if(documents.length > 0) {
            fromExistingOption = options.addBooleanOption("fromExisting", "Create plate from existing document", false);
        }

        options.addComboBoxOption("reactionType", "Type of reaction", typeValues, typeValues[0]);
        final Options.RadioOption<Options.OptionValue> plateOption = options.addRadioOption("plateType", "", plateValues, plateValues[2], Options.Alignment.VERTICAL_ALIGN);


        final Options.IntegerOption reactionNumber = options.addIntegerOption("reactionNumber", "Number of reactions", 1, 1, 26);
        plateOption.addDependent(plateValues[0], reactionNumber, true);
        

        if(fromExistingOption != null) {
            final Options.BooleanOption fromExistingOption1 = fromExistingOption;
            SimpleListener fromExistingListener = new SimpleListener() {
                public void objectChanged() {
                    plateOption.setEnabled(!fromExistingOption1.getValue());
                    reactionNumber.setEnabled(!fromExistingOption1.getValue());
                }
            };
            fromExistingOption.addChangeListener(fromExistingListener);
            fromExistingListener.objectChanged();
        }


        return options;
    }

    @Override
    public List<AnnotatedPluginDocument> performOperation(AnnotatedPluginDocument[] annotatedDocuments, ProgressListener progressListener, Options options) throws DocumentOperationException {
        if(!BiocodeService.getInstance().isLoggedIn()) {
            throw new DocumentOperationException(BiocodeUtilities.NOT_CONNECTED_ERROR_MESSAGE);
        }

        Plate.Size size = null;
        Options.OptionValue plateSize = (Options.OptionValue)options.getValue("plateType");
        Options.OptionValue reactionType = (Options.OptionValue)options.getValue("reactionType");

        Reaction.Type type = null;

        if("true".equals(options.getValueAsString("fromExisting"))) {
            PlateDocument plateDoc = (PlateDocument)annotatedDocuments[0].getDocument();
            Plate plate = plateDoc.getPlate();
            size = plate.getPlateSize();
        }
        else {
            if(plateSize.getName().equals("48Plate")) {
                size = Plate.Size.w48;
            }
            else if(plateSize.getName().equals("96Plate")) {
                size = Plate.Size.w96;
            }
            else if(plateSize.getName().equals("384Plate")) {
                size = Plate.Size.w384;
            }
        }
        if(reactionType.getName().equals("extraction")) {
            type = Reaction.Type.Extraction;
        }
        else if(reactionType.getName().equals("pcr")) {
            type = Reaction.Type.PCR;
        }
        else if(reactionType.getName().equals("cyclesequencing")) {
            type = Reaction.Type.CycleSequencing;
        }

        PlateViewer plateViewer;
        if(size != null) {
            plateViewer = new PlateViewer(size, type);
        }
        else {
            plateViewer = new PlateViewer((Integer)options.getValue("reactionNumber"), type);
        }

        if("true".equals(options.getValueAsString("fromExisting"))) {
            PlateDocument plateDoc = (PlateDocument)annotatedDocuments[0].getDocument();
            Plate plate = plateDoc.getPlate();
            Plate editingPlate = plateViewer.getPlate();
            assert plate.getPlateSize() == editingPlate.getPlateSize();
            if(plate.getReactionType() == editingPlate.getReactionType()) { //copy everything
                plate.setId(-1);
                for(Reaction reaction : plate.getReactions()) {
                    reaction.setId(-1);
                }
                plateViewer.setPlate(plate);
            }
            else {
                Reaction[] plateReactions = plate.getReactions();
                Reaction[] editingPlateReactions = editingPlate.getReactions();
                for(int i=0; i < plateReactions.length; i++) {
                    editingPlateReactions[i].setExtractionId(plateReactions[i].getExtractionId());
                    if(editingPlate.getReactionType() == Reaction.Type.Extraction) {
                        FimsSample fimsSample = plateReactions[i].getFimsSample();
                        if(fimsSample != null) {
                            ((ExtractionReaction)editingPlateReactions[i]).setTissueId(fimsSample.getId());
                        }
                    }
                }
            }

            Reaction[] plateReactions = plateViewer.getPlate().getReactions();
            progressListener.setMessage("Checking with the database");
            progressListener.setIndeterminateProgress();
            plateReactions[0].areReactionsValid(Arrays.asList(plateReactions));
            progressListener.setProgress(1.0);
            if(progressListener.isCanceled()) {
                return null;
            }
        }

        plateViewer.displayInFrame(true, GuiUtilities.getMainFrame());

        return null;
    }
}
