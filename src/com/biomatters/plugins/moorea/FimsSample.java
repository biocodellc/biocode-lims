package com.biomatters.plugins.moorea;

import com.biomatters.geneious.publicapi.documents.DocumentField;

import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: steve
 * Date: 11/05/2009
 * Time: 6:31:47 PM
 * To change this template use File | Settings | File Templates.
 */
public interface FimsSample {

    public String getId();

    public List<DocumentField> getFimsAttributes();

    public Object getFimsAttributeValue(String attributeName);

}
