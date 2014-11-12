package com.biomatters.plugins.biocode.server.security;

import org.springframework.security.ldap.userdetails.LdapUserDetailsImpl;

/**
 * @author Gen Li
 *         Created on 10/11/14 1:30 PM
 */
public class CustomLdapUserDetailsImpl extends LdapUserDetailsImpl {
    private String firstname;
    private String lastname;
    private String email;

    public String getFirstname() {
        return firstname;
    }

    public String getLastname() {
        return lastname;
    }

    public String getEmail() {
        return email;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof CustomLdapUserDetailsImpl) {
            return super.getDn().equals(((CustomLdapUserDetailsImpl) obj).getDn());
        }
        return false;
    }

    @Override
    public String toString() {
        return super.toString() +
                "Firstname: " + firstname + "; " +
                "Lastname: " + lastname + "; " +
                "Email: " + email + "; ";
    }

    public static class Essence extends LdapUserDetailsImpl.Essence {
        @Override
        protected LdapUserDetailsImpl createTarget() {
            return new CustomLdapUserDetailsImpl();
        }

        public void setFirstname(String firstname) {
            ((CustomLdapUserDetailsImpl)instance).firstname = firstname;
        }

        public void setLastname(String lastname) {
            ((CustomLdapUserDetailsImpl)instance).lastname = lastname;
        }

        public void setEmail(String email) {
            ((CustomLdapUserDetailsImpl)instance).email = email;
        }
    }
}