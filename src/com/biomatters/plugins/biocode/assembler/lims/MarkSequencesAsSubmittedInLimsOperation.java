package com.biomatters.plugins.biocode.assembler.lims;

import com.biomatters.geneious.publicapi.plugin.*;
import com.biomatters.geneious.publicapi.documents.sequence.NucleotideSequenceDocument;
import com.biomatters.geneious.publicapi.documents.sequence.SequenceAlignmentDocument;
import com.biomatters.geneious.publicapi.documents.sequence.SequenceDocument;
import com.biomatters.geneious.publicapi.documents.AnnotatedPluginDocument;
import com.biomatters.geneious.publicapi.utilities.StringUtilities;
import com.biomatters.plugins.biocode.BiocodePlugin;
import com.biomatters.plugins.biocode.BiocodeUtilities;
import com.biomatters.plugins.biocode.labbench.lims.LimsConnectionOptions;
import com.biomatters.plugins.biocode.labbench.lims.LIMSConnection;
import com.biomatters.plugins.biocode.labbench.BiocodeService;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.ResultSet;

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
        List<String> strings = new ArrayList<String>();
        for(AnnotatedPluginDocument doc : docsToMark.keySet()) {
            ids.add((Integer)doc.getFieldValue(LIMSConnection.SEQUENCE_ID));
            strings.add("id=?");
        }

        String idOr = StringUtilities.join(" OR ", strings);




        try {
            PreparedStatement statement1 = BiocodeService.getInstance().getActiveLIMSConnection().createStatement("SELECT COUNT(*) FROM assembly WHERE ("+idOr+") AND progress=?");
            for(int i=0; i < ids.size(); i++) {
                statement1.setInt(i+1, ids.get(i));
            }
            statement1.setString(ids.size()+1, "passed");
            ResultSet set = statement1.executeQuery();
            set.next();
            int count = set.getInt(1);

            if(count < ids.size()) {
                throw new DocumentOperationException("Some of the sequences you are marking are either not present in the database, or are marked as failed.  Please make sure that the sequences are present, and are passed before marking as submitted.");
            }
            


            if(LIMSConnection.EXPECTED_SERVER_VERSION < 9) {
                throw new DocumentOperationException("You need to be running against a more recent version of the Biocode LIMS database to mark sequences as submitted");
            }
            PreparedStatement statement2 = BiocodeService.getInstance().getActiveLIMSConnection().createStatement("UPDATE assembly SET submitted = ? WHERE "+idOr);
            statement2.setInt(1, submitted ? 1 : 0);
            for(int i=0; i < ids.size(); i++) {
                statement2.setInt(i+2, ids.get(i));
            }
            statement2.executeUpdate();

        } catch (SQLException e) {
            e.printStackTrace();
            throw new DocumentOperationException("There was a problem marking as submitted in LIMS: "+e.getMessage());
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
