package com.biomatters.plugins.biocode.labbench;

import com.biomatters.geneious.publicapi.documents.DocumentField;
import com.biomatters.geneious.publicapi.documents.XMLSerializable;

import java.util.List;

/**
 * @author steve
 */
public interface FimsSample extends XMLSerializable {

    public String getId();

    public String getSpecimenId();

    public List<DocumentField> getFimsAttributes();

    public List<DocumentField> getTaxonomyAttributes();

    public Object getFimsAttributeValue(String attributeName);

}
