package com.biomatters.plugins.moorea.fims;

import com.biomatters.plugins.moorea.FimsSample;
import com.biomatters.geneious.publicapi.documents.DocumentField;
import com.biomatters.geneious.publicapi.documents.XMLSerializationException;

import java.util.*;
import java.util.Date;
import java.sql.*;

import org.jdom.Element;

/**
 * @author Steven Stones-Havas
 * @version $Id$
 *          <p/>
 *          Created on 3/06/2009 10:51:53 AM
 */
public class SQLFimsSample implements FimsSample{
    private Map<String, Object> values;
    private List<DocumentField> fimsAttributes;
    private String fimsConnectionId;

    public SQLFimsSample(Element e) throws XMLSerializationException{
        fromXML(e);  
    }


    public SQLFimsSample(ResultSet resultSet, FIMSConnection fimsConnection) throws SQLException{
        values = new HashMap<String, Object>();
        fimsAttributes = fimsConnection.getFimsAttributes();
        fimsConnectionId = fimsConnection.getName();
        for(DocumentField field : fimsAttributes) {
            try {//skip collumns that don't exist
                resultSet.findColumn(field.getCode());
            }
            catch(SQLException ex) {
                continue;
            }

            Object value;

            if(field.getValueType().equals(Integer.class)) {
                value = resultSet.getInt(field.getCode());
            }
            else if(field.getValueType().equals(Double.class)) {
                value = resultSet.getDouble(field.getCode());
            }
            else if(field.getValueType().equals(String.class)) {
                value = resultSet.getString(field.getCode());
            }
            else if(field.getValueType().equals(Date.class)) {
                java.sql.Date date = resultSet.getDate(field.getCode());
                value = new java.util.Date(date.getTime());
            }
            else {
                throw new IllegalArgumentException("The class "+field.getValueType()+" is not supported!");
            }

            values.put(field.getCode(), value);
        }
    }

    public String getId() {
        Object o = values.get("biocode_tissue.seq_num");
        return o != null ? o.toString() : "Untitled";
    }

    public String getSpecimenId() {
        Object o = values.get("biocode.Specimen_Num_Collector");
        return o != null ? o.toString() : null;
    }

    public String getFimsConnectionId() {
        return null;
    }

    public List<DocumentField> getFimsAttributes() {
        return fimsAttributes;
    }

    public Object getFimsAttributeValue(String attributeName) {
        return values.get(attributeName);
    }

    public Element toXML() {
        Element e = new Element("FimsSample");
        e.setAttribute("fimsConnection", fimsConnectionId);

        Element fields = new Element("fields");
        for(DocumentField field : fimsAttributes) {
            fields.addContent(field.toXML());
        }
        e.addContent(fields);

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
        fimsAttributes = new ArrayList<DocumentField>();

        fimsConnectionId = element.getAttributeValue("fimsConnection");

        Element fields = element.getChild("fields");
        for(Element fieldChild : fields.getChildren()) {
            DocumentField field = new DocumentField();
            field.fromXML(fieldChild);
            fimsAttributes.add(field);
        }

        values = new HashMap<String, Object>();
        Element valuesElement = element.getChild("values");
        for(Element e : valuesElement.getChildren()) {
            DocumentField field = getDocumentField(e.getChildText("key"));
            if(field == null) {
                throw new IllegalStateException("The DocumentField "+e.getChildText("key")+" was not found!");
            }
            Object value = getObjectFromString(field, e.getChildText("value"));
            values.put(field.getCode(), value);
        }
    }

    private DocumentField getDocumentField(String fieldCode) {
        for(DocumentField field : fimsAttributes) {
            if(field.getCode().equals(fieldCode)) {
                return field;
            }
        }
        return null;
    }

    private static Object getObjectFromString(DocumentField field, String stringValue) {
        Class objectClass = field.getValueType();
        if(objectClass.equals(String.class)) {
            return stringValue;
        }
        else if(objectClass.equals(Integer.class)) {
            return Integer.parseInt(stringValue);
        }
        else if(objectClass.equals(Double.class)) {
            return Double.parseDouble(stringValue);
        }
        else if(objectClass.equals(Date.class)) {
            throw new RuntimeException("Date objects are not yet supported");
        }
        throw new IllegalStateException("Your field class ("+objectClass+") is not supported");
    }
}
