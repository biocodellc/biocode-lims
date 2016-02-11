package com.biomatters.plugins.biocode.labbench.fims.biocode;

import com.biomatters.geneious.publicapi.databaseservice.DatabaseServiceException;
import com.biomatters.geneious.publicapi.utilities.StringUtilities;
import com.biomatters.plugins.biocode.BiocodePlugin;
import org.glassfish.jersey.filter.LoggingFilter;

import javax.ws.rs.NotFoundException;
import javax.ws.rs.ProcessingException;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.*;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URLEncoder;
import java.util.*;
import java.util.logging.Logger;

/**
 * @author Matthew Cheung
 *         Created on 7/02/14 5:51 AM
 */
public class BiocodeFIMSUtils {

    static final String HOST = "biscicol.org";
    static final int PORT = serverProbablyDeployed() ? 80 : 8179;  // Should be reverted to 80 once the new server is deployed

    private static boolean serverProbablyDeployed() {
        GregorianCalendar cal = new GregorianCalendar();
        cal.set(Calendar.YEAR, 2016);
        cal.set(Calendar.MONTH, 2);
        cal.set(Calendar.DAY_OF_MONTH, 1);
        return new Date().after(cal.getTime());
    }

    static final String BISCICOL_URL = "http://" + HOST + ":" + PORT;

    static WebTarget getFimsWebTarget(String hostname) {
        return ClientBuilder.newClient().target(hostname)
                            .register(new LoggingFilter(Logger.getLogger(BiocodePlugin.class.getName()), true));
    }


    /**
     *
     * @param url The URL of the BiSciCol server
     * @param username The username to use to authenticate
     * @param password The password to use to authenticate
     * @throws MalformedURLException If the url specified was not a valid URL
     * @throws ProcessingException If a problem occurs accessing the webservice to login
     * @throws com.biomatters.geneious.publicapi.databaseservice.DatabaseServiceException If the server returned an error when we tried to authenticate
     */
    static void login(String url, String username, String password) throws MalformedURLException, ProcessingException, DatabaseServiceException {
        WebTarget path = getFimsWebTarget(url).path("biocode-fims/rest/authenticationService/login");
        Invocation.Builder request = path.request();
        Form formToPost = new Form().param("username", username).param("password", password);
        Response response = request.post(
                    Entity.entity(formToPost, MediaType.APPLICATION_FORM_URLENCODED_TYPE));
        // Ignore the actual result.  It will be a redirection URL which is irrelevant for us
        getRestServiceResult(String.class, response);
    }

    /**
     * Retrieves the result of a REST method call to either the BiSciCol or Biocode-FIMS services.
     *
     * @param resultType The class of the entity that should be returned from the method
     * @param response The response from the method call
     * @return The result entity
     * @throws com.biomatters.geneious.publicapi.databaseservice.DatabaseServiceException if an error was returned by the server
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

    static WebTarget getQueryTarget(String host) {
        WebTarget target = getFimsWebTarget(host);
        target = target.path("biocode-fims/rest/projects/query/json");
        return target;
    }

    static List<Project> getProjects(String host) throws DatabaseServiceException {
        WebTarget target = getFimsWebTarget(host);
        Invocation.Builder request = target.path("biocode-fims/rest/projects/user/list").request(MediaType.APPLICATION_JSON_TYPE);
        try {
            Response response = request.get();
            List<Project> fromService = getRestServiceResult(new GenericType<List<Project>>(){}, response);
            List<Project> returnList = new ArrayList<Project>();
            for (Project project : fromService) {
                if(project.code != null) {
                    returnList.add(project);
                }
            }
            return returnList;
        } catch(WebApplicationException e) {
            throw new DatabaseServiceException(e, "Error message from server: " + HOST + ": " + e.getMessage(), true);
        } catch(ProcessingException e) {
            throw new DatabaseServiceException(e, e.getMessage(), true);
        }
    }

    static List<Graph> getGraphsForProject(String host, String id) throws DatabaseServiceException {
        try {
            WebTarget target = getFimsWebTarget(host);
            Invocation.Builder request = target.path("biocode-fims/rest/projects").path(id).path("graphs").request(MediaType.APPLICATION_JSON_TYPE);
            Response response = request.get();
            return getRestServiceResult(new GenericType<List<Graph>>(){}, response);
        } catch(WebApplicationException e) {
            throw new DatabaseServiceException(e, "Error message from server: " + HOST + ": " + e.getMessage(), true);
        } catch(ProcessingException e) {
            throw new DatabaseServiceException(e, e.getMessage(), true);
        }
    }

    static final String EXPEDITION_NAME = "Expedition";

    static BiocodeFimsData getData(String host, String project, Graph graph, Form searchTerms, String filter) throws DatabaseServiceException {
        if(filter != null && filter.contains(",")) {
            try {
                filter = URLEncoder.encode(filter, "UTF-8");
            } catch(UnsupportedEncodingException e) {
                // Go with default encoding
                e.printStackTrace();
            }
        }

        List<String> graphsToSearch = new ArrayList<String>();
        if(graph != null) {
            graphsToSearch.add(graph.getGraphId());
        } else {
            for (Graph g : getGraphsForProject(host, project)) {
                graphsToSearch.add(g.getGraphId());
            }
        }

        BiocodeFimsData data = getBiocodeFimsData(host, project, graphsToSearch, searchTerms, filter);
        if(data.header == null)
            data.header = Collections.emptyList();

        if (data.data == null)
            data.data = Collections.emptyList();

        return data;
    }

    private static BiocodeFimsData getBiocodeFimsData(String host, String project, List<String> graphs, Form searchTerms, String filter) throws DatabaseServiceException {
        try {
            WebTarget target = getQueryTarget(host);

            if(searchTerms == null || searchTerms.asMap().isEmpty()) {
                target = target.queryParam("project_id", project);
                if(filter != null) {
                    target = target.queryParam("filter", filter);
                }
                if(graphs != null) {
                    target = target.queryParam("graphs", StringUtilities.join(",", graphs));
                }
                Invocation.Builder request = target.
                        request(MediaType.APPLICATION_JSON_TYPE);
                return request.get(BiocodeFimsData.class);
            } else {
                Invocation.Builder request = target.
                        request(MediaType.APPLICATION_JSON_TYPE);
                Form form = new Form(searchTerms.asMap());
                form.param("project_id", project);
                if(graphs != null) {
                    for (String graph : graphs) {
                        form.param("graphs", graph);
                    }
                }
                Response response = request.post(Entity.entity(form, MediaType.APPLICATION_FORM_URLENCODED_TYPE));
                return getRestServiceResult(BiocodeFimsData.class, response);
            }
        } catch (NotFoundException e) {
            throw new DatabaseServiceException("No data found.", false);
        } catch (WebApplicationException e) {
            throw new DatabaseServiceException(e, "Encountered an error communicating with " + BiocodeFIMSConnection.HOST, false);
        } catch(ProcessingException e) {
            throw new DatabaseServiceException(e, "Encountered an error connecting to server: " + e.getMessage(), true);
        }
    }
}