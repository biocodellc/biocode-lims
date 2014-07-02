package com.biomatters.plugins.biocode.labbench.fims;

/**
 * Represents a project in the FIMS
 *
 * @author Matthew Cheung
 *         Created on 1/07/14 5:36 PM
 */
public class FimsProject {

    String id;
    String name;
    FimsProject parent;

    public FimsProject(String id, String name, FimsProject parent) {
        this.id = id;
        this.name = name;
        this.parent = parent;
    }

    /**
     * @return An identifier that is unique for the project within the FIMS
     */
    public String getId() {
        return id;
    }

    /**
     *
     * @return The project name
     */
    public String getName() {
        return name;
    }

    /**
     *
     * @return This projects parent.  Or null if this project is a top level project.
     */
    public FimsProject getParent() {
        return parent;
    }
}
