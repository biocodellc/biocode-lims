package com.biomatters.plugins.biocode.server;

import com.biomatters.geneious.publicapi.databaseservice.DatabaseServiceException;
import com.biomatters.plugins.biocode.labbench.ConnectionException;
import com.biomatters.plugins.biocode.labbench.FimsSample;
import com.biomatters.plugins.biocode.labbench.PlateDocument;
import com.biomatters.plugins.biocode.labbench.WorkflowDocument;
import com.biomatters.plugins.biocode.labbench.fims.FimsProject;
import com.biomatters.plugins.biocode.labbench.lims.LimsSearchResult;
import com.biomatters.plugins.biocode.labbench.reaction.Reaction;
import com.biomatters.plugins.biocode.server.security.Projects;
import com.biomatters.plugins.biocode.server.security.Role;
import com.biomatters.plugins.biocode.server.security.User;
import com.biomatters.plugins.biocode.server.security.Users;
import com.biomatters.plugins.biocode.server.utilities.RestUtilities;

import javax.ws.rs.*;
import javax.ws.rs.core.Response;
import java.util.*;

/**
 * Main endpoint for querying the FIMS/LIMS as a whole.  Returns tissues, workflows, plates and sequences
 *
 * @author Matthew Cheung
 * @version $Id$
 *          <p/>
 *          Created on 23/03/14 5:21 PM
 */
@Path("search")
public class QueryService {

    // We'll go with XML rather than JSON because that's what Geneious deals with.  Could potentially go JSON, but
    // it would require conversion on server-side and client-side.  Better to work with XML by default and offer
    // JSON as a possible alternative in the future.

    @GET
    @Produces("application/xml")
    public Response search(@QueryParam("q") String query,
                           @DefaultValue("true")  @QueryParam("showTissues") boolean showTissues,
                           @DefaultValue("true")  @QueryParam("showWorkflows") boolean showWorkflows,
                           @DefaultValue("true")  @QueryParam("showPlates") boolean showPlates,
                           @DefaultValue("false") @QueryParam("showSequenceIds") boolean showSequenceIds,
                                                  @QueryParam("tissueIdsToMatch") String tissueIdsToMatch) throws DatabaseServiceException {

        Set<String> tissueIdsToMatchSet = tissueIdsToMatch == null ? null : new HashSet<String>(Arrays.asList(tissueIdsToMatch.split(",")));
        LimsSearchResult result = RestUtilities.getSearchResults(query, showTissues, showWorkflows, showPlates, showSequenceIds, tissueIdsToMatchSet);
        LimsSearchResult filteredResult = getPermissionsFilteredResult(result);
        return Response.ok(filteredResult).build();
    }

    LimsSearchResult getPermissionsFilteredResult(LimsSearchResult result) throws DatabaseServiceException {
        Set<String> sampleIds = new HashSet<String>();
        Set<String> extractionIds = new HashSet<String>();

        for (FimsSample fimsSample : result.getTissueSamples()) {
            sampleIds.add(fimsSample.getId());
        }
        for (WorkflowDocument workflowDocument : result.getWorkflows()) {
            extractionIds.add(workflowDocument.getWorkflow().getExtractionId());
        }
        for (PlateDocument plateDocument : result.getPlates()) {
            for (Reaction reaction : plateDocument.getPlate().getReactions()) {
                if(!reaction.isEmpty()) {
                    extractionIds.add(reaction.getExtractionId());
                }
            }
        }
        List<FimsSample> allSamples;
        Map<String, String> extractionIdToSampleId;
        try {
            extractionIdToSampleId = LIMSInitializationListener.getLimsConnection().getTissueIdsForExtractionIds(
                    "extraction", new ArrayList<String>(extractionIds));
            sampleIds.addAll(extractionIdToSampleId.values());
            allSamples = LIMSInitializationListener.getFimsConnection().retrieveSamplesForTissueIds(sampleIds);
        } catch (ConnectionException e) {
            throw new DatabaseServiceException(e, e.getMainMessage(), false);
        }

        LimsSearchResult filteredResult = new LimsSearchResult();
        Set<String> readableSampleIds = getReadableSampleIds(Users.getLoggedInUser(), allSamples);
        for (FimsSample sample : result.getTissueSamples()) {
            if(readableSampleIds.contains(sample.getId())) {
                filteredResult.addTissueSample(sample);
            }
        }
        for (WorkflowDocument workflow : result.getWorkflows()) {
            String sampleId = extractionIdToSampleId.get(workflow.getWorkflow().getExtractionId());
            if(readableSampleIds.contains(sampleId)) {
                filteredResult.addWorkflow(workflow);
            }
        }
        for (PlateDocument plateDocument : result.getPlates()) {
            boolean canReadCompletePlate = true;
            for (Reaction reaction : plateDocument.getPlate().getReactions()) {
                String sampleId = extractionIdToSampleId.get(reaction.getExtractionId());
                if(!reaction.isEmpty() && !readableSampleIds.contains(sampleId)) {
                    canReadCompletePlate = false;
                }
            }
            if(canReadCompletePlate) {
                filteredResult.addPlate(plateDocument);
            }
        }
        return filteredResult;
    }

    /**
     * @param user The user to check for
     * @param allSamples A list of all {@link com.biomatters.plugins.biocode.labbench.FimsSample} to examine
     * @return A list of IDs for samples of the supplied list that are readable
     * @throws DatabaseServiceException if there is a problem communicating with the FIMS or LIMS
     */
    Set<String> getReadableSampleIds(User user, List<FimsSample> allSamples) throws DatabaseServiceException {
        List<FimsProject> readableProjects = Projects.getFimsProjectsUserHasAtLeastRole(
                LIMSInitializationListener.getDataSource(), LIMSInitializationListener.getFimsConnection(),
                user, Role.READER);
        Map<String, Collection<FimsSample>> mappedSamples = LIMSInitializationListener.getFimsConnection().getProjectsForSamples(allSamples);
        Set<String> validSampleIds = new HashSet<String>();
        for (Map.Entry<String, Collection<FimsSample>> entry : mappedSamples.entrySet()) {
            for (FimsProject readableProject : readableProjects) {
                if(readableProject.getId().equals(entry.getKey())) {
                    for (FimsSample fimsSample : entry.getValue()) {
                        validSampleIds.add(fimsSample.getId());
                    }
                }
            }
        }
        return validSampleIds;
    }
}