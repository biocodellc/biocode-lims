package com.biomatters.plugins.biocode.server;

import com.biomatters.geneious.publicapi.databaseservice.DatabaseServiceException;
import com.biomatters.plugins.biocode.labbench.*;
import com.biomatters.plugins.biocode.labbench.lims.LimsSearchResult;
import com.biomatters.plugins.biocode.labbench.plates.Plate;
import com.biomatters.plugins.biocode.labbench.reaction.Reaction;
import com.biomatters.plugins.biocode.server.security.*;
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
                           @DefaultValue("false") @QueryParam("showSequences") boolean showSequenceIds,
                                                  @QueryParam("tissueIdsToMatch") String tissueIdsToMatch) throws DatabaseServiceException {

        return performSearch(query, showTissues, showWorkflows, showPlates, showSequenceIds, tissueIdsToMatch);
    }

    Response performSearch(String query, boolean showTissues, boolean showWorkflows, boolean showPlates, boolean showSequenceIds, String tissueIdsToMatch) throws DatabaseServiceException {
        Set<String> tissueIdsToMatchSet = tissueIdsToMatch == null ? null : new HashSet<String>(RestUtilities.getListFromString(tissueIdsToMatch));
        LimsSearchResult result = RestUtilities.getSearchResults(query, showTissues, showWorkflows, showPlates, showSequenceIds, tissueIdsToMatchSet);
        LimsSearchResult filteredResult = getPermissionsFilteredResult(result);
        return Response.ok(filteredResult).build();
    }

    @POST
    @Produces("application/xml")
    @Consumes("text/plain")
    public Response searchWithPost(@QueryParam("q") String query,
                           @DefaultValue("true")  @QueryParam("matchTissues") boolean matchTissues,
                           @DefaultValue("true")  @QueryParam("showTissues") boolean showTissues,
                           @DefaultValue("true")  @QueryParam("showWorkflows") boolean showWorkflows,
                           @DefaultValue("true")  @QueryParam("showPlates") boolean showPlates,
                           @DefaultValue("false") @QueryParam("showSequences") boolean showSequenceIds,
                                                  String tissueIdsToMatch) throws DatabaseServiceException {

        return performSearch(query, showTissues, showWorkflows, showPlates, showSequenceIds, matchTissues ? tissueIdsToMatch : null);
    }

    LimsSearchResult getPermissionsFilteredResult(LimsSearchResult result) throws DatabaseServiceException {
        Set<String> sampleIds = new HashSet<String>();
        Set<String> extractionIds = new HashSet<String>();

        sampleIds.addAll(result.getTissueIds());
        for (WorkflowDocument workflowDocument : result.getWorkflows()) {
            extractionIds.add(workflowDocument.getWorkflow().getExtractionId());
        }
        List<Plate> plates = new ArrayList<Plate>();
        for (PlateDocument plate : result.getPlates()) {
            plates.add(plate.getPlate());
        }
        extractionIds.addAll(AccessUtilities.getExtractionIdsFromPlates(plates));
        List<AssembledSequence> sequences = LIMSInitializationListener.getLimsConnection().getAssemblyDocuments(result.getSequenceIds(), null, true);
        for (AssembledSequence sequence : sequences) {
            extractionIds.add(sequence.extractionId);
        }

        List<FimsSample> allSamples;
        List<String> idsOfSamplesNotInDatabase;
        Map<String, String> extractionIdToSampleId;
        try {
            extractionIdToSampleId = LIMSInitializationListener.getLimsConnection().getTissueIdsForExtractionIds(
                    "extraction", new ArrayList<String>(extractionIds));
            sampleIds.addAll(extractionIdToSampleId.values());
            allSamples = LIMSInitializationListener.getFimsConnection().retrieveSamplesForTissueIds(sampleIds);

            Set<String> idsOfSamplesInDatabase = new HashSet<String>();
            for (FimsSample sample : allSamples) {
                idsOfSamplesInDatabase.add(sample.getId());
            }
            idsOfSamplesNotInDatabase = new ArrayList<String>(extractionIdToSampleId.values());
            idsOfSamplesNotInDatabase.removeAll(idsOfSamplesInDatabase);
        } catch (ConnectionException e) {
            throw new DatabaseServiceException(e, e.getMainMessage(), false);
        }

        Set<String> readableSampleIds = new HashSet<String>(idsOfSamplesNotInDatabase);
        readableSampleIds.addAll(AccessUtilities.getSampleIdsUserHasRoleFor(Users.getLoggedInUser(), allSamples, Role.READER));

        LimsSearchResult filteredResult = new LimsSearchResult();
        for (String tissueId : result.getTissueIds()) {
            if(readableSampleIds.contains(tissueId)) {
                filteredResult.addTissueSample(tissueId);
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
                    break;
                }
            }
            if(canReadCompletePlate) {
                filteredResult.addPlate(plateDocument);
            }
        }
        for (AssembledSequence sequence : sequences) {
            String sampleId = extractionIdToSampleId.get(sequence.extractionId);
            if(readableSampleIds.contains(sampleId)) {
                filteredResult.addSequenceID(sequence.id);
            }
        }
        return filteredResult;
    }
}