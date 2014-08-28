package com.biomatters.plugins.biocode.submission.bold;

import com.biomatters.geneious.publicapi.components.GPanel;
import com.biomatters.geneious.publicapi.components.GTextPane;
import com.biomatters.geneious.publicapi.documents.AnnotatedPluginDocument;
import com.biomatters.geneious.publicapi.documents.DocumentField;
import com.biomatters.geneious.publicapi.plugin.DocumentOperationException;
import com.biomatters.geneious.publicapi.plugin.Options;
import com.biomatters.plugins.biocode.BiocodeUtilities;
import com.biomatters.plugins.biocode.labbench.BiocodeService;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.*;
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

    private static final OptionValue FROM_LIMS = new OptionValue("fromLims", "Retrieve using annotated LIMS information" +
            (BiocodeService.getInstance().isLoggedIn() ? "" : " (Must be logged into LIMS)"),
            "Uses workflow information to locate the cycle sequencing and pcr reactions to obtain primers.");
    private static final OptionValue FROM_USER = new OptionValue("fromUser", "Manually enter", "Applies user entered information for all selected traces");
    private static final String PRIMER_INFO_FROM = "primerInfoFrom";
    private static final String FWD_PRIMER = "fwdPcr";
    private static final String REV_PRIMER = "revPcr";
    private static final String SEQ_PRIMER = "seqPrimer";
    private static final String MARKER = "marker";

    public BoldTraceSubmissionOptions() {
    }

    public BoldTraceSubmissionOptions(AnnotatedPluginDocument[] documentsToExport) throws DocumentOperationException {
        for (AnnotatedPluginDocument document : documentsToExport) {
            if(document.getFieldValue(BiocodeUtilities.IS_FORWARD_FIELD.getCode()) == null) {
                throw new DocumentOperationException(
                        "Could not determine direction of reads, make sure you have run <strong>Set Read Direction</strong> first.");
            }
        }

        List<OptionValue> fimsFieldsOptionValues = BiocodeUtilities.getOptionValuesForDocumentFields(getNonEmptyDocumentFields(documentsToExport));
        OptionValue defaultValue = fimsFieldsOptionValues.get(0);
        for (OptionValue candidate : fimsFieldsOptionValues) {
            String lowerCaseLabel = candidate.getLabel().toLowerCase();
            if(lowerCaseLabel.matches(".*bold\\s*(process)?\\s*id.*")) {
                defaultValue = candidate;
            }
        }

        addComboBoxOption(PROCESS_ID, "Field for BOLD Process ID:", fimsFieldsOptionValues, defaultValue);

        addDivider("Primer Information");
        FROM_LIMS.setEnabled(BiocodeService.getInstance().isLoggedIn());
        RadioOption<OptionValue> getPrimerInfoFromOption = addRadioOption(PRIMER_INFO_FROM, "",
                Arrays.asList(FROM_LIMS, FROM_USER), FROM_LIMS, Alignment.VERTICAL_ALIGN);
        StringOption fwdPrimerOption = addStringOption(FWD_PRIMER, "Forward PCR Primer:", "", "Required");
        StringOption revPrimerOption = addStringOption(REV_PRIMER, "Reverse PCR Primer:", "", "Required");
        StringOption seqPrimerOption = addStringOption(SEQ_PRIMER, "Sequencing Primer:", "", "Not Required");
        StringOption markerOption = addStringOption(MARKER, "Marker:", "", "Required");
        getPrimerInfoFromOption.setDependentPosition(RadioOption.DependentPosition.BELOW);
        getPrimerInfoFromOption.addDependent(fwdPrimerOption, FROM_USER);
        getPrimerInfoFromOption.addDependent(revPrimerOption, FROM_USER);
        getPrimerInfoFromOption.addDependent(seqPrimerOption, FROM_USER);
        getPrimerInfoFromOption.addDependent(markerOption, FROM_USER);

        addDivider("Output");
        addFileSelectionOption(LOCATION, "Location:", "").setSelectionType(JFileChooser.DIRECTORIES_ONLY);
        addStringOption(NAME, "Submission Name:", "", "Name of submission folder and zip file");

        Options filenameOptions = new Options(this.getClass());
        filenameOptions.addStringOption(FWD_SUFFIX, "Forward:", "", "Useful if both reads share same name");
        filenameOptions.addStringOption(REV_SUFFIX, "Reverse:", "", "Useful if both reads share same name");
        addChildOptions(SUFFIXES, "Trace Filename Suffix", "Set a suffix to use for the file names the traces are exported to", filenameOptions);
    }

    @Override
    public String verifyOptionsAreValid() {
        if(isUserManuallyEntering()) {
            if(isBlank(getUserEnteredForwardPcrPrimer())) {
                return "Must specify a forward PCR primer or use LIMS information.";
            } else if(isBlank(getUserEnteredReversePcrPrimer())) {
                return "Must specify a reverse PCR primer or use LIMS information.";
            } else if(isBlank(getUserEnteredMarker())) {
                return "Must specify a marker or use LIMS information.";
            }
        }
        return super.verifyOptionsAreValid();
    }

    public boolean isBlank(String userEnteredMarker) {
        return userEnteredMarker == null || userEnteredMarker.trim().isEmpty();
    }

    boolean isUserManuallyEntering() {
        return getValue(PRIMER_INFO_FROM) == FROM_USER;
    }

    String getUserEnteredForwardPcrPrimer() {
        if(isUserManuallyEntering()) {
            return getValueAsString(FWD_PRIMER);
        } else {
            return null;
        }
    }

    String getUserEnteredReversePcrPrimer() {
        if(isUserManuallyEntering()) {
            return getValueAsString(REV_PRIMER);
        } else {
            return null;
        }
    }

    String getUserEnteredSequencingPrimer() {
        if(isUserManuallyEntering()) {
            return getValueAsString(SEQ_PRIMER);
        } else {
            return null;
        }
    }

    String getUserEnteredMarker() {
        if(isUserManuallyEntering()) {
            return getValueAsString(MARKER);
        } else {
            return null;
        }
    }

    private static List<DocumentField> getNonEmptyDocumentFields(AnnotatedPluginDocument[] docs) {
        Set<DocumentField> fields = new HashSet<DocumentField>();
        for (AnnotatedPluginDocument doc : docs) {
            fields.addAll(doc.getDisplayableFields());
        }
        List<DocumentField> result = new ArrayList<DocumentField>();
        for (DocumentField field : fields) {
            if(fieldExistsOnDocs(field, docs)) {
                result.add(field);
            }
        }
        return result;
    }

    private static boolean fieldExistsOnDocs(DocumentField field, AnnotatedPluginDocument[] docs) {
        for (AnnotatedPluginDocument doc : docs) {
            if(doc.getFieldValue(field) != null) {
                return true;
            }
        }
        return false;
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

    public DocumentField getProcessIdField() {
        Object value = getValue(PROCESS_ID);
        if(value instanceof OptionValue) {
            return BiocodeUtilities.getDocumentFieldForOptionValue((OptionValue)value);
        } else {
            throw new IllegalStateException("Value for " + PROCESS_ID + " should have been an OptionValue but was " + value.getClass().getSimpleName());
        }
    }
}
