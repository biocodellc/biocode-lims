package com.biomatters.plugins.biocode.server.security;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;

import javax.ws.rs.ForbiddenException;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * @author Matthew Cheung
 *         Created on 9/06/14 1:32 PM
 */
@XmlRootElement
public class User {
    public String username;
    public String password;

    public User(String username) {
        this.username = username;
    }

    public User() {
    }

    /**
     * @return The current logged in {@link com.biomatters.plugins.biocode.server.security.User}
     */
    public static User get() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if(principal instanceof UserDetails) {
            UserDetails user = (UserDetails) principal;
            return new User(user.getUsername());
        } else {
            return null;
        }
    }

    public void checkIsAdmin() throws ForbiddenException {
        if(!username.equals("admin")) {
            throw new ForbiddenException("User is not an admin");
        }
    }
}
