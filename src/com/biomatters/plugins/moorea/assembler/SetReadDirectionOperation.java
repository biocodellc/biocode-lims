package com.biomatters.plugins.moorea.assembler;

import com.biomatters.geneious.publicapi.documents.AnnotatedPluginDocument;
import com.biomatters.geneious.publicapi.documents.DocumentField;
import com.biomatters.geneious.publicapi.documents.sequence.NucleotideSequenceDocument;
import com.biomatters.geneious.publicapi.plugin.*;
import com.biomatters.plugins.moorea.MooreaPlugin;
import jebl.util.ProgressListener;

import java.util.List;

/**
 * @author Richard
 * @version $Id$
 */
public class SetReadDirectionOperation extends DocumentOperation {
    private static final Options.OptionValue FORWARD_OPTION_VALUE = new Options.OptionValue("forward", "Forward");
    public static final DocumentField IS_FORWARD_FIELD = DocumentField.createBooleanField("Is Forward Read",
            "Whether this read is in the forward direction", "isForwardRead", true, false);

    public GeneiousActionOptions getActionOptions() {
        GeneiousActionOptions geneiousActionOptions = new GeneiousActionOptions("Set Read Direction...", "Mark sequences as forward or reverse reads so the correct reads are reverse complemented by assembly")
                .setInPopupMenu(true, 0.1);
        return GeneiousActionOptions.createSubmenuActionOptions(MooreaPlugin.getSuperBiocodeAction(), geneiousActionOptions);
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
        progressListener.setMessage("Setting read direction...");
        boolean isForward = options.getValue("readDirection").equals(FORWARD_OPTION_VALUE);
        for (AnnotatedPluginDocument annotatedDocument : annotatedDocuments) {
            if (progressListener.isCanceled()) {
                return null;
            }
            annotatedDocument.setFieldValue(IS_FORWARD_FIELD, isForward);
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
