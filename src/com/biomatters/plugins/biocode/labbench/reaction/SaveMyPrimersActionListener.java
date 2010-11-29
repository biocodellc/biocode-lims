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
//    private static SequenceAnnotationGenerator PRIMER_GENERATOR;

    public void actionPerformed(ActionEvent e) {
//        if(PRIMER_GENERATOR == null) { //todo don't store this statically because plugins can be reloaded
//            PRIMER_GENERATOR = PluginUtilities.getSequenceAnnotationGenerator("com.biomatters.plugins.primerDesign.PrimerCharacteristicAnnotationGenerator");
//        }

//        Runnable runnable = new Runnable() {
//            public void run() {
                List<AnnotatedPluginDocument> primers = new ArrayList<AnnotatedPluginDocument>();
                for(DocumentSelectionOption option : getPrimerOptions()) {
                    for(AnnotatedPluginDocument doc : option.getValue().getDocuments()) {
                        primers.add(doc);
                    }
                }

                try {
//                    Options options = PRIMER_GENERATOR.getOptions(primers.toArray(new AnnotatedPluginDocument[primers.size()]), null);
//                    options.restoreDefaults();
                    //todo fix this
//                    List<List<SequenceAnnotation>> annotations = PRIMER_GENERATOR.generateAnnotations(primers.toArray(new AnnotatedPluginDocument[primers.size()]), null, ProgressListener.EMPTY, options);
//                    if(annotations.size() == primers.size()) {
                        List<AnnotatedPluginDocument> newPrimers = new ArrayList<AnnotatedPluginDocument>();
                        for(int i=0; i < primers.size(); i++) {
                            AnnotatedPluginDocument origDoc = primers.get(i);
                            SequenceDocument origSeq = (SequenceDocument)origDoc.getDocument();
                            OligoSequenceDocument sequence = new OligoSequenceDocument(origDoc.getName(), origDoc.getSummary(), origSeq.getSequenceString(), origDoc.getCreationDate());
//                            sequence.setAnnotations(annotations.get(i));
                            newPrimers.add(DocumentUtilities.createAnnotatedPluginDocument(sequence));
                        }
//                        primers = newPrimers;
                    DocumentUtilities.addGeneratedDocuments(newPrimers, false);

//                    }

                } catch (DocumentOperationException e1) {
                    Dialogs.showMessageDialog("Failed to load primer document: " + e1.getMessage());
//                    DocumentUtilities.addGeneratedDocuments(primers, false, Collections.<AnnotatedPluginDocument>emptyList());
                }

//            }

//        };
//        BiocodeService.block("Extracting primers...", null, runnable);
    }

    public abstract List<DocumentSelectionOption> getPrimerOptions();
}
