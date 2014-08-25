package com.biomatters.plugins.biocode.server;

import com.biomatters.geneious.publicapi.databaseservice.DatabaseServiceException;
import com.biomatters.geneious.publicapi.databaseservice.Query;
import com.biomatters.geneious.publicapi.documents.Condition;
import com.biomatters.plugins.biocode.labbench.BiocodeService;
import com.biomatters.plugins.biocode.labbench.Workflow;
import com.biomatters.plugins.biocode.labbench.WorkflowDocument;
import com.biomatters.plugins.biocode.labbench.lims.LIMSConnection;
import com.biomatters.plugins.biocode.labbench.lims.LimsSearchResult;
import com.biomatters.plugins.biocode.server.security.AccessUtilities;
import com.biomatters.plugins.biocode.server.security.Role;
import com.biomatters.plugins.biocode.server.utilities.RestUtilities;

import javax.ws.rs.*;
import javax.ws.rs.core.NoContentException;
import java.util.Collections;
import java.util.List;

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
            Workflow result = RestUtilities.getOnlyItemFromList(LIMSInitializationListener.getLimsConnection().getWorkflows(Collections.singletonList(id)), "No workflow for id: " + id);
            AccessUtilities.checkUserHasRoleForExtractionIds(Collections.singletonList(result.getExtractionId()), Role.READER);
            return result;
        } catch (DatabaseServiceException e) {
            throw new InternalServerErrorException(e.getMessage(), e);
        }
    }

    @PUT
    @Consumes("text/plain")
    @Path("{id}/name")
    public void renameWorkflow(@PathParam("id") int id, String newName) {
        try {
            checkAccessForWorkflowId(id, Role.WRITER);
            LIMSInitializationListener.getLimsConnection().renameWorkflow(id, newName);
        } catch (DatabaseServiceException e) {
            throw new WebApplicationException(e.getMessage(), e);
        }
    }

    @DELETE
    @Path("{workflowId}/sequences/{extractionId}")
    public void deleteSequencesForWorkflow(@PathParam("workflowId")int workflowId, @PathParam("extractionId")String extractionId) {
        try {
            checkAccessForWorkflowId(workflowId, Role.WRITER);
            LIMSInitializationListener.getLimsConnection().deleteSequencesForWorkflowId(workflowId, extractionId);
        } catch (DatabaseServiceException e) {
            throw new InternalServerErrorException(e.getMessage(), e);
        }
    }

    private static void checkAccessForWorkflowId(int id, Role role) throws DatabaseServiceException {
        LimsSearchResult result = LIMSInitializationListener.getLimsConnection().getMatchingDocumentsFromLims(
                Query.Factory.createFieldQuery(LIMSConnection.WORKFLOW_ID_FIELD, Condition.EQUAL, new Object[]{id},
                        BiocodeService.getSearchDownloadOptions(false, true, false, false)), null, null
        );
        List<WorkflowDocument> workflowList = result.getWorkflows();
        if(workflowList.size() < 1) {
            throw new NotFoundException("Could not find workflow for id = " + id);
        }
        AccessUtilities.checkUserHasRoleForExtractionIds(Collections.singletonList(
                workflowList.get(0).getWorkflow().getExtractionId()
        ), role);
    }
}
