package com.biomatters.plugins.biocode.labbench.fims.geome;

import org.codehaus.jackson.annotate.JsonIgnoreProperties;

import java.util.ArrayList;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ProjectConfig {

    public List<Entity> entities = new ArrayList<>();

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Entity {
        public String conceptAlias;
        public List<Project.Field> attributes = new ArrayList<>();
    }
}
