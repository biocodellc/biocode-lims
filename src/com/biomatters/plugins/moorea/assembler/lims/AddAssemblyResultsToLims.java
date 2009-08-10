package com.biomatters.plugins.moorea.assembler.lims;

import com.biomatters.geneious.publicapi.documents.AnnotatedPluginDocument;
import com.biomatters.geneious.publicapi.plugin.*;
import com.biomatters.plugins.moorea.MooreaPlugin;
import jebl.util.ProgressListener;

import java.util.List;

/**
 * @author Richard
 * @version $Id$
 */
public class AddAssemblyResultsToLims extends DocumentOperation {

    public GeneiousActionOptions getActionOptions() {
        GeneiousActionOptions geneiousActionOptions = new GeneiousActionOptions("Add Assembly Results to LIMS...")
                .setInPopupMenu(true, 0.6);
        return GeneiousActionOptions.createSubmenuActionOptions(MooreaPlugin.getSuperBiocodeAction(), geneiousActionOptions);
    }

    public String getHelp() {
        return ""; //todo
    }

    public DocumentSelectionSignature[] getSelectionSignatures() {
        return new DocumentSelectionSignature[0];
    }

    @Override
    public boolean isDocumentGenerator() {
        return false;
    }

    @Override
    public List<AnnotatedPluginDocument> performOperation(AnnotatedPluginDocument[] annotatedDocuments, ProgressListener progressListener, Options options) throws DocumentOperationException {
        throw new DocumentOperationException("Coming soon");
    }

    @Override
    public Options getOptions(AnnotatedPluginDocument... documents) throws DocumentOperationException {
        return super.getOptions(documents);
    }
}
