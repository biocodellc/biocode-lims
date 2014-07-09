package com.biomatters.plugins.biocode.server;

import com.biomatters.geneious.publicapi.databaseservice.DatabaseServiceException;
import com.biomatters.geneious.publicapi.databaseservice.Query;
import com.biomatters.geneious.publicapi.documents.Condition;
import com.biomatters.geneious.publicapi.utilities.StringUtilities;
import com.biomatters.plugins.biocode.labbench.BadDataException;
import com.biomatters.plugins.biocode.labbench.BiocodeService;
import com.biomatters.plugins.biocode.labbench.PlateDocument;
import com.biomatters.plugins.biocode.labbench.lims.LIMSConnection;
import com.biomatters.plugins.biocode.labbench.lims.LimsSearchResult;
import com.biomatters.plugins.biocode.labbench.plates.GelImage;
import com.biomatters.plugins.biocode.labbench.plates.Plate;
import com.biomatters.plugins.biocode.labbench.reaction.ExtractionReaction;
import com.biomatters.plugins.biocode.labbench.reaction.Reaction;
import com.biomatters.plugins.biocode.server.security.AccessUtilities;
import com.biomatters.plugins.biocode.server.security.Role;
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
            AccessUtilities.checkUserHasRoleForPlate(plates.getList(), Role.WRITER);
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
            AccessUtilities.checkUserHasRoleForPlate(plates.getList(), Role.WRITER);
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
        try {
            checkAccessForPlateId(id);
            LIMSInitializationListener.getLimsConnection().renamePlate(id, newName);
        } catch (DatabaseServiceException e) {
            throw new WebApplicationException(e.getMessage(), e);
        }
    }

    private static void checkAccessForPlateId(int id) throws DatabaseServiceException {
        LimsSearchResult result = LIMSInitializationListener.getLimsConnection().getMatchingDocumentsFromLims(
                Query.Factory.createFieldQuery(LIMSConnection.PLATE_ID_FIELD, Condition.EQUAL, new Object[]{id},
                        BiocodeService.getSearchDownloadOptions(false, false, true, false)), null, null
        );
        List<PlateDocument> plateList = result.getPlates();
        if(plateList.size() < 1) {
            throw new NotFoundException("Could not find plate for id = " + id);
        }
        AccessUtilities.checkUserHasRoleForPlate(Collections.singletonList(plateList.get(0).getPlate()), Role.WRITER);
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
            AccessUtilities.checkUserHasRoleForPlate(plates, Role.READER);
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
            AccessUtilities.checkUserHasRoleForReactions(reactionList, Role.WRITER);
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
            AccessUtilities.checkUserHasRoleForReactions(extractions, Role.READER);
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
        try {
            checkAccessForPlateId(plateId);
            Map<Integer, List<GelImage>> map = LIMSInitializationListener.getLimsConnection().getGelImages(
                    Collections.singletonList(plateId));
            return map.get(plateId);
        } catch (DatabaseServiceException e) {
            throw new InternalServerErrorException(e.getMessage(), e);
        }
    }
}