package com.biomatters.plugins.moorea.lims;

import com.biomatters.plugins.moorea.PasswordOption;
import com.biomatters.plugins.moorea.ConnectionException;
import com.biomatters.plugins.moorea.MooreaLabBenchService;
import com.biomatters.geneious.publicapi.plugin.Options;
import com.biomatters.geneious.publicapi.components.Dialogs;

import java.io.FilenameFilter;
import java.io.File;
import java.sql.SQLException;
import java.sql.Driver;
import java.sql.Connection;
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

}
