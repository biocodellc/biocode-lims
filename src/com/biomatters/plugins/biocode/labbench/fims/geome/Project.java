package com.biomatters.plugins.biocode.labbench.fims.geome;

import com.biomatters.geneious.publicapi.documents.DocumentField;
import org.codehaus.jackson.annotate.JsonIgnoreProperties;

import javax.xml.bind.annotation.XmlElement;
import java.util.*;

/**
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Project {

    @XmlElement(name = "projectId")
    public int id;
    @XmlElement(name = "projectCode")
    public String code;
    @XmlElement(name = "projectTitle")
    public String title;
    @XmlElement(name = "projectConfiguration")
    public ProjectConfiguration configuration;

    //private Boolean validForLIMS = false;

    public Project() {
    }

    public Project(int id, String code, String title, ProjectConfiguration configuration, String jsonLocation) {
        this.id = id;
        this.code = code;
        this.title = title;
        this.configuration = configuration;
    }

    public Boolean getValidForLIMS() {
        // Data Publications Is the only project configuration not suitable for LIMS.  The reason for this is that
        // the entity key for Tissue is materialSampleID which is the same as Sample
        // In effect this makes the Tissue entity a 1:1 mirror with the Sample entity.
        // Data Publications, as a network approved configuration, is not meant to accumulate tissues.
        // TODO: establish more robust method of filtering based on a setting from server rather than name
        try {
            if (this.configuration.name.contains("Data Publications")) {
                return false;
            } else {
                return true;
            }
        } catch (Exception e) {
            // by default we likely want to return true since exceptions here will indicate some
            // difficulty in discovering the project configuration setting, which probably means
            // this is not "Data Publications"
            return true;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Project that = (Project) o;

        if (id != that.id) return false;
        if (!code.equals(that.code)) return false;
        if (!title.equals(that.title)) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = id;
        result = 31 * result + code.hashCode();
        result = 31 * result + title.hashCode();
        return result;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Field {
        public String uri;
        public String column;
        public String dataType;

        public Field() {
        }

        DocumentField asDocumentField() {
            return new DocumentField(column, "", uri, getJavaClassForDataType(dataType), false, false);
        }
    }

    private static final Map<String, Class> DATA_TYPES_TO_CLASSES = new HashMap<>();

    static {
        DATA_TYPES_TO_CLASSES.put("BOOLEAN", Boolean.class);
        DATA_TYPES_TO_CLASSES.put("FLOAT", Double.class);
        DATA_TYPES_TO_CLASSES.put("INTEGER", Integer.class);
        DATA_TYPES_TO_CLASSES.put("STRING", String.class);
    }


    private static Class getJavaClassForDataType(String geomeDataType) {
        return DATA_TYPES_TO_CLASSES.getOrDefault(geomeDataType, String.class);
    }
}
