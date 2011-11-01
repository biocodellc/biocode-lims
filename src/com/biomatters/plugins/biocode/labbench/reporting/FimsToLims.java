package com.biomatters.plugins.biocode.labbench.reporting;

import com.biomatters.plugins.biocode.labbench.BiocodeService;
import com.biomatters.plugins.biocode.labbench.FimsSample;
import com.biomatters.plugins.biocode.labbench.ConnectionException;
import com.biomatters.plugins.biocode.labbench.reaction.CycleSequencingCocktail;
import com.biomatters.plugins.biocode.labbench.reaction.PCRCocktail;
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
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
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
    public static final String FIMS_DEFINITION_TABLE = "fims_definition";
    public static final String FIMS_VALUES_TABLE = "fims_values";
    public static final String FIMS_DATE_TABLE = "fims_date";
    private List<Options.OptionValue> lociOptionValues;
    private List<String> loci;
    private List<SimpleListener> fimsTableChangedListeners = new ArrayList<SimpleListener>();
    private Map<String, String> friendlyNameMap = new HashMap<String, String>();
    private boolean limsHasFimsValues;
    private Date dateLastCopied;
    private List<String> primerNames;
    private List<String> revPrimerNames;

    public FimsToLims(BiocodeService service) throws SQLException{
        this.fims = service.getActiveFIMSConnection();
        this.lims = service.getActiveLIMSConnection();
        popuplateHasFimsLimsValues();
        initialiseCocktailMaps(service.getPCRCocktails(), service.getCycleSequencingCocktails());
        updateEverything();
    }

    void updateEverything() throws SQLException {
        populateLoci();
        populateFriendlyNameMap();
        populateFimsFields();
        populateDateLastCopied();
        populatePrimerNames();
    }

    private void populatePrimerNames() throws SQLException{
        primerNames = getAllPrimers(true);
        revPrimerNames = getAllPrimers(false);
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
        lociOptionValues = new ArrayList<Options.OptionValue>();
        loci = new ArrayList<String>();
        lociOptionValues.add(new Options.OptionValue("all", "All..."));
        String sql = "SELECT DISTINCT(locus) FROM workflow";
        Statement statement = lims.getConnection().createStatement();
        ResultSet resultSet = statement.executeQuery(sql);
        while(resultSet.next()) {
            lociOptionValues.add(new Options.OptionValue(resultSet.getString(1), resultSet.getString(1)));
            loci.add(resultSet.getString(1));
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

    private void popuplateHasFimsLimsValues() throws SQLException {
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
                limsHasFimsValues =  true;
                return;
            }
        }
        limsHasFimsValues =false;
    }

    private void populateDateLastCopied() throws SQLException {
        if(!limsHasFimsValues()) {
            dateLastCopied = new Date(0);
            return;
        }
        String sql = "SELECT value FROM "+FIMS_DATE_TABLE;
        Statement statement = lims.getConnection().createStatement();
        ResultSet resultSet = statement.executeQuery(sql);
        while(resultSet.next()) {
            Date date = resultSet.getDate(1);
            dateLastCopied = new Date(date.getTime());
            resultSet.close();
            return;
        }
        dateLastCopied = new Date(0);
    }

    /**
     * Note: this list includes all loci in {@link #getLoci()}, but also includes an entry for "All" loci...
     * @return
     */
    public List<Options.OptionValue> getLociOptionValues() {
        return lociOptionValues;
    }

    public List<String> getLoci() {
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

    private void populateFimsFields() throws SQLException {
        String sql = "DESCRIBE "+FIMS_VALUES_TABLE;
        List<DocumentField> results = new ArrayList<DocumentField>();
        if(!limsHasFimsValues) {
            return;
        }
        ResultSet resultsSet = lims.getConnection().createStatement().executeQuery(sql);
        while(resultsSet.next()) {
        DocumentField field = SqlUtilities.getDocumentField(resultsSet);
            if(field != null) {
                field = new DocumentField(getFriendlyName(field.getName()), field.getDescription(), field.getCode(), field.getValueType(), field.isDefaultVisible(), field.isEditable());
                results.add(field);
            }
        }
        fimsFields = results;
    }



    public boolean limsHasFimsValues(){
        return limsHasFimsValues;
    }

    public Date getDateLastCopied() {
        return dateLastCopied;
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

    private BiMap<String, Integer> pcrCocktailMap;
    private BiMap<String, Integer> sequencingCocktailMap;

    private void initialiseCocktailMaps(List<PCRCocktail> pcrCocktails, List<CycleSequencingCocktail> cycleSequencingCocktails) {
        pcrCocktailMap = HashBiMap.create();
        for(PCRCocktail cocktail : pcrCocktails) {
            pcrCocktailMap.put(cocktail.getName(), cocktail.getId());
        }

        sequencingCocktailMap = HashBiMap.create();
        for(CycleSequencingCocktail cocktail : cycleSequencingCocktails) {
            sequencingCocktailMap.put(cocktail.getName(), cocktail.getId());
        }
    }

    public int getCocktailId(String tableName, String cocktailName) {
        BiMap<String, Integer> map;
        if(tableName.equals("pcr")) {
            map = pcrCocktailMap;
        }
        else {
            map = sequencingCocktailMap;
        }
        return map.get(cocktailName);
    }


    public String getCocktailName(String tableName, int cocktailId) {
        BiMap<String, Integer> map;
        if(tableName.equals("pcr")) {
            map = pcrCocktailMap;
        }
        else {
            map = sequencingCocktailMap;
        }
        return map.inverse().get(cocktailId);
    }


    public void createFimsTable(final ProgressListener listener) throws ConnectionException {
        BiocodeService service = BiocodeService.getInstance();
        if(!service.isLoggedIn() || listener.isCanceled()) {
            return;
        }

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
            updateEverything();
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

    private List<DocumentField> fimsFields;

    public List<DocumentField> getFimsFields() {
        if(!limsHasFimsValues())  {
            return Arrays.asList(new DocumentField("none", "None", "None", String.class, false, false));
        }
        return new ArrayList<DocumentField>(fimsFields);
    }

    public DocumentField getFimsField(String name) {
        List<DocumentField> fimsFields = getFimsFields();
        for(DocumentField f : fimsFields) {
            if(f.getCode().equals(name)) {
                return f;
            }
        }
        return null;
    }

    public DocumentField getLimsField(String name) {
        List<DocumentField> limsFields = LIMSConnection.getSearchAttributes();
        for(DocumentField field : limsFields) {
            if(field.getCode().equals(name)) {
                return field;
            }
        }
        return null;
    }

    public DocumentField getFimsOrLimsField(String name) {
        DocumentField fimsField = getFimsField(name);
        if(fimsField != null) {
            return fimsField;
        }
        return getLimsField(name);
    }

    public FIMSConnection getFimsConnection() {
        return fims;
    }

    private List<String> getAllPrimers(boolean forward) throws SQLException{
        String primerFieldName = (forward ? "p" : "revP") + "rName";
        String sql = "SELECT distinct (" + primerFieldName + ") FROM pcr";
        PreparedStatement statement = getLimsConnection().getConnection().prepareStatement(sql);
        ResultSet resultSet = statement.executeQuery();
        List<String> primerNames = new ArrayList<String>();
        while(resultSet.next()) {
            primerNames.add(resultSet.getString(primerFieldName).trim());
        }
        return primerNames;
    }

    public List<String> getForwardPrimerNames() {
        return new ArrayList<String>(primerNames);
    }

    public List<String> getReversePrimerNames() {
        return new ArrayList<String>(revPrimerNames);
    }
}
