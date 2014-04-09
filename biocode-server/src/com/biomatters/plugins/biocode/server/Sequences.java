package com.biomatters.plugins.biocode.server;

import com.biomatters.geneious.publicapi.databaseservice.DatabaseServiceException;
import com.biomatters.plugins.biocode.labbench.AssembledSequence;
import com.biomatters.plugins.biocode.labbench.BiocodeService;
import com.biomatters.plugins.biocode.labbench.reaction.FailureReason;
import jebl.util.ProgressListener;

import javax.ws.rs.*;
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
    @GET
    public List<AssembledSequence> getForIds(@QueryParam("ids")String idsString,
        @DefaultValue("false")@QueryParam("includeFailed")Boolean includeFailed) {

        List<Integer> ids = getIntegerListFromString(idsString);

        try {
            return LIMSInitializationServlet.getLimsConnection().getAssemblyDocuments(ids, null, includeFailed);
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
                ids.add(Integer.parseInt(idsString));
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
            LIMSInitializationServlet.getLimsConnection().setSequenceStatus(submitted, Collections.singletonList(id));
        } catch (DatabaseServiceException e) {
            e.printStackTrace();
        }
    }

    @POST
    @Consumes("text/plain")
    @Path("deletes")
    public void delete(String idStrings) {
        try {
            LIMSInitializationServlet.getLimsConnection().deleteSequences(getIntegerListFromString(idStrings));
        } catch (DatabaseServiceException e) {
            e.printStackTrace();
        }
    }

    @POST
    @Consumes("application/xml")
    public int addSequence(AssembledSequence seq,
                           @QueryParam("isPass")boolean isPass,
                           @QueryParam("notes")String notes,
                           @QueryParam("technician")String technician,
                           @QueryParam("failureReason")FailureReason failureReason,
                           @QueryParam("failureNotes")String failureNotes,
                           @QueryParam("addChromatograms")boolean addChromatograms,
                           @QueryParam("reactionIds")String reactionIds
                           ) {
        try {
            return LIMSInitializationServlet.getLimsConnection().addAssembly(isPass, notes, technician, failureReason,
                    failureNotes, addChromatograms, seq, getIntegerListFromString(reactionIds), ProgressListener.EMPTY);
        } catch (DatabaseServiceException e) {
            throw new InternalServerErrorException(e.getMessage(), e);
        }
    }
}
