package com.biomatters.plugins.biocode.labbench.fims;

import com.biomatters.plugins.biocode.labbench.PasswordOptions;
import com.biomatters.plugins.biocode.labbench.BiocodeService;
import com.biomatters.plugins.biocode.XmlUtilities;
import com.biomatters.options.PasswordOption;

import java.util.List;
import java.util.Properties;
import java.util.ArrayList;
import java.io.IOException;
import java.sql.*;

/**
 * @author Steve
 * @version $Id$
 */
public class MySqlFimsConnectionOptions extends TableFimsConnectionOptions{
    private Driver driver;

    protected PasswordOptions getConnectionOptions() {
        return new MooreaFimsConnectionOptions(this.getClass(), true);
    }

    protected List<OptionValue> getTableColumns() throws IOException {
        MooreaFimsConnectionOptions connectionOptions = (MooreaFimsConnectionOptions)getChildOptions().get(CONNECTION_OPTIONS_KEY);

        driver = MySQLFimsConnection.getDriver();

        Properties properties = new Properties();
        properties.put("user", connectionOptions.getValueAsString("username"));
        properties.put("password", ((PasswordOption)connectionOptions.getOption("password")).getPassword());
        try {
            DriverManager.setLoginTimeout(20);
            Connection connection = driver.connect("jdbc:mysql://"+connectionOptions.getValueAsString("serverUrl")+":"+connectionOptions.getValueAsString("serverPort"), properties);
            Statement statement = connection.createStatement();
            statement.execute("USE "+connectionOptions.getValueAsString("database"));
            ResultSet resultSet = statement.executeQuery("DESCRIBE "+connectionOptions.getValueAsString("table"));

            List<OptionValue> results = new ArrayList<OptionValue>();

            while(resultSet.next()) {
                results.add(new OptionValue(XmlUtilities.encodeXMLChars("MYSQLFIMS:"+resultSet.getString("Field")), XmlUtilities.encodeXMLChars(resultSet.getString("Field"))));
                for(int i=1; i <= resultSet.getMetaData().getColumnCount(); i++) {
                    System.out.println(resultSet.getMetaData().getColumnLabel(i)+": "+resultSet.getObject(i)+" ("+resultSet.getMetaData().getColumnClassName(i)+")");
                }
                System.out.println("------------------------------------");
            }
            connection.close();
            return results;
        } catch (SQLException e1) {
            throw new IOException("Failed to connect to the MySQL database: "+e1.getMessage());
        }
    }

    protected boolean updateAutomatically() {
        return false;
    }
}
