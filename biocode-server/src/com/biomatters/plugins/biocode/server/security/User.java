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


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        User user = (User) o;

        if (email != null ? !email.equals(user.email) : user.email != null) return false;
        if (firstname != null ? !firstname.equals(user.firstname) : user.firstname != null) return false;
        if (lastname != null ? !lastname.equals(user.lastname) : user.lastname != null) return false;
        if (!username.equals(user.username)) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = username.hashCode();
        result = 31 * result + (firstname != null ? firstname.hashCode() : 0);
        result = 31 * result + (lastname != null ? lastname.hashCode() : 0);
        result = 31 * result + (email != null ? email.hashCode() : 0);
        return result;
    }
}
