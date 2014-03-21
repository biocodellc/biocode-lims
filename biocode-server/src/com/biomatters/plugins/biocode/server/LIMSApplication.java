package com.biomatters.plugins.biocode.server;

import org.glassfish.jersey.server.ResourceConfig;

import javax.ws.rs.ApplicationPath;

/**
 * @author Matthew Cheung
 * @version $Id$
 *          <p/>
 *          Created on 20/03/14 2:55 PM
 */
@ApplicationPath("service")
public class LIMSApplication extends ResourceConfig {
    public LIMSApplication() {
        packages("Com.biomatters.plugins.biocode.server");
    }
}
