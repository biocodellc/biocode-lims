package com.biomatters.plugins.biocode.labbench.lims;

import com.biomatters.geneious.publicapi.plugin.Options;
import com.biomatters.plugins.biocode.utilities.PasswordOption;
import com.biomatters.plugins.biocode.labbench.BiocodeService;
import com.biomatters.plugins.biocode.labbench.ConnectionException;
import com.biomatters.plugins.biocode.labbench.PasswordOptions;
import org.apache.commons.dbcp.*;

import javax.sql.DataSource;
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
    public DataSource connectToDb(Options LIMSOptions) throws ConnectionException {
        username = LIMSOptions.getValueAsString("username");
        DriverManager.setLoginTimeout(20);
        serverUrn = LIMSOptions.getValueAsString("server") + ":" + LIMSOptions.getValueAsString("port");
        schema = LIMSOptions.getValueAsString("database");
        String connectionString = "jdbc:mysql://" + serverUrn + "/" + schema;
        serverUrn += "/"+ schema;
        String password = ((PasswordOption) LIMSOptions.getOption("password")).getPassword();
        return createBasicDataSource(connectionString, BiocodeService.getInstance().getDriver(), username, password);
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
