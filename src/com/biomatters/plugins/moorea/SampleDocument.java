package com.biomatters.plugins.moorea;

import com.biomatters.geneious.publicapi.documents.DocumentField;
import com.biomatters.geneious.publicapi.documents.PluginDocument;
import com.biomatters.geneious.publicapi.documents.URN;
import com.biomatters.geneious.publicapi.documents.XMLSerializationException;
import org.jdom.Element;

import java.util.Date;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: steve
 * Date: 13/05/2009
 * Time: 8:03:12 AM
 * To change this template use File | Settings | File Templates.
 */
public class SampleDocument implements PluginDocument, FimsSample {
    private Element fimsResults;


    public SampleDocument(Element e) {
        fimsResults = e;
    }

    public String getId() {
        return null;   //todo: this
    }

    public List<DocumentField> getDisplayableFields() {
        return getFimsAttributes();
    }

    public Object getFieldValue(String fieldCodeName) {
        return getFimsAttributeValue(fieldCodeName);
    }

    public String getName() {
        return null; //todo: should probably be the sample ID number?
    }

    public URN getURN() {
        return null; //todo - a unique URN based on the sample ID number
    }

    public Date getCreationDate() {
        return null; //todo
    }

    public String getDescription() {
        return null; //todo
    }

    public String toHTML() {
        return null;
    }

    public Element toXML() {
        return fimsResults;
    }

    public void fromXML(Element element) throws XMLSerializationException {
        this.fimsResults = element;
    }

    public List<DocumentField> getFimsAttributes() {
        return null;
    }

    public Object getFimsAttributeValue(String attributeName) {
        return null;
    }
}
