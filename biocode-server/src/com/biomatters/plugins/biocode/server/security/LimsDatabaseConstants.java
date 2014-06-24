package com.biomatters.plugins.biocode.server.security;

/**
 * @author Gen Li
 *         Created on 20/06/14 5:42 PM
 */
public class LimsDatabaseConstants {
    public static final String USERS_TABLE_NAME       = "users";
    public static final String AUTHORITIES_TABLE_NAME = "authorities";

    public static final String USERNAME_COLUMN_NAME_USERS_TABLE  = "username";
    public static final String PASSWORD_COLUMN_NAME_USERS_TABLE  = "password";
    public static final String FIRSTNAME_COLUMN_NAME_USERS_TABLE = "firstname";
    public static final String LASTNAME_COLUMN_NAME_USERS_TABLE  = "lastname";
    public static final String EMAIL_COLUMN_NAME_USERS_TABLE     = "email";
    public static final String ENABLED_COLUMN_NAME_USERS_TABLE   = "enabled";

    public static final String USERNAME_COLUMN_NAME_AUTHORITIES_TABLE  = "username";
    public static final String AUTHORITY_COLUMN_NAME_AUTHORITIES_TABLE = "authority";

    public static final String AUTHORITY_ADMIN_CODE = "admin";
    public static final String AUTHORITY_WRITER_CODE = "writer";
    public static final String AUTHORITY_READER_CODE = "reader";
}
