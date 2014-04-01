package com.biomatters.plugins.biocode.labbench.lims;

import com.biomatters.geneious.publicapi.plugin.Options;
import com.biomatters.plugins.biocode.utilities.PasswordOption;
import com.biomatters.plugins.biocode.labbench.BiocodeService;
import com.biomatters.plugins.biocode.labbench.ConnectionException;
import com.biomatters.plugins.biocode.labbench.PasswordOptions;
import org.apache.commons.dbcp.*;
import org.apache.commons.pool.ObjectPool;
import org.apache.commons.pool.impl.GenericObjectPool;

import java.sql.*;
import java.util.Properties;

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
            String connectionString = "jdbc:mysql://" + serverUrn + "/" + schema;
            setupDriver(connectionString);

            connection = DriverManager.getConnection("jdbc:apache:commons:dbcp:lims", properties);
            connection2 = DriverManager.getConnection("jdbc:apache:commons:dbcp:lims", properties);
            serverUrn += "/"+ schema;
        } catch (SQLException e1) {
            throw new ConnectionException(e1);
        } catch (ClassNotFoundException e) {
            throw new ConnectionException(e);
        }
    }

    public static void setupDriver(String connectionString) throws SQLException, ClassNotFoundException {
        ConnectionFactory connectionFactory = new DriverManagerConnectionFactory(connectionString,null);
        PoolableConnectionFactory poolableConnectionFactory = new PoolableConnectionFactory(connectionFactory, null, null, null, false, true);

        ObjectPool<PoolableConnection> connectionPool =
            new GenericObjectPool<PoolableConnection>(poolableConnectionFactory);

        Class.forName("org.apache.commons.dbcp2.PoolingDriver");
        PoolingDriver driver = (PoolingDriver) DriverManager.getDriver("jdbc:apache:commons:dbcp:");
        driver.registerPool("lims", connectionPool);
    }

    public static void shutdownDriver() throws Exception {
        PoolingDriver driver = (PoolingDriver) DriverManager.getDriver("jdbc:apache:commons:dbcp:");
        driver.closePool("lims");
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
