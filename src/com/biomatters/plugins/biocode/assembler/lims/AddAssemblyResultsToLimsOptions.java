package com.biomatters.plugins.biocode.assembler.lims;

import com.biomatters.geneious.publicapi.components.GLabel;
import com.biomatters.geneious.publicapi.documents.AnnotatedPluginDocument;
import com.biomatters.geneious.publicapi.documents.sequence.SequenceAlignmentDocument;
import com.biomatters.geneious.publicapi.plugin.DocumentOperationException;
import com.biomatters.geneious.publicapi.plugin.Options;
import com.biomatters.geneious.publicapi.utilities.IconUtilities;
import com.biomatters.plugins.biocode.BiocodeUtilities;

import javax.swing.*;

/**
 * @author Richard
 * @version $Id$
 */
public class AddAssemblyResultsToLimsOptions extends Options {
    private BooleanOption addChromatogramsOption;
    private Option<String, ? extends JComponent> warningLabel;
    private Option<String,? extends JComponent> downloadLabel;
    private BooleanOption removePrevious;

    public AddAssemblyResultsToLimsOptions(AnnotatedPluginDocument[] documents, boolean passed) throws DocumentOperationException {
//        boolean isAlignmentSelected = SequenceAlignmentDocument.class.isAssignableFrom(documents[0].getDocumentClass());
        boolean contigSelected = false;
        for (AnnotatedPluginDocument doc : documents) {
            if (SequenceAlignmentDocument.class.isAssignableFrom(doc.getDocumentClass()) && !BiocodeUtilities.isAlignmentOfContigConsensusSequences(doc)) {
                contigSelected = true;
                break;
            }
        }

        if(passed) {
            warningLabel = addLabel(
                    "<html>This operation will also save "+(contigSelected ? "the consensus sequence of your assembly" : "a copy of your sequence")+" to the LIMS.  This "+(contigSelected ? "consensus" : "will consist of the trimmed sequence with quality, and")+" should be <br>" +
                    "the sequence that you submit to public sequence databases. <br>" +
                    "You should make sure that it is of sufficient quality, and that you " +
                    "have completed all edits etc. before marking as passed.</html>", false, true);
        }
        addChromatogramsOption = addBooleanOption("attachChromatograms", "Also add raw traces (chromatograms) to LIMS", false);
        downloadLabel = addLabel("If you downloaded your chromatograms from the LIMS, you do not need to add them again.");
        addChromatogramsOption.setDescription("<html>If assemblies are selected and they reference original chromatograms then the<br>" +
                                                    "chromatograms will be attached to the appropriate cycle sequencing entry in the LIMS</html>");
        addStringOption("technician", "Your name", "");
        addMultipleLineStringOption("notes", "Notes", "", 5, true);
        removePrevious = addBooleanOption("removePrevious", "Remove previous entries for " + (documents.length > 1 || contigSelected ? "these workflows." : "this workflow."), false);
        removePrevious.setDescription("<html>Normally marking as pass or failed always creates a new sequence entry.  <br>Use this option if you are correcting a previous entry, and wish to erase it from the database.</html>");
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

    public boolean removePreviousSequences() {
        return removePrevious.getValue();
    }

    @Override
    protected JPanel createPanel() {
        if (warningLabel != null) {
            JComponent component = warningLabel.getComponent();
            if (component instanceof GLabel) {
                ((GLabel) component).setSmall(true);
            }
            if (component instanceof JLabel) {
                ((JLabel) component).setIcon(IconUtilities.getIcons("warning16.png").getIcon16());
            }
        }
        if(downloadLabel.getComponent() instanceof GLabel) {
            ((GLabel)downloadLabel.getComponent()).setSmall(true);
        }
        return super.createPanel();
    }
}
