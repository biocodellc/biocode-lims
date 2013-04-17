package com.biomatters.plugins.biocode.labbench.lims;

import com.biomatters.geneious.publicapi.components.Dialogs;
import com.biomatters.geneious.publicapi.plugin.Options;
import com.biomatters.plugins.biocode.labbench.BiocodeService;
import com.biomatters.plugins.biocode.labbench.PasswordOptions;
import org.virion.jam.util.SimpleListener;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author Steve
 * @version $Id$
 *          <p/>
 *          Created on 17/04/13 2:52 PM
 */


@SuppressWarnings("ThrowableResultOfMethodCallIgnored")
public class LocalLIMSConnectionOptions extends PasswordOptions {
    private static final String SCRIPT_NAME = "labbench_"+LIMSConnection.EXPECTED_SERVER_VERSION+"_sqlite.sql";
   private static final Options.OptionValue NULL_DATABASE = new Options.OptionValue("null", "No databases");



    @SuppressWarnings("UnusedDeclaration")
    public LocalLIMSConnectionOptions() {
        super(LocalLIMSConnection.class);
        init();
    }

    public LocalLIMSConnectionOptions(Class cl) {
        super(cl);
        init();
    }

    @SuppressWarnings("UnusedDeclaration")
    public LocalLIMSConnectionOptions(Class cl, String preferenceNameSuffix) {
        super(cl, preferenceNameSuffix);
        init();
    }

    private void init() {
        List<OptionValue> dbValues = getDbValues(updateDatabaseList());

        beginAlignHorizontally("Choose database", false);
        final Options.ComboBoxOption<Options.OptionValue> databaseOption = addComboBoxOption("database", "", dbValues, dbValues.get(0));
        final Options.ButtonOption addDatabaseOption = addButtonOption("addDatabase", "", "Add Database");
        final Options.ButtonOption removeDatabaseOption = addButtonOption("removeDatabase", "", "Remove Database");
        endAlignHorizontally();

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
                if (Dialogs.showOkCancelDialog(addDatabaseOptions.getPanel(), "Add Database", getPanel())) {
                    List<String> dbNames = updateDatabaseList();
                    databaseOption.setPossibleValues(getDbValues(dbNames));
                    final String newDbName = addDatabaseOptions.getValueAsString("dbName");
                    for (String s : dbNames) {
                        if (s.equalsIgnoreCase(newDbName)) {
                            Dialogs.showMessageDialog("The database " + newDbName + " already exists!", "Database Exists", getPanel(), Dialogs.DialogIcon.INFORMATION);
                            return;
                        }
                    }
                    final AtomicReference<Exception> exception = new AtomicReference<Exception>();
                    Runnable runnable = new Runnable() {
                        public void run() {
                            try {
                                createDatabase(newDbName);
                            } catch (SQLException e1) {
                                exception.set(e1);
                            }
                        }
                    };
                    BiocodeService.block("Creating Database", getPanel(), runnable);
                    if (exception.get() != null) {
                        Dialogs.showMessageDialog("Could not create database: " + exception.get().getMessage(), "Could not create database", getPanel(), Dialogs.DialogIcon.WARNING);
                    }
                    dbNames = updateDatabaseList();
                    databaseOption.setPossibleValues(getDbValues(dbNames));
                    databaseOption.setValueFromString(newDbName);

                }
            }
        });

        removeDatabaseOption.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if (Dialogs.showYesNoDialog("Are you sure you want to delete this database? (this option cannot be undone)", "Delete Database", getPanel(), Dialogs.DialogIcon.QUESTION)) {
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
                        Dialogs.showMessageDialog(ex.getMessage(), "Error deleting database", getPanel(), Dialogs.DialogIcon.ERROR);
                    } finally {
                        databaseOption.setPossibleValues(getDbValues(updateDatabaseList()));
                    }
                }
            }
        });
    }

    private static void createDatabase(String newDbName) throws SQLException {
        try {
            InputStream scriptStream = LocalLIMSConnection.class.getResourceAsStream(SCRIPT_NAME);
            if(scriptStream == null) {
                throw new FileNotFoundException("Could not find resource "+LocalLIMSConnection.class.getPackage().getName()+"."+SCRIPT_NAME);
            }
            try {
                Class.forName("org.hsqldb.jdbc.JDBCDriver");
            } catch (ClassNotFoundException e) {
                e.printStackTrace(); //do nothing
            }
            Connection connection = DriverManager.getConnection("jdbc:hsqldb:file:" + LocalLIMSConnection.getDbPath(newDbName) + ";shutdown=true");
            DatabaseScriptRunner.runScript(connection, scriptStream, true, false);
            connection.close();
        } catch (IOException e) {
            throw new SQLException(e.getMessage());
        }
    }

    private static void deleteDatabase(String dbName) throws IOException{
        String[] extensions = new String[] {"properties", "script", "log", "data", "backup", "lobs", "lck", "tmp"};
        for(String extension : extensions) {
            File dbFile = new File(LocalLIMSConnection.getDbPath(dbName)+"."+extension);
            if(!dbFile.exists()) {
                continue;
            }
            if(dbFile.isFile() && !dbFile.delete()) {
                dbFile.deleteOnExit();
            }
        }
    }


    public static List<Options.OptionValue> getDatabaseOptionValues() {
        return getDbValues(updateDatabaseList());
    }

    private static List<String> updateDatabaseList() {
        Set<String> dbNamesSet = new LinkedHashSet<String>();
        File dataDirectory = BiocodeService.getInstance().getDataDirectory();
        if(dataDirectory == null) {
            return Collections.emptyList();
        }
        File[] files = dataDirectory.listFiles();
        if(files == null) {
            return Collections.emptyList();
        }
        for(File f : files) {
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

    private static List<Options.OptionValue> getDbValues(List<String> dbNames) {
        List<Options.OptionValue> dbValues = new ArrayList<OptionValue>();
        for(String s : dbNames) {
            dbValues.add(new Options.OptionValue(s,s));
        }
        if(dbValues.size() == 0) {
            dbValues.add(NULL_DATABASE);
        }
        return dbValues;
    }

    @Override
    public Options getEnterPasswordOptions() {
        return null;
    }

    @Override
    public void setPasswordsFromOptions(Options enterPasswordOptions) {
    }


}
