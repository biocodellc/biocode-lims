package com.biomatters.plugins.biocode.labbench;

import com.biomatters.geneious.publicapi.documents.*;
import org.jdom.Element;

import java.util.Date;
import java.util.List;

/**
 * @author steve
 */
public class TissueDocument implements PluginDocument, FimsSample {
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

    public List<DocumentField> getFimsAttributes() {
        return fimsResults.getFimsAttributes();
    }

    public List<DocumentField> getTaxonomyAttributes() {
        return fimsResults.getTaxonomyAttributes();
    }

    public Object getFimsAttributeValue(String attributeName) {
        return fimsResults.getFimsAttributeValue(attributeName);
    }

    public String getName() {
        return ""+getId();
    }

    public Class getFimsSampleClass() {
        return fimsResults.getClass();
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
        //noinspection StringConcatenationInsideStringBufferAppend
        htmlBuilder.append("<h1>"+getName()+"</h1>\n");
        //noinspection StringConcatenationInsideStringBufferAppend
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
            //noinspection StringConcatenationInsideStringBufferAppend
            htmlBuilder.append("<tr><td align=\"right\"><b>"+name+":</b></td><td>"+value+"</td></tr>\n");
        }
        htmlBuilder.append("</table>\n");
        return htmlBuilder.toString();
    }

    public String getTaxonomyHTML() {
        StringBuilder htmlBuilder = new StringBuilder();
        //noinspection StringConcatenationInsideStringBufferAppend
        htmlBuilder.append("<h1>"+getName()+"</h1>\n");
        htmlBuilder.append("<table border=\"0\">\n");
        List<DocumentField> taxonomyAttributes = fimsResults.getTaxonomyAttributes();
        if(taxonomyAttributes == null || taxonomyAttributes.size() == 0) {
            return null;
        }
        for(DocumentField field : taxonomyAttributes) {
            String name = field.getName();
            Object value = fimsResults.getFimsAttributeValue(field.getCode());
            if(value == null) {
                value = "";
            }
            //noinspection StringConcatenationInsideStringBufferAppend
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
