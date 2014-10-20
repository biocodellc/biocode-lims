package com.biomatters.plugins.biocode.server;

import com.biomatters.geneious.publicapi.databaseservice.DatabaseServiceException;
import com.biomatters.plugins.biocode.labbench.*;
import com.biomatters.plugins.biocode.labbench.lims.LimsSearchResult;
import com.biomatters.plugins.biocode.server.security.*;
import com.biomatters.plugins.biocode.server.utilities.RestUtilities;
import com.biomatters.plugins.biocode.utilities.SqlUtilities;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;

import javax.sql.DataSource;
import javax.ws.rs.*;
import javax.ws.rs.core.Response;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
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
        Map<Integer, String> extractionIdsForWorkflows = getExtractionIdsForWorkflows(result.getWorkflowIds());
        extractionIds.addAll(extractionIdsForWorkflows.values());

        Map<Integer, Collection<String>> extractionIdsForPlates = getExtractionIdsForPlates(result.getPlateIds());
        for (Collection<String> extractionIdsForPlate : extractionIdsForPlates.values()) {
            extractionIds.addAll(extractionIdsForPlate);
        }

        Map<Integer, String> sequenceExtractionMap = getExtractionIdsForSequences(result.getSequenceIds());
        extractionIds.addAll(sequenceExtractionMap.values());

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
        for (Integer workflow : result.getWorkflowIds()) {
            String sampleId = extractionIdToSampleId.get(extractionIdsForWorkflows.get(workflow));
            if(readableSampleIds.contains(sampleId)) {
                filteredResult.addWorkflow(workflow);
            }
        }
        for (Integer plateId : result.getPlateIds()) {
            boolean canReadCompletePlate = true;
            for (String extractionIdToCheck : extractionIdsForPlates.get(plateId)) {
                String sampleId = extractionIdToSampleId.get(extractionIdToCheck);
                if(!readableSampleIds.contains(sampleId)) {
                    canReadCompletePlate = false;
                    break;
                }
            }
            if(canReadCompletePlate) {
                filteredResult.addPlate(plateId);
            }
        }

        for (Map.Entry<Integer, String> entry : sequenceExtractionMap.entrySet()) {
            int sequenceId = entry.getKey();
            String extractionId = entry.getValue();

            String sampleId = extractionIdToSampleId.get(extractionId);
            if(readableSampleIds.contains(sampleId)) {
                filteredResult.addSequenceID(sequenceId);
            }
        }
        return filteredResult;
    }

    private static Map<Integer, Collection<String>> getExtractionIdsForPlates(List<Integer> plateIds) throws DatabaseServiceException {
        if(plateIds.isEmpty()) {
            return Collections.emptyMap();
        }
        Multimap<Integer, String> mapping = HashMultimap.create();
        DataSource dataSource = LIMSInitializationListener.getDataSource();
        Connection connection = null;
        try {
            connection = dataSource.getConnection();

            StringBuilder queryBuilder = new StringBuilder("SELECT plate.id, E.extractionId FROM plate " +
                    "LEFT OUTER JOIN extraction ON extraction.plate = plate.id " +
                    "LEFT OUTER JOIN workflow W ON extraction.id = W.extractionId " +
                    "LEFT OUTER JOIN pcr ON pcr.plate = plate.id  " +
                    "LEFT OUTER JOIN cyclesequencing ON cyclesequencing.plate = plate.id " +
                    "LEFT OUTER JOIN workflow ON workflow.id = " +
                    "CASE WHEN pcr.workflow IS NOT NULL THEN pcr.workflow ELSE " +
                        "CASE WHEN W.id IS NOT NULL THEN W.id ELSE " +
                            "cyclesequencing.workflow END " +
                    "END " +
                    "LEFT OUTER JOIN extraction E ON E.id = " +
                    "CASE WHEN extraction.id IS NULL THEN workflow.extractionId ELSE extraction.id END " +
                    "WHERE plate.id IN ");
            SqlUtilities.appendSetOfQuestionMarks(queryBuilder, plateIds.size());
            queryBuilder.append(" ORDER BY plate.id");
            PreparedStatement select = connection.prepareStatement(queryBuilder.toString());
            SqlUtilities.fillStatement(plateIds, select);
            SqlUtilities.printSql(queryBuilder.toString(), plateIds);
            ResultSet resultSet = select.executeQuery();
            while(resultSet.next()) {
                String extractionId = resultSet.getString("E.extractionId");
                if(extractionId != null) {
                    extractionId = extractionId.trim();
                    if(extractionId.length() > 0) {
                        mapping.put(resultSet.getInt("plate.id"), extractionId);
                    }
                }
            }
            return mapping.asMap();
        } catch (SQLException e) {
            throw new DatabaseServiceException(e, e.getMessage(), false);
        } finally {
            SqlUtilities.closeConnection(connection);
        }
    }

    public static Map<Integer, String> getExtractionIdsForWorkflows(List<Integer> workflowIds) throws DatabaseServiceException {
        if(workflowIds.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Integer, String> mapping = new HashMap<Integer, String>();
        DataSource dataSource = LIMSInitializationListener.getDataSource();
        Connection connection = null;
        try {
            connection = dataSource.getConnection();

            StringBuilder queryBuilder = new StringBuilder("SELECT workflow.id, extraction.extractionId FROM workflow " +
                    "INNER JOIN extraction ON extraction.id = workflow.extractionId WHERE workflow.id IN ");
            SqlUtilities.appendSetOfQuestionMarks(queryBuilder, workflowIds.size());
            PreparedStatement select = connection.prepareStatement(queryBuilder.toString());
            SqlUtilities.fillStatement(workflowIds, select);
            SqlUtilities.printSql(queryBuilder.toString(), workflowIds);
            ResultSet resultSet = select.executeQuery();
            while(resultSet.next()) {
                String extractionId = resultSet.getString("extractionId");
                if(extractionId != null) {
                    extractionId = extractionId.trim();
                    if(extractionId.length() > 0) {
                        mapping.put(resultSet.getInt("id"), extractionId);
                    }
                }
            }
            return mapping;
        } catch (SQLException e) {
            throw new DatabaseServiceException(e, e.getMessage(), false);
        } finally {
            SqlUtilities.closeConnection(connection);
        }
    }

    public static Map<Integer, String> getExtractionIdsForSequences(List<Integer> sequenceIds) throws DatabaseServiceException {
        if(sequenceIds.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<Integer, String> mapping = new HashMap<Integer, String>();
        DataSource dataSource = LIMSInitializationListener.getDataSource();
        Connection connection = null;
        try {
            StringBuilder queryBuilder = new StringBuilder("select assembly.id, extraction.extractionId from assembly, workflow, extraction where assembly.id IN ");
            SqlUtilities.appendSetOfQuestionMarks(queryBuilder, sequenceIds.size());
            queryBuilder.append(" and assembly.workflow = workflow.id and workflow.extractionId = extraction.id ");

            connection = dataSource.getConnection();
            PreparedStatement select = connection.prepareStatement(queryBuilder.toString());
            SqlUtilities.fillStatement(sequenceIds, select);
            SqlUtilities.printSql(queryBuilder.toString(), sequenceIds);
            ResultSet resultSet = select.executeQuery();
            while(resultSet.next()) {
                String extractionId = resultSet.getString("extractionId");
                if(extractionId != null) {
                    extractionId = extractionId.trim();
                    if(extractionId.length() > 0) {
                        mapping.put(resultSet.getInt("id"), extractionId);
                    }
                }
            }
            return mapping;
        } catch (SQLException e) {
            throw new DatabaseServiceException(e, e.getMessage(), false);
        } finally {
            SqlUtilities.closeConnection(connection);
        }
    }
}