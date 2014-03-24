package com.biomatters.plugins.biocode.server;

import javax.ws.rs.GET;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;

/**
 * @author Matthew Cheung
 * @version $Id$
 *          <p/>
 *          Created on 23/03/14 5:20 PM
 */
public class Workflows {
    @GET
    public String list() {
        return "There are no workflows here yet";
    }

    @GET
    @Path("{workflowId}")
    public String getTissue(@PathParam("workflowId")String workflowId) {
        throw new NotFoundException("No workflow for " + workflowId);
    }
}
