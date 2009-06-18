package com.biomatters.plugins.moorea;

import com.biomatters.geneious.publicapi.documents.PluginDocument;
import com.biomatters.geneious.publicapi.documents.DocumentField;
import com.biomatters.geneious.publicapi.documents.URN;
import com.biomatters.geneious.publicapi.documents.XMLSerializationException;

import java.util.List;
import java.util.Date;

import org.jdom.Element;

/**
 * @author Steven Stones-Havas
 * @version $Id$
 *          <p/>
 *          Created on 18/06/2009 4:10:35 PM
 */
public class PlateDocument extends MuitiPartDocument {

    public List<DocumentField> getDisplayableFields() {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public Object getFieldValue(String fieldCodeName) {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public String getName() {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public URN getURN() {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public Date getCreationDate() {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public String getDescription() {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public String toHTML() {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public Element toXML() {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public void fromXML(Element element) throws XMLSerializationException {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    public int getNumberOfParts() {
        return 0;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public Part getPart(int index) {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }
}
