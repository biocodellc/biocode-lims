package com.biomatters.plugins.biocode.labbench.reaction;

import com.biomatters.geneious.publicapi.components.Dialogs;
import com.biomatters.geneious.publicapi.documents.AnnotatedPluginDocument;
import com.biomatters.geneious.publicapi.documents.DocumentUtilities;
import com.biomatters.geneious.publicapi.documents.sequence.DefaultSequenceListDocument;
import com.biomatters.geneious.publicapi.documents.sequence.NucleotideSequenceDocument;
import com.biomatters.geneious.publicapi.plugin.*;
import com.biomatters.geneious.publicapi.utilities.ThreadUtilities;
import org.jdom.Element;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
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
    private List<T> sourceObjects;
    private List<T> deletedObjects;
    private DocumentViewerFactory factory;
    Preferences preferences = Preferences.userNodeForPackage(getClass());
    private JPanel holder = new JPanel(new BorderLayout());
    private JPanel sequenceHolder = new JPanel(new GridLayout(1,1));
    DocumentViewerMessageHandler messageHandler;
    private SequenceSelection sequenceSelection;
    private GeneiousAction addSequenceAction, removeSequencesAction, importSequencesAction;
    private DocumentViewer documentViewer;
    private String name;

    public SequencesEditor(final List<T> traces, String reactionName) {
        this.name = reactionName;
        sequenceHolder.setPreferredSize(new Dimension(640,480));
        addSequenceAction = new GeneiousAction("Add sequence(s)") {
            public void actionPerformed(ActionEvent e) {

                addSequences();
            }
        };

        removeSequencesAction = new GeneiousAction("Remove sequence(s)") {
            public void actionPerformed(ActionEvent e) {
                List<T> removed = removeSequences();
                SequencesEditor.this.sourceObjects.removeAll(removed);
                SequencesEditor.this.deletedObjects.addAll(removed);
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


        this.sourceObjects = new ArrayList<T>(traces);
        this.deletedObjects = new ArrayList<T>();
        if(this.sourceObjects != null && this.sourceObjects.size() > 0) {
            updateViewer();
        }
    }

    public List<T> getDeletedObjects() {
        return deletedObjects;
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

        if(canEdit()) {
            JToolBar toolbar = new JToolBar();
            toolbar.setFloatable(false);
            holder.add(toolbar, BorderLayout.NORTH);
            toolbar.add(addSequenceAction);
            toolbar.add(removeSequencesAction);
            toolbar.add(importSequencesAction);
            updateToolbar();
        }

        return Dialogs.showOkCancelDialog(holder, getDialogName(), owner, Dialogs.DialogIcon.NO_ICON);
    }

    String getDialogName() {
        return "Add Traces";
    }

    private void updateViewer() {

        Runnable runnable = new Runnable() {
            public void run() {
                sequenceHolder.removeAll();
                sequenceSelection = null;
                if(sourceObjects != null && sourceObjects.size() > 0) {
                    List<NucleotideSequenceDocument> sequences = createSequences(sourceObjects);
                    if(sequences.isEmpty()) {
                        Dialogs.showMessageDialog("No sequences to display");
                        return;
                    }

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
        if(canEdit()) {
            boolean enabled = sequenceSelection != null && sequenceSelection.getSelectedSequenceCount() > 0;
            removeSequencesAction.setEnabled(enabled);
            importSequencesAction.setEnabled(enabled);
        }
    }

    public List<T> getSourceObjects() {
        return sourceObjects;
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

    void addTrace(T trace) {
        sourceObjects.add(trace);
    }

    abstract List<NucleotideSequenceDocument> createSequences(List<T> traces);

    /**
     * The sequences can be added/removed
     *
     * @return true if the sequences can be added/removed.  If true then {@link #addSequences()} and {@link #removeSequences()}
     * must be implemented.
     */
    abstract boolean canEdit();
    abstract void importSequences();
    abstract void addSequences();
    abstract List<T> removeSequences();
}
