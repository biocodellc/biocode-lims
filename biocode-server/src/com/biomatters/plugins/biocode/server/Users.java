package com.biomatters.plugins.biocode.server;

import com.biomatters.plugins.biocode.server.security.LimsDatabaseConstants;
import com.biomatters.plugins.biocode.server.security.User;
import com.biomatters.plugins.biocode.utilities.SqlUtilities;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
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

    /**
     * @return The current logged in {@link com.biomatters.plugins.biocode.server.security.User}
     */
    public static User getLoggedInUser() throws InternalServerErrorException {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof UserDetails) {
            UserDetails user = (UserDetails) principal;
            return getUserForUsername(user.getUsername());
        } else {
            return null;
        }
    }

    @GET
    @Produces({"application/xml", "application/json"})
    public Response list() {
        Connection connection = null;
        try {
            connection = LIMSInitializationListener.getDataSource().getConnection();

            List<User> users = getUserList(connection);

            return Response.ok(new GenericEntity<List<User>>(users){}).build();
        } catch (SQLException e) {
            throw new InternalServerErrorException("Failed to list users", e);
        } finally {
            SqlUtilities.closeConnection(connection);
        }
    }

    public static List<User> getUserList(Connection connection) throws SQLException {
        String query = "SELECT " + LimsDatabaseConstants.USERS_TABLE_NAME + "." +
                                   LimsDatabaseConstants.USERNAME_COLUMN_NAME_USERS_TABLE + ", " +
                                   LimsDatabaseConstants.PASSWORD_COLUMN_NAME_USERS_TABLE + ", " +
                                   LimsDatabaseConstants.FIRSTNAME_COLUMN_NAME_USERS_TABLE + ", " +
                                   LimsDatabaseConstants.LASTNAME_COLUMN_NAME_USERS_TABLE + ", " +
                                   LimsDatabaseConstants.EMAIL_COLUMN_NAME_USERS_TABLE + ", "  +
                                   LimsDatabaseConstants.ENABLED_COLUMN_NAME_USERS_TABLE + ", " +
                                   LimsDatabaseConstants.AUTHORITY_COLUMN_NAME_AUTHORITIES_TABLE + " " +
                       "FROM " + getUserTableJoinedWithAuthTable();

        PreparedStatement statement = connection.prepareStatement(query);

        ResultSet resultSet = statement.executeQuery();

        List<User> users = new ArrayList<User>();
        while(resultSet.next()) {
            users.add(createUserFromResultSetRow(resultSet));
        }

        resultSet.close();
        return users;
    }

    @GET
    @Produces("application/xml")
    @Path("{username}")
    public User getUser(@PathParam("username")String username) {
        return getUserForUsername(username);
    }

    private static User getUserForUsername(String username) {
        Connection connection = null;
        try {
            connection = LIMSInitializationListener.getDataSource().getConnection();

            String usernameUserTable = LimsDatabaseConstants.USERS_TABLE_NAME + "." +
                    LimsDatabaseConstants.USERNAME_COLUMN_NAME_USERS_TABLE;
            String query = "SELECT " +
                    usernameUserTable + ", " +
                    LimsDatabaseConstants.PASSWORD_COLUMN_NAME_USERS_TABLE + ", " +
                    LimsDatabaseConstants.FIRSTNAME_COLUMN_NAME_USERS_TABLE + ", " +
                    LimsDatabaseConstants.LASTNAME_COLUMN_NAME_USERS_TABLE + ", " +
                    LimsDatabaseConstants.EMAIL_COLUMN_NAME_USERS_TABLE + ", " +
                    LimsDatabaseConstants.ENABLED_COLUMN_NAME_USERS_TABLE + ", " +
                    LimsDatabaseConstants.AUTHORITY_COLUMN_NAME_AUTHORITIES_TABLE + " " +
                    "FROM " + getUserTableJoinedWithAuthTable() + " " +
                    "WHERE " + usernameUserTable + "=?";

            PreparedStatement statement = connection.prepareStatement(query);

            statement.setObject(1, username);

            ResultSet resultSet = statement.executeQuery();

            if (!resultSet.next()) {
                throw new NotFoundException("User account '" + username + "' not found.");
            }

            return createUserFromResultSetRow(resultSet);
        } catch (SQLException e) {
            throw new InternalServerErrorException("Failed to retrieve user account '" + username + "'", e);
        } finally {
            SqlUtilities.closeConnection(connection);
        }
    }

    private static User createUserFromResultSetRow(ResultSet resultSet) throws SQLException {
        String authority = resultSet.getString(LimsDatabaseConstants.AUTHORITY_COLUMN_NAME_AUTHORITIES_TABLE);
        return new User(resultSet.getString(LimsDatabaseConstants.USERNAME_COLUMN_NAME_USERS_TABLE),
                        null,
                        resultSet.getString(LimsDatabaseConstants.FIRSTNAME_COLUMN_NAME_USERS_TABLE),
                        resultSet.getString(LimsDatabaseConstants.LASTNAME_COLUMN_NAME_USERS_TABLE),
                        resultSet.getString(LimsDatabaseConstants.EMAIL_COLUMN_NAME_USERS_TABLE),
                        resultSet.getBoolean(LimsDatabaseConstants.ENABLED_COLUMN_NAME_USERS_TABLE),
                        LimsDatabaseConstants.AUTHORITY_ADMIN_CODE.equals(authority)
                );
    }

    private static String getUserTableJoinedWithAuthTable() {
        return LimsDatabaseConstants.USERS_TABLE_NAME + " INNER JOIN " +
                LimsDatabaseConstants.AUTHORITIES_TABLE_NAME + " ON " +
                LimsDatabaseConstants.USERS_TABLE_NAME + "." +
                LimsDatabaseConstants.USERNAME_COLUMN_NAME_USERS_TABLE + " = " +
                LimsDatabaseConstants.AUTHORITIES_TABLE_NAME + "." +
                LimsDatabaseConstants.USERNAME_COLUMN_NAME_USERS_TABLE;
    }

    @POST
    @Produces("text/plain")
    @Consumes("application/xml")
    public String addUser(User user) {
        Connection connection = null;
        try {
            connection = LIMSInitializationListener.getDataSource().getConnection();
            SqlUtilities.beginTransaction(connection);

            String query = "INSERT INTO " + LimsDatabaseConstants.USERS_TABLE_NAME + " VALUES (?, ?, ?, ?, ?, ?)";

            PreparedStatement statement = connection.prepareStatement(query);

            statement.setObject(1, user.username);
            statement.setObject(2, encoder.encode(user.password));
            statement.setObject(3, user.firstname);
            statement.setObject(4, user.lastname);
            statement.setObject(5, user.email);
            statement.setObject(6, true);

            int inserted = statement.executeUpdate();
            if (inserted == 1) {
                PreparedStatement insertAuth = connection.prepareStatement("INSERT INTO " + LimsDatabaseConstants.AUTHORITIES_TABLE_NAME + " VALUES (?,?)");
                insertAuth.setObject(1, user.username);
                insertAuth.setObject(2, user.isAdministrator ?
                        LimsDatabaseConstants.AUTHORITY_ADMIN_CODE :
                        LimsDatabaseConstants.AUTHORITY_WRITER_CODE);
                int insertedAuth = insertAuth.executeUpdate();
                if(insertedAuth == 1) {
                    SqlUtilities.commitTransaction(connection);
                    return "User added.";
                } else {
                    throw new InternalServerErrorException("Failed to add user.  Inserted " + insertedAuth + " rows into database, expected 1.");
                }
            } else {
                throw new InternalServerErrorException("Failed to add user.  Inserted " + inserted + " rows into database, expected 1.");
            }
        } catch (SQLException e) {
            throw new InternalServerErrorException("Failed to add user.", e);
        } finally {
            SqlUtilities.closeConnection(connection);
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
            SqlUtilities.beginTransaction(connection);

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

            int updated = statement.executeUpdate();
            if (updated == 1) {
                PreparedStatement updateAuth = connection.prepareStatement(
                        "UPDATE " + LimsDatabaseConstants.AUTHORITIES_TABLE_NAME + " SET " +
                                LimsDatabaseConstants.AUTHORITY_COLUMN_NAME_AUTHORITIES_TABLE + " = ? " +
                                "WHERE " + LimsDatabaseConstants.USERNAME_COLUMN_NAME_AUTHORITIES_TABLE + "=?");
                updateAuth.setObject(1, user.isAdministrator ?
                        LimsDatabaseConstants.AUTHORITY_ADMIN_CODE :
                        LimsDatabaseConstants.AUTHORITY_WRITER_CODE);
                updateAuth.setObject(2, user.username);
                int updatedAuth = updateAuth.executeUpdate();
                if(updatedAuth == 1) {
                    SqlUtilities.commitTransaction(connection);
                    return "User account '" + username + "' updated.";
                } else {
                    throw new InternalServerErrorException("Failed to update user account '" + username + "'. Updated " + updatedAuth + " rows in database, expected 1.");
                }
            } else {
                throw new InternalServerErrorException("Failed to update user account '" + username + "'. Updated " + updated + " rows in database, expected 1.");
            }
        } catch (SQLException e) {
            throw new InternalServerErrorException("Failed to update user account '" + username + "'.", e);
        } finally {
            SqlUtilities.closeConnection(connection);
        }
    }

    @DELETE
    @Path("{username}")
    @Produces("text/plain")
    public String deleteUser(@PathParam("username")String username) {
        Connection connection = null;
        try {
            if (!isAdmin(getLoggedInUser())) {
                throw new ForbiddenException("Action denied: insufficient permissions.");
            }

            connection = LIMSInitializationListener.getDataSource().getConnection();
            SqlUtilities.beginTransaction(connection);

            String query = "DELETE FROM " + LimsDatabaseConstants.USERS_TABLE_NAME + " " +
                           "WHERE " + LimsDatabaseConstants.USERNAME_COLUMN_NAME_USERS_TABLE + "=?";

            PreparedStatement statement = connection.prepareStatement(query);

            statement.setObject(1, username);

            int deleted = statement.executeUpdate();
            if (deleted == 1) {
                SqlUtilities.commitTransaction(connection);
                return "User account '" + username + "' deleted.";
            } else {
                throw new InternalServerErrorException("Failed to delete user account '" + username + "'. Deleted " + deleted + " rows from database, but expected 1." );
            }
        } catch (SQLException e) {
            throw new InternalServerErrorException("Failed to delete user account '" + username + "'.", e);
        } finally {
            SqlUtilities.closeConnection(connection);
        }
    }

    public boolean isAdmin(User user) throws InternalServerErrorException {
        String username = user.username;
        Connection connection = null;
        try {
            connection = LIMSInitializationListener.getDataSource().getConnection();

            String query = "SELECT " + LimsDatabaseConstants.AUTHORITY_COLUMN_NAME_AUTHORITIES_TABLE + " " +
                           "FROM " + LimsDatabaseConstants.AUTHORITIES_TABLE_NAME + " " +
                           "WHERE " + LimsDatabaseConstants.USERNAME_COLUMN_NAME_AUTHORITIES_TABLE + "=?";

            PreparedStatement statement = connection.prepareStatement(query);

            statement.setObject(1, username);

            ResultSet resultSet = statement.executeQuery();

            if (!resultSet.next()) {
                throw new InternalServerErrorException("No authority associated with user account '" + username + "'.");
            }

            if (resultSet.getString("authority").equals(LimsDatabaseConstants.AUTHORITY_ADMIN_CODE)) {
                return true;
            } else {
                return false;
            }
        } catch (SQLException e) {
            throw new InternalServerErrorException("Error verifying account " + username + " authority.", e);
        } finally {
            SqlUtilities.closeConnection(connection);
        }
    }
}
