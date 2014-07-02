package com.biomatters.plugins.biocode.server;

import com.biomatters.geneious.publicapi.databaseservice.DatabaseServiceException;
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
        return Response.ok(RestUtilities.getSearchResults(query, showTissues, showWorkflows, showPlates, showSequenceIds, tissueIdsToMatchSet)).build();
    }

    @GET
    @Path("{id}")
    @Produces("application/xml")
    public String getResults(@PathParam("id")String id) {

        // Do we need this method for POST
        return id;
    }
}