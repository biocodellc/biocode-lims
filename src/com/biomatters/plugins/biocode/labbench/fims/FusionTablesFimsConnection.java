package com.biomatters.plugins.biocode.labbench.fims;

import com.google.gdata.client.ClientLoginAccountType;
import com.google.gdata.client.GoogleService;
import com.google.gdata.client.Service.GDataRequest;
import com.google.gdata.client.Service.GDataRequest.RequestType;
import com.google.gdata.util.AuthenticationException;
import com.google.gdata.util.ContentType;
import com.google.gdata.util.ServiceException;
import com.biomatters.plugins.biocode.labbench.PasswordOptions;
import com.biomatters.plugins.biocode.labbench.ConnectionException;
import com.biomatters.plugins.biocode.labbench.FimsSample;
import com.biomatters.plugins.biocode.labbench.TissueDocument;
import com.biomatters.plugins.biocode.BiocodeUtilities;
import com.biomatters.plugins.biocode.XmlUtilities;
import com.biomatters.geneious.publicapi.plugin.Options;
import com.biomatters.geneious.publicapi.documents.DocumentField;
import com.biomatters.geneious.publicapi.documents.AnnotatedPluginDocument;
import com.biomatters.geneious.publicapi.documents.Condition;
import com.biomatters.geneious.publicapi.databaseservice.*;
import com.biomatters.options.PasswordOption;

import java.util.regex.Pattern;
import java.util.*;
import java.io.IOException;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLEncoder;
import java.net.URLDecoder;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.text.ParseException;

/**
 * @author Steve
 * @version $Id$
 */
public class FusionTablesFimsConnection extends FIMSConnection{
    static final String SERVICE_URL = "https://www.google.com/fusiontables/api/query";

    static final Pattern CSV_VALUE_PATTERN = Pattern.compile("([^,\\r\\n\"]*|\"(([^\"]*\"\")*[^\"]*)\")(,|\\r?\\n)");

    private GoogleService service = new GoogleService("fusiontables", "fusiontables.ApiExample");
    private String tissueCol;
    private String specimenCol;
    private boolean storePlates;
    private String plateCol;
    private String wellCol;
    private List<DocumentField> fields;
    private List<DocumentField> taxonomyFields;
    private List<DocumentField> columns;
    private String tableId;


    public String getLabel() {
        return "Google";
    }

    public String getName() {
        return "Google";
    }

    public String getDescription() {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public PasswordOptions getConnectionOptions() {
        return new FusionTablesFimsConnectionOptions();
    }

    public void connect(Options options) throws ConnectionException {
        tableId = options.getValueAsString("tableId");
        if(tableId.length() == 0) {
            throw new ConnectionException("You must specify a Fusiion Table ID");
        }
        tissueCol = ((Options.OptionValue)options.getValue("tissueId")).getName();
        specimenCol = ((Options.OptionValue)options.getValue("specimenId")).getName();

        storePlates = (Boolean)options.getValue("storePlates");
        if(storePlates) {
            plateCol = ((Options.OptionValue)options.getValue("plateName")).getName();
            wellCol = ((Options.OptionValue)options.getValue("plateWell")).getName();
        }
        fields = new ArrayList<DocumentField>();
        taxonomyFields = new ArrayList<DocumentField>();

        try {
            service.setUserCredentials(options.getValueAsString("username"), ((PasswordOption)options.getOption("password")).getPassword(), ClientLoginAccountType.GOOGLE);
            columns = FusionTablesFimsConnectionOptions.getTableColumns(tableId, service);
        } catch (IOException e) {
            throw new ConnectionException(e.getMessage(), e);
        } catch (ServiceException e) {
            throw new ConnectionException(e.getMessage(), e);
        }

        List<Options> taxOptions = options.getMultipleOptions("taxFields").getValues();
        for(Options taxOptionsValue : taxOptions){
            Options.OptionValue colValue = (Options.OptionValue)((Options.ComboBoxOption)taxOptionsValue.getOption("taxCol")).getValue();
            String value = XmlUtilities.encodeXMLChars(colValue.getLabel());
            taxonomyFields.add(new DocumentField(value, value, value, String.class, true, false));
        }

        for (int i = 0, cellValuesSize = columns.size(); i < cellValuesSize; i++) {
            DocumentField field = columns.get(i);
            if (!taxonomyFields.contains(field)) {
                fields.add(field);
            }
        }

        //if the tissue or specimen id is also a taxonomy field, it won't be in the fields list, and will cause problems later on
        if(getTableCol(fields, tissueCol) == null) {
            throw new ConnectionException(null, "You have listed your tissue sample field as also being a taxonomy field.  This is not allowed.");
        }
        if(getTableCol(fields, specimenCol) == null) {
            throw new ConnectionException(null, "You have listed your specimen field as also being a taxonomy field.  This is not allowed.");
        }
        if(getTissueSampleDocumentField() == null) {
            throw new ConnectionException("You have not set a tissue column");
        }
    }

    public static DocumentField getTableCol(List<DocumentField> fields, String colName) {
        for(DocumentField field : fields) {
            if(field.getCode().equals(colName)) {
                return field;
            }
        }
        return null;
    }

    public void disconnect() {
        tissueCol = specimenCol = tableId = null;
        fields = null;
        taxonomyFields = null;
        columns = null;
        if(service != null) {
            try {
                service.setUserCredentials("", "");
            } catch (AuthenticationException e) {
                e.printStackTrace();
                //todo: exception handling
            }
        }
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
                    join = "contains ignoring case";
                    break;
                case BEGINS_WITH:
                    join = "starts with";
                    append="";
                    break;
                case ENDS_WITH:
                    join = "ends with";
                    prepend = "";
                    break;
                case CONTAINS:
                    join = "contains";
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
                    join = "does not contain";
                    append = "";
                    prepend = "";
                    break;
                case NOT_EQUAL:
                    join = "not equal to";
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
                    value = new SimpleDateFormat("yy.mm.dd").format(value);
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

    public DocumentField getTissueSampleDocumentField() {
        return getTableCol(fields, tissueCol);
    }

    public List<DocumentField> getCollectionAttributes() {
        return new ArrayList<DocumentField>(fields);
    }

    public List<DocumentField> getTaxonomyAttributes() {
        return new ArrayList<DocumentField>(taxonomyFields);
    }

    public List<DocumentField> getSearchAttributes() {
        ArrayList<DocumentField> fields = new ArrayList<DocumentField>(this.fields);
        fields.addAll(taxonomyFields);
        return fields;
    }

    public BiocodeUtilities.LatLong getLatLong(AnnotatedPluginDocument annotatedDocument) {
        return null;  //todo:
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
        String sql = "SELECT * FROM "+tableId+" WHERE "+getQuerySQLString(query);
        System.out.println(sql);

        try {
            URL url = new URL(SERVICE_URL + "?sql=" + URLEncoder.encode(sql, "UTF-8"));
            System.out.println(url);
            GDataRequest request = service.getRequestFactory().getRequest(RequestType.QUERY, url, ContentType.TEXT_PLAIN);
            request.execute();

            BufferedReader reader = new BufferedReader(new InputStreamReader(request.getResponseStream()));
            Map<String, Object> values = new LinkedHashMap<String, Object>();
            List<String> colHeaders = new ArrayList<String>();
            boolean firstTime = true;
            String line = null;
            while ((line = reader.readLine()) != null) {
                line = URLDecoder.decode(line, "UTF-8");
                System.out.println(line);
                String[] elements = line.split(",");
                for (int i = 0; i < elements.length; i++) {
                    String element = elements[i];
                    String decoded = (element == null || element.length() == 0) ? null : element.replaceAll("\"\"", "\"").trim();
                    if (firstTime) {
                        colHeaders.add(decoded);
                    } else {
                        values.put(colHeaders.get(i), convertValue(colHeaders.get(i), decoded));
                    }
                }
                if(!firstTime) {
                    results.add(new TableFimsSample(getCollectionAttributes(), getTaxonomyAttributes(), values, tissueCol, specimenCol));
                }
                values = new LinkedHashMap<String, Object>();
                firstTime = false;
            }
            
        } catch (IOException e) {
            e.printStackTrace();
            throw new ConnectionException(e.getMessage(), e);
        } catch (ServiceException e) {
            e.printStackTrace();
            throw new ConnectionException(e.getMessage(), e);
        }

        return results;
    }

    private Object convertValue(String columnName, String value) {
        if(value == null) {
            return null;
        }
        for(DocumentField field : columns) {
            if(field.getName().equals(columnName)) {
                if(Double.class.isAssignableFrom(field.getValueType())) {
                    return Double.parseDouble(value);
                }
                else if(Date.class.isAssignableFrom(field.getValueType())) {
                    try {
                        return DateFormat.getDateInstance().parse(value);
                    } catch (ParseException e) {
                        try {
                            return new SimpleDateFormat("yyyy").parse(value);
                        } catch (ParseException e1) {
                            e.printStackTrace();
                            assert false : e.getMessage();
                            return null;
                        }
                    }
                }
                else if(String.class.isAssignableFrom(field.getValueType())) {
                    return value;
                }
                else {
                    throw new RuntimeException("Unexpected value type: "+value.getClass().getName());
                }
            }
        }
        return null;
    }

    public void getAllSamples(RetrieveCallback callback) throws ConnectionException {
        String sql = "SELECT * FROM "+tableId;
        System.out.println(sql);

        try {
            URL url = new URL(SERVICE_URL + "?sql=" + URLEncoder.encode(sql, "UTF-8"));
            System.out.println(url);
            GDataRequest request = service.getRequestFactory().getRequest(RequestType.QUERY, url, ContentType.TEXT_PLAIN);
            request.execute();

            BufferedReader reader = new BufferedReader(new InputStreamReader(request.getResponseStream()));
            Map<String, Object> values = new LinkedHashMap<String, Object>();
            List<String> colHeaders = new ArrayList<String>();
            boolean firstTime = true;
            String line = null;
            while ((line = reader.readLine()) != null) {
                String[] elements = line.split(",");
                for (int i = 0; i < elements.length; i++) {
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
        } catch (ServiceException e) {
            e.printStackTrace();
            throw new ConnectionException(e.getMessage(), e);
        }

    }

    public Map<String, String> getTissueIdsFromExtractionBarcodes(List<String> extractionIds) throws ConnectionException {
        return Collections.emptyMap();
    }

    public Map<String, String> getTissueIdsFromFimsExtractionPlate(String plateId) throws ConnectionException {
        return null;
    }

    public boolean canGetTissueIdsFromFimsTissuePlate() {
        return storePlates;
    }

    @Override
    public DocumentField getPlateDocumentField() {
        if(!storePlates) {
            return null;
        }
        return getTableCol(fields, plateCol);
    }

    @Override
    public DocumentField getWellDocumentField() {
        if(!storePlates) {
            return null;
        }
        return getTableCol(fields, wellCol);
    }

    public boolean requiresMySql() {
        return false;
    }

}
