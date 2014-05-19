package com.biomatters.plugins.biocode.server;

import com.biomatters.geneious.publicapi.databaseservice.DatabaseServiceException;
import com.biomatters.geneious.publicapi.utilities.StringUtilities;
import com.biomatters.plugins.biocode.labbench.reaction.Thermocycle;

import javax.ws.rs.*;
import java.util.List;

/**
 * @author Matthew Cheung
 * @version $Id$
 *          <p/>
 *          Created on 6/04/14 9:30 PM
 */
@Path("thermocycles")
public class Thermocycles {

    @Produces("application/xml")
    @GET
    @Path("{type}")
    public XMLSerializableList<Thermocycle> getForType(@PathParam("type")String type) {
        try {
            return new XMLSerializableList<Thermocycle>(
                    Thermocycle.class,
                    LIMSInitializationListener.getLimsConnection().getThermocyclesFromDatabase(getType(type)));
        } catch (DatabaseServiceException e) {
            throw new InternalServerErrorException(e.getMessage(), e);
        }
    }

    @Consumes("application/xml")
    @POST
    @Path("{type}")
    public void add(XMLSerializableList<Thermocycle> toAdd, @PathParam("type")String type) {
        try {
            LIMSInitializationListener.getLimsConnection().addThermoCycles(getType(type), toAdd.getList());
        } catch (DatabaseServiceException e) {
            throw new InternalServerErrorException(e.getMessage(), e);
        }
    }

    private static Thermocycle.Type getType(String typeString) {
        try {
            return Thermocycle.Type.valueOf(typeString);
        } catch (IllegalArgumentException e) {
            throw new NotFoundException();
        }
    }

    // Unfortunately it looks like the LIMS was written to take a thermocycle object to delete.  So we can't use
    // HTTP DELETE.  We'll have to POST a delete request instead
    @Consumes("application/xml")
    @POST
    @Path("{type}/delete")
    public void delete(@PathParam("type")String type, XMLSerializableList<Thermocycle> toDelete) {
        try {
            LIMSInitializationListener.getLimsConnection().deleteThermoCycles(getType(type), toDelete.getList());
        } catch (DatabaseServiceException e) {
            throw new InternalServerErrorException(e.getMessage(), e);
        }
    }

    @Consumes("application/xml")
    @GET
    @Path("{id}/plates")
    public String getPlatesForThermocycle(@PathParam("id")int thermocycleId) {
        try {
            List<String> plateNames = LIMSInitializationListener.getLimsConnection().getPlatesUsingThermocycle(thermocycleId);
            return StringUtilities.join("\n", plateNames);
        } catch (DatabaseServiceException e) {
            throw new InternalServerErrorException(e.getMessage(), e);
        }
    }
}
