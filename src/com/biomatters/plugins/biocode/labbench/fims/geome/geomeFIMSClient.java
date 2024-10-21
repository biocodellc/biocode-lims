package com.biomatters.plugins.biocode.labbench.fims.geome;

import com.fasterxml.jackson.databind.DeserializationFeature;
import okhttp3.OkHttpClient;

import javax.net.ssl.*;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import com.biomatters.geneious.publicapi.databaseservice.DatabaseServiceException;
import com.biomatters.plugins.biocode.labbench.fims.biocode.ErrorResponse;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.biomatters.plugins.biocode.labbench.fims.biocode.BiocodeFimsData;
import com.biomatters.plugins.biocode.labbench.fims.biocode.QueryResult;
import okhttp3.*;
import org.apache.http.conn.ssl.TrustStrategy;
import org.apache.http.ssl.SSLContexts;
//import org.apache.http.conn.ssl.TrustStrategy;
//import org.apache.http.ssl.SSLContexts;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
//import org.codehaus.jackson.JsonNode;
//import org.codehaus.jackson.map.ObjectMapper;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Refactored geomeFIMSClient using OkHttpClient instead of Jersey.
 */
public class geomeFIMSClient {

    public OkHttpClient client;
    public AccessToken access_token;
    private String hostname;
    private static final ObjectMapper objectMapper = new ObjectMapper(); // Initialize the ObjectMapper

    static {
        // Configure the ObjectMapper to ignore unknown properties
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

       /*
    public geomeFIMSClient(String hostname) {
        this(hostname, 30);
    }
          */


        public geomeFIMSClient(String hostname2, int timeout) {
            this.hostname = hostname2;

            try {
            // Create a TrustManager that accepts all certificates
            final TrustManager[] trustAllCerts = new TrustManager[]{
                new X509TrustManager() {
                    @Override
                    public void checkClientTrusted(X509Certificate[] chain, String authType) {
                        System.out.println("Client trusted: " + authType);
                    }

                    @Override
                    public void checkServerTrusted(X509Certificate[] chain, String authType) {
                        System.out.println("Server trusted: " + authType);
                    }

                    @Override
                    public X509Certificate[] getAcceptedIssuers() {
                        System.out.println("Returning non-null accepted issuers.");
                        return new X509Certificate[0]; // Ensure a non-null array is returned
                    }
                }
            };

            // Set up the SSL context with the above TrustManager
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustAllCerts, new java.security.SecureRandom());

            // Create the SSLSocketFactory using the TrustManager
            SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();

            System.out.println("SSL Socket Factory initialized: " + sslSocketFactory);

            // Build the OkHttpClient with custom SSL configuration and hostname verifier
            client = new OkHttpClient.Builder()
                .sslSocketFactory(sslSocketFactory, (X509TrustManager) trustAllCerts[0])
                .hostnameVerifier((hostname, session) -> {
                    System.out.println("Hostname verified: " + hostname);
                    return true;  // Trust all hostnames
                })
                .connectTimeout(timeout, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(timeout, java.util.concurrent.TimeUnit.SECONDS)
                    .writeTimeout(timeout, java.util.concurrent.TimeUnit.SECONDS)

                .build();

            System.out.println("OkHttpClient initialized successfully.");

        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
                }

        

    HttpUrl getQueryTarget() {
        // Construct the base URL using the hostname and add paths to it
        return HttpUrl.parse(hostname) // Assuming hostname is a base URL
                .newBuilder()
                .build();
    }

    /**
         * Retrieves the result of a REST method call and maps it to a specific class.
         *
         * @param resultType The class of the entity that should be returned from the method
         * @param response   The response from the method call
         * @return The result entity
         * @throws DatabaseServiceException If the server returned an error or if a processing error occurs
         */
        public static <T> T getRestServiceResult(Class<T> resultType, Response response) throws DatabaseServiceException {
            try {
                // RESPONSE BODY CAN ONLY BE READ ONCE... DO WE HAVE MULTIPLE CALLS?
                int statusCode = response.code();
                if (statusCode != 200) {
                    // Deserialize the error response and throw a RuntimeException
                    String responseBody = response.body().string();
                    ErrorResponse errorResponse = objectMapper.readValue(responseBody, ErrorResponse.class);
                    throw new IllegalStateException("Server returned an error: " + errorResponse.usrMessage + " (HTTP " + statusCode + ")");
                } else {
                    // Deserialize the response body into the expected type
                    //return response.readEntity(resultType);
                    String responseBody = response.body().string();
                           return objectMapper.readValue(responseBody, resultType);

                   // String responseBody = response.body().string();
                   /* JsonNode rootNode = objectMapper.readTree(responseBody);

                      // Create a new ObjectNode to store only conceptAlias and attributes
                      ObjectNode filteredNode = objectMapper.createObjectNode();

                      
                      // Check and retain "conceptAlias"
                      if (rootNode.has("conceptAlias")) {
                          filteredNode.set("conceptAlias", rootNode.get("conceptAlias"));
                      }

                      // Check and retain "attributes"
                      if (rootNode.has("attributes")) {
                          filteredNode.set("attributes", rootNode.get("attributes"));
                      }

                    //return objectMapper.readValue(filteredNode, resultType);
                    return objectMapper.treeToValue(filteredNode, resultType);
                   */
                }
            } catch (IOException e) {
                throw new DatabaseServiceException(e, "Failed to process the response", false);
            } finally {
                response.close();
            }
        }

        /**
         * Retrieves the result of a REST method call and maps it to a generic type (like a List).
         *
         * @param resultType The type reference for the generic type
         * @param response   The response from the method call
         * @return The result entity
         * @throws DatabaseServiceException If the server returned an error or if a processing error occurs
         */
        public <T> T getRestServiceResult(TypeReference<T> resultType, Response response) throws DatabaseServiceException {
            try {
                int statusCode = response.code();
                if (statusCode != 200) {
                    // Deserialize the error response and throw a RuntimeException
                    String responseBody = response.body().string();
                    ErrorResponse errorResponse = objectMapper.readValue(responseBody, ErrorResponse.class);
                    throw new IllegalStateException("Server returned an error: " + errorResponse.usrMessage + " (HTTP " + statusCode + ")");
                } else {
                    // Deserialize the response body into the expected generic type
                    String responseBody = response.body().string();
                    return objectMapper.readValue(responseBody, resultType);
                }
            } catch (IOException e) {
                throw new DatabaseServiceException(e, "Failed to process the response",  false);
            } finally {
                response.close();
            }
        }


    private SSLContext getSslContext() {
        try {
            TrustStrategy acceptingTrustStrategy = (cert, authType) -> true;
            return SSLContexts.custom().loadTrustMaterial(null, acceptingTrustStrategy).build();
        } catch (NoSuchAlgorithmException | KeyManagementException | KeyStoreException e) {
            throw new RuntimeException("Failed to create SSL context", e);
        }
    }

    /**
     * Login method using OkHttpClient to authenticate and retrieve access token.
     */
    void login(String username, String password) throws IOException {
        JsonNode secrets = getClientSecrets();
        String clientId = secrets.get("client_id").asText();
        String clientSecret = secrets.get("client_secret").asText();

        // Build the form data for POST request
        RequestBody formBody = new FormBody.Builder()
                .add("username", username)
                .add("password", password)
                .add("client_id", clientId)
                .add("client_secret", clientSecret)
                .add("grant_type", "password")
                .build();

        // Build the request
        Request request = new Request.Builder()
                .url(hostname + "/v1/oauth/accessToken")
                .post(formBody)
                .build();

        // Execute the request
        try (Response response = client.newCall(request).execute()) {
            if (response.isSuccessful()) {
                String responseBody = response.body().string();
                access_token = new ObjectMapper().readValue(responseBody, AccessToken.class);

                // Log the successful authentication
                System.out.println("Logged in successfully with token: " + access_token.getAccess_token());
            } else {
                throw new IOException("Failed to login: " + response.code());
            }
        }
    }

    private JsonNode getClientSecrets() throws IOException {
        InputStream secretsStream = geomeFIMSClient.class.getResourceAsStream("/geome_secrets.json");
        if (secretsStream == null) {
            throw new IllegalStateException("Can't find client secrets for connection to geome");
        }

        JsonNode node = new ObjectMapper().readTree(secretsStream);
        if (hostname.contains("develop"))
            return node.get("develop");
        else
            return node.get("production");
    }

    /**
     * Retrieve a list of projects using OkHttpClient.
     */
    List<Project> getProjects(boolean includePublic) throws IOException {
        // Build the request URL
        HttpUrl.Builder urlBuilder = HttpUrl.parse(hostname + "/projects")
                .newBuilder()
                .addQueryParameter("includePublic", String.valueOf(includePublic));

        // Build the request
        Request request = new Request.Builder()
                .url(urlBuilder.build())
                .get()
                .build();

        // Execute the request
        try {
            Response response = client.newCall(request).execute();
            if (response.isSuccessful()) {
                // Parse the response body into a list of projects
                String responseBody = response.body().string();

                // Create an ObjectMapper
                ObjectMapper objectMapper = new ObjectMapper();
                List<Project> projects = new ArrayList<>();

                // Parse the response body into a JsonNode
                JsonNode rootNode = objectMapper.readTree(responseBody);

                // Ensure that the root is an array
                if (rootNode.isArray()) {
                    // Iterate over each element in the array
                    for (JsonNode node : rootNode) {
                            if (node.isObject()) {
                                // Cast the node to ObjectNode to use remove and set methods
                                ObjectNode projectNode = (ObjectNode) node;

                                // Rename the fields
                                if (projectNode.has("projectId")) {
                                    JsonNode idNode = projectNode.remove("projectId"); // Remove old key
                                    projectNode.set("id", idNode); // Set new key with the same value
                                }

                                if (projectNode.has("projectCode")) {
                                    JsonNode codeNode = projectNode.remove("projectCode");
                                    projectNode.set("code", codeNode);
                                }

                                if (projectNode.has("projectTitle")) {
                                    JsonNode titleNode = projectNode.remove("projectTitle");
                                    projectNode.set("title", titleNode);
                                }

                                if (projectNode.has("projectConfiguration")) {
                                    ObjectNode configNode = (ObjectNode)projectNode.remove("projectConfiguration");
                                    configNode.retain("id", "description", "name");
                                    projectNode.set("configuration", configNode);
                                }
                                
                                projectNode.retain("id", "title", "configuration", "code");

                                // Deserialize each project and add it to the list
                                Project project = objectMapper.treeToValue(projectNode, Project.class);
                                projects.add(project);
                            }
                        }
                } else {
                    throw new IOException("Expected an array of projects, but received something else.");
                }

                return projects; // Return the list of projects
            } else {
                throw new IOException("Failed to get projects: " + response.code());
            }
        } catch (Exception e) {
            throw new IOException("Some failure getting projects: " + e);
        }
    }

    /**
     * Retrieve data for a specific project using OkHttpClient.
     */
    BiocodeFimsData getData(String project, Map<String, String> searchTerms, String filter) throws IOException {
        if (filter != null && filter.contains(",")) {
            filter = URLEncoder.encode(filter, "UTF-8");
        }

        // Build the request URL with query parameters
        HttpUrl.Builder urlBuilder = HttpUrl.parse(hostname + "/data")
                .newBuilder()
                .addQueryParameter("projectId", project);
        if (filter != null) {
            urlBuilder.addQueryParameter("filter", filter);
        }

        // Build the request
        Request request = new Request.Builder()
                .url(urlBuilder.build())
                .get()
                .build();

        // Execute the request
        try (Response response = client.newCall(request).execute()) {
            if (response.isSuccessful()) {
                // Parse the response body into BiocodeFimsData
                String responseBody = response.body().string();
                return new ObjectMapper().readValue(responseBody, BiocodeFimsData.class);
            } else {
                throw new IOException("Failed to get data: " + response.code());
            }
        }
    }

    /**
     * Retrieve a list of graphs for a specific project using OkHttpClient.
     * @return
     */
    List<Project> getGraphsForProject(String projectId) throws IOException {
        // Build the request URL
        HttpUrl.Builder urlBuilder = HttpUrl.parse(hostname + "/biocode-fims/rest/v1.1/projects/" + projectId + "/graphs")
                .newBuilder();

        // Build the request
        Request request = new Request.Builder()
                .url(urlBuilder.build())
                .get()
                .build();

        // Execute the request
        try (Response response = client.newCall(request).execute()) {
            if (response.isSuccessful()) {
                // Parse the response body into a list of Graph objects
                String responseBody = response.body().string();

                return new ObjectMapper().readValue(responseBody, new TypeReference<List<Project>>() {});
            } else {
                throw new IOException("Failed to get graphs: " + response.code());
            }
        }
    }

    /**
     * Retrieve a paginated result set from a target URL.
     */
    private List<QueryResult> retrieveResult(HttpUrl.Builder urlBuilder) throws IOException {
        List<QueryResult> results = new ArrayList<>();
        int page = 0;

        while (true) {
            // Add pagination query parameters
            HttpUrl url = urlBuilder.addQueryParameter("limit", "100")
                                    .addQueryParameter("page", String.valueOf(page++))
                                    .build();

            // Build the request
            Request request = new Request.Builder()
                    .url(url)
                    .get()
                    .build();

            // Execute the request
            try (Response response = client.newCall(request).execute()) {
                if (response.isSuccessful()) {
                    // Parse the response body into a QueryResult object
                    String responseBody = response.body().string();
                    QueryResult result = new ObjectMapper().readValue(responseBody, QueryResult.class);

                    if (result.getContent().isEmpty()) {
                        break; // Exit if no more content is available
                    }

                    results.add(result);
                } else {
                    throw new IOException("Failed to retrieve results: " + response.code());
                }
            }
        }

        return results;
    }

    // Additional methods can be added here using similar patterns.
}

