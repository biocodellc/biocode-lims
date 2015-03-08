package com.biomatters.plugins.biocode.labbench;

import com.biomatters.geneious.publicapi.databaseservice.DatabaseServiceException;
import com.biomatters.geneious.publicapi.documents.AnnotatedPluginDocument;
import com.biomatters.geneious.publicapi.plugin.*;
import com.biomatters.geneious.publicapi.utilities.GuiUtilities;
import com.biomatters.geneious.publicapi.utilities.ThreadUtilities;
import com.biomatters.geneious.publicapi.components.ProgressFrame;
import com.biomatters.plugins.biocode.BiocodePlugin;
import com.biomatters.plugins.biocode.BiocodeUtilities;
import com.biomatters.plugins.biocode.labbench.plates.Plate;
import com.biomatters.plugins.biocode.labbench.plates.PlateViewer;
import com.biomatters.plugins.biocode.labbench.reaction.*;
import jebl.util.ProgressListener;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

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
                new DocumentSelectionSignature(Object.class, 0, 0),
                new DocumentSelectionSignature(PlateDocument.class, 1, 4),
                new DocumentSelectionSignature(TissueDocument.class, 1, Integer.MAX_VALUE)
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

        dontUse.setProgress(1.0);//make sure we never show the original progress dialog...

        @SuppressWarnings("deprecation") //using deprecated method so that api version doesn't have to be upped
        final ProgressFrame progressListener = new ProgressFrame("Verifying Reactions", "", 800, false);

        final NewPlateOptions options = (NewPlateOptions)optionsa;

        //analyse the documents
        Plate.Size pSize = null;
        final Plate.Size sizeFromOptions = options.getPlateSize();
        final Reaction.Type typeFromOptions = options.getReactionType();
        final boolean fromExistingPlates = options.isFromExistingPlates();
        final boolean fromExistingTissues = options.isFromExistingTissues();
        final boolean copyOnlyFailed = options.copyOnlyFailedReactions();
        final boolean copyOnlyPassed = options.copyOnlyPassedReactions();
        final AtomicReference<PlateViewer> plateViewer = new AtomicReference<PlateViewer>();
        final int numberOfReactionsFromOptions = options.getNumberOfReactions();
        int reactionCount = 0;

        if (fromExistingTissues && !options.getReactionType().equals(Reaction.Type.Extraction)) {
            throw new DocumentOperationException(options.getReactionType().name() + " plates cannot be created from tissue IDs.");
        }

        if (fromExistingPlates) {
            for (AnnotatedPluginDocument doc : documents) {
                PlateDocument plateDoc = (PlateDocument)doc.getDocument();
                Plate.Size size = plateDoc.getPlate().getPlateSize();
                reactionCount = plateDoc.getPlate().getReactions().length;
                if (pSize != null && size != pSize) {
                    throw new DocumentOperationException("All plates must be of the same size");
                }
                pSize = size;                      
            }
        }
        final Plate.Size plateSize = pSize;
        if(options.getPlateSize() == null && plateSize != null) {
            throw new DocumentOperationException("You cannot create individual reactions from a plate");
        }

        if(options.getPlateSize() == null) {
            if(numberOfReactionsFromOptions < reactionCount) {
                throw new DocumentOperationException("You must create at least the number of reactions as are in your existing document(s)");    
            }
        }

        final int reactionCount1 = reactionCount;
        Runnable runnable = new Runnable() {
            public void run() {
                if(sizeFromOptions != null) {
                    plateViewer.set(new PlateViewer(sizeFromOptions, typeFromOptions));
                }
                else {
                    if(options.getOption("reactionNumber").isEnabled() || options.getOption("stripNumber").isEnabled()) {
                        plateViewer.set(new PlateViewer(numberOfReactionsFromOptions, typeFromOptions));
                    }
                    else {
                        plateViewer.set(new PlateViewer(reactionCount1, typeFromOptions));
                    }

                }
            }
        };
        ThreadUtilities.invokeNowOrWait(runnable);
        
        if(fromExistingPlates) {
            PlateDocument plateDoc = (PlateDocument)documents[0].getDocument();
            Plate plate = plateDoc.getPlate();
            Plate editingPlate = plateViewer.get().getPlate();

            Boolean copy;
            if(copyOnlyFailed || copyOnlyPassed) {
                copy = copyOnlyPassed;
            }
            else {
                copy = null;
            }

            if(plateSize == sizeFromOptions) {
                copyPlateOfSameSize(plate, editingPlate, copy);
            }
            else if(sizeFromOptions == Plate.Size.w96){
                copy384To96(plate, editingPlate, (Integer)options.getValue("quadrant.value"), copy);

            }
            else if(sizeFromOptions == Plate.Size.w384) {
                Plate[] plates = new Plate[4];
                for (int i = 0; i < plates.length; i++) {
                    AnnotatedPluginDocument doc;
                    doc = options.getPlateForQuadrant(documents, i);
                    if(doc != null) {
                        PlateDocument pDoc = (PlateDocument) doc.getDocument();
                        plates[i] = pDoc.getPlate();
                    }
                }

                copy96To384(plates, editingPlate, copy);
            }
            else if(sizeFromOptions == null) {
                copyPlateToReactionList(plate, editingPlate);
            }

            progressListener.setProgress(1.0);
        } else if (fromExistingTissues) {
            initializePlate(plateViewer.get().getPlate(), getTissueDocuments(documents));
        }

        plateViewer.get().displayInFrame(true, GuiUtilities.getMainFrame());

        return null;
    }

    private static Collection<TissueDocument> getTissueDocuments(AnnotatedPluginDocument[] annotatedPluginDocumentsContainingTissueDocuments) throws DocumentOperationException {
        Collection<TissueDocument> tissueDocuments = new ArrayList<TissueDocument>();

        for (AnnotatedPluginDocument annotatedPluginDocument : annotatedPluginDocumentsContainingTissueDocuments) {
            if (!TissueDocument.class.isAssignableFrom(annotatedPluginDocument.getDocumentClass())) {
                throw new DocumentOperationException("Unexpected document type, expected: TissueDocument, actual: " + annotatedPluginDocument.getDocumentClass().getSimpleName() + ".");
            }

            tissueDocuments.add((TissueDocument)annotatedPluginDocument.getDocument());
        }

        return tissueDocuments;
    }

    private static void initializePlate(Plate plate, Collection<TissueDocument> tissueDocuments) throws DocumentOperationException {
        Iterator<String> tissueIDIterator = getTissueIDs(tissueDocuments).iterator();
        for (Reaction reaction : plate.getReactions()) {
            if (!(reaction instanceof ExtractionReaction)) {
                throw new DocumentOperationException("Cannot assign tissue ID to non extraction reaction.");
            }

            if (!tissueIDIterator.hasNext()) {
                break;
            }

            ((ExtractionReaction)reaction).setTissueId(tissueIDIterator.next());
        }
    }

    private static Collection<String> getTissueIDs(Collection<TissueDocument> tissueDocuments) {
        Collection<String> tissueIDs = new ArrayList<String>();

        for (TissueDocument tissueDocument : tissueDocuments) {
            String tissueID = tissueDocument.getId();
            if (tissueID == null) {
                tissueIDs.add("");
            } else {
                tissueIDs.add(tissueID);
            }
        }

        return tissueIDs;
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

    private void copy96To384(Plate[] srcPlates, Plate destPlate, Boolean passedOrFailed) throws DocumentOperationException{
        if(destPlate.getPlateSize() != Plate.Size.w384) {
            throw new IllegalArgumentException("The destination plate must be a 384 well plate");
        }

        Reaction.Type reactionType = null;
        for(int quadrant = 0; quadrant < srcPlates.length; quadrant++) {
            if(srcPlates[quadrant] == null) {
                continue;
            }
            reactionType = srcPlates[quadrant].getReactionType();
            Plate srcPlate = srcPlates[quadrant];
            int xoffset = quadrant % 2 == 0 ? 0 : 1;
            int yOffset = quadrant > 1 ? 1 : 0;
            for (int col = 0; col < srcPlate.getCols(); col++) {
                for (int row = 0; row < srcPlate.getRows(); row++) {
                    Reaction srcReaction = srcPlate.getReaction(row, col);
                    Reaction destReaction = destPlate.getReaction(row * 2 + yOffset, col * 2 + xoffset);
                    boolean copy = passedOrFailed == null || passedOrFailed == ReactionOptions.PASSED_VALUE.getName().equals(srcReaction.getFieldValue(ReactionOptions.RUN_STATUS));
                    if(copy) {
                        ReactionUtilities.copyReaction(srcReaction, destReaction);
                    }
                }
            }
        }
        if(reactionType == Reaction.Type.Extraction && destPlate.getReactionType() != Reaction.Type.Extraction) {
            autodetectWorkflows(destPlate);
        }
        if(reactionType == Reaction.Type.Extraction && destPlate.getReactionType() == Reaction.Type.Extraction) {
            generateExtractionIds(destPlate);
        }
    }

    private void copy384To96(Plate srcPlate, Plate destPlate, int quadrant, Boolean passedOrFailed) throws DocumentOperationException{
        quadrant = quadrant-1;//zero-index it
        int xoffset = quadrant % 2 == 0 ? 0 : 1;
        int yOffset = quadrant > 1 ? 1 : 0;
        for (int col = 0; col < destPlate.getCols(); col++) {
            for (int row = 0; row < destPlate.getRows(); row++) {
                Reaction destReaction = destPlate.getReaction(row, col);
                Reaction srcReaction = srcPlate.getReaction(row * 2 + yOffset, col * 2 + xoffset);
                boolean copy = passedOrFailed == null ? true : passedOrFailed == ReactionOptions.PASSED_VALUE.getName().equals(srcReaction.getFieldValue(ReactionOptions.RUN_STATUS));
                if (copy) {
                    ReactionUtilities.copyReaction(srcReaction, destReaction);
                }
            }
        }
        if(srcPlate.getReactionType() == Reaction.Type.Extraction && destPlate.getReactionType() != Reaction.Type.Extraction) {
            autodetectWorkflows(destPlate);
        }
        if(srcPlate.getReactionType() == Reaction.Type.Extraction && destPlate.getReactionType() == Reaction.Type.Extraction) {
            generateExtractionIds(destPlate);
        }

    }

    public static void copyPlateOfSameSize(Plate srcPlate, Plate destPlate, Boolean passedOrFailed) throws DocumentOperationException{
        if(srcPlate.getPlateSize() != destPlate.getPlateSize()) {
            throw new IllegalArgumentException("Plates were of different sizes");
        }

        if(srcPlate.getReactionType() == destPlate.getReactionType()) { //copy everything
            destPlate.setName(srcPlate.getName());
            destPlate.setThermocycle(srcPlate.getThermocycle());
        }
        Reaction[] srcReactions = srcPlate.getReactions();
        Reaction[] destReactions = destPlate.getReactions();
        int count = 0;
        for(int i=0; i < srcReactions.length; i++) {
            boolean copy = passedOrFailed == null ? true : passedOrFailed == ReactionOptions.PASSED_VALUE.getName().equals(srcReactions[i].getFieldValue(ReactionOptions.RUN_STATUS));
            if(copy) {
                count++;
                ReactionUtilities.copyReaction(srcReactions[i], destReactions[i]);
            }
            else {
                System.out.println("didn't copy!");
            }
        }
        System.out.println(count+" reactions copied");
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
            String tissueId = ""+r.getFieldValue(ExtractionOptions.TISSUE_ID);
            if(tissueId.length() > 0) {
                tissueIds.add(tissueId);
            }
        }

        try {
            Set<String> extractionIds = BiocodeService.getInstance().getActiveLIMSConnection().getAllExtractionIdsForTissueIds(tissueIds);

            for(Reaction r : plate.getReactions()) {
                String tissueId = ""+r.getFieldValue(ExtractionOptions.TISSUE_ID);
                if(tissueId.length() > 0) {
                    String extractionId = ReactionUtilities.getNewExtractionId(extractionIds, tissueId);
                    r.setExtractionId(extractionId);
                    extractionIds.add(extractionId);
                }
            }
        } catch (DatabaseServiceException e) {
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
        catch(DatabaseServiceException ex) {
            throw new DocumentOperationException(ex.getMessage(), ex);
        }
    }
}
