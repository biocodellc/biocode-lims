package com.biomatters.plugins.biocode.labbench.fims.biocode;

import com.biomatters.geneious.publicapi.databaseservice.DatabaseServiceException;
import com.biomatters.geneious.publicapi.utilities.StringUtilities;
import com.biomatters.plugins.biocode.labbench.BiocodeService;

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
import java.net.MalformedURLException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author Matthew Cheung
 *         Created on 7/02/14 5:51 AM
 */
public class BiocodeFIMSUtils {
    static void login(String hostname, String username, String password) throws MalformedURLException, ProcessingException {
            WebTarget path = ClientBuilder.newClient().target(hostname).path("id/authenticationService/login");
            Invocation.Builder request = path
                    .request(MediaType.TEXT_HTML_TYPE);
            Form formToPost = new Form().param("username", username).param("password", password);
            Response response = request.post(
                        Entity.entity(formToPost, MediaType.TEXT_PLAIN_TYPE));
            response.close();  // Unfortunately the login service doesn't provide any meaningful response.  It just redirects to the main page.
    }

    static WebTarget getQueryTarget() {
        WebTarget target = ClientBuilder.newClient().target("http://biscicol.org");
        target = target.path("biocode-fims/rest/query/json");
        return target;
    }

    public static void main(String[] args) throws DatabaseServiceException {
        List<Project> expeditions = getProjects();
        for (Project expedition : expeditions) {
            System.out.println(expedition.title);
        }
    }
    static List<Project> getProjects() throws DatabaseServiceException {
        WebTarget target = ClientBuilder.newClient().target("http://biscicol.org");
        Invocation.Builder request = target.path("id/projectService/listUserProjects").request(MediaType.APPLICATION_JSON_TYPE);
        try {
            ProjectList fromService = request.get(ProjectList.class);
            List<Project> returnList = new ArrayList<Project>();
            for (Project project : fromService.getProjects()) {
                if(project.code != null) {
                    returnList.add(project);
                }
            }
            return returnList;
        } catch(WebApplicationException e) {
            throw new DatabaseServiceException(e, "Problem contacting biscicol.org: " + e.getMessage(), true);
        } catch(ProcessingException e) {
            // Unfortunately the BCID service doesn't use HTTP error codes and reports errors by returning JSON in a
            // different format than the regular result.  So we have to do some special parsing.
            List<String> errors;
            try {
                errors = request.get(new GenericType<List<String>>() { });
            } catch (ProcessingException ex) {
                throw new DatabaseServiceException(ex.getMessage(), false);
            } catch (WebApplicationException ex) {
                throw new DatabaseServiceException(ex.getMessage(), false);
            }
            if(errors != null && !errors.isEmpty()) {
                if(errors.size() == 1) {
                    throw new DatabaseServiceException(errors.get(0), false);
                } else {
                    throw new DatabaseServiceException("Service returned: " + StringUtilities.join("\n", errors), false);
                }
            }
            throw new DatabaseServiceException(e, e.getMessage(), true);
        }
    }

    static List<Graph> getGraphsForProject(String id) throws DatabaseServiceException {
        try {
            WebTarget target = ClientBuilder.newClient().target("http://biscicol.org");
            Invocation.Builder request = target.path("id/projectService/graphs").path(id).request(MediaType.APPLICATION_JSON_TYPE);
            return request.get(GraphList.class).getData();
        } catch(WebApplicationException e) {
            throw new DatabaseServiceException(e, "Problem contacting biscicol.org: " + e.getMessage(), true);
        } catch(ProcessingException e) {
            throw new DatabaseServiceException(e, e.getMessage(), true);  // todo
        }
    }

    static final String EXPEDITION_NAME = "Expedition";

    static BiocodeFimsData getData(String project, Graph graph, Form searchTerms, String filter) throws DatabaseServiceException {
        if(filter != null && filter.contains(",")) {
            try {
                filter = URLEncoder.encode(filter, "UTF-8");
            } catch(UnsupportedEncodingException e) {
                // todo
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
        if (BiocodeService.getInstance().isQueryCancled())
            return new BiocodeFimsData();

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
//                System.out.println(response.readEntity(String.class));
                return response.readEntity(BiocodeFimsData.class);
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