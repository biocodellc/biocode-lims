package com.biomatters.plugins.moorea.labbench;

import com.biomatters.geneious.publicapi.databaseservice.QueryField;
import com.biomatters.geneious.publicapi.documents.Condition;
import com.biomatters.geneious.publicapi.documents.DocumentField;

/**
 * Created by IntelliJ IDEA.
 * User: steve
 * Date: 13/05/2009
 * Time: 3:26:05 PM
 * To change this template use File | Settings | File Templates.
 */
public class SourceAwareQueryField extends QueryField{
    private String sourceId;

    public SourceAwareQueryField(DocumentField field, Condition[] conditions, String sourceId) {
        super(field, conditions);
        this.sourceId = sourceId;
    }

    public SourceAwareQueryField(String sourceId) {
        super();
        this.sourceId = sourceId;
    }

    public String getSourceId() {
        return sourceId;
    }
}
