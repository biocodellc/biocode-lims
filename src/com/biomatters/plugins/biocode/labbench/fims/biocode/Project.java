package com.biomatters.plugins.biocode.labbench.fims.biocode;

import com.biomatters.geneious.publicapi.databaseservice.DatabaseServiceException;
import com.biomatters.geneious.publicapi.utilities.xml.FastSaxBuilder;
import org.jdom.Element;
import org.jdom.JDOMException;

import javax.xml.bind.annotation.XmlElement;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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

    public List<Field> getFields() {
        if(fields == null) {
            try {
                fields = retrieveFieldsFromXmlConfigurationFile();
            } catch (DatabaseServiceException e) {
                return Collections.emptyList();  // todo  Should the user be alerted?
            }
        }
        return fields;
    }

    private List<Project.Field> retrieveFieldsFromXmlConfigurationFile() throws DatabaseServiceException {
        List<Project.Field> fromXml = new ArrayList<Field>();
        String expeditionXmlLocation = xmlLocation;
        try {
            URL url = new URL(expeditionXmlLocation);
            InputStream inputStream = url.openStream();
            FastSaxBuilder builder = new FastSaxBuilder();
            Element element = builder.build(inputStream).getRootElement();
            Element mappingElement = element.getChild("mapping");
            if(mappingElement == null) {
                throw new DatabaseServiceException("Invalid configuration for " + title + ". " +
                        "Missing mapping element", false);
            }
            Element entityElement = mappingElement.getChild("entity");
            if(entityElement == null) {
                throw new DatabaseServiceException("Invalid configuration for " + title + ". " +
                        "Missing mapping/entity element", false);
            }
            List<Element> children = entityElement.getChildren("attribute");
            for (Element child : children) {
                fromXml.add(new Project.Field(child.getAttributeValue("uri"), child.getAttributeValue("column")));
            }
        } catch (MalformedURLException e) {
            throw new DatabaseServiceException(e, "Configuration file location for " + title
                    + " is an invalid URL (" + expeditionXmlLocation + ")", false);
        } catch (IOException e) {
            throw new DatabaseServiceException(e, "Failed to load fields for expedition " + title, true);
        } catch (JDOMException e) {
            throw new DatabaseServiceException(e, "Failed to load fields for expedition " + title, false);
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
    }
}
