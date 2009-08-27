package com.biomatters.plugins.moorea.submission.genbank.barstool;

import com.biomatters.geneious.publicapi.documents.AnnotatedPluginDocument;
import com.biomatters.geneious.publicapi.documents.DocumentField;
import com.biomatters.geneious.publicapi.plugin.DocumentOperationException;
import com.biomatters.geneious.publicapi.plugin.Options;
import com.biomatters.plugins.moorea.MooreaUtilities;
import com.biomatters.plugins.moorea.assembler.verify.Pair;

import javax.swing.*;
import java.io.File;
import java.util.Collections;
import java.util.List;

/**
 * @author Richard
 * @version $Id$
 */
public class ExportForBarstoolOptions extends Options {

    private final StringOption submissionName;
    private final FileSelectionOption folderOption;

    private final StringOption countryOption;
    private final StringOption projectOption;
    private final StringOption baseCallerOption;

    //    private final BooleanOption generateTranslationsOption;

    private String noReadDirectionValue = "N";

    public ExportForBarstoolOptions(AnnotatedPluginDocument[] documents) throws DocumentOperationException {

        folderOption = addFileSelectionOption("folder", "Folder:", "");
        folderOption.setDescription("The folder where the submission files will be saved");
        folderOption.setSelectionType(JFileChooser.DIRECTORIES_ONLY);
        submissionName = addStringOption("name", "Submission Name:", "BarSTool");
        submissionName.setDescription("Used as a base for naming the submission files");
        addDivider("Fields");
        countryOption = addStringOption("country", "Country:", "French Polynesia");
        projectOption = addStringOption("project", "Project Name:", "Biocode");
        projectOption.setDescription("a sequencing center's internal designation for a specific sequencing p roject. This field can be useful for grouping related traces.");
        baseCallerOption = addStringOption("baseCaller", "Base Calling Program:", "");
        baseCallerOption.setDescription("the base calling program. This field is free text. Program name, version numbers or dates are very useful. eg. phred-19980904e or abi-3.1");
//        generateTranslationsOption = addBooleanOption("generateTranslation", "Export protein translations", true);

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
        //todo
        return "Homo sapiens";
    }

    String getSequenceId(AnnotatedPluginDocument doc) {
        //todo
        return doc.getName();
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

    List<Pair<String, DocumentField>> getSourceFields() {
        return Collections.singletonList(new Pair<String, DocumentField>("Sequence_ID", DocumentField.NAME_FIELD));
    }

    List<Pair<String, String>> getFixedSourceFields() {
        return Collections.singletonList(new Pair<String, String>("Country", countryOption.getValue()));
    }

//    boolean isGenerateTranslation() {
//        return generateTranslationsOption.getValue();
//    }

    public String getTracesFolderName() {
        return getSubmissionName() + "_traces";
    }
}
