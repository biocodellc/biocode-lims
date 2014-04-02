package com.biomatters.plugins.biocode.labbench.lims;

import java.io.*;
import java.sql.SQLException;
import java.sql.Connection;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * This will run any of the setup scripts Biomatters has written to setup the shared database.
 *
 * @author Matthew Cheung
 * @version $Id: DatabaseScriptRunner.java 18038 2008-03-11 03:20:44Z matthew $
 */
class DatabaseScriptRunner {

    private DatabaseScriptRunner() {
        // Cannot instantiate, this is a utility class
    }

    /**
     * Runs the script for creating the tables required for a shared Database. The
     * script is loaded from a file and executed command by comand.
     *
     * @param connection The connection to a database to run the script on
     * @param scriptFile The script file to run
     * @param allowDrops If this is false then any DROP commands will not be run.
     * @param ignoreErrors if false the script will stop running when an error is encountered.  if true will continue running subsequent statements.
     * @throws SQLException if a problem is encountered while communicating with the database, this may include bad syntax
     * @throws IOException if a problem occurs reading the script file.  This is a FileNotFoundException if the script file did not exist.
     */
    public static void runScript(SqlLimsConnection.ConnectionWrapper connection, InputStream scriptFile, boolean allowDrops, boolean ignoreErrors) throws SQLException, IOException {
        List<String> commands = getCommandsFromScript(scriptFile);

        for(String command : commands) {
            // Do not run the drop lines; As at 2007-11-20, this code is only called when a database is empty.
            if (allowDrops || !command.toUpperCase().startsWith("DROP ")) {
                System.out.println(command);
                try {
                    connection.executeUpdate(command);
                    System.out.println("ok");
                } catch (SQLException e) {
                    if(ignoreErrors) {
                        System.out.println(e.getMessage());    
                    }
                    else {
                        e.printStackTrace();
                        throw e;
                    }
                }
            }
        }
    }


    /**
     *
     * @param scriptFile A reference to an sql script file.
     * @return An unmodifiable list containing the sql commands in the provided script file.  null if the file does not exist
     * @throws IOException if there are problems reading from the script file
     */
    static List<String> getCommandsFromScript(InputStream scriptFile) throws IOException {

        List<String> commands = new ArrayList<String>();
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new InputStreamReader(scriptFile));

            // Here we must read until we hit a ; character.  When we have found a ; that indicates that this is the end
            // of a command.  We must ignore anything after a -- on each line, because those are comments.
            StringBuilder commandBuilder = new StringBuilder();
            String currentLine = reader.readLine();
            while(currentLine != null) {
                if(currentLine.contains("--")) {
                    currentLine = currentLine.substring(0, currentLine.indexOf("--"));
                }

                if(currentLine.contains(";")) { // command read completely - we can execute it
                    // Do not execute the command with the trailing ;, this causes oracle drivers to fail
                    commandBuilder.append(currentLine.substring(0, currentLine.indexOf(";")));
                    final String command = commandBuilder.toString().trim();
                    commands.add(command);
                    commandBuilder = new StringBuilder();
                } else {
                    // In case a line ends without a "," or ";" or similar, we need to make
                    // sure it's still separated from what comes in the next line by whitespace
                    if (commandBuilder.length() > 0) {
                        commandBuilder.append(" ");
                    }
                    commandBuilder.append(currentLine);
                }
                currentLine = reader.readLine();
            }
            return Collections.unmodifiableList(commands);
        } finally {
            if (reader != null) {
                reader.close();
            }
        }
    }
}
