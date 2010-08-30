package com.biomatters.plugins.biocode.assembler.annotate;

import com.biomatters.geneious.publicapi.documents.AnnotatedPluginDocument;
import com.biomatters.geneious.publicapi.documents.sequence.NucleotideSequenceDocument;
import com.biomatters.geneious.publicapi.documents.sequence.SequenceAlignmentDocument;
import com.biomatters.geneious.publicapi.plugin.*;
import com.biomatters.plugins.biocode.BiocodePlugin;
import com.biomatters.plugins.biocode.labbench.ConnectionException;
import jebl.util.ProgressListener;

import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: Steve
 * Date: 26/08/2010
 * Time: 2:58:03 AM
 * To change this template use File | Settings | File Templates.
 */
public class AnnotateFimsDataOperation extends DocumentOperation {
    public GeneiousActionOptions getActionOptions() {
        GeneiousActionOptions geneiousActionOptions = new GeneiousActionOptions("Annotate without LIMS...",
                "Annotate sequences/assemblies with data from the Field Information Management System, bypassing the LIMS")
                .setInPopupMenu(true, 0.2);
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
    public List<AnnotatedPluginDocument> performOperation(AnnotatedPluginDocument[] annotatedDocuments, ProgressListener progressListener, Options o) throws DocumentOperationException {
        AnnotateLimsDataOperation.FimsDataGetter fimsDataGetter = new AnnotateLimsDataOperation.FimsDataGetter() {
            @Override
            public AnnotateLimsDataOptions.FimsData getFimsData(AnnotatedPluginDocument document) throws DocumentOperationException {
                try {
                    //todo
                    return null;
                } catch (Exception e) {
                    throw new DocumentOperationException("Failed to connect to FIMS: " + e.getMessage(), e);
                }
            }
        };
        AnnotateLimsDataOperation.annotateFimsData(annotatedDocuments, progressListener, fimsDataGetter);
        return null;
    }


}
