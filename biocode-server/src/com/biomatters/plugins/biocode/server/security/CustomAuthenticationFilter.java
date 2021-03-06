package com.biomatters.plugins.biocode.server.security;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * @author Gen Li
 *         Created on 28/10/14 3:07 PM
 */
public class CustomAuthenticationFilter extends BasicAuthenticationFilter {
    public CustomAuthenticationFilter(AuthenticationManager authenticationManager, AuthenticationEntryPoint authenticationEntryPoint) {
        super(authenticationManager, authenticationEntryPoint);
    }

    @Override
    protected void onSuccessfulAuthentication(HttpServletRequest request, HttpServletResponse response, Authentication authResult) throws IOException {
        super.onSuccessfulAuthentication(request, response, authResult);
        Users.handleLDAPUserLogin();
    }
}
