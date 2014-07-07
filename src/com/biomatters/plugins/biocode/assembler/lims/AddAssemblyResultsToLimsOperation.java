package com.biomatters.plugins.biocode.assembler.lims;

import com.biomatters.geneious.publicapi.components.Dialogs;
import com.biomatters.geneious.publicapi.databaseservice.DatabaseServiceException;
import com.biomatters.geneious.publicapi.databaseservice.Query;
import com.biomatters.geneious.publicapi.documents.*;
import com.biomatters.geneious.publicapi.documents.sequence.*;
import com.biomatters.geneious.publicapi.implementations.SequenceExtractionUtilities;
import com.biomatters.geneious.publicapi.plugin.*;
import com.biomatters.geneious.publicapi.utilities.FileUtilities;
import com.biomatters.geneious.publicapi.utilities.StringUtilities;
import com.biomatters.plugins.biocode.BiocodePlugin;
import com.biomatters.plugins.biocode.BiocodeUtilities;
import com.biomatters.plugins.biocode.assembler.BatchChromatogramExportOperation;
import com.biomatters.plugins.biocode.labbench.AssembledSequence;
import com.biomatters.plugins.biocode.labbench.BiocodeService;
import com.biomatters.plugins.biocode.labbench.PlateDocument;
import com.biomatters.plugins.biocode.labbench.Workflow;
import com.biomatters.plugins.biocode.labbench.lims.LIMSConnection;
import com.biomatters.plugins.biocode.labbench.plates.Plate;
import com.biomatters.plugins.biocode.labbench.reaction.*;
import jebl.util.CompositeProgressListener;
import jebl.util.ProgressListener;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * @author Richard
 * @version $Id$
 */
public class AddAssemblyResultsToLimsOperation extends DocumentOperation {

    private final boolean isPass;
    private final boolean isAutomated;

    public AddAssemblyResultsToLimsOperation(boolean isPass, boolean isAutomated) {
        this.isPass = isPass;
        this.isAutomated = isAutomated;
    }

    public GeneiousActionOptions getActionOptions() {
        GeneiousActionOptions geneiousActionOptions = new GeneiousActionOptions(isPass ? "Mark as Pass in LIMS..." : "Mark as Fail in LIMS...")
                .setInPopupMenu(true, isPass ? 0.65 : 0.66).setProOnly(true);
        return GeneiousActionOptions.createSubmenuActionOptions(BiocodePlugin.getSuperBiocodeAction(), geneiousActionOptions);
    }

    public String getHelp() {
        return "Select one or more sequences, contigs or alignments of contigs to mark them as " + (isPass ? "passed" : "failed") + " on the relevant workflows in " +
                "the LIMS (labratory information management system).";
    }

    @Override
    public String getUniqueId() {
        return isPass ? "MarkAssemblyAsPassInLims" : "MarkAssemblyAsFailInLims";
    }

    public DocumentSelectionSignature[] getSelectionSignatures() {

        return new DocumentSelectionSignature[] {
                new DocumentSelectionSignature(new DocumentSelectionSignature.DocumentSelectionSignatureAtom[] {
                        new DocumentSelectionSignature.DocumentSelectionSignatureAtom(SequenceAlignmentDocument.class, 1, Integer.MAX_VALUE),
                }),
                new DocumentSelectionSignature(new DocumentSelectionSignature.DocumentSelectionSignatureAtom[] {
                        new DocumentSelectionSignature.DocumentSelectionSignatureAtom(NucleotideSequenceDocument.class, 1, Integer.MAX_VALUE)
                })
        };
    }

    @Override
    public boolean isDocumentGenerator() {
        return false;
    }

    @Override
    public Options getOptions(AnnotatedPluginDocument... documents) throws DocumentOperationException {
        if (!BiocodeService.getInstance().isLoggedIn()) {
            throw new DocumentOperationException(BiocodeUtilities.NOT_CONNECTED_ERROR_MESSAGE);
        }
        try {
            return new AddAssemblyResultsToLimsOptions(documents, isPass);
        } catch (DatabaseServiceException e) {
            throw new DocumentOperationException(e.getMessage(), e);
        }
    }

    public Map<URN, AssemblyResult> getAssemblyResults(AnnotatedPluginDocument[] annotatedDocuments, ProgressListener progressListener, AddAssemblyResultsToLimsOptions options, SequenceSelection selection) throws DocumentOperationException, DatabaseServiceException {
        if(!BiocodeService.getInstance().isLoggedIn()) {
            throw new DocumentOperationException(BiocodeUtilities.NOT_CONNECTED_ERROR_MESSAGE);
        }
        Map<AnnotatedPluginDocument, SequenceDocument> docsToMark = MarkInLimsUtilities.getDocsToMark(annotatedDocuments, selection);

        Map<String, Plate> sequencingPlateCache = new HashMap<String, Plate>();
        LIMSConnection limsConnection = BiocodeService.getInstance().getActiveLIMSConnection();
        IssueTracker issueTracker = new IssueTracker(isAutomated);
        CompositeProgressListener progress = new CompositeProgressListener(progressListener, docsToMark.size());
        Map<URN, AssemblyResult> results = new HashMap<URN, AssemblyResult>();
//        Map<Integer, AssemblyResult> workflowsWithResults = new HashMap<Integer, AssemblyResult>();
        for (AnnotatedPluginDocument annotatedDocument : docsToMark.keySet()) {
            progress.beginSubtask();
            if (progress.isCanceled()) {
                break;
            }
            if(annotatedDocument == null) {
                continue;
            }

            markDocumentPassedOrFailed(isPass, annotatedDocument);

            AssemblyResult assemblyResult = new AssemblyResult();

            String errorString = getChromatogramProperties(options.getInputType(), sequencingPlateCache, limsConnection, annotatedDocument, assemblyResult);
            if (errorString != null) {
                issueTracker.setIssue(annotatedDocument, errorString);
                continue;
            }
            if(options.removePreviousSequences()) {
                try {
                    removeAllExistingSequencesInDatabase(assemblyResult.workflowId, assemblyResult.extractionId);
                } catch (DatabaseServiceException e) {
                    throw new DocumentOperationException("Could not remove existing sequences: "+e.getMessage(), e);
                }
            }

            Double coverage = (Double) annotatedDocument.getFieldValue(DocumentField.CONTIG_MEAN_COVERAGE);
            Integer disagreements = (Integer) annotatedDocument.getFieldValue(DocumentField.DISAGREEMENTS);
            Integer ambiguities = (Integer) annotatedDocument.getFieldValue(DocumentField.AMBIGUITIES);
            String bin = (String) annotatedDocument.getFieldValue(DocumentField.BIN);   //todo: seems to be getting the wrong value from here?

            int edits = getEdits(annotatedDocument);
            String[] trims = getTrimParameters(annotatedDocument);

            SequenceDocument consensus = docsToMark.get(annotatedDocument);
            if (consensus == null && SequenceAlignmentDocument.class.isAssignableFrom(annotatedDocument.getDocumentClass())) {
                consensus = (SequenceDocument) BiocodeUtilities.getConsensusSequence(annotatedDocument, options.getConsensusOptions()).getDocument();
            }
            if (isPass && consensus == null) {
                assert false: "there should be a consensus here!";
            }
            int[] qualities = null;
            if(NucleotideGraphSequenceDocument.class.isAssignableFrom(annotatedDocument.getDocumentClass())) {
                qualities = getQualities((SequenceDocument)annotatedDocument.getDocument());
            }
            else if(consensus != null) {
                qualities = getQualities(consensus);
            }

//            ssh: allowing only one consensus per workflow kinda makes sense when you're marking assemblies, but which of the two do we mark? and sometimes you're marking traces which have at least two entries per workflow, so this needs to go
//            if (workflowsWithResults.containsKey(assemblyResult.workflowId)) {
//                AssemblyResult existingAssemblyResult = workflowsWithResults.get(assemblyResult.workflowId);
//                for (Map.Entry<CycleSequencingReaction, List<AnnotatedPluginDocument>> chromatogramEntry : assemblyResult.getReactions().entrySet()) {
//                    existingAssemblyResult.addReaction(chromatogramEntry.getKey(), chromatogramEntry.getValue());
//                }
//                continue;
//            }
            assemblyResult.setContigProperties(annotatedDocument, consensus, qualities, coverage, disagreements, trims, edits, ambiguities, bin);
//            workflowsWithResults.put(assemblyResult.workflowId, assemblyResult);
            results.put(annotatedDocument.getURN(), assemblyResult);
        }
        if (!issueTracker.promptToContinue(!results.isEmpty())) {
            return null;
        }
        return results;
    }

    /**
     * Saves the reaction status to a field on the document. This should handle consensus alignments, contigs, and
     * individual sequences (marking only the traces)
     *
     * @param isPass pass or fail
     * @param document the document to mark
     * @throws DocumentOperationException
     */
    private void markDocumentPassedOrFailed(boolean isPass, AnnotatedPluginDocument document) throws DocumentOperationException{
        if(SequenceAlignmentDocument.class.isAssignableFrom(document.getDocumentClass())) {
            SequenceAlignmentDocument alignment = (SequenceAlignmentDocument)document.getDocument();
            for(int i=0; i < alignment.getNumberOfSequences(); i++) {
                if(alignment.getContigReferenceSequenceIndex() == i) {
                    continue;
                }
                AnnotatedPluginDocument reference = alignment.getReferencedDocument(i);
                if(reference != null) {
                    markDocumentPassedOrFailed(isPass, reference);
                }
            }
            if (alignment.isContig()) {
                updateReactionStatusField(isPass, document);
            }
        }
        else {
            updateReactionStatusField(isPass, document);
        }
    }

    private void updateReactionStatusField(boolean isPass, AnnotatedPluginDocument document) {
        document.setFieldValue(BiocodeUtilities.REACTION_STATUS_FIELD, isPass ? "passed" : "failed");
        document.save();
    }

    private void removeAllExistingSequencesInDatabase(Integer workflowId, String extractionId) throws DatabaseServiceException, DocumentOperationException {
        if(workflowId == null || extractionId == null) {
            return;
        }
        try {
            if(!BiocodeService.getInstance().deleteAllowed("assembly")) {
                throw new DocumentOperationException("It appears that you do not have permission to delete sequence records.  Please contact your System Administrator for assistance");
            }
        } catch (DatabaseServiceException e) {
            throw new DocumentOperationException(e.getMessage(), e);
        }

        LIMSConnection limsConnection = BiocodeService.getInstance().getActiveLIMSConnection();
        if(limsConnection == null) {
            throw new DocumentOperationException("You have been disconnected from the LIMS database.  Please reconnect and try again.");
        }
        limsConnection.deleteSequencesForWorkflowId(workflowId, extractionId);
    }


    private String getChromatogramProperties(InputType inputType, Map<String, Plate> sequencingPlateCache, LIMSConnection limsConnection,
                                             AnnotatedPluginDocument annotatedDocument, AssemblyResult assemblyResult) throws DocumentOperationException {
        List<AnnotatedPluginDocument> chromatograms = new ArrayList<AnnotatedPluginDocument>();
        if (SequenceAlignmentDocument.class.isAssignableFrom(annotatedDocument.getDocumentClass())) {
            SequenceAlignmentDocument alignment = (SequenceAlignmentDocument)annotatedDocument.getDocument();
            for (int i = 0; i < alignment.getNumberOfSequences(); i ++) {
                if (i == alignment.getContigReferenceSequenceIndex()) continue;
                AnnotatedPluginDocument referencedDocument = alignment.getReferencedDocument(i);
                if (referencedDocument == null) {
                    throw new DocumentOperationException("Contig \"" + annotatedDocument.getName() + "\" is missing a referened document");
                }
                if (!NucleotideSequenceDocument.class.isAssignableFrom(referencedDocument.getDocumentClass())) {
                    throw new DocumentOperationException("Contig \"" + annotatedDocument.getName() + "\" contains a sequence which is not DNA");
                }
                chromatograms.add(referencedDocument);
            }
        } else if(inputType == InputType.TRACES) {
            chromatograms.add(annotatedDocument);
        }

        boolean haveSourceChromatograms = !chromatograms.isEmpty();
        List<AnnotatedPluginDocument> toGetReactionsFrom = haveSourceChromatograms ? chromatograms : Collections.singletonList(annotatedDocument);

        for (AnnotatedPluginDocument doc : toGetReactionsFrom) {
            String plateName = (String)doc.getFieldValue(BiocodeUtilities.SEQUENCING_PLATE_FIELD);
            if (plateName == null) {
                return "FIMS data not annotated on referenced sequence (plate name)";
            }

            Plate plate;
            if (sequencingPlateCache.containsKey(plateName)) {
                plate = sequencingPlateCache.get(plateName);
            } else {
                Query q = Query.Factory.createFieldQuery(LIMSConnection.PLATE_NAME_FIELD, Condition.EQUAL, new Object[]{plateName},
                        BiocodeService.getSearchDownloadOptions(false, false, true, false));//Query.Factory.createQuery(plateName);
                List<PlateDocument> plateDocuments;
                try {
                    plateDocuments = limsConnection.getMatchingDocumentsFromLims(q, null, null).getPlates();
                } catch (DatabaseServiceException e) {
                    e.printStackTrace();
                    throw new DocumentOperationException("Failed to connect to FIMS: " + e.getMessage(), e);
                }
                if (plateDocuments.isEmpty()) {
                    sequencingPlateCache.put(plateName, null);
                    return "No plate found with name \"" + plateName + "\"";
                }
                if (plateDocuments.size() != 1) {
                    sequencingPlateCache.put(plateName, null);
                    return "Multiple plates found matching name \"" + plateName + "\"";
                }
                PlateDocument plateDocument = plateDocuments.get(0);
                if (plateDocument.getPlate().getReactionType() != Reaction.Type.CycleSequencing) {
                    sequencingPlateCache.put(plateName, null);
                    return "Plate \"" + plateName + "\" is not a sequencing plate";
                }
                plate = plateDocument.getPlate();
                sequencingPlateCache.put(plateName, plate);
            }
            if (plate == null) {
                return "Cannot find sequencing plate \"" + plateName + "\"";
            }

            String wellName = (String) doc.getFieldValue(BiocodeUtilities.SEQUENCING_WELL_FIELD);
            if (wellName == null) {
                return "FIMS data not annotated on referenced sequence (well name)";
            }
            BiocodeUtilities.Well well = new BiocodeUtilities.Well(wellName);

            Reaction reaction = plate.getReaction(well.row(), well.col());
            if (reaction == null) {
                return "No reaction found in well " + well.toPaddedString();
            }
            if (!(reaction instanceof CycleSequencingReaction)) {
                return "Reaction is not cycle sequencing";
            }

            Workflow workflow = reaction.getWorkflow();
            if (workflow == null) {
                return "No workflow record found in LIMS";
            }
            if (assemblyResult.workflowId != null && workflow.getId() != assemblyResult.workflowId) {
                return "Reads have different workflow IDs";
            }
            if (workflow.getId() == -1) {
                return "No workflow ID found in LIMS";
            }
            assemblyResult.workflowId = workflow.getId();

            if (reaction.getExtractionId() == null) {
                return "Extraction ID missing for workflow";
            }
            if (assemblyResult.extractionId != null && !reaction.getExtractionId().equals(assemblyResult.extractionId)) {
                return "Reads have different workflow IDs";
            }
            assemblyResult.extractionId = reaction.getExtractionId();

            assemblyResult.addReaction((CycleSequencingReaction) reaction,
                    haveSourceChromatograms ? Collections.singletonList(doc) : Collections.<AnnotatedPluginDocument>emptyList());
        }
        return null;
    }

    /**
     * Assembly results for a single workflow. May involve several sequencing reactions though (normally a forward and reverse)
     */
    public static final class AssemblyResult {
        public String extractionId;
        public Integer workflowId;
        public String consensus;
        public Double coverage;
        public Integer disagreements;
        public String[] trims;
        public int[] qualities;
        public Integer edits;
        public Integer ambiguities;
        public String bin;
        public String assemblyOptionValues;
        public String editRecord;

        private Map<Integer, List<AnnotatedPluginDocument>> chromatograms = new HashMap<Integer, List<AnnotatedPluginDocument>>();
        private Map<Integer, CycleSequencingReaction> reactionsById = new HashMap<Integer, CycleSequencingReaction>();

        public void setContigProperties(AnnotatedPluginDocument assembly, SequenceDocument consensus, int[] qualities, Double coverage, Integer disagreements, String[] trims, Integer edits, Integer ambiguities, String bin) throws DocumentOperationException {
            this.consensus = consensus.getSequenceString();
            this.coverage = coverage;
            this.disagreements = disagreements;
            this.trims = trims;
            this.edits = edits;
            this.ambiguities = ambiguities;
            this.bin = bin;
            this.qualities = qualities;
            this.assemblyOptionValues = getAssemblyOptionsValues(assembly);
            this.editRecord = MarkInLimsUtilities.getEditRecords(assembly, consensus);

        }

        public void addReaction(CycleSequencingReaction cycleSequencingReaction, List<AnnotatedPluginDocument> chromatograms) {
            List<AnnotatedPluginDocument> currentChromatograms = this.chromatograms.get(cycleSequencingReaction.getId());
            if (currentChromatograms == null) {
                currentChromatograms = new ArrayList<AnnotatedPluginDocument>();
                this.chromatograms.put(cycleSequencingReaction.getId(), currentChromatograms);
                this.reactionsById.put(cycleSequencingReaction.getId(), cycleSequencingReaction);
            }
            currentChromatograms.addAll(chromatograms);
        }

        public Map<CycleSequencingReaction, List<AnnotatedPluginDocument>> getReactions() {
            Map<CycleSequencingReaction, List<AnnotatedPluginDocument>> returnChromatograms = new HashMap<CycleSequencingReaction, List<AnnotatedPluginDocument>>();
            for (Map.Entry<Integer, List<AnnotatedPluginDocument>> entry : chromatograms.entrySet()) {
                returnChromatograms.put(reactionsById.get(entry.getKey()), entry.getValue());
            }
            return returnChromatograms;
        }
    }

    @Override
    public List<AnnotatedPluginDocument> performOperation(AnnotatedPluginDocument[] annotatedDocuments, ProgressListener progressListener, Options o, SequenceSelection selection) throws DocumentOperationException {
        if(!BiocodeService.getInstance().isLoggedIn()) {
            throw new DocumentOperationException(BiocodeUtilities.NOT_CONNECTED_ERROR_MESSAGE);
        }
        AddAssemblyResultsToLimsOptions options = (AddAssemblyResultsToLimsOptions) o;
        CompositeProgressListener progress = new CompositeProgressListener(progressListener, 0.4, 0.2);
        progress.beginSubtask("Checking results");
        LIMSConnection limsConnection;
        Map<URN, AssemblyResult> assemblyResults;
        try {
            assemblyResults = getAssemblyResults(annotatedDocuments, progress, options, selection);

            if(assemblyResults == null) {
                return null;
            }

            progress.beginSubtask("Saving to LIMS");

            limsConnection = BiocodeService.getInstance().getActiveLIMSConnection();
        } catch (DatabaseServiceException e) {
            throw new DocumentOperationException(e.getMessage(), e);
        }

        progress = new CompositeProgressListener(progress, assemblyResults.size());
//        if (progress.getRootProgressListener() instanceof ProgressFrame) {
//            ((ProgressFrame)progress.getRootProgressListener()).setCancelButtonLabel("Stop");
//        }

        Map<URN, Integer> seqIds = new HashMap<URN, Integer>();
        try {
            for (Map.Entry<URN, AssemblyResult> entry : assemblyResults.entrySet()) {
                progress.beginSubtask();
                AssemblyResult assemblyResult = entry.getValue();
                List<Integer> reactionIds = new ArrayList<Integer>();
                for (CycleSequencingReaction reaction : assemblyResult.getReactions().keySet()) {
                    reactionIds.add(reaction.getId());
                }
                AssembledSequence seq = new AssembledSequence();
                seq.extractionId = assemblyResult.extractionId;
                seq.workflowId = assemblyResult.workflowId;
                seq.consensus = assemblyResult.consensus;
                seq.coverage = assemblyResult.coverage;
                seq.numberOfDisagreements = assemblyResult.disagreements;
                seq.forwardTrimParameters = assemblyResult.trims[0];
                seq.reverseTrimParameters = assemblyResult.trims[1];
                seq.numOfEdits = assemblyResult.edits;
                seq.assemblyParameters = assemblyResult.assemblyOptionValues;
                if(assemblyResult.qualities != null) {
                    List<Integer> qualities = new ArrayList<Integer>();
                    for (int qualityValue : assemblyResult.qualities) {
                        qualities.add(qualityValue);
                    }
                    seq.confidenceScore = StringUtilities.join(",", qualities);
                }
                seq.bin = assemblyResult.bin;
                seq.numberOfAmbiguities = assemblyResult.ambiguities;
                seq.editRecord = assemblyResult.editRecord;

                int seqId = limsConnection.addAssembly(isPass, options.getNotes(), options.getTechnician(),
                        options.getFailureReason(), options.getFailureNotes(), options.isAddChromatograms(), seq, reactionIds, progress);
                if(progress.isCanceled()) {
                    return null;
                }
                seqIds.put(entry.getKey(), seqId);

                for (List<AnnotatedPluginDocument> docs : assemblyResult.getReactions().values()) {
                    for (AnnotatedPluginDocument doc : docs) {
                        doc.setFieldValue(LIMSConnection.SEQUENCE_ID, seqId);
                        doc.save();
                    }
                }

                attachChromats(limsConnection, isPass, options.isAddChromatograms(), assemblyResult);
            }

        } catch (DatabaseServiceException e) {
            throw new DocumentOperationException("Failed to mark as pass/fail in LIMS: " + e.getMessage(), e);
        }
        for (AnnotatedPluginDocument annotatedDocument : annotatedDocuments) {
            int savedSeqId = 0;
            try {
                savedSeqId = seqIds.get(annotatedDocument.getURN());
            } catch (NullPointerException e) {
                throw new DocumentOperationException(e.getMessage(), e);
            }
            annotatedDocument.setFieldValue(LIMSConnection.SEQUENCE_ID, savedSeqId);
            annotatedDocument.save();
        }
        for (AssemblyResult result : assemblyResults.values()) {
            for(CycleSequencingReaction reaction : result.reactionsById.values()) {
                reaction.purgeChromats();
            }
        }
        return null;
    }

    private static void attachChromats(LIMSConnection limsConnection, boolean isPass, boolean addChromatograms, AddAssemblyResultsToLimsOperation.AssemblyResult result) throws DocumentOperationException, DatabaseServiceException {
        BatchChromatogramExportOperation chromatogramExportOperation = new BatchChromatogramExportOperation();
        Options chromatogramExportOptions = null;
        File tempFolder;
        try {
            tempFolder = FileUtilities.createTempFile("chromat", ".ab1", true).getParentFile();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        for (Map.Entry<CycleSequencingReaction, List<AnnotatedPluginDocument>> entry : result.getReactions().entrySet()) {
            if (addChromatograms) {
                if (chromatogramExportOptions == null) {
                    chromatogramExportOptions = chromatogramExportOperation.getOptions(entry.getValue());
                    chromatogramExportOptions.setValue("exportTo", tempFolder.toString());
                }
                List<Trace> traces = new ArrayList<Trace>();
                for (AnnotatedPluginDocument chromatogramDocument : entry.getValue()) {
                    chromatogramExportOperation.performOperation(new AnnotatedPluginDocument[] {chromatogramDocument}, ProgressListener.EMPTY, chromatogramExportOptions);
                    File exportedFile = new File(tempFolder, chromatogramExportOperation.getFileNameUsedFor(chromatogramDocument));
                    try {
                        traces.add(new Trace(Arrays.asList((NucleotideSequenceDocument) chromatogramDocument.getDocument()), ReactionUtilities.loadFileIntoMemory(exportedFile)));
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
                entry.getKey().addSequences(traces);
            }
            entry.getKey().getOptions().setValue(ReactionOptions.RUN_STATUS, isPass ? ReactionOptions.PASSED_VALUE : ReactionOptions.FAILED_VALUE);
        }

        Set<CycleSequencingReaction> reactionSet = result.getReactions().keySet();
        limsConnection.saveReactions(reactionSet.toArray(new Reaction[reactionSet.size()]), Reaction.Type.CycleSequencing, null);
    }

    private static String getAssemblyOptionsValues(AnnotatedPluginDocument document) throws DocumentOperationException{
        if(document == null) {
            return null;
        }
        DocumentHistory history;
        try {
            history = document.getDocumentHistory();
        } catch (IOException e) {
            e.printStackTrace();
            throw new DocumentOperationException("Could not read the history for "+document.getName()+": "+e.getMessage());
        }
        List<DocumentHistoryEntry> historyEntries = history.getHistoryEntries();
        for (int i = historyEntries.size()-1; i >= 0; i--) {//get the most recent assembly (the one closest to the end of the list)
            DocumentHistoryEntry entry = historyEntries.get(i);
            List<DocumentHistoryEntryField> entryFields = entry.getFields();
            DocumentHistoryEntryField optionsField = null;
            DocumentHistoryEntryField uniqueIdField = null;
            for(DocumentHistoryEntryField field : entryFields) {
                if(field.getFieldCode().equals("com.biomatters.operationUniqueId")) {
                    uniqueIdField = field;
                }
                else if(field.getFieldCode().equals("com.biomatters.optionsValues")) {
                    optionsField = field;
                }
            }
            if(uniqueIdField != null && optionsField != null && uniqueIdField.getFieldValue().equals("com.biomatters.plugins.alignment.AssemblyOperation")) {
                return optionsField.getFieldValue();
            }
        }
        return null;
    }


    private int[] getQualities(SequenceDocument sequenceDocument) {
        if(!(sequenceDocument instanceof NucleotideGraphSequenceDocument)) {
            return null;
        }
        NucleotideGraphSequenceDocument graphSequence = (NucleotideGraphSequenceDocument)sequenceDocument;
        if(graphSequence.hasSequenceQualities()) {
            int[] result = new int[graphSequence.getSequenceLength()];
            for(int i=0; i < graphSequence.getSequenceLength(); i++) {
                result[i] = graphSequence.getSequenceQuality(i);
            }
            return result;
        }
        return null;
    }

    private int getEdits(AnnotatedPluginDocument annotatedDocument) throws DocumentOperationException {
        if (SequenceDocument.class.isAssignableFrom(annotatedDocument.getDocumentClass())) {
            return getEdits((SequenceDocument)annotatedDocument.getDocument());
        } else {
            int edits = 0;
            SequenceAlignmentDocument alignment = (SequenceAlignmentDocument)annotatedDocument.getDocument();
            for (int i = 0; i < alignment.getNumberOfSequences(); i ++) {
                if (i == alignment.getContigReferenceSequenceIndex()) continue;
                edits += getEdits(alignment.getSequence(i));
            }
            return edits;
        }
    }

    private int getEdits(SequenceDocument sequenceDocument) {
        int edits = 0;
        for (SequenceAnnotation sequenceAnnotation : sequenceDocument.getSequenceAnnotations()) {
            if (sequenceAnnotation.getType().equals(SequenceAnnotation.TYPE_EDITING_HISTORY_DELETION) ||
                sequenceAnnotation.getType().equals(SequenceAnnotation.TYPE_EDITING_HISTORY_INSERTION) ||
                sequenceAnnotation.getType().equals(SequenceAnnotation.TYPE_EDITING_HISTORY_REPLACEMENT)) {
                edits ++;
            }
        }
        return edits;
    }

    @Override
    public boolean loadDocumentsBeforeShowingOptions() {
        return true;
    }

    /**
     *
     * @param annotatedDocument
     * @return String[2] where String[0] = forward trim params and String[1] = reverse trim params. May contain null but never null.
     * @throws com.biomatters.geneious.publicapi.plugin.DocumentOperationException
     */
    private String[] getTrimParameters(AnnotatedPluginDocument annotatedDocument) throws DocumentOperationException {
        String[] trims = {null, null};
        if (!SequenceAlignmentDocument.class.isAssignableFrom(annotatedDocument.getDocumentClass())) {
            return trims;
        }
        SequenceAlignmentDocument alignment = (SequenceAlignmentDocument)annotatedDocument.getDocument();
        if (alignment.getNumberOfSequences() > 3) return trims;
        for (int i = 0; i < alignment.getNumberOfSequences(); i++) {
            if (i == alignment.getContigReferenceSequenceIndex()) continue;
            AnnotatedPluginDocument reference = alignment.getReferencedDocument(i);
            if (reference == null) continue;
            String trimParams = (String) reference.getFieldValue("trimParams.trimParams");
            int index = alignment.getSequence(i).getName().endsWith(SequenceExtractionUtilities.REVERSED_NAME_SUFFIX) ? 1 : 0;
            trims[index] = trimParams;
        }
        return trims;
    }

    private static final class IssueTracker {

        Map<AnnotatedPluginDocument, String> issues = new HashMap<AnnotatedPluginDocument, String>();
        List<String> extraMessages = new ArrayList<String>();
        private final boolean isAutomated;

        private IssueTracker(boolean automated) {
            isAutomated = automated;
        }

        public void setIssue(AnnotatedPluginDocument doc, String issue) {
            issues.put(doc, issue);
        }

        public void addExtraMessage(String message) {
            extraMessages.add(message);
        }

        public boolean promptToContinue(boolean canAddAny) throws DocumentOperationException {
            if (issues.isEmpty()) return true;

            StringBuilder message = new StringBuilder();
            if (canAddAny) {
                message.append("<html><b>Some results can't be added to the LIMS, do you wish to continue and add the remaining results to the LIMS?</b><br><br>");
            } else {
                message.append("<html><b>The selected results cannot be added to the LIMS for the following reasons.</b><br><br>");
            }
            message.append("Make sure you have run the \"Annotate with FIMS Data\" operations on the documents and that " +
                    "workflows exist in the LIMS.<br><br>");
            for (String extraMessage : extraMessages) {
                message.append("<b>Note</b>: ").append(extraMessage).append("<br><br>");
            }
            for (Map.Entry<AnnotatedPluginDocument, String> entry : issues.entrySet()) {
                message.append("<b>").append(entry.getKey().getName()).append("</b>: ").append(entry.getValue()).append("<br>");
            }
            message.append("</html>");
            if (!canAddAny || isAutomated) {
                throw new DocumentOperationException(message.toString());
            } else {
                String continueButton = "Continue";
                Dialogs.DialogOptions dialogOptions = new Dialogs.DialogOptions(new String[] {continueButton, "Cancel"}, "Problems found with results", null, Dialogs.DialogIcon.WARNING);
                Object choice = Dialogs.showDialog(dialogOptions, message.toString());
                return continueButton.equals(choice);
            }
        }
    }
}
