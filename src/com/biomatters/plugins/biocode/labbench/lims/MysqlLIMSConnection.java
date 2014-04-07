package com.biomatters.plugins.biocode.labbench.lims;

import com.biomatters.geneious.publicapi.plugin.Options;
import com.biomatters.plugins.biocode.utilities.PasswordOption;
import com.biomatters.plugins.biocode.labbench.BiocodeService;
import com.biomatters.plugins.biocode.labbench.ConnectionException;
import com.biomatters.plugins.biocode.labbench.PasswordOptions;
import org.apache.commons.dbcp.*;

import java.sql.*;

/**
 * @author Steve
 * @version $Id$
 *          <p/>
 *          Created on 17/04/13 2:25 PM
 */


public class MysqlLIMSConnection extends SqlLimsConnection {

    private String username;
    private String schema;

    @Override
    public PasswordOptions getConnectionOptions() {
        return new MySqlLIMSConnectionOptions(LIMSConnection.class);
    }

    @Override
    public BasicDataSource connectToDb(Options LIMSOptions) throws ConnectionException {
        BasicDataSource dataSource = new BasicDataSource();
        Driver driver = BiocodeService.getInstance().getDriver();
        dataSource.setDriverClassName(driver.getClass().getName());
        username = LIMSOptions.getValueAsString("username");
        dataSource.setUsername(username);
        dataSource.setPassword(((PasswordOption)LIMSOptions.getOption("password")).getPassword());
        DriverManager.setLoginTimeout(20);
        serverUrn = LIMSOptions.getValueAsString("server") + ":" + LIMSOptions.getValueAsString("port");
        schema = LIMSOptions.getValueAsString("database");
        dataSource.setUrl("jdbc:mysql://" + serverUrn + "/" + schema);
        serverUrn += "/"+ schema;
        return dataSource;
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
