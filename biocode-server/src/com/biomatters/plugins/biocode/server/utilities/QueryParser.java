package com.biomatters.plugins.biocode.server.utilities;

import com.biomatters.geneious.publicapi.documents.Condition;
import com.biomatters.geneious.publicapi.documents.DocumentField;
import com.biomatters.plugins.biocode.server.utilities.query.QueryValues;
import com.biomatters.plugins.biocode.server.utilities.query.*;

import javax.ws.rs.BadRequestException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Gen Li
 *         Created on 5/06/14 12:02 PM
 *
 *         Parses String query to com.biomatters.plugins.biocode.server.utilities.query.Query tree.
 */
class QueryParser {
    List<DocumentField> searchAttributes;

    QueryParser(List<DocumentField> validFields) {
        searchAttributes = validFields;
    }

    private static Map<Class, Map<String, Condition>> stringSymbolToConditionMaps = new HashMap<Class, Map<String, Condition>>();

    private static String conditionSymbolsGroupRegex;

    static {
        /* Build individual string symbol to condition maps. */
        Map<String, Condition> stringSymbolToIntegerConditionMap = new HashMap<String, Condition>();

        stringSymbolToIntegerConditionMap.put("<=", Condition.LESS_THAN_OR_EQUAL_TO);
        stringSymbolToIntegerConditionMap.put("<",  Condition.LESS_THAN);
        stringSymbolToIntegerConditionMap.put("=",  Condition.EQUAL);
        stringSymbolToIntegerConditionMap.put(">=", Condition.GREATER_THAN_OR_EQUAL_TO);
        stringSymbolToIntegerConditionMap.put(">",  Condition.GREATER_THAN);
        stringSymbolToIntegerConditionMap.put("!=", Condition.NOT_EQUAL);

        Map<String, Condition> stringSymbolToStringConditionMap = new HashMap<String, Condition>();

        stringSymbolToStringConditionMap.put("=",  Condition.EQUAL);
        stringSymbolToStringConditionMap.put("!=", Condition.NOT_EQUAL);
        stringSymbolToStringConditionMap.put("~",  Condition.CONTAINS);
        stringSymbolToStringConditionMap.put("!~", Condition.NOT_CONTAINS);
        stringSymbolToStringConditionMap.put("<",  Condition.STRING_LENGTH_LESS_THAN);
        stringSymbolToStringConditionMap.put(">",  Condition.STRING_LENGTH_GREATER_THAN);
        stringSymbolToStringConditionMap.put("<:", Condition.BEGINS_WITH);
        stringSymbolToStringConditionMap.put(":>", Condition.ENDS_WITH);

        Map<String, Condition> stringSymbolToDateConditionMap = new HashMap<String, Condition>();

        stringSymbolToDateConditionMap.put("<",  Condition.DATE_BEFORE);
        stringSymbolToDateConditionMap.put("<=", Condition.DATE_BEFORE_OR_ON);
        stringSymbolToDateConditionMap.put("=",  Condition.EQUAL);
        stringSymbolToDateConditionMap.put(">",  Condition.DATE_AFTER);
        stringSymbolToDateConditionMap.put(">=", Condition.DATE_AFTER_OR_ON);
        stringSymbolToDateConditionMap.put("!=", Condition.NOT_EQUAL);

        /* Build string symbol to condition map. */
        stringSymbolToConditionMaps.put(Integer.class, stringSymbolToIntegerConditionMap);
        stringSymbolToConditionMaps.put(String.class,  stringSymbolToStringConditionMap);
        stringSymbolToConditionMaps.put(Date.class,    stringSymbolToDateConditionMap);

        /* Build symbol conditions regular expression. */
        Set<String> conditionSymbols = new HashSet<String>();

        conditionSymbols.addAll(stringSymbolToIntegerConditionMap.keySet());
        conditionSymbols.addAll(stringSymbolToStringConditionMap.keySet());
        conditionSymbols.addAll(stringSymbolToDateConditionMap.keySet());

        StringBuilder conditionSymbolsGroupRegexBuilder = new StringBuilder();
        conditionSymbolsGroupRegexBuilder.append("(");
        for (String symbol : conditionSymbols) {
            conditionSymbolsGroupRegexBuilder.append(symbol).append("|");
        }
        if (conditionSymbolsGroupRegexBuilder.length() > 0) {
            conditionSymbolsGroupRegexBuilder.deleteCharAt(conditionSymbolsGroupRegexBuilder.length() - 1);
        }
        conditionSymbolsGroupRegexBuilder.append(")");
        conditionSymbolsGroupRegex = conditionSymbolsGroupRegexBuilder.toString();
    }

    final String advancedQueryStructure = "(\\[[^\\]]+" + conditionSymbolsGroupRegex + "[^\\]]+\\])((AND|OR|XOR)\\[[^\\]]+" + conditionSymbolsGroupRegex + "[^\\]]+\\])*";

    /* Primary parse method. */
    public Query parseQuery(String query) {
        if (query.matches(advancedQueryStructure)) {
            return constructQueryFromPostfix(infixToPostfix(query));
        } else {
            return new GeneralQuery(query);
        }
    }

    private Query constructQueryFromPostfix(Queue<String> queryPostfix) throws BadRequestException {
        int numAnd = 0, numXor = 0, numOr = 0;
        for (String element : queryPostfix) {
            if (element.equals("AND")) {
                numAnd++;
            } else if (element.equals("XOR")) {
                numXor++;
            } else if (element.equals("OR")) {
                numOr++;
            }
        }

        if (numAnd != 0 && numXor == 0 && numOr == 0) {
            List<QueryValues> queryValueses = new ArrayList<QueryValues>();
            for (String element : queryPostfix) {
                if (!element.equals("AND") && !element.equals("XOR") && !element.equals("OR")) {
                    queryValueses.add(stringToQueryValues(element));
                }
            }
            return new MultipleAndQuery(queryValueses.toArray(new QueryValues[queryValueses.size()]));
        } else if (numAnd == 0 && numXor == 0 && numOr != 0) {
            List<QueryValues> queryValueses = new ArrayList<QueryValues>();
            for (String element : queryPostfix) {
                if (!element.equals("AND") && !element.equals("XOR") && !element.equals("OR")) {
                    queryValueses.add(stringToQueryValues(element));
                }
            }
            return new MultipleOrQuery(queryValueses.toArray(new QueryValues[queryValueses.size()]));
        } else {
            Stack<Query> queryQueue = new Stack<Query>();
            while (!queryPostfix.isEmpty()) {
                String element = queryPostfix.remove();
                if (element.equals("AND")) {
                    Query RHS = queryQueue.pop();
                    Query LHS = queryQueue.pop();
                    queryQueue.push(new AndQuery(LHS, RHS));
                } else if (element.equals("XOR")) {
                    Query RHS = queryQueue.pop();
                    Query LHS = queryQueue.pop();
                    queryQueue.push(new XorQuery(LHS, RHS));
                } else if (element.equals("OR")) {
                    Query RHS = queryQueue.pop();
                    Query LHS = queryQueue.pop();
                    queryQueue.push(new OrQuery(LHS, RHS));
                } else {
                    queryQueue.push(new SingleQuery(stringToQueryValues(element)));
                }
            }
            return queryQueue.pop();
        }
    }

    private QueryValues stringToQueryValues(String query) {
        String[] queryParts = query.split(conditionSymbolsGroupRegex);
        if (queryParts.length != 2) {
            throw new BadRequestException("Invalid condition: " + query);
        }

        /* Extract query field. */
        DocumentField field = null;
        for (DocumentField searchAttribute : searchAttributes) {
            String searchAttributeCode = searchAttribute.getCode();
            int colonIndex = searchAttributeCode.indexOf(":");
            if (colonIndex != -1) {
                searchAttributeCode = searchAttributeCode.substring(colonIndex + 1);
            }
            if (searchAttributeCode.equals(queryParts[0])) {
                field = searchAttribute;
                break;
            }
        }
        if (field == null) {
            throw new BadRequestException("Invalid search attribute: " + queryParts[0]);
        }

        /* Extract query condition. */
        Pattern conditionSymbolRegexPattern = Pattern.compile(conditionSymbolsGroupRegex);
        Matcher matcher = conditionSymbolRegexPattern.matcher(query);
        matcher.find();
        String conditionSymbol = matcher.group();

        Condition condition = stringSymbolToConditionMaps.get(field.getValueType()).get(conditionSymbol);

        if (condition == null) {
            throw new BadRequestException("Invalid condition: " + conditionSymbol + " for field type: " + field.getCode());
        }

        /* Extract query value. */
        Object value = parseQueryValue(field.getValueType(), queryParts[1]);

        return QueryValues.createQueryValues(field, condition, value);
    }

    /* Unsupported field values grayed out. */
    private Object parseQueryValue(Class queryValueType, String stringValue) {
        Object result;

        if (queryValueType.equals(Integer.class)) {
            try {
                result = new Integer(stringValue);
            } catch (NumberFormatException e) {
                throw new BadRequestException("Invalid integer value: " + stringValue, e);
            }
        } else if (queryValueType.equals(String.class)) {
            result = stringValue;
        } else if (queryValueType.equals(Date.class)) {
            DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
            try {
                result = dateFormat.parse(stringValue);
            } catch (ParseException e) {
                throw new BadRequestException("Invalid date value: " + stringValue, e);
            }
        }
//        else if (queryValueType.equals(Boolean.class)) {
//            if (stringValue.toLowerCase().equals("true") || stringValue.toLowerCase().equals("false")) {
//                result = new Boolean(stringValue);
//            } else {
//                throw new BadRequestException("Invalid boolean value: " + stringValue);
//            }
//        } else if (queryValueType.equals(Long.class)) {
//            try {
//                result = new Long(stringValue);
//            } catch (NumberFormatException e) {
//                throw new BadRequestException("Invalid long value: " + stringValue, e);
//            }
//        } else if (queryValueType.equals(Double.class)) {
//            try {
//                result = new Double(stringValue);
//            } catch (NumberFormatException e) {
//                throw new BadRequestException("Invalid double value: " + stringValue, e);
//            }
//        }  else if (queryValueType.equals(URL.class)) {
//            try {
//                result = new URL(stringValue);
//            } catch (MalformedURLException e) {
//                throw new BadRequestException("Invalid url value: " + stringValue, e);
//            }
//        }
        else {
            throw new BadRequestException("Unsupported query value type.");
        }

        return result;
    }

    /* Converts String queries to postfix notation. */
    private Queue<String> infixToPostfix(String query) {
        Stack<String> stack = new Stack<String>();
        Queue<String> queryPostfix = new ConcurrentLinkedQueue<String>();

        int position = 0;
        while (position < query.length()) {
            switch(query.charAt(position)) {
                case '(':
                    stack.push(Character.toString(query.charAt(position)));
                    break;
                case '[':
                    StringBuilder condition = new StringBuilder();
                    position++;
                    while (query.charAt(position) != ']') {
                        condition.append(query.charAt(position++));
                    }
                    queryPostfix.add(condition.toString());
                    break;
                case ')':
                    String popped = stack.pop();
                    while (!popped.equals("(")) {
                        queryPostfix.add(popped);
                        popped = stack.pop();
                    }
                    break;
                case 'A':
                    if (query.charAt(position + 1) != 'N' || query.charAt(position + 2) != 'D') {
                        throw new BadRequestException("Invalid query: " + query);
                    }
                    position = position + 2;
                    while (!stack.empty() && stack.peek().equals("AND")) {
                        queryPostfix.add(stack.pop());
                    }
                    stack.push("AND");
                    break;
                case 'X':
                    if (query.charAt(position + 1) != 'O' || query.charAt(position + 2) != 'R') {
                        throw new BadRequestException("Invalid query: " + query);
                    }
                    position = position + 2;
                    while (!stack.empty() && (stack.peek().equals("AND") ||
                                              stack.peek().equals("XOR"))) {
                        queryPostfix.add(stack.pop());
                    }
                    stack.push("XOR");
                    break;
                case 'O':
                    if (query.charAt(position + 1) != 'R') {
                        throw new BadRequestException("Invalid query: " + query);
                    }
                    position++;
                    while (!stack.empty() && (stack.peek().equals("AND") ||
                                              stack.peek().equals("XOR") ||
                                              stack.peek().equals("OR"))) {
                        queryPostfix.add(stack.pop());
                    }
                    stack.push("OR");
                    break;
                default:
                    throw new BadRequestException("Invalid query: " + query);
            }
            position++;
        }

        // Add rest of elements in stack to result;
        while (!stack.empty()) {
            queryPostfix.add(stack.pop());
        }

        return queryPostfix;
    }
}