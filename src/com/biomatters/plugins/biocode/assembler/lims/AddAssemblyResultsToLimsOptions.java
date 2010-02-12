package com.biomatters.plugins.biocode.assembler.lims;

import com.biomatters.geneious.publicapi.documents.AnnotatedPluginDocument;
import com.biomatters.geneious.publicapi.documents.sequence.SequenceAlignmentDocument;
import com.biomatters.geneious.publicapi.plugin.DocumentOperationException;
import com.biomatters.geneious.publicapi.plugin.Options;
import com.biomatters.plugins.biocode.BiocodeUtilities;

import javax.swing.*;

/**
 * @author Richard
 * @version $Id$
 */
public class AddAssemblyResultsToLimsOptions extends Options {
    private BooleanOption addChromatogramsOption;

    public AddAssemblyResultsToLimsOptions(AnnotatedPluginDocument[] documents) throws DocumentOperationException {
//        boolean isAlignmentSelected = SequenceAlignmentDocument.class.isAssignableFrom(documents[0].getDocumentClass());

        addChromatogramsOption = addBooleanOption("attachChromatograms", "Add chromatograms to LIMS", false);
        addLabel("If you downloaded your chromatograms from the LIMS, you do not need to add them again.");  
        addChromatogramsOption.setDescription("<html>If assemblies are selected and they reference original chromatograms then the<br>" +
                                                    "chromatograms will be attached to the appropriate cycle sequencing entry in the LIMS</html>");

        boolean contigSelected = false;
        for (AnnotatedPluginDocument doc : documents) {
            if (SequenceAlignmentDocument.class.isAssignableFrom(doc.getDocumentClass()) && !BiocodeUtilities.isAlignmentOfContigs(doc)) {
                contigSelected = true;
                break;
            }
        }
        if (contigSelected) {
            Options consensusOptions = BiocodeUtilities.getConsensusOptions(documents);
            if (consensusOptions == null) {
                throw new DocumentOperationException("The consensus plugin must be installed to be able to add assemblies to LIMS");
            }
            addChildOptions("consensus", "Consensus", null, consensusOptions);
        }
    }

    Options getConsensusOptions() {
        return getChildOptions().get("consensus");
    }

    @Override
    protected JPanel createAdvancedPanel() {
        return null;
    }

    public boolean isAddChromatograms() {
        return addChromatogramsOption.getValue();
    }
}
