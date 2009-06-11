package com.biomatters.plugins.moorea.lims;

import com.biomatters.plugins.moorea.*;
import com.biomatters.geneious.publicapi.plugin.Options;

import java.sql.*;
import java.util.Properties;

/**
 * Created by IntelliJ IDEA.
 * User: steve
 * Date: 27/05/2009
 * Time: 6:28:38 AM
 * To change this template use File | Settings | File Templates.
 */
public class LIMSConnection {
    Driver driver;
    Connection connection;

    public Options getConnectionOptions() {
        Options LIMSOptions = new Options(this.getClass());
        LIMSOptions.addStringOption("server", "Server Address", "");
        LIMSOptions.addIntegerOption("port", "Port", 3306, 1, Integer.MAX_VALUE);
        LIMSOptions.addStringOption("database", "Database Name", "labbench");
        LIMSOptions.addStringOption("username", "Username", "");
        LIMSOptions.addCustomOption(new PasswordOption("password", "Password", ""));
        return LIMSOptions;
    }


    public void connect(Options LIMSOptions) throws ConnectionException {
        driver = MooreaLabBenchService.getDriver();
        //connect to the LIMS
        Properties properties = new Properties();
        properties.put("user", LIMSOptions.getValueAsString("username"));
        properties.put("password", ((PasswordOption)LIMSOptions.getOption("password")).getPassword());
        try {
            connection = driver.connect("jdbc:mysql://"+LIMSOptions.getValueAsString("server")+":"+LIMSOptions.getValueAsString("port"), properties);
            Statement statement = connection.createStatement();
            statement.execute("USE labbench");
        } catch (SQLException e1) {
            throw new ConnectionException(e1);
        }
    }

    public void disconnect() throws ConnectionException{
        if(connection != null) {
            try {
                connection.close();
            } catch (SQLException e) {
                throw new ConnectionException(e);
            }
        }
    }

    public ResultSet executeQuery(String sql) throws TransactionException{
        try {
            PreparedStatement statement = connection.prepareStatement(sql);
            return statement.executeQuery();
        }
        catch(SQLException ex) {
            throw new TransactionException("Could not execute LIMS query", ex);
        }
    }

    public void executeUpdate(String sql) throws TransactionException{
        Savepoint savepoint = null;
        try {
            connection.setAutoCommit(false);
            savepoint = connection.setSavepoint();
            for(String s : sql.split("\n")) {
                PreparedStatement statement = connection.prepareStatement(s);
                statement.execute();
            }
            connection.commit();
        }
        catch(SQLException ex) {
            try {
                if(savepoint != null) {
                    connection.rollback(savepoint);
                }
            } catch (SQLException e) {
                throw new TransactionException("Could not execute LIMS update query", ex);
            }
            throw new TransactionException("Could not execute LIMS update query", ex);
        }
        finally {
            try {
                connection.setAutoCommit(true);
            } catch (SQLException ignore) {}
        }
    }

    public Connection getConnection() {
        return connection;
    }

}
