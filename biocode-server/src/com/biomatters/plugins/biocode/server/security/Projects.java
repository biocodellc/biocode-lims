package com.biomatters.plugins.biocode.server.security;

import com.biomatters.geneious.publicapi.databaseservice.DatabaseServiceException;
import com.biomatters.geneious.publicapi.utilities.StringUtilities;
import com.biomatters.plugins.biocode.labbench.fims.FIMSConnection;
import com.biomatters.plugins.biocode.labbench.fims.FimsProject;
import com.biomatters.plugins.biocode.server.LIMSInitializationListener;
import com.biomatters.plugins.biocode.utilities.SqlUtilities;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;

import javax.sql.DataSource;
import javax.ws.rs.*;
import javax.ws.rs.core.GenericEntity;
import javax.ws.rs.core.Response;
import java.sql.*;
import java.util.*;

/**
 * @author Matthew Cheung
 *         Created on 13/06/14 2:22 PM
 */
@Path("projects")
public class Projects {

    @GET
    @Produces({"application/json;qs=1", "application/xml;qs=0.5"})
    public Response list() {
        List<Project> projectList = getProjectsForId(LIMSInitializationListener.getDataSource());
        return Response.ok(new GenericEntity<List<Project>>(projectList) { }).build();
    }

    static List<Project> getProjectsForId(DataSource dataSource, Integer... ids) {
        List<Project> projectList;
        Connection connection = null;
        try {
            connection = dataSource.getConnection();

            String queryString = "SELECT * FROM project " +
                    "LEFT OUTER JOIN project_role ON project.id = project_role.project_id " +
                    "LEFT OUTER JOIN users ON users.username = project_role.username " +
                    "LEFT OUTER JOIN authorities ON users.username = authorities.username ";
            if(ids.length > 0) {
                String questionMarkString = getQuestionMarkString(ids.length);
                queryString += "WHERE project.id IN (" + questionMarkString + ") ";
            }

            PreparedStatement select = connection.prepareStatement(queryString + "ORDER BY project.id");
            for(int i=0; i<ids.length; i++) {
                select.setObject(i+1, ids[i]);
            }
            ResultSet resultSet = select.executeQuery();
            projectList = getProjectsForResultSet(resultSet);

        } catch (SQLException e) {
            throw new InternalServerErrorException("Encountered an error accessing the database: " + e.getMessage(), e);
        } finally {
            SqlUtilities.closeConnection(connection);
        }
        return projectList;
    }

    private static String getQuestionMarkString(int count) {
        String[] questionMarks = new String[count];
        Arrays.fill(questionMarks, "?");
        return StringUtilities.join(",", Arrays.asList(questionMarks));
    }

    private static List<Project> getProjectsForResultSet(ResultSet resultSet) throws SQLException {
        Map<Integer, Project> projects = new HashMap<Integer, Project>();
        while(resultSet.next()) {
            int projectId = resultSet.getInt("id");
            Project project = projects.get(projectId);
            if(project == null) {
                project = new Project();
                project.id = projectId;
                project.globalId = resultSet.getString("external_id");
                project.description = resultSet.getString("description");
                project.name = resultSet.getString("name");
                project.parentProjectId = resultSet.getInt("parent");
                if(resultSet.wasNull()) {
                    project.parentProjectId = -1;
                }
                projects.put(projectId, project);
            }
            User user = Users.createUserFromResultSetRow(resultSet);
            if(user != null) {
                project.userRoles.put(user, Role.forId(resultSet.getInt("role")));
            }
        }
        return new ArrayList<Project>(projects.values());
    }

    @GET
    @Produces({"application/json;qs=1", "application/xml;qs=0.5"})
    @Path("{id}")
    public Project getForId(@PathParam("id")int id) {
        return getProjectForId(LIMSInitializationListener.getDataSource(), id);
    }

    static Project getProjectForId(DataSource dataSource, int id) {
        List<Project> projectList = getProjectsForId(dataSource, id);
        if(projectList.isEmpty()) {
            throw new NotFoundException("No project for id " + id);
        } else {
            assert(projectList.size() == 1);
            return projectList.get(0);
        }
    }

    static Project addProject(DataSource dataSource, Project project) {
        Connection connection = null;
        try {
            connection = dataSource.getConnection();
            SqlUtilities.beginTransaction(connection);

            if(project.id == null) {
                PreparedStatement getNextId = connection.prepareStatement("SELECT Max(id)+1 FROM project");
                ResultSet resultSet = getNextId.executeQuery();
                if(resultSet.next()) {
                    project.id = resultSet.getInt(1);
                } else {
                    project.id = 1;
                }
                resultSet.close();
            }

            PreparedStatement insert = connection.prepareStatement("INSERT INTO project(id,external_id,name,description,parent) VALUES(?,?,?,?,?)");
            insert.setObject(1, project.id);
            insert.setObject(2, project.globalId);
            insert.setObject(3, project.name);
            insert.setObject(4, project.description);
            if(project.parentProjectId == -1) {
                insert.setNull(5, Types.INTEGER);
            } else {
                insert.setObject(5, project.parentProjectId);
            }
            int inserted = insert.executeUpdate();
            if(inserted > 1) {
                throw new InternalServerErrorException("Inserted " + inserted + " projects instead of just 1.  Transaction rolled back");
            }

            addRolesForProject(connection, project);

            SqlUtilities.commitTransaction(connection);
            return project;
        } catch (SQLException e) {
            throw new InternalServerErrorException("Encountered an error accessing the database: " + e.getMessage(), e);
        } finally {
            SqlUtilities.closeConnection(connection);
        }
    }

    private static void addRolesForProject(Connection connection, Project project) throws SQLException {
        if(project.userRoles.isEmpty()) {
            return;
        }
        PreparedStatement assignRole = connection.prepareStatement("INSERT INTO project_role(project_id, username, role) VALUES(?,?,?)");
        assignRole.setObject(1, project.id);
        for (Map.Entry<User, Role> entry : project.userRoles.entrySet()) {
            assignRole.setObject(2, entry.getKey().username);
            assignRole.setObject(3, entry.getValue().id);
            assignRole.addBatch();
        }
        int[] updates = assignRole.executeBatch();
        for (int updateResult : updates) {
            if(updateResult != 1 && updateResult != PreparedStatement.SUCCESS_NO_INFO) {
                throw new InternalServerErrorException("Failed to insert project roles.");
            }
        }
    }

    static void deleteProject(DataSource dataSource, int id) {
        Connection connection = null;
        try {
            connection = dataSource.getConnection();
            SqlUtilities.beginTransaction(connection);
            PreparedStatement delete = connection.prepareStatement("DELETE FROM project WHERE id = ?");
            delete.setObject(1, id);
            int deleted = delete.executeUpdate();
            if(deleted > 1) {
                throw new InternalServerErrorException("Deleted " + deleted + " projects instead of just 1.  Transaction rolled back");
            }
            SqlUtilities.commitTransaction(connection);
        } catch (SQLException e) {
            throw new InternalServerErrorException("Encountered an error accessing the database: " + e.getMessage(), e);
        } finally {
            SqlUtilities.closeConnection(connection);
        }
    }

    @PUT
    @Consumes({"application/json", "application/xml"})
    @Path("{id}")
    public void updateProject(@PathParam("id")int id, Project project) {
        if(project.id != null && project.id != id) {
            throw new BadRequestException("Cannot change project ID");
        }
        if(project.id == null) {
            project.id = id;
        }
        DataSource dataSource = LIMSInitializationListener.getDataSource();
        updateProject(dataSource, project);
    }

    static void updateProject(DataSource dataSource, Project project) {
        Connection connection = null;
        try {
            connection = dataSource.getConnection();
            SqlUtilities.beginTransaction(connection);
            PreparedStatement update = connection.prepareStatement("UPDATE project SET " +
                    "name = ?, description = ?, parent = ? WHERE id = ?"
            );
            update.setObject(1, project.name);
            update.setObject(2, project.description);
            if(project.parentProjectId == -1) {
                update.setNull(3, Types.INTEGER);
            } else {
                update.setObject(3, project.parentProjectId);
            }
            update.setObject(4, project.id);
            int updated = update.executeUpdate();
            if(updated > 1) {
                throw new InternalServerErrorException("Updated " + updated + " projects instead of just 1.  Transaction rolled back");
            }

            clearProjectRoles(connection, project.id);
            addRolesForProject(connection, project);

            SqlUtilities.commitTransaction(connection);
        } catch (SQLException e) {
            throw new InternalServerErrorException("Encountered an error accessing the database: " + e.getMessage(), e);
        } finally {
            SqlUtilities.closeConnection(connection);
        }
    }

    static void clearProjectRoles(Connection connection, int projectId, String... usernames) throws SQLException {
        String statement = "DELETE FROM project_role WHERE project_id = ?";
        if(usernames.length > 0) {
            statement += " AND username IN (" + getQuestionMarkString(usernames.length) + ")";
        }
        PreparedStatement clearProjectRoles = connection.prepareStatement(statement);
        clearProjectRoles.setObject(1, projectId);
        for(int i=0; i<usernames.length; i++) {
            clearProjectRoles.setObject(i+2, usernames[i]);
        }
        clearProjectRoles.executeUpdate();
    }

    @GET
    @Produces({"application/json;qs=1", "application/xml;qs=0.5"})
    @Path("{projectId}/roles")
    public Response listRoles(@PathParam("projectId")int projectId) {
        Project project = getForId(projectId);
        if(project == null) {
            throw new NotFoundException("No project for id " + projectId);
        }
        return Response.ok(new GenericEntity<List<UserRole>>(
                UserRole.forMap(project.userRoles)){}).build();
    }

    @GET
    @Produces({"application/json;qs=1", "application/xml;qs=0.5"})
    @Path("{id}/roles/{username}")
    public Role listRolesForUser(@PathParam("id")int projectId, @PathParam("username")String username) {
        Project project = getForId(projectId);
        Role role = null;
        for (Map.Entry<User, Role> entry : project.userRoles.entrySet()) {
            if(username.equals(entry.getKey().username)) {
                role = entry.getValue();
            }
        }
        if(role == null) {
            throw new NotFoundException("User does not belong to project");
        } else {
            return role;
        }
    }

    @PUT
    @Consumes({"application/json", "application/xml"})
    @Path("{id}/roles/{username}")
    public void setRole(@PathParam("id") int projectId, @PathParam("username") String username, Role role) {
        setProjectRoleForUsername(LIMSInitializationListener.getDataSource(), projectId, username, role);
    }

    static void setProjectRoleForUsername(DataSource dataSource, int projectId, String username, Role role) {
        Project project = getProjectForId(dataSource, projectId);
        Role current = null;
        for (Map.Entry<User, Role> entry : project.userRoles.entrySet()) {
            if(entry.getKey().username.equals(username)) {
                current = entry.getValue();
            }
        }
        if(current == role) {
            return;
        }
        String sql;
        if(current == null) {
            sql = "INSERT INTO project_role (role, username, project_id) VALUES (?,?,?)";
        } else {
            sql = "UPDATE project_role SET role = ? WHERE username = ? AND project_id = ?";
        }
        Connection connection = null;
        try {
            connection = dataSource.getConnection();
            SqlUtilities.beginTransaction(connection);
            PreparedStatement statement = connection.prepareStatement(sql);
            statement.setObject(1, role.id);
            statement.setObject(2, username);
            statement.setObject(3, projectId);
            int numRows = statement.executeUpdate();
            if(numRows != 1) {
                throw new InternalServerErrorException("Updated " + numRows + " project roles instead of just 1.  Transaction rolled back");
            }
            SqlUtilities.commitTransaction(connection);
        } catch (SQLException e) {
            throw new InternalServerErrorException("Encountered an error accessing the database: " + e.getMessage(), e);
        } finally {
            SqlUtilities.closeConnection(connection);
        }
    }

    @DELETE
    @Path("{id}/roles/{username}")
    public void deleteRole(@PathParam("id")int projectId, @PathParam("username")String username) {
        DataSource dataSource = LIMSInitializationListener.getDataSource();
        removeUserFromProject(dataSource, projectId, username);
    }

    static void removeUserFromProject(DataSource dataSource, int projectId, String username) {
        Connection connection = null;
        try {
            connection = dataSource.getConnection();
            SqlUtilities.beginTransaction(connection);
            clearProjectRoles(connection, projectId, username);
            SqlUtilities.commitTransaction(connection);
        } catch (SQLException e) {
            throw new InternalServerErrorException("Encountered an error accessing the database: " + e.getMessage(), e);
        } finally {
            SqlUtilities.closeConnection(connection);
        }
    }

    public static void updateProjectsFromFims(DataSource dataSource, FIMSConnection fimsConnection) throws DatabaseServiceException {
        Multimap<String, String> parentToChildren = ArrayListMultimap.create();
        Map<String, Project> toAddToLims = new HashMap<String, Project>();
        List<FimsProject> fromFims = fimsConnection.getProjects();
        for (FimsProject toAdd : fromFims) {
            Project limsProjectToAdd = new Project();
            limsProjectToAdd.name = toAdd.getName();
            limsProjectToAdd.globalId = toAdd.getId();
            toAddToLims.put(limsProjectToAdd.globalId, limsProjectToAdd);
            FimsProject parent = toAdd.getParent();
            parentToChildren.put(parent == null ? null : parent.getId(), toAdd.getId());
        }

        List<Project> projectsInDatabase = getProjectsForId(dataSource);
        Map<String, Project> projects = new HashMap<String, Project>();
        for (Project project : projectsInDatabase) {
            projects.put(project.globalId, project);
        }
        try {
            updateProjectHierarchy(null, dataSource, projects, toAddToLims, parentToChildren.asMap());
        } catch (WebApplicationException e) {
            throw new DatabaseServiceException(e, "Failed to update projects: " + e.getMessage(), false);
        }
    }

    private static void updateProjectHierarchy(Project parent, DataSource dataSource, Map<String, Project> projectsInLims, Map<String, Project> projectsToAddFromFims, Map<String, Collection<String>> parentsToChildren) {
        Collection<String> toAdd = parentsToChildren.get(parent == null ? null : parent.globalId);

        if(toAdd != null) {
            int idOfParent = parent == null ? -1 : parent.id;
            for (String id : toAdd) {
                Project child = projectsToAddFromFims.get(id);
                Project inDatabase = projectsInLims.get(id);
                if (inDatabase == null) {
                    child.parentProjectId = idOfParent;
                    inDatabase = Projects.addProject(dataSource, child);
                } else {
                    boolean needsUpdating = false;
                    if (idOfParent != inDatabase.parentProjectId) {
                        inDatabase.parentProjectId = idOfParent;
                        needsUpdating = true;
                    }
                    if (!child.name.equals(inDatabase.name)) {
                        inDatabase.name = child.name;
                        needsUpdating = true;
                    }
                    if (needsUpdating) {
                        Projects.updateProject(dataSource, inDatabase);
                    }
                }
                updateProjectHierarchy(inDatabase, dataSource, projectsInLims, projectsToAddFromFims, parentsToChildren);
            }
        }

        for (Map.Entry<String, Project> entry : projectsInLims.entrySet()) {
            if(!projectsToAddFromFims.containsKey(entry.getKey())) {
                Projects.deleteProject(dataSource, entry.getValue().id);
            }
        }
    }

    /**
     *
     * @param fimsConnection The connection to use to get {@link com.biomatters.plugins.biocode.labbench.fims.FimsProject} from.
     * @param user The user account that is attempting to retreive data.
     * @param role The role to check for.
     * @return A list of {@link FimsProject}s that the specified user is allowed to view.  Or null if there are no projects in the system.
     */
    public static List<FimsProject> getFimsProjectsUserHasAtLeastRole(DataSource dataSource, FIMSConnection fimsConnection, User user, Role role) throws DatabaseServiceException {
        List<FimsProject> projectsFromFims = fimsConnection.getProjects();
        if(projectsFromFims.isEmpty()) {
            return null;
        }
        if(role == null) {
            return projectsFromFims;
        }
        if(user.isAdministrator) {
            return projectsFromFims;
        }
        List<Project> projectsInLims = Projects.getProjectsForId(dataSource);
        Map<String, Role> roleById = new HashMap<String, Role>();
        for (Project p : projectsInLims) {
            try {
                roleById.put(p.globalId, p.getRoleForUser(dataSource, user));
            } catch (SQLException e) {
                throw new DatabaseServiceException(e, "Failed to retrieve project role: " + e.getMessage(), false);
            }
        }
        List<FimsProject> toReturn = new ArrayList<FimsProject>();
        for (FimsProject candidate : projectsFromFims) {
            Role roleInProject = roleById.get(candidate.getId());
            if(roleInProject != null && roleInProject.isAtLeast(role)) {
                toReturn.add(candidate);
            }
        }
        return toReturn;
    }
}
