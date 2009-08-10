package com.biomatters.plugins.moorea.assembler.lims;

import com.biomatters.geneious.publicapi.documents.AnnotatedPluginDocument;
import com.biomatters.geneious.publicapi.documents.DocumentField;
import com.biomatters.geneious.publicapi.documents.PluginDocument;
import com.biomatters.geneious.publicapi.plugin.*;
import com.biomatters.plugins.moorea.MooreaPlugin;
import jebl.util.ProgressListener;

import java.util.List;

/**
 * @author Richard
 * @version $Id$
 */
public class SetAssemblyResultOperation extends DocumentOperation {

    private static final Options.OptionValue PASS_VALUE = new Options.OptionValue("pass", "Pass");
    private static final Options.OptionValue FAIL_VALUE = new Options.OptionValue("fail", "Fail");

    public static final DocumentField ASSEMBLY_RESULT_FIELD = DocumentField.createBooleanField("Assembly Result Pass",
            "Whether or not assembly passed", "assemblyPass", true, false);

    public GeneiousActionOptions getActionOptions() {
        GeneiousActionOptions geneiousActionOptions = new GeneiousActionOptions("Set Assembly Result...")
                .setInPopupMenu(true, 0.55);
        return GeneiousActionOptions.createSubmenuActionOptions(MooreaPlugin.getSuperBiocodeAction(), geneiousActionOptions);
    }

    public String getHelp() {
        return "Select one or more assemblies or consensus sequences to mark them as pass or fail for assembly.";
    }

    public DocumentSelectionSignature[] getSelectionSignatures() {
        return new DocumentSelectionSignature[] {
                new DocumentSelectionSignature(PluginDocument.class, 1, Integer.MAX_VALUE)
        };
    }

    @Override
    public boolean isDocumentGenerator() {
        return false;
    }

    @Override
    public Options getOptions(AnnotatedPluginDocument... documents) throws DocumentOperationException {
        Options options = new Options(SetAssemblyResultOperation.class);
        options.addRadioOption("result", "Result:", new Options.OptionValue[] {PASS_VALUE, FAIL_VALUE}, PASS_VALUE, Options.Alignment.HORIZONTAL_ALIGN);
        return options;
    }

    @Override
    public List<AnnotatedPluginDocument> performOperation(AnnotatedPluginDocument[] annotatedDocuments, ProgressListener progressListener, Options options) throws DocumentOperationException {
        progressListener.setIndeterminateProgress();
        boolean isPass = options.getValue("result").equals(PASS_VALUE);
        for (AnnotatedPluginDocument annotatedDocument : annotatedDocuments) {
            if (progressListener.isCanceled()) {
                return null;
            }
            annotatedDocument.setFieldValue(ASSEMBLY_RESULT_FIELD, isPass);
            annotatedDocument.save();
        }
        return null;
    }
}
