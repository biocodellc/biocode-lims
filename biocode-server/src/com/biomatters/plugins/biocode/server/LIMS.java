package com.biomatters.plugins.biocode.server;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;


/**
 * @author Matthew Cheung
 * @version $Id$
 *          <p/>
 *          Created on 20/03/14 2:39 PM
 */
@Path("lims")
public class LIMS {

    @GET
    @Produces("text/plain")
    public String getIt() {
        return "Got it!";
    }
}
