package com.biomatters.plugins.biocode.server;

import com.biomatters.plugins.biocode.server.utilities.StringArgumentUtilities;

import java.util.HashMap;

/**
 * @author Gen Li
 *         Created on 14/10/14 3:56 PM
 */
public class LDAPConfiguration {
    private String server;
    private int port;
    private String userDNPattern;

    public LDAPConfiguration(final String server, final int port, final String userDNPattern)
            throws IllegalArgumentException {
        StringArgumentUtilities.checkForNULLOrEmptyStringArguments(new HashMap<String, String>() {
            {
                put("server", server);
                put("userDNPattern", userDNPattern);
            }
        });

        this.server = server;
        this.port = port;
        this.userDNPattern = userDNPattern;
    }

    public String getServer() {
        return server;
    }

    public int getPort() {
        return port;
    }

    public String getUserDNPattern() { return userDNPattern; }
}