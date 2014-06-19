package com.biomatters.plugins.biocode.server;

import com.biomatters.plugins.biocode.server.security.Project;
import com.biomatters.plugins.biocode.server.security.Role;
import com.biomatters.plugins.biocode.server.security.User;

import javax.ws.rs.*;
import javax.ws.rs.core.GenericEntity;
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
    @Produces("application/xml")
    public Response list() {
        List<Project> projList = new ArrayList<Project>(Project.getListReadableByUser());
        return Response.ok(new GenericEntity<List<Project>>(projList){}).build();
    }

    @GET
    @Produces("application/xml")
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

    private Project getProjectForId(int id) {
        for (Project p : new ArrayList<Project>(Project.list)) {
            if(p.id == id) {
                return p;
            }
        }
        return null;
    }

    @GET
    @Produces("application/xml")
    @Path("{projectId}/roles")
    public Response listRoles(@PathParam("projectId")int projectId) {
        // todo can't use map?
        Project project = getProjectForId(projectId);
        if(project == null) {
            throw new NotFoundException("No project for id " + projectId);
        }
        return Response.ok(new GenericEntity<Map<User, Role>>(project.userRoles){}).build();
    }

    @PUT
    @Consumes("text/plain")
    @Path("{id}/roles/{username}/role")
    public void addRole(@PathParam("id")int projectId, @PathParam("username")String username, String rolename) {
        Project project = getProjectForId(projectId);
        if(project == null) {
            throw new NotFoundException("No project for id " + projectId);
        }
        User user = new Users().getUser(username);
        if(user == null) {
            throw new NotFoundException("No user for username " + username);
        }
        try {
            project.userRoles.put(user, Role.valueOf(rolename));
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("No role " + rolename);
        }
    }
}
