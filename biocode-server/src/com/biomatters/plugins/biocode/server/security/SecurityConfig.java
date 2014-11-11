package com.biomatters.plugins.biocode.server.security;

import com.biomatters.plugins.biocode.labbench.lims.DatabaseScriptRunner;
import com.biomatters.plugins.biocode.labbench.lims.LIMSConnection;
import com.biomatters.plugins.biocode.labbench.lims.LimsDatabaseConstants;
import com.biomatters.plugins.biocode.labbench.lims.SqlLimsConnection;
import com.biomatters.plugins.biocode.server.LIMSInitializationListener;
import com.biomatters.plugins.biocode.server.utilities.StringVerificationUtilities;
import com.biomatters.plugins.biocode.utilities.SqlUtilities;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.authentication.configurers.ldap.LdapAuthenticationProviderConfigurer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.www.BasicAuthenticationEntryPoint;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
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

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        http.csrf().disable()
            .authorizeRequests()
                .antMatchers(PROJECTS_URL + "/**", USERS_URL + "/**").hasAuthority(LimsDatabaseConstants.AUTHORITY_ADMIN_CODE)
                .antMatchers(INFO_URL + "/**").permitAll()
                .antMatchers(BASE_URL + "/**", BCIDROOTS_URL + "/**").authenticated()
                .anyRequest().permitAll().and()
            .addFilter(filter())
            .httpBasic();

        if (LIMSInitializationListener.getLDAPConfiguration() != null) {
            String LDAPAdminAuthority = LIMSInitializationListener.getLDAPConfiguration().getAdminAuthority();

            if (!StringVerificationUtilities.isStringNULLOrEmpty(LDAPAdminAuthority)) {
                http.authorizeRequests()
                    .antMatchers(PROJECTS_URL + "/**", USERS_URL + "/**").hasAuthority(LDAPAdminAuthority);
            }
        }
    }

    @Bean
    CustomAuthenticationFilter filter() throws Exception {
        return new CustomAuthenticationFilter(super.authenticationManager(), new BasicAuthenticationEntryPoint());
    }

    @Bean
    CustomLdapUserDetailsMapper userDetailsMapper() {
        return new CustomLdapUserDetailsMapper();
    }

    private void initializeAdminUserIfNecessary(DataSource dataSource) throws SQLException {
        Connection connection = null;
        try {
            connection = dataSource.getConnection();

            boolean noJDBCUser = true;

            for (User user : Users.getUserList(connection)) {
                if (!user.isLDAPAccount) {
                    noJDBCUser = false;
                    break;
                }
            }

            if (noJDBCUser) {
                Users.addUser(dataSource, new User("admin", "admin", "admin", "", "", true, true, false));
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

    private AuthenticationManagerBuilder authenticateWithLDAP(AuthenticationManagerBuilder auth, final LDAPConfiguration config) throws Exception {
        StringVerificationUtilities.throwExceptionIfAllNULLOrEmptyStrings(new HashMap<String, String>() {{
            put("server", config.getServer());
        }});

        StringVerificationUtilities.throwExceptionIfAllNULLOrEmptyStrings(new HashMap<String, String>() {{
            put("userDNPattern", config.getUserDNPattern());
            put("userSearchFilter", config.getUserSearchFilter());
        }});

        StringVerificationUtilities.throwExceptionIfAllNULLOrEmptyStrings(new HashMap<String, String>() {{
            put("groupSearchBase", config.getGroupSearchBase());
            put("groupSearchFilter", config.getGroupSearchFilter());
        }});

        LdapAuthenticationProviderConfigurer<AuthenticationManagerBuilder> ldapAuthenticationProviderConfigurer = auth.ldapAuthentication();

        ldapAuthenticationProviderConfigurer.contextSource().url(config.getServer());
        ldapAuthenticationProviderConfigurer.contextSource().port(config.getPort());

        CustomLdapUserDetailsMapper mapper = userDetailsMapper();

        mapper.setFirstnameAttribute(config.getFirstnameAttribute());
        mapper.setLastnameAttribute(config.getLastnameAttribute());
        mapper.setEmailAttribute(config.getEmailAttribute());

        ldapAuthenticationProviderConfigurer.userDetailsContextMapper(mapper);

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