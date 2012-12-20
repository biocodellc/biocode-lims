package com.biomatters.plugins.biocode.labbench.fims;

import com.biomatters.geneious.publicapi.utilities.StringUtilities;
import com.biomatters.plugins.biocode.labbench.ConnectionException;
import com.biomatters.plugins.biocode.labbench.FimsSample;
import com.biomatters.plugins.biocode.labbench.TissueDocument;
import com.biomatters.plugins.biocode.CSVUtilities;
import com.biomatters.geneious.publicapi.plugin.Options;
import com.biomatters.geneious.publicapi.documents.DocumentField;
import com.biomatters.geneious.publicapi.documents.Condition;
import com.biomatters.geneious.publicapi.databaseservice.*;
import com.google.api.services.fusiontables.model.Sqlresponse;

import java.util.regex.Pattern;
import java.util.*;
import java.io.IOException;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.text.ParseException;

/**
 * @author Steve
 * @version $Id$
 */
public class FusionTablesFimsConnection extends TableFimsConnection{

    private String tableId;


    public String getLabel() {
        return "Google Fusion Tables";
    }

    public String getName() {
        return "Google";
    }

    public String getDescription() {
        return "Use a remote Fusion Table as your FIMS";
    }

    public TableFimsConnectionOptions _getConnectionOptions() {
        return new FusionTablesFimsConnectionOptions();
    }

    public void _connect(TableFimsConnectionOptions optionsa) throws ConnectionException {
        FusionTablesFimsConnectionOptions options = (FusionTablesFimsConnectionOptions)optionsa;
        Options connectionOptions = options.getChildOptions().get(FusionTablesFimsConnectionOptions.CONNECTION_OPTIONS_KEY);
        tableId = connectionOptions.getValueAsString(TableFimsConnectionOptions.TABLE_ID);
        if(tableId.length() == 0 || tableId.equals(FusionTablesConnectionOptions.NO_TABLE.getName())) {
            throw new ConnectionException("You must specify a Fusion Table ID");
        }
    }

    public List<DocumentField> getTableColumns() throws IOException {
        return FusionTableUtils.getTableColumns(tableId);
    }


    public void _disconnect() {
        tableId = null;
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
            String fieldCode = "'"+aquery.getField().getCode()+"'";
            Class valueClass = aquery.getField().getValueType();

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
                    join = "CONTAINS IGNORING CASE";
                    break;
                case BEGINS_WITH:
                    join = "STARTS WITH";
                    append="";
                    break;
                case ENDS_WITH:
                    join = "ENDS WITH";
                    prepend = "";
                    break;
                case CONTAINS:
                    join = "CONTAINS IGNORING CASE";
                    append = "";
                    prepend = "";
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
                    join = "DOES NOT CONTAIN";
                    append = "";
                    prepend = "";
                    break;
                case NOT_EQUAL:
                    join = "NOT EQUAL TO";
                    break;
            }

            Object testSearchText = aquery.getValues()[0];
            if(testSearchText == null || testSearchText.toString().trim().length() == 0) {
                return null;
            }


           
            //noinspection StringConcatenationInsideStringBufferAppend
            queryBuilder.append(fieldCode +" "+ join +" ");

            Object[] queryValues = aquery.getValues();
            for (int i = 0; i < queryValues.length; i++) {
                Object value = queryValues[i];
                if(value instanceof Date) {
                    value = new SimpleDateFormat("yyyy.MM.dd").format(value);
                }
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

            int count = 0;
            boolean firstTime = true;
            for (Query childQuery : cquery.getChildren()) {
                String s = getQuerySQLString(childQuery);
                if (s == null) {
                    continue;
                } else if (!firstTime) {
                    queryBuilder.append(join);
                }
                firstTime = false;
                count++;
                queryBuilder.append(s);
            }
            if(count == 0) {
                return null;
            }

        }
        return queryBuilder.toString();
    }


    public Condition[] getFieldConditions(Class fieldClass) {
        if(Integer.class.equals(fieldClass) || Double.class.equals(fieldClass)) {
            return new Condition[] {
                    Condition.EQUAL,
                    Condition.NOT_EQUAL,
                    Condition.GREATER_THAN,
                    Condition.GREATER_THAN_OR_EQUAL_TO,
                    Condition.LESS_THAN,
                    Condition.LESS_THAN_OR_EQUAL_TO
            };
        }
        else if(String.class.equals(fieldClass)) {
            return new Condition[] {
                    Condition.CONTAINS,
                    Condition.NOT_CONTAINS,
                    Condition.EQUAL,
                    Condition.NOT_EQUAL,
                    Condition.BEGINS_WITH,
                    Condition.ENDS_WITH,
            };
        }
        else if(Date.class.equals(fieldClass)) {
            return new Condition[] {
                    Condition.EQUAL,
                    Condition.NOT_EQUAL,
                    Condition.GREATER_THAN,
                    Condition.GREATER_THAN_OR_EQUAL_TO,
                    Condition.LESS_THAN,
                    Condition.LESS_THAN_OR_EQUAL_TO
            };
        }
        else {
            return new Condition[] {
                    Condition.EQUAL,
                    Condition.NOT_EQUAL
            };
        }
    }

    public List<FimsSample> _getMatchingSamples(Query query) throws ConnectionException {
        if(query instanceof BasicSearchQuery) {
            String value = ((BasicSearchQuery)query).getSearchText();
            List<Query> queries = new ArrayList<Query>();
            for(DocumentField field : getSearchAttributes()) {
                queries.add(Query.Factory.createFieldQuery(field, Condition.APPROXIMATELY_EQUAL, value));
            }
            return getMatchingSamples(Query.Factory.createOrQuery(queries.toArray(new Query[queries.size()]), Collections.<String,Object>emptyMap()));
        }
        if(query instanceof CompoundSearchQuery && (((CompoundSearchQuery)query).getOperator() == CompoundSearchQuery.Operator.OR)) {
            Set<FimsSample> results = new LinkedHashSet<FimsSample>();
            for(Query q : ((CompoundSearchQuery)query).getChildren()) {
                results.addAll(_getMatchingSamples(q));
            }
            return new ArrayList<FimsSample>(results);
        }
        List<FimsSample> results = new ArrayList<FimsSample>();
        String querySQLString = getQuerySQLString(query);
        if(querySQLString == null) {
            return Collections.emptyList();
        }
        String sql = "SELECT * FROM "+tableId+" WHERE "+ querySQLString;
        System.out.println(sql);

        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(FusionTableUtils.queryTableForCsv(sql)));
            Map<String, Object> values = new LinkedHashMap<String, Object>();
            List<String> colHeaders = new ArrayList<String>();
            boolean firstTime = true;
            String line = null;
            while ((line = reader.readLine()) != null) {
                System.out.println(line);
                String[] elements = CSVUtilities.tokenizeLine(line);
                if(!firstTime) {
                    System.out.println(colHeaders.size()+", "+ elements.length);
                }
                assert elements.length == colHeaders.size() : "Please contact Steve if you see this error: getMatchingSamples(): "+line+"  |  "+ StringUtilities.join(",", colHeaders);
                int numberOfCols = Math.min(elements.length, colHeaders.size());
                for (int i = 0; i < numberOfCols; i++) {
                    String element = elements[i];
                    String decoded = (element == null || element.length() == 0) ? null : element.replaceAll("\"\"", "\"").trim();
                    if (firstTime) {
                        colHeaders.add(decoded);
                    } else {
                        values.put(colHeaders.get(i), convertValue(colHeaders.get(i), decoded));
                    }
                }
                if(!firstTime) {
                    results.add(new TableFimsSample(getCollectionAttributes(), getTaxonomyAttributes(), values, getTissueSampleDocumentField(), getSpecimenDocumentField()));
                }
                values = new LinkedHashMap<String, Object>();
                firstTime = false;
            }
            
        } catch (IOException e) {
            e.printStackTrace();
            throw new ConnectionException(e.getMessage(), e);
        }

        return results;
    }

    private Object convertValue(String columnName, String value) {
        if(!isConnected()) {
            return null;
        }
        if(value == null) {
            return null;
        }

        List<DocumentField> columns = getSearchAttributes();
        for (int i = 0; i < columns.size(); i++) {
            DocumentField field = columns.get(i);
            if (field.getName().equals(columnName)) {
                if (Double.class.isAssignableFrom(field.getValueType())) {
                    try {
                        return Double.parseDouble(value);
                    } catch (NumberFormatException e) {
                        System.out.println("Not a number: " + field.getName() + ", " + value);
                        return null;
                    }
                } else if (Date.class.isAssignableFrom(field.getValueType())) {
                    try {
                        return DateFormat.getDateInstance().parse(value);
                    } catch (ParseException e) {
                        try {
                            return new SimpleDateFormat("yyyy").parse(value);
                        } catch (ParseException e1) {
                            e.printStackTrace();
                            //assert false : e.getMessage();
                            System.out.println("Not a date: " + columnName + ", " + value);
                            return null;
                        }
                    }
                } else if (String.class.isAssignableFrom(field.getValueType())) {
                    return value;
                } else {
                    throw new RuntimeException("Unexpected value type: " + value.getClass().getName());
                }
            }
        }
        return null;
    }

    public int getTotalNumberOfSamples() throws ConnectionException {
        String sql = "SELECT ROWID FROM "+tableId;
        System.out.println(sql);
        try {
            Sqlresponse sqlresponse = FusionTableUtils.queryTable(sql);
            return sqlresponse.getRows().size();
        } catch (IOException e) {
            e.printStackTrace();
            throw new ConnectionException(e.getMessage(), e);
        }
    }

    public void getAllSamples(RetrieveCallback callback) throws ConnectionException {
        String sql = "SELECT * FROM "+tableId;
        System.out.println(sql);

        try {
            DocumentField tissueCol = getTissueSampleDocumentField();
            DocumentField specimenCol = getSpecimenDocumentField();                  

            BufferedReader reader = new BufferedReader(new InputStreamReader(FusionTableUtils.queryTableForCsv(sql)));
            Map<String, Object> values = new LinkedHashMap<String, Object>();
            List<String> colHeaders = new ArrayList<String>();
            boolean firstTime = true;
            String line = null;
            while ((line = reader.readLine()) != null) {
                String[] elements = CSVUtilities.tokenizeLine(line);
                assert elements.length == colHeaders.size() : "Please contact Steve if you see this error: getAllSamples(): "+line+"  |  "+ StringUtilities.join(",", colHeaders);
                int numberOfCols = Math.min(elements.length, colHeaders.size());
                for (int i = 0; i < numberOfCols; i++) {
                    String element = elements[i];
                    String decoded = element == null ? "" : element.replaceAll("\"\"", "\"");
                    if (firstTime) {
                        colHeaders.add(decoded);
                    } else {
                        values.put(colHeaders.get(i), convertValue(colHeaders.get(i), decoded));
                    }
                }
                if(!firstTime) {
                    callback.add(new TissueDocument(new TableFimsSample(getCollectionAttributes(), getTaxonomyAttributes(), values, tissueCol, specimenCol)), Collections.<String, Object>emptyMap());
                }
                values = new LinkedHashMap<String, Object>();
                firstTime = false;
            }

        } catch (IOException e) {
            e.printStackTrace();
            throw new ConnectionException(e.getMessage(), e);
        }

    }

    public boolean requiresMySql() {
        return false;
    }

}
