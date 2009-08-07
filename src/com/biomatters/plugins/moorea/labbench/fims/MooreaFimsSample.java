package com.biomatters.plugins.moorea.labbench.fims;

import com.biomatters.geneious.publicapi.documents.DocumentField;
import com.biomatters.geneious.publicapi.documents.XMLSerializationException;
import com.biomatters.plugins.moorea.labbench.FimsSample;
import org.jdom.Element;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

/**
 * @author Steven Stones-Havas
 * @version $Id$
 *          <p/>
 *          Created on 3/06/2009 10:51:53 AM
 */
public class MooreaFimsSample implements FimsSample {
    private Map<String, Object> values;
    private List<DocumentField> fimsAttributes;
    private List<DocumentField> taxonomyFimsAttributes;
    private List<DocumentField> hiddenFimsAttributes;
    private String fimsConnectionId;
    private static final DocumentField[] extraHiddenFields = new DocumentField[] {
        new DocumentField("Year Collected", "", "biocode_collecting_event.YearCollected", Integer.class, true, false),
        new DocumentField("Month Collected", "", "biocode_collecting_event.MonthCollected", Integer.class, true, false),
        new DocumentField("Day Collected", "", "biocode_collecting_event.DayCollected", Integer.class, true, false),
        new DocumentField("Tissue Number", "", "biocode_tissue.tissue_num", Integer.class, false, false),
        new DocumentField("Notes", "", "biocode_tissue.notes", String.class, false, false)
    };

    public MooreaFimsSample(Element e) throws XMLSerializationException{
        fromXML(e);  
    }


    public MooreaFimsSample(ResultSet resultSet, FIMSConnection fimsConnection) throws SQLException{
        values = new HashMap<String, Object>();
        fimsAttributes = fimsConnection.getCollectionAttributes();
        taxonomyFimsAttributes = fimsConnection.getTaxonomyAttributes();
        fimsConnectionId = fimsConnection.getName();
        for(DocumentField field : fimsAttributes) {
            putField(resultSet, field);
        }
        for(DocumentField field : taxonomyFimsAttributes) {
            putField(resultSet, field);
        }

        //special case
        hiddenFimsAttributes = new ArrayList<DocumentField>();
        for(DocumentField field : extraHiddenFields) {
            values.put(field.getCode(), getResult(resultSet, field.getCode(), field.getValueType()));
            hiddenFimsAttributes.add(field);
        }

        
    }

    private void putField(ResultSet resultSet, DocumentField field) throws SQLException {

        String code = field.getCode();
        if(DocumentField.ORGANISM_FIELD.getCode().equals(code)) {
            code = "biocode.ScientificName"; //we use the standard organism field so we need to map it to the correct database id
        }
        else if(DocumentField.COMMON_NAME_FIELD.getCode().equals(code)) {
            code = "biocode.ColloquialName"; //we use the standard common name field so we need to map it to the correct database id
        }

        try {//skip collumns that don't exist
            resultSet.findColumn(code);
        }
        catch(SQLException ex) {
            return;
        }

        Object value = getResult(resultSet, code, field.getValueType());

        values.put(field.getCode(), value);
    }

    private Object getResult(ResultSet resultSet, String code, Class valueType) throws SQLException {
        Object value;



        if(valueType.equals(Integer.class)) {
            value = resultSet.getInt(code);
        }
        else if(valueType.equals(Double.class)) {
            value = resultSet.getDouble(code);
        }
        else if(valueType.equals(String.class)) {
            value = resultSet.getString(code);
        }
        else if(valueType.equals(Date.class)) {
            java.sql.Date date = resultSet.getDate(code);
            value = new Date(date.getTime());
        }
        else {
            throw new IllegalArgumentException("The class "+valueType+" is not supported!");
        }
        return value;
    }

    public String getId() {
        Object o  = values.get("biocode_tissue.bnhm_id");
        Object o2 = values.get("biocode_tissue.tissue_num");
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
        Object o = values.get("biocode_tissue.bnhm_id");
        return o != null ? o.toString() : null;
    }

    public String getFimsConnectionId() {
        return null;
    }

    public List<DocumentField> getFimsAttributes() {
        return fimsAttributes;
    }

    public List<DocumentField> getTaxonomyAttributes() {
        return taxonomyFimsAttributes;
    }

    public Object getFimsAttributeValue(String attributeName) {
        if("biocode_collecting_event.CollectionTime".equals(attributeName)) {//special cases
            int year = (Integer)values.get("biocode_collecting_event.YearCollected");
            int month = (Integer)values.get("biocode_collecting_event.MonthCollected");
            int day = (Integer)values.get("biocode_collecting_event.DayCollected");

            Calendar cal = Calendar.getInstance();
            cal.set(year, month, day);
            return cal.getTime();
        }
        if("tissueId".equals(attributeName)) {
            return getId();   
        }
        return values.get(attributeName);
    }

    public Element toXML() {
        Element e = new Element("FimsSample");
        e.setAttribute("fimsConnection", fimsConnectionId);

        Element fields = new Element("collectionFields");
        for(DocumentField field : fimsAttributes) {
            fields.addContent(field.toXML());
        }
        Element fields2 = new Element("taxonomyFields");
        for(DocumentField field : taxonomyFimsAttributes) {
            fields2.addContent(field.toXML());
        }
        Element fields3 = new Element("hiddenFields");
        for(DocumentField field : hiddenFimsAttributes) {
            fields3.addContent(field.toXML());
        }
        e.addContent(fields);
        e.addContent(fields2);
        e.addContent(fields3);

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
            fimsAttributes = new ArrayList<DocumentField>();
            taxonomyFimsAttributes = new ArrayList<DocumentField>();
            hiddenFimsAttributes = new ArrayList<DocumentField>();

            fimsConnectionId = element.getAttributeValue("fimsConnection");

            Element fields = element.getChild("collectionFields");
            for(Element fieldChild : fields.getChildren()) {
                DocumentField field = new DocumentField();
                field.fromXML(fieldChild);
                fimsAttributes.add(field);
            }

            Element fields2 = element.getChild("taxonomyFields");
            for(Element fieldChild : fields2.getChildren()) {
                DocumentField field = new DocumentField();
                field.fromXML(fieldChild);
                taxonomyFimsAttributes.add(field);
            }

            Element fields3 = element.getChild("hiddenFields");
            for(Element fieldChild : fields3.getChildren()) {
                DocumentField field = new DocumentField();
                field.fromXML(fieldChild);
                hiddenFimsAttributes.add(field);
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
        } catch (XMLSerializationException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        } catch (IllegalStateException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }
    }

    private DocumentField getDocumentField(String fieldCode) {
        for(DocumentField field : fimsAttributes) {
            if(field.getCode().equals(fieldCode)) {
                return field;
            }
        }
        for(DocumentField field : taxonomyFimsAttributes) {
            if(field.getCode().equals(fieldCode)) {
                return field;
            }
        }
        for(DocumentField field : hiddenFimsAttributes) {
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
