package com.biomatters.plugins.biocode.server.security;

import java.util.Collection;
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

    public Map<User, Role> userRoles = new HashMap<User, Role>();

    public Project() {
    }

    public Project(int id, String name) {
        this.id = id;
        this.name = name;

        userRoles = new HashMap<User, Role>();
    }

    public static Project getForExtractionId(String extractionId) {
        return TEST;
    }

    public static Project getForId(int id) {
        return TEST;
    }

    public static Collection<Project> getListReadableByUser() {
        return Collections.singletonList(TEST);
    }

    private static Project TEST = new Project(0, "Test Project");
    static {
        TEST.userRoles.put(new User("admin"), Role.ADMIN);
        TEST.userRoles.put(new User("test"), Role.READER);
    }

    /**
     *
     * @return The role the current user has in the project.  Will fetch from parent groups if the user is not
     * part of the current project.
     */
    public Role getRoleForUser() {
        User currentUser = User.get();
        Role role = userRoles.get(currentUser);
        if(role != null) {
            return role;
        } else if(parent != null) {
            return parent.getRoleForUser();
        } else {
            return null;
        }
    }
}
