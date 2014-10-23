package com.biomatters.plugins.biocode.server.security;

import com.biomatters.plugins.biocode.labbench.lims.DatabaseScriptRunner;
import com.biomatters.plugins.biocode.labbench.lims.LIMSConnection;
import com.biomatters.plugins.biocode.labbench.lims.LimsDatabaseConstants;
import com.biomatters.plugins.biocode.labbench.lims.SqlLimsConnection;
import com.biomatters.plugins.biocode.server.LIMSInitializationListener;
import com.biomatters.plugins.biocode.server.utilities.StringVerificationUtilities;
import com.biomatters.plugins.biocode.utilities.SqlUtilities;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.authentication.configurers.ldap.LdapAuthenticationProviderConfigurer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.config.annotation.web.configurers.ExpressionUrlAuthorizationConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

/**
 * @author Matthew Cheung
 * @version $Id$
 *          <p/>
 *          Created on 8/05/14 1:56 PM
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig extends WebSecurityConfigurerAdapter {
    private static final String BASE_URL      = "/biocode";
    private static final String PROJECTS_URL  = BASE_URL + "/projects";
    private static final String USERS_URL     = BASE_URL + "/users";
    private static final String BCIDROOTS_URL = BASE_URL + "/bcid-roots";
    private static final String INFO_URL      = BASE_URL + "/info";

    private PasswordEncoder encoder = new BCryptPasswordEncoder();

    @Autowired
    public void configure(AuthenticationManagerBuilder auth) throws Exception {
        LIMSConnection limsConnection = LIMSInitializationListener.getLimsConnection();

        boolean needMemoryUsers = false;

        boolean hasDatabaseConnection = limsConnection instanceof SqlLimsConnection;

        LDAPConfiguration ldapConfiguration = LIMSInitializationListener.getLDAPConfiguration();

        if (ldapConfiguration != null) {
            authenticateWithLDAP(auth, ldapConfiguration);
        } else if (hasDatabaseConnection) {
            DataSource dataSource = ((SqlLimsConnection) limsConnection).getDataSource();

            needMemoryUsers = createUserTablesIfNecessary(dataSource);

            auth.jdbcAuthentication().dataSource(dataSource).passwordEncoder(encoder).and();

            initializeAdminUserIfNecessary(dataSource);
        }

        if (needMemoryUsers) {
            // If the use of LDAP authentication isn't specified or the database connection isn't set up or users
            // haven't been added yet then we need to also use memory auth with test users.
            auth.inMemoryAuthentication().withUser("admin").password("admin").roles(LimsDatabaseConstants.AUTHORITY_ADMIN_CODE);
        }
    }

    private void initializeAdminUserIfNecessary(DataSource dataSource) throws SQLException {
        Connection connection = null;
        try {
            connection = dataSource.getConnection();

            List<User> users = Users.getUserList(connection);

            Users usersResource = new Users();
            if (users.isEmpty()) {
                User newAdmin = new User("admin", "admin", "admin", "", "", true, true);

                usersResource.addUser(newAdmin);
            }
        } finally {
            SqlUtilities.closeConnection(connection);
        }
    }

    /**
     *
     * @param dataSource
     * @return true if there are currently no user accounts
     * @throws SQLException
     */
    public static boolean createUserTablesIfNecessary(DataSource dataSource) {
        Connection connection = null;
        try {
            connection = dataSource.getConnection();
            Set<String> tables = SqlUtilities.getDatabaseTableNamesLowerCase(connection);
            if(!tables.contains(LimsDatabaseConstants.USERS_TABLE_NAME.toLowerCase())) {
                setupTables(connection);
            }
            return false;
        } catch (SQLException e) {
            e.printStackTrace();
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return true;
        } finally {
            SqlUtilities.closeConnection(connection);
        }
    }

    private static void setupTables(Connection connection) throws SQLException, IOException {
        String scriptName = "add_access_control.sql";
        InputStream script = SecurityConfig.class.getResourceAsStream(scriptName);
        if(script == null) {
            throw new IllegalStateException("Missing " + scriptName + ".  Cannot set up security.");
        }
        DatabaseScriptRunner.runScript(connection, script, false, false);
    }

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        http.csrf().disable().authorizeRequests()
                .antMatchers(PROJECTS_URL + "/**").hasAuthority(LimsDatabaseConstants.AUTHORITY_ADMIN_CODE)
                .antMatchers(USERS_URL +"/**").hasAuthority(LimsDatabaseConstants.AUTHORITY_ADMIN_CODE)
                .antMatchers(BCIDROOTS_URL + "/**").hasAuthority(LimsDatabaseConstants.AUTHORITY_ADMIN_CODE)
                .antMatchers(INFO_URL + "/**").permitAll()
                .antMatchers(BASE_URL + "/**").authenticated()
                .anyRequest().permitAll()
            .and()
            .httpBasic();

        if (LIMSInitializationListener.getLDAPConfiguration() != null) {
            String LDAPAdminAuthority = LIMSInitializationListener.getLDAPConfiguration().getAdminAuthority();

            if (!StringVerificationUtilities.isStringNULLOrEmpty(LDAPAdminAuthority)) {
                http.csrf().disable().authorizeRequests()
                        .antMatchers(PROJECTS_URL + "/**").hasAnyAuthority(LDAPAdminAuthority)
                        .antMatchers(USERS_URL + "/**").hasAnyAuthority(LDAPAdminAuthority)
                        .antMatchers(BCIDROOTS_URL + "/**").hasAnyAuthority(LDAPAdminAuthority);
            }
        }
    }

    private AuthenticationManagerBuilder authenticateWithLDAP(AuthenticationManagerBuilder auth, final LDAPConfiguration config) throws Exception {
        StringVerificationUtilities.throwExceptionIfExistsNULLOrEmptyStrings(new HashMap<String, String>() {{
            put("server", config.getServer());
        }});

        StringVerificationUtilities.throwExceptionIfAllNULLOrEmptyStrings(new HashMap<String, String>() {{
            put("userDNPattern", config.getUserDNPattern());
            put("userSearchFilter", config.getUserSearchFilter());
        }});

        LdapAuthenticationProviderConfigurer<AuthenticationManagerBuilder> ldapAuthenticationProviderConfigurer = auth.ldapAuthentication();

        ldapAuthenticationProviderConfigurer.contextSource().url(config.getServer());
        ldapAuthenticationProviderConfigurer.contextSource().port(config.getPort());

        if (!StringVerificationUtilities.isStringNULLOrEmpty(config.getUserDNPattern())) {
            ldapAuthenticationProviderConfigurer.userDnPatterns(config.getUserDNPattern());
        }

        if (!StringVerificationUtilities.isStringNULLOrEmpty(config.getUserSearchFilter())) {
            ldapAuthenticationProviderConfigurer.userSearchFilter(config.getUserSearchFilter());
        }

        if (!StringVerificationUtilities.isStringNULLOrEmpty(config.getUserSearchBase())) {
            ldapAuthenticationProviderConfigurer.userSearchBase(config.getUserSearchBase());
        }

        if (!StringVerificationUtilities.isStringNULLOrEmpty(config.getGroupSearchBase())) {
            ldapAuthenticationProviderConfigurer.groupSearchBase(config.getGroupSearchBase());
        }

        if (!StringVerificationUtilities.isStringNULLOrEmpty(config.getGroupSearchFilter())) {
            ldapAuthenticationProviderConfigurer.groupSearchFilter(config.getGroupSearchFilter());
        }

        if (!StringVerificationUtilities.isStringNULLOrEmpty(config.getGroupRoleAttribute())) {
            ldapAuthenticationProviderConfigurer.groupRoleAttribute(config.getGroupRoleAttribute());
        }

        if (!StringVerificationUtilities.isStringNULLOrEmpty(config.getRolePrefix())) {
            ldapAuthenticationProviderConfigurer.rolePrefix(config.getRolePrefix());
        }

        return ldapAuthenticationProviderConfigurer.and();
    }
}