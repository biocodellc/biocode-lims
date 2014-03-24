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
public class Sequences {

    @GET
    public String list() {
        return "There are no sequences here yet";
    }

    @GET
    @Path("{sequence}")
    public String getSequence(@PathParam("sequence")String sequenceId) {
        throw new NotFoundException("No sequence for " + sequenceId);
    }
}
