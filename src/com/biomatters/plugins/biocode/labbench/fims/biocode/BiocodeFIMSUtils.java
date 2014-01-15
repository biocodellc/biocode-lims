package com.biomatters.plugins.biocode.labbench.fims.biocode;

import com.biomatters.geneious.publicapi.databaseservice.DatabaseServiceException;

import javax.ws.rs.NotFoundException;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
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

    static List<Graph> getGraphsForExpedition(String id) {
        WebTarget target = ClientBuilder.newClient().target("http://biscicol.org");
        Invocation.Builder request = target.path("id/expeditionService/graphs").path(id).request(MediaType.APPLICATION_JSON_TYPE);
        GraphList graphs = request.get(GraphList.class);
        return graphs.data;
    }

    static final String PROJECT_COL = "Project";

    static BiocodeFimsData getData(String expedition, String graphId, String filter) throws DatabaseServiceException {
        List<String> graphsToSearch = new ArrayList<String>();
        if(graphId != null) {
            graphsToSearch.add(graphId);
        } else {
            for (Graph graph : getGraphsForExpedition(expedition)) {
                graphsToSearch.add(graph.getGraphId());
            }
        }

        BiocodeFimsData data = new BiocodeFimsData();
        for (String graph : graphsToSearch) {
            BiocodeFimsData toAdd = getBiocodeFimsData(expedition, graph, filter);
            if(data.header == null || data.header.isEmpty()) {
                data.header = toAdd.header;
                data.header.add(0, PROJECT_COL);
                data.data = new ArrayList<Row>();
            }
            for (Row row : toAdd.data) {
                row.rowItems.add(graph);
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
