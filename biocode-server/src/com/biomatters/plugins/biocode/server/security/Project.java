package com.biomatters.plugins.biocode.server.security;

import com.biomatters.plugins.biocode.labbench.fims.FIMSConnection;

import javax.xml.bind.annotation.XmlRootElement;
import java.sql.SQLException;
import java.util.*;

/**
 * @author Matthew Cheung
 *         Created on 13/06/14 1:51 PM
 */
@XmlRootElement
public class Project {
    public Integer id;
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
            return Projects.getProjectForId(parentProjectId).getRoleForUser();
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
