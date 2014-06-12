package com.biomatters.plugins.biocode.server.utilities;

import com.biomatters.geneious.publicapi.documents.Condition;
import com.biomatters.geneious.publicapi.documents.DocumentField;

/**
 * @author Gen Li
 *         Created on 12/06/14 2:17 PM
 */
class QueryValues {
    private DocumentField searchAttribute;
    private Condition condition;
    private Object value;

    private QueryValues(DocumentField searchAttribute, Condition condition, Object value) {
        this.searchAttribute = searchAttribute;
        this.condition = condition;
        this.value = value;
    }

    DocumentField getSearchAttribute() { return searchAttribute; }

    Condition getCondition() { return condition; }

    Object getValue() { return value; }

    static QueryValues createQueryValues(DocumentField searchAttribute, Condition condition, Object value) {
        return new QueryValues(searchAttribute, condition, value);
    }
}