package com.biomatters.plugins.biocode.server.utilities;

import java.util.Map;

/**
 * @author Gen Li
 *         Created on 12/06/14 2:48 PM
 */
class SingleQuery extends BasicQuery {
    private QueryValues queryValues;

    SingleQuery(QueryValues queryValues) {
        this.queryValues = queryValues;
    }

    com.biomatters.geneious.publicapi.databaseservice.Query createGeneiousQuery(Map<String, Object> tissuesWorkflowsPlatesSequences) {
        return com.biomatters.geneious.publicapi.databaseservice.Query.Factory.createFieldQuery(queryValues.getSearchAttribute(),
                                                                                                queryValues.getCondition(),
                                                                                                new Object[] { queryValues.getValue() },
                                                                                                tissuesWorkflowsPlatesSequences);
    }
}