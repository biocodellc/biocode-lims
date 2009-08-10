package com.biomatters.plugins.moorea.assembler.verify;

import com.biomatters.geneious.publicapi.databaseservice.RetrieveCallback;
import com.biomatters.geneious.publicapi.documents.AnnotatedPluginDocument;
import com.biomatters.geneious.publicapi.documents.DocumentField;
import com.biomatters.geneious.publicapi.documents.DocumentUtilities;
import com.biomatters.geneious.publicapi.documents.PluginDocument;
import com.biomatters.geneious.publicapi.implementations.EValue;
import jebl.util.ProgressListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Richard
 * @version $Id$
 */
public class VerifyTaxonomyCallback extends RetrieveCallback {

    private final AnnotatedPluginDocument[] queries;
    private final String keywords;
    private final Map<AnnotatedPluginDocument, List<VerifyResult>> results = new HashMap<AnnotatedPluginDocument, List<VerifyResult>>();
    private int currentQuery = -1;
    private static final DocumentField CLOSEST_GENBANK_TAXON_FIELD = DocumentField.createStringField("Closest GenBank Taxon", "The taxonomy of the most similar sequence in GenBank, determined by BLAST", "closestGenbankTaxon", true, false);

    public VerifyTaxonomyCallback(AnnotatedPluginDocument[] queries, ProgressListener progressListener, String keywords) {
        super(progressListener);
        this.queries = queries;
        this.keywords = keywords;
    }

    protected void _add(PluginDocument document, Map<String, Object> searchResultProperties) {
        _add(DocumentUtilities.createAnnotatedPluginDocument(document), searchResultProperties);
    }

    protected void _add(AnnotatedPluginDocument document, Map<String, Object> searchResultProperties) {
        AnnotatedPluginDocument query = queries[currentQuery];
        query.setFieldValue(CLOSEST_GENBANK_TAXON_FIELD, document.getFieldValue(DocumentField.TAXONOMY_FIELD));
        query.save();
        List<VerifyResult> resultsForQuery = results.get(query);
        if (resultsForQuery == null) {
            resultsForQuery = new ArrayList<VerifyResult>();
            results.put(query, resultsForQuery);
        }
        resultsForQuery.add(new VerifyResult(document, (EValue)searchResultProperties.get("evalue")));
    }

    @Override
    public void setPropertyFields(List<DocumentField> searchResultProperties, DocumentField defaultSortingField) {
        currentQuery ++;
    }

    AnnotatedPluginDocument getResultsDocument() {
        return DocumentUtilities.createAnnotatedPluginDocument(new VerifyTaxonomyResultsDocument(results, keywords));
    }
}
