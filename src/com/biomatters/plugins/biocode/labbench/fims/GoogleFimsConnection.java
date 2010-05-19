package com.biomatters.plugins.biocode.labbench.fims;

import java.util.prefs.Preferences;
import java.util.*;
import java.util.regex.Pattern;
import java.util.regex.MatchResult;
import java.io.IOException;
import java.net.URL;
import java.net.URLEncoder;
import java.net.MalformedURLException;

import com.biomatters.geneious.publicapi.plugin.Options;
import com.biomatters.geneious.publicapi.documents.DocumentField;
import com.biomatters.geneious.publicapi.documents.AnnotatedPluginDocument;
import com.biomatters.geneious.publicapi.databaseservice.Query;
import com.biomatters.plugins.biocode.labbench.ConnectionException;
import com.biomatters.plugins.biocode.labbench.FimsSample;
import com.biomatters.plugins.biocode.labbench.PasswordOption;
import com.biomatters.plugins.biocode.BiocodeUtilities;
import com.google.gdata.client.GoogleService;
import com.google.gdata.client.ClientLoginAccountType;
import com.google.gdata.client.Service;
import com.google.gdata.client.spreadsheet.SpreadsheetService;
import com.google.gdata.util.AuthenticationException;
import com.google.gdata.util.ServiceException;
import com.google.gdata.util.ContentType;
import com.google.gdata.data.spreadsheet.SpreadsheetEntry;
import com.google.gdata.data.spreadsheet.SpreadsheetFeed;


/**
 * User: Steve
 * Date: 26/02/2010
 * Time: 9:51:03 PM
 */
public class GoogleFimsConnection extends FIMSConnection{
    Preferences prefs = Preferences.userNodeForPackage(this.getClass());


  private static final String SERVICE_URL =
      "http://tables.googlelabs.com/api/query";

  private static final Pattern CSV_VALUE_PATTERN =
      Pattern.compile("([^,\\r\\n\"]*|\"(([^\"]*\"\")*[^\"]*)\")(,|\\r?\\n)");

  private GoogleService service;


    int tissueCol;

    int specimenCol;

    public String getName() {
        return "google";
    }

    public String getDescription() {
        return  "Read field information from a Google spreadsheet";
    }

    public String getLabel() {
        return "Google Spreadsheet";
    }

    public Options getConnectionOptions() {
        return new GoogleFimsOptions(this.getClass());
    }

    private List<Options.OptionValue> getTableColumns() {
        return null; //todo
    }

    private List<DocumentField> fields;
     List<DocumentField> taxonomyFields;

    public void connect(Options options) throws ConnectionException {
        String email = options.getValueAsString("email");
        String password = ((PasswordOption)options.getOption("password")).getPassword();
//        service = new GoogleService("fusiontables", "fusiontables.ApiExample"); //todo: get an app name...
//        try {
//            service.setUserCredentials(email, password, ClientLoginAccountType.GOOGLE);
//        } catch (AuthenticationException e) {
//            throw new ConnectionException(e.getMessage(), e);
//        }

        SpreadsheetService myService = new SpreadsheetService("exampleCo-exampleApp-1");
        try {
            myService.setUserCredentials(email, password);

            URL feedUrl = new URL("http://spreadsheets.google.com/feeds/spreadsheets/private/full");

            SpreadsheetFeed feed = myService.getFeed(feedUrl, SpreadsheetFeed.class);
            List<SpreadsheetEntry> spreadsheets = feed.getEntries();
            for (int i = 0; i < spreadsheets.size(); i++) {
              SpreadsheetEntry entry = spreadsheets.get(i);
              System.out.println("\t" + entry.getTitle().getPlainText());
            }
            

        } catch (AuthenticationException e) {
            e.printStackTrace();
            throw new ConnectionException(e.getMessage());
        } catch (MalformedURLException e) {
            throw new ConnectionException(e.getMessage());
        } catch (IOException e) {
            throw new ConnectionException(e.getMessage());
        } catch (ServiceException e) {
            throw new ConnectionException(e.getMessage());
        }

    }

    public static List<SpreadsheetEntry> getSpreadsheetList(String email, String password) throws ServiceException, IOException {
        SpreadsheetService myService = new SpreadsheetService("exampleCo-exampleApp-1");
        myService.setUserCredentials(email, password);

        URL feedUrl = new URL("http://spreadsheets.google.com/feeds/spreadsheets/private/full");

        SpreadsheetFeed feed = myService.getFeed(feedUrl, SpreadsheetFeed.class);
        return feed.getEntries();
    }


    /**
   * Fetches the results for a select query. Prints them to standard
   * output, surrounding every field with (@code |}.
   *
   * This code uses the GDataRequest class and getRequestFactory() method
   * from the Google Data APIs Client Library.
   * The Google Fusion Tables API-specific part is in the construction
   * of the service URL. A Google Fusion Tables API SELECT statement
   * will be passed in to this method in the selectQuery parameter.
   */
//  private void runSelect(String selectQuery) throws IOException,
//            ServiceException {
//    URL url = new URL(
//        SERVICE_URL + "?sql=" + URLEncoder.encode(selectQuery, "UTF-8"));
//    Service.GDataRequest request = service.getRequestFactory().getRequest(
//            Service.GDataRequest.RequestType.QUERY, url, ContentType.TEXT_PLAIN);
//
//    request.execute();
//
//  /* Prints the results of the query.                */
//  /* No Google Fusion Tables API-specific code here. */
//
//    Scanner scanner = new Scanner(request.getResponseStream());
//    while (scanner.hasNextLine()) {
//      scanner.findWithinHorizon(CSV_VALUE_PATTERN, 0);
//      MatchResult match = scanner.match();
//      String quotedString = match.group(2);
//      String decoded = quotedString == null ? match.group(1)
//          : quotedString.replaceAll("\"\"", "\"");
//      System.out.print("|" + decoded);
//      if (!match.group(4).equals(",")) {
//        System.out.println("|");
//      }
//    }
//  }

    private static DocumentField getTableCol(List<DocumentField> fields, int colIndex) {
        for(DocumentField field : fields) {
            if(field.getCode().equals(""+colIndex)) {
                return field;
            }
        }
        return null;
    }

    public void disconnect() throws ConnectionException {
        tissueCol = specimenCol = -1;
        fields = null;
    }

    public List<DocumentField> getSearchAttributes() {
        List<DocumentField> searchAttributes = new ArrayList<DocumentField>();
        searchAttributes.addAll(fields);
        searchAttributes.addAll(taxonomyFields);
        return searchAttributes;
    }

    public DocumentField getTissueSampleDocumentField() {
        return getTableCol(fields, tissueCol);
    }

    public List<DocumentField> getCollectionAttributes() {
        return fields;
    }

    public List<DocumentField> getTaxonomyAttributes() {
        return taxonomyFields;
    }

    public List<FimsSample> _getMatchingSamples(Query query) {
        return null;//todo
    }

    public Map<String, String> getTissueIdsFromExtractionBarcodes(List<String> extractionIds) throws ConnectionException{
        return Collections.emptyMap();
    }

    public Map<String, String> getTissueIdsFromFimsExtractionPlate(String plateId) throws ConnectionException{
        return Collections.emptyMap();
    }

    public Map<String, String> getTissueIdsFromFimsTissuePlate(String plateId) throws ConnectionException{
        return Collections.emptyMap();
    }

    public boolean canGetTissueIdsFromFimsTissuePlate() {
        return false;
    }

    public BiocodeUtilities.LatLong getLatLong(AnnotatedPluginDocument annotatedDocument) {
        return null;
    }
}
