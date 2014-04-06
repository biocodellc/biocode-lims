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

    @GET
    @Produces("application/xml")
    public StringMap getWorkflowsForExtractionIds(@QueryParam("extractionIds")String extractionIds,
                                                  @QueryParam("loci")String loci, @QueryParam("type")String type) {
        if(extractionIds == null || extractionIds.trim().isEmpty()) {
            throw new BadRequestException("Must specify extractionIds");
        }

        if(loci == null || loci.trim().isEmpty()) {
            throw new BadRequestException("Must specify loci");
        }

        if(type == null || type.trim().isEmpty()) {
            throw new BadRequestException("Must specify type");
        }

        try {
            return new StringMap(
                    BiocodeService.getInstance().getActiveLIMSConnection().getWorkflowIds(
                            Arrays.asList(extractionIds.split(",")),
                            Arrays.asList(loci.split(",")),
                            Reaction.Type.valueOf(type)
                    ));
        } catch (DatabaseServiceException e) {
            throw new InternalServerErrorException(e.getMessage(), e);
        } catch(IllegalArgumentException e) {
            throw new BadRequestException(type + " is not valid type.");
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
}
