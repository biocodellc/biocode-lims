package com.biomatters.plugins.biocode.labbench.reaction;

import com.biomatters.geneious.publicapi.documents.sequence.SequenceDocument;
import com.biomatters.geneious.publicapi.documents.sequence.DefaultSequenceListDocument;
import com.biomatters.geneious.publicapi.documents.sequence.NucleotideSequenceDocument;
import com.biomatters.geneious.publicapi.documents.AnnotatedPluginDocument;
import com.biomatters.geneious.publicapi.documents.DocumentUtilities;
import com.biomatters.geneious.publicapi.plugin.*;
import com.biomatters.geneious.publicapi.components.Dialogs;
import com.biomatters.geneious.publicapi.components.ProgressFrame;
import com.biomatters.geneious.publicapi.utilities.ThreadUtilities;
import com.biomatters.geneious.publicapi.databaseservice.WritableDatabaseService;
import com.biomatters.geneious.publicapi.databaseservice.DatabaseServiceException;

import javax.swing.*;
import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.prefs.Preferences;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.IOException;

import jebl.util.ProgressListener;
import org.jdom.Element;

/**
 * @author Steven Stones-Havas
 * @version $Id$
 *          <p/>
 *          Created on 9/07/2009 10:59:03 PM
 */
public class TracesEditor {
    private List<Trace> traces;
    private DocumentViewerFactory factory;
    Preferences preferences = Preferences.userNodeForPackage(getClass());
    private JPanel holder = new JPanel(new BorderLayout());
    private JPanel sequenceHolder = new JPanel(new GridLayout(1,1));
    DocumentViewerMessageHandler messageHandler;
    private SequenceSelection sequenceSelection;
    private GeneiousAction addSequenceAction, removeSequencesAction, importSequencesAction;
    private DocumentViewer documentViewer;
    private String name;

    public TracesEditor(final List<Trace> tracesa, String reactionName) {
        this.name = reactionName;
        sequenceHolder.setPreferredSize(new Dimension(640,480));
        addSequenceAction = new GeneiousAction("Add sequence(s)") {
            public void actionPerformed(ActionEvent e) {
                JFileChooser chooser = new JFileChooser(preferences.get("addTraces_defaultFolder", System.getProperty("user.dir")));
                chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
                chooser.setMultiSelectionEnabled(true);
                if(chooser.showOpenDialog(holder) == JFileChooser.APPROVE_OPTION) {
                    preferences.put("addTraces_defaultFolder", chooser.getSelectedFile().getParent());
                    final File[] sequenceFiles = chooser.getSelectedFiles();
                    final ProgressFrame progress = new ProgressFrame("Importing", "Importing Documents", 500, false);
                    Runnable runnable = new Runnable() {
                        public void run() {
                            try {
                                List<AnnotatedPluginDocument> pluginDocuments = ReactionUtilities.importDocuments(sequenceFiles, progress);
                                for(File f : sequenceFiles) {
                                    traces.add(new Trace(ReactionUtilities.loadFileIntoMemory(f)));
                                }
                                if (!progress.isCanceled()) {
                                    updateViewer(traces);
                                }
                            } catch (IOException e1) {
                                showMessageDialog("Could not import your documents: " + e1.getMessage());
                            } catch (DocumentImportException e1) {
                                showMessageDialog("Could not import your documents: " + e1.getMessage());
                            }
                        }
                    };
                    new Thread(runnable).start();
                }
            }
        };

        removeSequencesAction = new GeneiousAction("Remove sequence(s)") {
            public void actionPerformed(ActionEvent e) {
                int currentIndex = 0;
                for(SequenceSelection.SequenceIndex selectedIndex : sequenceSelection.getSelectedSequenceIndices()) {
                    for (int i = 0; i < traces.size(); i++) {
                        Trace trace = traces.get(i);
                        boolean removed = false;
                        for(NucleotideSequenceDocument doc : trace.getSequences()){
                            if (currentIndex == selectedIndex.getSequenceIndex()) {
                                traces.remove(i);
                                removed = true;
                                currentIndex++;
                                break;
                            }
                            currentIndex++;
                        }
                        if(removed) {
                            break;
                        }
                    }
                }
                updateViewer(traces);
            }
        };

        importSequencesAction = new GeneiousAction("Import sequence(s) into Geneious"){
            public void actionPerformed(ActionEvent e) {
                WritableDatabaseService selectedFolder = ServiceUtilities.getUserSelectedFolder(null);
                if(selectedFolder != null){
                    int currentIndex = 0;
                    try {
                        for(SequenceSelection.SequenceIndex selectedIndex : sequenceSelection.getSelectedSequenceIndices()) {
                            for (int i = 0; i < traces.size(); i++) {
                                Trace trace = traces.get(i);
                                for(NucleotideSequenceDocument doc : trace.getSequences()){
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
        };

        messageHandler = new DocumentViewerMessageHandler(){
            public boolean handleMessage(Element message, String senderName, boolean focusReciever) {
                return false;
            }

            @Override
            public boolean setSequenceSelection(SequenceSelection newSelection, String senderName) {
                sequenceSelection = newSelection;
                updateToolbar();
                return true;
            }
        };


        this.traces = new ArrayList<Trace>(tracesa);
        if(traces != null && traces.size() > 0) {
            updateViewer(traces);
        }
    }

    public static DocumentViewerFactory getViewerFactory(DefaultSequenceListDocument sequenceList) {
        DocumentViewerFactory factory = null;
        AnnotatedPluginDocument annotatedDocument = DocumentUtilities.createAnnotatedPluginDocument(sequenceList);
        List<DocumentViewerFactory> documentViewerFactories = PluginUtilities.getDocumentViewerFactories(annotatedDocument);
        for(DocumentViewerFactory fac : documentViewerFactories) {
            String className = fac.getClass().getCanonicalName();
            System.out.println(className);
            if(className != null && className.equals("com.biomatters.plugins.sequenceviewer.SequenceViewerPlugin.SequenceViewerFactory")) {
                if(fac.createViewer(new AnnotatedPluginDocument[] {annotatedDocument}) != null) {
                    factory = fac;
                    break;
                }
            }
        }
        return factory;
    }

    public boolean showDialog(Component owner) {
        holder.add(sequenceHolder, BorderLayout.CENTER);

        JToolBar toolbar = new JToolBar();
        toolbar.setFloatable(false);
        holder.add(toolbar, BorderLayout.NORTH);
        toolbar.add(addSequenceAction);
        toolbar.add(removeSequencesAction);
        toolbar.add(importSequencesAction);
        updateToolbar();


        return Dialogs.showOkCancelDialog(holder, "Add Traces", owner, Dialogs.DialogIcon.NO_ICON);
    }


//    private void updatePanel(final List<AnnotatedPluginDocument> pluginDocuments){
//        Runnable runnable = new Runnable() {
//            public void run() {
//                List<NucleotideSequenceDocument> sequences = new ArrayList<NucleotideSequenceDocument>();
//                for(Trace trace : traces) {
//                    if(trace.getSequences() != null) {
//                        sequences.addAll(trace.getSequences());
//                    }
//                }
//                List<NucleotideSequenceDocument> nucleotideDocuments = null;
//                try {
//                    nucleotideDocuments = ReactionUtilities.getSequencesFromAnnotatedPluginDocuments(pluginDocuments);
//                } catch (IllegalArgumentException e) {
//                    Dialogs.showMessageDialog(e.getMessage());
//                }
//                if(nucleotideDocuments != null) {
//                    if(sequences != null) {
//                        nucleotideDocuments.addAll(sequences);
//                    }
//                    sequences = nucleotideDocuments;
//                    updateViewer(nucleotideDocuments);
//                }
//            }
//        };
//        ThreadUtilities.invokeNowOrLater(runnable);
//    }

    private void updateViewer(final List<Trace> traces) {

        Runnable runnable = new Runnable() {
            public void run() {
                sequenceHolder.removeAll();
                sequenceSelection = null;
                if(traces != null && traces.size() > 0) {
                    List<NucleotideSequenceDocument> sequences = ReactionUtilities.getAllSequences(traces);
                    if(factory == null) {
                        factory = getViewerFactory(DefaultSequenceListDocument.forNucleotideSequences(sequences));
                        if(factory == null) {
                            throw new RuntimeException("Could not find the sequence viewer!");
                        }
                    }
                    if(documentViewer != null) {
                        NoLongerViewedListener listener = documentViewer.getNoLongerViewedListener();
                        if(listener != null) {
                            listener.noLongerViewed(false);
                        }
                    }
                    documentViewer = createViewer(DefaultSequenceListDocument.forNucleotideSequences(sequences), factory);
                    documentViewer.setEditingEnabled(false, "You cannot edit raw reads.  If you wish to change the sequence, please remove it and attach a replacement.");
                    documentViewer.setInSplitLayout(new DocumentViewer.ViewerLocation(false, true,""));
                    documentViewer.setOutgoingMessageHandler(messageHandler);
                    sequenceHolder.add(documentViewer.getComponent(), BorderLayout.CENTER);
                }
                holder.validate();
                holder.repaint();
                updateToolbar();
            }
        };
        ThreadUtilities.invokeNowOrLater(runnable);
    }

    private void updateToolbar() {
        boolean enabled = sequenceSelection != null && sequenceSelection.getSelectedSequenceCount() > 0;
        removeSequencesAction.setEnabled(enabled);
        importSequencesAction.setEnabled(enabled);
    }


    public List<Trace> getTraces() {
        return traces;
    }

    private static DocumentViewer createViewer(DefaultSequenceListDocument sequenceList, DocumentViewerFactory factory) {
        return factory.createViewer(new AnnotatedPluginDocument[] {DocumentUtilities.createAnnotatedPluginDocument(sequenceList)});
    }

    private static void showMessageDialog(final String message) {
        Runnable r = new Runnable(){
            public void run() {
                Dialogs.showMessageDialog(message);
            }
        };
        ThreadUtilities.invokeNowOrLater(r);
    }


}
