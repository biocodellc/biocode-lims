package com.biomatters.plugins.biocode.server;

import com.biomatters.geneious.publicapi.utilities.StringUtilities;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.provisioning.JdbcUserDetailsManager;
import org.springframework.security.provisioning.UserDetailsManager;

import javax.ws.rs.*;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * @author Matthew Cheung
 *         Created on 15/05/14 5:58 PM
 */
@Path("users")
public class Users {

    UserDetailsManager manager;
    private UserDetailsManager getManager() {
        if(manager == null) {
            createManager();
        }
        return manager;
    }

    private synchronized void createManager() {
        JdbcUserDetailsManager manager = new JdbcUserDetailsManager();
        manager.setDataSource(LIMSInitializationListener.getDataSource());
    }

    @GET
    @Produces("text/plain")
    public String list() {
        // todo better way?  Does Spring Security provide a way to list all users?
        Connection connection = null;
        try {
            connection = LIMSInitializationListener.getDataSource().getConnection();
            PreparedStatement statement = connection.prepareStatement("SELECT username FROM users");
            ResultSet resultSet = statement.executeQuery();
            List<String> usernames = new ArrayList<String>();
            while(resultSet.next()) {
                usernames.add(resultSet.getString(1));
            }
            resultSet.close();
            return StringUtilities.join(",", usernames);
        } catch (SQLException e) {
            throw new InternalServerErrorException("Failed to list users", e);
        } finally {
            if(connection != null) {
                try {
                    connection.close();
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    @PUT
    public void addUser(@QueryParam("username")String username, @QueryParam("password")String password) {
        User user = new User(username, password);
        manager.createUser(user);
    }

    private static BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    private static class User implements UserDetails {

        private String username;
        private String passwordHash;
        public User(String username, String password) {
            this.username = username;
            this.passwordHash = encoder.encode(password);
        }

        @Override
        public Collection<? extends GrantedAuthority> getAuthorities() {
            return Collections.emptyList();
        }

        @Override
        public String getPassword() {
            return passwordHash;
        }

        @Override
        public String getUsername() {
            return username;
        }

        @Override
        public boolean isAccountNonExpired() {
            return true;
        }

        @Override
        public boolean isAccountNonLocked() {
            return true;
        }

        @Override
        public boolean isCredentialsNonExpired() {
            return true;
        }

        @Override
        public boolean isEnabled() {
            return true;
        }
    }
}