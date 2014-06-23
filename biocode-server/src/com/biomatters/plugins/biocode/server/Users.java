package com.biomatters.plugins.biocode.server;

import com.biomatters.plugins.biocode.server.security.LimsDatabaseConstants;
import com.biomatters.plugins.biocode.server.security.User;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import javax.ws.rs.*;
import javax.ws.rs.core.GenericEntity;
import javax.ws.rs.core.Response;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Matthew Cheung
 *         Created on 15/05/14 5:58 PM
 */
@Path("users")
public class Users {
    private BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    @GET
    @Produces({"application/xml", "application/json"})
    public Response list() {
        Connection connection = null;
        try {
            connection = LIMSInitializationListener.getDataSource().getConnection();

            String query = "SELECT " + LimsDatabaseConstants.USERNAME_COLUMN_NAME_USERS_TABLE + ", " +
                                       LimsDatabaseConstants.PASSWORD_COLUMN_NAME_USERS_TABLE + ", " +
                                       LimsDatabaseConstants.FIRSTNAME_COLUMN_NAME_USERS_TABLE + ", " +
                                       LimsDatabaseConstants.LASTNAME_COLUMN_NAME_USERS_TABLE + ", " +
                                       LimsDatabaseConstants.EMAIL_COLUMN_NAME_USERS_TABLE + ", "  +
                                       LimsDatabaseConstants.ENABLED_COLUMN_NAME_USERS_TABLE + " " +
                           "FROM " + LimsDatabaseConstants.USERS_TABLE_NAME;

            PreparedStatement statement = connection.prepareStatement(query);

            ResultSet resultSet = statement.executeQuery();

            List<User> users = new ArrayList<User>();
            while(resultSet.next()) {
                User user = new User(resultSet.getString(LimsDatabaseConstants.USERNAME_COLUMN_NAME_USERS_TABLE),
                                     resultSet.getString(LimsDatabaseConstants.PASSWORD_COLUMN_NAME_USERS_TABLE),
                                     resultSet.getString(LimsDatabaseConstants.FIRSTNAME_COLUMN_NAME_USERS_TABLE),
                                     resultSet.getString(LimsDatabaseConstants.LASTNAME_COLUMN_NAME_USERS_TABLE),
                                     resultSet.getString(LimsDatabaseConstants.EMAIL_COLUMN_NAME_USERS_TABLE),
                                     resultSet.getBoolean(LimsDatabaseConstants.ENABLED_COLUMN_NAME_USERS_TABLE));

                users.add(user);
            }

            resultSet.close();

            return Response.ok(new GenericEntity<List<User>>(users){}).build();
        } catch (SQLException e) {
            throw new InternalServerErrorException("Failed to list users", e);
        } finally {
            if (connection != null) {
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
        Connection connection = null;
        try {
            connection = LIMSInitializationListener.getDataSource().getConnection();

            String query = "SELECT " + LimsDatabaseConstants.USERNAME_COLUMN_NAME_USERS_TABLE + ", " +
                                       LimsDatabaseConstants.PASSWORD_COLUMN_NAME_USERS_TABLE + ", " +
                                       LimsDatabaseConstants.FIRSTNAME_COLUMN_NAME_USERS_TABLE + ", " +
                                       LimsDatabaseConstants.LASTNAME_COLUMN_NAME_USERS_TABLE + ", " +
                                       LimsDatabaseConstants.EMAIL_COLUMN_NAME_USERS_TABLE + ", "  +
                                       LimsDatabaseConstants.ENABLED_COLUMN_NAME_USERS_TABLE + " " +
                           "FROM " + LimsDatabaseConstants.USERS_TABLE_NAME + " "  +
                           "WHERE " + LimsDatabaseConstants.USERNAME_COLUMN_NAME_USERS_TABLE + "=?";

            PreparedStatement statement = connection.prepareStatement(query);

            statement.setObject(1, username);

            ResultSet resultSet = statement.executeQuery();

            if (!resultSet.next()) {
                throw new InternalServerErrorException("User account '" + username + "' not found.");
            }

            return new User(resultSet.getString(LimsDatabaseConstants.USERNAME_COLUMN_NAME_USERS_TABLE),
                            resultSet.getString(LimsDatabaseConstants.PASSWORD_COLUMN_NAME_USERS_TABLE),
                            resultSet.getString(LimsDatabaseConstants.FIRSTNAME_COLUMN_NAME_USERS_TABLE),
                            resultSet.getString(LimsDatabaseConstants.LASTNAME_COLUMN_NAME_USERS_TABLE),
                            resultSet.getString(LimsDatabaseConstants.EMAIL_COLUMN_NAME_USERS_TABLE),
                            resultSet.getBoolean(LimsDatabaseConstants.ENABLED_COLUMN_NAME_USERS_TABLE));
        } catch (SQLException e) {
            throw new InternalServerErrorException("Failed to retrieve user account '" + username + "'", e);
        } finally {
            if (connection != null) {
                try {
                    connection.close();
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    @POST
    @Produces("text/plain")
    @Consumes("application/xml")
    public String addUser(User user) {
        Connection connection = null;
        try {
            connection = LIMSInitializationListener.getDataSource().getConnection();

            String query = "INSERT INTO " + LimsDatabaseConstants.USERS_TABLE_NAME + " VALUES (?, ?, ?, ?, ?, ?)";

            PreparedStatement statement = connection.prepareStatement(query);

            statement.setObject(1, user.username);
            statement.setObject(2, user.password);
            statement.setObject(3, user.firstname);
            statement.setObject(4, user.lastname);
            statement.setObject(5, user.email);
            statement.setObject(6, user.enabled);

            if (statement.executeUpdate() > 0) {
                return "User added.";
            } else {
                return "Failed to add user.";
            }
        } catch (SQLException e) {
            throw new InternalServerErrorException("Failed to add user.", e);
        } finally {
            if (connection != null) {
                try {
                    connection.close();
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    @PUT
    @Path("{username}")
    @Produces("text/plain")
    @Consumes("application/xml")
    public String updateUser(@PathParam("username")String username, User user) {
        Connection connection = null;
        try {
            connection = LIMSInitializationListener.getDataSource().getConnection();

            String query = "UPDATE " + LimsDatabaseConstants.USERS_TABLE_NAME + " " +
                           "SET " + LimsDatabaseConstants.USERNAME_COLUMN_NAME_USERS_TABLE + "=?, " +
                                    LimsDatabaseConstants.PASSWORD_COLUMN_NAME_USERS_TABLE + "=?, " +
                                    LimsDatabaseConstants.FIRSTNAME_COLUMN_NAME_USERS_TABLE + "=?, " +
                                    LimsDatabaseConstants.LASTNAME_COLUMN_NAME_USERS_TABLE + "=?, " +
                                    LimsDatabaseConstants.EMAIL_COLUMN_NAME_USERS_TABLE + "=?, "  +
                                    LimsDatabaseConstants.ENABLED_COLUMN_NAME_USERS_TABLE + "=? " +
                           "WHERE " + LimsDatabaseConstants.USERNAME_COLUMN_NAME_USERS_TABLE + "=?";

            PreparedStatement statement = connection.prepareStatement(query);

            statement.setObject(1, user.username);
            statement.setObject(2, user.password);
            statement.setObject(3, user.firstname);
            statement.setObject(4, user.lastname);
            statement.setObject(5, user.email);
            statement.setObject(6, user.enabled);
            statement.setObject(7, username);

            if (statement.executeUpdate() > 0) {
                return "User account '" + username + "' updated.";
            } else {
                return "Failed to update user account '" + username + "'.";
            }
        } catch (SQLException e) {
            throw new InternalServerErrorException("Failed to update user account '" + username + "'.", e);
        } finally {
            if (connection != null) {
                try {
                    connection.close();
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    @DELETE
    @Path("{username}")
    @Produces("text/plain")
    public String deleteUser(@PathParam("username")String username) {
        Connection connection = null;
        try {
            if (!User.getLoggedInUser().isAdmin()) {
                throw new ForbiddenException("Action denied: insufficient permissions.");
            }

            connection = LIMSInitializationListener.getDataSource().getConnection();

            String query = "DELETE FROM " + LimsDatabaseConstants.USERS_TABLE_NAME + " " +
                           "WHERE " + LimsDatabaseConstants.USERNAME_COLUMN_NAME_USERS_TABLE + "=?";

            PreparedStatement statement = connection.prepareStatement(query);

            statement.setObject(1, username);

            if (statement.executeUpdate() > 0) {
                return "User account '" + username + "' deleted.";
            } else {
                return "Failed to delete user account '" + username + "'.";
            }
        } catch (SQLException e) {
            throw new InternalServerErrorException("Failed to delete user account '" + username + "'.", e);
        } finally {
            if (connection != null) {
                try {
                    connection.close();
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
