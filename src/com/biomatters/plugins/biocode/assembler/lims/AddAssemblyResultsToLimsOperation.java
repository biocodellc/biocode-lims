package com.biomatters.plugins.biocode.assembler.lims;

import com.biomatters.geneious.publicapi.components.Dialogs;
import com.biomatters.geneious.publicapi.components.ProgressFrame;
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
import com.biomatters.plugins.biocode.labbench.BiocodeService;
import com.biomatters.plugins.biocode.labbench.PlateDocument;
import com.biomatters.plugins.biocode.labbench.Workflow;
import com.biomatters.plugins.biocode.labbench.lims.LIMSConnection;
import com.biomatters.plugins.biocode.labbench.plates.Plate;
import com.biomatters.plugins.biocode.labbench.reaction.CycleSequencingReaction;
import com.biomatters.plugins.biocode.labbench.reaction.Reaction;
import com.biomatters.plugins.biocode.labbench.reaction.ReactionOptions;
import com.biomatters.plugins.biocode.labbench.reaction.ReactionUtilities;
import jebl.util.CompositeProgressListener;
import jebl.util.ProgressListener;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.input.SAXBuilder;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
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

            if (isAlignment) {
                if(BiocodeUtilities.isAlignmentOfContigs(document)) {
                    SequenceAlignmentDocument alignment = (SequenceAlignmentDocument)document.getDocument();
                    for (int i = 0; i < alignment.getNumberOfSequences(); i ++) {
                        if (i == alignment.getContigReferenceSequenceIndex()) continue;
                        docsToMark.put(alignment.getReferencedDocument(i), alignment.getSequence(i).getSequenceString().replace("-", ""));
                    }
                }
                else {
                    docsToMark.put(document, null);
                }
            } else {
                docsToMark.put(document, ((NucleotideSequenceDocument)document.getDocument()).getSequenceString());
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
            Integer ambiguities = (Integer) annotatedDocument.getFieldValue(DocumentField.AMBIGUITIES);
            String bin = (String) annotatedDocument.getFieldValue(DocumentField.BIN);

            int edits = getEdits(annotatedDocument);
            String[] trims = getTrimParameters(annotatedDocument);

            String consensus = docsToMark.get(annotatedDocument);
            SequenceDocument consensusDocument = null;
            if (consensus == null && SequenceAlignmentDocument.class.isAssignableFrom(annotatedDocument.getDocumentClass())) {
                consensusDocument = (SequenceDocument) BiocodeUtilities.getConsensusSequence(annotatedDocument, options.getConsensusOptions()).getDocument();
                consensus = consensusDocument.getSequenceString();
            }
            if (isPass && consensus == null) {
                assert false: "there should be a consensus here!";
            }
            int[] qualities = null;
            if(NucleotideGraphSequenceDocument.class.isAssignableFrom(annotatedDocument.getDocumentClass())) {
                qualities = getQualities((SequenceDocument)annotatedDocument.getDocument());
            }
            else if(consensusDocument != null) {
                qualities = getQualities(consensusDocument);
            }

            if (workflowsWithResults.containsKey(assemblyResult.workflowId)) {
                AssemblyResult existingAssemblyResult = workflowsWithResults.get(assemblyResult.workflowId);
                for (Map.Entry<CycleSequencingReaction, List<AnnotatedPluginDocument>> chromatogramEntry : assemblyResult.getReactions().entrySet()) {
                    existingAssemblyResult.addReaction(chromatogramEntry.getKey(), chromatogramEntry.getValue());
                }
                continue;
            }
            assemblyResult.setContigProperties(annotatedDocument, consensus, qualities, coverage, disagreements, trims, edits, ambiguities == null ? 0 : ambiguities, bin);
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
                    plateDocuments = limsConnection.getMatchingPlateDocuments(q, null, null);
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

            assemblyResult.addReaction((CycleSequencingReaction) reaction, Collections.singletonList(chromatogram));
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
        public int[] qualities;
        public int edits;
        public int ambiguities;
        public String bin;
        public AnnotatedPluginDocument assembly;

        private Map<Integer, List<AnnotatedPluginDocument>> chromatograms = new HashMap<Integer, List<AnnotatedPluginDocument>>();
        private Map<Integer, CycleSequencingReaction> reactionsById = new HashMap<Integer, CycleSequencingReaction>();

        public void setContigProperties(AnnotatedPluginDocument assembly, String consensus, int[] qualities, Double coverage, Integer disagreements, String[] trims, int edits, int ambiguities, String bin) {
            this.consensus = consensus;
            this.coverage = coverage;
            this.disagreements = disagreements;
            this.trims = trims;
            this.edits = edits;
            this.ambiguities = ambiguities;
            this.bin = bin;
            this.qualities = qualities;
            this.assembly = assembly;
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
    public List<AnnotatedPluginDocument> performOperation(AnnotatedPluginDocument[] annotatedDocuments, ProgressListener progressListener, Options o) throws DocumentOperationException {
        AddAssemblyResultsToLimsOptions options = (AddAssemblyResultsToLimsOptions) o;
        CompositeProgressListener progress = new CompositeProgressListener(progressListener, 0.4, 0.2);
        progress.beginSubtask("Checking results");
        List<AssemblyResult> assemblyResults = getAssemblyResults(annotatedDocuments, progress, options);

        for(AssemblyResult result : assemblyResults) {
            Collection<CycleSequencingReaction> reactions = result.reactionsById.values();
            for(CycleSequencingReaction reaction : reactions) {
                reaction.setRemoveExistingTracesOnSave(false); //tell the reaction not to remove the existing traces in the database on save so we don't have to download all the existing ones just to add some...
            }
        }

        progress.beginSubtask("Saving to LIMS");
        Connection connection = BiocodeService.getInstance().getActiveLIMSConnection().getConnection();
        progress = new CompositeProgressListener(progress, assemblyResults.size());
        if (progress.getRootProgressListener() instanceof ProgressFrame) {
            ((ProgressFrame)progress.getRootProgressListener()).setCancelButtonLabel("Stop");
        }
        for (AssemblyResult result : assemblyResults) {
            progress.beginSubtask();
            if (progress.isCanceled() || true) {
                break;
            }
            try {
                PreparedStatement statement;
                statement = connection.prepareStatement("INSERT INTO assembly (extraction_id, workflow, progress, consensus, " +
                        "coverage, disagreements, trim_params_fwd, trim_params_rev, edits, params, reference_seq_id, confidence_scores, other_processing_fwd, other_processing_rev, notes) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
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
                String params = getAssemblyOptionsValues(result.assembly);
                if(params != null) {
                    statement.setString(10, params); //params
                }
                else {
                    statement.setNull(10, Types.LONGVARCHAR); //params
                }
                statement.setNull(11, Types.INTEGER); //reference_seq_id
                if(result.qualities != null) {
                    List<Integer> qualitiesList = new ArrayList<Integer>();
                    for(int i : result.qualities) {
                        qualitiesList.add(i);
                    }
                    statement.setString(12, StringUtilities.join(",", qualitiesList));
                }
                else {
                    statement.setNull(12, Types.LONGVARCHAR); //confidence_scores
                }
                statement.setNull(13, Types.LONGVARCHAR); //other_processing_fwd
                statement.setNull(14, Types.LONGVARCHAR); //other_processing_rev
                statement.setNull(15, Types.LONGVARCHAR); //notes

                statement.execute();

                BatchChromatogramExportOperation chromatogramExportOperation = new BatchChromatogramExportOperation();
                Options chromatogramExportOptions = null;
                File tempFolder;
                try {
                    tempFolder = FileUtilities.createTempFile("chromat", ".ab1").getParentFile();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }

                for (Map.Entry<CycleSequencingReaction, List<AnnotatedPluginDocument>> entry : result.getReactions().entrySet()) {
                    if (options.isAddChromatograms()) {
                        if (chromatogramExportOptions == null) {
                            chromatogramExportOptions = chromatogramExportOperation.getOptions(entry.getValue());;
                            chromatogramExportOptions.setValue("exportTo", tempFolder.toString());
                        }
                        List<NucleotideSequenceDocument> sequences = new ArrayList<NucleotideSequenceDocument>();
                        List<ReactionUtilities.MemoryFile> rawTraces = new ArrayList<ReactionUtilities.MemoryFile>();
                        for (AnnotatedPluginDocument chromatogramDocument : entry.getValue()) {
                            sequences.add((NucleotideSequenceDocument)chromatogramDocument.getDocument());
                            chromatogramExportOperation.performOperation(new AnnotatedPluginDocument[] {chromatogramDocument}, ProgressListener.EMPTY, chromatogramExportOptions);
                            File exportedFile = new File(tempFolder, chromatogramExportOperation.getFileNameUsedFor(chromatogramDocument));
                            try {
                                rawTraces.add(ReactionUtilities.loadFileIntoMemory(exportedFile));
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                        }
                        entry.getKey().addSequences(sequences, rawTraces);
                    }
                    entry.getKey().getOptions().setValue(ReactionOptions.RUN_STATUS, isPass ? ReactionOptions.PASSED_VALUE : ReactionOptions.FAILED_VALUE);
                }

                Set<CycleSequencingReaction> reactionSet = result.getReactions().keySet();
                Reaction.saveReactions(reactionSet.toArray(new Reaction[reactionSet.size()]), Reaction.Type.CycleSequencing, connection, null);

                statement.close();

                for(CycleSequencingReaction reaction : result.reactionsById.values()) {
                    reaction.purgeChromats();
                }

            } catch (SQLException e) {
                e.printStackTrace();
                throw new DocumentOperationException("Failed to connect to LIMS: " + e.getMessage(), e);
            }
        }
        return null;
    }

    private Options getAssemblyOptions(AnnotatedPluginDocument document) throws DocumentOperationException {
        String optionsValues = getAssemblyOptionsValues(document);
        if(optionsValues != null) {
            return getOptions(optionsValues, "com.biomatters.plugins.alignment.AssemblyOperation", document);
        }
        return null;
    }

    private String getAssemblyOptionsValues(AnnotatedPluginDocument document) throws DocumentOperationException{
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

    /*
    copied from DOcumentHistoryEntryPanel (and edited slightly to change the way exceptions are handled)
     */
    private static Options getOptions(String optionsFieldValue, String operationUniqueIdFieldValue, AnnotatedPluginDocument document) throws DocumentOperationException{
        Element optionsElement;
        SAXBuilder saxBuilder = new SAXBuilder();
        Reader stringReader = new StringReader(optionsFieldValue);
        try {
            final Document xmlDocument = saxBuilder.build(stringReader);
            optionsElement = xmlDocument.getRootElement();
        }
        catch (JDOMException ex) {
            throw new DocumentOperationException("Information about the options used to assemble your sequences has been corrupted", ex);
        }
        catch (IOException ex) {
            throw new DocumentOperationException("Could not read the history of your documents: "+ex.getMessage(), ex);
        }

        DocumentOperation operation = PluginUtilities.getDocumentOperation(operationUniqueIdFieldValue);
        Options options = null;
        if(operation != null) {
            options = operation.getOptions(document);

            options.valuesFromXML(optionsElement);
        }
        return options;
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
