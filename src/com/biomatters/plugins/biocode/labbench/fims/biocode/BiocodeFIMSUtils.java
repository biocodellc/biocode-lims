package com.biomatters.plugins.biocode.labbench.fims.biocode;

import com.biomatters.geneious.publicapi.databaseservice.DatabaseServiceException;

import javax.ws.rs.NotFoundException;
import javax.ws.rs.ProcessingException;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Matthew Cheung
 *         Created on 7/02/14 5:51 AM
 */
public class BiocodeFIMSUtils {

    static WebTarget getWebTarget(String expedition, String graph) {
        WebTarget target = ClientBuilder.newClient().target("http://biscicol.org");
        target = target.path("biocode-fims/query/json").
                queryParam("expedition_id", expedition);
        if(graph != null) {
            target = target.queryParam("graphs", graph);
        }
        return target;
    }

    static List<Graph> getGraphsForExpedition(String id) throws DatabaseServiceException {
        try {
            WebTarget target = ClientBuilder.newClient().target("http://biscicol.org");
            Invocation.Builder request = target.path("id/expeditionService/graphs").path(id).request(MediaType.APPLICATION_JSON_TYPE);
            GraphList graphs = request.get(GraphList.class);
            return graphs.data;
        } catch(WebApplicationException e) {
            throw new DatabaseServiceException(e, "Problem contacting biscicol.org: " + e.getMessage(), true);
        } catch(ProcessingException e) {
            throw new DatabaseServiceException(e, e.getMessage(), true);  // todo
        }
    }

    static final String PROJECT_NAME = "Project";

    static BiocodeFimsData getData(String expedition, Graph graph, String filter) throws DatabaseServiceException {
        if(filter != null && filter.contains(",")) {
            try {
                filter = URLEncoder.encode(filter, "UTF-8");
            } catch(UnsupportedEncodingException e) {
                // todo
                e.printStackTrace();
            }
        }

        List<Graph> graphsToSearch = new ArrayList<Graph>();
        if(graph != null) {
            graphsToSearch.add(graph);
        } else {
            for (Graph g : getGraphsForExpedition(expedition)) {
                graphsToSearch.add(g);
            }
        }

        BiocodeFimsData data = new BiocodeFimsData();
        for (Graph g : graphsToSearch) {
            BiocodeFimsData toAdd = getBiocodeFimsData(expedition, g.getGraphId(), filter);
            if(data.header == null || data.header.isEmpty()) {
                data.header = toAdd.header;
                data.header.add(0, PROJECT_NAME);
                data.data = new ArrayList<Row>();
            }
            for (Row row : toAdd.data) {
                row.rowItems.add(0,g.getProjectTitle());
                data.data.add(row);
            }
        }

        return data;
    }

    private static BiocodeFimsData getBiocodeFimsData(String expedition, String graph, String filter) throws DatabaseServiceException {
        try {
            WebTarget target = getWebTarget(expedition, graph);
            if(filter != null) {
                target = target.queryParam("filter", filter);
            }
            System.out.println(target.getUri());
            Invocation.Builder request = target.
                    request(MediaType.APPLICATION_JSON_TYPE);
            return request.get(BiocodeFimsData.class);
        } catch (NotFoundException e) {
            throw new DatabaseServiceException("No data found.", false);
        } catch (WebApplicationException e) {
            throw new DatabaseServiceException(e, "Encountered an error communicating with " + BiocodeFIMSConnection.HOST, false);
        }
    }
}
