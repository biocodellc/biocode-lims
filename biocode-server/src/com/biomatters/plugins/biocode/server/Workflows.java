package com.biomatters.plugins.biocode.server;

import com.biomatters.geneious.publicapi.databaseservice.DatabaseServiceException;
import com.biomatters.plugins.biocode.labbench.BiocodeService;
import com.biomatters.plugins.biocode.labbench.Workflow;
import com.biomatters.plugins.biocode.labbench.reaction.Reaction;

import javax.ws.rs.*;
import java.util.Arrays;

/**
 * @author Matthew Cheung
 * @version $Id$
 *          <p/>
 *          Created on 23/03/14 5:20 PM
 */
@Path("workflows")
public class Workflows {
    @GET
    @Produces("application/xml")
    public XMLSerializableList<Workflow> get(@QueryParam("ids")String ids) {
        if(ids == null || ids.trim().isEmpty()) {
            throw new BadRequestException("Must specify ids");
        }

        try {
            return new XMLSerializableList<Workflow>(Workflow.class,
                    BiocodeService.getInstance().getActiveLIMSConnection().getWorkflows(Arrays.asList(ids.split(","))));
        } catch (DatabaseServiceException e) {
            throw new InternalServerErrorException(e.getMessage(), e);
        }
    }

    @PUT
    @Consumes("text/plain")
    @Path("{id}/name")
    public void renameWorkflow(@PathParam("id") int id, String newName) {
        try {
            BiocodeService.getInstance().getActiveLIMSConnection().renameWorkflow(id, newName);
        } catch (DatabaseServiceException e) {
            throw new WebApplicationException(e.getMessage(), e);
        }
    }

    @DELETE
    @Path("{workflowId}/sequences/{extractionId}")
    public void deleteSequencesForWorkflow(@PathParam("workflowId")int workflowId, @PathParam("extractionId")String extractionId) {
        try {
            BiocodeService.getInstance().getActiveLIMSConnection().deleteSequencesForWorkflowId(workflowId, extractionId);
        } catch (DatabaseServiceException e) {
            throw new InternalServerErrorException(e.getMessage(), e);
        }
    }
}
