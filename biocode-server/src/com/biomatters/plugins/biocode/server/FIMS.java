package com.biomatters.plugins.biocode.server;

import com.biomatters.geneious.publicapi.databaseservice.DatabaseServiceException;
import com.biomatters.geneious.publicapi.databaseservice.Query;
import com.biomatters.geneious.publicapi.documents.DocumentField;
import com.biomatters.geneious.publicapi.utilities.StringUtilities;
import com.biomatters.plugins.biocode.labbench.ConnectionException;
import com.biomatters.plugins.biocode.labbench.FimsSample;
import com.biomatters.plugins.biocode.labbench.fims.FIMSConnection;
import com.biomatters.plugins.biocode.labbench.fims.FimsProject;
import com.biomatters.plugins.biocode.labbench.rest.client.ServerFimsConnection;
import com.biomatters.plugins.biocode.server.security.Projects;
import com.biomatters.plugins.biocode.server.security.Role;
import com.biomatters.plugins.biocode.server.security.Users;

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
        FIMSConnection fims = LIMSInitializationListener.getFimsConnection();
        if(fims == null) {
            throw new InternalServerErrorException("Server not connected to FIMS.  Contact your systems administrator.");
        }
        DocumentField field = null;
        if("tissue".equals(id)) {
            field = fims.getTissueSampleDocumentField();
        } else if("latitude".equals(id)) {
            field = fims.getLatitudeField();
        } else if("longitude".equals(id)) {
            field = fims.getLongitudeField();
        } else if("plate".equals(id)) {
            field = fims.getPlateDocumentField();
        } else if("well".equals(id)) {
            field = fims.getWellDocumentField();
        }
        if(field == null) {
            throw new NotFoundException("No field for \"" + id + "\"");
        } else {
            return field;
        }
    }


    @Path("fields")
    @GET
    @Produces("application/xml")
    public XMLSerializableList<DocumentField> searchFields(@QueryParam("type")String type) {
        if("taxonomy".equals(type)) {
            return new XMLSerializableList<DocumentField>(DocumentField.class,
                                LIMSInitializationListener.getFimsConnection().getTaxonomyAttributes());
        } else if("collection".equals(type)) {
            return new XMLSerializableList<DocumentField>(DocumentField.class,
                                LIMSInitializationListener.getFimsConnection().getCollectionAttributes());
        } else {
            return new XMLSerializableList<DocumentField>(DocumentField.class,
                    LIMSInitializationListener.getFimsConnection().getSearchAttributes());
        }
    }

    @Path("samples/count")
    @GET
    @Produces("text/plain")
    public int getTotalNumberOfSamples() {
        try {
            return LIMSInitializationListener.getFimsConnection().getTotalNumberOfSamples();
        } catch (ConnectionException e) {
            throw new InternalServerErrorException(e);
        }
    }

    @Path("samples/search")
    @GET
    @Produces("text/plain")
    public String getMatchingSampleIds(@QueryParam("query")String queryString, @QueryParam("type")String typeString) {
        try {
            List<FimsProject> projectsUserHasAccessTo = Projects.getFimsProjectsUserHasAtLeastRole(
                    LIMSInitializationListener.getDataSource(),
                    LIMSInitializationListener.getFimsConnection(),
                    Users.getLoggedInUser(), Role.READER);
            Query query = RestQueryUtils.createQueryFromQueryString(RestQueryUtils.QueryType.forTypeString(typeString), queryString, Collections.<String, Object>emptyMap());
            List<String> result = LIMSInitializationListener.getFimsConnection().getTissueIdsMatchingQuery(query, projectsUserHasAccessTo);
            return StringUtilities.join(",", result);
        } catch (ConnectionException e) {
            throw new InternalServerErrorException(e);
        } catch (DatabaseServiceException e) {
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

            List<FimsSample> fimsSamples = LIMSInitializationListener.getFimsConnection().retrieveSamplesForTissueIds(
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
            return LIMSInitializationListener.getFimsConnection().storesPlateAndWellInformation();
        } else if(ServerFimsConnection.HAS_PHOTOS.equals(id)) {
            return LIMSInitializationListener.getFimsConnection().hasPhotos();
        } else {
            return null;
        }
    }
}
