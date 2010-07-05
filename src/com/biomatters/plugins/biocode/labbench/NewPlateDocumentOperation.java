package com.biomatters.plugins.biocode.labbench;

import com.biomatters.geneious.publicapi.documents.AnnotatedPluginDocument;
import com.biomatters.geneious.publicapi.plugin.*;
import com.biomatters.geneious.publicapi.utilities.GuiUtilities;
import com.biomatters.geneious.publicapi.utilities.ThreadUtilities;
import com.biomatters.geneious.publicapi.components.ProgressFrame;
import com.biomatters.plugins.biocode.BiocodePlugin;
import com.biomatters.plugins.biocode.BiocodeUtilities;
import com.biomatters.plugins.biocode.labbench.plates.Plate;
import com.biomatters.plugins.biocode.labbench.plates.PlateViewer;
import com.biomatters.plugins.biocode.labbench.reaction.Reaction;
import com.biomatters.plugins.biocode.labbench.reaction.ReactionUtilities;
import com.biomatters.plugins.biocode.labbench.reaction.ReactionOptions;
import jebl.util.ProgressListener;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.sql.SQLException;

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
                new DocumentSelectionSignature(PlateDocument.class,1,4)
        };
    }

    @Override
     public boolean isDocumentGenerator() {
        return false;
    }

    @Override
    public Options getOptions(AnnotatedPluginDocument... documents) throws DocumentOperationException {
        return new NewPlateOptions(documents);
    }

    @Override
    public List<AnnotatedPluginDocument> performOperation(final AnnotatedPluginDocument[] documents, ProgressListener dontUse, Options optionsa) throws DocumentOperationException {
        if(!BiocodeService.getInstance().isLoggedIn()) {
            throw new DocumentOperationException(BiocodeUtilities.NOT_CONNECTED_ERROR_MESSAGE);
        }

        final ProgressFrame progressListener = new ProgressFrame("Verifying Reactions", "", 0, false);

        final NewPlateOptions options = (NewPlateOptions)optionsa;

        //analyse the documents
        Plate.Size pSize = null;
        int reactionCount = 0;
        for(AnnotatedPluginDocument doc : documents) {
            PlateDocument plateDoc = (PlateDocument)doc.getDocument();
            Plate.Size size = plateDoc.getPlate().getPlateSize();
            reactionCount = plateDoc.getPlate().getReactions().length;
            if(pSize != null && size != pSize) {
                throw new DocumentOperationException("All plates must be of the same size");
            }
            pSize = size;
        }
        final Plate.Size plateSize = pSize;
        if(options.getPlateSize() == null && plateSize != null) {
            throw new DocumentOperationException("You cannot create individual reactions from a plate");
        }

        final Plate.Size sizeFromOptions = options.getPlateSize();
        final Reaction.Type typeFromOptions = options.getReactionType();
        final boolean fromExisting = options.isFromExisting();
        final boolean copyOnlyPassed = options.copyOnlyPassedReactions();
        final AtomicReference<DocumentOperationException> exception = new AtomicReference<DocumentOperationException>();
        final AtomicReference<PlateViewer> plateViewer = new AtomicReference<PlateViewer>();
        final int numberOfReactionsFromOptions = (Integer)options.getOption("reactionNumber").getValue();

        if(options.getPlateSize() == null) {
            if(numberOfReactionsFromOptions < reactionCount) {
                throw new DocumentOperationException("You must create at least the number of reactions as are in your existing document(s)");    
            }
        }

        final int reactionCount1 = reactionCount;
        Runnable runnable = new Runnable() {
            public void run() {
                try {
                    if(sizeFromOptions != null) {
                        plateViewer.set(new PlateViewer(sizeFromOptions, typeFromOptions));
                    }
                    else {
                        if(options.getOption("reactionNumber").isEnabled()) {
                            plateViewer.set(new PlateViewer(numberOfReactionsFromOptions, typeFromOptions));
                        }
                        else {
                            plateViewer.set(new PlateViewer(reactionCount1, typeFromOptions));
                        }

                    }

                    if(fromExisting) {
                        PlateDocument plateDoc = (PlateDocument)documents[0].getDocument();
                        Plate plate = plateDoc.getPlate();
                        Plate editingPlate = plateViewer.get().getPlate();


                        if(plateSize == sizeFromOptions) {
                            copyPlateOfSameSize(plateViewer.get(), plate, editingPlate, copyOnlyPassed);
                        }
                        else if(sizeFromOptions == Plate.Size.w96){
                            copy384To96(plate, editingPlate, (Integer)options.getValue("quadrant.value"), copyOnlyPassed);

                        }
                        else if(sizeFromOptions == Plate.Size.w384) {
                            Plate[] plates = new Plate[4];
                            for (int i = 0; i < plates.length; i++) {
                                AnnotatedPluginDocument doc = null;
                                doc = options.getPlateForQuadrant(documents, i);
                                if(doc != null) {
                                    PlateDocument pDoc = (PlateDocument) doc.getDocument();
                                    plates[i] = pDoc.getPlate();
                                }
                            }

                            copy96To384(plates, editingPlate);
                        }
                        else if(sizeFromOptions == null) {
                            copyPlateToReactionList(plate, editingPlate);
                        }

                        progressListener.setMessage("Checking with the database");
                        progressListener.setIndeterminateProgress();
                        Reaction[] plateReactions = editingPlate.getReactions();
                        plateReactions[0].areReactionsValid(Arrays.asList(plateReactions), null, true);
                        progressListener.setProgress(1.0);
                        if(progressListener.isCanceled()) {
                            return;
                        }
                    }


                } catch (DocumentOperationException e) {
                    exception.set(e);
                }
            }
        };
        ThreadUtilities.invokeNowOrWait(runnable);
        if(exception.get() != null) {
            throw exception.get();
        }
        plateViewer.get().displayInFrame(true, GuiUtilities.getMainFrame());

        return null;
    }

    private void copyPlateToReactionList(Plate srcPlate, Plate destPlate) throws DocumentOperationException{
        Reaction[] srcReactions = srcPlate.getReactions();
        Reaction[] destReactions = destPlate.getReactions();

        for(int i=0; i < Math.min(srcReactions.length, destReactions.length); i++) {
            ReactionUtilities.copyReaction(srcReactions[i], destReactions[i]);
        }
        if(srcPlate.getReactionType() == Reaction.Type.Extraction) {
            autodetectWorkflows(destPlate);
        }
    }

    private void copy96To384(Plate[] srcPlates, Plate destPlate) throws DocumentOperationException{
        if(destPlate.getPlateSize() != Plate.Size.w384) {
            throw new IllegalArgumentException("The destination plate must be a 384 well plate");
        }

        for(int quadrant = 0; quadrant < srcPlates.length; quadrant++) {
            if(srcPlates[quadrant] == null) {
                continue;
            }
            Plate srcPlate = srcPlates[quadrant];
            Reaction[] srcReactions = srcPlate.getReactions();
            for(int i=0; i < srcReactions.length; i++) {
                int xoffset = quadrant % 2 == 0 ? 0 : 1;
                int yOffset = quadrant > 1 ? 1 : 0;
                for(int col = 0; col < srcPlate.getCols(); col++) {
                    for(int row = 0; row < srcPlate.getRows(); row++) {
                        Reaction srcReaction = srcPlate.getReaction(row, col);
                        Reaction destReaction = destPlate.getReaction(row*2 + yOffset, col*2 + xoffset);
                        ReactionUtilities.copyReaction(srcReaction, destReaction);
                    }
                }
            }
        }
        if(srcPlates[0].getReactionType() == Reaction.Type.Extraction) {
            autodetectWorkflows(destPlate);
        }
    }

    private void copy384To96(Plate srcPlate, Plate destPlate, int quadrant, boolean onlyPassed) throws DocumentOperationException{
        quadrant = quadrant-1;//zero-index it
        Reaction[] srcReactions = srcPlate.getReactions();
        for(int i=0; i < srcReactions.length; i++) {
            int xoffset = quadrant % 2 == 0 ? 0 : 1;
            int yOffset = quadrant > 1 ? 1 : 0;
            for(int col = 0; col < destPlate.getCols(); col++) {
                for(int row = 0; row < destPlate.getRows(); row++) {
                    Reaction destReaction = destPlate.getReaction(row, col);
                    Reaction srcReaction = srcPlate.getReaction(row*2 + yOffset, col*2 + xoffset);
                    if(!onlyPassed || ReactionOptions.PASSED_VALUE.getName().equals(srcReaction.getFieldValue(ReactionOptions.RUN_STATUS))) {
                        ReactionUtilities.copyReaction(srcReaction, destReaction);
                    }
                }
            }
        }
        if(srcPlate.getReactionType() == Reaction.Type.Extraction) {
            autodetectWorkflows(destPlate);
        }

    }

    static void copyPlateOfSameSize(PlateViewer plateViewer, Plate srcPlate, Plate destPlate, boolean onlyPassed) throws DocumentOperationException{
        if(srcPlate.getPlateSize() != destPlate.getPlateSize()) {
            throw new IllegalArgumentException("Plates were of different sizes");
        }

        if(srcPlate.getReactionType() == destPlate.getReactionType()) { //copy everything
            destPlate.setName(srcPlate.getName());
            destPlate.setThermocycle(srcPlate.getThermocycle());
        }
        Reaction[] srcReactions = srcPlate.getReactions();
        Reaction[] destReactions = destPlate.getReactions();
        for(int i=0; i < srcReactions.length; i++) {
            if(!onlyPassed || ReactionOptions.PASSED_VALUE.getName().equals(srcReactions[i].getFieldValue(ReactionOptions.RUN_STATUS))) {
                ReactionUtilities.copyReaction(srcReactions[i], destReactions[i]);
            }
        }
        if(srcPlate.getReactionType() == Reaction.Type.Extraction && destPlate.getReactionType() != Reaction.Type.Extraction) {
            autodetectWorkflows(destPlate);
        }
        if(srcPlate.getReactionType() == Reaction.Type.Extraction && destPlate.getReactionType() == Reaction.Type.Extraction) {
            generateExtractionIds(destPlate);
        }
    }

    static void generateExtractionIds(Plate plate) throws DocumentOperationException{
        List<String> tissueIds = new ArrayList<String>();
        for(Reaction r : plate.getReactions()) {
            String tissueId = ""+r.getFieldValue("sampleId");
            if(tissueId.length() > 0) {
                tissueIds.add(tissueId);
            }
        }

        try {
            Set<String> extractionIds = BiocodeService.getInstance().getActiveLIMSConnection().getAllExtractionIdsStartingWith(tissueIds);

            for(Reaction r : plate.getReactions()) {
                String tissueId = ""+r.getFieldValue("sampleId");
                if(tissueId.length() > 0) {
                    String extractionId = ReactionUtilities.getNewExtractionId(extractionIds, tissueId);
                    r.setExtractionId(extractionId);
                    extractionIds.add(extractionId);
                }
            }
        } catch (SQLException e) {
            throw new DocumentOperationException("Error reading the database: "+e.getMessage(), e);
        }
    }

    static void autodetectWorkflows(Plate plate) throws DocumentOperationException {
        List<String> extractionIds = new ArrayList<String>();
        List<String> loci = new ArrayList<String>();
        for(Reaction r : plate.getReactions()) {
            extractionIds.add(r.getExtractionId());
            loci.add(r.getLocus());
        }
        try {
            Map<String, String> idToWorkflow = BiocodeService.getInstance().getWorkflowIds(extractionIds, loci, plate.getReactionType());
            Map<String,Workflow> workflowIdToWorkflow = BiocodeService.getInstance().getWorkflows(idToWorkflow.values());
            for(int row=0; row < plate.getRows(); row++) {
                for(int col=0; col < plate.getCols(); col++) {
                    Reaction reaction = plate.getReaction(row, col);
                    String value = idToWorkflow.get(reaction.getExtractionId());
                    if(value != null) {
                        reaction.setWorkflow(workflowIdToWorkflow.get(value));
                    }
                    else {
                        reaction.setWorkflow(null);
                    }
                }
            }
        }
        catch(SQLException ex) {
            throw new DocumentOperationException(ex.getMessage(), ex);
        }
    }
}
