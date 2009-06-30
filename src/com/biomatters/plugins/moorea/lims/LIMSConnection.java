package com.biomatters.plugins.moorea.lims;

import com.biomatters.plugins.moorea.*;
import com.biomatters.plugins.moorea.plates.Plate;
import com.biomatters.plugins.moorea.reaction.Reaction;
import com.biomatters.geneious.publicapi.plugin.Options;
import com.biomatters.geneious.publicapi.databaseservice.Query;
import com.biomatters.geneious.publicapi.databaseservice.CompoundSearchQuery;
import com.biomatters.geneious.publicapi.databaseservice.AdvancedSearchQueryTerm;

import java.sql.*;
import java.util.*;

/**
 * Created by IntelliJ IDEA.
 * User: steve
 * Date: 27/05/2009
 * Time: 6:28:38 AM
 * To change this template use File | Settings | File Templates.
 */
public class LIMSConnection {
    Driver driver;
    Connection connection;

    public Options getConnectionOptions() {
        Options LIMSOptions = new Options(this.getClass());
        LIMSOptions.addStringOption("server", "Server Address", "");
        LIMSOptions.addIntegerOption("port", "Port", 3306, 1, Integer.MAX_VALUE);
        LIMSOptions.addStringOption("database", "Database Name", "labbench");
        LIMSOptions.addStringOption("username", "Username", "");
        LIMSOptions.addCustomOption(new PasswordOption("password", "Password", ""));
        return LIMSOptions;
    }


    public void connect(Options LIMSOptions) throws ConnectionException {
        driver = MooreaLabBenchService.getDriver();
        //connect to the LIMS
        Properties properties = new Properties();
        properties.put("user", LIMSOptions.getValueAsString("username"));
        properties.put("password", ((PasswordOption)LIMSOptions.getOption("password")).getPassword());
        try {
            connection = driver.connect("jdbc:mysql://"+LIMSOptions.getValueAsString("server")+":"+LIMSOptions.getValueAsString("port"), properties);
            Statement statement = connection.createStatement();
            statement.execute("USE labbench");
        } catch (SQLException e1) {
            throw new ConnectionException(e1);
        }
    }

    public void disconnect() throws ConnectionException{
        if(connection != null) {
            try {
                connection.close();
            } catch (SQLException e) {
                throw new ConnectionException(e);
            }
        }
    }

    public ResultSet executeQuery(String sql) throws TransactionException{
        try {
            PreparedStatement statement = connection.prepareStatement(sql);
            return statement.executeQuery();
        }
        catch(SQLException ex) {
            throw new TransactionException("Could not execute LIMS query", ex);
        }
    }

    public void executeUpdate(String sql) throws TransactionException{
        Savepoint savepoint = null;
        try {
            connection.setAutoCommit(false);
            savepoint = connection.setSavepoint();
            for(String s : sql.split("\n")) {
                PreparedStatement statement = connection.prepareStatement(s);
                statement.execute();
            }
            connection.commit();
        }
        catch(SQLException ex) {
            try {
                if(savepoint != null) {
                    connection.rollback(savepoint);
                }
            } catch (SQLException e) {
                throw new TransactionException("Could not execute LIMS update query", ex);
            }
            throw new TransactionException("Could not execute LIMS update query", ex);
        }
        finally {
            try {
                connection.setAutoCommit(true);
            } catch (SQLException ignore) {}
        }
    }

    public Connection getConnection() {
        return connection;
    }

    public List<WorkflowDocument> getMatchingWorkflowDocuments(CompoundSearchQuery query, List<FimsSample> samples) throws SQLException{
        StringBuilder sql = new StringBuilder("SELECT * FROM workflow LEFT JOIN cycleSequencing ON cycleSequencing.workflow = workflow.id " +
                "LEFT JOIN pcr ON pcr.workflow = workflow.id " +
                "LEFT JOIN extraction ON workflow.extractionId = extraction.id " +
                "LEFT JOIN plate ON (plate.id = extraction.plate OR plate.id = pcr.plate OR plate.id = cycleSequencing.plate) "+
                "WHERE ");

        boolean somethingToSearch = false;
        if(samples != null && samples.size() > 0) {
            somethingToSearch = true;
            sql.append("(");
            for(int i=0; i < samples.size(); i++) {
                sql.append(" extraction.sampleId=?");
                if(i != samples.size()-1) {
                    sql.append(" OR");
                }
            }
            sql.append(")");
            if(query != null && query.getChildren().size() > 0) {
                sql.append(" AND ");
            }
        }
        if(query != null && query.getChildren().size() > 0) {
            somethingToSearch = true;
            sql.append("(");
            String mainJoin;
            switch(query.getOperator()) {
                case AND:
                    mainJoin = "AND";
                    break;
                default:
                    mainJoin = "OR";
            }
            for (int i = 0; i < query.getChildren().size(); i++) {
                if(query.getChildren().get(i) instanceof AdvancedSearchQueryTerm) {
                    AdvancedSearchQueryTerm q = (AdvancedSearchQueryTerm)query.getChildren().get(i);
                    QueryTermSurrounder termSurrounder = getQueryTermSurrounder(q);
                    sql.append(" "+ q.getField().getCode() +" "+ termSurrounder.getJoin() +" ");

                    Object[] queryValues = q.getValues();
                    for (int j = 0; j < queryValues.length; j++) {
                        Object value = queryValues[j];
                        String valueString = value.toString();
                        valueString = termSurrounder.getPrepend()+valueString+termSurrounder.getAppend()+"?";
                        sql.append(valueString);
                        if(i < queryValues.length-1) {
                            sql.append(" AND ");
                        }
                    }
                }
                if(i < query.getChildren().size()-1) {
                    sql.append(" "+mainJoin);
                }
            }
            sql.append(")");
        }
        if(!somethingToSearch) {
            return Collections.EMPTY_LIST;
        }

        //attach the values to the query
        PreparedStatement statement = connection.prepareStatement(sql.toString());
        int position = 1;
        if(samples != null && samples.size() > 0) {
            for(FimsSample sample : samples) {
                statement.setString(position, sample.getId());
                position++;
            }
        }
        if(query != null && query.getChildren().size() > 0) {
            for (Query q : query.getChildren()) {
                if(q instanceof AdvancedSearchQueryTerm) {
                    AdvancedSearchQueryTerm aq = (AdvancedSearchQueryTerm)q;
                    Class fclass = aq.getField().getClass();
                    Object[] queryValues = aq.getValues();
                    for (int j = 0; j < queryValues.length; j++) {
                        if(Integer.class.isAssignableFrom(fclass)) {
                            statement.setInt(position, (Integer)queryValues[j]);
                        }
                        else if(Double.class.isAssignableFrom(fclass)) {
                            statement.setDouble(position, (Double)queryValues[j]);
                        }
                        else if(String.class.isAssignableFrom(fclass)) {
                            statement.setString(position, (String)queryValues[j]);
                        }
                        else {
                            throw new SQLException("You have a field parameter with an invalid type: "+aq.getField().getName()+", "+fclass.getCanonicalName());
                        }
                        position++;
                    }
                }
            }
        }
        ResultSet resultSet = statement.executeQuery(); //todo: this should have everything in it.  We need to split it up into the proper parts...
        Map<Integer, WorkflowDocument> workflowDocs = new HashMap<Integer, WorkflowDocument>();
        while(resultSet.next()) {
            int workflowId = resultSet.getInt("workflow.id");
            if(workflowDocs.get(workflowId) != null) {
                workflowDocs.get(workflowId).addRow(resultSet);    
            }
            else {
                WorkflowDocument doc = new WorkflowDocument(resultSet);
                workflowDocs.put(workflowId, doc);
            }
        }
        return new ArrayList<WorkflowDocument>(workflowDocs.values());
    }

    public List<PlateDocument> getMatchingPlateDocuments(CompoundSearchQuery query, List<WorkflowDocument> workflowDocuments) throws SQLException{
        if(workflowDocuments.size() == 0) {
            return Collections.EMPTY_LIST;
        }
        StringBuilder sql = new StringBuilder("SELECT * FROM plate LEFT JOIN cycleSequencing ON cycleSequencing.plate = plate.id " +
                "LEFT JOIN pcr ON pcr.plate = plate.id " +
                "LEFT JOIN extraction ON extraction.plate = plate.id " +
                "WHERE");

        Set<Integer> plateIds = new HashSet<Integer>();
        for(WorkflowDocument doc : workflowDocuments) {
            for(int i=0; i < doc.getNumberOfParts(); i++) {
                WorkflowDocument.ReactionPart p = (WorkflowDocument.ReactionPart)doc.getPart(i);
                Reaction reaction = p.getReaction();
                plateIds.add(reaction.getPlate());
            }
        }
        for (Iterator<Integer> it = plateIds.iterator(); it.hasNext();) {
            Integer intg = it.next();
            sql.append(" plate.id=" + intg);
            if(it.hasNext()) {
                sql.append(" OR");
            }
        }
        PreparedStatement statement = connection.prepareStatement(sql.toString());
        ResultSet resultSet = statement.executeQuery();
        Map<Integer, Plate> plateMap = new HashMap<Integer, Plate>();
        while(resultSet.next()) {
            Plate plate;
            int plateId = resultSet.getInt("plate.id");
            if(plateMap.get(plateId) == null) {
                plate = new Plate(resultSet);  
                plateMap.put(plate.getId(), plate);
            }
            else {
                plate = plateMap.get(plateId);
            }
            plate.addReaction(resultSet);
        }
        List<PlateDocument> docs = new ArrayList<PlateDocument>();
        for(Plate plate : plateMap.values()) {
            docs.add(new PlateDocument(plate));
        }

        return docs;

    }

    private static QueryTermSurrounder getQueryTermSurrounder(AdvancedSearchQueryTerm query) {
        String join = "";
        String append = "";
        String prepend = "";
        switch(query.getCondition()) {
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
        return new QueryTermSurrounder(prepend, append, join);
    }

    private static class QueryTermSurrounder{
        private final String prepend, append, join;

        private QueryTermSurrounder(String prepend, String append, String join) {
            this.prepend = prepend;
            this.append = append;
            this.join = join;
        }

        public String getPrepend() {
            return prepend;
        }

        public String getAppend() {
            return append;
        }

        public String getJoin() {
            return join;
        }
    }

}
