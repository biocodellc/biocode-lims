package com.biomatters.plugins.biocode.server;

import com.biomatters.geneious.publicapi.documents.XMLSerializable;
import com.biomatters.geneious.publicapi.documents.XMLSerializer;
import org.jdom.output.Format;
import org.jdom.output.XMLOutputter;

import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.ext.MessageBodyWriter;
import javax.ws.rs.ext.Provider;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;

/**
 * Can be used to write an XMLSerializable to a response
 *
 * @author Matthew Cheung
 * @version $Id$
 *          <p/>
 *          Created on 25/03/14 9:51 AM
 */
@Provider
@Produces("application/xml")
public class XMLSerializableMessageWriter implements MessageBodyWriter<XMLSerializable> {
    @Override
    public boolean isWriteable(Class<?> aClass, Type type, Annotation[] annotations, MediaType mediaType) {
        return XMLSerializable.class.isAssignableFrom(aClass);
    }

    @Override
    public long getSize(XMLSerializable xmlSerializable, Class<?> aClass, Type type, Annotation[] annotations, MediaType mediaType) {
        // Don't bother implementing.  Ignored from Jersey 2.0 onwards
        return 0;
    }

    @Override
    public void writeTo(XMLSerializable xmlSerializable, Class<?> aClass, Type type, Annotation[] annotations, MediaType mediaType, MultivaluedMap<String, Object> stringObjectMultivaluedMap, OutputStream outputStream) throws IOException, WebApplicationException {
        new XMLOutputter(Format.getPrettyFormat()).output(XMLSerializer.classToXML("root", xmlSerializable), outputStream);
    }
}
