package com.biomatters.plugins.moorea.fims;

import com.biomatters.geneious.publicapi.databaseservice.Query;
import com.biomatters.geneious.publicapi.databaseservice.BasicSearchQuery;
import com.biomatters.geneious.publicapi.databaseservice.AdvancedSearchQueryTerm;
import com.biomatters.geneious.publicapi.databaseservice.CompoundSearchQuery;
import com.biomatters.geneious.publicapi.documents.DocumentField;
import com.biomatters.geneious.publicapi.documents.Condition;
import com.biomatters.geneious.publicapi.plugin.Options;
import com.biomatters.plugins.moorea.ConnectionException;
import com.biomatters.plugins.moorea.FimsSample;
import com.biomatters.plugins.moorea.MooreaLabBenchService;

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
        options.addStringOption("password", "Password", "");

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
        properties.put("password", options.getValueAsString("password"));
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
        fields.add(new DocumentField("Scientific Name", "", "biocode.ScientificName", String.class, true, false));
        fields.add(new DocumentField("Colloquial Name", "", "biocode.ColloquialName", String.class, true, false));
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

    public List<DocumentField> getCollectionAttributes() {
        List<DocumentField> fields = new ArrayList<DocumentField>();

        fields.add(new DocumentField("Tissue ID", "", "tissueId", String.class, true, false));

        fields.add(new DocumentField("Specimen ID", "", "biocode_tissue.bnhm_id", String.class, true, false));
        fields.add(new DocumentField("Catalog Number", "", "biocode.CatalogNumberNumeric", String.class, true, false));

        fields.add(new DocumentField("Plate Name", "", "biocode_tissue.format_name96", String.class, true, false));
        fields.add(new DocumentField("Well Number", "", "biocode_tissue.well_number96", String.class, true, false));
        fields.add(new DocumentField("Tissue Barcode", "", "biocode_tissue.tissue_barcode", String.class, true, false));

        fields.add(new DocumentField("BOLD ProcessID", "", "biocode_tissue.molecular_id", String.class, true, false));

        fields.add(new DocumentField("Project Name", "", "biocode_collecting_event.ProjectName", String.class, true, false));
        fields.add(new DocumentField("Collector", "", "biocode_collecting_event.Collector", String.class, true, false));
        fields.add(new DocumentField("Collection time", "", "biocode_collecting_event.CollectionTime", Date.class, true, false));

        fields.add(new DocumentField("Longitude", "", "biocode_collecting_event.DecimalLongitude", Integer.class, true, false));
        fields.add(new DocumentField("Latitude", "", "biocode_collecting_event.DecimalLatitude", Integer.class, true, false));

        fields.add(new DocumentField("Minimum Elevation", "", "biocode_collecting_event.MinElevationMeters", Integer.class, true, false));
        fields.add(new DocumentField("Maximum Elevation", "", "biocode_collecting_event.MaxElevationMeters", Integer.class, true, false));

        fields.add(new DocumentField("Minimum Depth", "", "biocode_collecting_event.MinDepthMeters", Integer.class, true, false));
        fields.add(new DocumentField("Maximum Depth", "", "biocode_collecting_event.MaxDepthMeters", Integer.class, true, false));

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

        queryBuilder.append(getQuerySQLString(query));
        

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
            join = " OR ";
            queryBuilder.append("(");
            boolean started = false;
            for (int i = 0; i < getSearchAttributes().size(); i++) {
                DocumentField field = getSearchAttributes().get(i);
                String fieldCode = field.getCode();
                if (!field.getValueType().equals(String.class) || fieldCode.equals("tissueId")) {
                    continue;
                }


                if (started) {
                    queryBuilder.append(join);
                }
                started = true;

                queryBuilder.append(fieldCode + " LIKE '%" + searchText + "%'");


            }
            queryBuilder.append(")");
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
            for (Iterator<? extends Query> it = cquery.getChildren().iterator(); it.hasNext();) {
                Query childQuery = it.next();

                queryBuilder.append(getQuerySQLString(childQuery));

                if(it.hasNext()) {
                    queryBuilder.append(join);
                }
            }

            queryBuilder.append(")");
        }
        return queryBuilder.toString();
    }
}
