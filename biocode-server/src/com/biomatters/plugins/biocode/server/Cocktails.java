package com.biomatters.plugins.biocode.server;

import com.biomatters.geneious.publicapi.databaseservice.DatabaseServiceException;
import com.biomatters.geneious.publicapi.utilities.StringUtilities;
import com.biomatters.plugins.biocode.labbench.BiocodeService;
import com.biomatters.plugins.biocode.labbench.reaction.*;

import javax.ws.rs.*;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * @author Matthew Cheung
 * @version $Id$
 *          <p/>
 *          Created on 6/04/14 11:29 PM
 */
@Path("cocktails")
public class Cocktails {

    @Produces("application/xml")
    @GET
    @Path("{type}")
    public XMLSerializableList<? extends Cocktail> getForType(@PathParam("type")String type) {
        try {
            if(getType(type) == Cocktail.Type.pcr) {
                return new XMLSerializableList<PCRCocktail>(PCRCocktail.class,
                        BiocodeService.getInstance().getActiveLIMSConnection().getPCRCocktailsFromDatabase());
            } else {
                return new XMLSerializableList<CycleSequencingCocktail>(CycleSequencingCocktail.class,
                                    BiocodeService.getInstance().getActiveLIMSConnection().getCycleSequencingCocktailsFromDatabase());
            }
        } catch (DatabaseServiceException e) {
            throw new InternalServerErrorException(e.getMessage(), e);
        }
    }

    @Consumes("application/xml")
    @POST
    public void add(XMLSerializableList<Cocktail> toAdd) {
        try {
            BiocodeService.getInstance().getActiveLIMSConnection().addCocktails(toAdd.getList());
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
    public void delete(XMLSerializableList<Cocktail> toDelete) {
        try {
            BiocodeService.getInstance().getActiveLIMSConnection().deleteCocktails(toDelete.getList());
        } catch (DatabaseServiceException e) {
            throw new InternalServerErrorException(e.getMessage(), e);
        }
    }

    @Consumes("application/xml")
    @GET
    @Path("{type}/{id}/plates")
    public String getPlatesForCocktail(@PathParam("type")String type, @PathParam("id")int thermocycleId) {
        try {
            Collection<String> plateNames = BiocodeService.getInstance().getActiveLIMSConnection().getPlatesUsingCocktail(Reaction.Type.valueOf(type), thermocycleId);
            return StringUtilities.join("\n", plateNames);
        } catch (DatabaseServiceException e) {
            throw new InternalServerErrorException(e.getMessage(), e);
        } catch(IllegalArgumentException e) {
            throw new NotFoundException();
        }
    }
}
