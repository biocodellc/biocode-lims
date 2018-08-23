package com.biomatters.plugins.biocode.labbench.fims.geome;

import com.biomatters.geneious.publicapi.databaseservice.DatabaseServiceException;
import com.biomatters.geneious.publicapi.utilities.StringUtilities;
import com.biomatters.plugins.biocode.BiocodePlugin;
import com.biomatters.plugins.biocode.labbench.fims.biocode.*;
import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.client.ClientProperties;
import org.glassfish.jersey.filter.LoggingFilter;
import org.glassfish.jersey.jackson.JacksonFeature;

import javax.ws.rs.NotFoundException;
import javax.ws.rs.ProcessingException;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Form;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.InvocationTargetException;
import java.net.MalformedURLException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * @author Matthew Cheung
 *         Created on 7/02/14 5:51 AM
 */
public class geomeFIMSClient {
    private WebTarget target;
    public  String access_token;

    public geomeFIMSClient(String hostname) {
        this(hostname, 0);
    }

    public geomeFIMSClient(String hostname, int timeout) {
        ClientConfig config = new ClientConfig()
                .property(ClientProperties.CONNECT_TIMEOUT, timeout * 1000)
                .property(ClientProperties.READ_TIMEOUT, timeout * 1000);

        target = ClientBuilder.newBuilder().withConfig(config).build().target(hostname)
                                    .register(JacksonFeature.class)
                                    .register(new LoggingFilter(Logger.getLogger(BiocodePlugin.class.getName()), false));
    }


    /**
     *
     * @param username The username to use to authenticate
     * @param password The password to use to authenticate
     * @throws MalformedURLException If the url specified was not a valid URL
     * @throws ProcessingException If a problem occurs accessing the webservice to login
     * @throws DatabaseServiceException If the server returned an error when we tried to authenticate
     */
    void login(String username, String password) throws MalformedURLException, ProcessingException, DatabaseServiceException {
        // TODO: fetch these from properties file, not sure we want to code these into application??
        String client_id = "";
        String client_secret = "";

        //WebTarget path = target.path("biocode-fims/rest/authenticationService/login");

        WebTarget path = target.path("oath/accessToken");
        Invocation.Builder request = path.request();
        Form formToPost = new Form()
                .param("username", username)
                .param("password", password)
                .param("client_id", client_id)
                .param("client_secret", client_secret);
        Response response = request.post(
                Entity.entity(formToPost, MediaType.APPLICATION_FORM_URLENCODED_TYPE));
        
        String result = getRestServiceResult(String.class, response);
        // Not sure what the result looks like, should have access_token
        System.out.println(result);
        // TODO: we need to grab the access token from the result
         access_token = "set access token here";
    }

    /**
     * Retrieves the result of a REST method call to either the BiSciCol or Biocode-FIMS services.
     *
     * @param resultType The class of the entity that should be returned from the method
     * @param response The response from the method call
     * @return The result entity
     * @throws DatabaseServiceException if an error was returned by the server
     */
    static <T> T getRestServiceResult(Class<T> resultType, Response response) throws DatabaseServiceException {
        try {
            int statusCode = response.getStatus();
            if (statusCode != 200) {
                ErrorResponse errorResponse = response.readEntity(ErrorResponse.class);
                throw new DatabaseServiceException(
                        new WebApplicationException(errorResponse.developerMessage, errorResponse.httpStatusCode),
                        "Server returned an error: " + errorResponse.usrMessage, false);
            } else {
                return response.readEntity(resultType);
            }
        } finally {
            response.close();
        }
    }

    static <T> T getRestServiceResult(GenericType<T> resultType, Response response) throws DatabaseServiceException {
        try {
            int statusCode = response.getStatus();
            if (statusCode != 200) {
                ErrorResponse errorResponse = response.readEntity(ErrorResponse.class);
                throw new DatabaseServiceException(
                        new WebApplicationException(errorResponse.developerMessage, errorResponse.httpStatusCode),
                        "Server returned an error: " + errorResponse.usrMessage, false);
            } else {
                return response.readEntity(resultType);
            }
        } finally {
            response.close();
        }
    }

    WebTarget getQueryTarget() {
        return target.path("v1/records/Tissue/json");
    }

    List<Project> getProjects() throws DatabaseServiceException {
        Invocation.Builder request = target.path("v1/projects").request(MediaType.APPLICATION_JSON_TYPE);
        try {
            Response response = request.get();
            List<Project> fromService = getRestServiceResult(new GenericType<List<Project>>() {
            }, response);
            List<Project> returnList = new ArrayList<Project>();
            for (Project project : fromService) {
                if (project.code != null) {
                    returnList.add(project);
                }
            }
            return returnList;
        } catch (WebApplicationException e) {
            throw new DatabaseServiceException(e, "Error message from server: " + target.getUri() + ": " + e.getMessage(), true);
        } catch (ProcessingException e) {
            throw new DatabaseServiceException(e, e.getMessage(), true);

        }
    }

    List<Graph> getGraphsForProject(String id) throws DatabaseServiceException {
        try {
            Invocation.Builder request = target.path("biocode-fims/rest/v1.1/projects").path(id).path("graphs").request(MediaType.APPLICATION_JSON_TYPE);
            Response response = request.get();
            return getRestServiceResult(new GenericType<List<Graph>>(){}, response);
        } catch(WebApplicationException e) {
            throw new DatabaseServiceException(e, "Error message from server: " + target.getUri().getHost() + ": " + e.getMessage(), true);
        } catch(ProcessingException e) {
            throw new DatabaseServiceException(e, e.getMessage(), true);
        }
    }


    BiocodeFimsData getData(String project, Form searchTerms, String filter) throws DatabaseServiceException {
        if(filter != null && filter.contains(",")) {
            try {
                filter = URLEncoder.encode(filter, "UTF-8");
            } catch(UnsupportedEncodingException e) {
                // Go with default encoding
                e.printStackTrace();
            }
        }

        return getBiocodeFimsData(project, searchTerms, filter);
    }

    private BiocodeFimsData getBiocodeFimsData(String project, Form searchTerms, String filter) throws DatabaseServiceException {
        try {
            WebTarget target = getQueryTarget();

            Entity<Form> entity = null;
            if(searchTerms == null || searchTerms.asMap().isEmpty()) {
                target = target.queryParam("projectId", project);
                if(filter != null) {
                    target = target.queryParam("filter", filter);
                }
            } else {
                Form form = new Form(searchTerms.asMap());
                form.param("projectId", project);
                entity = Entity.entity(form, MediaType.APPLICATION_FORM_URLENCODED_TYPE);
            }

            return QueryResultToData(retrieveResult(target, entity));
        } catch (NotFoundException e) {
            throw new DatabaseServiceException("No data found.", false);
        } catch (WebApplicationException e) {
            throw new DatabaseServiceException(e, "Encountered an error communicating with " + geomeFIMSConnection.GEOME_URL, false);
        } catch(ProcessingException e) {
            throw new DatabaseServiceException(e, "Encountered an error connecting to server: " + e.getMessage(), true);
        } catch (NoSuchMethodException e) {
            throw new DatabaseServiceException(e, "Failed to deserialize response. " + e.getMessage(), true);
        } catch (InvocationTargetException e) {
            throw new DatabaseServiceException(e, "Failed to deserialize response. " + e.getMessage(), true);
        } catch (IllegalAccessException e) {
            throw new DatabaseServiceException(e, "Failed to deserialize response. " + e.getMessage(), true);
        }
    }

    private List<QueryResult> retrieveResult(WebTarget target, Entity entity) throws DatabaseServiceException {
        List<QueryResult> res = new ArrayList<QueryResult>();
        target = target.queryParam("limit", "100");
        int page = 0;
        QueryResult result;
        while (true) {
            WebTarget target1 = target.queryParam("page", "" + page++);
            Invocation.Builder request = target1.request(MediaType.APPLICATION_JSON_TYPE);
            if (entity == null)
                result = request.get(QueryResult.class);
            else
                result = getRestServiceResult(QueryResult.class, request.post(entity));

            if (result.getContent().size() == 0)
                break;
            res.add(result);
        }

        return res;
    }

    private BiocodeFimsData QueryResultToData(List<QueryResult> results) throws IllegalAccessException, NoSuchMethodException, InvocationTargetException {
        BiocodeFimsData res = new BiocodeFimsData();

        if(results.isEmpty()) {
            return res;
        }

        //header
        List<Map<String, String>> rows = results.get(0).getContent();
        if(rows.isEmpty() || rows.get(0) == null) {
            return res;
        }
        res.header = new ArrayList<String>(rows.get(0).keySet());

        //data
        /*
        for (QueryResult result : results) {
            for (Map<String, String> rowValues : result.getContent()) {
                //Row row = new Row();
                //row.row Items = new ArrayList<String>(rowValues.values());
                //res.data.add(row);
            }
        }   */

        return res;
    }
}