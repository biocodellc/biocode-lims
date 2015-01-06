package com.biomatters.plugins.biocode.labbench.fims.biocode;

import com.biomatters.geneious.publicapi.databaseservice.DatabaseServiceException;
import com.biomatters.geneious.publicapi.utilities.StringUtilities;
import com.biomatters.plugins.biocode.BiocodePlugin;
import com.biomatters.plugins.biocode.labbench.ConnectionException;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

/**
 * @author Matthew Cheung
 *         Created on 7/02/14 5:51 AM
 */
public class BiocodeFIMSUtils {

    static final String HOST = "biscicol.org";
    static final int PORT = 80;
    static final String BISCICOL_URL = "http://" + HOST + ":" + PORT;

    static WebTarget getFimsWebTarget(String hostname) {
        return ClientBuilder.newClient().target(hostname)
                            .register(new LoggingFilter(Logger.getLogger(BiocodePlugin.class.getName()), false));
    }


    /**
     *
     * @param url The URL of the BiSciCol server
     * @param username The username to use to authenticate
     * @param password The password to use to authenticate
     * @throws MalformedURLException If the url specified was not a valid URL
     * @throws ProcessingException If a problem occurs accessing the webservice to login
     * @throws ConnectionException If the server returned an error when we tried to authenticate
     */
    static void login(String url, String username, String password) throws MalformedURLException, ProcessingException, ConnectionException {
        WebTarget path = getFimsWebTarget(url).path("id/authenticationService/login");
        Invocation.Builder request = path.request();
        Form formToPost = new Form().param("username", username).param("password", password);
        Response response = request.post(
                    Entity.entity(formToPost, MediaType.TEXT_PLAIN_TYPE));
        // Ignore the actual result.  It will be a redirection URL which is irrelevant for us
        getRestServiceResult(String.class, response);
    }

    /**
     * Retrieves the result of a REST method call to either the BiSciCol or Biocode-FIMS services.
     *
     * @param resultType The class of the entity that should be returned from the method
     * @param response The response from the method call
     * @return The result entity
     * @throws WebApplicationException if an error was returned by the server
     */
    static <T> T getRestServiceResult(Class<T> resultType, Response response) throws WebApplicationException {
        try {
            int statusCode = response.getStatus();
            if (statusCode != 200) {
                ErrorResponse errorResponse = response.readEntity(ErrorResponse.class);
                throw new WebApplicationException(errorResponse.usrMessage, errorResponse.httpStatusCode);
            } else {
                return response.readEntity(resultType);
            }
        } finally {
            response.close();
        }
    }

    static WebTarget getQueryTarget() {
        WebTarget target = getFimsWebTarget(BISCICOL_URL);
        target = target.path("biocode-fims/rest/query/json");
        return target;
    }

    static List<Project> getProjects() throws DatabaseServiceException {
        WebTarget target = getFimsWebTarget(BISCICOL_URL);
        Invocation.Builder request = target.path("id/projectService/listUserProjects").request(MediaType.APPLICATION_JSON_TYPE);
        try {
            Response response = request.get();
            ProjectList fromService = getRestServiceResult(ProjectList.class, response);
            List<Project> returnList = new ArrayList<Project>();
            for (Project project : fromService.getProjects()) {
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

    static List<Graph> getGraphsForProject(String id) throws DatabaseServiceException {
        try {
            WebTarget target = getFimsWebTarget(BISCICOL_URL);
            Invocation.Builder request = target.path("id/projectService/graphs").path(id).request(MediaType.APPLICATION_JSON_TYPE);
            Response response = request.get();
            return getRestServiceResult(GraphList.class, response).getData();
        } catch(WebApplicationException e) {
            throw new DatabaseServiceException(e, "Error message from server: " + HOST + ": " + e.getMessage(), true);
        } catch(ProcessingException e) {
            throw new DatabaseServiceException(e, e.getMessage(), true);
        }
    }

    static final String EXPEDITION_NAME = "Expedition";

    static BiocodeFimsData getData(String project, Graph graph, Form searchTerms, String filter) throws DatabaseServiceException {
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
            for (Graph g : getGraphsForProject(project)) {
                graphsToSearch.add(g.getGraphId());
            }
        }

        BiocodeFimsData data = getBiocodeFimsData(project, graphsToSearch, searchTerms, filter);
        if(data.header == null)
            data.header = Collections.emptyList();

        if (data.data == null)
            data.data = Collections.emptyList();

        return data;
    }

    private static BiocodeFimsData getBiocodeFimsData(String project, List<String> graphs, Form searchTerms, String filter) throws DatabaseServiceException {
        try {
            WebTarget target = getQueryTarget();

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