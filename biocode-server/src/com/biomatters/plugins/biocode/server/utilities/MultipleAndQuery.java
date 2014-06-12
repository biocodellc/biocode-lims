package com.biomatters.plugins.biocode.server.utilities;

import com.biomatters.geneious.publicapi.databaseservice.CompoundSearchQuery;

/**
 * @author Gen Li
 *         Created on 12/06/14 2:10 PM
 */
class MultipleAndQuery extends MultipleQuery {
    MultipleAndQuery(QueryValues[] queryValueses) {
        super(queryValueses);
    }

    @Override
    CompoundSearchQuery.Operator getOperator() { return CompoundSearchQuery.Operator.AND; }
}