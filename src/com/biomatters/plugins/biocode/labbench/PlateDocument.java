package com.biomatters.plugins.biocode.labbench;

import com.biomatters.geneious.publicapi.documents.*;
import com.biomatters.plugins.biocode.labbench.plates.Plate;

import java.util.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import com.biomatters.plugins.biocode.labbench.reaction.Reaction;
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
        return Collections.emptyList();
    }

    public Object getFieldValue(String fieldCodeName) {
        if (PluginDocument.MODIFIED_DATE_FIELD.getCode().equals(fieldCodeName)) {
            return new Date(plate.lastModified().getTime());
        }
        return null;
    }

    public String getName() {
        return plate.getName();
    }

    public URN getURN() {
        return null;
    }

    private final AtomicBoolean creationDateInitialized = new AtomicBoolean(false);
    private Date creationDate = new Date();
    public Date getCreationDate() {
        initializeCreationDateIfNecessary();
        return creationDate;
    }

    private void initializeCreationDateIfNecessary() {
        synchronized (creationDateInitialized) {
            if(!creationDateInitialized.getAndSet(true)) {
                for (Reaction reaction : plate.getReactions()) {
                    if(reaction.getDate().before(creationDate)) {
                        creationDate = reaction.getDate();
                    }
                }
            }
        }
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

    public Plate getPlate() {
        return plate;
    }


    public void setPlate(Plate plate) {
        this.plate = plate;
    }
}
