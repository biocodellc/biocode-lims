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
        IssueTracker issueTracker = new IssueTracker(isAutomated);
        CompositeProgressListener progress = new CompositeProgressListener(progressListener, docsToMark.size());
        Map<URN, AssemblyResult> results = new HashMap<URN, AssemblyResult>();
//        Map<Integer, AssemblyResult> workflowsWithResults = new HashMap<Integer, AssemblyResult>();
        for (AnnotatedPluginDocument annotatedDocument : docsToMark.keySet()) {
            progress.beginSubtask();
            if (progress.isCanceled()) {
                break;
            }

            markDocumentPassedOrFailed(isPass, annotatedDocument);

            AssemblyResult assemblyResult = new AssemblyResult();

            String errorString = getChromatogramProperties(options.getInputType(), sequencingPlateCache, annotatedDocument, assemblyResult);
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


    private static class PlateAndWell {
        private String plate;
        private String well;

        private PlateAndWell(AnnotatedPluginDocument document) {
            this(document, null);
        }

        /**
         * Get the plate and well associated with a trace sequence.  Uses the referenced document if there is one.
         * Otherwise uses the sequence document.
         *
         * @param document The referenced trace document
         * @param pluginDocument The trace sequence within an assembly
         */
        private PlateAndWell(AnnotatedPluginDocument document, PluginDocument pluginDocument) {
            if(document != null) {
                plate = getStringOrNull(document.getFieldValue(BiocodeUtilities.SEQUENCING_PLATE_FIELD.getCode()));
                well = getStringOrNull(document.getFieldValue(BiocodeUtilities.SEQUENCING_WELL_FIELD.getCode()));
            }
            // Fallback to the plugin document if necessary
            if(pluginDocument != null) {
                if (plate == null) {
                    plate = getStringOrNull(pluginDocument.getFieldValue(BiocodeUtilities.SEQUENCING_PLATE_FIELD.getCode()));
                }
                if (well == null) {
                    well = getStringOrNull(pluginDocument.getFieldValue(BiocodeUtilities.SEQUENCING_WELL_FIELD.getCode()));
                }
            }
        }

        private static String getStringOrNull(Object obj) {
            if(obj == null) {
                return null;
            } else {
                return String.valueOf(obj);
            }
        }
    }

    private String getChromatogramProperties(InputType inputType, Map<String, Plate> sequencingPlateCache,
                                             AnnotatedPluginDocument annotatedDocument, AssemblyResult assemblyResult) throws DocumentOperationException {

        Map<PlateAndWell, AnnotatedPluginDocument> reactionsToChromatograms = new HashMap<PlateAndWell, AnnotatedPluginDocument>();
        if (SequenceAlignmentDocument.class.isAssignableFrom(annotatedDocument.getDocumentClass())) {
            SequenceAlignmentDocument alignment = (SequenceAlignmentDocument)annotatedDocument.getDocument();
            for (int i = 0; i < alignment.getNumberOfSequences(); i ++) {
                if (i == alignment.getContigReferenceSequenceIndex()) continue;
                AnnotatedPluginDocument referencedDocument = alignment.getReferencedDocument(i);
                SequenceDocument sequence = alignment.getSequence(i);

                if (referencedDocument == null && sequence == null) {
                    throw new DocumentOperationException("Contig \"" + annotatedDocument.getName() + "\" is missing a referened document, please try to regenerate this Contig.");
                }
                if (referencedDocument != null && !NucleotideSequenceDocument.class.isAssignableFrom(referencedDocument.getDocumentClass())) {
                    throw new DocumentOperationException("Contig \"" + annotatedDocument.getName() + "\" contains a sequence which is not DNA");
                }
                reactionsToChromatograms.put(new PlateAndWell(referencedDocument, sequence), referencedDocument);
            }
        } else {
            reactionsToChromatograms.put(new PlateAndWell(annotatedDocument), inputType == InputType.TRACES ? annotatedDocument : null);
        }

        for (Map.Entry<PlateAndWell, AnnotatedPluginDocument> entry : reactionsToChromatograms.entrySet()) {
            PlateAndWell plateAndWell = entry.getKey();
            String plateName = plateAndWell.plate;
            if (plateName == null) {
                return "FIMS data not annotated on referenced sequence (plate name)";
            }

            Plate plate;
            if (sequencingPlateCache.containsKey(plateName)) {
                plate = sequencingPlateCache.get(plateName);
            } else {
                Query q = Query.Factory.createFieldQuery(LIMSConnection.PLATE_NAME_FIELD, Condition.EQUAL, new Object[]{plateName},
                        BiocodeService.getSearchDownloadOptions(false, false, true, false));//Query.Factory.createQuery(plateName);
                List<Plate> plates;
                try {
                    LIMSConnection limsConnection = BiocodeService.getInstance().getActiveLIMSConnection();
                    List<Integer> plateIds = limsConnection.getMatchingDocumentsFromLims(q, null, ProgressListener.EMPTY).getPlateIds();
                    plates = limsConnection.getPlates(plateIds, ProgressListener.EMPTY);
                } catch (DatabaseServiceException e) {
                    e.printStackTrace();
                    throw new DocumentOperationException("Failed to connect to FIMS: " + e.getMessage(), e);
                }
                if (plates.isEmpty()) {
                    sequencingPlateCache.put(plateName, null);
                    return "No plate found with name \"" + plateName + "\"";
                }
                if (plates.size() != 1) {
                    sequencingPlateCache.put(plateName, null);
                    return "Multiple plates found matching name \"" + plateName + "\"";
                }
                plate = plates.get(0);
                if (plate.getReactionType() != Reaction.Type.CycleSequencing) {
                    sequencingPlateCache.put(plateName, null);
                    return "Plate \"" + plateName + "\" is not a sequencing plate";
                }
                sequencingPlateCache.put(plateName, plate);
            }
            if (plate == null) {
                return "Cannot find sequencing plate \"" + plateName + "\"";
            }

            String wellName = plateAndWell.well;
            if (wellName == null) {
                return "FIMS data not annotated on referenced sequence (well name)";
            }
            BiocodeUtilities.Well well = new BiocodeUtilities.Well(wellName);

            Reaction reaction;
            try {
                reaction = plate.getReaction(well.row(), well.col());
            } catch (IllegalArgumentException e) {
                throw new DocumentOperationException(
                        "Well " + wellName + " could not be found on " + plateName + ": " +  e.getMessage() + "\n\n" +
                        "Your assembly and/or sequence documents may have been annotated with incorrect well " +
                        "values as a result of a bug that had existed in v2.8.7 and earlier versions of the biocode plugin. " +
                        "Please try re-annotating your assembly and/or sequence documents via fims/lims data using v2.8.8 or later of the plugin.", e
                );
            }
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

            AnnotatedPluginDocument doc = entry.getValue();
            assemblyResult.addReaction((CycleSequencingReaction) reaction, doc != null ? Collections.singletonList(doc) : Collections.<AnnotatedPluginDocument>emptyList());
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
        BatchChromatogramExportOperation chromatogramExportOperation = new BatchChromatogramExportOperation();
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

                attachChromats(limsConnection, isPass, options.isAddChromatograms(), assemblyResult, chromatogramExportOperation);
            }
        } catch (DatabaseServiceException e) {
            throw new DocumentOperationException("Failed to mark as pass/fail in LIMS: " + e.getMessage(), e);
        }

        for (AnnotatedPluginDocument annotatedDocument : annotatedDocuments) {
            Integer savedSeqId = seqIds.get(annotatedDocument.getURN());
            if (savedSeqId == null) {
                continue;
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

    private static void attachChromats(LIMSConnection limsConnection,
                                       boolean isPass,
                                       boolean addChromatograms,
                                       AddAssemblyResultsToLimsOperation.AssemblyResult result,
                                       BatchChromatogramExportOperation chromatogramExportOperation)
            throws DocumentOperationException, DatabaseServiceException {
        Options chromatogramExportOptions = null;
        File tempFolder;
        try {
            tempFolder = FileUtilities.createTempFile("chromat", ".ab1", true).getParentFile();
            for (Map.Entry<CycleSequencingReaction, List<AnnotatedPluginDocument>> entry : result.getReactions().entrySet()) {
                if (addChromatograms) {
                    if (chromatogramExportOptions == null) {
                        chromatogramExportOptions = chromatogramExportOperation.getOptions(entry.getValue());
                    }
                    List<Trace> traces = new ArrayList<Trace>();
                    for (AnnotatedPluginDocument chromatogramDocument : entry.getValue()) {
                        String nameOfFileToExport = chromatogramDocument.getName();
                        int indexOfLastDot = nameOfFileToExport.lastIndexOf(".");
                        String nameOfFileToExportWithExtensionRemoved = indexOfLastDot == -1 ? nameOfFileToExport :
                                nameOfFileToExport.substring(0, indexOfLastDot);
                        File exportFolder = tempFolder;
                        if (new File(exportFolder, nameOfFileToExport).exists() || new File(exportFolder, nameOfFileToExportWithExtensionRemoved + ".scf").exists()) {
                            exportFolder = FileUtilities.createTempDir(true);
                        }
                        chromatogramExportOptions.setStringValue(BatchChromatogramExportOperation.EXPORT_FOLDER, exportFolder.toString());
                        chromatogramExportOperation.performOperation(new AnnotatedPluginDocument[] {chromatogramDocument}, ProgressListener.EMPTY, chromatogramExportOptions);
                        File exportedFile = new File(exportFolder, chromatogramExportOperation.getFileNameUsedFor(chromatogramDocument));
                        traces.add(new Trace(Arrays.asList((NucleotideSequenceDocument) chromatogramDocument.getDocument()), ReactionUtilities.loadFileIntoMemory(exportedFile)));
                    }
                    entry.getKey().addSequences(traces);
                }
                entry.getKey().getOptions().setValue(ReactionOptions.RUN_STATUS, isPass ? ReactionOptions.PASSED_VALUE : ReactionOptions.FAILED_VALUE);
            }
        } catch (IOException e) {
            throw new DatabaseServiceException(e, "Failed to create temporary files and directories to export traces: " + e.getMessage(), false);
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
