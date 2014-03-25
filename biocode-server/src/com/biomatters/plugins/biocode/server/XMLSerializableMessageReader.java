package com.biomatters.plugins.biocode.server;

import com.biomatters.geneious.publicapi.documents.XMLSerializable;
import com.biomatters.geneious.publicapi.documents.XMLSerializationException;
import com.biomatters.geneious.publicapi.documents.XMLSerializer;
import com.biomatters.geneious.publicapi.utilities.xml.FastSaxBuilder;
import org.jdom.Document;
import org.jdom.JDOMException;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.ext.MessageBodyReader;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;

/**
 * @author Matthew Cheung
 * @version $Id$
 *          <p/>
 *          Created on 25/03/14 10:52 AM
 */
public class XMLSerializableMessageReader implements MessageBodyReader<XMLSerializable> {
    @Override
    public boolean isReadable(Class<?> aClass, Type type, Annotation[] annotations, MediaType mediaType) {
        return XMLSerializable.class.isAssignableFrom(aClass);
    }

    @Override
    public XMLSerializable readFrom(Class<XMLSerializable> xmlSerializableClass, Type type, Annotation[] annotations, MediaType mediaType, MultivaluedMap<String, String> stringStringMultivaluedMap, InputStream inputStream) throws IOException, WebApplicationException {
        try {
            FastSaxBuilder builder = new FastSaxBuilder();
            Document doc = builder.build(inputStream);
            return XMLSerializer.classFromXML(doc.getRootElement(), xmlSerializableClass);
        } catch (JDOMException e) {
            throw new WebApplicationException("Received invalid XML: " + e.getMessage(), e);
        } catch (XMLSerializationException e) {
            throw new WebApplicationException("Failed to deserialize XML: " + e.getMessage(), e);
        }
    }
}
