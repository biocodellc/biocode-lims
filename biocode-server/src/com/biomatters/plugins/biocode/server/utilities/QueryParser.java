package com.biomatters.plugins.biocode.server.utilities;

import com.biomatters.geneious.publicapi.documents.Condition;
import com.biomatters.geneious.publicapi.documents.DocumentField;
import com.biomatters.plugins.biocode.labbench.BiocodeService;
import com.biomatters.plugins.biocode.labbench.lims.LIMSConnection;

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
 *         Parses String query to com.biomatters.plugins.biocode.server.utilities.Query tree.
 */
class QueryParser {

    private static Map<Class, Map<String, Condition>> stringSymbolToConditionMaps = new HashMap<Class, Map<String, Condition>>();

    private static String conditionSymbolsGroupRegex;

    static {
        /* Build individual string symbol to condition maps. */
        Map<String, Condition> stringSymbolToIntegerConditionMap = new HashMap<String, Condition>();

        stringSymbolToIntegerConditionMap.put("<=", Condition.LESS_THAN_OR_EQUAL_TO);
        stringSymbolToIntegerConditionMap.put("<",  Condition.LESS_THAN_OR_EQUAL_TO);
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

        stringSymbolToDateConditionMap.put(">",  Condition.DATE_AFTER);
        stringSymbolToDateConditionMap.put("<=", Condition.DATE_AFTER_OR_ON);
        stringSymbolToDateConditionMap.put("=",  Condition.EQUAL);
        stringSymbolToDateConditionMap.put(">",  Condition.DATE_BEFORE_OR_ON);
        stringSymbolToDateConditionMap.put("<",  Condition.DATE_BEFORE);
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
            conditionSymbolsGroupRegexBuilder.append(symbol + "|");
        }
        if (conditionSymbolsGroupRegexBuilder.length() > 0) {
            conditionSymbolsGroupRegexBuilder.deleteCharAt(conditionSymbolsGroupRegexBuilder.length() - 1);
        }
        conditionSymbolsGroupRegexBuilder.append(")");
        conditionSymbolsGroupRegex = conditionSymbolsGroupRegexBuilder.toString();
    }

    /* Primary parse method. */
    Query parseQuery(String query) { return constructQueryFromPostfix(infixToPostfix(query)); }

    private Query constructQueryFromPostfix(Queue<String> queryPostfix) throws BadRequestException {
        Stack<Query> queryQueue = new Stack<Query>();
        while (!queryPostfix.isEmpty()) {
            String element = queryPostfix.remove();
            if (element.equals("AND")) {
                Query one = queryQueue.pop();
                Query two = queryQueue.pop();
                queryQueue.push(new AndQuery(one, two));
            } else if (element.equals("XOR")) {
                Query one = queryQueue.pop();
                Query two = queryQueue.pop();
                queryQueue.push(new XorQuery(one, two));
            } else if (element.equals("OR")) {
                Query one = queryQueue.pop();
                Query two = queryQueue.pop();
                queryQueue.push(new OrQuery(one, two));
            } else {
                queryQueue.push(stringToBasicQuery(element));
            }
        }
        return queryQueue.pop();
    }

    private Query stringToBasicQuery(String query) {
        String[] queryParts = query.split(conditionSymbolsGroupRegex);
        if (queryParts.length != 2) {
            throw new BadRequestException("Invalid condition: " + query);
        }

        List<DocumentField> searchAttributes = new ArrayList<DocumentField>(LIMSConnection.getSearchAttributes());
        searchAttributes.add(BiocodeService.getInstance().getActiveFIMSConnection().getTissueSampleDocumentField());
        DocumentField field = null;
        for (DocumentField searchAttribute : searchAttributes) {
            if (searchAttribute.getCode().equals(queryParts[0])) {
                field = searchAttribute;
                break;
            }
        }
        if (field == null) {
            throw new BadRequestException("Invalid search attribute: " + queryParts[0]);
        }

        Pattern conditionSymbolRegexPattern = Pattern.compile(conditionSymbolsGroupRegex);
        Matcher matcher = conditionSymbolRegexPattern.matcher(query);
        matcher.find();
        String conditionSymbol = matcher.group();

        Condition condition = stringSymbolToConditionMaps.get(field.getValueType()).get(conditionSymbol);

        if (condition == null) {
            throw new BadRequestException("Invalid condition: " + conditionSymbol + " for field type: " + field.getCode());
        }

        Class fieldClassType = field.getValueType();

        Object value = null;
        if (fieldClassType.equals(Integer.class)) {
            try {
                value = new Integer(queryParts[1]);
            } catch (NumberFormatException e) {
                throw new BadRequestException("Invalid integer value: " + queryParts[1], e);
            }
        } else if (fieldClassType.equals(String.class)) {
            value = queryParts[1];
        } else if (fieldClassType.equals(Date.class)) {
            DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
            try {
                value = dateFormat.parse(queryParts[1]);
            } catch (ParseException e) {
                throw new BadRequestException("Invalid date value: " + queryParts[1], e);
            }
        }

        return new BasicQuery(field, condition, value);
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
                    while (popped != "(") {
                        queryPostfix.add(popped);
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