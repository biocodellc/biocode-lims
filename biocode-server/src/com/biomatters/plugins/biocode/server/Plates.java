package com.biomatters.plugins.biocode.server;

import com.biomatters.geneious.publicapi.databaseservice.DatabaseServiceException;
import com.biomatters.geneious.publicapi.utilities.StringUtilities;
import com.biomatters.plugins.biocode.labbench.BadDataException;
import com.biomatters.plugins.biocode.labbench.BiocodeService;
import com.biomatters.plugins.biocode.labbench.plates.GelImage;
import com.biomatters.plugins.biocode.labbench.plates.Plate;
import com.biomatters.plugins.biocode.labbench.reaction.ExtractionReaction;
import com.biomatters.plugins.biocode.labbench.reaction.Reaction;
import jebl.util.ProgressListener;

import javax.ws.rs.*;
import java.util.*;

/**
 * @author Matthew Cheung
 * @version $Id$
 *          <p/>
 *          Created on 23/03/14 5:20 PM
 */
@Path("plates")
public class Plates {

    @GET
    public String list() {
        return "There are no plates here yet";
    }

    @GET
    @Path("{plateId}")
    public String getTissue(@PathParam("plateId")String plateId) {
        throw new NotFoundException("No plates for " + plateId);
    }

    @PUT
    @Consumes("application/xml")
    public void savePlate(Plate plate) {
        try {
            LIMSInitializationServlet.getLimsConnection().savePlate(plate, ProgressListener.EMPTY);
        } catch (DatabaseServiceException e) {
            throw new WebApplicationException(e.getMessage(), e);
        } catch (BadDataException e) {
            throw new WebApplicationException(e.getMessage(), e);
        }
    }

    @POST
    @Consumes("application/xml")
    @Produces("text/plain")
    @Path("delete")
    public String deletePlate(Plate plate) {
        try {
            Set<Integer> ids = LIMSInitializationServlet.getLimsConnection().deletePlate(plate, ProgressListener.EMPTY);
            return StringUtilities.join("\n", ids);
        } catch (DatabaseServiceException e) {
            throw new WebApplicationException(e.getMessage(), e);
        }
    }


    @PUT
    @Consumes("text/plain")
    @Path("{id}/name")
    public void renamePlate(@PathParam("id")int id, String newName) {
        try {
            LIMSInitializationServlet.getLimsConnection().renamePlate(id, newName);
        } catch (DatabaseServiceException e) {
            throw new WebApplicationException(e.getMessage(), e);
        }
    }

    @GET
    @Produces("application/xml")
    @Path("empty")
    public XMLSerializableList<Plate> getEmptyPlates(@QueryParam("platesToCheck")String platesToCheck) {
        if(platesToCheck == null || platesToCheck.trim().isEmpty()) {
            return null;
        }

        String[] idStrings = platesToCheck.split(",");
        List<Integer> ids = new ArrayList<Integer>();
        for (String idString : idStrings) {
            try {
                ids.add(Integer.parseInt(idString));
            } catch (NumberFormatException e) {
                throw new BadRequestException("plansToCheck contained bad ID: " + idString + " not an integer", e);
            }
        }
        try {
            return new XMLSerializableList<Plate>(Plate.class,
                    LIMSInitializationServlet.getLimsConnection().getEmptyPlates(ids));
        } catch (DatabaseServiceException e) {
            throw new InternalServerErrorException(e.getMessage(), e);
        }
    }

    @PUT
    @Consumes("application/xml")
    @Path("reactions")
    public void saveReactions(XMLSerializableList<Reaction> reactions, @QueryParam("type") String type) {
        Reaction.Type reactionType = Reaction.Type.valueOf(type);
        try {
            List<Reaction> reactionList = reactions.getList();
            LIMSInitializationServlet.getLimsConnection().saveReactions(reactionList.toArray(
                    new Reaction[reactionList.size()]
            ), reactionType, ProgressListener.EMPTY);
        } catch (DatabaseServiceException e) {
            throw new WebApplicationException(e.getMessage(), e);
        }
    }

    @GET
    @Path("extractions")
    public XMLSerializableList<ExtractionReaction> getExtractionsForIds(@QueryParam("ids")String ids) {
        if(ids == null) {
            throw new BadRequestException("Must specify ids");
        }
        try {
            return new XMLSerializableList<ExtractionReaction>(ExtractionReaction.class,
                    LIMSInitializationServlet.getLimsConnection().getExtractionsForIds(
                    Arrays.asList(ids.split(","))
            ));
        } catch (DatabaseServiceException e) {
            throw new WebApplicationException(e.getMessage(), e);
        }
    }

    @GET
    @Path("tissues")
    public StringMap getTissueIdsForExtractionIds(@QueryParam("type")String table, @QueryParam("extractionIds")String ids) {
        if(ids == null) {
            throw new BadRequestException("Must specify extractionIds");
        }
        if(table == null) {
            throw new BadRequestException("Must specify type");
        }

        try {
            return new StringMap(LIMSInitializationServlet.getLimsConnection().getTissueIdsForExtractionIds(
                    table, Arrays.asList(ids.split(","))));
        } catch (DatabaseServiceException e) {
            throw new WebApplicationException(e.getMessage(), e);
        }
    }

    @GET
    @Produces("application/xml")
    @Path("{plateId}/gels")
    public List<GelImage> getGels(@PathParam("plateId")int plateId) {
        try {
            Map<Integer, List<GelImage>> map = LIMSInitializationServlet.getLimsConnection().getGelImages(
                    Collections.singletonList(plateId));
            return map.get(plateId);
        } catch (DatabaseServiceException e) {
            throw new InternalServerErrorException(e.getMessage(), e);
        }
    }
}
