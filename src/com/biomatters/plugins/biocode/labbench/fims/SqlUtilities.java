package com.biomatters.plugins.biocode.labbench.fims;

import com.biomatters.geneious.publicapi.databaseservice.Query;
import com.biomatters.geneious.publicapi.databaseservice.BasicSearchQuery;
import com.biomatters.geneious.publicapi.databaseservice.CompoundSearchQuery;
import com.biomatters.geneious.publicapi.databaseservice.AdvancedSearchQueryTerm;
import com.biomatters.geneious.publicapi.documents.DocumentField;
import com.biomatters.geneious.publicapi.documents.Condition;

import java.sql.Statement;
import java.util.*;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * @author Steve
 * @version $Id$
 */
public class SqlUtilities {
    static String getQuerySQLString(Query query, List<DocumentField> searchAttributes, boolean specialCaseForBiocode) {
        return getQuerySQLString(query, searchAttributes, "", specialCaseForBiocode);    
    }

    static String getQuerySQLString(Query query, List<DocumentField> searchAttributes, String prefixToRemoveFromFields, boolean specialCaseForBiocode) {
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

    public static void printSql(String sql, List sqlValues) {
        for (Object o : sqlValues) {
            String toPrint;
            if (o instanceof CharSequence) {
                toPrint = "'" + o.toString().toLowerCase() + "'";
            } else {
                toPrint = o.toString();
            }
            sql = sql.replaceFirst("\\?", toPrint);
        }

        System.out.println(sql);
    }
}
