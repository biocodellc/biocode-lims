package com.biomatters.plugins.biocode.labbench.fims;

import com.biomatters.plugins.biocode.labbench.FimsSample;
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
public class ExcelFimsSample implements FimsSample {
    private Map<String, String> values;
    private List<DocumentField> fields;
    private List<DocumentField> taxFields;
    private int tissueCol, specimenCol;

    public ExcelFimsSample(Sheet sheet, int row, ExcelFimsConnection fimsConnection) {
        values = new HashMap<String, String>();
        fields = fimsConnection.getCollectionAttributes();
        taxFields = fimsConnection.getTaxonomyAttributes();
        tissueCol = fimsConnection.tissueCol;
        specimenCol = fimsConnection.specimenCol;

        for(DocumentField field : fields) {
            String value = sheet.getCell(Integer.parseInt(field.getCode()), row).getContents();
            values.put(field.getCode(), value);
        }
        for(DocumentField field : taxFields) {
            String value = sheet.getCell(Integer.parseInt(field.getCode()), row).getContents();
            values.put(field.getCode(), value);
        }
        System.out.println(values.size());
    }

    public ExcelFimsSample(Element e) throws XMLSerializationException {
        fromXML(e);
    }


    public String getId() {
        return values.get(""+tissueCol);
    }

    public String getSpecimenId() {
        return values.get(""+specimenCol);
    }

    public String getFimsConnectionId() {
        return null;
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
            entryElement.addContent(new Element("value").setText(value != null ? value.toString() : ""));
            values.addContent(entryElement);
        }
        e.addContent(values);

        return e;
    }

    public void fromXML(Element element) throws XMLSerializationException {
        try {
            tissueCol = Integer.parseInt(element.getChildText("tissueCol"));
            specimenCol = Integer.parseInt(element.getChildText("specimenCol"));
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

            values = new HashMap<String, String>();
            Element valuesElement = element.getChild("values");
            for(Element e : valuesElement.getChildren()) {
                DocumentField field = getDocumentField(e.getChildText("key"));
                if(field == null) {
                    throw new IllegalStateException("The DocumentField "+e.getChildText("key")+" was not found!");
                }
                String value = e.getChildText("value");
                values.put(field.getCode(), value);
            }
        } catch (Exception e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
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
}
