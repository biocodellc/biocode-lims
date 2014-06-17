package com.biomatters.plugins.biocode.server.utilities.query;

import com.biomatters.geneious.publicapi.databaseservice.CompoundSearchQuery;

/**
 * @author Gen Li
 *         Created on 12/06/14 2:10 PM
 */
public class MultipleOrQuery extends MultipleQuery {
    public MultipleOrQuery(QueryValues[] queryValueses) {
        super(queryValueses);
    }

    @Override
    protected CompoundSearchQuery.Operator getOperator() { return CompoundSearchQuery.Operator.OR; }
}
