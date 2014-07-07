package com.biomatters.plugins.biocode.server.security;

import com.biomatters.plugins.biocode.labbench.lims.DatabaseScriptRunner;
import com.biomatters.plugins.biocode.labbench.lims.LIMSConnection;
import com.biomatters.plugins.biocode.labbench.lims.SqlLimsConnection;
import com.biomatters.plugins.biocode.server.LIMSInitializationListener;
import com.biomatters.plugins.biocode.utilities.SqlUtilities;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.SQLException;
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
    private PasswordEncoder encoder = new BCryptPasswordEncoder();

    @Autowired
    public void configure(AuthenticationManagerBuilder auth) throws Exception {
        LIMSConnection limsConnection = LIMSInitializationListener.getLimsConnection();

        boolean needMemoryUsers;

        boolean hasDatabaseConnection = limsConnection instanceof SqlLimsConnection;

        if (hasDatabaseConnection) {
            DataSource dataSource = ((SqlLimsConnection) limsConnection).getDataSource();

            needMemoryUsers = createUserTablesIfNecessary(dataSource);

            auth = auth.jdbcAuthentication().dataSource(dataSource).passwordEncoder(encoder).and();

            initializeAdminUserIfNecessary(dataSource);
        } else {
            needMemoryUsers = true;
        }

        if(!hasDatabaseConnection || needMemoryUsers) {
            // If the database connection isn't set up or users haven't been added yet then we need to also use memory
            // auth with test users.
            auth.inMemoryAuthentication().withUser("admin").password("admin").roles(Role.ADMIN.name);
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
        http.csrf().disable()
            .authorizeRequests()
                .antMatchers("/biocode/projects/**").hasAuthority(LimsDatabaseConstants.AUTHORITY_ADMIN_CODE)
                .antMatchers("/biocode/users/**").hasAuthority(LimsDatabaseConstants.AUTHORITY_ADMIN_CODE)
                .antMatchers("/biocode/info/**").permitAll()
                .antMatchers("/biocode/**").authenticated()
                .anyRequest().permitAll()
            .and()
            .httpBasic();
    }
}
