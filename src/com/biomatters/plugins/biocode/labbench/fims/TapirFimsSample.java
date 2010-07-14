package com.biomatters.plugins.biocode.labbench.fims;

import com.biomatters.geneious.publicapi.documents.DocumentField;
import com.biomatters.geneious.publicapi.documents.XMLSerializationException;
import com.biomatters.geneious.publicapi.documents.XMLSerializer;
import com.biomatters.plugins.biocode.labbench.FimsSample;
import com.biomatters.plugins.biocode.labbench.fims.tapir.TAPIRClient;
import org.jdom.Element;

import java.util.*;

/**
 * Created by IntelliJ IDEA.
 * User: steve
 * Date: 20/08/2009
 * Time: 2:34:58 PM
 * To change this template use File | Settings | File Templates.
 */
public class TapirFimsSample implements FimsSample {
    private List<DocumentField> searchFields;
    private List<DocumentField> taxonomyFields;
    private Map<String, Object> values;

    public TapirFimsSample(Element e) throws XMLSerializationException {
        fromXML(e);
    }

    public TapirFimsSample(TAPIRFimsConnection connection, Element tapirHit, List<DocumentField> searchFields, List<DocumentField> taxonomyFields) {
       TAPIRClient.clearNamespace(tapirHit);
        this.searchFields = new ArrayList<DocumentField>(searchFields);
        this.searchFields.add(0, connection.getTissueSampleDocumentField());
        this.taxonomyFields = taxonomyFields;
        init(tapirHit);
    }

    public void init(Element tapirHit) {
        values = new HashMap<String, Object>();
        for(Element e : tapirHit.getChildren()) {
            DocumentField df = getDocumentFieldFromName(e.getName());
            if(df == null) {
                continue;
            }
            values.put(df.getCode(), getObjectFromString(e.getText(), df.getValueType()));
        }
    }

    private Object getObjectFromString(String text, Class valueType) {
        if(String.class.isAssignableFrom(valueType)){
            return text;
        }
        else if(Integer.class.isAssignableFrom(valueType)) {
            return Integer.parseInt(text);
        }
        else if(Double.class.isAssignableFrom(valueType)) {
            return Double.parseDouble(text);
        }
        else if(Float.class.isAssignableFrom(valueType)) {
            return Float.parseFloat(text);
        }
        else if(Date.class.isAssignableFrom(valueType)) {
            return null;
        }
        return null;
    }


    public String getId() {
        Object o  = values.get("http://rs.tdwg.org/dwc/dwcore/CatalogNumber");
        Object o2 = values.get("http://biocode.berkeley.edu/schema/tissue_num");
        if(o != null && o2 != null) {
            return o+"."+o2;
        }
        else if(o != null) {
            return o.toString();
        }
        else if(o2 != null) {
            return o2.toString();
        }
        return "Untitled";
    }

    public String getSpecimenId() {
        return ""+getFimsAttributeValue("http://rs.tdwg.org/dwc/dwcore/CatalogNumber");
    }

    public String getFimsConnectionId() {
        return null;
    }

    public List<DocumentField> getFimsAttributes() {
        return searchFields;
    }

    public List<DocumentField> getTaxonomyAttributes() {
        return taxonomyFields;
    }

    public Object getFimsAttributeValue(String attributeName) {
        if("tissueId".equals(attributeName)) {
            //special hack for tissue ids...
            return getId();
        }
        return values.get(attributeName);
    }

    public Element toXML() {
        Element fieldsElement = new Element("documentFields");
        for(DocumentField field : searchFields) {
            fieldsElement.addContent(XMLSerializer.classToXML("field", field));
        }
        Element taxonomyElement = new Element("taxonomyFields");
        for(DocumentField field : taxonomyFields) {
            taxonomyElement.addContent(XMLSerializer.classToXML("field", field));
        }
        Element root = new Element("tapirSample");
        root.addContent(fieldsElement);
        root.addContent(taxonomyElement);

        Element hits = new Element("hits");
        for(Map.Entry<String, Object> entry : values.entrySet()) {
            hits.addContent(new Element("hit").setAttribute("name", entry.getKey()).setAttribute("value", ""+entry.getValue()));
        }
        root.addContent(hits);
        root.addContent(new Element("test"));
        return root;
    }

    public void fromXML(Element element) throws XMLSerializationException {
        Element tapirHit = element.getChild("hits");
        searchFields = new ArrayList<DocumentField>();
        taxonomyFields = new ArrayList<DocumentField>();
        values = new HashMap<String, Object>();
                
        if(tapirHit == null) {
            return;
        }
        Element fields = element.getChild("documentFields");
        for(Element e : fields.getChildren("field")) {
            searchFields.add(XMLSerializer.classFromXML(e, DocumentField.class));
        }
        Element taxonomyFieldsElement = element.getChild("taxonomyFields");
        for(Element e : taxonomyFieldsElement.getChildren("field")) {
            taxonomyFields.add(XMLSerializer.classFromXML(e, DocumentField.class));
        }
        for(Element e : tapirHit.getChildren("hit")) {
                String name = e.getAttributeValue("name");
            DocumentField field = getDocumentFieldFromCode(name);
            if(field == null) {
                continue;
            }
            values.put(name, getObjectFromString(e.getAttributeValue("value"), field.getValueType()));
        }
    }

    private DocumentField getDocumentFieldFromName(String name) {
        for(DocumentField field : searchFields) {
            if(field.getName().equals(name)) {
                return field;
            }
        }
        for(DocumentField field : taxonomyFields) {
            if(field.getName().equals(name)) {
                return field;
            }
        }
        return null;
    }

    private DocumentField getDocumentFieldFromCode(String name) {
        for(DocumentField field : searchFields) {
            if(field.getCode().equals(name)) {
                return field;
            }
        }
        for(DocumentField field : taxonomyFields) {
            if(field.getCode().equals(name)) {
                return field;
            }
        }
        return null;
    }
}
