package com.biomatters.plugins.biocode.server;

import com.biomatters.geneious.publicapi.databaseservice.DatabaseServiceException;
import com.biomatters.plugins.biocode.labbench.AssembledSequence;
import com.biomatters.plugins.biocode.labbench.reaction.FailureReason;
import com.biomatters.plugins.biocode.server.utilities.RestUtilities;
import jebl.util.ProgressListener;

import javax.ws.rs.*;
import javax.ws.rs.core.NoContentException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author Matthew Cheung
 * @version $Id$
 *          <p/>
 *          Created on 23/03/14 5:20 PM
 */
@Path("sequences")
public class Sequences {

    @Produces("application/xml")
    @Path("{id}")
    @GET
    public AssembledSequence getForIds(@PathParam("id")String idsString,
        @DefaultValue("false")@QueryParam("includeFailed")Boolean includeFailed) throws NoContentException {

        try {
            List<AssembledSequence> data = LIMSInitializationListener.getLimsConnection().getAssemblyDocuments(getIntegerListFromString(idsString), null, includeFailed);
            return RestUtilities.getOnlyItemFromList(data, "No sequence for id: " + idsString);
        } catch (DatabaseServiceException e) {
            throw new InternalServerErrorException(e.getMessage(), e);
        }
    }

    private List<Integer> getIntegerListFromString(String idsString) {
        if(idsString == null || idsString.trim().isEmpty()) {
            throw new BadRequestException("Must specify ids");
        }

        List<Integer> ids = new ArrayList<Integer>();
        for (String idString : idsString.split(",")) {
            try {
                ids.add(Integer.parseInt(idString));
            } catch (NumberFormatException e) {
                throw new BadRequestException("Bad ID in ids: " + idString + " is not a number");
            }
        }
        return ids;
    }

    @PUT
    @Consumes("text/plain")
    @Path("{id}/submitted")
    public void setSubmitted(@PathParam("id")int id, boolean submitted) {
        try {
            LIMSInitializationListener.getLimsConnection().setSequenceStatus(submitted, Collections.singletonList(id));
        } catch (DatabaseServiceException e) {
            e.printStackTrace();
            throw new InternalServerErrorException(e.getMessage(), e);
        }
    }

    @DELETE
    @Consumes("text/plain")
    @Path("{id}")
    public void delete(@PathParam("id")String id) {
        try {
            List<Integer> integerListFromString = getIntegerListFromString(id);
            if (integerListFromString.size() != 1) {
                throw new BadRequestException("Invalid id: " + id);
            }
            LIMSInitializationListener.getLimsConnection().deleteSequences(integerListFromString);
        } catch (DatabaseServiceException e) {
            e.printStackTrace();
            throw new InternalServerErrorException(e.getMessage(), e);
        }
    }

    @POST
    @Consumes("application/xml")
    @Produces("text/plain")
    public int addSequence(AssembledSequence seq,
                           @QueryParam("isPass")boolean isPass,
                           @QueryParam("notes")String notes,
                           @QueryParam("technician")String technician,
                           @QueryParam("failureReason")String failureReason,
                           @QueryParam("failureNotes")String failureNotes,
                           @QueryParam("addChromatograms")boolean addChromatograms,
                           @QueryParam("reactionIds")String reactionIds
                           ) {
        try {
            return LIMSInitializationListener.getLimsConnection().addAssembly(isPass, notes, technician,
                    FailureReason.getReasonFromIdString(failureReason),
                    failureNotes, addChromatograms, seq, getIntegerListFromString(reactionIds), ProgressListener.EMPTY);
        } catch (DatabaseServiceException e) {
            throw new InternalServerErrorException(e.getMessage(), e);
        }
    }
}
