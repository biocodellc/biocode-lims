package com.biomatters.plugins.biocode.labbench.lims;

import com.biomatters.geneious.publicapi.plugin.Options;
import com.biomatters.options.PasswordOption;
import com.biomatters.plugins.biocode.labbench.BiocodeService;
import com.biomatters.plugins.biocode.labbench.ConnectionException;
import com.biomatters.plugins.biocode.labbench.PasswordOptions;

import java.sql.*;
import java.util.Properties;

/**
 * @author Steve
 * @version $Id$
 *          <p/>
 *          Created on 17/04/13 2:25 PM
 */


public class MysqlLIMSConnection extends LIMSConnection {

    private String username;
    private String schema;

    @Override
    public Driver getDriver() throws ConnectionException {
        return BiocodeService.getInstance().getDriver();
    }

    @Override
    public PasswordOptions getConnectionOptions() {
        return new MySqlLIMSConnectionOptions(LIMSConnection.class);
    }

    @Override
    public boolean requiresMySql() {
        return true;
    }

    @Override
    public void connectToDb(Options LIMSOptions) throws ConnectionException {
        //connect to the LIMS
        Properties properties = new Properties();
        username = LIMSOptions.getValueAsString("username");
        properties.put("user", username);
        properties.put("password", ((PasswordOption)LIMSOptions.getOption("password")).getPassword());
        try {
            DriverManager.setLoginTimeout(20);
            serverUrn = LIMSOptions.getValueAsString("server") + ":" + LIMSOptions.getValueAsString("port");
            schema = LIMSOptions.getValueAsString("database");
            connection = driver.connect("jdbc:mysql://" + serverUrn + "/" + schema, properties);
            connection2 = driver.connect("jdbc:mysql://"+ serverUrn + "/" + schema, properties);
            serverUrn += "/"+ schema;
        } catch (SQLException e1) {
            throw new ConnectionException(e1);
        }
    }

    @Override
    protected boolean canUpgradeDatabase() {
        return false;
    }

    @Override
    public boolean isLocal() {
        return false;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public String getSchema() {
        return schema;
    }
}
