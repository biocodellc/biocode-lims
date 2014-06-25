package com.biomatters.plugins.biocode.server.security;

import com.biomatters.plugins.biocode.labbench.fims.FIMSConnection;
import com.biomatters.plugins.biocode.server.Users;

import javax.xml.bind.annotation.XmlRootElement;
import java.sql.SQLException;
import java.util.*;

/**
 * @author Matthew Cheung
 *         Created on 13/06/14 1:51 PM
 */
@XmlRootElement
public class Project {
    public int id;
    public String name;
    public String description = "";
    public int parentProjectId = -1;

    public Map<User, Role> userRoles = new HashMap<User, Role>();

    public Project() {
    }

    public Project(int id, String name) {
        this.id = id;
        this.name = name;

        userRoles = new HashMap<User, Role>();
    }

    public Project(int id, String name, String description) {
        this(id, name);
        this.description = description;
    }

    public static Project getForExtractionId(String extractionId) {
        return TEST;
    }

    public static Project getForId(int id) {
        return TEST;
    }

    public static Collection<Project> getListReadableByUser() {
        return list;
    }

    private static Project TEST = new Project(0, "Test Project");
    public static List<Project> list = new ArrayList<Project>();
    static {        
        TEST.userRoles.put(new User("admin", "admin", "admin", "admin", "admin@admin.co.nz", true, true), Role.ADMIN);
        TEST.userRoles.put(new User("test", "test", "test", "test", "test@test.co.nz", true, false), Role.READER);
        list.add(TEST);

        Project test2 = new Project(2, "test proj2", "this is a description");
        test2.userRoles.put(new User("admin", "admin", "admin", "admin", "admin@admin.co.nz", true, true), Role.ADMIN);
        test2.userRoles.put(new User("test", "test", "test", "test", "test@test.co.nz", true, false), Role.READER);
        list.add(test2);

        Project test3 = new Project(3, "test proj3", "ok, just for test");
        test3.userRoles.put(new User("admin", "admin", "admin", "admin", "admin@admin.co.nz", true, true), Role.ADMIN);
        test3.userRoles.put(new User("test", "test", "test", "test", "test@test.co.nz", true, false), Role.READER);
        test3.parentProjectId = test2.id;
        list.add(test3);
    }

    /**
     *
     * @return The role the current user has in the project.  Will fetch from parent groups if the user is not
     * part of the current project.
     */
    public Role getRoleForUser() throws SQLException {
        User currentUser = Users.getLoggedInUser();
        Role role = userRoles.get(currentUser);
        if(role != null) {
            return role;
        } else if(parentProjectId != -1) {
            return getForId(parentProjectId).getRoleForUser();
        } else {
            return null;
        }
    }

    /**
     * Populate projects based on the contents of FIMS fields.
     *
     * @param fims A connection to the FIMS
     * @param columnNames
     */
    public void populateFromFimsField(FIMSConnection fims, List<String> columnNames) {

    }
}
