package com.biomatters.plugins.moorea;

import com.biomatters.geneious.publicapi.documents.XMLSerializable;
import com.biomatters.geneious.publicapi.documents.XMLSerializationException;
import org.jdom.Element;

/**
 * @author Steven Stones-Havas
 * @version $Id$
 *          <p/>
 *          Created on 16/06/2009 3:12:41 PM
 */
public class Workflow implements XMLSerializable {
    private int id;
    private String name;

    public Workflow(Element e) throws XMLSerializationException{
        fromXML(e);
    }

    public Workflow(int id, String name) {
        this.id = id;
        this.name = name;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Element toXML() {
        return new Element("workflow").addContent(new Element("name").setText(getName())).addContent(new Element("id").setText(""+getId()));
    }

    public void fromXML(Element element) throws XMLSerializationException {
        name = element.getChildText("name");
        if(element.getChildText("id") != null) {
            try {
                id = Integer.parseInt(element.getChildText("id"));
            }
            catch(NumberFormatException ex){
                throw new XMLSerializationException("The id field was invalid");
            }
        }
    }
}
