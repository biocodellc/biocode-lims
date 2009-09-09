package com.biomatters.plugins.moorea.submission.genbank.barstool;

import com.biomatters.geneious.publicapi.documents.AnnotatedPluginDocument;
import com.biomatters.geneious.publicapi.documents.sequence.SequenceAlignmentDocument;
import com.biomatters.geneious.publicapi.plugin.*;
import com.biomatters.plugins.moorea.MooreaPlugin;
import com.biomatters.plugins.moorea.MooreaUtilities;
import com.biomatters.plugins.moorea.labbench.MooreaLabBenchService;
import jebl.util.ProgressListener;

import java.util.List;

/**
 * @author Richard
 * @version $Id$
 */
public class ExportForBarstoolOperation extends DocumentOperation {

    private final boolean isAutomated;

    public ExportForBarstoolOperation(boolean isAutomated) {
        this.isAutomated = isAutomated;
    }

    public GeneiousActionOptions getActionOptions() {
        GeneiousActionOptions geneiousActionOptions = new GeneiousActionOptions("Export for BarSTool Submission...")
                .setInPopupMenu(true, 0.7);
        return GeneiousActionOptions.createSubmenuActionOptions(MooreaPlugin.getSuperBiocodeAction(), geneiousActionOptions);
    }

    public String getHelp() {
        return "Select several contigs or alignments of contigs to export the files necessary for submission through GenBank's BarSTool";
    }

    public DocumentSelectionSignature[] getSelectionSignatures() {
        return new DocumentSelectionSignature[] {
                new DocumentSelectionSignature(SequenceAlignmentDocument.class, 1, Integer.MAX_VALUE)
        };
    }

    @Override
    public Options getOptions(AnnotatedPluginDocument... documents) throws DocumentOperationException {
        if (!MooreaLabBenchService.getInstance().isLoggedIn()) {
            throw new DocumentOperationException(MooreaUtilities.NOT_CONNECTED_ERROR_MESSAGE);
        }
        return new ExportForBarstoolOptions(documents);
    }

    @Override
    public List<AnnotatedPluginDocument> performOperation(AnnotatedPluginDocument[] docs, ProgressListener progressListener, Options o) throws DocumentOperationException {
        new BarstoolExportHandler(docs, (ExportForBarstoolOptions)o, progressListener, isAutomated);
        return null;
    }

    @Override
    public boolean isDocumentGenerator() {
        return false;
    }
}
