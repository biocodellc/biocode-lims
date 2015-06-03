package com.biomatters.plugins.biocode.labbench.fims;

import com.biomatters.geneious.publicapi.documents.DocumentField;
import com.biomatters.geneious.publicapi.utilities.FileUtilities;
import com.biomatters.plugins.biocode.BiocodePlugin;
import com.biomatters.plugins.biocode.XmlUtilities;
import com.biomatters.plugins.biocode.labbench.BiocodeService;
import com.biomatters.plugins.biocode.labbench.LoginOptions;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.java6.auth.oauth2.FileCredentialStore;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.JsonParser;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.fusiontables.Fusiontables;
import com.google.api.services.fusiontables.FusiontablesScopes;
import com.google.api.services.fusiontables.model.Column;
import com.google.api.services.fusiontables.model.Sqlresponse;
import com.google.api.services.fusiontables.model.Table;
import com.google.api.services.fusiontables.model.TableList;

import java.io.*;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.util.*;

/**
 * @author Steve
 * @version $Id$
 *          <p/>
 *          Created on 3/12/12 11:51 AM
 */


public class FusionTableUtils {
    private static final String CLIENT_ID = "471304727334.apps.googleusercontent.com";
    private final String CLIENT_SECRET = "z0tyi6F8mkObEdY3zYHKZP1X";


    /** Global instance of the HTTP transport. */
      private static final HttpTransport HTTP_TRANSPORT = new NetHttpTransport();

      /** Global instance of the JSON factory. */
      private static final JsonFactory JSON_FACTORY = new GsonFactory();


    /** Authorizes the installed application to access user's protected data. */
    public static Credential authorize() throws IOException {
        // load client secrets
        GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(
                JSON_FACTORY, new InputStreamReader(FusionTableUtils.class.getResourceAsStream("/client_secrets.json")));
        if (clientSecrets.getDetails().getClientId().startsWith("Enter")
                || clientSecrets.getDetails().getClientSecret().startsWith("Enter ")) {
            throw new RuntimeException("Google API client id's are missing");
        }
        // set up file credential store
        FileCredentialStore credentialStore = new FileCredentialStore(
                getCredentialStoreLocation(), JSON_FACTORY);
        // set up authorization code flow
        GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                HTTP_TRANSPORT, JSON_FACTORY, clientSecrets,
                Arrays.asList(FusiontablesScopes.FUSIONTABLES_READONLY, "https://www.googleapis.com/auth/userinfo.email")).setCredentialStore(credentialStore)
                .build();
        // authorize
        return new AuthorizationCodeInstalledApp(flow, new LocalServerReceiver()).authorize("user");
    }

    private static Credential getCredentialOnlyIfCached() throws IOException{

        if(getCredentialStoreLocation().exists()) {
            JsonParser jsonParser = JSON_FACTORY.createJsonParser(new FileInputStream(getCredentialStoreLocation()));
            Object parsedJson = jsonParser.parseAndClose(Object.class, null);
            if(parsedJson instanceof Map) {
                Map parsedJsonMap = (Map)parsedJson;
                if(parsedJsonMap.get("credentials") != null && parsedJsonMap.get("credentials") instanceof Map) { //if there is a credential in the store
                    if(((Map)parsedJsonMap.get("credentials")).get("user") != null) { //there is a stored credential for 'user'
                        return authorize();
                    }
                }
            }
        }
        return null;
    }

    public static void clearCachedAccessTokens() {
        FileUtilities.deleteFileOrDirectory(getCredentialStoreLocation(), FileUtilities.RetryPolicy.Now);
    }

    private static File getCredentialStoreLocation() {
        return new File(BiocodeService.getInstance().getDataDirectory(), ".credentials/fusiontables.json");
    }

    //don't know if there is a wrapper for this, so just calling the rest method directly...
    public static String getAccountName() throws IOException {
        Credential credential = getCredentialOnlyIfCached();
        if(credential == null) {
            return null;
        }

        URL url = new URL("https://www.googleapis.com/oauth2/v1/userinfo?alt=json");

        URLConnection urlConnection = url.openConnection();
        urlConnection.setRequestProperty("user-agent", "MooreaBiocodePlugin/"+ BiocodePlugin.PLUGIN_VERSION);
        urlConnection.setRequestProperty("authorization", "Bearer "+credential.getAccessToken());

        JsonParser jsonParser = JSON_FACTORY.createJsonParser(urlConnection.getInputStream());
        Object userData = jsonParser.parseAndClose(Object.class, null);

        if(userData instanceof Map) {
            return (String)((Map)userData).get("email");
        }

        return null;
    }

    public static List<com.google.api.services.fusiontables.model.Table> listTables(int timeout) throws IOException {
        Fusiontables fusiontables = prepareForApiCall(timeout);
        if(fusiontables == null) {
            return Collections.emptyList();
        }

        // Fetch the table list
        Fusiontables.Table.List listTables = fusiontables.table().list();
        TableList tablelist = listTables.execute();

        if (tablelist.getItems() == null || tablelist.getItems().isEmpty()) {
            return Collections.emptyList();
        }

        return tablelist.getItems();
    }

    private static Fusiontables prepareForApiCall(final int timeoutInSeconds) throws IOException {
        Credential credential = getCredentialOnlyIfCached();
        if(credential == null) {
            return null;
        }

        Fusiontables.Builder builder = new Fusiontables.Builder(HTTP_TRANSPORT, JSON_FACTORY, credential).
                setApplicationName("MooreaBiocodePlugin/" + BiocodePlugin.PLUGIN_VERSION);
        return applyTimeoutToBuilder(builder, timeoutInSeconds).build();
    }

    private static Fusiontables.Builder applyTimeoutToBuilder(Fusiontables.Builder builder, final int timeoutInSeconds) {
        final HttpRequestInitializer original = builder.getHttpRequestInitializer();
        return builder.setHttpRequestInitializer(new HttpRequestInitializer() {
            @Override
            public void initialize(HttpRequest request) throws IOException {
                original.initialize(request);
                request.setConnectTimeout(timeoutInSeconds * 1000);
                request.setReadTimeout(timeoutInSeconds * 1000);
            }
        });
    }

    private static Table getTable(String tableId, int timeout) throws IOException {
        Fusiontables fusiontables = prepareForApiCall(timeout);
        if(fusiontables == null) {
            return null;
        }

        // Fetch the table list
        Fusiontables.Table.Get get = fusiontables.table().get(tableId);
        Table table = get.execute();

        return table;
    }

    public static List<DocumentField> getTableColumns(String tableId, int timeout) throws IOException {
        Table table = getTable(tableId, timeout);
        if(table == null) {
            return Collections.emptyList();
        }
        List<DocumentField> fields = new ArrayList<DocumentField>();
        for(Column col : table.getColumns()) {
            fields.add(new DocumentField(col.getName(), "", XmlUtilities.encodeXMLChars(col.getName()), getColumnClass(col.getType()), false, false));
        }
        return fields;
    }

    private static Class getColumnClass(String valueType) {
        if(valueType.toLowerCase().equals("string")) {
            return String.class;
        }
        if(valueType.toLowerCase().equals("number")) {
            return Double.class;
        }
        if(valueType.toLowerCase().equals("datetime")) {
            return Date.class;
        }
        if(valueType.toLowerCase().equals("location")) {
            return String.class;
        }
        return String.class;
    }


    public static Sqlresponse queryTable(String query, int timeout) throws IOException {
        Fusiontables fusiontables = prepareForApiCall(timeout);
        if(fusiontables == null) {
            return null;
        }

        // Fetch the table list
        Fusiontables.Query.SqlGet sqlGet = fusiontables.query().sqlGet(query);
        Sqlresponse sqlResponse = sqlGet.execute();

        return sqlResponse;
    }

    public static InputStream queryTableForCsv(String query) throws IOException {
        Credential credential = authorize();
        if(credential == null) {
            return null;
        }

        URL url = new URL("https://www.googleapis.com/fusiontables/v1/query?sql="+ URLEncoder.encode(query, "UTF-8")+"&alt=csv");

        URLConnection urlConnection = url.openConnection();
        urlConnection.setRequestProperty("user-agent", "MooreaBiocodePlugin/"+ BiocodePlugin.PLUGIN_VERSION);
        urlConnection.setRequestProperty("authorization", "Bearer "+credential.getAccessToken());

        return urlConnection.getInputStream();
    }


}
