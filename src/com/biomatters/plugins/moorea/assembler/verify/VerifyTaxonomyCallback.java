package com.biomatters.plugins.moorea.assembler.verify;

import com.biomatters.geneious.publicapi.databaseservice.RetrieveCallback;
import com.biomatters.geneious.publicapi.documents.AnnotatedPluginDocument;
import com.biomatters.geneious.publicapi.documents.DocumentField;
import com.biomatters.geneious.publicapi.documents.DocumentUtilities;
import com.biomatters.geneious.publicapi.documents.PluginDocument;
import jebl.util.ProgressListener;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Stack;

/**
 * @author Richard
 * @version $Id$
 */
public class VerifyTaxonomyCallback extends RetrieveCallback {

    private final Stack<Pair<AnnotatedPluginDocument, BiocodeTaxon>> queries = new Stack<Pair<AnnotatedPluginDocument, BiocodeTaxon>>();
    private final String keywords;
    private final List<VerifyResult> results = new ArrayList<VerifyResult>();
    private static final DocumentField CLOSEST_GENBANK_TAXON_FIELD = DocumentField.createStringField("Closest GenBank Taxon", "The taxonomy of the most similar sequence in GenBank, determined by BLAST", "closestGenbankTaxon", true, false);

    public VerifyTaxonomyCallback(List<Pair<AnnotatedPluginDocument, BiocodeTaxon>> queries, ProgressListener progressListener, String keywords) {
        super(progressListener);
        for (int i = queries.size() - 1; i >= 0 ; i--) {
            this.queries.push(queries.get(i));
        }
        this.keywords = keywords;
    }

    protected void _add(PluginDocument document, Map<String, Object> searchResultProperties) {
        _add(DocumentUtilities.createAnnotatedPluginDocument(document), searchResultProperties);
    }

    protected void _add(AnnotatedPluginDocument document, Map<String, Object> searchResultProperties) {
        VerifyResult currentResult = results.get(results.size() - 1);
        if (currentResult.hitDocuments.isEmpty()) {
            currentResult.queryDocument.setFieldValue(CLOSEST_GENBANK_TAXON_FIELD, document.getFieldValue(DocumentField.TAXONOMY_FIELD));
            currentResult.queryDocument.save();
        }

        currentResult.addHit(document);
    }

    @Override
    public void setPropertyFields(List<DocumentField> searchResultProperties, DocumentField defaultSortingField) {
        Pair<AnnotatedPluginDocument, BiocodeTaxon> documentTaxonPair = queries.pop();
        results.add(new VerifyResult(new ArrayList<AnnotatedPluginDocument>(), documentTaxonPair.getItemA(), documentTaxonPair.getItemB()));
    }

    VerifyTaxonomyResultsDocument getResultsDocument() {
        return new VerifyTaxonomyResultsDocument(results, keywords);
    }
}
