package com.biomatters.plugins.biocode.labbench.reaction;

import com.biomatters.geneious.publicapi.documents.DocumentField;
import com.biomatters.geneious.publicapi.documents.XMLSerializable;
import com.biomatters.geneious.publicapi.documents.XMLSerializationException;
import com.biomatters.geneious.publicapi.documents.XMLSerializer;
import com.biomatters.plugins.biocode.labbench.BiocodeService;

import java.util.List;
import java.util.Collections;
import java.util.ArrayList;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.ResultSet;
import java.io.StringReader;
import java.io.IOException;

import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.input.SAXBuilder;
import org.jdom.output.XMLOutputter;

/**
 * User: Steve
 * Date: 17/03/2010
 * Time: 7:06:10 PM
 */
public class DisplayFieldsTemplate implements XMLSerializable{
    private List<DocumentField> displayedFields;
    private DocumentField labelField = Reaction.WELL_DOCUMENT_FIELD;
    private String name;
    private Reaction.Type type;
    private Reaction.BackgroundColorer colorer;

    public DisplayFieldsTemplate(String name, Reaction.Type type, List<DocumentField> displayedFields, Reaction.BackgroundColorer colorer) {
        this(name, type, displayedFields, colorer, null);
    }

    public DisplayFieldsTemplate(String name, Reaction.Type type, List<DocumentField> displayedFields, Reaction.BackgroundColorer colorer, DocumentField labelField) {
        this.displayedFields = displayedFields;
        this.name = name;
        this.type = type;
        if(this.displayedFields == null) {
            this.displayedFields = Collections.emptyList();
        }
        if(name == null) {
            throw new IllegalArgumentException("Name cannot be null!");
        }
        if(type == null) {
            throw new IllegalArgumentException("Type cannot be null!");
        }
        if(colorer == null) {
            throw new IllegalArgumentException("Colorer cannot be null!");
        }
        this.colorer = colorer;
        if(labelField != null) {
            this.labelField = labelField;
        }
    }

    public DisplayFieldsTemplate(Element e) throws XMLSerializationException  {
        fromXML(e);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        DisplayFieldsTemplate that = (DisplayFieldsTemplate) o;

        if (!colorer.equals(that.colorer)) return false;
        if (!fieldsMatch(that.displayedFields)) return false;
        if (!name.equals(that.name)) return false;
        if (type != that.type) return false;
        if(!labelField.equals(that.getLabelField())) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = displayedFields.hashCode();
        result = 31 * result + name.hashCode();
        result = 31 * result + type.hashCode();
        result = 31 * result + colorer.hashCode();
        result = 31 * result + labelField.hashCode();
        return result;
    }

    public boolean fieldsMatch(List<DocumentField> fields) {
        if(fields.size() != displayedFields.size()) {
            return false;
        }
        for (int i = 0; i < this.displayedFields.size(); i++) {
            DocumentField field = this.displayedFields.get(i);
            if(!field.getCode().equals(fields.get(i).getCode())) {
                return false;
            }
        }
        return true;
    }

    public boolean colourerMatches(Reaction.BackgroundColorer colorer) {
        return colorer.equals(this.colorer);
    }

    public Reaction.Type getReactionType() {
        return type;
    }

    public List<DocumentField> getDisplayedFields() {
        return displayedFields;
    }

    public String getName() {
        return name;
    }

    public Reaction.BackgroundColorer getColorer() {
        return colorer;
    }

    public Element toXML() {
        Element e = fieldsToXML();
        e.addContent(new Element("Name").setText(name));
        e.addContent(new Element("Type").setText(type.toString()));
        if(colorer != null) {
            e.addContent(XMLSerializer.classToXML("Colorer", colorer));
        }
        e.addContent(XMLSerializer.classToXML(LABEL, labelField));
        return e;
    }

    private static final String LABEL = "label";

    public void fromXML(Element element) throws XMLSerializationException {
        name = element.getChildText("Name");
        type = Reaction.Type.valueOf(element.getChildText("Type"));
        Element colorerElement = element.getChild("Colorer");
        if(colorerElement != null) {
            colorer = XMLSerializer.classFromXML(colorerElement, Reaction.BackgroundColorer.class);
        }
        else {
            colorer = BiocodeService.getInstance().getDefaultDisplayedFieldsTemplate(type).getColorer();
        }
        fieldsFromXML(element);
        Element labelElement = element.getChild(LABEL);
        if(labelElement != null) {
            labelField = XMLSerializer.classFromXML(labelElement, DocumentField.class);
        }
    }

    public Element fieldsToXML() {
        Element e = new Element("DisplayedFields");
        for(DocumentField field : displayedFields) {
            e.addContent(XMLSerializer.classToXML("Field", field));
        }
        return e;
    }

    public void fieldsFromXML(Element element) throws XMLSerializationException {
        List<Element> fieldsElements = element.getChildren("Field");
        displayedFields = new ArrayList<DocumentField>();
        for(Element fieldsElement : fieldsElements) {
            displayedFields.add(XMLSerializer.classFromXML(fieldsElement,  DocumentField.class));
        }
    }

    public void toSQL(Connection conn) throws SQLException{
        PreparedStatement statement = conn.prepareStatement("INSERT INTO reactionFields (name, type, fields) values (?, ?, ?);");
        statement.setString(1, name);
        statement.setString(2, type.toString());
        Element fields = fieldsToXML();
        XMLOutputter out = new XMLOutputter();
        statement.setString(3, out.outputString(fields));
    }

    @Override
    public String toString() {
        return name;
    }


    public DocumentField getLabelField() {
        return labelField;
    }
}
