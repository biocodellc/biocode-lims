package com.biomatters.plugins.biocode;

import com.biomatters.geneious.publicapi.databaseservice.DatabaseServiceException;
import com.biomatters.geneious.publicapi.documents.AnnotatedPluginDocument;
import com.biomatters.geneious.publicapi.documents.sequence.SequenceDocument;
import com.biomatters.geneious.publicapi.plugin.*;
import com.biomatters.geneious.publicapi.utilities.SequenceUtilities;
import com.biomatters.plugins.biocode.labbench.BiocodeService;
import com.biomatters.plugins.biocode.labbench.lims.LIMSConnection;
import jebl.util.CompositeProgressListener;
import jebl.util.ProgressListener;

import java.util.*;

/**
 *
 * @author Gen Li
 *         Created on 18/02/15 5:21 PM
 */
public class ReverseAssemblySequencesOperation extends DocumentOperation {
    public GeneiousActionOptions getActionOptions() {
        GeneiousActionOptions geneiousActionOptions = new GeneiousActionOptions("Reverse Assembly Sequences", "").setInPopupMenu(true, 1);
        return GeneiousActionOptions.createSubmenuActionOptions(BiocodePlugin.getSuperBiocodeAction(), geneiousActionOptions);
    }

    public DocumentSelectionSignature[] getSelectionSignatures() {
        return new DocumentSelectionSignature[] { new DocumentSelectionSignature(SequenceDocument.class, 1, Integer.MAX_VALUE) };
    }

    @Override
    public void performOperation(AnnotatedPluginDocument[] annotatedDocuments, ProgressListener progressListener, Options options, SequenceSelection sequenceSelection, OperationCallback callback) throws DocumentOperationException {
        Map<Integer, String> assemblyIDToAssemblySequenceToSet = new HashMap<Integer, String>();
        CompositeProgressListener operationProgress = new CompositeProgressListener(progressListener, 2);

        operationProgress.beginSubtask("Generating reversed sequences...");
        for (AnnotatedPluginDocument annotatedDocument : annotatedDocuments) {
            if (!SequenceDocument.class.isAssignableFrom(annotatedDocument.getDocumentClass())) {
                throw new DocumentOperationException("Invalid document type. Expected: " + SequenceDocument.class.getSimpleName() + ", actual: " + annotatedDocument.getDocumentClass().getSimpleName() + ".");
            }

            SequenceDocument sequenceDocument = (SequenceDocument)annotatedDocument.getDocument();

            assemblyIDToAssemblySequenceToSet.put(
                    (Integer)annotatedDocument.getFieldValue(LIMSConnection.SEQUENCE_ID.getCode()),
                    SequenceUtilities.reverseComplement(sequenceDocument.getCharSequence()).toString()
            );
        }

        try {
            operationProgress.beginSubtask("Saving reversed sequences to the lims database...");
            BiocodeService.getInstance().getActiveLIMSConnection().setAssemblySequences(assemblyIDToAssemblySequenceToSet, progressListener);
        } catch (DatabaseServiceException e) {
            throw new DocumentOperationException(e.getMessage(), e);
        }
    }

    public String getHelp() {
        return "Select assembly sequences to reverse.";
    }
}