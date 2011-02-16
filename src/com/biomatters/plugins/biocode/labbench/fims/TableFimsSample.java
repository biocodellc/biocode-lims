package com.biomatters.plugins.biocode.labbench.fims;

import com.biomatters.plugins.biocode.labbench.FimsSample;
import com.biomatters.plugins.biocode.XmlUtilities;
import com.biomatters.geneious.publicapi.documents.DocumentField;
import com.biomatters.geneious.publicapi.documents.XMLSerializationException;

import java.util.*;

import org.jdom.Element;
import jxl.Sheet;

/**
 * @author Steven Stones-Havas
 * @version $Id$
 *          <p/>
 *          Created on 9/09/2009 6:41:55 PM
 */
public class TableFimsSample implements FimsSample {
    private Map<String, Object> values;
    private List<DocumentField> fields;
    private List<DocumentField> taxFields;
    private String tissueCol, specimenCol;

    public TableFimsSample(Sheet sheet, int row, ExcelFimsConnection fimsConnection) {
        values = new HashMap<String, Object>();
        fields = fimsConnection.getCollectionAttributes();
        taxFields = fimsConnection.getTaxonomyAttributes();
        tissueCol = ""+fimsConnection.tissueCol;
        specimenCol = ""+fimsConnection.specimenCol;

        for(DocumentField field : fields) {
            String value = sheet.getCell(Integer.parseInt(field.getCode()), row).getContents();
            values.put(field.getCode(), XmlUtilities.encodeXMLChars(value));
        }
        for(DocumentField field : taxFields) {
            String value = sheet.getCell(Integer.parseInt(field.getCode()), row).getContents();
            values.put(field.getCode(), XmlUtilities.encodeXMLChars(value));
        }
    }

    public TableFimsSample(List<DocumentField> fimsAttributes, List<DocumentField> taxonomyAttributes, Map<String, Object> fieldValues, String tissueIdCol, String specimenIdCol) {
        this.values = fieldValues;
        this.fields = fimsAttributes;
        this.taxFields = taxonomyAttributes;
        this.tissueCol = tissueIdCol;
        this.specimenCol = specimenIdCol;
    }

    public TableFimsSample(Element e) throws XMLSerializationException {
        fromXML(e);
    }


    public String getId() {
        Object value = values.get("" + tissueCol);
        return value != null ? value.toString() : null;
    }

    public String getSpecimenId() {
        Object value = values.get("" + specimenCol);
        return value != null ? value.toString() : null;
    }


    public List<DocumentField> getFimsAttributes() {
        return fields;
    }

    public List<DocumentField> getTaxonomyAttributes() {
        return taxFields;
    }

    public Object getFimsAttributeValue(String attributeName) {
        return values.get(attributeName);
    }

    public Element toXML() {
        Element e = new Element("FimsSample");
        e.addContent(new Element("tissueCol").setText(""+tissueCol));
        e.addContent(new Element("specimenCol").setText(""+specimenCol));

        Element fieldsElement = new Element("fields");
        for(DocumentField field : fields) {
            fieldsElement.addContent(field.toXML());
        }
        e.addContent(fieldsElement);

        Element taxFieldsElement = new Element("taxFields");
        for(DocumentField field : taxFields) {
            taxFieldsElement.addContent(field.toXML());
        }
        e.addContent(taxFieldsElement);

        Element values = new Element("values");
        for(Map.Entry entry : this.values.entrySet()) {
            Element entryElement = new Element("entry");
            entryElement.addContent(new Element("key").setText(entry.getKey().toString()));
            Object value = entry.getValue();
            entryElement.addContent(new Element("value").setText(value != null ? XmlUtilities.encodeXMLChars(value.toString()) : ""));
            values.addContent(entryElement);
        }
        e.addContent(values);

        return e;
    }


    public void fromXML(Element element) throws XMLSerializationException {
        try {
            tissueCol = element.getChildText("tissueCol");
            specimenCol = element.getChildText("specimenCol");
            fields = new ArrayList<DocumentField>();
            taxFields = new ArrayList<DocumentField>();

            Element fieldsElement = element.getChild("fields");
            for(Element fieldChild : fieldsElement.getChildren()) {
                DocumentField field = new DocumentField();
                field.fromXML(fieldChild);
                fields.add(field);
            }

            Element taxFieldsElement = element.getChild("taxFields");
            for(Element fieldChild : taxFieldsElement.getChildren()) {
                DocumentField field = new DocumentField();
                field.fromXML(fieldChild);
                taxFields.add(field);
            }

            values = new HashMap<String, Object>();
            Element valuesElement = element.getChild("values");
            for(Element e : valuesElement.getChildren()) {
                DocumentField field = getDocumentField(e.getChildText("key"));
                if(field == null) {
                    throw new IllegalStateException("The DocumentField "+e.getChildText("key")+" was not found!");
                }
                String value = XmlUtilities.decodeXMLChars(e.getChildText("value"));
                values.put(field.getCode(), value);
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw new XMLSerializationException("Could not deserialize FIMS sample: "+e.getMessage(), e);
        }
    }

    private DocumentField getDocumentField(String fieldCode) {
        for(DocumentField field : fields) {
            if(field.getCode().equals(fieldCode)) {
                return field;
            }
        }
        for(DocumentField field : taxFields) {
            if(field.getCode().equals(fieldCode)) {
                return field;
            }
        }
        return null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        TableFimsSample that = (TableFimsSample) o;

        if (getSpecimenId() != null ? !getSpecimenId().equals(that.getSpecimenId()) : that.getSpecimenId() != null) return false;
        if (getId() != null ? !getId().equals(that.getId()) : that.getId() != null) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = getId() != null ? getId().hashCode() : 0;
        result = 31 * result + (getSpecimenId() != null ? getSpecimenId().hashCode() : 0);
        return result;
    }
}
