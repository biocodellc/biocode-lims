package com.biomatters.plugins.biocode.labbench.fims;

import com.biomatters.plugins.biocode.labbench.PasswordOptions;
import com.biomatters.plugins.biocode.labbench.ConnectionException;
import com.biomatters.plugins.biocode.labbench.FimsSample;
import com.biomatters.plugins.biocode.labbench.BiocodeService;
import com.biomatters.plugins.biocode.BiocodeUtilities;
import com.biomatters.geneious.publicapi.plugin.Options;
import com.biomatters.geneious.publicapi.documents.DocumentField;
import com.biomatters.geneious.publicapi.documents.AnnotatedPluginDocument;
import com.biomatters.geneious.publicapi.databaseservice.Query;
import com.biomatters.geneious.publicapi.databaseservice.RetrieveCallback;
import com.biomatters.options.PasswordOption;

import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.Properties;
import java.io.IOException;
import java.sql.*;

/**
 * @author Steve
 * @version $Id$
 */
public class MySQLFimsConnection extends TableFimsConnection{
    private Connection connection;
    private Driver driver;
    private String tableName;

    static Driver getDriver() throws IOException {
        try {
            Class driverClass = BiocodeService.getDriverClass();
            if(driverClass == null) {
                throw new IOException("You need to specify the location of your MySQL Driver file");
            }
            return (Driver) driverClass.newInstance();
        } catch (InstantiationException e) {
            throw new IOException("Could not instantiate SQL driver.");
        } catch (IllegalAccessException e) {
            throw new IOException("Could not access SQL driver.");
        }
    }

    public String getLabel() {
        return "MySQL Database";
    }

    public String getName() {
        return "MySql";  //To change body of implemented methods use File | Settings | File Templates.
    }

    public String getDescription() {
        return "Use a MySQL database as your FIMS";
    }

    public TableFimsConnectionOptions _getConnectionOptions() {
        return new MySqlFimsConnectionOptions();
    }

    public void _connect(TableFimsConnectionOptions optionsa) throws ConnectionException {
        MooreaFimsConnectionOptions connectionOptions = (MooreaFimsConnectionOptions)optionsa.getChildOptions().get(TableFimsConnectionOptions.CONNECTION_OPTIONS_KEY);

        try {
            driver = MySQLFimsConnection.getDriver();
        } catch (IOException e) {
            throw new ConnectionException(e.getMessage(), e);
        }

        tableName = connectionOptions.getValueAsString("table");

        Properties properties = new Properties();
        properties.put("user", connectionOptions.getValueAsString("username"));
        properties.put("password", ((PasswordOption)connectionOptions.getOption("password")).getPassword());
        try {
            DriverManager.setLoginTimeout(20);
            connection = driver.connect("jdbc:mysql://"+connectionOptions.getValueAsString("serverUrl")+":"+connectionOptions.getValueAsString("serverPort"), properties);
            Statement statement = connection.createStatement();
            statement.execute("USE "+connectionOptions.getValueAsString("database"));
        } catch (SQLException e1) {
            throw new ConnectionException("Failed to connect to the MySQL database: "+e1.getMessage());
        }
    }

    private DocumentField getDocumentField(ResultSet resultSet) throws SQLException{//todo: multiple value types
        return DocumentField.createStringField(resultSet.getString("Field"), resultSet.getString("Field"), "MYSQLFIMS:"+resultSet.getString("Field"));
    }

    public List<DocumentField> getTableColumns() throws IOException {
        try {
            Statement statement = connection.createStatement();
            ResultSet resultSet = statement.executeQuery("DESCRIBE "+tableName);
            List<DocumentField> results = new ArrayList<DocumentField>();

            while(resultSet.next()) {
                results.add(getDocumentField(resultSet));
                for(int i=1; i <= resultSet.getMetaData().getColumnCount(); i++) {
                    System.out.println(resultSet.getMetaData().getColumnLabel(i)+": "+resultSet.getObject(i)+" ("+resultSet.getMetaData().getColumnClassName(i)+")");
                }
                System.out.println("------------------------------------");
            }

            return results;
        } catch (SQLException e) {
            IOException ioException = new IOException(e.toString());
            ioException.setStackTrace(e.getStackTrace());
            throw ioException;
        }

    }

    public void _disconnect() {
        if(connection != null) {
            try {
                connection.close();
            } catch (SQLException e) {
                e.printStackTrace();
                //ignore - garbage collection will deal with this...
            }
        }
        driver = null;
        connection = null;
    }

    public List<FimsSample> _getMatchingSamples(Query query) throws ConnectionException {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public void getAllSamples(RetrieveCallback callback) throws ConnectionException {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    public boolean requiresMySql() {
        return true;
    }
}
