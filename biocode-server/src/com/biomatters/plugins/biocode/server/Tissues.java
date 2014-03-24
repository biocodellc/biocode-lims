package com.biomatters.plugins.biocode.server;

import com.biomatters.plugins.biocode.labbench.BiocodeService;

import javax.ws.rs.GET;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;

/**
 * A tissue entry
 *
 * @author Matthew Cheung
 * @version $Id$
 *          <p/>
 *          Created on 23/03/14 4:57 PM
 */
@Path("tissues")
public class Tissues {

    @GET
    public String list() {
        return "There are no tissues here yet";
    }

    @GET
    @Path("{tissueId}")
    public String getTissue(@PathParam("tissueId")String tissueId) {


        throw new NotFoundException("No tissue for " + tissueId);
    }
}
