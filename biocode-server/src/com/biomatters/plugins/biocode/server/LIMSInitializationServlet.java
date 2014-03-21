package com.biomatters.plugins.biocode.server;

import javax.servlet.GenericServlet;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import java.io.IOException;

/**
 * Responsible for making the various LIMS connections on start up
 *
 * @author Matthew Cheung
 * @version $Id$
 *          <p/>
 *          Created on 20/03/14 3:18 PM
 */
public class LIMSInitializationServlet extends GenericServlet {

    private String settingsFolderName = ".biocode-lims";
    private String defaultPropertiesFile = "default_connection.properties";
    private String propertiesFile = "connection.properties";

    @Override
    public void service(ServletRequest servletRequest, ServletResponse servletResponse) throws ServletException, IOException {
        // Ignore any requests.  There shouldn't be any connections made to this servlet
    }

    @Override
    public void init() throws ServletException {

    }
}
