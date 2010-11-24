package com.biomatters.plugins.biocode.labbench.reaction;

import com.biomatters.geneious.publicapi.documents.DocumentUtilities;
import com.biomatters.geneious.publicapi.documents.AnnotatedPluginDocument;
import com.biomatters.geneious.publicapi.documents.sequence.SequenceAnnotation;
import com.biomatters.geneious.publicapi.documents.sequence.SequenceDocument;
import com.biomatters.geneious.publicapi.plugin.*;
import com.biomatters.geneious.publicapi.implementations.sequence.DefaultNucleotideSequence;
import com.biomatters.geneious.publicapi.implementations.sequence.OligoSequenceDocument;
import com.biomatters.geneious.publicapi.components.Dialogs;
import com.biomatters.plugins.biocode.labbench.BiocodeService;

import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;
import java.awt.*;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;

import jebl.util.ProgressListener;

/**
 * @author Steve
 * @version $Id$
 */
public abstract class SaveMyPrimersActionListener implements ActionListener {
    private static SequenceAnnotationGenerator PRIMER_GENERATOR;

    public void actionPerformed(ActionEvent e) {
        if(PRIMER_GENERATOR == null) {
            PRIMER_GENERATOR = PluginUtilities.getSequenceAnnotationGenerator("com.biomatters.plugins.primerDesign.PrimerCharacteristicAnnotationGenerator");
        }

        Runnable runnable = new Runnable() {
            public void run() {
                List<AnnotatedPluginDocument> primers = new ArrayList<AnnotatedPluginDocument>();
                for(DocumentSelectionOption option : getPrimerOptions()) {
                    for(AnnotatedPluginDocument doc : option.getValue().getDocuments()) {
                        primers.add(doc);
                    }
                }

                try {
                    Options options = PRIMER_GENERATOR.getOptions(primers.toArray(new AnnotatedPluginDocument[primers.size()]), null);
                    options.restoreDefaults();
                    List<List<SequenceAnnotation>> annotations = PRIMER_GENERATOR.generateAnnotations(primers.toArray(new AnnotatedPluginDocument[primers.size()]), null, ProgressListener.EMPTY, options);
                    if(annotations.size() == primers.size()) {
                        List<AnnotatedPluginDocument> newPrimers = new ArrayList<AnnotatedPluginDocument>();
                        for(int i=0; i < primers.size(); i++) {
                            AnnotatedPluginDocument origDoc = primers.get(i);
                            SequenceDocument origSeq = (SequenceDocument)origDoc.getDocument();
                            OligoSequenceDocument sequence = new OligoSequenceDocument(origDoc.getName(), origDoc.getSummary(), origSeq.getSequenceString(), origDoc.getCreationDate());
                            sequence.setAnnotations(annotations.get(i));
                            newPrimers.add(DocumentUtilities.createAnnotatedPluginDocument(sequence));
                        }
                        primers = newPrimers;

                    }

                } catch (DocumentOperationException e1) {
                    Dialogs.showMessageDialog("Could not generate primer annotations - please run \"Characteristics for Selection\" on your new primers.");
                    DocumentUtilities.addGeneratedDocuments(primers, false, Collections.<AnnotatedPluginDocument>emptyList());
                }

            }

        };
        BiocodeService.block("Extracting primers...", null, runnable);
    }

    public abstract List<DocumentSelectionOption> getPrimerOptions();
}
