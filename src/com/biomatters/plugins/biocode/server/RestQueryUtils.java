package com.biomatters.plugins.biocode.server;

import com.biomatters.geneious.publicapi.databaseservice.AdvancedSearchQueryTerm;
import com.biomatters.geneious.publicapi.databaseservice.BasicSearchQuery;
import com.biomatters.geneious.publicapi.databaseservice.CompoundSearchQuery;
import com.biomatters.geneious.publicapi.databaseservice.QueryField;
import com.biomatters.geneious.publicapi.documents.Condition;
import com.biomatters.geneious.publicapi.documents.DocumentField;
import com.biomatters.geneious.publicapi.utilities.StringUtilities;
import com.biomatters.plugins.biocode.BiocodePlugin;
import com.biomatters.plugins.biocode.labbench.BiocodeService;
import com.biomatters.plugins.biocode.labbench.ConnectionException;
import com.biomatters.plugins.biocode.labbench.rest.client.ExceptionClientFilter;
import com.biomatters.plugins.biocode.labbench.rest.client.ForbiddenExceptionClientFilter;
import com.biomatters.plugins.biocode.labbench.rest.client.VersionHeaderAddingFilter;
import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.client.ClientProperties;
import org.glassfish.jersey.client.authentication.HttpAuthenticationFeature;
import org.glassfish.jersey.filter.LoggingFilter;

import javax.net.ssl.*;
import javax.ws.rs.BadRequestException;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import java.security.GeneralSecurityException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.logging.Logger;
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

    public static String geneiousQueryToRestQueryString(com.biomatters.geneious.publicapi.databaseservice.Query query) {
        if (query instanceof BasicSearchQuery) {
            return ((BasicSearchQuery) query).getSearchText();
        } else if (query instanceof AdvancedSearchQueryTerm) {
            return parseAdvancedSearchQueryTerm((AdvancedSearchQueryTerm) query);
        } else if (query instanceof CompoundSearchQuery) {
            StringBuilder stringQueryBuilder = new StringBuilder();
            CompoundSearchQuery queryCastToCompoundSearchQuery = (CompoundSearchQuery)query;
            String compoundSearchQueryType = null;
            if (queryCastToCompoundSearchQuery.getOperator() == CompoundSearchQuery.Operator.AND) {
                compoundSearchQueryType = "AND";
            } else if (queryCastToCompoundSearchQuery.getOperator() == CompoundSearchQuery.Operator.OR) {
                compoundSearchQueryType = "OR";
            }
            List<? extends com.biomatters.geneious.publicapi.databaseservice.Query> childQueries = queryCastToCompoundSearchQuery.getChildren();
            if (childQueries.isEmpty()) {
                return null;
            }
            stringQueryBuilder.append(parseAdvancedSearchQueryTerm((AdvancedSearchQueryTerm)childQueries.get(0)));
            for (int i = 1; i < childQueries.size(); i++) {
                stringQueryBuilder.append(compoundSearchQueryType).append(parseAdvancedSearchQueryTerm((AdvancedSearchQueryTerm) childQueries.get(i)));
            }
            return stringQueryBuilder.toString();
        }
        return null;
    }

    private static String parseAdvancedSearchQueryTerm(AdvancedSearchQueryTerm query) {
        return "[" +
                query.getField().getCode() +
                getConditionSymbol(query.getField().getValueType(), query.getCondition()) +
                queryValueObjectToString(query.getValues()[0]) +
                "]";
    }

    private static String queryValueObjectToString(Object value) {
        if (value instanceof String) {
            return (String)value;
        } else if (value instanceof Integer) {
            return value.toString();
        } else if (value instanceof Date) {
            Date valueCastToDate = (Date)value;
            DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
            return dateFormat.format(valueCastToDate);
        } else {
            return null;
        }
    }

    /* Unsupported field values grayed out. */
    public static Object parseQueryValue(Class queryValueType, String stringValue) {
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
                List<String> valuesAsString = new ArrayList<String>();
                for (Object value : term.getValues()) {
                    valuesAsString.add(queryValueObjectToString(value));
                }
                queryBuilder.append("[").append(term.getField().getCode()).append("]").append(
                        getConditionSymbol(term.getField().getValueType(), term.getCondition())).append(
                        StringUtilities.join(",", valuesAsString));
            }

            return new RestQueryUtils.Query(type, queryBuilder.toString());
        }
    }

    public static com.biomatters.geneious.publicapi.databaseservice.Query createQueryFromQueryString(QueryType type, String queryString, Map<String, Object> searchOptions) throws BadRequestException {
        Set<String> conditions = new HashSet<String>();
        for (Map<String, Condition> mapForType : stringSymbolToConditionMaps.values()) {
            conditions.addAll(mapForType.keySet());
        }
        String possibleConditions = StringUtilities.join("|", conditions);

        Pattern fieldQueryPattern = Pattern.compile("\\[(.*)\\](" + possibleConditions + ")(.*)");
        String[] parts = queryString.split("\\+");

        List<com.biomatters.geneious.publicapi.databaseservice.Query> subQueries = new ArrayList<com.biomatters.geneious.publicapi.databaseservice.Query>();
        boolean containsBasicSearch = false;
        for (String part : parts) {
            Matcher matcher = fieldQueryPattern.matcher(part);
            if(matcher.matches()) {
                String code = matcher.group(1);

                DocumentField field = null;
                for (QueryField queryField : BiocodeService.getInstance().getSearchFields()) {
                    if (queryField.field.getCode().equals(code)) {
                        field = queryField.field;
                    }
                }
                if (field == null) {
                    throw new BadRequestException("Unknown field " + code);
                }

                Condition condition = null;
                String conditionString = matcher.group(2);
                for (Map.Entry<String, Condition> entry : stringSymbolToConditionMaps.get(field.getValueType()).entrySet()) {
                    if(entry.getKey().equals(conditionString)) {
                        condition = entry.getValue();
                    }
                }
                if (condition == null) {
                    throw new BadRequestException("Unsupported condition " + conditionString);
                }

                Object value = parseQueryValue(field.getValueType(), matcher.group(3));

                subQueries.add(com.biomatters.geneious.publicapi.databaseservice.Query.Factory.createFieldQuery(field, condition, new Object[]{value}, searchOptions));
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

    private static Map<Class, Map<String, Condition>> stringSymbolToConditionMaps = new HashMap<Class, Map<String, Condition>>();

    static {
        /* Build individual string symbol to condition maps. */
        Map<String, Condition> stringSymbolToIntegerConditionMap = new HashMap<String, Condition>();

        stringSymbolToIntegerConditionMap.put("<=", Condition.LESS_THAN_OR_EQUAL_TO);
        stringSymbolToIntegerConditionMap.put("<", Condition.LESS_THAN);
        stringSymbolToIntegerConditionMap.put("=", Condition.EQUAL);
        stringSymbolToIntegerConditionMap.put(">=", Condition.GREATER_THAN_OR_EQUAL_TO);
        stringSymbolToIntegerConditionMap.put(">", Condition.GREATER_THAN);
        stringSymbolToIntegerConditionMap.put("!=", Condition.NOT_EQUAL);

        Map<String, Condition> stringSymbolToStringConditionMap = new HashMap<String, Condition>();

        stringSymbolToStringConditionMap.put("=", Condition.EQUAL);
        stringSymbolToStringConditionMap.put("!=", Condition.NOT_EQUAL);
        stringSymbolToStringConditionMap.put("~", Condition.CONTAINS);
        stringSymbolToStringConditionMap.put("!~", Condition.NOT_CONTAINS);
        stringSymbolToStringConditionMap.put("<", Condition.STRING_LENGTH_LESS_THAN);
        stringSymbolToStringConditionMap.put(">", Condition.STRING_LENGTH_GREATER_THAN);
        stringSymbolToStringConditionMap.put("<:", Condition.BEGINS_WITH);
        stringSymbolToStringConditionMap.put(":>", Condition.ENDS_WITH);

        Map<String, Condition> stringSymbolToDateConditionMap = new HashMap<String, Condition>();

        stringSymbolToDateConditionMap.put(">", Condition.DATE_AFTER);
        stringSymbolToDateConditionMap.put(">=", Condition.DATE_AFTER_OR_ON);
        stringSymbolToDateConditionMap.put("=", Condition.EQUAL);
        stringSymbolToDateConditionMap.put("<=", Condition.DATE_BEFORE_OR_ON);
        stringSymbolToDateConditionMap.put("<", Condition.DATE_BEFORE);
        stringSymbolToDateConditionMap.put("!=", Condition.NOT_EQUAL);

        /* Build string symbol to condition map. */
        stringSymbolToConditionMaps.put(Integer.class, stringSymbolToIntegerConditionMap);
        stringSymbolToConditionMaps.put(String.class, stringSymbolToStringConditionMap);
        stringSymbolToConditionMaps.put(Date.class, stringSymbolToDateConditionMap);
    }

    private static String getConditionSymbol(Class fieldType, Condition condition) {
        Map<String, Condition> stringSymbolToConditionMap = stringSymbolToConditionMaps.get(fieldType);
        for (Map.Entry<String, Condition> entry : stringSymbolToConditionMap.entrySet()) {
            if (entry.getValue() == condition) {
                return entry.getKey();
            }
        }
        return "";
    }

    public static WebTarget getBiocodeWebTarget(String host, String username, String password, int requestTimeoutInSeconds) throws ConnectionException {
        HttpAuthenticationFeature authFeature = HttpAuthenticationFeature.universal(username, password);
        ClientBuilder clientBuilder = ClientBuilder.newBuilder().sslContext(getSSLContext()).hostnameVerifier(new HostnameVerifier() {
            @Override
            public boolean verify(String s, SSLSession sslSession) {
                return true;
            }
        }).withConfig(new ClientConfig()
                .property(ClientProperties.CONNECT_TIMEOUT, requestTimeoutInSeconds * 1000)
                .property(ClientProperties.READ_TIMEOUT, requestTimeoutInSeconds * 1000)
        );
        return clientBuilder.build().
                register(ForbiddenExceptionClientFilter.class).
                register(ExceptionClientFilter.class).
                register(VersionHeaderAddingFilter.class).
                register(new LoggingFilter(Logger.getLogger(BiocodePlugin.class.getName()), false)).
                register(authFeature).
                register(XMLSerializableMessageReader.class).
                register(XMLSerializableMessageWriter.class).
                target(host).path("biocode");

    }

    /**
     * Older versions of Java often have out of date lists of trusted certificate authorities (CA).  This method
     * returns an SSL context that trusts all certificates regardless of what is in the Java keystore.
     *
     * @throws com.biomatters.plugins.biocode.labbench.ConnectionException if there is a problem creating the SSL context
     */
    private static SSLContext getSSLContext() throws ConnectionException {
        // Create a trust manager that does not validate certificate chains
        TrustManager[] trustAllCerts = new TrustManager[]{
                new X509TrustManager() {
                    public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                        return new java.security.cert.X509Certificate[0];
                    }
                    public void checkClientTrusted(
                            java.security.cert.X509Certificate[] certs, String authType) {
                    }
                    public void checkServerTrusted(
                            java.security.cert.X509Certificate[] certs, String authType) {
                    }
                }
        };

        // Install the all-trusting trust manager
        try {
            SSLContext sc = SSLContext.getInstance("TLS");
            sc.init(null, trustAllCerts, new java.security.SecureRandom());
            return sc;
        } catch (GeneralSecurityException e) {
            throw new ConnectionException("Error setting up options for SSL connection. Error details:\n", e);
        }
    }
}
