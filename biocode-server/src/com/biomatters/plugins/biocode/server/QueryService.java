package com.biomatters.plugins.biocode.server;


import com.biomatters.geneious.publicapi.databaseservice.DatabaseServiceException;
import com.biomatters.geneious.publicapi.databaseservice.Query;
import com.biomatters.geneious.publicapi.databaseservice.RetrieveCallback;
import com.biomatters.geneious.publicapi.documents.*;
import com.biomatters.plugins.biocode.labbench.BiocodeService;
import com.biomatters.plugins.biocode.labbench.lims.LimsSearchResult;

import javax.ws.rs.*;
import javax.ws.rs.core.Response;
import java.util.ArrayList;
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

    // todo Transport method?
    // We'll go with XML rather than JSON because that's what Geneious deals with.  COuld potentially go JSON, but
    // it would require conversion on server-side and client-side.  Better to work with XML by default and offer
    // JSON as alternative:

    // Two ways we can write our objects out:
    // 1. MessageBodyWriter<List<XMLSerializable>> and wrap single entries in Colletions.singletonList()
    // 2. MessageBodyWriter<XMLSerializable> and define our own XMLSerialiableList
    //
    // Pure JAXB doesn't understand JDOM or XMLSerializable and it doesn't seem like you can mix JAXB and
    // MessageBodyWriter :(  JAXB only works with defined schemas.  So even with an adapter we would have to
    // write new JAXB classes for each type....
    //
    // 3. Actually if we were to write an adapter for each class then we could produce JSON or XML on the fly
    // Query?, DocumentField, ExtractionReaction, PCRReaction, CycleSequencing, Plate, etc. (18 in src)
    // A lot of the LIMS classes we could just annotate to be JAXB compliant.  Geneious ones have to use
    // XMLAdapter.  Question: Can we use JAXB to get same XML.  If so can we convert it to a JDOM element?
    // If not then we can't keep the XML consistent.... which could be bad down the line
    // Could marshall to a stream that is read by SAXBuilder?  Is that too inefficient?
    // JAXB can marshall to a  org.jdom2.transform.JDOMResult!!!


    @GET
    @Produces("application/xml")
    public Response search(@QueryParam("q")String queryString,
                           @QueryParam("type")String type,
                      @DefaultValue("tissues,workflows,plates")@QueryParam("include")String include) {
        // todo Consider ChunkedOutput.  Woudl need a way to separate chunks when reading back in
        try {
            Map<String, Object> searchOptions = BiocodeService.getSearchDownloadOptions(
                    include == null || include.toLowerCase().contains("tissues"),
                    include == null || include.toLowerCase().contains("workflows"),
                    include == null || include.toLowerCase().contains("plates"),
                    include == null || include.toLowerCase().contains("sequences"));

            Query query = RestQueryUtils.createQueryFromQueryString(
                    type == null || type.equals(RestQueryUtils.QueryType.AND.name()) ?
                            RestQueryUtils.QueryType.AND : RestQueryUtils.QueryType.OR, queryString, searchOptions
            );
            LimsSearchResult result = LIMSInitializationServlet.getLimsConnection().getMatchingDocumentsFromLims(query, null, null, false);
            return Response.ok(result).build();
        } catch (DatabaseServiceException e) {
            throw new InternalServerErrorException("Encountered error: " + e.getMessage());
        }
    }

    @GET
    @Path("{id}")
    @Produces("application/xml")
    public String getResults(@PathParam("id")String id) {

        // Do we need this method for POST
        return id;
    }

    private class WebCallback extends RetrieveCallback {

        @Override
        protected void _add(PluginDocument document, Map<String, Object> searchResultProperties) {
            //  Write to response
        }

        @Override
        protected void _add(AnnotatedPluginDocument document, Map<String, Object> searchResultProperties) {
            // todo Do sequences HAVE to go through here because they are annotated?
            throw new UnsupportedOperationException("Callback does not support AnnotatedPluginDocuments");
        }

        @Override
        protected boolean _isCanceled() {
            // todo Need to get this from request/response
            return super._isCanceled();
        }
    }
}
