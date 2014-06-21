package com.biomatters.plugins.biocode.server.security;

import com.biomatters.plugins.biocode.labbench.lims.LIMSConnection;
import com.biomatters.plugins.biocode.labbench.lims.SqlLimsConnection;
import com.biomatters.plugins.biocode.server.LIMSInitializationListener;
import org.apache.commons.dbcp.BasicDataSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * @author Matthew Cheung
 * @version $Id$
 *          <p/>
 *          Created on 8/05/14 1:56 PM
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig extends WebSecurityConfigurerAdapter {

    private BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    @Autowired
    public void configure(AuthenticationManagerBuilder auth) throws Exception {
        LIMSConnection limsConnection = LIMSInitializationListener.getLimsConnection();
        boolean hasDatabaseConnection = limsConnection instanceof SqlLimsConnection;
        boolean needMemoryUsers;
        if(hasDatabaseConnection) {
            BasicDataSource dataSource = ((SqlLimsConnection) limsConnection).getDataSource();
            needMemoryUsers = createUserTablesIfNecessary(dataSource);
            auth = auth.jdbcAuthentication().dataSource(dataSource).passwordEncoder(encoder).and();
        } else {
            needMemoryUsers = true;
        }

        if(!hasDatabaseConnection || needMemoryUsers) {
            // If the database connection isn't set up or users haven't been added yet then we need to also use memory
            // auth with test users.
            auth.inMemoryAuthentication().withUser("admin").password("admin").roles(Role.ADMIN.name());
        }
    }

    /**
     *
     * @param dataSource
     * @return true if there are currently no user accounts
     * @throws SQLException
     */
    private synchronized boolean createUserTablesIfNecessary(BasicDataSource dataSource) throws SQLException {
        Connection connection = null;
        try {
            connection = dataSource.getConnection();
            // todo Run SQL script if tables not present
            // todo Insert admin account
            return true;
        } finally {
            if(connection != null) {
                connection.close();
            }
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
