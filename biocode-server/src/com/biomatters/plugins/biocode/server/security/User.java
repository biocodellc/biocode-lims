package com.biomatters.plugins.biocode.server.security;

import com.biomatters.plugins.biocode.server.LIMSInitializationListener;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;

import javax.ws.rs.InternalServerErrorException;
import javax.xml.bind.annotation.XmlRootElement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * @author Matthew Cheung
 *         Created on 9/06/14 1:32 PM
 */
@XmlRootElement
public class User {
    public String username;
    public String password;
    public String firstname;
    public String lastname;
    public String email;
    public boolean enabled;

    public User(String username, String password, String firstname, String lastname, String email, boolean enabled) {
        this.username = username;
        this.password = password;
        this.firstname = firstname;
        this.lastname = lastname;
        this.email = email;
        this.enabled = enabled;
    }

    public User() {
    }

    /**
     * @return The current logged in {@link com.biomatters.plugins.biocode.server.security.User}
     */
    public static User getLoggedInUser() throws InternalServerErrorException {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        UserDetails user = (UserDetails) principal;

        Connection connection = null;

        if (principal instanceof UserDetails) {
            try {
                connection = new LIMSInitializationListener().getDataSource().getConnection();

                String query = "SELECT " + LimsDatabaseConstants.USERNAME_COLUMN_NAME_USERS_TABLE + ", " +
                                           LimsDatabaseConstants.PASSWORD_COLUMN_NAME_USERS_TABLE + ", " +
                                           LimsDatabaseConstants.FIRSTNAME_COLUMN_NAME_USERS_TABLE + ", " +
                                           LimsDatabaseConstants.LASTNAME_COLUMN_NAME_USERS_TABLE + ", " +
                                           LimsDatabaseConstants.EMAIL_COLUMN_NAME_USERS_TABLE + ", " +
                                           LimsDatabaseConstants.ENABLED_COLUMN_NAME_USERS_TABLE + " " +
                               "FROM " + LimsDatabaseConstants.USERS_TABLE_NAME + " " +
                               "WHERE " + LimsDatabaseConstants.USERNAME_COLUMN_NAME_USERS_TABLE + "=?";

                PreparedStatement statement = connection.prepareStatement(query);

                statement.setObject(1, user.getUsername());

                ResultSet resultSet = statement.executeQuery();

                if (!resultSet.next()) {
                    throw new InternalServerErrorException("Logged in user not found in database.");
                }

                return new User(resultSet.getString(LimsDatabaseConstants.USERNAME_COLUMN_NAME_USERS_TABLE),
                                resultSet.getString(LimsDatabaseConstants.PASSWORD_COLUMN_NAME_USERS_TABLE),
                                resultSet.getString(LimsDatabaseConstants.FIRSTNAME_COLUMN_NAME_USERS_TABLE),
                                resultSet.getString(LimsDatabaseConstants.LASTNAME_COLUMN_NAME_USERS_TABLE),
                                resultSet.getString(LimsDatabaseConstants.EMAIL_COLUMN_NAME_USERS_TABLE),
                                resultSet.getBoolean(LimsDatabaseConstants.ENABLED_COLUMN_NAME_USERS_TABLE));
            } catch (SQLException e) {
                throw new InternalServerErrorException("Error ", e);
            } finally {
                if (connection != null) {
                    try {
                        connection.close();
                    } catch (SQLException e) {
                        e.printStackTrace();
                    }
                }
            }
        } else {
            return null;
        }
    }

    public boolean isAdmin() throws InternalServerErrorException {
        Connection connection = null;
        try {
            connection = new LIMSInitializationListener().getDataSource().getConnection();

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
