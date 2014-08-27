package com.biomatters.plugins.biocode.submission.bold;

import com.biomatters.geneious.publicapi.components.GPanel;
import com.biomatters.geneious.publicapi.components.GTextPane;
import com.biomatters.geneious.publicapi.documents.AnnotatedPluginDocument;
import com.biomatters.geneious.publicapi.plugin.DocumentOperationException;
import com.biomatters.geneious.publicapi.plugin.Options;
import com.biomatters.plugins.biocode.BiocodeUtilities;
import com.biomatters.plugins.biocode.labbench.BiocodeService;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.Collections;
import java.util.List;

/**
 * @author Matthew Cheung
 *         Created on 26/08/14 4:43 PM
 */
public class BoldTraceSubmissionOptions extends Options {

    public static final String PROCESS_ID = "BoldProcessID";
    private static final String LOCATION = "outputLocation";
    private static final String NAME = "outputName";
    private static final String SUFFIXES = "filenameSuffix";
    private static final String FWD_SUFFIX = "forward";
    private static final String REV_SUFFIX = "reverse";

    public BoldTraceSubmissionOptions() {
    }

    public BoldTraceSubmissionOptions(AnnotatedPluginDocument[] documentsToExport) throws DocumentOperationException {
        if(BiocodeService.getInstance().getActiveFIMSConnection() == null) {
            throw new DocumentOperationException("Must be logged into FIMS/LIMS");
        }

        for (AnnotatedPluginDocument document : documentsToExport) {
            if(document.getFieldValue(BiocodeUtilities.IS_FORWARD_FIELD.getCode()) == null) {
                throw new DocumentOperationException(
                        "Could not determine direction of reads, make sure you have run <strong>Set Read Direction</strong> first.");
            }
        }

        List<OptionValue> fimsFieldsOptionValues = BiocodeUtilities.getOptionValuesForFimsFields();
        if(fimsFieldsOptionValues.isEmpty()) {
            fimsFieldsOptionValues = Collections.singletonList(new OptionValue("none", "No FIMS fields", "No FIMS fields are available", false));
        }
        OptionValue defaultValue = fimsFieldsOptionValues.get(0);
        for (OptionValue candidate : fimsFieldsOptionValues) {
            String lowerCaseLabel = candidate.getLabel();
            if(lowerCaseLabel.matches(".*bold\\s*(process)?\\s*id.*")) {
                defaultValue = candidate;
            }
        }

        addComboBoxOption(PROCESS_ID, "Field for BOLD Process ID:", fimsFieldsOptionValues, defaultValue);
        addDivider("Output");

        addFileSelectionOption(LOCATION, "Location:", "").setSelectionType(JFileChooser.DIRECTORIES_ONLY);
        addStringOption(NAME, "Submission Name:", "", "Name for the submission folder and the zip containing it");

        Options filenameOptions = new Options(this.getClass());
        filenameOptions.addStringOption(FWD_SUFFIX, "Forward:", "", "Useful if both reads share same name");
        filenameOptions.addStringOption(REV_SUFFIX, "Reverse:", "", "Useful if both reads share same name");
        addChildOptions(SUFFIXES, "Trace Filename Suffix", "Set a suffix to use for the file names the traces are exported to", filenameOptions);
    }

    public String getForwardSuffix() {
        return getValueAsString(SUFFIXES + "." + FWD_SUFFIX);
    }

    public String getReverseSuffix() {
        return getValueAsString(SUFFIXES + "." + REV_SUFFIX);
    }

    public String getSubmissionName() {
        return getValueAsString(NAME);
    }

    public File getZipFile() {
        return new File(getValueAsString(LOCATION), getValueAsString(NAME) + ".zip");
    }

    @Override
    protected JPanel createPanel() {
        GPanel mainPanel = new GPanel(new BorderLayout());

        GPanel infoPanel = new GPanel();
        GTextPane infoPane = GTextPane.createHtmlPane(
                "<i><strong>Note</strong>: This operation produces output according to the " +
                "<a href=\"http://www.boldsystems.org/index.php/resources/handbook?chapter=3_submissions.html#trace_submissions\">" +
                "BOLD systems v3 handbook</a></i>.");
        infoPanel.add(infoPane);
        mainPanel.add(infoPanel, BorderLayout.SOUTH);

        JPanel original = super.createPanel();
        mainPanel.add(original, BorderLayout.CENTER);
        return mainPanel;
    }
}
