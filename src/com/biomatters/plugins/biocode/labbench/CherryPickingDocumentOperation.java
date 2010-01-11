package com.biomatters.plugins.biocode.labbench;

import com.biomatters.geneious.publicapi.plugin.*;
import com.biomatters.geneious.publicapi.documents.AnnotatedPluginDocument;
import com.biomatters.geneious.publicapi.utilities.GuiUtilities;
import com.biomatters.plugins.biocode.labbench.reaction.Reaction;
import com.biomatters.plugins.biocode.labbench.reaction.ReactionOptions;
import com.biomatters.plugins.biocode.labbench.plates.Plate;
import com.biomatters.plugins.biocode.labbench.plates.PlateViewer;

import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;

import jebl.util.ProgressListener;

/**
 * Created by IntelliJ IDEA.
 * User: Steve
 * Date: 22/12/2009
 * Time: 7:04:21 PM
 * To change this template use File | Settings | File Templates.
 */
public class CherryPickingDocumentOperation extends DocumentOperation {

    public GeneiousActionOptions getActionOptions() {
        return new GeneiousActionOptions("New Reactions from Failed Reactions").setInMainToolbar(true);
    }

    public String getHelp() {
        return null;
    }

    public DocumentSelectionSignature[] getSelectionSignatures() {
        return new DocumentSelectionSignature[]{new DocumentSelectionSignature(PlateDocument.class, 1, Integer.MAX_VALUE)};
    }

    @Override
    public Options getOptions(AnnotatedPluginDocument... documents) throws DocumentOperationException {
        Reaction.Type reactionType = null;
        for(AnnotatedPluginDocument doc : documents) {
            PlateDocument plateDoc = (PlateDocument)doc.getDocument();
            if(reactionType != null && plateDoc.getPlate().getReactionType() != reactionType) {
                throw new DocumentOperationException("You must select plates of the same type");
            }
            reactionType = plateDoc.getPlate().getReactionType();
        }
        if(reactionType == Reaction.Type.Extraction) {
            throw new DocumentOperationException("You must select either PCR or Cycle Sequencing plates");
        }

        Options options = new Options(this.getClass());

        List<Options.OptionValue> sizeValues = Arrays.asList(
                new Options.OptionValue(Plate.Size.w96.name(), Plate.Size.w96.name()),
                new Options.OptionValue(Plate.Size.w384.name(), Plate.Size.w384.name())
        );

        options.addRadioOption("plateSize", "Plate Size", sizeValues, sizeValues.get(0), Options.Alignment.VERTICAL_ALIGN);



        return options;
    }

    @Override
    public List<AnnotatedPluginDocument> performOperation(AnnotatedPluginDocument[] annotatedDocuments, ProgressListener progressListener, Options options) throws DocumentOperationException {
        List<PlateDocument> plateDocuments = new ArrayList<PlateDocument>();
        for(AnnotatedPluginDocument doc : annotatedDocuments) {
            plateDocuments.add((PlateDocument)doc.getDocument());
        }

        List<Reaction> failedReactions = getFailedReactions(plateDocuments);

        if(failedReactions.size() == 0) {
            throw new DocumentOperationException("The selected plates do not contain any failed reactions");
        }

        Plate.Size plateSize = Plate.Size.valueOf(options.getValueAsString("plateSize"));

        int numberOfPlatesRequired = (int)Math.ceil(((double)failedReactions.size())/plateSize.numberOfReactions());


        List<PlateViewer> plates = new ArrayList<PlateViewer>();
        for(int i=0; i < numberOfPlatesRequired; i++) {
            PlateViewer plate = new PlateViewer(plateSize, failedReactions.get(0).getType());
            for (int j = 0; j < plate.getPlate().getReactions().length; j++) {
                if(j+i*plateSize.numberOfReactions() > failedReactions.size()-1) {
                    break;
                }
                Reaction newReaction = plate.getPlate().getReactions()[j];
                Reaction oldReaction = failedReactions.get(i*plateSize.numberOfReactions()+j);
                newReaction.setExtractionId(oldReaction.getExtractionId());
            }
            plates.add(plate);
        }
        for(PlateViewer viewer : plates) {
            viewer.displayInFrame(true, GuiUtilities.getMainFrame());
        }
        return null;
    }

    List<Reaction> getFailedReactions(List<PlateDocument> plates) {
        List<Reaction> reactions = new ArrayList<Reaction>();
        for(PlateDocument doc : plates) {
            Plate plate = doc.getPlate();
            for(Reaction r : plate.getReactions()) {
                if(r.getOptions().getValue(ReactionOptions.RUN_STATUS) == ReactionOptions.FAILED_VALUE) {
                    reactions.add(r);
                }
            }
        }
        return reactions;
    }
}
