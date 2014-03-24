package com.biomatters.plugins.biocode.server;

import org.glassfish.jersey.server.ResourceConfig;

import javax.ws.rs.ApplicationPath;
import javax.ws.rs.Path;

/**
 * @author Matthew Cheung
 * @version $Id$
 *          <p/>
 *          Created on 20/03/14 2:55 PM
 */
@ApplicationPath("lims")
public class LIMSWebService extends ResourceConfig {
    public LIMSWebService() {
        packages("com.biomatters.plugins.biocode.server");
        packages("org.glassfish.jersey.examples.jsonmoxy");
    }
}
