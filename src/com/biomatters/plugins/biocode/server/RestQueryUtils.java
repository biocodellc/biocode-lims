package com.biomatters.plugins.biocode.server;

import com.biomatters.geneious.publicapi.databaseservice.*;
import com.biomatters.geneious.publicapi.documents.Condition;
import com.biomatters.geneious.publicapi.documents.DocumentField;
import com.biomatters.geneious.publicapi.utilities.StringUtilities;
import com.biomatters.plugins.biocode.labbench.BiocodeService;

import javax.ws.rs.BadRequestException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Matthew Cheung
 * @version $Id$
 *          <p/>
 *          Created on 4/04/14 1:00 PM
 */
public class RestQueryUtils {

    public static class Query {
        private String type;
        private String queryString;

        public Query(String type, String queryString) {
            this.type = type;
            this.queryString = queryString;
        }

        public String getType() {
            return type;
        }

        public String getQueryString() {
            return queryString;
        }
    }

    public static RestQueryUtils.Query createRestQuery(com.biomatters.geneious.publicapi.databaseservice.Query query) {
        List<AdvancedSearchQueryTerm> terms = new ArrayList<AdvancedSearchQueryTerm>();
        String type = "AND";
        if(query instanceof CompoundSearchQuery) {
            if(((CompoundSearchQuery) query).getOperator() == CompoundSearchQuery.Operator.OR) {
                type = "OR";
            }
            for (com.biomatters.geneious.publicapi.databaseservice.Query child : ((CompoundSearchQuery) query).getChildren()) {
                if(child instanceof AdvancedSearchQueryTerm) {
                    terms.add((AdvancedSearchQueryTerm) child);
                }
            }
        } else if(query instanceof AdvancedSearchQueryTerm) {
            terms.add((AdvancedSearchQueryTerm)query);
        }

        if(terms.isEmpty()) {
            return new Query("OR", ((BasicSearchQuery)query).getSearchText());
        } else {
            StringBuilder queryBuilder = new StringBuilder();
            boolean first = true;
            for (AdvancedSearchQueryTerm term : terms) {
                if (!first) {
                    queryBuilder.append("+");
                } else {
                    first = false;
                }
                queryBuilder.append("[").append(term.getField().getCode()).append("]").append(conditionMap.get(term.getCondition())).append(
                        StringUtilities.join(",", Arrays.asList(term.getValues())));
            }

            return new RestQueryUtils.Query(type, queryBuilder.toString());
        }
    }

    public static com.biomatters.geneious.publicapi.databaseservice.Query createQueryFromQueryString(QueryType type, String queryString, Map<String, Object> searchOptions) throws BadRequestException {
        String possibleConditions = StringUtilities.join("|", conditionMap.values());

        Pattern fieldQueryPattern = Pattern.compile("\\[(.*)\\](" + possibleConditions + ")(.*)");
        String[] parts = queryString.split("\\+");

        List<com.biomatters.geneious.publicapi.databaseservice.Query> subQueries = new ArrayList<com.biomatters.geneious.publicapi.databaseservice.Query>();
        boolean containsBasicSearch = false;
        for (String part : parts) {
            Matcher matcher = fieldQueryPattern.matcher(part);
            if(matcher.matches()) {
                String code = matcher.group(1);
                Condition condition = null;
                String conditionString = matcher.group(2);
                for (Map.Entry<Condition, String> entry : conditionMap.entrySet()) {
                    if(entry.getValue().equals(conditionString)) {
                        condition = entry.getKey();
                    }
                }
                if(condition == null) {
                    throw new BadRequestException("Unsupported condition " + conditionString);
                }
                Object value = matcher.group(3);

                DocumentField field = null;
                for (QueryField queryField : BiocodeService.getInstance().getSearchFields()) {
                    if (queryField.field.getCode().equals(code)) {
                        field = queryField.field;
                    }
                }
                if (field == null) {
                    throw new BadRequestException("Unknown field " + code);
                }

                subQueries.add(com.biomatters.geneious.publicapi.databaseservice.Query.Factory.createFieldQuery(field, condition, value));
            } else {
                Pattern hasConditionPattern = Pattern.compile(".+(" + possibleConditions + ").+");
                Matcher check = hasConditionPattern.matcher(part);
                if(check.matches()) {
                    throw new BadRequestException("Must surround field code with [] when specify field search ie [plate.name]");
                }

                containsBasicSearch = true;
                subQueries.add(com.biomatters.geneious.publicapi.databaseservice.Query.Factory.createExtendedQuery(part, searchOptions));
            }
        }
        if(containsBasicSearch && subQueries.size() > 1) {
            throw new BadRequestException("Service does not support multiple basic queries");
        }

        if(subQueries.isEmpty()) {
            return null;
        } else if(subQueries.size() == 1) {
            return subQueries.get(0);
        } else if(type == QueryType.OR) {
            return com.biomatters.geneious.publicapi.databaseservice.Query.Factory.createOrQuery(subQueries.toArray(new com.biomatters.geneious.publicapi.databaseservice.Query[subQueries.size()]), searchOptions);
        } else {
            // Default to AND search
            return com.biomatters.geneious.publicapi.databaseservice.Query.Factory.createAndQuery(subQueries.toArray(new com.biomatters.geneious.publicapi.databaseservice.Query[subQueries.size()]), searchOptions);
        }
    }

    public static enum QueryType {AND, OR;

        public static QueryType forTypeString(String typeString) {
            for (QueryType type : QueryType.values()) {
                if(type.name().equals(typeString)) {
                    return type;
                }
            }
            return null;
        }
    }

    private static Map<Condition, String> conditionMap = new HashMap<Condition, String>();
    static {
        conditionMap.put(Condition.EQUAL, ":");
        conditionMap.put(Condition.NOT_EQUAL, "!:");
        conditionMap.put(Condition.GREATER_THAN, ">");
        conditionMap.put(Condition.GREATER_THAN_OR_EQUAL_TO, ">=");
        conditionMap.put(Condition.LESS_THAN, "<");
        conditionMap.put(Condition.LESS_THAN_OR_EQUAL_TO, "<=");

        conditionMap.put(Condition.CONTAINS, "~");
        conditionMap.put(Condition.NOT_CONTAINS, "!~");
//        Condition.STRING_LENGTH_LESS_THAN,
//        Condition.STRING_LENGTH_GREATER_THAN,
//        Condition.BEGINS_WITH,
//        Condition.ENDS_WITH
    }

    public static boolean supportsConditionForRestQuery(Condition condition) {
        return conditionMap.containsKey(condition);
    }
}
