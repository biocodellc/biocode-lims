package com.biomatters.plugins.biocode.server;

/**
 * @author Gen Li
 *         Created on 14/10/14 3:56 PM
 */
public class LDAPAuthenticationDetails {
    public LDAPAuthenticationDetails(String server, String port, String username, String password) {
        SERVER = server;
        PORT = port;
        USERNAME= username;
        PASSWORD = password;
    }

    public String SERVER;
    public String PORT;
    public String USERNAME;
    public String PASSWORD;
}