package com.biomatters.plugins.biocode.server;

import com.biomatters.plugins.biocode.labbench.BiocodeService;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;

/**
 * @author Matthew Cheung
 * @version $Id$
 *          <p/>
 *          Created on 6/04/14 11:11 PM
 */
@Path("permissions")
public class Permissions {

    // todo This can probably be removed once we move onto alpha with real user roles and privileges.
    @Produces("text/plain")
    @Path("delete/{table}")
    @GET
    public boolean canDelete(@PathParam("table")String table) {
        return LIMSInitializationServlet.getLimsConnection().deleteAllowed(table);
    }
}
