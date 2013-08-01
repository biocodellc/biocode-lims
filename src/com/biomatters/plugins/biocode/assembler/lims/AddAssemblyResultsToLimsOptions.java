package com.biomatters.plugins.biocode.assembler.lims;

import com.biomatters.geneious.publicapi.components.GLabel;
import com.biomatters.geneious.publicapi.documents.AnnotatedPluginDocument;
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
    private ComboBoxOption<OptionValue> uploadOption;
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

        if(passed) {
            OptionValue justSequence = new OptionValue("sequenceOnly", inputType.getUploadDescription(),
                    "Useful if traces were imported from LIMS plates");
            OptionValue withTraces = new OptionValue(WITH_TRACES, inputType.getWithTracesDescription(),
                    "Useful if traces imported from files and need attaching to LIMS plates");

            warningLabel = addLabel("<html>The sequence(s) being uploaded should be ready for submission to public databases." +
                    "<br>(Of sufficient quality, have had all edits completed etc)</html>");
            uploadOption = addComboBoxOption("upload", "Upload to LIMS:", new OptionValue[]{justSequence, withTraces}, justSequence);

            downloadLabel = addLabel("<html><strong>Note</strong>: If you downloaded your traces (chromatograms) from the LIMS, you do not need to upload them again.<br><br></html>");
        }

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

    private static final String WITH_TRACES = "uploadTracesToo";
    public boolean isAddChromatograms() {
        return uploadOption != null && WITH_TRACES.equals(uploadOption.getValueAsString());
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
        if(downloadLabel != null && downloadLabel.getComponent() instanceof GLabel) {
            ((GLabel)downloadLabel.getComponent()).setSmall(true);
        }
        return super.createPanel();
    }
    }
