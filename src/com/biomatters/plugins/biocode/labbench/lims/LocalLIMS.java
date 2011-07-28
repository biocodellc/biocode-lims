package com.biomatters.plugins.biocode.labbench.lims;

import com.biomatters.plugins.biocode.labbench.ConnectionException;
import com.biomatters.geneious.publicapi.plugin.Options;
import com.biomatters.geneious.publicapi.components.Dialogs;

import java.io.File;
import java.io.IOException;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.*;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;
import java.sql.*;

import org.virion.jam.util.SimpleListener;

/**
 * User: Steve
 * Date: 5/02/2010
 * Time: 8:24:56 PM
 */
public class LocalLIMS {
    private static final String SCRIPT_NAME = "labbench_"+LIMSConnection.EXPECTED_SERVER_VERSION+"_sqlite.sql";
    private static final Options.OptionValue NULL_DATABASE = new Options.OptionValue("null", "No databases");



    private static File dataDirectory;

    public static void initialize(File dataDirectory) {
        if(!dataDirectory.exists()) {
            if(!dataDirectory.mkdirs()) {
                //todo:handle
            }
        }
        LocalLIMS.dataDirectory = dataDirectory;
    }

    private static List<String> update() {
        Set<String> dbNamesSet = new LinkedHashSet<String>();
        if(dataDirectory == null) {
            return Collections.EMPTY_LIST;
        }
        for(File f : dataDirectory.listFiles()) {
            if(f.getName().endsWith(".db.log")) {
                dbNamesSet.add(f.getName().substring(0, f.getName().length()-7));
            }
            else if(f.getName().endsWith(".db.properties")) {
                dbNamesSet.add(f.getName().substring(0, f.getName().length()-14));
            }
            else if(f.getName().endsWith(".db.script")) {
                dbNamesSet.add(f.getName().substring(0, f.getName().length()-10));
            }
        }
        return new ArrayList<String>(dbNamesSet);
    }

    public static Options getConnectionOptions() {
        final Options connectionOptions = new Options(LocalLIMS.class);

        List<Options.OptionValue> dbValues = getDbValues(update());

        connectionOptions.beginAlignHorizontally("Choose database", false);
        final Options.ComboBoxOption<Options.OptionValue> databaseOption = connectionOptions.addComboBoxOption("database", "", dbValues, dbValues.get(0));
        final Options.ButtonOption addDatabaseOption = connectionOptions.addButtonOption("addDatabase", "", "Add Database");
        final Options.ButtonOption removeDatabaseOption = connectionOptions.addButtonOption("removeDatabase", "", "Remove Database");
        connectionOptions.endAlignHorizontally();

        SimpleListener enabledListener = new SimpleListener() {
            public void objectChanged() {
                removeDatabaseOption.setEnabled(!databaseOption.getValue().equals(NULL_DATABASE));
            }
        };
        databaseOption.addChangeListener(enabledListener);
        enabledListener.objectChanged();

        addDatabaseOption.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                Options addDatabaseOptions = new Options(this.getClass());
                addDatabaseOptions.addStringOption("dbName", "Database name", "");
                if(Dialogs.showOkCancelDialog(addDatabaseOptions.getPanel(), "Add Database", connectionOptions.getPanel())) {
                    List<String> dbNames = update();
                    if(databaseOption != null) {
                        databaseOption.setPossibleValues(getDbValues(dbNames));
                    }
                    String newDbName = addDatabaseOptions.getValueAsString("dbName");
                    for(String s : dbNames) {
                        if(s.equalsIgnoreCase(newDbName)) {
                            Dialogs.showMessageDialog("The database "+newDbName+" already exists!", "Database Exists", connectionOptions.getPanel(), Dialogs.DialogIcon.INFORMATION);
                            return;
                        }
                    }
                    try {
                        createDatabase(newDbName);
                    } catch (SQLException e1) {
                        Dialogs.showMessageDialog("Could not create database: "+e1.getMessage(), "Could not create database", connectionOptions.getPanel(), Dialogs.DialogIcon.WARNING);
                    }
                    if(databaseOption != null) {
                        dbNames = update();
                        databaseOption.setPossibleValues(getDbValues(dbNames));
                        databaseOption.setValueFromString(newDbName);
                    }

                }
            }
        });

        removeDatabaseOption.addActionListener(new ActionListener(){
            public void actionPerformed(ActionEvent e) {
                if(Dialogs.showYesNoDialog("Are you sure you want to delete this database? (this option cannot be undone)", "Delete Database", connectionOptions.getPanel(), Dialogs.DialogIcon.QUESTION)) {
                    try {
                        deleteDatabase(databaseOption.getValue().getName());
//                        String path = getDbPath(databaseOption.getValue().getName());
//                        File dbFile = new File(path);
//                        if(!dbFile.exists()) {
//                            throw new FileNotFoundException("The file "+path+" does not exist");
//                        }
//                        if(!dbFile.delete()) {
//                            dbFile.deleteOnExit();
//                            throw new IOException("The database could not be deleted because the file is in use.  You can try again, or it will be deleted when you quit Geneious.");
//                        }
                    } catch (IOException ex) {
                        ex.printStackTrace();
                        Dialogs.showMessageDialog(ex.getMessage(), "Error deleting database", connectionOptions.getPanel(), Dialogs.DialogIcon.ERROR);
                    } finally {
                        if(databaseOption != null) {
                            databaseOption.setPossibleValues(getDbValues(update()));
                        }
                    }
                }
            }
        });


        return connectionOptions;
    }

    private static void createDatabase(String newDbName) throws SQLException {
        try {
            InputStream scriptStream = LocalLIMS.class.getResourceAsStream(SCRIPT_NAME);
            if(scriptStream == null) {
                throw new FileNotFoundException("Could not find resource "+LocalLIMS.class.getPackage().getName()+"."+SCRIPT_NAME);
            }
            try {
                Class.forName("org.hsqldb.jdbc.JDBCDriver");
            } catch (ClassNotFoundException e) {
                e.printStackTrace(); //do nothing
            }
            Connection connection = DriverManager.getConnection("jdbc:hsqldb:file:" + getDbPath(newDbName)+";shutdown=true");
            DatabaseScriptRunner.runScript(connection, scriptStream, true, false);
            connection.close();
        } catch (IOException e) {
            throw new SQLException(e.getMessage());
        } 
    }

    private static void deleteDatabase(String dbName) throws IOException{
        String[] extensions = new String[] {"properties", "script", "log", "data", "backup", "lobs", "lck", "tmp"};
        for(String extension : extensions) {
            File dbFile = new File(getDbPath(dbName)+"."+extension);
            if(!dbFile.exists()) {
                continue;
            }
            if(dbFile.isFile() && !dbFile.delete()) {
                dbFile.deleteOnExit();
            }
        }
    }

    private static String getDbPath(String newDbName) throws IOException {
        return dataDirectory.getCanonicalPath() + File.separator + newDbName + ".db";
    }

    private static List<Options.OptionValue> getDbValues(List<String> dbNames) {
        List<Options.OptionValue> dbValues = new ArrayList<Options.OptionValue>();
        for(String s : dbNames) {
            dbValues.add(new Options.OptionValue(s,s));
        }
        if(dbValues.size() == 0) {
            dbValues.add(NULL_DATABASE);
        }
        return dbValues;
    }


    public Connection connect(Options options) throws ConnectionException{
        String dbName = options.getValueAsString("database");
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
            throw new ConnectionException("Could not create path to database "+dataDirectory.getAbsolutePath()+ File.separator + dbName + ".db", e);
        }
        try {
            return DriverManager.getConnection("jdbc:hsqldb:file:"+path+";shutdown=true");
            //return DriverManager.getConnection("jdbc:sqlite:" + path);
        } catch (SQLException e) {
            e.printStackTrace();
            throw new ConnectionException(e.getMessage(), e);
        }
    }


    public void upgradeDatabase(Options options) throws SQLException{
        Connection connection;
        try {
            connection = connect(options);
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
}
