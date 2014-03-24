package com.biomatters.plugins.biocode.server;

import javax.inject.Singleton;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;

/**
 * Provides basic information about the server.
 *
 * @author Matthew Cheung
 * @version $Id$
 *          <p/>
 *          Created on 23/03/14 6:56 PM
 */
@Path("info")
@Singleton
public class Info {
    @Produces("text/plain")
    @GET
    @Path("version")
    public String version() {
        return "0.1";
    }

    @Produces("text/plain")
    @GET
    @Path("details")
    public String getServerDetails() {
        return "Version:" + System.getProperty("java.version");
    }


}
