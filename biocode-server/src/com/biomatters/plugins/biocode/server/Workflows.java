package com.biomatters.plugins.biocode.server;

import com.biomatters.geneious.publicapi.databaseservice.DatabaseServiceException;
import com.biomatters.plugins.biocode.labbench.Workflow;

import javax.ws.rs.*;
import javax.ws.rs.core.NoContentException;
import java.util.Arrays;
import java.util.Collections;

/**
 * @author Matthew Cheung
 * @version $Id$
 *          <p/>
 *          Created on 23/03/14 5:20 PM
 */
@Path("workflows")
public class Workflows {
    @GET
    @Path("{id}")
    @Produces("application/xml")
    public Workflow get(@PathParam("id")String id) throws NoContentException {
        if(id == null || id.trim().isEmpty()) {
            throw new BadRequestException("Must specify ids");
        }
        try {
            return RestUtilities.getOnlyItemFromList(LIMSInitializationListener.getLimsConnection().getWorkflows(Collections.singletonList(id)), "No workflow for id: " + id);
        } catch (DatabaseServiceException e) {
            throw new InternalServerErrorException(e.getMessage(), e);
        }
    }

    @PUT
    @Consumes("text/plain")
    @Path("{id}/name")
    public void renameWorkflow(@PathParam("id") int id, String newName) {
        try {
            LIMSInitializationListener.getLimsConnection().renameWorkflow(id, newName);
        } catch (DatabaseServiceException e) {
            throw new WebApplicationException(e.getMessage(), e);
        }
    }

    @DELETE
    @Path("{workflowId}/sequences/{extractionId}")
    public void deleteSequencesForWorkflow(@PathParam("workflowId")int workflowId, @PathParam("extractionId")String extractionId) {
        try {
            LIMSInitializationListener.getLimsConnection().deleteSequencesForWorkflowId(workflowId, extractionId);
        } catch (DatabaseServiceException e) {
            throw new InternalServerErrorException(e.getMessage(), e);
        }
    }
}
