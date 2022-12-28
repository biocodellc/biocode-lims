package com.biomatters.plugins.biocode.labbench.lims;

import com.biomatters.geneious.publicapi.plugin.Geneious;
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
 *          <p/>
 *          Created on 17/04/13 2:27 PM
 */


public class LocalLIMSConnection extends SqlLimsConnection {


    @SuppressWarnings("UnusedDeclaration")
    public LocalLIMSConnection() {

    }

    @Override
    public PasswordOptions getConnectionOptions() {
        return new LocalLIMSConnectionOptions(LIMSConnection.class);
    }

    @Override
    public javax.sql.DataSource connectToDb(Options connectionOptions) throws ConnectionException {
        String dbName = connectionOptions.getValueAsString("database");

        String path;
        try {
            path = getDbPath(dbName);
            if (Geneious.isHeadless()) {
                boolean databaseFilesExist = false;
                for (File file : LocalLIMSConnectionOptions.getDatabaseFilesForDbPath(path)) {
                    if(file.exists()) {
                        databaseFilesExist = true;
                    }
                }
                if(!databaseFilesExist) {
                    LocalLIMSConnectionOptions.createDatabase(dbName); //creates Biocode Lims Database if it does not exist (server only)
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
            throw new ConnectionException("Could not create path to database "+BiocodeService.getInstance().getDataDirectory().getAbsolutePath()+ File.separator + dbName + ".db", e);
        } catch (SQLException e) {
            throw new ConnectionException(e.getMessage(), e);
        }
        String connectionString = "jdbc:hsqldb:file:" + path + ";shutdown=true";
        return createBasicDataSource(connectionString, null, null);
    }

    static String getDbPath(String newDbName) throws IOException {
        return BiocodeService.getInstance().getDataDirectory().getCanonicalPath() + File.separator + newDbName + ".db";
    }

    @Override
    protected boolean canUpgradeDatabase() {
        return true;
    }

    protected void upgradeDatabase(String currentVersion) throws ConnectionException {
        ConnectionWrapper connection = null;
        try {
            connection = getConnection();
            while(BiocodeUtilities.compareVersions(currentVersion, EXPECTED_SERVER_FULL_VERSION) < 0) {  // Keep applying upgrade scripts til version is expected
                String upgradeName = "upgrade_" + currentVersion + "_sqlite.sql";
                InputStream scriptStream = getClass().getResourceAsStream(upgradeName);
                if(scriptStream == null) {
                    throw new FileNotFoundException("Could not find resource "+getClass().getPackage().getName()+"."+upgradeName);
                }
                DatabaseScriptRunner.runScript(connection.getInternalConnection(), scriptStream, true, false);

                currentVersion = getFullVersionStringFromDatabase();
            }
        } catch(IOException ex) {
            throw new ConnectionException("Unable to read database upgrade script: " + ex.getMessage(), ex);
        } catch (SQLException e) {
            throw new ConnectionException("Failed to upgrade database:" + e.getMessage(), e);
        } finally {
            returnConnection(connection);
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
