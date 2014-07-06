package com.biomatters.plugins.biocode.utilities;

import com.biomatters.geneious.publicapi.databaseservice.Query;
import com.biomatters.geneious.publicapi.databaseservice.BasicSearchQuery;
import com.biomatters.geneious.publicapi.databaseservice.CompoundSearchQuery;
import com.biomatters.geneious.publicapi.databaseservice.AdvancedSearchQueryTerm;
import com.biomatters.geneious.publicapi.documents.DocumentField;
import com.biomatters.geneious.publicapi.documents.Condition;
import com.biomatters.geneious.publicapi.utilities.StringUtilities;
import com.biomatters.plugins.biocode.labbench.fims.MySQLFimsConnection;

import java.sql.*;
import java.util.*;
import java.util.Date;

/**
 * @author Steve
 * @version $Id$
 */
public class SqlUtilities {
    public static String getQuerySQLString(Query query, List<DocumentField> searchAttributes, boolean specialCaseForBiocode) {
        return getQuerySQLString(query, searchAttributes, "", specialCaseForBiocode);    
    }

    public static String getQuerySQLString(Query query, List<DocumentField> searchAttributes, String prefixToRemoveFromFields, boolean specialCaseForBiocode) {
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
            queryBuilder.append("(");
            List<Query> queryList = new ArrayList<Query>();
            for (DocumentField field : searchAttributes) {
                if (!field.getValueType().equals(String.class)) {
                    continue;
                }

                queryList.add(BasicSearchQuery.Factory.createFieldQuery(field, Condition.CONTAINS, searchText));
            }
            Query compoundQuery = CompoundSearchQuery.Factory.createOrQuery(queryList.toArray(new Query[queryList.size()]), Collections.<String, Object>emptyMap());
            return getQuerySQLString(compoundQuery, searchAttributes, prefixToRemoveFromFields, specialCaseForBiocode);
        }
        else if(query instanceof AdvancedSearchQueryTerm) {
            AdvancedSearchQueryTerm aquery = (AdvancedSearchQueryTerm)query;
            String fieldCode = aquery.getField().getCode();
            if(prefixToRemoveFromFields != null && prefixToRemoveFromFields.length() > 0 && fieldCode.length() > prefixToRemoveFromFields.length()) {
                fieldCode = fieldCode.substring(prefixToRemoveFromFields.length());
            }

            if(aquery.getCondition() == Condition.STRING_LENGTH_GREATER_THAN) {
                return "LEN("+fieldCode+") > "+aquery.getValues()[0];
            }
            else if(aquery.getCondition() == Condition.STRING_LENGTH_LESS_THAN) {
                return "LEN("+fieldCode+") < "+aquery.getValues()[0];
            }

            QueryTermSurrounder surrounder = getQueryTermSurrounder(aquery);
            join = surrounder.getJoin();
            prepend = surrounder.getPrepend();
            append = surrounder.getAppend();

            Object testSearchText = aquery.getValues()[0];
            if(testSearchText == null || testSearchText.toString().trim().length() == 0) {
                return null;
            }

            //special cases
            if(specialCaseForBiocode && fieldCode.equals("tissueId")) {
                String[] tissueIdParts = aquery.getValues()[0].toString().split("\\.");
                if(tissueIdParts.length == 2) {
                    //noinspection StringConcatenationInsideStringBufferAppend
                    queryBuilder.append("(biocode_tissue.bnhm_id "+join+" '"+prepend+tissueIdParts[0]+"' AND biocode_tissue.tissue_num "+join+" "+tissueIdParts[1]+")");
                }
                else {
                    //noinspection StringConcatenationInsideStringBufferAppend
                    queryBuilder.append("biocode_tissue.bnhm_id "+join+" '"+prepend+aquery.getValues()[0]+append+"'");
                }
            }
            else if(specialCaseForBiocode && fieldCode.equals("biocode_collecting_event.CollectionTime")) {
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
                if (specialCaseForBiocode && fieldCode.equals(DocumentField.ORGANISM_FIELD.getCode())) {
                    fieldCode = "biocode.ScientificName"; //we use the standard organism field so we need to map it to the correct database id
                }
                else if (specialCaseForBiocode && fieldCode.equals(DocumentField.COMMON_NAME_FIELD.getCode())) {
                    fieldCode = "biocode.ColloquialName"; //we use the standard common name field so we need to map it to the correct database id
                }
                String[] elements = fieldCode.split("\\.");
                for (int i1 = 0; i1 < elements.length; i1++) {
                    //noinspection StringConcatenationInsideStringBufferAppend
                    queryBuilder.append("`"+elements[i1]+'`');
                    if(i1 < elements.length-1) {
                        queryBuilder.append(".");
                    }
                }
                //noinspection StringConcatenationInsideStringBufferAppend
                queryBuilder.append(" "+join+" ");

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
            for (Query childQuery : cquery.getChildren()) {
                String s = getQuerySQLString(childQuery, searchAttributes, prefixToRemoveFromFields, specialCaseForBiocode);
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

            queryBuilder.append(")");
        }
        return queryBuilder.toString();
    }

    public static DocumentField getDocumentField(ResultSet resultSet, boolean resultSetFromMetaDataQuery) throws SQLException {
        String type = resultSet.getString(resultSetFromMetaDataQuery ? "TYPE_NAME" : "Type").toUpperCase();
        String fieldName = resultSet.getString(resultSetFromMetaDataQuery ? "COLUMN_NAME" : "Field");
        if(type.startsWith("TINYBLOB") || type.startsWith("BLOB") || type.startsWith("MEDIUMBLOB") || type.startsWith("LONGBLOB")) {
            return null; //we don't support blobs...
        }
        if(type.startsWith("BOOL") || type.equals("TINYINT(1)") || type.equals("BIT") || type.equals("BIT(1)")) {
            return DocumentField.createBooleanField(fieldName, fieldName, MySQLFimsConnection.FIELD_PREFIX + fieldName, true, false);
        }
        else if(type.startsWith("BIT") || type.startsWith("SMALLINT") || type.startsWith("MEDIUMINT)") || type.startsWith("BIGINT") || type.startsWith("SERIAL") || type.startsWith("INT")) {
            return DocumentField.createIntegerField(fieldName, fieldName, MySQLFimsConnection.FIELD_PREFIX + fieldName, true, false);
        }
        else if(type.startsWith("FLOAT") || type.startsWith("DOUBLE") || type.startsWith("DEC")) {
            return DocumentField.createDoubleField(fieldName, fieldName, MySQLFimsConnection.FIELD_PREFIX + fieldName, true, false);
        }
        else if(type.startsWith("DATE") || type.startsWith("TIME") || type.startsWith("YEAR")) {
            return DocumentField.createDateField(fieldName, fieldName, MySQLFimsConnection.FIELD_PREFIX + fieldName, true, false);
        }
        else if(type.startsWith("BINARY") || type.startsWith("VARBINARY") || type.startsWith("TINYTEXT") || type.startsWith("TINYTEXT") || type.startsWith("TEXT") || type.startsWith("MEDIUMTEXT") || type.startsWith("LONGTEXT") || type.startsWith("VARCHAR")) {
            return DocumentField.createStringField(fieldName, fieldName, MySQLFimsConnection.FIELD_PREFIX + fieldName, true, false);
        }
        assert false : "unrecognized column type: "+type;
        return DocumentField.createStringField(fieldName, fieldName, MySQLFimsConnection.FIELD_PREFIX + fieldName, true, false);
    }

    /**
     * Closes a collection of {@link java.sql.PreparedStatement}s, ignoring any SQLExceptions that are thrown as a result.
     *
     * @param statements The statements to close
     */
    public static void cleanUpStatements(Statement... statements) {
        for (Statement statement : statements) {
            if (statement != null) {
                try {
                    statement.close();
                } catch (SQLException e) {
                    // Ignore
                }
            }
        }
    }

    public static void closeConnection(Connection connection) {
        if(connection != null) {
            try {
                connection.close();
            } catch (SQLException e) {
                // Let garbage collector close
            }
        }
    }

    public static void beginTransaction(Connection connection) throws SQLException {
        connection.setAutoCommit(false);
    }

    public static void commitTransaction(Connection connection) throws SQLException {
        connection.commit();
        connection.setAutoCommit(true);
    }

    public static void printSql(String sql, Collection sqlValues) {
        sql = sql.replace("\n", "\n\t");
        if(sqlValues.isEmpty()) {
            System.out.println(sql);
            return;
        }
        StringBuilder builder = new StringBuilder();
        Iterator valueIterator = sqlValues.iterator();
        int index = 0;
        int oldIndex;
        do {
            oldIndex = index;
            index = sql.indexOf("?", index);
            if(index != -1) {
                builder.append(sql.substring(oldIndex, index));
                String toPrint = valueIterator.hasNext() ? getStringToPrint(valueIterator.next()) : "?";
                builder.append(toPrint);
                index++;
            }
        } while(index >= 0);
        builder.append(sql.substring(oldIndex));

        System.out.println(builder.toString());
    }

    private static String getStringToPrint(Object o) {
        String toPrint;
        if (o instanceof CharSequence) {
            toPrint = "'" + o.toString().toLowerCase() + "'";
        } else {
            toPrint = o.toString();
        }
        return toPrint;
    }

    /**
     * Appends set of question marks separated by commas and enclosed by parentheses to the end of a StringBuilder,
     * e.g. (?, ?, ?) is appended for count of 3.
     * @param builder StringBuilder to append to.
     * @param count number of question marks.
     */
    public static void appendSetOfQuestionMarks(StringBuilder builder, int count) {
            String[] qMarks = new String[count];
            Arrays.fill(qMarks, "?");
            builder.append("(").append(StringUtilities.join(",", Arrays.asList(qMarks))).append(")");
        }

    public static QueryTermSurrounder getQueryTermSurrounder(AdvancedSearchQueryTerm query) {
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
            case DATE_AFTER:
                join = ">";
                break;
            case GREATER_THAN_OR_EQUAL_TO:
            case DATE_AFTER_OR_ON:
                join = ">=";
                break;
            case LESS_THAN:
            case DATE_BEFORE:
                join = "<";
                break;
            case LESS_THAN_OR_EQUAL_TO:
            case DATE_BEFORE_OR_ON:
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

    public static void fillStatement(List<Object> sqlValues, PreparedStatement statement) throws SQLException {
        for (int i = 0; i < sqlValues.size(); i++) {
            Object o = sqlValues.get(i);
            if (o == null) {
                statement.setNull(i + 1, Types.JAVA_OBJECT);
            } else if (Integer.class.isAssignableFrom(o.getClass())) {
                statement.setInt(i + 1, (Integer) o);
            } else if (Double.class.isAssignableFrom(o.getClass())) {
                statement.setDouble(i + 1, (Double) o);
            } else if (String.class.isAssignableFrom(o.getClass())) {
                statement.setString(i + 1, o.toString().toLowerCase());
            } else if (Date.class.isAssignableFrom(o.getClass())) {
                statement.setDate(i + 1, new java.sql.Date(((Date) o).getTime()));
            } else if (Boolean.class.isAssignableFrom(o.getClass())) {
                statement.setBoolean(i + 1, (Boolean) o);
            } else {
                throw new SQLException("You have a field parameter with an invalid type: " + o.getClass().getCanonicalName());
            }
        }
    }

    public static class QueryTermSurrounder {
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

    public static Set<String> getDatabaseTableNamesLowerCase(Connection connection) throws SQLException {
        Set<String> namesOfTablesExistingInDatabase = new HashSet<String>();

        DatabaseMetaData metaData = connection.getMetaData();
        boolean hasTableType = false;
        ResultSet typesSet = metaData.getTableTypes();
        while(typesSet.next()) {
            String type = typesSet.getString(1);
            if("TABLE".equals(type)) {
                hasTableType = true;
                break;
            }
        }
        typesSet.close();

        ResultSet tables = metaData.getTables(
                null,
                null,
                "%",
                hasTableType ? new String[]{"TABLE"} : null);
        while(tables.next()) {
            namesOfTablesExistingInDatabase.add(tables.getString("TABLE_NAME").toLowerCase());
        }
        tables.close();
        return namesOfTablesExistingInDatabase;
    }
}
