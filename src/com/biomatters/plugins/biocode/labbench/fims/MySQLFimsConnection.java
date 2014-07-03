package com.biomatters.plugins.biocode.labbench.fims;

import com.biomatters.geneious.publicapi.databaseservice.DatabaseServiceException;
import com.biomatters.geneious.publicapi.utilities.StringUtilities;
import com.biomatters.plugins.biocode.labbench.*;
import com.biomatters.geneious.publicapi.documents.DocumentField;
import com.biomatters.geneious.publicapi.databaseservice.Query;
import com.biomatters.geneious.publicapi.databaseservice.RetrieveCallback;
import com.biomatters.plugins.biocode.utilities.PasswordOption;
import com.biomatters.plugins.biocode.utilities.SqlUtilities;

import java.util.*;
import java.util.Date;
import java.io.IOException;
import java.sql.*;

/**
 * @author Steve
 * @version $Id$
 */
public class MySQLFimsConnection extends TableFimsConnection {
    private Connection connection;
    private Driver driver;
    private String tableName;
    public static final String FIELD_PREFIX = "MYSQLFIMS:";

    public String getLabel() {
        return "Remote MySQL Database";
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

    private Statement createStatement() throws SQLException {
        if(connection == null) {
            throw new SQLException("Not logged into the FIMS");
        }
        Statement statement = connection.createStatement();
        statement.setQueryTimeout(BiocodeService.STATEMENT_QUERY_TIMEOUT);
        return statement;
    }

    private PreparedStatement prepareStatement(String query) throws SQLException{
        if(connection == null) {
            throw new SQLException("You are not connected to the FIMS database");
        }
        PreparedStatement statement = connection.prepareStatement(query);
        statement.setQueryTimeout(BiocodeService.STATEMENT_QUERY_TIMEOUT);
        return statement;
    }

    public void _connect(TableFimsConnectionOptions optionsa) throws ConnectionException {
        MooreaFimsConnectionOptions connectionOptions = (MooreaFimsConnectionOptions)optionsa.getChildOptions().get(TableFimsConnectionOptions.CONNECTION_OPTIONS_KEY);

        driver = BiocodeService.getInstance().getDriver();
        tableName = connectionOptions.getValueAsString("table");

        Properties properties = new Properties();
        properties.put("user", connectionOptions.getValueAsString("username"));
        properties.put("password", ((PasswordOption)connectionOptions.getOption("password")).getPassword());
        try {
            DriverManager.setLoginTimeout(20);
            String connectionStringring = "jdbc:mysql://" + connectionOptions.getValueAsString("serverUrl") + ":" +
                    connectionOptions.getValueAsString("serverPort") + "/" + connectionOptions.getValueAsString("database");
            connection = driver.connect(connectionStringring, properties);
            if(connection == null) {
                throw new SQLException("The driver "+driver.getClass().getName()+" is not the right kind of driver to connect to "+connectionOptions.getValueAsString("serverUrl"));
            }
        } catch (SQLException e1) {
            throw new ConnectionException("Failed to connect to the MySQL database: "+e1.getMessage());
        }
    }

    public List<DocumentField> getTableColumns() throws IOException {
        try {
            Statement statement = createStatement();
            ResultSet resultSet = statement.executeQuery("DESCRIBE "+tableName);
            List<DocumentField> results = new ArrayList<DocumentField>();

            while(resultSet.next() && connection != null) {
                DocumentField field = SqlUtilities.getDocumentField(resultSet, false);
                if(field != null) {
                    results.add(field);
                }
            }
            if(connection == null) {
                throw new SQLException("You are not connected to the database");
            }

            return results;
        } catch (SQLException e) {
            IOException ioException = new IOException(e.toString());
            ioException.setStackTrace(e.getStackTrace());
            throw ioException;
        }

    }

    public void _disconnect() {
        //we used to call connection.close(), but this can cause problems when the user disconnects while we're in the
        // middle of a search - instead set it to null and let the garbage collector deal with it when the queries have finished
        driver = null;
        connection = null;
    }

    @Override
    public List<String> getTissueIdsMatchingQuery(Query query, List<FimsProject> projectsToMatch) throws ConnectionException {
        StringBuilder queryBuilder = new StringBuilder();
        queryBuilder.append("SELECT ").append(getTissueCol()).append(" FROM ").append(tableName);

        List<Object> parameters = new ArrayList<Object>();
        String sqlString = SqlUtilities.getQuerySQLString(query, getSearchAttributes(), FIELD_PREFIX, false);
        if(projectsToMatch != null && !projectsToMatch.isEmpty()) {
            StringBuilder projectCondition = new StringBuilder();
            projectCondition.append(" AND (");
            boolean first = true;
            for (Map.Entry<DocumentField, Collection<FimsProject>> entry : getFieldsToProjects(projectsToMatch).entrySet()) {
                if(first) {
                    first = false;
                } else {
                    projectCondition.append(" AND ");
                }
                projectCondition.append(entry.getKey().getCode()).append(" IN ");
                SqlUtilities.appendSetOfQuestionMarks(projectCondition, entry.getValue().size());
                for (FimsProject project : entry.getValue()) {
                    parameters.add(project.getId());
                }
            }
            projectCondition.append(")");
            sqlString += projectCondition.toString();
        }

        if(sqlString != null) {
            queryBuilder.append(" WHERE ");
            queryBuilder.append(sqlString);
        }

        List<String> results = new ArrayList<String>();
        PreparedStatement select = null;
        try {
            select = prepareStatement(queryBuilder.toString());
            SqlUtilities.fillStatement(parameters, select);
            SqlUtilities.printSql(queryBuilder.toString(), parameters);
            ResultSet resultSet = select.executeQuery();
            while(resultSet.next()) {
                results.add(resultSet.getString(1));
            }
        } catch (SQLException e) {
            throw new ConnectionException("Failed to query FIMS: " + e.getMessage(), e);
        } finally {
            SqlUtilities.cleanUpStatements(select);
        }
        return results;
    }

    @Override
    protected String getTissueCol() {
        return super.getTissueCol().replace(FIELD_PREFIX, "");
    }

    @Override
    public List<FimsSample> _retrieveSamplesForTissueIds(List<String> tissueIds, RetrieveCallback callback) throws ConnectionException {
        StringBuilder query = new StringBuilder("SELECT * FROM " + tableName + " WHERE " + getTissueCol() + " IN ");
        SqlUtilities.appendSetOfQuestionMarks(query, tissueIds.size());

        String queryString = query.toString();
        SqlUtilities.printSql(queryString, tissueIds);
        try {
            PreparedStatement statement = prepareStatement(queryString);
            int index = 1;
            for (String tissueId : tissueIds) {
                statement.setObject(index++, tissueId);
            }

            ResultSet resultSet = statement.executeQuery();
            List<FimsSample> samples = new ArrayList<FimsSample>();
            while(resultSet.next() && connection != null){
                Map<String, Object> data = new HashMap<String, Object>();
                for(DocumentField f : getSearchAttributes()) {
                    if(String.class.isAssignableFrom(f.getValueType()) ) {
                        data.put(f.getCode(), resultSet.getString(f.getCode().substring(FIELD_PREFIX.length())));
                    }
                    else if(Integer.class.isAssignableFrom(f.getValueType()) ) {
                        data.put(f.getCode(), resultSet.getInt(f.getCode().substring(FIELD_PREFIX.length())));
                    }
                    else if(Double.class.isAssignableFrom(f.getValueType()) ) {
                        data.put(f.getCode(), resultSet.getDouble(f.getCode().substring(FIELD_PREFIX.length())));
                    }
                    else if(Date.class.isAssignableFrom(f.getValueType()) ) {
                        java.util.Date date = resultSet.getDate(f.getCode().substring(FIELD_PREFIX.length()));
                        if(date != null) {
                            date = new Date(date.getTime());
                        }
                        data.put(f.getCode(), date);
                    }
                    else if(Boolean.class.isAssignableFrom(f.getValueType())) {
                        data.put(f.getCode(), resultSet.getBoolean(f.getCode().substring(FIELD_PREFIX.length())));
                    }
                    else {
                        assert false : "Unrecognised field type: "+f.toString();
                    }
                }

                if (getTissueSampleDocumentField() == null) {
                    throw new ConnectionException("Tissue Sample Document Field not set.");
                }
                if (getSpecimenDocumentField() == null) {
                    throw new ConnectionException("Specimen Document Field not set.");
                }

                TableFimsSample sample = new TableFimsSample(getSearchAttributes(), getTaxonomyAttributes(), data, getTissueSampleDocumentField().getCode(), getSpecimenDocumentField().getCode());
                if(callback != null) {
                    callback.add(new TissueDocument(sample), Collections.<String, Object>emptyMap());
                }
                else {
                    samples.add(sample);
                }
            }
            resultSet.close();
            if(connection == null) {
                throw new SQLException("You are not connected to the database");
            }
            return samples;
        } catch (SQLException e) {
            throw new ConnectionException(e);
        }
    }

    public int getTotalNumberOfSamples() throws ConnectionException {
        String query = "SELECT count(*) FROM "+tableName;
        try {
            Statement statement = createStatement();

            ResultSet resultSet = statement.executeQuery(query);
            resultSet.next();
            return resultSet.getInt(1);

        } catch (SQLException e) {
            throw new ConnectionException(e.getMessage(), e);
        }
    }

    @Override
    protected List<List<String>> getProjectLists() throws DatabaseServiceException {
        List<String> projectColumns = new ArrayList<String>();
        for (DocumentField field : getProjectFields()) {
            projectColumns.add(field.getCode().replace(FIELD_PREFIX, ""));
        }

        List<List<String>> lists = new ArrayList<List<String>>();
        Statement select = null;
        try {
            select = createStatement();
            String columnList = StringUtilities.join(",", projectColumns);
            ResultSet resultSet = select.executeQuery("SELECT " + columnList + " FROM " + tableName + " GROUP BY " + columnList);
            while(resultSet.next()) {
                List<String> forRow = new ArrayList<String>();
                for (String columnName : projectColumns) {
                    String projectName = resultSet.getString(columnName);
                    if(projectName.trim().length() == 0) {
                        continue;
                    }
                    forRow.add(projectName);
                }
                if(!forRow.isEmpty()) {
                    lists.add(forRow);
                }
            }
            resultSet.close();
            return lists;
        } catch (SQLException e) {
            throw new DatabaseServiceException(e, "Failed to get list of projects: " + e.getMessage(), false);
        } finally {
            SqlUtilities.cleanUpStatements(select);
        }
    }
}
