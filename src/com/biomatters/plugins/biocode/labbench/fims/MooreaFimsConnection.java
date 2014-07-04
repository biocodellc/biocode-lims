package com.biomatters.plugins.biocode.labbench.fims;

import com.biomatters.geneious.publicapi.databaseservice.*;
import com.biomatters.geneious.publicapi.documents.DocumentField;
import com.biomatters.geneious.publicapi.plugin.Options;
import com.biomatters.plugins.biocode.labbench.*;
import com.biomatters.plugins.biocode.utilities.PasswordOption;

import java.sql.*;
import java.util.*;
import java.util.Date;
import java.net.URL;
import java.net.URLEncoder;
import java.io.InputStream;
import java.io.IOException;

import com.biomatters.plugins.biocode.utilities.SqlUtilities;
import org.jdom.input.SAXBuilder;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.output.XMLOutputter;
import org.jdom.output.Format;

/**
 * @author steve
 * @version $Id: 12/05/2009 5:51:15 AM steve $
 */
@SuppressWarnings({"ConstantConditions"})
public class MooreaFimsConnection extends FIMSConnection{

    @SuppressWarnings({"FieldCanBeLocal"})
    private Driver driver;
    private Connection connection;

    public static final DocumentField MOOREA_TISSUE_ID_FIELD = new DocumentField("Tissue ID", "", "tissueId", String.class, true, false);
    private static final DocumentField MOOREA_PLATE_NAME_FIELD = new DocumentField("Plate Name (FIMS)", "", "biocode_tissue.format_name96", String.class, true, false);
    private static final DocumentField MOOREA_WELL_NUMBER_FIELD = new DocumentField("Well Number (FIMS)", "", "biocode_tissue.well_number96", String.class, true, false);
    private static final DocumentField MOOREA_TISSUE_BARCODE_FIELD = new DocumentField("Tissue Barcode", "", "biocode_tissue.tissue_barcode", String.class, true, false);
    private static final DocumentField LONGITUDE_FIELD = new DocumentField("Longitude", "", "biocode_collecting_event.DecimalLongitude", Double.class, false, false);
    private static final DocumentField LATITUDE_FIELD = new DocumentField("Latitude", "", "biocode_collecting_event.DecimalLatitude", Double.class, false, false);
    private static final DocumentField PROJECT_FIELD = new DocumentField("Project Name", "", "biocode_collecting_event.ProjectCode", String.class, false, false);

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

        //connect
        Properties properties = new Properties();
        properties.put("user", options.getValueAsString("username"));
        properties.put("password", ((PasswordOption)options.getOption("password")).getPassword());
        try {
            DriverManager.setLoginTimeout(20);
            connection = driver.connect("jdbc:mysql://"+options.getValueAsString("serverUrl")+":"+options.getValueAsString("serverPort"), properties);
            Statement statement = connection.createStatement();
            statement.execute("USE biocode");
        } catch (SQLException e1) {
            throw new ConnectionException("Failed to connect to the LIMS database: "+e1.getMessage());
        }
    }

    private Statement createStatement() throws SQLException{
        if(connection == null) {
            throw new SQLException("You are not connected to the FIMS database");
        }
        Statement statement = connection.createStatement();
        statement.setQueryTimeout(BiocodeService.STATEMENT_QUERY_TIMEOUT);
        return statement;
    }

    private PreparedStatement prepareStatement(String query) throws SQLException{
        if(connection == null) {
            throw new SQLException("You are not connected to the FIMS database");
        }
        PreparedStatement statement = connection.prepareStatement(query);
        statement.setQueryTimeout(BiocodeService.STATEMENT_QUERY_TIMEOUT);
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
        connection = null;
    }

    public List<DocumentField> getTaxonomyAttributes() {
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

    public List<DocumentField> getCollectionAttributes() {
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
        StringBuilder queryBuilder = new StringBuilder();

        queryBuilder.append("SELECT biocode_tissue.bnhm_id, biocode_tissue.tissue_num FROM biocode, biocode_collecting_event, biocode_tissue WHERE biocode.bnhm_id = biocode_tissue.bnhm_id AND biocode.coll_eventID = biocode_collecting_event.EventID AND ");

        if(projectsToMatch != null && !projectsToMatch.isEmpty()) {
            queryBuilder.append(PROJECT_FIELD.getCode()).append(" IN ");
            SqlUtilities.appendSetOfQuestionMarks(queryBuilder, projectsToMatch.size());
            queryBuilder.append(" AND ");
        }

        String sqlString = SqlUtilities.getQuerySQLString(query, getSearchAttributes(), true);
        if(sqlString == null) {
            return Collections.emptyList();
        }
        queryBuilder.append(sqlString);


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
            try {
                int dot = tissueId.lastIndexOf(".");
                if (dot == -1) {
                    continue; // Not in our expected format.  Means there will be no match in the FIMS
                }
                String sampleId = tissueId.substring(0, dot);
                parameters.add(sampleId);
                Integer tissueNum = Integer.parseInt(tissueId.substring(dot + 1));
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
            select = connection.prepareStatement(queryBuilder.toString());
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
        List<FimsProject> projects = new ArrayList<FimsProject>();

        PreparedStatement select = null;
        try {
            select = prepareStatement("SELECT DISTINCT(" + PROJECT_FIELD.getCode() + ") FROM biocode_collecting_event");
            ResultSet resultSet = select.executeQuery();
            while(resultSet.next()) {
                String name = resultSet.getString(1).trim();
                if(name.length() > 0) {
                    projects.add(new FimsProject(name, name, null));
                }
            }
        } catch (SQLException e) {
            throw new DatabaseServiceException(e, "Could not retrieve projects from FIMS: " + e.getMessage(), false);
        } finally {
            SqlUtilities.cleanUpStatements(select);
        }

        return projects;
    }

    @Override
    public List<String> getProjectsForSamples(Collection<FimsSample> samples) {
        Set<String> projects = new HashSet<String>();
        for (FimsSample sample : samples) {
            Object projectName = sample.getFimsAttributeValue(PROJECT_FIELD.getCode());
            if(projectName != null) {
                projects.add(projectName.toString());
            }
        }
        return new ArrayList<String>(projects);
    }
}
