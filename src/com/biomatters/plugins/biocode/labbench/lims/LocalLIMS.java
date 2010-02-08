package com.biomatters.plugins.biocode.labbench.lims;

import com.biomatters.plugins.biocode.labbench.BiocodeService;
import com.biomatters.plugins.biocode.labbench.ConnectionException;
import com.biomatters.geneious.publicapi.utilities.FileUtilities;
import com.biomatters.geneious.publicapi.plugin.Options;
import com.biomatters.geneious.publicapi.components.Dialogs;

import java.io.File;
import java.io.IOException;
import java.io.FileNotFoundException;
import java.util.List;
import java.util.ArrayList;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Connection;
import java.net.URL;
import java.net.URISyntaxException;

import org.virion.jam.util.SimpleListener;

/**
 * User: Steve
 * Date: 5/02/2010
 * Time: 8:24:56 PM
 */
public class LocalLIMS {
    private static final String SCRIPT_NAME = "labbench_4.sql";
    private static final Options.OptionValue NULL_DATABASE = new Options.OptionValue("null", "No databases");



    private List<String> dbNames;
    private File dataDirectory;

    public void initialize(File dataDirectory) {
        if(!dataDirectory.exists()) {
            dataDirectory.mkdirs();
        }
        this.dataDirectory = dataDirectory;

        update();
    }

    private void update() {
        dbNames = new ArrayList<String>();
        for(File f : this.dataDirectory.listFiles()) {
            if(f.getName().endsWith(".db.log")) {
                dbNames.add(f.getName().substring(0, f.getName().length()-7));
            }
        }
    }

    public Options getConnectionOptions() {
        final Options connectionOptions = new Options(this.getClass());

        List<Options.OptionValue> dbValues = getDbValues();

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
                    update();
                    if(databaseOption != null) {
                        databaseOption.setPossibleValues(getDbValues());
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
                    update();
                    if(databaseOption != null) {
                        databaseOption.setPossibleValues(getDbValues());
                    }
                    databaseOption.setPossibleValues(getDbValues());
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
                        update();
                        if(databaseOption != null) {
                            databaseOption.setPossibleValues(getDbValues());
                        }
                    }
                }
            }
        });


        return connectionOptions;
    }

    private void createDatabase(String newDbName) throws SQLException {
        try {
            URL fileUrl = getClass().getResource(SCRIPT_NAME);
            if(fileUrl == null) {
                throw new FileNotFoundException("Could not find resource "+getClass().getPackage().getName()+"."+SCRIPT_NAME);
            }
            File connectionFile = new File(fileUrl.toURI());
            try {
                Class.forName("org.hsqldb.jdbc.JDBCDriver");
            } catch (ClassNotFoundException e) {
                e.printStackTrace(); //do nothing
            }
            Connection connection = DriverManager.getConnection("jdbc:hsqldb:file:" + getDbPath(newDbName));
            DatabaseScriptRunner.runScript(connection, connectionFile, true, true);
        } catch (IOException e) {
            throw new SQLException(e.getMessage());
        } catch(URISyntaxException e) {
            throw new SQLException(e.getMessage());
        }
    }

    private void deleteDatabase(String dbName) throws IOException{
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

    private String getDbPath(String newDbName) throws IOException {
        return dataDirectory.getCanonicalPath() + "\\" + newDbName + ".db";
    }

    private List<Options.OptionValue> getDbValues() {
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
        String path = null;
        try {
            Class.forName("org.hsqldb.jdbc.JDBCDriver");
        } catch (ClassNotFoundException e) {
            e.printStackTrace(); //do nothing
        }
        try {
            path = getDbPath(dbName);
        } catch (IOException e) {
            e.printStackTrace();
            throw new ConnectionException("Could not create path to database "+dataDirectory.getAbsolutePath()+ "\\" + dbName + ".db", e);
        }
        try {
            return DriverManager.getConnection("jdbc:hsqldb:file:"+path);
            //return DriverManager.getConnection("jdbc:sqlite:" + path);
        } catch (SQLException e) {
            e.printStackTrace();
            throw new ConnectionException(e.getMessage(), e);
        }
    }


}
