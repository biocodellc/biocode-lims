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
import jebl.util.ProgressListener;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.NoContentException;
import javax.ws.rs.core.Response;
import java.util.*;

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
    @Consumes("text/plain")
    public XMLSerializableList<WorkflowDocument> getWorkflows(@QueryParam("ids")String idListAsString) {
        try {
            List<WorkflowDocument> workflows = LIMSInitializationListener.getLimsConnection().getWorkflowsById(
                    Sequences.getIntegerListFromString(idListAsString), ProgressListener.EMPTY);
            Set<String> extractionIds = new HashSet<String>();
            for (WorkflowDocument workflow : workflows) {
                extractionIds.add(workflow.getWorkflow().getExtractionId());
            }
            AccessUtilities.checkUserHasRoleForExtractionIds(extractionIds, Role.READER);
            return new XMLSerializableList<WorkflowDocument>(WorkflowDocument.class, workflows);
        } catch (DatabaseServiceException e) {
            throw new WebApplicationException(Response.status(Response.Status.BAD_REQUEST)
                                                       .entity(e.getMessage())
                                                       .type(MediaType.TEXT_PLAIN_TYPE)
                                                       .build());
        }
    }

    @GET
    @Path("{id}")
    @Produces("application/xml")
    public Workflow get(@PathParam("id")String workflowName) throws NoContentException {
        if(workflowName == null || workflowName.trim().isEmpty()) {
            throw new BadRequestException("Must specify ids");
        }
        try {
            Workflow result = RestUtilities.getOnlyItemFromList(LIMSInitializationListener.getLimsConnection().getWorkflowsByName(Collections.singletonList(workflowName)), "No workflow for id: " + workflowName);
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
                        BiocodeService.getSearchDownloadOptions(false, true, false, false)), null, ProgressListener.EMPTY
        );
        Map<Integer, String> extractionIdsForWorkflows = QueryService.getExtractionIdsForWorkflows(result.getWorkflowIds());
        String extractionId = extractionIdsForWorkflows.get(id);
        if(extractionId == null) {
            throw new NotFoundException("Could not find workflow for id = " + id);
        }
        AccessUtilities.checkUserHasRoleForExtractionIds(Collections.singletonList(extractionId), role);
    }
}
