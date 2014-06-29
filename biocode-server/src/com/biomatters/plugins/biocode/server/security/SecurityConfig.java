package com.biomatters.plugins.biocode.server.security;

import com.biomatters.plugins.biocode.labbench.lims.LIMSConnection;
import com.biomatters.plugins.biocode.labbench.lims.SqlLimsConnection;
import com.biomatters.plugins.biocode.server.LIMSInitializationListener;
import com.biomatters.plugins.biocode.server.Users;
import com.biomatters.plugins.biocode.utilities.SqlUtilities;
import org.apache.commons.dbcp.BasicDataSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

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
    public synchronized boolean createUserTablesIfNecessary(DataSource dataSource) {
        Connection connection = null;
        try {
            connection = dataSource.getConnection();

            String createUsersTableQuery = "CREATE TABLE IF NOT EXISTS " + LimsDatabaseConstants.USERS_TABLE_NAME + "(" +
                                               LimsDatabaseConstants.USERNAME_COLUMN_NAME_USERS_TABLE +" VARCHAR(255) NOT NULL, " +
                                               LimsDatabaseConstants.PASSWORD_COLUMN_NAME_USERS_TABLE + " VARCHAR(255) NOT NULL, " +
                                               LimsDatabaseConstants.FIRSTNAME_COLUMN_NAME_USERS_TABLE + " VARCHAR(255) NOT NULL, " +
                                               LimsDatabaseConstants.LASTNAME_COLUMN_NAME_USERS_TABLE + " VARCHAR(255) NOT NULL, " +
                                               LimsDatabaseConstants.EMAIL_COLUMN_NAME_USERS_TABLE + " VARCHAR(320) NOT NULL, " +
                                               LimsDatabaseConstants.ENABLED_COLUMN_NAME_USERS_TABLE + " BIT NOT NULL, " +
                                               "PRIMARY KEY(" + LimsDatabaseConstants.USERNAME_COLUMN_NAME_USERS_TABLE + "))";

            PreparedStatement statement = connection.prepareStatement(createUsersTableQuery);

            statement.executeUpdate();

            String createAuthoritiesTableQuery = "CREATE TABLE IF NOT EXISTS " + LimsDatabaseConstants.AUTHORITIES_TABLE_NAME + "(" +
                                                     LimsDatabaseConstants.USERNAME_COLUMN_NAME_USERS_TABLE + " VARCHAR(255) NOT NULL, " +
                                                     LimsDatabaseConstants.AUTHORITY_COLUMN_NAME_AUTHORITIES_TABLE + " VARCHAR(50) NOT NULL, " +
                                                     "PRIMARY KEY (username)," +
                                                     "FOREIGN KEY (username) " +
                                                        "REFERENCES users(username) " +
                                                        "ON DELETE CASCADE)";

            statement = connection.prepareStatement(createAuthoritiesTableQuery);

            statement.executeUpdate();

            return false;
        } catch (SQLException e) {
            e.printStackTrace();
            return true;
        } finally {
            SqlUtilities.closeConnection(connection);
        }
    }

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        http.csrf().disable()
            .authorizeRequests().antMatchers("/biocode/**")
                .authenticated()
                .and()
            .httpBasic();
    }
}
