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
@Path("plates")
public class Plates {

    @GET
    public String list() {
        return "There are no plates here yet";
    }

    @GET
    @Path("{plateId}")
    public String getTissue(@PathParam("plateId")String plateId) {
        throw new NotFoundException("No plates for " + plateId);
    }
}
