package com.biomatters.plugins.biocode.server;

import com.biomatters.plugins.biocode.server.utilities.StringArgumentUtilities;

import java.util.HashMap;

/**
 * @author Gen Li
 *         Created on 14/10/14 3:56 PM
 */
public class LDAPAuthenticationDetails {
    private String server;
    private int port;
    private String username;
    private String password;

    public LDAPAuthenticationDetails(final String server, final int port, final String username, final String password) throws IllegalArgumentException {
        StringArgumentUtilities.checkForNULLOrEmptyStringArguments(new HashMap<String, String>() {
            {
                put("server", server);
                put("username", username);
                put("password", password);
            }
        });

        this.server = server;
        this.port = port;
        this.username = username;
        this.password = password;
    }

    public String getServer() {
        return server;
    }

    public int getPort() {
        return port;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }
}