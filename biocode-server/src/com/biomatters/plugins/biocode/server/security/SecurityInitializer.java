package com.biomatters.plugins.biocode.server.security;

import org.springframework.security.web.context.AbstractSecurityWebApplicationInitializer;

/**
 * @author Matthew Cheung
 *          <p/>
 *          Created on 8/05/14 2:11 PM
 */
public class SecurityInitializer extends AbstractSecurityWebApplicationInitializer {
    public SecurityInitializer() {
        super(SecurityConfig.class);
    }
}
