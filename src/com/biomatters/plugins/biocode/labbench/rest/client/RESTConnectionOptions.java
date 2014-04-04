package com.biomatters.plugins.biocode.labbench.rest.client;

import com.biomatters.geneious.publicapi.plugin.Options;
import com.biomatters.plugins.biocode.labbench.PasswordOptions;
import com.biomatters.plugins.biocode.utilities.PasswordOption;

/**
 * @author Matthew Cheung
 * @version $Id$
 *          <p/>
 *          Created on 25/03/14 2:45 PM
 */
public class RESTConnectionOptions extends PasswordOptions {

    private static final String URL = "url";
    private static final String USERNAME = "username";
    private static final String PASSWORD = "password";

    public RESTConnectionOptions() {
        super(RESTConnectionOptions.class);

        addStringOption(URL, "URL:", "", "The server to connect to");
        addStringOption(USERNAME, "Username:", "");
        addCustomOption(new PasswordOption(PASSWORD, "Password:", true));
    }

    public String getName() {
        return "Connect through hosted Biocode LIMS website";
    }

    public String getHost() {
        return getValueAsString(URL);
    }

    public String getUsername() {
        return getValueAsString(USERNAME);
    }
}
