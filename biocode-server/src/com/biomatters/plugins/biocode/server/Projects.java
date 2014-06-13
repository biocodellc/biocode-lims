package com.biomatters.plugins.biocode.server;

import com.biomatters.plugins.biocode.server.security.Project;

import javax.ws.rs.*;

/**
 * @author Matthew Cheung
 *         Created on 13/06/14 2:22 PM
 */
@Path("project")
public class Projects {

    @GET
    @Path("{id}")
    public Project getForId(@PathParam("id")int id) {
        return Project.getForId(id);
    }

    @PUT
    public void add(Project project) {

    }

    @DELETE
    @Path("{id}")
    public void delete(@PathParam("id")int id) {
    }

    @PUT
    @Path("{id}/{username}/{rolename}")
    public void addRole(@PathParam("id")String projectId, @PathParam("username")String username, @PathParam("rolename")String rolename) {

    }
}
