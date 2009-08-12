package com.biomatters.plugins.moorea.assembler.lims;

import com.biomatters.geneious.publicapi.documents.AnnotatedPluginDocument;
import com.biomatters.geneious.publicapi.documents.sequence.NucleotideGraphSequenceDocument;
import com.biomatters.geneious.publicapi.documents.sequence.SequenceAlignmentDocument;
import com.biomatters.geneious.publicapi.documents.sequence.SequenceDocument;
import com.biomatters.geneious.publicapi.plugin.DocumentOperation;
import com.biomatters.geneious.publicapi.plugin.DocumentOperationException;
import com.biomatters.geneious.publicapi.plugin.Options;
import com.biomatters.geneious.publicapi.plugin.PluginUtilities;
import com.biomatters.plugins.moorea.MooreaUtilities;
import jebl.util.ProgressListener;

import javax.swing.*;

/**
 * @author Richard
 * @version $Id$
 */
public class AddAssemblyResultsToLimsOptions extends Options {
    private BooleanOption addChromatogramsOption;

    public AddAssemblyResultsToLimsOptions(AnnotatedPluginDocument[] documents) throws DocumentOperationException {
        boolean isAlignmentSelected = false;
        for (AnnotatedPluginDocument document : documents) {
            if (SequenceAlignmentDocument.class.isAssignableFrom(document.getDocumentClass())) {
                isAlignmentSelected = true;
                break;
            }
        }
        addChromatogramsOption = addBooleanOption("attachChromatograms", "Add chromatograms to LIMS", true);
        addChromatogramsOption.setDescription("<html>If assemblies are selected and they reference original chromatograms then the<br>" +
                                                    "chromatograms will be attached to the appropriate cycle sequencing entry in the LIMS</html>");
        if (isAlignmentSelected) {
            //todo check sequence type
            Options consensusOptions = MooreaUtilities.getConsensusOptions(documents);
            if (consensusOptions == null) {
                throw new DocumentOperationException("The consensus plugin must be installed to be able to add assemblies to LIMS");
            }
            addChildOptions("consensus", "Consensus", null, consensusOptions);
        }
        //todo update taxonomy?
    }

    public String getConsensusSequence(AnnotatedPluginDocument doc) throws DocumentOperationException {
        if (SequenceAlignmentDocument.class.isAssignableFrom(doc.getDocumentClass())) {
            Options consensusOptions = getChildOptions().get("consensus");
            DocumentOperation consensusOperation = PluginUtilities.getDocumentOperation("Generate_Consensus");
            AnnotatedPluginDocument consensus = consensusOperation.performOperation(new AnnotatedPluginDocument[]{doc}, ProgressListener.EMPTY, consensusOptions).get(0);
            return ((SequenceDocument)consensus.getDocument()).getSequenceString();
        } else { //SequenceDocument
            SequenceDocument sequence = (SequenceDocument) doc.getDocument();
            if (sequence instanceof NucleotideGraphSequenceDocument) {
                NucleotideGraphSequenceDocument nucleotideGraph = (NucleotideGraphSequenceDocument) sequence;
                if (nucleotideGraph.hasChromatogramValues(0) || nucleotideGraph.hasChromatogramValues(1) ||
                        nucleotideGraph.hasChromatogramValues(2) || nucleotideGraph.hasChromatogramValues(3)) {
                    return null; //the sequence can't be a consensus sequence, return null because there is no consensus sequence to add to the database
                }
            }
            return sequence.getSequenceString();
        }
    }

    @Override
    protected JPanel createAdvancedPanel() {
        return null;
    }

    public boolean isAddChromatograms() {
        return addChromatogramsOption.getValue();
    }
}
