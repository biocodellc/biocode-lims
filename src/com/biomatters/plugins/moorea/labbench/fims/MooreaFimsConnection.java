package com.biomatters.plugins.moorea.labbench.fims;

import com.biomatters.geneious.publicapi.databaseservice.AdvancedSearchQueryTerm;
import com.biomatters.geneious.publicapi.databaseservice.BasicSearchQuery;
import com.biomatters.geneious.publicapi.databaseservice.CompoundSearchQuery;
import com.biomatters.geneious.publicapi.databaseservice.Query;
import com.biomatters.geneious.publicapi.documents.Condition;
import com.biomatters.geneious.publicapi.documents.DocumentField;
import com.biomatters.geneious.publicapi.plugin.Options;
import com.biomatters.geneious.publicapi.utilities.StringUtilities;
import com.biomatters.plugins.moorea.labbench.ConnectionException;
import com.biomatters.plugins.moorea.labbench.FimsSample;
import com.biomatters.plugins.moorea.labbench.MooreaLabBenchService;
import com.biomatters.plugins.moorea.labbench.PasswordOption;

import java.sql.*;
import java.util.*;
import java.util.Date;

/**
 * Created by IntelliJ IDEA.
 * User: steve
 * Date: 12/05/2009
 * Time: 5:51:15 AM
 * To change this template use File | Settings | File Templates.
 */
public class MooreaFimsConnection extends FIMSConnection{

    private Driver driver;
    private Connection connection;

    public String getLabel() {
        return "Moorea FIMS";
    }

    public String getName() {
        return "moorea";
    }

    public String getDescription() {
        return "A connection to the Moorea FIMS database";
    }

    public Options getConnectionOptions() {
        Options options = new Options(this.getClass(), "mooreaFIMS");
        options.addStringOption("serverUrl", "Server", "darwin.berkeley.edu");
        options.addIntegerOption("serverPort", "Port", 3306, 0, Integer.MAX_VALUE);
        options.addStringOption("username", "Username", "");
        //options.addStringOption("password", "Password", "");
        PasswordOption password = new PasswordOption("password", "Password", "");
        options.addCustomOption(password);

        return options;
    }

    public void connect(Options options) throws ConnectionException {

        //instantiate the driver class
        try {
            driver = (Driver) MooreaLabBenchService.getDriverClass().newInstance();
        } catch (InstantiationException e) {
            throw new ConnectionException("Could not instantiate SQL driver.");
        } catch (IllegalAccessException e) {
            throw new ConnectionException("Could not access SQL driver.");
        }


        //connect
        Properties properties = new Properties();
        properties.put("user", options.getValueAsString("username"));
        properties.put("password", ((PasswordOption)options.getOption("password")).getPassword());
        try {
            DriverManager.setLoginTimeout(20);
            connection = driver.connect("jdbc:mysql://"+options.getValueAsString("serverUrl")+":"+options.getValueAsString("serverPort"), properties);
            Statement statement = connection.createStatement();
            statement.execute("USE biocode");
        } catch (SQLException e1) {
            throw new ConnectionException("Failed to connect to the LIMS database: "+e1.getMessage());
        }
    }

    public void disconnect() throws ConnectionException{
        if(connection != null) {
            try {
                connection.close();
            } catch (SQLException e) {
                throw new ConnectionException(e);
            }
            connection = null;
        }
    }

    public List<DocumentField> getTaxonomyAttributes() {
        List<DocumentField> fields = new ArrayList<DocumentField>();
        fields.add(new DocumentField("Kingdom", "", "biocode.Kingdom", String.class, false, false));
        fields.add(new DocumentField("Phylum", "", "biocode.Phylum", String.class, false, false));
        fields.add(new DocumentField("Sub-phylum", "", "biocode.Subphylum", String.class, false, false));
        fields.add(new DocumentField("Superclass", "", "biocode.Superclass", String.class, false, false));
        fields.add(new DocumentField("Class", "", "biocode.Class", String.class, false, false));
        fields.add(new DocumentField("Subclass", "", "biocode.Subclass", String.class, false, false));
        fields.add(new DocumentField("Infraclass", "", "biocode.Infraclass", String.class, false, false));
        fields.add(new DocumentField("Super-order", "", "biocode.Superorder", String.class, false, false));
        fields.add(new DocumentField("Order", "", "biocode.Ordr", String.class, false, false)); //ordr is not a typo
        fields.add(new DocumentField("Suborder", "", "biocode.Suborder", String.class, false, false));
        fields.add(new DocumentField("Infraorder", "", "biocode.Infraorder", String.class, false, false));
        fields.add(new DocumentField("Super-family", "", "biocode.Superfamily", String.class, false, false));
        fields.add(new DocumentField("Family", "", "biocode.Family", String.class, false, false));
        fields.add(new DocumentField("Subfamily", "", "biocode.Subfamily", String.class, false, false));
        fields.add(new DocumentField("Tribe", "", "biocode.Tribe", String.class, false, false));
        fields.add(new DocumentField("Subtribe", "", "biocode.Subtribe", String.class, false, false));
        fields.add(new DocumentField("Genus", "", "biocode.Genus", String.class, false, false));
        fields.add(new DocumentField("Subgenus", "", "biocode.Subgenus", String.class, false, false));
        fields.add(new DocumentField("Specific Epithet", "", "biocode.SpecificEpithet", String.class, false, false));
        fields.add(new DocumentField("Subspecific Epithet", "", "biocode.SubspecificEpithet", String.class, false, false));
        return fields;
    }

    public DocumentField getTissueSampleDocumentField() {
        return getCollectionAttributes().get(0);
    }

    public DocumentField getTissueBarcodeDocumentField() {
        return getCollectionAttributes().get(5);
    }

    @Override
    public DocumentField getPlateDocumentField() {
        return getCollectionAttributes().get(3);
    }

    @Override
    public DocumentField getWellDocumentField() {
        return getCollectionAttributes().get(4);
    }

    public List<DocumentField> getCollectionAttributes() {
        List<DocumentField> fields = new ArrayList<DocumentField>();

        fields.add(new DocumentField("Tissue ID", "", "tissueId", String.class, true, false));

        fields.add(new DocumentField("Specimen ID", "", "biocode_tissue.bnhm_id", String.class, false, false));
        fields.add(new DocumentField("Catalog Number", "", "biocode.CatalogNumberNumeric", String.class, false, false));
        fields.add(new DocumentField("Specimen Num Collector", "", "biocode.Specimen_Num_Collector", String.class, false, false));

        fields.add(new DocumentField("Plate Name (FIMS)", "", "biocode_tissue.format_name96", String.class, true, false));
        fields.add(new DocumentField("Well Number (FIMS)", "", "biocode_tissue.well_number96", String.class, true, false));
        fields.add(new DocumentField("Tissue Barcode", "", "biocode_tissue.tissue_barcode", String.class, true, false));

        fields.add(new DocumentField("BOLD ProcessID", "", "biocode_tissue.molecular_id", String.class, true, false));

        fields.add(new DocumentField("Project Name", "", "biocode_collecting_event.ProjectName", String.class, false, false));
        fields.add(new DocumentField("Collector", "", "biocode_collecting_event.Collector", String.class, true, false));
        fields.add(new DocumentField("Collection time", "", "biocode_collecting_event.CollectionTime", Date.class, true, false));
        fields.add(new DocumentField("Identified By", "", "biocode.IdentifiedBy", String.class, true, false));

        fields.add(DocumentField.ORGANISM_FIELD);
        fields.add(DocumentField.COMMON_NAME_FIELD);
        fields.add(new DocumentField("Lowest Taxon", "", "biocode.LowestTaxon", String.class, true, false));
        fields.add(new DocumentField("Lowest Taxon Level", "", "biocode.LowestTaxonLevel", String.class, true, false));

        fields.add(new DocumentField("Longitude", "", "biocode_collecting_event.DecimalLongitude", Integer.class, false, false));
        fields.add(new DocumentField("Latitude", "", "biocode_collecting_event.DecimalLatitude", Integer.class, false, false));

        fields.add(new DocumentField("Minimum Elevation", "", "biocode_collecting_event.MinElevationMeters", Integer.class, false, false));
        fields.add(new DocumentField("Maximum Elevation", "", "biocode_collecting_event.MaxElevationMeters", Integer.class, false, false));

        fields.add(new DocumentField("Minimum Depth", "", "biocode_collecting_event.MinDepthMeters", Integer.class, false, false));
        fields.add(new DocumentField("Maximum Depth", "", "biocode_collecting_event.MaxDepthMeters", Integer.class, false, false));

        return fields;
    }

    public List<DocumentField> getSearchAttributes() {
        List<DocumentField> fields = new ArrayList<DocumentField>();
        fields.addAll(getCollectionAttributes());
        fields.addAll(getTaxonomyAttributes());
        return fields;
    }

    public List<FimsSample> getMatchingSamples(Query query) throws ConnectionException{
        StringBuilder queryBuilder = new StringBuilder();
        

        queryBuilder.append("SELECT * FROM biocode, biocode_collecting_event, biocode_tissue WHERE biocode.bnhm_id = biocode_tissue.bnhm_id AND biocode.coll_eventID = biocode_collecting_event.EventID AND ");

        String sqlString = getQuerySQLString(query);
        if(sqlString == null) {
            return Collections.EMPTY_LIST;
        }
        queryBuilder.append(sqlString);
        

        String queryString = queryBuilder.toString();

        System.out.println(queryString);
        if(connection == null) {
            throw new IllegalStateException("Not connected!");
        }
        try {
            Statement statement = connection.createStatement();
            ResultSet resultSet = statement.executeQuery(queryString);
            List<FimsSample> samples = new ArrayList<FimsSample>();
            while(resultSet.next()){
                samples.add(new MooreaFimsSample(resultSet, this));
            }
            return samples;
        } catch (SQLException e) {
            e.printStackTrace(); //todo: exception handling
            throw new ConnectionException(e);
        }
    }

    private String getQuerySQLString(Query query) {
        String join = "";
        String prepend = "";
        String append = "";
        StringBuilder queryBuilder = new StringBuilder();
        if(query instanceof BasicSearchQuery) {
            BasicSearchQuery basicQuery = (BasicSearchQuery)query;
            String searchText = basicQuery.getSearchText();
            if(searchText == null || searchText.trim().length() == 0) {
                return null;
            }
            join = " OR ";
            queryBuilder.append("(");
            List<Query> queryList = new ArrayList<Query>();
            for (int i = 0; i < getSearchAttributes().size(); i++) {
                DocumentField field = getSearchAttributes().get(i);
                if (!field.getValueType().equals(String.class)) {
                    continue;
                }

                queryList.add(BasicSearchQuery.Factory.createFieldQuery(field, Condition.CONTAINS, searchText));
            }
            Query compoundQuery = CompoundSearchQuery.Factory.createOrQuery(queryList.toArray(new Query[queryList.size()]), Collections.EMPTY_MAP);
            return getQuerySQLString(compoundQuery);
        }
        else if(query instanceof AdvancedSearchQueryTerm) {
            AdvancedSearchQueryTerm aquery = (AdvancedSearchQueryTerm)query;
            String fieldCode = aquery.getField().getCode();

            if(aquery.getCondition() == Condition.STRING_LENGTH_GREATER_THAN) {
                return "LEN("+fieldCode+") > "+aquery.getValues()[0];
            }
            else if(aquery.getCondition() == Condition.STRING_LENGTH_LESS_THAN) {
                return "LEN("+fieldCode+") < "+aquery.getValues()[0];
            }


            switch(aquery.getCondition()) {
                case EQUAL:
                    join = "=";
                    break;
                case APPROXIMATELY_EQUAL:
                    join = "LIKE";
                    break;
                case BEGINS_WITH:
                    join = "LIKE";
                    append="%";
                    break;
                case ENDS_WITH:
                    join = "LIKE";
                    prepend = "%";
                    break;
                case CONTAINS:
                    join = "LIKE";
                    append = "%";
                    prepend = "%";
                    break;
                case GREATER_THAN:
                    join = ">";
                    break;
                case GREATER_THAN_OR_EQUAL_TO:
                    join = ">=";
                    break;
                case LESS_THAN:
                    join = "<";
                    break;
                case LESS_THAN_OR_EQUAL_TO:
                    join = "<=";
                    break;
                case NOT_CONTAINS:
                    join = "NOT LIKE";
                    append = "%";
                    prepend = "%";
                    break;
                case NOT_EQUAL:
                    join = "!=";
                    break;
                case IN_RANGE:
                    join = "BETWEEN";
                    break;
            }

            Object testSearchText = aquery.getValues()[0];
            if(testSearchText == null || testSearchText.toString().trim().length() == 0) {
                return null;
            }


            //special cases
            if(fieldCode.equals("tissueId")) {
                String[] tissueIdParts = aquery.getValues()[0].toString().split("\\.");
                if(tissueIdParts.length == 2) {
                    queryBuilder.append("(biocode_tissue.bnhm_id "+join+" '"+tissueIdParts[0]+"' AND biocode_tissue.tissue_num "+join+" "+tissueIdParts[1]+")");
                }
                else {
                    queryBuilder.append("biocode_tissue.bnhm_id "+join+" '"+aquery.getValues()[0]+"'");
                }
            }
            else if(fieldCode.equals("biocode_collecting_event.CollectionTime")) {
                Date date = (Date)aquery.getValues()[0];
                //String queryString = "(biocode_collecting_event.YearCollected "+join+")";
                Calendar cal = Calendar.getInstance();
                cal.setTime(date);
                int year = cal.get(Calendar.YEAR);
                int month = cal.get(Calendar.MONTH);
                int day = cal.get(Calendar.DAY_OF_MONTH);
                String queryString;
                switch(aquery.getCondition()) {
                    case EQUAL :
                    case NOT_EQUAL:
                        queryString = "(biocode_collecting_event.YearCollected "+join+" "+year+") AND (biocode_collecting_event.MonthCollected "+join+" "+month+") AND (biocode_collecting_event.DayCollected "+join+" "+day+") AND ";
                        break;
                    default :
                        queryString = "(biocode_collecting_event.YearCollected "+join+" "+year+") OR (biocode_collecting_event.YearCollected = "+year+" AND biocode_collecting_event.MonthCollected "+join+" "+month+") OR (biocode_collecting_event.YearCollected = "+year+" AND biocode_collecting_event.MonthCollected = "+month+" AND biocode_collecting_event.DayCollected "+join+" "+day+")";
                }
                queryBuilder.append(queryString);
            }
            else {
                if (fieldCode.equals(DocumentField.ORGANISM_FIELD.getCode())) {
                    fieldCode = "biocode.ScientificName"; //we use the standard organism field so we need to map it to the correct database id
                }
                else if (fieldCode.equals(DocumentField.COMMON_NAME_FIELD.getCode())) {
                    fieldCode = "biocode.ColloquialName"; //we use the standard common name field so we need to map it to the correct database id
                }
                queryBuilder.append(fieldCode +" "+ join +" ");

                Object[] queryValues = aquery.getValues();
                for (int i = 0; i < queryValues.length; i++) {
                    Object value = queryValues[i];
                    String valueString = value.toString();
                    valueString = prepend+valueString+append;
                    if(value instanceof String) {
                        valueString = "'"+valueString+"'";
                    }
                    queryBuilder.append(valueString);
                    if(i < queryValues.length-1) {
                        queryBuilder.append(" AND ");
                    }
                }
            }

        }
        else if(query instanceof CompoundSearchQuery) {
            CompoundSearchQuery cquery = (CompoundSearchQuery)query;
            CompoundSearchQuery.Operator operator = cquery.getOperator();
            switch(operator) {
                case OR:
                    join = " OR ";
                    break;
                case AND:
                    join = " AND ";
                    break;
            }

            queryBuilder.append("(");
            int count = 0;
            boolean firstTime = true;
            for (Iterator<? extends Query> it = cquery.getChildren().iterator(); it.hasNext();) {
                Query childQuery = it.next();

                String s = getQuerySQLString(childQuery);
                if(s == null) {
                    continue;
                }
                else if(!firstTime) {
                    queryBuilder.append(join);
                }
                firstTime = false;
                count ++;
                queryBuilder.append(s);
            }
            if(count == 0) {
                return null;
            }

            queryBuilder.append(")");
        }
        return queryBuilder.toString();
    }

    public Map<String, String> getTissueIdsFromExtractionBarcodes(List<String> extractionIds) throws ConnectionException{
        if(extractionIds == null || extractionIds.size() == 0) {
            return Collections.emptyMap();
        }

        StringBuilder query = new StringBuilder("SELECT biocode_extract.extract_barcode, biocode_tissue.bnhm_id, biocode_tissue.tissue_num FROM biocode_extract, biocode_tissue WHERE biocode_extract.bnhm_id = biocode_tissue.bnhm_id AND (");

        List<String> queryTerms = new ArrayList<String>();
        for(String s : extractionIds) {
            queryTerms.add("biocode_extract.extract_barcode = ?");
        }

        query.append(StringUtilities.join(" OR ", queryTerms));
        query.append(");");
        try {
            PreparedStatement statement = connection.prepareStatement(query.toString());
            for (int i = 0; i < extractionIds.size(); i++) {
                String s = extractionIds.get(i);
                statement.setString(i+1, s);
            }
            ResultSet resultSet = statement.executeQuery();
            Map<String, String> result = new HashMap<String, String>();
            while(resultSet.next()) {
                result.put(resultSet.getString("biocode_extract.extract_barcode"), resultSet.getString("biocode_tissue.bnhm_id")+"."+resultSet.getString("biocode_tissue.tissue_num"));
            }
            return result;
            
        } catch (SQLException e) {
            throw new ConnectionException("Error fetching tissue data from FIMS", e);
        }
    }
}
