package com.biomatters.plugins.biocode.server;

import com.biomatters.geneious.publicapi.databaseservice.Query;
import com.biomatters.geneious.publicapi.documents.DocumentField;
import com.biomatters.geneious.publicapi.utilities.StringUtilities;
import com.biomatters.plugins.biocode.labbench.ConnectionException;
import com.biomatters.plugins.biocode.labbench.FimsSample;
import com.biomatters.plugins.biocode.labbench.fims.FIMSConnection;
import com.biomatters.plugins.biocode.labbench.rest.client.ServerFimsConnection;

import javax.ws.rs.*;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * @author Matthew Cheung
 * @version $Id$
 *          <p/>
 *          Created on 1/04/14 1:40 PM
 */
@Path("fims")
public class FIMS {

    @Path("fields/{id}")
    @GET
    @Produces("application/xml")
    public DocumentField getField(@PathParam("id") String id) {
        FIMSConnection fims = LIMSInitializationServlet.getFimsConnection();
        if(fims == null) {
            throw new InternalServerErrorException("Server not connected to FIMS.  Contact your systems administrator.");
        }
        if("tissue".equals(id)) {
            return fims.getTissueSampleDocumentField();
        } else if("latitude".equals(id)) {
            return fims.getLatitudeField();
        } else if("longitude".equals(id)) {
            return fims.getLongitudeField();
        } else if("plate".equals(id)) {
            return fims.getPlateDocumentField();
        } else if("well".equals(id)) {
            return fims.getWellDocumentField();
        } else if("null".equals(id)) {
            return null;
        } else {
            throw new NotFoundException("No field for \"" + id + "\"");
        }
    }


    @Path("fields")
    @GET
    @Produces("application/xml")
    public XMLSerializableList<DocumentField> searchFields(@QueryParam("type")String type) {
        if("taxonomy".equals(type)) {
            return new XMLSerializableList<DocumentField>(DocumentField.class,
                                LIMSInitializationServlet.getFimsConnection().getTaxonomyAttributes());
        } else if("collection".equals(type)) {
            return new XMLSerializableList<DocumentField>(DocumentField.class,
                                LIMSInitializationServlet.getFimsConnection().getCollectionAttributes());
        } else {
            return new XMLSerializableList<DocumentField>(DocumentField.class,
                    LIMSInitializationServlet.getFimsConnection().getSearchAttributes());
        }
    }

    @Path("samples/count")
    @GET
    @Produces("text/plain")
    public int getTotalNumberOfSamples() {
        try {
            return LIMSInitializationServlet.getFimsConnection().getTotalNumberOfSamples();
        } catch (ConnectionException e) {
            throw new InternalServerErrorException(e);
        }
    }

    @Path("samples/search")
    @GET
    @Produces("text/plain")
    public String getMatchingSampleIds(@QueryParam("query")String queryString, @QueryParam("type")String typeString) {
        try {
            Query query = RestQueryUtils.createQueryFromQueryString(RestQueryUtils.QueryType.forTypeString(typeString), queryString, Collections.<String, Object>emptyMap());
            List<String> result = LIMSInitializationServlet.getFimsConnection().getTissueIdsMatchingQuery(query);
            return StringUtilities.join(",", result);
        } catch (ConnectionException e) {
            throw new InternalServerErrorException(e);
        }
    }

    @Path("samples")
    @GET
    @Produces("application/xml")
    public XMLSerializableList<FimsSample> getSamplesForIds(@QueryParam("ids")String ids) {
        List<String> toSearchFor;
        if(ids == null) {
            toSearchFor = Collections.emptyList();
        } else {
            String[] idsArray = ids.split(",");
            toSearchFor = Arrays.asList(idsArray);
        }
        try {

            List<FimsSample> fimsSamples = LIMSInitializationServlet.getFimsConnection().retrieveSamplesForTissueIds(
                    toSearchFor);

            return new XMLSerializableList<FimsSample>(FimsSample.class, fimsSamples);
        } catch (ConnectionException e) {
            throw new InternalServerErrorException(e);
        }
    }

    @Path("property/{id}")
    @GET
    @Produces("text/plain")
    public Boolean getProperty(@PathParam("id")String id) {
        if(ServerFimsConnection.HAS_PLATE_INFO.equals(id)) {
            return LIMSInitializationServlet.getFimsConnection().storesPlateAndWellInformation();
        } else if(ServerFimsConnection.HAS_PHOTOS.equals(id)) {
            return LIMSInitializationServlet.getFimsConnection().hasPhotos();
        } else {
            return null;
        }
    }
}
