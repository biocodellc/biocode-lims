package com.biomatters.plugins.biocode.labbench.lims;

import com.biomatters.geneious.publicapi.components.Dialogs;
import com.biomatters.geneious.publicapi.databaseservice.DatabaseServiceException;
import com.biomatters.geneious.publicapi.databaseservice.Query;
import com.biomatters.geneious.publicapi.databaseservice.RetrieveCallback;
import com.biomatters.geneious.publicapi.documents.AnnotatedPluginDocument;
import com.biomatters.geneious.publicapi.documents.DocumentField;
import com.biomatters.geneious.publicapi.documents.PluginDocument;
import com.biomatters.geneious.publicapi.documents.URN;
import com.biomatters.geneious.publicapi.plugin.Options;
import com.biomatters.geneious.publicapi.utilities.StringUtilities;
import com.biomatters.plugins.biocode.labbench.BiocodeCallback;
import com.biomatters.plugins.biocode.labbench.BiocodeService;
import com.biomatters.plugins.biocode.labbench.ConnectionException;
import com.biomatters.plugins.biocode.labbench.FimsSample;
import com.biomatters.plugins.biocode.labbench.fims.FIMSConnection;
import com.biomatters.plugins.biocode.labbench.reaction.CycleSequencingCocktail;
import com.biomatters.plugins.biocode.labbench.reaction.PCRCocktail;
import com.biomatters.plugins.biocode.labbench.reaction.Reaction;
import com.biomatters.plugins.biocode.labbench.reporting.PrimerSet;
import com.biomatters.plugins.biocode.utilities.SqlUtilities;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import jebl.util.ProgressListener;
import org.virion.jam.util.SimpleListener;

import java.sql.*;
import java.util.*;
import java.util.Date;

/**
 * @author Steve
 */
public class FimsToLims {

    private FIMSConnection fims;
    private LIMSConnection lims;
    private static final String FIMS_DEFINITION_TABLE = "fims_definition";
    public static final String FIMS_VALUES_TABLE = "fims_values";
    public static final String FIMS_DATE_TABLE = "fims_date";
    private List<Options.OptionValue> lociOptionValues;
    private List<String> loci;
    private List<SimpleListener> fimsTableChangedListeners = new ArrayList<SimpleListener>();
    private Map<String, String> friendlyNameMap = new HashMap<String, String>();
    private boolean limsHasFimsValues;
    private Date dateLastCopied;
    private List<PrimerSet> pcrPrimers;
    private List<PrimerSet> pcrRevPrimers;
    private List<PrimerSet> sequencingPrimers;
    private List<PrimerSet> sequencingRevPrimers;

    public FimsToLims(BiocodeService service) throws SQLException, DatabaseServiceException {
        this.fims = service.getActiveFIMSConnection();
        this.lims = service.getActiveLIMSConnection();
        populateHasFimsLimsValues();
        initialiseCocktailMaps(service.getPCRCocktails(), service.getCycleSequencingCocktails());
        updateEverything();
    }

    void updateEverything() throws SQLException {
        populateHasFimsLimsValues();
        populateLoci();
        populateFriendlyNameMap();
        populateFimsFields();
        populateDateLastCopied();
        populatePrimers();
        populateDateLastCopied();
    }

    private void populatePrimers() throws SQLException{
        pcrPrimers = getAllPrimers(Reaction.Type.PCR, true);
        pcrRevPrimers = getAllPrimers(Reaction.Type.PCR, false);
        sequencingPrimers = getAllPrimers(Reaction.Type.CycleSequencing, true);
        sequencingRevPrimers = getAllPrimers(Reaction.Type.CycleSequencing, false);
    }

    public String getTissueColumnId() {
        return getSqlColName(fims.getTissueSampleDocumentField().getCode(), getLimsConnection().isLocal());
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
        String sql = "SELECT DISTINCT(locus) FROM workflow";
        Statement statement = lims.createStatement();
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
        Statement statement = lims.createStatement();
        ResultSet resultSet = statement.executeQuery(sql);
        while(resultSet.next()) {
            String field = resultSet.getString("field").toLowerCase();
            if(lims.isLocal() && field.startsWith("\"") && field.endsWith("\"")) {
                field = field.substring(1, field.indexOf('"', field.length()-1));
            }
            friendlyNameMap.put(field, resultSet.getString("name"));
        }
    }

    private void populateHasFimsLimsValues() throws SQLException {
        String sql;
        if(lims.isLocal()) {
            sql = "SELECT * FROM INFORMATION_SCHEMA.SYSTEM_TABLES";
        }
        else {
            sql = "SHOW TABLES";
        }
        Statement statement = lims.createStatement();
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
        Statement statement = lims.createStatement();
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
     * Note: this list includes all loci in {@link #getLoci()}, but may also include an entry for "All" loci...
     * @return
     * @param includeAll
     */
    public List<Options.OptionValue> getLociOptionValues(boolean includeAll) {
        ArrayList<Options.OptionValue> lociValues = new ArrayList<Options.OptionValue>(lociOptionValues);
        if(includeAll) {
            lociValues.add(0, new Options.OptionValue("all", "All..."));
        }
        return lociValues;
    }

    public List<String> getLoci() {
        return loci;
    }

    public String getFriendlyName(String fieldCode) {
        String value = friendlyNameMap.get(fieldCode.toLowerCase());
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
        if(!limsHasFimsValues) {
            return;
        }
        String sql = "DESCRIBE "+FIMS_VALUES_TABLE;
        List<DocumentField> results = new ArrayList<DocumentField>();
        ResultSet resultsSet;
        if(lims.isLocal()) {
            resultsSet = lims.getMetaData().getColumns(null, null, null, null);
        }
        else {
            resultsSet = lims.createStatement().executeQuery(sql);
        }
        while(resultsSet.next()) {
            if(lims.isLocal() && !resultsSet.getString(3).equalsIgnoreCase(FIMS_VALUES_TABLE)) {
                continue;
            }
            DocumentField field = SqlUtilities.getDocumentField(resultsSet, lims.isLocal());
            if(field != null) {
                field = new DocumentField(getFriendlyName(field.getName()), field.getDescription(), field.getCode(), field.getValueType(), field.isDefaultVisible(), field.isEditable());
                results.add(field);
            }
        }
        if(results.size() == 0) {
            limsHasFimsValues = false;
            throw new SQLException("Could not get information about the FIMS data stored in the LIMS");
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
            return lims.isLocal() ? "LONGVARCHAR" : "VARCHAR(255)";
        }
        if(String.class.isAssignableFrom(fieldClass)) {
            return lims.isLocal() ? "LONGVARCHAR" : "VARCHAR(255)";
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
        return lims.isLocal() ? "LONGVARCHAR" : "VARCHAR(255)";
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


    public void createFimsTable(final ProgressListener progress) throws ConnectionException {
        BiocodeService service = BiocodeService.getInstance();
        if(!service.isLoggedIn() || progress.isCanceled()) {
            return;
        }

        final BiocodeCallback listener = new BiocodeCallback(progress);
        BiocodeService.getInstance().registerCallback(listener);
        try {
            listener.setIndeterminateProgress();


            String dropDateTable = "DROP TABLE IF EXISTS " + FIMS_DATE_TABLE;
            Statement statement = lims.createStatement();
            statement.executeUpdate(dropDateTable);

            String dropDefinitionTable = "DROP TABLE IF EXISTS " + FIMS_DEFINITION_TABLE;
            statement = lims.createStatement();
            statement.executeUpdate(dropDefinitionTable);

            statement = lims.createStatement();
            String dropFimsTable = "DROP TABLE IF EXISTS "+FIMS_VALUES_TABLE;
            statement.executeUpdate(dropFimsTable);

            final List<String> fieldsAndTypes = new ArrayList<String>();
            final List<String> fields = new ArrayList<String>();
            for(DocumentField f : fims.getSearchAttributes()) {
                String colName = getSqlColName(f.getCode(), getLimsConnection().isLocal());
                fieldsAndTypes.add(colName +" "+getColumnDefinition(f.getName().toLowerCase().contains("notes") ? null : f.getValueType()));
                fields.add(colName);
            }
            fieldsAndTypes.add("PRIMARY KEY ("+getSqlColName(fims.getTissueSampleDocumentField().getCode(), getLimsConnection().isLocal())+")");

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
                            copyFimsSet(fimsSamples, lims, fields);
                        } catch (SQLException e) {
                            System.out.println("error at "+count+": "+e.getMessage());
                            for(FimsSample sample : fimsSamplesToUseInCaseOfError) { //try copy them one by one to find the error...
                                try {
                                    copyFimsSet(Arrays.asList(sample), lims, fields);
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

            BiocodeService.getInstance().retrieve(Query.Factory.createExtendedQuery("",
                    BiocodeService.getSearchDownloadOptions(true, false, false, false, false)), callback, new URN[0]);
            try {
                copyFimsSet(fimsSamples, lims, fields);
                fimsSamples.clear();
            } catch (SQLException e) {
                System.out.println("error at end: "+e.getMessage());
                for(FimsSample sample : fimsSamples) { //try copy them one by one to find the error...
                    try {
                        copyFimsSet(Arrays.asList(sample), lims, fields);
                    } catch (SQLException e1) {
                        throw new ConnectionException("There was an error copying your FIMS values into the LIMS: "+e.getMessage(), e);
                    }
                }
            }
            if(listener.isCanceled()) {
                return;
            }

            String createDefinitionTable;
            if(lims.isLocal()) {
                createDefinitionTable = "CREATE TABLE " + FIMS_DEFINITION_TABLE + "(field VARCHAR(255) PRIMARY KEY,\n" +
                        "  name  LONGVARCHAR)";
            }
            else {
                createDefinitionTable = "CREATE TABLE " + FIMS_DEFINITION_TABLE + " (`field` varchar(255), `name` longtext, PRIMARY KEY  (`field`))";
            }
            statement.executeUpdate(createDefinitionTable);
            String fillDefinitionTable = "INSERT INTO " + FIMS_DEFINITION_TABLE + " (field, name) VALUES (?, ?)";
            PreparedStatement fillStatement = lims.createStatement(fillDefinitionTable);
            for(DocumentField f : fims.getSearchAttributes()) {
                fillStatement.setString(1, getSqlColName(f.getCode(), getLimsConnection().isLocal()));
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
            fillStatement = lims.createStatement(fillDateTable);
            fillStatement.setDate(1, new java.sql.Date(new Date().getTime()));
            fillStatement.executeUpdate();

            for(SimpleListener tableListener : fimsTableChangedListeners) {
                tableListener.objectChanged();
            }
            updateEverything();
        }
        catch(SQLException ex ){
            ex.printStackTrace();
            Dialogs.showMessageDialog("There was an error copying your FIMS data into the LIMS: "+ex.getMessage());

        } catch (DatabaseServiceException e) {
            e.printStackTrace();
            Dialogs.showMessageDialog("There was an error copying your FIMS data into the LIMS: "+e.getMessage());
        } finally {
            listener.setProgress(1.0);
            BiocodeService.getInstance().unregisterCallback(listener);
        }

    }

    public static String quoteSqlColumn(String colName, boolean isLocal) {
        if(!isLocal) {
            return colName;
        }
        return "\""+colName+"\"";
    }

    public void copyFimsSet(List<FimsSample> fimsSamples, LIMSConnection limsConnection, List<String> fields) throws SQLException {
        StringBuilder sql = new StringBuilder("INSERT INTO "+FIMS_VALUES_TABLE+"("+StringUtilities.join(", ", fields)+") VALUES ");
        for(int i=0; i < fimsSamples.size(); i++) {
            sql.append("("+StringUtilities.join(", ", Collections.nCopies(fields.size(), "?"))+")");
            if(i < fimsSamples.size()-1) {
                sql.append(", ");
            }
        }
        PreparedStatement statement = limsConnection.createStatement(sql.toString());
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

    public static String getSqlColName(String code, boolean isLocal) {
        if(code.length() == 0) {
            return "a";
        }
        if(code.contains(":")) {
            code = code.substring(code.indexOf(":")+1);
        }
        String value = code.replace('.', '_');
        value = value.replace('\'', '_');
        value = value.replaceAll("\\s", "_");
        try {
            Integer.parseInt(""+value.charAt(0));
            value = "a"+value;
        } catch (NumberFormatException e) {} //do nothing
        return quoteSqlColumn(value, isLocal);
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

    private List<PrimerSet> getAllPrimers(Reaction.Type reactionType, boolean forward) throws SQLException{
        return lims.isLocal() ? getAllPrimersLocal(reactionType, forward) : getAllPrimersRemote(reactionType, forward);
    }

    private List<PrimerSet> getAllPrimersLocal(Reaction.Type reactionType, boolean forward) throws SQLException{
        boolean pcr;
        if(reactionType == Reaction.Type.PCR) {
            pcr = true;
        }
        else if(reactionType == Reaction.Type.CycleSequencing) {
            pcr = false;
        }
        else {
            throw new IllegalArgumentException("You may only call this method with PCR or CycleSequencing reactions");
        }

        String primerFieldName;
        String primerSequenceName;
        String tableName;
        String extraWhere;
        if(pcr) {
            primerFieldName = (forward ? "p" : "revP") + "rName";
            primerSequenceName = (forward ? "P" : "revP") + "rSequence";
            tableName = "pcr";
            extraWhere = "";
        }
        else {
            primerFieldName = "primerName";
            primerSequenceName = "primerSequence";
            tableName = "cyclesequencing";
            extraWhere = " WHERE direction="+(forward ? "'forward'" : "'reverse'");
        }
        String sql1 = "SELECT DISTINCT("+primerFieldName+") FROM "+tableName+extraWhere;
        PreparedStatement statement = getLimsConnection().createStatement(sql1);
        ResultSet resultSet = statement.executeQuery();
        List<PrimerSet> primers = new ArrayList<PrimerSet>();
        while(resultSet.next()) {
            String primerName = resultSet.getString(1);
            String sql2 = "SELECT "+primerSequenceName+" FROM "+tableName+" WHERE "+primerFieldName+"=?";
            System.out.println(sql2.replace("?", "'"+primerName+"'"));
            PreparedStatement statement2 = getLimsConnection().createStatement(sql2);
            statement2.setString(1, primerName);
            ResultSet resultSet2 = statement2.executeQuery();
            String primerSequence = "";
            if(resultSet2.next()) {
                primerSequence = resultSet2.getString(1);
                PrimerSet.Primer primer = new PrimerSet.Primer(primerName, primerSequence.trim());
                addPrimerToSet(primers, primer);
            }
        }
        return primers;
    }

    private List<PrimerSet> getAllPrimersRemote(Reaction.Type reactionType, boolean forward) throws SQLException {
        boolean pcr;
        if(reactionType == Reaction.Type.PCR) {
            pcr = true;
        }
        else if(reactionType == Reaction.Type.CycleSequencing) {
            pcr = false;
        }
        else {
            throw new IllegalArgumentException("You may only call this method with PCR or CycleSequencing reactions");
        }

        String primerFieldName;
        String primerSequenceName;
        String tableName;
        String extraWhere;
        if(pcr) {
            primerFieldName = (forward ? "p" : "revP") + "rName";
            primerSequenceName = (forward ? "P" : "revP") + "rSequence";
            tableName = "pcr";
            extraWhere = "";
        }
        else {
            primerFieldName = "primerName";
            primerSequenceName = "primerSequence";
            tableName = "cyclesequencing";
            extraWhere = " WHERE direction="+(forward ? "'forward'" : "'reverse'");
        }

        String sql = "SELECT "+primerFieldName+", "+primerSequenceName+" FROM "+tableName+extraWhere+" GROUP BY "+primerFieldName;
        System.out.println(sql);
        PreparedStatement statement = getLimsConnection().createStatement(sql);
        ResultSet resultSet = statement.executeQuery();
        List<PrimerSet> primers = new ArrayList<PrimerSet>();
        while(resultSet.next()) {
            PrimerSet.Primer primer = new PrimerSet.Primer(resultSet.getString(primerFieldName).trim(), resultSet.getString(primerSequenceName).trim());
            addPrimerToSet(primers, primer);
        }
        return primers;
    }

    private void addPrimerToSet(List<PrimerSet> primers, PrimerSet.Primer primer) {
        boolean found = false;
        for(PrimerSet set : primers) {
            if(set.contains(primer)) {
                set.addPrimer(primer);
                found = true;
                break;
            }
        }
        if(!found) {
            primers.add(new PrimerSet(primer));
        }
    }

    public List<PrimerSet> getForwardPcrPrimers() {
        return new ArrayList<PrimerSet>(pcrPrimers);
    }

    public List<PrimerSet> getReversePcrPrimers() {
        return new ArrayList<PrimerSet>(pcrRevPrimers);
    }

    public List<PrimerSet> getForwardSequencingPrimers() {
        return new ArrayList<PrimerSet>(sequencingPrimers);
    }

    public List<PrimerSet> getReverseSequencingPrimers() {
        return new ArrayList<PrimerSet>(sequencingRevPrimers);
    }
}
