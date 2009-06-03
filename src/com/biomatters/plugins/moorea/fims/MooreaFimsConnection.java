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
            connection = driver.connect("jdbc:mysql://"+options.getValueAsString("serverUrl")+":"+options.getValueAsString("serverPort"), properties);
            Statement statement = connection.createStatement();
            statement.execute("USE biocode");
        } catch (SQLException e1) {
            throw new ConnectionException("Failed to connect to the LIMS database: "+e1.getMessage());
        }
    }

    public void disconnect() {
        if(connection != null) {
            try {
                connection.close();
            } catch (SQLException e) {
                e.printStackTrace();  //todo: exception handling
            }
            connection = null;
        }
    }

    public List<DocumentField> getFimsAttributes() {
        List<DocumentField> fields = new ArrayList<DocumentField>();

        fields.add(new DocumentField("Tissue ID", "", "biocode_tissue.seq_num", String.class, true, false));

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



        fields.add(new DocumentField("Scientific Name", "", "biocode.ScientificName", String.class, true, false));
        fields.add(new DocumentField("Colloquial Name", "", "biocode.ColloquialName", String.class, true, false));
        fields.add(new DocumentField("Kingdom", "", "biocode.Kingdom", String.class, true, false));
        fields.add(new DocumentField("Phylum", "", "biocode.Phylum", String.class, true, false));
        fields.add(new DocumentField("Sub-phylum", "", "biocode.Subphylum", String.class, true, false));
        fields.add(new DocumentField("Superclass", "", "biocode.Superclass", String.class, true, false));
        fields.add(new DocumentField("Class", "", "biocode.Class", String.class, true, false));
        fields.add(new DocumentField("Subclass", "", "biocode.Subclass", String.class, true, false));
        fields.add(new DocumentField("Infraclass", "", "biocode.Infraclass", String.class, true, false));
        fields.add(new DocumentField("Super-order", "", "biocode.Superorder", String.class, true, false));
        fields.add(new DocumentField("Order", "", "biocode.Ordr", String.class, true, false)); //ordr is not a typo
        fields.add(new DocumentField("Suborder", "", "biocode.Suborder", String.class, true, false));
        fields.add(new DocumentField("Infraorder", "", "biocode.Infraorder", String.class, true, false));
        fields.add(new DocumentField("Super-family", "", "biocode.Superfamily", String.class, true, false));
        fields.add(new DocumentField("Family", "", "biocode.Family", String.class, true, false));
        fields.add(new DocumentField("Subfamily", "", "biocode.Subfamily", String.class, true, false));
        fields.add(new DocumentField("Tribe", "", "biocode.Tribe", String.class, true, false));
        fields.add(new DocumentField("Subtribe", "", "biocode.Subtribe", String.class, true, false));
        fields.add(new DocumentField("Genus", "", "biocode.Genus", String.class, true, false));
        fields.add(new DocumentField("Subgenus", "", "biocode.Subgenus", String.class, true, false));
        fields.add(new DocumentField("Specific Epithet", "", "biocode.SpecificEpithet", String.class, true, false));
        fields.add(new DocumentField("Subspecific Epithet", "", "biocode.SubspecificEpithet", String.class, true, false));



        


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
                samples.add(new SQLFimsSample(resultSet, this));
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
            for (Iterator<DocumentField> it = getFimsAttributes().iterator(); it.hasNext();) {
                DocumentField field = it.next();
                if(!field.getValueType().equals(String.class)) {
                    continue;
                }

                queryBuilder.append(field.getCode()+" LIKE '"+searchText+"'");
                
                if(it.hasNext()) {
                    queryBuilder.append(join);
                }
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
                    join = "<>";
                    break;
                case IN_RANGE:
                    join = "BETWEEN";
                    break;
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
