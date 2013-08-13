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

    private MarkInLimsUtilities.InputType inputType;

    public AddAssemblyResultsToLimsOptions(AnnotatedPluginDocument[] documents, boolean passed) throws DocumentOperationException {
        inputType = MarkInLimsUtilities.determineInputType(documents);
        if(inputType == MarkInLimsUtilities.InputType.MIXED) {
            throw new DocumentOperationException("This operation only works on documents of the same type.  " +
                    "Either select assemblies or consensus sequences.");
        }

        boolean contigSelected = inputType == MarkInLimsUtilities.InputType.CONTIGS;

        for (AnnotatedPluginDocument doc : documents) {
            if (SequenceAlignmentDocument.class.isAssignableFrom(doc.getDocumentClass()) && !BiocodeUtilities.isAlignmentOfContigConsensusSequences(doc)) {
                contigSelected = true;
                break;
            }
        }

        if(passed) {
            String sequenceOrSequences = documents.length > 1 ||
                    inputType == MarkInLimsUtilities.InputType.ALIGNMENT_OF_CONSENSUS ? "sequences" : "sequence";
            StringBuilder message = new StringBuilder("The ");
            message.append(inputType == MarkInLimsUtilities.InputType.CONTIGS ? "generated consensus " : "selected ");
            message.append(sequenceOrSequences);
            message.append(" will be uploaded to the LIMS as the final sequencing result.");
            addLabel(message.toString());
            warningLabel = addLabel("<html>The " + sequenceOrSequences + " being uploaded should be ready for submission to public databases." +
                    "<br>(Of sufficient quality, have had all edits completed etc)</html>");
        }
        removePrevious = addBooleanOption("removePrevious", "Remove previous final sequencing results", false);
        removePrevious.setDescription("<html>Normally marking as pass or failed always creates a new sequence entry.  <br>Use this option if you are correcting a previous entry, and wish to erase it from the database.</html>");

        Options traceOptions = new Options(AddAssemblyResultsToLimsOptions.class);
        addChromatogramsOption = traceOptions.addBooleanOption("attachChromatograms", "Also attach raw traces to sequencing reaction in LIMS", false);
        addChromatogramsOption.setDescription("<html>If assemblies are selected and they reference original chromatograms then the<br>" +
                "chromatograms will be attached to the appropriate cycle sequencing entry in the LIMS</html>");
        downloadLabel = traceOptions.addLabel("<html><u>Note</u>: If you downloaded your chromatograms from the LIMS, you do not need to add them again.</html>");
        addChildOptions("trace", "Traces", null, traceOptions, true);

        Options details = new Options(AddAssemblyResultsToLimsOptions.class);
        details.addStringOption("technician", "Your name", "");
        details.addMultipleLineStringOption("notes", "Notes", "", 5, true);
        addChildOptions("details", "Details", null, details, true);

        if (contigSelected) {
            Options consensusOptions = BiocodeUtilities.getConsensusOptions(documents);
            if (consensusOptions == null) {
                throw new DocumentOperationException("The consensus plugin must be installed to be able to add assemblies to LIMS");
            }
            addChildOptions("consensus", "Consensus", null, consensusOptions);
        }
    }

    public String getTechnician() {
        return getValueAsString("details.technician");
    }

    public String getNotes() {
        return getValueAsString("details.notes");
    }

    public MarkInLimsUtilities.InputType getInputType() {
        return inputType;
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
