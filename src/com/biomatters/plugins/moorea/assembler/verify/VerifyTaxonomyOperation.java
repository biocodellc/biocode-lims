package com.biomatters.plugins.moorea.assembler.verify;

import com.biomatters.geneious.publicapi.databaseservice.DatabaseService;
import com.biomatters.geneious.publicapi.databaseservice.DatabaseServiceException;
import com.biomatters.geneious.publicapi.documents.AnnotatedPluginDocument;
import com.biomatters.geneious.publicapi.documents.sequence.NucleotideSequenceDocument;
import com.biomatters.geneious.publicapi.documents.sequence.SequenceAlignmentDocument;
import com.biomatters.geneious.publicapi.plugin.*;
import com.biomatters.plugins.moorea.MooreaPlugin;
import jebl.util.ProgressListener;

import java.util.Collections;
import java.util.List;

/**
 * @author Richard
 * @version $Id$
 */
public class VerifyTaxonomyOperation extends DocumentOperation {

    public GeneiousActionOptions getActionOptions() {
        GeneiousActionOptions geneiousActionOptions = new GeneiousActionOptions("Verify Taxonomy...",
                "Perform a batch BLAST search to verify the taxonomy and locus of sequencing results")
                .setInPopupMenu(true, 0.3)
                .setProOnly(true);
        return GeneiousActionOptions.createSubmenuActionOptions(MooreaPlugin.getSuperBiocodeAction(), geneiousActionOptions);
    }

    public String getHelp() {
        return "Select one or more sequences or assemblies to verify taxonomy and locus using a batch BLAST.";
    }

    public DocumentSelectionSignature[] getSelectionSignatures() {
        return new DocumentSelectionSignature[] {
                new DocumentSelectionSignature(NucleotideSequenceDocument.class, 1, Integer.MAX_VALUE),
                new DocumentSelectionSignature(SequenceAlignmentDocument.class, 1, Integer.MAX_VALUE)
        };
    }

    @Override
    public Options getOptions(AnnotatedPluginDocument... documents) throws DocumentOperationException {
        return new VerifyTaxonomyOptions(documents);
    }

    @Override
    public List<AnnotatedPluginDocument> performOperation(AnnotatedPluginDocument[] annotatedDocuments, ProgressListener progressListener, Options o) throws DocumentOperationException {
        VerifyTaxonomyOptions options = (VerifyTaxonomyOptions) o;
        List<AnnotatedPluginDocument> queries = options.getQueries(annotatedDocuments);
        DatabaseService database = options.getDatabase();
        VerifyTaxonomyCallback callback = new VerifyTaxonomyCallback(annotatedDocuments, progressListener, options.getKeywords());
        try {
            database.batchSequenceSearch(queries, options.getProgram(), options.getSearchOptions(), callback);
        } catch (DatabaseServiceException e) {
            throw new DocumentOperationException("BLAST search failed: " + e.getMessage(), e);
        }
        return Collections.singletonList(callback.getResultsDocument());
    }
}
