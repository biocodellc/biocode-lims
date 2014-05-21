package com.biomatters.plugins.biocode.assembler.lims;

import com.biomatters.geneious.publicapi.databaseservice.DatabaseServiceException;
import com.biomatters.geneious.publicapi.plugin.*;
import com.biomatters.geneious.publicapi.documents.sequence.NucleotideSequenceDocument;
import com.biomatters.geneious.publicapi.documents.sequence.SequenceAlignmentDocument;
import com.biomatters.geneious.publicapi.documents.sequence.SequenceDocument;
import com.biomatters.geneious.publicapi.documents.AnnotatedPluginDocument;
import com.biomatters.plugins.biocode.BiocodePlugin;
import com.biomatters.plugins.biocode.BiocodeUtilities;
import com.biomatters.plugins.biocode.labbench.lims.LIMSConnection;
import com.biomatters.plugins.biocode.labbench.BiocodeService;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;

import jebl.util.ProgressListener;

/**
 * @author Steve
 * @version $Id$
 */
public class MarkSequencesAsSubmittedInLimsOperation extends DocumentOperation {


    public GeneiousActionOptions getActionOptions() {
        GeneiousActionOptions actionOptions = new GeneiousActionOptions("Mark as Submitted in LIMS...", "Mark the selected sequences as submitted to a sequence database (e.g. Genbank).  The sequences must first have been marked as passed").setInPopupMenu(true, 0.67).setProOnly(true);

        return GeneiousActionOptions.createSubmenuActionOptions(BiocodePlugin.getSuperBiocodeAction(), actionOptions);
    }

    public String getHelp() {
        return "Select contigs, or consensus alignments to mark as submitted to Genbank in the LIMS";
    }

    @Override
    public boolean isDocumentGenerator() {
        return false;
    }

    @Override
    public Options getOptions(SequenceSelection sequenceSelection, AnnotatedPluginDocument... documents) throws DocumentOperationException {
        if(!BiocodeService.getInstance().isLoggedIn()) {
            throw new DocumentOperationException("You need to connect to the LIMS service");
        }
        Map<AnnotatedPluginDocument,SequenceDocument> docsToMark = MarkInLimsUtilities.getDocsToMark(documents, sequenceSelection);
        for(AnnotatedPluginDocument doc : docsToMark.keySet()) {
            if(doc.getFieldValue(LIMSConnection.SEQUENCE_ID) == null) {
                throw new DocumentOperationException("At least one of your sequences does not have a record of having been marked as passed in the LIMS.  Make sure that you have marked all sequences as passed before marking them as submitted.");
            }
        }

        Options options = new Options(this.getClass());
        Options.OptionValue[] values = new Options.OptionValue[] {
                new Options.OptionValue("Yes", "Submitted"),
                new Options.OptionValue("No", "Not Submitted")
        };
        options.addComboBoxOption("markValue", "Mark sequences as", values, values[0]);
        return options;
    }

    @Override
    public List<AnnotatedPluginDocument> performOperation(AnnotatedPluginDocument[] annotatedDocuments, ProgressListener progressListener, Options options, SequenceSelection sequenceSelection) throws DocumentOperationException {
        if(!BiocodeService.getInstance().isLoggedIn()) {
            throw new DocumentOperationException(BiocodeUtilities.NOT_CONNECTED_ERROR_MESSAGE);
        }
        Map<AnnotatedPluginDocument,SequenceDocument> docsToMark = MarkInLimsUtilities.getDocsToMark(annotatedDocuments, sequenceSelection);
        boolean submitted = options.getValueAsString("markValue").equals("Yes");
        List<Integer> ids = new ArrayList<Integer>();
        for(AnnotatedPluginDocument doc : docsToMark.keySet()) {
            Object fieldValue = doc.getFieldValue(LIMSConnection.SEQUENCE_ID);
            if(fieldValue == null) {
                continue;
            }
            if(fieldValue instanceof Integer) {
                ids.add((Integer) fieldValue);
            } else if(fieldValue instanceof String) {
                try {
                    ids.add(Integer.parseInt((String)fieldValue));
                } catch (NumberFormatException e) {
                    // Not an int, ignore
                }
            }
        }

        try {
            BiocodeService.getInstance().getActiveLIMSConnection().setSequenceStatus(submitted, ids);
        } catch (DatabaseServiceException e) {
            throw new DocumentOperationException(e.getMessage(), e);
        }
        return null;
    }

    public DocumentSelectionSignature[] getSelectionSignatures() {
        return new DocumentSelectionSignature[] {
                new DocumentSelectionSignature(new DocumentSelectionSignature.DocumentSelectionSignatureAtom[] {
                        new DocumentSelectionSignature.DocumentSelectionSignatureAtom(SequenceAlignmentDocument.class, 1, Integer.MAX_VALUE),
                        new DocumentSelectionSignature.DocumentSelectionSignatureAtom(NucleotideSequenceDocument.class, 0, Integer.MAX_VALUE)
                }),
                new DocumentSelectionSignature(new DocumentSelectionSignature.DocumentSelectionSignatureAtom[] {
                        new DocumentSelectionSignature.DocumentSelectionSignatureAtom(SequenceAlignmentDocument.class, 0, Integer.MAX_VALUE),
                        new DocumentSelectionSignature.DocumentSelectionSignatureAtom(NucleotideSequenceDocument.class, 1, Integer.MAX_VALUE)
                })
        };
    }

}
