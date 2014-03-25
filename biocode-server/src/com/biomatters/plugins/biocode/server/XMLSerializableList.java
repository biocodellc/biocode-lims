package com.biomatters.plugins.biocode.server;

import com.biomatters.geneious.publicapi.documents.XMLSerializable;
import com.biomatters.geneious.publicapi.documents.XMLSerializationException;
import com.biomatters.geneious.publicapi.documents.XMLSerializer;
import org.jdom.Element;

import javax.xml.bind.annotation.XmlRootElement;
import java.util.ArrayList;
import java.util.List;

/**
 * Used to wrap a collection of {@link XMLSerializable} objects
 *
 * @author Matthew Cheung
 * @version $Id$
 *          <p/>
 *          Created on 25/03/14 11:44 AM
 */
@XmlRootElement
public class XMLSerializableList implements XMLSerializable {

    public XMLSerializableList() {
    }

    public List<XMLSerializable> list = new ArrayList<XMLSerializable>();

    public XMLSerializableList(List<XMLSerializable> list) {
        this.list = list;
    }

    @Override
    public Element toXML() {
        Element root = new Element(XMLSerializable.ROOT_ELEMENT_NAME);
        for (XMLSerializable child : list) {
            root.addContent(child.toXML());
        }
        return root;
    }

    @Override
    public void fromXML(Element element) throws XMLSerializationException {
        list = new ArrayList<XMLSerializable>();
        for (Element childElement : element.getChildren()) {
            list.add(XMLSerializer.classFromXML(childElement));
        }
    }
}
