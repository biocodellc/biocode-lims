package com.biomatters.plugins.biocode.server.security;

import javax.ws.rs.*;
import javax.ws.rs.core.GenericEntity;
import javax.ws.rs.core.Response;
import java.util.List;

/**
 * @author Matthew Cheung
 *         Created on 30/06/14 11:56 AM
 */
@Path("roles")
public class Roles {
    @GET
    @Produces({"application/json;qs=1", "application/xml;qs=0.5"})
    public Response list() {
        return Response.ok(new GenericEntity<List<Role>>(Role.values()) { }).build();
    }

    @GET
    @Produces({"application/json;qs=1", "application/xml;qs=0.5"})
    @Path("{name}")
    public Role forName(@PathParam("name")String name) {
        for (Role role : Role.values()) {
            if(role.name.equalsIgnoreCase(name)) {
                return role;
            }
        }
        throw new NotFoundException("No role for " + name);
    }
}
