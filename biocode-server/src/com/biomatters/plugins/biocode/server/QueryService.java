package com.biomatters.plugins.biocode.server;

import com.biomatters.geneious.publicapi.databaseservice.DatabaseServiceException;
import com.biomatters.geneious.publicapi.databaseservice.Query;
import com.biomatters.plugins.biocode.labbench.ConnectionException;
import com.biomatters.plugins.biocode.labbench.fims.FimsProject;
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
                           @DefaultValue("false") @QueryParam("showSequenceIds") boolean showSequenceIds,
                                                  @QueryParam("tissueIdsToMatch") String tissueIdsToMatch) throws DatabaseServiceException, ConnectionException {
        try {
            Set<String> tissueIdsToMatchSet;
            if(tissueIdsToMatch == null) {
                tissueIdsToMatchSet = getTissueIdsToMatchForUsersAccess();
            } else {
                tissueIdsToMatchSet = new HashSet<String>(Arrays.asList(tissueIdsToMatch.split(",")));
            }
            return Response.ok(RestUtilities.getSearchResults(query, showTissues, showWorkflows, showPlates, showSequenceIds, tissueIdsToMatchSet)).build();
        } catch (ConnectionException e) {
            throw new InternalServerErrorException("Could not access FIMS: " + e.getMainMessage());
        }
    }

    private Set<String> getTissueIdsToMatchForUsersAccess() throws DatabaseServiceException, ConnectionException {
        Set<String> tissueIdsToMatchSet;List<FimsProject> projectsUserHasAccessTo = Projects.getFimsProjectsUserHasAtLeastRole(
                LIMSInitializationListener.getDataSource(),
                LIMSInitializationListener.getFimsConnection(),
                Users.getLoggedInUser(), Role.READER);
        if(projectsUserHasAccessTo == null) {
            tissueIdsToMatchSet = null;
        } else {
            tissueIdsToMatchSet = new HashSet<String>(
                    LIMSInitializationListener.getFimsConnection().getTissueIdsMatchingQuery(
                            Query.Factory.createBrowseQuery(), projectsUserHasAccessTo)
            );
        }
        return tissueIdsToMatchSet;
    }

    @GET
    @Path("{id}")
    @Produces("application/xml")
    public String getResults(@PathParam("id")String id) {

        // Do we need this method for POST
        return id;
    }
}