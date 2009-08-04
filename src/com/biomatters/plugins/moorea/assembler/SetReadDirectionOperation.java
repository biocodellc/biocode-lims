package com.biomatters.plugins.moorea.assembler;

import com.biomatters.geneious.publicapi.documents.AnnotatedPluginDocument;
import com.biomatters.geneious.publicapi.documents.DocumentField;
import com.biomatters.geneious.publicapi.documents.sequence.NucleotideSequenceDocument;
import com.biomatters.geneious.publicapi.plugin.*;
import jebl.util.ProgressListener;

import java.util.List;

/**
 * @author Richard
 * @version $Id$
 */
public class SetReadDirectionOperation extends DocumentOperation {
    private static final Options.OptionValue FORWARD_OPTION_VALUE = new Options.OptionValue("forward", "Forward");
    private static final DocumentField IS_FORWARD_FIELD = DocumentField.createBooleanField("Is Forward Read",
            "Whether this read is in the forward direction", "isForwardRead", false, false);

    public GeneiousActionOptions getActionOptions() {
        return new GeneiousActionOptions("Set Read Direction...", "Mark sequences as forward or reverse reads so the correct reads are reverse complemented by assembly")
                .setMainMenuLocation(GeneiousActionOptions.MainMenu.Sequence);
    }

    public String getHelp() {
        return "Select one or more sequences to mark them as forward forward or reverse reads so the correct reads are reverse complemented by assembly.";
    }

    public DocumentSelectionSignature[] getSelectionSignatures() {
        return new DocumentSelectionSignature[] {
                new DocumentSelectionSignature(NucleotideSequenceDocument.class, 1, Integer.MAX_VALUE)
        };
    }

    @Override
    public List<AnnotatedPluginDocument> performOperation(AnnotatedPluginDocument[] annotatedDocuments,
                                                          ProgressListener progressListener, Options options) throws DocumentOperationException {
        progressListener.setIndeterminateProgress();
        boolean isForward = options.getValue("readDirection").equals(FORWARD_OPTION_VALUE);
        for (AnnotatedPluginDocument annotatedDocument : annotatedDocuments) {
            if (progressListener.isCanceled()) {
                return null;
            }
            annotatedDocument.setHiddenFieldValue(IS_FORWARD_FIELD, isForward);
            annotatedDocument.save();
        }
        return null;
    }

    @Override
    public Options getOptions(AnnotatedPluginDocument... documents) throws DocumentOperationException {
        Options o = new Options(SetReadDirectionOperation.class);
        o.addRadioOption("readDirection", "Read Direction:", new Options.OptionValue[] {
                FORWARD_OPTION_VALUE,
                new Options.OptionValue("reverse", "Reverse")
        }, FORWARD_OPTION_VALUE, Options.Alignment.HORIZONTAL_ALIGN);
        return o;
    }

    @Override
    public boolean isDocumentGenerator() {
        return false;
    }

    @Override
    public String getUniqueId() {
        return "setReadDirection";
    }
}
