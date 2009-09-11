package com.biomatters.plugins.biocode.assembler.verify;

import com.biomatters.geneious.publicapi.components.Dialogs;
import com.biomatters.geneious.publicapi.databaseservice.DatabaseService;
import com.biomatters.geneious.publicapi.databaseservice.DatabaseServiceException;
import com.biomatters.geneious.publicapi.documents.AnnotatedPluginDocument;
import com.biomatters.geneious.publicapi.documents.DocumentField;
import com.biomatters.geneious.publicapi.documents.DocumentUtilities;
import com.biomatters.geneious.publicapi.documents.sequence.SequenceAlignmentDocument;
import com.biomatters.geneious.publicapi.documents.types.TaxonomyDocument;
import com.biomatters.geneious.publicapi.plugin.*;
import com.biomatters.plugins.biocode.BiocodePlugin;
import com.biomatters.plugins.biocode.BiocodeUtilities;
import jebl.util.CompositeProgressListener;
import jebl.util.ProgressListener;

import java.util.*;

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
        return GeneiousActionOptions.createSubmenuActionOptions(BiocodePlugin.getSuperBiocodeAction(), geneiousActionOptions);
    }

    public String getHelp() {
        return "Select one or more contigs or alignments of contigs to verify taxonomy and locus using a batch BLAST.";
    }

    public DocumentSelectionSignature[] getSelectionSignatures() {
        return new DocumentSelectionSignature[] {
                new DocumentSelectionSignature(SequenceAlignmentDocument.class, 1, Integer.MAX_VALUE)
        };
    }

    @Override
    public Options getOptions(AnnotatedPluginDocument... documents) throws DocumentOperationException {
        return new VerifyTaxonomyOptions(documents);
    }

    @Override
    public List<AnnotatedPluginDocument> performOperation(AnnotatedPluginDocument[] annotatedDocs, ProgressListener progressListener, Options o) throws DocumentOperationException {
        VerifyTaxonomyOptions options = (VerifyTaxonomyOptions) o;
        Map<AnnotatedPluginDocument, String> contigMap = BiocodeUtilities.getContigDocuments(annotatedDocs);
        List<AnnotatedPluginDocument> queries = options.getQueries(contigMap);
        CompositeProgressListener progress = new CompositeProgressListener(progressListener, 0.2, 0.8);
        progress.beginSubtask("Retrieving full taxonomies");
        progress.setIndeterminateProgress();
        List<Pair<AnnotatedPluginDocument, BiocodeTaxon>> annotatedDocsWithTaxons = fillInTaxonomyFromNcbi(contigMap.keySet(), progress);
        if (progress.isCanceled()) return null;
        DatabaseService database = options.getDatabase();
        progress.beginSubtask();
        VerifyTaxonomyCallback callback = new VerifyTaxonomyCallback(annotatedDocsWithTaxons, progressListener, options.getKeywords());
        try {
            database.batchSequenceSearch(queries, options.getProgram(), options.getSearchOptions(), callback);
        } catch (DatabaseServiceException e) {
            throw new DocumentOperationException("BLAST search failed: " + e.getMessage(), e);
        }
        VerifyTaxonomyResultsDocument resultsDocument = callback.getResultsDocument();
        return Collections.singletonList(DocumentUtilities.createAnnotatedPluginDocument(resultsDocument));
    }

    private static final Map<String, BiocodeTaxon> TAXON_CACHE = new HashMap<String, BiocodeTaxon>();

    private static List<Pair<AnnotatedPluginDocument, BiocodeTaxon>> fillInTaxonomyFromNcbi(Set<AnnotatedPluginDocument> contigs, CompositeProgressListener progress) throws DocumentOperationException {
        GeneiousService taxonomyService = PluginUtilities.getGeneiousService("NCBI_taxonomy");
        if (!(taxonomyService instanceof DatabaseService)) {
            throw new DocumentOperationException("Could not find NCBI Taxonomy service. Make sure the NCBI Plugin is enabled.");
        }
        DatabaseService taxonomyDatabase = (DatabaseService) taxonomyService;
        List<Pair<AnnotatedPluginDocument, BiocodeTaxon>> taxons = new ArrayList<Pair<AnnotatedPluginDocument, BiocodeTaxon>>();
        for (AnnotatedPluginDocument query : contigs) {
            if (progress.isCanceled()) return null;
            Object taxonomyObject = query.getFieldValue(DocumentField.TAXONOMY_FIELD);
            if (taxonomyObject == null) {
                taxons.add(new Pair<AnnotatedPluginDocument, BiocodeTaxon>(query, null));
                continue;
            }
            String taxonomy = taxonomyObject.toString();
            int lastSemicolon = taxonomy.lastIndexOf(';');
            final String lowestTaxon;
            if (lastSemicolon == -1) {
                lowestTaxon = taxonomy;
            } else {
                lowestTaxon = taxonomy.substring(lastSemicolon + 1, taxonomy.length());
            }
            BiocodeTaxon taxon = null;
            if (TAXON_CACHE.containsKey(lowestTaxon.toLowerCase())) {
                taxon = TAXON_CACHE.get(lowestTaxon.toLowerCase());
            } else {
                List<AnnotatedPluginDocument> taxonomyDocuments = taxonomyDatabase.retrieve(lowestTaxon);
                if (taxonomyDocuments.isEmpty()) {
                    //this will be reported in the output document
                } else if (taxonomyDocuments.size() != 1) {
                    new Thread("Tell about multiple taxa found") {
                        public void run() {
                            //pretty crude but i'm hoping this won't happen
                            Dialogs.showMessageDialog("More than one taxon found at NCBI for " + lowestTaxon);
                        }
                    }.start();
                } else {
                    taxon = BiocodeTaxon.fromNcbiTaxon(((TaxonomyDocument) taxonomyDocuments.get(0).getDocument()).getTaxon());
                }
            }
            if (taxon != null) {
                query.setFieldValue(DocumentField.TAXONOMY_FIELD, taxon.toString());
                query.saveDocument();
            }
            TAXON_CACHE.put(lowestTaxon.toLowerCase(), taxon);
            taxons.add(new Pair<AnnotatedPluginDocument, BiocodeTaxon>(query, taxon));
        }
        return taxons;
    }
}