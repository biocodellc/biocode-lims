package com.biomatters.plugins.biocode.server.security;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Matthew Cheung
 *         Created on 13/06/14 1:51 PM
 */
public class Project {
    public int id;
    public String name;

    public Project parent;

    public Map<User, Role> userRoles;
    private static Project forId;

    public Project() {
    }

    public Project(int id, String name) {
        this.id = id;
        this.name = name;

        userRoles = new HashMap<User, Role>();
    }

    public static Project getForExtractionId() {
        return TEST;
    }

    public static Project getForId(int id) {
        return TEST;
    }

    private static Project TEST = new Project(0, "Test Project");
    static {
        TEST.userRoles = Collections.singletonMap(new User("matthew"), Role.ADMIN);
    }
}
