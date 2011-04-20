package com.biomatters.plugins.biocode.assembler.annotate;

import com.biomatters.geneious.publicapi.documents.*;
import com.biomatters.geneious.publicapi.documents.sequence.NucleotideSequenceDocument;
import com.biomatters.geneious.publicapi.documents.sequence.SequenceAlignmentDocument;
import com.biomatters.geneious.publicapi.plugin.*;
import com.biomatters.plugins.biocode.BiocodePlugin;
import com.biomatters.plugins.biocode.BiocodeUtilities;
import com.biomatters.plugins.biocode.labbench.BiocodeService;
import jebl.util.ProgressListener;

import java.util.*;

/**
 * @author Richard
 * @version $Id$
 */
public class AnnotateLimsDataOperation extends DocumentOperation {

    public GeneiousActionOptions getActionOptions() {
        GeneiousActionOptions geneiousActionOptions = new GeneiousActionOptions("Annotate with FIMS/LIMS Data...",
                "Annotate sequences/assemblies with data from the Lab and Field Information Management Systems. eg. Taxonomy, Collector, Primers used")
                .setInPopupMenu(true, 0.22);
        return GeneiousActionOptions.createSubmenuActionOptions(BiocodePlugin.getSuperBiocodeAction(), geneiousActionOptions);
    }

    public String getHelp() {
        return "Select one or more sequencing reads to annotate them with data from the FIMS (Field Information Management System) and LIMS (Lab Information Managment System).";
    }

    public DocumentSelectionSignature[] getSelectionSignatures() {
        return new DocumentSelectionSignature[] {
                new DocumentSelectionSignature(NucleotideSequenceDocument.class, 1, Integer.MAX_VALUE),
                new DocumentSelectionSignature(SequenceAlignmentDocument.class,1, Integer.MAX_VALUE)
        };
    }

    @Override
    public Options getOptions(AnnotatedPluginDocument... documents) throws DocumentOperationException {
        if (!BiocodeService.getInstance().isLoggedIn()) {
            throw new DocumentOperationException(BiocodeUtilities.NOT_CONNECTED_ERROR_MESSAGE);
        }
        return new AnnotateLimsDataOptions(documents);
    }

    @Override
    public boolean isDocumentGenerator() {
        return false;
    }

    @Override
    public List<AnnotatedPluginDocument> performOperation(AnnotatedPluginDocument[] annotatedDocuments, ProgressListener progressListener, Options o) throws DocumentOperationException {
        if(!BiocodeService.getInstance().isLoggedIn()) {
            throw new DocumentOperationException(BiocodeUtilities.NOT_CONNECTED_ERROR_MESSAGE);
        }
        final AnnotateLimsDataOptions options = (AnnotateLimsDataOptions) o;
        FimsDataGetter fimsDataGetter = new FimsDataGetter() {
            public FimsData getFimsData(AnnotatedPluginDocument document) throws DocumentOperationException {
                return options.getFimsData(document);
            }
        };
        AnnotateUtilities.annotateFimsData(annotatedDocuments, progressListener, fimsDataGetter);
        return null;
    }

}
