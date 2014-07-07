package com.biomatters.plugins.biocode.server;

import com.biomatters.geneious.publicapi.databaseservice.DatabaseServiceException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;

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
        return "0.2";
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
            return LIMSInitializationListener.getLimsConnection().getProperty(id);
        } catch (DatabaseServiceException e) {
            throw new InternalServerErrorException(e.getMessage(), e);
        }
    }

    @Consumes("text/plain")
    @PUT
    @Path("properties/{id}")
    public void getProperty(@PathParam("id")String id, String value) {
        try {
            LIMSInitializationListener.getLimsConnection().setProperty(id, value);
        } catch (DatabaseServiceException e) {
            throw new InternalServerErrorException(e.getMessage(), e);
        }
    }

    @Produces("text/plain")
    @GET
    @Path("profile")
    public String getUserProfile() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if(principal instanceof UserDetails) {
            UserDetails user = (UserDetails) principal;
            return "Username: " + user.getUsername() + "\nRoles: " + user.getAuthorities();
        } else {
            return principal.toString();
        }
    }

    @Produces("text/plain")
    @GET
    @Path("errors")
    public String getErrors() {
        String errors = LIMSInitializationListener.getErrorText();
        if(errors != null) {
            return "<p>The server configuration file is located at: " +
                    LIMSInitializationListener.getPropertiesFile().getAbsolutePath() + "</p>" +
                    "<p>Please report any errors to support@mooreabiocode.org</p>" +
                    "<p>" + errors + "</p>";
        }
        return "";
    }
}