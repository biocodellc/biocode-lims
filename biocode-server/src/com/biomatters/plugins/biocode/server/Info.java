package com.biomatters.plugins.biocode.server;

import com.biomatters.geneious.publicapi.databaseservice.DatabaseServiceException;
import com.biomatters.plugins.biocode.labbench.BiocodeService;

import javax.inject.Singleton;
import javax.ws.rs.*;

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
        return "Alpha Biocode LIMS Server\nAPI is likely to change\n\nJava Version:" + System.getProperty("java.version");
    }

    @Produces("text/plain")
    @GET
    @Path("properties/{id}")
    public String getProperty(@PathParam("id")String id) {
        try {
            return BiocodeService.getInstance().getActiveLIMSConnection().getProperty(id);
        } catch (DatabaseServiceException e) {
            throw new InternalServerErrorException(e.getMessage(), e);
        }
    }

    @Consumes("text/plain")
    @PUT
    @Path("properties/{id}")
    public void getProperty(@PathParam("id")String id, String value) {
        try {
            BiocodeService.getInstance().getActiveLIMSConnection().setProperty(id, value);
        } catch (DatabaseServiceException e) {
            throw new InternalServerErrorException(e.getMessage(), e);
        }
    }
}
