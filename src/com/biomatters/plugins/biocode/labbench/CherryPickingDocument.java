package com.biomatters.plugins.biocode.labbench;

import com.biomatters.geneious.publicapi.documents.*;
import com.biomatters.plugins.biocode.labbench.reaction.Reaction;
import org.jdom.Element;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: steve
 * Date: 21/04/2010
 * Time: 7:11:54 AM
 * To change this template use File | Settings | File Templates.
 */
public class CherryPickingDocument implements PluginDocument {
    private String name;
    private List<Reaction> reactions = new ArrayList<Reaction>();

    public CherryPickingDocument() {}

    public CherryPickingDocument(String name, List<Reaction> reactions) {
        this.name = name;
        this.reactions = reactions;
    }


    public List<DocumentField> getDisplayableFields() {
        return Collections.EMPTY_LIST;
    }

    public Object getFieldValue(String fieldCodeName) {
        return null;
    }

    public String getName() {
        return name;
    }

    public URN getURN() {
        return null;
    }

    public Date getCreationDate() {
        return null;
    }

    public String getDescription() {
        return "A Cherry Picked reaction set";
    }

    public String toHTML() {
        return null;
    }

    public List<Reaction> getReactions() {
        return Collections.unmodifiableList(reactions);
    }

    public Element toXML() {
        Element e = new Element("ReactionSet");
        e.addContent(new Element("Name").setText(name));
        for(Reaction r : reactions) {
            e.addContent(XMLSerializer.classToXML("reaction", r));
        }
        return e;
    }

    public void fromXML(Element e) throws XMLSerializationException {
        name = e.getChildText("Name");
        reactions = new ArrayList<Reaction>();
        for(Element el : e.getChildren("reaction")) {
            Reaction r = XMLSerializer.classFromXML(el, Reaction.class);
            reactions.add(r);
        }
    }
}
