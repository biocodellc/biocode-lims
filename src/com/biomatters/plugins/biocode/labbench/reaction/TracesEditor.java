package com.biomatters.plugins.biocode.labbench.reaction;

import com.biomatters.geneious.publicapi.components.Dialogs;
import com.biomatters.geneious.publicapi.components.ProgressFrame;
import com.biomatters.geneious.publicapi.databaseservice.DatabaseServiceException;
import com.biomatters.geneious.publicapi.databaseservice.WritableDatabaseService;
import com.biomatters.geneious.publicapi.documents.AnnotatedPluginDocument;
import com.biomatters.geneious.publicapi.documents.DocumentUtilities;
import com.biomatters.geneious.publicapi.documents.sequence.NucleotideSequenceDocument;
import com.biomatters.geneious.publicapi.plugin.DocumentImportException;
import com.biomatters.geneious.publicapi.plugin.DocumentOperationException;
import com.biomatters.geneious.publicapi.plugin.SequenceSelection;
import com.biomatters.geneious.publicapi.plugin.ServiceUtilities;
import com.biomatters.plugins.biocode.BiocodeUtilities;
import com.biomatters.plugins.biocode.assembler.annotate.AnnotateUtilities;
import com.biomatters.plugins.biocode.assembler.annotate.FimsData;
import com.biomatters.plugins.biocode.assembler.annotate.FimsDataGetter;
import com.biomatters.plugins.biocode.labbench.BiocodeService;
import com.biomatters.plugins.biocode.labbench.WorkflowDocument;
import jebl.util.ProgressListener;
import org.virion.jam.util.SimpleListener;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Matthew Cheung
 * @version $Id$
 *          <p/>
 *          Created on 19/09/13 3:28 PM
 */
public class TracesEditor extends SequencesEditor<Trace> {
    private Reaction reaction = null;

    public TracesEditor(final List<Trace> tracesa, String reactionName) {
        super(tracesa, reactionName);
    }

    public TracesEditor(final List<Trace> tracesa, String reactionName, Reaction reaction) {
        this(tracesa, reactionName);
        this.reaction = reaction;
    }

    List<NucleotideSequenceDocument> createSequences(List<Trace> traces) {
        return ReactionUtilities.getAllSequences(traces);
    }

    @Override
    boolean canEdit() {
        return true;
    }

    void importSequences() {
        WritableDatabaseService selectedFolder = ServiceUtilities.getUserSelectedFolder(null);
        if(selectedFolder != null){
            int currentIndex = 0;
            try {
                for(SequenceSelection.SequenceIndex selectedIndex : getCurrentSelection().getSelectedSequenceIndices()) {
                    for (Trace trace : getSourceObjects()) {
                        for (NucleotideSequenceDocument doc : trace.getSequences()) {
                            if (currentIndex == selectedIndex.getSequenceIndex()) {
                                AnnotatedPluginDocument annotatedPluginDocument = DocumentUtilities.createAnnotatedPluginDocument(doc);

                                if (reaction != null) {
                                    List<String> workflows = new ArrayList<String>();
                                    workflows.add(reaction.getWorkflow().getName());
                                    final List<WorkflowDocument> workflowDocuments = BiocodeService.getInstance().getWorkflowDocumentsForNames(workflows);
                                    FimsData data = null;

                                    if (workflowDocuments != null && workflowDocuments.size() > 0) {
                                        data = new FimsData(workflowDocuments.get(0), reaction.getPlateName(), new BiocodeUtilities.Well(reaction.getLocationString()));
                                    } else {
                                        data = new FimsData(reaction.getFimsSample(), reaction.getPlateName(), new BiocodeUtilities.Well(reaction.getLocationString()));
                                    }

                                    final FimsData finalData = data;
                                    FimsDataGetter fimsDataGetter = new FimsDataGetter() {
                                        public FimsData getFimsData(AnnotatedPluginDocument document) throws DocumentOperationException {
                                            return finalData;
                                        }
                                    };

                                    AnnotateUtilities.annotateFimsData(new AnnotatedPluginDocument[]{annotatedPluginDocument}, ProgressListener.EMPTY, fimsDataGetter, false);
                                }

                                selectedFolder.addDocumentCopy(annotatedPluginDocument, ProgressListener.EMPTY).setUnread(true);
                            }
                            currentIndex++;
                        }
                    }
                }
            } catch (DatabaseServiceException e1) {
                Dialogs.showMessageDialog(e1.getMessage());
            } catch (DocumentOperationException e) {
                Dialogs.showMessageDialog(e.getMessage());
            }
        }
    }

    List<Trace> removeSequences() {
        List<Trace> removedTraces = new ArrayList<Trace>();
        int currentIndex = 0;
        for(SequenceSelection.SequenceIndex selectedIndex : getCurrentSelection().getSelectedSequenceIndices()) {
            List<Trace> traces = getSourceObjects();
            for (Trace trace : traces) {
                boolean removed = false;
                for (NucleotideSequenceDocument doc : trace.getSequences()) {
                    if (currentIndex == selectedIndex.getSequenceIndex()) {
                        removedTraces.add(trace);
                        removed = true;
                        currentIndex++;
                        break;
                    }
                    currentIndex++;
                }
                if (removed) {
                    break;
                }
            }
        }
        return removedTraces;
    }

    void addSequences(final SimpleListener finishedListener) {
        JFileChooser chooser = new JFileChooser(preferences.get("addTraces_defaultFolder", System.getProperty("user.dir")));
        chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        chooser.setMultiSelectionEnabled(true);
        if(chooser.showOpenDialog(getComponent()) == JFileChooser.APPROVE_OPTION) {
            preferences.put("addTraces_defaultFolder", chooser.getSelectedFile().getParent());
            final File[] sequenceFiles = chooser.getSelectedFiles();
            @SuppressWarnings("deprecation") //using deprecated method so that api version doesn't have to be upped
            final ProgressFrame progress = new ProgressFrame("Importing", "Importing Documents", 800, true);
            Runnable runnable = new Runnable() {
                public void run() {
                    try {
                        ReactionUtilities.importDocuments(sequenceFiles, progress);
                        for(File f : sequenceFiles) {
                            addTrace(new Trace(ReactionUtilities.loadFileIntoMemory(f)));
                        }
                        if(finishedListener != null) {
                            finishedListener.objectChanged();
                        }
                    } catch (IOException e1) {
                        showMessageDialog("Could not import your documents: " + e1.getMessage());
                    } catch (DocumentImportException e1) {
                        showMessageDialog("Could not import your documents: " + e1.getMessage());
                    } finally {
                        progress.setComplete();
                    }
                }
            };
            new Thread(runnable).start();
        }
    }
}
