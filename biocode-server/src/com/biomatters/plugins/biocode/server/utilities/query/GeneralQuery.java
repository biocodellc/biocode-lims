package com.biomatters.plugins.biocode.server.utilities.query;

import com.biomatters.core.documents.SearchQuery;
import com.biomatters.geneious.publicapi.databaseservice.Query;

import java.util.Map;


/**
 * @author Gen Li
 *         Created on 17/06/14 4:12 PM
 */
public class GeneralQuery extends BasicQuery {
    private String queryContent;

    public GeneralQuery(String queryContent) { this.queryContent = queryContent; }

    @Override
    protected Query createGeneiousQuery(Map<String, Object> tissuesWorkflowsPlatesSequences) {
        return new SearchQuery.BasicQueryWithExtendedOptions(tissuesWorkflowsPlatesSequences, queryContent);
    }
}
