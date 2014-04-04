package com.biomatters.plugins.biocode.server;

import com.biomatters.geneious.publicapi.databaseservice.AdvancedSearchQueryTerm;
import com.biomatters.geneious.publicapi.databaseservice.CompoundSearchQuery;
import com.biomatters.geneious.publicapi.databaseservice.QueryField;
import com.biomatters.geneious.publicapi.documents.Condition;
import com.biomatters.geneious.publicapi.documents.DocumentField;
import com.biomatters.geneious.publicapi.utilities.StringUtilities;
import com.biomatters.plugins.biocode.labbench.BiocodeService;
import com.biomatters.plugins.biocode.labbench.fims.MooreaFimsConnection;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * @author Matthew Cheung
 * @version $Id$
 *          <p/>
 *          Created on 4/04/14 1:00 PM
 */
public class QueryUtils {

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

    public static QueryUtils.Query createRestQuery(com.biomatters.geneious.publicapi.databaseservice.Query query) {
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

        StringBuilder queryBuilder = new StringBuilder();
        boolean first = true;
        for (AdvancedSearchQueryTerm term : terms) {
            if(!first) {
                queryBuilder.append("+");
            } else {
                first = false;
            }
            queryBuilder.append(term.getField().getCode()).append(":").append(
                                        StringUtilities.join(",", Arrays.asList(term.getValues())));
        }

        return new QueryUtils.Query(type, queryBuilder.toString());
    }

    public static com.biomatters.geneious.publicapi.databaseservice.Query createQueryFromQueryString(QueryType type, String queryString, Map<String, Object> searchOptions) {
        if(!queryString.contains(":")) {
            return com.biomatters.geneious.publicapi.databaseservice.Query.Factory.createFieldQuery(MooreaFimsConnection.MOOREA_TISSUE_ID_FIELD, Condition.CONTAINS,
                            new Object[]{queryString}, searchOptions);
        }

        String[] parts = queryString.split("\\+");
        List<com.biomatters.geneious.publicapi.databaseservice.Query> subQueries = new ArrayList<com.biomatters.geneious.publicapi.databaseservice.Query>();
        for (String part : parts) {
            String[] split = part.split("\\:");
            if(split.length != 2) {
                continue;  // todo Is this what we should be doing
            }
            String code = split[0];
            String value = split[1];

            DocumentField field = null;
            for (QueryField queryField : BiocodeService.getInstance().getSearchFields()) {
                if(queryField.field.getCode().equals(code)) {
                    field = queryField.field;
                }
            }
            if(field == null) {
                return null;  // todo better error handling?
            }
            // todo allow multiple conditions
            subQueries.add(com.biomatters.geneious.publicapi.databaseservice.Query.Factory.createFieldQuery(field, Condition.EQUAL, value));
        }

        if(type == QueryType.OR) {
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
}
