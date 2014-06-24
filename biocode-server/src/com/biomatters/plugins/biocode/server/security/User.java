package com.biomatters.plugins.biocode.server.security;

import javax.xml.bind.annotation.XmlRootElement;

/**
 * @author Matthew Cheung
 *         Created on 9/06/14 1:32 PM
 */
@XmlRootElement
public class User {
    public String username;
    public String password;
    public String firstname;
    public String lastname;
    public String email;
    public boolean enabled = true;
    public boolean isAdministrator = false;

    public User(String username, String password, String firstname, String lastname, String email, boolean enabled, boolean isAdministrator) {
        this.username = username;
        this.password = password;
        this.firstname = firstname;
        this.lastname = lastname;
        this.email = email;
        this.enabled = enabled;
        this.isAdministrator = isAdministrator;
    }

    public User() {
        // Empty constructor required for automagic construction from JSON or XML
    }

}
