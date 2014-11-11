package com.biomatters.plugins.biocode.server.security;

import org.springframework.ldap.core.DirContextOperations;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.ldap.userdetails.LdapUserDetailsImpl;
import org.springframework.security.ldap.userdetails.LdapUserDetailsMapper;

import java.util.Collection;

/**
 * @author Gen Li
 *         Created on 10/11/14 1:20 PM
 */
public class CustomLdapUserDetailsMapper extends LdapUserDetailsMapper {
    private String firstnameAttribute;
    private String lastnameAttribute;
    private String emailAttribute;

    public void setFirstnameAttribute(String firstnameAttribute) {
        this.firstnameAttribute = firstnameAttribute;
    }

    public void setLastnameAttribute(String lastnameAttribute) {
        this.lastnameAttribute = lastnameAttribute;
    }

    public void setEmailAttribute(String emailAttribute) {
        this.emailAttribute = emailAttribute;
    }

    @Override
    public UserDetails mapUserFromContext(final DirContextOperations dirContextOperations, String username, Collection<? extends GrantedAuthority> grantedAuthorities) {
        CustomLdapUserDetailsImpl.Essence essence = new CustomLdapUserDetailsImpl.Essence();

        LdapUserDetailsImpl userDetails = (LdapUserDetailsImpl)super.mapUserFromContext(dirContextOperations, username, grantedAuthorities);

        essence.setAuthorities(userDetails.getAuthorities());
        essence.setDn(userDetails.getDn());
        essence.setPassword(userDetails.getPassword());
        essence.setUsername(userDetails.getUsername());
        essence.setAccountNonExpired(userDetails.isAccountNonLocked());
        essence.setAccountNonLocked(userDetails.isAccountNonLocked());
        essence.setCredentialsNonExpired(userDetails.isCredentialsNonExpired());
        essence.setEnabled(userDetails.isEnabled());
        essence.setTimeBeforeExpiration(userDetails.getTimeBeforeExpiration());
        essence.setGraceLoginsRemaining(userDetails.getGraceLoginsRemaining());

        Object firstname = dirContextOperations.getObjectAttribute(firstnameAttribute);
        Object lastname = dirContextOperations.getObjectAttribute(lastnameAttribute);
        Object email = dirContextOperations.getObjectAttribute(emailAttribute);

        if (firstname == null || !(firstname instanceof String)) {
            firstname = "";
        }

        if (lastname == null || !(lastname instanceof String)) {
            lastname = "";
        }

        if (email == null || !(email instanceof String)) {
            email = "";
        }

        essence.setEmail((String)email);
        essence.setLastname((String)lastname);
        essence.setFirstname((String)firstname);

        return essence.createUserDetails();
    }
}