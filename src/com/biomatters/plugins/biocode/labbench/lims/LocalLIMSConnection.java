package com.biomatters.plugins.biocode.labbench.lims;

import com.biomatters.geneious.publicapi.plugin.Options;
import com.biomatters.plugins.biocode.BiocodeUtilities;
import com.biomatters.plugins.biocode.labbench.BiocodeService;
import com.biomatters.plugins.biocode.labbench.ConnectionException;
import com.biomatters.plugins.biocode.labbench.PasswordOptions;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.sql.*;

/**
 * @author Steve
 * @version $Id$
 *          <p/>
 *          Created on 17/04/13 2:27 PM
 */


public class LocalLIMSConnection extends SqlLimsConnection {


    @SuppressWarnings("UnusedDeclaration")
    public LocalLIMSConnection() {

    }

    /**
     * creates a new LIMSConnection connected to the given local LIMS database
     * @param localDatabaseName
     * @throws ConnectionException
     */
    public LocalLIMSConnection(String localDatabaseName) throws ConnectionException{
        connect(localDatabaseName);
    }

    @Override
    public PasswordOptions getConnectionOptions() {
        return new LocalLIMSConnectionOptions(LIMSConnection.class);
    }

    @Override
    public Driver getDriver() throws ConnectionException {
        return BiocodeService.getInstance().getLocalDriver();
    }

    @Override
    public void connectToDb(Options connectionOptions) throws ConnectionException {
        String dbName = connectionOptions.getValueAsString("database");
        connect(dbName);
    }

    private void connect(String dbName) throws ConnectionException {
        connection = getDbConnection(dbName);
        serverUrn = "local/"+dbName;
    }

    static String getDbPath(String newDbName) throws IOException {
        return BiocodeService.getInstance().getDataDirectory().getCanonicalPath() + File.separator + newDbName + ".db";
    }

    @Override
    public boolean requiresMySql() {
        return false;
    }

    public Connection getDbConnection(String dbName) throws ConnectionException{
        String path;
        try {
            Class.forName("org.hsqldb.jdbc.JDBCDriver");
        } catch (ClassNotFoundException e) {
            e.printStackTrace(); //do nothing
        }
        try {
            path = getDbPath(dbName);
        } catch (IOException e) {
            e.printStackTrace();
            throw new ConnectionException("Could not create path to database "+BiocodeService.getInstance().getDataDirectory().getAbsolutePath()+ File.separator + dbName + ".db", e);
        }
        try {
            return DriverManager.getConnection("jdbc:hsqldb:file:" + path + ";shutdown=true");
            //return DriverManager.getConnection("jdbc:sqlite:" + path);
        } catch (SQLException e) {
            e.printStackTrace();
            throw new ConnectionException(e.getMessage(), e);
        }
    }

    @Override
    protected boolean canUpgradeDatabase() {
        return true;
    }

    protected void upgradeDatabase(String currentVersion) throws ConnectionException {
        try {
            while(BiocodeUtilities.compareVersions(currentVersion, EXPECTED_SERVER_FULL_VERSION) < 0) {  // Keep applying upgrade scripts til version is expected
                String upgradeName = "upgrade_" + currentVersion + "_sqlite.sql";
                InputStream scriptStream = getClass().getResourceAsStream(upgradeName);
                if(scriptStream == null) {
                    throw new FileNotFoundException("Could not find resource "+getClass().getPackage().getName()+"."+upgradeName);
                }
                DatabaseScriptRunner.runScript(connection, scriptStream, true, false);

                currentVersion = getFullVersionStringFromDatabase();
            }
        } catch(IOException ex) {
            throw new ConnectionException("Unable to read database upgrade script: " + ex.getMessage(), ex);
        } catch (SQLException e) {
            throw new ConnectionException("Failed to upgrade database:" + e.getMessage(), e);
        }
    }

    @Override
    public boolean isLocal() {
        return true;
    }

    @Override
    public String getUsername() {
        throw new IllegalStateException("Username does not apply to local connections");
    }

    @Override
    public String getSchema() {
        throw new IllegalStateException("Schema does not apply to local connections");
    }
}
