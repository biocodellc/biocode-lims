package com.biomatters.plugins.biocode.labbench.fims.geome;

import com.biomatters.geneious.publicapi.documents.DocumentField;
import org.codehaus.jackson.annotate.JsonIgnoreProperties;

import javax.xml.bind.annotation.XmlElement;
import java.util.*;

/**
 * The projectConfiguration settings contain information about which which network approved
 * configuration this project belongs to
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProjectConfiguration {
    @XmlElement(name = "id")
    public int id;
    @XmlElement(name = "name")
    public String name;
    @XmlElement(name = "description")
    public String description;

    public ProjectConfiguration() {
    }

    public ProjectConfiguration(int id, String name, String description, String jsonLocation) {
        this.id = id;
        this.name = name;
        this.description = description;
    }
}
