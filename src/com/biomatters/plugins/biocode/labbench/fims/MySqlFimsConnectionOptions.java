package com.biomatters.plugins.biocode.labbench.fims;

import com.biomatters.plugins.biocode.labbench.BiocodeService;
import com.biomatters.plugins.biocode.labbench.ConnectionException;
import com.biomatters.plugins.biocode.labbench.PasswordOptions;
import com.biomatters.plugins.biocode.utilities.PasswordOption;
import com.biomatters.geneious.publicapi.documents.DocumentField;
import com.biomatters.plugins.biocode.utilities.SqlUtilities;

import java.util.List;
import java.util.Properties;
import java.util.ArrayList;
import java.io.IOException;
import java.sql.*;

/**
 * @author Steve
 */
public class MySqlFimsConnectionOptions extends TableFimsConnectionOptions{
    private Driver driver;

    protected PasswordOptions getConnectionOptions() {
        return new MooreaFimsConnectionOptions(this.getClass(), true);
    }

    protected List<OptionValue> _getTableColumns() throws IOException {
        MooreaFimsConnectionOptions connectionOptions = (MooreaFimsConnectionOptions)getChildOptions().get(CONNECTION_OPTIONS_KEY);

        try {
            driver = BiocodeService.getInstance().getDriver();
        } catch (ConnectionException e) {
            throw new IOException("Could not load MySQL driver");
        }

        Properties properties = new Properties();
        properties.put("user", connectionOptions.getValueAsString("username"));
        properties.put("password", ((PasswordOption)connectionOptions.getOption("password")).getPassword());
        try {
            DriverManager.setLoginTimeout(20);
            Connection connection = DriverManager.getConnection("jdbc:mysql://"+connectionOptions.getValueAsString("serverUrl")+":"+connectionOptions.getValueAsString("serverPort"), properties);
            Statement statement = connection.createStatement();
            statement.execute("USE "+connectionOptions.getValueAsString("database"));
            ResultSet resultSet = statement.executeQuery("DESCRIBE "+connectionOptions.getValueAsString("table"));

            List<OptionValue> results = new ArrayList<OptionValue>();

            while(resultSet.next()) {
                DocumentField field = SqlUtilities.getDocumentField(resultSet, false);
                if(field != null) {
                    results.add(new OptionValue(field.getCode(), field.getName()));
                }
            }
            connection.close();
            return results;
        } catch (SQLException e1) {
            throw new IOException("Failed to connect to the MySQL database: "+e1.getMessage(), e1);
        }
    }

    protected boolean updateAutomatically() {
        return false;
    }
}
