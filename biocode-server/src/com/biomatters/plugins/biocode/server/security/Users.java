package com.biomatters.plugins.biocode.server.security;

import com.biomatters.plugins.biocode.server.LIMSInitializationListener;
import com.biomatters.plugins.biocode.utilities.SqlUtilities;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import javax.sql.DataSource;
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
    private static BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

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
    @Produces({"application/json;qs=1", "application/xml;qs=0.5"})
    public Response list() {
        Connection connection = null;
        try {
            connection = LIMSInitializationListener.getDataSource().getConnection();

            List<User> users = getUserList(connection);

            return Response.ok(new GenericEntity<List<User>>(users){}).build();
        } catch (SQLException e) {
            throw new InternalServerErrorException("Failed to list users.", e);
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
                       "FROM "   + getUserTableJoinedWithAuthTable();

        PreparedStatement statement = connection.prepareStatement(query);

        ResultSet resultSet = statement.executeQuery();

        List<User> users = new ArrayList<User>();

        while (resultSet.next()) {
            User user = createUserFromResultSetRow(resultSet);
            if(user != null) {
                users.add(user);
            }
        }

        resultSet.close();

        return users;
    }

    @GET
    @Produces({"application/json;qs=1", "application/xml;qs=0.5"})
    @Path("{username}")
    public User getUser(@PathParam("username")String username) {
        User user = getUserForUsername(username);
        if(user == null) {
            throw new NotFoundException("No user for " + username);
        }
        return user;
    }

    private static User getUserForUsername(String username) {
        Connection connection = null;
        try {
            connection = LIMSInitializationListener.getDataSource().getConnection();

            String usernameUserTable = LimsDatabaseConstants.USERS_TABLE_NAME + "." + LimsDatabaseConstants.USERNAME_COLUMN_NAME_USERS_TABLE;

            String query = "SELECT " + usernameUserTable + ", " +
                                       LimsDatabaseConstants.PASSWORD_COLUMN_NAME_USERS_TABLE + ", " +
                                       LimsDatabaseConstants.FIRSTNAME_COLUMN_NAME_USERS_TABLE + ", " +
                                       LimsDatabaseConstants.LASTNAME_COLUMN_NAME_USERS_TABLE + ", " +
                                       LimsDatabaseConstants.EMAIL_COLUMN_NAME_USERS_TABLE + ", " +
                                       LimsDatabaseConstants.ENABLED_COLUMN_NAME_USERS_TABLE + ", " +
                                       LimsDatabaseConstants.AUTHORITY_COLUMN_NAME_AUTHORITIES_TABLE + " " +
                           "FROM "   + getUserTableJoinedWithAuthTable() + " " +
                           "WHERE "  + usernameUserTable + "=?";

            PreparedStatement statement = connection.prepareStatement(query);

            statement.setObject(1, username);

            ResultSet resultSet = statement.executeQuery();

            if (!resultSet.next()) {
                throw new NotFoundException("User account '" + username + "' not found.");
            }

            return createUserFromResultSetRow(resultSet);
        } catch (SQLException e) {
            throw new InternalServerErrorException("Failed to retrieve user account '" + username + "'.", e);
        } finally {
            SqlUtilities.closeConnection(connection);
        }
    }

    /**
     *
     * @param resultSet The resultSet to create a user from.
     * @return A user representing the current row in the result set.  Or null if there is no user account.
     * @throws SQLException if a problem occurs communicating with the database
     */
    static User createUserFromResultSetRow(ResultSet resultSet) throws SQLException {
        String username = resultSet.getString(LimsDatabaseConstants.USERNAME_COLUMN_NAME_USERS_TABLE);
        if(username == null) {
            return null;
        }
        String authority = resultSet.getString(LimsDatabaseConstants.AUTHORITY_COLUMN_NAME_AUTHORITIES_TABLE);
        return new User(username,
                        null,
                        resultSet.getString(LimsDatabaseConstants.FIRSTNAME_COLUMN_NAME_USERS_TABLE),
                        resultSet.getString(LimsDatabaseConstants.LASTNAME_COLUMN_NAME_USERS_TABLE),
                        resultSet.getString(LimsDatabaseConstants.EMAIL_COLUMN_NAME_USERS_TABLE),
                        resultSet.getBoolean(LimsDatabaseConstants.ENABLED_COLUMN_NAME_USERS_TABLE),
                        LimsDatabaseConstants.AUTHORITY_ADMIN_CODE.equals(authority));
    }

    private static String getUserTableJoinedWithAuthTable() {
        return LimsDatabaseConstants.USERS_TABLE_NAME + " INNER JOIN " + LimsDatabaseConstants.AUTHORITIES_TABLE_NAME +
               " ON " +
               LimsDatabaseConstants.USERS_TABLE_NAME + "." + LimsDatabaseConstants.USERNAME_COLUMN_NAME_USERS_TABLE +
               "=" +
               LimsDatabaseConstants.AUTHORITIES_TABLE_NAME + "." + LimsDatabaseConstants.USERNAME_COLUMN_NAME_USERS_TABLE;
    }

    @POST
    @Produces("text/plain")
    @Consumes({"application/json", "application/xml"})
    public String addUser(User user) {
        return addUser(LIMSInitializationListener.getDataSource(), user);
    }

    static String addUser(DataSource dataSource, User user) {
        Connection connection = null;
        try {
            connection = dataSource.getConnection();

            SqlUtilities.beginTransaction(connection);

            String addUserQuery = "INSERT INTO " + LimsDatabaseConstants.USERS_TABLE_NAME + " VALUES (?, ?, ?, ?, ?, ?)";

            PreparedStatement statement = connection.prepareStatement(addUserQuery);

            statement.setObject(1, user.username);
            statement.setObject(2, encoder.encode(user.password));
            statement.setObject(3, user.firstname);
            statement.setObject(4, user.lastname);
            statement.setObject(5, user.email);
            statement.setObject(6, true);

            int inserted = statement.executeUpdate();

            if (inserted == 1) {
                String addAuthorityQuery = "INSERT INTO " + LimsDatabaseConstants.AUTHORITIES_TABLE_NAME + " VALUES (?,?)";

                PreparedStatement insertAuth = connection.prepareStatement(addAuthorityQuery);

                insertAuth.setObject(1, user.username);
                insertAuth.setObject(2, user.isAdministrator ? LimsDatabaseConstants.AUTHORITY_ADMIN_CODE : LimsDatabaseConstants.AUTHORITY_WRITER_CODE);

                int insertedAuth = insertAuth.executeUpdate();

                if(insertedAuth == 1) {
                    SqlUtilities.commitTransaction(connection);
                    return "User added.";
                } else {
                    throw new InternalServerErrorException("Failed to add user account. " +
                                                           "Rows inserted into authorities database, expected: 1, actual: " + insertedAuth);
                }
            } else {
                throw new InternalServerErrorException("Failed to add user account. " +
                                                       "Rows inserted into users database, expected: 1, actual: " + inserted);
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
    @Consumes({"application/json", "application/xml"})
    public String updateUser(@PathParam("username")String username, User user) {
        Connection connection = null;
        try {
            connection = LIMSInitializationListener.getDataSource().getConnection();

            SqlUtilities.beginTransaction(connection);

            String updateUserQuery = "UPDATE " + LimsDatabaseConstants.USERS_TABLE_NAME + " " +
                                     "SET "    + LimsDatabaseConstants.USERNAME_COLUMN_NAME_USERS_TABLE + "=?, " +
                                                 (user.password == null ? "" : LimsDatabaseConstants.PASSWORD_COLUMN_NAME_USERS_TABLE + "=?, ") +
                                                 LimsDatabaseConstants.FIRSTNAME_COLUMN_NAME_USERS_TABLE + "=?, " +
                                                 LimsDatabaseConstants.LASTNAME_COLUMN_NAME_USERS_TABLE + "=?, " +
                                                 LimsDatabaseConstants.EMAIL_COLUMN_NAME_USERS_TABLE + "=?, "  +
                                                 LimsDatabaseConstants.ENABLED_COLUMN_NAME_USERS_TABLE + "=? " +
                                     "WHERE " +  LimsDatabaseConstants.USERNAME_COLUMN_NAME_USERS_TABLE + "=?";

            PreparedStatement statement = connection.prepareStatement(updateUserQuery);

            int i = 1;
            statement.setObject(i++, user.username);
            if (user.password != null)
                statement.setObject(i++, encoder.encode(user.password));
            statement.setObject(i++, user.firstname);
            statement.setObject(i++, user.lastname);
            statement.setObject(i++, user.email);
            statement.setObject(i++, user.enabled);
            statement.setObject(i++, username);

            int updated = statement.executeUpdate();

            if (updated == 1) {
                String updateAuthorityQuery = "UPDATE " + LimsDatabaseConstants.AUTHORITIES_TABLE_NAME + " " +
                                              "SET "    + LimsDatabaseConstants.AUTHORITY_COLUMN_NAME_AUTHORITIES_TABLE + "=? " +
                                              "WHERE "  + LimsDatabaseConstants.USERNAME_COLUMN_NAME_AUTHORITIES_TABLE + "=?";

                PreparedStatement updateAuth = connection.prepareStatement(updateAuthorityQuery);
                updateAuth.setObject(1, user.isAdministrator ?
                        LimsDatabaseConstants.AUTHORITY_ADMIN_CODE :
                        LimsDatabaseConstants.AUTHORITY_WRITER_CODE);
                updateAuth.setObject(2, user.username);
                int updatedAuth = updateAuth.executeUpdate();
                if(updatedAuth == 1) {
                    SqlUtilities.commitTransaction(connection);
                    return "User account '" + username + "' updated.";
                } else {
                    throw new InternalServerErrorException("Failed to update user account '" + username + "'. " +
                                                           "Rows updated in authorities database, expected: 1, actual: " + updatedAuth);
                }
            } else {
                throw new InternalServerErrorException("Failed to update user account '" + username + "'. " +
                                                       "Rows updated in users database, expected: 1, actual: " + updated);
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
                throw new InternalServerErrorException("Failed to delete user account '" + username + "'. " +
                                                       "Rows deleted from users database, expected: 1, actual: " + deleted);
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
            throw new InternalServerErrorException("Error verifying authority of user account '" + username + "'", e);
        } finally {
            SqlUtilities.closeConnection(connection);
        }
    }
}
