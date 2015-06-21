package com.biomatters.plugins.biocode.labbench.fims;

import com.biomatters.plugins.biocode.labbench.ConnectionException;
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

    public TableFimsSample(Sheet sheet, int row, TableFimsConnection fimsConnection) throws ConnectionException {
        values = new HashMap<String, Object>();
        fields = fimsConnection.getCollectionAttributes();
        taxFields = fimsConnection.getTaxonomyAttributes();
        tissueCol = fimsConnection.getTissueSampleDocumentField().getCode();
        specimenCol = fimsConnection.getSpecimenDocumentField().getCode();

        for(DocumentField field : fields) {
            int column = getTableIndex(sheet, field);
            if(column < 0) {
                throw new ConnectionException("Could not find the column \""+field.getName()+"\" in the spreadsheet");
            }
            String value = sheet.getCell(column, row).getContents();
            values.put(field.getCode(), XmlUtilities.encodeXMLChars(value));
        }
        for(DocumentField field : taxFields) {
            int column = getTableIndex(sheet, field);
            if(column < 0) {
                throw new ConnectionException("Could not find the column \""+field.getName()+"\" in the spreadsheet");
            }
            String value = sheet.getCell(column, row).getContents();
            values.put(field.getCode(), XmlUtilities.encodeXMLChars(value));
        }
    }
    
    private int getTableIndex(Sheet sheet, DocumentField documentField) {
        String name = documentField.getCode();
        name = name.replace(TableFimsConnection.CODE_PREFIX, "");
        for (int i = 0, cellValuesSize = sheet.getColumns(); i < cellValuesSize; i++) {
            String cellContents = sheet.getCell(i, 0).getContents();
            if(XmlUtilities.encodeXMLChars(cellContents).equalsIgnoreCase(name)) {
                return i;
            }
        }
        return -1;
    }

    public TableFimsSample(List<DocumentField> fimsAttributes, List<DocumentField> taxonomyAttributes, Map<String, Object> fieldValues, String tissueCol, String specimenCol) {
        this.values = fieldValues;
        this.fields = fimsAttributes;
        this.taxFields = taxonomyAttributes;
        this.tissueCol = tissueCol;
        this.specimenCol = specimenCol;
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
            Object key = entry.getKey();
            if(key == null) {
                continue;
            }
            entryElement.addContent(new Element("key").setText(key.toString()));
            Object value = entry.getValue();
            entryElement.addContent(new Element("value").setText(value != null ? valueToXmlStorage(value) : ""));
            values.addContent(entryElement);
        }
        e.addContent(values);

        return e;
    }

    private static String valueToXmlStorage(Object value) {
        for (Converter converter : Converter.values()) {
            String converted = converter.convertToString(value);
            if(converted != null) {
                return converted;
            }
        }
        // Crash here for developers.
        assert value instanceof String : "TableFimsSample only supports serializing Strings or Dates to XML.  " +
                "If you've added a new type of DocumentField then you will need to add support for serializing it.  " +
                "Otherwise Geneious core will have a heart attack when it discovers that the value after fromXML() is a " +
                "String but the DocumentField is of another type";
        return XmlUtilities.encodeXMLChars(value.toString());
    }


    private static Object valueFromXmlStorage(String elementText) {
        for (Converter converter : Converter.values()) {
            Object converted = converter.convertFromString(elementText);
            if(converted != null) {
                return converted;
            }
        }
        return XmlUtilities.decodeXMLChars(elementText);
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
                String valueText = e.getChildText("value");
                Object value = valueFromXmlStorage(valueText);
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

    /**
     * Used to convert objects of a certain type to/from a String.
     * @param <Type>
     */
    private static abstract class Converter<Type> {

        private String prefix;
        private Class<Type> type;

        private Converter(String prefix, Class<Type> type) {
            this.prefix = prefix;
            this.type = type;
        }

        /**
         * @param value The value to be converted into a String
         * @return a String representation of the value or null if the value cannot be converted by this Converter
         */
        public final String convertToString(Object value) {
            if(!type.isAssignableFrom(value.getClass())) {
                return null;
            }
            return prefix + _convertToString(type.cast(value));
        }

        protected String _convertToString(Type value) {
            return String.valueOf(value);
        }

        /**
         *
         * @param stringValue String to be converted back to value of the correct type.  Should have been obtained from
         * calling {@link #convertToString(Object)}
         * @return The converted value or null if the String was not in the correct format for this converter
         */
        public final Type convertFromString(String stringValue) {
            if(!stringValue.startsWith(prefix)) {
                return null;
            }
            String substring = stringValue.substring(prefix.length());
            return _convertFromString(substring);
        }

        protected abstract Type _convertFromString(String stringValue);

        private static final Converter<Date> DATE_CONVERTER = new Converter<Date>("XML_Date_Value:", Date.class) {
            @Override
            protected String _convertToString(Date value) {
                return String.valueOf(value.getTime());
            }

            @Override
            protected Date _convertFromString(String stringValue) {
                try {
                    return new Date(Long.valueOf(stringValue));
                } catch (NumberFormatException e) {
                    return null;
                }
            }
        };

        private static final Converter<Double> DOUBLE_CONVERTER = new Converter<Double>("XML_Double_Value:", Double.class) {
            @Override
            protected Double _convertFromString(String stringValue) {
                try {
                    return Double.valueOf(stringValue);
                } catch (NumberFormatException e) {
                    return null;
                }
            }
        };

        /**
         *
         * @return All available converters
         */
        public static Collection<Converter> values() {
            return Arrays.<Converter>asList(DATE_CONVERTER, DOUBLE_CONVERTER);
        }
    }
}
