package com.biomatters.plugins.biocode.server;

import com.biomatters.plugins.biocode.server.security.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.provisioning.JdbcUserDetailsManager;
import org.springframework.security.provisioning.UserDetailsManager;

import javax.ws.rs.*;
import javax.ws.rs.core.GenericEntity;
import javax.ws.rs.core.Response;
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
        this.manager = manager;
    }

    @GET
    @Produces({"application/xml", "application/json"})
    public Response list() {
        Connection connection = null;
        try {
            connection = LIMSInitializationListener.getDataSource().getConnection();
            PreparedStatement statement = connection.prepareStatement("SELECT username FROM users");
            ResultSet resultSet = statement.executeQuery();
            List<User> users = new ArrayList<User>();
            while(resultSet.next()) {
                User user = new User();
                user.username = resultSet.getString(1);
                users.add(user);
            }
            resultSet.close();

            return Response.ok(new GenericEntity<List<User>>(users){}).build();

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

    @GET
    @Produces("application/xml")
    @Path("{username}")
    public User getUser(@PathParam("username")String username) {
        return User.get();  // todo
    }

    @POST
    @Consumes("application/xml")
    public void addUser(User user) {
        UserAccountToAdd account = new UserAccountToAdd(user.username, user.password);
        getManager().createUser(account);
    }

    @PUT
    @Path("{username}")
    @Consumes("application/xml")
    public void updateUser(@PathParam("username")String username, User user) {
        // todo
    }

    @GET
    @Path("createTest/{username}")
    public User test(@PathParam("username")String username, @QueryParam("password")String password) {
        if(password == null) {
            password = "helix8";
        }
        getManager().createUser(new UserAccountToAdd(username, password));
        return new User(username);
    }

    @DELETE
    @Path("{username}")
    public void deleteUser(@PathParam("username")String username) {
        User.get().checkIsAdmin();
        getManager().deleteUser(username);
    }

    private static BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    private static class UserAccountToAdd implements UserDetails {

        private String username;
        private String passwordHash;
        public UserAccountToAdd(String username, String password) {
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