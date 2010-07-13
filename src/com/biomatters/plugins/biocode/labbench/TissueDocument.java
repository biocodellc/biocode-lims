package com.biomatters.plugins.biocode.labbench;

import com.biomatters.geneious.publicapi.documents.*;
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
public class TissueDocument implements PluginDocument {
    private FimsSample fimsResults;


    public TissueDocument(FimsSample sample) {
        fimsResults = sample;
    }

    public TissueDocument(){} //for XMLSerialization

    public TissueDocument(Element e) throws XMLSerializationException{
        fromXML(e);
    }

    public String getId() {
        return fimsResults.getId();
    }

    public String getSpecimenId() {
        return fimsResults.getSpecimenId();
    }

    public List<DocumentField> getDisplayableFields() {
        return fimsResults.getFimsAttributes();
    }

    public Object getFieldValue(String fieldCodeName) {
        return fimsResults.getFimsAttributeValue(fieldCodeName);
    }

    public String getName() {
        return getId();
    }

    public URN getURN() {
        return null;//new URN(BiocodeService.UNIQUE_ID, fimsResults.getFimsConnectionId(), getId());
    }

    public Date getCreationDate() {
        return new Date();
    }

    public String getDescription() {
        Object description = getFieldValue("biocode_tissue.notes");
        return description != null ? description.toString() : "";
    }

    public String toHTML() {
        return null;
    }

    public String getTissueHTML() {
        StringBuilder htmlBuilder = new StringBuilder();
        htmlBuilder.append("<h1>"+getName()+"</h1>\n");
        htmlBuilder.append("<table border=\"0\">\n");
        List<DocumentField> fimsAttributes = fimsResults.getFimsAttributes();
        if(fimsAttributes == null || fimsAttributes.size() == 0) {
            return null;
        }
        for(DocumentField field : fimsAttributes) {
            String name = field.getName();
            Object value = fimsResults.getFimsAttributeValue(field.getCode());
            if(value == null) {
                value = "";
            }
            htmlBuilder.append("<tr><td align=\"right\"><b>"+name+":</b></td><td>"+value+"</td></tr>\n");
        }
        htmlBuilder.append("</table>\n");
        return htmlBuilder.toString();
    }

    public String getTaxonomyHTML() {
        StringBuilder htmlBuilder = new StringBuilder();
        htmlBuilder.append("<h1>"+getName()+"</h1>\n");
        htmlBuilder.append("<table border=\"0\">\n");
        List<DocumentField> taxonomyAttributes = fimsResults.getTaxonomyAttributes();
        if(taxonomyAttributes == null || taxonomyAttributes.size() == 0) {
            return null;
        }
        for(DocumentField field : taxonomyAttributes) {
            String name = field.getName();
            Object value = fimsResults.getFimsAttributeValue(field.getCode());
            htmlBuilder.append("<tr><td align=\"right\"><b>"+name+":</b></td><td>"+value+"</td></tr>\n");
        }
        htmlBuilder.append("</table>\n");
        return htmlBuilder.toString();
    }

    public Element toXML() {
        return XMLSerializer.classToXML("tissueSample", fimsResults);
    }

    public void fromXML(Element element) throws XMLSerializationException {
        fimsResults = (FimsSample)XMLSerializer.classFromXML(element);
    }
}
