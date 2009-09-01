package com.biomatters.plugins.moorea.submission.genbank.barstool;

import com.biomatters.geneious.publicapi.documents.AnnotatedPluginDocument;
import com.biomatters.geneious.publicapi.documents.DocumentField;
import com.biomatters.geneious.publicapi.plugin.DocumentOperationException;
import com.biomatters.geneious.publicapi.plugin.Options;
import com.biomatters.plugins.moorea.MooreaUtilities;
import com.biomatters.plugins.moorea.assembler.verify.Pair;

import javax.swing.*;
import java.io.File;
import java.util.*;

/**
 * @author Richard
 * @version $Id$
 */
public class ExportForBarstoolOptions extends Options {

    private final StringOption submissionName;
    private final FileSelectionOption folderOption;
//    private final BooleanOption generateTranslationsOption;

    private final StringOption countryOption;
    private final StringOption projectOption;
    private final StringOption baseCallerOption;

    private final BooleanOption latLongOption;

    private static final String SEQUENCE_ID = "Sequence_ID";
    private static final String COLLECTED_BY = "Collected_by";
    private static final String COLLECTION_DATE = "Collection_date";
    private static final String SPECIMEN_VOUCHER = "Specimen_voucher";
    private static final String IDENTIFIED_BY = "Identified_by";
    static final String NOTE = "Note";


    private static final String[] SOURCE_FIELDS = new String[] {SEQUENCE_ID, COLLECTED_BY, COLLECTION_DATE, SPECIMEN_VOUCHER, IDENTIFIED_BY, NOTE};

    public ExportForBarstoolOptions(AnnotatedPluginDocument[] documents) throws DocumentOperationException {

        Set<DocumentField> fieldsSeen = new HashSet<DocumentField>();
        List<OptionValue> stringFields = new ArrayList<OptionValue>();
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
        Collections.sort(stringFields, new Comparator<OptionValue>() {
            public int compare(OptionValue o1, OptionValue o2) {
                return o1.getLabel().compareTo(o2.getLabel());
            }
        });

        folderOption = addFileSelectionOption("folder", "Folder:", "");
        folderOption.setDescription("The folder where the submission files will be saved");
        folderOption.setSelectionType(JFileChooser.DIRECTORIES_ONLY);
        submissionName = addStringOption("name", "Submission Name:", "BarSTool");
        submissionName.setDescription("Used as a base for naming the submission files");
//        generateTranslationsOption = addBooleanOption("generateTranslation", "Export protein translations", true);
        addDivider("Fields");
        countryOption = addStringOption("country", "Country:", "French Polynesia");
        projectOption = addStringOption("project", "Project Name:", "Biocode");
        projectOption.setDescription("a sequencing center's internal designation for a specific sequencing p roject. This field can be useful for grouping related traces.");
        baseCallerOption = addStringOption("baseCaller", "Base Calling Program:", "");
        baseCallerOption.setDescription("the base calling program. This field is free text. Program name, version numbers or dates are very useful. eg. phred-19980904e or abi-3.1");
        addComboBoxOption(SEQUENCE_ID, "Sequence ID:", stringFields, stringFields.get(0));
        addComboBoxOption(COLLECTED_BY, "Collected by:", stringFields, stringFields.get(0));
        addComboBoxOption(COLLECTION_DATE, "Collection Date:", dateFields, dateFields.get(0));
        addComboBoxOption(SPECIMEN_VOUCHER, "Specimen Voucher ID:", stringFields, stringFields.get(0));
        addComboBoxOption(IDENTIFIED_BY, "Identified by:", stringFields, stringFields.get(0));
        addComboBoxOption(NOTE, "Note:", stringFields, stringFields.get(0));

        latLongOption = addBooleanOption("latLong", "Include Lat-Long", true);

        boolean contigSelected = false;
        for (AnnotatedPluginDocument doc : documents) {
            if (!MooreaUtilities.isAlignmentOfContigs(doc)) {
                contigSelected = true;
                break;
            }
        }
        if (contigSelected) {
            //todo check sequence type
            Options consensusOptions = MooreaUtilities.getConsensusOptions(documents);
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

    /**
     *
     * @return Pairs of source field code to document field code
     */
    List<Pair<String, String>> getSourceFields() {
        List<Pair<String,String>> sourceFields = new ArrayList<Pair<String, String>>();
        for (String sourceField : SOURCE_FIELDS) {
            sourceFields.add(new Pair<String, String>(sourceField, getValueAsString(sourceField)));
        }
        return sourceFields;
    }

    List<Pair<String, String>> getFixedSourceFields() {
        String country = countryOption.getValue();
        if (country.length() > 0) {
            return Collections.singletonList(new Pair<String, String>("Country", country));
        } else {
            return Collections.emptyList();
        }
    }

//    boolean isGenerateTranslation() {
//        return generateTranslationsOption.getValue();
//    }

    public String getTracesFolderName() {
        return getSubmissionName() + "_traces";
    }
}
