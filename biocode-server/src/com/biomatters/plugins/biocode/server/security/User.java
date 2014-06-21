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
    public String userName;
    private String firstName;
    private String lastName;
    private boolean enabled;
    private String email;

    public User(String userName, String firstName, String lastName, boolean enabled, String email) {
        this.userName = userName;
        this.firstName = firstName;
        this.lastName = lastName;
        this.enabled = enabled;
        this.email = email;
    }

    public User() {
    }

    /**
     * @return The current logged in {@link com.biomatters.plugins.biocode.server.security.User}
     */
    public static User getLoggedInUser() throws SQLException, InternalServerErrorException {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        UserDetails user = (UserDetails) principal;

        if (principal instanceof UserDetails) {
            Connection connection = new LIMSInitializationListener().getDataSource().getConnection();

            String query = "SELECT " + LimsDatabaseConstants.USERNAME_COLUMN_NAME_USERS_TABLE + ", " +
                                       LimsDatabaseConstants.FIRSTNAME_COLUMN_NAME_USERS_TABLE + ", " +
                                       LimsDatabaseConstants.LASTNAME_COLUMN_NAME_USERS_TABLE + ", " +
                                       LimsDatabaseConstants.ENABLED_COLUMN_NAME_USERS_TABLE + ", " +
                                       LimsDatabaseConstants.EMAIL_COLUMN_NAME_USERS_TABLE +
                           "FROM " + LimsDatabaseConstants.USERS_TABLE_NAME + " " +
                           "WHERE " + LimsDatabaseConstants.USERS_TABLE_NAME + "." + LimsDatabaseConstants.USERNAME_COLUMN_NAME_USERS_TABLE + "=?";

            PreparedStatement statement = connection.prepareStatement(query);

            statement.setObject(1, user.getUsername());

            ResultSet resultSet = statement.executeQuery();

            if (!resultSet.next()) {
                throw new InternalServerErrorException("Logged in user not found in database.");
            }

            return new User(resultSet.getString(LimsDatabaseConstants.USERNAME_COLUMN_NAME_USERS_TABLE),
                            resultSet.getString(LimsDatabaseConstants.FIRSTNAME_COLUMN_NAME_USERS_TABLE),
                            resultSet.getString(LimsDatabaseConstants.LASTNAME_COLUMN_NAME_USERS_TABLE),
                            resultSet.getBoolean(LimsDatabaseConstants.ENABLED_COLUMN_NAME_USERS_TABLE),
                            resultSet.getString(LimsDatabaseConstants.EMAIL_COLUMN_NAME_USERS_TABLE));
        } else {
            return null;
        }
    }

    public boolean isAdmin() throws SQLException {
        Connection connection = new LIMSInitializationListener().getDataSource().getConnection();

        String query = "SELECT " + LimsDatabaseConstants.AUTHORITY_COLUMN_NAME_AUTHORITIES_TABLE + " " +
                       "FROM " + LimsDatabaseConstants.AUTHORITIES_TABLE_NAME + " " +
                       "WHERE " + LimsDatabaseConstants.AUTHORITIES_TABLE_NAME + "." + LimsDatabaseConstants.USERNAME_COLUMN_NAME_AUTHORITIES_TABLE + "=" + userName;

        PreparedStatement statement = connection.prepareStatement(query);

        ResultSet resultSet = statement.executeQuery();

        if (!resultSet.next()) {
            throw new InternalServerErrorException("No authority associated with user account '" + userName + "'.");
        }

        if (resultSet.getString("authority").equals("admin")) {
            return true;
        } else {
            return false;
        }
    }

    public String getUserName() { return userName; }

    public String getFirstName() { return firstName; }

    public String getLastName() { return lastName; }

    public boolean getEnabled() { return enabled; }

    public String getEmail() { return email; }
}
