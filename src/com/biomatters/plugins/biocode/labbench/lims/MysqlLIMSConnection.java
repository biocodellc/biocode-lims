package com.biomatters.plugins.biocode.labbench.lims;

import com.biomatters.geneious.publicapi.plugin.Options;
import com.biomatters.plugins.biocode.BiocodeUtilities;
import com.biomatters.plugins.biocode.labbench.BiocodeService;
import com.biomatters.plugins.biocode.labbench.ConnectionException;
import com.biomatters.plugins.biocode.labbench.PasswordOptions;
import com.biomatters.plugins.biocode.utilities.PasswordOption;
import org.apache.commons.dbcp.BasicDataSource;

import javax.naming.NamingException;
import javax.sql.DataSource;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * @author Steve
 *          <p/>
 *          Created on 17/04/13 2:25 PM
 */


public class MysqlLIMSConnection extends SqlLimsConnection {

    private String username;
    private String schema;

    private String jndi;

    @Override
    public PasswordOptions getConnectionOptions() {
        return new MySqlLIMSConnectionOptions(LIMSConnection.class);
    }

    @Override
    public DataSource connectToDb(Options LIMSOptions) throws ConnectionException {
        jndi = LIMSOptions.getValueAsString("jndi");

        if (jndi != null && jndi.trim().length() > 0) {
            return initWithjndi(jndi);
        } else {
            username = LIMSOptions.getValueAsString("username");
            DriverManager.setLoginTimeout(20);
            serverUrn = LIMSOptions.getValueAsString("server") + ":" + LIMSOptions.getValueAsString("port");
            schema = LIMSOptions.getValueAsString("database");
            String connectionString = "jdbc:mysql://" + serverUrn + "/" + schema;
            serverUrn += "/" + schema;
            String password = ((PasswordOption) LIMSOptions.getOption("password")).getPassword();

            return createBasicDataSource(connectionString, username, password);
        }
    }

    private DataSource initWithjndi(String jndi) throws ConnectionException {
        try {
            DataSource ds = BiocodeUtilities.getDataSourceByJNDI(jndi);
            username = ds.getConnection().getMetaData().getUserName();
            String url = ds.getConnection().getMetaData().getURL();
            int index = url.lastIndexOf("/");
            if (index != -1 && index != url.length() - 1) {
                schema = url.substring(index + 1);
                if (schema.contains(":")) {
                    schema = "";
                }
            }
            return ds;
        } catch (NamingException e) {
            e.printStackTrace();
            throw new ConnectionException(e);
        } catch (SQLException e) {
            e.printStackTrace();
            throw new ConnectionException(e);
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

    @Override
    public synchronized DataSource getDataSource() throws SQLException {
        if (jndi != null && jndi.trim().length() > 0) {
            try {
                return BiocodeUtilities.getDataSourceByJNDI(jndi);
            }  catch (NamingException e) {
                throw new SQLException(e);
            }
        } else {
            return super.getDataSource();
        }
    }
}
