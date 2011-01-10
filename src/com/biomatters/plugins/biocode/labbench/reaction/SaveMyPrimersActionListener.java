package com.biomatters.plugins.biocode.labbench.reaction;

import com.biomatters.geneious.publicapi.components.Dialogs;
import com.biomatters.geneious.publicapi.documents.AnnotatedPluginDocument;
import com.biomatters.geneious.publicapi.documents.DocumentUtilities;
import com.biomatters.geneious.publicapi.documents.sequence.SequenceDocument;
import com.biomatters.geneious.publicapi.implementations.sequence.OligoSequenceDocument;
import com.biomatters.geneious.publicapi.plugin.DocumentOperationException;
import com.biomatters.geneious.publicapi.plugin.DocumentSelectionOption;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Steve
 * @version $Id$
 */
public abstract class SaveMyPrimersActionListener implements ActionListener {

    public void actionPerformed(ActionEvent e) {
        List<AnnotatedPluginDocument> primers = new ArrayList<AnnotatedPluginDocument>();
        for(DocumentSelectionOption option : getPrimerOptions()) {
            for(AnnotatedPluginDocument doc : option.getValue().getDocuments()) {
                primers.add(doc);
            }
        }

        try {
            List<AnnotatedPluginDocument> newPrimers = new ArrayList<AnnotatedPluginDocument>();
            for (AnnotatedPluginDocument origDoc : primers) {
                SequenceDocument origSeq = (SequenceDocument) origDoc.getDocument();
                OligoSequenceDocument sequence = new OligoSequenceDocument(origDoc.getName(), origDoc.getSummary(), origSeq.getSequenceString(), origDoc.getCreationDate());
                newPrimers.add(DocumentUtilities.createAnnotatedPluginDocument(sequence));
            }
            DocumentUtilities.addGeneratedDocuments(newPrimers, false);
        } catch (DocumentOperationException e1) {
            Dialogs.showMessageDialog("Failed to load primer document: " + e1.getMessage());
        }
    }

    public abstract List<DocumentSelectionOption> getPrimerOptions();
}
