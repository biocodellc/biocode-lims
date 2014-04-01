package com.biomatters.plugins.biocode.server;

import com.biomatters.geneious.publicapi.databaseservice.Query;
import com.biomatters.geneious.publicapi.databaseservice.QueryField;
import com.biomatters.geneious.publicapi.documents.Condition;
import com.biomatters.geneious.publicapi.documents.DocumentField;
import com.biomatters.plugins.biocode.labbench.BiocodeService;
import com.biomatters.plugins.biocode.labbench.fims.MooreaFimsConnection;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author Matthew Cheung
 * @version $Id$
 *          <p/>
 *          Created on 1/04/14 2:51 PM
 */
public class QueryUtils {

    public static Query createQueryFromQueryString(QueryType type, String queryString, Map<String, Object> searchOptions) {
        if(!queryString.contains(":")) {
            return Query.Factory.createFieldQuery(MooreaFimsConnection.MOOREA_TISSUE_ID_FIELD, Condition.CONTAINS,
                            new Object[]{queryString}, searchOptions);
        }

        String[] parts = queryString.split("\\+");
        List<Query> subQueries = new ArrayList<Query>();
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
            subQueries.add(Query.Factory.createFieldQuery(field, Condition.EQUAL, value));
        }

        if(type == QueryType.OR) {
            return Query.Factory.createOrQuery(subQueries.toArray(new Query[subQueries.size()]), searchOptions);
        } else {
            // Default to AND search
            return Query.Factory.createAndQuery(subQueries.toArray(new Query[subQueries.size()]), searchOptions);
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
