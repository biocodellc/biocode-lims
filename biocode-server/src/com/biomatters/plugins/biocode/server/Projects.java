package com.biomatters.plugins.biocode.server;

import com.biomatters.plugins.biocode.server.security.Project;
import com.biomatters.plugins.biocode.server.security.Role;
import com.biomatters.plugins.biocode.server.security.User;
import com.biomatters.plugins.biocode.server.security.UserRole;

import javax.ws.rs.*;
import javax.ws.rs.core.GenericEntity;
import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author Matthew Cheung
 *         Created on 13/06/14 2:22 PM
 */
@Path("projects")
public class Projects {

    @GET
    @Produces("application/json")
    public Response list() {
        List<Project> projList = new ArrayList<Project>(Project.getListReadableByUser());
        return Response.ok(new GenericEntity<List<Project>>(projList){}).build();
    }

    @GET
    @Produces("application/json")
    @Path("{id}")
    public Project getForId(@PathParam("id")int id) {
        return Project.getForId(id);
    }

    @POST
    public void add(Project project) {
        Project.list.add(project);
    }

    @DELETE
    @Path("{id}")
    public void delete(@PathParam("id")int id) {
        Project project = getProjectForId(id);
        if(project != null) {
            Project.list.remove(project);
        }

    }

    @PUT
    @Consumes("application/json")
    @Path("{id}")
    public void updateProject(@PathParam("id")int id, Project project) {

    }

    private Project getProjectForId(int id) {
        for (Project p : new ArrayList<Project>(Project.list)) {
            if(p.id == id) {
                return p;
            }
        }
        return null;
    }

    @GET
    @Produces("application/json")
    @Path("{projectId}/roles")
    public Response listRoles(@PathParam("projectId")int projectId) {
        Project project = getProjectForId(projectId);
        if(project == null) {
            throw new NotFoundException("No project for id " + projectId);
        }
        return Response.ok(new GenericEntity<List<UserRole>>(
                UserRole.forMap(project.userRoles)){}).build();
    }

    @GET
    @Produces("application/json")
    @Path("{id}/roles/{username}")
    public Role listRolesForUser(@PathParam("id")int projectId, @PathParam("username")String username) {
        return Role.ADMIN; // todo
    }

    @PUT
    @Consumes("application/json")
    @Path("{id}/roles/{username}")
    public void addRole(@PathParam("id")int projectId, @PathParam("username")String username, Role role) {
        // todo
        Project project = getProjectForId(projectId);
        if(project == null) {
            throw new NotFoundException("No project for id " + projectId);
        }
        User user = new Users().getUser(username);
        if(user == null) {
            throw new NotFoundException("No user for username " + username);
        }
        if(role == null) {
            throw new BadRequestException("Must specify role");
        }
        project.userRoles.put(user, role);
    }

    @DELETE
    @Path("{id}/roles/{username}")
    public void deleteRole(@PathParam("id")int projectId, @PathParam("username")String username) {
        // todo
    }
}
