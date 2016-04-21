package com.biomatters.plugins.biocode.assembler.download;

import com.biomatters.geneious.publicapi.components.Dialogs;
import com.biomatters.geneious.publicapi.databaseservice.DatabaseServiceException;
import com.biomatters.geneious.publicapi.documents.AnnotatedPluginDocument;
import com.biomatters.geneious.publicapi.documents.DocumentField;
import com.biomatters.geneious.publicapi.documents.DocumentUtilities;
import com.biomatters.geneious.publicapi.documents.PluginDocument;
import com.biomatters.geneious.publicapi.documents.sequence.NucleotideSequenceDocument;
import com.biomatters.geneious.publicapi.plugin.*;
import com.biomatters.plugins.biocode.BiocodePlugin;
import com.biomatters.plugins.biocode.BiocodeUtilities;
import com.biomatters.plugins.biocode.assembler.annotate.AnnotateUtilities;
import com.biomatters.plugins.biocode.assembler.annotate.FimsData;
import com.biomatters.plugins.biocode.assembler.annotate.FimsDataGetter;
import com.biomatters.plugins.biocode.labbench.*;
import com.biomatters.plugins.biocode.labbench.plates.Plate;
import com.biomatters.plugins.biocode.labbench.reaction.CycleSequencingOptions;
import com.biomatters.plugins.biocode.labbench.reaction.CycleSequencingReaction;
import com.biomatters.plugins.biocode.labbench.reaction.Reaction;
import com.biomatters.plugins.biocode.labbench.reaction.Trace;
import com.google.common.collect.ArrayListMultimap;
import jebl.util.CompositeProgressListener;
import jebl.util.ProgressListener;

import java.io.IOException;
import java.util.*;

/**
 * @author Richard
 * @version $Id$
 */
public class DownloadChromatogramsFromLimsOperation extends DocumentOperation {

    private final boolean isAutomated;

    public DownloadChromatogramsFromLimsOperation(boolean isAutomated) {
        this.isAutomated = isAutomated;
    }

    public GeneiousActionOptions getActionOptions() {
        GeneiousActionOptions geneiousActionOptions = new GeneiousActionOptions("Download Traces from LIMS...",
                "Download chromatograms/traces from LIMS and annotate them with the necessary data for assembly and submission.")
                .setInPopupMenu(true, 0.11);
        return GeneiousActionOptions.createSubmenuActionOptions(BiocodePlugin.getSuperBiocodeAction(), geneiousActionOptions);
    }

    public String getHelp() {
        //matches any document selection so the help won't be shown currently
        return "";
    }

    public DocumentSelectionSignature[] getSelectionSignatures() {
        return new DocumentSelectionSignature[]{new DocumentSelectionSignature(PluginDocument.class, 0, Integer.MAX_VALUE)};
    }

    @Override
    public Options getOptions(AnnotatedPluginDocument... documents) throws DocumentOperationException {
        if (!BiocodeService.getInstance().isLoggedIn()) {
            throw new DocumentOperationException(BiocodeUtilities.NOT_CONNECTED_ERROR_MESSAGE);
        }
        return new DownloadChromatogramsFromLimsOptions(documents);
    }

    @Override
    public void performOperation(AnnotatedPluginDocument[] annotatedDocuments, ProgressListener progressListener,
                                 Options _options, SequenceSelection sequenceSelection, OperationCallback operationCallback
    ) throws DocumentOperationException {

        if(!BiocodeService.getInstance().isLoggedIn()) {
            throw new DocumentOperationException(BiocodeUtilities.NOT_CONNECTED_ERROR_MESSAGE);
        }
        if(!(_options instanceof DownloadChromatogramsFromLimsOptions)) {
            throw new IllegalArgumentException("Options must be obtained by calling getOptions()");
        }
        DownloadChromatogramsFromLimsOptions options = (DownloadChromatogramsFromLimsOptions)_options;

        BiocodeCallback callback = new BiocodeCallback(progressListener);
        BiocodeService.getInstance().registerCallback(callback);
        try {
            CompositeProgressListener progress = new CompositeProgressListener(callback, 0.2, 0.4, 0.2, 0.2);
            progress.setIndeterminateProgress();
            progress.beginSubtask("Getting reactions");

            Map<String, List<String>> toRetrieve = options.getPlatesAndWorkflowsToRetrieve(annotatedDocuments);
            Map<CycleSequencingReaction, FimsData> fimsDataForReactions = getReactionsForPlateNames(toRetrieve, progress);
            if (fimsDataForReactions == null) return;
            Set<CycleSequencingReaction> reactions = fimsDataForReactions.keySet();
            progress.beginSubtask("Downloading Traces");
            try {
                BiocodeUtilities.downloadTracesForReactions(reactions, progress);
            } catch (DatabaseServiceException e) {
                e.printStackTrace();
                throw new DocumentOperationException("Failed to download raw traces: " + e.getMessage(), e);
            } catch (IOException e) {
                e.printStackTrace();
                throw new DocumentOperationException("Failed to download raw traces: " + e.getMessage(), e);
            } catch (DocumentImportException e) {
                e.printStackTrace();
                throw new DocumentOperationException("Failed to download raw traces: " + e.getMessage(), e);
            }
            progress.beginSubtask("Creating Documents...");
            final Map<AnnotatedPluginDocument, FimsData> fimsData = new HashMap<AnnotatedPluginDocument, FimsData>();
            List<AnnotatedPluginDocument> chromatogramDocuments = new ArrayList<AnnotatedPluginDocument>();
            CompositeProgressListener tracesProgress = new CompositeProgressListener(progress, reactions.size());
            for (CycleSequencingReaction reaction : reactions) {
                tracesProgress.beginSubtask();
                if (tracesProgress.isCanceled()) return;
                CycleSequencingOptions sequencingOptions = (CycleSequencingOptions) reaction.getOptions();
                boolean isForward = sequencingOptions.getValueAsString(CycleSequencingOptions.DIRECTION).equals(CycleSequencingOptions.FORWARD_VALUE);
                List<Trace> traces = reaction.getTraces();
                if (traces == null) continue;
                for(Trace trace : traces) {
                    List<NucleotideSequenceDocument> traceSequences = trace.getSequences();
                    if(traceSequences == null) {
                        continue;
                    }
                    for (NucleotideSequenceDocument traceSequence : traceSequences) {
                        if(traceSequence == null) {
                            continue;
                        }
                        AnnotatedPluginDocument traceDocument = DocumentUtilities.createAnnotatedPluginDocument(traceSequence);
                        traceDocument.setFieldValue(BiocodeUtilities.IS_FORWARD_FIELD, isForward);
                        fimsData.put(traceDocument, fimsDataForReactions.get(reaction));
                        chromatogramDocuments.add(traceDocument);
                    }
                }
                reaction.purgeChromats();
            }
            if (progress.isCanceled()) return;
            FimsDataGetter fimsDataGetter = new FimsDataGetter() {
                public FimsData getFimsData(AnnotatedPluginDocument document) throws DocumentOperationException {
                    return fimsData.get(document);
                }
            };
            AnnotatedPluginDocument[] array = chromatogramDocuments.toArray(new AnnotatedPluginDocument[chromatogramDocuments.size()]);
            try {
                AnnotateUtilities.annotateFimsData(array, ProgressListener.EMPTY, fimsDataGetter, false);
            } catch (DocumentOperationException e) {
                if (!isAutomated) {
                    String continueButton = "Continue";
                    Dialogs.DialogOptions dialogOptions = new Dialogs.DialogOptions(new String[] {continueButton, "Cancel"}, "Some FIMS Data Not Found");
                    Object choice = Dialogs.showDialog(dialogOptions, e.getMessage());
                    if (!choice.equals(continueButton)) {
                        return;
                    }
                }
            }
            if (progress.isCanceled()) return;
            for (AnnotatedPluginDocument chromatogramDocument : chromatogramDocuments) {
                operationCallback.addDocument(chromatogramDocument, false, ProgressListener.EMPTY);
            }

            if(options.isAssembleTraces()) {
                progress.beginSubtask("Assembling Traces");
                DocumentOperation assemblyOperation = PluginUtilities.getDocumentOperation("com.biomatters.plugins.alignment.AssemblyOperation");
                if(assemblyOperation == null) {
                    Dialogs.showMessageDialog("Could not assemble traces because could not find assembly operation. " +
                            "Please make sure the Alignmnet plugin is installed and enabled by going to Tools menu -> Plugins...");
                } else {
                    assembleTraces(assemblyOperation, annotatedDocuments, chromatogramDocuments, progress, operationCallback);
                }
            }
        } catch (DatabaseServiceException e) {
            e.printStackTrace();
            throw new DocumentOperationException("Could not retrieve active LIMS connection: " + e.getMessage(), e);
        }
        finally {
            BiocodeService.getInstance().unregisterCallback(callback);
        }
    }

    private void assembleTraces(DocumentOperation assemblyOperation, AnnotatedPluginDocument[] annotatedDocuments, List<AnnotatedPluginDocument> chromatogramDocuments, ProgressListener progress, OperationCallback operationCallback) throws DocumentOperationException {
        ArrayListMultimap<AnnotatedPluginDocument, AnnotatedPluginDocument> refToTraces = ArrayListMultimap.create();

        ArrayListMultimap<String, AnnotatedPluginDocument> tracesByWorkflow = ArrayListMultimap.create();
        for (AnnotatedPluginDocument trace : chromatogramDocuments) {
            Object workflow = trace.getFieldValue(BiocodeUtilities.WORKFLOW_NAME_FIELD);
            if(workflow != null) {
                tracesByWorkflow.put(workflow.toString(), trace);
            }
        }

        for (AnnotatedPluginDocument annotatedDocument : annotatedDocuments) {
            Set<Object> validPlates = new HashSet<Object>(BiocodeUtilities.getPlatesAnnotatedOnDocument(annotatedDocument));

            Object workflowName = annotatedDocument.getFieldValue(BiocodeUtilities.WORKFLOW_NAME_FIELD);
            if(workflowName != null) {
                List<AnnotatedPluginDocument> traces = tracesByWorkflow.get(workflowName.toString());
                for (AnnotatedPluginDocument trace : traces) {
                    Object plateName = trace.getFieldValue(BiocodeUtilities.SEQUENCING_PLATE_FIELD);
                    if(validPlates.contains(plateName)) {
                        refToTraces.put(annotatedDocument, trace);
                    }
                }
            }
        }


        CompositeProgressListener compositeProgress = new CompositeProgressListener(progress, refToTraces.keySet().size());
        for (Map.Entry<AnnotatedPluginDocument, Collection<AnnotatedPluginDocument>> entry : refToTraces.asMap().entrySet()) {
            AnnotatedPluginDocument reference = entry.getKey();
            compositeProgress.beginSubtask("Re-assembling " + reference.getName() + "...");
            if(compositeProgress.isCanceled()) {
                throw new DocumentOperationException.Canceled();
            }
            AnnotatedPluginDocument assembly = assembleTracesToRef(assemblyOperation, reference, entry.getValue(), compositeProgress.EMPTY);
            if(assembly != null) {
                assembly.setName(reference.getName());
                for (DocumentField field : reference.getDisplayableFields()) {
                    if(!AnnotateUtilities.FIELDS_TO_NOT_COPY.contains(field.getCode()) && assembly.getFieldValue(field) == null) {
                        assembly.setFieldValue(field, reference.getFieldValue(field));
                    }
                }
                operationCallback.addDocument(assembly, false, ProgressListener.EMPTY);
            }
        }

    }

    private AnnotatedPluginDocument assembleTracesToRef(DocumentOperation assemblyOperation, AnnotatedPluginDocument reference, Collection<AnnotatedPluginDocument> traces, ProgressListener progress) throws DocumentOperationException {
        List<AnnotatedPluginDocument> input = new ArrayList<AnnotatedPluginDocument>();
        input.add(reference);
        input.addAll(traces);
        Options assemblyOptions = assemblyOperation.getOptions(input);
        assemblyOptions.setValue("data.useReferenceSequence", true);
        assemblyOptions.setValue("data.referenceSequenceName", new DocumentSelectionOption.FolderOrDocuments(reference));
        assemblyOptions.setValue("data.groupAssemblies", false);
        assemblyOptions.setValue("data.assembleListsSeparately", false);
        assemblyOptions.setValue("method.algorithm.referenceAssembly", "Geneious.reference");
        assemblyOptions.setValue("trimOptions.method", "noTrim");
        assemblyOptions.setValue("results.saveReport", false);
        assemblyOptions.setValue("results.resultsInSubfolder", false);
        assemblyOptions.setValue("results.generateContigs", true);
        assemblyOptions.setValue("results.generateConsensusSequencesReference", false);
        assemblyOptions.setValue("results.saveUnusedReads", false);

        List<AnnotatedPluginDocument> annotatedPluginDocuments = assemblyOperation.performOperation(input, progress, assemblyOptions);
        if(annotatedPluginDocuments.isEmpty()) {
            return null;
        } else {
            assert annotatedPluginDocuments.size() == 1;
            return annotatedPluginDocuments.get(0);
        }
    }

    /**
     *
     * @param platesAndWorkflowsToRetreive A map from plate name -> list of workflows to retrieve from that plate.  If the list of workflows is null then all wells will be retrieved
     * @param progressListener A {@link ProgressListener} to report progress to
     * @return A map with a {@link CycleSequencingReaction} -> {@link FimsData} entry for each well specified in the platesAndWorkflowsToRetreive
     *
     * @throws DatabaseServiceException if there is a problem communicating with the LIMS
     * @throws DocumentOperationException if there were any other problems downloading traces
     */
    static Map<CycleSequencingReaction, FimsData> getReactionsForPlateNames(Map<String, List<String>> platesAndWorkflowsToRetreive, ProgressListener progressListener) throws DatabaseServiceException, DocumentOperationException {
        final Map<CycleSequencingReaction, FimsData> fimsDataForReactions = new HashMap<CycleSequencingReaction, FimsData>();
        CompositeProgressListener reactionsProgress = new CompositeProgressListener(progressListener, platesAndWorkflowsToRetreive.size());
        for (Map.Entry<String, List<String>> entry : platesAndWorkflowsToRetreive.entrySet()) {
            String plateName = entry.getKey();
            reactionsProgress.beginSubtask(plateName);
            if (reactionsProgress.isCanceled()) return null;
            Plate plate = BiocodeService.getInstance().getPlateForName(plateName);
            if (plate == null) {
                throw new DocumentOperationException("No plate found with name \"" + plateName + "\"");
            }
            if (plate.getReactionType() != Reaction.Type.CycleSequencing) {
                throw new DocumentOperationException("Plate \"" + plateName + "\" is not a sequencing plate");
            }

            List<String> workflowNames = entry.getValue();
            if(workflowNames == null) {
                workflowNames = new ArrayList<String>();
                for (Reaction reaction : plate.getReactions()) {
                    if (!reaction.isEmpty() && reaction.getWorkflow() != null) {
                        workflowNames.add(reaction.getWorkflow().getName());
                    }
                }
            }

            List<WorkflowDocument> workflows;
            try {
                workflows = BiocodeService.getInstance().getWorkflowDocumentsForNames(workflowNames);
            } catch (DatabaseServiceException e) {
                throw new DocumentOperationException(e.getMessage(), e);
            }

            for (Reaction reaction : plate.getReactions()) {
                if (reactionsProgress.isCanceled()) return null;
                if (!reaction.isEmpty()) {
                    FimsData fimsData = null;
                    BiocodeUtilities.Well well = Plate.getWell(reaction.getPosition(), plate.getPlateSize());
                    if (reaction.getWorkflow() != null) {
                        if(!workflowNames.contains(reaction.getWorkflow().getName())) {
                            continue;
                        }
                        WorkflowDocument workflow = findWorkflow(workflows, reaction.getWorkflow().getId());
                        if (workflow != null) {
                            fimsData = new FimsData(workflow, plate.getName(), well);
                        }
                    }
                    FimsSample fimsSample = reaction.getFimsSample();
                    if(fimsData == null && fimsSample != null) {
                        fimsData = new FimsData(fimsSample, plate.getName(), well);
                    }
                    fimsDataForReactions.put((CycleSequencingReaction) reaction, fimsData);
                }
            }
        }
        return fimsDataForReactions;
    }

    private static WorkflowDocument findWorkflow(List<WorkflowDocument> workflows, int id) {
        for(WorkflowDocument document : workflows) {
            if(document.getWorkflow().getId() == id) {
                return document;
            }
        }
        return null;
    }

    @Override
    public double getFractionOfTimeToSaveResults() {
        return 0.3;
    }
}
