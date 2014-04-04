package com.biomatters.plugins.biocode.server;

import com.biomatters.geneious.publicapi.documents.XMLSerializable;
import com.biomatters.geneious.publicapi.documents.XMLSerializationException;
import org.jdom.Element;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Matthew Cheung
 * @version $Id$
 *          <p/>
 *          Created on 4/04/14 4:02 PM
 */
public class StringMap implements XMLSerializable {

    private Map<String, String> map;

    public StringMap() {
    }

    public StringMap(Map<String, String> map) {
        this.map = map;
    }

    @Override
    public Element toXML() {
        Element root = new Element(XMLSerializable.ROOT_ELEMENT_NAME);
        for (Map.Entry<String, String> entry : map.entrySet()) {
            root.addContent(new Element(entry.getKey()).setText(entry.getValue()));
        }
        return root;
    }

    @Override
    public void fromXML(Element element) throws XMLSerializationException {
        map = new HashMap<String, String>();
        for (Element child : element.getChildren()) {
            map.put(child.getName(), child.getValue());
        }
    }

    public Map<String, String> getMap() {
        return Collections.unmodifiableMap(map);
    }
}
