package com.biomatters.plugins.biocode.labbench.reporting;

import com.biomatters.plugins.biocode.labbench.BiocodeService;
import com.biomatters.plugins.biocode.labbench.FimsSample;
import com.biomatters.plugins.biocode.labbench.ConnectionException;
import com.biomatters.plugins.biocode.labbench.reaction.ExtractionReaction;
import com.biomatters.plugins.biocode.labbench.reaction.Reaction;
import com.biomatters.plugins.biocode.labbench.lims.LIMSConnection;
import com.biomatters.plugins.biocode.labbench.fims.FIMSConnection;
import com.biomatters.plugins.biocode.labbench.fims.SqlUtilities;
import com.biomatters.geneious.publicapi.documents.DocumentField;
import com.biomatters.geneious.publicapi.documents.PluginDocument;
import com.biomatters.geneious.publicapi.documents.AnnotatedPluginDocument;
import com.biomatters.geneious.publicapi.utilities.StringUtilities;
import com.biomatters.geneious.publicapi.databaseservice.RetrieveCallback;
import com.biomatters.geneious.publicapi.plugin.Options;
import jebl.util.ProgressListener;

import java.sql.*;
import java.util.*;
import java.util.Date;

import org.virion.jam.util.SimpleListener;

/**
 * @author Steve
 * @version $Id$
 */
public class FimsToLims {

    private FIMSConnection fims;
    private LIMSConnection lims;
    private static final String FIMS_DEFINITION_TABLE = "fims_definition";
    private static final String FIMS_VALUES_TABLE = "fims_values";
    private static final String FIMS_DATE_TABLE = "fims_date";
    private List<Options.OptionValue> loci;
    private List<SimpleListener> fimsTableChangedListeners = new ArrayList<SimpleListener>();
    private Map<String, String> friendlyNameMap = new HashMap<String, String>();

    public FimsToLims(FIMSConnection fimsConnection, LIMSConnection limsConnection) throws SQLException{
        this.fims = fimsConnection;
        this.lims = limsConnection;
        populateLoci();
        populateFriendlyNameMap();
    }

    public String getTissueColumnId() {
        return getSqlColName(fims.getTissueSampleDocumentField().getCode());
    }

    public LIMSConnection getLimsConnection() {
        return lims;
    }

    public void addFimsTableChangedListener(SimpleListener listener) {
        this.fimsTableChangedListeners.add(listener);
    }

    private void populateLoci() throws SQLException {
        loci = new ArrayList<Options.OptionValue>();
        loci.add(new Options.OptionValue("all", "All..."));       
        String sql = "SELECT DISTINCT(locus) FROM workflow";
        Statement statement = lims.getConnection().createStatement();
        ResultSet resultSet = statement.executeQuery(sql);
        while(resultSet.next()) {
            loci.add(new Options.OptionValue(resultSet.getString(1), resultSet.getString(1)));
        }
    }

    private void populateFriendlyNameMap() throws SQLException {
        friendlyNameMap.clear();
        if(!limsHasFimsValues()) {
            return;
        }
        String sql = "SELECT * FROM "+FIMS_DEFINITION_TABLE;
        Statement statement = lims.getConnection().createStatement();
        ResultSet resultSet = statement.executeQuery(sql);
        while(resultSet.next()) {
            friendlyNameMap.put(resultSet.getString("field"), resultSet.getString("name"));
        }
    }

    public List<Options.OptionValue> getLoci() {
        return loci;
    }

    public String getFriendlyName(String fieldCode) {
        String value = friendlyNameMap.get(fieldCode);
        if(value != null) {
            return value;
        }
        for(Reaction.Type type : Reaction.Type.values()) {
            Reaction r = Reaction.getNewReaction(type);
            for (Iterator it = r.getDisplayableFields().iterator(); it.hasNext();) {
                DocumentField f = (DocumentField) it.next();
                if (f.getCode().equals(fieldCode)) {
                    return f.getName();
                }
            }
        }
        return fieldCode;
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
            if(FIMS_DATE_TABLE.equals(resultSet.getString(lims.isLocal() ? 3 : 1).toLowerCase())) {
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
        String sql = "SELECT value FROM "+FIMS_DATE_TABLE;
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
        if(fieldClass == null) {
            return lims.isLocal() ? "LONGVARCHAR" : "LONGTEXT";
        }
        if(String.class.isAssignableFrom(fieldClass)) {
            return lims.isLocal() ? "LONGVARCHAR" : "VARCHAR(128)";
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

            String dropDateTable = "DROP TABLE IF EXISTS " + FIMS_DATE_TABLE;
            Statement statement = limsConnection.createStatement();
            statement.executeUpdate(dropDateTable);

            String dropDefinitionTable = "DROP TABLE IF EXISTS " + FIMS_DEFINITION_TABLE;
            statement = limsConnection.createStatement();
            statement.executeUpdate(dropDefinitionTable);

            statement = limsConnection.createStatement();
            String dropFimsTable = "DROP TABLE IF EXISTS "+FIMS_VALUES_TABLE;
            statement.executeUpdate(dropFimsTable);

            final List<String> fieldsAndTypes = new ArrayList<String>();
            final List<String> fields = new ArrayList<String>();
            for(DocumentField f : fims.getSearchAttributes()) {
                String colName = getSqlColName(f.getCode());
                fieldsAndTypes.add(colName +" "+getColumnDefinition(f.getName().toLowerCase().contains("notes") ? null : f.getValueType()));
                fields.add(colName);
            }
            fieldsAndTypes.add("PRIMARY KEY ("+getSqlColName(fims.getTissueSampleDocumentField().getCode())+")");

            String createValuesTable = "CREATE TABLE "+FIMS_VALUES_TABLE+"("+ StringUtilities.join(", ", fieldsAndTypes)+")";
            System.out.println(createValuesTable);
            statement.executeUpdate(createValuesTable);
            final List<FimsSample> fimsSamples = new ArrayList<FimsSample>();

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

                    fimsSamples.add(fimsSample);
                    if(fimsSamples.size() >= 100) {
                        listener.setMessage("Copying record "+(count+1)+" of "+total);
                        listener.setProgress(((double)count)/total);
                        List<FimsSample> fimsSamplesToUseInCaseOfError = new ArrayList<FimsSample>(fimsSamples);
                        try {
                            copyFimsSet(fimsSamples, limsConnection, fields);
                        } catch (SQLException e) {
                            System.out.println("error at "+count+": "+e.getMessage());
                            for(FimsSample sample : fimsSamplesToUseInCaseOfError) { //try copy them one by one to find the error...
                                try {
                                    copyFimsSet(Arrays.asList(sample), limsConnection, fields);
                                } catch (SQLException e1) {
                                    System.out.println("error at "+count+": "+e.getMessage());
                                }
                            }

                            //todo: exception handling
                        }
                        finally {
                            fimsSamples.clear();
                        }
                    }
                    this.count++;

                }
            };

            fims.getAllSamples(callback);
            try {
                copyFimsSet(fimsSamples, limsConnection, fields);
                fimsSamples.clear();
            } catch (SQLException e) {
                System.out.println("error at end: "+e.getMessage());
                //todo: exception handling
            }
            if(listener.isCanceled()) {
                return;
            }

            String createDefinitionTable;
            if(lims.isLocal()) {
                createDefinitionTable = "CREATE TABLE " + FIMS_DEFINITION_TABLE + "(field VARCHAR(255) PRIMARY KEY IDENTITY,\n" +
                        "  name  LONGVARCHAR)";
            }
            else {
                createDefinitionTable = "CREATE TABLE " + FIMS_DEFINITION_TABLE + " (`field` varchar(255), `name` longtext, PRIMARY KEY  (`field`))";
            }
            statement.executeUpdate(createDefinitionTable);
            String fillDefinitionTable = "INSERT INTO " + FIMS_DEFINITION_TABLE + " (field, name) VALUES (?, ?)";
            PreparedStatement fillStatement = limsConnection.prepareStatement(fillDefinitionTable);
            for(DocumentField f : fims.getSearchAttributes()) {
                fillStatement.setString(1, getSqlColName(f.getCode()));
                fillStatement.setString(2, f.getName());
                fillStatement.executeUpdate();
            }

            String createDateTable = "CREATE TABLE " + FIMS_DATE_TABLE + " (`id` int(10) unsigned NOT NULL auto_increment, `value` timestamp NOT NULL default CURRENT_TIMESTAMP, PRIMARY KEY  (`id`))";

            if(lims.isLocal()) {
                createDateTable = "CREATE TABLE " + FIMS_DATE_TABLE + "(id INTEGER PRIMARY KEY IDENTITY,\n" +
                        "  value timestamp DEFAULT CURRENT_TIMESTAMP)";
            }
            System.out.println(createDateTable);
            statement.executeUpdate(createDateTable);

            String fillDateTable = "INSERT INTO " + FIMS_DATE_TABLE + " (value) VALUES (?)";
            fillStatement = limsConnection.prepareStatement(fillDateTable);
            fillStatement.setDate(1, new java.sql.Date(new Date().getTime()));
            fillStatement.executeUpdate();

            for(SimpleListener tableListener : fimsTableChangedListeners) {
                tableListener.objectChanged();
            }

            listener.setProgress(1.0);

        }
        catch(SQLException ex ){
            ex.printStackTrace();
            //todo: exception handling
        }

    }

    public void copyFimsSet(List<FimsSample> fimsSamples, Connection limsConnection, List<String> fields) throws SQLException {
        StringBuilder sql = new StringBuilder("INSERT INTO "+FIMS_VALUES_TABLE+"("+StringUtilities.join(", ", fields)+") VALUES ");
        for(int i=0; i < fimsSamples.size(); i++) {
            sql.append("("+StringUtilities.join(", ", Collections.nCopies(fields.size(), "?"))+")");
            if(i < fimsSamples.size()-1) {
                sql.append(", ");
            }
        }
        PreparedStatement statement = limsConnection.prepareStatement(sql.toString());
        int count = 1;
        for(FimsSample fimsSample : fimsSamples) {
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
        }
        statement.executeUpdate();
    }

    public static String getSqlColName(String code) {
        if(code.contains(":")) {
            code = code.substring(code.indexOf(":")+1);
        }
        String value = code.replace('.', '_');
        try {
            Integer.parseInt(""+value.charAt(0));
            value = "a"+value;
        } catch (NumberFormatException e) {} //do nothing
        return value;
    }

    public List<DocumentField> getFimsFields() throws SQLException{
        if(!limsHasFimsValues())  {
            return Arrays.asList(new DocumentField("none", "None", "None", String.class, false, false));
        }
        String sql = "DESCRIBE "+FIMS_VALUES_TABLE;
        List<DocumentField> results = new ArrayList<DocumentField>();
        ResultSet resultsSet = lims.getConnection().createStatement().executeQuery(sql);
        while(resultsSet.next()) {
        DocumentField field = SqlUtilities.getDocumentField(resultsSet);
            if(field != null) {
                field = new DocumentField(getFriendlyName(field.getName()), field.getDescription(), field.getCode(), field.getValueType(), field.isDefaultVisible(), field.isEditable());
                results.add(field);
            }
        }
        return results;
    }
}
