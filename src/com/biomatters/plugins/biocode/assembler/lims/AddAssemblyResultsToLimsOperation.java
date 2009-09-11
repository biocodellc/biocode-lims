package com.biomatters.plugins.biocode.assembler.lims;

import com.biomatters.geneious.publicapi.components.Dialogs;
import com.biomatters.geneious.publicapi.components.ProgressFrame;
import com.biomatters.geneious.publicapi.databaseservice.Query;
import com.biomatters.geneious.publicapi.documents.AnnotatedPluginDocument;
import com.biomatters.geneious.publicapi.documents.DocumentField;
import com.biomatters.geneious.publicapi.documents.sequence.NucleotideSequenceDocument;
import com.biomatters.geneious.publicapi.documents.sequence.SequenceAlignmentDocument;
import com.biomatters.geneious.publicapi.documents.sequence.SequenceAnnotation;
import com.biomatters.geneious.publicapi.documents.sequence.SequenceDocument;
import com.biomatters.geneious.publicapi.implementations.SequenceExtractionUtilities;
import com.biomatters.geneious.publicapi.plugin.*;
import com.biomatters.plugins.biocode.BiocodePlugin;
import com.biomatters.plugins.biocode.BiocodeUtilities;
import com.biomatters.plugins.biocode.labbench.BiocodeService;
import com.biomatters.plugins.biocode.labbench.PlateDocument;
import com.biomatters.plugins.biocode.labbench.Workflow;
import com.biomatters.plugins.biocode.labbench.lims.LIMSConnection;
import com.biomatters.plugins.biocode.labbench.plates.Plate;
import com.biomatters.plugins.biocode.labbench.reaction.CycleSequencingReaction;
import com.biomatters.plugins.biocode.labbench.reaction.Reaction;
import com.biomatters.plugins.biocode.labbench.reaction.ReactionOptions;
import jebl.util.CompositeProgressListener;
import jebl.util.ProgressListener;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
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
                .setInPopupMenu(true, isPass ? 0.6 : 0.61);
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
                        new DocumentSelectionSignature.DocumentSelectionSignatureAtom(NucleotideSequenceDocument.class, 0, Integer.MAX_VALUE)
                }),
                new DocumentSelectionSignature(new DocumentSelectionSignature.DocumentSelectionSignatureAtom[] {
                        new DocumentSelectionSignature.DocumentSelectionSignatureAtom(SequenceAlignmentDocument.class, 0, Integer.MAX_VALUE),
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
        return new AddAssemblyResultsToLimsOptions(documents);
    }

    public List<AssemblyResult> getAssemblyResults(AnnotatedPluginDocument[] annotatedDocuments, ProgressListener progressListener, AddAssemblyResultsToLimsOptions options) throws DocumentOperationException {

        Map<AnnotatedPluginDocument, String> docsToMark = new HashMap<AnnotatedPluginDocument, String>();
        for (AnnotatedPluginDocument document : annotatedDocuments) {
            boolean isAlignment = SequenceAlignmentDocument.class.isAssignableFrom(document.getDocumentClass());
            if (isAlignment) {
                if (!(((SequenceAlignmentDocument)document.getDocument()).getSequence(0) instanceof NucleotideSequenceDocument)) {
                    throw new DocumentOperationException("Selected alignment \"" + document.getName() + "\" is not an alignment of DNA sequences");
                }
            } else if (!NucleotideSequenceDocument.class.isAssignableFrom(document.getDocumentClass())) {
                throw new DocumentOperationException("Selected sequence \"" + document.getName() + "\" is not DNA");

            }

            if (isAlignment && BiocodeUtilities.isAlignmentOfContigs(document)) {
                SequenceAlignmentDocument alignment = (SequenceAlignmentDocument)document.getDocument();
                for (int i = 0; i < alignment.getNumberOfSequences(); i ++) {
                    if (i == alignment.getContigReferenceSequenceIndex()) continue;
                    docsToMark.put(alignment.getReferencedDocument(i), alignment.getSequence(i).getSequenceString().replace("-", ""));
                }
            } else {
                docsToMark.put(document, null);
            }
        }

        Map<String, Plate> sequencingPlateCache = new HashMap<String, Plate>();
        LIMSConnection limsConnection = BiocodeService.getInstance().getActiveLIMSConnection();
        IssueTracker issueTracker = new IssueTracker(isAutomated);
        CompositeProgressListener progress = new CompositeProgressListener(progressListener, docsToMark.size());
        List<AssemblyResult> results = new ArrayList<AssemblyResult>();
        Map<Integer, AssemblyResult> workflowsWithResults = new HashMap<Integer, AssemblyResult>();
        for (AnnotatedPluginDocument annotatedDocument : docsToMark.keySet()) {
            progress.beginSubtask();
            if (progress.isCanceled()) {
                break;
            }

            AssemblyResult assemblyResult = new AssemblyResult();

            String errorString = getChromatogramProperties(sequencingPlateCache, limsConnection, annotatedDocument, assemblyResult);
            if (errorString != null) {
                issueTracker.setIssue(annotatedDocument, errorString);
                continue;
            }

            Double coverage = (Double) annotatedDocument.getFieldValue(DocumentField.CONTIG_MEAN_COVERAGE);
            Integer disagreements = (Integer) annotatedDocument.getFieldValue(DocumentField.DISAGREEMENTS);

            int edits = getEdits(annotatedDocument);
            String[] trims = getTrimParameters(annotatedDocument);

            String consensus = docsToMark.get(annotatedDocument);
            if (consensus == null && SequenceAlignmentDocument.class.isAssignableFrom(annotatedDocument.getDocumentClass())) {
                consensus = ((SequenceDocument) BiocodeUtilities.getConsensusSequence(annotatedDocument, options.getConsensusOptions()).getDocument()).getSequenceString();
            }
            if (isPass && consensus == null) {
                issueTracker.setIssue(annotatedDocument, "Not a consensus sequence, cannot pass");
                continue;
            }

            if (workflowsWithResults.containsKey(assemblyResult.workflowId)) {
                if (isPass) {
                    issueTracker.setIssue(annotatedDocument, "Another selected result matches same workflow");
                    continue;
                } else {
                    AssemblyResult existingAssemblyResult = workflowsWithResults.get(assemblyResult.workflowId);
                    for (Map.Entry<CycleSequencingReaction, List<NucleotideSequenceDocument>> chromatogramEntry : assemblyResult.getReactions().entrySet()) {
                        existingAssemblyResult.addReaction(chromatogramEntry.getKey(), chromatogramEntry.getValue());
                    }
                    continue;
                }
            }

            assemblyResult.setContigProperties(consensus, coverage, disagreements, trims, edits);
            workflowsWithResults.put(assemblyResult.workflowId, assemblyResult);
            results.add(assemblyResult);
        }
        if (!issueTracker.promptToContinue(!results.isEmpty())) {
            return null;
        }
        return results;
    }

    private String getChromatogramProperties(Map<String, Plate> sequencingPlateCache, LIMSConnection limsConnection,
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
        } else {
            chromatograms.add(annotatedDocument);
        }

        for (AnnotatedPluginDocument chromatogram : chromatograms) {
            String plateName = (String)chromatogram.getFieldValue(BiocodeUtilities.SEQUENCING_PLATE_FIELD);
            if (plateName == null) {
                return "FIMS data not annotated on referenced sequence (plate name)";
            }

            Plate plate;
            if (sequencingPlateCache.containsKey(plateName)) {
                plate = sequencingPlateCache.get(plateName);
            } else {
                Query q = Query.Factory.createQuery(plateName);
                List<PlateDocument> plateDocuments;
                try {
                    plateDocuments = limsConnection.getMatchingPlateDocuments(q, null);
                } catch (SQLException e) {
                    e.printStackTrace();
                    throw new DocumentOperationException("Failed to connect to LIMS: " + e.getMessage(), e);
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

            String wellName = (String) chromatogram.getFieldValue(BiocodeUtilities.SEQUENCING_WELL_FIELD);
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

            assemblyResult.addReaction((CycleSequencingReaction) reaction, Collections.singletonList((NucleotideSequenceDocument) chromatogram.getDocument()));
        }
        return null;
    }

    /**
     * Assembly results for a single workflow. May involve several sequencing reactions though (normally a forward and reverse)
     */
    private static final class AssemblyResult {

        public String extractionId;
        public Integer workflowId;
        public String consensus;
        public Double coverage;
        public Integer disagreements;
        public String[] trims;
        public int edits;

        private Map<Integer, List<NucleotideSequenceDocument>> chromatograms = new HashMap<Integer, List<NucleotideSequenceDocument>>();
        private Map<Integer, CycleSequencingReaction> reactionsById = new HashMap<Integer, CycleSequencingReaction>();

        public void setContigProperties(String consensus, Double coverage, Integer disagreements, String[] trims, int edits) {
            this.consensus = consensus;
            this.coverage = coverage;
            this.disagreements = disagreements;
            this.trims = trims;
            this.edits = edits;
        }

        public void addReaction(CycleSequencingReaction cycleSequencingReaction, List<NucleotideSequenceDocument> chromatograms) {
            List<NucleotideSequenceDocument> currentChromatograms = this.chromatograms.get(cycleSequencingReaction.getId());
            if (currentChromatograms == null) {
                currentChromatograms = new ArrayList<NucleotideSequenceDocument>();
                this.chromatograms.put(cycleSequencingReaction.getId(), currentChromatograms);
                this.reactionsById.put(cycleSequencingReaction.getId(), cycleSequencingReaction);
            }
            currentChromatograms.addAll(chromatograms);
        }

        public Map<CycleSequencingReaction, List<NucleotideSequenceDocument>> getReactions() {
            Map<CycleSequencingReaction, List<NucleotideSequenceDocument>> returnChromatograms = new HashMap<CycleSequencingReaction, List<NucleotideSequenceDocument>>();
            for (Map.Entry<Integer, List<NucleotideSequenceDocument>> entry : chromatograms.entrySet()) {
                returnChromatograms.put(reactionsById.get(entry.getKey()), entry.getValue());
            }
            return returnChromatograms;
        }
    }

    @Override
    public List<AnnotatedPluginDocument> performOperation(AnnotatedPluginDocument[] annotatedDocuments, ProgressListener progressListener, Options o) throws DocumentOperationException {
        AddAssemblyResultsToLimsOptions options = (AddAssemblyResultsToLimsOptions) o;
        CompositeProgressListener progress = new CompositeProgressListener(progressListener, 0.8, 0.2);
        progress.beginSubtask("Checking results");
        List<AssemblyResult> assemblyResults = getAssemblyResults(annotatedDocuments, progress, options);

        progress.beginSubtask("Saving to LIMS");
        Connection connection = BiocodeService.getInstance().getActiveLIMSConnection().getConnection();
        progress = new CompositeProgressListener(progress, assemblyResults.size());
        if (progress.getRootProgressListener() instanceof ProgressFrame) {
            ((ProgressFrame)progress.getRootProgressListener()).setCancelButtonLabel("Stop");
        }
        for (AssemblyResult result : assemblyResults) {
            progress.beginSubtask();
            if (progress.isCanceled()) {
                break;
            }
            try {
                PreparedStatement statement;
                statement = connection.prepareStatement("INSERT INTO assembly (extraction_id, workflow, progress, consensus, " +
                        "coverage, disagreements, trim_params_fwd, trim_params_rev, edits) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)");
                statement.setString(1, result.extractionId);
                statement.setInt(2, result.workflowId);
                statement.setString(3, isPass ? "passed" : "failed");
                if (result.consensus == null) {
                    statement.setNull(4, Types.LONGVARCHAR);
                } else {
                    statement.setString(4, result.consensus);
                }
                if (result.coverage == null) {
                    statement.setNull(5, Types.FLOAT);
                } else {
                    statement.setDouble(5, result.coverage);
                }
                if (result.disagreements == null) {
                    statement.setNull(6, Types.INTEGER);
                } else {
                    statement.setInt(6, result.disagreements);
                }
                if (result.trims[0] == null) {
                    statement.setNull(7, Types.LONGVARCHAR);
                } else {
                    statement.setString(7, result.trims[0]);
                }
                if (result.trims[1] == null) {
                    statement.setNull(8, Types.LONGVARCHAR);
                } else {
                    statement.setString(8, result.trims[1]);
                }
                statement.setInt(9, result.edits);

                statement.execute();

                for (Map.Entry<CycleSequencingReaction, List<NucleotideSequenceDocument>> entry : result.getReactions().entrySet()) {
                    if (options.isAddChromatograms()) {
                        entry.getKey().addSequences(entry.getValue());
                    }
                    entry.getKey().getOptions().setValue(ReactionOptions.RUN_STATUS, isPass ? ReactionOptions.PASSED_VALUE : ReactionOptions.FAILED_VALUE);
                    Reaction.saveReactions(new Reaction[] {entry.getKey()}, Reaction.Type.CycleSequencing, connection, null);
                }

            } catch (SQLException e) {
                throw new DocumentOperationException("Failed to connect to LIMS: " + e.getMessage(), e);
            }
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

    /**
     *
     * @param annotatedDocument
     * @return String[2] where String[0] = forward trim params and String[1] = reverse trim params. May contain null but never null.
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