package com.biomatters.plugins.biocode.labbench.lims;

import com.biomatters.geneious.publicapi.components.Dialogs;
import com.biomatters.geneious.publicapi.plugin.Options;
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


public class LocalLIMSConnection extends LIMSConnection {


    @SuppressWarnings("UnusedDeclaration")
    public LocalLIMSConnection() {

    }

    /**
     * creates a new LIMSConnection connected to the given local LIMS database
     * @param localDatabaseName
     * @throws ConnectionException
     */
    public LocalLIMSConnection(String localDatabaseName) throws ConnectionException{
        connect(localDatabaseName, false);
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
        connect(dbName, false);
    }

    private void connect(String dbName, boolean alreadyAskedAboutUpgrade) throws ConnectionException {
        connection = getDbConnection(dbName);
        serverUrn = "local/"+dbName;
        try {
            ResultSet resultSet = connection.createStatement().executeQuery("SELECT * FROM databaseversion LIMIT 1");
            if(!resultSet.next()) {
                throw new ConnectionException("Your LIMS database appears to be corrupt.  Please contact your systems administrator for assistance.");
            }
            else {
                int version = resultSet.getInt("version");

                if(version < EXPECTED_SERVER_VERSION) {
                    if(alreadyAskedAboutUpgrade || Dialogs.showYesNoDialog("The LIMS database you are connecting to is written for an older version of this plugin.  Would you like to upgrade it?", "Old database", null, Dialogs.DialogIcon.QUESTION)) {
                        upgradeDatabase(dbName);
                        connect(dbName, true);
                    }
                    else {
                        throw new ConnectionException("You need to upgrade your database, or choose another one to continue");
                    }
                }
                else if(version > EXPECTED_SERVER_VERSION) {
                    throw new ConnectionException("This database was written for a newer version of the LIMS plugin, and cannot be accessed");
                }
            }
        }
        catch(SQLException ex) {
            throw new ConnectionException(ex.getMessage(), ex);
        }
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

    public void upgradeDatabase(String dbName) throws SQLException{
        Connection connection;
        try {
            connection = getDbConnection(dbName);
        }
        catch(ConnectionException ex) {
            throw new SQLException("Could not connect to the database to upgrade it: "+ex.getMessage());
        }
        try {
            ResultSet resultSet = connection.createStatement().executeQuery("SELECT * FROM databaseversion LIMIT 1");
            if(!resultSet.next()) {
                throw new SQLException("Your LIMS database appears to be corrupt.  Please contact your systems administrator for assistance.");
            }
            int version = resultSet.getInt("version");
            resultSet.close();

            String upgradeName = "upgrade_" + version + "_sqlite.sql";
            InputStream scriptStream = getClass().getResourceAsStream(upgradeName);
            if(scriptStream == null) {
                throw new FileNotFoundException("Could not find resource "+getClass().getPackage().getName()+"."+upgradeName);
            }
            DatabaseScriptRunner.runScript(connection, scriptStream, true, false);
            connection.close();
        }
        catch(IOException ex) {
            throw new SQLException(ex.getMessage());
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
