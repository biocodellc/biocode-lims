package com.biomatters.plugins.biocode.labbench.fims;

import com.biomatters.geneious.publicapi.documents.DocumentField;
import com.biomatters.geneious.publicapi.documents.XMLSerializationException;
import com.biomatters.plugins.biocode.labbench.FimsSample;
import com.biomatters.plugins.biocode.labbench.BiocodeService;
import org.jdom.Element;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.*;

/**
 * @author Steven Stones-Havas
 * @version $Id$
 *          <p/>
 *          Created on 3/06/2009 10:51:53 AM
 */
public class MooreaFimsSample implements FimsSample {
    private Map<String, Object> values;
    private Map<String, DocumentField> fimsAttributes;
    private Map<String, DocumentField> taxonomyFimsAttributes;
    private Map<String, DocumentField> hiddenFimsAttributes;
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
        initialiseAttributeFields(fimsConnection);
        fimsConnectionId = fimsConnection.getName();
        for(DocumentField field : fimsAttributes.values()) {
            putField(resultSet, field);
        }
        for(DocumentField field : taxonomyFimsAttributes.values()) {
            putField(resultSet, field);
        }

        for(DocumentField field : extraHiddenFields) {
            values.put(field.getCode(), getResult(resultSet, field.getCode(), field.getValueType()));
        }

        
    }

    private void initialiseAttributeFields(FIMSConnection fimsConnection) {
        fimsAttributes = new LinkedHashMap<String, DocumentField>();
        taxonomyFimsAttributes = new LinkedHashMap<String, DocumentField>();
        hiddenFimsAttributes = new LinkedHashMap<String, DocumentField>();


        if(fimsConnection == null) {
            return;
        }

        List<DocumentField> collectionAttributeList = fimsConnection.getCollectionAttributes();
        for(DocumentField field : collectionAttributeList) {
            fimsAttributes.put(field.getCode(), field);
        }
        List<DocumentField> taxonomyAttributeList = fimsConnection.getTaxonomyAttributes();
        for(DocumentField field : taxonomyAttributeList) {
            taxonomyFimsAttributes.put(field.getCode(), field);
        }

                //special case
        for(DocumentField field : extraHiddenFields) {
            hiddenFimsAttributes.put(field.getCode(), field);
        }
    }

    private void putField(ResultSet resultSet, DocumentField field) throws SQLException {

        String code = field.getCode();
        if(DocumentField.ORGANISM_FIELD.getCode().equals(code)) {
            code = "biocode.ScientificName"; //we use the standard organism field so we need to map it to the correct database id
        }
        else if(DocumentField.COMMON_NAME_FIELD.getCode().equals(code)) {
            code = "biocode.ColloquialName"; //we use the standard common name field so we need to map it to the correct database id
        } else if(MooreaFimsConnection.PROJECT_FIELD.getCode().equals(code)) {
            code = "biocode.ProjectCode";  // We used the wrong column originally.  But we don't want to change the code otherwise we'll end up with two.
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
            Timestamp timestamp = resultSet.getTimestamp(code);
            value = new Date(timestamp.getTime());
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


    public List<DocumentField> getFimsAttributes() {
        return new ArrayList<DocumentField>(fimsAttributes.values());
    }

    public List<DocumentField> getTaxonomyAttributes() {
        return new ArrayList<DocumentField>(taxonomyFimsAttributes.values());
    }

    public Object getFimsAttributeValue(String attributeName) {
        if("biocode_collecting_event.CollectionTime".equals(attributeName)) {//special cases
            int year = (Integer)values.get("biocode_collecting_event.YearCollected");
            int month = (Integer)values.get("biocode_collecting_event.MonthCollected");
            int day = (Integer)values.get("biocode_collecting_event.DayCollected");

            Calendar cal = Calendar.getInstance();
            //noinspection MagicConstant
            cal.set(year, month - 1, day, 0, 0, 0);
            cal.set(Calendar.MILLISECOND, 0);
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

//        Element fields = new Element("collectionFields");
//        for(DocumentField field : fimsAttributes.values()) {
//            fields.addContent(field.toXML());
//        }
//        Element fields2 = new Element("taxonomyFields");
//        for(DocumentField field : taxonomyFimsAttributes.values()) {
//            fields2.addContent(field.toXML());
//        }
//        Element fields3 = new Element("hiddenFields");
//        for(DocumentField field : hiddenFimsAttributes.values()) {
//            fields3.addContent(field.toXML());
//        }
//        e.addContent(fields);
//        e.addContent(fields2);
//        e.addContent(fields3);

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

            fimsConnectionId = element.getAttributeValue("fimsConnection");
            initialiseAttributeFields(BiocodeService.getInstance().getActiveFIMSConnection());

//            Element fields = element.getChild("collectionFields");
//            for(Element fieldChild : fields.getChildren()) {
//                DocumentField field = new DocumentField();
//                field.fromXML(fieldChild);
//                fimsAttributes.put(field.getCode(), field);
//            }
//
//            Element fields2 = element.getChild("taxonomyFields");
//            for(Element fieldChild : fields2.getChildren()) {
//                DocumentField field = new DocumentField();
//                field.fromXML(fieldChild);
//                taxonomyFimsAttributes.put(field.getCode(), field);
//            }
//
//            Element fields3 = element.getChild("hiddenFields");
//            for(Element fieldChild : fields3.getChildren()) {
//                DocumentField field = new DocumentField();
//                field.fromXML(fieldChild);
//                hiddenFimsAttributes.put(field.getCode(), field);
//            }

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
        } catch (IllegalStateException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }
    }

    private DocumentField getDocumentField(String fieldCode) {
        DocumentField field1 = fimsAttributes.get(fieldCode);
        if(field1 != null) {
            return field1;
        }
        DocumentField field2 = taxonomyFimsAttributes.get(fieldCode);
        if(field2 != null) {
            return field2;
        }
        return hiddenFimsAttributes.get(fieldCode);
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
