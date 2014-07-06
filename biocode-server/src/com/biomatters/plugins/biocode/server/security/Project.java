package com.biomatters.plugins.biocode.server.security;

import com.biomatters.plugins.biocode.labbench.fims.FIMSConnection;
import com.biomatters.plugins.biocode.server.LIMSInitializationListener;

import javax.sql.DataSource;
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

    String globalId;

    public Map<User, Role> userRoles = new HashMap<User, Role>();

    public Project() {
    }

    /**
     *
     * @return The role the current user has in the project.  Will fetch from parent groups if the user is not
     * part of the current project.
     */
    public Role getRoleForUser(DataSource dataSource, User user) throws SQLException {
        Role role = userRoles.get(user);
        if(role != null) {
            return role;
        } else if(parentProjectId != -1) {
            return Projects.getProjectForId(dataSource, parentProjectId).getRoleForUser(dataSource, user);
        } else {
            return null;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Project project = (Project) o;

        if (id != null ? !id.equals(project.id) : project.id != null) return false;
        if (!name.equals(project.name)) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = id != null ? id.hashCode() : 0;
        result = 31 * result + name.hashCode();
        return result;
    }
}
