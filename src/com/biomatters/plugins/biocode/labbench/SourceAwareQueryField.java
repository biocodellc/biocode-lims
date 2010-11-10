package com.biomatters.plugins.biocode.labbench;

import com.biomatters.geneious.publicapi.databaseservice.QueryField;
import com.biomatters.geneious.publicapi.documents.Condition;
import com.biomatters.geneious.publicapi.documents.DocumentField;

/**
 * @author steve
 * @version $Id: 13/05/2009 3:26:05 PM steve $
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
