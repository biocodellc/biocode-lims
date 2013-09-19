package com.biomatters.plugins.biocode.labbench.reaction;

import com.biomatters.geneious.publicapi.components.Dialogs;
import com.biomatters.geneious.publicapi.components.ProgressFrame;
import com.biomatters.geneious.publicapi.databaseservice.DatabaseServiceException;
import com.biomatters.geneious.publicapi.databaseservice.WritableDatabaseService;
import com.biomatters.geneious.publicapi.documents.DocumentUtilities;
import com.biomatters.geneious.publicapi.documents.sequence.NucleotideSequenceDocument;
import com.biomatters.geneious.publicapi.plugin.DocumentImportException;
import com.biomatters.geneious.publicapi.plugin.SequenceSelection;
import com.biomatters.geneious.publicapi.plugin.ServiceUtilities;
import jebl.util.ProgressListener;

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
    public TracesEditor(final List<Trace> tracesa, String reactionName) {
        super(tracesa, reactionName);
    }

    List<NucleotideSequenceDocument> createSequences(List<Trace> traces) {
        return ReactionUtilities.getAllSequences(traces);
    }

    void importSequences() {
        WritableDatabaseService selectedFolder = ServiceUtilities.getUserSelectedFolder(null);
        if(selectedFolder != null){
            int currentIndex = 0;
            try {
                for(SequenceSelection.SequenceIndex selectedIndex : getCurrentSelection().getSelectedSequenceIndices()) {
                    for (Trace trace : getTraces()) {
                        for (NucleotideSequenceDocument doc : trace.getSequences()) {
                            if (currentIndex == selectedIndex.getSequenceIndex()) {
                                selectedFolder.addDocumentCopy(DocumentUtilities.createAnnotatedPluginDocument(doc), ProgressListener.EMPTY).setUnread(true);
                            }
                            currentIndex++;
                        }
                    }
                }
            } catch (DatabaseServiceException e1) {
                Dialogs.showMessageDialog(e1.getMessage());
            }
        }
    }

    List<Trace> removeSequences() {
        List<Trace> removedTraces = new ArrayList<Trace>();
        int currentIndex = 0;
        for(SequenceSelection.SequenceIndex selectedIndex : getCurrentSelection().getSelectedSequenceIndices()) {
            List<Trace> traces = getTraces();
            for (Trace trace : traces) {
                boolean removed = false;
                for (NucleotideSequenceDocument doc : trace.getSequences()) {
                    if (currentIndex == selectedIndex.getSequenceIndex()) {
                        if (trace.getId() >= 0) {
                            removedTraces.add(trace);
                        }
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

    void addSequences() {
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
