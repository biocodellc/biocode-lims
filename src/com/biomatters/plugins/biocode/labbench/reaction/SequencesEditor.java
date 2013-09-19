package com.biomatters.plugins.biocode.labbench.reaction;

import com.biomatters.geneious.publicapi.components.Dialogs;
import com.biomatters.geneious.publicapi.components.ProgressFrame;
import com.biomatters.geneious.publicapi.databaseservice.DatabaseServiceException;
import com.biomatters.geneious.publicapi.databaseservice.WritableDatabaseService;
import com.biomatters.geneious.publicapi.documents.AnnotatedPluginDocument;
import com.biomatters.geneious.publicapi.documents.DocumentUtilities;
import com.biomatters.geneious.publicapi.documents.sequence.DefaultSequenceListDocument;
import com.biomatters.geneious.publicapi.documents.sequence.NucleotideSequenceDocument;
import com.biomatters.geneious.publicapi.plugin.*;
import com.biomatters.geneious.publicapi.utilities.ThreadUtilities;
import jebl.util.ProgressListener;
import org.jdom.Element;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.prefs.Preferences;

/**
 * @author Steven Stones-Havas
 * @version $Id$
 *          <p/>
 *          Created on 9/07/2009 10:59:03 PM
 */
public abstract class SequencesEditor<T> {
    private List<Trace> traces;
    private List<Trace> deletedTraces;
    private DocumentViewerFactory factory;
    Preferences preferences = Preferences.userNodeForPackage(getClass());
    private JPanel holder = new JPanel(new BorderLayout());
    private JPanel sequenceHolder = new JPanel(new GridLayout(1,1));
    DocumentViewerMessageHandler messageHandler;
    private SequenceSelection sequenceSelection;
    private GeneiousAction addSequenceAction, removeSequencesAction, importSequencesAction;
    private DocumentViewer documentViewer;
    private String name;

    public SequencesEditor(final List<Trace> tracesa, String reactionName) {
        this.name = reactionName;
        sequenceHolder.setPreferredSize(new Dimension(640,480));
        addSequenceAction = new GeneiousAction("Add sequence(s)") {
            public void actionPerformed(ActionEvent e) {

                addSequences();
            }
        };

        removeSequencesAction = new GeneiousAction("Remove sequence(s)") {
            public void actionPerformed(ActionEvent e) {
                List<Trace> removed = removeSequences();
                traces.removeAll(removed);
                updateViewer();
            }
        };

        importSequencesAction = new GeneiousAction("Import sequence(s) into Geneious"){
            public void actionPerformed(ActionEvent e) {
                importSequences();
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
        this.deletedTraces = new ArrayList<Trace>();
        if(traces != null && traces.size() > 0) {
            updateViewer();
        }
    }

    public List<Trace> getDeletedTraces() {
        return deletedTraces;
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

    private void updateViewer() {

        Runnable runnable = new Runnable() {
            public void run() {
                sequenceHolder.removeAll();
                sequenceSelection = null;
                if(traces != null && traces.size() > 0) {
                    List<NucleotideSequenceDocument> sequences = createSequences(traces);
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

    static void showMessageDialog(final String message) {
        Runnable r = new Runnable(){
            public void run() {
                Dialogs.showMessageDialog(message);
            }
        };
        ThreadUtilities.invokeNowOrLater(r);
    }

    SequenceSelection getCurrentSelection() {
        return sequenceSelection;
    }

    JComponent getComponent() {
        return holder;
    }

    void addTrace(Trace trace) {
        traces.add(trace);
    }

    abstract List<NucleotideSequenceDocument> createSequences(List<Trace> traces);

    abstract void importSequences();
    abstract void addSequences();
    abstract List<Trace> removeSequences();
}
