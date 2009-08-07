package com.biomatters.plugins.moorea.assembler.annotate;

import com.biomatters.geneious.publicapi.documents.AnnotatedPluginDocument;
import com.biomatters.geneious.publicapi.documents.PluginDocument;
import com.biomatters.geneious.publicapi.plugin.*;
import jebl.util.ProgressListener;

import java.util.List;

/**
 * @author Richard
 * @version $Id$
 */
public class AnnotateFimsDataOperation extends DocumentOperation {

    public GeneiousActionOptions getActionOptions() {
        return new GeneiousActionOptions("Annotate With FIMS Data...",
                "Annotate sequences/assemblies with data from the Field Information Management System. eg. Taxonomy, Collector")
                .setMainMenuLocation(GeneiousActionOptions.MainMenu.Sequence);
    }

    public String getHelp() {
        return "Select one or more sequences or assemblies to attempt to annotate them with data from the FIMS (Field Information Management System).";
    }

    public DocumentSelectionSignature[] getSelectionSignatures() {
        return new DocumentSelectionSignature[] {
                new DocumentSelectionSignature(PluginDocument.class, 1, Integer.MAX_VALUE)
        };
    }

    @Override
    public Options getOptions(AnnotatedPluginDocument... documents) throws DocumentOperationException {
        return new AnnotateFimsDataOptions(documents);
    }

    @Override
    public boolean isDocumentGenerator() {
        return false;
    }

    @Override
    public List<AnnotatedPluginDocument> performOperation(AnnotatedPluginDocument[] annotatedDocuments, ProgressListener progressListener, Options options) throws DocumentOperationException {
        return super.performOperation(annotatedDocuments, progressListener, options);
    }
}
