package com.biomatters.plugins.biocode.labbench.lims;

import com.biomatters.geneious.publicapi.components.Dialogs;
import com.biomatters.geneious.publicapi.databaseservice.*;
import com.biomatters.geneious.publicapi.documents.AnnotatedPluginDocument;
import com.biomatters.geneious.publicapi.documents.Condition;
import com.biomatters.geneious.publicapi.documents.DocumentField;
import com.biomatters.geneious.publicapi.documents.DocumentUtilities;
import com.biomatters.geneious.publicapi.documents.sequence.DefaultNucleotideGraph;
import com.biomatters.geneious.publicapi.documents.sequence.NucleotideGraph;
import com.biomatters.geneious.publicapi.documents.sequence.SequenceDocument;
import com.biomatters.geneious.publicapi.implementations.sequence.DefaultNucleotideGraphSequence;
import com.biomatters.geneious.publicapi.implementations.sequence.DefaultNucleotideSequence;
import com.biomatters.geneious.publicapi.plugin.DocumentOperationException;
import com.biomatters.geneious.publicapi.plugin.Options;
import com.biomatters.geneious.publicapi.utilities.StringUtilities;
import com.biomatters.geneious.publicapi.utilities.ThreadUtilities;
import com.biomatters.plugins.biocode.assembler.annotate.AnnotateUtilities;
import com.biomatters.plugins.biocode.assembler.annotate.FimsData;
import com.biomatters.plugins.biocode.assembler.annotate.FimsDataGetter;
import com.biomatters.plugins.biocode.labbench.*;
import com.biomatters.plugins.biocode.labbench.plates.GelImage;
import com.biomatters.plugins.biocode.labbench.plates.Plate;
import com.biomatters.plugins.biocode.labbench.reaction.CycleSequencingReaction;
import com.biomatters.plugins.biocode.labbench.reaction.ExtractionReaction;
import com.biomatters.plugins.biocode.labbench.reaction.PCRReaction;
import com.biomatters.plugins.biocode.labbench.reaction.Reaction;
import com.biomatters.options.PasswordOption;

import java.sql.*;
import java.util.*;
import java.util.Date;
import java.text.DateFormat;
import java.text.SimpleDateFormat;

/**
 * Created by IntelliJ IDEA.
 * User: steve
 * Date: 27/05/2009
 * Time: 6:28:38 AM
 * To change this template use File | Settings | File Templates.
 */
public class LIMSConnection {
    public static final int EXPECTED_SERVER_VERSION = 7;
    Driver driver;
    Connection connection;
    Connection connection2;
    private LocalLIMS localLIMS;
    public static final DocumentField WORKFLOW_NAME_FIELD = new DocumentField("Workflow Name", "", "workflow.name", String.class, true, false);
    public static final DocumentField PLATE_TYPE_FIELD = DocumentField.createEnumeratedField(new String[] {"Extraction", "PCR", "CycleSequencing"}, "Plate type", "", "plate.type", true, false);
    public static final DocumentField PLATE_NAME_FIELD = new DocumentField("Plate Name (LIMS)", "", "plate.name", String.class, true, false);
    public static final DocumentField DATE_FIELD = new DocumentField("Last Modified (LIMS)", "", "date", Date.class, true, false);
    public static final DocumentField PLATE_DATE_FIELD = new DocumentField("Last Modified (LIMS plate)", "", "plate.date", Date.class, false, false);
    public static final DocumentField WORKFLOW_DATE_FIELD = new DocumentField("Last Modified (LIMS workflow)", "", "workflow.date", Date.class, false, false);
    public static final DocumentField WORKFLOW_LOCUS_FIELD = new DocumentField("Locus", "The locus of the workflow", "locus", String.class, true, false);
    private boolean isLocal;

    public Options getConnectionOptions() {
        Options LIMSOptions = new Options(this.getClass());

        Options remoteOptions = new Options(this.getClass());
        remoteOptions.addStringOption("server", "Server Address:", "");
        remoteOptions.addIntegerOption("port", "Port:", 3306, 1, Integer.MAX_VALUE);
        remoteOptions.addStringOption("database", "Database Name:", "labbench");
        remoteOptions.addStringOption("username", "Username:", "");
        remoteOptions.addCustomOption(new PasswordOption("password", "Password:", true));

        LIMSOptions.addChildOptions("remote", "Remote Server", "Connect to a LIMS database on a remote MySQL server", remoteOptions);

        Options localOptions = getLocalOptions();


        LIMSOptions.addChildOptions("local", "Local Database", "Create and connect to LIMS databases on your local computer", localOptions);


        LIMSOptions.addChildOptionsPageChooser("connectionType", "LIMS location", Collections.EMPTY_LIST, Options.PageChooserType.COMBO_BOX, false);

        return  LIMSOptions;
    }

    private Options getLocalOptions() {
        if(localLIMS == null) {
            localLIMS = new LocalLIMS();
            localLIMS.initialize(BiocodeService.getInstance().getDataDirectory());
        }
        Options localOptions = localLIMS.getConnectionOptions();
        
        return localOptions;
    }

    public boolean isLocal() {
        return isLocal;
    }


    public void connect(Options LIMSOptions) throws ConnectionException {
        if(LIMSOptions.getValueAsString("connectionType").equals("remote")) {
            driver = BiocodeService.getDriver();
            connectRemote(LIMSOptions.getChildOptions().get("remote"));
        }
        else {
            driver = BiocodeService.getLocalDriver();
            connectLocal(LIMSOptions.getChildOptions().get("local"), false);
        }
    }

    private void connectLocal(Options LIMSOptions, boolean alreadyAskedBoundUpgrade) throws ConnectionException {
        isLocal = true;
        connection = localLIMS.connect(LIMSOptions);
        try {
            ResultSet resultSet = connection.createStatement().executeQuery("SELECT * FROM databaseversion LIMIT 1");
            if(!resultSet.next()) {
                throw new ConnectionException("Your LIMS database appears to be corrupt.  Please contact your systems administrator for assistance.");
            }
            else {
                int version = resultSet.getInt("version");

                if(version < EXPECTED_SERVER_VERSION) {
                    if(alreadyAskedBoundUpgrade || Dialogs.showYesNoDialog("The LIMS database you are connecting to is written for an older version of this plugin.  Would you like to upgrade it?", "Old database", null, Dialogs.DialogIcon.QUESTION)) {
                        localLIMS.upgradeDatabase(LIMSOptions);
                        connectLocal(LIMSOptions, true);
                    }
                    else {
                        throw new ConnectionException("You need to upgrade your database, or choose another one to continue");
                    }
                }
                else if(version > EXPECTED_SERVER_VERSION) {
                    throw new ConnectionException("This database was written for a newer version of the LIMS plugin, and cannot be accessed");
                }
            }
        }
        catch(SQLException ex) {
            throw new ConnectionException(ex.getMessage(), ex);
        }
    }

    private void connectRemote(Options LIMSOptions) throws ConnectionException {
        isLocal = false;
        //connect to the LIMS
        Properties properties = new Properties();
        properties.put("user", LIMSOptions.getValueAsString("username"));
        properties.put("password", ((PasswordOption)LIMSOptions.getOption("password")).getPassword());
        try {
            DriverManager.setLoginTimeout(20);
            connection = driver.connect("jdbc:mysql://"+LIMSOptions.getValueAsString("server")+":"+LIMSOptions.getValueAsString("port"), properties);
            connection2 = driver.connect("jdbc:mysql://"+LIMSOptions.getValueAsString("server")+":"+LIMSOptions.getValueAsString("port"), properties);
            Statement statement = connection.createStatement();
            connection.createStatement().execute("USE "+LIMSOptions.getValueAsString("database"));
            connection2.createStatement().execute("USE "+LIMSOptions.getValueAsString("database"));
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
//        if(connection != null) {
//            Thread t = new Thread() {
//                public void run() {
//                    try {
//                        connection.close();
//                        isLocal = false;
//                    } catch (SQLException e) {
//                        System.out.println(e);
//                        e.printStackTrace();
//                    }
//                }
//            };
//            t.start();
//        }
        //we used to explicitly close the SQL connection, but this was causing crashes if the user logged out while a query was in progress.
        //now we remove all references to it and let the garbage collector close it when the queries have finished.
        connection = null;
        connection2 = null;
        isLocal = false;
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
            getPlatesStatement.close();
        }

        PreparedStatement deleteStatement = connection.prepareStatement("DELETE FROM "+tableName+" WHERE "+termString);
        deleteStatement.executeUpdate();
        deleteStatement.close();

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
                PLATE_NAME_FIELD,
                WORKFLOW_NAME_FIELD,
                PLATE_TYPE_FIELD,
                DATE_FIELD,
                PLATE_DATE_FIELD,
                WORKFLOW_DATE_FIELD,
                WORKFLOW_LOCUS_FIELD
        );
    }

    public List<AnnotatedPluginDocument> getMatchingAssemblyDocuments(final Collection<WorkflowDocument> workflows, RetrieveCallback callback) throws SQLException{
        String sql = "SELECT * FROM assembly WHERE (";
        List<String> terms = new ArrayList<String>();
        for(WorkflowDocument workflow : workflows) {
            terms.add("workflow="+workflow.getId());
        }
        sql = sql+StringUtilities.join(" OR ", terms)+")";

        Statement statement = connection.createStatement();
        statement.setFetchSize(1);
        try {
            final ResultSet resultSet = statement.executeQuery(sql);
            List<AnnotatedPluginDocument> resultDocuments = new ArrayList<AnnotatedPluginDocument>();
            while(resultSet.next()) {
                AnnotatedPluginDocument doc = createAssemblyDocument(resultSet);
                FimsDataGetter getter = new FimsDataGetter(){
                    public FimsData getFimsData(AnnotatedPluginDocument document) throws DocumentOperationException {
                        try {
                            for(WorkflowDocument workflow : workflows) {
                                if(workflow.getId() == resultSet.getInt("workflow")) {
                                    return new FimsData(workflow, null, null);
                                }
                            }
                        }
                        catch(SQLException ex) {
                            throw new DocumentOperationException("Could not get workflow id from assembly table: "+ex.getMessage());
                        }
                        return null;
                    }
                };
                AnnotateUtilities.annotateDocument(getter, new ArrayList<String>(), doc);
                resultDocuments.add(doc);
                if(callback != null) {
                    callback.add(doc, Collections.EMPTY_MAP);
                }
            }
            return resultDocuments;
        }
        catch (DocumentOperationException e) {
            e.printStackTrace();
            if(e.getCause() != null && e.getCause() instanceof SQLException) {
                throw (SQLException)e.getCause();
            }
            throw new SQLException(e.getMessage());
        } finally {
            statement.close();
        }
    }

    private AnnotatedPluginDocument createAssemblyDocument(ResultSet resultSet) throws SQLException{
        String qualities = resultSet.getString("confidence_scores");
        SequenceDocument sequence;
        if(qualities == null) {
            String name = resultSet.getString("extraction_id");
            sequence = new DefaultNucleotideSequence(name, "Assembly consensus sequence for "+name, resultSet.getString("consensus"), new Date(resultSet.getDate("date").getTime()));
        }
        else {
            String sequenceString = resultSet.getString("consensus");
            NucleotideGraph graph = DefaultNucleotideGraph.createNucleotideGraph(null, null, qualitiesFromString(qualities), sequenceString.length(), 0);
            String name = resultSet.getString("extraction_id");
            sequence = new DefaultNucleotideGraphSequence(name, "Assembly consensus sequence for "+name, sequenceString, new Date(resultSet.getDate("date").getTime()), graph);
        }
        AnnotatedPluginDocument doc = DocumentUtilities.createAnnotatedPluginDocument(sequence);
        //todo: add data as fields and notes...
        return doc;
    }

    private int[] qualitiesFromString(String qualString) {
        String[] values = qualString.split(",");
        int[] result = new int[values.length];
        for(int i=0; i < values.length; i++) {
            result[i] = Integer.parseInt(values[i]);
        }
        return result;
    }

    public List<WorkflowDocument> getMatchingWorkflowDocuments(Query query, Collection<FimsSample> samples, RetrieveCallback callback) throws SQLException{
        List<? extends Query> refinedQueries;
        CompoundSearchQuery.Operator operator;

        if(query instanceof BasicSearchQuery) {
            query = generateAdvancedQueryFromBasicQuery(query);
        }

        if(query instanceof CompoundSearchQuery) {
            refinedQueries = removeFields(((CompoundSearchQuery)query).getChildren(), Arrays.asList("plate.name", "plate.type", "plate.date"));
            operator = ((CompoundSearchQuery)query).getOperator();
        }
        else {
            refinedQueries = removeFields(Arrays.asList(query), Arrays.asList("plate.name", "plate.type", "plate.date"));
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
        ArrayList<Object> sqlValues = new ArrayList<Object>();
                        
        if(samples != null && samples.size() > 0) {
            somethingToSearch = true;
            sql.append("(");
            FimsSample[] samplesArray = samples.toArray(new FimsSample[samples.size()]);
            for(int i=0; i < samplesArray.length; i++) {
                sql.append(" extraction.sampleId=?");
                sqlValues.add(samplesArray[i].getId());
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
                sql.append(queryToSql(refinedQueries, operator, "workflow", sqlValues));
                sql.append(")");
            }
        }
        if(!somethingToSearch) {
            return Collections.emptyList();
        }

        //attach the values to the query
        System.out.println(sql.toString());
        PreparedStatement statement = connection.prepareStatement(sql.toString());
        if(!BiocodeService.getInstance().getActiveLIMSConnection().isLocal()) {
            statement.setFetchSize(Integer.MIN_VALUE);
        }
        int position = 1;
        if(samples != null && samples.size() > 0) {
            for(FimsSample sample : samples) {
                statement.setString(position, sample.getId());
                position++;
            }
        }
        for (int i = 0; i < sqlValues.size(); i++) {
            Object o = sqlValues.get(i);
            if(Integer.class.isAssignableFrom(o.getClass())) {
                statement.setInt(i+1, (Integer)o);
            }
            else if(Double.class.isAssignableFrom(o.getClass())) {
                statement.setDouble(i+1, (Double)o);
            }
            else if(String.class.isAssignableFrom(o.getClass())) {
                statement.setString(i+1, o.toString().toLowerCase());
            }
            else if(Date.class.isAssignableFrom(o.getClass())) {
                statement.setDate(i+1, new java.sql.Date(((Date)o).getTime()));
            }
            else {
                throw new SQLException("You have a field parameter with an invalid type: "+o.getClass().getCanonicalName());
            }
        }
        ResultSet resultSet = statement.executeQuery();

        Map<Integer, WorkflowDocument> workflowDocs = new HashMap<Integer, WorkflowDocument>();
        int prevWorkflowId = -1;
        while(resultSet.next()) {
            if(callback != null && callback.isCanceled()) {
                resultSet.close();
                return Collections.EMPTY_LIST;
            }
            int workflowId = resultSet.getInt("workflow.id");
            if(callback != null && prevWorkflowId >= 0 && prevWorkflowId != workflowId) {
                WorkflowDocument prevWorkflow = workflowDocs.get(prevWorkflowId);
                if(prevWorkflow != null) {
                    prevWorkflow.sortReactions();
                    callback.add(prevWorkflow, Collections.<String, Object>emptyMap());
                }
            }
            prevWorkflowId = workflowId;
            if(workflowDocs.get(workflowId) != null) {
                workflowDocs.get(workflowId).addRow(resultSet);
            }
            else {
                WorkflowDocument doc = new WorkflowDocument(resultSet);
                workflowDocs.put(workflowId, doc);
            }
        }
        if(prevWorkflowId >= 0) {
            WorkflowDocument prevWorkflow = workflowDocs.get(prevWorkflowId);
            if(prevWorkflow != null && callback != null) {
                prevWorkflow.sortReactions();
                callback.add(prevWorkflow, Collections.<String, Object>emptyMap());
            }
        }
        statement.close();
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

    private String queryToSql(List<? extends Query> queries, CompoundSearchQuery.Operator operator, String tableName, List<Object> inserts) {
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
                String code = q.getField().getCode();
                if("date".equals(code)) {
                    code = tableName+".date"; //hack for last modified date...
                }
                if(String.class.isAssignableFrom(q.getField().getValueType())) {
                    code = "LOWER("+code+")";
                }

                Object[] queryValues = q.getValues();

                //if(code.contains("date") && (q.getCondition() == Condition.EQUAL || q.getCondition() == Condition.NOT_EQUAL)) {
//                    //hack for dates - we need to check that the date is greater than 12:00am on the day in question and less than 12:00am on the next day
//                    sql.append("(");
//                    Date dateValue = (Date) queryValues[0];
//                    sql.append(" "+ code +" "+(q.getCondition() == Condition.EQUAL ? ">" : "<")+" ");
//                    appendValue(inserts, sql, false, termSurrounder, new Date(dateValue.getTime()-86300000), q.getCondition());//minus one day...
//                    if(q.getCondition() == Condition.EQUAL) {
//                        sql.append(" AND ");
//                    }
//                    else {
//                        sql.append(" OR ");
//                    }
//                    sql.append(" "+ code +" "+(q.getCondition() == Condition.EQUAL ? "<" : ">")+" ");
//                    appendValue(inserts, sql, false, termSurrounder, new Date(dateValue.getTime()), q.getCondition());
//                    sql.append(")");
//                    if(i < queryValues.length - 1) {
//                        sql.append(" AND ");
//                    }
                //}
                //else {
                    sql.append(" "+ code +" "+ termSurrounder.getJoin() +" ");

                    for (int j = 0; j < queryValues.length; j++) {
                        appendValue(inserts, sql, i < queryValues.length - 1, termSurrounder, queryValues[j], q.getCondition());
                    }
                //}
            }
            if(i < queries.size()-1) {
                sql.append(" "+mainJoin);
            }
        }
        return sql.toString();
    }

    private void appendValue(List<Object> inserts, StringBuilder sql, boolean appendAnd, QueryTermSurrounder termSurrounder, Object value, Condition condition) {
        String valueString = valueToString(value);
        //valueString = termSurrounder.getPrepend()+valueString+termSurrounder.getAppend();
        if(Date.class.isAssignableFrom(value.getClass()) && (condition == Condition.LESS_THAN_OR_EQUAL_TO || condition == Condition.GREATER_THAN)) { //hack to make these conditions work...
            value = new Date(((Date)value).getTime()+86300000);
        }
        if(value instanceof String) {
            inserts.add(termSurrounder.getPrepend()+valueString+termSurrounder.getAppend());
        }
        else {
            inserts.add(value);
        }
        sql.append("?");
        if(appendAnd) {
            sql.append(" AND ");
        }
    }

    private static String valueToString(Object value) {
        if(value instanceof Date) {
            DateFormat format = new SimpleDateFormat("yyyy-mm-dd kk:mm:ss");
            return format.format((Date)value);
        }
        return value.toString();
    }

    public List<PlateDocument> getMatchingPlateDocuments(Query query, List<WorkflowDocument> workflowDocuments, RetrieveCallback callback) throws SQLException{
        List<? extends Query> refinedQueries;
        CompoundSearchQuery.Operator operator;
        Set<Integer> plateIds = new HashSet<Integer>();
        long startTime = System.currentTimeMillis();

        if(query instanceof BasicSearchQuery) {
            query = generateAdvancedQueryFromBasicQuery(query);
        }

        if(query instanceof CompoundSearchQuery) {
            refinedQueries = removeFields(((CompoundSearchQuery)query).getChildren(), Arrays.asList("workflow.name", "workflow.date", "locus"));
            operator = ((CompoundSearchQuery)query).getOperator();
        }
        else {
            refinedQueries = removeFields(Arrays.asList(query), Arrays.asList("workflow.name", "workflow.date", "locus"));
            operator = CompoundSearchQuery.Operator.AND;
        }
        if((workflowDocuments == null || workflowDocuments.size() == 0) && refinedQueries.size() == 0) {
            return Collections.emptyList();
        }
        StringBuilder sql = new StringBuilder("SELECT * FROM plate LEFT JOIN cyclesequencing ON cyclesequencing.plate = plate.id " +
                "LEFT JOIN pcr ON pcr.plate = plate.id " +
                "LEFT JOIN workflow ON (workflow.id = pcr.workflow OR workflow.id = cyclesequencing.workflow) " +
                "LEFT JOIN extraction ON (extraction.plate = plate.id OR extraction.id = workflow.extractionId) " +            
                "WHERE");
        ArrayList<Object> sqlValues = new ArrayList<Object>();

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

            sql.append(queryToSql(refinedQueries, operator, "plate", sqlValues));

            sql.append(")");
        }
        System.out.println(sql.toString());
        Connection connection = isLocal ? this.connection : this.connection2;
        PreparedStatement statement = connection.prepareStatement(sql.toString());
        if(!BiocodeService.getInstance().getActiveLIMSConnection().isLocal()) {
            statement.setFetchSize(Integer.MIN_VALUE);
        }

        for (int i = 0; i < sqlValues.size(); i++) {
            Object o = sqlValues.get(i);
            if(Integer.class.isAssignableFrom(o.getClass())) {
                statement.setInt(i+1, (Integer)o);
            }
            else if(Double.class.isAssignableFrom(o.getClass())) {
                statement.setDouble(i+1, (Double)o);
            }
            else if(String.class.isAssignableFrom(o.getClass())) {
                statement.setString(i+1, o.toString().toLowerCase());
            }
            else if(Date.class.isAssignableFrom(o.getClass())) {
                statement.setDate(i+1, new java.sql.Date(((Date)o).getTime()));
            }
            else {
                throw new SQLException("You have a field parameter with an invalid type: "+o.getClass().getCanonicalName());
            }
        }
        System.out.println("EXECUTING PLATE QUERY");

        ResultSet resultSet = statement.executeQuery();
        final StringBuilder totalErrors = new StringBuilder("");
        Map<Integer, Plate> plateMap = new HashMap<Integer, Plate>();
        List<ExtractionReaction> extractionReactions = new ArrayList<ExtractionReaction>();
        List<PCRReaction> pcrReactions = new ArrayList<PCRReaction>();
        List<CycleSequencingReaction> cyclesequencingReactions = new ArrayList<CycleSequencingReaction>();
        List<Integer> returnedPlateIds = new ArrayList<Integer>();
        int count = 0;
        System.out.println("Creating Reactions...");
        int previousId = -1;
        while(resultSet.next()) {
            count++;
            System.out.println(count);

            if(callback != null && callback.isCanceled()) {
                resultSet.close();
                return Collections.EMPTY_LIST;
            }

            Plate plate;
            int plateId = resultSet.getInt("plate.id");
            //System.out.println(plateId);

            if(previousId >= 0 && previousId != plateId) {
                Plate prevPlate = plateMap.get(previousId);
                if(prevPlate != null) {
                    prevPlate.initialiseReactions();
                    String error = checkReactions(prevPlate);
                    if(error != null) {
                        totalErrors.append(error+"\n");
                    }
                    System.out.println("Adding "+prevPlate.getName());
                    if(callback != null) {
                        callback.add(new PlateDocument(prevPlate), Collections.<String, Object>emptyMap());
                    }
                    plateMap.put(previousId, prevPlate);
                }
            }
            previousId = plateId;

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
        statement.close();
        if(previousId >= 0) {
            Plate prevPlate = plateMap.get(previousId);
            if(prevPlate != null) {
                prevPlate.initialiseReactions();
                String error = checkReactions(prevPlate);
                if(error != null) {
                    totalErrors.append(error+"\n");
                }
                System.out.println("Adding "+prevPlate.getName());
                if (callback != null) {
                    callback.add(new PlateDocument(prevPlate), Collections.<String, Object>emptyMap());
                }

                plateMap.put(previousId, prevPlate);
                
            }
        }
        System.out.println("count="+count);

//        if(extractionReactions.size() > 0) {
//            System.out.println("Checking extractions");
//            String extractionErrors = extractionReactions.get(0).areReactionsValid(extractionReactions, null, true);
//            if(extractionErrors != null) {
//                totalErrors.append(extractionErrors+"\n");
//            }
//        }
//        if(pcrReactions.size() > 0) {
//            System.out.println("Checking PCR's");
//            String pcrErrors = pcrReactions.get(0).areReactionsValid(pcrReactions, null, true);
//            if(pcrErrors != null) {
//                totalErrors.append(pcrErrors+"\n");
//            }
//        }
//        if(cyclesequencingReactions.size() > 0) {
//            System.out.println("Checking Cycle Sequencing's...");
//            String cyclesequencingErrors = cyclesequencingReactions.get(0).areReactionsValid(cyclesequencingReactions, null, true);
//            if(cyclesequencingErrors != null) {
//                totalErrors.append(cyclesequencingErrors+"\n");
//            }
//        }
        if(totalErrors.length() > 0) {
            Runnable runnable = new Runnable() {
                public void run() {
                    if(totalErrors.toString().contains("connection")) {
                        Dialogs.showMoreOptionsDialog(new Dialogs.DialogOptions(new String[] {"OK"}, "Connection Error"), "There was an error connecting to the server.  Try logging out and logging in again.", totalErrors.toString());    
                    }
                    else {
                        Dialogs.showMessageDialog("Geneious has detected the following possible errors in your database.  Please contact your system administrator for asistance.\n\n"+totalErrors, "Database errors detected", null, Dialogs.DialogIcon.WARNING);
                    }
                }
            };
            ThreadUtilities.invokeNowOrLater(runnable);
        }

        System.out.println("Creating plate documents");
        List<PlateDocument> docs = new ArrayList<PlateDocument>();
        System.out.println("Getting GEL images");
        //getGelImagesForPlates(plateMap.values());   //we are only downloading these when the user wants to view them now...
        for(Plate plate : plateMap.values()) {
            docs.add(new PlateDocument(plate));
        }
        int time = (int)(System.currentTimeMillis()-startTime)/1000;
        System.out.println("done in "+time+" seconds!");

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

    private String checkReactions(Plate plate) {
        System.out.println("Checking "+plate.getName());
        List<Reaction> reactions = new ArrayList<Reaction>();
        for(Reaction r : plate.getReactions()) {
            if(r != null) {
                reactions.add(r);
            }
        }
        return reactions.get(0).areReactionsValid(reactions, null, true);
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

    public Map<String, Reaction> getExtractionReactions(List<Reaction> sourceReactions) throws SQLException{
        if(sourceReactions == null || sourceReactions.size() == 0) {
            return Collections.EMPTY_MAP;
        }
        StringBuilder sql = new StringBuilder("SELECT * FROM extraction WHERE (");
        for (int i=0; i < sourceReactions.size(); i++) {
            sql.append("extractionId=?");
            if (i < sourceReactions.size() - 1) {
                sql.append(" OR ");
            }
        }
        sql.append(")");
        PreparedStatement statement = connection.prepareStatement(sql.toString());
        for (int i=0; i < sourceReactions.size(); i++) {
            statement.setString(i+1, sourceReactions.get(i).getExtractionId());
        }
        ResultSet resultSet = statement.executeQuery();
        Map<String, Reaction> reactions = new HashMap<String, Reaction>();
        while(resultSet.next()) {
            ExtractionReaction reaction = new ExtractionReaction(resultSet);
            reactions.put(reaction.getExtractionId(), reaction);
        }
        return reactions;
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
        System.out.println(sql);
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
        statement.close();
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

    public Set<String> getAllExtractionIdsStartingWith(List<String> tissueIds) throws SQLException{
        
        List<String> queries = new ArrayList<String>();
        for(String s : tissueIds) {
            queries.add("extractionId LIKE ?");
        }

        String sql = "SELECT extractionId FROM extraction WHERE "+StringUtilities.join(" OR ", queries);

        PreparedStatement statement = connection.prepareStatement(sql);
        for (int i = 0; i < tissueIds.size(); i++) {
            statement.setString(i+1, tissueIds.get(i)+"%");
        }

        ResultSet set = statement.executeQuery();
        Set<String> result = new HashSet<String>();

        while(set.next()) {
            result.add(set.getString("extractionId"));
        }


        return result;
    }

    public Map<String, ExtractionReaction> getExtractionsFromBarcodes(List<String> barcodes) throws SQLException{
         StringBuilder sql = new StringBuilder("SELECT * FROM workflow LEFT JOIN cyclesequencing ON cyclesequencing.workflow = workflow.id " +
                "LEFT JOIN pcr ON pcr.workflow = workflow.id " +
                "LEFT JOIN extraction ON workflow.extractionId = extraction.id " +
                "LEFT JOIN plate ON plate.id = extraction.plate "+
                "WHERE (");

        List<String> queryParams = new ArrayList<String>();
        for(String barcode : barcodes) {
            queryParams.add("extraction.extractionBarcode = ?");
        }

        sql.append(StringUtilities.join(" OR ", queryParams));

        sql.append(")");

        PreparedStatement statement = connection.prepareStatement(sql.toString());

        for (int i = 0; i < barcodes.size(); i++) {
            String barcode = barcodes.get(i);
            statement.setString(i+1, barcode);
        }

        ResultSet r = statement.executeQuery();

        Map<String, ExtractionReaction> results = new HashMap<String, ExtractionReaction>();
        while(r.next()) {
            ExtractionReaction reaction = new ExtractionReaction(r);
            results.put(""+reaction.getFieldValue("extractionBarcode"), reaction);
        }

        return results;
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
