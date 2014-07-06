package com.biomatters.plugins.biocode.server;

import com.biomatters.geneious.publicapi.databaseservice.DatabaseServiceException;
import com.biomatters.geneious.publicapi.utilities.StringUtilities;
import com.biomatters.plugins.biocode.labbench.BadDataException;
import com.biomatters.plugins.biocode.labbench.BiocodeService;
import com.biomatters.plugins.biocode.labbench.FimsSample;
import com.biomatters.plugins.biocode.labbench.plates.GelImage;
import com.biomatters.plugins.biocode.labbench.plates.Plate;
import com.biomatters.plugins.biocode.labbench.reaction.ExtractionReaction;
import com.biomatters.plugins.biocode.labbench.reaction.Reaction;
import com.biomatters.plugins.biocode.server.security.Project;
import com.biomatters.plugins.biocode.server.security.Role;
import jebl.util.ProgressListener;
import sun.reflect.generics.reflectiveObjects.NotImplementedException;

import javax.ws.rs.*;
import java.sql.SQLException;
import java.util.*;

/**
 * @author Matthew Cheung
 * @version $Id$
 *          <p/>
 *          Created on 23/03/14 5:20 PM
 */
@Path("plates")
public class Plates {

    @PUT
    @Consumes("application/xml")
    public void add(XMLSerializableList<Plate> plates) {
        checkCanEditPlate(plates.getList());

        try {
            LIMSInitializationListener.getLimsConnection().savePlates(plates.getList(), ProgressListener.EMPTY);
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
    public String delete(XMLSerializableList<Plate> plates) {
        checkCanEditPlate(plates.getList());
        try {
            Set<Integer> ids = LIMSInitializationListener.getLimsConnection().deletePlates(plates.getList(), ProgressListener.EMPTY);
            return StringUtilities.join("\n", ids);
        } catch (DatabaseServiceException e) {
            throw new WebApplicationException(e.getMessage(), e);
        }
    }

    @PUT
    @Consumes("text/plain")
    @Path("{id}/name")
    public void renamePlate(@PathParam("id")int id, String newName) {
        // todo access check
        try {
            LIMSInitializationListener.getLimsConnection().renamePlate(id, newName);
        } catch (DatabaseServiceException e) {
            throw new WebApplicationException(e.getMessage(), e);
        }
    }

    @GET
    @Produces("application/xml")
    @Path("empty")
    public XMLSerializableList<Plate> getEmptyPlates(@QueryParam("platesToCheck")String platesToCheck) { // todo: Confirm if required.
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
                    LIMSInitializationListener.getLimsConnection().getEmptyPlates(ids));
        } catch (DatabaseServiceException e) {
            throw new InternalServerErrorException(e.getMessage(), e);
        }
    }

    @PUT
    @Consumes("application/xml")
    @Path("reactions")
    public void saveReactions(XMLSerializableList<Reaction> reactions, @QueryParam("type") String type) throws SQLException {
        List<Reaction> reactionList = reactions.getList();
        checkCanEditReactions(reactionList);
        Reaction.Type reactionType = Reaction.Type.valueOf(type);
        try {
            LIMSInitializationListener.getLimsConnection().saveReactions(reactionList.toArray(
                    new Reaction[reactionList.size()]
            ), reactionType, ProgressListener.EMPTY);
        } catch (DatabaseServiceException e) {
            throw new WebApplicationException(e.getMessage(), e);
        }
    }

    @GET
    @Path("extractions")
    public XMLSerializableList<ExtractionReaction> getExtractionsForIds(@QueryParam("ids")String ids) { // todo: Migrate logic to QueryService?
        if(ids == null) {
            throw new BadRequestException("Must specify ids");
        }
        try {
            return new XMLSerializableList<ExtractionReaction>(ExtractionReaction.class,
                    LIMSInitializationListener.getLimsConnection().getExtractionsForIds(
                    Arrays.asList(ids.split(","))
            ));
        } catch (DatabaseServiceException e) {
            throw new WebApplicationException(e.getMessage(), e);
        }
    }

    @GET
    @Path("tissues")
    public StringMap getTissueIdsForExtractionIds(@QueryParam("type")String table, @QueryParam("extractionIds")String ids) { // todo: Migrate logic to QueryService.
        if(ids == null) {
            throw new BadRequestException("Must specify extractionIds");
        }
        if(table == null) {
            throw new BadRequestException("Must specify type");
        }

        try {
            return new StringMap(LIMSInitializationListener.getLimsConnection().getTissueIdsForExtractionIds(
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
            Map<Integer, List<GelImage>> map = LIMSInitializationListener.getLimsConnection().getGelImages(
                    Collections.singletonList(plateId));
            return map.get(plateId);
        } catch (DatabaseServiceException e) {
            throw new InternalServerErrorException(e.getMessage(), e);
        }
    }

    /**
     * Throws a {@link javax.ws.rs.ForbiddenException} if the current logged in user cannot edit the plates specified
     * @param plates a list of {@link Plate}s to check
     */
    private void checkCanEditPlate(List<Plate> plates) {
        // todo
        // 1. Get projects for plate - ... query fims?
        Set<FimsSample> samples = new HashSet<FimsSample>();
        for (Plate plate : plates) {
            for (Reaction reaction : plate.getReactions()) {
                samples.add(reaction.getFimsSample());  // Can this be null?
            }
        }

        // Get projects for extraction IDs?  So we need to get tissue ids for extraction ids?
        List<Project> projectsToCheck = new ArrayList<Project>();
        // 2. Check user has at least WRITER to all those projects - easy
    }

    /**
     * Throws a {@link javax.ws.rs.ForbiddenException} if the current logged in user cannot edit the plates specified
     * @param reactionList a list of {@link Reaction}s to check
     */
    private void checkCanEditReactions(List<Reaction> reactionList) throws SQLException {
//        for (Reaction reaction : reactionList) {
//            Project project = Project.getForExtractionId(reaction.getExtractionId());
//            if(project.getRoleForUser().isAtLeast(Role.WRITER)) {
//                throw new ForbiddenException();
//            }
//        }
    }
}