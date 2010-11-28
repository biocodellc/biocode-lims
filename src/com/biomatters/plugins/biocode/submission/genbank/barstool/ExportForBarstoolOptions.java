package com.biomatters.plugins.biocode.submission.genbank.barstool;

import com.biomatters.geneious.publicapi.components.Dialogs;
import com.biomatters.geneious.publicapi.components.GButton;
import com.biomatters.geneious.publicapi.documents.AnnotatedPluginDocument;
import com.biomatters.geneious.publicapi.documents.DocumentField;
import com.biomatters.geneious.publicapi.plugin.DocumentOperationException;
import com.biomatters.geneious.publicapi.plugin.Options;
import com.biomatters.plugins.biocode.BiocodeUtilities;
import com.biomatters.plugins.biocode.assembler.verify.Pair;
import jebl.evolution.sequences.GeneticCode;
import org.jdom.Element;
import org.virion.jam.util.SimpleListener;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.io.File;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * @author Richard
 * @version $Id$
 */
public class ExportForBarstoolOptions extends Options {

    private final StringOption submissionName;
    private final FileSelectionOption folderOption;

    private final StringOption projectOption;
    private final StringOption baseCallerOption;
    private final StringOption dateFormatOption;

    private final BooleanOption latLongOption;

    private final ExtraSubmissionFieldsOptions extraSubmissionFieldsOptions;

    private static final String SEQUENCE_ID = "Sequence_ID";
    private static final String COLLECTED_BY = "Collected_by";
    private static final String COLLECTION_DATE = "Collection_date";
    private static final String COUNTRY = "Country";
    private static final String SPECIMEN_VOUCHER = "Specimen_voucher";
    private static final String IDENTIFIED_BY = "Identified_by";
    static final String NOTE = "Note";

    private static final String[] SOURCE_FIELDS = new String[] {SEQUENCE_ID, COLLECTED_BY, COLLECTION_DATE, COUNTRY, SPECIMEN_VOUCHER, IDENTIFIED_BY, NOTE};

    private static final OptionValue NONE = new OptionValue("none", "<html><i>None</i></html>");
    private final ComboBoxOption<OptionValue> translationOption;

    public ExportForBarstoolOptions(AnnotatedPluginDocument[] documents) throws DocumentOperationException {

        Set<DocumentField> fieldsSeen = new HashSet<DocumentField>();
        final List<OptionValue> stringFields = new ArrayList<OptionValue>();
        List<OptionValue> dateFields = new ArrayList<OptionValue>();
//        List<OptionValue> numberFields = new ArrayList<OptionValue>();
        for (AnnotatedPluginDocument document : documents) {
            for (DocumentField documentField : document.getExtendedDisplayableFields()) {
                if (fieldsSeen.add(documentField)) {
                    OptionValue optionValue = new OptionValue(documentField.getCode(), documentField.getName());
                    if (documentField.getValueType() == String.class) {
                        stringFields.add(optionValue);
                    } else if (documentField.getValueType() == Date.class) {
                        dateFields.add(optionValue);
                    } /*else if (Number.class.isAssignableFrom(documentField.getValueType())) {
                        numberFields.add(optionValue);
                    }*/
                }
            }
        }
        Comparator<OptionValue> labelComparator = new Comparator<OptionValue>() {
            public int compare(OptionValue o1, OptionValue o2) {
                return o1.getLabel().compareTo(o2.getLabel());
            }
        };
        Collections.sort(stringFields, labelComparator);
        Collections.sort(dateFields, labelComparator);
        List<OptionValue> stringFieldsWithNone = new ArrayList<OptionValue>(stringFields);
        stringFieldsWithNone.add(0, NONE);
        dateFields.add(0, NONE);
        List<OptionValue> dateAndStringFields = new ArrayList<OptionValue>(dateFields);
        dateAndStringFields.addAll(stringFields);

        folderOption = addFileSelectionOption("folder", "Folder:", "");
        folderOption.setDescription("The folder where the submission files will be saved");
        folderOption.setSelectionType(JFileChooser.DIRECTORIES_ONLY);
        submissionName = addStringOption("name", "Submission Name:", "BarSTool");
        submissionName.setDescription("Used as a base for naming the submission files");

        GeneticCode[] codes = GeneticCode.getGeneticCodesArray();
        List<OptionValue> geneticCodes = new ArrayList<OptionValue>();
        geneticCodes.add(NONE);
        for (GeneticCode code : codes) {
            geneticCodes.add(new OptionValue(code.getName(), code.getDescription()));
        }
        translationOption = addComboBoxOption("translation", "Genetic Code:", geneticCodes, geneticCodes.get(6));
        translationOption.setDescription("Genetic code used to generate translations (frame is determined automatically). Select None to not export translations.");

        addDivider("Fields");
        projectOption = addStringOption("project", "Project Name:", "Biocode");
        projectOption.setDescription("A sequencing center's internal designation for a specific sequencing project. This field can be useful for grouping related traces.");
        baseCallerOption = addStringOption("baseCaller", "Base Calling Program:", "");
        baseCallerOption.setDescription("The base calling program. This field is free text. Program name, version numbers or dates are very useful. eg. phred-19980904e or abi-3.1");
        ComboBoxOption<OptionValue> sequenceIdOption = addComboBoxOption(SEQUENCE_ID, "Sequence ID:", stringFields, stringFields.get(0));
        sequenceIdOption.setDescription("Identifies the same specimen in all the steps of a submission. Sequence_IDs must be unique within the set and may not contain spaces. ");
        ComboBoxOption<OptionValue> collectedByOption = addComboBoxOption(COLLECTED_BY, "Collected by:", stringFieldsWithNone, stringFieldsWithNone.get(0));
        collectedByOption.setDescription("Name of person who collected the sample");

        final ComboBoxOption<OptionValue> collectionDateOption = addComboBoxOption(COLLECTION_DATE, "Collection Date:", dateAndStringFields, dateAndStringFields.get(0));
        collectionDateOption.setDescription("Date the specimen was collected");
        dateFormatOption = addStringOption("dateFormat", "Date Format:", "MM-dd-yyyy");
        dateFormatOption.setEnabled(false);
        dateFormatOption.setFillHorizontalSpace(false);
        collectionDateOption.addChangeListener(new SimpleListener() {
            public void objectChanged() {
                dateFormatOption.setEnabled(stringFields.contains(collectionDateOption.getValue()));
            }
        });

        ComboBoxOption<OptionValue> countryOption = addComboBoxOption(COUNTRY, "Country:", stringFields, stringFields.get(0));
        countryOption.setDescription("The country of origin of DNA samples used");
        ComboBoxOption<OptionValue> specimenVoucherOption = addComboBoxOption(SPECIMEN_VOUCHER, "Specimen Voucher ID:", stringFields, stringFields.get(0));
        specimenVoucherOption.setDescription("An identifier of the individual or collection of the source organism and the place where it is currently stored, usually an institution");
        ComboBoxOption<OptionValue> identifiedByOption = addComboBoxOption(IDENTIFIED_BY, "Identified by:", stringFieldsWithNone, stringFieldsWithNone.get(0));
        identifiedByOption.setDescription("Name of the person or persons who identified by taxonomic name the organism from which the sequence was obtained");
        ComboBoxOption<OptionValue> noteOption = addComboBoxOption(NOTE, "Note:", stringFieldsWithNone, stringFieldsWithNone.get(0));
        noteOption.setDescription("Any additional information that you wish to provide about the sequence");

        latLongOption = addBooleanOption("latLong", "Include Lat-Long", true);

        extraSubmissionFieldsOptions = new ExtraSubmissionFieldsOptions(stringFields);
        extraSubmissionFieldsOptions.restorePreferences();
        addCustomComponent(new GButton(new AbstractAction("Additional Source Fields...") {
            public void actionPerformed(ActionEvent e) {
                Element valuesBefore = extraSubmissionFieldsOptions.valuesToXML("values");
                if (!Dialogs.showOptionsDialog(extraSubmissionFieldsOptions, "Additional Source Fields", true, ExportForBarstoolOptions.this.getPanel())) {
                    extraSubmissionFieldsOptions.valuesFromXML(valuesBefore);
                }
            }
        }));

        boolean contigSelected = false;
        for (AnnotatedPluginDocument doc : documents) {
            if (!BiocodeUtilities.isAlignmentOfChromatograms(doc)) {
                contigSelected = true;
                break;
            }
        }
        if (contigSelected) {
            Options consensusOptions = BiocodeUtilities.getConsensusOptions(documents);
            if (consensusOptions == null) {
                throw new DocumentOperationException("The consensus plugin must be installed to be able to add assemblies to LIMS");
            }
            consensusOptions.setValue("removeGaps", true);
            addChildOptions("consensus", "Consensus", null, consensusOptions);
        }
    }

    @Override
    protected JPanel createAdvancedPanel() {
        return null;
    }

    @Override
    public String verifyOptionsAreValid() {
        File folder = getFolder();
        if (!folder.exists()) {
            if (!folder.mkdirs()) {
                return "The folder " + folder + " doesn't exist and it couldn't be created. Make sure you have sufficient permissions to access the folder.";
            }
        }
        if (!folder.isDirectory()) {
            return "The selected file is not a folder. Please select a folder to export to.";
        }
        if (!folder.canWrite()) {
            return "The folder " + folder + " cannot be written to. Make sure you have sufficient permissions to access the folder.";
        }
        return null;
    }

    /**
     *
     * @return Genetic code to use for translation or null if shouldn't export translations
     */
    GeneticCode getGeneticCode() {
        String geneticCodeName = translationOption.getValue().getName();
        if (geneticCodeName.equals(NONE.getName())) {
            return null;
        }
        for (GeneticCode geneticCode : GeneticCode.getGeneticCodes()) {
            if (geneticCode.getName().equals(geneticCodeName)) {
                return geneticCode;
            }
        }
        return null;
    }

    File getFolder() {
        return new File(folderOption.getValue());
    }

    String getOrganismName(AnnotatedPluginDocument doc) {
        Object organismObject = doc.getFieldValue(DocumentField.ORGANISM_FIELD);
        if (organismObject != null) {
            return (String)organismObject;
        }
        Object taxonObject = doc.getFieldValue(DocumentField.TAXONOMY_FIELD);
        if (taxonObject == null) {
            return null;
        }
        String[] taxa = ((String)taxonObject).split(";");
        return taxa[taxa.length - 1].trim();
    }

    String getSequenceId(AnnotatedPluginDocument doc) {
        return (String)doc.getFieldValue(getValueAsString(SEQUENCE_ID));
    }

    Options getConsensusOptions() {
        return getChildOptions().get("consensus");
    }

    String getSubmissionName() {
        return submissionName.getValue();
    }

    String getProjectName() {
        return projectOption.getValue();
    }

    String getBaseCaller() {
        return baseCallerOption.getValue();
    }

    boolean isIncludeLatLong() {
        return latLongOption.getValue();
    }

    DateFormat getDateFormat() {
        return new SimpleDateFormat(dateFormatOption.getValue());
    }

    /**
     *
     * @return Pairs of source field code to document field code
     */
    List<Pair<String, String>> getSourceFields() {
        List<Pair<String,String>> sourceFields = new ArrayList<Pair<String, String>>();
        for (String sourceField : SOURCE_FIELDS) {
            String value = getValueAsString(sourceField);
            if (!value.equals(NONE.getName())) {
                sourceFields.add(new Pair<String, String>(sourceField, value));
            }
        }
        sourceFields.addAll(extraSubmissionFieldsOptions.getExtraFields());
        return sourceFields;
    }

    List<Pair<String, String>> getFixedSourceFields() {
        return Collections.emptyList();
    }

    public String getTracesFolderName() {
        return getSubmissionName() + "_traces";
    }
}
