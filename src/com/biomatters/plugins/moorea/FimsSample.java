package com.biomatters.plugins.moorea;

import com.biomatters.geneious.publicapi.documents.DocumentField;
import com.biomatters.geneious.publicapi.documents.XMLSerializable;

import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: steve
 * Date: 11/05/2009
 * Time: 6:31:47 PM
 * To change this template use File | Settings | File Templates.
 */
public interface FimsSample extends XMLSerializable {

    public String getId();

    public String getSpecimenId();

    public String getFimsConnectionId();

    public List<DocumentField> getFimsAttributes();

    public List<DocumentField> getTaxonomyAttributes();

    public Object getFimsAttributeValue(String attributeName);

}
