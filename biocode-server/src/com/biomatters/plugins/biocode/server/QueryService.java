package com.biomatters.plugins.biocode.server;


import com.biomatters.geneious.publicapi.databaseservice.DatabaseServiceException;
import com.biomatters.geneious.publicapi.databaseservice.Query;
import com.biomatters.geneious.publicapi.databaseservice.QueryFactoryImplementation;
import com.biomatters.geneious.publicapi.documents.AnnotatedPluginDocument;
import com.biomatters.plugins.biocode.labbench.BiocodeService;
import jebl.util.ProgressListener;

import javax.ws.rs.*;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlValue;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Main endpoint for querying the FIMS/LIMS as a whole.  Returns tissues, workflows, plates and sequences
 *
 * @author Matthew Cheung
 * @version $Id$
 *          <p/>
 *          Created on 23/03/14 5:21 PM
 */
// todo Singleton?
@Path("search")
public class QueryService {

    @PUT
    @Produces("application/json")
    public String search(@QueryParam("q")String queryString,
                      @DefaultValue("tissues,workflows,plates")@QueryParam("include")String include) {

        Query query = createQueryFromQueryString(queryString);

        try {
            // JSON Callback
            BiocodeService service = BiocodeService.getInstance();
            List<AnnotatedPluginDocument> docs = service.retrieve(query, ProgressListener.EMPTY);
            StringBuilder result = new StringBuilder();
            for (AnnotatedPluginDocument doc : docs) {
                result.append("Doc[").append(doc.getDocumentClass().getSimpleName()).append("]: ").append(doc.getName()).append("\n");
            }

            return result.toString();
        } catch (DatabaseServiceException e) {
            throw new InternalServerErrorException("Encountered error: " + e.getMessage());
        }
    }

    @GET
    @Path("{id}")
    @Produces("application/xml")  // todo Stick with XML?  Or JSON
    public String getResults(@PathParam("id")String id) {
        return id;
    }

    private Query createQueryFromQueryString(String queryString) {
        return Query.Factory.createQuery(queryString);
    }


}
