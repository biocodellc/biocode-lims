package com.biomatters.plugins.biocode.server;

import com.biomatters.plugins.biocode.server.utilities.StringVerificationUtilities;

import java.util.HashMap;

/**
 * @author Gen Li
 *         Created on 14/10/14 3:56 PM
 */
public class LDAPConfiguration {
    private String server;
    private int port;
    private String userDNPattern;
    private String userSearchBase;
    private String userSearchFilter;
    private String groupSearchBase;
    private String groupSearchFilter;
    private String groupRoleAttribute;
    private String rolePrefix;

    public LDAPConfiguration(String server,
                             int port,
                             String userDNPattern,
                             String userSearchBase,
                             String userSearchFilter,
                             String groupSearchBase,
                             String groupSearchFilter,
                             String groupRoleAttribute,
                             String rolePrefix) {
        this.server = server;
        this.port = port;
        this.userDNPattern = userDNPattern;
        this.userSearchBase = userSearchBase;
        this.userSearchFilter = userSearchFilter;
        this.groupSearchBase = groupSearchBase;
        this.groupSearchFilter = groupSearchFilter;
        this.groupRoleAttribute = groupRoleAttribute;
        this.rolePrefix = rolePrefix;
    }

    public String getServer() {
        return server;
    }

    public int getPort() {
        return port;
    }

    public String getUserDNPattern() { return userDNPattern; }

    public String getUserSearchBase() { return userSearchBase; }

    public String getUserSearchFilter() { return userSearchFilter; }

    public String getGroupSearchBase() { return groupSearchBase; }

    public String getGroupSearchFilter() { return groupSearchFilter; }

    public String getGroupRoleAttribute() { return groupRoleAttribute; }

    public String getRolePrefix() { return rolePrefix; }
}