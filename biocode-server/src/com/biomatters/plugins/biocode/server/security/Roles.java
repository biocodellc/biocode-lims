package com.biomatters.plugins.biocode.server.security;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
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
}
