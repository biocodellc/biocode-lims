package com.biomatters.plugins.biocode.server.security;

/**
 * @author Gen Li
 *         Created on 14/10/14 3:56 PM
 */
public class LDAPConfiguration {
    private static final String ROLE_PREFIX = "ROLE_";

    private String server;
    private int port;
    private String userDNPattern;
    private String userSearchBase;
    private String userSearchFilter;
    private String groupSearchBase;
    private String groupSearchFilter;
    private String groupRoleAttribute;
    private String rolePrefix;
    private String adminAuthority;

    public LDAPConfiguration(String server,
                             int port,
                             String userDNPattern,
                             String userSearchBase,
                             String userSearchFilter,
                             String groupSearchBase,
                             String groupSearchFilter,
                             String groupRoleAttribute,
                             String rolePrefix,
                             String adminAuthority) {
        this.server = server;
        this.port = port;
        this.userDNPattern = userDNPattern;
        this.userSearchBase = userSearchBase;
        this.userSearchFilter = userSearchFilter;
        this.groupSearchBase = groupSearchBase;
        this.groupSearchFilter = groupSearchFilter;
        this.groupRoleAttribute = groupRoleAttribute;
        this.rolePrefix = rolePrefix;
        this.adminAuthority = adminAuthority;
    }

    public String getServer() {
        return server;
    }

    public int getPort() {
        return port;
    }

    public String getUserDNPattern() {
        return userDNPattern;
    }

    public String getUserSearchBase() {
        return userSearchBase;
    }

    public String getUserSearchFilter() {
        return userSearchFilter;
    }

    public String getGroupSearchBase() {
        return groupSearchBase;
    }

    public String getGroupSearchFilter() {
        return groupSearchFilter;
    }

    public String getGroupRoleAttribute() {
        return groupRoleAttribute;
    }

    public String getRolePrefix() {
        return rolePrefix;
    }

    public String getAdminAuthority() {
        return adminAuthority == null ? null : ROLE_PREFIX + adminAuthority.toUpperCase();
    }
}