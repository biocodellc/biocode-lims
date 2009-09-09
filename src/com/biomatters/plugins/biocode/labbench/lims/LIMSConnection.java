package com.biomatters.plugins.biocode.labbench.lims;

import com.biomatters.geneious.publicapi.components.Dialogs;
import com.biomatters.geneious.publicapi.databaseservice.AdvancedSearchQueryTerm;
import com.biomatters.geneious.publicapi.databaseservice.BasicSearchQuery;
import com.biomatters.geneious.publicapi.databaseservice.CompoundSearchQuery;
import com.biomatters.geneious.publicapi.databaseservice.Query;
import com.biomatters.geneious.publicapi.documents.Condition;
import com.biomatters.geneious.publicapi.documents.DocumentField;
import com.biomatters.geneious.publicapi.plugin.Options;
import com.biomatters.geneious.publicapi.utilities.StringUtilities;
import com.biomatters.geneious.publicapi.utilities.ThreadUtilities;
import com.biomatters.plugins.biocode.labbench.*;
import com.biomatters.plugins.biocode.labbench.plates.GelImage;
import com.biomatters.plugins.biocode.labbench.plates.Plate;
import com.biomatters.plugins.biocode.labbench.reaction.CycleSequencingReaction;
import com.biomatters.plugins.biocode.labbench.reaction.ExtractionReaction;
import com.biomatters.plugins.biocode.labbench.reaction.PCRReaction;
import com.biomatters.plugins.biocode.labbench.reaction.Reaction;

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
    private static final int EXPECTED_SERVER_VERSION = 3;
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
        driver = BiocodeService.getDriver();
        //connect to the LIMS
        Properties properties = new Properties();
        properties.put("user", LIMSOptions.getValueAsString("username"));
        properties.put("password", ((PasswordOption)LIMSOptions.getOption("password")).getPassword());
        try {
            DriverManager.setLoginTimeout(20);
            connection = driver.connect("jdbc:mysql://"+LIMSOptions.getValueAsString("server")+":"+LIMSOptions.getValueAsString("port"), properties);
            Statement statement = connection.createStatement();
            statement.execute("USE labbench");
            ResultSet resultSet = statement.executeQuery("SELECT * FROM databaseversion LIMIT 1");
            if(!resultSet.next()) {
                throw new ConnectionException("Your LIMS database appears to be corrupt.  Please contact your systems administrator for assistance.");
            }
            else {
                int version = resultSet.getInt("version");
                if(version != EXPECTED_SERVER_VERSION) {
                    throw new ConnectionException("The server you are connecting to is running an "+(version > EXPECTED_SERVER_VERSION ? "newer" : "older")+" version of the LIMS database ("+version+") than this plugin was designed for ("+EXPECTED_SERVER_VERSION+").  Please contact your systems administrator for assistance.");
                }
            }
            resultSet.close();
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

    public Set<Integer> deleteRecords(String tableName, String term, Iterable ids) throws SQLException{
        List<String> terms = new ArrayList<String>();
        int count = 0;
        for(Object id : ids) {
            count++;
            terms.add(term+"="+id);
        }

        if(count == 0) {
            return Collections.emptySet();
        }

        String termString = StringUtilities.join(" OR ", terms);

        Set<Integer> plateIds = new HashSet<Integer>();
        if(tableName.equals("extraction") || tableName.equals("pcr") || tableName.equals("cyclesequencing")) {
            PreparedStatement getPlatesStatement = connection.prepareStatement("SELECT plate FROM "+tableName+" WHERE "+termString);
            ResultSet resultSet = getPlatesStatement.executeQuery();
            while(resultSet.next()) {
                plateIds.add(resultSet.getInt("plate"));
            }
        }

        PreparedStatement deleteStatement = connection.prepareStatement("DELETE FROM "+tableName+" WHERE "+termString);
        deleteStatement.executeUpdate();

        return plateIds;
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

    public void executeUpdate(String sql) throws TransactionException {
        Savepoint savepoint = null;
        try {
            connection.setAutoCommit(false);
            savepoint = connection.setSavepoint();
            for(String s : sql.split("\n")) {
                PreparedStatement statement = connection.prepareStatement(s);
                statement.execute();
                statement.close();
            }
            connection.commit();
        }
        catch(SQLException ex) {
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

    public List<DocumentField> getSearchAttributes() {
        return Arrays.asList(
                new DocumentField("Plate Name (LIMS)", "", "plate.name", String.class, true, false),
                new DocumentField("Workflow Name", "", "workflow.name", String.class, true, false),
                DocumentField.createEnumeratedField(new String[] {"Extraction", "PCR", "CycleSequencing"}, "Plate type", "", "plate.type", true, false)
        );
    }

    public List<WorkflowDocument> getMatchingWorkflowDocuments(Query query, List<FimsSample> samples) throws SQLException{
        List<? extends Query> refinedQueries;
        CompoundSearchQuery.Operator operator;

        if(query instanceof BasicSearchQuery) {
            query = generateAdvancedQueryFromBasicQuery(query);
        }

        if(query instanceof CompoundSearchQuery) {
            refinedQueries = removeFields(((CompoundSearchQuery)query).getChildren(), Arrays.asList("plate.name", "plate.type"));
            operator = ((CompoundSearchQuery)query).getOperator();
        }
        else {
            refinedQueries = removeFields(Arrays.asList(query), Arrays.asList("plate.name", "plate.type"));
            operator = CompoundSearchQuery.Operator.AND;
        }
        if((samples == null || samples.size() == 0) && refinedQueries.size() == 0) {
            return Collections.emptyList();
        }

        StringBuilder sql = new StringBuilder("SELECT * FROM workflow LEFT JOIN cyclesequencing ON cyclesequencing.workflow = workflow.id " +
                "LEFT JOIN pcr ON pcr.workflow = workflow.id " +
                "LEFT JOIN extraction ON workflow.extractionId = extraction.id " +
                "LEFT JOIN plate ON (plate.id = extraction.plate OR plate.id = pcr.plate OR plate.id = cyclesequencing.plate) "+
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
            if(refinedQueries.size() > 0) {
                sql.append(" OR ");
            }
        }
        if(refinedQueries.size() > 0) {
            somethingToSearch = true;
            if(refinedQueries.size() > 0) {
                somethingToSearch = true;
                sql.append("(");
                sql.append(queryToSql(refinedQueries, operator, new ArrayList<Object>()));
                sql.append(")");
            }
        }
        if(!somethingToSearch) {
            return Collections.emptyList();
        }

        //attach the values to the query
        System.out.println(sql.toString());
        PreparedStatement statement = connection.prepareStatement(sql.toString());
        int position = 1;
        if(samples != null && samples.size() > 0) {
            for(FimsSample sample : samples) {
                statement.setString(position, sample.getId());
                position++;
            }
        }
        for (Query q : refinedQueries) {
            if(q instanceof AdvancedSearchQueryTerm) {
                AdvancedSearchQueryTerm aq = (AdvancedSearchQueryTerm)q;
                Class fclass = aq.getField().getValueType();
                Object[] queryValues = aq.getValues();
                for (int j = 0; j < queryValues.length; j++) {
                    if(Integer.class.isAssignableFrom(fclass)) {
                        statement.setInt(position, (Integer)queryValues[j]);
                    }
                    else if(Double.class.isAssignableFrom(fclass)) {
                        statement.setDouble(position, (Double)queryValues[j]);
                    }
                    else if(String.class.isAssignableFrom(fclass)) {
                        QueryTermSurrounder ts = getQueryTermSurrounder(aq);
                        statement.setString(position, ts.getPrepend()+queryValues[j].toString().replace("\"", "")+ts.getAppend());
                    }
                    else {
                        throw new SQLException("You have a field parameter with an invalid type: "+aq.getField().getName()+", "+fclass.getCanonicalName());
                    }
                    position++;
                }
            }
        }
        ResultSet resultSet = statement.executeQuery();
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
        resultSet.close();
        return new ArrayList<WorkflowDocument>(workflowDocs.values());
    }

    private List<? extends Query> removeFields(List<? extends Query> queries, List<String>  codesToIgnore) {
        if(queries == null) {
            return Collections.emptyList();
        }
        List<Query> returnList = new ArrayList<Query>();
        for(Query q : queries) {
            if(q instanceof AdvancedSearchQueryTerm) {
                if(!codesToIgnore.contains(((AdvancedSearchQueryTerm)q).getField().getCode())) {
                    returnList.add(q);
                }
            }
            else if (q != null) {
                returnList.add(q);
            }
        }

        return returnList;
    }

//    private AdvancedSearchQueryTerm getField(List<? extends Query> queries, String  code) {
//        if(queries == null) {
//            return null;
//        }
//        AdvancedSearchQueryTerm returnValue = null;
//        for(Query q : queries) {
//            if(q instanceof AdvancedSearchQueryTerm) {
//                AdvancedSearchQueryTerm advancedSearchQueryTerm = (AdvancedSearchQueryTerm) q;
//                if(code.equals(advancedSearchQueryTerm.getField().getCode())) {
//                    returnValue = advancedSearchQueryTerm;
//                }
//            }
//        }
//
//        return returnValue;
//    }

    private String queryToSql(List<? extends Query> queries, CompoundSearchQuery.Operator operator, List<Object> inserts) {
        StringBuilder sql = new StringBuilder();
        String mainJoin;
        switch(operator) {
            case AND:
                mainJoin = "AND";
                break;
            default:
                mainJoin = "OR";
        }
        for (int i = 0; i < queries.size(); i++) {
            if(queries.get(i) instanceof AdvancedSearchQueryTerm) {
                AdvancedSearchQueryTerm q = (AdvancedSearchQueryTerm)queries.get(i);
                QueryTermSurrounder termSurrounder = getQueryTermSurrounder(q);
                sql.append(" "+ q.getField().getCode() +" "+ termSurrounder.getJoin() +" ");

                Object[] queryValues = q.getValues();
                for (int j = 0; j < queryValues.length; j++) {
                    Object value = queryValues[j];
                    String valueString = value.toString();
                    valueString = termSurrounder.getPrepend()+valueString+termSurrounder.getAppend();
                    if(value instanceof String) {
                        inserts.add(valueString);
                    }
                    else {
                        inserts.add(value);
                    }
                    sql.append("?");
                    if(i < queryValues.length-1) {
                        sql.append(" AND ");
                    }
                }
            }
            if(i < queries.size()-1) {
                sql.append(" "+mainJoin);
            }
        }
        return sql.toString();
    }

    public List<PlateDocument> getMatchingPlateDocuments(Query query, List<WorkflowDocument> workflowDocuments) throws SQLException{
        List<? extends Query> refinedQueries;
        CompoundSearchQuery.Operator operator;
        Set<Integer> plateIds = new HashSet<Integer>();

        if(query instanceof BasicSearchQuery) {
            query = generateAdvancedQueryFromBasicQuery(query);
        }

        if(query instanceof CompoundSearchQuery) {
            refinedQueries = removeFields(((CompoundSearchQuery)query).getChildren(), Arrays.asList("workflow.name"));
            operator = ((CompoundSearchQuery)query).getOperator();
        }
        else {
            refinedQueries = removeFields(Arrays.asList(query), Arrays.asList("workflow.name"));
            operator = CompoundSearchQuery.Operator.AND;
        }
        if((workflowDocuments == null || workflowDocuments.size() == 0) && refinedQueries.size() == 0) {
            return Collections.emptyList();
        }
        StringBuilder sql = new StringBuilder("SELECT * FROM plate LEFT JOIN cyclesequencing ON cyclesequencing.plate = plate.id " +
                "LEFT JOIN pcr ON pcr.plate = plate.id " +
                "LEFT JOIN extraction ON extraction.plate = plate.id " +
                "LEFT JOIN workflow ON (workflow.extractionId = extraction.id OR workflow.id = pcr.workflow OR workflow.id = cyclesequencing.workflow) " +
                "WHERE");

        if (workflowDocuments != null) {
            for(WorkflowDocument doc : workflowDocuments) {
                for(int i=0; i < doc.getNumberOfParts(); i++) {
                    WorkflowDocument.ReactionPart p = (WorkflowDocument.ReactionPart)doc.getPart(i);
                    Reaction reaction = p.getReaction();
                    plateIds.add(reaction.getPlateId());
                }
            }
        }
        if(plateIds.size() > 0) {
            sql.append(" (");
            for (Iterator<Integer> it = plateIds.iterator(); it.hasNext();) {
                Integer intg = it.next();
                sql.append(" plate.id=" + intg);
                if(it.hasNext()) {
                    sql.append(" OR");
                }
            }
            sql.append(")");
        }
        if(refinedQueries.size() > 0) {
            if(plateIds.size() > 0) {
                sql.append(" OR (");
            }
            else{
                sql.append(" (");
            }

            sql.append(queryToSql(refinedQueries, operator, new ArrayList<Object>()));

            sql.append(")");
        }
        System.out.println(sql.toString());
        PreparedStatement statement = connection.prepareStatement(sql.toString());

        int position = 1;
        for (Query q : refinedQueries) {
            if(q instanceof AdvancedSearchQueryTerm) {
                AdvancedSearchQueryTerm aq = (AdvancedSearchQueryTerm)q;
                Class fclass = aq.getField().getValueType();
                Object[] queryValues = aq.getValues();
                for (int j = 0; j < queryValues.length; j++) {
                    if(Integer.class.isAssignableFrom(fclass)) {
                        statement.setInt(position, (Integer)queryValues[j]);
                    }
                    else if(Double.class.isAssignableFrom(fclass)) {
                        statement.setDouble(position, (Double)queryValues[j]);
                    }
                    else if(String.class.isAssignableFrom(fclass)) {
                        QueryTermSurrounder ts = getQueryTermSurrounder(aq);
                        statement.setString(position, ts.getPrepend()+queryValues[j].toString().replace("\"", "")+ts.getAppend());
                    }
                    else {
                        throw new SQLException("You have a field parameter with an invalid type: "+aq.getField().getName()+", "+fclass.getCanonicalName());
                    }
                    position++;
                }
            }
        }

        ResultSet resultSet = statement.executeQuery();
        Map<Integer, Plate> plateMap = new HashMap<Integer, Plate>();
        List<ExtractionReaction> extractionReactions = new ArrayList<ExtractionReaction>();
        List<PCRReaction> pcrReactions = new ArrayList<PCRReaction>();
        List<CycleSequencingReaction> cyclesequencingReactions = new ArrayList<CycleSequencingReaction>();
        List<Integer> returnedPlateIds = new ArrayList<Integer>();
        int count = 0;
        while(resultSet.next()) {
            count++;
            Plate plate;
            int plateId = resultSet.getInt("plate.id");
            if(plateMap.get(plateId) == null) {
                returnedPlateIds.add(plateId);
                plate = new Plate(resultSet);
                plateMap.put(plate.getId(), plate);
            }
            else {
                plate = plateMap.get(plateId);
            }
            Reaction reaction = plate.addReaction(resultSet);
            if(reaction == null) {
                //do nothing
            }
            else if(reaction instanceof ExtractionReaction) {
                extractionReactions.add((ExtractionReaction)reaction);
            }
            else if(reaction instanceof PCRReaction) {
                pcrReactions.add((PCRReaction)reaction);
            }
            else if(reaction instanceof CycleSequencingReaction) {
                cyclesequencingReactions.add((CycleSequencingReaction)reaction);
            }
        }
        System.out.println("count="+count);
        final StringBuilder totalErrors = new StringBuilder("");
        if(extractionReactions.size() > 0) {
            String extractionErrors = extractionReactions.get(0).areReactionsValid(extractionReactions);
            if(extractionErrors != null) {
                totalErrors.append(extractionErrors+"\n");
            }
        }
        if(pcrReactions.size() > 0) {
            String pcrErrors = pcrReactions.get(0).areReactionsValid(pcrReactions);
            if(pcrErrors != null) {
                totalErrors.append(pcrErrors+"\n");
            }
        }
        if(cyclesequencingReactions.size() > 0) {
            String cyclesequencingErrors = cyclesequencingReactions.get(0).areReactionsValid(cyclesequencingReactions);
            if(cyclesequencingErrors != null) {
                totalErrors.append(cyclesequencingErrors+"\n");
            }
        }
        if(totalErrors.length() > 0) {
            Runnable runnable = new Runnable() {
                public void run() {
                    Dialogs.showMessageDialog("Geneious has detected the following possible errors in your database.  Please contact your system administrator for asistance.\n\n"+totalErrors, "Database errors detected", null, Dialogs.DialogIcon.WARNING);
                }
            };
            ThreadUtilities.invokeNowOrLater(runnable);
        }

        List<PlateDocument> docs = new ArrayList<PlateDocument>();
        getGelImagesForPlates(plateMap.values());
        for(Plate plate : plateMap.values()) {
            docs.add(new PlateDocument(plate));
        }

        return docs;

    }

    private Query generateAdvancedQueryFromBasicQuery(Query query) {
        String value = ((BasicSearchQuery)query).getSearchText();
        List<DocumentField> searchFields = getSearchAttributes();
        List<Query> queryTerms = new ArrayList<Query>();
        for(DocumentField field : searchFields) {
            if(String.class.isAssignableFrom(field.getValueType())) {
                if(field.isEnumeratedField()) {
                    boolean hasValue = false;
                    for(String s : field.getEnumerationValues()) {
                        if(s.equalsIgnoreCase(value)) {
                            hasValue = true;
                        }
                    }
                    if(!hasValue)
                        continue;
                }
                queryTerms.add(Query.Factory.createFieldQuery(field, Condition.CONTAINS, value));
            }
        }
        query = Query.Factory.createOrQuery(queryTerms.toArray(new Query[queryTerms.size()]), Collections.EMPTY_MAP);
        return query;
    }

    public void getGelImagesForPlates(Collection<Plate> plates) throws SQLException {
        List<Integer> plateIds = new ArrayList<Integer>();
        for(Plate plate : plates) {
            plateIds.add(plate.getId());
        }

        Map<Integer, List<GelImage>> gelimages = getGelImages(plateIds);
        for(Plate plate : plates) {
            List<GelImage> gelimagesForPlate = gelimages.get(plate.getId());
            if(gelimagesForPlate != null) {
                plate.setImages(gelimagesForPlate);
            }
        }
    }

    private Map<Integer, List<GelImage>> getGelImages(Collection<Integer> plateIds) throws SQLException{
        if(plateIds == null || plateIds.size() == 0) {
            return Collections.EMPTY_MAP;
        }
        StringBuilder sql = new StringBuilder("SELECT * FROM gelimages WHERE (");
        for (Iterator<Integer> it = plateIds.iterator(); it.hasNext();) {
            Integer i = it.next();
            sql.append("gelimages.plate=" + i);
            if (it.hasNext()) {
                sql.append(" OR ");
            }
        }
        sql.append(")");
        PreparedStatement statement = connection.prepareStatement(sql.toString());
        ResultSet resultSet = statement.executeQuery();
        Map<Integer, List<GelImage>> map = new HashMap<Integer, List<GelImage>>();
        while(resultSet.next()) {
            GelImage image = new GelImage(resultSet);
            List<GelImage> imageList;
            List<GelImage> existingImageList = map.get(image.getPlate());
            if(existingImageList != null) {
                imageList = existingImageList;
            }
            else {
                imageList = new ArrayList<GelImage>();
                map.put(image.getPlate(), imageList);
            }
            imageList.add(image);
        }
        return map;
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
