package com.biomatters.plugins.biocode.labbench.fims;

import com.biomatters.geneious.publicapi.databaseservice.*;
import com.biomatters.geneious.publicapi.documents.AnnotatedPluginDocument;
import com.biomatters.geneious.publicapi.documents.DocumentField;
import com.biomatters.geneious.publicapi.plugin.Options;
import com.biomatters.geneious.publicapi.utilities.StringUtilities;
import com.biomatters.plugins.biocode.BiocodeUtilities;
import com.biomatters.plugins.biocode.labbench.*;
import com.biomatters.options.PasswordOption;

import java.sql.*;
import java.util.*;
import java.util.Date;
import java.net.URL;
import java.net.URLEncoder;
import java.io.InputStream;
import java.io.IOException;

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

    private static final DocumentField MOOREA_TISSUE_ID_FIELD = new DocumentField("Tissue ID", "", "tissueId", String.class, true, false);
    private static final DocumentField MOOREA_PLATE_NAME_FIELD = new DocumentField("Plate Name (FIMS)", "", "biocode_tissue.format_name96", String.class, true, false);
    private static final DocumentField MOOREA_WELL_NUMBER_FIELD = new DocumentField("Well Number (FIMS)", "", "biocode_tissue.well_number96", String.class, true, false);
    private static final DocumentField MOOREA_TISSUE_BARCODE_FIELD = new DocumentField("Tissue Barcode", "", "biocode_tissue.tissue_barcode", String.class, true, false);
    private static final DocumentField LONGITUDE_FIELD = new DocumentField("Longitude", "", "biocode_collecting_event.DecimalLongitude", Double.class, false, false);
    private static final DocumentField LATITUDE_FIELD = new DocumentField("Latitude", "", "biocode_collecting_event.DecimalLatitude", Double.class, false, false);

    public String getLabel() {
        return "Moorea FIMS";
    }

    public String getName() {
        return "biocode";
    }

    public String getDescription() {
        return "A connection to the Moorea FIMS database";
    }

    public boolean requiresMySql() {
        return true;
    }

    public PasswordOptions getConnectionOptions() {
        return new MooreaFimsConnectionOptions(this.getClass(), "mooreaFIMS");
    }

    public void _connect(Options options) throws ConnectionException {

        //instantiate the driver class
        try {
            Class driverClass = BiocodeService.getDriverClass();
            if(driverClass == null) {
                throw new ConnectionException("You need to specify the location of your MySQL Driver file");
            }
            driver = (Driver) driverClass.newInstance();
        } catch (InstantiationException e) {
            throw new ConnectionException("Could not instantiate SQL driver.");
        } catch (IllegalAccessException e) {
            throw new ConnectionException("Could not access SQL driver.");
        }


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
        fields.add(new DocumentField("Project Name", "", "biocode_collecting_event.ProjectCode", String.class, false, false));
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

    public List<DocumentField> getSearchAttributes() {
        List<DocumentField> fields = new ArrayList<DocumentField>();
        fields.addAll(getCollectionAttributes());
        fields.addAll(getTaxonomyAttributes());
        return fields;
    }

    public BiocodeUtilities.LatLong getLatLong(AnnotatedPluginDocument annotatedDocument) {
        Object latObject = annotatedDocument.getFieldValue(LATITUDE_FIELD);
        Object longObject = annotatedDocument.getFieldValue(LONGITUDE_FIELD);
        if (latObject == null || longObject == null) {
            return null;
        }
        return new BiocodeUtilities.LatLong((Double)latObject, (Double)longObject);
    }

    public int getTotalNumberOfSamples() throws ConnectionException {
        String query = "SELECT count(*) FROM biocode, biocode_collecting_event, biocode_tissue WHERE biocode.bnhm_id = biocode_tissue.bnhm_id AND biocode.coll_eventID = biocode_collecting_event.EventID";
        try {
            Statement statement = connection.createStatement();

            ResultSet resultSet = statement.executeQuery(query);
            resultSet.next();
            return resultSet.getInt(1);

        } catch (SQLException e) {
            throw new ConnectionException(e.getMessage(), e);
        }
    }

    public void getAllSamples(RetrieveCallback callback) throws ConnectionException{
        String query = "SELECT * FROM biocode, biocode_collecting_event, biocode_tissue WHERE biocode.bnhm_id = biocode_tissue.bnhm_id AND biocode.coll_eventID = biocode_collecting_event.EventID";
        try {
            Statement statement = connection.createStatement();

            statement.setFetchSize(Integer.MIN_VALUE);

            ResultSet resultSet = statement.executeQuery(query);
            while(resultSet.next() && !callback.isCanceled()) {
                callback.add(new TissueDocument(new MooreaFimsSample(resultSet, this)), null);
            }

        } catch (SQLException e) {
            throw new ConnectionException(e.getMessage(), e);
        }
    }

    public List<FimsSample> _getMatchingSamples(Query query) throws ConnectionException{
        StringBuilder queryBuilder = new StringBuilder();
        

        queryBuilder.append("SELECT * FROM biocode, biocode_collecting_event, biocode_tissue WHERE biocode.bnhm_id = biocode_tissue.bnhm_id AND biocode.coll_eventID = biocode_collecting_event.EventID AND ");

        String sqlString = SqlUtilities.getQuerySQLString(query, getSearchAttributes(), true);
        if(sqlString == null) {
            return Collections.emptyList();
        }
        queryBuilder.append(sqlString);
        

        String queryString = queryBuilder.toString();

        System.out.println(queryString);
        if(connection == null) {
            throw new IllegalStateException("Not connected!");
        }
        try {
            Statement statement = connection.createStatement();
            ResultSet resultSet = statement.executeQuery(queryString);
            List<FimsSample> samples = new ArrayList<FimsSample>();
            while(resultSet.next()){
                samples.add(new MooreaFimsSample(resultSet, this));
            }
            resultSet.close();
            return samples;
        } catch (SQLException e) {
            throw new ConnectionException(e);
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
            PreparedStatement statement = connection.prepareStatement(query);
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

    public Map<String, String> getTissueIdsFromFimsExtractionPlate(String plateId) throws ConnectionException{
        if(plateId == null || plateId.length() == 0) {
            return Collections.emptyMap();
        }

        return getFimsPlateData("biocode_extract.format_name96='"+plateId+"'", "biocode_extract.well_number96");
    }

    public Map<String, String> getTissueIdsFromExtractionBarcodes(List<String> extractionIds) throws ConnectionException{
        if(extractionIds == null || extractionIds.size() == 0) {
            return Collections.emptyMap();
        }

        StringBuilder query = new StringBuilder("(");

        List<String> queryTerms = new ArrayList<String>();
        for(String s : extractionIds) {
            queryTerms.add("biocode_extract.extract_barcode = '"+s+"'");
        }

        query.append(StringUtilities.join(" OR ", queryTerms));
        query.append(");");
        return getFimsPlateData(query.toString(), "biocode_extract.extract_barcode");
    }

    private Map<String, String> getFimsPlateData(String andQuery, String colToUseForKey) throws ConnectionException {
        String query = "SELECT biocode_extract.extract_barcode, biocode_tissue.bnhm_id, biocode_tissue.tissue_num, biocode_extract.format_name96, biocode_extract.well_number96, biocode_tissue.well_number96 FROM biocode_extract, biocode_tissue WHERE biocode_extract.from_tissue_seq_num = biocode_tissue.seq_num  AND "+andQuery;

        try {
            PreparedStatement statement = connection.prepareStatement(query);
            ResultSet resultSet = statement.executeQuery();
            Map<String, String> result = new HashMap<String, String>();
            while(resultSet.next()) {
                result.put(resultSet.getString(colToUseForKey), resultSet.getString("biocode_tissue.bnhm_id")+"."+resultSet.getString("biocode_tissue.tissue_num"));
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
        Element root = null;
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
}
