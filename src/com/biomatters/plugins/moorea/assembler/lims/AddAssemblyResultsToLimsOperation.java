package com.biomatters.plugins.moorea.assembler.lims;

import com.biomatters.geneious.publicapi.components.Dialogs;
import com.biomatters.geneious.publicapi.components.ProgressFrame;
import com.biomatters.geneious.publicapi.documents.AnnotatedPluginDocument;
import com.biomatters.geneious.publicapi.documents.DocumentField;
import com.biomatters.geneious.publicapi.documents.sequence.NucleotideSequenceDocument;
import com.biomatters.geneious.publicapi.documents.sequence.SequenceAlignmentDocument;
import com.biomatters.geneious.publicapi.documents.sequence.SequenceAnnotation;
import com.biomatters.geneious.publicapi.documents.sequence.SequenceDocument;
import com.biomatters.geneious.publicapi.implementations.SequenceExtractionUtilities;
import com.biomatters.geneious.publicapi.plugin.*;
import com.biomatters.plugins.moorea.MooreaPlugin;
import com.biomatters.plugins.moorea.MooreaUtilities;
import com.biomatters.plugins.moorea.labbench.MooreaLabBenchService;
import com.biomatters.plugins.moorea.labbench.WorkflowDocument;
import com.biomatters.plugins.moorea.labbench.fims.FIMSConnection;
import com.biomatters.plugins.moorea.labbench.lims.LIMSConnection;
import com.biomatters.plugins.moorea.labbench.reaction.CycleSequencingReaction;
import com.biomatters.plugins.moorea.labbench.reaction.Reaction;
import jebl.util.CompositeProgressListener;
import jebl.util.ProgressListener;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
        return GeneiousActionOptions.createSubmenuActionOptions(MooreaPlugin.getSuperBiocodeAction(), geneiousActionOptions);
    }

    public String getHelp() {
        return "Select one or more assemblies or consensus sequences to mark them as " + (isPass ? "passed" : "failed") + " on the relevant workflows in " +
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
        if (!MooreaLabBenchService.getInstance().isLoggedIn()) {
            throw new DocumentOperationException(MooreaUtilities.NOT_CONNECTED_ERROR_MESSAGE);
        }
        return new AddAssemblyResultsToLimsOptions(documents);
    }

    public List<AssemblyResult> getAssemblyResults(AnnotatedPluginDocument[] annotatedDocuments, ProgressListener progressListener, AddAssemblyResultsToLimsOptions options) throws DocumentOperationException {

        Map<AnnotatedPluginDocument, String> docsToMark = new HashMap<AnnotatedPluginDocument, String>();
        for (AnnotatedPluginDocument document : annotatedDocuments) {
            if (SequenceAlignmentDocument.class.isAssignableFrom(document.getDocumentClass()) && MooreaUtilities.isAlignmentOfContigs(document)) {
                SequenceAlignmentDocument alignment = (SequenceAlignmentDocument)document.getDocument();
                for (int i = 0; i < alignment.getNumberOfSequences(); i ++) {
                    if (i == alignment.getContigReferenceSequenceIndex()) continue;
                    docsToMark.put(alignment.getReferencedDocument(i), alignment.getSequence(i).getSequenceString().replace("-", ""));
                }
            } else {
                docsToMark.put(document, null);
            }
        }

        LIMSConnection limsConnection = MooreaLabBenchService.getInstance().getActiveLIMSConnection();
        IssueTracker issueTracker = new IssueTracker(isAutomated);
        FIMSConnection fimsConnection = MooreaLabBenchService.getInstance().getActiveFIMSConnection();
        CompositeProgressListener progress = new CompositeProgressListener(progressListener, docsToMark.size());
        DocumentField tissueIdField = fimsConnection.getTissueSampleDocumentField();
        List<AssemblyResult> results = new ArrayList<AssemblyResult>();
        Map<Integer, AssemblyResult> workflowsWithResults = new HashMap<Integer, AssemblyResult>();
        for (AnnotatedPluginDocument annotatedDocument : docsToMark.keySet()) {
            progress.beginSubtask();
            if (progress.isCanceled()) {
                break;
            }

            Object tissueId = annotatedDocument.getFieldValue(tissueIdField);
            if (tissueId == null) {
                issueTracker.setIssue(annotatedDocument, "FIMS data not annotated on document");
                continue;
            }

            WorkflowDocument workflow = MooreaUtilities.getMostRecentWorkflow(limsConnection, fimsConnection, tissueId);
            if (workflow == null) {
                issueTracker.setIssue(annotatedDocument, "No workflow record found in LIMS");
                continue;
            }
            int workflowId = workflow.getId();
            if (workflowId == -1) {
                issueTracker.setIssue(annotatedDocument, "No workflow record found in LIMS");
                continue;
            }
            String extractionId = workflow.getMostRecentReaction(Reaction.Type.Extraction).getExtractionId();

            Double coverage = (Double) annotatedDocument.getFieldValue(DocumentField.CONTIG_MEAN_COVERAGE);
            Integer disagreements = (Integer) annotatedDocument.getFieldValue(DocumentField.DISAGREEMENTS);

            int edits = getEdits(annotatedDocument);
            String[] trims = getTrimParameters(annotatedDocument);

            String consensus = docsToMark.get(annotatedDocument);
            if (consensus == null && SequenceAlignmentDocument.class.isAssignableFrom(annotatedDocument.getDocumentClass())) {
                consensus = ((SequenceDocument) MooreaUtilities.getConsensusSequence(annotatedDocument, options.getConsensusOptions()).getDocument()).getSequenceString();
            }
            if (isPass && consensus == null) {
                issueTracker.setIssue(annotatedDocument, "Not a consensus sequence, cannot pass");
                continue;
            }
            List<NucleotideSequenceDocument> chromatograms = new ArrayList<NucleotideSequenceDocument>();
            CycleSequencingReaction cycleSequencing = (CycleSequencingReaction) workflow.getMostRecentReaction(Reaction.Type.CycleSequencing);
            if (cycleSequencing == null) {
                issueTracker.setIssue(annotatedDocument, "No cycle sequencing event found in LIMS");
                continue;
            }
            if (options.isAddChromatograms()) {
                if (consensus == null) {
                    chromatograms.add((NucleotideSequenceDocument)annotatedDocument.getDocument());
                } else {
                    SequenceAlignmentDocument alignment = (SequenceAlignmentDocument)annotatedDocument.getDocument();
                    if (alignment.getNumberOfSequences() < 3) {
                        for (int i = 0; i < alignment.getNumberOfSequences(); i++) {
                            if (i == alignment.getContigReferenceSequenceIndex()) continue;
                            AnnotatedPluginDocument reference = alignment.getReferencedDocument(i);
                            if (reference == null || !NucleotideSequenceDocument.class.isAssignableFrom(reference.getDocumentClass())) continue;
                            chromatograms.add((NucleotideSequenceDocument)reference.getDocument());
                        }
                    }
                }
            }
            if (workflowsWithResults.containsKey(workflowId)) {
                if (isPass) {
                    issueTracker.setIssue(annotatedDocument, "Another selected result matches same workflow");
                    continue;
                } else {
                    workflowsWithResults.get(workflowId).addChromatograms(chromatograms);
                    continue;
                }
            }


            AssemblyResult assemblyResult = new AssemblyResult(extractionId, workflowId, consensus, coverage, disagreements, trims, edits, cycleSequencing, chromatograms);
            workflowsWithResults.put(workflowId, assemblyResult);
            results.add(assemblyResult);
        }
        if (!issueTracker.promptToContinue(!results.isEmpty())) {
            return null;
        }
        return results;
    }

    private static final class AssemblyResult {

        public final String extractionId;
        public final int workflowId;
        public final String consensus;
        public final Double coverage;
        public final Integer disagreements;
        public final String[] trims;
        public final int edits;
        public final CycleSequencingReaction cycleSequencing;
        public final List<NucleotideSequenceDocument> chromatograms;

        private AssemblyResult(String extractionId, int workflowId, String consensus,
                               Double coverage, Integer disagreements, String[] trims, int edits, CycleSequencingReaction cycleSequencing, List<NucleotideSequenceDocument> chromatograms) {
            this.extractionId = extractionId;
            this.workflowId = workflowId;
            this.consensus = consensus;
            this.coverage = coverage;
            this.disagreements = disagreements;
            this.trims = trims;
            this.edits = edits;
            this.cycleSequencing = cycleSequencing;
            this.chromatograms = chromatograms;
        }

        public void addChromatograms(List<NucleotideSequenceDocument> chromatograms) {
            this.chromatograms.addAll(chromatograms);
        }
    }

    @Override
    public List<AnnotatedPluginDocument> performOperation(AnnotatedPluginDocument[] annotatedDocuments, ProgressListener progressListener, Options o) throws DocumentOperationException {
        AddAssemblyResultsToLimsOptions options = (AddAssemblyResultsToLimsOptions) o;
        CompositeProgressListener progress = new CompositeProgressListener(progressListener, 0.8, 0.2);
        progress.beginSubtask("Checking results");
        List<AssemblyResult> assemblyResults = getAssemblyResults(annotatedDocuments, progress, options);

        progress.beginSubtask("Saving to LIMS");
        Connection connection = MooreaLabBenchService.getInstance().getActiveLIMSConnection().getConnection();
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

                result.cycleSequencing.addSequences(result.chromatograms);
                Reaction.saveReactions(new Reaction[] {result.cycleSequencing}, Reaction.Type.CycleSequencing, connection, null);
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
