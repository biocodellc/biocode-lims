package com.biomatters.plugins.biocode.server;

import com.biomatters.plugins.biocode.labbench.reaction.FailureReason;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import java.util.List;

/**
 * @author Matthew Cheung
 *          <p/>
 *          Created on 6/04/14 10:40 PM
 */
@Path("failureReasons")
public class FailureReasons {
    @GET
    @Produces("application/xml")
    public List<FailureReason> list() {
        return LIMSInitializationListener.getLimsConnection().getPossibleFailureReasons();
    }
}
