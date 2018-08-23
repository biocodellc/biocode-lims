package com.biomatters.plugins.biocode.labbench.fims.geome;

import java.util.ArrayList;
import java.util.List;

public class ProjectConfig {
    public List<Entity> entities = new ArrayList<>();
    public static class Entity {
        String type;
        List<Project.Field> attributes = new ArrayList<>();
    }
}
