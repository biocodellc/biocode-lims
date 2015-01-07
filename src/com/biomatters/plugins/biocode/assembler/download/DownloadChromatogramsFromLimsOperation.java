package com.biomatters.plugins.biocode.assembler.download;

import com.biomatters.geneious.publicapi.components.Dialogs;
import com.biomatters.geneious.publicapi.databaseservice.DatabaseServiceException;
import com.biomatters.geneious.publicapi.documents.AnnotatedPluginDocument;
import com.biomatters.geneious.publicapi.documents.DocumentUtilities;
import com.biomatters.geneious.publicapi.documents.PluginDocument;
import com.biomatters.geneious.publicapi.documents.sequence.NucleotideSequenceDocument;
import com.biomatters.geneious.publicapi.plugin.*;
import com.biomatters.plugins.biocode.BiocodePlugin;
import com.biomatters.plugins.biocode.BiocodeUtilities;
import com.biomatters.plugins.biocode.assembler.SetReadDirectionOperation;
import com.biomatters.plugins.biocode.assembler.annotate.AnnotateUtilities;
import com.biomatters.plugins.biocode.assembler.annotate.FimsData;
import com.biomatters.plugins.biocode.assembler.annotate.FimsDataGetter;
import com.biomatters.plugins.biocode.labbench.*;
import com.biomatters.plugins.biocode.labbench.plates.Plate;
import com.biomatters.plugins.biocode.labbench.reaction.CycleSequencingOptions;
import com.biomatters.plugins.biocode.labbench.reaction.CycleSequencingReaction;
import com.biomatters.plugins.biocode.labbench.reaction.Reaction;
import com.biomatters.plugins.biocode.labbench.reaction.Trace;
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
    public List<AnnotatedPluginDocument> performOperation(AnnotatedPluginDocument[] annotatedDocuments, ProgressListener progressListener, Options options) throws DocumentOperationException {
        if(!BiocodeService.getInstance().isLoggedIn()) {
            throw new DocumentOperationException(BiocodeUtilities.NOT_CONNECTED_ERROR_MESSAGE);
        }
        BiocodeCallback callback = new BiocodeCallback(progressListener);
        BiocodeService.getInstance().registerCallback(callback);
        try {
            CompositeProgressListener progress = new CompositeProgressListener(callback, 0.2, 0.5, 0.2);
            progress.setIndeterminateProgress();
            progress.beginSubtask("Getting reactions");
            List<String> plateNames = ((DownloadChromatogramsFromLimsOptions)options).getPlateNames();
            List<CycleSequencingReaction> reactions = new ArrayList<CycleSequencingReaction>();
            final Map<CycleSequencingReaction, FimsData> fimsDataForReactions = new HashMap<CycleSequencingReaction, FimsData>();
            CompositeProgressListener reactionsProgress = new CompositeProgressListener(progress, plateNames.size());
            for (String plateName : plateNames) {
                reactionsProgress.beginSubtask(plateName);
                if (reactionsProgress.isCanceled()) return null;
                Plate plate = BiocodeService.getInstance().getPlateForName(plateName);
                if (plate == null) {
                    throw new DocumentOperationException("No plate found with name \"" + plateName + "\"");
                }
                if (plate.getReactionType() != Reaction.Type.CycleSequencing) {
                    throw new DocumentOperationException("Plate \"" + plateName + "\" is not a sequencing plate");
                }

                List<String> workflowNames = new ArrayList<String>();
                for (Reaction reaction : plate.getReactions()) {
                    if(!reaction.isEmpty() && reaction.getWorkflow() != null) {
                        workflowNames.add(reaction.getWorkflow().getName());
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
                        reactions.add((CycleSequencingReaction) reaction);
                        BiocodeUtilities.Well well = Plate.getWell(reaction.getPosition(), plate.getPlateSize());
                        if (reaction.getWorkflow() != null) {
                            WorkflowDocument workflow = findWorkflow(workflows, reaction.getWorkflow().getId());
                            if (workflow != null) {
                                fimsDataForReactions.put((CycleSequencingReaction) reaction, new FimsData(workflow, plate.getName(), well));
                                continue;
                            }
                        }

                        fimsDataForReactions.put((CycleSequencingReaction)reaction, new FimsData(reaction.getFimsSample(), plate.getName(), well));
                    }
                }
            }
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
                if (tracesProgress.isCanceled()) return null;
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
                        traceDocument.setFieldValue(SetReadDirectionOperation.IS_FORWARD_FIELD, isForward);
                        fimsData.put(traceDocument, fimsDataForReactions.get(reaction));
                        chromatogramDocuments.add(traceDocument);
                    }
                }
                reaction.purgeChromats();
            }
            if (progress.isCanceled()) return null;
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
                        return null;
                    }
                }
            }
            if (progress.isCanceled()) return null;
            return chromatogramDocuments;
        } catch (DatabaseServiceException e) {
            e.printStackTrace();
            throw new DocumentOperationException("Could not retrieve active LIMS connection: " + e.getMessage(), e);
        }
        finally {
            BiocodeService.getInstance().unregisterCallback(callback);
        }
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
