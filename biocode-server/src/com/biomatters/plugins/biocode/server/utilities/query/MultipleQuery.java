package com.biomatters.plugins.biocode.server.utilities.query;

import com.biomatters.core.documents.SearchQuery;
import com.biomatters.geneious.publicapi.databaseservice.*;
import com.biomatters.geneious.publicapi.databaseservice.Query;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author Gen Li
 *         Created on 12/06/14 3:16 PM
 */
public abstract class MultipleQuery extends BasicQuery {
    protected QueryValues[] queryValueses;

    public MultipleQuery(QueryValues[] queryValueses) {
        this.queryValueses = queryValueses;
    }

    @Override
    protected com.biomatters.geneious.publicapi.databaseservice.Query createGeneiousQuery(Map<String, Object> tissuesWorkflowsPlatesSequences) {
        List<com.biomatters.geneious.publicapi.databaseservice.Query> geneiousQueries = new ArrayList<Query>();

        for (QueryValues queryValues : queryValueses) {
            geneiousQueries.add(com.biomatters.geneious.publicapi.databaseservice.Query.Factory.createFieldQuery(queryValues.getSearchAttribute(),
                                                                                                                 queryValues.getCondition(),
                                                                                                                 new Object[] { queryValues.getValue() },
                                                                                                                 tissuesWorkflowsPlatesSequences));
        }

        return new SearchQuery.CompoundQueryWithExtendedOptions(tissuesWorkflowsPlatesSequences,
                                                                geneiousQueries.toArray(new com.biomatters.geneious.publicapi.databaseservice.Query[geneiousQueries.size()]),
                                                                getOperator());
    }

    protected abstract CompoundSearchQuery.Operator getOperator();
}