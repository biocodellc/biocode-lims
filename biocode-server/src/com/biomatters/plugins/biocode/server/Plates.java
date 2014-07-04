package com.biomatters.plugins.biocode.server;

import com.biomatters.geneious.publicapi.databaseservice.DatabaseServiceException;
import com.biomatters.geneious.publicapi.utilities.StringUtilities;
import com.biomatters.plugins.biocode.labbench.BadDataException;
import com.biomatters.plugins.biocode.labbench.FimsSample;
import com.biomatters.plugins.biocode.labbench.fims.FimsProject;
import com.biomatters.plugins.biocode.labbench.plates.GelImage;
import com.biomatters.plugins.biocode.labbench.plates.Plate;
import com.biomatters.plugins.biocode.labbench.reaction.ExtractionReaction;
import com.biomatters.plugins.biocode.labbench.reaction.Reaction;
import com.biomatters.plugins.biocode.server.security.Projects;
import com.biomatters.plugins.biocode.server.security.Role;
import com.biomatters.plugins.biocode.server.security.Users;
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

    @PUT
    @Consumes("application/xml")
    public void add(XMLSerializableList<Plate> plates) {
        try {
            checkUserHasRoleForPlate(plates.getList(), Role.WRITER);
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
        try {
            checkUserHasRoleForPlate(plates.getList(), Role.WRITER);
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
            List<Plate> plates = LIMSInitializationListener.getLimsConnection().getEmptyPlates(ids);
            checkUserHasRoleForPlate(plates, Role.READER);
            return new XMLSerializableList<Plate>(Plate.class, plates);
        } catch (DatabaseServiceException e) {
            throw new InternalServerErrorException(e.getMessage(), e);
        }
    }

    @PUT
    @Consumes("application/xml")
    @Path("reactions")
    public void saveReactions(XMLSerializableList<Reaction> reactions, @QueryParam("type") String type) {
        List<Reaction> reactionList = reactions.getList();
        Reaction.Type reactionType = Reaction.Type.valueOf(type);
        try {
            checkUserHasRoleForReactions(reactionList, Role.WRITER);
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
            List<ExtractionReaction> extractions = LIMSInitializationListener.getLimsConnection().getExtractionsForIds(
                    Arrays.asList(ids.split(","))
            );
            checkUserHasRoleForReactions(extractions, Role.READER);
            return new XMLSerializableList<ExtractionReaction>(ExtractionReaction.class, extractions);
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
        // todo get Plate and check access
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
     * @param plates a list of {@link com.biomatters.plugins.biocode.labbench.plates.Plate}s to check
     * @param role
     */
    private void checkUserHasRoleForPlate(List<Plate> plates, Role role) throws DatabaseServiceException {
        Set<FimsSample> samples = new HashSet<FimsSample>();
        for (Plate plate : plates) {
            for (Reaction reaction : plate.getReactions()) {
                samples.add(reaction.getFimsSample());  // Can this be null?
            }
        }

        checkUserHasRoleForSamples(samples, Role.WRITER);
    }

    private void checkUserHasRoleForSamples(Collection<FimsSample> samples, Role role) throws DatabaseServiceException {
        List<String> projectIds = LIMSInitializationListener.getFimsConnection().getProjectsForSamples(samples);
        List<FimsProject> projectsUserCanWriteTo = Projects.getFimsProjectsUserHasAtLeastRole(
                LIMSInitializationListener.getDataSource(),
                LIMSInitializationListener.getFimsConnection(),
                Users.getLoggedInUser(), role);

        for (String projectId : projectIds) {
            boolean found = false;
            for (FimsProject fimsProject : projectsUserCanWriteTo) {
                if(fimsProject.getId().equals(projectId)) {
                    found = true;
                }
            }
            if(!found) {
                throw new ForbiddenException("User cannot access project: " + projectId);
            }
        }
    }

    /**
     * Throws a {@link javax.ws.rs.ForbiddenException} if the current logged in user cannot edit the plates specified
     * @param reactionList a list of {@link Reaction}s to check
     */
    private void checkUserHasRoleForReactions(Collection<? extends Reaction> reactionList, Role role) throws DatabaseServiceException {
        List<FimsSample> samples = new ArrayList<FimsSample>();
        for (Reaction reaction : reactionList) {
            samples.add(reaction.getFimsSample());
        }
        checkUserHasRoleForSamples(samples, role);
    }
}