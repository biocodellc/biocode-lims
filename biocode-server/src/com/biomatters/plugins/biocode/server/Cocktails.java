package com.biomatters.plugins.biocode.server;

import com.biomatters.geneious.publicapi.databaseservice.DatabaseServiceException;
import com.biomatters.geneious.publicapi.utilities.StringUtilities;
import com.biomatters.plugins.biocode.labbench.reaction.*;

import javax.ws.rs.*;
import java.util.ArrayList;
import java.util.Collection;

/**
 * @author Matthew Cheung
 *          <p/>
 *          Created on 6/04/14 11:29 PM
 */
@Path("cocktails")
public class Cocktails {

    @Produces("application/xml")
    @GET
    @Path("{type}")
    public XMLSerializableList<Cocktail> getForType(@PathParam("type")String type) {
        try {
            if(getType(type) == Cocktail.Type.pcr) {
                return new XMLSerializableList<Cocktail>(Cocktail.class,
                        new ArrayList<Cocktail>(
                        LIMSInitializationListener.getLimsConnection().getPCRCocktailsFromDatabase()));
            } else {
                return new XMLSerializableList<Cocktail>(Cocktail.class,
                        new ArrayList<Cocktail>(
                        LIMSInitializationListener.getLimsConnection().getCycleSequencingCocktailsFromDatabase()));
            }
        } catch (DatabaseServiceException e) {
            throw new InternalServerErrorException(e.getMessage(), e);
        }
    }

    @Consumes("application/xml")
    @POST
    public void add(XMLSerializableList<Cocktail> cocktails) {
        try {
            LIMSInitializationListener.getLimsConnection().addCocktails(cocktails.getList());
        } catch (DatabaseServiceException e) {
            throw new InternalServerErrorException(e.getMessage(), e);
        }
    }

    private static Cocktail.Type getType(String type) {
        try {
            return Cocktail.Type.valueOf(type);
        } catch (IllegalArgumentException e) {
            throw new NotFoundException();
        }
    }

    // Unfortunately it looks like the LIMS was written to take a thermocycle object to delete.  So we can't use
    // HTTP DELETE.  We'll have to POST a delete request instead
    @Consumes("application/xml")
    @POST
    @Path("delete")
    public void delete(XMLSerializableList<Cocktail> cocktails) {
        try {
            LIMSInitializationListener.getLimsConnection().deleteCocktails(cocktails.getList());
        } catch (DatabaseServiceException e) {
            throw new InternalServerErrorException(e.getMessage(), e);
        }
    }

    @GET
    @Path("{type}/{id}/plates")
    public String getPlatesForCocktail(@PathParam("type")String type, @PathParam("id")int thermocycleId) {
        try {
            Collection<String> plateNames = LIMSInitializationListener.getLimsConnection().getPlatesUsingCocktail(Reaction.Type.valueOf(type), thermocycleId);
            return StringUtilities.join("\n", plateNames);
        } catch (DatabaseServiceException e) {
            throw new InternalServerErrorException(e.getMessage(), e);
        } catch(IllegalArgumentException e) {
            throw new NotFoundException();
        }
    }
}