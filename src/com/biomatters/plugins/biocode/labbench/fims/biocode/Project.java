package com.biomatters.plugins.biocode.labbench.fims.biocode;

import com.biomatters.geneious.publicapi.components.Dialogs;
import com.biomatters.geneious.publicapi.databaseservice.DatabaseServiceException;
import com.biomatters.geneious.publicapi.utilities.xml.FastSaxBuilder;
import com.biomatters.plugins.biocode.BiocodeUtilities;
import org.jdom.Element;
import org.jdom.JDOMException;

import javax.xml.bind.annotation.XmlElement;
import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.HashSet;

/**
 * @author Matthew Cheung
 *         Created on 13/02/14 8:52 PM
 */
public class Project {

    @XmlElement(name="project_id")public int id;
    @XmlElement(name="project_code")public String code;
    @XmlElement(name="project_title")public String title;
    @XmlElement(name="biovalidator_validation_xml")public String xmlLocation;

    private List<Field> fields;

    public Project() {
    }

    public Project(int id, String code, String title, String xmlLocation) {
        this.id = id;
        this.code = code;
        this.title = title;
        this.xmlLocation = xmlLocation;
    }

    public synchronized List<Field> getFields() {
        if(fields == null) {
            try {
                fields = retrieveFieldsFromXmlConfigurationFile();
            } catch (DatabaseServiceException e) {
                BiocodeUtilities.displayExceptionDialog("Failed to Load FIMS Fields", e.getMessage(), e, null);
                return Collections.emptyList();
            }
        }
        return fields;
    }

    private List<Project.Field> retrieveFieldsFromXmlConfigurationFile() throws DatabaseServiceException {
        String expeditionXmlLocation = xmlLocation;
        //noinspection ProhibitedExceptionCaught
        try {
            URL url = new URL(expeditionXmlLocation);
            InputStream inputStream = url.openStream();
            List<Field> fields = getProjectFieldsFromXmlElement(title, inputStream);
            inputStream.close();
            return fields;
        } catch (MalformedURLException e) {
            throw new DatabaseServiceException(e, "Configuration file location for " + title
                    + " is an invalid URL (" + expeditionXmlLocation + ")", false);
        } catch (IOException e) {
            throw new DatabaseServiceException(e, "Failed to load fields for expedition " + title + " from " + xmlLocation + ": " + e.getMessage(), false);
        } catch (JDOMException e) {
            throw new DatabaseServiceException(e, "Failed to load fields for expedition " + title + " from " + xmlLocation + ": " + e.getMessage(), false);
        } catch(ArrayIndexOutOfBoundsException e) {
            throw new DatabaseServiceException(e, "Failed to load fields for expedition " + title + " from " + xmlLocation + ": " + e.getMessage(), false);
        }
    }

    public static List<Field> getProjectFieldsFromXmlElement(String projectTitle, InputStream inputStream) throws DatabaseServiceException, IOException, JDOMException {
        FastSaxBuilder builder = new FastSaxBuilder();
        InputStreamReader reader = new InputStreamReader(inputStream);
        Element element = builder.build(reader).getRootElement();
        reader.close();

        List<Field> fromXml = new ArrayList<Field>();
        Element mappingElement = element.getChild("mapping");
        if(mappingElement == null) {
            throw new DatabaseServiceException("Invalid configuration for " + projectTitle + ". " +
                    "Missing mapping element", false);
        }
        Element entityElement = mappingElement.getChild("entity");
        if(entityElement == null) {
            throw new DatabaseServiceException("Invalid configuration for " + projectTitle + ". " +
                    "Missing mapping/entity element", false);
        }
        List<Element> children = entityElement.getChildren("attribute");
        Set<String> urisSeen = new HashSet<String>();
        for (Element child : children) {
            String uri = child.getAttributeValue("uri");
            if (!urisSeen.contains(uri)) {
                fromXml.add(new Field(uri, child.getAttributeValue("column")));
                urisSeen.add(uri);
            }
        }
        return fromXml;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Project that = (Project) o;

        if (id != that.id) return false;
        if (!code.equals(that.code)) return false;
        if (!title.equals(that.title)) return false;
        if (!xmlLocation.equals(that.xmlLocation)) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = id;
        result = 31 * result + code.hashCode();
        result = 31 * result + title.hashCode();
        result = 31 * result + xmlLocation.hashCode();
        return result;
    }

    public static class Field {
        String uri;
        String name;

        public Field(String uri, String name) {
            this.uri = uri;
            this.name = name;
        }

        public String getName() {
            return name;
        }

        public String getURI() {
            return uri;
        }
    }
}
