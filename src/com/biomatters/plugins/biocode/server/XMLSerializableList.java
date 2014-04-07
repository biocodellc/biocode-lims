package com.biomatters.plugins.biocode.server;

import com.biomatters.geneious.publicapi.documents.XMLSerializable;
import com.biomatters.geneious.publicapi.documents.XMLSerializationException;
import com.biomatters.geneious.publicapi.documents.XMLSerializer;
import org.jdom.Element;

import javax.xml.bind.annotation.XmlRootElement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
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
public class XMLSerializableList<T extends XMLSerializable> implements XMLSerializable {

    public XMLSerializableList() {
    }

    private Class<T> type;
    private List<T> list = new ArrayList<T>();

    public XMLSerializableList(Class<T> type, List<T> list) {
        this.list = list;
        this.type = type;
    }

    private static final String TYPE = "class";
    @Override
    public Element toXML() {
        Element root = new Element(XMLSerializable.ROOT_ELEMENT_NAME);
        root.setAttribute(TYPE, type.getName());

        for (XMLSerializable child : list) {
            root.addContent(XMLSerializer.classToXML("child", child));
        }
        return root;
    }

    @Override
    public void fromXML(Element element) throws XMLSerializationException {
        list = new ArrayList<T>();
        try {
            //noinspection unchecked
            type = (Class<T>)Class.forName(element.getAttributeValue(TYPE));
        } catch (ClassNotFoundException e) {
            throw new XMLSerializationException(e.getMessage(), e);
        }
        for (Element childElement : element.getChildren()) {
            list.add(XMLSerializer.classFromXML(childElement, type));
        }
    }

    /**
     *
     * @return An unmodifiable list
     */
    public List<T> getList() {
        return Collections.unmodifiableList(list);
    }

    /**
     * Adds an item to the list
     * @param item
     */
    public void add(T item) {
        list.add(item);
    }

    /**
     * Adds a collection of items to the list
     * @param items
     */
    public void addAll(Collection<? extends T> items) {
        list.addAll(items);
    }
}
