package com.biomatters.plugins.biocode.server;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;

/**
 * @author Matthew Cheung
 *          <p/>
 *          Created on 6/04/14 11:11 PM
 */
@Path("permissions")
public class Permissions {

    @Produces("text/plain")
    @Path("delete/{table}")
    @GET
    public boolean canDelete(@PathParam("table")String table) {
        // Always return true.  The server should be configured with a database account that always allows it to delete
        // from any table.  It controls write access to individual objects based on project membership rather than
        // restricting based on object type.  We should remove this method the next time we change the server API.
        return true;
    }
}
