package com.biomatters.plugins.biocode.server.security;

import com.biomatters.geneious.publicapi.utilities.StringUtilities;
import com.biomatters.plugins.biocode.server.LIMSInitializationListener;
import com.biomatters.plugins.biocode.utilities.SqlUtilities;

import javax.sql.DataSource;
import javax.ws.rs.*;
import javax.ws.rs.core.GenericEntity;
import javax.ws.rs.core.Response;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
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
                project.description = resultSet.getString("description");
                project.name = resultSet.getString("name");
                project.parentProjectId = resultSet.getInt("parent");
                projects.put(projectId, project);
            }
            User user = Users.createUserFromResultSetRow(resultSet);
            project.userRoles.put(user, Role.forId(resultSet.getInt("role")));
        }
        return new ArrayList<Project>(projects.values());
    }

    @GET
    @Produces({"application/json;qs=1", "application/xml;qs=0.5"})
    @Path("{id}")
    public Project getForId(@PathParam("id")int id) {
        return getProjectForId(id);
    }

    static Project getProjectForId(int id) {
        List<Project> projectList = getProjectsForId(LIMSInitializationListener.getDataSource(), id);
        if(projectList.isEmpty()) {
            throw new NotFoundException("No project for id " + id);
        } else {
            assert(projectList.size() == 1);
            return projectList.get(0);
        }
    }

    @POST
    @Consumes({"application/json", "application/xml"})
    @Produces({"application/json;qs=1", "application/xml;qs=0.5"})
    public Project add(Project project) {
        return addProject(LIMSInitializationListener.getDataSource(), project);
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

            PreparedStatement insert = connection.prepareStatement("INSERT INTO project(id,name,description,parent) VALUES(?,?,?,?)");
            insert.setObject(1, project.id);
            insert.setObject(2, project.name);
            insert.setObject(3, project.description);
            insert.setObject(4, project.parentProjectId);
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

    @DELETE
    @Path("{id}")
    public void delete(@PathParam("id")int id) {
        deleteProject(LIMSInitializationListener.getDataSource(), id);
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
            update.setObject(3, project.parentProjectId);
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
        Project project = getForId(projectId);
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
            connection = LIMSInitializationListener.getDataSource().getConnection();
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
        Connection connection = null;
        try {
            connection = LIMSInitializationListener.getDataSource().getConnection();
            SqlUtilities.beginTransaction(connection);
            clearProjectRoles(connection, projectId, username);
            SqlUtilities.commitTransaction(connection);
        } catch (SQLException e) {
            throw new InternalServerErrorException("Encountered an error accessing the database: " + e.getMessage(), e);
        } finally {
            SqlUtilities.closeConnection(connection);
        }
    }
}
