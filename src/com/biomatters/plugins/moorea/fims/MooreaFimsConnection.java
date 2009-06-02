package com.biomatters.plugins.moorea.fims;

import com.biomatters.geneious.publicapi.databaseservice.Query;
import com.biomatters.geneious.publicapi.documents.DocumentField;
import com.biomatters.geneious.publicapi.plugin.Options;
import com.biomatters.plugins.moorea.ConnectionException;
import com.biomatters.plugins.moorea.FimsSample;
import com.biomatters.plugins.moorea.MooreaLabBenchService;

import java.sql.Driver;
import java.sql.SQLException;
import java.util.List;
import java.util.Properties;

/**
 * Created by IntelliJ IDEA.
 * User: steve
 * Date: 12/05/2009
 * Time: 5:51:15 AM
 * To change this template use File | Settings | File Templates.
 */
public class MooreaFimsConnection extends FIMSConnection{

    private Driver driver;

    public String getLabel() {
        return "Moorea FIMS";
    }

    public String getName() {
        return "moorea";
    }

    public String getDescription() {
        return "A connection to the Moorea FIMS database";
    }

    public Options getConnectionOptions() {
        Options options = new Options(this.getClass(), "mooreaFIMS");
        options.addStringOption("serverUrl", "Server", "darwin.berkeley.edu");
        options.addIntegerOption("serverPort", "Port", 3306, 0, Integer.MAX_VALUE);
        options.addStringOption("username", "Username", "");
        options.addStringOption("password", "Password", "");

        return options;
    }

    public void connect(Options options) throws ConnectionException {

        //instantiate the driver class
        try {
            driver = (Driver) MooreaLabBenchService.getDriverClass().newInstance();
        } catch (InstantiationException e) {
            throw new ConnectionException("Could not instantiate SQL driver.");
        } catch (IllegalAccessException e) {
            throw new ConnectionException("Could not access SQL driver.");
        }


        //connect
        Properties properties = new Properties();
        properties.put("user", options.getValueAsString("username"));
        properties.put("password", options.getValueAsString("password"));
        try {
            driver.connect("jdbc:mysql://"+options.getValueAsString("serverUrl")+":"+options.getValueAsString("serverPort"), properties);
        } catch (SQLException e1) {
            throw new ConnectionException("Failed to connect to the LIMS database: "+e1.getMessage());
        }
    }

    public List<DocumentField> getFimsAttributes() {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public List<FimsSample> getMatchingSamples(List<Query> queries) {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }
}
