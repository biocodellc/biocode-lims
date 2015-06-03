package com.biomatters.plugins.biocode.labbench.fims;

import com.biomatters.geneious.publicapi.components.Dialogs;
import com.biomatters.geneious.publicapi.databaseservice.DatabaseServiceException;
import com.biomatters.geneious.publicapi.databaseservice.Query;
import com.biomatters.geneious.publicapi.databaseservice.RetrieveCallback;
import com.biomatters.geneious.publicapi.documents.DocumentField;
import com.biomatters.geneious.publicapi.plugin.Options;
import com.biomatters.plugins.biocode.BiocodeUtilities;
import com.biomatters.plugins.biocode.labbench.BiocodeService;
import com.biomatters.plugins.biocode.labbench.ConnectionException;
import com.biomatters.plugins.biocode.labbench.FimsSample;
import com.biomatters.plugins.biocode.labbench.PasswordOptions;
import com.biomatters.plugins.biocode.utilities.PasswordOption;
import com.biomatters.plugins.biocode.utilities.SqlUtilities;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.input.SAXBuilder;
import org.jdom.output.Format;
import org.jdom.output.XMLOutputter;

import javax.naming.NamingException;
import javax.sql.DataSource;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLEncoder;
import java.sql.*;
import java.util.*;
import java.util.Date;

/**
 * @author steve
 * @version $Id: 12/05/2009 5:51:15 AM steve $
 */
@SuppressWarnings({"ConstantConditions"})
public class MooreaFimsConnection extends FIMSConnection{

    @SuppressWarnings({"FieldCanBeLocal", "unused"})
    private Driver driver;
    private Connection connection;
    private String jndi;
    private boolean isClosed = true;
    private String username;
    private String password;
    private String serverUrl;
    private String serverPort;


    public static final DocumentField MOOREA_TISSUE_ID_FIELD = new DocumentField("Tissue ID", "", "tissueId", String.class, true, false);
    private static final DocumentField MOOREA_PLATE_NAME_FIELD = new DocumentField("Plate Name (FIMS)", "", "biocode_tissue.format_name96", String.class, true, false);
    private static final DocumentField MOOREA_WELL_NUMBER_FIELD = new DocumentField("Well Number (FIMS)", "", "biocode_tissue.well_number96", String.class, true, false);
    private static final DocumentField MOOREA_TISSUE_BARCODE_FIELD = new DocumentField("Tissue Barcode", "", "biocode_tissue.tissue_barcode", String.class, true, false);
    private static final DocumentField LONGITUDE_FIELD = new DocumentField("Longitude", "", "biocode_collecting_event.DecimalLongitude", Double.class, false, false);
    private static final DocumentField LATITUDE_FIELD = new DocumentField("Latitude", "", "biocode_collecting_event.DecimalLatitude", Double.class, false, false);
    static final DocumentField PROJECT_FIELD = new DocumentField("Project Name", "", "biocode_collecting_event.ProjectCode", String.class, false, false);
    static final DocumentField BIOCODE_PROJECT_FIELD = new DocumentField("Project Name", "", "biocode.ProjectCode", String.class, false, false);
    private static final DocumentField SUBPROJECT_FIELD = new DocumentField("SubProject", "", "biocode.SubProject", String.class, false, false);
    private static final DocumentField SUBSUBPROJECT_FIELD = new DocumentField("SubSubProject", "", "biocode.SubSubProject", String.class, false, false);

    public String getLabel() {
        return "Moorea FIMS";
    }

    public String getName() {
        return "biocode";
    }

    public String getDescription() {
        return "A connection to the Moorea FIMS database";
    }

    public PasswordOptions getConnectionOptions() {
        return new MooreaFimsConnectionOptions(this.getClass(), "mooreaFIMS");
    }

    public void _connect(Options options) throws ConnectionException {
        //instantiate the driver class
        driver = BiocodeService.getInstance().getDriver();
        jndi = options.getValueAsString("jndi");
        username = options.getValueAsString("username");
        password = ((PasswordOption)options.getOption("password")).getPassword();
        serverUrl = options.getValueAsString("serverUrl");
        serverPort = options.getValueAsString("serverPort");

        isClosed = false;

        //connect
        try {
            DriverManager.setLoginTimeout(20);
            Statement statement = getConnection().createStatement();
            statement.execute("USE biocode");
        } catch (SQLException e1) {
            throw new ConnectionException("Failed to connect to the LIMS database: " + e1.getMessage());
        }
    }

    public Connection getConnection() throws SQLException {
        if (isClosed) {
            connection = null;
            return null;
        }

        try {
            if (jndi != null && jndi.trim().length() > 0) {
                DataSource dataSource = BiocodeUtilities.getDataSourceByJNDI(jndi);
                connection = dataSource.getConnection();
            } else if (connection == null) {
                Properties properties = new Properties();
                properties.put("user", username);
                properties.put("password", password);
                connection = DriverManager.getConnection("jdbc:mysql://" + serverUrl + ":" + serverPort, properties);
            }

            if (connection == null) {
                throw new SQLException("You are not connected to the FIMS database");
            }
        } catch (NamingException e) {
            throw new SQLException("Failed to connect to the FIMS database: " + e.getMessage());
        } catch (SQLException e) {
            throw new SQLException("Failed to connect to the FIMS database: " + e.getMessage());
        }

        return connection;
    }

    private Statement createStatement() throws SQLException{
        Statement statement = getConnection().createStatement();
        statement.setQueryTimeout(requestTimeoutInSeconds);
        return statement;
    }

    private PreparedStatement prepareStatement(String query) throws SQLException{
        PreparedStatement statement = getConnection().prepareStatement(query);
        statement.setQueryTimeout(requestTimeoutInSeconds);
        return statement;
    }

    public void disconnect() {
//        if(connection != null) {
//            try {
//                connection.close();
//            } catch (SQLException e) {
//                throw new ConnectionException(e);
//            }
//            connection = null;
//        }
        //we used to explicitly close the SQL connection, but this was causing crashes if the user logged out while a query was in progress.
        //now we remove all references to it and let the garbage collector close it when the queries have finished.
        isClosed = true;
        connection = null;
    }

    public List<DocumentField> _getTaxonomyAttributes() {
        List<DocumentField> fields = new ArrayList<DocumentField>();
        fields.add(new DocumentField("Kingdom", "", "biocode.Kingdom", String.class, false, false));
        fields.add(new DocumentField("Phylum", "", "biocode.Phylum", String.class, false, false));
        fields.add(new DocumentField("Sub-phylum", "", "biocode.Subphylum", String.class, false, false));
        fields.add(new DocumentField("Superclass", "", "biocode.Superclass", String.class, false, false));
        fields.add(new DocumentField("Class", "", "biocode.Class", String.class, false, false));
        fields.add(new DocumentField("Subclass", "", "biocode.Subclass", String.class, false, false));
        fields.add(new DocumentField("Infraclass", "", "biocode.Infraclass", String.class, false, false));
        fields.add(new DocumentField("Super-order", "", "biocode.Superorder", String.class, false, false));
        fields.add(new DocumentField("Order", "", "biocode.Ordr", String.class, false, false)); //ordr is not a typo
        fields.add(new DocumentField("Suborder", "", "biocode.Suborder", String.class, false, false));
        fields.add(new DocumentField("Infraorder", "", "biocode.Infraorder", String.class, false, false));
        fields.add(new DocumentField("Super-family", "", "biocode.Superfamily", String.class, false, false));
        fields.add(new DocumentField("Family", "", "biocode.Family", String.class, false, false));
        fields.add(new DocumentField("Subfamily", "", "biocode.Subfamily", String.class, false, false));
        fields.add(new DocumentField("Tribe", "", "biocode.Tribe", String.class, false, false));
        fields.add(new DocumentField("Subtribe", "", "biocode.Subtribe", String.class, false, false));
        fields.add(new DocumentField("Genus", "", "biocode.Genus", String.class, false, false));
        fields.add(new DocumentField("Subgenus", "", "biocode.Subgenus", String.class, false, false));
        fields.add(new DocumentField("Specific Epithet", "", "biocode.SpecificEpithet", String.class, false, false));
        fields.add(new DocumentField("Subspecific Epithet", "", "biocode.SubspecificEpithet", String.class, false, false));
        return fields;
    }

    public DocumentField getTissueSampleDocumentField() {
        return MOOREA_TISSUE_ID_FIELD;
    }

    public List<DocumentField> _getCollectionAttributes() {
        List<DocumentField> fields = new ArrayList<DocumentField>();

        fields.add(MOOREA_TISSUE_ID_FIELD);

        fields.add(new DocumentField("Specimen ID", "", "biocode_tissue.bnhm_id", String.class, false, false));
        fields.add(new DocumentField("Catalog Number", "", "biocode.CatalogNumberNumeric", String.class, false, false));
        fields.add(new DocumentField("Specimen Num Collector", "", "biocode.Specimen_Num_Collector", String.class, false, true));
        fields.add(new DocumentField("Specimen Holding Institution", "", "biocode.HoldingInstitution", String.class, false, true));
        fields.add(new DocumentField("Specimen Holding Institution Accession", "", "biocode.AccessionNumber", String.class, false, true));
        fields.add(new DocumentField("Tissue Holding Institution", "", "biocode_tissue.HoldingInstitution", String.class, false, true));

        fields.add(MOOREA_PLATE_NAME_FIELD);
        fields.add(MOOREA_WELL_NUMBER_FIELD);
        fields.add(MOOREA_TISSUE_BARCODE_FIELD);

        fields.add(new DocumentField("Preservative", "", "biocode_tissue.preservative", String.class, false, true));
        fields.add(new DocumentField("Tissue Type", "", "biocode_tissue.tissuetype", String.class, false, true));

        fields.add(new DocumentField("BOLD ProcessID", "", "biocode_tissue.molecular_id", String.class, true, false));

        fields.add(new DocumentField("Collector's Event ID", "", "biocode_collecting_event.Coll_EventID_collector", String.class, false, false));
        fields.add(PROJECT_FIELD);
        fields.add(SUBPROJECT_FIELD);
        fields.add(SUBSUBPROJECT_FIELD);
        fields.add(new DocumentField("Taxa Team", "", "biocode_collecting_event.TaxTeam", String.class, true, false));
        fields.add(new DocumentField("Collector", "", "biocode_collecting_event.Collector", String.class, true, false));
        fields.add(new DocumentField("Collector List", "", "biocode_collecting_event.Collector_List", String.class, true, false));
        fields.add(new DocumentField("Collection time", "", "biocode_collecting_event.CollectionTime", Date.class, true, false));
        fields.add(new DocumentField("Identified By", "", "biocode.IdentifiedBy", String.class, true, false));

        fields.add(new DocumentField("Specimen Notes", "", "biocode.notes", String.class, false, false));

        fields.add(DocumentField.ORGANISM_FIELD);
        fields.add(DocumentField.COMMON_NAME_FIELD);

        fields.add(new DocumentField("Lowest Taxon", "", "biocode.LowestTaxon", String.class, true, false));
        fields.add(new DocumentField("Lowest Taxon Level", "", "biocode.LowestTaxonLevel", String.class, true, false));
        fields.add(new DocumentField("Lowest Taxon (Generated)", "", "biocode.LowestTaxon_Generated", String.class, true, false));

        //fields.add(new DocumentField("Taxon Notes", "", "biocode_collecting_event.TaxonNotes", String.class, false, false));

        fields.add(new DocumentField("Country", "", "biocode_collecting_event.Country", String.class, false, false));
        fields.add(new DocumentField("Island", "", "biocode_collecting_event.Island", String.class, false, false));
        fields.add(new DocumentField("Island Group", "", "biocode_collecting_event.IslandGroup", String.class, false, false));
        fields.add(new DocumentField("State/Province", "", "biocode_collecting_event.StateProvince", String.class, true, false));
        fields.add(new DocumentField("County", "", "biocode_collecting_event.County", String.class, true, false));
        fields.add(new DocumentField("Locality", "", "biocode_collecting_event.Locality", String.class, true, false));
        fields.add(new DocumentField("Habitat", "", "biocode_collecting_event.Habitat", String.class, true, false));
        fields.add(new DocumentField("Micro Habitat", "", "biocode_collecting_event.MicroHabitat", String.class, true, false));


        fields.add(LONGITUDE_FIELD);
        fields.add(LATITUDE_FIELD);

        fields.add(new DocumentField("Minimum Elevation", "", "biocode_collecting_event.MinElevationMeters", Integer.class, false, false));
        fields.add(new DocumentField("Maximum Elevation", "", "biocode_collecting_event.MaxElevationMeters", Integer.class, false, false));

        fields.add(new DocumentField("Minimum Depth", "", "biocode_collecting_event.MinDepthMeters", Integer.class, false, false));
        fields.add(new DocumentField("Maximum Depth", "", "biocode_collecting_event.MaxDepthMeters", Integer.class, false, false));

        return fields;
    }

    public List<DocumentField> _getSearchAttributes() {
        List<DocumentField> fields = new ArrayList<DocumentField>();
        fields.addAll(getCollectionAttributes());
        fields.addAll(getTaxonomyAttributes());
        return fields;
    }


    @Override
    public DocumentField getLatitudeField() {
        return LATITUDE_FIELD;
    }

    @Override
    public DocumentField getLongitudeField() {
        return LONGITUDE_FIELD;
    }

    public int getTotalNumberOfSamples() throws ConnectionException {
        String query = "SELECT count(*) FROM biocode, biocode_collecting_event, biocode_tissue WHERE biocode.bnhm_id = biocode_tissue.bnhm_id AND biocode.coll_eventID = biocode_collecting_event.EventID";
        try {
            Statement statement = createStatement();

            ResultSet resultSet = statement.executeQuery(query);
            resultSet.next();
            return resultSet.getInt(1);

        } catch (SQLException e) {
            throw new ConnectionException(e.getMessage(), e);
        }
    }

    @Override
    public List<String> getTissueIdsMatchingQuery(Query query, List<FimsProject> projectsToMatch) throws ConnectionException {
        return getTissueIdsMatchingQuery(query, projectsToMatch, false);
    }

    @Override
    public List<String> getTissueIdsMatchingQuery(Query query, List<FimsProject> projectsToMatch, boolean allowEmpty) throws ConnectionException {
        StringBuilder queryBuilder = new StringBuilder();

        queryBuilder.append("SELECT biocode_tissue.bnhm_id, biocode_tissue.tissue_num FROM biocode, biocode_collecting_event, biocode_tissue WHERE biocode.bnhm_id = biocode_tissue.bnhm_id AND biocode.coll_eventID = biocode_collecting_event.EventID ");

        String sqlString = SqlUtilities.getQuerySQLString(query, getSearchAttributes(), true);

        if(projectsToMatch != null && !projectsToMatch.isEmpty()) {
            queryBuilder.append(" AND ");
            queryBuilder.append(BIOCODE_PROJECT_FIELD.getCode()).append(" IN ");
            SqlUtilities.appendSetOfQuestionMarks(queryBuilder, projectsToMatch.size());
        } else if(sqlString == null) {
            if (!Dialogs.showContinueCancelDialog("The Moorea FIMS contains a large number of tissue records.  " +
                    "This search may take a long time and cause Geneious to become slow.\n\n" +
                    "Are you sure you want to continue?", "Large Number of Tissues", null, Dialogs.DialogIcon.INFORMATION)) {
                return Collections.emptyList();
            }
        }

        if(sqlString != null) {
            queryBuilder.append(" AND ");
            queryBuilder.append(sqlString);
        }

        String queryString = queryBuilder.toString();
        PreparedStatement statement = null;
        try {
            statement = prepareStatement(queryString);
            List<Object> projectNames = new ArrayList<Object>();
            if(projectsToMatch != null) {
                for (FimsProject fimsProject : projectsToMatch) {
                    projectNames.add(fimsProject.getName());
                }
            }
            SqlUtilities.printSql(queryString, projectNames);
            SqlUtilities.fillStatement(projectNames, statement);
            ResultSet resultSet = statement.executeQuery();
            List<String> tissueIds = new ArrayList<String>();
            while(resultSet.next()){
                tissueIds.add(resultSet.getString("biocode_tissue.bnhm_id") + "." + resultSet.getInt("biocode_tissue.tissue_num"));
            }
            resultSet.close();
            return tissueIds;
        } catch (SQLException e) {
            throw new ConnectionException(e);
        } finally {
            SqlUtilities.cleanUpStatements(statement);
        }
    }

    @Override
    protected List<FimsSample> _retrieveSamplesForTissueIds(List<String> tissueIds, RetrieveCallback callback) throws ConnectionException {
        List<FimsSample> samples = new ArrayList<FimsSample>();
        StringBuilder queryBuilder = new StringBuilder("SELECT * FROM biocode, biocode_collecting_event, biocode_tissue WHERE biocode.bnhm_id = biocode_tissue.bnhm_id AND biocode.coll_eventID = biocode_collecting_event.EventID");

        queryBuilder.append(" AND (");

        List<Object> parameters = new ArrayList<Object>();
        boolean first = true;
        for (String tissueId : tissueIds) {
            int dot = tissueId.lastIndexOf(".");
            if (dot == -1) {
                continue; // Not in our expected format.  Means there will be no match in the FIMS
            }
            String sampleId = tissueId.substring(0, dot);
            try {
                Integer tissueNum = Integer.parseInt(tissueId.substring(dot + 1));
                parameters.add(sampleId);
                parameters.add(tissueNum);
            } catch (NumberFormatException e) {
                continue;  // Not in our expected format.  Means there will be no match in the FIMS
            }

            if (!first) {
                queryBuilder.append(" OR ");
            } else {
                first = false;
            }
            queryBuilder.append("(biocode_tissue.bnhm_id = ? AND biocode_tissue.tissue_num = ?)");
        }
        queryBuilder.append(")");
        if(parameters.isEmpty()) {  // No valid tissue IDs
            return Collections.emptyList();
        }

        SqlUtilities.printSql(queryBuilder.toString(), parameters);
        PreparedStatement select = null;
        try {
            select = getConnection().prepareStatement(queryBuilder.toString());
            for (int index=1; index<=parameters.size(); index++) {
                select.setObject(index, parameters.get(index - 1));
            }
            ResultSet resultSet = select.executeQuery();
            while(resultSet.next()) {
                samples.add(new MooreaFimsSample(resultSet, this));
            }
            return samples;
        } catch (SQLException e) {
            throw new ConnectionException("Failed to retrieve samples from FIMS: " + e.getMessage(), e);
        } finally {
            SqlUtilities.cleanUpStatements(select);
        }
    }

    public boolean storesPlateAndWellInformation() {
        return true;
    }

    @Override
    public DocumentField getPlateDocumentField() {
        return MOOREA_PLATE_NAME_FIELD;
    }

    @Override
    public DocumentField getWellDocumentField() {
        return MOOREA_WELL_NUMBER_FIELD;
    }

    /*
    this method probably doesn't need to be overridden anymore, but I'm leaving the code here as it works and is probably more efficient than the method it overrides...
     */
    @Override
    public Map<String, String> getTissueIdsFromFimsTissuePlate(String plateId) throws ConnectionException{
        if(plateId == null || plateId.length() == 0) {
            return Collections.emptyMap();
        }

        String query = "SELECT biocode_tissue.bnhm_id, biocode_tissue.tissue_num, biocode_tissue.well_number96 FROM biocode_tissue WHERE biocode_tissue.format_name96='"+plateId+"'";

        try {
            PreparedStatement statement = prepareStatement(query);
            ResultSet resultSet = statement.executeQuery();
            Map<String, String> result = new HashMap<String, String>();
            while(resultSet.next()) {
                result.put(resultSet.getString("biocode_tissue.well_number96"), resultSet.getString("biocode_tissue.bnhm_id")+"."+resultSet.getString("biocode_tissue.tissue_num"));
            }
            statement.close();
            return result;

        } catch (SQLException e) {
            e.printStackTrace();
            throw new ConnectionException("Error fetching tissue data from FIMS", e);
        }
    }

    public boolean hasPhotos() {
        return true;
    }

    @Override
    public List<String> getImageUrls(FimsSample fimsSample) throws IOException {
        URL xmlUrl = new URL("http://calphotos.berkeley.edu/cgi-bin/img_query?getthumbinfo=1&specimen_no="+ URLEncoder.encode(fimsSample.getSpecimenId(), "UTF-8")+"&format=xml&num=all&query_src=lims");
        InputStream in = xmlUrl.openStream();
        SAXBuilder builder = new SAXBuilder();
        Element root;
        try {
            root = builder.build(in).detachRootElement();
        } catch (JDOMException e) {
            IOException exception = new IOException("Error parsing server response: "+e.getMessage());
            exception.initCause(e);
            throw exception;
        }
        XMLOutputter out = new XMLOutputter(Format.getPrettyFormat());
        out.output(root, System.out);
        List<Element> imageUrls = root.getChildren("enlarge_jpeg_url");
        List<String> result = new ArrayList<String>();
        for(Element e : imageUrls) {
            result.add(e.getText());
        }
        return result;
    }

    @Override
    public List<FimsProject> getProjects() throws DatabaseServiceException {
        List<List<String>> combinations = new ArrayList<List<String>>();

        String columns = "biocode.ProjectCode, biocode.SubProject, biocode.SubSubProject";
        PreparedStatement select = null;
        try {
            select = prepareStatement("SELECT " + columns + " FROM biocode GROUP BY " + columns);
            ResultSet resultSet = select.executeQuery();
            while(resultSet.next()) {
                String proj = getValueAsTrimmedString(resultSet.getString(1));
                String sub = getValueAsTrimmedString(resultSet.getString(2));
                String subsub = getValueAsTrimmedString(resultSet.getString(3));
                combinations.add(Arrays.asList(proj, sub, subsub));
            }
        } catch (SQLException e) {
            throw new DatabaseServiceException(e, "Could not retrieve projects from FIMS: " + e.getMessage(), false);
        } finally {
            SqlUtilities.cleanUpStatements(select);
        }

        return getProjectsFromListOfCombinations(combinations, true);
    }

    String getValueAsTrimmedString(String columnValue) {
        if(columnValue == null) {
            return "";
        } else {
            return columnValue.trim();
        }
    }

    @Override
    public Map<String, Collection<FimsSample>> getProjectsForSamples(Collection<FimsSample> samples) {
        List<DocumentField> projectFieldsLowestToHighest = Arrays.asList(SUBSUBPROJECT_FIELD, SUBPROJECT_FIELD, PROJECT_FIELD);
        Multimap<String, FimsSample> projects = ArrayListMultimap.create();
        for (FimsSample sample : samples) {
            String projectName = getProjectForSample(projectFieldsLowestToHighest, sample);
            if(projectName != null) {
                projects.put(projectName, sample);
            }
        }
        return projects.asMap();
    }

    /**
     * Typically {@link com.biomatters.geneious.publicapi.documents.DocumentField} created by the Moorea FIMS use the
     * column name as the code {@link com.biomatters.geneious.publicapi.documents.DocumentField#getCode()}.  But the
     * following are some special cases where that is not the case.
     * <ul>
     *     <li>We are using a core Geneious document field to represent a column in the FIMS</li>
     *     <li>The column we are using has changed from the original column, but we don't want to create a new document field</li>
     *     <li>The document field is a concatenation of several fields in the FIMS database</li>
     * </ul>
     * This method handles the first two cases where one SQL column maps to the document field.  In any other case it
     * will just return the code for the supplied document field.
     *
     * @param field The {@link com.biomatters.geneious.publicapi.documents.DocumentField}, cannot be null.
     * @return The matching SQL column name
     */
    public static String getSQLColumnNameForDocumentField(DocumentField field) {
        if (field.equals(DocumentField.ORGANISM_FIELD)) {
            return "biocode.ScientificName"; //we use the standard organism field so we need to map it to the correct database id
        } else if (field.equals(DocumentField.COMMON_NAME_FIELD)) {
            return "biocode.ColloquialName"; //we use the standard common name field so we need to map it to the correct database id
        } else if(field.equals(PROJECT_FIELD)) {
            return BIOCODE_PROJECT_FIELD.getCode();
        } else {
            return field.getCode();
        }
    }
}
