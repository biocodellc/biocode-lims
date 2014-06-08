package com.biomatters.plugins.biocode.server.utilities;

import com.biomatters.geneious.publicapi.databaseservice.DatabaseServiceException;
import com.biomatters.geneious.publicapi.documents.Condition;
import com.biomatters.geneious.publicapi.documents.DocumentField;
import com.biomatters.plugins.biocode.labbench.BiocodeService;
import com.biomatters.plugins.biocode.labbench.lims.LimsSearchResult;

import java.util.Map;
import java.util.Set;

/**
 * @author Gen Li
 *         Created on 5/06/14 12:01 PM
 */
class BasicQuery extends Query {
    private DocumentField searchAttribute;
    private Condition condition;
    private Object value;

    BasicQuery(DocumentField searchAttribute, Condition condition, Object value) {
        this.searchAttribute = searchAttribute;
        this.condition = condition;
        this.value = value;
    }

    @Override
    LimsSearchResult execute(Map<String, Object> tissuesWorkflowsPlatesSequences, Set<String> tissuesToMatch) throws DatabaseServiceException {
        com.biomatters.geneious.publicapi.databaseservice.Query query = com.biomatters.geneious.publicapi.databaseservice.Query.Factory.createFieldQuery(searchAttribute, condition, new Object[]{ value }, tissuesWorkflowsPlatesSequences);
        return BiocodeService.getInstance().getActiveLIMSConnection().getMatchingDocumentsFromLims(query, tissuesToMatch, null);
    }
}