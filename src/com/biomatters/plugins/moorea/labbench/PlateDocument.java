package com.biomatters.plugins.moorea.labbench;

import com.biomatters.geneious.publicapi.documents.*;
import com.biomatters.plugins.moorea.labbench.plates.Plate;

import java.util.*;
import java.util.List;

import org.jdom.Element;

/**
 * @author Steven Stones-Havas
 * @version $Id$
 *          <p/>
 *          Created on 18/06/2009 4:10:35 PM
 */
public class PlateDocument implements PluginDocument {

    private Plate plate;

    public PlateDocument() {}

    public PlateDocument(Element e) throws XMLSerializationException{
        fromXML(e);
    }

    public PlateDocument(Plate p) {
        this.plate = p;
    }

    public List<DocumentField> getDisplayableFields() {
        return null;
    }

    public Object getFieldValue(String fieldCodeName) {
        return null;
    }

    public String getName() {
        return plate.getName();
    }

    public URN getURN() {
        return null;
    }

    public Date getCreationDate() {
        return null;
    }

    public String getDescription() {
        return "A "+plate.getReactionType().toString()+" plate.";
    }

    public String toHTML() {
        return null;
    }

    public Element toXML() {
        return plate.toXML();
    }

    public void fromXML(Element element) throws XMLSerializationException {
        plate = new Plate(element);
    }

    public int getNumberOfParts() {
        return 1;
    }

    public Plate getPlate() {
        return plate;
    }


    public void setPlate(Plate plate) {
        this.plate = plate;
    }
}
