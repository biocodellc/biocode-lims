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

    private final List<AnnotatedPluginDocument> queries;
    private final Map<AnnotatedPluginDocument, List<VerifyResult>> results = new HashMap<AnnotatedPluginDocument, List<VerifyResult>>();
    private int currentQuery = -1;

    public VerifyTaxonomyCallback(List<AnnotatedPluginDocument> queries, ProgressListener progressListener) {
        super(progressListener);
        this.queries = queries;
    }

    protected void _add(PluginDocument document, Map<String, Object> searchResultProperties) {
        _add(DocumentUtilities.createAnnotatedPluginDocument(document), searchResultProperties);
    }

    protected void _add(AnnotatedPluginDocument document, Map<String, Object> searchResultProperties) {
        AnnotatedPluginDocument query = queries.get(currentQuery);
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
        return DocumentUtilities.createAnnotatedPluginDocument(new VerifyTaxonomyResultsDocument(results));
    }
}
