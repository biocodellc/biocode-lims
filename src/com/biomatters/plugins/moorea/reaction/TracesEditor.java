package com.biomatters.plugins.moorea.reaction;

import com.biomatters.geneious.publicapi.documents.sequence.SequenceDocument;
import com.biomatters.geneious.publicapi.documents.sequence.SequenceListDocument;
import com.biomatters.geneious.publicapi.documents.sequence.DefaultSequenceListDocument;
import com.biomatters.geneious.publicapi.documents.sequence.NucleotideSequenceDocument;
import com.biomatters.geneious.publicapi.documents.AnnotatedPluginDocument;
import com.biomatters.geneious.publicapi.documents.DocumentUtilities;
import com.biomatters.geneious.publicapi.documents.PluginDocument;
import com.biomatters.geneious.publicapi.plugin.*;
import com.biomatters.geneious.publicapi.components.Dialogs;
import com.biomatters.geneious.publicapi.components.ProgressFrame;
import com.biomatters.geneious.publicapi.implementations.sequence.DefaultNucleotideSequence;
import com.biomatters.geneious.publicapi.utilities.ThreadUtilities;

import javax.swing.*;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;
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
    private List<NucleotideSequenceDocument> sequences;
    private DocumentViewerFactory factory;
    private JPanel holder = new JPanel(new BorderLayout());
    private JPanel sequenceHolder = new JPanel(new GridLayout(1,1));
    DocumentViewerMessageHandler messageHandler;
    private SequenceSelection sequenceSelection;
    private GeneiousAction addSequenceAction, removeSequencesAction;
    private DocumentViewer documentViewer;

    public TracesEditor(List<NucleotideSequenceDocument> sequencesa) {
        sequenceHolder.setPreferredSize(new Dimension(640,480));
        addSequenceAction = new GeneiousAction("Add sequence") {
            public void actionPerformed(ActionEvent e) {
                JFileChooser chooser = new JFileChooser(System.getProperty("user.dir"));
                chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
                chooser.setMultiSelectionEnabled(true);
                if(chooser.showOpenDialog(holder) == JFileChooser.APPROVE_OPTION) {
                    final File[] sequenceFiles = chooser.getSelectedFiles();
                    final ProgressFrame progress = new ProgressFrame("Importing", "Importing Documents", 500, false);
                    Runnable runnable = new Runnable() {
                        public void run() {
                            try {
                                List<AnnotatedPluginDocument> pluginDocuments = new ArrayList<AnnotatedPluginDocument>();
                                for (int i = 0; i < sequenceFiles.length && !progress.isCanceled(); i++) {
                                    File f = sequenceFiles[i];
                                    List<AnnotatedPluginDocument> docs = PluginUtilities.importDocuments(f, ProgressListener.EMPTY);
                                    pluginDocuments.addAll(docs);
                                }
                                if (!progress.isCanceled()) {
                                    updatePanel(pluginDocuments);
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
                for(SequenceDocument selectedDoc : sequenceSelection.getSelectedSequences()) {
                    for(NucleotideSequenceDocument doc : sequences) {
                        if(doc.getName().equals(selectedDoc.getName()) && doc.getSequenceString().equalsIgnoreCase(selectedDoc.getSequenceString())) {
                            sequences.remove(doc);
                            break;
                        }
                    }
                }
                updateViewer(sequences);
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


        this.sequences = sequencesa;
        boolean faked = false;
        DefaultSequenceListDocument sequenceList;
        if(sequences == null || sequences.size() == 0) {
            faked = true;
            sequenceList = DefaultSequenceListDocument.forNucleotideSequences(Arrays.asList((NucleotideSequenceDocument)new DefaultNucleotideSequence("test", "atgc")));
        }
        else {
            sequenceList = DefaultSequenceListDocument.forNucleotideSequences(sequences);
        }
        factory = getViewerFactory(sequenceList);
        if(factory == null) {
            throw new RuntimeException("Could not find the sequence viewer!");
        }
        if(!faked) {
            updateViewer(sequences);
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
        updateToolbar();


        return Dialogs.showOkCancelDialog(holder, "Add Traces", owner, Dialogs.DialogIcon.NO_ICON);
    }


    private void updatePanel(final List<AnnotatedPluginDocument> pluginDocuments){
        Runnable runnable = new Runnable() {
            public void run() {
                List<NucleotideSequenceDocument> nucleotideDocuments = new ArrayList<NucleotideSequenceDocument>();
                if(pluginDocuments == null || pluginDocuments.size() == 0) {
                    Dialogs.showMessageDialog("No documents were imported!");
                    return;
                }
                for(AnnotatedPluginDocument doc : pluginDocuments) {
                    try {
                        if(SequenceListDocument.class.isAssignableFrom(doc.getDocumentClass())) {
                            nucleotideDocuments.addAll(((SequenceListDocument)doc.getDocument()).getNucleotideSequences());
                        }
                        else if(NucleotideSequenceDocument.class.isAssignableFrom(doc.getDocumentClass())) {
                                nucleotideDocuments.add((NucleotideSequenceDocument)doc.getDocument());
                        }
                        else {
                            Dialogs.showMessageDialog("You can only import nucleotide sequences.  The document "+doc.getName()+" was not a nucleotide sequence.");
                            return;
                        }
                    } catch (DocumentOperationException e1) {
                        Dialogs.showMessageDialog(e1.getMessage());
                        return;
                    }
                }
                if(sequences != null) {
                    nucleotideDocuments.addAll(sequences);
                    sequences = nucleotideDocuments;
                }
                updateViewer(nucleotideDocuments);
            }
        };
        ThreadUtilities.invokeNowOrLater(runnable);
    }

    private void updateViewer(List<NucleotideSequenceDocument> nucleotideDocuments) {

        sequenceHolder.removeAll();
        sequenceSelection = null;
        if(nucleotideDocuments != null && nucleotideDocuments.size() > 0) {
            if(documentViewer != null) {
                NoLongerViewedListener listener = documentViewer.getNoLongerViewedListener();
                if(listener != null) {
                    listener.noLongerViewed(false);
                }
            }
            documentViewer = createViewer(DefaultSequenceListDocument.forNucleotideSequences(nucleotideDocuments), factory);
            documentViewer.setOutgoingMessageHandler(messageHandler);
            sequenceHolder.add(documentViewer.getComponent(), BorderLayout.CENTER);
        }
        holder.validate();
        holder.repaint();
        updateToolbar();
    }

    private void updateToolbar() {
        removeSequencesAction.setEnabled(sequenceSelection != null && sequenceSelection.getSelectedSequenceCount() > 0);
    }

    public List<NucleotideSequenceDocument> getSequences() {
        return sequences;
    }

    public void setSequences(List<NucleotideSequenceDocument> sequences) {
        this.sequences = sequences;
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
