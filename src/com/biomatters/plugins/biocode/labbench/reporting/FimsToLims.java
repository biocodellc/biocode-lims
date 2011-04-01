package com.biomatters.plugins.biocode.labbench.reporting;

import com.biomatters.plugins.biocode.labbench.BiocodeService;
import com.biomatters.plugins.biocode.labbench.FimsSample;
import com.biomatters.plugins.biocode.labbench.TissueDocument;
import com.biomatters.plugins.biocode.labbench.ConnectionException;
import com.biomatters.plugins.biocode.labbench.lims.LIMSConnection;
import com.biomatters.plugins.biocode.labbench.fims.FIMSConnection;
import com.biomatters.plugins.biocode.labbench.fims.SqlUtilities;
import com.biomatters.geneious.publicapi.documents.DocumentField;
import com.biomatters.geneious.publicapi.documents.PluginDocument;
import com.biomatters.geneious.publicapi.documents.AnnotatedPluginDocument;
import com.biomatters.geneious.publicapi.utilities.StringUtilities;
import com.biomatters.geneious.publicapi.databaseservice.RetrieveCallback;
import jebl.util.ProgressListener;

import java.sql.*;
import java.util.*;
import java.util.Date;

/**
 * @author Steve
 * @version $Id$
 */
public class FimsToLims {

    private FIMSConnection fims;
    private LIMSConnection lims;
    private static final String FIMS_DEFINITION_TABLE = "fims_definition";
    private static final String FIMS_VALUES_TABLE = "fims_values";

    public FimsToLims(FIMSConnection fimsConnection, LIMSConnection limsConnection) {
        this.fims = fimsConnection;
        this.lims = limsConnection;
    }

    public boolean limsHasFimsValues() throws SQLException{
        String sql;
        if(lims.isLocal()) {
            sql = "SELECT * FROM INFORMATION_SCHEMA.SYSTEM_TABLES";
        }
        else {
            sql = "SHOW TABLES";
        }
        Statement statement = lims.getConnection().createStatement();
        ResultSet resultSet = statement.executeQuery(sql);
        while(resultSet.next()) {
            if(FIMS_DEFINITION_TABLE.equals(resultSet.getString(lims.isLocal() ? 3 : 1).toLowerCase())) {
                resultSet.close();
                return true;
            }
        }
        return false;
    }

    public Date getDateLastCopied() throws SQLException{
        if(!limsHasFimsValues()) {
            return null;
        }
        String sql = "SELECT value FROM "+FIMS_DEFINITION_TABLE;
        Statement statement = lims.getConnection().createStatement();
        ResultSet resultSet = statement.executeQuery(sql);
        while(resultSet.next()) {
            Date date = resultSet.getDate(1);
            return new Date(date.getTime());
        }
        return new Date(0);
    }

    private String getColumnDefinition(Class fieldClass) {
        //todo: more types...
        if(String.class.isAssignableFrom(fieldClass)) {
            return lims.isLocal() ? "LONGVARCHAR" : "LONGTEXT";
        }
        if(Integer.class.isAssignableFrom(fieldClass)) {
            return "INTEGER";
        }
        if(Double.class.isAssignableFrom(fieldClass) || Float.class.isAssignableFrom(fieldClass)) {
            return "FLOAT";
        }
        if(Date.class.isAssignableFrom(fieldClass)) {
            return "DATE";
        }
        return lims.isLocal() ? "LONGVARCHAR" : "LONGTEXT";
    }


    public void createFimsTable(final ProgressListener listener) throws ConnectionException {
        BiocodeService service = BiocodeService.getInstance();
        if(!service.isLoggedIn() || listener.isCanceled()) {
            return;
        }

//        String dropDefinitionTable = "DROP TABLE IF EXISTS fims_definition";
//        Statement statement = service.getActiveLIMSConnection().getConnection().createStatement();
//        statement.executeUpdate(dropDefinitionTable);
//
//        String createDefinitionTable = "CREATE TABLE fims_definition(key  VARCHAR(999) PRIMARY KEY IDENTITY, value  VARCHAR(999) NOT NULL, type VARCHAR(99))";
//        statement.executeUpdate(createDefinitionTable);
//
//        for(DocumentField field : service.getActiveFIMSConnection().getSearchAttributes()){
//            String insertIntoDefinitionTable = "INSERT INTO fims_definition (key, value, type)"
//        }
        try {
            listener.setIndeterminateProgress();

            final Connection limsConnection = lims.getConnection();

            String dropDefinitionTable = "DROP TABLE IF EXISTS " + FIMS_DEFINITION_TABLE;
            Statement statement = limsConnection.createStatement();
            statement.executeUpdate(dropDefinitionTable);

            statement = limsConnection.createStatement();
            String dropFimsTable = "DROP TABLE IF EXISTS "+FIMS_VALUES_TABLE;
            statement.executeUpdate(dropFimsTable);

            final List<String> fieldsAndTypes = new ArrayList<String>();
            final List<String> fields = new ArrayList<String>();
            for(DocumentField f : fims.getSearchAttributes()) {
                String colName = getSqlColName(f.getCode());
                fieldsAndTypes.add(colName +" "+getColumnDefinition(f.getValueType()));
                fields.add(colName);
            }

            String createValuesTable = "CREATE TABLE "+FIMS_VALUES_TABLE+"("+ StringUtilities.join(", ", fieldsAndTypes)+")";
            System.out.println(createValuesTable);
            statement.executeUpdate(createValuesTable);

            RetrieveCallback callback = new RetrieveCallback(listener){
                protected void _add(PluginDocument document, Map<String, Object> searchResultProperties) {
                    handle((FimsSample)document);
                }

                protected void _add(AnnotatedPluginDocument document, Map<String, Object> searchResultProperties) {
                    handle((FimsSample)document.getDocumentOrCrash());
                }
                int total = fims.getTotalNumberOfSamples();
                int count = 0;
                private void handle(FimsSample fimsSample) {
                    listener.setMessage("Copying record "+(count+1)+" of "+total);
                    listener.setProgress(((double)count)/total);
                    String sql = "INSERT INTO "+FIMS_VALUES_TABLE+"("+StringUtilities.join(", ", fields)+") VALUES ("+StringUtilities.join(", ", Collections.nCopies(fieldsAndTypes.size(), "?"))+")";
                    try {
                        PreparedStatement statement = limsConnection.prepareStatement(sql);
                        int count = 1;
                        for(DocumentField f : fims.getSearchAttributes()) {
                            Object value = fimsSample.getFimsAttributeValue(f.getCode());
                            if(value == null) {
                                statement.setNull(count, Types.OTHER);
                            }
                            else {
                                statement.setObject(count, value);
                            }
                            count++;
                        }
                        this.count++;
                        statement.executeUpdate();
                    } catch (SQLException e) {
                        System.out.println("error at "+count+": "+e.getMessage());
                        //todo: exception handling
                    }


                }
            };

            fims.getAllSamples(callback);
            if(listener.isCanceled()) {
                return;
            }

            String createDefinitionTable = "CREATE TABLE " + FIMS_DEFINITION_TABLE + " (`id` int(10) unsigned NOT NULL auto_increment, `value` timestamp NOT NULL default CURRENT_TIMESTAMP, PRIMARY KEY  (`id`))";

            if(lims.isLocal()) {
                createDefinitionTable = "CREATE TABLE " + FIMS_DEFINITION_TABLE + "(id INTEGER PRIMARY KEY IDENTITY,\n" +
                        "  value timestamp DEFAULT CURRENT_TIMESTAMP)";
            }
            System.out.println(createDefinitionTable);
            statement.executeUpdate(createDefinitionTable);

            String fillDefinitionTable = "INSERT INTO " + FIMS_DEFINITION_TABLE + " (value) VALUES (?)";
            PreparedStatement fillStatement = limsConnection.prepareStatement(fillDefinitionTable);
            fillStatement.setDate(1, new java.sql.Date(new Date().getTime()));
            fillStatement.executeUpdate();

            listener.setProgress(1.0);

        }
        catch(SQLException ex ){
            ex.printStackTrace();
            //todo: exception handling
        }




    }

    private static String getSqlColName(String code) {
        String value = code.replace('.', '_');
        try {
            Integer.parseInt(""+value.charAt(0));
            value = "a"+value;
        } catch (NumberFormatException e) {} //do nothing
        return value;
    }

    public List<DocumentField> getFimsFields() throws SQLException{
        String sql = "DESCRIBE "+FIMS_VALUES_TABLE;
        List<DocumentField> results = new ArrayList<DocumentField>();
        ResultSet resultsSet = lims.getConnection().createStatement().executeQuery(sql);
        while(resultsSet.next()) {
        DocumentField field = SqlUtilities.getDocumentField(resultsSet);
            if(field != null) {
                results.add(field);
            }
        }
        return results;
    }
}
