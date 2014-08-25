package com.biomatters.plugins.biocode.server;

import com.biomatters.geneious.publicapi.databaseservice.DatabaseServiceException;
import com.biomatters.plugins.biocode.labbench.AssembledSequence;
import com.biomatters.plugins.biocode.labbench.reaction.FailureReason;
import com.biomatters.plugins.biocode.server.security.AccessUtilities;
import com.biomatters.plugins.biocode.server.security.Role;
import com.biomatters.plugins.biocode.server.utilities.RestUtilities;
import jebl.util.ProgressListener;

import javax.ws.rs.*;
import javax.ws.rs.core.NoContentException;
import java.util.*;

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
    public List<AssembledSequence> getForIds(@QueryParam("ids")String idListString,
        @DefaultValue("false")@QueryParam("includeFailed")Boolean includeFailed) throws NoContentException {

        try {
            List<Integer> idList = getIntegerListFromString(idListString);
            Role roleToCheckFor = Role.READER;
            return getAssembledSequencesWithRoleCheck(idList, includeFailed, roleToCheckFor);
        } catch (DatabaseServiceException e) {
            throw new InternalServerErrorException(e.getMessage(), e);
        }
    }

    List<AssembledSequence> getAssembledSequencesWithRoleCheck(List<Integer> idList, Boolean includeFailed, Role roleToCheckFor) throws DatabaseServiceException {
        List<AssembledSequence> data = LIMSInitializationListener.getLimsConnection().getAssemblyDocuments(idList, null, includeFailed);
        Set<String> extractionIds = new HashSet<String>();
        for (AssembledSequence sequence : data) {
            extractionIds.add(sequence.extractionId);
        }
        AccessUtilities.checkUserHasRoleForExtractionIds(extractionIds, roleToCheckFor);
        return data;
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
            getAssembledSequencesWithRoleCheck(Collections.singletonList(id), true, Role.WRITER);
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
            getAssembledSequencesWithRoleCheck(integerListFromString, true, Role.WRITER);
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
            AccessUtilities.checkUserHasRoleForExtractionIds(Collections.singletonList(seq.extractionId), Role.WRITER);
            return LIMSInitializationListener.getLimsConnection().addAssembly(isPass, notes, technician,
                    FailureReason.getReasonFromIdString(failureReason),
                    failureNotes, addChromatograms, seq, getIntegerListFromString(reactionIds), ProgressListener.EMPTY);
        } catch (DatabaseServiceException e) {
            throw new InternalServerErrorException(e.getMessage(), e);
        }
    }
}
